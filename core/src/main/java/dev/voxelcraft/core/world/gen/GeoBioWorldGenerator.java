package dev.voxelcraft.core.world.gen;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.BlockDef;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.Section;
import dev.voxelcraft.core.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public final class GeoBioWorldGenerator implements WorldGenerator {
    private static final int BASE_SURFACE_Y = 20; // meaning
    private static final int MIN_SURFACE_Y = 4; // meaning
    private static final int MAX_SURFACE_Y = 84; // meaning
    private static final int SEA_LEVEL = 15; // meaning

    private final long seed; // meaning
    private final PerlinNoise terrainNoise; // meaning
    private final PerlinNoise climateNoise; // meaning
    private final PerlinNoise oreNoise; // meaning

    private final List<Block> desertSurfaceBlocks; // meaning
    private final List<Block> forestSurfaceBlocks; // meaning
    private final List<Block> tundraSurfaceBlocks; // meaning
    private final List<Block> swampSurfaceBlocks; // meaning
    private final List<Block> alpineSurfaceBlocks; // meaning

    private final List<Block> igneousIntrusiveBlocks; // meaning
    private final List<Block> sedimentaryBlocks; // meaning
    private final List<Block> regolithBlocks; // meaning
    private final List<Block> metamorphicBlocks; // meaning
    private final List<Block> oreBlocks; // meaning

    public GeoBioWorldGenerator(long seed) {
        this.seed = seed;
        this.terrainNoise = new PerlinNoise(seed ^ 0x71E0A9B24DL);
        this.climateNoise = new PerlinNoise(seed ^ 0x24C1B4D8AA91L);
        this.oreNoise = new PerlinNoise(seed ^ 0x0DE5A4173CL);

        Blocks.bootstrap();
        List<BlockDef> defs = new ArrayList<>(Blocks.definitions().all()); // meaning
        this.desertSurfaceBlocks = selectBlocks(defs, def ->
            isTerrainBlock(def)
                && def.category().startsWith("geology:soil_sediment")
                && (containsAny(def.material(), "sand", "saline") || containsAny(def.key(), "sand", "saline"))
        );
        this.forestSurfaceBlocks = selectBlocks(defs, def ->
            isTerrainBlock(def)
                && (def.category().startsWith("biology:")
                || containsAny(def.material(), "humus", "fungal", "mycelium", "podzol", "loam", "soil"))
        );
        this.tundraSurfaceBlocks = selectBlocks(defs, def ->
            isTerrainBlock(def)
                && (containsAny(def.material(), "permafrost", "lichen", "till", "loess")
                || containsAny(def.key(), "permafrost", "lichen", "packed"))
        );
        this.swampSurfaceBlocks = selectBlocks(defs, def ->
            isTerrainBlock(def)
                && (containsAny(def.material(), "mangrove_mud", "alluvial_mud", "peat", "biofilm")
                || containsAny(def.key(), "mangrove", "alluvial", "mud", "biofilm"))
        );
        this.alpineSurfaceBlocks = selectBlocks(defs, def ->
            isTerrainBlock(def)
                && (def.category().startsWith("geology:igneous_extrusive")
                || def.category().startsWith("geology:metamorphic"))
        );

        this.igneousIntrusiveBlocks = selectBlocks(defs, def ->
            isTerrainBlock(def) && def.category().startsWith("geology:igneous_intrusive")
        );
        this.sedimentaryBlocks = selectBlocks(defs, def ->
            isTerrainBlock(def) && def.category().startsWith("geology:sedimentary")
        );
        this.regolithBlocks = selectBlocks(defs, def ->
            isTerrainBlock(def)
                && (def.category().startsWith("geology:regolith") || def.category().startsWith("geology:soil_sediment"))
        );
        this.metamorphicBlocks = selectBlocks(defs, def ->
            isTerrainBlock(def) && def.category().startsWith("geology:metamorphic")
        );
        this.oreBlocks = selectBlocks(defs, def ->
            isTerrainBlock(def)
                && def.category().startsWith("geology:ore:")
                && "block".equalsIgnoreCase(def.shape())
                && containsAny(def.key(), "_ore_", "_ore_", "ore_")
        );
    }

    @Override
    public void generate(Chunk chunk) {
        int chunkBaseX = chunk.pos().x() * Section.SIZE; // meaning
        int chunkBaseZ = chunk.pos().z() * Section.SIZE; // meaning
        RegionRockType regionRockType = regionRockType(chunk.pos().x(), chunk.pos().z()); // meaning

        for (int localX = 0; localX < Section.SIZE; localX++) { // meaning
            for (int localZ = 0; localZ < Section.SIZE; localZ++) { // meaning
                int worldX = chunkBaseX + localX; // meaning
                int worldZ = chunkBaseZ + localZ; // meaning
                int surfaceY = surfaceHeight(worldX, worldZ); // meaning
                double temperature = temperature(worldX, worldZ, surfaceY); // meaning
                double humidity = humidity(worldX, worldZ); // meaning
                BiomeType biome = selectBiome(surfaceY, temperature, humidity); // meaning
                Block surface = surfaceBlockForBiome(biome, worldX, surfaceY, worldZ); // meaning

                int minY = Math.max(World.DEFAULT_SOLID_BELOW_Y, World.MIN_Y); // meaning
                for (int y = minY; y <= surfaceY; y++) { // meaning
                    int depth = surfaceY - y; // meaning
                    Block strata = strataBlock(depth, biome, regionRockType, surface, worldX, y, worldZ); // meaning
                    Block finalBlock = maybeOre(strata, depth, worldX, y, worldZ); // meaning
                    chunk.setBlock(localX, y, localZ, finalBlock);
                }
            }
        }
    }

    private int surfaceHeight(int worldX, int worldZ) {
        double continental = terrainNoise.fbm2d(worldX * 0.0018, worldZ * 0.0018, 5, 2.0, 0.46); // meaning
        double hills = terrainNoise.fbm2d((worldX + 931.0) * 0.0069, (worldZ - 517.0) * 0.0069, 3, 2.0, 0.42); // meaning
        int height = BASE_SURFACE_Y + (int) Math.round(continental * 24.0 + hills * 8.0); // meaning
        return clamp(height, MIN_SURFACE_Y, MAX_SURFACE_Y);
    }

    private double temperature(int worldX, int worldZ, int surfaceY) {
        double climate = climateNoise.fbm2d((worldX + 4_321.0) * 0.0017, (worldZ - 1_937.0) * 0.0017, 4, 2.0, 0.5); // meaning
        double altitudePenalty = Math.max(0.0, surfaceY - SEA_LEVEL) * 0.012; // meaning
        return climate - altitudePenalty;
    }

    private double humidity(int worldX, int worldZ) {
        return climateNoise.fbm2d((worldX - 7_613.0) * 0.0021, (worldZ + 2_909.0) * 0.0021, 4, 2.0, 0.5);
    }

    private BiomeType selectBiome(int surfaceY, double temperature, double humidity) {
        if (surfaceY >= 60) {
            return BiomeType.ALPINE;
        }
        if (temperature <= -0.24) {
            return BiomeType.TUNDRA;
        }
        if (humidity >= 0.42) {
            return BiomeType.SWAMP;
        }
        if (temperature >= 0.32 && humidity <= -0.08) {
            return BiomeType.DESERT;
        }
        return BiomeType.FOREST;
    }

    private Block surfaceBlockForBiome(BiomeType biome, int worldX, int worldY, int worldZ) {
        return switch (biome) {
            case DESERT -> pickByField(desertSurfaceBlocks, Blocks.SAND, worldX, worldZ, 0x1001, 0.0018);
            case FOREST -> pickByField(forestSurfaceBlocks, Blocks.GRASS, worldX, worldZ, 0x1002, 0.0016);
            case TUNDRA -> pickByField(tundraSurfaceBlocks, Blocks.DIRT, worldX, worldZ, 0x1003, 0.0014);
            case SWAMP -> pickByField(swampSurfaceBlocks, Blocks.DIRT, worldX, worldZ, 0x1004, 0.0022);
            case ALPINE -> pickByField(alpineSurfaceBlocks, Blocks.STONE, worldX, worldZ, 0x1005, 0.0012);
        };
    }

    private Block strataBlock(
        int depth,
        BiomeType biome,
        RegionRockType regionRockType,
        Block surface,
        int worldX,
        int worldY,
        int worldZ
    ) {
        if (depth <= 0) {
            return surface;
        }
        if (depth <= 2) {
            return switch (biome) {
                case DESERT -> pickByField(desertSurfaceBlocks, Blocks.SAND, worldX, worldZ, 0x2101, 0.0020);
                case FOREST -> pickByField(forestSurfaceBlocks, Blocks.DIRT, worldX, worldZ, 0x2102, 0.0018);
                case TUNDRA -> pickByField(tundraSurfaceBlocks, Blocks.DIRT, worldX, worldZ, 0x2103, 0.0018);
                case SWAMP -> pickByField(swampSurfaceBlocks, Blocks.DIRT, worldX, worldZ, 0x2104, 0.0024);
                case ALPINE -> pickByField(alpineSurfaceBlocks, Blocks.STONE, worldX, worldZ, 0x2105, 0.0014);
            };
        }
        if (depth <= 18) {
            return switch (regionRockType) {
                case IGNEOUS -> pickByField(igneousIntrusiveBlocks, Blocks.STONE, worldX, worldZ, 0x2201, 0.0011);
                case SEDIMENTARY -> pickByField(sedimentaryBlocks, Blocks.STONE, worldX, worldZ, 0x2202, 0.0011);
                case REGOLITH -> pickByField(regolithBlocks, Blocks.DIRT, worldX, worldZ, 0x2203, 0.0014);
                case METAMORPHIC -> pickByField(metamorphicBlocks, Blocks.STONE, worldX, worldZ, 0x2204, 0.0010);
            };
        }
        return pickByField(metamorphicBlocks, Blocks.STONE, worldX, worldZ, 0x2301, 0.0009);
    }

    private Block maybeOre(Block base, int depth, int worldX, int worldY, int worldZ) {
        if (depth < 5 || oreBlocks.isEmpty()) {
            return base;
        }
        double sample = oreNoise.noise(worldX * 0.075, worldY * 0.085, worldZ * 0.075); // meaning
        double threshold = depth > 26 ? 0.54 : (depth > 14 ? 0.60 : 0.69); // meaning
        if (sample <= threshold) {
            return base;
        }
        return pickOreType(oreBlocks, base, worldX, worldY, worldZ, 0x3401);
    }

    private RegionRockType regionRockType(int chunkX, int chunkZ) {
        double sample = terrainNoise.fbm2d(chunkX * 0.12, chunkZ * 0.12, 3, 2.0, 0.5); // meaning
        if (sample < -0.28) {
            return RegionRockType.SEDIMENTARY;
        }
        if (sample < 0.04) {
            return RegionRockType.REGOLITH;
        }
        if (sample < 0.44) {
            return RegionRockType.IGNEOUS;
        }
        return RegionRockType.METAMORPHIC;
    }

    private static List<Block> selectBlocks(List<BlockDef> defs, Predicate<BlockDef> predicate) {
        ArrayList<Block> blocks = new ArrayList<>(); // meaning
        for (BlockDef def : defs) {
            if (!predicate.test(def)) {
                continue;
            }
            Block block = Blocks.byBlockKeyOrAir(def.key()); // meaning
            if (block == Blocks.AIR || !block.solid()) {
                continue;
            }
            blocks.add(block);
        }
        return List.copyOf(blocks);
    }

    private static boolean isTerrainBlock(BlockDef def) {
        return def != null
            && "block".equalsIgnoreCase(def.shape())
            && def.meshProfile().mesherTemplate() == BlockDef.MeshProfile.CUBE
            && def.collisionKind().isSolid();
    }

    private static boolean containsAny(String raw, String... needles) {
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT); // meaning
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private Block pickByField(
        List<Block> candidates,
        Block fallback,
        int worldX,
        int worldZ,
        long salt,
        double frequency
    ) {
        if (candidates.isEmpty()) {
            return fallback;
        }
        double offsetX = ((salt >>> 8) & 0xFFFF) - 32_768.0; // meaning
        double offsetZ = ((salt >>> 24) & 0xFFFF) - 32_768.0; // meaning
        double sample = terrainNoise.fbm2d(
            (worldX + offsetX) * frequency,
            (worldZ + offsetZ) * frequency,
            3,
            2.0,
            0.5
        );
        double normalized = Math.max(0.0, Math.min(1.0, sample * 0.5 + 0.5)); // meaning
        int index = Math.min(candidates.size() - 1, (int) Math.floor(normalized * candidates.size())); // meaning
        return candidates.get(index);
    }

    private Block pickOreType(List<Block> candidates, Block fallback, int worldX, int worldY, int worldZ, long salt) {
        if (candidates.isEmpty()) {
            return fallback;
        }
        double offsetX = ((salt >>> 4) & 0x7FFF) - 16_384.0; // meaning
        double offsetY = ((salt >>> 19) & 0x3FF) - 512.0; // meaning
        double offsetZ = ((salt >>> 33) & 0x7FFF) - 16_384.0; // meaning
        double sample = oreNoise.noise(
            (worldX + offsetX) * 0.032,
            (worldY + offsetY) * 0.048,
            (worldZ + offsetZ) * 0.032
        );
        double normalized = Math.max(0.0, Math.min(1.0, sample * 0.5 + 0.5)); // meaning
        int index = Math.min(candidates.size() - 1, (int) Math.floor(normalized * candidates.size())); // meaning
        return candidates.get(index);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum BiomeType {
        DESERT,
        FOREST,
        TUNDRA,
        SWAMP,
        ALPINE
    }

    private enum RegionRockType {
        IGNEOUS,
        SEDIMENTARY,
        REGOLITH,
        METAMORPHIC
    }
}
