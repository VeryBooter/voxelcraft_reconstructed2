package dev.voxelcraft.core.block;

// 中文标注：本文件已标记。

import dev.voxelcraft.core.state.BlockState;
import dev.voxelcraft.core.util.ResourceLocation;
import java.util.Map;
import java.util.Objects;

public class Block {
    private final ResourceLocation id;
    private final boolean solid;
    private final BlockState defaultState;
    private final BlockId blockId;
    private final BlockDef def;

    public Block(String id, boolean solid) {
        this(ResourceLocation.of(id), solid, BlockId.ofUnsigned(0), null);
    }

    public Block(ResourceLocation id, boolean solid) {
        this(id, solid, BlockId.ofUnsigned(0), null);
    }

    public Block(ResourceLocation id, boolean solid, BlockId blockId, BlockDef def) {
        this.id = Objects.requireNonNull(id, "id");
        this.solid = solid;
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        this.def = def;
        this.defaultState = new BlockState(this, Map.of());
    }

    public Block(ResourceLocation id, BlockDef def) {
        this(
            id,
            def != null && def.isSolidForCollision(),
            def == null ? BlockId.ofUnsigned(0) : def.id(),
            def
        );
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

    public BlockId blockId() {
        return blockId;
    }

    public BlockDef def() {
        return def;
    }
}
