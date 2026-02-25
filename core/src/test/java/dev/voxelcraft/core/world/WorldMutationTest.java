package dev.voxelcraft.core.world;

import dev.voxelcraft.core.block.Blocks;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
/**
 * 中文说明：测试用例：用于验证 WorldMutationTest 相关行为与回归约束。
 */

// 中文标注（类）：`WorldMutationTest`，职责：用于测试与回归验证。
class WorldMutationTest {
    // 中文标注（方法）：`setBlockUpdatesStateAndVersion`，参数：无；用途：设置、写入或注册方块、updates、状态、and、版本。
    @Test
    void setBlockUpdatesStateAndVersion() {
        // 中文标注（局部变量）：`world`，含义：用于表示世界。
        World world = new World(12345L);
        // 中文标注（局部变量）：`pos`，含义：用于表示位置。
        BlockPos pos = new BlockPos(2, 10, -3);

        // 中文标注（局部变量）：`beforeVersion`，含义：用于表示before、版本。
        long beforeVersion = world.blockUpdateVersion();
        // 中文标注（局部变量）：`changed`，含义：用于表示changed。
        boolean changed = world.setBlock(pos, Blocks.STONE);

        Assertions.assertTrue(changed);
        Assertions.assertEquals(Blocks.STONE, world.getBlock(pos));
        Assertions.assertEquals(beforeVersion + 1, world.blockUpdateVersion());
    }

    // 中文标注（方法）：`settingSameBlockDoesNotBumpVersion`，参数：无；用途：设置、写入或注册setting、same、方块、does、not、bump、版本。
    @Test
    void settingSameBlockDoesNotBumpVersion() {
        // 中文标注（局部变量）：`world`，含义：用于表示世界。
        World world = new World(12345L);
        // 中文标注（局部变量）：`surfaceY`，含义：用于表示surface、Y坐标。
        int surfaceY = findSurfaceY(world, 0, 0);
        // 中文标注（局部变量）：`pos`，含义：用于表示位置。
        BlockPos pos = new BlockPos(0, surfaceY, 0);
        // 中文标注（局部变量）：`existing`，含义：用于表示existing。
        var existing = world.getBlock(pos);

        // 中文标注（局部变量）：`beforeVersion`，含义：用于表示before、版本。
        long beforeVersion = world.blockUpdateVersion();
        // 中文标注（局部变量）：`changed`，含义：用于表示changed。
        boolean changed = world.setBlock(pos, existing);

        Assertions.assertFalse(changed);
        Assertions.assertEquals(beforeVersion, world.blockUpdateVersion());
    }

    // 中文标注（方法）：`setBlockOutsideHeightIsIgnored`，参数：无；用途：设置、写入或注册方块、outside、高度、is、ignored。
    @Test
    void setBlockOutsideHeightIsIgnored() {
        // 中文标注（局部变量）：`world`，含义：用于表示世界。
        World world = new World(12345L);

        Assertions.assertFalse(world.setBlock(new BlockPos(0, World.MIN_Y - 1, 0), Blocks.STONE));
        Assertions.assertFalse(world.setBlock(new BlockPos(0, World.MAX_Y + 1, 0), Blocks.STONE));
    }

    // 中文标注（方法）：`negativeCoordinatesMapToCorrectChunks`，参数：无；用途：执行negative、coordinates、映射、to、correct、区块集合相关逻辑。
    @Test
    void negativeCoordinatesMapToCorrectChunks() {
        // 中文标注（局部变量）：`world`，含义：用于表示世界。
        World world = new World(12345L);
        // 中文标注（局部变量）：`pos`，含义：用于表示位置。
        BlockPos pos = new BlockPos(-1, 12, -1);

        world.setBlock(pos, Blocks.DIRT);

        Assertions.assertEquals(Blocks.DIRT, world.getBlock(pos));
    }

    // 中文标注（方法）：`readingFarChunkTriggersGeneration`，参数：无；用途：获取或读取reading、far、区块、triggers、generation。
    @Test
    void readingFarChunkTriggersGeneration() {
        // 中文标注（局部变量）：`world`，含义：用于表示世界。
        World world = new World(12345L);
        // 中文标注（局部变量）：`beforeVersion`，含义：用于表示before、版本。
        long beforeVersion = world.blockUpdateVersion();

        // 中文标注（局部变量）：`pos`，含义：用于表示位置。
        BlockPos pos = new BlockPos(400, 0, 400);
        Assertions.assertNotEquals(Blocks.AIR, world.getBlock(pos));
        Assertions.assertTrue(world.blockUpdateVersion() > beforeVersion);
    }

    // 中文标注（方法）：`findSurfaceY`，参数：world、x、z；用途：获取或读取find、surface、Y坐标。
    // 中文标注（参数）：`world`，含义：用于表示世界。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    private static int findSurfaceY(World world, int x, int z) {
        // 中文标注（局部变量）：`y`，含义：用于表示Y坐标。
        for (int y = 96; y >= World.MIN_Y; y--) {
            if (world.getBlock(new BlockPos(x, y, z)) != Blocks.AIR) {
                return y;
            }
        }
        return 0;
    }
}