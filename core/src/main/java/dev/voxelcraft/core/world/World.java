package dev.voxelcraft.core.world;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.gen.FlatWorldGenerator;
import dev.voxelcraft.core.world.gen.WorldGenerator;
import java.util.Collection;
import java.util.Objects;
import java.util.SplittableRandom;

public final class World {
    public static final int MIN_Y = -2048;
    public static final int MAX_Y = 319;

    private final ChunkManager chunkManager = new ChunkManager();
    private final WorldGenerator worldGenerator;
    private final long seed;
    private long ticks;
    private long blockUpdateVersion;

    public World() {
        this(new SplittableRandom().nextLong());
    }

    public World(long seed) {
        Blocks.bootstrap();
        this.seed = seed;
        this.worldGenerator = new FlatWorldGenerator(seed);
        for (int chunkX = -2; chunkX <= 2; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 2; chunkZ++) {
                ensureChunkGenerated(chunkX, chunkZ);
            }
        }
        blockUpdateVersion = 0L;
    }

    public void tick() {
        ticks++;
    }

    public long ticks() {
        return ticks;
    }

    public long seed() {
        return seed;
    }

    public ChunkManager chunkManager() {
        return chunkManager;
    }

    public Block getBlock(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        if (!isWithinWorldY(pos.y())) {
            return Blocks.AIR;
        }
        Chunk chunk = ensureChunkGenerated(
            Math.floorDiv(pos.x(), Section.SIZE),
            Math.floorDiv(pos.z(), Section.SIZE)
        );

        int localX = Math.floorMod(pos.x(), Section.SIZE);
        int localZ = Math.floorMod(pos.z(), Section.SIZE);
        return chunk.getBlock(localX, pos.y(), localZ);
    }

    public Block peekBlock(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        if (!isWithinWorldY(pos.y())) {
            return Blocks.AIR;
        }

        int chunkX = Math.floorDiv(pos.x(), Section.SIZE);
        int chunkZ = Math.floorDiv(pos.z(), Section.SIZE);
        Chunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return Blocks.AIR;
        }

        int localX = Math.floorMod(pos.x(), Section.SIZE);
        int localZ = Math.floorMod(pos.z(), Section.SIZE);
        return chunk.getBlock(localX, pos.y(), localZ);
    }

    public boolean setBlock(BlockPos pos, Block block) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(block, "block");
        if (!isWithinWorldY(pos.y())) {
            return false;
        }
        if (getBlock(pos) == block) {
            return false;
        }
        Chunk chunk = ensureChunkGenerated(
            Math.floorDiv(pos.x(), Section.SIZE),
            Math.floorDiv(pos.z(), Section.SIZE)
        );
        int localX = Math.floorMod(pos.x(), Section.SIZE);
        int localZ = Math.floorMod(pos.z(), Section.SIZE);
        chunk.setBlock(localX, pos.y(), localZ, block);
        blockUpdateVersion++;
        return true;
    }

    public boolean isWithinWorldY(int y) {
        return y >= MIN_Y && y <= MAX_Y;
    }

    public long blockUpdateVersion() {
        return blockUpdateVersion;
    }

    public Collection<Chunk> loadedChunks() {
        return chunkManager.chunks();
    }

    public Chunk getOrGenerateChunk(int chunkX, int chunkZ) {
        return ensureChunkGenerated(chunkX, chunkZ);
    }

    private Chunk ensureChunkGenerated(int chunkX, int chunkZ) {
        Chunk existing = chunkManager.getChunk(chunkX, chunkZ);
        if (existing != null) {
            return existing;
        }

        Chunk created = chunkManager.getOrCreateChunk(chunkX, chunkZ);
        worldGenerator.generate(created);
        blockUpdateVersion++;
        return created;
    }
}
