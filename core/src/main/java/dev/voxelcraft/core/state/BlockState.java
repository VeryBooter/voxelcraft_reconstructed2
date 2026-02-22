package dev.voxelcraft.core.state;

import dev.voxelcraft.core.block.Block;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class BlockState {
    private final Block block;
    private final Map<StateProperty<?>, Object> values;

    public BlockState(Block block, Map<StateProperty<?>, Object> values) {
        this.block = Objects.requireNonNull(block, "block");
        this.values = Map.copyOf(values);
    }

    public Block block() {
        return block;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(StateProperty<T> property) {
        return (T) values.get(property);
    }

    public <T> BlockState with(StateProperty<T> property, T value) {
        HashMap<StateProperty<?>, Object> next = new HashMap<>(values);
        next.put(property, value);
        return new BlockState(block, next);
    }
}
