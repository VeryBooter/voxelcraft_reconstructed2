package dev.voxelcraft.core.block;

import dev.voxelcraft.core.block.data.BlockCsvDataLoader;
import dev.voxelcraft.core.registry.Registries;
import dev.voxelcraft.core.util.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class Blocks {
    private static boolean bootstrapped;
    private static final BlockDefinitionRegistry DEFINITIONS = new BlockDefinitionRegistry();
    private static final Map<String, Block> BLOCKS_BY_KEY = new LinkedHashMap<>();
    private static Map<String, GrowthRuleSchema> growthRuleSchemas =
        Map.of("NONE", new GrowthRuleSchema("NONE", Map.of()));
    private static Block[] blocksByNumericId = new Block[0];

    public static Block AIR;
    public static Block STONE;
    public static Block DIRT;
    public static Block GRASS;
    public static Block SAND;
    public static Block WOOD;
    public static Block LEAVES;

    private Blocks() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }

        int nextNumericId = registerBuiltins();

        Optional<BlockCsvDataLoader.LoadResult> loaded = BlockCsvDataLoader.loadFromConfiguredPaths(nextNumericId);
        if (loaded.isPresent()) {
            BlockCsvDataLoader.LoadResult result = loaded.get();
            for (BlockDef def : result.blockDefs()) {
                registerBlock(def);
            }
            growthRuleSchemas = result.growthSchemas();
            System.out.printf(
                "[block-csv] loaded blocks=%d meshProfiles=%d growthSchemas=%d enabledGrowth=%d validationErrors=%d path=%s%n",
                result.blockDefs().size(),
                result.meshProfiles().size(),
                result.growthSchemas().size(),
                result.enabledGrowthRuleCount(),
                result.growthValidationErrors(),
                result.blocksPath()
            );
        } else {
            System.out.println("[block-csv] csv files not found, using builtin block set only.");
        }

        finalizeAliases();
        buildNumericIndex();
        Registries.BLOCKS.freeze();
        bootstrapped = true;
    }

    public static Block byIdOrAir(String id) {
        Objects.requireNonNull(id, "id");
        bootstrap();
        Block found = Registries.BLOCKS.entries().get(ResourceLocation.of(id));
        return found == null ? AIR : found;
    }

    public static Block byBlockKeyOrAir(String blockKey) {
        Objects.requireNonNull(blockKey, "blockKey");
        bootstrap();
        Block found = BLOCKS_BY_KEY.get(BlockDef.normalizeKey(blockKey));
        return found == null ? AIR : found;
    }

    public static Block byNumericIdOrAir(int numericId) {
        bootstrap();
        if (numericId < 0 || numericId >= blocksByNumericId.length) {
            return AIR;
        }
        Block found = blocksByNumericId[numericId];
        return found == null ? AIR : found;
    }

    public static BlockDefinitionRegistry definitions() {
        bootstrap();
        return DEFINITIONS;
    }

    public static Map<String, GrowthRuleSchema> growthRuleSchemas() {
        bootstrap();
        return growthRuleSchemas;
    }

    private static int registerBuiltins() {
        int nextId = 0;
        nextId = registerBuiltin(nextId, "air", false, false, BlockDef.OcclusionMode.NONE, BlockDef.MeshProfile.CUBE,
            BlockDef.CollisionKind.NONE, BlockDef.RenderBucket.CUTOUT, "none", "none", false, false, "none");
        nextId = registerBuiltin(nextId, "stone", true, true, BlockDef.OcclusionMode.FULL, BlockDef.MeshProfile.CUBE,
            BlockDef.CollisionKind.FULL, BlockDef.RenderBucket.OPAQUE, "hard", "stone", false, false, "none");
        nextId = registerBuiltin(nextId, "dirt", true, true, BlockDef.OcclusionMode.FULL, BlockDef.MeshProfile.CUBE,
            BlockDef.CollisionKind.FULL, BlockDef.RenderBucket.OPAQUE, "soft", "grit", false, false, "none");
        nextId = registerBuiltin(nextId, "grass", true, true, BlockDef.OcclusionMode.FULL, BlockDef.MeshProfile.CUBE,
            BlockDef.CollisionKind.FULL, BlockDef.RenderBucket.OPAQUE, "soft", "grit", false, false, "grass");
        nextId = registerBuiltin(nextId, "sand", true, true, BlockDef.OcclusionMode.FULL, BlockDef.MeshProfile.CUBE,
            BlockDef.CollisionKind.FULL, BlockDef.RenderBucket.OPAQUE, "soft", "sand", false, false, "none");
        nextId = registerBuiltin(nextId, "wood", true, true, BlockDef.OcclusionMode.FULL, BlockDef.MeshProfile.CUBE,
            BlockDef.CollisionKind.FULL, BlockDef.RenderBucket.OPAQUE, "hard", "wood", true, false, "none");
        nextId = registerBuiltin(nextId, "leaves", true, false, BlockDef.OcclusionMode.PARTIAL, BlockDef.MeshProfile.CUBE,
            BlockDef.CollisionKind.FULL, BlockDef.RenderBucket.CUTOUT, "soft", "leaf", true, false, "foliage");
        return nextId;
    }

    private static int registerBuiltin(
        int numericId,
        String key,
        boolean solid,
        boolean occludes,
        BlockDef.OcclusionMode occlusionMode,
        BlockDef.MeshProfile meshProfile,
        BlockDef.CollisionKind collisionKind,
        BlockDef.RenderBucket renderBucket,
        String hardnessClass,
        String soundClass,
        boolean flammable,
        boolean requiresWater,
        String tintMode
    ) {
        BlockDef def = new BlockDef(
            BlockId.ofUnsigned(numericId),
            key,
            key,
            "builtin",
            key,
            "default",
            "block",
            renderBucket,
            renderBucket == BlockDef.RenderBucket.CUTOUT ? BlockDef.AlphaMode.CUTOUT : BlockDef.AlphaMode.OPAQUE,
            renderBucket == BlockDef.RenderBucket.TRANSLUCENT,
            occludes,
            occlusionMode,
            meshProfile,
            collisionKind,
            "0,0,0",
            "1,1,1",
            BlockDef.ATTACH_NONE,
            false,
            meshProfile == BlockDef.MeshProfile.CROSS,
            hardnessClass,
            soundClass,
            flammable,
            requiresWater,
            tintMode,
            "NONE",
            "",
            Map.of()
        );
        Block block = new Block(resourceIdForKey(key), solid, def.id(), def);
        registerBlock(def, block);
        return numericId + 1;
    }

    private static void registerBlock(BlockDef def) {
        Block block = new Block(resourceIdForKey(def.key()), def);
        registerBlock(def, block);
    }

    private static void registerBlock(BlockDef def, Block block) {
        ResourceLocation id = block.id();
        if (Registries.BLOCKS.entries().containsKey(id)) {
            return;
        }
        DEFINITIONS.register(def);
        Registries.BLOCKS.register(id, block);
        BLOCKS_BY_KEY.put(BlockDef.normalizeKey(def.key()), block);
    }

    private static ResourceLocation resourceIdForKey(String blockKey) {
        String normalizedKey = BlockDef.normalizeKey(blockKey);
        if (normalizedKey.contains(":")) {
            return ResourceLocation.of(normalizedKey);
        }
        return ResourceLocation.of("voxelcraft:" + normalizedKey);
    }

    private static void finalizeAliases() {
        AIR = BLOCKS_BY_KEY.getOrDefault("air", AIR);
        STONE = BLOCKS_BY_KEY.getOrDefault("stone", AIR);
        DIRT = BLOCKS_BY_KEY.getOrDefault("dirt", STONE);
        GRASS = BLOCKS_BY_KEY.getOrDefault("grass", DIRT);
        SAND = BLOCKS_BY_KEY.getOrDefault("sand", DIRT);
        WOOD = BLOCKS_BY_KEY.getOrDefault("wood", STONE);
        LEAVES = BLOCKS_BY_KEY.getOrDefault("leaves", WOOD);
    }

    private static void buildNumericIndex() {
        int maxId = 0;
        for (BlockDef def : DEFINITIONS.all()) {
            maxId = Math.max(maxId, def.id().asUnsignedInt());
        }
        Block[] table = new Block[maxId + 1];
        for (Map.Entry<String, Block> entry : BLOCKS_BY_KEY.entrySet()) {
            Block block = entry.getValue();
            int numericId = block.blockId().asUnsignedInt();
            if (numericId >= 0 && numericId < table.length && table[numericId] == null) {
                table[numericId] = block;
            }
        }
        blocksByNumericId = table;
    }
}
