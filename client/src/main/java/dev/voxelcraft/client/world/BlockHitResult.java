package dev.voxelcraft.client.world;

import dev.voxelcraft.core.world.BlockPos;

public record BlockHitResult(BlockPos targetBlock, BlockPos placementBlock, double distance) {
}
