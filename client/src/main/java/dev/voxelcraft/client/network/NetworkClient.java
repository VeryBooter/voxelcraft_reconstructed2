package dev.voxelcraft.client.network;

import dev.voxelcraft.client.player.PlayerController;
import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.net.PacketIO;
import dev.voxelcraft.core.net.Protocol;
import dev.voxelcraft.core.world.BlockPos;
import dev.voxelcraft.core.world.Section;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NetworkClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Thread readerThread;
    private final AtomicBoolean connected = new AtomicBoolean(true);
    private final Queue<WorldMutation> pendingMutations = new ConcurrentLinkedQueue<>();

    private volatile int clientId = -1;
    private volatile int protocolVersion = -1;
    private volatile String lastError = "";

    private NetworkClient(String host, int port) throws IOException {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;

        this.socket = new Socket(host, port);
        this.socket.setTcpNoDelay(true);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());

        sendHello();

        this.readerThread = new Thread(this::readLoop, "voxelcraft-net-client-reader");
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    public static NetworkClient connect(String host, int port) throws IOException {
        return new NetworkClient(host, port);
    }

    public boolean isConnected() {
        return connected.get() && !socket.isClosed();
    }

    public void drainIncoming(ClientWorldView worldView) {
        WorldMutation mutation;
        while ((mutation = pendingMutations.poll()) != null) {
            mutation.apply(worldView);
        }
    }

    public void sendPlayerState(PlayerController playerController) {
        if (!isConnected()) {
            return;
        }

        send(out -> {
            out.writeByte(Protocol.C2S_PLAYER_STATE);
            out.writeDouble(playerController.x());
            out.writeDouble(playerController.y());
            out.writeDouble(playerController.z());
            out.writeFloat(playerController.yaw());
            out.writeFloat(playerController.pitch());
        });
    }

    public void requestChunkRadius(int centerChunkX, int centerChunkZ, int radius) {
        if (!isConnected()) {
            return;
        }

        send(out -> {
            out.writeByte(Protocol.C2S_REQUEST_CHUNKS);
            out.writeInt(centerChunkX);
            out.writeInt(centerChunkZ);
            out.writeInt(radius);
        });
    }

    public void sendBlockSet(BlockPos pos, Block block) {
        if (!isConnected()) {
            return;
        }

        send(out -> {
            out.writeByte(Protocol.C2S_BLOCK_SET);
            out.writeInt(pos.x());
            out.writeInt(pos.y());
            out.writeInt(pos.z());
            PacketIO.writeString(out, block.id().toString());
        });
    }

    public String statusLine() {
        if (isConnected()) {
            String idText = clientId >= 0 ? String.valueOf(clientId) : "pending";
            return "online " + host + ":" + port + " id=" + idText;
        }
        if (lastError.isBlank()) {
            return "offline " + host + ":" + port;
        }
        return "offline " + host + ":" + port + " reason=" + lastError;
    }

    @Override
    public void close() {
        if (!connected.compareAndSet(true, false)) {
            return;
        }

        try {
            socket.close();
        } catch (IOException ignored) {
        }

        readerThread.interrupt();
    }

    private void sendHello() throws IOException {
        synchronized (out) {
            out.writeByte(Protocol.C2S_HELLO);
            out.writeInt(Protocol.VERSION);
            out.flush();
        }
    }

    private void send(PacketWriter writer) {
        if (!isConnected()) {
            return;
        }

        try {
            synchronized (out) {
                writer.write(out);
                out.flush();
            }
        } catch (IOException exception) {
            disconnect(exception.getMessage());
        }
    }

    private void readLoop() {
        try {
            while (isConnected()) {
                byte packetType;
                try {
                    packetType = in.readByte();
                } catch (EOFException eofException) {
                    disconnect("server closed connection");
                    break;
                }

                switch (packetType) {
                    case Protocol.S2C_WELCOME -> readWelcome();
                    case Protocol.S2C_CHUNK_DATA -> readChunkData();
                    case Protocol.S2C_BLOCK_UPDATE -> readBlockUpdate();
                    default -> throw new IOException("Unknown packet type " + packetType);
                }
            }
        } catch (SocketException socketException) {
            if (isConnected()) {
                disconnect(socketException.getMessage());
            }
        } catch (IOException exception) {
            disconnect(exception.getMessage());
        }
    }

    private void readWelcome() throws IOException {
        int remoteClientId = in.readInt();
        int remoteProtocolVersion = in.readInt();

        this.clientId = remoteClientId;
        this.protocolVersion = remoteProtocolVersion;

        if (remoteProtocolVersion != Protocol.VERSION) {
            disconnect("protocol mismatch server=" + remoteProtocolVersion + " client=" + Protocol.VERSION);
        }
    }

    private void readChunkData() throws IOException {
        int chunkX = in.readInt();
        int chunkZ = in.readInt();
        int blockCount = in.readInt();

        List<RemoteBlock> blocks = new ArrayList<>(Math.max(0, blockCount));
        for (int i = 0; i < blockCount; i++) {
            int localX = in.readUnsignedByte();
            int y = in.readShort();
            int localZ = in.readUnsignedByte();
            String blockId = PacketIO.readString(in);
            blocks.add(new RemoteBlock(localX, y, localZ, blockId));
        }

        pendingMutations.add(worldView -> {
            int baseX = chunkX * Section.SIZE;
            int baseZ = chunkZ * Section.SIZE;
            for (RemoteBlock block : blocks) {
                int worldX = baseX + block.localX;
                int worldY = block.y;
                int worldZ = baseZ + block.localZ;
                worldView.setBlock(worldX, worldY, worldZ, Blocks.byIdOrAir(block.blockId));
            }
        });
    }

    private void readBlockUpdate() throws IOException {
        int x = in.readInt();
        int y = in.readInt();
        int z = in.readInt();
        String blockId = PacketIO.readString(in);

        pendingMutations.add(worldView -> worldView.setBlock(x, y, z, Blocks.byIdOrAir(blockId)));
    }

    private void disconnect(String reason) {
        if (!connected.compareAndSet(true, false)) {
            return;
        }

        lastError = reason == null ? "disconnected" : reason;

        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    @FunctionalInterface
    private interface PacketWriter {
        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    private interface WorldMutation {
        void apply(ClientWorldView worldView);
    }

    private static final class RemoteBlock {
        private final int localX;
        private final int y;
        private final int localZ;
        private final String blockId;

        private RemoteBlock(int localX, int y, int localZ, String blockId) {
            this.localX = localX;
            this.y = y;
            this.localZ = localZ;
            this.blockId = blockId;
        }
    }
}
