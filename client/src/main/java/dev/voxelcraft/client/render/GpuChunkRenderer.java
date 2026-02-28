package dev.voxelcraft.client.render;

import dev.voxelcraft.client.GameClient;
import dev.voxelcraft.client.player.PlayerController;
import dev.voxelcraft.client.render.ChunkMesher.ChunkMeshData;
import dev.voxelcraft.client.render.ChunkMesher.ChunkSnapshot;
import dev.voxelcraft.client.render.ChunkRenderSystem.RenderStats;
import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.BlockDef;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.ChunkPos;
import dev.voxelcraft.core.world.Section;
import dev.voxelcraft.core.world.World;
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
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.ARBMultiDrawIndirect;
import org.lwjgl.opengl.GL43;

import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_ARRAY;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_CW;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_CCW;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glColorPointer;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glFrontFace;
import static org.lwjgl.opengl.GL11.glDisableClientState;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL11.glFrustum;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScaled;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTranslated;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL11.glVertexPointer;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL11.glColorMask;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glLineWidth;
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
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL15.glGetQueryObjecti;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
/**
 * 中文说明：GPU 区块渲染主入口：负责区块可见性判定、网格任务调度、上传与最终绘制。
 */

// 中文标注（类）：`GpuChunkRenderer`，职责：封装GPU、区块、渲染器相关逻辑。
public final class GpuChunkRenderer implements AutoCloseable {
    // 中文标注（字段）：`VERTICAL_FOV_DEGREES`，含义：用于表示垂直、fov、degrees。
    private static final float VERTICAL_FOV_DEGREES = 75.0f; // meaning
    // 中文标注（字段）：`NEAR_PLANE`，含义：用于表示near、plane。
    private static final double NEAR_PLANE = 0.05; // meaning
    // 中文标注（字段）：`FAR_PLANE`，含义：用于表示far、plane。
    private static final double FAR_PLANE = 4_800.0; // meaning

    // 中文标注（字段）：`RENDER_CHUNK_RADIUS`，含义：用于表示渲染、区块、radius。
    private static final int RENDER_CHUNK_RADIUS = 50; // meaning
    // 中文标注（字段）：`MIN_UPLOADS_PER_FRAME`，含义：用于表示最小、uploads、per、帧。
    private static final int MIN_UPLOADS_PER_FRAME = 1; // meaning
    // 中文标注（字段）：`DEFAULT_UPLOADS_PER_FRAME`，含义：用于表示默认、uploads、per、帧。
    private static final int DEFAULT_UPLOADS_PER_FRAME = 3; // meaning
    // 中文标注（字段）：`MAX_UPLOADS_PER_FRAME`，含义：用于表示最大、uploads、per、帧。
    private static final int MAX_UPLOADS_PER_FRAME = 6; // meaning
    // 中文标注（字段）：`MIN_MESH_SUBMITS_PER_FRAME`，含义：用于表示最小、网格、submits、per、帧。
    private static final int MIN_MESH_SUBMITS_PER_FRAME = 1; // meaning
    // 中文标注（字段）：`DEFAULT_MESH_SUBMITS_PER_FRAME`，含义：用于表示默认、网格、submits、per、帧。
    private static final int DEFAULT_MESH_SUBMITS_PER_FRAME = 6; // meaning
    // 中文标注（字段）：`MAX_MESH_SUBMITS_PER_FRAME`，含义：用于表示最大、网格、submits、per、帧。
    private static final int MAX_MESH_SUBMITS_PER_FRAME = 12; // meaning
    // 中文标注（字段）：`BYTE_BUFFER_POOL_BUCKET_LIMIT`，含义：用于表示字节、缓冲区、池、bucket、limit。
    private static final int BYTE_BUFFER_POOL_BUCKET_LIMIT = 8; // meaning
    // 中文标注（字段）：`FRAME_TIME_EMA_ALPHA`，含义：用于表示帧、时间、ema、alpha。
    private static final double FRAME_TIME_EMA_ALPHA = 0.12; // meaning
    // 中文标注（字段）：`UPLOAD_BUDGET_REDUCE_MS`，含义：用于表示上传、budget、reduce、ms。
    private static final double UPLOAD_BUDGET_REDUCE_MS = 14.5; // meaning
    // 中文标注（字段）：`UPLOAD_BUDGET_INCREASE_MS`，含义：用于表示上传、budget、increase、ms。
    private static final double UPLOAD_BUDGET_INCREASE_MS = 9.5; // meaning
    // 中文标注（字段）：`UPLOAD_BUDGET_RECOVER_MS`，含义：用于表示上传、budget、recover、ms。
    private static final double UPLOAD_BUDGET_RECOVER_MS = 8.0; // meaning
    // 中文标注（字段）：`DEFAULT_UPLOAD_TIME_BUDGET_MS`，含义：用于表示默认、上传、时间、budget、ms。
    private static final double DEFAULT_UPLOAD_TIME_BUDGET_MS = 1.25; // meaning
    // 中文标注（字段）：`RECENTLY_VISIBLE_BIAS_FRAMES`，含义：用于表示recently、visible、bias、frames。
    private static final int RECENTLY_VISIBLE_BIAS_FRAMES = 45; // meaning
    // 中文标注（字段）：`PRIORITY_FORWARD_BIAS_WEIGHT`，含义：用于表示priority、forward、bias、weight。
    private static final double PRIORITY_FORWARD_BIAS_WEIGHT = 48.0; // meaning
    // 中文标注（字段）：`PRIORITY_RECENT_VISIBLE_BIAS`，含义：用于表示priority、recent、visible、bias。
    private static final double PRIORITY_RECENT_VISIBLE_BIAS = 24.0; // meaning
    // 中文标注（字段）：`OCCLUSION_HIDDEN_HYSTERESIS_FRAMES`，含义：用于表示occlusion、hidden、hysteresis、frames。
    private static final int OCCLUSION_HIDDEN_HYSTERESIS_FRAMES = 2; // meaning
    // 中文标注（字段）：`OCCLUSION_RESAMPLE_INTERVAL_FRAMES`，含义：用于表示occlusion、resample、interval、frames。
    private static final int OCCLUSION_RESAMPLE_INTERVAL_FRAMES = 10; // meaning
    // 中文标注（字段）：`OCCLUSION_MAX_QUERIES_PER_FRAME`，含义：用于表示occlusion、最大、queries、per、帧。
    private static final int OCCLUSION_MAX_QUERIES_PER_FRAME = 96; // meaning
    // 中文标注（字段）：`DEFAULT_OCCLUSION_RESULT_POLL_BUDGET`，含义：用于表示默认、occlusion、结果、poll、budget。
    private static final int DEFAULT_OCCLUSION_RESULT_POLL_BUDGET = 192; // meaning
    // 中文标注（字段）：`DEFAULT_LOD_START_CHUNK_DISTANCE`，含义：用于表示默认、细节层级、开始、区块、distance。
    private static final int DEFAULT_LOD_START_CHUNK_DISTANCE = 4; // meaning
    // 中文标注（字段）：`DEFAULT_LOD_HYSTERESIS_CHUNKS`，含义：用于表示默认、细节层级、hysteresis、区块集合。
    private static final int DEFAULT_LOD_HYSTERESIS_CHUNKS = 1; // meaning
    // 中文标注（字段）：`DEFAULT_SHARED_ARENA_VERTEX_MB`，含义：用于表示默认、shared、arena、顶点、mb。
    private static final int DEFAULT_SHARED_ARENA_VERTEX_MB = 128; // meaning
    // 中文标注（字段）：`DEFAULT_SHARED_ARENA_INDEX_MB`，含义：用于表示默认、shared、arena、索引、mb。
    private static final int DEFAULT_SHARED_ARENA_INDEX_MB = 64; // meaning
    private static final int MAX_CULL_LOGS_PER_FRAME = 32; // meaning
    // 中文标注（字段）：`VERTEX_STRIDE_BYTES`，含义：用于表示顶点、步长、字节数据。
    private static final int VERTEX_STRIDE_BYTES = ChunkMesher.GPU_VERTEX_STRIDE_BYTES; // meaning
    // 中文标注（字段）：`POSITION_OFFSET_BYTES`，含义：用于表示位置、偏移、字节数据。
    private static final long POSITION_OFFSET_BYTES = 0L; // meaning
    // 中文标注（字段）：`COLOR_OFFSET_BYTES`，含义：用于表示颜色、偏移、字节数据。
    private static final long COLOR_OFFSET_BYTES = ChunkMesher.GPU_COLOR_OFFSET_BYTES; // meaning
    private static final float[] HOTBAR_COLOR_DIRT = rgb(120, 84, 58);
    private static final float[] HOTBAR_COLOR_STONE = rgb(125, 127, 131);
    private static final float[] HOTBAR_COLOR_GRASS = rgb(101, 178, 83);
    private static final float[] HOTBAR_COLOR_SAND = rgb(215, 201, 150);
    private static final float[] HOTBAR_COLOR_WOOD = rgb(143, 91, 48);
    private static final float[] HOTBAR_COLOR_FALLBACK = rgb(210, 210, 210);
    private static final int MATERIAL_LUT_WIDTH = 4096; // meaning
    private static final int MATERIAL_LUT_HEIGHT = 2; // meaning
    private static final int MATERIAL_PATTERN_RAW_STONE = 0; // meaning
    private static final int MATERIAL_PATTERN_POLISHED = 1; // meaning
    private static final int MATERIAL_PATTERN_BRICKS = 2; // meaning
    private static final int MATERIAL_PATTERN_TILES = 3; // meaning
    private static final int MATERIAL_PATTERN_ORE = 4; // meaning
    private static final int MATERIAL_PATTERN_WOOD = 5; // meaning
    private static final int MATERIAL_PATTERN_ORGANIC = 6; // meaning
    // 中文标注（字段）：`AMBIENT_VERTEX_SHADER_SOURCE`，含义：用于表示环境光、顶点、着色器、source。
    private static final String AMBIENT_VERTEX_SHADER_SOURCE = """
        #version 120
        varying vec4 vPacked;
        varying vec3 vWorldPos;
        void main() {
            gl_Position = ftransform();
            vPacked = gl_Color;
            vWorldPos = gl_Vertex.xyz;
        }
        """;
    // 中文标注（字段）：`AMBIENT_FRAGMENT_SHADER_SOURCE`，含义：用于表示环境光、fragment、着色器、source。
    private static final String AMBIENT_FRAGMENT_SHADER_SOURCE = """
        #version 120
        uniform float uAmbient;
        uniform sampler2D uMatLut;
        varying vec4 vPacked;
        varying vec3 vWorldPos;

        float hash21(vec2 p) {
            return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
        }

        float valueNoise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            float a = hash21(i);
            float b = hash21(i + vec2(1.0, 0.0));
            float c = hash21(i + vec2(0.0, 1.0));
            float d = hash21(i + vec2(1.0, 1.0));
            vec2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
        }

        vec2 faceUv(vec3 worldPos, int faceIndex) {
            if (faceIndex == 0 || faceIndex == 1) {
                return fract(worldPos.xz);
            }
            if (faceIndex == 4 || faceIndex == 5) {
                return fract(worldPos.zy);
            }
            return fract(worldPos.xy);
        }

        vec2 quantizeUv(vec2 uv, float pixels) {
            return (floor(uv * pixels) + 0.5) / pixels;
        }

        void main() {
            int packed16 = int(floor(vPacked.r * 255.0 + 0.5)) + int(floor(vPacked.g * 255.0 + 0.5)) * 256;
            int faceIndex = packed16 / 4096;
            int id = packed16 - faceIndex * 4096;
            float brightness = clamp(vPacked.b, 0.0, 1.0);
            float alpha = clamp(vPacked.a, 0.0, 1.0);
            if (alpha < 0.5) {
                discard;
            }

            float lutX = (clamp(float(id), 0.0, 4095.0) + 0.5) / 4096.0;
            vec4 row0 = texture2D(uMatLut, vec2(lutX, 0.25));
            vec4 row1 = texture2D(uMatLut, vec2(lutX, 0.75));
            vec3 base = row0.rgb;
            vec3 accent = row1.rgb;
            float noiseScale = mix(0.2, 1.4, row0.a);
            float patternType = row1.a * 255.0;
            vec2 uvBase = faceUv(vWorldPos, faceIndex);
            vec2 uv = quantizeUv(uvBase, 16.0);
            vec2 uvFine = quantizeUv(uvBase, 32.0);
            vec3 material = base;

            if (patternType < 0.5) {
                float grain = valueNoise(uvFine * (7.0 * noiseScale) + vec2(float(faceIndex) * 0.73, 0.0));
                float layer = valueNoise(vec2(uv.y * 3.0 + float(faceIndex) * 0.15, floor(vWorldPos.y * 0.25) * 0.4));
                float mixAmount = clamp(grain * 0.55 + layer * 0.25, 0.0, 1.0);
                material = mix(base, accent, mixAmount);
            } else if (patternType < 1.5) {
                float n = valueNoise(uv * (2.0 + noiseScale) + vec2(float(faceIndex) * 0.19, 0.0));
                material = mix(base, accent, 0.14 + n * 0.16);
            } else if (patternType < 2.5) {
                vec2 brickUv = uvBase * 8.0;
                vec2 brickCell = floor(brickUv);
                float offset = step(1.0, mod(brickCell.y, 2.0)) * 0.5;
                vec2 st = fract(vec2(brickUv.x + offset, brickUv.y));
                float mortar = max(step(st.x, 0.06), max(step(0.94, st.x), max(step(st.y, 0.06), step(0.94, st.y))));
                float brickShade = valueNoise((brickCell + vec2(float(faceIndex), 0.0)) * 0.37);
                vec3 brickColor = mix(base, accent, 0.20 + brickShade * 0.35);
                material = mix(brickColor, vec3(0.11, 0.11, 0.11), mortar * 0.95);
            } else if (patternType < 3.5) {
                vec2 tileUv = uvBase * 6.0;
                vec2 tileCell = floor(tileUv);
                vec2 st = fract(tileUv);
                float grout = max(step(st.x, 0.05), max(step(0.95, st.x), max(step(st.y, 0.05), step(0.95, st.y))));
                float tileShade = valueNoise(tileCell * 0.27 + vec2(float(faceIndex) * 0.2, 0.0));
                material = mix(mix(base, accent, tileShade * 0.35), vec3(0.12, 0.12, 0.12), grout * 0.90);
            } else if (patternType < 4.5) {
                float rockN = valueNoise(uvFine * (9.0 * noiseScale) + vec2(float(faceIndex) * 0.33, 0.0));
                float veinN = valueNoise((uvBase + vWorldPos.xy * 0.07) * 5.0 + vec2(float(faceIndex) * 0.17, 3.1));
                float oreMask = step(0.78, veinN);
                vec3 rock = mix(base, accent, rockN * 0.25);
                material = mix(rock, accent * 1.35, oreMask * 0.80);
            } else if (patternType < 5.5) {
                float grain = sin((uvBase.x * 20.0 * noiseScale) + valueNoise(uvFine * 2.0) * 4.0);
                float rings = valueNoise(vec2(uvBase.y * 6.0, uvBase.x * 1.7 + float(faceIndex) * 0.13));
                material = mix(base, accent, 0.25 + (grain * 0.5 + 0.5) * 0.35 + rings * 0.2);
            } else {
                float n = valueNoise(uvFine * (6.5 * noiseScale) + vWorldPos.xz * 0.03);
                float patch = valueNoise(floor(vWorldPos.xz * 0.5) + vec2(float(faceIndex) * 0.2, 0.0));
                float stripe = mix(0.92, 1.08, step(1.0, mod(floor(vWorldPos.x + vWorldPos.z), 2.0)));
                material = mix(base, accent, n * 0.5) * mix(0.95, 1.05, patch) * stripe;
            }

            vec3 lit = material * (brightness * uAmbient);
            gl_FragColor = vec4(clamp(lit, 0.0, 1.0), alpha);
        }
        """;

    // 中文标注（字段）：`mesher`，含义：用于表示mesher。
    private final ChunkMesher mesher = new ChunkMesher(); // meaning
    // 中文标注（字段）：`frustum`，含义：用于表示视锥体。
    private final Frustum frustum = new Frustum(); // meaning
    // 中文标注（字段）：`features`，含义：用于表示features。
    private final GpuConfig features = GpuConfig.load(); // meaning
    // 中文标注（字段）：`gpuChunks`，含义：用于表示GPU、区块集合。
    private final Map<ChunkPos, GpuChunk> gpuChunks = new HashMap<>(); // meaning
    // 中文标注（字段）：`uploadQueue`，含义：用于表示上传、队列。
    private final PriorityBlockingQueue<QueuedMeshUpload> uploadQueue = new PriorityBlockingQueue<>(
        64,
        Comparator
            .comparingDouble(QueuedMeshUpload::priorityKey)
            .thenComparingLong(QueuedMeshUpload::sequence)
    );
    // 中文标注（字段）：`uploadQueueLifecycleLock`，含义：用于表示上传、队列、lifecycle、锁。
    private final Object uploadQueueLifecycleLock = new Object(); // meaning
    // 中文标注（字段）：`inFlightVersion`，含义：用于表示in、flight、版本。
    private final ConcurrentHashMap<ChunkPos, Long> inFlightVersion = new ConcurrentHashMap<>(); // meaning
    // 中文标注（字段）：`meshingJobsInFlight`，含义：用于表示meshing、jobs、in、flight。
    private final AtomicInteger meshingJobsInFlight = new AtomicInteger(); // meaning
    // 中文标注（字段）：`uploadSequence`，含义：用于表示上传、sequence。
    private final AtomicLong uploadSequence = new AtomicLong(); // meaning
    // 中文标注（字段）：`meshTaskSequence`，含义：用于表示网格、task、sequence。
    private final AtomicLong meshTaskSequence = new AtomicLong(); // meaning
    private final AtomicInteger worldEpoch = new AtomicInteger(); // meaning
    // 中文标注（字段）：`meshPool`，含义：用于表示网格、池。
    private final ThreadPoolExecutor meshPool; // meaning
    // 中文标注（字段）：`meshWorkerCount`，含义：用于表示网格、worker、数量。
    private final int meshWorkerCount; // meaning
    // 中文标注（字段）：`uploadBufferPool`，含义：用于表示上传、缓冲区、池。
    private final DirectByteBufferPool uploadBufferPool = new DirectByteBufferPool(BYTE_BUFFER_POOL_BUCKET_LIMIT); // meaning
    // 中文标注（字段）：`scratchLoadedChunks`，含义：用于表示临时工作区、loaded、区块集合。
    private final ArrayList<Chunk> scratchLoadedChunks = new ArrayList<>(); // meaning
    // 中文标注（字段）：`scratchChunksInRange`，含义：用于表示临时工作区、区块集合、in、范围。
    private final ArrayList<Chunk> scratchChunksInRange = new ArrayList<>(); // meaning
    // 中文标注（字段）：`scratchActiveChunkPositions`，含义：用于表示临时工作区、active、区块、positions。
    private final HashSet<ChunkPos> scratchActiveChunkPositions = new HashSet<>(); // meaning
    // 中文标注（字段）：`scratchPruneRemovals`，含义：用于表示临时工作区、prune、removals。
    private final ArrayList<ChunkPos> scratchPruneRemovals = new ArrayList<>(); // meaning
    // 中文标注（字段）：`scratchOcclusionCandidates`，含义：用于表示临时工作区、occlusion、candidates。
    private final ArrayList<GpuChunk> scratchOcclusionCandidates = new ArrayList<>(); // meaning
    // 中文标注（字段）：`scratchMdiChunks`，含义：用于表示临时工作区、mdi、区块集合。
    private final ArrayList<GpuChunk> scratchMdiChunks = new ArrayList<>(); // meaning
    // 中文标注（字段）：`recentlyVisibleFrame`，含义：用于表示recently、visible、帧。
    private final HashMap<ChunkPos, Long> recentlyVisibleFrame = new HashMap<>(); // meaning
    // 中文标注（字段）：`lodSelectionCache`，含义：用于表示细节层级、selection、缓存。
    private final HashMap<ChunkPos, Integer> lodSelectionCache = new HashMap<>(); // meaning
    // 中文标注（字段）：`frameTimeWindowMs`，含义：用于表示帧、时间、窗口、ms。
    private final double[] frameTimeWindowMs = new double[512]; // meaning
    // 中文标注（字段）：`frameTimeSortScratchMs`，含义：用于表示帧、时间、sort、临时工作区、ms。
    private final double[] frameTimeSortScratchMs = new double[512]; // meaning

    // 中文标注（字段）：`perfWindowStartNanos`，含义：用于表示perf、窗口、开始、nanos。
    private long perfWindowStartNanos = System.nanoTime(); // meaning
    // 中文标注（字段）：`perfFrames`，含义：用于表示perf、frames。
    private int perfFrames; // meaning
    // 中文标注（字段）：`perfUploadJobs`，含义：用于表示perf、上传、jobs。
    private long perfUploadJobs; // meaning
    // 中文标注（字段）：`perfUploadBytes`，含义：用于表示perf、上传、字节数据。
    private long perfUploadBytes; // meaning
    // 中文标注（字段）：`perfUploadDropped`，含义：用于表示perf、上传、dropped。
    private long perfUploadDropped; // meaning
    // 中文标注（字段）：`perfBufferReallocs`，含义：用于表示perf、缓冲区、reallocs。
    private long perfBufferReallocs; // meaning
    // 中文标注（字段）：`perfBufferOrphans`，含义：用于表示perf、缓冲区、orphans。
    private long perfBufferOrphans; // meaning
    // 中文标注（字段）：`perfBufferSubDatas`，含义：用于表示perf、缓冲区、sub、datas。
    private long perfBufferSubDatas; // meaning
    // 中文标注（字段）：`perfMeshingQueueTopPriority`，含义：用于表示perf、meshing、队列、顶面、priority。
    private double perfMeshingQueueTopPriority = Double.NaN; // meaning
    // 中文标注（字段）：`perfUploadQueueTopPriority`，含义：用于表示perf、上传、队列、顶面、priority。
    private double perfUploadQueueTopPriority = Double.NaN; // meaning
    // 中文标注（字段）：`perfVisibleLatencyNanosTotal`，含义：用于表示perf、visible、latency、nanos、total。
    private long perfVisibleLatencyNanosTotal; // meaning
    // 中文标注（字段）：`perfVisibleLatencySamples`，含义：用于表示perf、visible、latency、samples。
    private int perfVisibleLatencySamples; // meaning
    // 中文标注（字段）：`perfOcclusionQueries`，含义：用于表示perf、occlusion、queries。
    private long perfOcclusionQueries; // meaning
    // 中文标注（字段）：`perfOcclusionCulledChunks`，含义：用于表示perf、occlusion、culled、区块集合。
    private long perfOcclusionCulledChunks; // meaning
    // 中文标注（字段）：`perfOcclusionQueryPolls`，含义：用于表示perf、occlusion、query、polls。
    private long perfOcclusionQueryPolls; // meaning
    // 中文标注（字段）：`perfOcclusionQueryDeferredPolls`，含义：用于表示perf、occlusion、query、deferred、polls。
    private long perfOcclusionQueryDeferredPolls; // meaning
    // 中文标注（字段）：`perfOcclusionQueryReadStalls`，含义：用于表示perf、occlusion、query、读取、stalls。
    private long perfOcclusionQueryReadStalls; // meaning
    // 中文标注（字段）：`perfMdiBatches`，含义：用于表示perf、mdi、batches。
    private long perfMdiBatches; // meaning
    // 中文标注（字段）：`perfMdiChunks`，含义：用于表示perf、mdi、区块集合。
    private long perfMdiChunks; // meaning
    // 中文标注（字段）：`perfMdiFallbackChunks`，含义：用于表示perf、mdi、fallback、区块集合。
    private long perfMdiFallbackChunks; // meaning
    // 中文标注（字段）：`perfDrawElementsChunks`，含义：用于表示perf、绘制、elements、区块集合。
    private long perfDrawElementsChunks; // meaning
    // 中文标注（字段）：`perfSharedArenaDrawChunks`，含义：用于表示perf、shared、arena、绘制、区块集合。
    private long perfSharedArenaDrawChunks; // meaning
    // 中文标注（字段）：`perfLocalDrawChunks`，含义：用于表示perf、局部、绘制、区块集合。
    private long perfLocalDrawChunks; // meaning
    // 中文标注（字段）：`perfMdiCommandBytes`，含义：用于表示perf、mdi、command、字节数据。
    private long perfMdiCommandBytes; // meaning
    // 中文标注（字段）：`perfMdiCommandBufferReallocs`，含义：用于表示perf、mdi、command、缓冲区、reallocs。
    private long perfMdiCommandBufferReallocs; // meaning
    // 中文标注（字段）：`perfMdiCommandBufferOrphans`，含义：用于表示perf、mdi、command、缓冲区、orphans。
    private long perfMdiCommandBufferOrphans; // meaning
    // 中文标注（字段）：`perfSharedArenaAllocFailures`，含义：用于表示perf、shared、arena、alloc、failures。
    private long perfSharedArenaAllocFailures; // meaning
    // 中文标注（字段）：`perfSharedArenaFallbackUploads`，含义：用于表示perf、shared、arena、fallback、uploads。
    private long perfSharedArenaFallbackUploads; // meaning
    // 中文标注（字段）：`adaptiveUploadsPerFrame`，含义：用于表示adaptive、uploads、per、帧。
    private int adaptiveUploadsPerFrame = DEFAULT_UPLOADS_PER_FRAME; // meaning
    // 中文标注（字段）：`adaptiveMeshSubmitsPerFrame`，含义：用于表示adaptive、网格、submits、per、帧。
    private int adaptiveMeshSubmitsPerFrame = DEFAULT_MESH_SUBMITS_PER_FRAME; // meaning
    // 中文标注（字段）：`renderCpuMsEma`，含义：用于表示渲染、CPU、ms、ema。
    private double renderCpuMsEma = -1.0; // meaning
    // 中文标注（字段）：`frameTimeWindowIndex`，含义：用于表示帧、时间、窗口、索引。
    private int frameTimeWindowIndex; // meaning
    // 中文标注（字段）：`frameTimeWindowCount`，含义：用于表示帧、时间、窗口、数量。
    private int frameTimeWindowCount; // meaning
    // 中文标注（字段）：`frameSequence`，含义：用于表示帧、sequence。
    private long frameSequence; // meaning
    // 中文标注（字段）：`lastMeshingSubmitNanos`，含义：用于表示last、meshing、submit、nanos。
    private long lastMeshingSubmitNanos; // meaning
    // 中文标注（字段）：`lastUploadQueueDrainNanos`，含义：用于表示last、上传、队列、drain、nanos。
    private long lastUploadQueueDrainNanos; // meaning
    // 中文标注（字段）：`lastDrawLoopNanos`，含义：用于表示last、绘制、loop、nanos。
    private long lastDrawLoopNanos; // meaning
    // 中文标注（字段）：`glCapabilitiesLogged`，含义：用于表示OpenGL、capabilities、logged。
    private boolean glCapabilitiesLogged; // meaning
    // 中文标注（字段）：`supportsMdiCore43`，含义：用于表示supports、mdi、core、43。
    private boolean supportsMdiCore43; // meaning
    // 中文标注（字段）：`supportsMdiArb`，含义：用于表示supports、mdi、arb。
    private boolean supportsMdiArb; // meaning
    // 中文标注（字段）：`supportsMdi`，含义：用于表示supports、mdi。
    private boolean supportsMdi; // meaning
    // 中文标注（字段）：`supportsOcclusionQuery`，含义：用于表示supports、occlusion、query。
    private boolean supportsOcclusionQuery; // meaning
    // 中文标注（字段）：`supportsPersistentMapping`，含义：用于表示supports、persistent、mapping。
    private boolean supportsPersistentMapping; // meaning
    // 中文标注（字段）：`occlusionBoxMesh`，含义：用于表示occlusion、box、网格。
    private OcclusionBoxMesh occlusionBoxMesh; // meaning
    // 中文标注（字段）：`sharedChunkBufferArena`，含义：用于表示shared、区块、缓冲区、arena。
    private SharedChunkBufferArena sharedChunkBufferArena; // meaning
    // 中文标注（字段）：`mdiIndirectBufferId`，含义：用于表示mdi、indirect、缓冲区、标识。
    private int mdiIndirectBufferId; // meaning
    // 中文标注（字段）：`mdiIndirectBufferCapacityBytes`，含义：用于表示mdi、indirect、缓冲区、capacity、字节数据。
    private int mdiIndirectBufferCapacityBytes; // meaning
    // 中文标注（字段）：`mdiCommandUploadBytes`，含义：用于表示mdi、command、上传、字节数据。
    private ByteBuffer mdiCommandUploadBytes; // meaning
    // 中文标注（字段）：`mdiCommandScratch`，含义：用于表示mdi、command、临时工作区。
    private int[] mdiCommandScratch = new int[5 * 64]; // meaning
    // 中文标注（字段）：`ambientShaderProgramId`，含义：用于表示环境光、着色器、program、标识。
    private int ambientShaderProgramId; // meaning
    // 中文标注（字段）：`ambientUniformLocation`，含义：用于表示环境光、uniform、location。
    private int ambientUniformLocation = -1; // meaning
    private int materialLutUniformLocation = -1; // meaning
    private int materialLutTextureId; // meaning
    // 中文标注（字段）：`latestTitleStats`，含义：用于表示latest、title、stats。
    private volatile String latestTitleStats = "gpu init"; // meaning
    // 中文标注（字段）：`closing`，含义：用于表示closing。
    private volatile boolean closing; // meaning
    private long lastWorldSeed = Long.MIN_VALUE; // meaning
    // 中文标注（字段）：`latestMeshCaptureMinY`，含义：用于表示latest、网格、capture、最小、Y坐标。
    private volatile int latestMeshCaptureMinY = World.MIN_Y; // meaning
    // 中文标注（字段）：`latestMeshCaptureMaxY`，含义：用于表示latest、网格、capture、最大、Y坐标。
    private volatile int latestMeshCaptureMaxY = World.MAX_Y; // meaning
    // 中文标注（字段）：`latestMeshCaptureBandKey`，含义：用于表示latest、网格、capture、带、键。
    private volatile int latestMeshCaptureBandKey; // meaning
    // 中文标注（字段）：`latestMeshConfigHash`，含义：用于表示latest、网格、config、hash。
    private volatile int latestMeshConfigHash; // meaning

    // 中文标注（构造方法）：`GpuChunkRenderer`，参数：无；用途：初始化`GpuChunkRenderer`实例。
    public GpuChunkRenderer() {
        meshWorkerCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        meshPool = new ThreadPoolExecutor(
            meshWorkerCount,
            meshWorkerCount,
            0L,
            TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<>(),
            // 中文标注（Lambda参数）：`runnable`，含义：用于表示runnable。
            runnable -> {
                // 中文标注（局部变量）：`thread`，含义：用于表示thread。
                Thread thread = new Thread(runnable, "voxelcraft-chunk-mesher"); // meaning
                thread.setDaemon(true);
                return thread;
            }
        );
    }

    // 中文标注（方法）：`render`，参数：width、height、gameClient；用途：执行渲染或图形资源处理：渲染。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    // 中文标注（参数）：`gameClient`，含义：用于表示game、客户端。
    public synchronized RenderStats render(int width, int height, GameClient gameClient) {
        if (closing) {
            return new RenderStats(0, 0, 0);
        }
        // 中文标注（局部变量）：`renderStarted`，含义：用于表示渲染、started。
        long renderStarted = System.nanoTime(); // meaning
        initializeCapabilitiesIfNeeded();
        ensureAmbientShaderProgram();
        ensureMaterialLutTexture();
        frameSequence++;
        // 中文标注（局部变量）：`safeWidth`，含义：用于表示safe、宽度。
        int safeWidth = clampViewportDimension(width); // meaning
        // 中文标注（局部变量）：`safeHeight`，含义：用于表示safe、高度。
        int safeHeight = clampViewportDimension(height); // meaning

        // 中文标注（局部变量）：`player`，含义：用于表示玩家。
        PlayerController player = gameClient.playerController(); // meaning
        // 中文标注（局部变量）：`worldView`，含义：用于表示世界、view。
        ClientWorldView worldView = gameClient.worldView(); // meaning
        // 中文标注（局部变量）：`ambient`，含义：用于表示环境光。
        float ambient = gameClient.ambientLight(); // meaning
        long worldSeed = worldView.world().seed(); // meaning
        if (worldSeed != lastWorldSeed) {
            lastWorldSeed = worldSeed;
            resetForWorldSwitch();
        }

        prepareFrameGlState(safeWidth, safeHeight, ambient);
        try {
            if (features.hud()) {
                renderHudBackgroundOverlay(safeWidth, safeHeight, ambient);
            }
            configurePlayerCameraAndFrustum(player, safeWidth, safeHeight);

            // 中文标注（局部变量）：`frameSet`，含义：用于表示帧、集合。
            ChunkFrameSet frameSet = collectChunksInRange(worldView, player); // meaning
            // 中文标注（局部变量）：`meshingSubmitStarted`，含义：用于表示meshing、submit、started。
            long meshingSubmitStarted = System.nanoTime(); // meaning
            submitMeshJobsForDirtyChunks(worldView, frameSet.chunks(), player);
            lastMeshingSubmitNanos = System.nanoTime() - meshingSubmitStarted;

            // 中文标注（局部变量）：`uploadDrainStarted`，含义：用于表示上传、drain、started。
            long uploadDrainStarted = System.nanoTime(); // meaning
            processUploadQueue(worldView, player, adaptiveUploadsPerFrame, features.uploadTimeBudgetMs());
            lastUploadQueueDrainNanos = System.nanoTime() - uploadDrainStarted;
            pruneGpuChunks(frameSet.positions());

            // 中文标注（局部变量）：`drawLoopStarted`，含义：用于表示绘制、loop、started。
            long drawLoopStarted = System.nanoTime(); // meaning
            // 中文标注（局部变量）：`frameStats`，含义：用于表示帧、stats。
            FrameStats frameStats = renderVisibleChunks(frameSet.chunks(), ambient, player); // meaning
            if (features.hud()) {
                renderHudForegroundOverlay(safeWidth, safeHeight, gameClient);
            }
            lastDrawLoopNanos = System.nanoTime() - drawLoopStarted;
            // 中文标注（局部变量）：`renderCpuNanos`，含义：用于表示渲染、CPU、nanos。
            long renderCpuNanos = System.nanoTime() - renderStarted; // meaning
            recordFrameTime(renderCpuNanos);
            updateAdaptiveBudgets(renderCpuNanos);
            emitPerfLine(frameStats, frameSet.chunks().size());

            int totalFaces = frameStats.totalCandidateTriangles() / 2; // meaning
            int frustumFaces = frameStats.frustumCandidateTriangles() / 2; // meaning
            int drawnFaces = frameStats.totalTriangles() / 2; // meaning
            return new RenderStats(totalFaces, frustumFaces, drawnFaces);
        } finally {
            glFrontFace(GL_CCW);
        }
    }

    private static int clampViewportDimension(int value) {
        return Math.max(1, value);
    }

    private synchronized void resetForWorldSwitch() {
        int epoch = worldEpoch.incrementAndGet(); // meaning
        for (GpuChunk gpuChunk : gpuChunks.values()) {
            gpuChunk.dispose(sharedChunkBufferArena);
        }
        gpuChunks.clear();
        synchronized (uploadQueueLifecycleLock) {
            QueuedMeshUpload pending; // meaning
            while ((pending = uploadQueue.poll()) != null) {
                pending.meshData().releaseBuffers(uploadBufferPool);
            }
        }
        inFlightVersion.clear();
        recentlyVisibleFrame.clear();
        lodSelectionCache.clear();
        System.out.printf("[gpu-world] renderer reset for world switch seed=%d epoch=%d%n", lastWorldSeed, epoch);
    }

    private void prepareFrameGlState(int width, int height, float ambient) {
        glViewport(0, 0, width, height);

        float skyR = 0.31f * ambient; // meaning
        float skyG = 0.53f * ambient; // meaning
        float skyB = 0.78f * ambient; // meaning
        glClearColor(skyR, skyG, skyB, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glEnable(GL_DEPTH_TEST);
        if (features.disableFaceCulling()) {
            glDisable(GL_CULL_FACE);
        } else {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
        }
        // We use a Z reflection in the view transform to match the engine's +Z-forward camera convention.
        // Reflection flips winding, so front-face must be switched to CW for correct culling.
        glFrontFace(GL_CW);
    }

    private void configurePlayerCameraAndFrustum(PlayerController player, int width, int height) {
        configureProjection(width, height);
        configureCamera(player);

        double aspect = (double) width / (double) height; // meaning
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
    }

    private void renderHudBackgroundOverlay(int width, int height, float ambient) {
        beginScreenSpaceOverlay(width, height);
        try {
            float skyTopR = colorComponent(94, ambient); // meaning
            float skyTopG = colorComponent(170, ambient); // meaning
            float skyTopB = colorComponent(240, ambient); // meaning
            float skyBottomR = colorComponent(178, ambient); // meaning
            float skyBottomG = colorComponent(225, ambient); // meaning
            float skyBottomB = colorComponent(255, ambient); // meaning

            glBegin(GL_QUADS);
            glColor4f(skyTopR, skyTopG, skyTopB, 1.0f);
            glVertex2f(0.0f, 0.0f);
            glVertex2f((float) width, 0.0f);
            glColor4f(skyBottomR, skyBottomG, skyBottomB, 1.0f);
            glVertex2f((float) width, (float) height);
            glVertex2f(0.0f, (float) height);
            glEnd();

            int horizonY = (int) (height * 0.72); // meaning
            float horizonAmbient = ambient * 0.85f; // meaning
            glColor4f(
                colorComponent(84, horizonAmbient),
                colorComponent(140, horizonAmbient),
                colorComponent(72, horizonAmbient),
                1.0f
            );
            glBegin(GL_QUADS);
            glVertex2f(0.0f, (float) horizonY);
            glVertex2f((float) width, (float) horizonY);
            glVertex2f((float) width, (float) height);
            glVertex2f(0.0f, (float) height);
            glEnd();
        } finally {
            endScreenSpaceOverlay();
        }
    }

    private void renderHudForegroundOverlay(int width, int height, GameClient gameClient) {
        beginScreenSpaceOverlay(width, height);
        try {
            drawOverlayCrosshair(width, height);
            drawOverlayHotbar(width, height, gameClient);
        } finally {
            endScreenSpaceOverlay();
        }
    }

    private void beginScreenSpaceOverlay(int width, int height) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_TEXTURE_2D);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0.0, width, height, 0.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
    }

    private void endScreenSpaceOverlay() {
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);

        glDisable(GL_BLEND);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glLineWidth(1.0f);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        if (!features.disableFaceCulling()) {
            glEnable(GL_CULL_FACE);
        }
    }

    private void drawOverlayCrosshair(int width, int height) {
        float cx = width * 0.5f; // meaning
        float cy = height * 0.5f; // meaning
        glColor4f(1.0f, 1.0f, 1.0f, 230.0f / 255.0f);
        glLineWidth(1.0f);
        glBegin(GL_LINES);
        glVertex2f(cx - 8.0f, cy);
        glVertex2f(cx + 8.0f, cy);
        glVertex2f(cx, cy - 8.0f);
        glVertex2f(cx, cy + 8.0f);
        glEnd();
    }

    private void drawOverlayHotbar(int width, int height, GameClient gameClient) {
        int slots = gameClient.hotbarSlotCount(); // meaning
        int selectedSlot = gameClient.selectedHotbarSlot(); // meaning
        int slotSize = 54; // meaning
        int gap = 9; // meaning
        int totalWidth = slots * slotSize + Math.max(0, slots - 1) * gap; // meaning
        int left = (width - totalWidth) / 2; // meaning
        int top = height - slotSize - 26; // meaning

        for (int slot = 0; slot < slots; slot++) { // meaning
            int slotX = left + slot * (slotSize + gap); // meaning
            boolean selected = slot == selectedSlot; // meaning

            if (selected) {
                glColor4f(1.0f, 1.0f, 1.0f, 215.0f / 255.0f);
            } else {
                glColor4f(20.0f / 255.0f, 24.0f / 255.0f, 28.0f / 255.0f, 170.0f / 255.0f);
            }
            glBegin(GL_QUADS);
            glVertex2f(slotX, top);
            glVertex2f(slotX + slotSize, top);
            glVertex2f(slotX + slotSize, top + slotSize);
            glVertex2f(slotX, top + slotSize);
            glEnd();

            if (selected) {
                glColor4f(20.0f / 255.0f, 24.0f / 255.0f, 28.0f / 255.0f, 220.0f / 255.0f);
            } else {
                glColor4f(235.0f / 255.0f, 235.0f / 255.0f, 235.0f / 255.0f, 180.0f / 255.0f);
            }
            glLineWidth(1.0f);
            glBegin(GL_LINES);
            glVertex2f(slotX, top);
            glVertex2f(slotX + slotSize, top);
            glVertex2f(slotX + slotSize, top);
            glVertex2f(slotX + slotSize, top + slotSize);
            glVertex2f(slotX + slotSize, top + slotSize);
            glVertex2f(slotX, top + slotSize);
            glVertex2f(slotX, top + slotSize);
            glVertex2f(slotX, top);
            glEnd();

            Block block = gameClient.hotbarBlockAt(slot); // meaning
            drawOverlayHotbarBlockSwatch(slotX, top, slotSize, block, selected);
        }
    }

    private void drawOverlayHotbarBlockSwatch(int slotX, int top, int slotSize, Block block, boolean selected) {
        int inset = selected ? 7 : 9; // meaning
        int swatchX = slotX + inset; // meaning
        int swatchY = top + inset; // meaning
        int swatchW = slotSize - inset * 2; // meaning
        int swatchH = slotSize - inset * 2; // meaning
        if (swatchW <= 2 || swatchH <= 2) {
            return;
        }

        float[] base = hotbarBlockColor(block); // meaning
        float alpha = selected ? 0.96f : 0.88f; // meaning
        float highlight = selected ? 1.20f : 1.12f; // meaning
        float shadow = selected ? 0.62f : 0.70f; // meaning

        glColor4f(base[0], base[1], base[2], alpha);
        glBegin(GL_QUADS);
        glVertex2f(swatchX, swatchY);
        glVertex2f(swatchX + swatchW, swatchY);
        glVertex2f(swatchX + swatchW, swatchY + swatchH);
        glVertex2f(swatchX, swatchY + swatchH);
        glEnd();

        int topBand = Math.max(2, swatchH / 3); // meaning
        glColor4f(
            Math.min(1.0f, base[0] * highlight),
            Math.min(1.0f, base[1] * highlight),
            Math.min(1.0f, base[2] * highlight),
            alpha
        );
        glBegin(GL_QUADS);
        glVertex2f(swatchX, swatchY);
        glVertex2f(swatchX + swatchW, swatchY);
        glVertex2f(swatchX + swatchW, swatchY + topBand);
        glVertex2f(swatchX, swatchY + topBand);
        glEnd();

        int rightBand = Math.max(2, swatchW / 5); // meaning
        glColor4f(base[0] * shadow, base[1] * shadow, base[2] * shadow, alpha);
        glBegin(GL_QUADS);
        glVertex2f(swatchX + swatchW - rightBand, swatchY);
        glVertex2f(swatchX + swatchW, swatchY);
        glVertex2f(swatchX + swatchW, swatchY + swatchH);
        glVertex2f(swatchX + swatchW - rightBand, swatchY + swatchH);
        glEnd();
    }

    private static float[] hotbarBlockColor(Block block) {
        if (block == Blocks.DIRT) {
            return HOTBAR_COLOR_DIRT;
        }
        if (block == Blocks.STONE) {
            return HOTBAR_COLOR_STONE;
        }
        if (block == Blocks.GRASS) {
            return HOTBAR_COLOR_GRASS;
        }
        if (block == Blocks.SAND) {
            return HOTBAR_COLOR_SAND;
        }
        if (block == Blocks.WOOD) {
            return HOTBAR_COLOR_WOOD;
        }
        return HOTBAR_COLOR_FALLBACK;
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[] {r / 255.0f, g / 255.0f, b / 255.0f};
    }

    private static float colorComponent(int value, float multiplier) {
        return Math.max(0.0f, Math.min(255.0f, value * multiplier)) / 255.0f;
    }

    private void ensureMaterialLutTexture() {
        if (materialLutTextureId != 0) {
            return;
        }
        ByteBuffer pixels = buildMaterialLutTextureData(); // meaning
        materialLutTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, materialLutTextureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, MATERIAL_LUT_WIDTH, MATERIAL_LUT_HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private ByteBuffer buildMaterialLutTextureData() {
        MaterialEntry[] entries = new MaterialEntry[MATERIAL_LUT_WIDTH]; // meaning
        for (int id = 0; id < entries.length; id++) { // meaning
            entries[id] = fallbackMaterialEntry(id);
        }
        for (BlockDef def : Blocks.definitions().all()) {
            int id = def.id().asUnsignedInt(); // meaning
            if (id < 0 || id >= MATERIAL_LUT_WIDTH) {
                continue;
            }
            entries[id] = materialEntryFor(def, id);
        }

        ByteBuffer bytes = ByteBuffer.allocateDirect(MATERIAL_LUT_WIDTH * MATERIAL_LUT_HEIGHT * 4); // meaning
        for (int x = 0; x < MATERIAL_LUT_WIDTH; x++) { // meaning
            MaterialEntry entry = entries[x]; // meaning
            bytes.put((byte) entry.baseR());
            bytes.put((byte) entry.baseG());
            bytes.put((byte) entry.baseB());
            bytes.put((byte) entry.noiseByte());
        }
        for (int x = 0; x < MATERIAL_LUT_WIDTH; x++) { // meaning
            MaterialEntry entry = entries[x]; // meaning
            bytes.put((byte) entry.accentR());
            bytes.put((byte) entry.accentG());
            bytes.put((byte) entry.accentB());
            bytes.put((byte) entry.patternByte());
        }
        bytes.flip();
        return bytes;
    }

    private static MaterialEntry fallbackMaterialEntry(int id) {
        int seed = hashMaterial(id, 0x6D2B79F5, 0x9E3779B9, 0x85EBCA6B); // meaning
        int baseR = clampByte(82 + (seed & 63));
        int baseG = clampByte(88 + ((seed >>> 6) & 63));
        int baseB = clampByte(90 + ((seed >>> 12) & 63));
        int accentR = clampByte(baseR + 18);
        int accentG = clampByte(baseG + 16);
        int accentB = clampByte(baseB + 14);
        return new MaterialEntry(baseR, baseG, baseB, 140, accentR, accentG, accentB, MATERIAL_PATTERN_RAW_STONE);
    }

    private static MaterialEntry materialEntryFor(BlockDef def, int id) {
        String category = lower(def.category()); // meaning
        String material = lower(def.material()); // meaning
        String variant = lower(def.variant()); // meaning
        String key = lower(def.key()); // meaning
        int seed = hashMaterial(id, category.hashCode(), material.hashCode(), variant.hashCode()); // meaning
        int pattern = patternTypeFor(category, material, key, variant); // meaning

        int[] base = baseColorFor(category, material, key, seed); // meaning
        int[] accent = accentColorFor(pattern, base, material, key, seed); // meaning
        int noiseByte = noiseScaleByte(pattern, seed); // meaning
        return new MaterialEntry(
            base[0],
            base[1],
            base[2],
            noiseByte,
            accent[0],
            accent[1],
            accent[2],
            clampByte(pattern)
        );
    }

    private static int patternTypeFor(String category, String material, String key, String variant) {
        if (category.contains("ore:") || material.contains("ore") || key.contains("ore_") || key.contains("_ore_")) {
            return MATERIAL_PATTERN_ORE;
        }
        if (containsAny(material, key, variant, "wood", "bark", "sapwood", "heartwood", "timber")) {
            return MATERIAL_PATTERN_WOOD;
        }
        if (containsAny(material, key, variant, "brick", "cobbled")) {
            return MATERIAL_PATTERN_BRICKS;
        }
        if (containsAny(material, key, variant, "tile", "slab")) {
            return MATERIAL_PATTERN_TILES;
        }
        if (containsAny(material, key, variant, "polished", "smooth")) {
            return MATERIAL_PATTERN_POLISHED;
        }
        if (category.startsWith("biology:") || containsAny(material, key, variant, "moss", "leaf", "humus", "fung", "biofilm")) {
            return MATERIAL_PATTERN_ORGANIC;
        }
        return MATERIAL_PATTERN_RAW_STONE;
    }

    private static int[] baseColorFor(String category, String material, String key, int seed) {
        int[] base;
        if (containsAny(material, key, category, "sand", "saline", "chalk", "loess")) {
            base = new int[] {214, 198, 148};
        } else if (containsAny(material, key, category, "soil", "mud", "dirt", "humus", "clay", "loam", "peat")) {
            base = new int[] {124, 96, 70};
        } else if (containsAny(material, key, category, "wood", "bark", "sapwood", "heartwood")) {
            base = new int[] {132, 94, 58};
        } else if (containsAny(material, key, category, "leaf", "moss", "lichen", "biofilm", "algae", "grass")) {
            base = new int[] {86, 146, 84};
        } else if (containsAny(material, key, category, "coral", "sponge")) {
            base = new int[] {186, 132, 134};
        } else if (category.startsWith("geology:")) {
            base = new int[] {132, 136, 143};
        } else {
            base = new int[] {96 + (seed & 31), 100 + ((seed >>> 5) & 31), 104 + ((seed >>> 10) & 31)};
        }
        int jitterR = ((seed >>> 15) & 15) - 7; // meaning
        int jitterG = ((seed >>> 19) & 15) - 7; // meaning
        int jitterB = ((seed >>> 23) & 15) - 7; // meaning
        return new int[] {
            clampByte(base[0] + jitterR),
            clampByte(base[1] + jitterG),
            clampByte(base[2] + jitterB)
        };
    }

    private static int[] accentColorFor(int pattern, int[] base, String material, String key, int seed) {
        if (pattern == MATERIAL_PATTERN_ORE) {
            int warm = containsAny(material, key, "", "copper", "gold", "sulfur", "phosphate") ? 34 : 8; // meaning
            return new int[] {
                clampByte(base[0] + 52 + warm),
                clampByte(base[1] + 44 + (seed & 15)),
                clampByte(base[2] + 32 + ((seed >>> 4) & 23))
            };
        }
        if (pattern == MATERIAL_PATTERN_WOOD) {
            return new int[] {
                clampByte(base[0] + 30),
                clampByte(base[1] + 20),
                clampByte(base[2] + 12)
            };
        }
        if (pattern == MATERIAL_PATTERN_ORGANIC) {
            return new int[] {
                clampByte(base[0] + 18),
                clampByte(base[1] + 36),
                clampByte(base[2] + 16)
            };
        }
        if (pattern == MATERIAL_PATTERN_POLISHED) {
            return new int[] {
                clampByte(base[0] + 22),
                clampByte(base[1] + 22),
                clampByte(base[2] + 22)
            };
        }
        return new int[] {
            clampByte(base[0] + 16 + (seed & 7)),
            clampByte(base[1] + 16 + ((seed >>> 3) & 7)),
            clampByte(base[2] + 16 + ((seed >>> 6) & 7))
        };
    }

    private static int noiseScaleByte(int pattern, int seed) {
        return switch (pattern) {
            case MATERIAL_PATTERN_POLISHED -> clampByte(48 + (seed & 15));
            case MATERIAL_PATTERN_BRICKS -> clampByte(82 + (seed & 12));
            case MATERIAL_PATTERN_TILES -> clampByte(70 + (seed & 14));
            case MATERIAL_PATTERN_ORE -> clampByte(186 + (seed & 25));
            case MATERIAL_PATTERN_WOOD -> clampByte(146 + (seed & 18));
            case MATERIAL_PATTERN_ORGANIC -> clampByte(172 + (seed & 24));
            default -> clampByte(124 + (seed & 55));
        };
    }

    private static boolean containsAny(String a, String b, String c, String... needles) {
        for (String needle : needles) {
            if ((!a.isEmpty() && a.contains(needle))
                || (!b.isEmpty() && b.contains(needle))
                || (!c.isEmpty() && c.contains(needle))) {
                return true;
            }
        }
        return false;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static int hashMaterial(int a, int b, int c, int d) {
        int value = a * 0x9E3779B9 + b * 0x7F4A7C15 + c * 0x85EBCA6B + d * 0xC2B2AE35; // meaning
        value ^= (value >>> 15);
        value *= 0x2C1B3C6D;
        value ^= (value >>> 12);
        return value;
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private record MaterialEntry(
        int baseR,
        int baseG,
        int baseB,
        int noiseByte,
        int accentR,
        int accentG,
        int accentB,
        int patternByte
    ) {
    }

    // 中文标注（方法）：`close`，参数：无；用途：执行close相关逻辑。
    @Override
    public synchronized void close() {
        synchronized (uploadQueueLifecycleLock) {
            if (closing) {
                return;
            }
            closing = true;
        }
        List<Runnable> cancelledMeshingTasks = meshPool.shutdownNow(); // meaning
        // shutdownNow 返回的是尚未开始执行的任务；这里需要显式释放它们持有的 snapshot 等资源。
        for (Runnable cancelledTask : cancelledMeshingTasks) {
            if (cancelledTask instanceof PrioritizedMeshTask prioritizedMeshTask) {
                prioritizedMeshTask.cancel();
            }
        }
        // 中文标注（局部变量）：`gpuChunk`，含义：用于表示GPU、区块。
        for (GpuChunk gpuChunk : gpuChunks.values()) {
            gpuChunk.dispose(sharedChunkBufferArena);
        }
        gpuChunks.clear();
        // 中文标注（局部变量）：`pending`，含义：用于表示pending。
        synchronized (uploadQueueLifecycleLock) {
            QueuedMeshUpload pending; // meaning
            while ((pending = uploadQueue.poll()) != null) {
                pending.meshData().releaseBuffers(uploadBufferPool);
            }
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
            materialLutUniformLocation = -1;
        }
        if (materialLutTextureId != 0) {
            glDeleteTextures(materialLutTextureId);
            materialLutTextureId = 0;
        }
        mdiCommandUploadBytes = null;
        uploadBufferPool.clear();
    }

    // 中文标注（方法）：`collectChunksInRange`，参数：worldView、player；用途：执行collect、区块集合、in、范围相关逻辑。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    // 中文标注（参数）：`player`，含义：用于表示玩家。
    private ChunkFrameSet collectChunksInRange(ClientWorldView worldView, PlayerController player) {
        worldView.copyLoadedChunksInto(scratchLoadedChunks);
        scratchChunksInRange.clear();
        scratchActiveChunkPositions.clear();

        // 中文标注（局部变量）：`playerChunkX`，含义：用于表示玩家、区块、X坐标。
        int playerChunkX = Math.floorDiv((int) Math.floor(player.x()), Section.SIZE); // meaning
        // 中文标注（局部变量）：`playerChunkZ`，含义：用于表示玩家、区块、Z坐标。
        int playerChunkZ = Math.floorDiv((int) Math.floor(player.z()), Section.SIZE); // meaning

        // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
        for (Chunk chunk : scratchLoadedChunks) {
            // 中文标注（局部变量）：`dx`，含义：用于表示dx。
            int dx = Math.abs(chunk.pos().x() - playerChunkX); // meaning
            // 中文标注（局部变量）：`dz`，含义：用于表示dz。
            int dz = Math.abs(chunk.pos().z() - playerChunkZ); // meaning
            if (dx > RENDER_CHUNK_RADIUS || dz > RENDER_CHUNK_RADIUS) {
                continue;
            }
            scratchChunksInRange.add(chunk);
            scratchActiveChunkPositions.add(chunk.pos());
        }

        // 中文标注（局部变量）：`playerX`，含义：用于表示玩家、X坐标。
        double playerX = player.x(); // meaning
        // 中文标注（局部变量）：`playerZ`，含义：用于表示玩家、Z坐标。
        double playerZ = player.z(); // meaning
        if (features.priorityForwardBias() || features.priorityRecentlyVisibleBias()) {
            // 中文标注（局部变量）：`lookX`，含义：用于表示look、X坐标。
            double lookX = player.lookDirX(); // meaning
            // 中文标注（局部变量）：`lookZ`，含义：用于表示look、Z坐标。
            double lookZ = player.lookDirZ(); // meaning
            // 中文标注（Lambda参数）：`chunk`，含义：用于表示区块。
            scratchChunksInRange.sort(Comparator.comparingDouble(chunk -> chunkPriorityKey(chunk, playerX, playerZ, lookX, lookZ))); // meaning
        } else {
            // 中文标注（Lambda参数）：`chunk`，含义：用于表示区块。
            scratchChunksInRange.sort(Comparator.comparingDouble(chunk -> chunkDistanceSq(chunk, playerX, playerZ))); // meaning
        }

        return new ChunkFrameSet(scratchChunksInRange, scratchActiveChunkPositions);
    }

    // 中文标注（方法）：`submitMeshJobsForDirtyChunks`，参数：worldView、chunksInRange、player；用途：执行submit、网格、jobs、for、dirty、区块集合相关逻辑。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    // 中文标注（参数）：`chunksInRange`，含义：用于表示区块集合、in、范围。
    // 中文标注（参数）：`player`，含义：用于表示玩家。
    private void submitMeshJobsForDirtyChunks(ClientWorldView worldView, List<Chunk> chunksInRange, PlayerController player) {
        int submitWorldEpoch = worldEpoch.get(); // meaning
        // 中文标注（局部变量）：`playerX`，含义：用于表示玩家、X坐标。
        double playerX = player.x(); // meaning
        // 中文标注（局部变量）：`playerY`，含义：用于表示玩家、Y坐标。
        double playerY = player.y(); // meaning
        // 中文标注（局部变量）：`playerZ`，含义：用于表示玩家、Z坐标。
        double playerZ = player.z(); // meaning
        // 中文标注（局部变量）：`lookX`，含义：用于表示look、X坐标。
        double lookX = player.lookDirX(); // meaning
        // 中文标注（局部变量）：`lookZ`，含义：用于表示look、Z坐标。
        double lookZ = player.lookDirZ(); // meaning

        // 垂直捕获范围是 GPU 几何正确性的关键控制面：
        // 1) fullHeightMeshing=true 时直接覆盖世界全高度，优先保证不漏几何；
        // 2) 否则按 FAR_PLANE 推导带宽，避免出现“视野看得到但 snapshot 没抓到”的蓝洞。
        // 中文标注（局部变量）：`fullHeightMeshing`，含义：用于表示full、高度、meshing。
        boolean fullHeightMeshing = features.fullHeightMeshing(); // meaning
        // 中文标注（局部变量）：`playerBlockY`，含义：用于表示玩家、方块、Y坐标。
        int playerBlockY = (int) Math.floor(playerY); // meaning
        // 中文标注（局部变量）：`margin`，含义：用于表示margin。
        int margin = 32; // meaning
        // 中文标注（局部变量）：`below`，含义：用于表示below。
        int below = (int) Math.ceil(FAR_PLANE) + margin; // meaning
        // 中文标注（局部变量）：`above`，含义：用于表示above。
        int above = (int) Math.ceil(FAR_PLANE) + margin; // meaning
        // 中文标注（局部变量）：`minY`，含义：用于表示最小、Y坐标。
        int minY = Math.max(World.MIN_Y, playerBlockY - below); // meaning
        // 中文标注（局部变量）：`maxY`，含义：用于表示最大、Y坐标。
        int maxY = Math.min(World.MAX_Y, playerBlockY + above); // meaning
        if (fullHeightMeshing) {
            minY = World.MIN_Y;
            maxY = World.MAX_Y;
        }
        latestMeshCaptureMinY = minY;
        latestMeshCaptureMaxY = maxY;
        // 中文标注（局部变量）：`bandKey`，含义：用于表示带、键。
        int bandKey = meshBandKey(minY, maxY); // meaning
        latestMeshCaptureBandKey = bandKey;
        // 中文标注（局部变量）：`meshConfigHash`，含义：用于表示网格、config、hash。
        int meshConfigHash = features.meshConfigHash(); // meaning
        latestMeshConfigHash = meshConfigHash;
        // 中文标注（局部变量）：`submitted`，含义：用于表示submitted。
        int submitted = 0; // meaning

        // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
        for (Chunk chunk : chunksInRange) {
            if (submitted >= adaptiveMeshSubmitsPerFrame) {
                break;
            }

            // 中文标注（局部变量）：`pos`，含义：用于表示位置。
            ChunkPos pos = chunk.pos(); // meaning
            // 中文标注（局部变量）：`currentVersion`，含义：用于表示current、版本。
            long currentVersion = chunk.version(); // meaning
            // 中文标注（局部变量）：`desiredLodLevel`，含义：用于表示desired、细节层级、级别。
            int desiredLodLevel = resolveChunkLodLevel(chunk, playerX, playerZ); // meaning
            // 中文标注（局部变量）：`gpuChunk`，含义：用于表示GPU、区块。
            GpuChunk gpuChunk = gpuChunks.get(pos); // meaning
            // 只要影响几何生成的任一参数变化（version/lod/band/fullHeight/configHash），
            // 就必须强制重新 meshing+upload，禁止复用旧网格。
            if (gpuChunk != null
                && gpuChunk.versionUploaded == currentVersion
                && gpuChunk.lodLevelUploaded == desiredLodLevel
                && gpuChunk.bandKeyUploaded == bandKey
                && gpuChunk.fullHeightMeshingUploaded == fullHeightMeshing
                && gpuChunk.meshConfigHashUploaded == meshConfigHash) {
                continue;
            }

            // 中文标注（局部变量）：`desiredBuildKey`，含义：用于表示desired、构建、键。
            long desiredBuildKey = meshBuildKey(currentVersion, desiredLodLevel, bandKey, fullHeightMeshing, meshConfigHash); // meaning
            // 中文标注（局部变量）：`inFlight`，含义：用于表示in、flight。
            Long inFlight = inFlightVersion.get(pos); // meaning
            if (inFlight != null && inFlight == desiredBuildKey) {
                continue;
            }
            if (inFlight != null) {
                continue;
            }

            if (inFlightVersion.putIfAbsent(pos, desiredBuildKey) != null) {
                continue;
            }

            // 中文标注（局部变量）：`snapshot`，含义：用于表示快照。
            ChunkSnapshot snapshot; // meaning
            try {
                snapshot = mesher.captureChunkSnapshot(worldView, chunk, minY, maxY);
            // 中文标注（异常参数）：`snapshotFailure`，含义：用于表示snapshot、failure。
            } catch (RuntimeException snapshotFailure) {
                inFlightVersion.remove(pos, desiredBuildKey);
                System.err.printf(
                    "[gpu-snapshot] failed chunk=(%d,%d) version=%d lod=%d band=%d buildKey=%d: %s%n",
                    pos.x(),
                    pos.z(),
                    currentVersion,
                    desiredLodLevel,
                    bandKey,
                    desiredBuildKey,
                    snapshotFailure
                );
                continue;
            // 中文标注（异常参数）：`snapshotFailure`，含义：用于表示snapshot、failure。
            } catch (Error snapshotFailure) {
                inFlightVersion.remove(pos, desiredBuildKey);
                throw snapshotFailure;
            }
            // 中文标注（局部变量）：`priorityKey`，含义：用于表示priority、键。
            double priorityKey = chunkPriorityKey(chunk, playerX, playerZ, lookX, lookZ); // meaning
            // 中文标注（局部变量）：`submittedNanos`，含义：用于表示submitted、nanos。
            long submittedNanos = System.nanoTime(); // meaning
            meshingJobsInFlight.incrementAndGet();
            try {
                meshPool.execute(new PrioritizedMeshTask(priorityKey, meshTaskSequence.getAndIncrement(), () -> {
                    // 中文标注（局部变量）：`meshData`，含义：用于表示网格、数据。
                    ChunkMeshData meshData = null; // meaning
                    try {
                        meshData = mesher.buildChunkMesh(snapshot, uploadBufferPool, desiredLodLevel);
                        if (submitWorldEpoch != worldEpoch.get()) {
                            if (meshData != null) {
                                meshData.releaseBuffers(uploadBufferPool);
                            }
                            return;
                        }
                        synchronized (uploadQueueLifecycleLock) {
                            if (closing) {
                                if (submitWorldEpoch == worldEpoch.get()) {
                                    inFlightVersion.remove(pos, desiredBuildKey);
                                }
                                meshData.releaseBuffers(uploadBufferPool);
                                meshData = null;
                                return;
                            }
                            uploadQueue.add(
                                new QueuedMeshUpload(
                                    meshData,
                                    priorityKey,
                                    uploadSequence.getAndIncrement(),
                                    submittedNanos,
                                    desiredBuildKey,
                                    fullHeightMeshing,
                                    meshConfigHash,
                                    submitWorldEpoch
                                )
                            );
                            meshData = null;
                        }
                    // 中文标注（异常参数）：`meshFailure`，含义：用于表示mesh、failure。
                    } catch (RuntimeException meshFailure) {
                        if (submitWorldEpoch == worldEpoch.get()) {
                            inFlightVersion.remove(pos, desiredBuildKey);
                        }
                        if (meshData != null) {
                            meshData.releaseBuffers(uploadBufferPool);
                        }
                        System.err.printf(
                            "[gpu-mesh] failed chunk=(%d,%d) version=%d lod=%d band=%d buildKey=%d: %s%n",
                            pos.x(),
                            pos.z(),
                            currentVersion,
                            desiredLodLevel,
                            bandKey,
                            desiredBuildKey,
                            meshFailure
                        );
                    // 中文标注（异常参数）：`meshFailure`，含义：用于表示mesh、failure。
                    } catch (Error meshFailure) {
                        if (submitWorldEpoch == worldEpoch.get()) {
                            inFlightVersion.remove(pos, desiredBuildKey);
                        }
                        if (meshData != null) {
                            meshData.releaseBuffers(uploadBufferPool);
                        }
                        throw meshFailure;
                    } finally {
                        meshingJobsInFlight.decrementAndGet();
                    }
                }, () -> {
                    if (submitWorldEpoch == worldEpoch.get()) {
                        inFlightVersion.remove(pos, desiredBuildKey);
                    }
                    mesher.discardChunkSnapshot(snapshot);
                    meshingJobsInFlight.decrementAndGet();
                }));
                submitted++;
            // 中文标注（异常参数）：`rejected`，含义：用于表示rejected。
            } catch (RejectedExecutionException rejected) {
                mesher.discardChunkSnapshot(snapshot);
                inFlightVersion.remove(pos, desiredBuildKey);
                meshingJobsInFlight.decrementAndGet();
            }
        }
    }

    // 中文标注（方法）：`processUploadQueue`，参数：worldView、player、maxUploads、uploadBudgetMs；用途：处理process、上传、队列逻辑。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    // 中文标注（参数）：`player`，含义：用于表示玩家。
    // 中文标注（参数）：`maxUploads`，含义：用于表示最大、uploads。
    // 中文标注（参数）：`uploadBudgetMs`，含义：用于表示上传、budget、ms。
    private void processUploadQueue(ClientWorldView worldView, PlayerController player, int maxUploads, double uploadBudgetMs) {
        // 中文标注（局部变量）：`meshHead`，含义：用于表示网格、head。
        PrioritizedMeshTask meshHead = peekMeshingTask(); // meaning
        perfMeshingQueueTopPriority = meshHead == null ? Double.NaN : meshHead.priorityKey();
        // 中文标注（局部变量）：`queuedHead`，含义：用于表示queued、head。
        QueuedMeshUpload queuedHead = uploadQueue.peek(); // meaning
        perfUploadQueueTopPriority = queuedHead == null ? Double.NaN : queuedHead.priorityKey();
        UploadValidationContext validationContext = buildUploadValidationContext(player); // meaning

        // 中文标注（局部变量）：`startedNanos`，含义：用于表示started、nanos。
        long startedNanos = System.nanoTime(); // meaning
        // 中文标注（局部变量）：`processed`，含义：用于表示processed。
        int processed = 0; // meaning
        while (processed < maxUploads) {
            if (isUploadBudgetExceeded(processed, startedNanos, uploadBudgetMs)) {
                break;
            }
            // 中文标注（局部变量）：`queuedUpload`，含义：用于表示queued、上传。
            QueuedMeshUpload queuedUpload = uploadQueue.poll(); // meaning
            if (queuedUpload == null) {
                break;
            }
            processed++;
            // 中文标注（局部变量）：`meshData`，含义：用于表示网格、数据。
            ChunkMeshData meshData = queuedUpload.meshData(); // meaning
            if (queuedUpload.worldEpoch() != worldEpoch.get()) {
                meshData.releaseBuffers(uploadBufferPool);
                continue;
            }

            inFlightVersion.remove(meshData.pos(), queuedUpload.buildKey());
            // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
            Chunk chunk = worldView.getChunk(meshData.pos()); // meaning
            if (chunk == null) {
                dropQueuedUpload(meshData, true);
                continue;
            }
            if (isQueuedUploadStale(chunk, meshData, queuedUpload, validationContext)) {
                dropQueuedUpload(meshData, false);
                continue;
            }
            try {
                recordUploadStats(uploadChunkMesh(
                    meshData,
                    queuedUpload.submittedNanos(),
                    queuedUpload.fullHeightMeshing(),
                    queuedUpload.meshConfigHash()
                ));
            } finally {
                meshData.releaseBuffers(uploadBufferPool);
            }
        }
    }

    private UploadValidationContext buildUploadValidationContext(PlayerController player) {
        double playerX = player.x(); // meaning
        double playerZ = player.z(); // meaning
        boolean fullHeightMeshing = features.fullHeightMeshing(); // meaning
        int meshConfigHash = features.meshConfigHash(); // meaning
        int currentBandKey = meshBandKey(computeMeshCaptureMinY(player.y()), computeMeshCaptureMaxY(player.y())); // meaning
        return new UploadValidationContext(playerX, playerZ, currentBandKey, fullHeightMeshing, meshConfigHash);
    }

    private static boolean isUploadBudgetExceeded(int processed, long startedNanos, double uploadBudgetMs) {
        if (processed <= 0 || uploadBudgetMs <= 0.0) {
            return false;
        }
        double elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000.0; // meaning
        return elapsedMs >= uploadBudgetMs;
    }

    private boolean isQueuedUploadStale(
        Chunk chunk,
        ChunkMeshData meshData,
        QueuedMeshUpload queuedUpload,
        UploadValidationContext validationContext
    ) {
        if (chunk.version() != meshData.version()) {
            return true;
        }
        int desiredLodLevel = resolveChunkLodLevel(chunk, validationContext.playerX(), validationContext.playerZ()); // meaning
        if (desiredLodLevel != meshData.lodLevel()) {
            return true;
        }
        if (queuedUpload.fullHeightMeshing() != validationContext.fullHeightMeshing()) {
            return true;
        }
        if (queuedUpload.meshConfigHash() != validationContext.meshConfigHash()) {
            return true;
        }
        return validationContext.currentBandKey() != meshData.bandKey();
    }

    private void dropQueuedUpload(ChunkMeshData meshData, boolean clearChunkCaches) {
        perfUploadDropped++;
        if (clearChunkCaches) {
            recentlyVisibleFrame.remove(meshData.pos());
            lodSelectionCache.remove(meshData.pos());
        }
        meshData.releaseBuffers(uploadBufferPool);
    }

    // 中文标注（方法）：`recordUploadStats`，参数：stats；用途：执行record、上传、stats相关逻辑。
    // 中文标注（参数）：`stats`，含义：用于表示stats。
    private void recordUploadStats(UploadStats stats) {
        perfUploadJobs += stats.uploadJobs();
        perfUploadBytes += stats.uploadBytes();
        perfBufferReallocs += stats.bufferReallocs();
        perfBufferOrphans += stats.bufferOrphans();
        perfBufferSubDatas += stats.bufferSubDatas();
    }

    // 中文标注（方法）：`uploadChunkMesh`，参数：meshData、submittedNanos、fullHeightMeshing、meshConfigHash；用途：执行渲染或图形资源处理：上传、区块、网格。
    private UploadStats uploadChunkMesh(
        // 中文标注（参数）：`meshData`，含义：用于表示网格、数据。
        ChunkMeshData meshData,
        // 中文标注（参数）：`submittedNanos`，含义：用于表示submitted、nanos。
        long submittedNanos,
        // 中文标注（参数）：`fullHeightMeshing`，含义：用于表示full、高度、meshing。
        boolean fullHeightMeshing,
        // 中文标注（参数）：`meshConfigHash`，含义：用于表示网格、config、hash。
        int meshConfigHash
    ) {
        // 中文标注（Lambda参数）：`unused`，含义：用于表示unused。
        // 中文标注（局部变量）：`gpuChunk`，含义：用于表示GPU、区块。
        GpuChunk gpuChunk = gpuChunks.computeIfAbsent(meshData.pos(), unused -> new GpuChunk(meshData.pos())); // meaning
        // 中文标注（局部变量）：`stats`，含义：用于表示stats。
        UploadStats stats; // meaning
        try {
            stats = gpuChunk.upload(
                meshData,
                submittedNanos,
                fullHeightMeshing,
                meshConfigHash,
                features.orphaningUpload(),
                sharedChunkBufferArena
            );
        // 中文标注（异常参数）：`uploadFailure`，含义：用于表示upload、failure。
        } catch (RuntimeException uploadFailure) {
            gpuChunk.dispose(sharedChunkBufferArena);
            throw uploadFailure;
        // 中文标注（异常参数）：`uploadFailure`，含义：用于表示upload、failure。
        } catch (Error uploadFailure) {
            gpuChunk.dispose(sharedChunkBufferArena);
            throw uploadFailure;
        }
        if (gpuChunk.lastUploadSharedArenaAllocFailure) {
            perfSharedArenaAllocFailures++;
        }
        if (gpuChunk.lastUploadSharedArenaFallback) {
            perfSharedArenaFallbackUploads++;
        }
        return stats;
    }

    // 中文标注（方法）：`pruneGpuChunks`，参数：activePositions；用途：执行prune、GPU、区块集合相关逻辑。
    // 中文标注（参数）：`activePositions`，含义：用于表示active、positions。
    private void pruneGpuChunks(Set<ChunkPos> activePositions) {
        if (gpuChunks.isEmpty()) {
            return;
        }

        scratchPruneRemovals.clear();
        // 中文标注（局部变量）：`pos`，含义：用于表示位置。
        for (ChunkPos pos : gpuChunks.keySet()) {
            if (!activePositions.contains(pos)) {
                scratchPruneRemovals.add(pos);
            }
        }

        // 中文标注（局部变量）：`pos`，含义：用于表示位置。
        for (ChunkPos pos : scratchPruneRemovals) {
            // 中文标注（局部变量）：`removed`，含义：用于表示removed。
            GpuChunk removed = gpuChunks.remove(pos); // meaning
            if (removed != null) {
                removed.dispose(sharedChunkBufferArena);
            }
            inFlightVersion.remove(pos);
            recentlyVisibleFrame.remove(pos);
            lodSelectionCache.remove(pos);
        }
        scratchPruneRemovals.clear();
    }

    // 中文标注（方法）：`renderVisibleChunks`，参数：chunksInRange、ambient；用途：执行渲染或图形资源处理：渲染、visible、区块集合。
    // 中文标注（参数）：`chunksInRange`，含义：用于表示区块集合、in、范围。
    // 中文标注（参数）：`ambient`，含义：用于表示环境光。
    private FrameStats renderVisibleChunks(List<Chunk> chunksInRange, float ambient, PlayerController player) {
        VisibleChunkRenderPass pass = beginVisibleChunkRenderPass(ambient, player); // meaning
        try {
            for (Chunk chunk : chunksInRange) {
                processVisibleChunkCandidate(pass, chunk);
            }
            flushMdiDrawBatch(pass);
        } finally {
            teardownVisibleChunkRenderPassGlState(pass);
        }

        if (pass.occlusionEnabled && !scratchOcclusionCandidates.isEmpty()) {
            pass.stateChanges += issueOcclusionQueries(scratchOcclusionCandidates);
        }

        return pass.toFrameStats();
    }

    private VisibleChunkRenderPass beginVisibleChunkRenderPass(float ambient, PlayerController player) {
        boolean occlusionEnabled = !features.disableChunkOcclusionCull()
            && features.occlusionQuery()
            && supportsOcclusionQuery
            && occlusionBoxMesh != null;
        boolean mdiEnabled = features.mdi() && supportsMdi && sharedChunkBufferArena != null; // meaning
        int occlusionResultPollBudget = occlusionEnabled ? Math.max(0, features.occlusionResultPollBudget()) : 0; // meaning
        VisibleChunkRenderPass pass = new VisibleChunkRenderPass(
            occlusionEnabled,
            mdiEnabled,
            occlusionResultPollBudget,
            player.eyeX(),
            player.eyeY(),
            player.eyeZ(),
            player.yaw(),
            player.pitch()
        );
        scratchOcclusionCandidates.clear();
        scratchMdiChunks.clear();

        GL20.glUseProgram(ambientShaderProgramId);
        GL20.glUniform1f(ambientUniformLocation, features.applyAmbientToBlocks() ? ambient : 1.0f);
        GL20.glUniform1i(materialLutUniformLocation, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, materialLutTextureId);
        pass.stateChanges += 5;

        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        return pass;
    }

    private void processVisibleChunkCandidate(VisibleChunkRenderPass pass, Chunk chunk) {
        GpuChunk gpuChunk = gpuChunks.get(chunk.pos()); // meaning
        if (gpuChunk == null || !gpuChunk.valid || gpuChunk.indexCount <= 0) {
            return;
        }
        pass.totalCandidateTriangles += gpuChunk.triangleCount;

        if (!isGpuChunkFrustumVisible(pass, gpuChunk)) {
            return;
        }
        pass.frustumCandidateTriangles += gpuChunk.triangleCount;

        pollGpuChunkOcclusionIfNeeded(pass, gpuChunk);
        if (!shouldDrawChunkAfterOcclusion(pass, gpuChunk)) {
            return;
        }

        recordVisibleChunk(pass, chunk, gpuChunk);

        if (pass.mdiEnabled && gpuChunk.usesSharedArena && sharedChunkBufferArena != null) {
            scratchMdiChunks.add(gpuChunk);
            return;
        }

        drawVisibleChunkImmediate(pass, gpuChunk);
    }

    private boolean isGpuChunkFrustumVisible(VisibleChunkRenderPass pass, GpuChunk gpuChunk) {
        if (features.disableChunkFrustumCull()) {
            return true;
        }

        // 正确性优先：使用“chunk 固定 X/Z 范围”的保守包围盒做裁剪，而不是 mesh 的精确 bounds。
        // 原因：mesh bounds 在地形稀疏/坑洞边缘时可能非常薄，若与相机约定存在细微偏差会放大误剔除。
        double cullMinX = gpuChunk.pos.x() * (double) Section.SIZE; // meaning
        double cullMinZ = gpuChunk.pos.z() * (double) Section.SIZE; // meaning
        double cullMaxX = cullMinX + Section.SIZE; // meaning
        double cullMaxZ = cullMinZ + Section.SIZE; // meaning
        double cullMinY = gpuChunk.fullHeightMeshingUploaded ? World.MIN_Y : gpuChunk.minY; // meaning
        double cullMaxY = gpuChunk.fullHeightMeshingUploaded ? (World.MAX_Y + 1.0) : gpuChunk.maxY; // meaning
        Frustum.AabbVisibilityResult visibility = frustum.classifyAabb(
            cullMinX,
            cullMinY,
            cullMinZ,
            cullMaxX,
            cullMaxY,
            cullMaxZ
        );
        if (!visibility.visible()) {
            logChunkCull(
                pass,
                gpuChunk,
                "FRUSTUM",
                cullMinX,
                cullMinY,
                cullMinZ,
                cullMaxX,
                cullMaxY,
                cullMaxZ,
                visibility.rejectPlaneIndex()
            );
        }
        return visibility.visible();
    }

    private void pollGpuChunkOcclusionIfNeeded(VisibleChunkRenderPass pass, GpuChunk gpuChunk) {
        if (!pass.occlusionEnabled) {
            return;
        }
        if (!gpuChunk.hasPendingOcclusionQuery()) {
            return;
        }
        if (pass.occlusionResultPollBudget > 0) {
            pass.occlusionResultPollBudget--;
            perfOcclusionQueryPolls++;
            if (gpuChunk.pollOcclusionQueryResult()) {
                perfOcclusionQueryReadStalls++;
            }
            return;
        }
        perfOcclusionQueryDeferredPolls++;
    }

    private boolean shouldDrawChunkAfterOcclusion(VisibleChunkRenderPass pass, GpuChunk gpuChunk) {
        if (!pass.occlusionEnabled) {
            return true;
        }
        scratchOcclusionCandidates.add(gpuChunk);
        if (gpuChunk.shouldDrawBasedOnOcclusion(frameSequence)) {
            return true;
        }
        perfOcclusionCulledChunks++;
        logChunkCull(
            pass,
            gpuChunk,
            "OCCLUSION",
            gpuChunk.minX,
            gpuChunk.minY,
            gpuChunk.minZ,
            gpuChunk.maxX,
            gpuChunk.maxY,
            gpuChunk.maxZ,
            -1
        );
        return false;
    }

    private void logChunkCull(
        VisibleChunkRenderPass pass,
        GpuChunk gpuChunk,
        String reason,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        int planeIndex
    ) {
        if (pass.cullLogsEmitted >= MAX_CULL_LOGS_PER_FRAME) {
            return;
        }
        pass.cullLogsEmitted++;
        String planeName = planeIndex >= 0 ? Frustum.planeName(planeIndex) : "N/A"; // meaning
        System.out.printf(
            "[gpu-cull] chunk=(%d,%d) reason=%s plane=%d planeName=%s cam=(%.2f,%.2f,%.2f) yaw=%.1f pitch=%.1f aabb=[(%.2f,%.2f,%.2f)->(%.2f,%.2f,%.2f)]%n",
            gpuChunk.pos.x(),
            gpuChunk.pos.z(),
            reason,
            planeIndex,
            planeName,
            pass.cameraX,
            pass.cameraY,
            pass.cameraZ,
            pass.cameraYaw,
            pass.cameraPitch,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ
        );
    }

    private void recordVisibleChunk(VisibleChunkRenderPass pass, Chunk chunk, GpuChunk gpuChunk) {
        pass.visibleChunks++;
        if (gpuChunk.lodLevelUploaded > 0) {
            pass.lodVisibleChunks++;
        }
        pass.totalTriangles += gpuChunk.triangleCount;
        recentlyVisibleFrame.put(chunk.pos(), frameSequence);
        if (gpuChunk.lastVisibleLatencyRecordedVersion != gpuChunk.versionUploaded && gpuChunk.lastMeshSubmitNanos > 0L) {
            perfVisibleLatencyNanosTotal += Math.max(0L, System.nanoTime() - gpuChunk.lastMeshSubmitNanos);
            perfVisibleLatencySamples++;
            gpuChunk.lastVisibleLatencyRecordedVersion = gpuChunk.versionUploaded;
        }
    }

    private void drawVisibleChunkImmediate(VisibleChunkRenderPass pass, GpuChunk gpuChunk) {
        if (pass.mdiEnabled) {
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
        bindArrayBuffer(pass, drawArrayBuffer);
        bindElementBuffer(pass, drawElementBuffer);

        long vertexBaseOffset = gpuChunk.usesSharedArena ? gpuChunk.sharedVertexOffsetBytes : 0L; // meaning
        long indexBaseOffset = gpuChunk.usesSharedArena ? gpuChunk.sharedIndexOffsetBytes : 0L; // meaning
        glVertexPointer(3, GL_FLOAT, VERTEX_STRIDE_BYTES, vertexBaseOffset + POSITION_OFFSET_BYTES);
        glColorPointer(4, GL_UNSIGNED_BYTE, VERTEX_STRIDE_BYTES, vertexBaseOffset + COLOR_OFFSET_BYTES);
        glDrawElements(GL_TRIANGLES, gpuChunk.indexCount, GL_UNSIGNED_INT, indexBaseOffset);
        pass.drawCalls++;
    }

    private void flushMdiDrawBatch(VisibleChunkRenderPass pass) {
        if (!pass.mdiEnabled || scratchMdiChunks.isEmpty() || sharedChunkBufferArena == null) {
            return;
        }

        int sharedVbo = sharedChunkBufferArena.vboId(); // meaning
        int sharedIbo = sharedChunkBufferArena.iboId(); // meaning
        bindArrayBuffer(pass, sharedVbo);
        bindElementBuffer(pass, sharedIbo);
        if (mdiIndirectBufferId == 0) {
            mdiIndirectBufferId = glGenBuffers();
        }
        bindIndirectBuffer(pass, mdiIndirectBufferId);

        glVertexPointer(3, GL_FLOAT, VERTEX_STRIDE_BYTES, POSITION_OFFSET_BYTES);
        glColorPointer(4, GL_UNSIGNED_BYTE, VERTEX_STRIDE_BYTES, COLOR_OFFSET_BYTES);
        uploadMdiCommandsAndDraw(scratchMdiChunks);
        perfMdiBatches++;
        perfMdiChunks += scratchMdiChunks.size();
        perfSharedArenaDrawChunks += scratchMdiChunks.size();
        pass.drawCalls++;
    }

    private void teardownVisibleChunkRenderPassGlState(VisibleChunkRenderPass pass) {
        bindArrayBuffer(pass, 0);
        bindElementBuffer(pass, 0);
        bindIndirectBuffer(pass, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);
        GL20.glUseProgram(0);
        pass.stateChanges += 2;
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
    }

    private void bindArrayBuffer(VisibleChunkRenderPass pass, int bufferId) {
        if (pass.boundArrayBuffer == bufferId) {
            return;
        }
        glBindBuffer(GL_ARRAY_BUFFER, bufferId);
        pass.boundArrayBuffer = bufferId;
        pass.stateChanges++;
    }

    private void bindElementBuffer(VisibleChunkRenderPass pass, int bufferId) {
        if (pass.boundElementBuffer == bufferId) {
            return;
        }
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferId);
        pass.boundElementBuffer = bufferId;
        pass.stateChanges++;
    }

    private void bindIndirectBuffer(VisibleChunkRenderPass pass, int bufferId) {
        if (pass.boundIndirectBuffer == bufferId) {
            return;
        }
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, bufferId);
        pass.boundIndirectBuffer = bufferId;
        pass.stateChanges++;
    }

    // 中文标注（方法）：`issueOcclusionQueries`，参数：candidates；用途：判断issue、occlusion、queries是否满足条件。
    // 中文标注（参数）：`candidates`，含义：用于表示candidates。
    private int issueOcclusionQueries(List<GpuChunk> candidates) {
        if (candidates.isEmpty() || occlusionBoxMesh == null) {
            return 0;
        }
        OcclusionQueryPass pass = beginOcclusionQueryPass(); // meaning
        try {
            // 先给当前“隐藏”的块发重采样查询，避免在 query budget 有上限时被可见块长期饿死。
            issueOcclusionQueriesForVisibilityGroup(pass, candidates, true);
            if (!pass.reachedBudget()) {
                issueOcclusionQueriesForVisibilityGroup(pass, candidates, false);
            }
        } finally {
            endOcclusionQueryPass(pass);
        }
        return pass.stateChanges;
    }

    private OcclusionQueryPass beginOcclusionQueryPass() {
        OcclusionQueryPass pass = new OcclusionQueryPass(); // meaning
        glColorMask(false, false, false, false);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        glEnableClientState(GL_VERTEX_ARRAY);
        pass.stateChanges += 4;

        glBindBuffer(GL_ARRAY_BUFFER, occlusionBoxMesh.vboId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, occlusionBoxMesh.iboId);
        glVertexPointer(3, GL_FLOAT, 3 * Float.BYTES, 0L);
        pass.stateChanges += 2;
        return pass;
    }

    private void issueOcclusionQueriesForVisibilityGroup(
        OcclusionQueryPass pass,
        List<GpuChunk> candidates,
        boolean prioritizeHidden
    ) {
        for (GpuChunk chunk : candidates) {
            if (pass.reachedBudget()) {
                return;
            }
            if (prioritizeHidden == chunk.occlusionVisible) {
                continue;
            }
            if (!chunk.shouldIssueOcclusionQuery(frameSequence)) {
                continue;
            }
            issueSingleOcclusionQuery(pass, chunk);
        }
    }

    private void issueSingleOcclusionQuery(OcclusionQueryPass pass, GpuChunk chunk) {
        int queryId = chunk.ensureOcclusionQueryId(); // meaning
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
        pass.issued++;
        perfOcclusionQueries++;
    }

    private void endOcclusionQueryPass(OcclusionQueryPass pass) {
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glDisableClientState(GL_VERTEX_ARRAY);
        if (features.disableFaceCulling()) {
            glDisable(GL_CULL_FACE);
        } else {
            glEnable(GL_CULL_FACE);
        }
        glDepthMask(true);
        glColorMask(true, true, true, true);
        pass.stateChanges += 4;
    }

    // 中文标注（方法）：`uploadMdiCommandsAndDraw`，参数：chunks；用途：执行渲染或图形资源处理：上传、mdi、commands、and、绘制。
    // 中文标注（参数）：`chunks`，含义：用于表示区块集合。
    private void uploadMdiCommandsAndDraw(List<GpuChunk> chunks) {
        MdiBatchDescriptor batch = buildMdiBatchDescriptor(chunks); // meaning
        if (batch == null) {
            return;
        }
        encodeMdiCommands(chunks, batch.commandInts());
        uploadMdiCommandBuffer(batch.commandInts(), batch.commandBytes());
        drawMdiBatch(batch.drawCount());
    }

    private MdiBatchDescriptor buildMdiBatchDescriptor(List<GpuChunk> chunks) {
        int drawCount = chunks.size(); // meaning
        if (drawCount <= 0 || mdiIndirectBufferId == 0) {
            return null;
        }
        int commandInts = drawCount * 5; // meaning
        int commandBytes = commandInts * Integer.BYTES; // meaning
        return new MdiBatchDescriptor(drawCount, commandInts, commandBytes);
    }

    private void encodeMdiCommands(List<GpuChunk> chunks, int commandInts) {
        ensureMdiCommandScratchCapacity(commandInts);

        int write = 0; // meaning
        for (GpuChunk chunk : chunks) {
            mdiCommandScratch[write++] = chunk.indexCount;
            mdiCommandScratch[write++] = 1; // instanceCount
            mdiCommandScratch[write++] = (int) (chunk.sharedIndexOffsetBytes / Integer.BYTES); // firstIndex (uint indices)
            mdiCommandScratch[write++] = (int) (chunk.sharedVertexOffsetBytes / VERTEX_STRIDE_BYTES); // baseVertex
            mdiCommandScratch[write++] = 0; // baseInstance
        }
    }

    private void uploadMdiCommandBuffer(int commandInts, int commandBytes) {
        ensureMdiUploadBufferCapacity(commandBytes);
        mdiCommandUploadBytes.clear();
        IntBuffer commandIntsBuffer = mdiCommandUploadBytes.asIntBuffer(); // meaning
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
    }

    private void drawMdiBatch(int drawCount) {
        if (supportsMdiCore43) {
            GL43.glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0L, drawCount, 0);
        } else {
            ARBMultiDrawIndirect.glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0L, drawCount, 0);
        }
    }

    // 中文标注（方法）：`ensureMdiCommandScratchCapacity`，参数：requiredInts；用途：执行ensure、mdi、command、临时工作区、capacity相关逻辑。
    // 中文标注（参数）：`requiredInts`，含义：用于表示required、ints。
    private void ensureMdiCommandScratchCapacity(int requiredInts) {
        if (mdiCommandScratch.length >= requiredInts) {
            return;
        }
        // 中文标注（局部变量）：`newCapacity`，含义：用于表示new、capacity。
        int newCapacity = Math.max(64, mdiCommandScratch.length); // meaning
        while (newCapacity < requiredInts) {
            newCapacity *= 2;
        }
        mdiCommandScratch = Arrays.copyOf(mdiCommandScratch, newCapacity);
    }

    // 中文标注（方法）：`ensureMdiUploadBufferCapacity`，参数：requiredBytes；用途：执行ensure、mdi、上传、缓冲区、capacity相关逻辑。
    // 中文标注（参数）：`requiredBytes`，含义：用于表示required、字节数据。
    private void ensureMdiUploadBufferCapacity(int requiredBytes) {
        if (mdiCommandUploadBytes != null && mdiCommandUploadBytes.capacity() >= requiredBytes) {
            return;
        }
        // 中文标注（局部变量）：`newCapacity`，含义：用于表示new、capacity。
        int newCapacity = Math.max(20 * 64, requiredBytes); // meaning
        mdiCommandUploadBytes = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder());
    }

    // 中文标注（方法）：`emitPerfLine`，参数：frameStats、chunksInRange；用途：执行emit、perf、line相关逻辑。
    // 中文标注（参数）：`frameStats`，含义：用于表示帧、stats。
    // 中文标注（参数）：`chunksInRange`，含义：用于表示区块集合、in、范围。
    private void emitPerfLine(FrameStats frameStats, int chunksInRange) {
        // 中文标注（局部变量）：`now`，含义：用于表示now。
        long now = System.nanoTime(); // meaning
        perfFrames++;
        // 中文标注（局部变量）：`elapsed`，含义：用于表示已耗时。
        long elapsed = now - perfWindowStartNanos; // meaning
        if (elapsed < 1_000_000_000L) {
            return;
        }

        // 中文标注（局部变量）：`fps`，含义：用于表示fps。
        double fps = (perfFrames * 1_000_000_000.0) / elapsed; // meaning
        // 中文标注（局部变量）：`percentiles`，含义：用于表示percentiles。
        FrameTimePercentiles percentiles = frameTimePercentiles(); // meaning
        // 中文标注（局部变量）：`avgLatencyMs`，含义：用于表示平均、latency、ms。
        double avgLatencyMs = perfVisibleLatencySamples == 0
            ? 0.0
            : (perfVisibleLatencyNanosTotal / (double) perfVisibleLatencySamples) / 1_000_000.0;
        latestTitleStats = String.format(
            "p95 %.1fms p99 %.1fms fh %d mch %d yr %d..%d bk %d uq %d ub %d mb %d lod %d oq %d oc %d qp %d/%d qs %d mdi %d/%d fb %d",
            percentiles.p95Ms(),
            percentiles.p99Ms(),
            features.fullHeightMeshing() ? 1 : 0,
            latestMeshConfigHash,
            latestMeshCaptureMinY,
            latestMeshCaptureMaxY,
            latestMeshCaptureBandKey,
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
        // 启动时打印 GPU 控制面的“单一真相”，包含 key 名和值来源，方便验收与二分定位。
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

    // 中文标注（方法）：`latestTitleStats`，参数：无；用途：执行latest、title、stats相关逻辑。
    public String latestTitleStats() {
        return latestTitleStats;
    }

    // 中文标注（方法）：`lastMeshingSubmitNanos`，参数：无；用途：执行last、meshing、submit、nanos相关逻辑。
    public long lastMeshingSubmitNanos() {
        return lastMeshingSubmitNanos;
    }

    // 中文标注（方法）：`lastUploadQueueDrainNanos`，参数：无；用途：执行last、上传、队列、drain、nanos相关逻辑。
    public long lastUploadQueueDrainNanos() {
        return lastUploadQueueDrainNanos;
    }

    // 中文标注（方法）：`lastDrawLoopNanos`，参数：无；用途：执行last、绘制、loop、nanos相关逻辑。
    public long lastDrawLoopNanos() {
        return lastDrawLoopNanos;
    }

    // 中文标注（方法）：`updateAdaptiveBudgets`，参数：renderCpuNanos；用途：更新更新、adaptive、budgets相关状态。
    // 中文标注（参数）：`renderCpuNanos`，含义：用于表示渲染、CPU、nanos。
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

    // 中文标注（方法）：`updateAdaptiveUploadBudget`，参数：renderCpuNanos；用途：更新更新、adaptive、上传、budget相关状态。
    // 中文标注（参数）：`renderCpuNanos`，含义：用于表示渲染、CPU、nanos。
    private void updateAdaptiveUploadBudget(long renderCpuNanos) {
        updateRenderCpuEma(renderCpuNanos);

        // 中文标注（局部变量）：`queueSize`，含义：用于表示队列、大小。
        int queueSize = uploadQueue.size(); // meaning
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

    // 中文标注（方法）：`updateRenderCpuEma`，参数：renderCpuNanos；用途：更新更新、渲染、CPU、ema相关状态。
    // 中文标注（参数）：`renderCpuNanos`，含义：用于表示渲染、CPU、nanos。
    private void updateRenderCpuEma(long renderCpuNanos) {
        // 中文标注（局部变量）：`renderCpuMs`，含义：用于表示渲染、CPU、ms。
        double renderCpuMs = renderCpuNanos / 1_000_000.0; // meaning
        if (renderCpuMsEma < 0.0) {
            renderCpuMsEma = renderCpuMs;
        } else {
            renderCpuMsEma = (renderCpuMsEma * (1.0 - FRAME_TIME_EMA_ALPHA)) + (renderCpuMs * FRAME_TIME_EMA_ALPHA);
        }
    }

    // 中文标注（方法）：`updateAdaptiveMeshSubmitBudget`，参数：无；用途：更新更新、adaptive、网格、submit、budget相关状态。
    private void updateAdaptiveMeshSubmitBudget() {
        // 中文标注（局部变量）：`queueSize`，含义：用于表示队列、大小。
        int queueSize = uploadQueue.size(); // meaning
        // 中文标注（局部变量）：`inFlight`，含义：用于表示in、flight。
        int inFlight = meshingJobsInFlight.get(); // meaning
        // 中文标注（局部变量）：`healthyInFlightCap`，含义：用于表示healthy、in、flight、cap。
        int healthyInFlightCap = Math.max(2, meshWorkerCount * 2); // meaning

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

    // 中文标注（方法）：`peekMeshingTask`，参数：无；用途：执行peek、meshing、task相关逻辑。
    private PrioritizedMeshTask peekMeshingTask() {
        // 中文标注（局部变量）：`head`，含义：用于表示head。
        Runnable head = meshPool.getQueue().peek(); // meaning
        // 中文标注（局部变量）：`prioritizedMeshTask`，含义：用于表示prioritized、网格、task。
        if (head instanceof PrioritizedMeshTask prioritizedMeshTask) {
            return prioritizedMeshTask;
        }
        return null;
    }

    // 中文标注（方法）：`recordFrameTime`，参数：renderCpuNanos；用途：执行record、帧、时间相关逻辑。
    // 中文标注（参数）：`renderCpuNanos`，含义：用于表示渲染、CPU、nanos。
    private void recordFrameTime(long renderCpuNanos) {
        // 中文标注（局部变量）：`frameMs`，含义：用于表示帧、ms。
        double frameMs = renderCpuNanos / 1_000_000.0; // meaning
        frameTimeWindowMs[frameTimeWindowIndex] = frameMs;
        frameTimeWindowIndex = (frameTimeWindowIndex + 1) % frameTimeWindowMs.length;
        if (frameTimeWindowCount < frameTimeWindowMs.length) {
            frameTimeWindowCount++;
        }
    }

    // 中文标注（方法）：`frameTimePercentiles`，参数：无；用途：执行帧、时间、percentiles相关逻辑。
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

    // 中文标注（方法）：`percentile`，参数：sortedValues、count、quantile；用途：执行percentile相关逻辑。
    // 中文标注（参数）：`sortedValues`，含义：用于表示sorted、values。
    // 中文标注（参数）：`count`，含义：用于表示数量。
    // 中文标注（参数）：`quantile`，含义：用于表示quantile。
    private static double percentile(double[] sortedValues, int count, double quantile) {
        if (count <= 0) {
            return 0.0;
        }
        // 中文标注（局部变量）：`index`，含义：用于表示索引。
        int index = (int) Math.ceil((count - 1) * quantile); // meaning
        index = Math.max(0, Math.min(count - 1, index));
        return sortedValues[index];
    }

    // 中文标注（方法）：`chunkPriorityKey`，参数：chunk、playerX、playerZ、lookX、lookZ；用途：执行区块、priority、键相关逻辑。
    // 中文标注（参数）：`chunk`，含义：用于表示区块。
    // 中文标注（参数）：`playerX`，含义：用于表示玩家、X坐标。
    // 中文标注（参数）：`playerZ`，含义：用于表示玩家、Z坐标。
    // 中文标注（参数）：`lookX`，含义：用于表示look、X坐标。
    // 中文标注（参数）：`lookZ`，含义：用于表示look、Z坐标。
    private double chunkPriorityKey(Chunk chunk, double playerX, double playerZ, double lookX, double lookZ) {
        // 中文标注（局部变量）：`distanceSq`，含义：用于表示distance、sq。
        double distanceSq = chunkDistanceSq(chunk, playerX, playerZ); // meaning
        // 中文标注（局部变量）：`priority`，含义：用于表示priority。
        double priority = distanceSq; // meaning
        if (features.priorityForwardBias()) {
            // 中文标注（局部变量）：`centerX`，含义：用于表示center、X坐标。
            double centerX = chunk.pos().x() * Section.SIZE + (Section.SIZE * 0.5); // meaning
            // 中文标注（局部变量）：`centerZ`，含义：用于表示center、Z坐标。
            double centerZ = chunk.pos().z() * Section.SIZE + (Section.SIZE * 0.5); // meaning
            // 中文标注（局部变量）：`dx`，含义：用于表示dx。
            double dx = centerX - playerX; // meaning
            // 中文标注（局部变量）：`dz`，含义：用于表示dz。
            double dz = centerZ - playerZ; // meaning
            // 中文标注（局部变量）：`len`，含义：用于表示长度。
            double len = Math.sqrt(dx * dx + dz * dz); // meaning
            if (len > 1.0e-6) {
                // 中文标注（局部变量）：`forwardDot`，含义：用于表示forward、dot。
                double forwardDot = ((dx / len) * lookX) + ((dz / len) * lookZ); // meaning
                if (forwardDot > 0.0) {
                    priority -= forwardDot * PRIORITY_FORWARD_BIAS_WEIGHT;
                }
            }
        }
        if (features.priorityRecentlyVisibleBias()) {
            // 中文标注（局部变量）：`lastVisibleFrame`，含义：用于表示last、visible、帧。
            Long lastVisibleFrame = recentlyVisibleFrame.get(chunk.pos()); // meaning
            if (lastVisibleFrame != null && frameSequence - lastVisibleFrame <= RECENTLY_VISIBLE_BIAS_FRAMES) {
                priority -= PRIORITY_RECENT_VISIBLE_BIAS;
            }
        }
        return priority;
    }

    // 中文标注（方法）：`initializeCapabilitiesIfNeeded`，参数：无；用途：执行initialize、capabilities、if、needed相关逻辑。
    private void initializeCapabilitiesIfNeeded() {
        if (glCapabilitiesLogged) {
            return;
        }
        // 中文标注（局部变量）：`caps`，含义：用于表示caps。
        GLCapabilities caps = GL.getCapabilities(); // meaning
        supportsMdiCore43 = caps.OpenGL43;
        supportsMdiArb = caps.GL_ARB_multi_draw_indirect;
        supportsMdi = supportsMdiCore43 || supportsMdiArb;
        supportsOcclusionQuery = caps.OpenGL15 || caps.GL_ARB_occlusion_query;
        supportsPersistentMapping = caps.OpenGL44 || caps.GL_ARB_buffer_storage;

        // 中文标注（局部变量）：`mdiEnabled`，含义：用于表示mdi、enabled。
        boolean mdiEnabled = features.mdi() && supportsMdi && features.sharedChunkArena(); // meaning
        // 中文标注（局部变量）：`capMinY`，含义：用于表示cap、最小、Y坐标。
        int capMinY = latestMeshCaptureMinY; // meaning
        // 中文标注（局部变量）：`capMaxY`，含义：用于表示cap、最大、Y坐标。
        int capMaxY = latestMeshCaptureMaxY; // meaning
        // 中文标注（局部变量）：`capBandKey`，含义：用于表示cap、带、键。
        int capBandKey = latestMeshCaptureBandKey; // meaning
        System.out.printf(
            "[gpu-cap] farPlane=%.1f yr=%d..%d bandKey=%d meshBuildKeySchema=v+lod+band+fh+mch meshConfigHash=%d fullHeightMeshing=%s(fullHeightKey=%s,value=%s) disableChunkFrustumCull=%s(disableCullKey=%s,value=%s) mdiSupported=%s mdiCore43=%s mdiArb=%s mdiEnabled=%s occlusionSupported=%s occlusionEnabled=%s(occlusionKey=%s,value=%s) occlusionPollBudget=%d persistentMappingSupported=%s persistentMappingEnabled=%s priorityBiasEnabled=%s adaptiveUploadBudget=%s adaptiveMeshSubmitBudget=%s orphaningUpload=%s lodEnabled=%s lodStartChunks=%d lodHysteresis=%d sharedChunkArena=%s sharedArenaVertexMB=%d sharedArenaIndexMB=%d%n",
            FAR_PLANE,
            capMinY,
            capMaxY,
            capBandKey,
            features.meshConfigHash(),
            features.fullHeightMeshing(),
            features.fullHeightMeshingSourceKey(),
            features.fullHeightMeshingSourceValue(),
            features.disableChunkFrustumCull(),
            features.disableChunkFrustumCullSourceKey(),
            features.disableChunkFrustumCullSourceValue(),
            supportsMdi,
            supportsMdiCore43,
            supportsMdiArb,
            mdiEnabled,
            supportsOcclusionQuery,
            features.occlusionQuery() && supportsOcclusionQuery,
            features.occlusionQuerySourceKey(),
            features.occlusionQuerySourceValue(),
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
        emitCapabilitySupportWarnings();
        CapabilityInitResources capabilityResources = createCapabilityInitResources(); // meaning
        occlusionBoxMesh = capabilityResources.occlusionBoxMesh();
        sharedChunkBufferArena = capabilityResources.sharedChunkBufferArena();
        glCapabilitiesLogged = true;
    }

    private void emitCapabilitySupportWarnings() {
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
    }

    private CapabilityInitResources createCapabilityInitResources() {
        OcclusionBoxMesh createdOcclusionBoxMesh = null; // meaning
        SharedChunkBufferArena createdSharedChunkBufferArena = null; // meaning
        try {
            if (features.occlusionQuery() && supportsOcclusionQuery) {
                createdOcclusionBoxMesh = new OcclusionBoxMesh();
            }
            if (features.sharedChunkArena()) {
                createdSharedChunkBufferArena = new SharedChunkBufferArena(
                    mebibytesToBytesSaturated(features.sharedChunkArenaVertexMb()),
                    mebibytesToBytesSaturated(features.sharedChunkArenaIndexMb())
                );
            }
            return new CapabilityInitResources(createdOcclusionBoxMesh, createdSharedChunkBufferArena);
        // 中文标注（异常参数）：`failure`，含义：用于表示初始化、failure。
        } catch (RuntimeException failure) {
            disposeCapabilityInitResources(createdOcclusionBoxMesh, createdSharedChunkBufferArena);
            throw failure;
        // 中文标注（异常参数）：`failure`，含义：用于表示初始化、failure。
        } catch (Error failure) {
            disposeCapabilityInitResources(createdOcclusionBoxMesh, createdSharedChunkBufferArena);
            throw failure;
        }
    }

    private static void disposeCapabilityInitResources(
        OcclusionBoxMesh createdOcclusionBoxMesh,
        SharedChunkBufferArena createdSharedChunkBufferArena
    ) {
        if (createdSharedChunkBufferArena != null) {
            createdSharedChunkBufferArena.dispose();
        }
        if (createdOcclusionBoxMesh != null) {
            createdOcclusionBoxMesh.dispose();
        }
    }

    // 中文标注（方法）：`ensureAmbientShaderProgram`，参数：无；用途：执行ensure、环境光、着色器、program相关逻辑。
    private void ensureAmbientShaderProgram() {
        if (ambientShaderProgramId != 0) {
            return;
        }

        // 中文标注（局部变量）：`vertexShader`，含义：用于表示顶点、着色器。
        int vertexShader = 0; // meaning
        // 中文标注（局部变量）：`fragmentShader`，含义：用于表示fragment、着色器。
        int fragmentShader = 0; // meaning
        // 中文标注（局部变量）：`program`，含义：用于表示program。
        int program = 0; // meaning
        try {
            vertexShader = compileShader(GL20.GL_VERTEX_SHADER, AMBIENT_VERTEX_SHADER_SOURCE, "vertex");
            fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, AMBIENT_FRAGMENT_SHADER_SOURCE, "fragment");

            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glLinkProgram(program);

            // 中文标注（局部变量）：`linkStatus`，含义：用于表示link、status。
            int linkStatus = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS); // meaning
            // 中文标注（局部变量）：`programLog`，含义：用于表示program、log。
            String programLog = GL20.glGetProgramInfoLog(program); // meaning
            if (linkStatus == 0) {
                if (programLog != null && !programLog.isBlank()) {
                    System.err.println("[gpu-shader] ambient program link failed:\n" + programLog);
                }
                throw new IllegalStateException("Failed to link ambient shader program");
            }
            if (programLog != null && !programLog.isBlank()) {
                System.out.println("[gpu-shader] ambient program link log:\n" + programLog);
            }

            // 中文标注（局部变量）：`uniformLoc`，含义：用于表示uniform、loc。
            int uniformLoc = GL20.glGetUniformLocation(program, "uAmbient"); // meaning
            if (uniformLoc < 0) {
                throw new IllegalStateException("Ambient shader missing uniform uAmbient");
            }
            int materialLutLoc = GL20.glGetUniformLocation(program, "uMatLut"); // meaning
            if (materialLutLoc < 0) {
                throw new IllegalStateException("Ambient shader missing uniform uMatLut");
            }

            ambientShaderProgramId = program;
            ambientUniformLocation = uniformLoc;
            materialLutUniformLocation = materialLutLoc;
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

    // 中文标注（方法）：`compileShader`，参数：shaderType、source、label；用途：执行compile、着色器相关逻辑。
    // 中文标注（参数）：`shaderType`，含义：用于表示着色器、类型。
    // 中文标注（参数）：`source`，含义：用于表示source。
    // 中文标注（参数）：`label`，含义：用于表示label。
    private static int compileShader(int shaderType, String source, String label) {
        // 中文标注（局部变量）：`shaderId`，含义：用于表示着色器、标识。
        int shaderId = GL20.glCreateShader(shaderType); // meaning
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);
        // 中文标注（局部变量）：`compileStatus`，含义：用于表示compile、status。
        int compileStatus = GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS); // meaning
        // 中文标注（局部变量）：`shaderLog`，含义：用于表示着色器、log。
        String shaderLog = GL20.glGetShaderInfoLog(shaderId); // meaning
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

    // 中文标注（方法）：`configureProjection`，参数：width、height；用途：执行configure、projection相关逻辑。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    private static void configureProjection(int width, int height) {
        // 中文标注（局部变量）：`aspect`，含义：用于表示aspect。
        double aspect = (double) width / (double) height; // meaning
        // 中文标注（局部变量）：`top`，含义：用于表示顶面。
        double top = NEAR_PLANE * Math.tan(Math.toRadians(VERTICAL_FOV_DEGREES * 0.5)); // meaning
        // 中文标注（局部变量）：`bottom`，含义：用于表示底面。
        double bottom = -top; // meaning
        // 中文标注（局部变量）：`right`，含义：用于表示right。
        double right = top * aspect; // meaning
        // 中文标注（局部变量）：`left`，含义：用于表示left。
        double left = -right; // meaning

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glFrustum(left, right, bottom, top, NEAR_PLANE, FAR_PLANE);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    // 中文标注（方法）：`configureCamera`，参数：player；用途：执行configure、相机相关逻辑。
    // 中文标注（参数）：`player`，含义：用于表示玩家。
    private static void configureCamera(PlayerController player) {
        glScaled(1.0, 1.0, -1.0);
        glRotatef(-player.pitch(), 1.0f, 0.0f, 0.0f);
        glRotatef(-player.yaw(), 0.0f, 1.0f, 0.0f);
        glTranslated(-player.eyeX(), -player.eyeY(), -player.eyeZ());
    }

    // 中文标注（方法）：`chunkDistanceSq`，参数：chunk、playerX、playerZ；用途：执行区块、distance、sq相关逻辑。
    // 中文标注（参数）：`chunk`，含义：用于表示区块。
    // 中文标注（参数）：`playerX`，含义：用于表示玩家、X坐标。
    // 中文标注（参数）：`playerZ`，含义：用于表示玩家、Z坐标。
    private static double chunkDistanceSq(Chunk chunk, double playerX, double playerZ) {
        // 中文标注（局部变量）：`centerX`，含义：用于表示center、X坐标。
        double centerX = chunk.pos().x() * Section.SIZE + (Section.SIZE * 0.5); // meaning
        // 中文标注（局部变量）：`centerZ`，含义：用于表示center、Z坐标。
        double centerZ = chunk.pos().z() * Section.SIZE + (Section.SIZE * 0.5); // meaning
        // 中文标注（局部变量）：`dx`，含义：用于表示dx。
        double dx = centerX - playerX; // meaning
        // 中文标注（局部变量）：`dz`，含义：用于表示dz。
        double dz = centerZ - playerZ; // meaning
        return dx * dx + dz * dz;
    }

    // 中文标注（方法）：`resolveChunkLodLevel`，参数：chunk、playerX、playerZ；用途：执行resolve、区块、细节层级、级别相关逻辑。
    // 中文标注（参数）：`chunk`，含义：用于表示区块。
    // 中文标注（参数）：`playerX`，含义：用于表示玩家、X坐标。
    // 中文标注（参数）：`playerZ`，含义：用于表示玩家、Z坐标。
    private int resolveChunkLodLevel(Chunk chunk, double playerX, double playerZ) {
        if (!features.lod()) {
            return 0;
        }
        // 中文标注（局部变量）：`centerX`，含义：用于表示center、X坐标。
        double centerX = chunk.pos().x() * Section.SIZE + (Section.SIZE * 0.5); // meaning
        // 中文标注（局部变量）：`centerZ`，含义：用于表示center、Z坐标。
        double centerZ = chunk.pos().z() * Section.SIZE + (Section.SIZE * 0.5); // meaning
        // 中文标注（局部变量）：`dxChunks`，含义：用于表示dx、区块集合。
        int dxChunks = (int) Math.floor(Math.abs(centerX - playerX) / Section.SIZE); // meaning
        // 中文标注（局部变量）：`dzChunks`，含义：用于表示dz、区块集合。
        int dzChunks = (int) Math.floor(Math.abs(centerZ - playerZ) / Section.SIZE); // meaning
        // 中文标注（局部变量）：`chebyshev`，含义：用于表示chebyshev。
        int chebyshev = Math.max(dxChunks, dzChunks); // meaning
        // 中文标注（局部变量）：`threshold`，含义：用于表示threshold。
        int threshold = Math.max(1, features.lodStartChunkDistance()); // meaning
        // 中文标注（局部变量）：`hysteresis`，含义：用于表示hysteresis。
        int hysteresis = Math.max(0, features.lodHysteresisChunks()); // meaning
        // 中文标注（局部变量）：`previous`，含义：用于表示previous。
        Integer previous = lodSelectionCache.get(chunk.pos()); // meaning
        // 中文标注（局部变量）：`resolved`，含义：用于表示resolved。
        int resolved; // meaning
        if (previous != null && previous > 0) {
            // 中文标注（局部变量）：`exitThreshold`，含义：用于表示exit、threshold。
            int exitThreshold = Math.max(0, threshold - hysteresis); // meaning
            resolved = chebyshev > exitThreshold ? 1 : 0;
        } else {
            // 中文标注（局部变量）：`enterThreshold`，含义：用于表示enter、threshold。
            int enterThreshold = threshold + hysteresis; // meaning
            resolved = chebyshev >= enterThreshold ? 1 : 0;
        }
        lodSelectionCache.put(chunk.pos(), resolved);
        return resolved;
    }

    // buildKey 必须覆盖所有影响几何内容的参数；否则会错误复用旧 mesh，导致“看起来像没改到点上”。
    // 中文标注（方法）：`meshBuildKey`，参数：version、lodLevel、bandKey、fullHeightMeshing、meshConfigHash；用途：执行网格、构建、键相关逻辑。
    // 中文标注（参数）：`version`，含义：用于表示版本。
    // 中文标注（参数）：`lodLevel`，含义：用于表示细节层级、级别。
    // 中文标注（参数）：`bandKey`，含义：用于表示带、键。
    // 中文标注（参数）：`fullHeightMeshing`，含义：用于表示full、高度、meshing。
    // 中文标注（参数）：`meshConfigHash`，含义：用于表示网格、config、hash。
    private static long meshBuildKey(long version, int lodLevel, int bandKey, boolean fullHeightMeshing, int meshConfigHash) {
        // 中文标注（局部变量）：`key`，含义：用于表示键。
        long key = version; // meaning
        key = key * 31L + lodLevel;
        key = key * 1315423911L + (bandKey & 0xFFFF_FFFFL);
        key = key * 31L + (fullHeightMeshing ? 1L : 0L);
        key = key * 1315423911L + (meshConfigHash & 0xFFFF_FFFFL);
        return key;
    }

    // 中文标注（方法）：`computeMeshCaptureMinY`，参数：playerY；用途：执行compute、网格、capture、最小、Y坐标相关逻辑。
    // 中文标注（参数）：`playerY`，含义：用于表示玩家、Y坐标。
    private int computeMeshCaptureMinY(double playerY) {
        if (features.fullHeightMeshing()) {
            return World.MIN_Y;
        }
        // 中文标注（局部变量）：`playerBlockY`，含义：用于表示玩家、方块、Y坐标。
        int playerBlockY = (int) Math.floor(playerY); // meaning
        // 中文标注（局部变量）：`margin`，含义：用于表示margin。
        int margin = 32; // meaning
        // 中文标注（局部变量）：`below`，含义：用于表示below。
        int below = (int) Math.ceil(FAR_PLANE) + margin; // meaning
        return Math.max(World.MIN_Y, playerBlockY - below);
    }

    // 中文标注（方法）：`computeMeshCaptureMaxY`，参数：playerY；用途：执行compute、网格、capture、最大、Y坐标相关逻辑。
    // 中文标注（参数）：`playerY`，含义：用于表示玩家、Y坐标。
    private int computeMeshCaptureMaxY(double playerY) {
        if (features.fullHeightMeshing()) {
            return World.MAX_Y;
        }
        // 中文标注（局部变量）：`playerBlockY`，含义：用于表示玩家、方块、Y坐标。
        int playerBlockY = (int) Math.floor(playerY); // meaning
        // 中文标注（局部变量）：`margin`，含义：用于表示margin。
        int margin = 32; // meaning
        // 中文标注（局部变量）：`above`，含义：用于表示above。
        int above = (int) Math.ceil(FAR_PLANE) + margin; // meaning
        return Math.min(World.MAX_Y, playerBlockY + above);
    }

    // 中文标注（方法）：`meshBandKey`，参数：minY、maxY；用途：执行网格、带、键相关逻辑。
    // 中文标注（参数）：`minY`，含义：用于表示最小、Y坐标。
    // 中文标注（参数）：`maxY`，含义：用于表示最大、Y坐标。
    private static int meshBandKey(int minY, int maxY) {
        // 当前世界高度跨度 < 4096，使用 12bit+12bit 打包，避免 hash 碰撞导致错误复用 mesh。
        // 若未来高度跨度扩大，再退回普通 hash。
        // 中文标注（局部变量）：`worldSpan`，含义：用于表示世界、span。
        int worldSpan = World.MAX_Y - World.MIN_Y + 1; // meaning
        if (worldSpan <= 4096) {
            // 中文标注（局部变量）：`minOffset`，含义：用于表示最小、偏移。
            int minOffset = minY - World.MIN_Y; // meaning
            // 中文标注（局部变量）：`maxOffset`，含义：用于表示最大、偏移。
            int maxOffset = maxY - World.MIN_Y; // meaning
            if (minOffset >= 0 && minOffset < 4096 && maxOffset >= 0 && maxOffset < 4096) {
                return (minOffset << 12) | maxOffset;
            }
        }
        return ((minY * 31) + 17) * 31 + maxY;
    }

    // 中文标注（方法）：`mebibytesToBytesSaturated`，参数：mebibytes；用途：安全转换配置容量，避免 int 溢出。
    // 中文标注（参数）：`mebibytes`，含义：用于表示mb 数值。
    private static int mebibytesToBytesSaturated(int mebibytes) {
        long bytes = Math.max(1L, (long) mebibytes) * 1024L * 1024L; // meaning
        return (int) Math.min(Integer.MAX_VALUE, bytes);
    }

    // 中文标注（记录类）：`ChunkFrameSet`，职责：封装区块、帧、集合相关逻辑。
    // 中文标注（字段）：`chunks`，含义：用于表示区块集合。
    // 中文标注（字段）：`positions`，含义：用于表示positions。
    private record ChunkFrameSet(List<Chunk> chunks, Set<ChunkPos> positions) {
    }

    // 中文标注（记录类）：`FrameStats`，职责：封装帧、stats相关逻辑。
    // 中文标注（字段）：`drawCalls`，含义：用于表示绘制、calls。
    // 中文标注（字段）：`visibleChunks`，含义：用于表示visible、区块集合。
    // 中文标注（字段）：`lodVisibleChunks`，含义：用于表示细节层级、visible、区块集合。
    // 中文标注（字段）：`totalTriangles`，含义：用于表示total、triangles。
    // 中文标注（字段）：`stateChanges`，含义：用于表示状态、changes。
    private record FrameStats(
        int drawCalls,
        int visibleChunks,
        int lodVisibleChunks,
        int totalTriangles,
        int stateChanges,
        int totalCandidateTriangles,
        int frustumCandidateTriangles
    ) {
    }

    private record UploadValidationContext(
        double playerX,
        double playerZ,
        int currentBandKey,
        boolean fullHeightMeshing,
        int meshConfigHash
    ) {
    }

    private record CapabilityInitResources(
        OcclusionBoxMesh occlusionBoxMesh,
        SharedChunkBufferArena sharedChunkBufferArena
    ) {
    }

    private static final class VisibleChunkRenderPass {
        private int drawCalls; // meaning
        private int visibleChunks; // meaning
        private int lodVisibleChunks; // meaning
        private int totalTriangles; // meaning
        private int totalCandidateTriangles; // meaning
        private int frustumCandidateTriangles; // meaning
        private int stateChanges = 4; // meaning
        private int boundArrayBuffer; // meaning
        private int boundElementBuffer; // meaning
        private int boundIndirectBuffer; // meaning
        private int occlusionResultPollBudget; // meaning
        private int cullLogsEmitted; // meaning
        private final boolean occlusionEnabled; // meaning
        private final boolean mdiEnabled; // meaning
        private final double cameraX; // meaning
        private final double cameraY; // meaning
        private final double cameraZ; // meaning
        private final float cameraYaw; // meaning
        private final float cameraPitch; // meaning

        private VisibleChunkRenderPass(
            boolean occlusionEnabled,
            boolean mdiEnabled,
            int occlusionResultPollBudget,
            double cameraX,
            double cameraY,
            double cameraZ,
            float cameraYaw,
            float cameraPitch
        ) {
            this.occlusionEnabled = occlusionEnabled;
            this.mdiEnabled = mdiEnabled;
            this.occlusionResultPollBudget = occlusionResultPollBudget;
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
            this.cameraYaw = cameraYaw;
            this.cameraPitch = cameraPitch;
        }

        private FrameStats toFrameStats() {
            return new FrameStats(
                drawCalls,
                visibleChunks,
                lodVisibleChunks,
                totalTriangles,
                stateChanges,
                totalCandidateTriangles,
                frustumCandidateTriangles
            );
        }
    }

    private static final class OcclusionQueryPass {
        private int stateChanges; // meaning
        private int issued; // meaning

        private boolean reachedBudget() {
            return issued >= OCCLUSION_MAX_QUERIES_PER_FRAME;
        }
    }

    private record MdiBatchDescriptor(int drawCount, int commandInts, int commandBytes) {
    }

    // 中文标注（记录类）：`QueuedMeshUpload`，职责：封装queued、网格、上传相关逻辑。
    private record QueuedMeshUpload(
        // 中文标注（字段）：`meshData`，含义：用于表示网格、数据。
        ChunkMeshData meshData,
        // 中文标注（字段）：`priorityKey`，含义：用于表示priority、键。
        double priorityKey,
        // 中文标注（字段）：`sequence`，含义：用于表示sequence。
        long sequence,
        // 中文标注（字段）：`submittedNanos`，含义：用于表示submitted、nanos。
        long submittedNanos,
        // 中文标注（字段）：`buildKey`，含义：用于表示构建、键。
        long buildKey,
        // 中文标注（字段）：`fullHeightMeshing`，含义：用于表示full、高度、meshing。
        boolean fullHeightMeshing,
        // 中文标注（字段）：`meshConfigHash`，含义：用于表示网格、config、hash。
        int meshConfigHash,
        // 中文标注（字段）：`worldEpoch`，含义：用于表示世界纪元，用于丢弃世界切换前的旧上传结果。
        int worldEpoch
    ) {
    }

    // 中文标注（记录类）：`UploadStats`，职责：封装上传、stats相关逻辑。
    // 中文标注（字段）：`uploadJobs`，含义：用于表示上传、jobs。
    // 中文标注（字段）：`uploadBytes`，含义：用于表示上传、字节数据。
    // 中文标注（字段）：`bufferReallocs`，含义：用于表示缓冲区、reallocs。
    // 中文标注（字段）：`bufferOrphans`，含义：用于表示缓冲区、orphans。
    // 中文标注（字段）：`bufferSubDatas`，含义：用于表示缓冲区、sub、datas。
    private record UploadStats(int uploadJobs, long uploadBytes, int bufferReallocs, int bufferOrphans, int bufferSubDatas) {
    }

    // 中文标注（记录类）：`FrameTimePercentiles`，职责：封装帧、时间、percentiles相关逻辑。
    // 中文标注（字段）：`p50Ms`，含义：用于表示p、50、ms。
    // 中文标注（字段）：`p95Ms`，含义：用于表示p、95、ms。
    // 中文标注（字段）：`p99Ms`，含义：用于表示p、99、ms。
    private record FrameTimePercentiles(double p50Ms, double p95Ms, double p99Ms) {
    }

    // 中文标注（记录类）：`GpuConfig`，职责：封装GPU、config相关逻辑。
    private record GpuConfig(
        // 中文标注（字段）：`priorityForwardBias`，含义：用于表示priority、forward、bias。
        boolean priorityForwardBias,
        // 中文标注（字段）：`priorityRecentlyVisibleBias`，含义：用于表示priority、recently、visible、bias。
        boolean priorityRecentlyVisibleBias,
        // 中文标注（字段）：`adaptiveUploadBudget`，含义：用于表示adaptive、上传、budget。
        boolean adaptiveUploadBudget,
        // 中文标注（字段）：`adaptiveMeshSubmitBudget`，含义：用于表示adaptive、网格、submit、budget。
        boolean adaptiveMeshSubmitBudget,
        // 中文标注（字段）：`orphaningUpload`，含义：用于表示orphaning、上传。
        boolean orphaningUpload,
        // 中文标注（字段）：`mdi`，含义：用于表示mdi。
        boolean mdi,
        // 中文标注（字段）：`occlusionQuery`，含义：用于表示occlusion、query。
        boolean occlusionQuery,
        // 中文标注（字段）：`occlusionResultPollBudget`，含义：用于表示occlusion、结果、poll、budget。
        int occlusionResultPollBudget,
        // 中文标注（字段）：`persistentMapping`，含义：用于表示persistent、mapping。
        boolean persistentMapping,
        // 中文标注（字段）：`uploadTimeBudgetMs`，含义：用于表示上传、时间、budget、ms。
        double uploadTimeBudgetMs,
        // 中文标注（字段）：`fullHeightMeshing`，含义：用于表示full、高度、meshing。
        boolean fullHeightMeshing,
        // 中文标注（字段）：`disableChunkFrustumCull`，含义：用于表示disable、区块、视锥体、cull。
        boolean disableChunkFrustumCull,
        // 中文标注（字段）：`disableChunkOcclusionCull`，含义：用于表示disable、区块、遮挡、cull。
        boolean disableChunkOcclusionCull,
        // 中文标注（字段）：`disableFaceCulling`，含义：用于表示disable、face、culling。
        boolean disableFaceCulling,
        // 中文标注（字段）：`hud`，含义：用于表示hud。
        boolean hud,
        // 中文标注（字段）：`applyAmbientToBlocks`，含义：用于表示apply、ambient、to、blocks。
        boolean applyAmbientToBlocks,
        // 中文标注（字段）：`lod`，含义：用于表示细节层级。
        boolean lod,
        // 中文标注（字段）：`lodStartChunkDistance`，含义：用于表示细节层级、开始、区块、distance。
        int lodStartChunkDistance,
        // 中文标注（字段）：`lodHysteresisChunks`，含义：用于表示细节层级、hysteresis、区块集合。
        int lodHysteresisChunks,
        // 中文标注（字段）：`sharedChunkArena`，含义：用于表示shared、区块、arena。
        boolean sharedChunkArena,
        // 中文标注（字段）：`sharedChunkArenaVertexMb`，含义：用于表示shared、区块、arena、顶点、mb。
        int sharedChunkArenaVertexMb,
        // 中文标注（字段）：`sharedChunkArenaIndexMb`，含义：用于表示shared、区块、arena、索引、mb。
        int sharedChunkArenaIndexMb,
        // 中文标注（字段）：`fullHeightMeshingSourceKey`，含义：用于表示full、高度、meshing、source、键。
        String fullHeightMeshingSourceKey,
        // 中文标注（字段）：`fullHeightMeshingSourceValue`，含义：用于表示full、高度、meshing、source、值。
        String fullHeightMeshingSourceValue,
        // 中文标注（字段）：`disableChunkFrustumCullSourceKey`，含义：用于表示disable、区块、视锥体、cull、source、键。
        String disableChunkFrustumCullSourceKey,
        // 中文标注（字段）：`disableChunkFrustumCullSourceValue`，含义：用于表示disable、区块、视锥体、cull、source、值。
        String disableChunkFrustumCullSourceValue,
        // 中文标注（字段）：`occlusionQuerySourceKey`，含义：用于表示occlusion、query、source、键。
        String occlusionQuerySourceKey,
        // 中文标注（字段）：`occlusionQuerySourceValue`，含义：用于表示occlusion、query、source、值。
        String occlusionQuerySourceValue,
        // 中文标注（字段）：`meshConfigHash`，含义：用于表示网格、config、hash。
        int meshConfigHash
    ) {
        // 中文标注（方法）：`load`，参数：无；用途：获取或读取加载。
        private static GpuConfig load() {
            // 中文标注（局部变量）：`priorityForwardBias`，含义：用于表示priority、forward、bias。
            ResolvedBoolean priorityForwardBias = flagCompat("vc.gpu.priorityForwardBias", "voxelcraft.gpu.priorityForwardBias", true); // meaning
            // 中文标注（局部变量）：`priorityRecentlyVisibleBias`，含义：用于表示priority、recently、visible、bias。
            ResolvedBoolean priorityRecentlyVisibleBias = flagCompat("vc.gpu.priorityRecentlyVisibleBias", "voxelcraft.gpu.priorityRecentlyVisibleBias", true); // meaning
            // 中文标注（局部变量）：`adaptiveUploadBudget`，含义：用于表示adaptive、上传、budget。
            ResolvedBoolean adaptiveUploadBudget = flagCompat("vc.gpu.adaptiveUploadBudget", "voxelcraft.gpu.adaptiveUploadBudget", true); // meaning
            // 中文标注（局部变量）：`adaptiveMeshSubmitBudget`，含义：用于表示adaptive、网格、submit、budget。
            ResolvedBoolean adaptiveMeshSubmitBudget = flagCompat("vc.gpu.adaptiveMeshSubmitBudget", "voxelcraft.gpu.adaptiveMeshSubmitBudget", true); // meaning
            // 中文标注（局部变量）：`orphaningUpload`，含义：用于表示orphaning、上传。
            ResolvedBoolean orphaningUpload = flagCompat("vc.gpu.orphaningUpload", "voxelcraft.gpu.orphaningUpload", true); // meaning
            // 中文标注（局部变量）：`mdi`，含义：用于表示mdi。
            ResolvedBoolean mdi = flagCompat("vc.gpu.mdi", "voxelcraft.gpu.mdi", false); // meaning
            // 中文标注（局部变量）：`occlusionQuery`，含义：用于表示occlusion、query。
            ResolvedBoolean occlusionQuery = flagCompat("vc.gpu.occlusionQuery", "voxelcraft.gpu.occlusionQuery", false); // meaning
            // 中文标注（局部变量）：`occlusionResultPollBudget`，含义：用于表示occlusion、结果、poll、budget。
            int occlusionResultPollBudget = intFlagCompat("vc.gpu.occlusionPollBudget", "voxelcraft.gpu.occlusionPollBudget", DEFAULT_OCCLUSION_RESULT_POLL_BUDGET); // meaning
            // 中文标注（局部变量）：`persistentMapping`，含义：用于表示persistent、mapping。
            ResolvedBoolean persistentMapping = flagCompat("vc.gpu.persistentMapping", "voxelcraft.gpu.persistentMapping", false); // meaning
            // 中文标注（局部变量）：`uploadTimeBudgetMs`，含义：用于表示上传、时间、budget、ms。
            double uploadTimeBudgetMs = doubleFlagCompat("vc.gpu.uploadBudgetMs", "voxelcraft.gpu.uploadBudgetMs", DEFAULT_UPLOAD_TIME_BUDGET_MS); // meaning
            // 中文标注（局部变量）：`fullHeightMeshing`，含义：用于表示full、高度、meshing。
            ResolvedBoolean fullHeightMeshing = flagCompat("vc.gpu.fullHeightMeshing", "voxelcraft.gpu.fullHeightMeshing", false); // meaning
            // 中文标注（局部变量）：`disableChunkFrustumCull`，含义：用于表示disable、区块、视锥体、cull。
            ResolvedBoolean disableChunkFrustumCull = flagCompat("vc.gpu.disableChunkFrustumCull", "voxelcraft.gpu.disableChunkFrustumCull", false); // meaning
            ResolvedBoolean disableChunkOcclusionCull = flagCompat("vc.gpu.disableChunkOcclusionCull", "voxelcraft.gpu.disableChunkOcclusionCull", false); // meaning
            ResolvedBoolean disableFaceCulling = flagCompat("vc.gpu.disableFaceCulling", "voxelcraft.gpu.disableFaceCulling", false); // meaning
            ResolvedBoolean hud = flagCompat("vc.gpu.hud", "voxelcraft.gpu.hud", true); // meaning
            ResolvedBoolean applyAmbientToBlocks = flagCompat(
                "vc.lighting.applyAmbientToBlocks",
                "voxelcraft.lighting.applyAmbientToBlocks",
                true
            );
            // 中文标注（局部变量）：`lod`，含义：用于表示细节层级。
            ResolvedBoolean lod = flagCompat("vc.gpu.lod", "voxelcraft.gpu.lod", false); // meaning
            // 中文标注（局部变量）：`lodStartChunkDistance`，含义：用于表示细节层级、开始、区块、distance。
            int lodStartChunkDistance = intFlagCompat("vc.gpu.lodStartChunks", "voxelcraft.gpu.lodStartChunks", DEFAULT_LOD_START_CHUNK_DISTANCE); // meaning
            // 中文标注（局部变量）：`lodHysteresisChunks`，含义：用于表示细节层级、hysteresis、区块集合。
            int lodHysteresisChunks = intFlagCompat("vc.gpu.lodHysteresisChunks", "voxelcraft.gpu.lodHysteresisChunks", DEFAULT_LOD_HYSTERESIS_CHUNKS); // meaning
            // 中文标注（局部变量）：`sharedChunkArena`，含义：用于表示shared、区块、arena。
            ResolvedBoolean sharedChunkArena = flagCompat("vc.gpu.sharedChunkArena", "voxelcraft.gpu.sharedChunkArena", false); // meaning
            // 中文标注（局部变量）：`sharedChunkArenaVertexMb`，含义：用于表示shared、区块、arena、顶点、mb。
            int sharedChunkArenaVertexMb = intFlagCompat("vc.gpu.sharedArenaVertexMB", "voxelcraft.gpu.sharedArenaVertexMB", DEFAULT_SHARED_ARENA_VERTEX_MB); // meaning
            // 中文标注（局部变量）：`sharedChunkArenaIndexMb`，含义：用于表示shared、区块、arena、索引、mb。
            int sharedChunkArenaIndexMb = intFlagCompat("vc.gpu.sharedArenaIndexMB", "voxelcraft.gpu.sharedArenaIndexMB", DEFAULT_SHARED_ARENA_INDEX_MB); // meaning

            // 中文标注（局部变量）：`meshConfigHash`，含义：用于表示网格、config、hash。
            int meshConfigHash = computeMeshConfigHash(
                fullHeightMeshing.value(),
                lod.value(),
                lodStartChunkDistance,
                lodHysteresisChunks
            );

            return new GpuConfig(
                priorityForwardBias.value(),
                priorityRecentlyVisibleBias.value(),
                adaptiveUploadBudget.value(),
                adaptiveMeshSubmitBudget.value(),
                orphaningUpload.value(),
                mdi.value(),
                occlusionQuery.value(),
                occlusionResultPollBudget,
                persistentMapping.value(),
                uploadTimeBudgetMs,
                fullHeightMeshing.value(),
                disableChunkFrustumCull.value(),
                disableChunkOcclusionCull.value(),
                disableFaceCulling.value(),
                hud.value(),
                applyAmbientToBlocks.value(),
                lod.value(),
                lodStartChunkDistance,
                lodHysteresisChunks,
                sharedChunkArena.value(),
                sharedChunkArenaVertexMb,
                sharedChunkArenaIndexMb,
                fullHeightMeshing.sourceKey(),
                fullHeightMeshing.sourceValue(),
                disableChunkFrustumCull.sourceKey(),
                disableChunkFrustumCull.sourceValue(),
                occlusionQuery.sourceKey(),
                occlusionQuery.sourceValue(),
                meshConfigHash
            );
        }

        // 中文标注（方法）：`computeMeshConfigHash`，参数：fullHeightMeshing、lod、lodStartChunkDistance、lodHysteresisChunks；用途：执行compute、网格、config、hash相关逻辑。
        private static int computeMeshConfigHash(
            // 中文标注（参数）：`fullHeightMeshing`，含义：用于表示full、高度、meshing。
            boolean fullHeightMeshing,
            // 中文标注（参数）：`lod`，含义：用于表示细节层级。
            boolean lod,
            // 中文标注（参数）：`lodStartChunkDistance`，含义：用于表示细节层级、开始、区块、distance。
            int lodStartChunkDistance,
            // 中文标注（参数）：`lodHysteresisChunks`，含义：用于表示细节层级、hysteresis、区块集合。
            int lodHysteresisChunks
        ) {
            // 中文标注（局部变量）：`hash`，含义：用于表示hash。
            int hash = 17; // meaning
            hash = hash * 31 + (fullHeightMeshing ? 1 : 0);
            hash = hash * 31 + (lod ? 1 : 0);
            hash = hash * 31 + lodStartChunkDistance;
            hash = hash * 31 + lodHysteresisChunks;
            return hash;
        }

        // 中文标注（方法）：`flagCompat`，参数：key、legacyKey、defaultValue；用途：执行flag、compat相关逻辑。
        // 中文标注（参数）：`key`，含义：用于表示键。
        // 中文标注（参数）：`legacyKey`，含义：用于表示legacy、键。
        // 中文标注（参数）：`defaultValue`，含义：用于表示默认、值。
        private static ResolvedBoolean flagCompat(String key, String legacyKey, boolean defaultValue) {
            // 中文标注（局部变量）：`raw`，含义：用于表示raw。
            String raw = System.getProperty(key); // meaning
            if (raw != null) {
                return new ResolvedBoolean(parseBoolean(raw, defaultValue), key, Boolean.toString(parseBoolean(raw, defaultValue)));
            }
            // 中文标注（局部变量）：`legacyRaw`，含义：用于表示legacy、raw。
            String legacyRaw = System.getProperty(legacyKey); // meaning
            if (legacyRaw != null) {
                return new ResolvedBoolean(parseBoolean(legacyRaw, defaultValue), legacyKey, Boolean.toString(parseBoolean(legacyRaw, defaultValue)));
            }
            return new ResolvedBoolean(defaultValue, "default(" + key + ")", Boolean.toString(defaultValue));
        }

        // 中文标注（方法）：`intFlagCompat`，参数：key、legacyKey、defaultValue；用途：执行int、flag、compat相关逻辑。
        // 中文标注（参数）：`key`，含义：用于表示键。
        // 中文标注（参数）：`legacyKey`，含义：用于表示legacy、键。
        // 中文标注（参数）：`defaultValue`，含义：用于表示默认、值。
        private static int intFlagCompat(String key, String legacyKey, int defaultValue) {
            // 中文标注（局部变量）：`raw`，含义：用于表示raw。
            String raw = System.getProperty(key); // meaning
            if (raw != null) {
                return parseInt(raw, defaultValue);
            }
            // 中文标注（局部变量）：`legacyRaw`，含义：用于表示legacy、raw。
            String legacyRaw = System.getProperty(legacyKey); // meaning
            if (legacyRaw != null) {
                return parseInt(legacyRaw, defaultValue);
            }
            return defaultValue;
        }

        // 中文标注（方法）：`doubleFlagCompat`，参数：key、legacyKey、defaultValue；用途：执行double、flag、compat相关逻辑。
        // 中文标注（参数）：`key`，含义：用于表示键。
        // 中文标注（参数）：`legacyKey`，含义：用于表示legacy、键。
        // 中文标注（参数）：`defaultValue`，含义：用于表示默认、值。
        private static double doubleFlagCompat(String key, String legacyKey, double defaultValue) {
            // 中文标注（局部变量）：`raw`，含义：用于表示raw。
            String raw = System.getProperty(key); // meaning
            if (raw != null) {
                return parseDouble(raw, defaultValue);
            }
            // 中文标注（局部变量）：`legacyRaw`，含义：用于表示legacy、raw。
            String legacyRaw = System.getProperty(legacyKey); // meaning
            if (legacyRaw != null) {
                return parseDouble(legacyRaw, defaultValue);
            }
            return defaultValue;
        }

        // 中文标注（方法）：`parseBoolean`，参数：raw、defaultValue；用途：执行parse、boolean相关逻辑。
        // 中文标注（参数）：`raw`，含义：用于表示raw。
        // 中文标注（参数）：`defaultValue`，含义：用于表示默认、值。
        private static boolean parseBoolean(String raw, boolean defaultValue) {
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

        // 中文标注（方法）：`parseInt`，参数：raw、defaultValue；用途：执行parse、int相关逻辑。
        // 中文标注（参数）：`raw`，含义：用于表示raw。
        // 中文标注（参数）：`defaultValue`，含义：用于表示默认、值。
        private static int parseInt(String raw, int defaultValue) {
            try {
                return Integer.parseInt(raw.trim());
            // 中文标注（异常参数）：`ignored`，含义：用于表示ignored。
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }

        // 中文标注（方法）：`parseDouble`，参数：raw、defaultValue；用途：执行parse、double相关逻辑。
        // 中文标注（参数）：`raw`，含义：用于表示raw。
        // 中文标注（参数）：`defaultValue`，含义：用于表示默认、值。
        private static double parseDouble(String raw, double defaultValue) {
            try {
                // 中文标注（局部变量）：`parsed`，含义：用于表示parsed。
                double parsed = Double.parseDouble(raw.trim()); // meaning
                if (Double.isFinite(parsed) && parsed >= 0.0) {
                    return parsed;
                }
            // 中文标注（异常参数）：`ignored`，含义：用于表示ignored。
            } catch (NumberFormatException ignored) {
            }
            return defaultValue;
        }

        // 中文标注（记录类）：`ResolvedBoolean`，职责：封装resolved、boolean相关逻辑。
        // 中文标注（字段）：`value`，含义：用于表示值。
        // 中文标注（字段）：`sourceKey`，含义：用于表示source、键。
        // 中文标注（字段）：`sourceValue`，含义：用于表示source、值。
        private record ResolvedBoolean(boolean value, String sourceKey, String sourceValue) {
        }
    }

    // 中文标注（类）：`PrioritizedMeshTask`，职责：封装prioritized、网格、task相关逻辑。
    private static final class PrioritizedMeshTask implements Runnable, Comparable<PrioritizedMeshTask> {
        // 中文标注（字段）：`priorityKey`，含义：用于表示priority、键。
        private final double priorityKey; // meaning
        // 中文标注（字段）：`sequence`，含义：用于表示sequence。
        private final long sequence; // meaning
        // 中文标注（字段）：`delegate`，含义：用于表示delegate。
        private final Runnable delegate; // meaning
        // 中文标注（字段）：`cancelAction`，含义：用于表示取消、动作。
        private final Runnable cancelAction; // meaning
        // 中文标注（字段）：`completedOrCancelled`，含义：用于表示已完成或已取消。
        private final AtomicBoolean completedOrCancelled = new AtomicBoolean(); // meaning

        // 中文标注（构造方法）：`PrioritizedMeshTask`，参数：priorityKey、sequence、delegate、cancelAction；用途：初始化`PrioritizedMeshTask`实例。
        // 中文标注（参数）：`priorityKey`，含义：用于表示priority、键。
        // 中文标注（参数）：`sequence`，含义：用于表示sequence。
        // 中文标注（参数）：`delegate`，含义：用于表示delegate。
        // 中文标注（参数）：`cancelAction`，含义：用于表示取消、动作。
        private PrioritizedMeshTask(double priorityKey, long sequence, Runnable delegate, Runnable cancelAction) {
            this.priorityKey = priorityKey;
            this.sequence = sequence;
            this.delegate = delegate;
            this.cancelAction = cancelAction;
        }

        // 中文标注（方法）：`priorityKey`，参数：无；用途：执行priority、键相关逻辑。
        private double priorityKey() {
            return priorityKey;
        }

        // 中文标注（方法）：`run`，参数：无；用途：执行run相关逻辑。
        @Override
        public void run() {
            if (!completedOrCancelled.compareAndSet(false, true)) {
                return;
            }
            delegate.run();
        }

        // 中文标注（方法）：`cancel`，参数：无；用途：取消尚未执行的 meshing 任务并释放捕获资源。
        private void cancel() {
            if (!completedOrCancelled.compareAndSet(false, true)) {
                return;
            }
            if (cancelAction != null) {
                cancelAction.run();
            }
        }

        // 中文标注（方法）：`compareTo`，参数：other；用途：执行compare、to相关逻辑。
        @Override
        // 中文标注（参数）：`other`，含义：用于表示other。
        public int compareTo(PrioritizedMeshTask other) {
            // 中文标注（局部变量）：`byPriority`，含义：用于表示by、priority。
            int byPriority = Double.compare(priorityKey, other.priorityKey); // meaning
            if (byPriority != 0) {
                return byPriority;
            }
            return Long.compare(sequence, other.sequence);
        }
    }

    // 中文标注（类）：`GpuChunk`，职责：封装GPU、区块相关逻辑。
    private static final class GpuChunk {
        // 中文标注（字段）：`pos`，含义：用于表示位置。
        private final ChunkPos pos; // meaning
        // 中文标注（字段）：`vboId`，含义：用于表示vbo、标识。
        private int vboId; // meaning
        // 中文标注（字段）：`iboId`，含义：用于表示ibo、标识。
        private int iboId; // meaning
        // 中文标注（字段）：`occlusionQueryId`，含义：用于表示occlusion、query、标识。
        private int occlusionQueryId; // meaning
        // 中文标注（字段）：`indexCount`，含义：用于表示索引、数量。
        private int indexCount; // meaning
        // 中文标注（字段）：`triangleCount`，含义：用于表示triangle、数量。
        private int triangleCount; // meaning
        // 中文标注（字段）：`versionUploaded`，含义：用于表示版本、uploaded。
        private long versionUploaded = Long.MIN_VALUE; // meaning
        // 中文标注（字段）：`lodLevelUploaded`，含义：用于表示细节层级、级别、uploaded。
        private int lodLevelUploaded; // meaning
        // 中文标注（字段）：`bandKeyUploaded`，含义：用于表示带、键、uploaded。
        private int bandKeyUploaded; // meaning
        // 中文标注（字段）：`fullHeightMeshingUploaded`，含义：用于表示full、高度、meshing、uploaded。
        private boolean fullHeightMeshingUploaded; // meaning
        // 中文标注（字段）：`meshConfigHashUploaded`，含义：用于表示网格、config、hash、uploaded。
        private int meshConfigHashUploaded; // meaning
        // 中文标注（字段）：`valid`，含义：用于表示valid。
        private boolean valid; // meaning
        // 中文标注（字段）：`usesSharedArena`，含义：用于表示uses、shared、arena。
        private boolean usesSharedArena; // meaning
        // 中文标注（字段）：`sharedVertexOffsetBytes`，含义：用于表示shared、顶点、偏移、字节数据。
        private long sharedVertexOffsetBytes; // meaning
        // 中文标注（字段）：`sharedIndexOffsetBytes`，含义：用于表示shared、索引、偏移、字节数据。
        private long sharedIndexOffsetBytes; // meaning
        // 中文标注（字段）：`sharedAllocation`，含义：用于表示shared、allocation。
        private SharedChunkBufferArena.Allocation sharedAllocation; // meaning
        // 中文标注（字段）：`lastUploadSharedArenaAllocFailure`，含义：用于表示last、上传、shared、arena、alloc、failure。
        private boolean lastUploadSharedArenaAllocFailure; // meaning
        // 中文标注（字段）：`lastUploadSharedArenaFallback`，含义：用于表示last、上传、shared、arena、fallback。
        private boolean lastUploadSharedArenaFallback; // meaning
        // 中文标注（字段）：`occlusionQueryPending`，含义：用于表示occlusion、query、pending。
        private boolean occlusionQueryPending; // meaning
        // 中文标注（字段）：`occlusionVisible`，含义：用于表示occlusion、visible。
        private boolean occlusionVisible = true; // meaning
        // 中文标注（字段）：`occlusionHiddenStreak`，含义：用于表示occlusion、hidden、streak。
        private int occlusionHiddenStreak; // meaning
        // 中文标注（字段）：`occlusionLastQueryFrame`，含义：用于表示occlusion、last、query、帧。
        private long occlusionLastQueryFrame = Long.MIN_VALUE; // meaning
        // 中文标注（字段）：`vboCapacityBytes`，含义：用于表示vbo、capacity、字节数据。
        private int vboCapacityBytes; // meaning
        // 中文标注（字段）：`iboCapacityBytes`，含义：用于表示ibo、capacity、字节数据。
        private int iboCapacityBytes; // meaning
        // 中文标注（字段）：`lastMeshSubmitNanos`，含义：用于表示last、网格、submit、nanos。
        private long lastMeshSubmitNanos; // meaning
        // 中文标注（字段）：`lastVisibleLatencyRecordedVersion`，含义：用于表示last、visible、latency、recorded、版本。
        private long lastVisibleLatencyRecordedVersion = Long.MIN_VALUE; // meaning
        // 中文标注（字段）：`minX`，含义：用于表示最小、X坐标。
        private double minX; // meaning
        // 中文标注（字段）：`minY`，含义：用于表示最小、Y坐标。
        private double minY; // meaning
        // 中文标注（字段）：`minZ`，含义：用于表示最小、Z坐标。
        private double minZ; // meaning
        // 中文标注（字段）：`maxX`，含义：用于表示最大、X坐标。
        private double maxX; // meaning
        // 中文标注（字段）：`maxY`，含义：用于表示最大、Y坐标。
        private double maxY; // meaning
        // 中文标注（字段）：`maxZ`，含义：用于表示最大、Z坐标。
        private double maxZ; // meaning

        // 中文标注（构造方法）：`GpuChunk`，参数：pos；用途：初始化`GpuChunk`实例。
        // 中文标注（参数）：`pos`，含义：用于表示位置。
        private GpuChunk(ChunkPos pos) {
            this.pos = pos;
        }

        // 中文标注（方法）：`upload`，参数：meshData、submittedNanos、fullHeightMeshing、meshConfigHash、orphaningUpload、sharedArena；用途：执行渲染或图形资源处理：上传。
        private UploadStats upload(
            // 中文标注（参数）：`meshData`，含义：用于表示网格、数据。
            ChunkMeshData meshData,
            // 中文标注（参数）：`submittedNanos`，含义：用于表示submitted、nanos。
            long submittedNanos,
            // 中文标注（参数）：`fullHeightMeshing`，含义：用于表示full、高度、meshing。
            boolean fullHeightMeshing,
            // 中文标注（参数）：`meshConfigHash`，含义：用于表示网格、config、hash。
            int meshConfigHash,
            // 中文标注（参数）：`orphaningUpload`，含义：用于表示orphaning、上传。
            boolean orphaningUpload,
            // 中文标注（参数）：`sharedArena`，含义：用于表示shared、arena。
            SharedChunkBufferArena sharedArena
        ) {
            // 中文标注（局部变量）：`uploadBytes`，含义：用于表示上传、字节数据。
            long uploadBytes = 0L; // meaning
            // 中文标注（局部变量）：`bufferReallocs`，含义：用于表示缓冲区、reallocs。
            int bufferReallocs = 0; // meaning
            // 中文标注（局部变量）：`bufferOrphans`，含义：用于表示缓冲区、orphans。
            int bufferOrphans = 0; // meaning
            // 中文标注（局部变量）：`bufferSubDatas`，含义：用于表示缓冲区、sub、datas。
            int bufferSubDatas = 0; // meaning
            lastUploadSharedArenaAllocFailure = false;
            lastUploadSharedArenaFallback = false;
            if (meshData.indexCount() > 0) {
                // 中文标注（局部变量）：`vertexBytes`，含义：用于表示顶点、字节数据。
                ByteBuffer vertexBytes = meshData.vertexBytes(); // meaning
                // 中文标注（局部变量）：`indexBytes`，含义：用于表示索引、字节数据。
                ByteBuffer indexBytes = meshData.indexBytes(); // meaning
                if (vertexBytes == null || indexBytes == null) {
                    throw new IllegalStateException("Non-empty mesh missing upload buffers for chunk " + pos);
                }

                if (sharedArena != null) {
                    // 中文标注（局部变量）：`sharedUploaded`，含义：用于表示shared、uploaded。
                    boolean sharedUploaded = uploadToSharedArena(sharedArena, meshData, vertexBytes, indexBytes); // meaning
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
            bandKeyUploaded = meshData.bandKey();
            fullHeightMeshingUploaded = fullHeightMeshing;
            meshConfigHashUploaded = meshConfigHash;
            minX = meshData.minX();
            minY = meshData.minY();
            minZ = meshData.minZ();
            maxX = meshData.maxX();
            maxY = meshData.maxY();
            maxZ = meshData.maxZ();
            lastMeshSubmitNanos = submittedNanos;
            valid = true;
            // after mesh upload, discard any pending query result for old geometry and reset visibility hysteresis
            occlusionQueryPending = false;
            occlusionHiddenStreak = 0;
            occlusionVisible = true;
            return new UploadStats(1, uploadBytes, bufferReallocs, bufferOrphans, bufferSubDatas);
        }

        // 中文标注（字段）：`localUploadBufferReallocs`，含义：用于表示局部、上传、缓冲区、reallocs。
        private int localUploadBufferReallocs; // meaning
        // 中文标注（字段）：`localUploadBufferOrphans`，含义：用于表示局部、上传、缓冲区、orphans。
        private int localUploadBufferOrphans; // meaning
        // 中文标注（字段）：`localUploadBufferSubDatas`，含义：用于表示局部、上传、缓冲区、sub、datas。
        private int localUploadBufferSubDatas; // meaning

        // 中文标注（方法）：`uploadToLocalBuffers`，参数：meshData、vertexBytes、indexBytes、orphaningUpload；用途：执行渲染或图形资源处理：上传、to、局部、buffers。
        private long uploadToLocalBuffers(
            // 中文标注（参数）：`meshData`，含义：用于表示网格、数据。
            ChunkMeshData meshData,
            // 中文标注（参数）：`vertexBytes`，含义：用于表示顶点、字节数据。
            ByteBuffer vertexBytes,
            // 中文标注（参数）：`indexBytes`，含义：用于表示索引、字节数据。
            ByteBuffer indexBytes,
            // 中文标注（参数）：`orphaningUpload`，含义：用于表示orphaning、上传。
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

        // 中文标注（方法）：`uploadToSharedArena`，参数：sharedArena、meshData、vertexBytes、indexBytes；用途：执行渲染或图形资源处理：上传、to、shared、arena。
        private boolean uploadToSharedArena(
            // 中文标注（参数）：`sharedArena`，含义：用于表示shared、arena。
            SharedChunkBufferArena sharedArena,
            // 中文标注（参数）：`meshData`，含义：用于表示网格、数据。
            ChunkMeshData meshData,
            // 中文标注（参数）：`vertexBytes`，含义：用于表示顶点、字节数据。
            ByteBuffer vertexBytes,
            // 中文标注（参数）：`indexBytes`，含义：用于表示索引、字节数据。
            ByteBuffer indexBytes
        ) {
            if (sharedArena == null) {
                return false;
            }
            // 中文标注（局部变量）：`vertexBytesNeeded`，含义：用于表示顶点、字节数据、needed。
            int vertexBytesNeeded = meshData.vertexByteCount(); // meaning
            // 中文标注（局部变量）：`indexBytesNeeded`，含义：用于表示索引、字节数据、needed。
            int indexBytesNeeded = meshData.indexByteCount(); // meaning
            if (sharedAllocation == null
                || sharedAllocation.vertexCapacityBytes() < vertexBytesNeeded
                || sharedAllocation.indexCapacityBytes() < indexBytesNeeded) {
                if (sharedAllocation != null) {
                    sharedArena.free(sharedAllocation);
                    sharedAllocation = null;
                }
                // 中文标注（局部变量）：`allocation`，含义：用于表示allocation。
                SharedChunkBufferArena.Allocation allocation = sharedArena.allocate(vertexBytesNeeded, indexBytesNeeded); // meaning
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

        // 中文标注（方法）：`sharedArenaFree`，参数：sharedArena；用途：执行shared、arena、free相关逻辑。
        // 中文标注（参数）：`sharedArena`，含义：用于表示shared、arena。
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

        // 中文标注（方法）：`freeLocalBuffers`，参数：无；用途：执行free、局部、buffers相关逻辑。
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

        // 中文标注（方法）：`ensureOcclusionQueryId`，参数：无；用途：执行ensure、occlusion、query、标识相关逻辑。
        private int ensureOcclusionQueryId() {
            if (occlusionQueryId == 0) {
                occlusionQueryId = glGenQueries();
            }
            return occlusionQueryId;
        }

        // 中文标注（方法）：`markOcclusionQueryIssued`，参数：frameSequence；用途：执行mark、occlusion、query、issued相关逻辑。
        // 中文标注（参数）：`frameSequence`，含义：用于表示帧、sequence。
        private void markOcclusionQueryIssued(long frameSequence) {
            occlusionQueryPending = true;
            occlusionLastQueryFrame = frameSequence;
        }

        // 中文标注（方法）：`hasPendingOcclusionQuery`，参数：无；用途：判断pending、occlusion、query是否满足条件。
        private boolean hasPendingOcclusionQuery() {
            return occlusionQueryPending && occlusionQueryId != 0;
        }

        // 中文标注（方法）：`pollOcclusionQueryResult`，参数：无；用途：执行poll、occlusion、query、结果相关逻辑。
        private boolean pollOcclusionQueryResult() {
            if (!occlusionQueryPending || occlusionQueryId == 0) {
                return false;
            }
            if (glGetQueryObjecti(occlusionQueryId, GL_QUERY_RESULT_AVAILABLE) == 0) {
                return true;
            }
            // 中文标注（局部变量）：`samples`，含义：用于表示samples。
            int samples = glGetQueryObjecti(occlusionQueryId, GL_QUERY_RESULT); // meaning
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

        // 中文标注（方法）：`shouldDrawBasedOnOcclusion`，参数：frameSequence；用途：判断should、绘制、based、on、occlusion是否满足条件。
        // 中文标注（参数）：`frameSequence`，含义：用于表示帧、sequence。
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

        // 中文标注（方法）：`shouldIssueOcclusionQuery`，参数：frameSequence；用途：判断should、issue、occlusion、query是否满足条件。
        // 中文标注（参数）：`frameSequence`，含义：用于表示帧、sequence。
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

        // 中文标注（方法）：`dispose`，参数：sharedArena；用途：执行dispose相关逻辑。
        // 中文标注（参数）：`sharedArena`，含义：用于表示shared、arena。
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
            versionUploaded = Long.MIN_VALUE;
            lodLevelUploaded = 0;
            bandKeyUploaded = 0;
            fullHeightMeshingUploaded = false;
            meshConfigHashUploaded = 0;
            lastMeshSubmitNanos = 0L;
            lastVisibleLatencyRecordedVersion = Long.MIN_VALUE;
            occlusionQueryPending = false;
            occlusionVisible = true;
            occlusionHiddenStreak = 0;
            occlusionLastQueryFrame = Long.MIN_VALUE;
            valid = false;
        }
    }

    // 中文标注（类）：`OcclusionBoxMesh`，职责：封装occlusion、box、网格相关逻辑。
    private static final class OcclusionBoxMesh {
        // 中文标注（字段）：`UNIT_CUBE_VERTICES`，含义：用于表示unit、cube、顶点集合。
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
        // 中文标注（字段）：`UNIT_CUBE_INDICES`，含义：用于表示unit、cube、索引集合。
        private static final int[] UNIT_CUBE_INDICES = {
            0, 1, 2, 2, 3, 0, // back
            4, 7, 6, 6, 5, 4, // front
            0, 4, 5, 5, 1, 0, // bottom
            3, 2, 6, 6, 7, 3, // top
            1, 5, 6, 6, 2, 1, // right
            0, 3, 7, 7, 4, 0  // left
        };

        // 中文标注（字段）：`vboId`，含义：用于表示vbo、标识。
        private final int vboId; // meaning
        // 中文标注（字段）：`iboId`，含义：用于表示ibo、标识。
        private final int iboId; // meaning
        // 中文标注（字段）：`indexCount`，含义：用于表示索引、数量。
        private final int indexCount; // meaning

        // 中文标注（构造方法）：`OcclusionBoxMesh`，参数：无；用途：初始化`OcclusionBoxMesh`实例。
        private OcclusionBoxMesh() {
            // 中文标注（局部变量）：`createdVboId`，含义：用于表示已创建 vbo 标识。
            int createdVboId = 0; // meaning
            // 中文标注（局部变量）：`createdIboId`，含义：用于表示已创建 ibo 标识。
            int createdIboId = 0; // meaning
            try {
                createdVboId = glGenBuffers();
                createdIboId = glGenBuffers();

                // 中文标注（局部变量）：`vb`，含义：用于表示vb。
                ByteBuffer vb = ByteBuffer.allocateDirect(UNIT_CUBE_VERTICES.length * Float.BYTES).order(ByteOrder.nativeOrder()); // meaning
                // 中文标注（局部变量）：`vf`，含义：用于表示vf。
                FloatBuffer vf = vb.asFloatBuffer(); // meaning
                vf.put(UNIT_CUBE_VERTICES).flip();

                // 中文标注（局部变量）：`ib`，含义：用于表示ib。
                ByteBuffer ib = ByteBuffer.allocateDirect(UNIT_CUBE_INDICES.length * Integer.BYTES).order(ByteOrder.nativeOrder()); // meaning
                // 中文标注（局部变量）：`ii`，含义：用于表示ii。
                IntBuffer ii = ib.asIntBuffer(); // meaning
                ii.put(UNIT_CUBE_INDICES).flip();

                glBindBuffer(GL_ARRAY_BUFFER, createdVboId);
                glBufferData(GL_ARRAY_BUFFER, vb, GL_STATIC_DRAW);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, createdIboId);
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
            // 中文标注（异常参数）：`failure`，含义：用于表示构造、failure。
            } catch (RuntimeException failure) {
                if (createdVboId != 0) {
                    glDeleteBuffers(createdVboId);
                }
                if (createdIboId != 0) {
                    glDeleteBuffers(createdIboId);
                }
                throw failure;
            // 中文标注（异常参数）：`failure`，含义：用于表示构造、failure。
            } catch (Error failure) {
                if (createdVboId != 0) {
                    glDeleteBuffers(createdVboId);
                }
                if (createdIboId != 0) {
                    glDeleteBuffers(createdIboId);
                }
                throw failure;
            } finally {
                glBindBuffer(GL_ARRAY_BUFFER, 0);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            }
            vboId = createdVboId;
            iboId = createdIboId;
            indexCount = UNIT_CUBE_INDICES.length;
        }

        // 中文标注（方法）：`dispose`，参数：无；用途：执行dispose相关逻辑。
        private void dispose() {
            glDeleteBuffers(vboId);
            glDeleteBuffers(iboId);
        }
    }

    // 中文标注（类）：`SharedChunkBufferArena`，职责：封装shared、区块、缓冲区、arena相关逻辑。
    private static final class SharedChunkBufferArena {
        // 中文标注（字段）：`vboId`，含义：用于表示vbo、标识。
        private final int vboId; // meaning
        // 中文标注（字段）：`iboId`，含义：用于表示ibo、标识。
        private final int iboId; // meaning
        // 中文标注（字段）：`vertexCapacityBytes`，含义：用于表示顶点、capacity、字节数据。
        private final int vertexCapacityBytes; // meaning
        // 中文标注（字段）：`indexCapacityBytes`，含义：用于表示索引、capacity、字节数据。
        private final int indexCapacityBytes; // meaning
        // 中文标注（字段）：`freeVertexRanges`，含义：用于表示free、顶点、ranges。
        private final ArrayList<Range> freeVertexRanges = new ArrayList<>(); // meaning
        // 中文标注（字段）：`freeIndexRanges`，含义：用于表示free、索引、ranges。
        private final ArrayList<Range> freeIndexRanges = new ArrayList<>(); // meaning
        // 中文标注（字段）：`usedVertexBytes`，含义：用于表示used、顶点、字节数据。
        private int usedVertexBytes; // meaning
        // 中文标注（字段）：`usedIndexBytes`，含义：用于表示used、索引、字节数据。
        private int usedIndexBytes; // meaning

        // 中文标注（构造方法）：`SharedChunkBufferArena`，参数：vertexCapacityBytes、indexCapacityBytes；用途：初始化`SharedChunkBufferArena`实例。
        // 中文标注（参数）：`vertexCapacityBytes`，含义：用于表示顶点、capacity、字节数据。
        // 中文标注（参数）：`indexCapacityBytes`，含义：用于表示索引、capacity、字节数据。
        private SharedChunkBufferArena(int vertexCapacityBytes, int indexCapacityBytes) {
            // 中文标注（局部变量）：`resolvedVertexCapacityBytes`，含义：用于表示解析后的顶点容量。
            int resolvedVertexCapacityBytes = Math.max(64 * 1024, vertexCapacityBytes); // meaning
            // 中文标注（局部变量）：`resolvedIndexCapacityBytes`，含义：用于表示解析后的索引容量。
            int resolvedIndexCapacityBytes = Math.max(64 * 1024, indexCapacityBytes); // meaning
            // 中文标注（局部变量）：`createdVboId`，含义：用于表示已创建 vbo 标识。
            int createdVboId = 0; // meaning
            // 中文标注（局部变量）：`createdIboId`，含义：用于表示已创建 ibo 标识。
            int createdIboId = 0; // meaning
            try {
                createdVboId = glGenBuffers();
                createdIboId = glGenBuffers();
                glBindBuffer(GL_ARRAY_BUFFER, createdVboId);
                glBufferData(GL_ARRAY_BUFFER, (long) resolvedVertexCapacityBytes, GL_STATIC_DRAW);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, createdIboId);
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, (long) resolvedIndexCapacityBytes, GL_STATIC_DRAW);
            // 中文标注（异常参数）：`failure`，含义：用于表示构造、failure。
            } catch (RuntimeException failure) {
                if (createdVboId != 0) {
                    glDeleteBuffers(createdVboId);
                }
                if (createdIboId != 0) {
                    glDeleteBuffers(createdIboId);
                }
                throw failure;
            // 中文标注（异常参数）：`failure`，含义：用于表示构造、failure。
            } catch (Error failure) {
                if (createdVboId != 0) {
                    glDeleteBuffers(createdVboId);
                }
                if (createdIboId != 0) {
                    glDeleteBuffers(createdIboId);
                }
                throw failure;
            } finally {
                glBindBuffer(GL_ARRAY_BUFFER, 0);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            }
            this.vertexCapacityBytes = resolvedVertexCapacityBytes;
            this.indexCapacityBytes = resolvedIndexCapacityBytes;
            this.vboId = createdVboId;
            this.iboId = createdIboId;
            freeVertexRanges.add(new Range(0, this.vertexCapacityBytes));
            freeIndexRanges.add(new Range(0, this.indexCapacityBytes));
        }

        // 中文标注（方法）：`allocate`，参数：vertexBytes、indexBytes；用途：执行allocate相关逻辑。
        // 中文标注（参数）：`vertexBytes`，含义：用于表示顶点、字节数据。
        // 中文标注（参数）：`indexBytes`，含义：用于表示索引、字节数据。
        private Allocation allocate(int vertexBytes, int indexBytes) {
            if (vertexBytes < 0 || indexBytes < 0) {
                return null;
            }
            // 中文标注（局部变量）：`vertexRange`，含义：用于表示顶点、范围。
            Range vertexRange = takeRange(freeVertexRanges, align(vertexBytes, 16)); // meaning
            if (vertexRange == null) {
                return null;
            }
            // 中文标注（局部变量）：`indexRange`，含义：用于表示索引、范围。
            Range indexRange = takeRange(freeIndexRanges, align(indexBytes, 16)); // meaning
            if (indexRange == null) {
                putRange(freeVertexRanges, vertexRange);
                return null;
            }
            usedVertexBytes += vertexRange.length;
            usedIndexBytes += indexRange.length;
            return new Allocation(vertexRange, indexRange);
        }

        // 中文标注（方法）：`free`，参数：allocation；用途：执行free相关逻辑。
        // 中文标注（参数）：`allocation`，含义：用于表示allocation。
        private void free(Allocation allocation) {
            if (allocation == null) {
                return;
            }
            usedVertexBytes = Math.max(0, usedVertexBytes - allocation.vertexRange.length);
            usedIndexBytes = Math.max(0, usedIndexBytes - allocation.indexRange.length);
            putRange(freeVertexRanges, allocation.vertexRange);
            putRange(freeIndexRanges, allocation.indexRange);
        }

        // 中文标注（方法）：`upload`，参数：allocation、vertexBytes、vertexByteCount、indexBytes、indexByteCount；用途：执行渲染或图形资源处理：上传。
        // 中文标注（参数）：`allocation`，含义：用于表示allocation。
        // 中文标注（参数）：`vertexBytes`，含义：用于表示顶点、字节数据。
        // 中文标注（参数）：`vertexByteCount`，含义：用于表示顶点、字节、数量。
        // 中文标注（参数）：`indexBytes`，含义：用于表示索引、字节数据。
        // 中文标注（参数）：`indexByteCount`，含义：用于表示索引、字节、数量。
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

        // 中文标注（方法）：`vboId`，参数：无；用途：执行vbo、标识相关逻辑。
        private int vboId() {
            return vboId;
        }

        // 中文标注（方法）：`iboId`，参数：无；用途：执行ibo、标识相关逻辑。
        private int iboId() {
            return iboId;
        }

        // 中文标注（方法）：`usedVertexBytes`，参数：无；用途：执行used、顶点、字节数据相关逻辑。
        private int usedVertexBytes() {
            return usedVertexBytes;
        }

        // 中文标注（方法）：`usedIndexBytes`，参数：无；用途：执行used、索引、字节数据相关逻辑。
        private int usedIndexBytes() {
            return usedIndexBytes;
        }

        // 中文标注（方法）：`dispose`，参数：无；用途：执行dispose相关逻辑。
        private void dispose() {
            glDeleteBuffers(vboId);
            glDeleteBuffers(iboId);
            freeVertexRanges.clear();
            freeIndexRanges.clear();
            usedVertexBytes = 0;
            usedIndexBytes = 0;
        }

        // 中文标注（方法）：`align`，参数：value、alignment；用途：执行align相关逻辑。
        // 中文标注（参数）：`value`，含义：用于表示值。
        // 中文标注（参数）：`alignment`，含义：用于表示alignment。
        private static int align(int value, int alignment) {
            if (value <= 0) {
                return 0;
            }
            // 中文标注（局部变量）：`mask`，含义：用于表示掩码。
            int mask = alignment - 1; // meaning
            return (value + mask) & ~mask;
        }

        // 中文标注（方法）：`takeRange`，参数：freeRanges、bytes；用途：执行take、范围相关逻辑。
        // 中文标注（参数）：`freeRanges`，含义：用于表示free、ranges。
        // 中文标注（参数）：`bytes`，含义：用于表示字节数据。
        private static Range takeRange(ArrayList<Range> freeRanges, int bytes) {
            if (bytes == 0) {
                return new Range(0, 0);
            }
            // 中文标注（局部变量）：`i`，含义：用于表示i。
            for (int i = 0; i < freeRanges.size(); i++) { // meaning
                // 中文标注（局部变量）：`range`，含义：用于表示范围。
                Range range = freeRanges.get(i); // meaning
                if (range.length < bytes) {
                    continue;
                }
                // 中文标注（局部变量）：`allocated`，含义：用于表示allocated。
                Range allocated = new Range(range.offset, bytes); // meaning
                if (range.length == bytes) {
                    freeRanges.remove(i);
                } else {
                    freeRanges.set(i, new Range(range.offset + bytes, range.length - bytes));
                }
                return allocated;
            }
            return null;
        }

        // 中文标注（方法）：`putRange`，参数：freeRanges、returned；用途：设置、写入或注册put、范围。
        // 中文标注（参数）：`freeRanges`，含义：用于表示free、ranges。
        // 中文标注（参数）：`returned`，含义：用于表示returned。
        private static void putRange(ArrayList<Range> freeRanges, Range returned) {
            if (returned == null || returned.length <= 0) {
                return;
            }
            // 中文标注（局部变量）：`insertAt`，含义：用于表示insert、at。
            int insertAt = 0; // meaning
            while (insertAt < freeRanges.size() && freeRanges.get(insertAt).offset < returned.offset) {
                insertAt++;
            }
            freeRanges.add(insertAt, returned);
            // 中文标注（局部变量）：`mergeIndex`，含义：用于表示merge、索引。
            int mergeIndex = Math.max(0, insertAt - 1); // meaning
            while (mergeIndex < freeRanges.size() - 1) {
                // 中文标注（局部变量）：`left`，含义：用于表示left。
                Range left = freeRanges.get(mergeIndex); // meaning
                // 中文标注（局部变量）：`right`，含义：用于表示right。
                Range right = freeRanges.get(mergeIndex + 1); // meaning
                if (left.offset + left.length != right.offset) {
                    mergeIndex++;
                    continue;
                }
                freeRanges.set(mergeIndex, new Range(left.offset, left.length + right.length));
                freeRanges.remove(mergeIndex + 1);
            }
        }

        // 中文标注（记录类）：`Range`，职责：封装范围相关逻辑。
        // 中文标注（字段）：`offset`，含义：用于表示偏移。
        // 中文标注（字段）：`length`，含义：用于表示长度。
        private record Range(int offset, int length) {
        }

        // 中文标注（类）：`Allocation`，职责：封装allocation相关逻辑。
        private static final class Allocation {
            // 中文标注（字段）：`vertexRange`，含义：用于表示顶点、范围。
            private final Range vertexRange; // meaning
            // 中文标注（字段）：`indexRange`，含义：用于表示索引、范围。
            private final Range indexRange; // meaning

            // 中文标注（构造方法）：`Allocation`，参数：vertexRange、indexRange；用途：初始化`Allocation`实例。
            // 中文标注（参数）：`vertexRange`，含义：用于表示顶点、范围。
            // 中文标注（参数）：`indexRange`，含义：用于表示索引、范围。
            private Allocation(Range vertexRange, Range indexRange) {
                this.vertexRange = vertexRange;
                this.indexRange = indexRange;
            }

            // 中文标注（方法）：`vertexOffsetBytes`，参数：无；用途：执行顶点、偏移、字节数据相关逻辑。
            private long vertexOffsetBytes() {
                return vertexRange.offset;
            }

            // 中文标注（方法）：`indexOffsetBytes`，参数：无；用途：执行索引、偏移、字节数据相关逻辑。
            private long indexOffsetBytes() {
                return indexRange.offset;
            }

            // 中文标注（方法）：`vertexCapacityBytes`，参数：无；用途：执行顶点、capacity、字节数据相关逻辑。
            private int vertexCapacityBytes() {
                return vertexRange.length;
            }

            // 中文标注（方法）：`indexCapacityBytes`，参数：无；用途：执行索引、capacity、字节数据相关逻辑。
            private int indexCapacityBytes() {
                return indexRange.length;
            }
        }
    }

}
