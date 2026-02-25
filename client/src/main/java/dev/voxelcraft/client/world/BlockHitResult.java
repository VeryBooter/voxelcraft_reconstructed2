package dev.voxelcraft.client.world;

import dev.voxelcraft.core.world.BlockPos;
/**
 * 中文说明：世界模块组件：提供 BlockHitResult 的坐标、区块或世界访问能力。
 */

// 中文标注（记录类）：`BlockHitResult`，职责：封装方块、命中、结果相关逻辑。
// 中文标注（字段）：`targetBlock`，含义：用于表示target、方块。
// 中文标注（字段）：`placementBlock`，含义：用于表示placement、方块。
// 中文标注（字段）：`distance`，含义：用于表示distance。
public record BlockHitResult(BlockPos targetBlock, BlockPos placementBlock, double distance) {
}