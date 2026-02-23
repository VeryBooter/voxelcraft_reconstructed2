package dev.voxelcraft.client.render;

public final class Frustum {
    private double cameraX;
    private double cameraY;
    private double cameraZ;

    private double yawRadians;
    private double pitchRadians;
    private double cosYaw;
    private double sinYaw;
    private double cosPitch;
    private double sinPitch;

    private double nearPlane;
    private double farPlane;
    private double tanHalfVerticalFov;
    private double tanHalfHorizontalFov;

    public void setCamera(
        double cameraX,
        double cameraY,
        double cameraZ,
        float yawDegrees,
        float pitchDegrees,
        float verticalFovDegrees,
        double aspect,
        double nearPlane,
        double farPlane
    ) {
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.yawRadians = Math.toRadians(yawDegrees);
        this.pitchRadians = Math.toRadians(pitchDegrees);
        this.cosYaw = Math.cos(yawRadians);
        this.sinYaw = Math.sin(yawRadians);
        this.cosPitch = Math.cos(-pitchRadians);
        this.sinPitch = Math.sin(-pitchRadians);
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;

        double halfFovRadians = Math.toRadians(verticalFovDegrees) * 0.5;
        this.tanHalfVerticalFov = Math.tan(halfFovRadians);
        this.tanHalfHorizontalFov = tanHalfVerticalFov * Math.max(0.1, aspect);
    }

    public CameraPoint toCameraSpace(double worldX, double worldY, double worldZ) {
        double dx = worldX - cameraX;
        double dy = worldY - cameraY;
        double dz = worldZ - cameraZ;

        double x1 = dx * cosYaw - dz * sinYaw;
        double z1 = dx * sinYaw + dz * cosYaw;

        double y2 = dy * cosPitch - z1 * sinPitch;
        double z2 = dy * sinPitch + z1 * cosPitch;

        return new CameraPoint(x1, y2, z2);
    }

    public void toCameraSpace(double worldX, double worldY, double worldZ, CameraPoint out) {
        double dx = worldX - cameraX;
        double dy = worldY - cameraY;
        double dz = worldZ - cameraZ;

        double x1 = dx * cosYaw - dz * sinYaw;
        double z1 = dx * sinYaw + dz * cosYaw;

        double y2 = dy * cosPitch - z1 * sinPitch;
        double z2 = dy * sinPitch + z1 * cosPitch;

        out.set(x1, y2, z2);
    }

    public boolean isPointVisible(double worldX, double worldY, double worldZ, double radius) {
        double dx = worldX - cameraX;
        double dy = worldY - cameraY;
        double dz = worldZ - cameraZ;

        double x1 = dx * cosYaw - dz * sinYaw;
        double z1 = dx * sinYaw + dz * cosYaw;

        double y2 = dy * cosPitch - z1 * sinPitch;
        double z2 = dy * sinPitch + z1 * cosPitch;

        if (z2 < nearPlane - radius || z2 > farPlane + radius) {
            return false;
        }

        double horizontalLimit = (z2 + radius) * tanHalfHorizontalFov;
        double verticalLimit = (z2 + radius) * tanHalfVerticalFov;
        return Math.abs(x1) <= horizontalLimit
            && Math.abs(y2) <= verticalLimit;
    }

    public boolean isAabbVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (cameraX >= minX && cameraX <= maxX
            && cameraY >= minY && cameraY <= maxY
            && cameraZ >= minZ && cameraZ <= maxZ) {
            return true;
        }

        // Conservative frustum-vs-AABB test in camera space using center/extents.
        // This avoids the false negatives of the old "any corner visible" test.
        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        double halfX = (maxX - minX) * 0.5;
        double halfY = (maxY - minY) * 0.5;
        double halfZ = (maxZ - minZ) * 0.5;

        double dx = centerX - cameraX;
        double dy = centerY - cameraY;
        double dz = centerZ - cameraZ;

        double x1 = dx * cosYaw - dz * sinYaw;
        double z1 = dx * sinYaw + dz * cosYaw;

        double y2 = dy * cosPitch - z1 * sinPitch;
        double z2 = dy * sinPitch + z1 * cosPitch;

        // Extents projected onto camera axes (rows of the world->camera rotation).
        double radiusX = Math.abs(cosYaw) * halfX + Math.abs(sinYaw) * halfZ;
        double radiusY =
            Math.abs(sinPitch * sinYaw) * halfX
                + Math.abs(cosPitch) * halfY
                + Math.abs(sinPitch * cosYaw) * halfZ;
        double radiusZ =
            Math.abs(cosPitch * sinYaw) * halfX
                + Math.abs(sinPitch) * halfY
                + Math.abs(cosPitch * cosYaw) * halfZ;

        double zMin = z2 - radiusZ;
        double zMax = z2 + radiusZ;
        if (zMax < nearPlane || zMin > farPlane) {
            return false;
        }

        double horizontalSlop = radiusX + radiusZ * tanHalfHorizontalFov;
        double verticalSlop = radiusY + radiusZ * tanHalfVerticalFov;

        if (x1 - z2 * tanHalfHorizontalFov > horizontalSlop) {
            return false;
        }
        if (-x1 - z2 * tanHalfHorizontalFov > horizontalSlop) {
            return false;
        }
        if (y2 - z2 * tanHalfVerticalFov > verticalSlop) {
            return false;
        }
        if (-y2 - z2 * tanHalfVerticalFov > verticalSlop) {
            return false;
        }

        return true;
    }

    private boolean isPointVisibleNoRadius(double worldX, double worldY, double worldZ) {
        double dx = worldX - cameraX;
        double dy = worldY - cameraY;
        double dz = worldZ - cameraZ;

        double x1 = dx * cosYaw - dz * sinYaw;
        double z1 = dx * sinYaw + dz * cosYaw;

        double y2 = dy * cosPitch - z1 * sinPitch;
        double z2 = dy * sinPitch + z1 * cosPitch;

        if (z2 < nearPlane || z2 > farPlane) {
            return false;
        }

        double horizontalLimit = z2 * tanHalfHorizontalFov;
        double verticalLimit = z2 * tanHalfVerticalFov;
        return Math.abs(x1) <= horizontalLimit
            && Math.abs(y2) <= verticalLimit;
    }

    public double nearPlane() {
        return nearPlane;
    }

    public static final class CameraPoint {
        public double x;
        public double y;
        public double z;

        public CameraPoint() {
        }

        public CameraPoint(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private void set(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }
    }
}
