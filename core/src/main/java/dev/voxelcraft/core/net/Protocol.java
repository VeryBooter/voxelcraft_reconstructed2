package dev.voxelcraft.core.net;

public final class Protocol {
    public static final int VERSION = 1;

    public static final byte C2S_HELLO = 1;
    public static final byte C2S_PLAYER_STATE = 2;
    public static final byte C2S_BLOCK_SET = 3;
    public static final byte C2S_REQUEST_CHUNKS = 4;

    public static final byte S2C_WELCOME = 101;
    public static final byte S2C_CHUNK_DATA = 102;
    public static final byte S2C_BLOCK_UPDATE = 103;

    private Protocol() {
    }
}
