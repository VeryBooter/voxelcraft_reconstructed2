package dev.voxelcraft.core.world;

import dev.voxelcraft.core.block.Blocks;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorldGenerationTest {
    @Test
    void generatedColumnHasExpectedSurfaceLayers() {
        World world = new World(12345L);
        int surfaceY = findSurfaceY(world, 0, 0);
        var top = world.getBlock(new BlockPos(0, surfaceY, 0));
        var below = world.getBlock(new BlockPos(0, surfaceY - 1, 0));

        Assertions.assertTrue(surfaceY >= 1);
        Assertions.assertNotEquals(Blocks.AIR, top);
        Assertions.assertNotEquals(Blocks.AIR, below);
        Assertions.assertEquals(Blocks.AIR, world.getBlock(new BlockPos(0, surfaceY + 1, 0)));
    }

    @Test
    void terrainHasHeightVariationAcrossColumns() {
        World world = new World(12345L);
        int[][] samples = {
            {0, 0},
            {32, 0},
            {64, 0},
            {0, 32},
            {0, 64},
            {48, 48}
        };

        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        for (int[] sample : samples) {
            int surfaceY = findSurfaceY(world, sample[0], sample[1]);
            minSurface = Math.min(minSurface, surfaceY);
            maxSurface = Math.max(maxSurface, surfaceY);
        }

        Assertions.assertTrue(maxSurface > minSurface);
    }

    @Test
    void outOfBoundsYReturnsAir() {
        World world = new World(12345L);
        Assertions.assertEquals(Blocks.AIR, world.getBlock(new BlockPos(0, World.MIN_Y - 1, 0)));
        Assertions.assertEquals(Blocks.AIR, world.getBlock(new BlockPos(0, World.MAX_Y + 1, 0)));
    }

    private static int findSurfaceY(World world, int x, int z) {
        for (int y = 96; y >= World.MIN_Y; y--) {
            if (world.getBlock(new BlockPos(x, y, z)) != Blocks.AIR) {
                return y;
            }
        }
        return World.MIN_Y - 1;
    }
}
