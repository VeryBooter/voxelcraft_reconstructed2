package dev.voxelcraft.client.render;

import dev.voxelcraft.client.GameClient;
import dev.voxelcraft.client.player.PlayerController;
import dev.voxelcraft.client.render.ChunkMesher.ChunkMeshData;
import dev.voxelcraft.client.render.ChunkMesher.ChunkSnapshot;
import dev.voxelcraft.client.render.ChunkRenderSystem.RenderStats;
import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.ChunkPos;
import dev.voxelcraft.core.world.Section;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.ARBMultiDrawIndirect;
import org.lwjgl.opengl.GL43;

import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_COLOR_ARRAY;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_CW;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_CCW;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glColorPointer;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glFrontFace;
import static org.lwjgl.opengl.GL11.glDisableClientState;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL11.glFrustum;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScaled;
import static org.lwjgl.opengl.GL11.glTranslated;
import static org.lwjgl.opengl.GL11.glVertexPointer;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL11.glColorMask;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL15.GL_SAMPLES_PASSED;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBeginQuery;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteQueries;
import static org.lwjgl.opengl.GL15.glEndQuery;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL15.glGetQueryObjecti;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;

public final class GpuChunkRenderer implements AutoCloseable {
    private static final float VERTICAL_FOV_DEGREES = 75.0f;
    private static final double NEAR_PLANE = 0.05;
    private static final double FAR_PLANE = 320.0;

    private static final int RENDER_CHUNK_RADIUS = 5;
    private static final int MIN_UPLOADS_PER_FRAME = 1;
    private static final int DEFAULT_UPLOADS_PER_FRAME = 3;
    private static final int MAX_UPLOADS_PER_FRAME = 6;
    private static final int MIN_MESH_SUBMITS_PER_FRAME = 1;
    private static final int DEFAULT_MESH_SUBMITS_PER_FRAME = 6;
    private static final int MAX_MESH_SUBMITS_PER_FRAME = 12;
    private static final int BYTE_BUFFER_POOL_BUCKET_LIMIT = 8;
    private static final double FRAME_TIME_EMA_ALPHA = 0.12;
    private static final double UPLOAD_BUDGET_REDUCE_MS = 14.5;
    private static final double UPLOAD_BUDGET_INCREASE_MS = 9.5;
    private static final double UPLOAD_BUDGET_RECOVER_MS = 8.0;
    private static final double DEFAULT_UPLOAD_TIME_BUDGET_MS = 1.25;
    private static final int RECENTLY_VISIBLE_BIAS_FRAMES = 45;
    private static final double PRIORITY_FORWARD_BIAS_WEIGHT = 48.0;
    private static final double PRIORITY_RECENT_VISIBLE_BIAS = 24.0;
    private static final int OCCLUSION_HIDDEN_HYSTERESIS_FRAMES = 2;
    private static final int OCCLUSION_RESAMPLE_INTERVAL_FRAMES = 10;
    private static final int OCCLUSION_MAX_QUERIES_PER_FRAME = 96;
    private static final int DEFAULT_OCCLUSION_RESULT_POLL_BUDGET = 192;
    private static final int DEFAULT_LOD_START_CHUNK_DISTANCE = 4;
    private static final int DEFAULT_LOD_HYSTERESIS_CHUNKS = 1;
    private static final int DEFAULT_SHARED_ARENA_VERTEX_MB = 128;
    private static final int DEFAULT_SHARED_ARENA_INDEX_MB = 64;
    private static final int VERTEX_STRIDE_BYTES = ChunkMesher.GPU_VERTEX_STRIDE_BYTES;
    private static final long POSITION_OFFSET_BYTES = 0L;
    private static final long COLOR_OFFSET_BYTES = ChunkMesher.GPU_COLOR_OFFSET_BYTES;
    private static final String AMBIENT_VERTEX_SHADER_SOURCE = """
        #version 120
        uniform float uAmbient;
        varying vec4 vColor;
        void main() {
            gl_Position = ftransform();
            vColor = gl_Color * vec4(uAmbient, uAmbient, uAmbient, 1.0);
        }
        """;
    private static final String AMBIENT_FRAGMENT_SHADER_SOURCE = """
        #version 120
        varying vec4 vColor;
        void main() {
            gl_FragColor = vColor;
        }
        """;

    private final ChunkMesher mesher = new ChunkMesher();
    private final Frustum frustum = new Frustum();
    private final GpuFeatureFlags features = GpuFeatureFlags.load();
    private final Map<ChunkPos, GpuChunk> gpuChunks = new HashMap<>();
    private final PriorityBlockingQueue<QueuedMeshUpload> uploadQueue = new PriorityBlockingQueue<>(
        64,
        Comparator
            .comparingDouble(QueuedMeshUpload::priorityKey)
            .thenComparingLong(QueuedMeshUpload::sequence)
    );
    private final ConcurrentHashMap<ChunkPos, Long> inFlightVersion = new ConcurrentHashMap<>();
    private final AtomicInteger meshingJobsInFlight = new AtomicInteger();
    private final AtomicLong uploadSequence = new AtomicLong();
    private final AtomicLong meshTaskSequence = new AtomicLong();
    private final ThreadPoolExecutor meshPool;
    private final int meshWorkerCount;
    private final DirectByteBufferPool uploadBufferPool = new DirectByteBufferPool(BYTE_BUFFER_POOL_BUCKET_LIMIT);
    private final ArrayList<Chunk> scratchLoadedChunks = new ArrayList<>();
    private final ArrayList<Chunk> scratchChunksInRange = new ArrayList<>();
    private final HashSet<ChunkPos> scratchActiveChunkPositions = new HashSet<>();
    private final ArrayList<ChunkPos> scratchPruneRemovals = new ArrayList<>();
    private final ArrayList<GpuChunk> scratchOcclusionCandidates = new ArrayList<>();
    private final ArrayList<GpuChunk> scratchMdiChunks = new ArrayList<>();
    private final HashMap<ChunkPos, Long> recentlyVisibleFrame = new HashMap<>();
    private final HashMap<ChunkPos, Integer> lodSelectionCache = new HashMap<>();
    private final double[] frameTimeWindowMs = new double[512];
    private final double[] frameTimeSortScratchMs = new double[512];

    private long perfWindowStartNanos = System.nanoTime();
    private int perfFrames;
    private long perfUploadJobs;
    private long perfUploadBytes;
    private long perfUploadDropped;
    private long perfBufferReallocs;
    private long perfBufferOrphans;
    private long perfBufferSubDatas;
    private double perfMeshingQueueTopPriority = Double.NaN;
    private double perfUploadQueueTopPriority = Double.NaN;
    private long perfVisibleLatencyNanosTotal;
    private int perfVisibleLatencySamples;
    private long perfOcclusionQueries;
    private long perfOcclusionCulledChunks;
    private long perfOcclusionQueryPolls;
    private long perfOcclusionQueryDeferredPolls;
    private long perfOcclusionQueryReadStalls;
    private long perfMdiBatches;
    private long perfMdiChunks;
    private long perfMdiFallbackChunks;
    private long perfDrawElementsChunks;
    private long perfSharedArenaDrawChunks;
    private long perfLocalDrawChunks;
    private long perfMdiCommandBytes;
    private long perfMdiCommandBufferReallocs;
    private long perfMdiCommandBufferOrphans;
    private long perfSharedArenaAllocFailures;
    private long perfSharedArenaFallbackUploads;
    private int adaptiveUploadsPerFrame = DEFAULT_UPLOADS_PER_FRAME;
    private int adaptiveMeshSubmitsPerFrame = DEFAULT_MESH_SUBMITS_PER_FRAME;
    private double renderCpuMsEma = -1.0;
    private int frameTimeWindowIndex;
    private int frameTimeWindowCount;
    private long frameSequence;
    private long lastMeshingSubmitNanos;
    private long lastUploadQueueDrainNanos;
    private long lastDrawLoopNanos;
    private boolean glCapabilitiesLogged;
    private boolean supportsMdiCore43;
    private boolean supportsMdiArb;
    private boolean supportsMdi;
    private boolean supportsOcclusionQuery;
    private boolean supportsPersistentMapping;
    private OcclusionBoxMesh occlusionBoxMesh;
    private SharedChunkBufferArena sharedChunkBufferArena;
    private int mdiIndirectBufferId;
    private int mdiIndirectBufferCapacityBytes;
    private ByteBuffer mdiCommandUploadBytes;
    private int[] mdiCommandScratch = new int[5 * 64];
    private int ambientShaderProgramId;
    private int ambientUniformLocation = -1;
    private volatile String latestTitleStats = "gpu init";

    public GpuChunkRenderer() {
        meshWorkerCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        meshPool = new ThreadPoolExecutor(
            meshWorkerCount,
            meshWorkerCount,
            0L,
            TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable, "voxelcraft-chunk-mesher");
                thread.setDaemon(true);
                return thread;
            }
        );
    }

    public RenderStats render(int width, int height, GameClient gameClient) {
        long renderStarted = System.nanoTime();
        initializeCapabilitiesIfNeeded();
        ensureAmbientShaderProgram();
        frameSequence++;
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);

        PlayerController player = gameClient.playerController();
        ClientWorldView worldView = gameClient.worldView();
        float ambient = gameClient.ambientLight();

        glViewport(0, 0, safeWidth, safeHeight);

        float skyR = 0.31f * ambient;
        float skyG = 0.53f * ambient;
        float skyB = 0.78f * ambient;
        glClearColor(skyR, skyG, skyB, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        // We use a Z reflection in the view transform to match the engine's +Z-forward camera convention.
        // Reflection flips winding, so front-face must be switched to CW for correct culling.
        glFrontFace(GL_CW);

        configureProjection(safeWidth, safeHeight);
        configureCamera(player);

        double aspect = (double) safeWidth / (double) safeHeight;
        frustum.setCamera(
            player.eyeX(),
            player.eyeY(),
            player.eyeZ(),
            player.yaw(),
            player.pitch(),
            VERTICAL_FOV_DEGREES,
            aspect,
            NEAR_PLANE,
            FAR_PLANE
        );

        ChunkFrameSet frameSet = collectChunksInRange(worldView, player);
        long meshingSubmitStarted = System.nanoTime();
        submitMeshJobsForDirtyChunks(worldView, frameSet.chunks(), player);
        lastMeshingSubmitNanos = System.nanoTime() - meshingSubmitStarted;

        long uploadDrainStarted = System.nanoTime();
        processUploadQueue(worldView, player, adaptiveUploadsPerFrame, features.uploadTimeBudgetMs());
        lastUploadQueueDrainNanos = System.nanoTime() - uploadDrainStarted;
        pruneGpuChunks(frameSet.positions());

        long drawLoopStarted = System.nanoTime();
        FrameStats frameStats = renderVisibleChunks(frameSet.chunks(), ambient);
        lastDrawLoopNanos = System.nanoTime() - drawLoopStarted;
        long renderCpuNanos = System.nanoTime() - renderStarted;
        recordFrameTime(renderCpuNanos);
        updateAdaptiveBudgets(renderCpuNanos);
        emitPerfLine(frameStats, frameSet.chunks().size());

        int approxFaces = frameStats.totalTriangles / 2;
        glFrontFace(GL_CCW);
        return new RenderStats(approxFaces, frameStats.visibleChunks, approxFaces);
    }

    @Override
    public void close() {
        meshPool.shutdownNow();
        for (GpuChunk gpuChunk : gpuChunks.values()) {
            gpuChunk.dispose(sharedChunkBufferArena);
        }
        gpuChunks.clear();
        QueuedMeshUpload pending;
        while ((pending = uploadQueue.poll()) != null) {
            pending.meshData().releaseBuffers(uploadBufferPool);
        }
        inFlightVersion.clear();
        recentlyVisibleFrame.clear();
        lodSelectionCache.clear();
        if (occlusionBoxMesh != null) {
            occlusionBoxMesh.dispose();
            occlusionBoxMesh = null;
        }
        if (sharedChunkBufferArena != null) {
            sharedChunkBufferArena.dispose();
            sharedChunkBufferArena = null;
        }
        if (mdiIndirectBufferId != 0) {
            glDeleteBuffers(mdiIndirectBufferId);
            mdiIndirectBufferId = 0;
            mdiIndirectBufferCapacityBytes = 0;
        }
        if (ambientShaderProgramId != 0) {
            GL20.glDeleteProgram(ambientShaderProgramId);
            ambientShaderProgramId = 0;
            ambientUniformLocation = -1;
        }
        mdiCommandUploadBytes = null;
        uploadBufferPool.clear();
    }

    private ChunkFrameSet collectChunksInRange(ClientWorldView worldView, PlayerController player) {
        worldView.copyLoadedChunksInto(scratchLoadedChunks);
        scratchChunksInRange.clear();
        scratchActiveChunkPositions.clear();

        int playerChunkX = Math.floorDiv((int) Math.floor(player.x()), Section.SIZE);
        int playerChunkZ = Math.floorDiv((int) Math.floor(player.z()), Section.SIZE);

        for (Chunk chunk : scratchLoadedChunks) {
            int dx = Math.abs(chunk.pos().x() - playerChunkX);
            int dz = Math.abs(chunk.pos().z() - playerChunkZ);
            if (dx > RENDER_CHUNK_RADIUS || dz > RENDER_CHUNK_RADIUS) {
                continue;
            }
            scratchChunksInRange.add(chunk);
            scratchActiveChunkPositions.add(chunk.pos());
        }

        double playerX = player.x();
        double playerZ = player.z();
        if (features.priorityForwardBias() || features.priorityRecentlyVisibleBias()) {
            double lookX = player.lookDirX();
            double lookZ = player.lookDirZ();
            scratchChunksInRange.sort(Comparator.comparingDouble(chunk -> chunkPriorityKey(chunk, playerX, playerZ, lookX, lookZ)));
        } else {
            scratchChunksInRange.sort(Comparator.comparingDouble(chunk -> chunkDistanceSq(chunk, playerX, playerZ)));
        }

        return new ChunkFrameSet(scratchChunksInRange, scratchActiveChunkPositions);
    }

    private void submitMeshJobsForDirtyChunks(ClientWorldView worldView, List<Chunk> chunksInRange, PlayerController player) {
        double playerX = player.x();
        double playerY = player.y();
        double playerZ = player.z();
        double lookX = player.lookDirX();
        double lookZ = player.lookDirZ();

        int minY = mesher.gpuMinY(playerY);
        int maxY = mesher.gpuMaxY(playerY);
        int submitted = 0;

        for (Chunk chunk : chunksInRange) {
            if (submitted >= adaptiveMeshSubmitsPerFrame) {
                break;
            }

            ChunkPos pos = chunk.pos();
            long currentVersion = chunk.version();
            int desiredLodLevel = resolveChunkLodLevel(chunk, playerX, playerZ);
            GpuChunk gpuChunk = gpuChunks.get(pos);
            if (gpuChunk != null && gpuChunk.versionUploaded == currentVersion && gpuChunk.lodLevelUploaded == desiredLodLevel) {
                continue;
            }

            long desiredBuildKey = meshBuildKey(currentVersion, desiredLodLevel);
            Long inFlight = inFlightVersion.get(pos);
            if (inFlight != null && inFlight == desiredBuildKey) {
                continue;
            }
            if (inFlight != null) {
                continue;
            }

            if (inFlightVersion.putIfAbsent(pos, desiredBuildKey) != null) {
                continue;
            }

            ChunkSnapshot snapshot = mesher.captureChunkSnapshot(worldView, chunk, minY, maxY);
            double priorityKey = chunkPriorityKey(chunk, playerX, playerZ, lookX, lookZ);
            long submittedNanos = System.nanoTime();
            meshingJobsInFlight.incrementAndGet();
            try {
                meshPool.execute(new PrioritizedMeshTask(priorityKey, meshTaskSequence.getAndIncrement(), () -> {
                    try {
                        ChunkMeshData meshData = mesher.buildChunkMesh(snapshot, uploadBufferPool, desiredLodLevel);
                        uploadQueue.add(
                            new QueuedMeshUpload(
                                meshData,
                                priorityKey,
                                uploadSequence.getAndIncrement(),
                                submittedNanos
                            )
                        );
                    } finally {
                        meshingJobsInFlight.decrementAndGet();
                    }
                }));
                submitted++;
            } catch (RejectedExecutionException rejected) {
                inFlightVersion.remove(pos, desiredBuildKey);
                meshingJobsInFlight.decrementAndGet();
            }
        }
    }

    private void processUploadQueue(ClientWorldView worldView, PlayerController player, int maxUploads, double uploadBudgetMs) {
        PrioritizedMeshTask meshHead = peekMeshingTask();
        perfMeshingQueueTopPriority = meshHead == null ? Double.NaN : meshHead.priorityKey();
        QueuedMeshUpload queuedHead = uploadQueue.peek();
        perfUploadQueueTopPriority = queuedHead == null ? Double.NaN : queuedHead.priorityKey();

        long startedNanos = System.nanoTime();
        int processed = 0;
        while (processed < maxUploads) {
            if (processed > 0 && uploadBudgetMs > 0.0) {
                double elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000.0;
                if (elapsedMs >= uploadBudgetMs) {
                    break;
                }
            }
            QueuedMeshUpload queuedUpload = uploadQueue.poll();
            if (queuedUpload == null) {
                break;
            }
            processed++;
            ChunkMeshData meshData = queuedUpload.meshData();

            inFlightVersion.remove(meshData.pos(), meshBuildKey(meshData.version(), meshData.lodLevel()));
            Chunk chunk = worldView.getChunk(meshData.pos());
            if (chunk == null) {
                perfUploadDropped++;
                meshData.releaseBuffers(uploadBufferPool);
                continue;
            }
            if (chunk.version() != meshData.version()) {
                perfUploadDropped++;
                meshData.releaseBuffers(uploadBufferPool);
                continue;
            }
            int desiredLodLevel = resolveChunkLodLevel(chunk, player.x(), player.z());
            if (desiredLodLevel != meshData.lodLevel()) {
                perfUploadDropped++;
                meshData.releaseBuffers(uploadBufferPool);
                continue;
            }
            try {
                recordUploadStats(uploadChunkMesh(meshData, queuedUpload.submittedNanos()));
            } finally {
                meshData.releaseBuffers(uploadBufferPool);
            }
        }
    }

    private void recordUploadStats(UploadStats stats) {
        perfUploadJobs += stats.uploadJobs();
        perfUploadBytes += stats.uploadBytes();
        perfBufferReallocs += stats.bufferReallocs();
        perfBufferOrphans += stats.bufferOrphans();
        perfBufferSubDatas += stats.bufferSubDatas();
    }

    private UploadStats uploadChunkMesh(ChunkMeshData meshData, long submittedNanos) {
        GpuChunk gpuChunk = gpuChunks.computeIfAbsent(meshData.pos(), unused -> new GpuChunk(meshData.pos()));
        UploadStats stats = gpuChunk.upload(meshData, submittedNanos, features.orphaningUpload(), sharedChunkBufferArena);
        if (gpuChunk.lastUploadSharedArenaAllocFailure) {
            perfSharedArenaAllocFailures++;
        }
        if (gpuChunk.lastUploadSharedArenaFallback) {
            perfSharedArenaFallbackUploads++;
        }
        return stats;
    }

    private void pruneGpuChunks(Set<ChunkPos> activePositions) {
        if (gpuChunks.isEmpty()) {
            return;
        }

        scratchPruneRemovals.clear();
        for (ChunkPos pos : gpuChunks.keySet()) {
            if (!activePositions.contains(pos)) {
                scratchPruneRemovals.add(pos);
            }
        }

        for (ChunkPos pos : scratchPruneRemovals) {
            GpuChunk removed = gpuChunks.remove(pos);
            if (removed != null) {
                removed.dispose(sharedChunkBufferArena);
            }
            inFlightVersion.remove(pos);
            recentlyVisibleFrame.remove(pos);
            lodSelectionCache.remove(pos);
        }
        scratchPruneRemovals.clear();
    }

    private FrameStats renderVisibleChunks(List<Chunk> chunksInRange, float ambient) {
        int drawCalls = 0;
        int visibleChunks = 0;
        int lodVisibleChunks = 0;
        int totalTriangles = 0;
        int stateChanges = 4; // enable/disable client states (vertex+color)
        int boundArrayBuffer = 0;
        int boundElementBuffer = 0;
        int boundIndirectBuffer = 0;
        boolean occlusionEnabled = features.occlusionQuery() && supportsOcclusionQuery && occlusionBoxMesh != null;
        boolean mdiEnabled = features.mdi() && supportsMdi && sharedChunkBufferArena != null;
        int occlusionResultPollBudget = occlusionEnabled ? Math.max(0, features.occlusionResultPollBudget()) : 0;
        scratchOcclusionCandidates.clear();
        scratchMdiChunks.clear();

        GL20.glUseProgram(ambientShaderProgramId);
        GL20.glUniform1f(ambientUniformLocation, ambient);
        stateChanges += 2;

        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);

        for (Chunk chunk : chunksInRange) {
            GpuChunk gpuChunk = gpuChunks.get(chunk.pos());
            if (gpuChunk == null || !gpuChunk.valid || gpuChunk.indexCount <= 0) {
                continue;
            }

            if (!frustum.isAabbVisible(
                gpuChunk.minX,
                gpuChunk.minY,
                gpuChunk.minZ,
                gpuChunk.maxX,
                gpuChunk.maxY,
                gpuChunk.maxZ
            )) {
                continue;
            }

            if (gpuChunk.hasPendingOcclusionQuery()) {
                if (occlusionResultPollBudget > 0) {
                    occlusionResultPollBudget--;
                    perfOcclusionQueryPolls++;
                    if (gpuChunk.pollOcclusionQueryResult()) {
                        perfOcclusionQueryReadStalls++;
                    }
                } else {
                    perfOcclusionQueryDeferredPolls++;
                }
            }
            if (occlusionEnabled) {
                scratchOcclusionCandidates.add(gpuChunk);
                if (!gpuChunk.shouldDrawBasedOnOcclusion(frameSequence)) {
                    perfOcclusionCulledChunks++;
                    continue;
                }
            }

            visibleChunks++;
            if (gpuChunk.lodLevelUploaded > 0) {
                lodVisibleChunks++;
            }
            totalTriangles += gpuChunk.triangleCount;
            recentlyVisibleFrame.put(chunk.pos(), frameSequence);
            if (gpuChunk.lastVisibleLatencyRecordedVersion != gpuChunk.versionUploaded && gpuChunk.lastMeshSubmitNanos > 0L) {
                perfVisibleLatencyNanosTotal += Math.max(0L, System.nanoTime() - gpuChunk.lastMeshSubmitNanos);
                perfVisibleLatencySamples++;
                gpuChunk.lastVisibleLatencyRecordedVersion = gpuChunk.versionUploaded;
            }

            if (mdiEnabled && gpuChunk.usesSharedArena && sharedChunkBufferArena != null) {
                scratchMdiChunks.add(gpuChunk);
                continue;
            }

            if (mdiEnabled) {
                perfMdiFallbackChunks++;
            }
            perfDrawElementsChunks++;
            if (gpuChunk.usesSharedArena) {
                perfSharedArenaDrawChunks++;
            } else {
                perfLocalDrawChunks++;
            }

            int drawArrayBuffer = gpuChunk.usesSharedArena && sharedChunkBufferArena != null
                ? sharedChunkBufferArena.vboId()
                : gpuChunk.vboId;
            int drawElementBuffer = gpuChunk.usesSharedArena && sharedChunkBufferArena != null
                ? sharedChunkBufferArena.iboId()
                : gpuChunk.iboId;
            if (boundArrayBuffer != drawArrayBuffer) {
                glBindBuffer(GL_ARRAY_BUFFER, drawArrayBuffer);
                boundArrayBuffer = drawArrayBuffer;
                stateChanges++;
            }
            if (boundElementBuffer != drawElementBuffer) {
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, drawElementBuffer);
                boundElementBuffer = drawElementBuffer;
                stateChanges++;
            }
            long vertexBaseOffset = gpuChunk.usesSharedArena ? gpuChunk.sharedVertexOffsetBytes : 0L;
            long indexBaseOffset = gpuChunk.usesSharedArena ? gpuChunk.sharedIndexOffsetBytes : 0L;
            glVertexPointer(3, GL_FLOAT, VERTEX_STRIDE_BYTES, vertexBaseOffset + POSITION_OFFSET_BYTES);
            glColorPointer(4, GL_UNSIGNED_BYTE, VERTEX_STRIDE_BYTES, vertexBaseOffset + COLOR_OFFSET_BYTES);
            glDrawElements(GL_TRIANGLES, gpuChunk.indexCount, GL_UNSIGNED_INT, indexBaseOffset);
            drawCalls++;
        }

        if (mdiEnabled && !scratchMdiChunks.isEmpty()) {
            int sharedVbo = sharedChunkBufferArena.vboId();
            int sharedIbo = sharedChunkBufferArena.iboId();
            if (boundArrayBuffer != sharedVbo) {
                glBindBuffer(GL_ARRAY_BUFFER, sharedVbo);
                boundArrayBuffer = sharedVbo;
                stateChanges++;
            }
            if (boundElementBuffer != sharedIbo) {
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sharedIbo);
                boundElementBuffer = sharedIbo;
                stateChanges++;
            }
            if (mdiIndirectBufferId == 0) {
                mdiIndirectBufferId = glGenBuffers();
            }
            if (boundIndirectBuffer != mdiIndirectBufferId) {
                glBindBuffer(GL_DRAW_INDIRECT_BUFFER, mdiIndirectBufferId);
                boundIndirectBuffer = mdiIndirectBufferId;
                stateChanges++;
            }
            glVertexPointer(3, GL_FLOAT, VERTEX_STRIDE_BYTES, POSITION_OFFSET_BYTES);
            glColorPointer(4, GL_UNSIGNED_BYTE, VERTEX_STRIDE_BYTES, COLOR_OFFSET_BYTES);
            uploadMdiCommandsAndDraw(scratchMdiChunks);
            perfMdiBatches++;
            perfMdiChunks += scratchMdiChunks.size();
            perfSharedArenaDrawChunks += scratchMdiChunks.size();
            drawCalls++;
        }

        if (boundArrayBuffer != 0) {
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            stateChanges++;
        }
        if (boundElementBuffer != 0) {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            stateChanges++;
        }
        if (boundIndirectBuffer != 0) {
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
            stateChanges++;
        }
        GL20.glUseProgram(0);
        stateChanges++;
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);

        if (occlusionEnabled && !scratchOcclusionCandidates.isEmpty()) {
            stateChanges += issueOcclusionQueries(scratchOcclusionCandidates);
        }

        return new FrameStats(drawCalls, visibleChunks, lodVisibleChunks, totalTriangles, stateChanges);
    }

    private int issueOcclusionQueries(List<GpuChunk> candidates) {
        if (candidates.isEmpty() || occlusionBoxMesh == null) {
            return 0;
        }

        int stateChanges = 0;
        int issued = 0;

        glColorMask(false, false, false, false);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        glEnableClientState(GL_VERTEX_ARRAY);
        stateChanges += 4;

        glBindBuffer(GL_ARRAY_BUFFER, occlusionBoxMesh.vboId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, occlusionBoxMesh.iboId);
        glVertexPointer(3, GL_FLOAT, 3 * Float.BYTES, 0L);
        stateChanges += 2;

        for (GpuChunk chunk : candidates) {
            if (issued >= OCCLUSION_MAX_QUERIES_PER_FRAME) {
                break;
            }
            if (!chunk.shouldIssueOcclusionQuery(frameSequence)) {
                continue;
            }
            int queryId = chunk.ensureOcclusionQueryId();
            glPushMatrix();
            glTranslated(chunk.minX, chunk.minY, chunk.minZ);
            glScaled(
                Math.max(1.0e-4, chunk.maxX - chunk.minX),
                Math.max(1.0e-4, chunk.maxY - chunk.minY),
                Math.max(1.0e-4, chunk.maxZ - chunk.minZ)
            );
            glBeginQuery(GL_SAMPLES_PASSED, queryId);
            glDrawElements(GL_TRIANGLES, occlusionBoxMesh.indexCount, GL_UNSIGNED_INT, 0L);
            glEndQuery(GL_SAMPLES_PASSED);
            glPopMatrix();
            chunk.markOcclusionQueryIssued(frameSequence);
            issued++;
            perfOcclusionQueries++;
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glDisableClientState(GL_VERTEX_ARRAY);
        glEnable(GL_CULL_FACE);
        glDepthMask(true);
        glColorMask(true, true, true, true);
        stateChanges += 4;
        return stateChanges;
    }

    private void uploadMdiCommandsAndDraw(List<GpuChunk> chunks) {
        int drawCount = chunks.size();
        if (drawCount <= 0 || mdiIndirectBufferId == 0) {
            return;
        }
        int commandInts = drawCount * 5;
        ensureMdiCommandScratchCapacity(commandInts);

        int write = 0;
        for (GpuChunk chunk : chunks) {
            mdiCommandScratch[write++] = chunk.indexCount;
            mdiCommandScratch[write++] = 1; // instanceCount
            mdiCommandScratch[write++] = (int) (chunk.sharedIndexOffsetBytes / Integer.BYTES); // firstIndex (uint indices)
            mdiCommandScratch[write++] = (int) (chunk.sharedVertexOffsetBytes / VERTEX_STRIDE_BYTES); // baseVertex
            mdiCommandScratch[write++] = 0; // baseInstance
        }

        int commandBytes = commandInts * Integer.BYTES;
        ensureMdiUploadBufferCapacity(commandBytes);
        IntBuffer commandIntsBuffer = mdiCommandUploadBytes.asIntBuffer();
        commandIntsBuffer.clear();
        commandIntsBuffer.put(mdiCommandScratch, 0, commandInts);
        mdiCommandUploadBytes.position(0);
        mdiCommandUploadBytes.limit(commandBytes);

        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, mdiIndirectBufferId);
        if (commandBytes > mdiIndirectBufferCapacityBytes) {
            glBufferData(GL_DRAW_INDIRECT_BUFFER, (long) commandBytes, GL_DYNAMIC_DRAW);
            mdiIndirectBufferCapacityBytes = commandBytes;
            perfMdiCommandBufferReallocs++;
        } else if (features.orphaningUpload()) {
            glBufferData(GL_DRAW_INDIRECT_BUFFER, (long) mdiIndirectBufferCapacityBytes, GL_DYNAMIC_DRAW);
            perfMdiCommandBufferOrphans++;
        }
        glBufferSubData(GL_DRAW_INDIRECT_BUFFER, 0L, mdiCommandUploadBytes);
        perfMdiCommandBytes += commandBytes;

        if (supportsMdiCore43) {
            GL43.glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0L, drawCount, 0);
        } else {
            ARBMultiDrawIndirect.glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0L, drawCount, 0);
        }
    }

    private void ensureMdiCommandScratchCapacity(int requiredInts) {
        if (mdiCommandScratch.length >= requiredInts) {
            return;
        }
        int newCapacity = Math.max(64, mdiCommandScratch.length);
        while (newCapacity < requiredInts) {
            newCapacity *= 2;
        }
        mdiCommandScratch = Arrays.copyOf(mdiCommandScratch, newCapacity);
    }

    private void ensureMdiUploadBufferCapacity(int requiredBytes) {
        if (mdiCommandUploadBytes != null && mdiCommandUploadBytes.capacity() >= requiredBytes) {
            return;
        }
        int newCapacity = Math.max(20 * 64, requiredBytes);
        mdiCommandUploadBytes = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder());
    }

    private void emitPerfLine(FrameStats frameStats, int chunksInRange) {
        long now = System.nanoTime();
        perfFrames++;
        long elapsed = now - perfWindowStartNanos;
        if (elapsed < 1_000_000_000L) {
            return;
        }

        double fps = (perfFrames * 1_000_000_000.0) / elapsed;
        FrameTimePercentiles percentiles = frameTimePercentiles();
        double avgLatencyMs = perfVisibleLatencySamples == 0
            ? 0.0
            : (perfVisibleLatencyNanosTotal / (double) perfVisibleLatencySamples) / 1_000_000.0;
        latestTitleStats = String.format(
            "p95 %.1fms p99 %.1fms uq %d ub %d mb %d lod %d oq %d oc %d qp %d/%d qs %d mdi %d/%d fb %d",
            percentiles.p95Ms(),
            percentiles.p99Ms(),
            uploadQueue.size(),
            adaptiveUploadsPerFrame,
            adaptiveMeshSubmitsPerFrame,
            frameStats.lodVisibleChunks,
            perfOcclusionQueries,
            perfOcclusionCulledChunks,
            perfOcclusionQueryPolls,
            perfOcclusionQueryDeferredPolls,
            perfOcclusionQueryReadStalls,
            perfMdiBatches,
            perfMdiChunks,
            perfMdiFallbackChunks
        );
        System.out.printf(
            "[gpu-perf] fps=%.1f drawCalls=%d stateChanges=%d visibleChunks=%d lodVisibleChunks=%d triangles=%d uploadQueueSize=%d meshingJobsInFlight=%d totalChunksInRange=%d uploadBudget=%d uploadBudgetMs=%.2f meshSubmitBudget=%d renderCpuMsEma=%.2f p50=%.2fms p95=%.2fms p99=%.2fms meshingQueueTopPriority=%.2f uploadQueueTopPriority=%.2f avgLatencyToVisibleMesh=%.2fms occlusionQueries=%d occlusionCulled=%d queryPolls=%d queryPollDeferred=%d queryReadStalls=%d mdiBatches=%d mdiChunks=%d mdiFallbackChunks=%d drawElementsChunks=%d sharedArenaDrawChunks=%d localDrawChunks=%d mdiCmdMB=%.3f mdiCmdReallocs=%d mdiCmdOrphans=%d sharedArenaAllocFailures=%d sharedArenaFallbackUploads=%d sharedArenaVertexUsedMB=%.2f sharedArenaIndexUsedMB=%.2f uploadJobs=%d uploadMB=%.2f droppedUploads=%d bufferReallocs=%d bufferOrphans=%d subDatas=%d%n",
            fps,
            frameStats.drawCalls,
            frameStats.stateChanges,
            frameStats.visibleChunks,
            frameStats.lodVisibleChunks,
            frameStats.totalTriangles,
            uploadQueue.size(),
            meshingJobsInFlight.get(),
            chunksInRange,
            adaptiveUploadsPerFrame,
            features.uploadTimeBudgetMs(),
            adaptiveMeshSubmitsPerFrame,
            renderCpuMsEma < 0.0 ? 0.0 : renderCpuMsEma,
            percentiles.p50Ms(),
            percentiles.p95Ms(),
            percentiles.p99Ms(),
            Double.isNaN(perfMeshingQueueTopPriority) ? 0.0 : perfMeshingQueueTopPriority,
            Double.isNaN(perfUploadQueueTopPriority) ? 0.0 : perfUploadQueueTopPriority,
            avgLatencyMs,
            perfOcclusionQueries,
            perfOcclusionCulledChunks,
            perfOcclusionQueryPolls,
            perfOcclusionQueryDeferredPolls,
            perfOcclusionQueryReadStalls,
            perfMdiBatches,
            perfMdiChunks,
            perfMdiFallbackChunks,
            perfDrawElementsChunks,
            perfSharedArenaDrawChunks,
            perfLocalDrawChunks,
            perfMdiCommandBytes / (1024.0 * 1024.0),
            perfMdiCommandBufferReallocs,
            perfMdiCommandBufferOrphans,
            perfSharedArenaAllocFailures,
            perfSharedArenaFallbackUploads,
            sharedChunkBufferArena == null ? 0.0 : sharedChunkBufferArena.usedVertexBytes() / (1024.0 * 1024.0),
            sharedChunkBufferArena == null ? 0.0 : sharedChunkBufferArena.usedIndexBytes() / (1024.0 * 1024.0),
            perfUploadJobs,
            perfUploadBytes / (1024.0 * 1024.0),
            perfUploadDropped,
            perfBufferReallocs,
            perfBufferOrphans,
            perfBufferSubDatas
        );

        perfFrames = 0;
        perfUploadJobs = 0L;
        perfUploadBytes = 0L;
        perfUploadDropped = 0L;
        perfBufferReallocs = 0L;
        perfBufferOrphans = 0L;
        perfBufferSubDatas = 0L;
        perfMeshingQueueTopPriority = Double.NaN;
        perfUploadQueueTopPriority = Double.NaN;
        perfVisibleLatencyNanosTotal = 0L;
        perfVisibleLatencySamples = 0;
        perfOcclusionQueries = 0L;
        perfOcclusionCulledChunks = 0L;
        perfOcclusionQueryPolls = 0L;
        perfOcclusionQueryDeferredPolls = 0L;
        perfOcclusionQueryReadStalls = 0L;
        perfMdiBatches = 0L;
        perfMdiChunks = 0L;
        perfMdiFallbackChunks = 0L;
        perfDrawElementsChunks = 0L;
        perfSharedArenaDrawChunks = 0L;
        perfLocalDrawChunks = 0L;
        perfMdiCommandBytes = 0L;
        perfMdiCommandBufferReallocs = 0L;
        perfMdiCommandBufferOrphans = 0L;
        perfSharedArenaAllocFailures = 0L;
        perfSharedArenaFallbackUploads = 0L;
        perfWindowStartNanos = now;
    }

    public String latestTitleStats() {
        return latestTitleStats;
    }

    public long lastMeshingSubmitNanos() {
        return lastMeshingSubmitNanos;
    }

    public long lastUploadQueueDrainNanos() {
        return lastUploadQueueDrainNanos;
    }

    public long lastDrawLoopNanos() {
        return lastDrawLoopNanos;
    }

    private void updateAdaptiveBudgets(long renderCpuNanos) {
        if (features.adaptiveUploadBudget()) {
            updateAdaptiveUploadBudget(renderCpuNanos);
        } else {
            updateRenderCpuEma(renderCpuNanos);
            adaptiveUploadsPerFrame = DEFAULT_UPLOADS_PER_FRAME;
        }
        if (features.adaptiveMeshSubmitBudget()) {
            updateAdaptiveMeshSubmitBudget();
        } else {
            adaptiveMeshSubmitsPerFrame = DEFAULT_MESH_SUBMITS_PER_FRAME;
        }
    }

    private void updateAdaptiveUploadBudget(long renderCpuNanos) {
        updateRenderCpuEma(renderCpuNanos);

        int queueSize = uploadQueue.size();
        if (renderCpuMsEma > UPLOAD_BUDGET_REDUCE_MS) {
            adaptiveUploadsPerFrame = Math.max(MIN_UPLOADS_PER_FRAME, adaptiveUploadsPerFrame - 1);
            return;
        }
        if (queueSize > adaptiveUploadsPerFrame && renderCpuMsEma < UPLOAD_BUDGET_INCREASE_MS) {
            adaptiveUploadsPerFrame = Math.min(MAX_UPLOADS_PER_FRAME, adaptiveUploadsPerFrame + 1);
            return;
        }
        if (queueSize == 0 && adaptiveUploadsPerFrame > DEFAULT_UPLOADS_PER_FRAME && renderCpuMsEma < UPLOAD_BUDGET_RECOVER_MS) {
            adaptiveUploadsPerFrame--;
            return;
        }
        if (queueSize > 0 && adaptiveUploadsPerFrame < DEFAULT_UPLOADS_PER_FRAME && renderCpuMsEma < UPLOAD_BUDGET_RECOVER_MS) {
            adaptiveUploadsPerFrame++;
        }
    }

    private void updateRenderCpuEma(long renderCpuNanos) {
        double renderCpuMs = renderCpuNanos / 1_000_000.0;
        if (renderCpuMsEma < 0.0) {
            renderCpuMsEma = renderCpuMs;
        } else {
            renderCpuMsEma = (renderCpuMsEma * (1.0 - FRAME_TIME_EMA_ALPHA)) + (renderCpuMs * FRAME_TIME_EMA_ALPHA);
        }
    }

    private void updateAdaptiveMeshSubmitBudget() {
        int queueSize = uploadQueue.size();
        int inFlight = meshingJobsInFlight.get();
        int healthyInFlightCap = Math.max(2, meshWorkerCount * 2);

        if (renderCpuMsEma > UPLOAD_BUDGET_REDUCE_MS
            || queueSize > adaptiveUploadsPerFrame * 4
            || inFlight > healthyInFlightCap) {
            adaptiveMeshSubmitsPerFrame = Math.max(MIN_MESH_SUBMITS_PER_FRAME, adaptiveMeshSubmitsPerFrame - 1);
            return;
        }

        if (queueSize < adaptiveUploadsPerFrame * 2
            && inFlight < meshWorkerCount
            && renderCpuMsEma >= 0.0
            && renderCpuMsEma < UPLOAD_BUDGET_INCREASE_MS) {
            adaptiveMeshSubmitsPerFrame = Math.min(MAX_MESH_SUBMITS_PER_FRAME, adaptiveMeshSubmitsPerFrame + 1);
            return;
        }

        if (queueSize == 0 && inFlight == 0 && adaptiveMeshSubmitsPerFrame > DEFAULT_MESH_SUBMITS_PER_FRAME) {
            adaptiveMeshSubmitsPerFrame--;
            return;
        }

        if (queueSize > 0
            && queueSize < adaptiveUploadsPerFrame
            && inFlight < meshWorkerCount
            && adaptiveMeshSubmitsPerFrame < DEFAULT_MESH_SUBMITS_PER_FRAME
            && renderCpuMsEma < UPLOAD_BUDGET_RECOVER_MS) {
            adaptiveMeshSubmitsPerFrame++;
        }
    }

    private PrioritizedMeshTask peekMeshingTask() {
        Runnable head = meshPool.getQueue().peek();
        if (head instanceof PrioritizedMeshTask prioritizedMeshTask) {
            return prioritizedMeshTask;
        }
        return null;
    }

    private void recordFrameTime(long renderCpuNanos) {
        double frameMs = renderCpuNanos / 1_000_000.0;
        frameTimeWindowMs[frameTimeWindowIndex] = frameMs;
        frameTimeWindowIndex = (frameTimeWindowIndex + 1) % frameTimeWindowMs.length;
        if (frameTimeWindowCount < frameTimeWindowMs.length) {
            frameTimeWindowCount++;
        }
    }

    private FrameTimePercentiles frameTimePercentiles() {
        if (frameTimeWindowCount == 0) {
            return new FrameTimePercentiles(0.0, 0.0, 0.0);
        }
        System.arraycopy(frameTimeWindowMs, 0, frameTimeSortScratchMs, 0, frameTimeWindowCount);
        Arrays.sort(frameTimeSortScratchMs, 0, frameTimeWindowCount);
        return new FrameTimePercentiles(
            percentile(frameTimeSortScratchMs, frameTimeWindowCount, 0.50),
            percentile(frameTimeSortScratchMs, frameTimeWindowCount, 0.95),
            percentile(frameTimeSortScratchMs, frameTimeWindowCount, 0.99)
        );
    }

    private static double percentile(double[] sortedValues, int count, double quantile) {
        if (count <= 0) {
            return 0.0;
        }
        int index = (int) Math.ceil((count - 1) * quantile);
        index = Math.max(0, Math.min(count - 1, index));
        return sortedValues[index];
    }

    private double chunkPriorityKey(Chunk chunk, double playerX, double playerZ, double lookX, double lookZ) {
        double distanceSq = chunkDistanceSq(chunk, playerX, playerZ);
        double priority = distanceSq;
        if (features.priorityForwardBias()) {
            double centerX = chunk.pos().x() * Section.SIZE + (Section.SIZE * 0.5);
            double centerZ = chunk.pos().z() * Section.SIZE + (Section.SIZE * 0.5);
            double dx = centerX - playerX;
            double dz = centerZ - playerZ;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1.0e-6) {
                double forwardDot = ((dx / len) * lookX) + ((dz / len) * lookZ);
                if (forwardDot > 0.0) {
                    priority -= forwardDot * PRIORITY_FORWARD_BIAS_WEIGHT;
                }
            }
        }
        if (features.priorityRecentlyVisibleBias()) {
            Long lastVisibleFrame = recentlyVisibleFrame.get(chunk.pos());
            if (lastVisibleFrame != null && frameSequence - lastVisibleFrame <= RECENTLY_VISIBLE_BIAS_FRAMES) {
                priority -= PRIORITY_RECENT_VISIBLE_BIAS;
            }
        }
        return priority;
    }

    private void initializeCapabilitiesIfNeeded() {
        if (glCapabilitiesLogged) {
            return;
        }
        GLCapabilities caps = GL.getCapabilities();
        supportsMdiCore43 = caps.OpenGL43;
        supportsMdiArb = caps.GL_ARB_multi_draw_indirect;
        supportsMdi = supportsMdiCore43 || supportsMdiArb;
        supportsOcclusionQuery = caps.OpenGL15 || caps.GL_ARB_occlusion_query;
        supportsPersistentMapping = caps.OpenGL44 || caps.GL_ARB_buffer_storage;
        glCapabilitiesLogged = true;

        boolean mdiEnabled = features.mdi() && supportsMdi && features.sharedChunkArena();
        System.out.printf(
            "[gpu-cap] mdiSupported=%s mdiCore43=%s mdiArb=%s mdiEnabled=%s occlusionSupported=%s occlusionEnabled=%s occlusionPollBudget=%d persistentMappingSupported=%s persistentMappingEnabled=%s priorityBiasEnabled=%s adaptiveUploadBudget=%s adaptiveMeshSubmitBudget=%s orphaningUpload=%s lodEnabled=%s lodStartChunks=%d lodHysteresis=%d sharedChunkArena=%s sharedArenaVertexMB=%d sharedArenaIndexMB=%d%n",
            supportsMdi,
            supportsMdiCore43,
            supportsMdiArb,
            mdiEnabled,
            supportsOcclusionQuery,
            features.occlusionQuery() && supportsOcclusionQuery,
            features.occlusionResultPollBudget(),
            supportsPersistentMapping,
            features.persistentMapping() && supportsPersistentMapping,
            features.priorityForwardBias() || features.priorityRecentlyVisibleBias(),
            features.adaptiveUploadBudget(),
            features.adaptiveMeshSubmitBudget(),
            features.orphaningUpload(),
            features.lod(),
            features.lodStartChunkDistance(),
            features.lodHysteresisChunks(),
            features.sharedChunkArena(),
            features.sharedChunkArenaVertexMb(),
            features.sharedChunkArenaIndexMb()
        );
        if (features.mdi() && !supportsMdi) {
            System.out.println("[gpu-cap] MDI requested but unsupported; using drawElements fallback.");
        }
        if (features.mdi() && supportsMdi && !features.sharedChunkArena()) {
            System.out.println("[gpu-cap] MDI requested but sharedChunkArena is disabled; using drawElements fallback.");
        }
        if (features.occlusionQuery() && !supportsOcclusionQuery) {
            System.out.println("[gpu-cap] Occlusion query requested but unsupported; disabled.");
        }
        if (features.persistentMapping() && !supportsPersistentMapping) {
            System.out.println("[gpu-cap] Persistent mapping requested but unsupported; using orphaning/subdata path.");
        }
        if (features.occlusionQuery() && supportsOcclusionQuery) {
            occlusionBoxMesh = new OcclusionBoxMesh();
        }
        if (features.sharedChunkArena()) {
            sharedChunkBufferArena = new SharedChunkBufferArena(
                Math.max(1, features.sharedChunkArenaVertexMb()) * 1024 * 1024,
                Math.max(1, features.sharedChunkArenaIndexMb()) * 1024 * 1024
            );
        }
    }

    private void ensureAmbientShaderProgram() {
        if (ambientShaderProgramId != 0) {
            return;
        }

        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            vertexShader = compileShader(GL20.GL_VERTEX_SHADER, AMBIENT_VERTEX_SHADER_SOURCE, "vertex");
            fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, AMBIENT_FRAGMENT_SHADER_SOURCE, "fragment");

            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glLinkProgram(program);

            int linkStatus = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS);
            String programLog = GL20.glGetProgramInfoLog(program);
            if (linkStatus == 0) {
                if (programLog != null && !programLog.isBlank()) {
                    System.err.println("[gpu-shader] ambient program link failed:\n" + programLog);
                }
                throw new IllegalStateException("Failed to link ambient shader program");
            }
            if (programLog != null && !programLog.isBlank()) {
                System.out.println("[gpu-shader] ambient program link log:\n" + programLog);
            }

            int uniformLoc = GL20.glGetUniformLocation(program, "uAmbient");
            if (uniformLoc < 0) {
                throw new IllegalStateException("Ambient shader missing uniform uAmbient");
            }

            ambientShaderProgramId = program;
            ambientUniformLocation = uniformLoc;
            program = 0;
        } finally {
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
        }
    }

    private static int compileShader(int shaderType, String source, String label) {
        int shaderId = GL20.glCreateShader(shaderType);
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);
        int compileStatus = GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS);
        String shaderLog = GL20.glGetShaderInfoLog(shaderId);
        if (compileStatus == 0) {
            if (shaderLog != null && !shaderLog.isBlank()) {
                System.err.println("[gpu-shader] ambient " + label + " compile failed:\n" + shaderLog);
            }
            GL20.glDeleteShader(shaderId);
            throw new IllegalStateException("Failed to compile ambient " + label + " shader");
        }
        if (shaderLog != null && !shaderLog.isBlank()) {
            System.out.println("[gpu-shader] ambient " + label + " compile log:\n" + shaderLog);
        }
        return shaderId;
    }

    private static void configureProjection(int width, int height) {
        double aspect = (double) width / (double) height;
        double top = NEAR_PLANE * Math.tan(Math.toRadians(VERTICAL_FOV_DEGREES * 0.5));
        double bottom = -top;
        double right = top * aspect;
        double left = -right;

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glFrustum(left, right, bottom, top, NEAR_PLANE, FAR_PLANE);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private static void configureCamera(PlayerController player) {
        glScaled(1.0, 1.0, -1.0);
        glRotatef(-player.pitch(), 1.0f, 0.0f, 0.0f);
        glRotatef(-player.yaw(), 0.0f, 1.0f, 0.0f);
        glTranslated(-player.eyeX(), -player.eyeY(), -player.eyeZ());
    }

    private static double chunkDistanceSq(Chunk chunk, double playerX, double playerZ) {
        double centerX = chunk.pos().x() * Section.SIZE + (Section.SIZE * 0.5);
        double centerZ = chunk.pos().z() * Section.SIZE + (Section.SIZE * 0.5);
        double dx = centerX - playerX;
        double dz = centerZ - playerZ;
        return dx * dx + dz * dz;
    }

    private int resolveChunkLodLevel(Chunk chunk, double playerX, double playerZ) {
        if (!features.lod()) {
            return 0;
        }
        double centerX = chunk.pos().x() * Section.SIZE + (Section.SIZE * 0.5);
        double centerZ = chunk.pos().z() * Section.SIZE + (Section.SIZE * 0.5);
        int dxChunks = (int) Math.floor(Math.abs(centerX - playerX) / Section.SIZE);
        int dzChunks = (int) Math.floor(Math.abs(centerZ - playerZ) / Section.SIZE);
        int chebyshev = Math.max(dxChunks, dzChunks);
        int threshold = Math.max(1, features.lodStartChunkDistance());
        int hysteresis = Math.max(0, features.lodHysteresisChunks());
        Integer previous = lodSelectionCache.get(chunk.pos());
        int resolved;
        if (previous != null && previous > 0) {
            int exitThreshold = Math.max(0, threshold - hysteresis);
            resolved = chebyshev > exitThreshold ? 1 : 0;
        } else {
            int enterThreshold = threshold + hysteresis;
            resolved = chebyshev >= enterThreshold ? 1 : 0;
        }
        lodSelectionCache.put(chunk.pos(), resolved);
        return resolved;
    }

    private static long meshBuildKey(long version, int lodLevel) {
        return (version << 8) ^ (lodLevel & 0xFFL);
    }

    private record ChunkFrameSet(List<Chunk> chunks, Set<ChunkPos> positions) {
    }

    private record FrameStats(int drawCalls, int visibleChunks, int lodVisibleChunks, int totalTriangles, int stateChanges) {
    }

    private record QueuedMeshUpload(ChunkMeshData meshData, double priorityKey, long sequence, long submittedNanos) {
    }

    private record UploadStats(int uploadJobs, long uploadBytes, int bufferReallocs, int bufferOrphans, int bufferSubDatas) {
    }

    private record FrameTimePercentiles(double p50Ms, double p95Ms, double p99Ms) {
    }

    private record GpuFeatureFlags(
        boolean priorityForwardBias,
        boolean priorityRecentlyVisibleBias,
        boolean adaptiveUploadBudget,
        boolean adaptiveMeshSubmitBudget,
        boolean orphaningUpload,
        boolean mdi,
        boolean occlusionQuery,
        int occlusionResultPollBudget,
        boolean persistentMapping,
        double uploadTimeBudgetMs,
        boolean lod,
        int lodStartChunkDistance,
        int lodHysteresisChunks,
        boolean sharedChunkArena,
        int sharedChunkArenaVertexMb,
        int sharedChunkArenaIndexMb
    ) {
        private static GpuFeatureFlags load() {
            return new GpuFeatureFlags(
                flag("voxelcraft.gpu.priorityForwardBias", true),
                flag("voxelcraft.gpu.priorityRecentlyVisibleBias", true),
                flag("voxelcraft.gpu.adaptiveUploadBudget", true),
                flag("voxelcraft.gpu.adaptiveMeshSubmitBudget", true),
                flag("voxelcraft.gpu.orphaningUpload", true),
                flag("voxelcraft.gpu.mdi", false),
                flag("voxelcraft.gpu.occlusionQuery", false),
                intFlag("voxelcraft.gpu.occlusionPollBudget", DEFAULT_OCCLUSION_RESULT_POLL_BUDGET),
                flag("voxelcraft.gpu.persistentMapping", false),
                doubleFlag("voxelcraft.gpu.uploadBudgetMs", DEFAULT_UPLOAD_TIME_BUDGET_MS),
                flag("voxelcraft.gpu.lod", false),
                intFlag("voxelcraft.gpu.lodStartChunks", DEFAULT_LOD_START_CHUNK_DISTANCE),
                intFlag("voxelcraft.gpu.lodHysteresisChunks", DEFAULT_LOD_HYSTERESIS_CHUNKS),
                flag("voxelcraft.gpu.sharedChunkArena", false),
                intFlag("voxelcraft.gpu.sharedArenaVertexMB", DEFAULT_SHARED_ARENA_VERTEX_MB),
                intFlag("voxelcraft.gpu.sharedArenaIndexMB", DEFAULT_SHARED_ARENA_INDEX_MB)
            );
        }

        private static boolean flag(String key, boolean defaultValue) {
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

        private static double doubleFlag(String key, double defaultValue) {
            String raw = System.getProperty(key);
            if (raw == null) {
                return defaultValue;
            }
            try {
                double parsed = Double.parseDouble(raw.trim());
                if (Double.isFinite(parsed) && parsed >= 0.0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
            return defaultValue;
        }

        private static int intFlag(String key, int defaultValue) {
            String raw = System.getProperty(key);
            if (raw == null) {
                return defaultValue;
            }
            try {
                int parsed = Integer.parseInt(raw.trim());
                return parsed;
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
    }

    private static final class PrioritizedMeshTask implements Runnable, Comparable<PrioritizedMeshTask> {
        private final double priorityKey;
        private final long sequence;
        private final Runnable delegate;

        private PrioritizedMeshTask(double priorityKey, long sequence, Runnable delegate) {
            this.priorityKey = priorityKey;
            this.sequence = sequence;
            this.delegate = delegate;
        }

        private double priorityKey() {
            return priorityKey;
        }

        @Override
        public void run() {
            delegate.run();
        }

        @Override
        public int compareTo(PrioritizedMeshTask other) {
            int byPriority = Double.compare(priorityKey, other.priorityKey);
            if (byPriority != 0) {
                return byPriority;
            }
            return Long.compare(sequence, other.sequence);
        }
    }

    private static final class GpuChunk {
        private final ChunkPos pos;
        private int vboId;
        private int iboId;
        private int occlusionQueryId;
        private int indexCount;
        private int triangleCount;
        private long versionUploaded = Long.MIN_VALUE;
        private int lodLevelUploaded;
        private boolean valid;
        private boolean usesSharedArena;
        private long sharedVertexOffsetBytes;
        private long sharedIndexOffsetBytes;
        private SharedChunkBufferArena.Allocation sharedAllocation;
        private boolean lastUploadSharedArenaAllocFailure;
        private boolean lastUploadSharedArenaFallback;
        private boolean occlusionQueryPending;
        private boolean occlusionVisible = true;
        private int occlusionHiddenStreak;
        private long occlusionLastQueryFrame = Long.MIN_VALUE;
        private int vboCapacityBytes;
        private int iboCapacityBytes;
        private long lastMeshSubmitNanos;
        private long lastVisibleLatencyRecordedVersion = Long.MIN_VALUE;
        private double minX;
        private double minY;
        private double minZ;
        private double maxX;
        private double maxY;
        private double maxZ;

        private GpuChunk(ChunkPos pos) {
            this.pos = pos;
        }

        private UploadStats upload(
            ChunkMeshData meshData,
            long submittedNanos,
            boolean orphaningUpload,
            SharedChunkBufferArena sharedArena
        ) {
            long uploadBytes = 0L;
            int bufferReallocs = 0;
            int bufferOrphans = 0;
            int bufferSubDatas = 0;
            lastUploadSharedArenaAllocFailure = false;
            lastUploadSharedArenaFallback = false;
            if (meshData.indexCount() > 0) {
                ByteBuffer vertexBytes = meshData.vertexBytes();
                ByteBuffer indexBytes = meshData.indexBytes();
                if (vertexBytes == null || indexBytes == null) {
                    throw new IllegalStateException("Non-empty mesh missing upload buffers for chunk " + pos);
                }

                if (sharedArena != null) {
                    boolean sharedUploaded = uploadToSharedArena(sharedArena, meshData, vertexBytes, indexBytes);
                    if (sharedUploaded) {
                        bufferSubDatas += 2;
                        uploadBytes += meshData.vertexByteCount();
                        uploadBytes += meshData.indexByteCount();
                    } else {
                        lastUploadSharedArenaAllocFailure = true;
                        lastUploadSharedArenaFallback = true;
                    }
                    if (sharedUploaded) {
                        freeLocalBuffers();
                    } else {
                        uploadBytes += uploadToLocalBuffers(meshData, vertexBytes, indexBytes, orphaningUpload);
                        bufferReallocs += localUploadBufferReallocs;
                        bufferOrphans += localUploadBufferOrphans;
                        bufferSubDatas += localUploadBufferSubDatas;
                    }
                } else {
                    if (sharedAllocation != null) {
                        sharedArenaFree(sharedArena);
                    }
                    usesSharedArena = false;
                    uploadBytes += uploadToLocalBuffers(meshData, vertexBytes, indexBytes, orphaningUpload);
                    bufferReallocs += localUploadBufferReallocs;
                    bufferOrphans += localUploadBufferOrphans;
                    bufferSubDatas += localUploadBufferSubDatas;
                }
            } else {
                if (sharedAllocation != null) {
                    sharedArenaFree(sharedArena);
                }
                usesSharedArena = false;
                freeLocalBuffers();
                glBindBuffer(GL_ARRAY_BUFFER, 0);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            }

            indexCount = meshData.indexCount();
            triangleCount = meshData.triangleCount();
            versionUploaded = meshData.version();
            lodLevelUploaded = meshData.lodLevel();
            minX = meshData.minX();
            minY = meshData.minY();
            minZ = meshData.minZ();
            maxX = meshData.maxX();
            maxY = meshData.maxY();
            maxZ = meshData.maxZ();
            lastMeshSubmitNanos = submittedNanos;
            valid = true;
            // after mesh upload, keep previous occlusion state but reset stale hidden streak on changed geometry
            occlusionHiddenStreak = 0;
            occlusionVisible = true;
            return new UploadStats(1, uploadBytes, bufferReallocs, bufferOrphans, bufferSubDatas);
        }

        private int localUploadBufferReallocs;
        private int localUploadBufferOrphans;
        private int localUploadBufferSubDatas;

        private long uploadToLocalBuffers(
            ChunkMeshData meshData,
            ByteBuffer vertexBytes,
            ByteBuffer indexBytes,
            boolean orphaningUpload
        ) {
            localUploadBufferReallocs = 0;
            localUploadBufferOrphans = 0;
            localUploadBufferSubDatas = 0;
            if (vboId == 0) {
                vboId = glGenBuffers();
            }
            if (iboId == 0) {
                iboId = glGenBuffers();
            }

            vertexBytes.position(0);
            vertexBytes.limit(meshData.vertexByteCount());
            indexBytes.position(0);
            indexBytes.limit(meshData.indexByteCount());

            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            if (meshData.vertexByteCount() > vboCapacityBytes) {
                glBufferData(GL_ARRAY_BUFFER, (long) meshData.vertexByteCount(), GL_STATIC_DRAW);
                vboCapacityBytes = meshData.vertexByteCount();
                localUploadBufferReallocs++;
            } else if (orphaningUpload) {
                glBufferData(GL_ARRAY_BUFFER, (long) vboCapacityBytes, GL_STATIC_DRAW);
                localUploadBufferOrphans++;
            }
            glBufferSubData(GL_ARRAY_BUFFER, 0L, vertexBytes);
            localUploadBufferSubDatas++;

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboId);
            if (meshData.indexByteCount() > iboCapacityBytes) {
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, (long) meshData.indexByteCount(), GL_STATIC_DRAW);
                iboCapacityBytes = meshData.indexByteCount();
                localUploadBufferReallocs++;
            } else if (orphaningUpload) {
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, (long) iboCapacityBytes, GL_STATIC_DRAW);
                localUploadBufferOrphans++;
            }
            glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, 0L, indexBytes);
            localUploadBufferSubDatas++;
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

            usesSharedArena = false;
            sharedVertexOffsetBytes = 0L;
            sharedIndexOffsetBytes = 0L;
            return (long) meshData.vertexByteCount() + meshData.indexByteCount();
        }

        private boolean uploadToSharedArena(
            SharedChunkBufferArena sharedArena,
            ChunkMeshData meshData,
            ByteBuffer vertexBytes,
            ByteBuffer indexBytes
        ) {
            if (sharedArena == null) {
                return false;
            }
            int vertexBytesNeeded = meshData.vertexByteCount();
            int indexBytesNeeded = meshData.indexByteCount();
            if (sharedAllocation == null
                || sharedAllocation.vertexCapacityBytes() < vertexBytesNeeded
                || sharedAllocation.indexCapacityBytes() < indexBytesNeeded) {
                if (sharedAllocation != null) {
                    sharedArena.free(sharedAllocation);
                    sharedAllocation = null;
                }
                SharedChunkBufferArena.Allocation allocation = sharedArena.allocate(vertexBytesNeeded, indexBytesNeeded);
                if (allocation == null) {
                    return false;
                }
                sharedAllocation = allocation;
            }
            sharedArena.upload(sharedAllocation, vertexBytes, vertexBytesNeeded, indexBytes, indexBytesNeeded);
            usesSharedArena = true;
            sharedVertexOffsetBytes = sharedAllocation.vertexOffsetBytes();
            sharedIndexOffsetBytes = sharedAllocation.indexOffsetBytes();
            return true;
        }

        private void sharedArenaFree(SharedChunkBufferArena sharedArena) {
            if (sharedAllocation == null) {
                return;
            }
            if (sharedArena != null) {
                sharedArena.free(sharedAllocation);
            }
            sharedAllocation = null;
            sharedVertexOffsetBytes = 0L;
            sharedIndexOffsetBytes = 0L;
        }

        private void freeLocalBuffers() {
            if (vboId != 0) {
                glDeleteBuffers(vboId);
                vboId = 0;
            }
            if (iboId != 0) {
                glDeleteBuffers(iboId);
                iboId = 0;
            }
            vboCapacityBytes = 0;
            iboCapacityBytes = 0;
        }

        private int ensureOcclusionQueryId() {
            if (occlusionQueryId == 0) {
                occlusionQueryId = glGenQueries();
            }
            return occlusionQueryId;
        }

        private void markOcclusionQueryIssued(long frameSequence) {
            occlusionQueryPending = true;
            occlusionLastQueryFrame = frameSequence;
        }

        private boolean hasPendingOcclusionQuery() {
            return occlusionQueryPending && occlusionQueryId != 0;
        }

        private boolean pollOcclusionQueryResult() {
            if (!occlusionQueryPending || occlusionQueryId == 0) {
                return false;
            }
            if (glGetQueryObjecti(occlusionQueryId, GL_QUERY_RESULT_AVAILABLE) == 0) {
                return true;
            }
            int samples = glGetQueryObjecti(occlusionQueryId, GL_QUERY_RESULT);
            occlusionQueryPending = false;
            if (samples > 0) {
                occlusionVisible = true;
                occlusionHiddenStreak = 0;
            } else {
                occlusionVisible = false;
                occlusionHiddenStreak = Math.min(255, occlusionHiddenStreak + 1);
            }
            return false;
        }

        private boolean shouldDrawBasedOnOcclusion(long frameSequence) {
            if (occlusionVisible) {
                return true;
            }
            if (occlusionHiddenStreak < OCCLUSION_HIDDEN_HYSTERESIS_FRAMES) {
                return true;
            }
            // Periodically allow retry via query-only pass; draw can stay skipped.
            return false;
        }

        private boolean shouldIssueOcclusionQuery(long frameSequence) {
            if (occlusionQueryPending) {
                return false;
            }
            if (occlusionVisible) {
                return true;
            }
            if (occlusionLastQueryFrame == Long.MIN_VALUE) {
                return true;
            }
            return frameSequence - occlusionLastQueryFrame >= OCCLUSION_RESAMPLE_INTERVAL_FRAMES;
        }

        private void dispose(SharedChunkBufferArena sharedArena) {
            freeLocalBuffers();
            sharedArenaFree(sharedArena);
            usesSharedArena = false;
            if (occlusionQueryId != 0) {
                glDeleteQueries(occlusionQueryId);
                occlusionQueryId = 0;
            }
            indexCount = 0;
            triangleCount = 0;
            lodLevelUploaded = 0;
            lastMeshSubmitNanos = 0L;
            lastVisibleLatencyRecordedVersion = Long.MIN_VALUE;
            occlusionQueryPending = false;
            occlusionVisible = true;
            occlusionHiddenStreak = 0;
            occlusionLastQueryFrame = Long.MIN_VALUE;
            valid = false;
        }
    }

    private static final class OcclusionBoxMesh {
        private static final float[] UNIT_CUBE_VERTICES = {
            0f, 0f, 0f,
            1f, 0f, 0f,
            1f, 1f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
            1f, 0f, 1f,
            1f, 1f, 1f,
            0f, 1f, 1f
        };
        private static final int[] UNIT_CUBE_INDICES = {
            0, 1, 2, 2, 3, 0, // back
            4, 7, 6, 6, 5, 4, // front
            0, 4, 5, 5, 1, 0, // bottom
            3, 2, 6, 6, 7, 3, // top
            1, 5, 6, 6, 2, 1, // right
            0, 3, 7, 7, 4, 0  // left
        };

        private final int vboId;
        private final int iboId;
        private final int indexCount;

        private OcclusionBoxMesh() {
            vboId = glGenBuffers();
            iboId = glGenBuffers();
            indexCount = UNIT_CUBE_INDICES.length;

            ByteBuffer vb = ByteBuffer.allocateDirect(UNIT_CUBE_VERTICES.length * Float.BYTES).order(ByteOrder.nativeOrder());
            FloatBuffer vf = vb.asFloatBuffer();
            vf.put(UNIT_CUBE_VERTICES).flip();

            ByteBuffer ib = ByteBuffer.allocateDirect(UNIT_CUBE_INDICES.length * Integer.BYTES).order(ByteOrder.nativeOrder());
            IntBuffer ii = ib.asIntBuffer();
            ii.put(UNIT_CUBE_INDICES).flip();

            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, vb, GL_STATIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        }

        private void dispose() {
            glDeleteBuffers(vboId);
            glDeleteBuffers(iboId);
        }
    }

    private static final class SharedChunkBufferArena {
        private final int vboId;
        private final int iboId;
        private final int vertexCapacityBytes;
        private final int indexCapacityBytes;
        private final ArrayList<Range> freeVertexRanges = new ArrayList<>();
        private final ArrayList<Range> freeIndexRanges = new ArrayList<>();
        private int usedVertexBytes;
        private int usedIndexBytes;

        private SharedChunkBufferArena(int vertexCapacityBytes, int indexCapacityBytes) {
            this.vertexCapacityBytes = Math.max(64 * 1024, vertexCapacityBytes);
            this.indexCapacityBytes = Math.max(64 * 1024, indexCapacityBytes);
            this.vboId = glGenBuffers();
            this.iboId = glGenBuffers();
            freeVertexRanges.add(new Range(0, this.vertexCapacityBytes));
            freeIndexRanges.add(new Range(0, this.indexCapacityBytes));

            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, (long) this.vertexCapacityBytes, GL_STATIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, (long) this.indexCapacityBytes, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        }

        private Allocation allocate(int vertexBytes, int indexBytes) {
            if (vertexBytes < 0 || indexBytes < 0) {
                return null;
            }
            Range vertexRange = takeRange(freeVertexRanges, align(vertexBytes, 16));
            if (vertexRange == null) {
                return null;
            }
            Range indexRange = takeRange(freeIndexRanges, align(indexBytes, 16));
            if (indexRange == null) {
                putRange(freeVertexRanges, vertexRange);
                return null;
            }
            usedVertexBytes += vertexRange.length;
            usedIndexBytes += indexRange.length;
            return new Allocation(vertexRange, indexRange);
        }

        private void free(Allocation allocation) {
            if (allocation == null) {
                return;
            }
            usedVertexBytes = Math.max(0, usedVertexBytes - allocation.vertexRange.length);
            usedIndexBytes = Math.max(0, usedIndexBytes - allocation.indexRange.length);
            putRange(freeVertexRanges, allocation.vertexRange);
            putRange(freeIndexRanges, allocation.indexRange);
        }

        private void upload(Allocation allocation, ByteBuffer vertexBytes, int vertexByteCount, ByteBuffer indexBytes, int indexByteCount) {
            if (allocation == null) {
                throw new IllegalArgumentException("allocation");
            }
            if (vertexBytes != null && vertexByteCount > 0) {
                vertexBytes.position(0);
                vertexBytes.limit(vertexByteCount);
                glBindBuffer(GL_ARRAY_BUFFER, vboId);
                glBufferSubData(GL_ARRAY_BUFFER, allocation.vertexOffsetBytes(), vertexBytes);
            }
            if (indexBytes != null && indexByteCount > 0) {
                indexBytes.position(0);
                indexBytes.limit(indexByteCount);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboId);
                glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, allocation.indexOffsetBytes(), indexBytes);
            }
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        }

        private int vboId() {
            return vboId;
        }

        private int iboId() {
            return iboId;
        }

        private int usedVertexBytes() {
            return usedVertexBytes;
        }

        private int usedIndexBytes() {
            return usedIndexBytes;
        }

        private void dispose() {
            glDeleteBuffers(vboId);
            glDeleteBuffers(iboId);
            freeVertexRanges.clear();
            freeIndexRanges.clear();
            usedVertexBytes = 0;
            usedIndexBytes = 0;
        }

        private static int align(int value, int alignment) {
            if (value <= 0) {
                return 0;
            }
            int mask = alignment - 1;
            return (value + mask) & ~mask;
        }

        private static Range takeRange(ArrayList<Range> freeRanges, int bytes) {
            if (bytes == 0) {
                return new Range(0, 0);
            }
            for (int i = 0; i < freeRanges.size(); i++) {
                Range range = freeRanges.get(i);
                if (range.length < bytes) {
                    continue;
                }
                Range allocated = new Range(range.offset, bytes);
                if (range.length == bytes) {
                    freeRanges.remove(i);
                } else {
                    freeRanges.set(i, new Range(range.offset + bytes, range.length - bytes));
                }
                return allocated;
            }
            return null;
        }

        private static void putRange(ArrayList<Range> freeRanges, Range returned) {
            if (returned == null || returned.length <= 0) {
                return;
            }
            int insertAt = 0;
            while (insertAt < freeRanges.size() && freeRanges.get(insertAt).offset < returned.offset) {
                insertAt++;
            }
            freeRanges.add(insertAt, returned);
            int mergeIndex = Math.max(0, insertAt - 1);
            while (mergeIndex < freeRanges.size() - 1) {
                Range left = freeRanges.get(mergeIndex);
                Range right = freeRanges.get(mergeIndex + 1);
                if (left.offset + left.length != right.offset) {
                    mergeIndex++;
                    continue;
                }
                freeRanges.set(mergeIndex, new Range(left.offset, left.length + right.length));
                freeRanges.remove(mergeIndex + 1);
            }
        }

        private record Range(int offset, int length) {
        }

        private static final class Allocation {
            private final Range vertexRange;
            private final Range indexRange;

            private Allocation(Range vertexRange, Range indexRange) {
                this.vertexRange = vertexRange;
                this.indexRange = indexRange;
            }

            private long vertexOffsetBytes() {
                return vertexRange.offset;
            }

            private long indexOffsetBytes() {
                return indexRange.offset;
            }

            private int vertexCapacityBytes() {
                return vertexRange.length;
            }

            private int indexCapacityBytes() {
                return indexRange.length;
            }
        }
    }

}
