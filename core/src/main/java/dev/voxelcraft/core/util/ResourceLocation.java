package dev.voxelcraft.core.util;

import java.util.Objects;
/**
 * 中文说明：工具类：提供 ResourceLocation 的通用辅助能力。
 */

// 中文标注（记录类）：`ResourceLocation`，职责：封装resource、location相关逻辑。
// 中文标注（字段）：`namespace`，含义：用于表示namespace。
// 中文标注（字段）：`path`，含义：用于表示path。
// 中文标注（参数）：`namespace`，含义：用于表示namespace。
// 中文标注（参数）：`path`，含义：用于表示path。
public record ResourceLocation(String namespace, String path) {
    // 中文标注（构造方法）：`ResourceLocation`，参数：namespace、path；用途：初始化`ResourceLocation`实例。
    public ResourceLocation {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (namespace.isBlank() || path.isBlank()) {
            throw new IllegalArgumentException("namespace/path must not be blank");
        }
    }

    // 中文标注（方法）：`of`，参数：value；用途：执行of相关逻辑。
    // 中文标注（参数）：`value`，含义：用于表示值。
    public static ResourceLocation of(String value) {
        Objects.requireNonNull(value, "value");
        // 中文标注（局部变量）：`split`，含义：用于表示split。
        String[] split = value.split(":", 2); // meaning
        if (split.length == 1) {
            return new ResourceLocation("voxelcraft", split[0]);
        }
        return new ResourceLocation(split[0], split[1]);
    }

    // 中文标注（方法）：`toString`，参数：无；用途：进行转换或编解码：string。
    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
