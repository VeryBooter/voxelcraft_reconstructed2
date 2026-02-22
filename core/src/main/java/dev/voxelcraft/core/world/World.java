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
        return getBlock(pos.x(), pos.y(), pos.z());
    }

    public Block getBlock(int x, int y, int z) {
        if (!isWithinWorldY(y)) {
            return Blocks.AIR;
        }
        Chunk chunk = ensureChunkGenerated(
            Math.floorDiv(x, Section.SIZE),
            Math.floorDiv(z, Section.SIZE)
        );

        int localX = Math.floorMod(x, Section.SIZE);
        int localZ = Math.floorMod(z, Section.SIZE);
        return chunk.getBlock(localX, y, localZ);
    }

    public Block peekBlock(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        return peekBlock(pos.x(), pos.y(), pos.z());
    }

    public Block peekBlock(int x, int y, int z) {
        if (!isWithinWorldY(y)) {
            return Blocks.AIR;
        }

        int chunkX = Math.floorDiv(x, Section.SIZE);
        int chunkZ = Math.floorDiv(z, Section.SIZE);
        Chunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return Blocks.AIR;
        }

        int localX = Math.floorMod(x, Section.SIZE);
        int localZ = Math.floorMod(z, Section.SIZE);
        return chunk.getBlock(localX, y, localZ);
    }

    public boolean setBlock(BlockPos pos, Block block) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(block, "block");
        return setBlock(pos.x(), pos.y(), pos.z(), block);
    }

    public boolean setBlock(int x, int y, int z, Block block) {
        Objects.requireNonNull(block, "block");
        if (!isWithinWorldY(y)) {
            return false;
        }
        int chunkX = Math.floorDiv(x, Section.SIZE);
        int chunkZ = Math.floorDiv(z, Section.SIZE);
        Chunk chunk = ensureChunkGenerated(
            chunkX,
            chunkZ
        );
        int localX = Math.floorMod(x, Section.SIZE);
        int localZ = Math.floorMod(z, Section.SIZE);
        if (chunk.getBlock(localX, y, localZ) == block) {
            return false;
        }
        chunk.setBlock(localX, y, localZ, block);
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
