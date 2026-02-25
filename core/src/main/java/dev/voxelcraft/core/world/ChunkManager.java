package dev.voxelcraft.core.world;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import java.util.Collections;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
/**
 * 中文说明：世界模块组件：提供 ChunkManager 的坐标、区块或世界访问能力。
 */

// 中文标注（类）：`ChunkManager`，职责：封装区块、管理器相关逻辑。
public final class ChunkManager {
    // 中文标注（字段）：`chunks`，含义：用于表示区块集合。
    private final Map<Long, Chunk> chunks = new ConcurrentHashMap<>();

    // 中文标注（方法）：`getOrCreateChunk`，参数：chunkX、chunkZ；用途：获取或读取or、创建、区块。
    // 中文标注（参数）：`chunkX`，含义：用于表示区块、X坐标。
    // 中文标注（参数）：`chunkZ`，含义：用于表示区块、Z坐标。
    public Chunk getOrCreateChunk(int chunkX, int chunkZ) {
        // 中文标注（局部变量）：`key`，含义：用于表示键。
        long key = chunkKey(chunkX, chunkZ);
        return chunks.computeIfAbsent(key, unused -> new Chunk(new ChunkPos(chunkX, chunkZ)));
    }

    // 中文标注（方法）：`getChunk`，参数：chunkX、chunkZ；用途：获取或读取区块。
    // 中文标注（参数）：`chunkX`，含义：用于表示区块、X坐标。
    // 中文标注（参数）：`chunkZ`，含义：用于表示区块、Z坐标。
    public Chunk getChunk(int chunkX, int chunkZ) {
        return chunks.get(chunkKey(chunkX, chunkZ));
    }

    // 中文标注（方法）：`installChunkIfAbsent`，参数：chunk；用途：执行install、区块、if、absent相关逻辑。
    // 中文标注（参数）：`chunk`，含义：用于表示区块。
    public Chunk installChunkIfAbsent(Chunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        // 中文标注（局部变量）：`key`，含义：用于表示键。
        long key = chunkKey(chunk.pos().x(), chunk.pos().z());
        // 中文标注（局部变量）：`existing`，含义：用于表示existing。
        Chunk existing = chunks.putIfAbsent(key, chunk);
        return existing == null ? chunk : existing;
    }

    // 中文标注（方法）：`getBlock`，参数：pos；用途：获取或读取方块。
    // 中文标注（参数）：`pos`，含义：用于表示位置。
    public Block getBlock(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
        int chunkX = Math.floorDiv(pos.x(), Section.SIZE);
        // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
        int chunkZ = Math.floorDiv(pos.z(), Section.SIZE);
        // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
        Chunk chunk = getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return Blocks.AIR;
        }

        // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
        int localX = Math.floorMod(pos.x(), Section.SIZE);
        // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
        int localZ = Math.floorMod(pos.z(), Section.SIZE);
        return chunk.getBlock(localX, pos.y(), localZ);
    }

    // 中文标注（方法）：`setBlock`，参数：pos、block；用途：设置、写入或注册方块。
    // 中文标注（参数）：`pos`，含义：用于表示位置。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    public void setBlock(BlockPos pos, Block block) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(block, "block");
        // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
        int chunkX = Math.floorDiv(pos.x(), Section.SIZE);
        // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
        int chunkZ = Math.floorDiv(pos.z(), Section.SIZE);
        // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
        int localX = Math.floorMod(pos.x(), Section.SIZE);
        // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
        int localZ = Math.floorMod(pos.z(), Section.SIZE);

        // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
        Chunk chunk = getOrCreateChunk(chunkX, chunkZ);
        chunk.setBlock(localX, pos.y(), localZ, block);
    }

    // 中文标注（方法）：`chunks`，参数：无；用途：执行区块集合相关逻辑。
    public Collection<Chunk> chunks() {
        return Collections.unmodifiableCollection(chunks.values());
    }

    // 中文标注（方法）：`chunkKey`，参数：chunkX、chunkZ；用途：执行区块、键相关逻辑。
    // 中文标注（参数）：`chunkX`，含义：用于表示区块、X坐标。
    // 中文标注（参数）：`chunkZ`，含义：用于表示区块、Z坐标。
    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xffff_ffffL);
    }
}
