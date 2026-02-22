package dev.voxelcraft.server.net;

import dev.voxelcraft.core.Game;
import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.net.PacketIO;
import dev.voxelcraft.core.net.Protocol;
import dev.voxelcraft.core.world.BlockPos;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.World;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class VoxelcraftServer implements AutoCloseable {
    private static final long TICK_INTERVAL_NANOS = 1_000_000_000L / 20L;
    private static final int DEFAULT_CHUNK_RADIUS = 4;
    private static final int MAX_CHUNK_RADIUS = 8;
    private static final int NETWORK_CHUNK_MIN_Y = -128;

    private final int port;
    private final Game game = new Game();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger clientIdSequence = new AtomicInteger(1);
    private final Map<Integer, ClientConnection> clients = new ConcurrentHashMap<>();
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "voxelcraft-client-io");
        thread.setDaemon(true);
        return thread;
    });

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private Thread tickThread;

    public VoxelcraftServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        serverSocket = new ServerSocket(port);
        System.out.println("[server] listening on 0.0.0.0:" + port);

        acceptThread = new Thread(this::acceptLoop, "voxelcraft-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        tickThread = new Thread(this::tickLoop, "voxelcraft-tick");
        tickThread.setDaemon(true);
        tickThread.start();
    }

    public void blockUntilShutdown() {
        while (running.get()) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);

                int clientId = clientIdSequence.getAndIncrement();
                ClientConnection connection = new ClientConnection(clientId, socket);
                clients.put(clientId, connection);
                connection.start();

                System.out.println("[server] client connected id=" + clientId + " from " + socket.getRemoteSocketAddress());
            } catch (SocketException socketException) {
                if (running.get()) {
                    System.err.println("[server] accept loop socket error: " + socketException.getMessage());
                }
            } catch (IOException exception) {
                if (running.get()) {
                    System.err.println("[server] accept loop failed: " + exception.getMessage());
                }
            }
        }
    }

    private void tickLoop() {
        while (running.get()) {
            long started = System.nanoTime();
            game.tick();

            long elapsed = System.nanoTime() - started;
            long sleepNanos = TICK_INTERVAL_NANOS - elapsed;
            if (sleepNanos <= 0) {
                continue;
            }

            long sleepMillis = sleepNanos / 1_000_000L;
            int sleepNanosRemainder = (int) (sleepNanos % 1_000_000L);
            try {
                Thread.sleep(sleepMillis, sleepNanosRemainder);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handlePacket(ClientConnection connection, byte packetType, DataInputStream in) throws IOException {
        if (!connection.authenticated && packetType != Protocol.C2S_HELLO) {
            connection.disconnect("packet before hello");
            return;
        }

        switch (packetType) {
            case Protocol.C2S_HELLO -> handleHello(connection, in);
            case Protocol.C2S_PLAYER_STATE -> handlePlayerState(connection, in);
            case Protocol.C2S_BLOCK_SET -> handleBlockSet(connection, in);
            case Protocol.C2S_REQUEST_CHUNKS -> handleChunkRequest(connection, in);
            default -> connection.disconnect("unknown packet type " + packetType);
        }
    }

    private void handleHello(ClientConnection connection, DataInputStream in) throws IOException {
        int clientProtocolVersion = in.readInt();
        if (clientProtocolVersion != Protocol.VERSION) {
            connection.disconnect("protocol mismatch client=" + clientProtocolVersion + " server=" + Protocol.VERSION);
            return;
        }

        connection.authenticated = true;
        connection.send(out -> {
            out.writeByte(Protocol.S2C_WELCOME);
            out.writeInt(connection.id);
            out.writeInt(Protocol.VERSION);
        });

        sendChunkRadius(connection, 0, 0, DEFAULT_CHUNK_RADIUS);
    }

    private void handlePlayerState(ClientConnection connection, DataInputStream in) throws IOException {
        connection.playerX = in.readDouble();
        connection.playerY = in.readDouble();
        connection.playerZ = in.readDouble();
        connection.playerYaw = in.readFloat();
        connection.playerPitch = in.readFloat();
    }

    private void handleBlockSet(ClientConnection connection, DataInputStream in) throws IOException {
        int x = in.readInt();
        int y = in.readInt();
        int z = in.readInt();
        String blockId = PacketIO.readString(in);

        Block block = Blocks.byIdOrAir(blockId);
        boolean changed = game.world().setBlock(new BlockPos(x, y, z), block);
        if (!changed) {
            return;
        }

        broadcastBlockUpdate(x, y, z, block);
    }

    private void handleChunkRequest(ClientConnection connection, DataInputStream in) throws IOException {
        int centerChunkX = in.readInt();
        int centerChunkZ = in.readInt();
        int radius = in.readInt();

        int clampedRadius = Math.max(0, Math.min(MAX_CHUNK_RADIUS, radius));
        sendChunkRadius(connection, centerChunkX, centerChunkZ, clampedRadius);
    }

    private void sendChunkRadius(ClientConnection connection, int centerChunkX, int centerChunkZ, int radius) {
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                sendChunk(connection, chunkX, chunkZ);
            }
        }
    }

    private void sendChunk(ClientConnection connection, int chunkX, int chunkZ) {
        Chunk chunk = game.world().getOrGenerateChunk(chunkX, chunkZ);
        List<ChunkBlockRecord> records = new ArrayList<>();
        chunk.forEachNonAirInRange(NETWORK_CHUNK_MIN_Y, World.MAX_Y, (localX, y, localZ, block) -> {
            records.add(new ChunkBlockRecord(localX, y, localZ, block.id().toString()));
        });

        connection.send(out -> {
            out.writeByte(Protocol.S2C_CHUNK_DATA);
            out.writeInt(chunkX);
            out.writeInt(chunkZ);
            out.writeInt(records.size());
            for (ChunkBlockRecord record : records) {
                out.writeByte(record.localX);
                out.writeShort(record.y);
                out.writeByte(record.localZ);
                PacketIO.writeString(out, record.blockId);
            }
        });
    }

    private void broadcastBlockUpdate(int x, int y, int z, Block block) {
        String blockId = block.id().toString();
        for (ClientConnection client : clients.values()) {
            if (!client.authenticated) {
                continue;
            }

            client.send(out -> {
                out.writeByte(Protocol.S2C_BLOCK_UPDATE);
                out.writeInt(x);
                out.writeInt(y);
                out.writeInt(z);
                PacketIO.writeString(out, blockId);
            });
        }
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException exception) {
            System.err.println("[server] failed to close server socket: " + exception.getMessage());
        }

        for (ClientConnection client : clients.values()) {
            client.disconnect("server shutting down");
        }
        clients.clear();

        if (acceptThread != null) {
            acceptThread.interrupt();
        }
        if (tickThread != null) {
            tickThread.interrupt();
        }
        clientExecutor.shutdownNow();

        System.out.println("[server] shutdown complete");
    }

    @FunctionalInterface
    private interface PacketWriter {
        void write(DataOutputStream out) throws IOException;
    }

    private static final class ChunkBlockRecord {
        private final int localX;
        private final int y;
        private final int localZ;
        private final String blockId;

        private ChunkBlockRecord(int localX, int y, int localZ, String blockId) {
            this.localX = localX;
            this.y = y;
            this.localZ = localZ;
            this.blockId = blockId;
        }
    }

    private final class ClientConnection {
        private final int id;
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private volatile boolean authenticated;

        private volatile double playerX;
        private volatile double playerY;
        private volatile double playerZ;
        private volatile float playerYaw;
        private volatile float playerPitch;

        private ClientConnection(int id, Socket socket) throws IOException {
            this.id = id;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        }

        private void start() {
            clientExecutor.execute(this::readLoop);
        }

        private void readLoop() {
            try {
                while (running.get() && !socket.isClosed()) {
                    byte packetType;
                    try {
                        packetType = in.readByte();
                    } catch (EOFException eofException) {
                        break;
                    }

                    handlePacket(this, packetType, in);
                }
            } catch (IOException exception) {
                if (running.get()) {
                    System.err.println("[server] client " + id + " read failed: " + exception.getMessage());
                }
            } finally {
                disconnect("connection closed");
            }
        }

        private void send(PacketWriter writer) {
            if (closed.get()) {
                return;
            }

            try {
                synchronized (out) {
                    writer.write(out);
                    out.flush();
                }
            } catch (IOException exception) {
                disconnect("send failed: " + exception.getMessage());
            }
        }

        private void disconnect(String reason) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }

            clients.remove(id);
            try {
                socket.close();
            } catch (IOException ignored) {
            }

            if (running.get()) {
                System.out.println("[server] client disconnected id=" + id + " reason=" + reason);
            }
        }
    }
}
