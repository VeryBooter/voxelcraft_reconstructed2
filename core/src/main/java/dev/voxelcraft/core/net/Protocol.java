package dev.voxelcraft.core.net;
/**
 * 中文说明：网络模块组件：负责 Protocol 的协议封装、通信或连接管理。
 */

// 中文标注（类）：`Protocol`，职责：封装协议相关逻辑。
public final class Protocol {
    // 中文标注（字段）：`VERSION`，含义：用于表示版本。
    public static final int VERSION = 1; // meaning

    // 中文标注（字段）：`C2S_HELLO`，含义：用于表示c、2、s、hello。
    public static final byte C2S_HELLO = 1; // meaning
    // 中文标注（字段）：`C2S_PLAYER_STATE`，含义：用于表示c、2、s、玩家、状态。
    public static final byte C2S_PLAYER_STATE = 2; // meaning
    // 中文标注（字段）：`C2S_BLOCK_SET`，含义：用于表示c、2、s、方块、集合。
    public static final byte C2S_BLOCK_SET = 3; // meaning
    // 中文标注（字段）：`C2S_REQUEST_CHUNKS`，含义：用于表示c、2、s、request、区块集合。
    public static final byte C2S_REQUEST_CHUNKS = 4; // meaning

    // 中文标注（字段）：`S2C_WELCOME`，含义：用于表示s、2、c、welcome。
    public static final byte S2C_WELCOME = 101; // meaning
    // 中文标注（字段）：`S2C_CHUNK_DATA`，含义：用于表示s、2、c、区块、数据。
    public static final byte S2C_CHUNK_DATA = 102; // meaning
    // 中文标注（字段）：`S2C_BLOCK_UPDATE`，含义：用于表示s、2、c、方块、更新。
    public static final byte S2C_BLOCK_UPDATE = 103; // meaning

    // 中文标注（构造方法）：`Protocol`，参数：无；用途：初始化`Protocol`实例。
    private Protocol() {
    }
}
