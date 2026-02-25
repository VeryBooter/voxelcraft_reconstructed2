package dev.voxelcraft.client.render;
/**
 * 中文说明：渲染模块组件：提供 Vec3 的渲染相关数据结构或功能实现。
 */

// 中文标注（记录类）：`Vec3`，职责：封装vec、3相关逻辑。
// 中文标注（字段）：`x`，含义：用于表示X坐标。
// 中文标注（字段）：`y`，含义：用于表示Y坐标。
// 中文标注（字段）：`z`，含义：用于表示Z坐标。
public record Vec3(double x, double y, double z) {
    // 中文标注（方法）：`add`，参数：other；用途：执行add相关逻辑。
    // 中文标注（参数）：`other`，含义：用于表示other。
    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    // 中文标注（方法）：`subtract`，参数：other；用途：执行subtract相关逻辑。
    // 中文标注（参数）：`other`，含义：用于表示other。
    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    // 中文标注（方法）：`multiply`，参数：scalar；用途：执行multiply相关逻辑。
    // 中文标注（参数）：`scalar`，含义：用于表示scalar。
    public Vec3 multiply(double scalar) {
        return new Vec3(x * scalar, y * scalar, z * scalar);
    }

    // 中文标注（方法）：`dot`，参数：other；用途：执行dot相关逻辑。
    // 中文标注（参数）：`other`，含义：用于表示other。
    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    // 中文标注（方法）：`length`，参数：无；用途：执行长度相关逻辑。
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    // 中文标注（方法）：`normalized`，参数：无；用途：执行normalized相关逻辑。
    public Vec3 normalized() {
        // 中文标注（局部变量）：`length`，含义：用于表示长度。
        double length = length();
        if (length == 0.0) {
            return new Vec3(0.0, 0.0, 0.0);
        }
        return new Vec3(x / length, y / length, z / length);
    }
}