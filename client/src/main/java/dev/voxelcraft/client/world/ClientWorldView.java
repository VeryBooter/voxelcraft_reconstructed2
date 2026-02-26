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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * 中文说明：客户端世界视图：封装本地区块可见范围、生成预算与射线查询接口。
 */

// 中文标注（类）：`ClientWorldView`，职责：封装客户端、世界、view相关逻辑。
public final class ClientWorldView implements AutoCloseable {
    // 中文标注（字段）：`CHUNK_GEN_SLOW_LOG_THRESHOLD_NANOS`，含义：用于表示区块、gen、slow、log、threshold、nanos。
    private static final long CHUNK_GEN_SLOW_LOG_THRESHOLD_NANOS = 10_000_000L; // meaning
    // 中文标注（字段）：`DEFAULT_ASYNC_CHUNK_GEN_SUBMIT_BUDGET`，含义：用于表示默认、async、区块、gen、submit、budget。
    private static final int DEFAULT_ASYNC_CHUNK_GEN_SUBMIT_BUDGET = 2; // meaning
    // 中文标注（字段）：`world`，含义：用于表示世界。
    private final World world; // meaning
    // 中文标注（字段）：`pendingChunkGeneration`，含义：用于表示pending、区块、generation。
    private final ArrayDeque<ChunkPos> pendingChunkGeneration = new ArrayDeque<>(); // meaning
    // 中文标注（字段）：`pendingChunkGenerationSet`，含义：用于表示pending、区块、generation、集合。
    private final HashSet<ChunkPos> pendingChunkGenerationSet = new HashSet<>(); // meaning
    // 中文标注（字段）：`asyncChunkGenerationEnabled`，含义：用于表示async、区块、generation、enabled。
    private final boolean asyncChunkGenerationEnabled; // meaning
    // 中文标注（字段）：`asyncChunkGenerationSubmitBudgetPerTick`，含义：用于表示async、区块、generation、submit、budget、per、刻。
    private final int asyncChunkGenerationSubmitBudgetPerTick; // meaning
    // 中文标注（字段）：`chunkGenerationPool`，含义：用于表示区块、generation、池。
    private final ExecutorService chunkGenerationPool; // meaning
    // 中文标注（字段）：`readyGeneratedChunks`，含义：用于表示ready、generated、区块集合。
    private final ConcurrentLinkedQueue<GeneratedChunk> readyGeneratedChunks = new ConcurrentLinkedQueue<>(); // meaning
    // 中文标注（字段）：`inFlightChunkGeneration`，含义：用于表示in、flight、区块、generation。
    private final ConcurrentHashMap<Long, Boolean> inFlightChunkGeneration = new ConcurrentHashMap<>(); // meaning
    // 中文标注（字段）：`chunkGenerationJobsInFlight`，含义：用于表示区块、generation、jobs、in、flight。
    private final AtomicInteger chunkGenerationJobsInFlight = new AtomicInteger(); // meaning
    // 中文标注（字段）：`chunkGenStatsWindowStartNanos`，含义：用于表示区块、gen、stats、窗口、开始、nanos。
    private long chunkGenStatsWindowStartNanos = System.nanoTime(); // meaning
    // 中文标注（字段）：`chunkGenStatsChunks`，含义：用于表示区块、gen、stats、区块集合。
    private int chunkGenStatsChunks; // meaning
    // 中文标注（字段）：`chunkGenStatsDrainCalls`，含义：用于表示区块、gen、stats、drain、calls。
    private int chunkGenStatsDrainCalls; // meaning
    // 中文标注（字段）：`chunkGenStatsTotalNanos`，含义：用于表示区块、gen、stats、total、nanos。
    private long chunkGenStatsTotalNanos; // meaning
    // 中文标注（字段）：`chunkGenStatsMaxNanos`，含义：用于表示区块、gen、stats、最大、nanos。
    private long chunkGenStatsMaxNanos; // meaning
    // 中文标注（字段）：`chunkGenStatsChunkTotalNanos`，含义：用于表示区块、gen、stats、区块、total、nanos。
    private long chunkGenStatsChunkTotalNanos; // meaning
    // 中文标注（字段）：`chunkGenStatsChunkMaxNanos`，含义：用于表示区块、gen、stats、区块、最大、nanos。
    private long chunkGenStatsChunkMaxNanos; // meaning
    // 中文标注（字段）：`chunkGenStatsAsyncGeneratedChunks`，含义：用于表示区块、gen、stats、async、generated、区块集合。
    private long chunkGenStatsAsyncGeneratedChunks; // meaning
    // 中文标注（字段）：`chunkGenStatsAsyncGenTotalNanos`，含义：用于表示区块、gen、stats、async、gen、total、nanos。
    private long chunkGenStatsAsyncGenTotalNanos; // meaning
    // 中文标注（字段）：`chunkGenStatsAsyncGenMaxNanos`，含义：用于表示区块、gen、stats、async、gen、最大、nanos。
    private long chunkGenStatsAsyncGenMaxNanos; // meaning
    // 中文标注（字段）：`lastChunkGenSubmitNanos`，含义：用于表示last、区块、gen、submit、nanos。
    private long lastChunkGenSubmitNanos; // meaning
    // 中文标注（字段）：`lastChunkInstallNanos`，含义：用于表示last、区块、install、nanos。
    private long lastChunkInstallNanos; // meaning
    // 中文标注（字段）：`lastChunkGenSubmittedCount`，含义：用于表示last、区块、gen、submitted、数量。
    private int lastChunkGenSubmittedCount; // meaning
    // 中文标注（字段）：`lastChunkInstalledCount`，含义：用于表示last、区块、installed、数量。
    private int lastChunkInstalledCount; // meaning
    // 中文标注（字段）：`closing`，含义：用于表示关闭中的生命周期状态。
    private volatile boolean closing; // meaning

    // 中文标注（构造方法）：`ClientWorldView`，参数：world；用途：初始化`ClientWorldView`实例。
    // 中文标注（参数）：`world`，含义：用于表示世界。
    public ClientWorldView(World world) {
        this.world = world;
        this.asyncChunkGenerationEnabled = booleanProperty("voxelcraft.chunkGenAsync", false);
        this.asyncChunkGenerationSubmitBudgetPerTick = Math.max(1, intProperty("voxelcraft.chunkGenSubmitBudget", DEFAULT_ASYNC_CHUNK_GEN_SUBMIT_BUDGET));
        if (asyncChunkGenerationEnabled) {
            // 中文标注（局部变量）：`workerCount`，含义：用于表示worker、数量。
            int workerCount = Math.max(1, intProperty("voxelcraft.chunkGenWorkers", Math.max(1, Runtime.getRuntime().availableProcessors() - 1))); // meaning
            // 中文标注（Lambda参数）：`runnable`，含义：用于表示runnable。
            // 中文标注（局部变量）：`threadFactory`，含义：用于表示thread、factory。
            ThreadFactory threadFactory = runnable -> {
                // 中文标注（局部变量）：`thread`，含义：用于表示thread。
                Thread thread = new Thread(runnable, "voxelcraft-chunk-gen"); // meaning
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

    // 中文标注（方法）：`world`，参数：无；用途：执行世界相关逻辑。
    public World world() {
        return world;
    }

    // 中文标注（方法）：`loadedChunks`，参数：无；用途：获取或读取loaded、区块集合。
    public Iterable<Chunk> loadedChunks() {
        // 中文标注（局部变量）：`snapshot`，含义：用于表示快照。
        List<Chunk> snapshot = new ArrayList<>(); // meaning
        // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
        for (Chunk chunk : world.loadedChunks()) {
            snapshot.add(chunk);
        }
        return snapshot;
    }

    // 中文标注（方法）：`loadedChunksSnapshot`，参数：无；用途：获取或读取loaded、区块集合、快照。
    public List<Chunk> loadedChunksSnapshot() {
        // 中文标注（局部变量）：`snapshot`，含义：用于表示快照。
        List<Chunk> snapshot = new ArrayList<>(); // meaning
        copyLoadedChunksInto(snapshot);
        return snapshot;
    }

    // 中文标注（方法）：`copyLoadedChunksInto`，参数：out；用途：执行copy、loaded、区块集合、into相关逻辑。
    // 中文标注（参数）：`out`，含义：用于表示out。
    public void copyLoadedChunksInto(List<Chunk> out) {
        out.clear();
        // 中文标注（局部变量）：`loaded`，含义：用于表示loaded。
        Collection<Chunk> loaded = world.loadedChunks(); // meaning
        out.addAll(loaded);
    }

    // 中文标注（方法）：`getChunk`，参数：chunkX、chunkZ；用途：获取或读取区块。
    // 中文标注（参数）：`chunkX`，含义：用于表示区块、X坐标。
    // 中文标注（参数）：`chunkZ`，含义：用于表示区块、Z坐标。
    public Chunk getChunk(int chunkX, int chunkZ) {
        return world.chunkManager().getChunk(chunkX, chunkZ);
    }

    // 中文标注（方法）：`getChunk`，参数：pos；用途：获取或读取区块。
    // 中文标注（参数）：`pos`，含义：用于表示位置。
    public Chunk getChunk(ChunkPos pos) {
        return getChunk(pos.x(), pos.z());
    }

    // 中文标注（方法）：`getBlock`，参数：x、y、z；用途：获取或读取方块。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    public Block getBlock(int x, int y, int z) {
        return world.getBlock(x, y, z);
    }

    // 中文标注（方法）：`peekBlock`，参数：x、y、z；用途：执行peek、方块相关逻辑。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    public Block peekBlock(int x, int y, int z) {
        return world.peekBlock(x, y, z);
    }

    // 中文标注（方法）：`setBlock`，参数：x、y、z、block；用途：设置、写入或注册方块。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    public boolean setBlock(int x, int y, int z, Block block) {
        return world.setBlock(x, y, z, block);
    }

    // 中文标注（方法）：`setBlock`，参数：pos、block；用途：设置、写入或注册方块。
    // 中文标注（参数）：`pos`，含义：用于表示位置。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    public boolean setBlock(BlockPos pos, Block block) {
        return world.setBlock(pos, block);
    }

    // 中文标注（方法）：`isSolid`，参数：x、y、z；用途：判断实体是否满足条件。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    public boolean isSolid(int x, int y, int z) {
        // 中文标注（局部变量）：`block`，含义：用于表示方块。
        Block block = peekBlock(x, y, z); // meaning
        return block != null && block != Blocks.AIR && block.solid();
    }

    // 中文标注（方法）：`isWithinWorldY`，参数：y；用途：判断within、世界、Y坐标是否满足条件。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    public boolean isWithinWorldY(int y) {
        return world.isWithinWorldY(y);
    }

    // 中文标注（方法）：`blockUpdateVersion`，参数：无；用途：执行方块、更新、版本相关逻辑。
    public long blockUpdateVersion() {
        return world.blockUpdateVersion();
    }

    // 中文标注（方法）：`ensureChunkRadius`，参数：centerChunkX、centerChunkZ、radius；用途：执行ensure、区块、radius相关逻辑。
    // 中文标注（参数）：`centerChunkX`，含义：用于表示center、区块、X坐标。
    // 中文标注（参数）：`centerChunkZ`，含义：用于表示center、区块、Z坐标。
    // 中文标注（参数）：`radius`，含义：用于表示radius。
    public synchronized void ensureChunkRadius(int centerChunkX, int centerChunkZ, int radius) {
        if (closing) {
            return;
        }
        // 中文标注（局部变量）：`startedNanos`，含义：用于表示started、nanos。
        long startedNanos = System.nanoTime(); // meaning
        // 中文标注（局部变量）：`clampedRadius`，含义：用于表示clamped、radius。
        int clampedRadius = Math.max(0, radius); // meaning
        // 中文标注（局部变量）：`enqueued`，含义：用于表示enqueued。
        int enqueued = 0; // meaning

        // 中文标注（局部变量）：`ring`，含义：用于表示ring。
        for (int ring = 0; ring <= clampedRadius; ring++) { // meaning
            if (ring == 0) {
                enqueued += enqueueChunkIfMissing(centerChunkX, centerChunkZ);
                continue;
            }

            // 中文标注（局部变量）：`minX`，含义：用于表示最小、X坐标。
            int minX = centerChunkX - ring; // meaning
            // 中文标注（局部变量）：`maxX`，含义：用于表示最大、X坐标。
            int maxX = centerChunkX + ring; // meaning
            // 中文标注（局部变量）：`minZ`，含义：用于表示最小、Z坐标。
            int minZ = centerChunkZ - ring; // meaning
            // 中文标注（局部变量）：`maxZ`，含义：用于表示最大、Z坐标。
            int maxZ = centerChunkZ + ring; // meaning

            // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
            for (int chunkX = minX; chunkX <= maxX; chunkX++) { // meaning
                enqueued += enqueueChunkIfMissing(chunkX, minZ);
                enqueued += enqueueChunkIfMissing(chunkX, maxZ);
            }
            // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
            for (int chunkZ = minZ + 1; chunkZ <= maxZ - 1; chunkZ++) { // meaning
                enqueued += enqueueChunkIfMissing(minX, chunkZ);
                enqueued += enqueueChunkIfMissing(maxX, chunkZ);
            }
        }

        // 中文标注（局部变量）：`elapsedNanos`，含义：用于表示已耗时、nanos。
        long elapsedNanos = System.nanoTime() - startedNanos; // meaning
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

    // 中文标注（方法）：`drainChunkGenerationBudget`，参数：maxPerTick；用途：执行drain、区块、generation、budget相关逻辑。
    // 中文标注（参数）：`maxPerTick`，含义：用于表示最大、per、刻。
    public synchronized void drainChunkGenerationBudget(int maxPerTick) {
        // 中文标注（局部变量）：`budget`，含义：用于表示budget。
        int budget = Math.max(0, maxPerTick); // meaning
        lastChunkGenSubmitNanos = 0L;
        lastChunkInstallNanos = 0L;
        lastChunkGenSubmittedCount = 0;
        lastChunkInstalledCount = 0;
        if (closing) {
            return;
        }
        if (budget == 0) {
            if (asyncChunkGenerationEnabled) {
                // 中文标注（局部变量）：`installStarted`，含义：用于表示install、started。
                long installStarted = System.nanoTime(); // meaning
                lastChunkInstalledCount = drainReadyChunkInstallBudget(0);
                lastChunkInstallNanos = System.nanoTime() - installStarted;
            }
            emitChunkGenerationStatsIfDue();
            return;
        }

        if (asyncChunkGenerationEnabled) {
            // 中文标注（局部变量）：`submitStarted`，含义：用于表示submit、started。
            long submitStarted = System.nanoTime(); // meaning
            lastChunkGenSubmittedCount = submitChunkGenerationJobs(Math.max(asyncChunkGenerationSubmitBudgetPerTick, budget));
            lastChunkGenSubmitNanos = System.nanoTime() - submitStarted;

            // 中文标注（局部变量）：`installStarted`，含义：用于表示install、started。
            long installStarted = System.nanoTime(); // meaning
            lastChunkInstalledCount = drainReadyChunkInstallBudget(budget);
            lastChunkInstallNanos = System.nanoTime() - installStarted;
            emitChunkGenerationStatsIfDue();
            return;
        }

        if (pendingChunkGeneration.isEmpty()) {
            emitChunkGenerationStatsIfDue();
            return;
        }

        // 中文标注（局部变量）：`startedNanos`，含义：用于表示started、nanos。
        long startedNanos = System.nanoTime(); // meaning
        // 中文标注（局部变量）：`generated`，含义：用于表示generated。
        int generated = 0; // meaning
        while (generated < budget && !pendingChunkGeneration.isEmpty()) {
            // 中文标注（局部变量）：`pos`，含义：用于表示位置。
            ChunkPos pos = pendingChunkGeneration.pollFirst(); // meaning
            if (pos == null) {
                break;
            }
            pendingChunkGenerationSet.remove(pos);
            if (world.chunkManager().getChunk(pos.x(), pos.z()) != null) {
                continue;
            }
            // 中文标注（局部变量）：`singleChunkStartedNanos`，含义：用于表示single、区块、started、nanos。
            long singleChunkStartedNanos = System.nanoTime(); // meaning
            world.getOrGenerateChunk(pos.x(), pos.z());
            // 中文标注（局部变量）：`singleChunkNanos`，含义：用于表示single、区块、nanos。
            long singleChunkNanos = System.nanoTime() - singleChunkStartedNanos; // meaning
            chunkGenStatsChunkTotalNanos += singleChunkNanos;
            chunkGenStatsChunkMaxNanos = Math.max(chunkGenStatsChunkMaxNanos, singleChunkNanos);
            generated++;
        }

        // 中文标注（局部变量）：`elapsedNanos`，含义：用于表示已耗时、nanos。
        long elapsedNanos = System.nanoTime() - startedNanos; // meaning
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

    // 中文标注（方法）：`lastChunkGenSubmitNanos`，参数：无；用途：执行last、区块、gen、submit、nanos相关逻辑。
    public long lastChunkGenSubmitNanos() {
        return lastChunkGenSubmitNanos;
    }

    // 中文标注（方法）：`lastChunkInstallNanos`，参数：无；用途：执行last、区块、install、nanos相关逻辑。
    public long lastChunkInstallNanos() {
        return lastChunkInstallNanos;
    }

    // 中文标注（方法）：`lastChunkGenSubmittedCount`，参数：无；用途：执行last、区块、gen、submitted、数量相关逻辑。
    public int lastChunkGenSubmittedCount() {
        return lastChunkGenSubmittedCount;
    }

    // 中文标注（方法）：`lastChunkInstalledCount`，参数：无；用途：执行last、区块、installed、数量相关逻辑。
    public int lastChunkInstalledCount() {
        return lastChunkInstalledCount;
    }

    // 中文标注（方法）：`chunkGenerationJobsInFlight`，参数：无；用途：执行区块、generation、jobs、in、flight相关逻辑。
    public int chunkGenerationJobsInFlight() {
        return chunkGenerationJobsInFlight.get();
    }

    // 中文标注（方法）：`readyGeneratedChunkCount`，参数：无；用途：获取或读取ready、generated、区块、数量。
    public int readyGeneratedChunkCount() {
        return readyGeneratedChunks.size();
    }

    // 中文标注（方法）：`pendingChunkGenerationCount`，参数：无；用途：执行pending、区块、generation、数量相关逻辑。
    public synchronized int pendingChunkGenerationCount() {
        return pendingChunkGeneration.size();
    }

    // 中文标注（方法）：`enqueueChunkIfMissing`，参数：chunkX、chunkZ；用途：执行enqueue、区块、if、missing相关逻辑。
    // 中文标注（参数）：`chunkX`，含义：用于表示区块、X坐标。
    // 中文标注（参数）：`chunkZ`，含义：用于表示区块、Z坐标。
    private int enqueueChunkIfMissing(int chunkX, int chunkZ) {
        if (closing) {
            return 0;
        }
        if (world.chunkManager().getChunk(chunkX, chunkZ) != null) {
            return 0;
        }
        // 中文标注（局部变量）：`pos`，含义：用于表示位置。
        ChunkPos pos = new ChunkPos(chunkX, chunkZ); // meaning
        // 中文标注（局部变量）：`key`，含义：用于表示键。
        long key = chunkKey(chunkX, chunkZ); // meaning
        if (asyncChunkGenerationEnabled && inFlightChunkGeneration.containsKey(key)) {
            return 0;
        }
        if (!pendingChunkGenerationSet.add(pos)) {
            return 0;
        }
        pendingChunkGeneration.addLast(pos);
        return 1;
    }

    // 中文标注（方法）：`submitChunkGenerationJobs`，参数：submitBudget；用途：执行submit、区块、generation、jobs相关逻辑。
    // 中文标注（参数）：`submitBudget`，含义：用于表示submit、budget。
    private int submitChunkGenerationJobs(int submitBudget) {
        if (closing || !asyncChunkGenerationEnabled || chunkGenerationPool == null || submitBudget <= 0) {
            return 0;
        }

        // 中文标注（局部变量）：`submitted`，含义：用于表示submitted。
        int submitted = 0; // meaning
        while (submitted < submitBudget && !pendingChunkGeneration.isEmpty()) {
            // 中文标注（局部变量）：`pos`，含义：用于表示位置。
            ChunkPos pos = pendingChunkGeneration.pollFirst(); // meaning
            if (pos == null) {
                break;
            }
            pendingChunkGenerationSet.remove(pos);

            if (world.chunkManager().getChunk(pos.x(), pos.z()) != null) {
                continue;
            }

            // 中文标注（局部变量）：`key`，含义：用于表示键。
            long key = chunkKey(pos.x(), pos.z()); // meaning
            if (inFlightChunkGeneration.putIfAbsent(key, Boolean.TRUE) != null) {
                continue;
            }

            chunkGenerationJobsInFlight.incrementAndGet();
            try {
                chunkGenerationPool.execute(new ChunkGenerationTask(() -> {
                    // 中文标注（局部变量）：`started`，含义：用于表示started。
                    long started = System.nanoTime(); // meaning
                    try {
                        // 中文标注（局部变量）：`generatedChunk`，含义：用于表示generated、区块。
                        Chunk generatedChunk = world.generateChunkDetached(pos.x(), pos.z()); // meaning
                        // 中文标注（局部变量）：`generationNanos`，含义：用于表示generation、nanos。
                        long generationNanos = System.nanoTime() - started; // meaning
                        // close/shutdown 后不再入 ready 队列，避免已关闭对象残留结果和 inFlight 状态。
                        if (closing) {
                            inFlightChunkGeneration.remove(key);
                            return;
                        }
                        // 中文标注（局部变量）：`readyChunk`，含义：用于表示ready、区块。
                        GeneratedChunk readyChunk = new GeneratedChunk(generatedChunk, generationNanos); // meaning
                        readyGeneratedChunks.add(readyChunk);
                        if (closing && readyGeneratedChunks.remove(readyChunk)) {
                            inFlightChunkGeneration.remove(key);
                            return;
                        }
                        recordAsyncGenerationStats(generationNanos);
                    // 中文标注（异常参数）：`generationFailure`，含义：用于表示generation、failure。
                    } catch (RuntimeException generationFailure) {
                        inFlightChunkGeneration.remove(key);
                        System.err.printf(
                            "[chunk-gen] async generation failed chunk=(%d,%d): %s%n",
                            pos.x(),
                            pos.z(),
                            generationFailure
                        );
                    // 中文标注（异常参数）：`generationFailure`，含义：用于表示generation、failure。
                    } catch (Error generationFailure) {
                        inFlightChunkGeneration.remove(key);
                        throw generationFailure;
                    } finally {
                        chunkGenerationJobsInFlight.decrementAndGet();
                    }
                }, () -> {
                    inFlightChunkGeneration.remove(key);
                    chunkGenerationJobsInFlight.decrementAndGet();
                }));
                submitted++;
            // 中文标注（异常参数）：`submitFailure`，含义：用于表示submit、failure。
            } catch (RuntimeException submitFailure) {
                inFlightChunkGeneration.remove(key);
                chunkGenerationJobsInFlight.decrementAndGet();
            }
        }
        return submitted;
    }

    // 中文标注（方法）：`drainReadyChunkInstallBudget`，参数：maxInstallPerTick；用途：执行drain、ready、区块、install、budget相关逻辑。
    // 中文标注（参数）：`maxInstallPerTick`，含义：用于表示最大、install、per、刻。
    private int drainReadyChunkInstallBudget(int maxInstallPerTick) {
        // 中文标注（局部变量）：`budget`，含义：用于表示budget。
        int budget = Math.max(0, maxInstallPerTick); // meaning
        // 中文标注（局部变量）：`installed`，含义：用于表示installed。
        int installed = 0; // meaning
        while (installed < budget) {
            // 中文标注（局部变量）：`ready`，含义：用于表示ready。
            GeneratedChunk ready = readyGeneratedChunks.poll(); // meaning
            if (ready == null) {
                break;
            }
            // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
            Chunk chunk = ready.chunk(); // meaning
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

    // 中文标注（方法）：`recordAsyncGenerationStats`，参数：generationNanos；用途：执行record、async、generation、stats相关逻辑。
    // 中文标注（参数）：`generationNanos`，含义：用于表示generation、nanos。
    private synchronized void recordAsyncGenerationStats(long generationNanos) {
        chunkGenStatsAsyncGeneratedChunks++;
        chunkGenStatsAsyncGenTotalNanos += generationNanos;
        chunkGenStatsAsyncGenMaxNanos = Math.max(chunkGenStatsAsyncGenMaxNanos, generationNanos);
    }

    // 中文标注（方法）：`emitChunkGenerationStatsIfDue`，参数：无；用途：执行emit、区块、generation、stats、if、due相关逻辑。
    private void emitChunkGenerationStatsIfDue() {
        // 中文标注（局部变量）：`now`，含义：用于表示now。
        long now = System.nanoTime(); // meaning
        // 中文标注（局部变量）：`elapsed`，含义：用于表示已耗时。
        long elapsed = now - chunkGenStatsWindowStartNanos; // meaning
        if (elapsed < 1_000_000_000L) {
            return;
        }
        if (chunkGenStatsChunks > 0) {
            // 中文标注（局部变量）：`avgDrainMs`，含义：用于表示平均、drain、ms。
            double avgDrainMs = chunkGenStatsDrainCalls == 0
                ? 0.0
                : (chunkGenStatsTotalNanos / (double) chunkGenStatsDrainCalls) / 1_000_000.0;
            // 中文标注（局部变量）：`slowestMs`，含义：用于表示slowest、ms。
            double slowestMs = chunkGenStatsMaxNanos / 1_000_000.0; // meaning
            // 中文标注（局部变量）：`avgChunkMs`，含义：用于表示平均、区块、ms。
            double avgChunkMs = (chunkGenStatsChunkTotalNanos / (double) chunkGenStatsChunks) / 1_000_000.0; // meaning
            // 中文标注（局部变量）：`slowestChunkMs`，含义：用于表示slowest、区块、ms。
            double slowestChunkMs = chunkGenStatsChunkMaxNanos / 1_000_000.0; // meaning
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
        // 中文标注（局部变量）：`asyncGeneratedChunks`，含义：用于表示async、generated、区块集合。
        long asyncGeneratedChunks; // meaning
        // 中文标注（局部变量）：`asyncGenTotalNanos`，含义：用于表示async、gen、total、nanos。
        long asyncGenTotalNanos; // meaning
        // 中文标注（局部变量）：`asyncGenMaxNanos`，含义：用于表示async、gen、最大、nanos。
        long asyncGenMaxNanos; // meaning
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

    // 中文标注（方法）：`close`，参数：无；用途：执行close相关逻辑。
    @Override
    public synchronized void close() {
        if (closing) {
            return;
        }
        closing = true;
        if (chunkGenerationPool != null) {
            List<Runnable> cancelledTasks = chunkGenerationPool.shutdownNow(); // meaning
            for (Runnable cancelledTask : cancelledTasks) {
                if (cancelledTask instanceof ChunkGenerationTask chunkGenerationTask) {
                    chunkGenerationTask.cancel();
                }
            }
        }
        pendingChunkGeneration.clear();
        pendingChunkGenerationSet.clear();
        readyGeneratedChunks.clear();
        inFlightChunkGeneration.clear();
    }

    // 中文标注（方法）：`chunkKey`，参数：chunkX、chunkZ；用途：执行区块、键相关逻辑。
    // 中文标注（参数）：`chunkX`，含义：用于表示区块、X坐标。
    // 中文标注（参数）：`chunkZ`，含义：用于表示区块、Z坐标。
    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xffff_ffffL);
    }

    // 中文标注（方法）：`booleanProperty`，参数：key、defaultValue；用途：执行boolean、属性相关逻辑。
    // 中文标注（参数）：`key`，含义：用于表示键。
    // 中文标注（参数）：`defaultValue`，含义：用于表示默认、值。
    private static boolean booleanProperty(String key, boolean defaultValue) {
        // 中文标注（局部变量）：`raw`，含义：用于表示raw。
        String raw = System.getProperty(key); // meaning
        if (raw == null) {
            return defaultValue;
        }
        // 中文标注（局部变量）：`normalized`，含义：用于表示normalized。
        String normalized = raw.trim().toLowerCase(); // meaning
        if (normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on")) {
            return true;
        }
        if (normalized.equals("0") || normalized.equals("false") || normalized.equals("no") || normalized.equals("off")) {
            return false;
        }
        return defaultValue;
    }

    // 中文标注（方法）：`intProperty`，参数：key、defaultValue；用途：执行int、属性相关逻辑。
    // 中文标注（参数）：`key`，含义：用于表示键。
    // 中文标注（参数）：`defaultValue`，含义：用于表示默认、值。
    private static int intProperty(String key, int defaultValue) {
        // 中文标注（局部变量）：`raw`，含义：用于表示raw。
        String raw = System.getProperty(key); // meaning
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        // 中文标注（异常参数）：`ignored`，含义：用于表示ignored。
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    // 中文标注（记录类）：`GeneratedChunk`，职责：封装generated、区块相关逻辑。
    // 中文标注（字段）：`chunk`，含义：用于表示区块。
    // 中文标注（字段）：`generationNanos`，含义：用于表示generation、nanos。
    private record GeneratedChunk(Chunk chunk, long generationNanos) {
    }

    // 中文标注（类）：`ChunkGenerationTask`，职责：封装可取消的异步区块生成任务，确保 close/shutdown 不遗漏状态清理。
    private static final class ChunkGenerationTask implements Runnable {
        // 中文标注（字段）：`delegate`，含义：用于表示delegate。
        private final Runnable delegate; // meaning
        // 中文标注（字段）：`cancelAction`，含义：用于表示取消动作。
        private final Runnable cancelAction; // meaning
        // 中文标注（字段）：`completedOrCancelled`，含义：用于表示已完成或已取消。
        private final AtomicBoolean completedOrCancelled = new AtomicBoolean(); // meaning

        // 中文标注（构造方法）：`ChunkGenerationTask`，参数：delegate、cancelAction；用途：初始化`ChunkGenerationTask`实例。
        // 中文标注（参数）：`delegate`，含义：用于表示delegate。
        // 中文标注（参数）：`cancelAction`，含义：用于表示取消动作。
        private ChunkGenerationTask(Runnable delegate, Runnable cancelAction) {
            this.delegate = delegate;
            this.cancelAction = cancelAction;
        }

        // 中文标注（方法）：`run`，参数：无；用途：执行区块生成任务。
        @Override
        public void run() {
            if (!completedOrCancelled.compareAndSet(false, true)) {
                return;
            }
            delegate.run();
        }

        // 中文标注（方法）：`cancel`，参数：无；用途：取消尚未执行任务并清理 inFlight/计数。
        private void cancel() {
            if (!completedOrCancelled.compareAndSet(false, true)) {
                return;
            }
            if (cancelAction != null) {
                cancelAction.run();
            }
        }
    }
}
