package dev.voxelcraft.core.world.gen;

import dev.voxelcraft.core.world.Chunk;
/**
 * 中文说明：世界生成模块组件：提供 WorldGenerator 的生成规则或采样能力。
 */

// 中文标注（接口）：`WorldGenerator`，职责：封装世界、生成器相关逻辑。
public interface WorldGenerator {
    // 中文标注（方法）：`generate`，参数：chunk；用途：执行generate相关逻辑。
    // 中文标注（参数）：`chunk`，含义：用于表示区块。
    void generate(Chunk chunk);
}