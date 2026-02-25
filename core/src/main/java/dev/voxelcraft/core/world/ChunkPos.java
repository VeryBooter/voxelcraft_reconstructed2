package dev.voxelcraft.core.world;
/**
 * 中文说明：世界模块组件：提供 ChunkPos 的坐标、区块或世界访问能力。
 */

// 中文标注（记录类）：`ChunkPos`，职责：封装区块、位置相关逻辑。
// 中文标注（字段）：`x`，含义：用于表示X坐标。
// 中文标注（字段）：`z`，含义：用于表示Z坐标。
public record ChunkPos(int x, int z) {
}