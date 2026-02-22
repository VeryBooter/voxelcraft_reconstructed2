package dev.voxelcraft.core.registry;

import dev.voxelcraft.core.block.Block;

public final class Registries {
    public static final Registry<Block> BLOCKS = new Registry<>("blocks");

    private Registries() {
    }
}
