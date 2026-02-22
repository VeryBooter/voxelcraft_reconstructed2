package dev.voxelcraft.client.physics;

public record AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public boolean intersects(AABB other) {
        return minX < other.maxX && maxX > other.minX
            && minY < other.maxY && maxY > other.minY
            && minZ < other.maxZ && maxZ > other.minZ;
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public AABB moved(double dx, double dy, double dz) {
        return new AABB(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }
}
