package dev.voxelcraft.client.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.BufferUtils;

final class DirectByteBufferPool {
    private static final int MIN_CAPACITY_BYTES = 256;

    private final Map<Integer, ArrayDeque<ByteBuffer>> buckets = new HashMap<>();
    private final int maxBuffersPerBucket;

    DirectByteBufferPool(int maxBuffersPerBucket) {
        this.maxBuffersPerBucket = Math.max(1, maxBuffersPerBucket);
    }

    synchronized ByteBuffer acquire(int minBytes) {
        int capacity = bucketCapacity(minBytes);
        ArrayDeque<ByteBuffer> bucket = buckets.get(capacity);
        ByteBuffer buffer = bucket == null ? null : bucket.pollFirst();
        if (buffer == null) {
            buffer = BufferUtils.createByteBuffer(capacity);
            buffer.order(ByteOrder.nativeOrder());
        }
        buffer.clear();
        return buffer;
    }

    synchronized void release(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        int capacity = bucketCapacity(buffer.capacity());
        ArrayDeque<ByteBuffer> bucket = buckets.computeIfAbsent(capacity, unused -> new ArrayDeque<>());
        if (bucket.size() >= maxBuffersPerBucket) {
            return;
        }
        buffer.clear();
        bucket.addFirst(buffer);
    }

    synchronized void clear() {
        buckets.clear();
    }

    private static int bucketCapacity(int minBytes) {
        int capacity = Math.max(MIN_CAPACITY_BYTES, Math.max(0, minBytes));
        int bucket = 1;
        while (bucket < capacity) {
            bucket <<= 1;
        }
        return bucket;
    }
}
