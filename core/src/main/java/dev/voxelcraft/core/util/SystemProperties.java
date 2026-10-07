package dev.voxelcraft.core.util;

import java.util.Locale;

/**
 * 读取带旧键兼容的系统配置。仅在主键未设置时读取旧键；空值或非法值使用默认值。
 */
public final class SystemProperties {
    private SystemProperties() {
    }

    public static boolean getBoolean(String key, String legacyKey, boolean defaultValue) {
        String raw = read(key, legacyKey);
        if (raw == null) {
            return defaultValue;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> defaultValue;
        };
    }

    public static int getInt(String key, String legacyKey, int defaultValue) {
        String raw = read(key, legacyKey);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String read(String key, String legacyKey) {
        String raw = System.getProperty(key);
        return raw != null ? raw : System.getProperty(legacyKey);
    }
}
