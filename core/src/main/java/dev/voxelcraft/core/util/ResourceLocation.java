package dev.voxelcraft.core.util;

import java.util.Objects;

public record ResourceLocation(String namespace, String path) {
    public ResourceLocation {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (namespace.isBlank() || path.isBlank()) {
            throw new IllegalArgumentException("namespace/path must not be blank");
        }
    }

    public static ResourceLocation of(String value) {
        Objects.requireNonNull(value, "value");
        String[] split = value.split(":", 2);
        if (split.length == 1) {
            return new ResourceLocation("voxelcraft", split[0]);
        }
        return new ResourceLocation(split[0], split[1]);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
