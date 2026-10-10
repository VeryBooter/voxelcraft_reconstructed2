package dev.voxelcraft.client.render;

import dev.voxelcraft.client.GameClient;
import dev.voxelcraft.client.player.PlayerController;
import dev.voxelcraft.client.render.ChunkMesher.ChunkMeshData;
import dev.voxelcraft.client.render.ChunkRenderSystem.RenderStats;
import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.util.SystemProperties;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.ChunkPos;
import dev.voxelcraft.core.world.Section;
import dev.voxelcraft.core.world.World;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeCocoa;
import org.lwjgl.system.MemoryStack;

/** Metal renders the exact shared buffers written by ChunkMesher; there is no mesh upload copy. */
public final class MetalChunkRenderer implements ClientGpuRenderer {
    private static final float FOV = 75.0f;
    private static final float NEAR = 0.05f;
    private static final float FAR = 4800.0f;
    private final ChunkMesher mesher = new ChunkMesher();
    private final Frustum frustum = new Frustum();
    private final Map<ChunkPos, CachedMesh> meshes = new HashMap<>();
    private final Map<ChunkPos, BuildKey> inFlight = new HashMap<>();
    private final ConcurrentLinkedQueue<CompletedMesh> ready = new ConcurrentLinkedQueue<>();
    private final AtomicInteger outstandingJobs = new AtomicInteger();
    private final ArrayList<Chunk> loaded = new ArrayList<>();
    private final boolean lodEnabled = SystemProperties.getBoolean("vc.gpu.lod", "voxelcraft.gpu.lod", true);
    private final boolean applyAmbient = SystemProperties.getBoolean("vc.lighting.applyAmbientToBlocks", "voxelcraft.lighting.applyAmbientToBlocks", true);
    private final int workers = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
    private ExecutorService meshPool;
    private MetalSharedBuffers buffers;
    private long context;
    private long window;
    private World world;
    private int epoch;
    private int atlasColumns;
    private int atlasRows;
    private BufferedImage hudImage;
    private long meshSubmitNanos;
    private long completedDrainNanos;
    private long drawNanos;
    private int drawnChunks;
    private boolean closed;

    public MetalChunkRenderer() {
        // Initialize headless font rendering before GLFW takes ownership of AppKit's event loop.
        BufferedImage warmup = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = warmup.createGraphics();
        try { graphics.drawString("V", 0, 12); } finally { graphics.dispose(); }
    }

    @Override
    public void initialize(long glfwWindow) {
        if (closed) throw new IllegalStateException("Metal renderer closed");
        if (context != 0) return;
        MetalNative.load();
        window = glfwWindow;
        context = MetalNative.create(window == 0 ? 0 : GLFWNativeCocoa.glfwGetCocoaWindow(window),
            MetalNative.shaderSource(), Integer.getInteger("voxelcraft.vsync", 1) != 0);
        buffers = new MetalSharedBuffers(context);
        try {
            BlockTextureAtlas.AtlasBuildResult atlas = BlockTextureAtlas.build();
            atlasColumns = atlas.atlasColumns();
            atlasRows = atlas.atlasRows();
            MetalNative.setTexture(context, 0, atlas.atlasWidth(), atlas.atlasHeight(), atlas.atlasPixels());
            MetalNative.setTexture(context, 1, BlockTextureAtlas.BLOCK_ATLAS_META_WIDTH, 6, atlas.metaPixels());
            loadTexture(2, "/textures/1758252625670.jpg");
            loadTexture(3, "/textures/1740065499332.jpg");
            meshPool = Executors.newFixedThreadPool(workers, task -> {
                Thread thread = new Thread(task, "voxelcraft-metal-mesher");
                thread.setDaemon(true);
                return thread;
            });
            System.out.println("[metal] device=" + MetalNative.deviceName(context)
                + " storage=shared meshUploadCopies=0 workers=" + workers);
        } catch (Throwable failure) {
            close();
            throw failure;
        }
    }

    private void loadTexture(int slot, String resource) {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing " + resource);
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IOException("Cannot decode " + resource);
            ByteBuffer rgba = ByteBuffer.allocateDirect(image.getWidth() * image.getHeight() * 4);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getRGB(x, y);
                    rgba.put((byte)(pixel >>> 16)).put((byte)(pixel >>> 8)).put((byte)pixel).put((byte)255);
                }
            }
            rgba.flip();
            MetalNative.setTexture(context, slot, image.getWidth(), image.getHeight(), rgba);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load Metal texture", failure);
        }
    }

    @Override
    public RenderStats render(int width, int height, GameClient game) {
        if (closed || context == 0) throw new IllegalStateException("Metal renderer is not initialized");
        ClientWorldView view = game.worldView();
        if (world != view.world()) {
            world = view.world();
            epoch++;
            meshes.values().forEach(mesh -> mesh.data.releaseBuffers(buffers));
            meshes.clear();
            inFlight.clear();
        }
        PlayerController player = game.playerController();
        frustum.setCamera(player.eyeX(), player.eyeY(), player.eyeZ(), player.yaw(), player.pitch(),
            FOV, (double)width / height, NEAR, FAR);
        view.copyLoadedChunksInto(loaded);
        int px = Math.floorDiv((int)Math.floor(player.x()), Section.SIZE);
        int pz = Math.floorDiv((int)Math.floor(player.z()), Section.SIZE);
        int radius = game.renderDistanceChunkRadius();
        loaded.removeIf(chunk -> Math.abs(chunk.pos().x() - px) > radius || Math.abs(chunk.pos().z() - pz) > radius);
        loaded.sort(Comparator.comparingDouble(chunk -> distanceSquared(chunk, player)));
        Map<ChunkPos, BuildKey> desired = new HashMap<>();
        for (Chunk chunk : loaded) desired.put(chunk.pos(), key(view, chunk, player));
        Set<ChunkPos> active = desired.keySet();
        meshes.entrySet().removeIf(entry -> {
            if (active.contains(entry.getKey())) return false;
            entry.getValue().data.releaseBuffers(buffers);
            return true;
        });

        long start = System.nanoTime();
        for (CompletedMesh result; (result = ready.poll()) != null;) {
            inFlight.remove(result.pos, result.key);
            if (result.failure != null) throw new IllegalStateException("Metal mesh failed at " + result.pos, result.failure);
            if (!result.key.equals(desired.get(result.pos))) {
                result.data.releaseBuffers(buffers);
                continue;
            }
            CachedMesh old = meshes.put(result.pos, new CachedMesh(result.key, result.data));
            if (old != null) old.data.releaseBuffers(buffers);
        }
        completedDrainNanos = System.nanoTime() - start;
        start = System.nanoTime();
        int submitted = 0;
        long snapshotBudget = Math.max(1, Integer.getInteger("vc.metal.snapshotBudgetMs", 4)) * 1_000_000L;
        for (Chunk chunk : loaded) {
            if (submitted >= 4 || outstandingJobs.get() >= workers * 2) break;
            // Capturing snapshots runs on the game thread. Allow one job, then defer additional
            // snapshots once the time budget is spent so loading cannot monopolize a frame.
            if (submitted > 0 && System.nanoTime() - start >= snapshotBudget) break;
            // Keep nearby geometry ready for turns; defer other offscreen snapshots and mesh jobs.
            // World data and existing meshes remain cached throughout the render-distance area.
            if (Math.max(Math.abs(chunk.pos().x() - px), Math.abs(chunk.pos().z() - pz)) > 1
                    && !chunkInView(chunk.pos())) continue;
            BuildKey key = desired.get(chunk.pos());
            CachedMesh cached = meshes.get(chunk.pos());
            if ((cached != null && cached.key.equals(key)) || inFlight.containsKey(chunk.pos())) continue;
            var snapshot = mesher.captureChunkSnapshot(view, chunk, World.MIN_Y, World.MAX_Y);
            inFlight.put(chunk.pos(), key);
            outstandingJobs.incrementAndGet();
            try {
                meshPool.execute(() -> {
                    try {
                        ChunkMeshData data = mesher.buildChunkMesh(snapshot, buffers, key.lod);
                        ready.add(new CompletedMesh(chunk.pos(), key, data, null));
                    } catch (Throwable failure) {
                        ready.add(new CompletedMesh(chunk.pos(), key, null, failure));
                    } finally {
                        outstandingJobs.decrementAndGet();
                    }
                });
            } catch (RuntimeException failure) {
                inFlight.remove(chunk.pos(), key);
                mesher.discardChunkSnapshot(snapshot);
                outstandingJobs.decrementAndGet();
                throw failure;
            }
            submitted++;
        }
        meshSubmitNanos = System.nanoTime() - start;
        start = System.nanoTime();
        ArrayList<ChunkMeshData> visible = new ArrayList<>();
        int totalFaces = 0;
        int visibleFaces = 0;
        for (CachedMesh mesh : meshes.values()) {
            ChunkMeshData data = mesh.data;
            totalFaces += data.triangleCount() / 2;
            if (data.indexCount() == 0 || !frustum.classifyAabb(data.minX(), data.minY(), data.minZ(),
                    data.maxX(), data.maxY(), data.maxZ()).visible()) continue;
            visible.add(data);
            visibleFaces += data.triangleCount() / 2;
        }
        long[] vertices = new long[visible.size()];
        long[] indices = new long[visible.size()];
        int[] counts = new int[visible.size()];
        for (int i = 0; i < visible.size(); i++) {
            ChunkMeshData data = visible.get(i);
            vertices[i] = buffers.handle(data.vertexBytes());
            indices[i] = buffers.handle(data.indexBytes());
            counts[i] = data.indexCount();
        }
        RenderStats stats = new RenderStats(totalFaces, visibleFaces, visibleFaces);
        int hudWidth = width, hudHeight = height;
        if (window != 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var w = stack.mallocInt(1); var h = stack.mallocInt(1);
                GLFW.glfwGetWindowSize(window, w, h);
                hudWidth = Math.max(1, w.get(0)); hudHeight = Math.max(1, h.get(0));
            }
        }
        ByteBuffer hud = drawHud(game, stats, hudWidth, hudHeight);
        try {
            float[] uniforms = uniforms(player.eyeX(), player.eyeY(), player.eyeZ(), player.yaw(), player.pitch(),
                (float)width / height, applyAmbient ? game.ambientLight() : 1.0f, atlasColumns, atlasRows);
            uniforms[19] = game.ambientLight();
            MetalNative.render(context, width, height, uniforms, vertices, indices, counts, buffers.handle(hud), hudWidth, hudHeight);
        } finally {
            buffers.release(hud);
        }
        drawnChunks = visible.size();
        drawNanos = System.nanoTime() - start;
        return stats;
    }

    private ByteBuffer drawHud(GameClient game, RenderStats stats, int width, int height) {
        if (hudImage == null || hudImage.getWidth() != width || hudImage.getHeight() != height)
            hudImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = hudImage.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, width, height);
            graphics.setComposite(AlphaComposite.SrcOver);
            game.renderOverlay(graphics, width, height, stats);
        } finally {
            graphics.dispose();
        }
        ByteBuffer data = buffers.acquire(Math.multiplyExact(Math.multiplyExact(width, height), 4));
        data.asIntBuffer().put(((DataBufferInt)hudImage.getRaster().getDataBuffer()).getData());
        return data;
    }

    static float[] uniforms(double x, double y, double z, float yaw, float pitch, float aspect,
                             float ambient, int cols, int rows) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(-pitch);
        float focal = (float)(1.0 / Math.tan(Math.toRadians(FOV) * 0.5));
        return new float[] {(float)x, (float)y, (float)z, ambient,
            (float)Math.cos(yr), (float)Math.sin(yr), (float)Math.cos(pr), (float)Math.sin(pr),
            focal / aspect, focal, FAR / (FAR - NEAR), -NEAR * FAR / (FAR - NEAR),
            cols, rows, BlockTextureAtlas.BLOCK_ATLAS_TILE_SIZE, 0,
            Blocks.PORTAL == null ? 4096 : Blocks.PORTAL.blockId().asUnsignedInt(),
            Blocks.MISSILE == null ? 4096 : Blocks.MISSILE.blockId().asUnsignedInt(), 0, ambient};
    }

    private BuildKey key(ClientWorldView view, Chunk chunk, PlayerController player) {
        int distance = Math.max(Math.abs(chunk.pos().x() - Math.floorDiv((int)Math.floor(player.x()), Section.SIZE)),
            Math.abs(chunk.pos().z() - Math.floorDiv((int)Math.floor(player.z()), Section.SIZE)));
        int lod = !lodEnabled || distance < 12 ? 0 : distance < 28 ? 1 : 2;
        // Include neighboring chunks so boundary faces update when a neighbor changes or arrives.
        long neighbors = 1;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            Chunk neighbor = view.getChunk(chunk.pos().x() + dx, chunk.pos().z() + dz);
            neighbors = 31 * neighbors + (neighbor == null ? -1 : neighbor.version());
            neighbors = 31 * neighbors + System.identityHashCode(neighbor);
        }
        return new BuildKey(epoch, chunk, chunk.version(), neighbors, lod);
    }

    private static double distanceSquared(Chunk chunk, PlayerController player) {
        double dx = chunk.pos().x() * (double)Section.SIZE + Section.SIZE * 0.5 - player.x();
        double dz = chunk.pos().z() * (double)Section.SIZE + Section.SIZE * 0.5 - player.z();
        return dx * dx + dz * dz;
    }

    private boolean chunkInView(ChunkPos pos) {
        double x = pos.x() * (double)Section.SIZE, z = pos.z() * (double)Section.SIZE;
        // Full chunk height is deliberately conservative: tall terrain must not disappear.
        return frustum.classifyAabb(x, World.MIN_Y, z, x + Section.SIZE, World.MAX_Y + 1.0,
            z + Section.SIZE).visible();
    }

    @Override public String latestTitleStats() {
        return "Metal chunks=" + drawnChunks + " pending=" + inFlight.size() + " " + (buffers == null ? "" : buffers.stats()) + " meshUploadCopies=0";
    }
    @Override public long lastMeshingSubmitNanos() { return meshSubmitNanos; }
    @Override public long lastUploadQueueDrainNanos() { return completedDrainNanos; }
    @Override public long lastDrawLoopNanos() { return drawNanos; }

    public ByteBuffer readOffscreenPixels(int width, int height) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(width, height), 4));
        readOffscreenPixels(pixels);
        return pixels;
    }

    /** Read the completed offscreen frame into a reusable caller-owned direct buffer. */
    public void readOffscreenPixels(ByteBuffer pixels) {
        if (window != 0 || context == 0) throw new IllegalStateException("Requires an initialized offscreen renderer");
        if (!pixels.isDirect()) throw new IllegalArgumentException("Pixels must be a direct buffer");
        MetalNative.readPixels(context, pixels);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        boolean interrupted = false;
        if (meshPool != null) {
            meshPool.shutdown(); // Queued jobs own snapshots; let them release those before closing the allocator.
            for (;;) {
                try {
                    if (meshPool.awaitTermination(1, TimeUnit.SECONDS)) break;
                } catch (InterruptedException ignored) { interrupted = true; }
            }
        }
        try {
            if (buffers != null) {
                meshes.values().forEach(mesh -> mesh.data.releaseBuffers(buffers));
                for (CompletedMesh result; (result = ready.poll()) != null;)
                    if (result.data != null) result.data.releaseBuffers(buffers);
                buffers.close();
            }
        } finally {
            if (context != 0) { MetalNative.destroy(context); context = 0; }
            if (interrupted) Thread.currentThread().interrupt();
        }
        meshes.clear(); inFlight.clear();
    }

    private record BuildKey(int epoch, Chunk chunk, long version, long neighbors, int lod) { }
    private record CachedMesh(BuildKey key, ChunkMeshData data) { }
    private record CompletedMesh(ChunkPos pos, BuildKey key, ChunkMeshData data, Throwable failure) { }
}
