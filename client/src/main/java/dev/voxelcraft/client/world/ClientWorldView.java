package dev.voxelcraft.client.world;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.BlockPos;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.ChunkPos;
import dev.voxelcraft.core.world.World;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClientWorldView implements AutoCloseable {
    private static final long CHUNK_GEN_SLOW_LOG_THRESHOLD_NANOS = 10_000_000L;
    private static final int DEFAULT_ASYNC_CHUNK_GEN_SUBMIT_BUDGET = 2;
    private final World world;
    private final ArrayDeque<ChunkPos> pendingChunkGeneration = new ArrayDeque<>();
    private final HashSet<ChunkPos> pendingChunkGenerationSet = new HashSet<>();
    private final boolean asyncChunkGenerationEnabled;
    private final int asyncChunkGenerationSubmitBudgetPerTick;
    private final ExecutorService chunkGenerationPool;
    private final ConcurrentLinkedQueue<GeneratedChunk> readyGeneratedChunks = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Long, Boolean> inFlightChunkGeneration = new ConcurrentHashMap<>();
    private final AtomicInteger chunkGenerationJobsInFlight = new AtomicInteger();
    private long chunkGenStatsWindowStartNanos = System.nanoTime();
    private int chunkGenStatsChunks;
    private int chunkGenStatsDrainCalls;
    private long chunkGenStatsTotalNanos;
    private long chunkGenStatsMaxNanos;
    private long chunkGenStatsChunkTotalNanos;
    private long chunkGenStatsChunkMaxNanos;
    private long chunkGenStatsAsyncGeneratedChunks;
    private long chunkGenStatsAsyncGenTotalNanos;
    private long chunkGenStatsAsyncGenMaxNanos;
    private long lastChunkGenSubmitNanos;
    private long lastChunkInstallNanos;
    private int lastChunkGenSubmittedCount;
    private int lastChunkInstalledCount;

    public ClientWorldView(World world) {
        this.world = world;
        this.asyncChunkGenerationEnabled = booleanProperty("voxelcraft.chunkGenAsync", false);
        this.asyncChunkGenerationSubmitBudgetPerTick = Math.max(1, intProperty("voxelcraft.chunkGenSubmitBudget", DEFAULT_ASYNC_CHUNK_GEN_SUBMIT_BUDGET));
        if (asyncChunkGenerationEnabled) {
            int workerCount = Math.max(1, intProperty("voxelcraft.chunkGenWorkers", Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));
            ThreadFactory threadFactory = runnable -> {
                Thread thread = new Thread(runnable, "voxelcraft-chunk-gen");
                thread.setDaemon(true);
                return thread;
            };
            this.chunkGenerationPool = Executors.newFixedThreadPool(workerCount, threadFactory);
            System.out.printf(
                "[chunk-gen] async generation enabled workers=%d submitBudget=%d%n",
                workerCount,
                asyncChunkGenerationSubmitBudgetPerTick
            );
        } else {
            this.chunkGenerationPool = null;
        }
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

    public List<Chunk> loadedChunksSnapshot() {
        List<Chunk> snapshot = new ArrayList<>();
        copyLoadedChunksInto(snapshot);
        return snapshot;
    }

    public void copyLoadedChunksInto(List<Chunk> out) {
        out.clear();
        Collection<Chunk> loaded = world.loadedChunks();
        out.addAll(loaded);
    }

    public Chunk getChunk(int chunkX, int chunkZ) {
        return world.chunkManager().getChunk(chunkX, chunkZ);
    }

    public Chunk getChunk(ChunkPos pos) {
        return getChunk(pos.x(), pos.z());
    }

    public Block getBlock(int x, int y, int z) {
        return world.getBlock(x, y, z);
    }

    public Block peekBlock(int x, int y, int z) {
        return world.peekBlock(x, y, z);
    }

    public boolean setBlock(int x, int y, int z, Block block) {
        return world.setBlock(x, y, z, block);
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
        long startedNanos = System.nanoTime();
        int clampedRadius = Math.max(0, radius);
        int enqueued = 0;

        for (int ring = 0; ring <= clampedRadius; ring++) {
            if (ring == 0) {
                enqueued += enqueueChunkIfMissing(centerChunkX, centerChunkZ);
                continue;
            }

            int minX = centerChunkX - ring;
            int maxX = centerChunkX + ring;
            int minZ = centerChunkZ - ring;
            int maxZ = centerChunkZ + ring;

            for (int chunkX = minX; chunkX <= maxX; chunkX++) {
                enqueued += enqueueChunkIfMissing(chunkX, minZ);
                enqueued += enqueueChunkIfMissing(chunkX, maxZ);
            }
            for (int chunkZ = minZ + 1; chunkZ <= maxZ - 1; chunkZ++) {
                enqueued += enqueueChunkIfMissing(minX, chunkZ);
                enqueued += enqueueChunkIfMissing(maxX, chunkZ);
            }
        }

        long elapsedNanos = System.nanoTime() - startedNanos;
        if (elapsedNanos > CHUNK_GEN_SLOW_LOG_THRESHOLD_NANOS) {
            System.out.printf(
                "[chunk-gen] ensureChunkRadius center=(%d,%d) r=%d enqueued=%d pending=%d took=%.2fms%n",
                centerChunkX,
                centerChunkZ,
                clampedRadius,
                enqueued,
                pendingChunkGeneration.size(),
                elapsedNanos / 1_000_000.0
            );
        }
    }

    public void drainChunkGenerationBudget(int maxPerTick) {
        int budget = Math.max(0, maxPerTick);
        lastChunkGenSubmitNanos = 0L;
        lastChunkInstallNanos = 0L;
        lastChunkGenSubmittedCount = 0;
        lastChunkInstalledCount = 0;
        if (budget == 0) {
            if (asyncChunkGenerationEnabled) {
                long installStarted = System.nanoTime();
                lastChunkInstalledCount = drainReadyChunkInstallBudget(0);
                lastChunkInstallNanos = System.nanoTime() - installStarted;
            }
            emitChunkGenerationStatsIfDue();
            return;
        }

        if (asyncChunkGenerationEnabled) {
            long submitStarted = System.nanoTime();
            lastChunkGenSubmittedCount = submitChunkGenerationJobs(Math.max(asyncChunkGenerationSubmitBudgetPerTick, budget));
            lastChunkGenSubmitNanos = System.nanoTime() - submitStarted;

            long installStarted = System.nanoTime();
            lastChunkInstalledCount = drainReadyChunkInstallBudget(budget);
            lastChunkInstallNanos = System.nanoTime() - installStarted;
            emitChunkGenerationStatsIfDue();
            return;
        }

        if (pendingChunkGeneration.isEmpty()) {
            emitChunkGenerationStatsIfDue();
            return;
        }

        long startedNanos = System.nanoTime();
        int generated = 0;
        while (generated < budget && !pendingChunkGeneration.isEmpty()) {
            ChunkPos pos = pendingChunkGeneration.pollFirst();
            if (pos == null) {
                break;
            }
            pendingChunkGenerationSet.remove(pos);
            if (world.chunkManager().getChunk(pos.x(), pos.z()) != null) {
                continue;
            }
            long singleChunkStartedNanos = System.nanoTime();
            world.getOrGenerateChunk(pos.x(), pos.z());
            long singleChunkNanos = System.nanoTime() - singleChunkStartedNanos;
            chunkGenStatsChunkTotalNanos += singleChunkNanos;
            chunkGenStatsChunkMaxNanos = Math.max(chunkGenStatsChunkMaxNanos, singleChunkNanos);
            generated++;
        }

        long elapsedNanos = System.nanoTime() - startedNanos;
        if (generated > 0) {
            chunkGenStatsChunks += generated;
            chunkGenStatsDrainCalls++;
            chunkGenStatsTotalNanos += elapsedNanos;
            chunkGenStatsMaxNanos = Math.max(chunkGenStatsMaxNanos, elapsedNanos);
        }
        if (generated > 0 && elapsedNanos > CHUNK_GEN_SLOW_LOG_THRESHOLD_NANOS) {
            System.out.printf(
                "[chunk-gen] drain budget=%d generated=%d pending=%d took=%.2fms%n",
                budget,
                generated,
                pendingChunkGeneration.size(),
                elapsedNanos / 1_000_000.0
            );
        }
        emitChunkGenerationStatsIfDue();
    }

    public long lastChunkGenSubmitNanos() {
        return lastChunkGenSubmitNanos;
    }

    public long lastChunkInstallNanos() {
        return lastChunkInstallNanos;
    }

    public int lastChunkGenSubmittedCount() {
        return lastChunkGenSubmittedCount;
    }

    public int lastChunkInstalledCount() {
        return lastChunkInstalledCount;
    }

    public int chunkGenerationJobsInFlight() {
        return chunkGenerationJobsInFlight.get();
    }

    public int readyGeneratedChunkCount() {
        return readyGeneratedChunks.size();
    }

    public int pendingChunkGenerationCount() {
        return pendingChunkGeneration.size();
    }

    private int enqueueChunkIfMissing(int chunkX, int chunkZ) {
        if (world.chunkManager().getChunk(chunkX, chunkZ) != null) {
            return 0;
        }
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        long key = chunkKey(chunkX, chunkZ);
        if (asyncChunkGenerationEnabled && inFlightChunkGeneration.containsKey(key)) {
            return 0;
        }
        if (!pendingChunkGenerationSet.add(pos)) {
            return 0;
        }
        pendingChunkGeneration.addLast(pos);
        return 1;
    }

    private int submitChunkGenerationJobs(int submitBudget) {
        if (!asyncChunkGenerationEnabled || chunkGenerationPool == null || submitBudget <= 0) {
            return 0;
        }

        int submitted = 0;
        while (submitted < submitBudget && !pendingChunkGeneration.isEmpty()) {
            ChunkPos pos = pendingChunkGeneration.pollFirst();
            if (pos == null) {
                break;
            }
            pendingChunkGenerationSet.remove(pos);

            if (world.chunkManager().getChunk(pos.x(), pos.z()) != null) {
                continue;
            }

            long key = chunkKey(pos.x(), pos.z());
            if (inFlightChunkGeneration.putIfAbsent(key, Boolean.TRUE) != null) {
                continue;
            }

            chunkGenerationJobsInFlight.incrementAndGet();
            try {
                chunkGenerationPool.execute(() -> {
                    long started = System.nanoTime();
                    try {
                        Chunk generatedChunk = world.generateChunkDetached(pos.x(), pos.z());
                        long generationNanos = System.nanoTime() - started;
                        readyGeneratedChunks.add(new GeneratedChunk(generatedChunk, generationNanos));
                        recordAsyncGenerationStats(generationNanos);
                    } finally {
                        chunkGenerationJobsInFlight.decrementAndGet();
                    }
                });
                submitted++;
            } catch (RuntimeException submitFailure) {
                inFlightChunkGeneration.remove(key);
                chunkGenerationJobsInFlight.decrementAndGet();
            }
        }
        return submitted;
    }

    private int drainReadyChunkInstallBudget(int maxInstallPerTick) {
        int budget = Math.max(0, maxInstallPerTick);
        int installed = 0;
        while (installed < budget) {
            GeneratedChunk ready = readyGeneratedChunks.poll();
            if (ready == null) {
                break;
            }
            Chunk chunk = ready.chunk();
            inFlightChunkGeneration.remove(chunkKey(chunk.pos().x(), chunk.pos().z()));
            if (world.chunkManager().getChunk(chunk.pos().x(), chunk.pos().z()) != null) {
                continue;
            }
            if (world.installGeneratedChunkIfAbsent(chunk)) {
                installed++;
            }
        }
        return installed;
    }

    private synchronized void recordAsyncGenerationStats(long generationNanos) {
        chunkGenStatsAsyncGeneratedChunks++;
        chunkGenStatsAsyncGenTotalNanos += generationNanos;
        chunkGenStatsAsyncGenMaxNanos = Math.max(chunkGenStatsAsyncGenMaxNanos, generationNanos);
    }

    private void emitChunkGenerationStatsIfDue() {
        long now = System.nanoTime();
        long elapsed = now - chunkGenStatsWindowStartNanos;
        if (elapsed < 1_000_000_000L) {
            return;
        }
        if (chunkGenStatsChunks > 0) {
            double avgDrainMs = chunkGenStatsDrainCalls == 0
                ? 0.0
                : (chunkGenStatsTotalNanos / (double) chunkGenStatsDrainCalls) / 1_000_000.0;
            double slowestMs = chunkGenStatsMaxNanos / 1_000_000.0;
            double avgChunkMs = (chunkGenStatsChunkTotalNanos / (double) chunkGenStatsChunks) / 1_000_000.0;
            double slowestChunkMs = chunkGenStatsChunkMaxNanos / 1_000_000.0;
            System.out.printf(
                "[chunk-gen] chunksPerSec=%d avgChunkMs=%.2f slowestChunkMs=%.2f avgDrainMs=%.2f slowestDrainMs=%.2f pending=%d ready=%d inFlight=%d%n",
                chunkGenStatsChunks,
                avgChunkMs,
                slowestChunkMs,
                avgDrainMs,
                slowestMs,
                pendingChunkGeneration.size(),
                readyGeneratedChunks.size(),
                chunkGenerationJobsInFlight.get()
            );
        }
        long asyncGeneratedChunks;
        long asyncGenTotalNanos;
        long asyncGenMaxNanos;
        synchronized (this) {
            asyncGeneratedChunks = chunkGenStatsAsyncGeneratedChunks;
            asyncGenTotalNanos = chunkGenStatsAsyncGenTotalNanos;
            asyncGenMaxNanos = chunkGenStatsAsyncGenMaxNanos;
            chunkGenStatsAsyncGeneratedChunks = 0L;
            chunkGenStatsAsyncGenTotalNanos = 0L;
            chunkGenStatsAsyncGenMaxNanos = 0L;
        }
        if (asyncGeneratedChunks > 0) {
            System.out.printf(
                "[chunk-gen-async] generatedPerSec=%d avgGenMs=%.2f slowestGenMs=%.2f pending=%d ready=%d inFlight=%d%n",
                asyncGeneratedChunks,
                (asyncGenTotalNanos / (double) asyncGeneratedChunks) / 1_000_000.0,
                asyncGenMaxNanos / 1_000_000.0,
                pendingChunkGeneration.size(),
                readyGeneratedChunks.size(),
                chunkGenerationJobsInFlight.get()
            );
        }
        chunkGenStatsWindowStartNanos = now;
        chunkGenStatsChunks = 0;
        chunkGenStatsDrainCalls = 0;
        chunkGenStatsTotalNanos = 0L;
        chunkGenStatsMaxNanos = 0L;
        chunkGenStatsChunkTotalNanos = 0L;
        chunkGenStatsChunkMaxNanos = 0L;
    }

    @Override
    public void close() {
        if (chunkGenerationPool != null) {
            chunkGenerationPool.shutdownNow();
        }
        pendingChunkGeneration.clear();
        pendingChunkGenerationSet.clear();
        readyGeneratedChunks.clear();
        inFlightChunkGeneration.clear();
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xffff_ffffL);
    }

    private static boolean booleanProperty(String key, boolean defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on")) {
            return true;
        }
        if (normalized.equals("0") || normalized.equals("false") || normalized.equals("no") || normalized.equals("off")) {
            return false;
        }
        return defaultValue;
    }

    private static int intProperty(String key, int defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private record GeneratedChunk(Chunk chunk, long generationNanos) {
    }
}
