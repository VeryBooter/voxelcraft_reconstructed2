package dev.voxelcraft.core.world;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ChunkManager {
    private final Map<ChunkPos, Chunk> chunks = new HashMap<>();

    public Chunk getOrCreateChunk(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        return chunks.computeIfAbsent(pos, Chunk::new);
    }

    public Chunk getChunk(int chunkX, int chunkZ) {
        return chunks.get(new ChunkPos(chunkX, chunkZ));
    }

    public Block getBlock(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        int chunkX = Math.floorDiv(pos.x(), Section.SIZE);
        int chunkZ = Math.floorDiv(pos.z(), Section.SIZE);
        Chunk chunk = getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return Blocks.AIR;
        }

        int localX = Math.floorMod(pos.x(), Section.SIZE);
        int localZ = Math.floorMod(pos.z(), Section.SIZE);
        return chunk.getBlock(localX, pos.y(), localZ);
    }

    public void setBlock(BlockPos pos, Block block) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(block, "block");
        int chunkX = Math.floorDiv(pos.x(), Section.SIZE);
        int chunkZ = Math.floorDiv(pos.z(), Section.SIZE);
        int localX = Math.floorMod(pos.x(), Section.SIZE);
        int localZ = Math.floorMod(pos.z(), Section.SIZE);

        Chunk chunk = getOrCreateChunk(chunkX, chunkZ);
        chunk.setBlock(localX, pos.y(), localZ, block);
    }

    public Collection<Chunk> chunks() {
        return Collections.unmodifiableCollection(chunks.values());
    }
}
