package dev.voxelcraft.core;

import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.World;
import dev.voxelcraft.core.world.WorldStack;
import java.util.SplittableRandom;
/**
 * 中文说明：核心模块组件：提供 Game 的基础数据结构与规则实现。
 */

// 中文标注（类）：`Game`，职责：封装game相关逻辑。
public final class Game {
    // 中文标注（字段）：`worldStack`，含义：用于表示世界切片栈。
    private final WorldStack worldStack; // meaning
    // 中文标注（字段）：`activeW`，含义：用于表示当前激活的离散切片索引。
    private int activeW; // meaning

    // 中文标注（构造方法）：`Game`，参数：无；用途：初始化`Game`实例。
    public Game() {
        Blocks.bootstrap();
        long baseSeed = new SplittableRandom().nextLong(); // meaning
        this.worldStack = new WorldStack(baseSeed);
        this.activeW = 0;
    }

    // 中文标注（方法）：`world`，参数：无；用途：执行世界相关逻辑。
    public World world() {
        return worldStack.get(activeW);
    }

    public WorldStack worldStack() {
        return worldStack;
    }

    public int w() {
        return activeW;
    }

    public void switchW(int newW) {
        activeW = newW;
        worldStack.get(activeW);
    }

    public int activeW() {
        return w();
    }

    public void switchSlice(int newW) {
        switchW(newW);
    }

    // 中文标注（方法）：`tick`，参数：无；用途：更新刻相关状态。
    public void tick() {
        world().tick();
    }
}
