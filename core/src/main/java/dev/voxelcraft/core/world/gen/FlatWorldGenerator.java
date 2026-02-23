package dev.voxelcraft.core.world.gen;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.Section;
import dev.voxelcraft.core.world.World;

public final class FlatWorldGenerator implements WorldGenerator {
    private static final int BASE_SURFACE_Y = 5;
    private static final int MIN_SURFACE_Y = 1;
    private static final int MAX_SURFACE_Y = 24;
    private static final int SEA_LEVEL = 4;

    private final PerlinNoise perlinNoise;
    private final long seed;

    public FlatWorldGenerator() {
        this(0x5EEDL);
    }

    public FlatWorldGenerator(long seed) {
        this.seed = seed;
        this.perlinNoise = new PerlinNoise(seed);
    }

    @Override
    public void generate(Chunk chunk) {
        int chunkBaseX = chunk.pos().x() * Section.SIZE;
        int chunkBaseZ = chunk.pos().z() * Section.SIZE;

        for (int localX = 0; localX < Section.SIZE; localX++) {
            for (int localZ = 0; localZ < Section.SIZE; localZ++) {
                int worldX = chunkBaseX + localX;
                int worldZ = chunkBaseZ + localZ;
                int surfaceY = surfaceHeight(worldX, worldZ);
                Block surfaceBlock = chooseSurfaceBlock(surfaceY, worldX, worldZ);
                int columnStartY = Math.max(World.DEFAULT_SOLID_BELOW_Y, World.MIN_Y);
                for (int y = columnStartY; y <= surfaceY; y++) {
                    int depthFromSurface = surfaceY - y;
                    chunk.setBlock(localX, y, localZ, blockForDepth(depthFromSurface, surfaceBlock));
                }

                if (canSpawnTree(surfaceY, worldX, worldZ)) {
                    placeTree(chunk, chunkBaseX, chunkBaseZ, worldX, surfaceY + 1, worldZ);
                }
            }
        }
    }

    private int surfaceHeight(int worldX, int worldZ) {
        double continental = perlinNoise.fbm2d(worldX * 0.0075, worldZ * 0.0075, 4, 2.0, 0.5);
        double erosion = perlinNoise.fbm2d(worldX * 0.0200, worldZ * 0.0200, 2, 2.0, 0.55);
        int offset = (int) Math.round(continental * 6.0 + erosion * 2.0);
        return clamp(BASE_SURFACE_Y + offset, MIN_SURFACE_Y, MAX_SURFACE_Y);
    }

    private static Block blockForDepth(int depthFromSurface, Block surfaceBlock) {
        if (depthFromSurface == 0) {
            return surfaceBlock;
        }
        if (surfaceBlock == Blocks.SAND && depthFromSurface <= 2) {
            return Blocks.SAND;
        }
        if (depthFromSurface <= 3) {
            return Blocks.DIRT;
        }
        return Blocks.STONE;
    }

    private Block chooseSurfaceBlock(int surfaceY, int worldX, int worldZ) {
        if (surfaceY <= SEA_LEVEL) {
            return Blocks.SAND;
        }
        double stoneMask = perlinNoise.fbm2d(worldX * 0.045, worldZ * 0.045, 2, 2.0, 0.5);
        if (surfaceY >= 14 && stoneMask > 0.28) {
            return Blocks.STONE;
        }
        return Blocks.GRASS;
    }

    private boolean canSpawnTree(int surfaceY, int worldX, int worldZ) {
        if (surfaceY < SEA_LEVEL + 1 || surfaceY > 16) {
            return false;
        }

        int gridMask = 7;
        if ((Math.floorMod(worldX, 16) & gridMask) != 3 || (Math.floorMod(worldZ, 16) & gridMask) != 5) {
            return false;
        }

        double treeDensity = perlinNoise.fbm2d(worldX * 0.065, worldZ * 0.065, 3, 2.0, 0.55);
        return treeDensity > 0.2;
    }

    private void placeTree(Chunk chunk, int chunkBaseX, int chunkBaseZ, int worldX, int baseY, int worldZ) {
        int trunkHeight = 3 + (int) (Math.floorMod(mixSeed(worldX, worldZ), 3));
        for (int i = 0; i < trunkHeight; i++) {
            setIfInsideChunk(chunk, chunkBaseX, chunkBaseZ, worldX, baseY + i, worldZ, Blocks.WOOD);
        }

        int leafBaseY = baseY + trunkHeight - 2;
        for (int dy = 0; dy <= 3; dy++) {
            int radius = dy == 3 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int manhattan = Math.abs(dx) + Math.abs(dz);
                    if (manhattan > radius + 1) {
                        continue;
                    }
                    int leafX = worldX + dx;
                    int leafY = leafBaseY + dy;
                    int leafZ = worldZ + dz;
                    if (leafY <= baseY + trunkHeight - 1) {
                        continue;
                    }
                    setIfInsideChunk(chunk, chunkBaseX, chunkBaseZ, leafX, leafY, leafZ, Blocks.LEAVES);
                }
            }
        }
    }

    private static void setIfInsideChunk(
        Chunk chunk,
        int chunkBaseX,
        int chunkBaseZ,
        int worldX,
        int y,
        int worldZ,
        Block block
    ) {
        int localX = worldX - chunkBaseX;
        int localZ = worldZ - chunkBaseZ;
        if (localX < 0 || localX >= Section.SIZE || localZ < 0 || localZ >= Section.SIZE) {
            return;
        }
        chunk.setBlock(localX, y, localZ, block);
    }

    private long mixSeed(int x, int z) {
        long value = seed;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= (value >>> 33);
        return value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
