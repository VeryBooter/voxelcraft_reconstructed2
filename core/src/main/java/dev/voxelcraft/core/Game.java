package dev.voxelcraft.core;

import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.World;
/**
 * 中文说明：核心模块组件：提供 Game 的基础数据结构与规则实现。
 */

// 中文标注（类）：`Game`，职责：封装game相关逻辑。
public final class Game {
    // 中文标注（字段）：`world`，含义：用于表示世界。
    private final World world;

    // 中文标注（构造方法）：`Game`，参数：无；用途：初始化`Game`实例。
    public Game() {
        Blocks.bootstrap();
        this.world = new World();
    }

    // 中文标注（方法）：`world`，参数：无；用途：执行世界相关逻辑。
    public World world() {
        return world;
    }

    // 中文标注（方法）：`tick`，参数：无；用途：更新刻相关状态。
    public void tick() {
        world.tick();
    }
}