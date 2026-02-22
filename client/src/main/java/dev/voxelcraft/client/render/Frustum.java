package dev.voxelcraft.client.render;

public final class Frustum {
    private double cameraX;
    private double cameraY;
    private double cameraZ;

    private double yawRadians;
    private double pitchRadians;

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

        double cosYaw = Math.cos(yawRadians);
        double sinYaw = Math.sin(yawRadians);
        double x1 = dx * cosYaw - dz * sinYaw;
        double z1 = dx * sinYaw + dz * cosYaw;

        double cosPitch = Math.cos(-pitchRadians);
        double sinPitch = Math.sin(-pitchRadians);
        double y2 = dy * cosPitch - z1 * sinPitch;
        double z2 = dy * sinPitch + z1 * cosPitch;

        return new CameraPoint(x1, y2, z2);
    }

    public boolean isPointVisible(double worldX, double worldY, double worldZ, double radius) {
        CameraPoint cameraPoint = toCameraSpace(worldX, worldY, worldZ);
        if (cameraPoint.z < nearPlane - radius || cameraPoint.z > farPlane + radius) {
            return false;
        }

        double horizontalLimit = (cameraPoint.z + radius) * tanHalfHorizontalFov;
        double verticalLimit = (cameraPoint.z + radius) * tanHalfVerticalFov;
        return Math.abs(cameraPoint.x) <= horizontalLimit
            && Math.abs(cameraPoint.y) <= verticalLimit;
    }

    public boolean isAabbVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (cameraX >= minX && cameraX <= maxX
            && cameraY >= minY && cameraY <= maxY
            && cameraZ >= minZ && cameraZ <= maxZ) {
            return true;
        }

        return isPointVisible(minX, minY, minZ, 0.0)
            || isPointVisible(maxX, minY, minZ, 0.0)
            || isPointVisible(minX, maxY, minZ, 0.0)
            || isPointVisible(maxX, maxY, minZ, 0.0)
            || isPointVisible(minX, minY, maxZ, 0.0)
            || isPointVisible(maxX, minY, maxZ, 0.0)
            || isPointVisible(minX, maxY, maxZ, 0.0)
            || isPointVisible(maxX, maxY, maxZ, 0.0);
    }

    public double nearPlane() {
        return nearPlane;
    }

    public record CameraPoint(double x, double y, double z) {
    }
}
