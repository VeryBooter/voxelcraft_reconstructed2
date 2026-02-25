package dev.voxelcraft.client.render;

import java.awt.Color;
import java.util.List;
/**
 * 中文说明：渲染模块组件：提供 Mesh 的渲染相关数据结构或功能实现。
 */

// 中文标注（类）：`Mesh`，职责：封装网格相关逻辑。
public final class Mesh {
    // 中文标注（字段）：`chunks`，含义：用于表示区块集合。
    private final List<ChunkBatch> chunks;
    // 中文标注（字段）：`faceCount`，含义：用于表示面、数量。
    private final int faceCount;

    // 中文标注（构造方法）：`Mesh`，参数：chunks；用途：初始化`Mesh`实例。
    // 中文标注（参数）：`chunks`，含义：用于表示区块集合。
    public Mesh(List<ChunkBatch> chunks) {
        this.chunks = List.copyOf(chunks);
        // 中文标注（局部变量）：`totalFaces`，含义：用于表示total、面集合。
        int totalFaces = 0;
        // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
        for (ChunkBatch chunk : this.chunks) {
            totalFaces += chunk.faceCount();
        }
        this.faceCount = totalFaces;
    }

    // 中文标注（方法）：`chunks`，参数：无；用途：执行区块集合相关逻辑。
    public List<ChunkBatch> chunks() {
        return chunks;
    }

    // 中文标注（方法）：`faceCount`，参数：无；用途：执行面、数量相关逻辑。
    public int faceCount() {
        return faceCount;
    }

    // 中文标注（方法）：`chunkCount`，参数：无；用途：执行区块、数量相关逻辑。
    public int chunkCount() {
        return chunks.size();
    }

    // 中文标注（记录类）：`Face`，职责：封装面相关逻辑。
    // 中文标注（字段）：`v0`，含义：用于表示v、0。
    // 中文标注（字段）：`v1`，含义：用于表示v、1。
    // 中文标注（字段）：`v2`，含义：用于表示v、2。
    // 中文标注（字段）：`v3`，含义：用于表示v、3。
    // 中文标注（字段）：`color`，含义：用于表示颜色。
    public record Face(Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3, Color color) {
        // 中文标注（方法）：`minX`，参数：无；用途：执行最小、X坐标相关逻辑。
        public double minX() {
            return Math.min(Math.min(v0.x(), v1.x()), Math.min(v2.x(), v3.x()));
        }

        // 中文标注（方法）：`minY`，参数：无；用途：执行最小、Y坐标相关逻辑。
        public double minY() {
            return Math.min(Math.min(v0.y(), v1.y()), Math.min(v2.y(), v3.y()));
        }

        // 中文标注（方法）：`minZ`，参数：无；用途：执行最小、Z坐标相关逻辑。
        public double minZ() {
            return Math.min(Math.min(v0.z(), v1.z()), Math.min(v2.z(), v3.z()));
        }

        // 中文标注（方法）：`maxX`，参数：无；用途：执行最大、X坐标相关逻辑。
        public double maxX() {
            return Math.max(Math.max(v0.x(), v1.x()), Math.max(v2.x(), v3.x()));
        }

        // 中文标注（方法）：`maxY`，参数：无；用途：执行最大、Y坐标相关逻辑。
        public double maxY() {
            return Math.max(Math.max(v0.y(), v1.y()), Math.max(v2.y(), v3.y()));
        }

        // 中文标注（方法）：`maxZ`，参数：无；用途：执行最大、Z坐标相关逻辑。
        public double maxZ() {
            return Math.max(Math.max(v0.z(), v1.z()), Math.max(v2.z(), v3.z()));
        }

        // 中文标注（方法）：`center`，参数：无；用途：执行center相关逻辑。
        public Vec3 center() {
            return new Vec3(
                (v0.x() + v1.x() + v2.x() + v3.x()) * 0.25,
                (v0.y() + v1.y() + v2.y() + v3.y()) * 0.25,
                (v0.z() + v1.z() + v2.z() + v3.z()) * 0.25
            );
        }
    }

    // 中文标注（记录类）：`ChunkBatch`，职责：封装区块、batch相关逻辑。
    public record ChunkBatch(
        // 中文标注（字段）：`chunkBaseX`，含义：用于表示区块、base、X坐标。
        // 中文标注（参数）：`chunkBaseX`，含义：用于表示区块、base、X坐标。
        int chunkBaseX,
        // 中文标注（字段）：`chunkBaseZ`，含义：用于表示区块、base、Z坐标。
        // 中文标注（参数）：`chunkBaseZ`，含义：用于表示区块、base、Z坐标。
        int chunkBaseZ,
        // 中文标注（字段）：`minY`，含义：用于表示最小、Y坐标。
        // 中文标注（参数）：`minY`，含义：用于表示最小、Y坐标。
        double minY,
        // 中文标注（字段）：`maxY`，含义：用于表示最大、Y坐标。
        // 中文标注（参数）：`maxY`，含义：用于表示最大、Y坐标。
        double maxY,
        // 中文标注（字段）：`faces`，含义：用于表示面集合。
        // 中文标注（参数）：`faces`，含义：用于表示面集合。
        List<Face> faces
    ) {
        // 中文标注（构造方法）：`ChunkBatch`，参数：chunkBaseX、chunkBaseZ、minY、maxY、faces；用途：初始化`ChunkBatch`实例。
        public ChunkBatch {
            faces = List.copyOf(faces);
        }

        // 中文标注（方法）：`faceCount`，参数：无；用途：执行面、数量相关逻辑。
        public int faceCount() {
            return faces.size();
        }

        // 中文标注（方法）：`minX`，参数：无；用途：执行最小、X坐标相关逻辑。
        public double minX() {
            return chunkBaseX;
        }

        // 中文标注（方法）：`maxX`，参数：无；用途：执行最大、X坐标相关逻辑。
        public double maxX() {
            return chunkBaseX + 16.0;
        }

        // 中文标注（方法）：`minZ`，参数：无；用途：执行最小、Z坐标相关逻辑。
        public double minZ() {
            return chunkBaseZ;
        }

        // 中文标注（方法）：`maxZ`，参数：无；用途：执行最大、Z坐标相关逻辑。
        public double maxZ() {
            return chunkBaseZ + 16.0;
        }
    }
}