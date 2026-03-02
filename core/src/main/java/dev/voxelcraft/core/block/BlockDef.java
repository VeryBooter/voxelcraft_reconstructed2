package dev.voxelcraft.core.block;

// 中文标注：本文件已标记。

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record BlockDef(
    BlockId id,
    String key,
    String displayName,
    String category,
    String material,
    String variant,
    String shape,
    RenderBucket renderBucket,
    AlphaMode alphaMode,
    boolean needsSorting,
    boolean occludes,
    OcclusionMode occlusionMode,
    MeshProfile meshProfile,
    CollisionKind collisionKind,
    String aabbMin,
    String aabbMax,
    int attachFacesMask,
    boolean requiresSupport,
    boolean doubleSided,
    String hardnessClass,
    String soundClass,
    boolean flammable,
    boolean requiresWater,
    String tintMode,
    String growthRuleId,
    String growthParamsJson,
    Map<String, Object> growthParams
) {
    public static final int ATTACH_NONE = 0;
    public static final int ATTACH_TOP = 1 << 0;
    public static final int ATTACH_BOTTOM = 1 << 1;
    public static final int ATTACH_NORTH = 1 << 2;
    public static final int ATTACH_EAST = 1 << 3;
    public static final int ATTACH_SOUTH = 1 << 4;
    public static final int ATTACH_WEST = 1 << 5;
    public static final int ATTACH_ANY = ATTACH_TOP | ATTACH_BOTTOM | ATTACH_NORTH | ATTACH_EAST | ATTACH_SOUTH | ATTACH_WEST;

    public BlockDef {
        key = normalizeKey(key);
        displayName = sanitize(displayName);
        category = sanitize(category);
        material = sanitize(material);
        variant = sanitize(variant);
        shape = sanitize(shape);
        aabbMin = sanitize(aabbMin);
        aabbMax = sanitize(aabbMax);
        hardnessClass = sanitize(hardnessClass);
        soundClass = sanitize(soundClass);
        tintMode = sanitize(tintMode);
        growthRuleId = sanitize(growthRuleId).isEmpty() ? "NONE" : sanitize(growthRuleId).toUpperCase(Locale.ROOT);
        growthParamsJson = sanitize(growthParamsJson);
        growthParams = growthParams == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(growthParams));
    }

    public boolean isFullOccluder() {
        return occludes && occlusionMode == OcclusionMode.FULL;
    }

    public boolean isSolidForCollision() {
        return collisionKind.isSolid();
    }

    public static int parseAttachFacesMask(String raw) {
        String normalized = sanitize(raw).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return ATTACH_NONE;
        }
        if (normalized.equals("any")) {
            return ATTACH_ANY;
        }

        int mask = ATTACH_NONE;
        String[] tokens = normalized.split("[,|;/ ]+");
        for (String token : tokens) {
            switch (token) {
                case "top", "up" -> mask |= ATTACH_TOP;
                case "bottom", "down" -> mask |= ATTACH_BOTTOM;
                case "north", "n" -> mask |= ATTACH_NORTH;
                case "east", "e" -> mask |= ATTACH_EAST;
                case "south", "s" -> mask |= ATTACH_SOUTH;
                case "west", "w" -> mask |= ATTACH_WEST;
                default -> {
                }
            }
        }
        return mask;
    }

    public static String normalizeKey(String raw) {
        String sanitized = sanitize(raw);
        return sanitized.toLowerCase(Locale.ROOT);
    }

    private static String sanitize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    public enum RenderBucket {
        OPAQUE,
        CUTOUT,
        TRANSLUCENT;

        public static RenderBucket from(String raw, RenderBucket fallback) {
            String normalized = normalizeEnumToken(raw);
            if (normalized.isEmpty()) {
                return fallback;
            }
            try {
                return RenderBucket.valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                return fallback;
            }
        }
    }

    public enum AlphaMode {
        OPAQUE,
        CUTOUT,
        TRANSLUCENT,
        MASKED,
        UNKNOWN;

        public static AlphaMode from(String raw, AlphaMode fallback) {
            String normalized = normalizeEnumToken(raw);
            if (normalized.isEmpty()) {
                return fallback;
            }
            try {
                return AlphaMode.valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                return fallback;
            }
        }
    }

    public enum OcclusionMode {
        FULL,
        PARTIAL,
        NONE;

        public static OcclusionMode from(String raw, OcclusionMode fallback) {
            String normalized = normalizeEnumToken(raw);
            if (normalized.isEmpty()) {
                return fallback;
            }
            try {
                return OcclusionMode.valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                return fallback;
            }
        }
    }

    public enum MeshProfile {
        CUBE,
        SLAB_HALF,
        STAIRS,
        WALL,
        LAYERED_1_8,
        PILE_LOW,
        SURFACE_PATCH,
        CROSS,
        CLUSTER,
        DEPOSIT_PATCH,
        PILLAR,
        RUBBLE,
        UNKNOWN;

        public static MeshProfile from(String raw, MeshProfile fallback) {
            String normalized = normalizeEnumToken(raw);
            if (normalized.isEmpty()) {
                return fallback;
            }
            try {
                return MeshProfile.valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                return fallback;
            }
        }

        public MeshProfile mesherTemplate() {
            return switch (this) {
                case DEPOSIT_PATCH -> SURFACE_PATCH;
                case PILLAR -> CUBE;
                case RUBBLE -> PILE_LOW;
                default -> this;
            };
        }
    }

    public enum CollisionKind {
        FULL,
        SLAB,
        STAIRS,
        WALL,
        LAYERED,
        PILE,
        RUBBLE,
        NONE,
        UNKNOWN;

        public static CollisionKind from(String raw, CollisionKind fallback) {
            String normalized = normalizeEnumToken(raw);
            if (normalized.isEmpty()) {
                return fallback;
            }
            return switch (normalized) {
                case "FULL", "BLOCK" -> FULL;
                case "SLAB" -> SLAB;
                case "STAIRS" -> STAIRS;
                case "WALL" -> WALL;
                case "LAYERED", "LAYERED_1_8" -> LAYERED;
                case "PILE" -> PILE;
                case "RUBBLE" -> RUBBLE;
                case "NONE" -> NONE;
                default -> fallback;
            };
        }

        public boolean isSolid() {
            return switch (this) {
                case FULL, SLAB, STAIRS, WALL, LAYERED, PILE, RUBBLE -> true;
                case NONE, UNKNOWN -> false;
            };
        }
    }

    private static String normalizeEnumToken(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
            .replace('-', '_')
            .replace(':', '_')
            .replace('/', '_')
            .replace(' ', '_')
            .toUpperCase(Locale.ROOT);
    }
}
