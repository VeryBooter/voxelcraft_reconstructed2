package dev.voxelcraft.core;

import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.World;

public final class Game {
    private final World world;

    public Game() {
        Blocks.bootstrap();
        this.world = new World();
    }

    public World world() {
        return world;
    }

    public void tick() {
        world.tick();
    }
}
