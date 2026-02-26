package dev.voxelcraft.core.world.gen;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.Section;
import dev.voxelcraft.core.world.World;
/**
 * 中文说明：世界生成器实现：生成地形起伏、地表层次与树木等结构。
 */

// 中文标注（类）：`FlatWorldGenerator`，职责：封装flat、世界、生成器相关逻辑。
public final class FlatWorldGenerator implements WorldGenerator {
    // 中文标注（字段）：`BASE_SURFACE_Y`，含义：用于表示base、surface、Y坐标。
    private static final int BASE_SURFACE_Y = 5;
    // 中文标注（字段）：`MIN_SURFACE_Y`，含义：用于表示最小、surface、Y坐标。
    private static final int MIN_SURFACE_Y = 1;
    // 中文标注（字段）：`MAX_SURFACE_Y`，含义：用于表示最大、surface、Y坐标。
    private static final int MAX_SURFACE_Y = 24;
    // 中文标注（字段）：`SEA_LEVEL`，含义：用于表示sea、级别。
    private static final int SEA_LEVEL = 4;

    // 中文标注（字段）：`perlinNoise`，含义：用于表示柏林、噪声。
    private final PerlinNoise perlinNoise;
    // 中文标注（字段）：`seed`，含义：用于表示seed。
    private final long seed;

    // 中文标注（构造方法）：`FlatWorldGenerator`，参数：无；用途：初始化`FlatWorldGenerator`实例。
    public FlatWorldGenerator() {
        this(0x5EEDL);
    }

    // 中文标注（构造方法）：`FlatWorldGenerator`，参数：seed；用途：初始化`FlatWorldGenerator`实例。
    // 中文标注（参数）：`seed`，含义：用于表示seed。
    public FlatWorldGenerator(long seed) {
        this.seed = seed;
        this.perlinNoise = new PerlinNoise(seed);
    }

    // 中文标注（方法）：`generate`，参数：chunk；用途：执行generate相关逻辑。
    @Override
    // 中文标注（参数）：`chunk`，含义：用于表示区块。
    public void generate(Chunk chunk) {
        // 中文标注（局部变量）：`chunkBaseX`，含义：用于表示区块、base、X坐标。
        int chunkBaseX = chunk.pos().x() * Section.SIZE;
        // 中文标注（局部变量）：`chunkBaseZ`，含义：用于表示区块、base、Z坐标。
        int chunkBaseZ = chunk.pos().z() * Section.SIZE;

        // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
        for (int localX = 0; localX < Section.SIZE; localX++) {
            // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
            for (int localZ = 0; localZ < Section.SIZE; localZ++) {
                // 中文标注（局部变量）：`worldX`，含义：用于表示世界、X坐标。
                int worldX = chunkBaseX + localX;
                // 中文标注（局部变量）：`worldZ`，含义：用于表示世界、Z坐标。
                int worldZ = chunkBaseZ + localZ;
                // 中文标注（局部变量）：`surfaceY`，含义：用于表示surface、Y坐标。
                int surfaceY = surfaceHeight(worldX, worldZ);
                // 中文标注（局部变量）：`surfaceBlock`，含义：用于表示surface、方块。
                Block surfaceBlock = chooseSurfaceBlock(surfaceY, worldX, worldZ);
                // 中文标注（局部变量）：`columnStartY`，含义：用于表示column、开始、Y坐标。
                int columnStartY = Math.max(World.DEFAULT_SOLID_BELOW_Y, World.MIN_Y);
                // 中文标注（局部变量）：`y`，含义：用于表示Y坐标。
                for (int y = columnStartY; y <= surfaceY; y++) {
                    // 中文标注（局部变量）：`depthFromSurface`，含义：用于表示深度、from、surface。
                    int depthFromSurface = surfaceY - y;
                    chunk.setBlock(localX, y, localZ, blockForDepth(depthFromSurface, surfaceBlock));
                }

                if (canSpawnTree(surfaceY, worldX, worldZ)) {
                    placeTree(chunk, chunkBaseX, chunkBaseZ, worldX, surfaceY + 1, worldZ);
                }
            }
        }
    }

    // 中文标注（方法）：`surfaceHeight`，参数：worldX、worldZ；用途：执行surface、高度相关逻辑。
    // 中文标注（参数）：`worldX`，含义：用于表示世界、X坐标。
    // 中文标注（参数）：`worldZ`，含义：用于表示世界、Z坐标。
    private int surfaceHeight(int worldX, int worldZ) {
        // 固定核平滑：完全按世界坐标采样 raw 高度，保证 deterministic 且跨 chunk 无缝。
        int center = surfaceHeightRaw(worldX, worldZ);
        int north = surfaceHeightRaw(worldX, worldZ - 1);
        int south = surfaceHeightRaw(worldX, worldZ + 1);
        int west = surfaceHeightRaw(worldX - 1, worldZ);
        int east = surfaceHeightRaw(worldX + 1, worldZ);
        int northWest = surfaceHeightRaw(worldX - 1, worldZ - 1);
        int northEast = surfaceHeightRaw(worldX + 1, worldZ - 1);
        int southWest = surfaceHeightRaw(worldX - 1, worldZ + 1);
        int southEast = surfaceHeightRaw(worldX + 1, worldZ + 1);

        double smoothHeight = (
            (center * 4.0)
                + ((north + south + west + east) * 2.0)
                + (northWest + northEast + southWest + southEast)
        ) / 16.0;
        return clamp((int) Math.round(smoothHeight), MIN_SURFACE_Y, MAX_SURFACE_Y);
    }

    // 中文标注（方法）：`surfaceHeightRaw`，参数：worldX、worldZ；用途：执行surface、高度、raw相关逻辑。
    // 中文标注（参数）：`worldX`，含义：用于表示世界、X坐标。
    // 中文标注（参数）：`worldZ`，含义：用于表示世界、Z坐标。
    private int surfaceHeightRaw(int worldX, int worldZ) {
        // 更低频的大尺度起伏，减少“碎坡”。
        double continental = perlinNoise.fbm2d(worldX * 0.0022, worldZ * 0.0022, 4, 2.0, 0.40);
        // 弱 hills 用于打破过于单调的平面，但幅度远小于主体。
        double hills = perlinNoise.fbm2d(worldX * 0.0075, worldZ * 0.0075, 2, 2.0, 0.35);

        // 非线性压缩：降低极端值出现概率，让坡度更渐进。
        double combined = continental * 0.95 + hills * 0.28;
        double shaped = Math.copySign(Math.pow(Math.abs(combined), 0.90), combined);

        int offset = (int) Math.round(shaped * 9.0);
        return clamp(BASE_SURFACE_Y + offset, MIN_SURFACE_Y, MAX_SURFACE_Y);
    }

    // 中文标注（方法）：`blockForDepth`，参数：depthFromSurface、surfaceBlock；用途：执行方块、for、深度相关逻辑。
    // 中文标注（参数）：`depthFromSurface`，含义：用于表示深度、from、surface。
    // 中文标注（参数）：`surfaceBlock`，含义：用于表示surface、方块。
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

    // 中文标注（方法）：`chooseSurfaceBlock`，参数：surfaceY、worldX、worldZ；用途：执行choose、surface、方块相关逻辑。
    // 中文标注（参数）：`surfaceY`，含义：用于表示surface、Y坐标。
    // 中文标注（参数）：`worldX`，含义：用于表示世界、X坐标。
    // 中文标注（参数）：`worldZ`，含义：用于表示世界、Z坐标。
    private Block chooseSurfaceBlock(int surfaceY, int worldX, int worldZ) {
        if (surfaceY <= SEA_LEVEL) {
            return Blocks.SAND;
        }
        // 中文标注（局部变量）：`stoneMask`，含义：用于表示石头、掩码。
        double stoneMask = perlinNoise.fbm2d(worldX * 0.045, worldZ * 0.045, 2, 2.0, 0.5);
        if (surfaceY >= 14 && stoneMask > 0.28) {
            return Blocks.STONE;
        }
        return Blocks.GRASS;
    }

    // 中文标注（方法）：`canSpawnTree`，参数：surfaceY、worldX、worldZ；用途：判断spawn、tree是否满足条件。
    // 中文标注（参数）：`surfaceY`，含义：用于表示surface、Y坐标。
    // 中文标注（参数）：`worldX`，含义：用于表示世界、X坐标。
    // 中文标注（参数）：`worldZ`，含义：用于表示世界、Z坐标。
    private boolean canSpawnTree(int surfaceY, int worldX, int worldZ) {
        if (surfaceY < SEA_LEVEL + 1 || surfaceY > 16) {
            return false;
        }

        // 中文标注（局部变量）：`gridMask`，含义：用于表示grid、掩码。
        int gridMask = 7;
        if ((Math.floorMod(worldX, 16) & gridMask) != 3 || (Math.floorMod(worldZ, 16) & gridMask) != 5) {
            return false;
        }

        // 中文标注（局部变量）：`treeDensity`，含义：用于表示tree、density。
        double treeDensity = perlinNoise.fbm2d(worldX * 0.065, worldZ * 0.065, 3, 2.0, 0.55);
        return treeDensity > 0.2;
    }

    // 中文标注（方法）：`placeTree`，参数：chunk、chunkBaseX、chunkBaseZ、worldX、baseY、worldZ；用途：执行place、tree相关逻辑。
    // 中文标注（参数）：`chunk`，含义：用于表示区块。
    // 中文标注（参数）：`chunkBaseX`，含义：用于表示区块、base、X坐标。
    // 中文标注（参数）：`chunkBaseZ`，含义：用于表示区块、base、Z坐标。
    // 中文标注（参数）：`worldX`，含义：用于表示世界、X坐标。
    // 中文标注（参数）：`baseY`，含义：用于表示base、Y坐标。
    // 中文标注（参数）：`worldZ`，含义：用于表示世界、Z坐标。
    private void placeTree(Chunk chunk, int chunkBaseX, int chunkBaseZ, int worldX, int baseY, int worldZ) {
        // 中文标注（局部变量）：`trunkHeight`，含义：用于表示trunk、高度。
        int trunkHeight = 3 + (int) (Math.floorMod(mixSeed(worldX, worldZ), 3));
        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = 0; i < trunkHeight; i++) {
            setIfInsideChunk(chunk, chunkBaseX, chunkBaseZ, worldX, baseY + i, worldZ, Blocks.WOOD);
        }

        // 中文标注（局部变量）：`leafBaseY`，含义：用于表示leaf、base、Y坐标。
        int leafBaseY = baseY + trunkHeight - 2;
        // 中文标注（局部变量）：`dy`，含义：用于表示dy。
        for (int dy = 0; dy <= 3; dy++) {
            // 中文标注（局部变量）：`radius`，含义：用于表示radius。
            int radius = dy == 3 ? 1 : 2;
            // 中文标注（局部变量）：`dx`，含义：用于表示dx。
            for (int dx = -radius; dx <= radius; dx++) {
                // 中文标注（局部变量）：`dz`，含义：用于表示dz。
                for (int dz = -radius; dz <= radius; dz++) {
                    // 中文标注（局部变量）：`manhattan`，含义：用于表示manhattan。
                    int manhattan = Math.abs(dx) + Math.abs(dz);
                    if (manhattan > radius + 1) {
                        continue;
                    }
                    // 中文标注（局部变量）：`leafX`，含义：用于表示leaf、X坐标。
                    int leafX = worldX + dx;
                    // 中文标注（局部变量）：`leafY`，含义：用于表示leaf、Y坐标。
                    int leafY = leafBaseY + dy;
                    // 中文标注（局部变量）：`leafZ`，含义：用于表示leaf、Z坐标。
                    int leafZ = worldZ + dz;
                    if (leafY <= baseY + trunkHeight - 1) {
                        continue;
                    }
                    setIfInsideChunk(chunk, chunkBaseX, chunkBaseZ, leafX, leafY, leafZ, Blocks.LEAVES);
                }
            }
        }
    }

    // 中文标注（方法）：`setIfInsideChunk`，参数：chunk、chunkBaseX、chunkBaseZ、worldX、y、worldZ、block；用途：设置、写入或注册if、inside、区块。
    private static void setIfInsideChunk(
        // 中文标注（参数）：`chunk`，含义：用于表示区块。
        Chunk chunk,
        // 中文标注（参数）：`chunkBaseX`，含义：用于表示区块、base、X坐标。
        int chunkBaseX,
        // 中文标注（参数）：`chunkBaseZ`，含义：用于表示区块、base、Z坐标。
        int chunkBaseZ,
        // 中文标注（参数）：`worldX`，含义：用于表示世界、X坐标。
        int worldX,
        // 中文标注（参数）：`y`，含义：用于表示Y坐标。
        int y,
        // 中文标注（参数）：`worldZ`，含义：用于表示世界、Z坐标。
        int worldZ,
        // 中文标注（参数）：`block`，含义：用于表示方块。
        Block block
    ) {
        // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
        int localX = worldX - chunkBaseX;
        // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
        int localZ = worldZ - chunkBaseZ;
        if (localX < 0 || localX >= Section.SIZE || localZ < 0 || localZ >= Section.SIZE) {
            return;
        }
        chunk.setBlock(localX, y, localZ, block);
    }

    // 中文标注（方法）：`mixSeed`，参数：x、z；用途：执行mix、seed相关逻辑。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    private long mixSeed(int x, int z) {
        // 中文标注（局部变量）：`value`，含义：用于表示值。
        long value = seed;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= (value >>> 33);
        return value;
    }

    // 中文标注（方法）：`clamp`，参数：value、min、max；用途：执行clamp相关逻辑。
    // 中文标注（参数）：`value`，含义：用于表示值。
    // 中文标注（参数）：`min`，含义：用于表示最小。
    // 中文标注（参数）：`max`，含义：用于表示最大。
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
