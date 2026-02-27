package dev.voxelcraft.core.block.data;

import dev.voxelcraft.core.block.BlockDef;
import dev.voxelcraft.core.block.BlockId;
import dev.voxelcraft.core.block.GrowthRuleSchema;
import dev.voxelcraft.core.block.MeshProfileDef;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BlockCsvDataLoader {
    private static final String DEFAULT_BLOCKS_FILE = "blocks_mesh_library_v2_ecohardcore.csv";
    private static final String DEFAULT_MESH_PROFILES_FILE = "mesh_profiles.csv";
    private static final String DEFAULT_GROWTH_RULES_FILE = "growth_rules.csv";

    private static final Path[] BLOCKS_FALLBACK_PATHS = {
        Paths.get(DEFAULT_BLOCKS_FILE),
        Paths.get("data", DEFAULT_BLOCKS_FILE),
        Paths.get("/Users/kevinli/Downloads/DownloadFromInternet/AtlasDownloadSection", DEFAULT_BLOCKS_FILE)
    };
    private static final Path[] MESH_PROFILE_FALLBACK_PATHS = {
        Paths.get(DEFAULT_MESH_PROFILES_FILE),
        Paths.get("data", DEFAULT_MESH_PROFILES_FILE),
        Paths.get("/Users/kevinli/Downloads/DownloadFromInternet/AtlasDownloadSection", DEFAULT_MESH_PROFILES_FILE)
    };
    private static final Path[] GROWTH_RULE_FALLBACK_PATHS = {
        Paths.get(DEFAULT_GROWTH_RULES_FILE),
        Paths.get("data", DEFAULT_GROWTH_RULES_FILE),
        Paths.get("/Users/kevinli/Downloads/DownloadFromInternet/AtlasDownloadSection", DEFAULT_GROWTH_RULES_FILE)
    };

    private BlockCsvDataLoader() {
    }

    public static Optional<LoadResult> loadFromConfiguredPaths(int startingBlockId) {
        Path blocksPath = resolvePath("vc.blocks.csv", "voxelcraft.blocks.csv", BLOCKS_FALLBACK_PATHS);
        if (blocksPath == null || !Files.isRegularFile(blocksPath)) {
            return Optional.empty();
        }

        Path meshProfilesPath = resolvePath("vc.meshProfiles.csv", "voxelcraft.meshProfiles.csv", MESH_PROFILE_FALLBACK_PATHS);
        Path growthRulesPath = resolvePath("vc.growthRules.csv", "voxelcraft.growthRules.csv", GROWTH_RULE_FALLBACK_PATHS);

        try {
            return Optional.of(load(blocksPath, meshProfilesPath, growthRulesPath, startingBlockId));
        } catch (IOException exception) {
            System.err.println("[block-csv] failed to load csv data: " + exception.getMessage());
            return Optional.empty();
        }
    }

    public static LoadResult load(Path blocksPath, Path meshProfilesPath, Path growthRulesPath, int startingBlockId)
        throws IOException {
        Map<String, MeshProfileDef> meshProfiles =
            meshProfilesPath != null && Files.isRegularFile(meshProfilesPath)
                ? loadMeshProfiles(meshProfilesPath)
                : Map.of();
        Map<String, GrowthRuleSchema> growthSchemas =
            growthRulesPath != null && Files.isRegularFile(growthRulesPath)
                ? loadGrowthRules(growthRulesPath)
                : Map.of("NONE", new GrowthRuleSchema("NONE", Map.of()));

        List<Map<String, String>> rows = readCsv(blocksPath);
        List<BlockDef> defs = new ArrayList<>(rows.size());
        int validationErrors = 0;
        int enabledGrowthRuleCount = 0;
        int nextId = startingBlockId;

        for (Map<String, String> row : rows) {
            String key = normalizeKey(row.get("block_id"));
            if (key.isEmpty()) {
                continue;
            }

            String meshProfileRaw = value(row, "mesh_profile");
            BlockDef.MeshProfile meshProfile = BlockDef.MeshProfile.from(meshProfileRaw, BlockDef.MeshProfile.CUBE);
            MeshProfileDef profileDefaults = meshProfiles.get(meshProfile.name());

            BlockDef.AlphaMode alphaMode = BlockDef.AlphaMode.from(
                value(row, "alpha_mode"),
                profileDefaults == null ? BlockDef.AlphaMode.OPAQUE : profileDefaults.defaultAlphaMode()
            );
            BlockDef.OcclusionMode occlusionMode = BlockDef.OcclusionMode.from(
                value(row, "occlusion_mode"),
                profileDefaults == null ? BlockDef.OcclusionMode.FULL : profileDefaults.defaultOcclusionMode()
            );
            BlockDef.CollisionKind collisionKind = BlockDef.CollisionKind.from(
                value(row, "collision_kind"),
                profileDefaults == null ? BlockDef.CollisionKind.FULL : profileDefaults.defaultCollisionKind()
            );

            String aabbMin = fallback(value(row, "aabb_min"), profileDefaults == null ? "0,0,0" : profileDefaults.defaultAabbMin());
            String aabbMax = fallback(value(row, "aabb_max"), profileDefaults == null ? "1,1,1" : profileDefaults.defaultAabbMax());

            int attachFacesMask = BlockDef.parseAttachFacesMask(
                fallback(value(row, "attach_faces"), profileDefaults == null ? "" : defaultAttachFacesText(profileDefaults.defaultAttachFacesMask()))
            );
            boolean requiresSupport = parseBoolean(
                fallback(value(row, "requires_support"), profileDefaults == null ? "false" : Boolean.toString(profileDefaults.defaultRequiresSupport())),
                false
            );
            boolean doubleSided = parseBoolean(
                fallback(value(row, "double_sided"), profileDefaults == null ? "false" : Boolean.toString(profileDefaults.defaultDoubleSided())),
                false
            );

            BlockDef.RenderBucket renderBucket = BlockDef.RenderBucket.from(value(row, "render_bucket"), defaultBucketFromAlpha(alphaMode));
            boolean needsSorting = parseBoolean(value(row, "needs_sorting"), renderBucket == BlockDef.RenderBucket.TRANSLUCENT);
            boolean occludes = parseBoolean(value(row, "occludes"), occlusionMode == BlockDef.OcclusionMode.FULL);

            String growthRuleId = normalizeRuleId(value(row, "growth_rule_id"));
            String growthParamsJson = value(row, "growth_params_json");
            Map<String, Object> growthParams = Map.of();
            boolean growthRuleEnabled = false;
            if (!growthRuleId.equals("NONE")) {
                growthRuleEnabled = true;
                if (!growthParamsJson.isEmpty()) {
                    try {
                        growthParams = SimpleJson.parseObject(growthParamsJson);
                    } catch (RuntimeException exception) {
                        validationErrors++;
                        System.err.printf("[block-csv] invalid growth_params_json block=%s rule=%s error=%s%n",
                            key, growthRuleId, exception.getMessage());
                        growthRuleId = "NONE";
                        growthParamsJson = "";
                        growthParams = Map.of();
                        growthRuleEnabled = false;
                    }
                }

                GrowthRuleSchema schema = growthSchemas.get(growthRuleId);
                if (growthRuleEnabled && schema != null && !schema.requiredFields().isEmpty()) {
                    Set<String> missing = new LinkedHashSet<>();
                    for (String required : schema.requiredFields()) {
                        if (!growthParams.containsKey(required)) {
                            missing.add(required);
                        }
                    }
                    if (!missing.isEmpty()) {
                        validationErrors++;
                        System.err.printf("[block-csv] growth params missing fields block=%s rule=%s missing=%s%n",
                            key, growthRuleId, missing);
                        growthRuleId = "NONE";
                        growthParamsJson = "";
                        growthParams = Map.of();
                        growthRuleEnabled = false;
                    }
                }
            }
            if (growthRuleEnabled) {
                enabledGrowthRuleCount++;
            }

            BlockDef def = new BlockDef(
                BlockId.ofUnsigned(nextId++),
                key,
                fallback(value(row, "display_name"), key),
                value(row, "category"),
                value(row, "material"),
                value(row, "variant"),
                value(row, "shape"),
                renderBucket,
                alphaMode,
                needsSorting,
                occludes,
                occlusionMode,
                meshProfile,
                collisionKind,
                aabbMin,
                aabbMax,
                attachFacesMask,
                requiresSupport,
                doubleSided,
                value(row, "hardness_class"),
                value(row, "sound_class"),
                parseBoolean(value(row, "flammable"), false),
                parseBoolean(value(row, "requires_water"), false),
                value(row, "tint_mode"),
                growthRuleId,
                growthParamsJson,
                growthParams
            );
            defs.add(def);
        }

        return new LoadResult(defs, meshProfiles, growthSchemas, validationErrors, enabledGrowthRuleCount,
            blocksPath, meshProfilesPath, growthRulesPath);
    }

    private static String defaultAttachFacesText(int mask) {
        if (mask == BlockDef.ATTACH_ANY) {
            return "any";
        }
        return "";
    }

    private static BlockDef.RenderBucket defaultBucketFromAlpha(BlockDef.AlphaMode alphaMode) {
        return switch (alphaMode) {
            case TRANSLUCENT -> BlockDef.RenderBucket.TRANSLUCENT;
            case CUTOUT, MASKED -> BlockDef.RenderBucket.CUTOUT;
            default -> BlockDef.RenderBucket.OPAQUE;
        };
    }

    private static String normalizeRuleId(String raw) {
        String normalized = value(raw).toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? "NONE" : normalized;
    }

    private static Map<String, MeshProfileDef> loadMeshProfiles(Path path) throws IOException {
        List<Map<String, String>> rows = readCsv(path);
        Map<String, MeshProfileDef> map = new HashMap<>();

        for (Map<String, String> row : rows) {
            BlockDef.MeshProfile meshProfile = BlockDef.MeshProfile.from(value(row, "mesh_profile"), BlockDef.MeshProfile.UNKNOWN);
            if (meshProfile == BlockDef.MeshProfile.UNKNOWN) {
                continue;
            }

            MeshProfileDef def = new MeshProfileDef(
                meshProfile,
                BlockDef.AlphaMode.from(value(row, "default_alpha_mode"), BlockDef.AlphaMode.OPAQUE),
                BlockDef.OcclusionMode.from(value(row, "default_occlusion_mode"), BlockDef.OcclusionMode.FULL),
                BlockDef.CollisionKind.from(value(row, "collision_kind"), BlockDef.CollisionKind.FULL),
                fallback(value(row, "aabb_min"), "0,0,0"),
                fallback(value(row, "aabb_max"), "1,1,1"),
                BlockDef.parseAttachFacesMask(value(row, "attach_faces")),
                parseBoolean(value(row, "requires_support"), false),
                parseBoolean(value(row, "double_sided"), false)
            );
            map.put(meshProfile.name(), def);
        }

        return Collections.unmodifiableMap(map);
    }

    private static Map<String, GrowthRuleSchema> loadGrowthRules(Path path) throws IOException {
        List<Map<String, String>> rows = readCsv(path);
        Map<String, GrowthRuleSchema> map = new LinkedHashMap<>();

        for (Map<String, String> row : rows) {
            String id = normalizeRuleId(value(row, "growth_rule_id"));
            if (id.isEmpty()) {
                continue;
            }
            String schemaJson = value(row, "params_schema_json");
            Map<String, String> fieldTypes = new LinkedHashMap<>();
            if (!schemaJson.isEmpty()) {
                try {
                    Map<String, Object> parsed = SimpleJson.parseObject(schemaJson);
                    for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                        fieldTypes.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
                    }
                } catch (RuntimeException exception) {
                    System.err.printf("[block-csv] invalid growth schema rule=%s error=%s%n", id, exception.getMessage());
                    continue;
                }
            }
            map.put(id, new GrowthRuleSchema(id, fieldTypes));
        }

        map.putIfAbsent("NONE", new GrowthRuleSchema("NONE", Map.of()));
        return Collections.unmodifiableMap(map);
    }

    private static Path resolvePath(String key, String legacyKey, Path[] fallbackCandidates) {
        String configured = System.getProperty(key);
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty(legacyKey);
        }
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured.trim());
        }

        for (Path candidate : fallbackCandidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Map<String, String>> readCsv(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return List.of();
            }
            List<String> headers = parseCsvLine(stripBom(headerLine));
            List<Map<String, String>> rows = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String key = headers.get(i);
                    String value = i < values.size() ? values.get(i) : "";
                    row.put(key, value.trim());
                }
                rows.add(row);
            }
            return rows;
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString());
        return values;
    }

    private static String stripBom(String headerLine) {
        if (!headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
            return headerLine.substring(1);
        }
        return headerLine;
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        String normalized = value(raw).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return fallback;
        }
        return switch (normalized) {
            case "1", "true", "yes", "y", "on" -> true;
            case "0", "false", "no", "n", "off" -> false;
            default -> fallback;
        };
    }

    private static String fallback(String primary, String fallback) {
        return primary == null || primary.isBlank() ? (fallback == null ? "" : fallback.trim()) : primary.trim();
    }

    private static String normalizeKey(String raw) {
        return value(raw).toLowerCase(Locale.ROOT);
    }

    private static String value(Map<String, String> row, String key) {
        return value(row == null ? null : row.get(key));
    }

    private static String value(String raw) {
        return raw == null ? "" : raw.trim();
    }

    public record LoadResult(
        List<BlockDef> blockDefs,
        Map<String, MeshProfileDef> meshProfiles,
        Map<String, GrowthRuleSchema> growthSchemas,
        int growthValidationErrors,
        int enabledGrowthRuleCount,
        Path blocksPath,
        Path meshProfilesPath,
        Path growthRulesPath
    ) {
    }
}
