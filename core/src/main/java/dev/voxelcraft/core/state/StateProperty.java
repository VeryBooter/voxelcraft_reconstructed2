package dev.voxelcraft.core.state;

import java.util.Objects;

public record StateProperty<T>(String name, Class<T> type) {
    public StateProperty {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Property name must not be blank");
        }
    }
}
