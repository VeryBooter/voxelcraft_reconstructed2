package dev.voxelcraft.client.world;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.BlockPos;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.World;
import java.util.ArrayList;
import java.util.List;

public final class ClientWorldView {
    private final World world;

    public ClientWorldView(World world) {
        this.world = world;
    }

    public World world() {
        return world;
    }

    public Iterable<Chunk> loadedChunks() {
        List<Chunk> snapshot = new ArrayList<>();
        for (Chunk chunk : world.loadedChunks()) {
            snapshot.add(chunk);
        }
        return snapshot;
    }

    public Block getBlock(int x, int y, int z) {
        return world.getBlock(new BlockPos(x, y, z));
    }

    public Block peekBlock(int x, int y, int z) {
        return world.peekBlock(new BlockPos(x, y, z));
    }

    public boolean setBlock(int x, int y, int z, Block block) {
        return world.setBlock(new BlockPos(x, y, z), block);
    }

    public boolean setBlock(BlockPos pos, Block block) {
        return world.setBlock(pos, block);
    }

    public boolean isSolid(int x, int y, int z) {
        Block block = peekBlock(x, y, z);
        return block != null && block != Blocks.AIR && block.solid();
    }

    public boolean isWithinWorldY(int y) {
        return world.isWithinWorldY(y);
    }

    public long blockUpdateVersion() {
        return world.blockUpdateVersion();
    }

    public void ensureChunkRadius(int centerChunkX, int centerChunkZ, int radius) {
        int clampedRadius = Math.max(0, radius);
        for (int chunkX = centerChunkX - clampedRadius; chunkX <= centerChunkX + clampedRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - clampedRadius; chunkZ <= centerChunkZ + clampedRadius; chunkZ++) {
                world.getOrGenerateChunk(chunkX, chunkZ);
            }
        }
    }
}
