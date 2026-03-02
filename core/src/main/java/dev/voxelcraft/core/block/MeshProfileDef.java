package dev.voxelcraft.core.block;

// 中文标注：本文件已标记。

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
