package dev.voxelcraft.core.registry;

import dev.voxelcraft.core.util.ResourceLocation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Registry<T> {
    private final String debugName;
    private final Map<ResourceLocation, T> entries = new LinkedHashMap<>();
    private boolean frozen;

    public Registry(String debugName) {
        this.debugName = Objects.requireNonNull(debugName, "debugName");
    }

    public synchronized T register(ResourceLocation id, T value) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, "value");
        ensureMutable();
        if (entries.putIfAbsent(id, value) != null) {
            throw new IllegalStateException("Duplicate registry key in " + debugName + ": " + id);
        }
        return value;
    }

    public synchronized T get(ResourceLocation id) {
        T value = entries.get(id);
        if (value == null) {
            throw new IllegalStateException("Missing registry key in " + debugName + ": " + id);
        }
        return value;
    }

    public synchronized Map<ResourceLocation, T> entries() {
        return Collections.unmodifiableMap(entries);
    }

    public synchronized void freeze() {
        this.frozen = true;
    }

    public synchronized boolean isFrozen() {
        return frozen;
    }

    private void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException("Registry is frozen: " + debugName);
        }
    }
}
