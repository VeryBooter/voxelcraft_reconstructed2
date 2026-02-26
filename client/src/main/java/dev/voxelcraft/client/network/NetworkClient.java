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
/**
 * 中文说明：网络模块组件：负责 NetworkClient 的协议封装、通信或连接管理。
 */

// 中文标注（类）：`NetworkClient`，职责：封装网络、客户端相关逻辑。
public final class NetworkClient implements AutoCloseable {
    // 中文标注（字段）：`host`，含义：用于表示host。
    private final String host; // meaning
    // 中文标注（字段）：`port`，含义：用于表示port。
    private final int port; // meaning
    // 中文标注（字段）：`socket`，含义：用于表示套接字。
    private final Socket socket; // meaning
    // 中文标注（字段）：`in`，含义：用于表示in。
    private final DataInputStream in; // meaning
    // 中文标注（字段）：`out`，含义：用于表示out。
    private final DataOutputStream out; // meaning
    // 中文标注（字段）：`readerThread`，含义：用于表示reader、thread。
    private final Thread readerThread; // meaning
    // 中文标注（字段）：`connected`，含义：用于表示connected。
    private final AtomicBoolean connected = new AtomicBoolean(true); // meaning
    // 中文标注（字段）：`pendingMutations`，含义：用于表示pending、mutations。
    private final Queue<WorldMutation> pendingMutations = new ConcurrentLinkedQueue<>(); // meaning

    // 中文标注（字段）：`clientId`，含义：用于表示客户端、标识。
    private volatile int clientId = -1; // meaning
    // 中文标注（字段）：`protocolVersion`，含义：用于表示协议、版本。
    private volatile int protocolVersion = -1; // meaning
    // 中文标注（字段）：`lastError`，含义：用于表示last、error。
    private volatile String lastError = ""; // meaning

    // 中文标注（构造方法）：`NetworkClient`，参数：host、port；用途：初始化`NetworkClient`实例。
    // 中文标注（参数）：`host`，含义：用于表示host。
    // 中文标注（参数）：`port`，含义：用于表示port。
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

    // 中文标注（方法）：`connect`，参数：host、port；用途：执行connect相关逻辑。
    // 中文标注（参数）：`host`，含义：用于表示host。
    // 中文标注（参数）：`port`，含义：用于表示port。
    public static NetworkClient connect(String host, int port) throws IOException {
        return new NetworkClient(host, port);
    }

    // 中文标注（方法）：`isConnected`，参数：无；用途：判断connected是否满足条件。
    public boolean isConnected() {
        return connected.get() && !socket.isClosed();
    }

    // 中文标注（方法）：`drainIncoming`，参数：worldView；用途：执行drain、incoming相关逻辑。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    public void drainIncoming(ClientWorldView worldView) {
        // 中文标注（局部变量）：`mutation`，含义：用于表示mutation。
        WorldMutation mutation; // meaning
        while ((mutation = pendingMutations.poll()) != null) {
            mutation.apply(worldView);
        }
    }

    // 中文标注（方法）：`sendPlayerState`，参数：playerController；用途：执行send、玩家、状态相关逻辑。
    // 中文标注（参数）：`playerController`，含义：用于表示玩家、控制器。
    public void sendPlayerState(PlayerController playerController) {
        if (!isConnected()) {
            return;
        }

        // 中文标注（Lambda参数）：`out`，含义：用于表示out。
        send(out -> {
            out.writeByte(Protocol.C2S_PLAYER_STATE);
            out.writeDouble(playerController.x());
            out.writeDouble(playerController.y());
            out.writeDouble(playerController.z());
            out.writeFloat(playerController.yaw());
            out.writeFloat(playerController.pitch());
        });
    }

    // 中文标注（方法）：`requestChunkRadius`，参数：centerChunkX、centerChunkZ、radius；用途：执行request、区块、radius相关逻辑。
    // 中文标注（参数）：`centerChunkX`，含义：用于表示center、区块、X坐标。
    // 中文标注（参数）：`centerChunkZ`，含义：用于表示center、区块、Z坐标。
    // 中文标注（参数）：`radius`，含义：用于表示radius。
    public void requestChunkRadius(int centerChunkX, int centerChunkZ, int radius) {
        if (!isConnected()) {
            return;
        }

        // 中文标注（Lambda参数）：`out`，含义：用于表示out。
        send(out -> {
            out.writeByte(Protocol.C2S_REQUEST_CHUNKS);
            out.writeInt(centerChunkX);
            out.writeInt(centerChunkZ);
            out.writeInt(radius);
        });
    }

    // 中文标注（方法）：`sendBlockSet`，参数：pos、block；用途：执行send、方块、集合相关逻辑。
    // 中文标注（参数）：`pos`，含义：用于表示位置。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    public void sendBlockSet(BlockPos pos, Block block) {
        if (!isConnected()) {
            return;
        }

        // 中文标注（Lambda参数）：`out`，含义：用于表示out。
        send(out -> {
            out.writeByte(Protocol.C2S_BLOCK_SET);
            out.writeInt(pos.x());
            out.writeInt(pos.y());
            out.writeInt(pos.z());
            PacketIO.writeString(out, block.id().toString());
        });
    }

    // 中文标注（方法）：`statusLine`，参数：无；用途：执行status、line相关逻辑。
    public String statusLine() {
        if (isConnected()) {
            // 中文标注（局部变量）：`idText`，含义：用于表示标识、text。
            String idText = clientId >= 0 ? String.valueOf(clientId) : "pending"; // meaning
            return "online " + host + ":" + port + " id=" + idText;
        }
        if (lastError.isBlank()) {
            return "offline " + host + ":" + port;
        }
        return "offline " + host + ":" + port + " reason=" + lastError;
    }

    // 中文标注（方法）：`close`，参数：无；用途：执行close相关逻辑。
    @Override
    public void close() {
        if (!connected.compareAndSet(true, false)) {
            return;
        }

        try {
            socket.close();
        // 中文标注（异常参数）：`ignored`，含义：用于表示ignored。
        } catch (IOException ignored) {
        }

        readerThread.interrupt();
    }

    // 中文标注（方法）：`sendHello`，参数：无；用途：执行send、hello相关逻辑。
    private void sendHello() throws IOException {
        synchronized (out) {
            out.writeByte(Protocol.C2S_HELLO);
            out.writeInt(Protocol.VERSION);
            out.flush();
        }
    }

    // 中文标注（方法）：`send`，参数：writer；用途：执行send相关逻辑。
    // 中文标注（参数）：`writer`，含义：用于表示writer。
    private void send(PacketWriter writer) {
        if (!isConnected()) {
            return;
        }

        try {
            synchronized (out) {
                writer.write(out);
                out.flush();
            }
        // 中文标注（异常参数）：`exception`，含义：用于表示exception。
        } catch (IOException exception) {
            disconnect(exception.getMessage());
        }
    }

    // 中文标注（方法）：`readLoop`，参数：无；用途：获取或读取读取、loop。
    private void readLoop() {
        try {
            while (isConnected()) {
                // 中文标注（局部变量）：`packetType`，含义：用于表示数据包、类型。
                byte packetType; // meaning
                try {
                    packetType = in.readByte();
                // 中文标注（异常参数）：`eofException`，含义：用于表示eof、exception。
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
        // 中文标注（异常参数）：`socketException`，含义：用于表示套接字、exception。
        } catch (SocketException socketException) {
            if (isConnected()) {
                disconnect(socketException.getMessage());
            }
        // 中文标注（异常参数）：`exception`，含义：用于表示exception。
        } catch (IOException exception) {
            disconnect(exception.getMessage());
        }
    }

    // 中文标注（方法）：`readWelcome`，参数：无；用途：获取或读取读取、welcome。
    private void readWelcome() throws IOException {
        // 中文标注（局部变量）：`remoteClientId`，含义：用于表示remote、客户端、标识。
        int remoteClientId = in.readInt(); // meaning
        // 中文标注（局部变量）：`remoteProtocolVersion`，含义：用于表示remote、协议、版本。
        int remoteProtocolVersion = in.readInt(); // meaning

        this.clientId = remoteClientId;
        this.protocolVersion = remoteProtocolVersion;

        if (remoteProtocolVersion != Protocol.VERSION) {
            disconnect("protocol mismatch server=" + remoteProtocolVersion + " client=" + Protocol.VERSION);
        }
    }

    // 中文标注（方法）：`readChunkData`，参数：无；用途：获取或读取读取、区块、数据。
    private void readChunkData() throws IOException {
        // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
        int chunkX = in.readInt(); // meaning
        // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
        int chunkZ = in.readInt(); // meaning
        // 中文标注（局部变量）：`blockCount`，含义：用于表示方块、数量。
        int blockCount = in.readInt(); // meaning

        // 中文标注（局部变量）：`blocks`，含义：用于表示方块集合。
        List<RemoteBlock> blocks = new ArrayList<>(Math.max(0, blockCount)); // meaning
        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = 0; i < blockCount; i++) { // meaning
            // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
            int localX = in.readUnsignedByte(); // meaning
            // 中文标注（局部变量）：`y`，含义：用于表示Y坐标。
            int y = in.readShort(); // meaning
            // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
            int localZ = in.readUnsignedByte(); // meaning
            // 中文标注（局部变量）：`blockId`，含义：用于表示方块、标识。
            String blockId = PacketIO.readString(in); // meaning
            blocks.add(new RemoteBlock(localX, y, localZ, blockId));
        }

        // 中文标注（Lambda参数）：`worldView`，含义：用于表示世界、view。
        pendingMutations.add(worldView -> {
            // 中文标注（局部变量）：`baseX`，含义：用于表示base、X坐标。
            int baseX = chunkX * Section.SIZE; // meaning
            // 中文标注（局部变量）：`baseZ`，含义：用于表示base、Z坐标。
            int baseZ = chunkZ * Section.SIZE; // meaning
            // 中文标注（局部变量）：`block`，含义：用于表示方块。
            for (RemoteBlock block : blocks) {
                // 中文标注（局部变量）：`worldX`，含义：用于表示世界、X坐标。
                int worldX = baseX + block.localX; // meaning
                // 中文标注（局部变量）：`worldY`，含义：用于表示世界、Y坐标。
                int worldY = block.y; // meaning
                // 中文标注（局部变量）：`worldZ`，含义：用于表示世界、Z坐标。
                int worldZ = baseZ + block.localZ; // meaning
                worldView.setBlock(worldX, worldY, worldZ, Blocks.byIdOrAir(block.blockId));
            }
        });
    }

    // 中文标注（方法）：`readBlockUpdate`，参数：无；用途：获取或读取读取、方块、更新。
    private void readBlockUpdate() throws IOException {
        // 中文标注（局部变量）：`x`，含义：用于表示X坐标。
        int x = in.readInt(); // meaning
        // 中文标注（局部变量）：`y`，含义：用于表示Y坐标。
        int y = in.readInt(); // meaning
        // 中文标注（局部变量）：`z`，含义：用于表示Z坐标。
        int z = in.readInt(); // meaning
        // 中文标注（局部变量）：`blockId`，含义：用于表示方块、标识。
        String blockId = PacketIO.readString(in); // meaning

        // 中文标注（Lambda参数）：`worldView`，含义：用于表示世界、view。
        pendingMutations.add(worldView -> worldView.setBlock(x, y, z, Blocks.byIdOrAir(blockId))); // meaning
    }

    // 中文标注（方法）：`disconnect`，参数：reason；用途：执行disconnect相关逻辑。
    // 中文标注（参数）：`reason`，含义：用于表示reason。
    private void disconnect(String reason) {
        if (!connected.compareAndSet(true, false)) {
            return;
        }

        lastError = reason == null ? "disconnected" : reason;

        try {
            socket.close();
        // 中文标注（异常参数）：`ignored`，含义：用于表示ignored。
        } catch (IOException ignored) {
        }
    }

    // 中文标注（接口）：`PacketWriter`，职责：封装数据包、writer相关逻辑。
    @FunctionalInterface
    private interface PacketWriter {
        // 中文标注（方法）：`write`，参数：out；用途：设置、写入或注册写入。
        // 中文标注（参数）：`out`，含义：用于表示out。
        void write(DataOutputStream out) throws IOException; // meaning
    }

    // 中文标注（接口）：`WorldMutation`，职责：封装世界、mutation相关逻辑。
    @FunctionalInterface
    private interface WorldMutation {
        // 中文标注（方法）：`apply`，参数：worldView；用途：处理apply逻辑。
        // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
        void apply(ClientWorldView worldView); // meaning
    }

    // 中文标注（类）：`RemoteBlock`，职责：封装remote、方块相关逻辑。
    private static final class RemoteBlock {
        // 中文标注（字段）：`localX`，含义：用于表示局部、X坐标。
        private final int localX; // meaning
        // 中文标注（字段）：`y`，含义：用于表示Y坐标。
        private final int y; // meaning
        // 中文标注（字段）：`localZ`，含义：用于表示局部、Z坐标。
        private final int localZ; // meaning
        // 中文标注（字段）：`blockId`，含义：用于表示方块、标识。
        private final String blockId; // meaning

        // 中文标注（构造方法）：`RemoteBlock`，参数：localX、y、localZ、blockId；用途：初始化`RemoteBlock`实例。
        // 中文标注（参数）：`localX`，含义：用于表示局部、X坐标。
        // 中文标注（参数）：`y`，含义：用于表示Y坐标。
        // 中文标注（参数）：`localZ`，含义：用于表示局部、Z坐标。
        // 中文标注（参数）：`blockId`，含义：用于表示方块、标识。
        private RemoteBlock(int localX, int y, int localZ, String blockId) {
            this.localX = localX;
            this.y = y;
            this.localZ = localZ;
            this.blockId = blockId;
        }
    }
}
