package dev.voxelcraft.core.block;

import dev.voxelcraft.core.state.BlockState;
import dev.voxelcraft.core.util.ResourceLocation;
import java.util.Map;
import java.util.Objects;

public class Block {
    private final ResourceLocation id;
    private final boolean solid;
    private final BlockState defaultState;

    public Block(String id, boolean solid) {
        this(ResourceLocation.of(id), solid);
    }

    public Block(ResourceLocation id, boolean solid) {
        this.id = Objects.requireNonNull(id, "id");
        this.solid = solid;
        this.defaultState = new BlockState(this, Map.of());
    }

    public ResourceLocation id() {
        return id;
    }

    public boolean solid() {
        return solid;
    }

    public BlockState defaultState() {
        return defaultState;
    }
}
