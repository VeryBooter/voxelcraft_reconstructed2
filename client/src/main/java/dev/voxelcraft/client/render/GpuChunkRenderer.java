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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_COLOR_ARRAY;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
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
import static org.lwjgl.opengl.GL11.glDisableClientState;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL11.glFrustum;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glTranslated;
import static org.lwjgl.opengl.GL11.glVertexPointer;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;

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
    private static final int VERTEX_STRIDE_BYTES = ChunkMesher.GPU_VERTEX_STRIDE_BYTES;
    private static final long POSITION_OFFSET_BYTES = 0L;
    private static final long COLOR_OFFSET_BYTES = ChunkMesher.GPU_COLOR_OFFSET_BYTES;

    private final ChunkMesher mesher = new ChunkMesher();
    private final Frustum frustum = new Frustum();
    private final Map<ChunkPos, GpuChunk> gpuChunks = new HashMap<>();
    private final PriorityBlockingQueue<QueuedMeshUpload> uploadQueue = new PriorityBlockingQueue<>(
        64,
        Comparator
            .comparingDouble(QueuedMeshUpload::distanceSq)
            .thenComparingLong(QueuedMeshUpload::sequence)
    );
    private final ConcurrentHashMap<ChunkPos, Long> inFlightVersion = new ConcurrentHashMap<>();
    private final AtomicInteger meshingJobsInFlight = new AtomicInteger();
    private final AtomicLong uploadSequence = new AtomicLong();
    private final ExecutorService meshPool;
    private final int meshWorkerCount;
    private final DirectByteBufferPool uploadBufferPool = new DirectByteBufferPool(BYTE_BUFFER_POOL_BUCKET_LIMIT);
    private final ArrayList<Chunk> scratchLoadedChunks = new ArrayList<>();
    private final ArrayList<Chunk> scratchChunksInRange = new ArrayList<>();
    private final HashSet<ChunkPos> scratchActiveChunkPositions = new HashSet<>();
    private final ArrayList<ChunkPos> scratchPruneRemovals = new ArrayList<>();

    private long perfWindowStartNanos = System.nanoTime();
    private int perfFrames;
    private long perfUploadJobs;
    private long perfUploadBytes;
    private long perfUploadDropped;
    private long perfBufferReallocs;
    private long perfBufferOrphans;
    private long perfBufferSubDatas;
    private int adaptiveUploadsPerFrame = DEFAULT_UPLOADS_PER_FRAME;
    private int adaptiveMeshSubmitsPerFrame = DEFAULT_MESH_SUBMITS_PER_FRAME;
    private double renderCpuMsEma = -1.0;

    public GpuChunkRenderer() {
        meshWorkerCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        meshPool = Executors.newFixedThreadPool(meshWorkerCount, runnable -> {
            Thread thread = new Thread(runnable, "voxelcraft-chunk-mesher");
            thread.setDaemon(true);
            return thread;
        });
    }

    public RenderStats render(int width, int height, GameClient gameClient) {
        long renderStarted = System.nanoTime();
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
        submitMeshJobsForDirtyChunks(worldView, frameSet.chunks(), player.x(), player.y(), player.z());
        processUploadQueue(worldView, adaptiveUploadsPerFrame);
        pruneGpuChunks(frameSet.positions());

        FrameStats frameStats = renderVisibleChunks(frameSet.chunks(), ambient);
        updateAdaptiveBudgets(System.nanoTime() - renderStarted);
        emitPerfLine(frameStats, frameSet.chunks().size());

        int approxFaces = frameStats.totalTriangles / 2;
        return new RenderStats(approxFaces, frameStats.visibleChunks, approxFaces);
    }

    @Override
    public void close() {
        meshPool.shutdownNow();
        for (GpuChunk gpuChunk : gpuChunks.values()) {
            gpuChunk.dispose();
        }
        gpuChunks.clear();
        QueuedMeshUpload pending;
        while ((pending = uploadQueue.poll()) != null) {
            pending.meshData().releaseBuffers(uploadBufferPool);
        }
        inFlightVersion.clear();
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
        scratchChunksInRange.sort(Comparator.comparingDouble(chunk -> chunkDistanceSq(chunk, playerX, playerZ)));

        return new ChunkFrameSet(scratchChunksInRange, scratchActiveChunkPositions);
    }

    private void submitMeshJobsForDirtyChunks(
        ClientWorldView worldView,
        List<Chunk> chunksInRange,
        double playerX,
        double centerY,
        double playerZ
    ) {
        int minY = mesher.gpuMinY(centerY);
        int maxY = mesher.gpuMaxY(centerY);
        int submitted = 0;

        for (Chunk chunk : chunksInRange) {
            if (submitted >= adaptiveMeshSubmitsPerFrame) {
                break;
            }

            ChunkPos pos = chunk.pos();
            long currentVersion = chunk.version();
            GpuChunk gpuChunk = gpuChunks.get(pos);
            if (gpuChunk != null && gpuChunk.versionUploaded == currentVersion) {
                continue;
            }

            Long inFlight = inFlightVersion.get(pos);
            if (inFlight != null && inFlight >= currentVersion) {
                continue;
            }
            if (inFlight != null) {
                continue;
            }

            if (inFlightVersion.putIfAbsent(pos, currentVersion) != null) {
                continue;
            }

            ChunkSnapshot snapshot = mesher.captureChunkSnapshot(worldView, chunk, minY, maxY);
            double uploadPriorityDistanceSq = chunkDistanceSq(chunk, playerX, playerZ);
            meshingJobsInFlight.incrementAndGet();
            try {
                meshPool.execute(() -> {
                    try {
                        ChunkMeshData meshData = mesher.buildChunkMesh(snapshot, uploadBufferPool);
                        uploadQueue.add(new QueuedMeshUpload(meshData, uploadPriorityDistanceSq, uploadSequence.getAndIncrement()));
                    } finally {
                        meshingJobsInFlight.decrementAndGet();
                    }
                });
                submitted++;
            } catch (RejectedExecutionException rejected) {
                inFlightVersion.remove(pos, currentVersion);
                meshingJobsInFlight.decrementAndGet();
            }
        }
    }

    private void processUploadQueue(ClientWorldView worldView, int maxUploads) {
        int processed = 0;
        while (processed < maxUploads) {
            QueuedMeshUpload queuedUpload = uploadQueue.poll();
            if (queuedUpload == null) {
                break;
            }
            processed++;
            ChunkMeshData meshData = queuedUpload.meshData();

            inFlightVersion.remove(meshData.pos(), meshData.version());
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
            try {
                recordUploadStats(uploadChunkMesh(meshData));
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

    private UploadStats uploadChunkMesh(ChunkMeshData meshData) {
        GpuChunk gpuChunk = gpuChunks.computeIfAbsent(meshData.pos(), unused -> new GpuChunk(meshData.pos()));
        return gpuChunk.upload(meshData);
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
                removed.dispose();
            }
            inFlightVersion.remove(pos);
        }
        scratchPruneRemovals.clear();
    }

    private FrameStats renderVisibleChunks(List<Chunk> chunksInRange, float ambient) {
        int drawCalls = 0;
        int visibleChunks = 0;
        int totalTriangles = 0;

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

            visibleChunks++;
            totalTriangles += gpuChunk.triangleCount;

            glBindBuffer(GL_ARRAY_BUFFER, gpuChunk.vboId);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gpuChunk.iboId);
            glVertexPointer(3, GL_FLOAT, VERTEX_STRIDE_BYTES, POSITION_OFFSET_BYTES);
            glColorPointer(4, GL_UNSIGNED_BYTE, VERTEX_STRIDE_BYTES, COLOR_OFFSET_BYTES);
            glDrawElements(GL_TRIANGLES, gpuChunk.indexCount, GL_UNSIGNED_INT, 0L);
            drawCalls++;
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);

        return new FrameStats(drawCalls, visibleChunks, totalTriangles);
    }

    private void emitPerfLine(FrameStats frameStats, int chunksInRange) {
        long now = System.nanoTime();
        perfFrames++;
        long elapsed = now - perfWindowStartNanos;
        if (elapsed < 1_000_000_000L) {
            return;
        }

        double fps = (perfFrames * 1_000_000_000.0) / elapsed;
        System.out.printf(
            "[gpu-perf] fps=%.1f drawCalls=%d visibleChunks=%d triangles=%d uploadQueueSize=%d meshingJobsInFlight=%d totalChunksInRange=%d uploadBudget=%d meshSubmitBudget=%d renderCpuMsEma=%.2f uploadJobs=%d uploadMB=%.2f droppedUploads=%d bufferReallocs=%d bufferOrphans=%d subDatas=%d%n",
            fps,
            frameStats.drawCalls,
            frameStats.visibleChunks,
            frameStats.totalTriangles,
            uploadQueue.size(),
            meshingJobsInFlight.get(),
            chunksInRange,
            adaptiveUploadsPerFrame,
            adaptiveMeshSubmitsPerFrame,
            renderCpuMsEma < 0.0 ? 0.0 : renderCpuMsEma,
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
        perfWindowStartNanos = now;
    }

    private void updateAdaptiveBudgets(long renderCpuNanos) {
        updateAdaptiveUploadBudget(renderCpuNanos);
        updateAdaptiveMeshSubmitBudget();
    }

    private void updateAdaptiveUploadBudget(long renderCpuNanos) {
        double renderCpuMs = renderCpuNanos / 1_000_000.0;
        if (renderCpuMsEma < 0.0) {
            renderCpuMsEma = renderCpuMs;
        } else {
            renderCpuMsEma = (renderCpuMsEma * (1.0 - FRAME_TIME_EMA_ALPHA)) + (renderCpuMs * FRAME_TIME_EMA_ALPHA);
        }

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
        glRotatef(player.pitch(), 1.0f, 0.0f, 0.0f);
        glRotatef(180.0f - player.yaw(), 0.0f, 1.0f, 0.0f);
        glTranslated(-player.eyeX(), -player.eyeY(), -player.eyeZ());
    }

    private static double chunkDistanceSq(Chunk chunk, double playerX, double playerZ) {
        double centerX = chunk.pos().x() * Section.SIZE + (Section.SIZE * 0.5);
        double centerZ = chunk.pos().z() * Section.SIZE + (Section.SIZE * 0.5);
        double dx = centerX - playerX;
        double dz = centerZ - playerZ;
        return dx * dx + dz * dz;
    }

    private record ChunkFrameSet(List<Chunk> chunks, Set<ChunkPos> positions) {
    }

    private record FrameStats(int drawCalls, int visibleChunks, int totalTriangles) {
    }

    private record QueuedMeshUpload(ChunkMeshData meshData, double distanceSq, long sequence) {
    }

    private record UploadStats(int uploadJobs, long uploadBytes, int bufferReallocs, int bufferOrphans, int bufferSubDatas) {
    }

    private static final class GpuChunk {
        private final ChunkPos pos;
        private int vboId;
        private int iboId;
        private int indexCount;
        private int triangleCount;
        private long versionUploaded = Long.MIN_VALUE;
        private boolean valid;
        private int vboCapacityBytes;
        private int iboCapacityBytes;
        private double minX;
        private double minY;
        private double minZ;
        private double maxX;
        private double maxY;
        private double maxZ;

        private GpuChunk(ChunkPos pos) {
            this.pos = pos;
        }

        private UploadStats upload(ChunkMeshData meshData) {
            if (vboId == 0) {
                vboId = glGenBuffers();
            }
            if (iboId == 0) {
                iboId = glGenBuffers();
            }
            long uploadBytes = 0L;
            int bufferReallocs = 0;
            int bufferOrphans = 0;
            int bufferSubDatas = 0;
            if (meshData.indexCount() > 0) {
                ByteBuffer vertexBytes = meshData.vertexBytes();
                ByteBuffer indexBytes = meshData.indexBytes();
                if (vertexBytes == null || indexBytes == null) {
                    throw new IllegalStateException("Non-empty mesh missing upload buffers for chunk " + pos);
                }

                vertexBytes.position(0);
                vertexBytes.limit(meshData.vertexByteCount());
                indexBytes.position(0);
                indexBytes.limit(meshData.indexByteCount());

                glBindBuffer(GL_ARRAY_BUFFER, vboId);
                if (meshData.vertexByteCount() > vboCapacityBytes) {
                    glBufferData(GL_ARRAY_BUFFER, (long) meshData.vertexByteCount(), GL_STATIC_DRAW);
                    vboCapacityBytes = meshData.vertexByteCount();
                    bufferReallocs++;
                } else {
                    // orphan old storage to reduce update stalls, then subload new contents
                    glBufferData(GL_ARRAY_BUFFER, (long) vboCapacityBytes, GL_STATIC_DRAW);
                    bufferOrphans++;
                }
                glBufferSubData(GL_ARRAY_BUFFER, 0L, vertexBytes);
                bufferSubDatas++;
                uploadBytes += meshData.vertexByteCount();

                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboId);
                if (meshData.indexByteCount() > iboCapacityBytes) {
                    glBufferData(GL_ELEMENT_ARRAY_BUFFER, (long) meshData.indexByteCount(), GL_STATIC_DRAW);
                    iboCapacityBytes = meshData.indexByteCount();
                    bufferReallocs++;
                } else {
                    glBufferData(GL_ELEMENT_ARRAY_BUFFER, (long) iboCapacityBytes, GL_STATIC_DRAW);
                    bufferOrphans++;
                }
                glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, 0L, indexBytes);
                bufferSubDatas++;
                uploadBytes += meshData.indexByteCount();

                glBindBuffer(GL_ARRAY_BUFFER, 0);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            } else {
                glBindBuffer(GL_ARRAY_BUFFER, 0);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            }

            indexCount = meshData.indexCount();
            triangleCount = meshData.triangleCount();
            versionUploaded = meshData.version();
            minX = meshData.minX();
            minY = meshData.minY();
            minZ = meshData.minZ();
            maxX = meshData.maxX();
            maxY = meshData.maxY();
            maxZ = meshData.maxZ();
            valid = true;
            return new UploadStats(1, uploadBytes, bufferReallocs, bufferOrphans, bufferSubDatas);
        }

        private void dispose() {
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
            indexCount = 0;
            triangleCount = 0;
            valid = false;
        }
    }

}
