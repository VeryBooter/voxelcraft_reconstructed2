package dev.voxelcraft.core.block;

public record MeshProfileDef(
    BlockDef.MeshProfile meshProfile,
    BlockDef.AlphaMode defaultAlphaMode,
    BlockDef.OcclusionMode defaultOcclusionMode,
    BlockDef.CollisionKind defaultCollisionKind,
    String defaultAabbMin,
    String defaultAabbMax,
    int defaultAttachFacesMask,
    boolean defaultRequiresSupport,
    boolean defaultDoubleSided
) {
}
