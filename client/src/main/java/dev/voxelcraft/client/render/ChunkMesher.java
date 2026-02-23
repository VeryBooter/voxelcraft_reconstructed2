package dev.voxelcraft.client.render;

import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.block.Block;
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

public final class ChunkMesher {
    public static final int GPU_VERTEX_WORDS = 4; // x(float bits), y(float bits), z(float bits), rgba(ubyte4 packed)
    public static final int GPU_VERTEX_STRIDE_BYTES = GPU_VERTEX_WORDS * Integer.BYTES;
    public static final long GPU_COLOR_OFFSET_BYTES = 3L * Float.BYTES;

    private static final int VERTICAL_RANGE_BELOW = 96;
    private static final int VERTICAL_RANGE_ABOVE = 192;
    private static final int LOD_LEVEL_FULL = 0;
    private static final int LOD_LEVEL_HEIGHTFIELD_2X2 = 1;
    private static final int LOD_CELL_SIZE = 2;
    private static final boolean NATIVE_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final int EMPTY_MASK = 0;

    private static final int SNAPSHOT_XZ = Section.SIZE + 2;
    private static final int SNAPSHOT_PLANE = SNAPSHOT_XZ * SNAPSHOT_XZ;
    private static final int[] SNAPSHOT_CHUNK_INDEX_LUT = buildSnapshotChunkIndexLut();
    private static final int[] SNAPSHOT_LOCAL_COORD_LUT = buildSnapshotLocalCoordLut();
    private static final Color COLOR_GRASS = new Color(96, 170, 82);
    private static final Color COLOR_DIRT = new Color(127, 94, 66);
    private static final Color COLOR_STONE = new Color(134, 138, 145);
    private static final Color COLOR_SAND = new Color(214, 198, 148);
    private static final Color COLOR_WOOD = new Color(132, 94, 57);
    private static final Color COLOR_LEAVES = new Color(76, 140, 72);
    private static final Color COLOR_FALLBACK = new Color(215, 103, 60);

    private long cachedWorldVersion = Long.MIN_VALUE;
    private int cachedMinY = Integer.MIN_VALUE;
    private int cachedMaxY = Integer.MAX_VALUE;
    private Mesh cachedMesh = new Mesh(List.of());
    private final ThreadLocal<SoftwareBuildScratch> softwareScratch = ThreadLocal.withInitial(SoftwareBuildScratch::new);
    private final ThreadLocal<MeshBuildScratch> gpuScratch = ThreadLocal.withInitial(MeshBuildScratch::new);
    private final SnapshotBlockArrayPool snapshotBlockPool = new SnapshotBlockArrayPool(32);

    public Mesh build(ClientWorldView worldView, double centerY) {
        long worldVersion = worldView.blockUpdateVersion();
        int minY = Math.max(World.MIN_Y, (int) Math.floor(centerY) - VERTICAL_RANGE_BELOW);
        int maxY = Math.min(World.MAX_Y, (int) Math.floor(centerY) + VERTICAL_RANGE_ABOVE);

        if (worldVersion == cachedWorldVersion && minY == cachedMinY && maxY == cachedMaxY) {
            return cachedMesh;
        }

        SoftwareBuildScratch scratch = softwareScratch.get();
        ArrayList<Mesh.ChunkBatch> chunkBatches = scratch.chunkBatches;
        chunkBatches.clear();
        for (Chunk chunk : worldView.loadedChunks()) {
            int chunkBaseX = chunk.pos().x() * Section.SIZE;
            int chunkBaseZ = chunk.pos().z() * Section.SIZE;
            scratch.beginChunk(worldView, chunkBaseX, chunkBaseZ);
            chunk.forEachNonAirInRange(minY, maxY, scratch.consumer);
            if (scratch.hasFaces()) {
                chunkBatches.add(scratch.finishChunkBatch(minY));
            }
        }

        cachedMesh = new Mesh(chunkBatches);
        cachedWorldVersion = worldVersion;
        cachedMinY = minY;
        cachedMaxY = maxY;
        return cachedMesh;
    }

    public int gpuMinY(double centerY) {
        return Math.max(World.MIN_Y, (int) Math.floor(centerY) - VERTICAL_RANGE_BELOW);
    }

    public int gpuMaxY(double centerY) {
        return Math.min(World.MAX_Y, (int) Math.floor(centerY) + VERTICAL_RANGE_ABOVE);
    }

    public ChunkSnapshot captureChunkSnapshot(ClientWorldView worldView, Chunk chunk, int minY, int maxY) {
        int clampedMinY = Math.max(World.MIN_Y, minY);
        int clampedMaxY = Math.min(World.MAX_Y, maxY);
        if (clampedMaxY < clampedMinY) {
            clampedMaxY = clampedMinY;
        }

        int height = clampedMaxY - clampedMinY + 1;
        int expandedHeight = height + 2;
        int blockCount = SNAPSHOT_XZ * SNAPSHOT_XZ * expandedHeight;
        Block[] blocks = snapshotBlockPool.acquire(blockCount);

        MeshBuildScratch scratch = gpuScratch.get();
        Chunk[] snapshotNeighbors = scratch.snapshotNeighbors;
        Section[] snapshotNeighborSections = scratch.snapshotNeighborSections;
        int chunkX = chunk.pos().x();
        int chunkZ = chunk.pos().z();
        for (int neighborDz = -1; neighborDz <= 1; neighborDz++) {
            for (int neighborDx = -1; neighborDx <= 1; neighborDx++) {
                int neighborIndex = (neighborDz + 1) * 3 + (neighborDx + 1);
                snapshotNeighbors[neighborIndex] = worldView.getChunk(chunkX + neighborDx, chunkZ + neighborDz);
            }
        }

        for (int exY = 0; exY < expandedHeight; exY++) {
            int worldY = clampedMinY + exY - 1;
            int planeBase = exY * SNAPSHOT_PLANE;
            if (worldY < World.MIN_Y || worldY > World.MAX_Y) {
                Arrays.fill(blocks, planeBase, planeBase + SNAPSHOT_PLANE, Blocks.AIR);
                continue;
            }

            int sectionY = Math.floorDiv(worldY, Section.SIZE);
            int localY = Math.floorMod(worldY, Section.SIZE);
            for (int i = 0; i < snapshotNeighbors.length; i++) {
                Chunk neighborChunk = snapshotNeighbors[i];
                snapshotNeighborSections[i] = neighborChunk == null ? null : neighborChunk.sectionOrNull(sectionY);
            }

            for (int exZ = 0; exZ < SNAPSHOT_XZ; exZ++) {
                int rowBase = planeBase + exZ * SNAPSHOT_XZ;
                int chunkZIndex = SNAPSHOT_CHUNK_INDEX_LUT[exZ];
                int localZ = SNAPSHOT_LOCAL_COORD_LUT[exZ];
                int neighborRowBase = chunkZIndex * 3;
                for (int exX = 0; exX < SNAPSHOT_XZ; exX++) {
                    int chunkXIndex = SNAPSHOT_CHUNK_INDEX_LUT[exX];
                    int localX = SNAPSHOT_LOCAL_COORD_LUT[exX];
                    Section sourceSection = snapshotNeighborSections[neighborRowBase + chunkXIndex];
                    blocks[rowBase + exX] = sourceSection == null
                        ? (worldY < World.DEFAULT_SOLID_BELOW_Y ? Blocks.STONE : Blocks.AIR)
                        : sourceSection.getBlock(localX, localY, localZ);
                }
            }
        }

        return new ChunkSnapshot(chunk.pos(), chunk.version(), clampedMinY, clampedMaxY, blocks);
    }

    public ChunkMeshData buildChunkMesh(ChunkSnapshot snapshot, DirectByteBufferPool bufferPool) {
        return buildChunkMesh(snapshot, bufferPool, LOD_LEVEL_FULL);
    }

    public ChunkMeshData buildChunkMesh(ChunkSnapshot snapshot, DirectByteBufferPool bufferPool, int lodLevel) {
        try {
            MeshBuildScratch scratch = gpuScratch.get();
            PackedVertexBuilder vertices = scratch.vertices;
            IntArrayBuilder indices = scratch.indices;
            BoundsAccumulator bounds = scratch.bounds;
            vertices.reset();
            indices.reset();
            bounds.reset();

            int chunkBaseX = snapshot.pos().x() * Section.SIZE;
            int chunkBaseZ = snapshot.pos().z() * Section.SIZE;
            int expandedHeight = snapshot.expandedHeight();
            int snapshotHeight = snapshot.height();

            if (snapshotHeight > 0) {
                if (lodLevel <= LOD_LEVEL_FULL) {
                    int[] horizontalMask = scratch.horizontalMask();
                    int[] verticalMask = scratch.verticalMask(snapshotHeight);

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
                double chunkMinX = chunkBaseX;
                double chunkMinZ = chunkBaseZ;
                double chunkMaxX = chunkBaseX + Section.SIZE;
                double chunkMaxZ = chunkBaseZ + Section.SIZE;
                return new ChunkMeshData(
                    snapshot.pos(),
                    snapshot.version(),
                    lodLevel,
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

            int vertexByteCount = vertices.wordCount() * Integer.BYTES;
            int indexByteCount = indices.size() * Integer.BYTES;
            ByteBuffer vertexBytes = bufferPool.acquire(vertexByteCount);
            ByteBuffer indexBytes = bufferPool.acquire(indexByteCount);
            try {
                IntBuffer vertexInts = vertexBytes.asIntBuffer();
                vertexInts.put(vertices.data, 0, vertices.wordCount());
                vertexBytes.position(0);
                vertexBytes.limit(vertexByteCount);

                IntBuffer indexInts = indexBytes.asIntBuffer();
                indexInts.put(indices.data, 0, indices.size());
                indexBytes.position(0);
                indexBytes.limit(indexByteCount);
            } catch (RuntimeException exception) {
                bufferPool.release(vertexBytes);
                bufferPool.release(indexBytes);
                throw exception;
            }

            return new ChunkMeshData(
                snapshot.pos(),
                snapshot.version(),
                lodLevel,
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
        } finally {
            snapshot.releaseBlocks(snapshotBlockPool);
        }
    }

    private static void buildHeightfieldLodMesh2x2(
        ChunkSnapshot snapshot,
        int expandedHeight,
        int chunkBaseX,
        int chunkBaseZ,
        PackedVertexBuilder vertices,
        IntArrayBuilder indices,
        BoundsAccumulator bounds,
        MeshBuildScratch scratch
    ) {
        int[] columnHeights = scratch.lodColumnHeights();
        Block[] columnBlocks = scratch.lodColumnBlocks();
        int columnCount = Section.SIZE * Section.SIZE;
        Arrays.fill(columnHeights, 0, columnCount, Integer.MIN_VALUE);
        Arrays.fill(columnBlocks, 0, columnCount, null);

        for (int localZ = 0; localZ < Section.SIZE; localZ++) {
            int exZ = localZ + 1;
            for (int localX = 0; localX < Section.SIZE; localX++) {
                int exX = localX + 1;
                int columnIndex = localZ * Section.SIZE + localX;
                for (int localY = snapshot.height() - 1; localY >= 0; localY--) {
                    int exY = localY + 1;
                    Block block = snapshot.blockAtExpanded(exX, exY, exZ, expandedHeight);
                    if (!isSolid(block)) {
                        continue;
                    }
                    columnHeights[columnIndex] = snapshot.minY() + localY;
                    columnBlocks[columnIndex] = block;
                    break;
                }
            }
        }

        int coarseWidth = Math.max(1, Section.SIZE / LOD_CELL_SIZE);
        int coarseHeight = coarseWidth;
        int[] cellHeights = scratch.lodCellHeights(coarseWidth * coarseHeight);
        Block[] cellBlocks = scratch.lodCellBlocks(coarseWidth * coarseHeight);
        Arrays.fill(cellHeights, 0, coarseWidth * coarseHeight, Integer.MIN_VALUE);
        Arrays.fill(cellBlocks, 0, coarseWidth * coarseHeight, null);

        for (int cellZ = 0; cellZ < coarseHeight; cellZ++) {
            for (int cellX = 0; cellX < coarseWidth; cellX++) {
                int bestHeight = Integer.MIN_VALUE;
                Block bestBlock = null;
                int startX = cellX * LOD_CELL_SIZE;
                int startZ = cellZ * LOD_CELL_SIZE;
                for (int dz = 0; dz < LOD_CELL_SIZE; dz++) {
                    for (int dx = 0; dx < LOD_CELL_SIZE; dx++) {
                        int localX = startX + dx;
                        int localZ = startZ + dz;
                        if (localX >= Section.SIZE || localZ >= Section.SIZE) {
                            continue;
                        }
                        int columnIndex = localZ * Section.SIZE + localX;
                        int topY = columnHeights[columnIndex];
                        if (topY > bestHeight) {
                            bestHeight = topY;
                            bestBlock = columnBlocks[columnIndex];
                        }
                    }
                }
                int cellIndex = cellZ * coarseWidth + cellX;
                cellHeights[cellIndex] = bestHeight;
                cellBlocks[cellIndex] = bestBlock;
            }
        }

        for (int cellZ = 0; cellZ < coarseHeight; cellZ++) {
            for (int cellX = 0; cellX < coarseWidth; cellX++) {
                int cellIndex = cellZ * coarseWidth + cellX;
                int topY = cellHeights[cellIndex];
                Block block = cellBlocks[cellIndex];
                if (topY == Integer.MIN_VALUE || !isSolid(block)) {
                    continue;
                }

                float x0 = chunkBaseX + (cellX * LOD_CELL_SIZE);
                float z0 = chunkBaseZ + (cellZ * LOD_CELL_SIZE);
                float x1 = x0 + LOD_CELL_SIZE;
                float z1 = z0 + LOD_CELL_SIZE;
                float topPlaneY = topY + 1.0f;
                appendQuad(
                    vertices, indices, bounds, gpuPackedColor(block, FaceDirection.UP),
                    x0, topPlaneY, z0,
                    x1, topPlaneY, z0,
                    x1, topPlaneY, z1,
                    x0, topPlaneY, z1
                );

                if (cellX + 1 < coarseWidth) {
                    int neighborHeight = cellHeights[cellIndex + 1];
                    if (neighborHeight < topY) {
                        float y0 = neighborHeight + 1.0f;
                        float y1 = topY + 1.0f;
                        float x = x1;
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
                    int neighborHeight = cellHeights[cellIndex + coarseWidth];
                    if (neighborHeight < topY) {
                        float y0 = neighborHeight + 1.0f;
                        float y1 = topY + 1.0f;
                        float z = z1;
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
                    float y0 = snapshot.minY();
                    float y1 = topY + 1.0f;
                    float x = x0;
                    appendQuad(
                        vertices, indices, bounds, gpuPackedColor(block, FaceDirection.WEST),
                        x, y0, z0,
                        x, y1, z0,
                        x, y1, z1,
                        x, y0, z1
                    );
                }
                if (cellZ == 0) {
                    float y0 = snapshot.minY();
                    float y1 = topY + 1.0f;
                    float z = z0;
                    appendQuad(
                        vertices, indices, bounds, gpuPackedColor(block, FaceDirection.NORTH),
                        x1, y0, z,
                        x1, y1, z,
                        x0, y1, z,
                        x0, y0, z
                    );
                }
                if (cellX == coarseWidth - 1) {
                    float y0 = snapshot.minY();
                    float y1 = topY + 1.0f;
                    float x = x1;
                    appendQuad(
                        vertices, indices, bounds, gpuPackedColor(block, FaceDirection.EAST),
                        x, y0, z1,
                        x, y1, z1,
                        x, y1, z0,
                        x, y0, z0
                    );
                }
                if (cellZ == coarseHeight - 1) {
                    float y0 = snapshot.minY();
                    float y1 = topY + 1.0f;
                    float z = z1;
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

    private static void buildTopBottomGreedy(
        ChunkSnapshot snapshot,
        int expandedHeight,
        int chunkBaseX,
        int chunkBaseZ,
        PackedVertexBuilder vertices,
        IntArrayBuilder indices,
        BoundsAccumulator bounds,
        int[] mask,
        boolean topFace
    ) {
        FaceDirection direction = topFace ? FaceDirection.UP : FaceDirection.DOWN;
        int neighborYDelta = topFace ? 1 : -1;

        for (int localY = 0; localY < snapshot.height(); localY++) {
            int exY = localY + 1;
            int maskIndex = 0;
            for (int localZ = 0; localZ < Section.SIZE; localZ++) {
                int exZ = localZ + 1;
                for (int localX = 0; localX < Section.SIZE; localX++) {
                    int exX = localX + 1;
                    Block block = snapshot.blockAtExpanded(exX, exY, exZ, expandedHeight);
                    Block neighbor = snapshot.blockAtExpanded(exX, exY + neighborYDelta, exZ, expandedHeight);
                    mask[maskIndex++] = faceMask(block, neighbor, direction);
                }
            }

            int planeY = snapshot.minY() + localY + (topFace ? 1 : 0);
            emitGreedyRectangles(mask, Section.SIZE, Section.SIZE, (u, v, width, height, packedColor) -> {
                float x0 = chunkBaseX + u;
                float y = planeY;
                float z0 = chunkBaseZ + v;
                float x1 = x0 + width;
                float z1 = z0 + height;
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

    private static void buildNorthSouthGreedy(
        ChunkSnapshot snapshot,
        int expandedHeight,
        int chunkBaseX,
        int chunkBaseZ,
        PackedVertexBuilder vertices,
        IntArrayBuilder indices,
        BoundsAccumulator bounds,
        int[] mask,
        boolean northFace
    ) {
        FaceDirection direction = northFace ? FaceDirection.NORTH : FaceDirection.SOUTH;
        int neighborZDelta = northFace ? -1 : 1;

        for (int localZ = 0; localZ < Section.SIZE; localZ++) {
            int exZ = localZ + 1;
            int maskIndex = 0;
            for (int localY = 0; localY < snapshot.height(); localY++) {
                int exY = localY + 1;
                for (int localX = 0; localX < Section.SIZE; localX++) {
                    int exX = localX + 1;
                    Block block = snapshot.blockAtExpanded(exX, exY, exZ, expandedHeight);
                    Block neighbor = snapshot.blockAtExpanded(exX, exY, exZ + neighborZDelta, expandedHeight);
                    mask[maskIndex++] = faceMask(block, neighbor, direction);
                }
            }

            int planeZ = chunkBaseZ + localZ + (northFace ? 0 : 1);
            emitGreedyRectangles(mask, Section.SIZE, snapshot.height(), (u, v, width, height, packedColor) -> {
                float x0 = chunkBaseX + u;
                float y0 = snapshot.minY() + v;
                float z = planeZ;
                float x1 = x0 + width;
                float y1 = y0 + height;
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

    private static void buildWestEastGreedy(
        ChunkSnapshot snapshot,
        int expandedHeight,
        int chunkBaseX,
        int chunkBaseZ,
        PackedVertexBuilder vertices,
        IntArrayBuilder indices,
        BoundsAccumulator bounds,
        int[] mask,
        boolean westFace
    ) {
        FaceDirection direction = westFace ? FaceDirection.WEST : FaceDirection.EAST;
        int neighborXDelta = westFace ? -1 : 1;

        for (int localX = 0; localX < Section.SIZE; localX++) {
            int exX = localX + 1;
            int maskIndex = 0;
            for (int localY = 0; localY < snapshot.height(); localY++) {
                int exY = localY + 1;
                for (int localZ = 0; localZ < Section.SIZE; localZ++) {
                    int exZ = localZ + 1;
                    Block block = snapshot.blockAtExpanded(exX, exY, exZ, expandedHeight);
                    Block neighbor = snapshot.blockAtExpanded(exX + neighborXDelta, exY, exZ, expandedHeight);
                    mask[maskIndex++] = faceMask(block, neighbor, direction);
                }
            }

            int planeX = chunkBaseX + localX + (westFace ? 0 : 1);
            emitGreedyRectangles(mask, Section.SIZE, snapshot.height(), (u, v, width, height, packedColor) -> {
                float x = planeX;
                float y0 = snapshot.minY() + v;
                float z0 = chunkBaseZ + u;
                float y1 = y0 + height;
                float z1 = z0 + width;
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

    private static void emitGreedyRectangles(int[] mask, int width, int height, GreedyRectConsumer consumer) {
        for (int v = 0; v < height; v++) {
            for (int u = 0; u < width; ) {
                int index = v * width + u;
                int maskValue = mask[index];
                if (maskValue == EMPTY_MASK) {
                    u++;
                    continue;
                }

                int rectWidth = 1;
                while (u + rectWidth < width && mask[index + rectWidth] == maskValue) {
                    rectWidth++;
                }

                int rectHeight = 1;
                outer:
                while (v + rectHeight < height) {
                    int rowStart = (v + rectHeight) * width + u;
                    for (int offset = 0; offset < rectWidth; offset++) {
                        if (mask[rowStart + offset] != maskValue) {
                            break outer;
                        }
                    }
                    rectHeight++;
                }

                consumer.accept(u, v, rectWidth, rectHeight, maskValue);

                for (int clearV = 0; clearV < rectHeight; clearV++) {
                    int rowStart = (v + clearV) * width + u;
                    for (int clearU = 0; clearU < rectWidth; clearU++) {
                        mask[rowStart + clearU] = EMPTY_MASK;
                    }
                }

                u += rectWidth;
            }
        }
    }

    private static int faceMask(Block block, Block neighbor, FaceDirection direction) {
        if (!isSolid(block) || isSolid(neighbor)) {
            return EMPTY_MASK;
        }
        return gpuPackedColor(block, direction);
    }

    private static boolean isSolid(Block block) {
        return block != null && block != Blocks.AIR && block.solid();
    }

    private static int gpuPackedColor(Block block, FaceDirection direction) {
        Color base = colorFor(block);
        float brightness = direction.brightness;
        int red = clamp((int) (base.getRed() * brightness));
        int green = clamp((int) (base.getGreen() * brightness));
        int blue = clamp((int) (base.getBlue() * brightness));
        return packRgba(red, green, blue, 255);
    }

    private static int packRgba(int red, int green, int blue, int alpha) {
        if (NATIVE_LITTLE_ENDIAN) {
            return (alpha << 24) | (blue << 16) | (green << 8) | red;
        }
        return (red << 24) | (green << 16) | (blue << 8) | alpha;
    }

    private static void appendQuad(
        PackedVertexBuilder vertices,
        IntArrayBuilder indices,
        BoundsAccumulator bounds,
        int packedColor,
        float x0,
        float y0,
        float z0,
        float x1,
        float y1,
        float z1,
        float x2,
        float y2,
        float z2,
        float x3,
        float y3,
        float z3
    ) {
        int baseVertex = vertices.vertexCount();
        vertices.appendVertex(x0, y0, z0, packedColor);
        vertices.appendVertex(x1, y1, z1, packedColor);
        vertices.appendVertex(x2, y2, z2, packedColor);
        vertices.appendVertex(x3, y3, z3, packedColor);

        indices.append(baseVertex);
        indices.append(baseVertex + 1);
        indices.append(baseVertex + 2);
        indices.append(baseVertex + 2);
        indices.append(baseVertex + 3);
        indices.append(baseVertex);

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
        if (block == Blocks.AIR || !block.solid()) {
            return;
        }

        Color blockColor = colorFor(block);
        for (FaceDirection direction : FaceDirection.values()) {
            if (worldView.isSolid(x + direction.dx, y + direction.dy, z + direction.dz)) {
                continue;
            }

            float detail = detailBrightness(block, direction, x, y, z);
            Color shaded = shade(blockColor, direction.brightness * detail);
            Vec3 v0 = direction.vertex(x, y, z, 0);
            Vec3 v1 = direction.vertex(x, y, z, 1);
            Vec3 v2 = direction.vertex(x, y, z, 2);
            Vec3 v3 = direction.vertex(x, y, z, 3);
            outFaces.add(new Mesh.Face(v0, v1, v2, v3, shaded));
        }
    }

    private static final class SoftwareBuildScratch {
        private final ArrayList<Mesh.ChunkBatch> chunkBatches = new ArrayList<>();
        private final ArrayList<Mesh.Face> chunkFaces = new ArrayList<>();
        private final ChunkFaceCollector consumer = new ChunkFaceCollector(this);
        private ClientWorldView worldView;
        private int chunkBaseX;
        private int chunkBaseZ;
        private int minSolidY = Integer.MAX_VALUE;
        private int maxSolidY = Integer.MIN_VALUE;

        private void beginChunk(ClientWorldView worldView, int chunkBaseX, int chunkBaseZ) {
            this.worldView = worldView;
            this.chunkBaseX = chunkBaseX;
            this.chunkBaseZ = chunkBaseZ;
            this.minSolidY = Integer.MAX_VALUE;
            this.maxSolidY = Integer.MIN_VALUE;
            chunkFaces.clear();
        }

        private boolean hasFaces() {
            return !chunkFaces.isEmpty();
        }

        private Mesh.ChunkBatch finishChunkBatch(int fallbackMinY) {
            double chunkMinY = minSolidY == Integer.MAX_VALUE ? fallbackMinY : minSolidY;
            double chunkMaxY = maxSolidY == Integer.MIN_VALUE ? (fallbackMinY + 1.0) : (maxSolidY + 1.0);
            return new Mesh.ChunkBatch(chunkBaseX, chunkBaseZ, chunkMinY, chunkMaxY, chunkFaces);
        }

        private void accept(int localX, int y, int localZ, Block block) {
            int worldX = chunkBaseX + localX;
            int worldZ = chunkBaseZ + localZ;
            if (block != Blocks.AIR && block.solid()) {
                if (y < minSolidY) {
                    minSolidY = y;
                }
                if (y > maxSolidY) {
                    maxSolidY = y;
                }
            }
            addVisibleFaces(worldView, chunkFaces, block, worldX, y, worldZ);
        }

        private static final class ChunkFaceCollector implements Chunk.NonAirBlockConsumer {
            private final SoftwareBuildScratch owner;

            private ChunkFaceCollector(SoftwareBuildScratch owner) {
                this.owner = owner;
            }

            @Override
            public void accept(int localX, int y, int localZ, Block block) {
                owner.accept(localX, y, localZ, block);
            }
        }
    }

    private static int snapshotIndex(int x, int y, int z, int expandedHeight) {
        return (y * SNAPSHOT_XZ + z) * SNAPSHOT_XZ + x;
    }

    private static int[] buildSnapshotChunkIndexLut() {
        int[] lut = new int[SNAPSHOT_XZ];
        for (int ex = 0; ex < SNAPSHOT_XZ; ex++) {
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

    private static int[] buildSnapshotLocalCoordLut() {
        int[] lut = new int[SNAPSHOT_XZ];
        for (int ex = 0; ex < SNAPSHOT_XZ; ex++) {
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
        return COLOR_FALLBACK;
    }

    private static float detailBrightness(Block block, FaceDirection direction, int x, int y, int z) {
        int hash = hash(x, y, z);
        float randomJitter = 0.90f + ((hash & 15) / 15.0f) * 0.18f;
        float stripe = (((x + z) & 1) == 0) ? 0.96f : 1.02f;

        if (block == Blocks.GRASS && direction == FaceDirection.UP) {
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

    private static Color shade(Color color, float brightness) {
        int red = clamp((int) (color.getRed() * brightness));
        int green = clamp((int) (color.getGreen() * brightness));
        int blue = clamp((int) (color.getBlue() * brightness));
        return new Color(red, green, blue);
    }

    private static int hash(int x, int y, int z) {
        int value = x * 734_287 + y * 912_931 + z * 438_289;
        value ^= (value >>> 13);
        value *= 1_274_126_177;
        value ^= (value >>> 16);
        return value;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static final class ChunkSnapshot {
        private final ChunkPos pos;
        private final long version;
        private final int minY;
        private final int maxY;
        private final int height;
        private final Block[] blocks;

        private ChunkSnapshot(ChunkPos pos, long version, int minY, int maxY, Block[] blocks) {
            this.pos = pos;
            this.version = version;
            this.minY = minY;
            this.maxY = maxY;
            this.height = Math.max(0, maxY - minY + 1);
            this.blocks = blocks;
        }

        public ChunkPos pos() {
            return pos;
        }

        public long version() {
            return version;
        }

        public int minY() {
            return minY;
        }

        public int maxY() {
            return maxY;
        }

        public int height() {
            return height;
        }

        private int expandedHeight() {
            return height + 2;
        }

        private Block blockAtExpanded(int exX, int exY, int exZ, int expandedHeight) {
            if (exX < 0 || exX >= SNAPSHOT_XZ || exZ < 0 || exZ >= SNAPSHOT_XZ || exY < 0 || exY >= expandedHeight) {
                return Blocks.AIR;
            }
            Block block = blocks[snapshotIndex(exX, exY, exZ, expandedHeight)];
            return block == null ? Blocks.AIR : block;
        }

        private void releaseBlocks(SnapshotBlockArrayPool pool) {
            pool.release(blocks);
        }
    }

    public static final class ChunkMeshData {
        private final ChunkPos pos;
        private final long version;
        private final int lodLevel;
        private final ByteBuffer vertexBytes;
        private final int vertexByteCount;
        private final ByteBuffer indexBytes;
        private final int indexByteCount;
        private final int indexCount;
        private final int triangleCount;
        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;

        public ChunkMeshData(
            ChunkPos pos,
            long version,
            int lodLevel,
            ByteBuffer vertexBytes,
            int vertexByteCount,
            ByteBuffer indexBytes,
            int indexByteCount,
            int indexCount,
            int triangleCount,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
        ) {
            this.pos = pos;
            this.version = version;
            this.lodLevel = lodLevel;
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

        public ChunkPos pos() {
            return pos;
        }

        public long version() {
            return version;
        }

        public ByteBuffer vertexBytes() {
            return vertexBytes;
        }

        public int lodLevel() {
            return lodLevel;
        }

        public int vertexByteCount() {
            return vertexByteCount;
        }

        public ByteBuffer indexBytes() {
            return indexBytes;
        }

        public int indexByteCount() {
            return indexByteCount;
        }

        public int indexCount() {
            return indexCount;
        }

        public int triangleCount() {
            return triangleCount;
        }

        public double minX() {
            return minX;
        }

        public double minY() {
            return minY;
        }

        public double minZ() {
            return minZ;
        }

        public double maxX() {
            return maxX;
        }

        public double maxY() {
            return maxY;
        }

        public double maxZ() {
            return maxZ;
        }

        public void releaseBuffers(DirectByteBufferPool bufferPool) {
            bufferPool.release(vertexBytes);
            bufferPool.release(indexBytes);
        }
    }

    @FunctionalInterface
    private interface GreedyRectConsumer {
        void accept(int u, int v, int width, int height, int packedColor);
    }

    private static final class BoundsAccumulator {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private void reset() {
            minX = Double.POSITIVE_INFINITY;
            minY = Double.POSITIVE_INFINITY;
            minZ = Double.POSITIVE_INFINITY;
            maxX = Double.NEGATIVE_INFINITY;
            maxY = Double.NEGATIVE_INFINITY;
            maxZ = Double.NEGATIVE_INFINITY;
        }

        private void include(float x, float y, float z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
    }

    private static final class PackedVertexBuilder {
        private int[] data;
        private int size;

        private PackedVertexBuilder(int initialCapacityWords) {
            data = new int[Math.max(16, initialCapacityWords)];
        }

        private void reset() {
            size = 0;
        }

        private int vertexCount() {
            return size / GPU_VERTEX_WORDS;
        }

        private int wordCount() {
            return size;
        }

        private void appendVertex(float x, float y, float z, int packedColor) {
            ensureCapacity(GPU_VERTEX_WORDS);
            data[size++] = Float.floatToRawIntBits(x);
            data[size++] = Float.floatToRawIntBits(y);
            data[size++] = Float.floatToRawIntBits(z);
            data[size++] = packedColor;
        }

        private void ensureCapacity(int extraWords) {
            int required = size + extraWords;
            if (required <= data.length) {
                return;
            }
            int newCapacity = data.length;
            while (newCapacity < required) {
                newCapacity *= 2;
            }
            int[] newData = new int[newCapacity];
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }

    }

    private static final class IntArrayBuilder {
        private int[] data;
        private int size;

        private IntArrayBuilder(int initialCapacity) {
            data = new int[Math.max(16, initialCapacity)];
        }

        private void reset() {
            size = 0;
        }

        private int size() {
            return size;
        }

        private void append(int value) {
            ensureCapacity(1);
            data[size++] = value;
        }

        private void ensureCapacity(int extra) {
            int required = size + extra;
            if (required <= data.length) {
                return;
            }
            int newCapacity = data.length;
            while (newCapacity < required) {
                newCapacity *= 2;
            }
            int[] newData = new int[newCapacity];
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }

    }

    private static final class MeshBuildScratch {
        private final PackedVertexBuilder vertices = new PackedVertexBuilder(4_096);
        private final IntArrayBuilder indices = new IntArrayBuilder(6_144);
        private final BoundsAccumulator bounds = new BoundsAccumulator();
        private final Chunk[] snapshotNeighbors = new Chunk[9];
        private final Section[] snapshotNeighborSections = new Section[9];
        private int[] horizontalMask = new int[Section.SIZE * Section.SIZE];
        private int[] verticalMask = new int[Section.SIZE * Section.SIZE];
        private int[] lodColumnHeights = new int[Section.SIZE * Section.SIZE];
        private Block[] lodColumnBlocks = new Block[Section.SIZE * Section.SIZE];
        private int[] lodCellHeights = new int[(Section.SIZE / LOD_CELL_SIZE) * (Section.SIZE / LOD_CELL_SIZE)];
        private Block[] lodCellBlocks = new Block[(Section.SIZE / LOD_CELL_SIZE) * (Section.SIZE / LOD_CELL_SIZE)];

        private int[] horizontalMask() {
            if (horizontalMask.length < Section.SIZE * Section.SIZE) {
                horizontalMask = new int[Section.SIZE * Section.SIZE];
            }
            return horizontalMask;
        }

        private int[] verticalMask(int snapshotHeight) {
            int required = Math.max(1, Section.SIZE * snapshotHeight);
            if (verticalMask.length < required) {
                verticalMask = new int[required];
            }
            return verticalMask;
        }

        private int[] lodColumnHeights() {
            int required = Section.SIZE * Section.SIZE;
            if (lodColumnHeights.length < required) {
                lodColumnHeights = new int[required];
            }
            return lodColumnHeights;
        }

        private Block[] lodColumnBlocks() {
            int required = Section.SIZE * Section.SIZE;
            if (lodColumnBlocks.length < required) {
                lodColumnBlocks = new Block[required];
            }
            return lodColumnBlocks;
        }

        private int[] lodCellHeights(int required) {
            if (lodCellHeights.length < required) {
                lodCellHeights = new int[required];
            }
            return lodCellHeights;
        }

        private Block[] lodCellBlocks(int required) {
            if (lodCellBlocks.length < required) {
                lodCellBlocks = new Block[required];
            }
            return lodCellBlocks;
        }
    }

    private static final class SnapshotBlockArrayPool {
        private final Map<Integer, ArrayDeque<Block[]>> buckets = new HashMap<>();
        private final int maxArraysPerBucket;

        private SnapshotBlockArrayPool(int maxArraysPerBucket) {
            this.maxArraysPerBucket = Math.max(1, maxArraysPerBucket);
        }

        private synchronized Block[] acquire(int exactLength) {
            ArrayDeque<Block[]> bucket = buckets.get(exactLength);
            Block[] blocks = bucket == null ? null : bucket.pollFirst();
            if (blocks != null) {
                return blocks;
            }
            return new Block[exactLength];
        }

        private synchronized void release(Block[] blocks) {
            if (blocks == null) {
                return;
            }
            ArrayDeque<Block[]> bucket = buckets.computeIfAbsent(blocks.length, unused -> new ArrayDeque<>());
            if (bucket.size() >= maxArraysPerBucket) {
                return;
            }
            bucket.addFirst(blocks);
        }
    }

    private enum FaceDirection {
        UP(0, 1, 0, 1.0f) {
            @Override
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x, y + 1, z);
                    case 1 -> new Vec3(x + 1, y + 1, z);
                    case 2 -> new Vec3(x + 1, y + 1, z + 1);
                    default -> new Vec3(x, y + 1, z + 1);
                };
            }
        },
        DOWN(0, -1, 0, 0.58f) {
            @Override
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x, y, z + 1);
                    case 1 -> new Vec3(x + 1, y, z + 1);
                    case 2 -> new Vec3(x + 1, y, z);
                    default -> new Vec3(x, y, z);
                };
            }
        },
        NORTH(0, 0, -1, 0.82f) {
            @Override
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x + 1, y, z);
                    case 1 -> new Vec3(x + 1, y + 1, z);
                    case 2 -> new Vec3(x, y + 1, z);
                    default -> new Vec3(x, y, z);
                };
            }
        },
        SOUTH(0, 0, 1, 0.82f) {
            @Override
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x, y, z + 1);
                    case 1 -> new Vec3(x, y + 1, z + 1);
                    case 2 -> new Vec3(x + 1, y + 1, z + 1);
                    default -> new Vec3(x + 1, y, z + 1);
                };
            }
        },
        WEST(-1, 0, 0, 0.74f) {
            @Override
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x, y, z);
                    case 1 -> new Vec3(x, y + 1, z);
                    case 2 -> new Vec3(x, y + 1, z + 1);
                    default -> new Vec3(x, y, z + 1);
                };
            }
        },
        EAST(1, 0, 0, 0.74f) {
            @Override
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x + 1, y, z + 1);
                    case 1 -> new Vec3(x + 1, y + 1, z + 1);
                    case 2 -> new Vec3(x + 1, y + 1, z);
                    default -> new Vec3(x + 1, y, z);
                };
            }
        };

        private final int dx;
        private final int dy;
        private final int dz;
        private final float brightness;

        FaceDirection(int dx, int dy, int dz, float brightness) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.brightness = brightness;
        }

        abstract Vec3 vertex(int x, int y, int z, int index);
    }
}
