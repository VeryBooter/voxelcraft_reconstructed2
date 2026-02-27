package dev.voxelcraft.client.render;

import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.BlockDef;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.ChunkPos;
import dev.voxelcraft.core.world.Section;
import dev.voxelcraft.core.world.World;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * 中文说明：区块网格构建器：负责快照采样、面生成、贪心合并以及 GPU 上传数据打包。
 */

// 中文标注（类）：`ChunkMesher`，职责：封装区块、mesher相关逻辑。
public final class ChunkMesher {
    // 中文标注（字段）：`GPU_VERTEX_WORDS`，含义：用于表示GPU、顶点、字数组。
    public static final int GPU_VERTEX_WORDS = 4; // meaning
    // 中文标注（字段）：`GPU_VERTEX_STRIDE_BYTES`，含义：用于表示GPU、顶点、步长、字节数据。
    public static final int GPU_VERTEX_STRIDE_BYTES = GPU_VERTEX_WORDS * Integer.BYTES; // meaning
    // 中文标注（字段）：`GPU_COLOR_OFFSET_BYTES`，含义：用于表示GPU、颜色、偏移、字节数据。
    public static final long GPU_COLOR_OFFSET_BYTES = 3L * Float.BYTES; // meaning

    // 中文标注（字段）：`VERTICAL_RANGE_BELOW`，含义：用于表示垂直、范围、below。
    private static final int VERTICAL_RANGE_BELOW = 96; // meaning
    // 中文标注（字段）：`VERTICAL_RANGE_ABOVE`，含义：用于表示垂直、范围、above。
    private static final int VERTICAL_RANGE_ABOVE = 192; // meaning
    // 中文标注（字段）：`LOD_LEVEL_FULL`，含义：用于表示细节层级、级别、full。
    private static final int LOD_LEVEL_FULL = 0; // meaning
    // 中文标注（字段）：`LOD_LEVEL_HEIGHTFIELD_2X2`，含义：用于表示细节层级、级别、heightfield、2、X坐标、2。
    private static final int LOD_LEVEL_HEIGHTFIELD_2X2 = 1; // meaning
    // 中文标注（字段）：`LOD_CELL_SIZE`，含义：用于表示细节层级、cell、大小。
    private static final int LOD_CELL_SIZE = 2; // meaning
    // 中文标注（字段）：`NATIVE_LITTLE_ENDIAN`，含义：用于表示native、little、endian。
    private static final boolean NATIVE_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN; // meaning
    // 中文标注（字段）：`EMPTY_MASK`，含义：用于表示empty、掩码。
    private static final int EMPTY_MASK = 0; // meaning

    // 中文标注（字段）：`SNAPSHOT_XZ`，含义：用于表示快照、xz。
    private static final int SNAPSHOT_XZ = Section.SIZE + 2; // meaning
    // 中文标注（字段）：`SNAPSHOT_PLANE`，含义：用于表示快照、plane。
    private static final int SNAPSHOT_PLANE = SNAPSHOT_XZ * SNAPSHOT_XZ; // meaning
    // 中文标注（字段）：`SNAPSHOT_CHUNK_INDEX_LUT`，含义：用于表示快照、区块、索引、lut。
    private static final int[] SNAPSHOT_CHUNK_INDEX_LUT = buildSnapshotChunkIndexLut(); // meaning
    // 中文标注（字段）：`SNAPSHOT_LOCAL_COORD_LUT`，含义：用于表示快照、局部、coord、lut。
    private static final int[] SNAPSHOT_LOCAL_COORD_LUT = buildSnapshotLocalCoordLut(); // meaning
    // 中文标注（字段）：`COLOR_GRASS`，含义：用于表示颜色、草地。
    private static final Color COLOR_GRASS = new Color(96, 170, 82); // meaning
    // 中文标注（字段）：`COLOR_DIRT`，含义：用于表示颜色、泥土。
    private static final Color COLOR_DIRT = new Color(127, 94, 66); // meaning
    // 中文标注（字段）：`COLOR_STONE`，含义：用于表示颜色、石头。
    private static final Color COLOR_STONE = new Color(134, 138, 145); // meaning
    // 中文标注（字段）：`COLOR_SAND`，含义：用于表示颜色、沙子。
    private static final Color COLOR_SAND = new Color(214, 198, 148); // meaning
    // 中文标注（字段）：`COLOR_WOOD`，含义：用于表示颜色、木头。
    private static final Color COLOR_WOOD = new Color(132, 94, 57); // meaning
    // 中文标注（字段）：`COLOR_LEAVES`，含义：用于表示颜色、树叶。
    private static final Color COLOR_LEAVES = new Color(76, 140, 72); // meaning
    // 中文标注（字段）：`COLOR_FALLBACK`，含义：用于表示颜色、fallback。
    private static final Color COLOR_FALLBACK = new Color(215, 103, 60); // meaning

    // 中文标注（字段）：`cachedWorldVersion`，含义：用于表示缓存、世界、版本。
    private long cachedWorldVersion = Long.MIN_VALUE; // meaning
    // 中文标注（字段）：`cachedMinY`，含义：用于表示缓存、最小、Y坐标。
    private int cachedMinY = Integer.MIN_VALUE; // meaning
    // 中文标注（字段）：`cachedMaxY`，含义：用于表示缓存、最大、Y坐标。
    private int cachedMaxY = Integer.MAX_VALUE; // meaning
    // 中文标注（字段）：`cachedMesh`，含义：用于表示缓存、网格。
    private Mesh cachedMesh = new Mesh(List.of()); // meaning
    // 中文标注（字段）：`gpuScratch`，含义：用于表示GPU、临时工作区。
    private final ThreadLocal<MeshBuildScratch> gpuScratch = ThreadLocal.withInitial(MeshBuildScratch::new); // meaning
    // 中文标注（字段）：`snapshotBlockPool`，含义：用于表示快照、方块、池。
    private final SnapshotBlockArrayPool snapshotBlockPool = new SnapshotBlockArrayPool(4); // meaning

    // 中文标注（方法）：`build`，参数：worldView、centerY；用途：构建或创建构建。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    // 中文标注（参数）：`centerY`，含义：用于表示center、Y坐标。
    public Mesh build(ClientWorldView worldView, double centerY) {
        // 中文标注（局部变量）：`worldVersion`，含义：用于表示世界、版本。
        long worldVersion = worldView.blockUpdateVersion(); // meaning
        // 中文标注（局部变量）：`minY`，含义：用于表示最小、Y坐标。
        int minY = Math.max(World.MIN_Y, (int) Math.floor(centerY) - VERTICAL_RANGE_BELOW); // meaning
        // 中文标注（局部变量）：`maxY`，含义：用于表示最大、Y坐标。
        int maxY = Math.min(World.MAX_Y, (int) Math.floor(centerY) + VERTICAL_RANGE_ABOVE); // meaning

        if (worldVersion == cachedWorldVersion && minY == cachedMinY && maxY == cachedMaxY) {
            return cachedMesh;
        }

        // 中文标注（局部变量）：`chunkBatches`，含义：用于表示区块、batches。
        List<Mesh.ChunkBatch> chunkBatches = new ArrayList<>(); // meaning
        // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
        for (Chunk chunk : worldView.loadedChunks()) {
            // 中文标注（局部变量）：`chunkBaseX`，含义：用于表示区块、base、X坐标。
            int chunkBaseX = chunk.pos().x() * Section.SIZE; // meaning
            // 中文标注（局部变量）：`chunkBaseZ`，含义：用于表示区块、base、Z坐标。
            int chunkBaseZ = chunk.pos().z() * Section.SIZE; // meaning
            // 中文标注（局部变量）：`facesForChunk`，含义：用于表示面集合、for、区块。
            List<Mesh.Face> facesForChunk = new ArrayList<>(); // meaning
            // 中文标注（局部变量）：`chunkYRange`，含义：用于表示区块、yrange。
            int[] chunkYRange = {Integer.MAX_VALUE, Integer.MIN_VALUE}; // meaning

            // 中文标注（Lambda参数）：`localX`，含义：用于表示局部、X坐标。
            // 中文标注（Lambda参数）：`y`，含义：用于表示Y坐标。
            // 中文标注（Lambda参数）：`localZ`，含义：用于表示局部、Z坐标。
            // 中文标注（Lambda参数）：`block`，含义：用于表示方块。
            chunk.forEachNonAirInRange(minY, maxY, (localX, y, localZ, block) -> {
                // 中文标注（局部变量）：`worldX`，含义：用于表示世界、X坐标。
                int worldX = chunkBaseX + localX; // meaning
                // 中文标注（局部变量）：`worldZ`，含义：用于表示世界、Z坐标。
                int worldZ = chunkBaseZ + localZ; // meaning
                if (block != Blocks.AIR) {
                    if (y < chunkYRange[0]) {
                        chunkYRange[0] = y;
                    }
                    if (y > chunkYRange[1]) {
                        chunkYRange[1] = y;
                    }
                }
                addVisibleFaces(worldView, facesForChunk, block, worldX, y, worldZ);
            });

            if (!facesForChunk.isEmpty()) {
                // 中文标注（局部变量）：`chunkMinY`，含义：用于表示区块、最小、Y坐标。
                double chunkMinY = chunkYRange[0] == Integer.MAX_VALUE ? minY : chunkYRange[0]; // meaning
                // 中文标注（局部变量）：`chunkMaxY`，含义：用于表示区块、最大、Y坐标。
                double chunkMaxY = chunkYRange[1] == Integer.MIN_VALUE ? (minY + 1.0) : (chunkYRange[1] + 1.0); // meaning
                chunkBatches.add(new Mesh.ChunkBatch(chunkBaseX, chunkBaseZ, chunkMinY, chunkMaxY, facesForChunk));
            }
        }

        cachedMesh = new Mesh(chunkBatches);
        cachedWorldVersion = worldVersion;
        cachedMinY = minY;
        cachedMaxY = maxY;
        return cachedMesh;
    }

    // 中文标注（方法）：`gpuMinY`，参数：centerY；用途：执行GPU、最小、Y坐标相关逻辑。
    // 中文标注（参数）：`centerY`，含义：用于表示center、Y坐标。
    public int gpuMinY(double centerY) {
        return Math.max(World.MIN_Y, (int) Math.floor(centerY) - VERTICAL_RANGE_BELOW);
    }

    // 中文标注（方法）：`gpuMaxY`，参数：centerY；用途：执行GPU、最大、Y坐标相关逻辑。
    // 中文标注（参数）：`centerY`，含义：用于表示center、Y坐标。
    public int gpuMaxY(double centerY) {
        return Math.min(World.MAX_Y, (int) Math.floor(centerY) + VERTICAL_RANGE_ABOVE);
    }

    // 中文标注（方法）：`captureChunkSnapshot`，参数：worldView、chunk、minY、maxY；用途：构建或创建capture、区块、快照。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    // 中文标注（参数）：`chunk`，含义：用于表示区块。
    // 中文标注（参数）：`minY`，含义：用于表示最小、Y坐标。
    // 中文标注（参数）：`maxY`，含义：用于表示最大、Y坐标。
    public ChunkSnapshot captureChunkSnapshot(ClientWorldView worldView, Chunk chunk, int minY, int maxY) {
        // 中文标注（局部变量）：`clampedMinY`，含义：用于表示clamped、最小、Y坐标。
        int clampedMinY = Math.max(World.MIN_Y, minY); // meaning
        // 中文标注（局部变量）：`clampedMaxY`，含义：用于表示clamped、最大、Y坐标。
        int clampedMaxY = Math.min(World.MAX_Y, maxY); // meaning
        if (clampedMaxY < clampedMinY) {
            clampedMaxY = clampedMinY;
        }

        // 中文标注（局部变量）：`height`，含义：用于表示高度。
        int height = clampedMaxY - clampedMinY + 1; // meaning
        // 中文标注（局部变量）：`expandedHeight`，含义：用于表示expanded、高度。
        int expandedHeight = height + 2; // meaning
        // 中文标注（局部变量）：`blockCount`，含义：用于表示方块、数量。
        int blockCount = SNAPSHOT_XZ * SNAPSHOT_XZ * expandedHeight; // meaning
        // 中文标注（局部变量）：`blocks`，含义：用于表示方块集合。
        Block[] blocks = snapshotBlockPool.acquire(blockCount); // meaning
        try {
            // 中文标注（局部变量）：`scratch`，含义：用于表示临时工作区。
            MeshBuildScratch scratch = gpuScratch.get(); // meaning
            // 中文标注（局部变量）：`snapshotNeighbors`，含义：用于表示快照、邻居集合。
            Chunk[] snapshotNeighbors = scratch.snapshotNeighbors; // meaning
            // 中文标注（局部变量）：`snapshotNeighborSections`，含义：用于表示快照、邻居、sections。
            Section[] snapshotNeighborSections = scratch.snapshotNeighborSections; // meaning
            // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
            int chunkX = chunk.pos().x(); // meaning
            // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
            int chunkZ = chunk.pos().z(); // meaning
            // 中文标注（局部变量）：`neighborDz`，含义：用于表示邻居、dz。
            for (int neighborDz = -1; neighborDz <= 1; neighborDz++) { // meaning
                // 中文标注（局部变量）：`neighborDx`，含义：用于表示邻居、dx。
                for (int neighborDx = -1; neighborDx <= 1; neighborDx++) { // meaning
                    // 中文标注（局部变量）：`neighborIndex`，含义：用于表示邻居、索引。
                    int neighborIndex = (neighborDz + 1) * 3 + (neighborDx + 1); // meaning
                    snapshotNeighbors[neighborIndex] = worldView.getChunk(chunkX + neighborDx, chunkZ + neighborDz);
                }
            }

            // 中文标注（局部变量）：`exY`，含义：用于表示ex、Y坐标。
            for (int exY = 0; exY < expandedHeight; exY++) { // meaning
                // 中文标注（局部变量）：`worldY`，含义：用于表示世界、Y坐标。
                int worldY = clampedMinY + exY - 1; // meaning
                // 中文标注（局部变量）：`planeBase`，含义：用于表示plane、base。
                int planeBase = exY * SNAPSHOT_PLANE; // meaning
                // 超出世界合法高度的平面一律视为空气；这里是 snapshot 的硬边界。
                if (worldY < World.MIN_Y || worldY > World.MAX_Y) {
                    Arrays.fill(blocks, planeBase, planeBase + SNAPSHOT_PLANE, Blocks.AIR);
                    continue;
                }

                // 中文标注（局部变量）：`sectionY`，含义：用于表示分段、Y坐标。
                int sectionY = Math.floorDiv(worldY, Section.SIZE); // meaning
                // 中文标注（局部变量）：`localY`，含义：用于表示局部、Y坐标。
                int localY = Math.floorMod(worldY, Section.SIZE); // meaning
                // 中文标注（局部变量）：`i`，含义：用于表示i。
                for (int i = 0; i < snapshotNeighbors.length; i++) { // meaning
                    // 中文标注（局部变量）：`neighborChunk`，含义：用于表示邻居、区块。
                    Chunk neighborChunk = snapshotNeighbors[i]; // meaning
                    snapshotNeighborSections[i] = neighborChunk == null ? null : neighborChunk.sectionOrNull(sectionY);
                }

                // 中文标注（局部变量）：`exZ`，含义：用于表示ex、Z坐标。
                for (int exZ = 0; exZ < SNAPSHOT_XZ; exZ++) { // meaning
                    // 中文标注（局部变量）：`rowBase`，含义：用于表示row、base。
                    int rowBase = planeBase + exZ * SNAPSHOT_XZ; // meaning
                    // 中文标注（局部变量）：`chunkZIndex`，含义：用于表示区块、zindex。
                    int chunkZIndex = SNAPSHOT_CHUNK_INDEX_LUT[exZ]; // meaning
                    // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
                    int localZ = SNAPSHOT_LOCAL_COORD_LUT[exZ]; // meaning
                    // 中文标注（局部变量）：`neighborRowBase`，含义：用于表示邻居、row、base。
                    int neighborRowBase = chunkZIndex * 3; // meaning
                    // 中文标注（局部变量）：`exX`，含义：用于表示ex、X坐标。
                    for (int exX = 0; exX < SNAPSHOT_XZ; exX++) { // meaning
                        // 中文标注（局部变量）：`chunkXIndex`，含义：用于表示区块、xindex。
                        int chunkXIndex = SNAPSHOT_CHUNK_INDEX_LUT[exX]; // meaning
                        // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
                        int localX = SNAPSHOT_LOCAL_COORD_LUT[exX]; // meaning
                        // 中文标注（局部变量）：`neighborIndex`，含义：用于表示邻居、索引。
                        int neighborIndex = neighborRowBase + chunkXIndex; // meaning
                        // 中文标注（局部变量）：`sourceChunk`，含义：用于表示source、区块。
                        Chunk sourceChunk = snapshotNeighbors[neighborIndex]; // meaning
                        // 中文标注（局部变量）：`sourceSection`，含义：用于表示source、分段。
                        Section sourceSection = snapshotNeighborSections[neighborIndex]; // meaning
                        // 区分“缺失 chunk”和“chunk 存在但 section 缺失”：
                        // 1) 缺失 chunk：与 CPU 路径 worldView.peekBlock 一致，按 AIR 处理；
                        // 2) section 缺失：遵守隐式地层规则，深层默认 STONE。
                        blocks[rowBase + exX] = sourceSection == null
                            ? (sourceChunk != null && worldY < World.DEFAULT_SOLID_BELOW_Y ? Blocks.STONE : Blocks.AIR)
                            : sourceSection.getBlock(localX, localY, localZ);
                    }
                }
            }

            return new ChunkSnapshot(chunk.pos(), chunk.version(), clampedMinY, clampedMaxY, blocks);
        // 中文标注（异常参数）：`exception`，含义：用于表示exception。
        } catch (RuntimeException exception) {
            snapshotBlockPool.release(blocks);
            throw exception;
        // 中文标注（异常参数）：`exception`，含义：用于表示exception。
        } catch (Error exception) {
            snapshotBlockPool.release(blocks);
            throw exception;
        }
    }

    // 中文标注（方法）：`buildChunkMesh`，参数：snapshot、bufferPool；用途：构建或创建构建、区块、网格。
    // 中文标注（参数）：`snapshot`，含义：用于表示快照。
    // 中文标注（参数）：`bufferPool`，含义：用于表示缓冲区、池。
    public ChunkMeshData buildChunkMesh(ChunkSnapshot snapshot, DirectByteBufferPool bufferPool) {
        return buildChunkMesh(snapshot, bufferPool, LOD_LEVEL_FULL);
    }

    // 中文标注（方法）：`buildChunkMesh`，参数：snapshot、bufferPool、lodLevel；用途：构建或创建构建、区块、网格。
    // 中文标注（参数）：`snapshot`，含义：用于表示快照。
    // 中文标注（参数）：`bufferPool`，含义：用于表示缓冲区、池。
    // 中文标注（参数）：`lodLevel`，含义：用于表示细节层级、级别。
    public ChunkMeshData buildChunkMesh(ChunkSnapshot snapshot, DirectByteBufferPool bufferPool, int lodLevel) {
        try {
            // 中文标注（局部变量）：`scratch`，含义：用于表示临时工作区。
            MeshBuildScratch scratch = gpuScratch.get(); // meaning
            // 中文标注（局部变量）：`vertices`，含义：用于表示顶点集合。
            PackedVertexBuilder vertices = scratch.vertices; // meaning
            // 中文标注（局部变量）：`indices`，含义：用于表示索引集合。
            IntArrayBuilder indices = scratch.indices; // meaning
            // 中文标注（局部变量）：`bounds`，含义：用于表示bounds。
            BoundsAccumulator bounds = scratch.bounds; // meaning
            vertices.reset();
            indices.reset();
            bounds.reset();

            // 中文标注（局部变量）：`chunkBaseX`，含义：用于表示区块、base、X坐标。
            int chunkBaseX = snapshot.pos().x() * Section.SIZE; // meaning
            // 中文标注（局部变量）：`chunkBaseZ`，含义：用于表示区块、base、Z坐标。
            int chunkBaseZ = snapshot.pos().z() * Section.SIZE; // meaning
            // 中文标注（局部变量）：`expandedHeight`，含义：用于表示expanded、高度。
            int expandedHeight = snapshot.expandedHeight(); // meaning
            // 中文标注（局部变量）：`snapshotHeight`，含义：用于表示快照、高度。
            int snapshotHeight = snapshot.height(); // meaning

            if (snapshotHeight > 0) {
                if (lodLevel <= LOD_LEVEL_FULL) {
                    // 中文标注（局部变量）：`horizontalMask`，含义：用于表示水平、掩码。
                    int[] horizontalMask = scratch.horizontalMask(); // meaning
                    // 中文标注（局部变量）：`verticalMask`，含义：用于表示垂直、掩码。
                    int[] verticalMask = scratch.verticalMask(snapshotHeight); // meaning

                    buildTopBottomGreedy(snapshot, expandedHeight, chunkBaseX, chunkBaseZ, vertices, indices, bounds, horizontalMask, true);
                    buildTopBottomGreedy(snapshot, expandedHeight, chunkBaseX, chunkBaseZ, vertices, indices, bounds, horizontalMask, false);
                    buildNorthSouthGreedy(snapshot, expandedHeight, chunkBaseX, chunkBaseZ, vertices, indices, bounds, verticalMask, true);
                    buildNorthSouthGreedy(snapshot, expandedHeight, chunkBaseX, chunkBaseZ, vertices, indices, bounds, verticalMask, false);
                    buildWestEastGreedy(snapshot, expandedHeight, chunkBaseX, chunkBaseZ, vertices, indices, bounds, verticalMask, true);
                    buildWestEastGreedy(snapshot, expandedHeight, chunkBaseX, chunkBaseZ, vertices, indices, bounds, verticalMask, false);
                } else {
                    buildHeightfieldLodMesh2x2(snapshot, expandedHeight, chunkBaseX, chunkBaseZ, vertices, indices, bounds, scratch);
                }
            }

            if (indices.size() == 0) {
                // 中文标注（局部变量）：`chunkMinX`，含义：用于表示区块、最小、X坐标。
                double chunkMinX = chunkBaseX; // meaning
                // 中文标注（局部变量）：`chunkMinZ`，含义：用于表示区块、最小、Z坐标。
                double chunkMinZ = chunkBaseZ; // meaning
                // 中文标注（局部变量）：`chunkMaxX`，含义：用于表示区块、最大、X坐标。
                double chunkMaxX = chunkBaseX + Section.SIZE; // meaning
                // 中文标注（局部变量）：`chunkMaxZ`，含义：用于表示区块、最大、Z坐标。
                double chunkMaxZ = chunkBaseZ + Section.SIZE; // meaning
                return new ChunkMeshData(
                    snapshot.pos(),
                    snapshot.version(),
                    lodLevel,
                    bandKey(snapshot.minY(), snapshot.maxY()),
                    null,
                    0,
                    null,
                    0,
                    0,
                    0,
                    chunkMinX,
                    snapshot.minY(),
                    chunkMinZ,
                    chunkMaxX,
                    snapshot.maxY() + 1.0,
                    chunkMaxZ
                );
            }

            // 中文标注（局部变量）：`vertexByteCount`，含义：用于表示顶点、字节、数量。
            int vertexByteCount = vertices.wordCount() * Integer.BYTES; // meaning
            // 中文标注（局部变量）：`indexByteCount`，含义：用于表示索引、字节、数量。
            int indexByteCount = indices.size() * Integer.BYTES; // meaning
            // 中文标注（局部变量）：`vertexBytes`，含义：用于表示顶点、字节数据。
            ByteBuffer vertexBytes = null; // meaning
            // 中文标注（局部变量）：`indexBytes`，含义：用于表示索引、字节数据。
            ByteBuffer indexBytes = null; // meaning
            try {
                vertexBytes = bufferPool.acquire(vertexByteCount);
                indexBytes = bufferPool.acquire(indexByteCount);
                // 中文标注（局部变量）：`vertexInts`，含义：用于表示顶点、ints。
                IntBuffer vertexInts = vertexBytes.asIntBuffer(); // meaning
                vertexInts.put(vertices.data, 0, vertices.wordCount());
                vertexBytes.position(0);
                vertexBytes.limit(vertexByteCount);

                // 中文标注（局部变量）：`indexInts`，含义：用于表示索引、ints。
                IntBuffer indexInts = indexBytes.asIntBuffer(); // meaning
                indexInts.put(indices.data, 0, indices.size());
                indexBytes.position(0);
                indexBytes.limit(indexByteCount);
                return new ChunkMeshData(
                    snapshot.pos(),
                    snapshot.version(),
                    lodLevel,
                    bandKey(snapshot.minY(), snapshot.maxY()),
                    vertexBytes,
                    vertexByteCount,
                    indexBytes,
                    indexByteCount,
                    indices.size(),
                    indices.size() / 3,
                    bounds.minX,
                    bounds.minY,
                    bounds.minZ,
                    bounds.maxX,
                    bounds.maxY,
                    bounds.maxZ
                );
            // 中文标注（异常参数）：`exception`，含义：用于表示exception。
            } catch (RuntimeException exception) {
                bufferPool.release(vertexBytes);
                bufferPool.release(indexBytes);
                throw exception;
            // 中文标注（异常参数）：`exception`，含义：用于表示exception。
            } catch (Error exception) {
                bufferPool.release(vertexBytes);
                bufferPool.release(indexBytes);
                throw exception;
            }
        } finally {
            snapshot.releaseBlocks(snapshotBlockPool);
        }
    }

    // 中文标注（方法）：`discardChunkSnapshot`，参数：snapshot；用途：执行discard、区块、快照相关逻辑。
    // 中文标注（参数）：`snapshot`，含义：用于表示快照。
    void discardChunkSnapshot(ChunkSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshot.releaseBlocks(snapshotBlockPool);
    }

    // 中文标注（方法）：`buildHeightfieldLodMesh2x2`，参数：snapshot、expandedHeight、chunkBaseX、chunkBaseZ、vertices、indices、bounds、scratch；用途：构建或创建构建、heightfield、细节层级、网格、2、X坐标、2。
    private static void buildHeightfieldLodMesh2x2(
        // 中文标注（参数）：`snapshot`，含义：用于表示快照。
        ChunkSnapshot snapshot,
        // 中文标注（参数）：`expandedHeight`，含义：用于表示expanded、高度。
        int expandedHeight,
        // 中文标注（参数）：`chunkBaseX`，含义：用于表示区块、base、X坐标。
        int chunkBaseX,
        // 中文标注（参数）：`chunkBaseZ`，含义：用于表示区块、base、Z坐标。
        int chunkBaseZ,
        // 中文标注（参数）：`vertices`，含义：用于表示顶点集合。
        PackedVertexBuilder vertices,
        // 中文标注（参数）：`indices`，含义：用于表示索引集合。
        IntArrayBuilder indices,
        // 中文标注（参数）：`bounds`，含义：用于表示bounds。
        BoundsAccumulator bounds,
        // 中文标注（参数）：`scratch`，含义：用于表示临时工作区。
        MeshBuildScratch scratch
    ) {
        // 中文标注（局部变量）：`columnHeights`，含义：用于表示column、heights。
        int[] columnHeights = scratch.lodColumnHeights(); // meaning
        // 中文标注（局部变量）：`columnBlocks`，含义：用于表示column、方块集合。
        Block[] columnBlocks = scratch.lodColumnBlocks(); // meaning
        // 中文标注（局部变量）：`columnCount`，含义：用于表示column、数量。
        int columnCount = Section.SIZE * Section.SIZE; // meaning
        Arrays.fill(columnHeights, 0, columnCount, Integer.MIN_VALUE);
        Arrays.fill(columnBlocks, 0, columnCount, null);

        // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
        for (int localZ = 0; localZ < Section.SIZE; localZ++) { // meaning
            // 中文标注（局部变量）：`exZ`，含义：用于表示ex、Z坐标。
            int exZ = localZ + 1; // meaning
            // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
            for (int localX = 0; localX < Section.SIZE; localX++) { // meaning
                // 中文标注（局部变量）：`exX`，含义：用于表示ex、X坐标。
                int exX = localX + 1; // meaning
                // 中文标注（局部变量）：`columnIndex`，含义：用于表示column、索引。
                int columnIndex = localZ * Section.SIZE + localX; // meaning
                // 中文标注（局部变量）：`localY`，含义：用于表示局部、Y坐标。
                for (int localY = snapshot.height() - 1; localY >= 0; localY--) { // meaning
                    // 中文标注（局部变量）：`exY`，含义：用于表示ex、Y坐标。
                    int exY = localY + 1; // meaning
                    // 中文标注（局部变量）：`block`，含义：用于表示方块。
                    Block block = snapshot.blockAtExpanded(exX, exY, exZ, expandedHeight); // meaning
                    if (!isSolid(block)) {
                        continue;
                    }
                    columnHeights[columnIndex] = snapshot.minY() + localY;
                    columnBlocks[columnIndex] = block;
                    break;
                }
            }
        }

        // 中文标注（局部变量）：`coarseWidth`，含义：用于表示coarse、宽度。
        int coarseWidth = Math.max(1, Section.SIZE / LOD_CELL_SIZE); // meaning
        // 中文标注（局部变量）：`coarseHeight`，含义：用于表示coarse、高度。
        int coarseHeight = coarseWidth; // meaning
        // 中文标注（局部变量）：`cellHeights`，含义：用于表示cell、heights。
        int[] cellHeights = scratch.lodCellHeights(coarseWidth * coarseHeight); // meaning
        // 中文标注（局部变量）：`cellBlocks`，含义：用于表示cell、方块集合。
        Block[] cellBlocks = scratch.lodCellBlocks(coarseWidth * coarseHeight); // meaning
        Arrays.fill(cellHeights, 0, coarseWidth * coarseHeight, Integer.MIN_VALUE);
        Arrays.fill(cellBlocks, 0, coarseWidth * coarseHeight, null);

        // 中文标注（局部变量）：`cellZ`，含义：用于表示cell、Z坐标。
        for (int cellZ = 0; cellZ < coarseHeight; cellZ++) { // meaning
            // 中文标注（局部变量）：`cellX`，含义：用于表示cell、X坐标。
            for (int cellX = 0; cellX < coarseWidth; cellX++) { // meaning
                // 中文标注（局部变量）：`bestHeight`，含义：用于表示best、高度。
                int bestHeight = Integer.MIN_VALUE; // meaning
                // 中文标注（局部变量）：`bestBlock`，含义：用于表示best、方块。
                Block bestBlock = null; // meaning
                // 中文标注（局部变量）：`startX`，含义：用于表示开始、X坐标。
                int startX = cellX * LOD_CELL_SIZE; // meaning
                // 中文标注（局部变量）：`startZ`，含义：用于表示开始、Z坐标。
                int startZ = cellZ * LOD_CELL_SIZE; // meaning
                // 中文标注（局部变量）：`dz`，含义：用于表示dz。
                for (int dz = 0; dz < LOD_CELL_SIZE; dz++) { // meaning
                    // 中文标注（局部变量）：`dx`，含义：用于表示dx。
                    for (int dx = 0; dx < LOD_CELL_SIZE; dx++) { // meaning
                        // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
                        int localX = startX + dx; // meaning
                        // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
                        int localZ = startZ + dz; // meaning
                        if (localX >= Section.SIZE || localZ >= Section.SIZE) {
                            continue;
                        }
                        // 中文标注（局部变量）：`columnIndex`，含义：用于表示column、索引。
                        int columnIndex = localZ * Section.SIZE + localX; // meaning
                        // 中文标注（局部变量）：`topY`，含义：用于表示顶面、Y坐标。
                        int topY = columnHeights[columnIndex]; // meaning
                        if (topY > bestHeight) {
                            bestHeight = topY;
                            bestBlock = columnBlocks[columnIndex];
                        }
                    }
                }
                // 中文标注（局部变量）：`cellIndex`，含义：用于表示cell、索引。
                int cellIndex = cellZ * coarseWidth + cellX; // meaning
                cellHeights[cellIndex] = bestHeight;
                cellBlocks[cellIndex] = bestBlock;
            }
        }

        // 中文标注（局部变量）：`cellZ`，含义：用于表示cell、Z坐标。
        for (int cellZ = 0; cellZ < coarseHeight; cellZ++) { // meaning
            // 中文标注（局部变量）：`cellX`，含义：用于表示cell、X坐标。
            for (int cellX = 0; cellX < coarseWidth; cellX++) { // meaning
                // 中文标注（局部变量）：`cellIndex`，含义：用于表示cell、索引。
                int cellIndex = cellZ * coarseWidth + cellX; // meaning
                // 中文标注（局部变量）：`topY`，含义：用于表示顶面、Y坐标。
                int topY = cellHeights[cellIndex]; // meaning
                // 中文标注（局部变量）：`block`，含义：用于表示方块。
                Block block = cellBlocks[cellIndex]; // meaning
                if (topY == Integer.MIN_VALUE || !isSolid(block)) {
                    continue;
                }

                // 中文标注（局部变量）：`x0`，含义：用于表示X坐标、0。
                float x0 = chunkBaseX + (cellX * LOD_CELL_SIZE); // meaning
                // 中文标注（局部变量）：`z0`，含义：用于表示Z坐标、0。
                float z0 = chunkBaseZ + (cellZ * LOD_CELL_SIZE); // meaning
                // 中文标注（局部变量）：`x1`，含义：用于表示X坐标、1。
                float x1 = x0 + LOD_CELL_SIZE; // meaning
                // 中文标注（局部变量）：`z1`，含义：用于表示Z坐标、1。
                float z1 = z0 + LOD_CELL_SIZE; // meaning
                // 中文标注（局部变量）：`topPlaneY`，含义：用于表示顶面、plane、Y坐标。
                float topPlaneY = topY + 1.0f; // meaning
                appendQuad(
                    vertices, indices, bounds, gpuPackedColor(block, FaceDirection.UP),
                    x0, topPlaneY, z0,
                    x1, topPlaneY, z0,
                    x1, topPlaneY, z1,
                    x0, topPlaneY, z1
                );

                if (cellX + 1 < coarseWidth) {
                    // 中文标注（局部变量）：`neighborHeight`，含义：用于表示邻居、高度。
                    int neighborHeight = cellHeights[cellIndex + 1]; // meaning
                    if (neighborHeight < topY) {
                        // 中文标注（局部变量）：`y0`，含义：用于表示Y坐标、0。
                        float y0 = neighborHeight + 1.0f; // meaning
                        // 中文标注（局部变量）：`y1`，含义：用于表示Y坐标、1。
                        float y1 = topY + 1.0f; // meaning
                        // 中文标注（局部变量）：`x`，含义：用于表示X坐标。
                        float x = x1; // meaning
                        appendQuad(
                            vertices, indices, bounds, gpuPackedColor(block, FaceDirection.EAST),
                            x, y0, z1,
                            x, y1, z1,
                            x, y1, z0,
                            x, y0, z0
                        );
                    }
                }
                if (cellZ + 1 < coarseHeight) {
                    // 中文标注（局部变量）：`neighborHeight`，含义：用于表示邻居、高度。
                    int neighborHeight = cellHeights[cellIndex + coarseWidth]; // meaning
                    if (neighborHeight < topY) {
                        // 中文标注（局部变量）：`y0`，含义：用于表示Y坐标、0。
                        float y0 = neighborHeight + 1.0f; // meaning
                        // 中文标注（局部变量）：`y1`，含义：用于表示Y坐标、1。
                        float y1 = topY + 1.0f; // meaning
                        // 中文标注（局部变量）：`z`，含义：用于表示Z坐标。
                        float z = z1; // meaning
                        appendQuad(
                            vertices, indices, bounds, gpuPackedColor(block, FaceDirection.SOUTH),
                            x0, y0, z,
                            x0, y1, z,
                            x1, y1, z,
                            x1, y0, z
                        );
                    }
                }
                if (cellX == 0) {
                    // 中文标注（局部变量）：`y0`，含义：用于表示Y坐标、0。
                    float y0 = snapshot.minY(); // meaning
                    // 中文标注（局部变量）：`y1`，含义：用于表示Y坐标、1。
                    float y1 = topY + 1.0f; // meaning
                    // 中文标注（局部变量）：`x`，含义：用于表示X坐标。
                    float x = x0; // meaning
                    appendQuad(
                        vertices, indices, bounds, gpuPackedColor(block, FaceDirection.WEST),
                        x, y0, z0,
                        x, y1, z0,
                        x, y1, z1,
                        x, y0, z1
                    );
                }
                if (cellZ == 0) {
                    // 中文标注（局部变量）：`y0`，含义：用于表示Y坐标、0。
                    float y0 = snapshot.minY(); // meaning
                    // 中文标注（局部变量）：`y1`，含义：用于表示Y坐标、1。
                    float y1 = topY + 1.0f; // meaning
                    // 中文标注（局部变量）：`z`，含义：用于表示Z坐标。
                    float z = z0; // meaning
                    appendQuad(
                        vertices, indices, bounds, gpuPackedColor(block, FaceDirection.NORTH),
                        x1, y0, z,
                        x1, y1, z,
                        x0, y1, z,
                        x0, y0, z
                    );
                }
                if (cellX == coarseWidth - 1) {
                    // 中文标注（局部变量）：`y0`，含义：用于表示Y坐标、0。
                    float y0 = snapshot.minY(); // meaning
                    // 中文标注（局部变量）：`y1`，含义：用于表示Y坐标、1。
                    float y1 = topY + 1.0f; // meaning
                    // 中文标注（局部变量）：`x`，含义：用于表示X坐标。
                    float x = x1; // meaning
                    appendQuad(
                        vertices, indices, bounds, gpuPackedColor(block, FaceDirection.EAST),
                        x, y0, z1,
                        x, y1, z1,
                        x, y1, z0,
                        x, y0, z0
                    );
                }
                if (cellZ == coarseHeight - 1) {
                    // 中文标注（局部变量）：`y0`，含义：用于表示Y坐标、0。
                    float y0 = snapshot.minY(); // meaning
                    // 中文标注（局部变量）：`y1`，含义：用于表示Y坐标、1。
                    float y1 = topY + 1.0f; // meaning
                    // 中文标注（局部变量）：`z`，含义：用于表示Z坐标。
                    float z = z1; // meaning
                    appendQuad(
                        vertices, indices, bounds, gpuPackedColor(block, FaceDirection.SOUTH),
                        x0, y0, z,
                        x0, y1, z,
                        x1, y1, z,
                        x1, y0, z
                    );
                }
            }
        }
    }

    // 中文标注（方法）：`buildTopBottomGreedy`，参数：snapshot、expandedHeight、chunkBaseX、chunkBaseZ、vertices、indices、bounds、mask、topFace；用途：构建或创建构建、顶面、底面、greedy。
    private static void buildTopBottomGreedy(
        // 中文标注（参数）：`snapshot`，含义：用于表示快照。
        ChunkSnapshot snapshot,
        // 中文标注（参数）：`expandedHeight`，含义：用于表示expanded、高度。
        int expandedHeight,
        // 中文标注（参数）：`chunkBaseX`，含义：用于表示区块、base、X坐标。
        int chunkBaseX,
        // 中文标注（参数）：`chunkBaseZ`，含义：用于表示区块、base、Z坐标。
        int chunkBaseZ,
        // 中文标注（参数）：`vertices`，含义：用于表示顶点集合。
        PackedVertexBuilder vertices,
        // 中文标注（参数）：`indices`，含义：用于表示索引集合。
        IntArrayBuilder indices,
        // 中文标注（参数）：`bounds`，含义：用于表示bounds。
        BoundsAccumulator bounds,
        // 中文标注（参数）：`mask`，含义：用于表示掩码。
        int[] mask,
        // 中文标注（参数）：`topFace`，含义：用于表示顶面、面。
        boolean topFace
    ) {
        // 中文标注（局部变量）：`direction`，含义：用于表示direction。
        FaceDirection direction = topFace ? FaceDirection.UP : FaceDirection.DOWN; // meaning
        // 中文标注（局部变量）：`neighborYDelta`，含义：用于表示邻居、ydelta。
        int neighborYDelta = topFace ? 1 : -1; // meaning

        // 中文标注（局部变量）：`localY`，含义：用于表示局部、Y坐标。
        for (int localY = 0; localY < snapshot.height(); localY++) { // meaning
            // 中文标注（局部变量）：`exY`，含义：用于表示ex、Y坐标。
            int exY = localY + 1; // meaning
            // 中文标注（局部变量）：`maskIndex`，含义：用于表示掩码、索引。
            int maskIndex = 0; // meaning
            // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
            for (int localZ = 0; localZ < Section.SIZE; localZ++) { // meaning
                // 中文标注（局部变量）：`exZ`，含义：用于表示ex、Z坐标。
                int exZ = localZ + 1; // meaning
                // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
                for (int localX = 0; localX < Section.SIZE; localX++) { // meaning
                    // 中文标注（局部变量）：`exX`，含义：用于表示ex、X坐标。
                    int exX = localX + 1; // meaning
                    // 中文标注（局部变量）：`block`，含义：用于表示方块。
                    Block block = snapshot.blockAtExpanded(exX, exY, exZ, expandedHeight); // meaning
                    // 中文标注（局部变量）：`neighbor`，含义：用于表示邻居。
                    Block neighbor = snapshot.blockAtExpanded(exX, exY + neighborYDelta, exZ, expandedHeight); // meaning
                    mask[maskIndex++] = faceMask(block, neighbor, direction);
                }
            }

            // 中文标注（局部变量）：`planeY`，含义：用于表示plane、Y坐标。
            int planeY = snapshot.minY() + localY + (topFace ? 1 : 0); // meaning
            // 中文标注（Lambda参数）：`u`，含义：用于表示u。
            // 中文标注（Lambda参数）：`v`，含义：用于表示v。
            // 中文标注（Lambda参数）：`width`，含义：用于表示宽度。
            // 中文标注（Lambda参数）：`height`，含义：用于表示高度。
            // 中文标注（Lambda参数）：`packedColor`，含义：用于表示packed、颜色。
            emitGreedyRectangles(mask, Section.SIZE, Section.SIZE, (u, v, width, height, packedColor) -> {
                // 中文标注（局部变量）：`x0`，含义：用于表示X坐标、0。
                float x0 = chunkBaseX + u; // meaning
                // 中文标注（局部变量）：`y`，含义：用于表示Y坐标。
                float y = planeY; // meaning
                // 中文标注（局部变量）：`z0`，含义：用于表示Z坐标、0。
                float z0 = chunkBaseZ + v; // meaning
                // 中文标注（局部变量）：`x1`，含义：用于表示X坐标、1。
                float x1 = x0 + width; // meaning
                // 中文标注（局部变量）：`z1`，含义：用于表示Z坐标、1。
                float z1 = z0 + height; // meaning
                if (topFace) {
                    appendQuad(
                        vertices, indices, bounds, packedColor,
                        x0, y, z0,
                        x1, y, z0,
                        x1, y, z1,
                        x0, y, z1
                    );
                } else {
                    appendQuad(
                        vertices, indices, bounds, packedColor,
                        x0, y, z1,
                        x1, y, z1,
                        x1, y, z0,
                        x0, y, z0
                    );
                }
            });
        }
    }

    // 中文标注（方法）：`buildNorthSouthGreedy`，参数：snapshot、expandedHeight、chunkBaseX、chunkBaseZ、vertices、indices、bounds、mask、northFace；用途：构建或创建构建、北、南、greedy。
    private static void buildNorthSouthGreedy(
        // 中文标注（参数）：`snapshot`，含义：用于表示快照。
        ChunkSnapshot snapshot,
        // 中文标注（参数）：`expandedHeight`，含义：用于表示expanded、高度。
        int expandedHeight,
        // 中文标注（参数）：`chunkBaseX`，含义：用于表示区块、base、X坐标。
        int chunkBaseX,
        // 中文标注（参数）：`chunkBaseZ`，含义：用于表示区块、base、Z坐标。
        int chunkBaseZ,
        // 中文标注（参数）：`vertices`，含义：用于表示顶点集合。
        PackedVertexBuilder vertices,
        // 中文标注（参数）：`indices`，含义：用于表示索引集合。
        IntArrayBuilder indices,
        // 中文标注（参数）：`bounds`，含义：用于表示bounds。
        BoundsAccumulator bounds,
        // 中文标注（参数）：`mask`，含义：用于表示掩码。
        int[] mask,
        // 中文标注（参数）：`northFace`，含义：用于表示北、面。
        boolean northFace
    ) {
        // 中文标注（局部变量）：`direction`，含义：用于表示direction。
        FaceDirection direction = northFace ? FaceDirection.NORTH : FaceDirection.SOUTH; // meaning
        // 中文标注（局部变量）：`neighborZDelta`，含义：用于表示邻居、zdelta。
        int neighborZDelta = northFace ? -1 : 1; // meaning

        // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
        for (int localZ = 0; localZ < Section.SIZE; localZ++) { // meaning
            // 中文标注（局部变量）：`exZ`，含义：用于表示ex、Z坐标。
            int exZ = localZ + 1; // meaning
            // 中文标注（局部变量）：`maskIndex`，含义：用于表示掩码、索引。
            int maskIndex = 0; // meaning
            // 中文标注（局部变量）：`localY`，含义：用于表示局部、Y坐标。
            for (int localY = 0; localY < snapshot.height(); localY++) { // meaning
                // 中文标注（局部变量）：`exY`，含义：用于表示ex、Y坐标。
                int exY = localY + 1; // meaning
                // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
                for (int localX = 0; localX < Section.SIZE; localX++) { // meaning
                    // 中文标注（局部变量）：`exX`，含义：用于表示ex、X坐标。
                    int exX = localX + 1; // meaning
                    // 中文标注（局部变量）：`block`，含义：用于表示方块。
                    Block block = snapshot.blockAtExpanded(exX, exY, exZ, expandedHeight); // meaning
                    // 中文标注（局部变量）：`neighbor`，含义：用于表示邻居。
                    Block neighbor = snapshot.blockAtExpanded(exX, exY, exZ + neighborZDelta, expandedHeight); // meaning
                    mask[maskIndex++] = faceMask(block, neighbor, direction);
                }
            }

            // 中文标注（局部变量）：`planeZ`，含义：用于表示plane、Z坐标。
            int planeZ = chunkBaseZ + localZ + (northFace ? 0 : 1); // meaning
            // 中文标注（Lambda参数）：`u`，含义：用于表示u。
            // 中文标注（Lambda参数）：`v`，含义：用于表示v。
            // 中文标注（Lambda参数）：`width`，含义：用于表示宽度。
            // 中文标注（Lambda参数）：`height`，含义：用于表示高度。
            // 中文标注（Lambda参数）：`packedColor`，含义：用于表示packed、颜色。
            emitGreedyRectangles(mask, Section.SIZE, snapshot.height(), (u, v, width, height, packedColor) -> {
                // 中文标注（局部变量）：`x0`，含义：用于表示X坐标、0。
                float x0 = chunkBaseX + u; // meaning
                // 中文标注（局部变量）：`y0`，含义：用于表示Y坐标、0。
                float y0 = snapshot.minY() + v; // meaning
                // 中文标注（局部变量）：`z`，含义：用于表示Z坐标。
                float z = planeZ; // meaning
                // 中文标注（局部变量）：`x1`，含义：用于表示X坐标、1。
                float x1 = x0 + width; // meaning
                // 中文标注（局部变量）：`y1`，含义：用于表示Y坐标、1。
                float y1 = y0 + height; // meaning
                if (northFace) {
                    appendQuad(
                        vertices, indices, bounds, packedColor,
                        x1, y0, z,
                        x1, y1, z,
                        x0, y1, z,
                        x0, y0, z
                    );
                } else {
                    appendQuad(
                        vertices, indices, bounds, packedColor,
                        x0, y0, z,
                        x0, y1, z,
                        x1, y1, z,
                        x1, y0, z
                    );
                }
            });
        }
    }

    // 中文标注（方法）：`buildWestEastGreedy`，参数：snapshot、expandedHeight、chunkBaseX、chunkBaseZ、vertices、indices、bounds、mask、westFace；用途：构建或创建构建、西、东、greedy。
    private static void buildWestEastGreedy(
        // 中文标注（参数）：`snapshot`，含义：用于表示快照。
        ChunkSnapshot snapshot,
        // 中文标注（参数）：`expandedHeight`，含义：用于表示expanded、高度。
        int expandedHeight,
        // 中文标注（参数）：`chunkBaseX`，含义：用于表示区块、base、X坐标。
        int chunkBaseX,
        // 中文标注（参数）：`chunkBaseZ`，含义：用于表示区块、base、Z坐标。
        int chunkBaseZ,
        // 中文标注（参数）：`vertices`，含义：用于表示顶点集合。
        PackedVertexBuilder vertices,
        // 中文标注（参数）：`indices`，含义：用于表示索引集合。
        IntArrayBuilder indices,
        // 中文标注（参数）：`bounds`，含义：用于表示bounds。
        BoundsAccumulator bounds,
        // 中文标注（参数）：`mask`，含义：用于表示掩码。
        int[] mask,
        // 中文标注（参数）：`westFace`，含义：用于表示西、面。
        boolean westFace
    ) {
        // 中文标注（局部变量）：`direction`，含义：用于表示direction。
        FaceDirection direction = westFace ? FaceDirection.WEST : FaceDirection.EAST; // meaning
        // 中文标注（局部变量）：`neighborXDelta`，含义：用于表示邻居、xdelta。
        int neighborXDelta = westFace ? -1 : 1; // meaning

        // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
        for (int localX = 0; localX < Section.SIZE; localX++) { // meaning
            // 中文标注（局部变量）：`exX`，含义：用于表示ex、X坐标。
            int exX = localX + 1; // meaning
            // 中文标注（局部变量）：`maskIndex`，含义：用于表示掩码、索引。
            int maskIndex = 0; // meaning
            // 中文标注（局部变量）：`localY`，含义：用于表示局部、Y坐标。
            for (int localY = 0; localY < snapshot.height(); localY++) { // meaning
                // 中文标注（局部变量）：`exY`，含义：用于表示ex、Y坐标。
                int exY = localY + 1; // meaning
                // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
                for (int localZ = 0; localZ < Section.SIZE; localZ++) { // meaning
                    // 中文标注（局部变量）：`exZ`，含义：用于表示ex、Z坐标。
                    int exZ = localZ + 1; // meaning
                    // 中文标注（局部变量）：`block`，含义：用于表示方块。
                    Block block = snapshot.blockAtExpanded(exX, exY, exZ, expandedHeight); // meaning
                    // 中文标注（局部变量）：`neighbor`，含义：用于表示邻居。
                    Block neighbor = snapshot.blockAtExpanded(exX + neighborXDelta, exY, exZ, expandedHeight); // meaning
                    mask[maskIndex++] = faceMask(block, neighbor, direction);
                }
            }

            // 中文标注（局部变量）：`planeX`，含义：用于表示plane、X坐标。
            int planeX = chunkBaseX + localX + (westFace ? 0 : 1); // meaning
            // 中文标注（Lambda参数）：`u`，含义：用于表示u。
            // 中文标注（Lambda参数）：`v`，含义：用于表示v。
            // 中文标注（Lambda参数）：`width`，含义：用于表示宽度。
            // 中文标注（Lambda参数）：`height`，含义：用于表示高度。
            // 中文标注（Lambda参数）：`packedColor`，含义：用于表示packed、颜色。
            emitGreedyRectangles(mask, Section.SIZE, snapshot.height(), (u, v, width, height, packedColor) -> {
                // 中文标注（局部变量）：`x`，含义：用于表示X坐标。
                float x = planeX; // meaning
                // 中文标注（局部变量）：`y0`，含义：用于表示Y坐标、0。
                float y0 = snapshot.minY() + v; // meaning
                // 中文标注（局部变量）：`z0`，含义：用于表示Z坐标、0。
                float z0 = chunkBaseZ + u; // meaning
                // 中文标注（局部变量）：`y1`，含义：用于表示Y坐标、1。
                float y1 = y0 + height; // meaning
                // 中文标注（局部变量）：`z1`，含义：用于表示Z坐标、1。
                float z1 = z0 + width; // meaning
                if (westFace) {
                    appendQuad(
                        vertices, indices, bounds, packedColor,
                        x, y0, z0,
                        x, y1, z0,
                        x, y1, z1,
                        x, y0, z1
                    );
                } else {
                    appendQuad(
                        vertices, indices, bounds, packedColor,
                        x, y0, z1,
                        x, y1, z1,
                        x, y1, z0,
                        x, y0, z0
                    );
                }
            });
        }
    }

    // 中文标注（方法）：`emitGreedyRectangles`，参数：mask、width、height、consumer；用途：执行emit、greedy、rectangles相关逻辑。
    // 中文标注（参数）：`mask`，含义：用于表示掩码。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    // 中文标注（参数）：`consumer`，含义：用于表示consumer。
    private static void emitGreedyRectangles(int[] mask, int width, int height, GreedyRectConsumer consumer) {
        // 中文标注（局部变量）：`v`，含义：用于表示v。
        for (int v = 0; v < height; v++) { // meaning
            // 中文标注（局部变量）：`u`，含义：用于表示u。
            for (int u = 0; u < width; ) { // meaning
                // 中文标注（局部变量）：`index`，含义：用于表示索引。
                int index = v * width + u; // meaning
                // 中文标注（局部变量）：`maskValue`，含义：用于表示掩码、值。
                int maskValue = mask[index]; // meaning
                if (maskValue == EMPTY_MASK) {
                    u++;
                    continue;
                }

                // 中文标注（局部变量）：`rectWidth`，含义：用于表示rect、宽度。
                int rectWidth = 1; // meaning
                while (u + rectWidth < width && mask[index + rectWidth] == maskValue) {
                    rectWidth++;
                }

                // 中文标注（局部变量）：`rectHeight`，含义：用于表示rect、高度。
                int rectHeight = 1; // meaning
                outer:
                while (v + rectHeight < height) {
                    // 中文标注（局部变量）：`rowStart`，含义：用于表示row、开始。
                    int rowStart = (v + rectHeight) * width + u; // meaning
                    // 中文标注（局部变量）：`offset`，含义：用于表示偏移。
                    for (int offset = 0; offset < rectWidth; offset++) { // meaning
                        if (mask[rowStart + offset] != maskValue) {
                            break outer;
                        }
                    }
                    rectHeight++;
                }

                consumer.accept(u, v, rectWidth, rectHeight, maskValue);

                // 中文标注（局部变量）：`clearV`，含义：用于表示clear、v。
                for (int clearV = 0; clearV < rectHeight; clearV++) { // meaning
                    // 中文标注（局部变量）：`rowStart`，含义：用于表示row、开始。
                    int rowStart = (v + clearV) * width + u; // meaning
                    // 中文标注（局部变量）：`clearU`，含义：用于表示clear、u。
                    for (int clearU = 0; clearU < rectWidth; clearU++) { // meaning
                        mask[rowStart + clearU] = EMPTY_MASK;
                    }
                }

                u += rectWidth;
            }
        }
    }

    // 中文标注（方法）：`faceMask`，参数：block、neighbor、direction；用途：执行面、掩码相关逻辑。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    // 中文标注（参数）：`neighbor`，含义：用于表示邻居。
    // 中文标注（参数）：`direction`，含义：用于表示direction。
    private static int faceMask(Block block, Block neighbor, FaceDirection direction) {
        if (!isGpuGreedyRenderableSolid(block) || isGpuGreedyOccluder(neighbor)) {
            return EMPTY_MASK;
        }
        return gpuPackedColor(block, direction);
    }

    // 中文标注（方法）：`isSolid`，参数：block；用途：判断实体是否满足条件。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    private static boolean isSolid(Block block) {
        return block != null && block != Blocks.AIR && block.solid();
    }

    private static boolean isGpuGreedyRenderableSolid(Block block) {
        if (!isSolid(block)) {
            return false;
        }
        BlockDef def = block.def();
        if (def == null) {
            return true;
        }
        return def.meshProfile().mesherTemplate() == BlockDef.MeshProfile.CUBE;
    }

    private static boolean isGpuGreedyOccluder(Block block) {
        if (block == null || block == Blocks.AIR) {
            return false;
        }
        BlockDef def = block.def();
        if (def != null) {
            return def.isFullOccluder() && def.meshProfile().mesherTemplate() == BlockDef.MeshProfile.CUBE;
        }
        return block.solid();
    }

    // 中文标注（方法）：`gpuPackedColor`，参数：block、direction；用途：执行GPU、packed、颜色相关逻辑。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    // 中文标注（参数）：`direction`，含义：用于表示direction。
    private static int gpuPackedColor(Block block, FaceDirection direction) {
        // 中文标注（局部变量）：`base`，含义：用于表示base。
        Color base = colorFor(block); // meaning
        // 中文标注（局部变量）：`brightness`，含义：用于表示亮度。
        float brightness = direction.brightness; // meaning
        // 中文标注（局部变量）：`red`，含义：用于表示red。
        int red = clamp((int) (base.getRed() * brightness)); // meaning
        // 中文标注（局部变量）：`green`，含义：用于表示green。
        int green = clamp((int) (base.getGreen() * brightness)); // meaning
        // 中文标注（局部变量）：`blue`，含义：用于表示blue。
        int blue = clamp((int) (base.getBlue() * brightness)); // meaning
        return packRgba(red, green, blue, 255);
    }

    // 中文标注（方法）：`packRgba`，参数：red、green、blue、alpha；用途：执行pack、颜色值相关逻辑。
    // 中文标注（参数）：`red`，含义：用于表示red。
    // 中文标注（参数）：`green`，含义：用于表示green。
    // 中文标注（参数）：`blue`，含义：用于表示blue。
    // 中文标注（参数）：`alpha`，含义：用于表示alpha。
    private static int packRgba(int red, int green, int blue, int alpha) {
        if (NATIVE_LITTLE_ENDIAN) {
            return (alpha << 24) | (blue << 16) | (green << 8) | red;
        }
        return (red << 24) | (green << 16) | (blue << 8) | alpha;
    }

    // 中文标注（方法）：`appendQuad`，参数：vertices、indices、bounds、packedColor、x0、y0、z0、x1、y1、z1、x2、y2、z2、x3、y3、z3；用途：执行append、quad相关逻辑。
    private static void appendQuad(
        // 中文标注（参数）：`vertices`，含义：用于表示顶点集合。
        PackedVertexBuilder vertices,
        // 中文标注（参数）：`indices`，含义：用于表示索引集合。
        IntArrayBuilder indices,
        // 中文标注（参数）：`bounds`，含义：用于表示bounds。
        BoundsAccumulator bounds,
        // 中文标注（参数）：`packedColor`，含义：用于表示packed、颜色。
        int packedColor,
        // 中文标注（参数）：`x0`，含义：用于表示X坐标、0。
        float x0,
        // 中文标注（参数）：`y0`，含义：用于表示Y坐标、0。
        float y0,
        // 中文标注（参数）：`z0`，含义：用于表示Z坐标、0。
        float z0,
        // 中文标注（参数）：`x1`，含义：用于表示X坐标、1。
        float x1,
        // 中文标注（参数）：`y1`，含义：用于表示Y坐标、1。
        float y1,
        // 中文标注（参数）：`z1`，含义：用于表示Z坐标、1。
        float z1,
        // 中文标注（参数）：`x2`，含义：用于表示X坐标、2。
        float x2,
        // 中文标注（参数）：`y2`，含义：用于表示Y坐标、2。
        float y2,
        // 中文标注（参数）：`z2`，含义：用于表示Z坐标、2。
        float z2,
        // 中文标注（参数）：`x3`，含义：用于表示X坐标、3。
        float x3,
        // 中文标注（参数）：`y3`，含义：用于表示Y坐标、3。
        float y3,
        // 中文标注（参数）：`z3`，含义：用于表示Z坐标、3。
        float z3
    ) {
        // 中文标注（局部变量）：`baseVertex`，含义：用于表示base、顶点。
        int baseVertex = vertices.vertexCount(); // meaning
        vertices.appendVertex(x0, y0, z0, packedColor);
        vertices.appendVertex(x1, y1, z1, packedColor);
        vertices.appendVertex(x2, y2, z2, packedColor);
        vertices.appendVertex(x3, y3, z3, packedColor);

        // 统一 GPU 面 winding 为 CCW（世界空间外向法线）。
        // GpuChunkRenderer 在 view 变换里做了 Z 反射，并通过 glFrontFace(GL_CW) 做补偿；
        // 因此这里必须保持一致的外向 CCW，不能混用/反向，否则会出现地面等面被背面裁剪误删。
        indices.append(baseVertex);
        indices.append(baseVertex + 2);
        indices.append(baseVertex + 1);
        indices.append(baseVertex + 2);
        indices.append(baseVertex);
        indices.append(baseVertex + 3);

        bounds.include(x0, y0, z0);
        bounds.include(x1, y1, z1);
        bounds.include(x2, y2, z2);
        bounds.include(x3, y3, z3);
    }

    private static void addVisibleFaces(
        ClientWorldView worldView,
        List<Mesh.Face> outFaces,
        Block block,
        int x,
        int y,
        int z
    ) {
        if (block == null || block == Blocks.AIR) {
            return;
        }

        BlockDef def = block.def();
        BlockDef.MeshProfile profile = def == null
            ? BlockDef.MeshProfile.CUBE
            : def.meshProfile().mesherTemplate();

        switch (profile) {
            case CUBE -> emitCube(worldView, outFaces, block, def, x, y, z);
            case SLAB_HALF -> emitSlabHalf(worldView, outFaces, block, def, x, y, z);
            case STAIRS -> emitStairs(worldView, outFaces, block, def, x, y, z);
            case WALL -> emitWall(worldView, outFaces, block, def, x, y, z);
            case LAYERED_1_8 -> emitLayered1to8(worldView, outFaces, block, def, x, y, z);
            case PILE_LOW -> emitPileLow(worldView, outFaces, block, def, x, y, z);
            case SURFACE_PATCH -> emitSurfacePatch(worldView, outFaces, block, def, x, y, z);
            case CROSS -> emitCross(outFaces, block, def, x, y, z);
            case CLUSTER -> emitCluster(worldView, outFaces, block, def, x, y, z);
            default -> emitCube(worldView, outFaces, block, def, x, y, z);
        }
    }

    private static void emitCube(
        ClientWorldView worldView,
        List<Mesh.Face> outFaces,
        Block block,
        BlockDef def,
        int x,
        int y,
        int z
    ) {
        for (FaceDirection direction : FaceDirection.values()) {
            if (shouldCullDirection(worldView, def, x, y, z, direction)) {
                continue;
            }
            addDirectionalFace(outFaces, block, def, x, y, z, direction);
        }
    }

    private static void emitSlabHalf(
        ClientWorldView worldView,
        List<Mesh.Face> outFaces,
        Block block,
        BlockDef def,
        int x,
        int y,
        int z
    ) {
        emitAxisAlignedBox(worldView, outFaces, block, def, x, y, z, 0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
    }

    private static void emitStairs(
        ClientWorldView worldView,
        List<Mesh.Face> outFaces,
        Block block,
        BlockDef def,
        int x,
        int y,
        int z
    ) {
        emitAxisAlignedBox(worldView, outFaces, block, def, x, y, z, 0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
        emitAxisAlignedBox(worldView, outFaces, block, def, x, y, z, 0.0, 0.5, 0.0, 1.0, 1.0, 0.5);
    }

    private static void emitWall(
        ClientWorldView worldView,
        List<Mesh.Face> outFaces,
        Block block,
        BlockDef def,
        int x,
        int y,
        int z
    ) {
        emitAxisAlignedBox(worldView, outFaces, block, def, x, y, z, 0.375, 0.0, 0.375, 0.625, 1.0, 0.625);
        if (connectsWallTo(worldView, x, y, z, 0, -1)) {
            emitAxisAlignedBox(worldView, outFaces, block, def, x, y, z, 0.4375, 0.0, 0.0, 0.5625, 0.8125, 0.5);
        }
        if (connectsWallTo(worldView, x, y, z, 0, 1)) {
            emitAxisAlignedBox(worldView, outFaces, block, def, x, y, z, 0.4375, 0.0, 0.5, 0.5625, 0.8125, 1.0);
        }
        if (connectsWallTo(worldView, x, y, z, -1, 0)) {
            emitAxisAlignedBox(worldView, outFaces, block, def, x, y, z, 0.0, 0.0, 0.4375, 0.5, 0.8125, 0.5625);
        }
        if (connectsWallTo(worldView, x, y, z, 1, 0)) {
            emitAxisAlignedBox(worldView, outFaces, block, def, x, y, z, 0.5, 0.0, 0.4375, 1.0, 0.8125, 0.5625);
        }
    }

    private static void emitLayered1to8(
        ClientWorldView worldView,
        List<Mesh.Face> outFaces,
        Block block,
        BlockDef def,
        int x,
        int y,
        int z
    ) {
        emitAxisAlignedBox(worldView, outFaces, block, def, x, y, z, 0.0, 0.0, 0.0, 1.0, 0.125, 1.0);
    }

    private static void emitPileLow(
        ClientWorldView worldView,
        List<Mesh.Face> outFaces,
        Block block,
        BlockDef def,
        int x,
        int y,
        int z
    ) {
        emitAxisAlignedBox(worldView, outFaces, block, def, x, y, z, 0.125, 0.0, 0.125, 0.875, 0.25, 0.875);
    }

    private static void emitSurfacePatch(
        ClientWorldView worldView,
        List<Mesh.Face> outFaces,
        Block block,
        BlockDef def,
        int x,
        int y,
        int z
    ) {
        if (def != null && (def.attachFacesMask() & BlockDef.ATTACH_TOP) == 0 && def.attachFacesMask() != BlockDef.ATTACH_ANY) {
            return;
        }
        if (shouldCullDirection(worldView, def, x, y, z, FaceDirection.UP)) {
            return;
        }
        float yPlane = (float) y + 0.02f;
        addCustomFace(
            outFaces,
            block,
            def,
            FaceDirection.UP,
            x,
            y,
            z,
            new Vec3(x, yPlane, z),
            new Vec3(x + 1.0, yPlane, z),
            new Vec3(x + 1.0, yPlane, z + 1.0),
            new Vec3(x, yPlane, z + 1.0)
        );
    }

    private static void emitCross(
        List<Mesh.Face> outFaces,
        Block block,
        BlockDef def,
        int x,
        int y,
        int z
    ) {
        addCustomFace(
            outFaces,
            block,
            def,
            FaceDirection.UP,
            x,
            y,
            z,
            new Vec3(x, y, z),
            new Vec3(x + 1.0, y, z + 1.0),
            new Vec3(x + 1.0, y + 1.0, z + 1.0),
            new Vec3(x, y + 1.0, z)
        );
        addCustomFace(
            outFaces,
            block,
            def,
            FaceDirection.UP,
            x,
            y,
            z,
            new Vec3(x + 1.0, y, z),
            new Vec3(x, y, z + 1.0),
            new Vec3(x, y + 1.0, z + 1.0),
            new Vec3(x + 1.0, y + 1.0, z)
        );
    }

    private static void emitCluster(
        ClientWorldView worldView,
        List<Mesh.Face> outFaces,
        Block block,
        BlockDef def,
        int x,
        int y,
        int z
    ) {
        emitAxisAlignedBox(worldView, outFaces, block, def, x, y, z, 0.2, 0.0, 0.2, 0.8, 0.7, 0.8);
    }

    private static void emitAxisAlignedBox(
        ClientWorldView worldView,
        List<Mesh.Face> outFaces,
        Block block,
        BlockDef def,
        int x,
        int y,
        int z,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        if (!shouldCullDirection(worldView, def, x, y, z, FaceDirection.UP)) {
            addCustomFace(
                outFaces, block, def, FaceDirection.UP, x, y, z,
                new Vec3(x + minX, y + maxY, z + minZ),
                new Vec3(x + maxX, y + maxY, z + minZ),
                new Vec3(x + maxX, y + maxY, z + maxZ),
                new Vec3(x + minX, y + maxY, z + maxZ)
            );
        }
        if (!shouldCullDirection(worldView, def, x, y, z, FaceDirection.DOWN)) {
            addCustomFace(
                outFaces, block, def, FaceDirection.DOWN, x, y, z,
                new Vec3(x + minX, y + minY, z + maxZ),
                new Vec3(x + maxX, y + minY, z + maxZ),
                new Vec3(x + maxX, y + minY, z + minZ),
                new Vec3(x + minX, y + minY, z + minZ)
            );
        }
        if (!shouldCullDirection(worldView, def, x, y, z, FaceDirection.NORTH)) {
            addCustomFace(
                outFaces, block, def, FaceDirection.NORTH, x, y, z,
                new Vec3(x + maxX, y + minY, z + minZ),
                new Vec3(x + maxX, y + maxY, z + minZ),
                new Vec3(x + minX, y + maxY, z + minZ),
                new Vec3(x + minX, y + minY, z + minZ)
            );
        }
        if (!shouldCullDirection(worldView, def, x, y, z, FaceDirection.SOUTH)) {
            addCustomFace(
                outFaces, block, def, FaceDirection.SOUTH, x, y, z,
                new Vec3(x + minX, y + minY, z + maxZ),
                new Vec3(x + minX, y + maxY, z + maxZ),
                new Vec3(x + maxX, y + maxY, z + maxZ),
                new Vec3(x + maxX, y + minY, z + maxZ)
            );
        }
        if (!shouldCullDirection(worldView, def, x, y, z, FaceDirection.WEST)) {
            addCustomFace(
                outFaces, block, def, FaceDirection.WEST, x, y, z,
                new Vec3(x + minX, y + minY, z + minZ),
                new Vec3(x + minX, y + maxY, z + minZ),
                new Vec3(x + minX, y + maxY, z + maxZ),
                new Vec3(x + minX, y + minY, z + maxZ)
            );
        }
        if (!shouldCullDirection(worldView, def, x, y, z, FaceDirection.EAST)) {
            addCustomFace(
                outFaces, block, def, FaceDirection.EAST, x, y, z,
                new Vec3(x + maxX, y + minY, z + maxZ),
                new Vec3(x + maxX, y + maxY, z + maxZ),
                new Vec3(x + maxX, y + maxY, z + minZ),
                new Vec3(x + maxX, y + minY, z + minZ)
            );
        }
    }

    private static boolean connectsWallTo(ClientWorldView worldView, int x, int y, int z, int dx, int dz) {
        Block neighbor = worldView.peekBlock(x + dx, y, z + dz);
        if (neighbor == null || neighbor == Blocks.AIR) {
            return false;
        }
        BlockDef def = neighbor.def();
        if (def != null) {
            return def.isFullOccluder() || def.collisionKind() == BlockDef.CollisionKind.WALL;
        }
        return neighbor.solid();
    }

    private static void addDirectionalFace(
        List<Mesh.Face> outFaces,
        Block block,
        BlockDef def,
        int x,
        int y,
        int z,
        FaceDirection direction
    ) {
        addCustomFace(
            outFaces,
            block,
            def,
            direction,
            x,
            y,
            z,
            direction.vertex(x, y, z, 0),
            direction.vertex(x, y, z, 1),
            direction.vertex(x, y, z, 2),
            direction.vertex(x, y, z, 3)
        );
    }

    private static void addCustomFace(
        List<Mesh.Face> outFaces,
        Block block,
        BlockDef def,
        FaceDirection direction,
        int x,
        int y,
        int z,
        Vec3 v0,
        Vec3 v1,
        Vec3 v2,
        Vec3 v3
    ) {
        Color blockColor = colorFor(block);
        float detail = detailBrightness(block, direction, x, y, z);
        Color shaded = shade(blockColor, direction.brightness * detail);
        BlockDef.RenderBucket bucket = def == null ? BlockDef.RenderBucket.OPAQUE : def.renderBucket();
        if (bucket == BlockDef.RenderBucket.TRANSLUCENT) {
            shaded = new Color(shaded.getRed(), shaded.getGreen(), shaded.getBlue(), 180);
        }
        boolean needsSorting = def != null && def.needsSorting();
        outFaces.add(new Mesh.Face(v0, v1, v2, v3, shaded, bucket, needsSorting));
    }

    private static boolean shouldCullDirection(
        ClientWorldView worldView,
        BlockDef def,
        int x,
        int y,
        int z,
        FaceDirection direction
    ) {
        Block neighbor = worldView.peekBlock(x + direction.dx, y + direction.dy, z + direction.dz);
        if (neighbor == null || neighbor == Blocks.AIR) {
            return false;
        }
        if (def != null && def.occlusionMode() != BlockDef.OcclusionMode.FULL) {
            return false;
        }
        BlockDef neighborDef = neighbor.def();
        if (neighborDef != null) {
            return neighborDef.isFullOccluder();
        }
        return neighbor.solid();
    }

    // 中文标注（方法）：`snapshotIndex`，参数：x、y、z、expandedHeight；用途：执行快照、索引相关逻辑。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    // 中文标注（参数）：`expandedHeight`，含义：用于表示expanded、高度。
    private static int snapshotIndex(int x, int y, int z, int expandedHeight) {
        return (y * SNAPSHOT_XZ + z) * SNAPSHOT_XZ + x;
    }

    // 中文标注（方法）：`buildSnapshotChunkIndexLut`，参数：无；用途：构建或创建构建、快照、区块、索引、lut。
    private static int[] buildSnapshotChunkIndexLut() {
        // 中文标注（局部变量）：`lut`，含义：用于表示lut。
        int[] lut = new int[SNAPSHOT_XZ]; // meaning
        // 中文标注（局部变量）：`ex`，含义：用于表示ex。
        for (int ex = 0; ex < SNAPSHOT_XZ; ex++) { // meaning
            if (ex == 0) {
                lut[ex] = 0;
            } else if (ex == SNAPSHOT_XZ - 1) {
                lut[ex] = 2;
            } else {
                lut[ex] = 1;
            }
        }
        return lut;
    }

    // 中文标注（方法）：`buildSnapshotLocalCoordLut`，参数：无；用途：构建或创建构建、快照、局部、coord、lut。
    private static int[] buildSnapshotLocalCoordLut() {
        // 中文标注（局部变量）：`lut`，含义：用于表示lut。
        int[] lut = new int[SNAPSHOT_XZ]; // meaning
        // 中文标注（局部变量）：`ex`，含义：用于表示ex。
        for (int ex = 0; ex < SNAPSHOT_XZ; ex++) { // meaning
            if (ex == 0) {
                lut[ex] = Section.SIZE - 1;
            } else if (ex == SNAPSHOT_XZ - 1) {
                lut[ex] = 0;
            } else {
                lut[ex] = ex - 1;
            }
        }
        return lut;
    }

    // 中文标注（方法）：`colorFor`，参数：block；用途：执行颜色、for相关逻辑。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    private static Color colorFor(Block block) {
        if (block == Blocks.GRASS) {
            return COLOR_GRASS;
        }
        if (block == Blocks.DIRT) {
            return COLOR_DIRT;
        }
        if (block == Blocks.STONE) {
            return COLOR_STONE;
        }
        if (block == Blocks.SAND) {
            return COLOR_SAND;
        }
        if (block == Blocks.WOOD) {
            return COLOR_WOOD;
        }
        if (block == Blocks.LEAVES) {
            return COLOR_LEAVES;
        }
        BlockDef def = block.def();
        if (def != null) {
            String material = def.material().toLowerCase();
            String key = def.key().toLowerCase();
            if (material.contains("grass") || key.contains("grass")) {
                return COLOR_GRASS;
            }
            if (material.contains("dirt") || material.contains("soil") || key.contains("dirt")) {
                return COLOR_DIRT;
            }
            if (material.contains("sand") || key.contains("sand")) {
                return COLOR_SAND;
            }
            if (material.contains("wood") || key.contains("wood") || key.contains("log")) {
                return COLOR_WOOD;
            }
            if (material.contains("leaf") || key.contains("leaf")) {
                return COLOR_LEAVES;
            }
            if (material.contains("stone") || material.contains("rock")) {
                return COLOR_STONE;
            }
            int seed = hash(key.hashCode(), material.hashCode(), def.meshProfile().ordinal());
            int red = clamp(80 + (seed & 0x3F));
            int green = clamp(90 + ((seed >>> 6) & 0x3F));
            int blue = clamp(80 + ((seed >>> 12) & 0x3F));
            return new Color(red, green, blue);
        }
        return COLOR_FALLBACK;
    }

    // 中文标注（方法）：`detailBrightness`，参数：block、direction、x、y、z；用途：执行detail、亮度相关逻辑。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    // 中文标注（参数）：`direction`，含义：用于表示direction。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    private static float detailBrightness(Block block, FaceDirection direction, int x, int y, int z) {
        // 中文标注（局部变量）：`hash`，含义：用于表示hash。
        int hash = hash(x, y, z); // meaning
        // 中文标注（局部变量）：`randomJitter`，含义：用于表示random、jitter。
        float randomJitter = 0.90f + ((hash & 15) / 15.0f) * 0.18f; // meaning
        // 中文标注（局部变量）：`stripe`，含义：用于表示stripe。
        float stripe = (((x + z) & 1) == 0) ? 0.96f : 1.02f; // meaning

        BlockDef def = block.def();
        if ((block == Blocks.GRASS || (def != null && def.material().toLowerCase().contains("grass"))) && direction == FaceDirection.UP) {
            return randomJitter * stripe;
        }
        if (block == Blocks.STONE) {
            return randomJitter * 0.98f;
        }
        if (block == Blocks.DIRT || block == Blocks.SAND) {
            return randomJitter * (0.94f + ((y & 1) * 0.04f));
        }
        if (block == Blocks.LEAVES) {
            return randomJitter * 1.04f;
        }
        return randomJitter;
    }

    // 中文标注（方法）：`shade`，参数：color、brightness；用途：执行shade相关逻辑。
    // 中文标注（参数）：`color`，含义：用于表示颜色。
    // 中文标注（参数）：`brightness`，含义：用于表示亮度。
    private static Color shade(Color color, float brightness) {
        // 中文标注（局部变量）：`red`，含义：用于表示red。
        int red = clamp((int) (color.getRed() * brightness)); // meaning
        // 中文标注（局部变量）：`green`，含义：用于表示green。
        int green = clamp((int) (color.getGreen() * brightness)); // meaning
        // 中文标注（局部变量）：`blue`，含义：用于表示blue。
        int blue = clamp((int) (color.getBlue() * brightness)); // meaning
        return new Color(red, green, blue);
    }

    // 中文标注（方法）：`hash`，参数：x、y、z；用途：判断hash是否满足条件。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    private static int hash(int x, int y, int z) {
        // 中文标注（局部变量）：`value`，含义：用于表示值。
        int value = x * 734_287 + y * 912_931 + z * 438_289; // meaning
        value ^= (value >>> 13);
        value *= 1_274_126_177;
        value ^= (value >>> 16);
        return value;
    }

    // 中文标注（方法）：`clamp`，参数：value；用途：执行clamp相关逻辑。
    // 中文标注（参数）：`value`，含义：用于表示值。
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // 中文标注（类）：`ChunkSnapshot`，职责：封装区块、快照相关逻辑。
    public static final class ChunkSnapshot {
        // 中文标注（字段）：`pos`，含义：用于表示位置。
        private final ChunkPos pos; // meaning
        // 中文标注（字段）：`version`，含义：用于表示版本。
        private final long version; // meaning
        // 中文标注（字段）：`minY`，含义：用于表示最小、Y坐标。
        private final int minY; // meaning
        // 中文标注（字段）：`maxY`，含义：用于表示最大、Y坐标。
        private final int maxY; // meaning
        // 中文标注（字段）：`height`，含义：用于表示高度。
        private final int height; // meaning
        // 中文标注（字段）：`blocks`，含义：用于表示方块集合。
        private final Block[] blocks; // meaning

        // 中文标注（构造方法）：`ChunkSnapshot`，参数：pos、version、minY、maxY、blocks；用途：初始化`ChunkSnapshot`实例。
        // 中文标注（参数）：`pos`，含义：用于表示位置。
        // 中文标注（参数）：`version`，含义：用于表示版本。
        // 中文标注（参数）：`minY`，含义：用于表示最小、Y坐标。
        // 中文标注（参数）：`maxY`，含义：用于表示最大、Y坐标。
        // 中文标注（参数）：`blocks`，含义：用于表示方块集合。
        private ChunkSnapshot(ChunkPos pos, long version, int minY, int maxY, Block[] blocks) {
            this.pos = pos;
            this.version = version;
            this.minY = minY;
            this.maxY = maxY;
            this.height = Math.max(0, maxY - minY + 1);
            this.blocks = blocks;
        }

        // 中文标注（方法）：`pos`，参数：无；用途：执行位置相关逻辑。
        public ChunkPos pos() {
            return pos;
        }

        // 中文标注（方法）：`version`，参数：无；用途：执行版本相关逻辑。
        public long version() {
            return version;
        }

        // 中文标注（方法）：`minY`，参数：无；用途：执行最小、Y坐标相关逻辑。
        public int minY() {
            return minY;
        }

        // 中文标注（方法）：`maxY`，参数：无；用途：执行最大、Y坐标相关逻辑。
        public int maxY() {
            return maxY;
        }

        // 中文标注（方法）：`height`，参数：无；用途：执行高度相关逻辑。
        public int height() {
            return height;
        }

        // 中文标注（方法）：`expandedHeight`，参数：无；用途：执行expanded、高度相关逻辑。
        private int expandedHeight() {
            return height + 2;
        }

        // 中文标注（方法）：`blockAtExpanded`，参数：exX、exY、exZ、expandedHeight；用途：执行方块、at、expanded相关逻辑。
        // 中文标注（参数）：`exX`，含义：用于表示ex、X坐标。
        // 中文标注（参数）：`exY`，含义：用于表示ex、Y坐标。
        // 中文标注（参数）：`exZ`，含义：用于表示ex、Z坐标。
        // 中文标注（参数）：`expandedHeight`，含义：用于表示expanded、高度。
        private Block blockAtExpanded(int exX, int exY, int exZ, int expandedHeight) {
            if (exX < 0 || exX >= SNAPSHOT_XZ || exZ < 0 || exZ >= SNAPSHOT_XZ || exY < 0 || exY >= expandedHeight) {
                return Blocks.AIR;
            }
            // 中文标注（局部变量）：`block`，含义：用于表示方块。
            Block block = blocks[snapshotIndex(exX, exY, exZ, expandedHeight)]; // meaning
            return block == null ? Blocks.AIR : block;
        }

        // 中文标注（方法）：`releaseBlocks`，参数：pool；用途：执行release、方块集合相关逻辑。
        // 中文标注（参数）：`pool`，含义：用于表示池。
        private void releaseBlocks(SnapshotBlockArrayPool pool) {
            pool.release(blocks);
        }
    }

    // 中文标注（类）：`ChunkMeshData`，职责：封装区块、网格、数据相关逻辑。
    public static final class ChunkMeshData {
        // 中文标注（字段）：`pos`，含义：用于表示位置。
        private final ChunkPos pos; // meaning
        // 中文标注（字段）：`version`，含义：用于表示版本。
        private final long version; // meaning
        // 中文标注（字段）：`lodLevel`，含义：用于表示细节层级、级别。
        private final int lodLevel; // meaning
        // 中文标注（字段）：`bandKey`，含义：用于表示带、键。
        private final int bandKey; // meaning
        // 中文标注（字段）：`vertexBytes`，含义：用于表示顶点、字节数据。
        private final ByteBuffer vertexBytes; // meaning
        // 中文标注（字段）：`vertexByteCount`，含义：用于表示顶点、字节、数量。
        private final int vertexByteCount; // meaning
        // 中文标注（字段）：`indexBytes`，含义：用于表示索引、字节数据。
        private final ByteBuffer indexBytes; // meaning
        // 中文标注（字段）：`indexByteCount`，含义：用于表示索引、字节、数量。
        private final int indexByteCount; // meaning
        // 中文标注（字段）：`indexCount`，含义：用于表示索引、数量。
        private final int indexCount; // meaning
        // 中文标注（字段）：`triangleCount`，含义：用于表示triangle、数量。
        private final int triangleCount; // meaning
        // 中文标注（字段）：`minX`，含义：用于表示最小、X坐标。
        private final double minX; // meaning
        // 中文标注（字段）：`minY`，含义：用于表示最小、Y坐标。
        private final double minY; // meaning
        // 中文标注（字段）：`minZ`，含义：用于表示最小、Z坐标。
        private final double minZ; // meaning
        // 中文标注（字段）：`maxX`，含义：用于表示最大、X坐标。
        private final double maxX; // meaning
        // 中文标注（字段）：`maxY`，含义：用于表示最大、Y坐标。
        private final double maxY; // meaning
        // 中文标注（字段）：`maxZ`，含义：用于表示最大、Z坐标。
        private final double maxZ; // meaning

        // 中文标注（构造方法）：`ChunkMeshData`，参数：pos、version、lodLevel、bandKey、vertexBytes、vertexByteCount、indexBytes、indexByteCount、indexCount、triangleCount、minX、minY、minZ、maxX、maxY、maxZ；用途：初始化`ChunkMeshData`实例。
        public ChunkMeshData(
            // 中文标注（参数）：`pos`，含义：用于表示位置。
            ChunkPos pos,
            // 中文标注（参数）：`version`，含义：用于表示版本。
            long version,
            // 中文标注（参数）：`lodLevel`，含义：用于表示细节层级、级别。
            int lodLevel,
            // 中文标注（参数）：`bandKey`，含义：用于表示带、键。
            int bandKey,
            // 中文标注（参数）：`vertexBytes`，含义：用于表示顶点、字节数据。
            ByteBuffer vertexBytes,
            // 中文标注（参数）：`vertexByteCount`，含义：用于表示顶点、字节、数量。
            int vertexByteCount,
            // 中文标注（参数）：`indexBytes`，含义：用于表示索引、字节数据。
            ByteBuffer indexBytes,
            // 中文标注（参数）：`indexByteCount`，含义：用于表示索引、字节、数量。
            int indexByteCount,
            // 中文标注（参数）：`indexCount`，含义：用于表示索引、数量。
            int indexCount,
            // 中文标注（参数）：`triangleCount`，含义：用于表示triangle、数量。
            int triangleCount,
            // 中文标注（参数）：`minX`，含义：用于表示最小、X坐标。
            double minX,
            // 中文标注（参数）：`minY`，含义：用于表示最小、Y坐标。
            double minY,
            // 中文标注（参数）：`minZ`，含义：用于表示最小、Z坐标。
            double minZ,
            // 中文标注（参数）：`maxX`，含义：用于表示最大、X坐标。
            double maxX,
            // 中文标注（参数）：`maxY`，含义：用于表示最大、Y坐标。
            double maxY,
            // 中文标注（参数）：`maxZ`，含义：用于表示最大、Z坐标。
            double maxZ
        ) {
            this.pos = pos;
            this.version = version;
            this.lodLevel = lodLevel;
            this.bandKey = bandKey;
            this.vertexBytes = vertexBytes;
            this.vertexByteCount = vertexByteCount;
            this.indexBytes = indexBytes;
            this.indexByteCount = indexByteCount;
            this.indexCount = indexCount;
            this.triangleCount = triangleCount;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        // 中文标注（方法）：`pos`，参数：无；用途：执行位置相关逻辑。
        public ChunkPos pos() {
            return pos;
        }

        // 中文标注（方法）：`version`，参数：无；用途：执行版本相关逻辑。
        public long version() {
            return version;
        }

        // 中文标注（方法）：`vertexBytes`，参数：无；用途：执行顶点、字节数据相关逻辑。
        public ByteBuffer vertexBytes() {
            return vertexBytes;
        }

        // 中文标注（方法）：`lodLevel`，参数：无；用途：执行细节层级、级别相关逻辑。
        public int lodLevel() {
            return lodLevel;
        }

        // 中文标注（方法）：`bandKey`，参数：无；用途：执行带、键相关逻辑。
        public int bandKey() {
            return bandKey;
        }

        // 中文标注（方法）：`vertexByteCount`，参数：无；用途：执行顶点、字节、数量相关逻辑。
        public int vertexByteCount() {
            return vertexByteCount;
        }

        // 中文标注（方法）：`indexBytes`，参数：无；用途：执行索引、字节数据相关逻辑。
        public ByteBuffer indexBytes() {
            return indexBytes;
        }

        // 中文标注（方法）：`indexByteCount`，参数：无；用途：执行索引、字节、数量相关逻辑。
        public int indexByteCount() {
            return indexByteCount;
        }

        // 中文标注（方法）：`indexCount`，参数：无；用途：执行索引、数量相关逻辑。
        public int indexCount() {
            return indexCount;
        }

        // 中文标注（方法）：`triangleCount`，参数：无；用途：执行triangle、数量相关逻辑。
        public int triangleCount() {
            return triangleCount;
        }

        // 中文标注（方法）：`minX`，参数：无；用途：执行最小、X坐标相关逻辑。
        public double minX() {
            return minX;
        }

        // 中文标注（方法）：`minY`，参数：无；用途：执行最小、Y坐标相关逻辑。
        public double minY() {
            return minY;
        }

        // 中文标注（方法）：`minZ`，参数：无；用途：执行最小、Z坐标相关逻辑。
        public double minZ() {
            return minZ;
        }

        // 中文标注（方法）：`maxX`，参数：无；用途：执行最大、X坐标相关逻辑。
        public double maxX() {
            return maxX;
        }

        // 中文标注（方法）：`maxY`，参数：无；用途：执行最大、Y坐标相关逻辑。
        public double maxY() {
            return maxY;
        }

        // 中文标注（方法）：`maxZ`，参数：无；用途：执行最大、Z坐标相关逻辑。
        public double maxZ() {
            return maxZ;
        }

        // 中文标注（方法）：`releaseBuffers`，参数：bufferPool；用途：执行release、buffers相关逻辑。
        // 中文标注（参数）：`bufferPool`，含义：用于表示缓冲区、池。
        public void releaseBuffers(DirectByteBufferPool bufferPool) {
            bufferPool.release(vertexBytes);
            bufferPool.release(indexBytes);
        }
    }

    // 中文标注（接口）：`GreedyRectConsumer`，职责：封装greedy、rect、consumer相关逻辑。
    @FunctionalInterface
    private interface GreedyRectConsumer {
        // 中文标注（方法）：`accept`，参数：u、v、width、height、packedColor；用途：执行accept相关逻辑。
        // 中文标注（参数）：`u`，含义：用于表示u。
        // 中文标注（参数）：`v`，含义：用于表示v。
        // 中文标注（参数）：`width`，含义：用于表示宽度。
        // 中文标注（参数）：`height`，含义：用于表示高度。
        // 中文标注（参数）：`packedColor`，含义：用于表示packed、颜色。
        void accept(int u, int v, int width, int height, int packedColor); // meaning
    }

    // 中文标注（类）：`BoundsAccumulator`，职责：封装bounds、accumulator相关逻辑。
    private static final class BoundsAccumulator {
        // 中文标注（字段）：`minX`，含义：用于表示最小、X坐标。
        private double minX = Double.POSITIVE_INFINITY; // meaning
        // 中文标注（字段）：`minY`，含义：用于表示最小、Y坐标。
        private double minY = Double.POSITIVE_INFINITY; // meaning
        // 中文标注（字段）：`minZ`，含义：用于表示最小、Z坐标。
        private double minZ = Double.POSITIVE_INFINITY; // meaning
        // 中文标注（字段）：`maxX`，含义：用于表示最大、X坐标。
        private double maxX = Double.NEGATIVE_INFINITY; // meaning
        // 中文标注（字段）：`maxY`，含义：用于表示最大、Y坐标。
        private double maxY = Double.NEGATIVE_INFINITY; // meaning
        // 中文标注（字段）：`maxZ`，含义：用于表示最大、Z坐标。
        private double maxZ = Double.NEGATIVE_INFINITY; // meaning

        // 中文标注（方法）：`reset`，参数：无；用途：执行reset相关逻辑。
        private void reset() {
            minX = Double.POSITIVE_INFINITY;
            minY = Double.POSITIVE_INFINITY;
            minZ = Double.POSITIVE_INFINITY;
            maxX = Double.NEGATIVE_INFINITY;
            maxY = Double.NEGATIVE_INFINITY;
            maxZ = Double.NEGATIVE_INFINITY;
        }

        // 中文标注（方法）：`include`，参数：x、y、z；用途：执行include相关逻辑。
        // 中文标注（参数）：`x`，含义：用于表示X坐标。
        // 中文标注（参数）：`y`，含义：用于表示Y坐标。
        // 中文标注（参数）：`z`，含义：用于表示Z坐标。
        private void include(float x, float y, float z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
    }

    // 中文标注（方法）：`bandKey`，参数：minY、maxY；用途：执行带、键相关逻辑。
    // 中文标注（参数）：`minY`，含义：用于表示最小、Y坐标。
    // 中文标注（参数）：`maxY`，含义：用于表示最大、Y坐标。
    private static int bandKey(int minY, int maxY) {
        // 与 GpuChunkRenderer.meshBandKey(...) 保持一致；否则上传阶段会把新 mesh 误判为 stale。
        int worldSpan = World.MAX_Y - World.MIN_Y + 1; // meaning
        if (worldSpan <= 4096) {
            int minOffset = minY - World.MIN_Y; // meaning
            int maxOffset = maxY - World.MIN_Y; // meaning
            if (minOffset >= 0 && minOffset < 4096 && maxOffset >= 0 && maxOffset < 4096) {
                return (minOffset << 12) | maxOffset;
            }
        }
        return ((minY * 31) + 17) * 31 + maxY;
    }

    // 中文标注（类）：`PackedVertexBuilder`，职责：封装packed、顶点、builder相关逻辑。
    private static final class PackedVertexBuilder {
        // 中文标注（字段）：`data`，含义：用于表示数据。
        private int[] data; // meaning
        // 中文标注（字段）：`size`，含义：用于表示大小。
        private int size; // meaning

        // 中文标注（构造方法）：`PackedVertexBuilder`，参数：initialCapacityWords；用途：初始化`PackedVertexBuilder`实例。
        // 中文标注（参数）：`initialCapacityWords`，含义：用于表示initial、capacity、字数组。
        private PackedVertexBuilder(int initialCapacityWords) {
            data = new int[Math.max(16, initialCapacityWords)];
        }

        // 中文标注（方法）：`reset`，参数：无；用途：执行reset相关逻辑。
        private void reset() {
            size = 0;
        }

        // 中文标注（方法）：`vertexCount`，参数：无；用途：执行顶点、数量相关逻辑。
        private int vertexCount() {
            return size / GPU_VERTEX_WORDS;
        }

        // 中文标注（方法）：`wordCount`，参数：无；用途：执行字、数量相关逻辑。
        private int wordCount() {
            return size;
        }

        // 中文标注（方法）：`appendVertex`，参数：x、y、z、packedColor；用途：执行append、顶点相关逻辑。
        // 中文标注（参数）：`x`，含义：用于表示X坐标。
        // 中文标注（参数）：`y`，含义：用于表示Y坐标。
        // 中文标注（参数）：`z`，含义：用于表示Z坐标。
        // 中文标注（参数）：`packedColor`，含义：用于表示packed、颜色。
        private void appendVertex(float x, float y, float z, int packedColor) {
            ensureCapacity(GPU_VERTEX_WORDS);
            data[size++] = Float.floatToRawIntBits(x);
            data[size++] = Float.floatToRawIntBits(y);
            data[size++] = Float.floatToRawIntBits(z);
            data[size++] = packedColor;
        }

        // 中文标注（方法）：`ensureCapacity`，参数：extraWords；用途：执行ensure、capacity相关逻辑。
        // 中文标注（参数）：`extraWords`，含义：用于表示extra、字数组。
        private void ensureCapacity(int extraWords) {
            // 中文标注（局部变量）：`required`，含义：用于表示required。
            int required = size + extraWords; // meaning
            if (required <= data.length) {
                return;
            }
            // 中文标注（局部变量）：`newCapacity`，含义：用于表示new、capacity。
            int newCapacity = data.length; // meaning
            while (newCapacity < required) {
                newCapacity *= 2;
            }
            // 中文标注（局部变量）：`newData`，含义：用于表示new、数据。
            int[] newData = new int[newCapacity]; // meaning
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }

    }

    // 中文标注（类）：`IntArrayBuilder`，职责：封装int、数组、builder相关逻辑。
    private static final class IntArrayBuilder {
        // 中文标注（字段）：`data`，含义：用于表示数据。
        private int[] data; // meaning
        // 中文标注（字段）：`size`，含义：用于表示大小。
        private int size; // meaning

        // 中文标注（构造方法）：`IntArrayBuilder`，参数：initialCapacity；用途：初始化`IntArrayBuilder`实例。
        // 中文标注（参数）：`initialCapacity`，含义：用于表示initial、capacity。
        private IntArrayBuilder(int initialCapacity) {
            data = new int[Math.max(16, initialCapacity)];
        }

        // 中文标注（方法）：`reset`，参数：无；用途：执行reset相关逻辑。
        private void reset() {
            size = 0;
        }

        // 中文标注（方法）：`size`，参数：无；用途：执行大小相关逻辑。
        private int size() {
            return size;
        }

        // 中文标注（方法）：`append`，参数：value；用途：执行append相关逻辑。
        // 中文标注（参数）：`value`，含义：用于表示值。
        private void append(int value) {
            ensureCapacity(1);
            data[size++] = value;
        }

        // 中文标注（方法）：`ensureCapacity`，参数：extra；用途：执行ensure、capacity相关逻辑。
        // 中文标注（参数）：`extra`，含义：用于表示extra。
        private void ensureCapacity(int extra) {
            // 中文标注（局部变量）：`required`，含义：用于表示required。
            int required = size + extra; // meaning
            if (required <= data.length) {
                return;
            }
            // 中文标注（局部变量）：`newCapacity`，含义：用于表示new、capacity。
            int newCapacity = data.length; // meaning
            while (newCapacity < required) {
                newCapacity *= 2;
            }
            // 中文标注（局部变量）：`newData`，含义：用于表示new、数据。
            int[] newData = new int[newCapacity]; // meaning
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }

    }

    // 中文标注（类）：`MeshBuildScratch`，职责：封装网格、构建、临时工作区相关逻辑。
    private static final class MeshBuildScratch {
        // 中文标注（字段）：`vertices`，含义：用于表示顶点集合。
        private final PackedVertexBuilder vertices = new PackedVertexBuilder(4_096); // meaning
        // 中文标注（字段）：`indices`，含义：用于表示索引集合。
        private final IntArrayBuilder indices = new IntArrayBuilder(6_144); // meaning
        // 中文标注（字段）：`bounds`，含义：用于表示bounds。
        private final BoundsAccumulator bounds = new BoundsAccumulator(); // meaning
        // 中文标注（字段）：`snapshotNeighbors`，含义：用于表示快照、邻居集合。
        private final Chunk[] snapshotNeighbors = new Chunk[9]; // meaning
        // 中文标注（字段）：`snapshotNeighborSections`，含义：用于表示快照、邻居、sections。
        private final Section[] snapshotNeighborSections = new Section[9]; // meaning
        // 中文标注（字段）：`horizontalMask`，含义：用于表示水平、掩码。
        private int[] horizontalMask = new int[Section.SIZE * Section.SIZE]; // meaning
        // 中文标注（字段）：`verticalMask`，含义：用于表示垂直、掩码。
        private int[] verticalMask = new int[Section.SIZE * Section.SIZE]; // meaning
        // 中文标注（字段）：`lodColumnHeights`，含义：用于表示细节层级、column、heights。
        private int[] lodColumnHeights = new int[Section.SIZE * Section.SIZE]; // meaning
        // 中文标注（字段）：`lodColumnBlocks`，含义：用于表示细节层级、column、方块集合。
        private Block[] lodColumnBlocks = new Block[Section.SIZE * Section.SIZE]; // meaning
        // 中文标注（字段）：`lodCellHeights`，含义：用于表示细节层级、cell、heights。
        private int[] lodCellHeights = new int[(Section.SIZE / LOD_CELL_SIZE) * (Section.SIZE / LOD_CELL_SIZE)]; // meaning
        // 中文标注（字段）：`lodCellBlocks`，含义：用于表示细节层级、cell、方块集合。
        private Block[] lodCellBlocks = new Block[(Section.SIZE / LOD_CELL_SIZE) * (Section.SIZE / LOD_CELL_SIZE)]; // meaning

        // 中文标注（方法）：`horizontalMask`，参数：无；用途：执行水平、掩码相关逻辑。
        private int[] horizontalMask() {
            if (horizontalMask.length < Section.SIZE * Section.SIZE) {
                horizontalMask = new int[Section.SIZE * Section.SIZE];
            }
            return horizontalMask;
        }

        // 中文标注（方法）：`verticalMask`，参数：snapshotHeight；用途：执行垂直、掩码相关逻辑。
        // 中文标注（参数）：`snapshotHeight`，含义：用于表示快照、高度。
        private int[] verticalMask(int snapshotHeight) {
            // 中文标注（局部变量）：`required`，含义：用于表示required。
            int required = Math.max(1, Section.SIZE * snapshotHeight); // meaning
            if (verticalMask.length < required) {
                verticalMask = new int[required];
            }
            return verticalMask;
        }

        // 中文标注（方法）：`lodColumnHeights`，参数：无；用途：执行细节层级、column、heights相关逻辑。
        private int[] lodColumnHeights() {
            // 中文标注（局部变量）：`required`，含义：用于表示required。
            int required = Section.SIZE * Section.SIZE; // meaning
            if (lodColumnHeights.length < required) {
                lodColumnHeights = new int[required];
            }
            return lodColumnHeights;
        }

        // 中文标注（方法）：`lodColumnBlocks`，参数：无；用途：执行细节层级、column、方块集合相关逻辑。
        private Block[] lodColumnBlocks() {
            // 中文标注（局部变量）：`required`，含义：用于表示required。
            int required = Section.SIZE * Section.SIZE; // meaning
            if (lodColumnBlocks.length < required) {
                lodColumnBlocks = new Block[required];
            }
            return lodColumnBlocks;
        }

        // 中文标注（方法）：`lodCellHeights`，参数：required；用途：执行细节层级、cell、heights相关逻辑。
        // 中文标注（参数）：`required`，含义：用于表示required。
        private int[] lodCellHeights(int required) {
            if (lodCellHeights.length < required) {
                lodCellHeights = new int[required];
            }
            return lodCellHeights;
        }

        // 中文标注（方法）：`lodCellBlocks`，参数：required；用途：执行细节层级、cell、方块集合相关逻辑。
        // 中文标注（参数）：`required`，含义：用于表示required。
        private Block[] lodCellBlocks(int required) {
            if (lodCellBlocks.length < required) {
                lodCellBlocks = new Block[required];
            }
            return lodCellBlocks;
        }
    }

    // 中文标注（类）：`SnapshotBlockArrayPool`，职责：封装快照、方块、数组、池相关逻辑。
    private static final class SnapshotBlockArrayPool {
        // 中文标注（字段）：`buckets`，含义：用于表示buckets。
        private final Map<Integer, ArrayDeque<Block[]>> buckets = new HashMap<>(); // meaning
        // 中文标注（字段）：`maxArraysPerBucket`，含义：用于表示最大、arrays、per、bucket。
        private final int maxArraysPerBucket; // meaning

        // 中文标注（构造方法）：`SnapshotBlockArrayPool`，参数：maxArraysPerBucket；用途：初始化`SnapshotBlockArrayPool`实例。
        // 中文标注（参数）：`maxArraysPerBucket`，含义：用于表示最大、arrays、per、bucket。
        private SnapshotBlockArrayPool(int maxArraysPerBucket) {
            this.maxArraysPerBucket = Math.max(1, maxArraysPerBucket);
        }

        // 中文标注（方法）：`acquire`，参数：exactLength；用途：执行acquire相关逻辑。
        // 中文标注（参数）：`exactLength`，含义：用于表示exact、长度。
        private synchronized Block[] acquire(int exactLength) {
            // 中文标注（局部变量）：`bucket`，含义：用于表示bucket。
            ArrayDeque<Block[]> bucket = buckets.get(exactLength); // meaning
            // 中文标注（局部变量）：`blocks`，含义：用于表示方块集合。
            Block[] blocks = bucket == null ? null : bucket.pollFirst(); // meaning
            if (blocks != null) {
                return blocks;
            }
            return new Block[exactLength];
        }

        // 中文标注（方法）：`release`，参数：blocks；用途：执行release相关逻辑。
        // 中文标注（参数）：`blocks`，含义：用于表示方块集合。
        private synchronized void release(Block[] blocks) {
            if (blocks == null) {
                return;
            }
            // 中文标注（Lambda参数）：`unused`，含义：用于表示unused。
            // 中文标注（局部变量）：`bucket`，含义：用于表示bucket。
            ArrayDeque<Block[]> bucket = buckets.computeIfAbsent(blocks.length, unused -> new ArrayDeque<>()); // meaning
            if (bucket.size() >= maxArraysPerBucket) {
                return;
            }
            bucket.addFirst(blocks);
        }
    }

    // 中文标注（枚举）：`FaceDirection`，职责：封装面、direction相关逻辑。
    private enum FaceDirection {
        // 中文标注（字段）：`UP`，含义：用于表示up。
        UP(0, 1, 0, 1.0f) {
            // 中文标注（方法）：`vertex`，参数：x、y、z、index；用途：执行顶点相关逻辑。
            @Override
            // 中文标注（参数）：`x`，含义：用于表示X坐标。
            // 中文标注（参数）：`y`，含义：用于表示Y坐标。
            // 中文标注（参数）：`z`，含义：用于表示Z坐标。
            // 中文标注（参数）：`index`，含义：用于表示索引。
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x, y + 1, z);
                    case 1 -> new Vec3(x + 1, y + 1, z);
                    case 2 -> new Vec3(x + 1, y + 1, z + 1);
                    default -> new Vec3(x, y + 1, z + 1);
                };
            }
        },
        // 中文标注（字段）：`DOWN`，含义：用于表示down。
        DOWN(0, -1, 0, 0.58f) {
            // 中文标注（方法）：`vertex`，参数：x、y、z、index；用途：执行顶点相关逻辑。
            @Override
            // 中文标注（参数）：`x`，含义：用于表示X坐标。
            // 中文标注（参数）：`y`，含义：用于表示Y坐标。
            // 中文标注（参数）：`z`，含义：用于表示Z坐标。
            // 中文标注（参数）：`index`，含义：用于表示索引。
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x, y, z + 1);
                    case 1 -> new Vec3(x + 1, y, z + 1);
                    case 2 -> new Vec3(x + 1, y, z);
                    default -> new Vec3(x, y, z);
                };
            }
        },
        // 中文标注（字段）：`NORTH`，含义：用于表示北。
        NORTH(0, 0, -1, 0.82f) {
            // 中文标注（方法）：`vertex`，参数：x、y、z、index；用途：执行顶点相关逻辑。
            @Override
            // 中文标注（参数）：`x`，含义：用于表示X坐标。
            // 中文标注（参数）：`y`，含义：用于表示Y坐标。
            // 中文标注（参数）：`z`，含义：用于表示Z坐标。
            // 中文标注（参数）：`index`，含义：用于表示索引。
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x + 1, y, z);
                    case 1 -> new Vec3(x + 1, y + 1, z);
                    case 2 -> new Vec3(x, y + 1, z);
                    default -> new Vec3(x, y, z);
                };
            }
        },
        // 中文标注（字段）：`SOUTH`，含义：用于表示南。
        SOUTH(0, 0, 1, 0.82f) {
            // 中文标注（方法）：`vertex`，参数：x、y、z、index；用途：执行顶点相关逻辑。
            @Override
            // 中文标注（参数）：`x`，含义：用于表示X坐标。
            // 中文标注（参数）：`y`，含义：用于表示Y坐标。
            // 中文标注（参数）：`z`，含义：用于表示Z坐标。
            // 中文标注（参数）：`index`，含义：用于表示索引。
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x, y, z + 1);
                    case 1 -> new Vec3(x, y + 1, z + 1);
                    case 2 -> new Vec3(x + 1, y + 1, z + 1);
                    default -> new Vec3(x + 1, y, z + 1);
                };
            }
        },
        // 中文标注（字段）：`WEST`，含义：用于表示西。
        WEST(-1, 0, 0, 0.74f) {
            // 中文标注（方法）：`vertex`，参数：x、y、z、index；用途：执行顶点相关逻辑。
            @Override
            // 中文标注（参数）：`x`，含义：用于表示X坐标。
            // 中文标注（参数）：`y`，含义：用于表示Y坐标。
            // 中文标注（参数）：`z`，含义：用于表示Z坐标。
            // 中文标注（参数）：`index`，含义：用于表示索引。
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x, y, z);
                    case 1 -> new Vec3(x, y + 1, z);
                    case 2 -> new Vec3(x, y + 1, z + 1);
                    default -> new Vec3(x, y, z + 1);
                };
            }
        },
        // 中文标注（字段）：`EAST`，含义：用于表示东。
        EAST(1, 0, 0, 0.74f) {
            // 中文标注（方法）：`vertex`，参数：x、y、z、index；用途：执行顶点相关逻辑。
            @Override
            // 中文标注（参数）：`x`，含义：用于表示X坐标。
            // 中文标注（参数）：`y`，含义：用于表示Y坐标。
            // 中文标注（参数）：`z`，含义：用于表示Z坐标。
            // 中文标注（参数）：`index`，含义：用于表示索引。
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x + 1, y, z + 1);
                    case 1 -> new Vec3(x + 1, y + 1, z + 1);
                    case 2 -> new Vec3(x + 1, y + 1, z);
                    default -> new Vec3(x + 1, y, z);
                };
            }
        };

        // 中文标注（字段）：`dx`，含义：用于表示dx。
        private final int dx; // meaning
        // 中文标注（字段）：`dy`，含义：用于表示dy。
        private final int dy; // meaning
        // 中文标注（字段）：`dz`，含义：用于表示dz。
        private final int dz; // meaning
        // 中文标注（字段）：`brightness`，含义：用于表示亮度。
        private final float brightness; // meaning

        // 中文标注（构造方法）：`FaceDirection`，参数：dx、dy、dz、brightness；用途：初始化`FaceDirection`实例。
        // 中文标注（参数）：`dx`，含义：用于表示dx。
        // 中文标注（参数）：`dy`，含义：用于表示dy。
        // 中文标注（参数）：`dz`，含义：用于表示dz。
        // 中文标注（参数）：`brightness`，含义：用于表示亮度。
        FaceDirection(int dx, int dy, int dz, float brightness) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.brightness = brightness;
        }

        // 中文标注（方法）：`vertex`，参数：x、y、z、index；用途：执行顶点相关逻辑。
        // 中文标注（参数）：`x`，含义：用于表示X坐标。
        // 中文标注（参数）：`y`，含义：用于表示Y坐标。
        // 中文标注（参数）：`z`，含义：用于表示Z坐标。
        // 中文标注（参数）：`index`，含义：用于表示索引。
        abstract Vec3 vertex(int x, int y, int z, int index); // meaning
    }
}
