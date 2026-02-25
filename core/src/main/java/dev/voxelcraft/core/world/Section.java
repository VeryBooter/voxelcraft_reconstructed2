package dev.voxelcraft.core.world;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import java.util.Arrays;
/**
 * 中文说明：区块纵向分段（16x16x16）：提供方块存储与局部坐标访问。
 */

// 中文标注（类）：`Section`，职责：封装分段相关逻辑。
public final class Section {
    // 中文标注（字段）：`SIZE`，含义：用于表示大小。
    public static final int SIZE = 16;
    // 中文标注（字段）：`blocks`，含义：用于表示方块集合。
    private Block[] blocks;
    // 中文标注（字段）：`uniformBlock`，含义：用于表示uniform、方块。
    private Block uniformBlock;

    // 中文标注（方法）：`getBlock`，参数：x、y、z；用途：获取或读取方块。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    public Block getBlock(int x, int y, int z) {
        if (blocks == null) {
            return blockOrAir(uniformBlock);
        }
        return blockOrAir(blocks[index(x, y, z)]);
    }

    // 中文标注（方法）：`setBlock`，参数：x、y、z、block；用途：设置、写入或注册方块。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    public void setBlock(int x, int y, int z, Block block) {
        // 中文标注（局部变量）：`value`，含义：用于表示值。
        Block value = blockOrNull(block);
        // 中文标注（局部变量）：`blockIndex`，含义：用于表示方块、索引。
        int blockIndex = index(x, y, z);

        if (blocks == null) {
            // 中文标注（局部变量）：`existing`，含义：用于表示existing。
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

    // 中文标注（方法）：`fill`，参数：block；用途：执行fill相关逻辑。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    public void fill(Block block) {
        uniformBlock = blockOrNull(block);
        blocks = null;
    }

    // 中文标注（方法）：`isUniform`，参数：无；用途：判断uniform是否满足条件。
    public boolean isUniform() {
        return blocks == null;
    }

    // 中文标注（方法）：`uniformBlock`，参数：无；用途：执行uniform、方块相关逻辑。
    public Block uniformBlock() {
        return blockOrAir(uniformBlock);
    }

    // 中文标注（方法）：`index`，参数：x、y、z；用途：执行索引相关逻辑。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    private static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    // 中文标注（方法）：`blockOrAir`，参数：block；用途：执行方块、or、空气相关逻辑。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    private static Block blockOrAir(Block block) {
        return block == null ? Blocks.AIR : block;
    }

    // 中文标注（方法）：`blockOrNull`，参数：block；用途：执行方块、or、null相关逻辑。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    private static Block blockOrNull(Block block) {
        return block == Blocks.AIR ? null : block;
    }
}