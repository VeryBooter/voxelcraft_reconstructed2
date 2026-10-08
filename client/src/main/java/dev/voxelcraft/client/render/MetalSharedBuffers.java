package dev.voxelcraft.client.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/** Buffers become reusable only after every GPU command reading them completes. */
final class MetalSharedBuffers implements MeshBufferAllocator, AutoCloseable {
    private static final long MAX_POOLED_BYTES = 64L * 1024 * 1024;
    private final long context;
    private final Map<ByteBuffer, Allocation> owned = new IdentityHashMap<>();
    private final ArrayList<Allocation> available = new ArrayList<>();
    private long pooledBytes;
    private long liveBytes;
    private long reuseCount;
    private boolean closed;

    MetalSharedBuffers(long context) { this.context = context; }

    @Override
    public synchronized ByteBuffer acquire(int minBytes) {
        if (closed) throw new IllegalStateException("Metal buffer allocator closed");
        if (minBytes <= 0 || minBytes > (1 << 29)) throw new IllegalArgumentException("Invalid mesh size: " + minBytes);
        int capacity = 256;
        while (capacity < minBytes) capacity <<= 1;
        Allocation allocation = null;
        for (int i = 0; i < available.size(); i++) {
            Allocation candidate = available.get(i);
            if (candidate.bytes.capacity() == capacity && MetalNative.idle(candidate.handle)) {
                allocation = available.remove(i);
                pooledBytes -= capacity;
                reuseCount++;
                break;
            }
        }
        if (allocation == null) {
            long handle = MetalNative.allocate(context, capacity);
            try {
                ByteBuffer bytes = MetalNative.contents(handle).order(ByteOrder.nativeOrder());
                allocation = new Allocation(handle, bytes);
                liveBytes += capacity;
            } catch (Throwable failure) {
                MetalNative.releaseBuffer(handle);
                throw failure;
            }
        }
        allocation.bytes.clear();
        owned.put(allocation.bytes, allocation);
        return allocation.bytes;
    }

    synchronized long handle(ByteBuffer bytes) {
        Allocation allocation = owned.get(bytes);
        if (allocation == null) throw new IllegalStateException("Buffer is no longer owned by this mesh");
        return allocation.handle;
    }

    @Override
    public synchronized void release(ByteBuffer bytes) {
        if (bytes == null) return;
        Allocation allocation = owned.remove(bytes);
        if (allocation == null) throw new IllegalStateException("Double release or foreign Metal buffer");
        if (!closed && pooledBytes + bytes.capacity() <= MAX_POOLED_BYTES) {
            available.add(allocation);
            pooledBytes += bytes.capacity();
        } else {
            dispose(allocation);
        }
    }

    synchronized String stats() {
        return "shared=" + (liveBytes / (1024 * 1024)) + "MiB reused=" + reuseCount;
    }

    private void dispose(Allocation allocation) {
        MetalNative.releaseBuffer(allocation.handle);
        liveBytes -= allocation.bytes.capacity();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        owned.values().forEach(this::dispose);
        available.forEach(this::dispose);
        owned.clear();
        available.clear();
        pooledBytes = 0;
    }

    private record Allocation(long handle, ByteBuffer bytes) { }
}
