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
/**
 * 中文说明：服务器网络主逻辑：负责连接、协议收发、区块同步与玩家状态维护。
 */

// 中文标注（类）：`VoxelcraftServer`，职责：封装voxelcraft、服务器相关逻辑。
public final class VoxelcraftServer implements AutoCloseable {
    // 中文标注（字段）：`TICK_INTERVAL_NANOS`，含义：用于表示刻、interval、nanos。
    private static final long TICK_INTERVAL_NANOS = 1_000_000_000L / 20L;
    // 中文标注（字段）：`DEFAULT_CHUNK_RADIUS`，含义：用于表示默认、区块、radius。
    private static final int DEFAULT_CHUNK_RADIUS = 4;
    // 中文标注（字段）：`MAX_CHUNK_RADIUS`，含义：用于表示最大、区块、radius。
    private static final int MAX_CHUNK_RADIUS = 8;
    // 中文标注（字段）：`NETWORK_CHUNK_MIN_Y`，含义：用于表示网络、区块、最小、Y坐标。
    private static final int NETWORK_CHUNK_MIN_Y = -128;

    // 中文标注（字段）：`port`，含义：用于表示port。
    private final int port;
    // 中文标注（字段）：`game`，含义：用于表示game。
    private final Game game = new Game();
    // 中文标注（字段）：`running`，含义：用于表示running。
    private final AtomicBoolean running = new AtomicBoolean(false);
    // 中文标注（字段）：`clientIdSequence`，含义：用于表示客户端、标识、sequence。
    private final AtomicInteger clientIdSequence = new AtomicInteger(1);
    // 中文标注（字段）：`clients`，含义：用于表示clients。
    private final Map<Integer, ClientConnection> clients = new ConcurrentHashMap<>();
    // 中文标注（字段）：`clientExecutor`，含义：用于表示客户端、executor。
    // 中文标注（Lambda参数）：`runnable`，含义：用于表示runnable。
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool(runnable -> {
        // 中文标注（局部变量）：`thread`，含义：用于表示thread。
        Thread thread = new Thread(runnable, "voxelcraft-client-io");
        thread.setDaemon(true);
        return thread;
    });

    // 中文标注（字段）：`serverSocket`，含义：用于表示服务器、套接字。
    private ServerSocket serverSocket;
    // 中文标注（字段）：`acceptThread`，含义：用于表示accept、thread。
    private Thread acceptThread;
    // 中文标注（字段）：`tickThread`，含义：用于表示刻、thread。
    private Thread tickThread;

    // 中文标注（构造方法）：`VoxelcraftServer`，参数：port；用途：初始化`VoxelcraftServer`实例。
    // 中文标注（参数）：`port`，含义：用于表示port。
    public VoxelcraftServer(int port) {
        this.port = port;
    }

    // 中文标注（方法）：`start`，参数：无；用途：执行开始相关逻辑。
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

    // 中文标注（方法）：`blockUntilShutdown`，参数：无；用途：执行方块、until、shutdown相关逻辑。
    public void blockUntilShutdown() {
        while (running.get()) {
            try {
                Thread.sleep(500L);
            // 中文标注（异常参数）：`interruptedException`，含义：用于表示interrupted、exception。
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // 中文标注（方法）：`acceptLoop`，参数：无；用途：执行accept、loop相关逻辑。
    private void acceptLoop() {
        while (running.get()) {
            try {
                // 中文标注（局部变量）：`socket`，含义：用于表示套接字。
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);

                // 中文标注（局部变量）：`clientId`，含义：用于表示客户端、标识。
                int clientId = clientIdSequence.getAndIncrement();
                // 中文标注（局部变量）：`connection`，含义：用于表示connection。
                ClientConnection connection = new ClientConnection(clientId, socket);
                clients.put(clientId, connection);
                connection.start();

                System.out.println("[server] client connected id=" + clientId + " from " + socket.getRemoteSocketAddress());
            // 中文标注（异常参数）：`socketException`，含义：用于表示套接字、exception。
            } catch (SocketException socketException) {
                if (running.get()) {
                    System.err.println("[server] accept loop socket error: " + socketException.getMessage());
                }
            // 中文标注（异常参数）：`exception`，含义：用于表示exception。
            } catch (IOException exception) {
                if (running.get()) {
                    System.err.println("[server] accept loop failed: " + exception.getMessage());
                }
            }
        }
    }

    // 中文标注（方法）：`tickLoop`，参数：无；用途：更新刻、loop相关状态。
    private void tickLoop() {
        while (running.get()) {
            // 中文标注（局部变量）：`started`，含义：用于表示started。
            long started = System.nanoTime();
            game.tick();

            // 中文标注（局部变量）：`elapsed`，含义：用于表示已耗时。
            long elapsed = System.nanoTime() - started;
            // 中文标注（局部变量）：`sleepNanos`，含义：用于表示sleep、nanos。
            long sleepNanos = TICK_INTERVAL_NANOS - elapsed;
            if (sleepNanos <= 0) {
                continue;
            }

            // 中文标注（局部变量）：`sleepMillis`，含义：用于表示sleep、millis。
            long sleepMillis = sleepNanos / 1_000_000L;
            // 中文标注（局部变量）：`sleepNanosRemainder`，含义：用于表示sleep、nanos、remainder。
            int sleepNanosRemainder = (int) (sleepNanos % 1_000_000L);
            try {
                Thread.sleep(sleepMillis, sleepNanosRemainder);
            // 中文标注（异常参数）：`interruptedException`，含义：用于表示interrupted、exception。
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // 中文标注（方法）：`handlePacket`，参数：connection、packetType、in；用途：处理handle、数据包逻辑。
    // 中文标注（参数）：`connection`，含义：用于表示connection。
    // 中文标注（参数）：`packetType`，含义：用于表示数据包、类型。
    // 中文标注（参数）：`in`，含义：用于表示in。
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

    // 中文标注（方法）：`handleHello`，参数：connection、in；用途：处理handle、hello逻辑。
    // 中文标注（参数）：`connection`，含义：用于表示connection。
    // 中文标注（参数）：`in`，含义：用于表示in。
    private void handleHello(ClientConnection connection, DataInputStream in) throws IOException {
        // 中文标注（局部变量）：`clientProtocolVersion`，含义：用于表示客户端、协议、版本。
        int clientProtocolVersion = in.readInt();
        if (clientProtocolVersion != Protocol.VERSION) {
            connection.disconnect("protocol mismatch client=" + clientProtocolVersion + " server=" + Protocol.VERSION);
            return;
        }

        connection.authenticated = true;
        // 中文标注（Lambda参数）：`out`，含义：用于表示out。
        connection.send(out -> {
            out.writeByte(Protocol.S2C_WELCOME);
            out.writeInt(connection.id);
            out.writeInt(Protocol.VERSION);
        });

        sendChunkRadius(connection, 0, 0, DEFAULT_CHUNK_RADIUS);
    }

    // 中文标注（方法）：`handlePlayerState`，参数：connection、in；用途：处理handle、玩家、状态逻辑。
    // 中文标注（参数）：`connection`，含义：用于表示connection。
    // 中文标注（参数）：`in`，含义：用于表示in。
    private void handlePlayerState(ClientConnection connection, DataInputStream in) throws IOException {
        connection.playerX = in.readDouble();
        connection.playerY = in.readDouble();
        connection.playerZ = in.readDouble();
        connection.playerYaw = in.readFloat();
        connection.playerPitch = in.readFloat();
    }

    // 中文标注（方法）：`handleBlockSet`，参数：connection、in；用途：处理handle、方块、集合逻辑。
    // 中文标注（参数）：`connection`，含义：用于表示connection。
    // 中文标注（参数）：`in`，含义：用于表示in。
    private void handleBlockSet(ClientConnection connection, DataInputStream in) throws IOException {
        // 中文标注（局部变量）：`x`，含义：用于表示X坐标。
        int x = in.readInt();
        // 中文标注（局部变量）：`y`，含义：用于表示Y坐标。
        int y = in.readInt();
        // 中文标注（局部变量）：`z`，含义：用于表示Z坐标。
        int z = in.readInt();
        // 中文标注（局部变量）：`blockId`，含义：用于表示方块、标识。
        String blockId = PacketIO.readString(in);

        // 中文标注（局部变量）：`block`，含义：用于表示方块。
        Block block = Blocks.byIdOrAir(blockId);
        // 中文标注（局部变量）：`changed`，含义：用于表示changed。
        boolean changed = game.world().setBlock(new BlockPos(x, y, z), block);
        if (!changed) {
            return;
        }

        broadcastBlockUpdate(x, y, z, block);
    }

    // 中文标注（方法）：`handleChunkRequest`，参数：connection、in；用途：处理handle、区块、request逻辑。
    // 中文标注（参数）：`connection`，含义：用于表示connection。
    // 中文标注（参数）：`in`，含义：用于表示in。
    private void handleChunkRequest(ClientConnection connection, DataInputStream in) throws IOException {
        // 中文标注（局部变量）：`centerChunkX`，含义：用于表示center、区块、X坐标。
        int centerChunkX = in.readInt();
        // 中文标注（局部变量）：`centerChunkZ`，含义：用于表示center、区块、Z坐标。
        int centerChunkZ = in.readInt();
        // 中文标注（局部变量）：`radius`，含义：用于表示radius。
        int radius = in.readInt();

        // 中文标注（局部变量）：`clampedRadius`，含义：用于表示clamped、radius。
        int clampedRadius = Math.max(0, Math.min(MAX_CHUNK_RADIUS, radius));
        sendChunkRadius(connection, centerChunkX, centerChunkZ, clampedRadius);
    }

    // 中文标注（方法）：`sendChunkRadius`，参数：connection、centerChunkX、centerChunkZ、radius；用途：执行send、区块、radius相关逻辑。
    // 中文标注（参数）：`connection`，含义：用于表示connection。
    // 中文标注（参数）：`centerChunkX`，含义：用于表示center、区块、X坐标。
    // 中文标注（参数）：`centerChunkZ`，含义：用于表示center、区块、Z坐标。
    // 中文标注（参数）：`radius`，含义：用于表示radius。
    private void sendChunkRadius(ClientConnection connection, int centerChunkX, int centerChunkZ, int radius) {
        // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                sendChunk(connection, chunkX, chunkZ);
            }
        }
    }

    // 中文标注（方法）：`sendChunk`，参数：connection、chunkX、chunkZ；用途：执行send、区块相关逻辑。
    // 中文标注（参数）：`connection`，含义：用于表示connection。
    // 中文标注（参数）：`chunkX`，含义：用于表示区块、X坐标。
    // 中文标注（参数）：`chunkZ`，含义：用于表示区块、Z坐标。
    private void sendChunk(ClientConnection connection, int chunkX, int chunkZ) {
        // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
        Chunk chunk = game.world().getOrGenerateChunk(chunkX, chunkZ);
        // 中文标注（局部变量）：`records`，含义：用于表示records。
        List<ChunkBlockRecord> records = new ArrayList<>();
        // 中文标注（Lambda参数）：`localX`，含义：用于表示局部、X坐标。
        // 中文标注（Lambda参数）：`y`，含义：用于表示Y坐标。
        // 中文标注（Lambda参数）：`localZ`，含义：用于表示局部、Z坐标。
        // 中文标注（Lambda参数）：`block`，含义：用于表示方块。
        chunk.forEachNonAirInRange(NETWORK_CHUNK_MIN_Y, World.MAX_Y, (localX, y, localZ, block) -> {
            records.add(new ChunkBlockRecord(localX, y, localZ, block.id().toString()));
        });

        // 中文标注（Lambda参数）：`out`，含义：用于表示out。
        connection.send(out -> {
            out.writeByte(Protocol.S2C_CHUNK_DATA);
            out.writeInt(chunkX);
            out.writeInt(chunkZ);
            out.writeInt(records.size());
            // 中文标注（局部变量）：`record`，含义：用于表示record。
            for (ChunkBlockRecord record : records) {
                out.writeByte(record.localX);
                out.writeShort(record.y);
                out.writeByte(record.localZ);
                PacketIO.writeString(out, record.blockId);
            }
        });
    }

    // 中文标注（方法）：`broadcastBlockUpdate`，参数：x、y、z、block；用途：执行broadcast、方块、更新相关逻辑。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    private void broadcastBlockUpdate(int x, int y, int z, Block block) {
        // 中文标注（局部变量）：`blockId`，含义：用于表示方块、标识。
        String blockId = block.id().toString();
        // 中文标注（局部变量）：`client`，含义：用于表示客户端。
        for (ClientConnection client : clients.values()) {
            if (!client.authenticated) {
                continue;
            }

            // 中文标注（Lambda参数）：`out`，含义：用于表示out。
            client.send(out -> {
                out.writeByte(Protocol.S2C_BLOCK_UPDATE);
                out.writeInt(x);
                out.writeInt(y);
                out.writeInt(z);
                PacketIO.writeString(out, blockId);
            });
        }
    }

    // 中文标注（方法）：`close`，参数：无；用途：执行close相关逻辑。
    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        // 中文标注（异常参数）：`exception`，含义：用于表示exception。
        } catch (IOException exception) {
            System.err.println("[server] failed to close server socket: " + exception.getMessage());
        }

        // 中文标注（局部变量）：`client`，含义：用于表示客户端。
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

    // 中文标注（接口）：`PacketWriter`，职责：封装数据包、writer相关逻辑。
    @FunctionalInterface
    private interface PacketWriter {
        // 中文标注（方法）：`write`，参数：out；用途：设置、写入或注册写入。
        // 中文标注（参数）：`out`，含义：用于表示out。
        void write(DataOutputStream out) throws IOException;
    }

    // 中文标注（类）：`ChunkBlockRecord`，职责：封装区块、方块、record相关逻辑。
    private static final class ChunkBlockRecord {
        // 中文标注（字段）：`localX`，含义：用于表示局部、X坐标。
        private final int localX;
        // 中文标注（字段）：`y`，含义：用于表示Y坐标。
        private final int y;
        // 中文标注（字段）：`localZ`，含义：用于表示局部、Z坐标。
        private final int localZ;
        // 中文标注（字段）：`blockId`，含义：用于表示方块、标识。
        private final String blockId;

        // 中文标注（构造方法）：`ChunkBlockRecord`，参数：localX、y、localZ、blockId；用途：初始化`ChunkBlockRecord`实例。
        // 中文标注（参数）：`localX`，含义：用于表示局部、X坐标。
        // 中文标注（参数）：`y`，含义：用于表示Y坐标。
        // 中文标注（参数）：`localZ`，含义：用于表示局部、Z坐标。
        // 中文标注（参数）：`blockId`，含义：用于表示方块、标识。
        private ChunkBlockRecord(int localX, int y, int localZ, String blockId) {
            this.localX = localX;
            this.y = y;
            this.localZ = localZ;
            this.blockId = blockId;
        }
    }

    // 中文标注（类）：`ClientConnection`，职责：封装客户端、connection相关逻辑。
    private final class ClientConnection {
        // 中文标注（字段）：`id`，含义：用于表示标识。
        private final int id;
        // 中文标注（字段）：`socket`，含义：用于表示套接字。
        private final Socket socket;
        // 中文标注（字段）：`in`，含义：用于表示in。
        private final DataInputStream in;
        // 中文标注（字段）：`out`，含义：用于表示out。
        private final DataOutputStream out;
        // 中文标注（字段）：`closed`，含义：用于表示closed。
        private final AtomicBoolean closed = new AtomicBoolean(false);

        // 中文标注（字段）：`authenticated`，含义：用于表示authenticated。
        private volatile boolean authenticated;

        // 中文标注（字段）：`playerX`，含义：用于表示玩家、X坐标。
        private volatile double playerX;
        // 中文标注（字段）：`playerY`，含义：用于表示玩家、Y坐标。
        private volatile double playerY;
        // 中文标注（字段）：`playerZ`，含义：用于表示玩家、Z坐标。
        private volatile double playerZ;
        // 中文标注（字段）：`playerYaw`，含义：用于表示玩家、yaw。
        private volatile float playerYaw;
        // 中文标注（字段）：`playerPitch`，含义：用于表示玩家、pitch。
        private volatile float playerPitch;

        // 中文标注（构造方法）：`ClientConnection`，参数：id、socket；用途：初始化`ClientConnection`实例。
        // 中文标注（参数）：`id`，含义：用于表示标识。
        // 中文标注（参数）：`socket`，含义：用于表示套接字。
        private ClientConnection(int id, Socket socket) throws IOException {
            this.id = id;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        }

        // 中文标注（方法）：`start`，参数：无；用途：执行开始相关逻辑。
        private void start() {
            clientExecutor.execute(this::readLoop);
        }

        // 中文标注（方法）：`readLoop`，参数：无；用途：获取或读取读取、loop。
        private void readLoop() {
            try {
                while (running.get() && !socket.isClosed()) {
                    // 中文标注（局部变量）：`packetType`，含义：用于表示数据包、类型。
                    byte packetType;
                    try {
                        packetType = in.readByte();
                    // 中文标注（异常参数）：`eofException`，含义：用于表示eof、exception。
                    } catch (EOFException eofException) {
                        break;
                    }

                    handlePacket(this, packetType, in);
                }
            // 中文标注（异常参数）：`exception`，含义：用于表示exception。
            } catch (IOException exception) {
                if (running.get()) {
                    System.err.println("[server] client " + id + " read failed: " + exception.getMessage());
                }
            } finally {
                disconnect("connection closed");
            }
        }

        // 中文标注（方法）：`send`，参数：writer；用途：执行send相关逻辑。
        // 中文标注（参数）：`writer`，含义：用于表示writer。
        private void send(PacketWriter writer) {
            if (closed.get()) {
                return;
            }

            try {
                synchronized (out) {
                    writer.write(out);
                    out.flush();
                }
            // 中文标注（异常参数）：`exception`，含义：用于表示exception。
            } catch (IOException exception) {
                disconnect("send failed: " + exception.getMessage());
            }
        }

        // 中文标注（方法）：`disconnect`，参数：reason；用途：执行disconnect相关逻辑。
        // 中文标注（参数）：`reason`，含义：用于表示reason。
        private void disconnect(String reason) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }

            clients.remove(id);
            try {
                socket.close();
            // 中文标注（异常参数）：`ignored`，含义：用于表示ignored。
            } catch (IOException ignored) {
            }

            if (running.get()) {
                System.out.println("[server] client disconnected id=" + id + " reason=" + reason);
            }
        }
    }
}