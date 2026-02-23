package dev.voxelcraft.client.render;

import java.awt.Color;
import java.util.List;

public final class Mesh {
    private final List<ChunkBatch> chunks;
    private final int faceCount;

    public Mesh(List<ChunkBatch> chunks) {
        this.chunks = List.copyOf(chunks);
        int totalFaces = 0;
        for (ChunkBatch chunk : this.chunks) {
            totalFaces += chunk.faceCount();
        }
        this.faceCount = totalFaces;
    }

    public List<ChunkBatch> chunks() {
        return chunks;
    }

    public int faceCount() {
        return faceCount;
    }

    public int chunkCount() {
        return chunks.size();
    }

    public record Face(Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3, Color color) {
        public double minX() {
            return Math.min(Math.min(v0.x(), v1.x()), Math.min(v2.x(), v3.x()));
        }

        public double minY() {
            return Math.min(Math.min(v0.y(), v1.y()), Math.min(v2.y(), v3.y()));
        }

        public double minZ() {
            return Math.min(Math.min(v0.z(), v1.z()), Math.min(v2.z(), v3.z()));
        }

        public double maxX() {
            return Math.max(Math.max(v0.x(), v1.x()), Math.max(v2.x(), v3.x()));
        }

        public double maxY() {
            return Math.max(Math.max(v0.y(), v1.y()), Math.max(v2.y(), v3.y()));
        }

        public double maxZ() {
            return Math.max(Math.max(v0.z(), v1.z()), Math.max(v2.z(), v3.z()));
        }

        public Vec3 center() {
            return new Vec3(
                (v0.x() + v1.x() + v2.x() + v3.x()) * 0.25,
                (v0.y() + v1.y() + v2.y() + v3.y()) * 0.25,
                (v0.z() + v1.z() + v2.z() + v3.z()) * 0.25
            );
        }
    }

    public record ChunkBatch(
        int chunkBaseX,
        int chunkBaseZ,
        double minY,
        double maxY,
        List<Face> faces
    ) {
        public ChunkBatch {
            faces = List.copyOf(faces);
        }

        public int faceCount() {
            return faces.size();
        }

        public double minX() {
            return chunkBaseX;
        }

        public double maxX() {
            return chunkBaseX + 16.0;
        }

        public double minZ() {
            return chunkBaseZ;
        }

        public double maxZ() {
            return chunkBaseZ + 16.0;
        }
    }
}
