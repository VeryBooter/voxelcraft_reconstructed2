package dev.voxelcraft.core.world;

import dev.voxelcraft.core.block.Blocks;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
/**
 * 中文说明：测试用例：用于验证 WorldGenerationTest 相关行为与回归约束。
 */

// 中文标注（类）：`WorldGenerationTest`，职责：用于测试与回归验证。
class WorldGenerationTest {
    // 中文标注（方法）：`generatedColumnHasExpectedSurfaceLayers`，参数：无；用途：执行generated、column、has、expected、surface、layers相关逻辑。
    @Test
    void generatedColumnHasExpectedSurfaceLayers() {
        // 中文标注（局部变量）：`world`，含义：用于表示世界。
        World world = new World(12345L); // meaning
        // 中文标注（局部变量）：`surfaceY`，含义：用于表示surface、Y坐标。
        int surfaceY = findSurfaceY(world, 0, 0); // meaning
        // 中文标注（局部变量）：`top`，含义：用于表示顶面。
        var top = world.getBlock(new BlockPos(0, surfaceY, 0)); // meaning
        // 中文标注（局部变量）：`below`，含义：用于表示below。
        var below = world.getBlock(new BlockPos(0, surfaceY - 1, 0)); // meaning

        Assertions.assertTrue(surfaceY >= 1);
        Assertions.assertNotEquals(Blocks.AIR, top);
        Assertions.assertNotEquals(Blocks.AIR, below);
        Assertions.assertEquals(Blocks.AIR, world.getBlock(new BlockPos(0, surfaceY + 1, 0)));
    }

    // 中文标注（方法）：`terrainHasHeightVariationAcrossColumns`，参数：无；用途：执行terrain、has、高度、variation、across、columns相关逻辑。
    @Test
    void terrainHasHeightVariationAcrossColumns() {
        // 中文标注（局部变量）：`world`，含义：用于表示世界。
        World world = new World(12345L); // meaning
        // 中文标注（局部变量）：`samples`，含义：用于表示samples。
        int[][] samples = {
            {0, 0},
            {32, 0},
            {64, 0},
            {0, 32},
            {0, 64},
            {48, 48}
        };

        // 中文标注（局部变量）：`minSurface`，含义：用于表示最小、surface。
        int minSurface = Integer.MAX_VALUE; // meaning
        // 中文标注（局部变量）：`maxSurface`，含义：用于表示最大、surface。
        int maxSurface = Integer.MIN_VALUE; // meaning
        // 中文标注（局部变量）：`sample`，含义：用于表示sample。
        for (int[] sample : samples) {
            // 中文标注（局部变量）：`surfaceY`，含义：用于表示surface、Y坐标。
            int surfaceY = findSurfaceY(world, sample[0], sample[1]); // meaning
            minSurface = Math.min(minSurface, surfaceY);
            maxSurface = Math.max(maxSurface, surfaceY);
        }

        Assertions.assertTrue(maxSurface > minSurface);
    }

    // 中文标注（方法）：`outOfBoundsYReturnsAir`，参数：无；用途：执行out、of、bounds、yreturns、空气相关逻辑。
    @Test
    void outOfBoundsYReturnsAir() {
        // 中文标注（局部变量）：`world`，含义：用于表示世界。
        World world = new World(12345L); // meaning
        Assertions.assertEquals(Blocks.AIR, world.getBlock(new BlockPos(0, World.MIN_Y - 1, 0)));
        Assertions.assertEquals(Blocks.AIR, world.getBlock(new BlockPos(0, World.MAX_Y + 1, 0)));
    }

    // 中文标注（方法）：`findSurfaceY`，参数：world、x、z；用途：获取或读取find、surface、Y坐标。
    // 中文标注（参数）：`world`，含义：用于表示世界。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    private static int findSurfaceY(World world, int x, int z) {
        // 中文标注（局部变量）：`y`，含义：用于表示Y坐标。
        for (int y = 96; y >= World.MIN_Y; y--) { // meaning
            if (world.getBlock(new BlockPos(x, y, z)) != Blocks.AIR) {
                return y;
            }
        }
        return World.MIN_Y - 1;
    }
}
