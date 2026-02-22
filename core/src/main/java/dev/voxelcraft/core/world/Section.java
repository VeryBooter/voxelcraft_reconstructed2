package dev.voxelcraft.core.world;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import java.util.Arrays;

public final class Section {
    public static final int SIZE = 16;
    private Block[] blocks;
    private Block uniformBlock;

    public Block getBlock(int x, int y, int z) {
        if (blocks == null) {
            return blockOrAir(uniformBlock);
        }
        return blockOrAir(blocks[index(x, y, z)]);
    }

    public void setBlock(int x, int y, int z, Block block) {
        Block value = blockOrNull(block);
        int blockIndex = index(x, y, z);

        if (blocks == null) {
            Block existing = blockOrAir(uniformBlock);
            if (existing == blockOrAir(value)) {
                return;
            }
            blocks = new Block[SIZE * SIZE * SIZE];
            if (uniformBlock != null) {
                Arrays.fill(blocks, uniformBlock);
            }
            uniformBlock = null;
        }

        blocks[blockIndex] = value;
    }

    public void fill(Block block) {
        uniformBlock = blockOrNull(block);
        blocks = null;
    }

    public boolean isUniform() {
        return blocks == null;
    }

    public Block uniformBlock() {
        return blockOrAir(uniformBlock);
    }

    private static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    private static Block blockOrAir(Block block) {
        return block == null ? Blocks.AIR : block;
    }

    private static Block blockOrNull(Block block) {
        return block == Blocks.AIR ? null : block;
    }
}
