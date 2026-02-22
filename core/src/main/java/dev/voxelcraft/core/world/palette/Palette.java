package dev.voxelcraft.core.world.palette;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Palette<T> {
    private final Map<T, Integer> valueToId = new HashMap<>();
    private final List<T> idToValue = new ArrayList<>();

    public int idFor(T value) {
        Objects.requireNonNull(value, "value");
        Integer existing = valueToId.get(value);
        if (existing != null) {
            return existing;
        }
        int id = idToValue.size();
        idToValue.add(value);
        valueToId.put(value, id);
        return id;
    }

    public T valueFor(int id) {
        if (id < 0 || id >= idToValue.size()) {
            throw new IllegalArgumentException("Invalid palette id: " + id);
        }
        return idToValue.get(id);
    }

    public int size() {
        return idToValue.size();
    }
}
