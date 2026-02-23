package dev.voxelcraft.client.render;

public final class Frustum {
    private double cameraX;
    private double cameraY;
    private double cameraZ;

    private double yawRadians;
    private double pitchRadians;
    private double cosYaw;
    private double sinYaw;
    private double cosNegPitch;
    private double sinNegPitch;

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
        this.cosNegPitch = Math.cos(-pitchRadians);
        this.sinNegPitch = Math.sin(-pitchRadians);
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

        double y2 = dy * cosNegPitch - z1 * sinNegPitch;
        double z2 = dy * sinNegPitch + z1 * cosNegPitch;

        return new CameraPoint(x1, y2, z2);
    }

    public void toCameraSpace(double worldX, double worldY, double worldZ, MutableCameraPoint out) {
        double dx = worldX - cameraX;
        double dy = worldY - cameraY;
        double dz = worldZ - cameraZ;

        double x1 = dx * cosYaw - dz * sinYaw;
        double z1 = dx * sinYaw + dz * cosYaw;

        double y2 = dy * cosNegPitch - z1 * sinNegPitch;
        double z2 = dy * sinNegPitch + z1 * cosNegPitch;

        out.set(x1, y2, z2);
    }

    public boolean isPointVisible(double worldX, double worldY, double worldZ, double radius) {
        double dx = worldX - cameraX;
        double dy = worldY - cameraY;
        double dz = worldZ - cameraZ;

        double x1 = dx * cosYaw - dz * sinYaw;
        double z1 = dx * sinYaw + dz * cosYaw;

        double y2 = dy * cosNegPitch - z1 * sinNegPitch;
        double z2 = dy * sinNegPitch + z1 * cosNegPitch;

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

        return isPointVisibleNoRadius(minX, minY, minZ)
            || isPointVisibleNoRadius(maxX, minY, minZ)
            || isPointVisibleNoRadius(minX, maxY, minZ)
            || isPointVisibleNoRadius(maxX, maxY, minZ)
            || isPointVisibleNoRadius(minX, minY, maxZ)
            || isPointVisibleNoRadius(maxX, minY, maxZ)
            || isPointVisibleNoRadius(minX, maxY, maxZ)
            || isPointVisibleNoRadius(maxX, maxY, maxZ);
    }

    private boolean isPointVisibleNoRadius(double worldX, double worldY, double worldZ) {
        double dx = worldX - cameraX;
        double dy = worldY - cameraY;
        double dz = worldZ - cameraZ;

        double x1 = dx * cosYaw - dz * sinYaw;
        double z1 = dx * sinYaw + dz * cosYaw;

        double y2 = dy * cosNegPitch - z1 * sinNegPitch;
        double z2 = dy * sinNegPitch + z1 * cosNegPitch;

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

    public record CameraPoint(double x, double y, double z) {
    }

    public static final class MutableCameraPoint {
        private double x;
        private double y;
        private double z;

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
