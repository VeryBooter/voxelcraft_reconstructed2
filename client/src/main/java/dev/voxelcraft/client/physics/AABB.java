package dev.voxelcraft.client.physics;
/**
 * 中文说明：物理辅助组件：定义 AABB 的几何边界或碰撞计算基础结构。
 */

// 中文标注（记录类）：`AABB`，职责：封装包围盒相关逻辑。
// 中文标注（字段）：`minX`，含义：用于表示最小、X坐标。
// 中文标注（字段）：`minY`，含义：用于表示最小、Y坐标。
// 中文标注（字段）：`minZ`，含义：用于表示最小、Z坐标。
// 中文标注（字段）：`maxX`，含义：用于表示最大、X坐标。
// 中文标注（字段）：`maxY`，含义：用于表示最大、Y坐标。
// 中文标注（字段）：`maxZ`，含义：用于表示最大、Z坐标。
public record AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    // 中文标注（方法）：`intersects`，参数：other；用途：执行intersects相关逻辑。
    // 中文标注（参数）：`other`，含义：用于表示other。
    public boolean intersects(AABB other) {
        return minX < other.maxX && maxX > other.minX
            && minY < other.maxY && maxY > other.minY
            && minZ < other.maxZ && maxZ > other.minZ;
    }

    // 中文标注（方法）：`contains`，参数：x、y、z；用途：判断contains是否满足条件。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    // 中文标注（方法）：`moved`，参数：dx、dy、dz；用途：执行moved相关逻辑。
    // 中文标注（参数）：`dx`，含义：用于表示dx。
    // 中文标注（参数）：`dy`，含义：用于表示dy。
    // 中文标注（参数）：`dz`，含义：用于表示dz。
    public AABB moved(double dx, double dy, double dz) {
        return new AABB(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }
}