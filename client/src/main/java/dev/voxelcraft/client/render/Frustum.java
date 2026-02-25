package dev.voxelcraft.client.render;
/**
 * 中文说明：视锥体工具：负责相机空间变换与点/AABB 可见性测试（带热路径优化）。
 */

// 中文标注（类）：`Frustum`，职责：封装视锥体相关逻辑。
public final class Frustum {
    // 中文标注（字段）：`cameraX`，含义：用于表示相机、X坐标。
    private double cameraX;
    // 中文标注（字段）：`cameraY`，含义：用于表示相机、Y坐标。
    private double cameraY;
    // 中文标注（字段）：`cameraZ`，含义：用于表示相机、Z坐标。
    private double cameraZ;

    // 中文标注（字段）：`yawRadians`，含义：用于表示yaw、radians。
    private double yawRadians;
    // 中文标注（字段）：`pitchRadians`，含义：用于表示pitch、radians。
    private double pitchRadians;
    // 中文标注（字段）：`cosYaw`，含义：用于表示cos、yaw。
    private double cosYaw;
    // 中文标注（字段）：`sinYaw`，含义：用于表示sin、yaw。
    private double sinYaw;
    // 中文标注（字段）：`cosPitch`，含义：用于表示cos、pitch。
    private double cosPitch;
    // 中文标注（字段）：`sinPitch`，含义：用于表示sin、pitch。
    private double sinPitch;

    // 中文标注（字段）：`nearPlane`，含义：用于表示near、plane。
    private double nearPlane;
    // 中文标注（字段）：`farPlane`，含义：用于表示far、plane。
    private double farPlane;
    // 中文标注（字段）：`tanHalfVerticalFov`，含义：用于表示tan、half、垂直、fov。
    private double tanHalfVerticalFov;
    // 中文标注（字段）：`tanHalfHorizontalFov`，含义：用于表示tan、half、水平、fov。
    private double tanHalfHorizontalFov;

    // 中文标注（方法）：`setCamera`，参数：cameraX、cameraY、cameraZ、yawDegrees、pitchDegrees、verticalFovDegrees、aspect、nearPlane、farPlane；用途：设置、写入或注册相机。
    public void setCamera(
        // 中文标注（参数）：`cameraX`，含义：用于表示相机、X坐标。
        double cameraX,
        // 中文标注（参数）：`cameraY`，含义：用于表示相机、Y坐标。
        double cameraY,
        // 中文标注（参数）：`cameraZ`，含义：用于表示相机、Z坐标。
        double cameraZ,
        // 中文标注（参数）：`yawDegrees`，含义：用于表示yaw、degrees。
        float yawDegrees,
        // 中文标注（参数）：`pitchDegrees`，含义：用于表示pitch、degrees。
        float pitchDegrees,
        // 中文标注（参数）：`verticalFovDegrees`，含义：用于表示垂直、fov、degrees。
        float verticalFovDegrees,
        // 中文标注（参数）：`aspect`，含义：用于表示aspect。
        double aspect,
        // 中文标注（参数）：`nearPlane`，含义：用于表示near、plane。
        double nearPlane,
        // 中文标注（参数）：`farPlane`，含义：用于表示far、plane。
        double farPlane
    ) {
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.yawRadians = Math.toRadians(yawDegrees);
        this.pitchRadians = Math.toRadians(pitchDegrees);
        // yaw/pitch 的三角函数在 setCamera 时缓存，避免点/AABB 热路径重复 trig 计算。
        this.cosYaw = Math.cos(yawRadians);
        this.sinYaw = Math.sin(yawRadians);
        this.cosPitch = Math.cos(-pitchRadians);
        this.sinPitch = Math.sin(-pitchRadians);
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;

        // 中文标注（局部变量）：`halfFovRadians`，含义：用于表示half、fov、radians。
        double halfFovRadians = Math.toRadians(verticalFovDegrees) * 0.5;
        this.tanHalfVerticalFov = Math.tan(halfFovRadians);
        this.tanHalfHorizontalFov = tanHalfVerticalFov * Math.max(0.1, aspect);
    }

    // 中文标注（方法）：`toCameraSpace`，参数：worldX、worldY、worldZ；用途：进行转换或编解码：相机、space。
    // 中文标注（参数）：`worldX`，含义：用于表示世界、X坐标。
    // 中文标注（参数）：`worldY`，含义：用于表示世界、Y坐标。
    // 中文标注（参数）：`worldZ`，含义：用于表示世界、Z坐标。
    public CameraPoint toCameraSpace(double worldX, double worldY, double worldZ) {
        // 中文标注（局部变量）：`dx`，含义：用于表示dx。
        double dx = worldX - cameraX;
        // 中文标注（局部变量）：`dy`，含义：用于表示dy。
        double dy = worldY - cameraY;
        // 中文标注（局部变量）：`dz`，含义：用于表示dz。
        double dz = worldZ - cameraZ;

        // 中文标注（局部变量）：`x1`，含义：用于表示X坐标、1。
        double x1 = dx * cosYaw - dz * sinYaw;
        // 中文标注（局部变量）：`z1`，含义：用于表示Z坐标、1。
        double z1 = dx * sinYaw + dz * cosYaw;

        // 中文标注（局部变量）：`y2`，含义：用于表示Y坐标、2。
        double y2 = dy * cosPitch - z1 * sinPitch;
        // 中文标注（局部变量）：`z2`，含义：用于表示Z坐标、2。
        double z2 = dy * sinPitch + z1 * cosPitch;

        return new CameraPoint(x1, y2, z2);
    }

    // 中文标注（方法）：`toCameraSpace`，参数：worldX、worldY、worldZ、out；用途：进行转换或编解码：相机、space。
    // 中文标注（参数）：`worldX`，含义：用于表示世界、X坐标。
    // 中文标注（参数）：`worldY`，含义：用于表示世界、Y坐标。
    // 中文标注（参数）：`worldZ`，含义：用于表示世界、Z坐标。
    // 中文标注（参数）：`out`，含义：用于表示out。
    public void toCameraSpace(double worldX, double worldY, double worldZ, CameraPoint out) {
        // 中文标注（局部变量）：`dx`，含义：用于表示dx。
        double dx = worldX - cameraX;
        // 中文标注（局部变量）：`dy`，含义：用于表示dy。
        double dy = worldY - cameraY;
        // 中文标注（局部变量）：`dz`，含义：用于表示dz。
        double dz = worldZ - cameraZ;

        // 中文标注（局部变量）：`x1`，含义：用于表示X坐标、1。
        double x1 = dx * cosYaw - dz * sinYaw;
        // 中文标注（局部变量）：`z1`，含义：用于表示Z坐标、1。
        double z1 = dx * sinYaw + dz * cosYaw;

        // 中文标注（局部变量）：`y2`，含义：用于表示Y坐标、2。
        double y2 = dy * cosPitch - z1 * sinPitch;
        // 中文标注（局部变量）：`z2`，含义：用于表示Z坐标、2。
        double z2 = dy * sinPitch + z1 * cosPitch;

        out.set(x1, y2, z2);
    }

    // 中文标注（方法）：`isPointVisible`，参数：worldX、worldY、worldZ、radius；用途：判断point、visible是否满足条件。
    // 中文标注（参数）：`worldX`，含义：用于表示世界、X坐标。
    // 中文标注（参数）：`worldY`，含义：用于表示世界、Y坐标。
    // 中文标注（参数）：`worldZ`，含义：用于表示世界、Z坐标。
    // 中文标注（参数）：`radius`，含义：用于表示radius。
    public boolean isPointVisible(double worldX, double worldY, double worldZ, double radius) {
        // 中文标注（局部变量）：`dx`，含义：用于表示dx。
        double dx = worldX - cameraX;
        // 中文标注（局部变量）：`dy`，含义：用于表示dy。
        double dy = worldY - cameraY;
        // 中文标注（局部变量）：`dz`，含义：用于表示dz。
        double dz = worldZ - cameraZ;

        // 中文标注（局部变量）：`x1`，含义：用于表示X坐标、1。
        double x1 = dx * cosYaw - dz * sinYaw;
        // 中文标注（局部变量）：`z1`，含义：用于表示Z坐标、1。
        double z1 = dx * sinYaw + dz * cosYaw;

        // 中文标注（局部变量）：`y2`，含义：用于表示Y坐标、2。
        double y2 = dy * cosPitch - z1 * sinPitch;
        // 中文标注（局部变量）：`z2`，含义：用于表示Z坐标、2。
        double z2 = dy * sinPitch + z1 * cosPitch;

        if (z2 < nearPlane - radius || z2 > farPlane + radius) {
            return false;
        }

        // 中文标注（局部变量）：`horizontalLimit`，含义：用于表示水平、limit。
        double horizontalLimit = (z2 + radius) * tanHalfHorizontalFov;
        // 中文标注（局部变量）：`verticalLimit`，含义：用于表示垂直、limit。
        double verticalLimit = (z2 + radius) * tanHalfVerticalFov;
        return Math.abs(x1) <= horizontalLimit
            && Math.abs(y2) <= verticalLimit;
    }

    // 中文标注（方法）：`isAabbVisible`，参数：minX、minY、minZ、maxX、maxY、maxZ；用途：判断包围盒、visible是否满足条件。
    // 中文标注（参数）：`minX`，含义：用于表示最小、X坐标。
    // 中文标注（参数）：`minY`，含义：用于表示最小、Y坐标。
    // 中文标注（参数）：`minZ`，含义：用于表示最小、Z坐标。
    // 中文标注（参数）：`maxX`，含义：用于表示最大、X坐标。
    // 中文标注（参数）：`maxY`，含义：用于表示最大、Y坐标。
    // 中文标注（参数）：`maxZ`，含义：用于表示最大、Z坐标。
    public boolean isAabbVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (cameraX >= minX && cameraX <= maxX
            && cameraY >= minY && cameraY <= maxY
            && cameraZ >= minZ && cameraZ <= maxZ) {
            return true;
        }

        // 保守的 AABB 视锥拒绝测试：
        // transform the 8 AABB corners into camera space (no allocations, cached trig),
        // then reject only if all corners lie outside the same frustum plane.
        // 中文标注（局部变量）：`OUT_LEFT`，含义：用于表示out、left。
        final int OUT_LEFT = 1 << 0;
        // 中文标注（局部变量）：`OUT_RIGHT`，含义：用于表示out、right。
        final int OUT_RIGHT = 1 << 1;
        // 中文标注（局部变量）：`OUT_BOTTOM`，含义：用于表示out、底面。
        final int OUT_BOTTOM = 1 << 2;
        // 中文标注（局部变量）：`OUT_TOP`，含义：用于表示out、顶面。
        final int OUT_TOP = 1 << 3;
        // 中文标注（局部变量）：`OUT_NEAR`，含义：用于表示out、near。
        final int OUT_NEAR = 1 << 4;
        // 中文标注（局部变量）：`OUT_FAR`，含义：用于表示out、far。
        final int OUT_FAR = 1 << 5;

        // 中文标注（局部变量）：`andMask`，含义：用于表示and、掩码。
        int andMask = OUT_LEFT | OUT_RIGHT | OUT_BOTTOM | OUT_TOP | OUT_NEAR | OUT_FAR;

        // 中文标注（局部变量）：`ix`，含义：用于表示ix。
        for (int ix = 0; ix < 2; ix++) {
            // 中文标注（局部变量）：`worldX`，含义：用于表示世界、X坐标。
            double worldX = ix == 0 ? minX : maxX;
            // 中文标注（局部变量）：`iy`，含义：用于表示iy。
            for (int iy = 0; iy < 2; iy++) {
                // 中文标注（局部变量）：`worldY`，含义：用于表示世界、Y坐标。
                double worldY = iy == 0 ? minY : maxY;
                // 中文标注（局部变量）：`iz`，含义：用于表示iz。
                for (int iz = 0; iz < 2; iz++) {
                    // 中文标注（局部变量）：`worldZ`，含义：用于表示世界、Z坐标。
                    double worldZ = iz == 0 ? minZ : maxZ;

                    // 中文标注（局部变量）：`dx`，含义：用于表示dx。
                    double dx = worldX - cameraX;
                    // 中文标注（局部变量）：`dy`，含义：用于表示dy。
                    double dy = worldY - cameraY;
                    // 中文标注（局部变量）：`dz`，含义：用于表示dz。
                    double dz = worldZ - cameraZ;

                    // 中文标注（局部变量）：`x1`，含义：用于表示X坐标、1。
                    double x1 = dx * cosYaw - dz * sinYaw;
                    // 中文标注（局部变量）：`z1`，含义：用于表示Z坐标、1。
                    double z1 = dx * sinYaw + dz * cosYaw;

                    // 中文标注（局部变量）：`y2`，含义：用于表示Y坐标、2。
                    double y2 = dy * cosPitch - z1 * sinPitch;
                    // 中文标注（局部变量）：`z2`，含义：用于表示Z坐标、2。
                    double z2 = dy * sinPitch + z1 * cosPitch;

                    // 中文标注（局部变量）：`mask`，含义：用于表示掩码。
                    int mask = 0;
                    if (x1 + z2 * tanHalfHorizontalFov < 0.0) {
                        mask |= OUT_LEFT;
                    }
                    if (-x1 + z2 * tanHalfHorizontalFov < 0.0) {
                        mask |= OUT_RIGHT;
                    }
                    if (y2 + z2 * tanHalfVerticalFov < 0.0) {
                        mask |= OUT_BOTTOM;
                    }
                    if (-y2 + z2 * tanHalfVerticalFov < 0.0) {
                        mask |= OUT_TOP;
                    }
                    if (z2 < nearPlane) {
                        mask |= OUT_NEAR;
                    }
                    if (z2 > farPlane) {
                        mask |= OUT_FAR;
                    }

                    if (mask == 0) {
                        return true;
                    }
                    andMask &= mask;
                    if (andMask == 0) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // 中文标注（方法）：`isPointVisibleNoRadius`，参数：worldX、worldY、worldZ；用途：判断point、visible、no、radius是否满足条件。
    // 中文标注（参数）：`worldX`，含义：用于表示世界、X坐标。
    // 中文标注（参数）：`worldY`，含义：用于表示世界、Y坐标。
    // 中文标注（参数）：`worldZ`，含义：用于表示世界、Z坐标。
    private boolean isPointVisibleNoRadius(double worldX, double worldY, double worldZ) {
        // 中文标注（局部变量）：`dx`，含义：用于表示dx。
        double dx = worldX - cameraX;
        // 中文标注（局部变量）：`dy`，含义：用于表示dy。
        double dy = worldY - cameraY;
        // 中文标注（局部变量）：`dz`，含义：用于表示dz。
        double dz = worldZ - cameraZ;

        // 中文标注（局部变量）：`x1`，含义：用于表示X坐标、1。
        double x1 = dx * cosYaw - dz * sinYaw;
        // 中文标注（局部变量）：`z1`，含义：用于表示Z坐标、1。
        double z1 = dx * sinYaw + dz * cosYaw;

        // 中文标注（局部变量）：`y2`，含义：用于表示Y坐标、2。
        double y2 = dy * cosPitch - z1 * sinPitch;
        // 中文标注（局部变量）：`z2`，含义：用于表示Z坐标、2。
        double z2 = dy * sinPitch + z1 * cosPitch;

        if (z2 < nearPlane || z2 > farPlane) {
            return false;
        }

        // 中文标注（局部变量）：`horizontalLimit`，含义：用于表示水平、limit。
        double horizontalLimit = z2 * tanHalfHorizontalFov;
        // 中文标注（局部变量）：`verticalLimit`，含义：用于表示垂直、limit。
        double verticalLimit = z2 * tanHalfVerticalFov;
        return Math.abs(x1) <= horizontalLimit
            && Math.abs(y2) <= verticalLimit;
    }

    // 中文标注（方法）：`nearPlane`，参数：无；用途：执行near、plane相关逻辑。
    public double nearPlane() {
        return nearPlane;
    }

    // 中文标注（类）：`CameraPoint`，职责：封装相机、point相关逻辑。
    public static final class CameraPoint {
        // 中文标注（字段）：`x`，含义：用于表示X坐标。
        public double x;
        // 中文标注（字段）：`y`，含义：用于表示Y坐标。
        public double y;
        // 中文标注（字段）：`z`，含义：用于表示Z坐标。
        public double z;

        // 中文标注（构造方法）：`CameraPoint`，参数：无；用途：初始化`CameraPoint`实例。
        public CameraPoint() {
        }

        // 中文标注（构造方法）：`CameraPoint`，参数：x、y、z；用途：初始化`CameraPoint`实例。
        // 中文标注（参数）：`x`，含义：用于表示X坐标。
        // 中文标注（参数）：`y`，含义：用于表示Y坐标。
        // 中文标注（参数）：`z`，含义：用于表示Z坐标。
        public CameraPoint(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        // 中文标注（方法）：`set`，参数：x、y、z；用途：设置、写入或注册集合。
        // 中文标注（参数）：`x`，含义：用于表示X坐标。
        // 中文标注（参数）：`y`，含义：用于表示Y坐标。
        // 中文标注（参数）：`z`，含义：用于表示Z坐标。
        private void set(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        // 中文标注（方法）：`x`，参数：无；用途：执行X坐标相关逻辑。
        public double x() {
            return x;
        }

        // 中文标注（方法）：`y`，参数：无；用途：执行Y坐标相关逻辑。
        public double y() {
            return y;
        }

        // 中文标注（方法）：`z`，参数：无；用途：执行Z坐标相关逻辑。
        public double z() {
            return z;
        }
    }
}