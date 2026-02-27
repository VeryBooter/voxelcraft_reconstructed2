package dev.voxelcraft.core.block;

public record BlockId(short raw) {
    public static final int MAX_UNSIGNED = 0xFFFF;

    public static BlockId ofUnsigned(int value) {
        if (value < 0 || value > MAX_UNSIGNED) {
            throw new IllegalArgumentException("BlockId out of range [0,65535]: " + value);
        }
        return new BlockId((short) value);
    }

    public int asUnsignedInt() {
        return Short.toUnsignedInt(raw);
    }
}
