package dev.voxelcraft.core.world.gen;

import dev.voxelcraft.core.world.Chunk;

public interface WorldGenerator {
    void generate(Chunk chunk);
}
