package dev.voxelcraft.client.render;

import java.nio.ByteBuffer;

/** Owns the native storage used for mesh output; ownership transfers with ChunkMeshData. */
public interface MeshBufferAllocator {
    ByteBuffer acquire(int minBytes);
    void release(ByteBuffer buffer);
}
