package dev.voxelcraft.core.world;

import dev.voxelcraft.core.block.Blocks;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorldMutationTest {
    @Test
    void setBlockUpdatesStateAndVersion() {
        World world = new World(12345L);
        BlockPos pos = new BlockPos(2, 10, -3);

        long beforeVersion = world.blockUpdateVersion();
        boolean changed = world.setBlock(pos, Blocks.STONE);

        Assertions.assertTrue(changed);
        Assertions.assertEquals(Blocks.STONE, world.getBlock(pos));
        Assertions.assertEquals(beforeVersion + 1, world.blockUpdateVersion());
    }

    @Test
    void settingSameBlockDoesNotBumpVersion() {
        World world = new World(12345L);
        int surfaceY = findSurfaceY(world, 0, 0);
        BlockPos pos = new BlockPos(0, surfaceY, 0);
        var existing = world.getBlock(pos);

        long beforeVersion = world.blockUpdateVersion();
        boolean changed = world.setBlock(pos, existing);

        Assertions.assertFalse(changed);
        Assertions.assertEquals(beforeVersion, world.blockUpdateVersion());
    }

    @Test
    void setBlockOutsideHeightIsIgnored() {
        World world = new World(12345L);

        Assertions.assertFalse(world.setBlock(new BlockPos(0, World.MIN_Y - 1, 0), Blocks.STONE));
        Assertions.assertFalse(world.setBlock(new BlockPos(0, World.MAX_Y + 1, 0), Blocks.STONE));
    }

    @Test
    void negativeCoordinatesMapToCorrectChunks() {
        World world = new World(12345L);
        BlockPos pos = new BlockPos(-1, 12, -1);

        world.setBlock(pos, Blocks.DIRT);

        Assertions.assertEquals(Blocks.DIRT, world.getBlock(pos));
    }

    @Test
    void readingFarChunkTriggersGeneration() {
        World world = new World(12345L);
        long beforeVersion = world.blockUpdateVersion();

        BlockPos pos = new BlockPos(400, 0, 400);
        Assertions.assertNotEquals(Blocks.AIR, world.getBlock(pos));
        Assertions.assertTrue(world.blockUpdateVersion() > beforeVersion);
    }

    private static int findSurfaceY(World world, int x, int z) {
        for (int y = 96; y >= World.MIN_Y; y--) {
            if (world.getBlock(new BlockPos(x, y, z)) != Blocks.AIR) {
                return y;
            }
        }
        return 0;
    }
}
