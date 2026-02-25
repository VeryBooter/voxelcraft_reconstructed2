package dev.voxelcraft.client.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.BufferUtils;
/**
 * 中文说明：渲染模块组件：提供 DirectByteBufferPool 的渲染相关数据结构或功能实现。
 */

// 中文标注（类）：`DirectByteBufferPool`，职责：封装direct、字节、缓冲区、池相关逻辑。
final class DirectByteBufferPool {
    // 中文标注（字段）：`MIN_CAPACITY_BYTES`，含义：用于表示最小、capacity、字节数据。
    private static final int MIN_CAPACITY_BYTES = 256;

    // 中文标注（字段）：`buckets`，含义：用于表示buckets。
    private final Map<Integer, ArrayDeque<ByteBuffer>> buckets = new HashMap<>();
    // 中文标注（字段）：`maxBuffersPerBucket`，含义：用于表示最大、buffers、per、bucket。
    private final int maxBuffersPerBucket;

    // 中文标注（构造方法）：`DirectByteBufferPool`，参数：maxBuffersPerBucket；用途：初始化`DirectByteBufferPool`实例。
    // 中文标注（参数）：`maxBuffersPerBucket`，含义：用于表示最大、buffers、per、bucket。
    DirectByteBufferPool(int maxBuffersPerBucket) {
        this.maxBuffersPerBucket = Math.max(1, maxBuffersPerBucket);
    }

    // 中文标注（方法）：`acquire`，参数：minBytes；用途：执行acquire相关逻辑。
    // 中文标注（参数）：`minBytes`，含义：用于表示最小、字节数据。
    synchronized ByteBuffer acquire(int minBytes) {
        // 中文标注（局部变量）：`capacity`，含义：用于表示capacity。
        int capacity = bucketCapacity(minBytes);
        // 中文标注（局部变量）：`bucket`，含义：用于表示bucket。
        ArrayDeque<ByteBuffer> bucket = buckets.get(capacity);
        // 中文标注（局部变量）：`buffer`，含义：用于表示缓冲区。
        ByteBuffer buffer = bucket == null ? null : bucket.pollFirst();
        if (buffer == null) {
            buffer = BufferUtils.createByteBuffer(capacity);
            buffer.order(ByteOrder.nativeOrder());
        }
        buffer.clear();
        return buffer;
    }

    // 中文标注（方法）：`release`，参数：buffer；用途：执行release相关逻辑。
    // 中文标注（参数）：`buffer`，含义：用于表示缓冲区。
    synchronized void release(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        // 中文标注（局部变量）：`capacity`，含义：用于表示capacity。
        int capacity = bucketCapacity(buffer.capacity());
        // 中文标注（Lambda参数）：`unused`，含义：用于表示unused。
        // 中文标注（局部变量）：`bucket`，含义：用于表示bucket。
        ArrayDeque<ByteBuffer> bucket = buckets.computeIfAbsent(capacity, unused -> new ArrayDeque<>());
        if (bucket.size() >= maxBuffersPerBucket) {
            return;
        }
        buffer.clear();
        bucket.addFirst(buffer);
    }

    // 中文标注（方法）：`clear`，参数：无；用途：执行clear相关逻辑。
    synchronized void clear() {
        buckets.clear();
    }

    // 中文标注（方法）：`bucketCapacity`，参数：minBytes；用途：执行bucket、capacity相关逻辑。
    // 中文标注（参数）：`minBytes`，含义：用于表示最小、字节数据。
    private static int bucketCapacity(int minBytes) {
        // 中文标注（局部变量）：`capacity`，含义：用于表示capacity。
        int capacity = Math.max(MIN_CAPACITY_BYTES, Math.max(0, minBytes));
        // 中文标注（局部变量）：`bucket`，含义：用于表示bucket。
        int bucket = 1;
        while (bucket < capacity) {
            bucket <<= 1;
        }
        return bucket;
    }
}