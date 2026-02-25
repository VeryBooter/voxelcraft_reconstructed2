package dev.voxelcraft.client.light;

import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.world.World;
/**
 * 中文说明：光照模块组件：负责 LightEngine 的光照估算或明暗计算逻辑。
 */

// 中文标注（类）：`LightEngine`，职责：封装光照、engine相关逻辑。
public final class LightEngine {
    // 中文标注（字段）：`ambient`，含义：用于表示环境光。
    private float ambient = 1.0f;

    // 中文标注（方法）：`tick`，参数：worldView；用途：更新刻相关状态。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    public void tick(ClientWorldView worldView) {
        // 中文标注（局部变量）：`ticks`，含义：用于表示ticks。
        long ticks = worldView.world().ticks();
        // 中文标注（局部变量）：`dayPhase`，含义：用于表示day、phase。
        double dayPhase = (ticks % 24_000L) / 24_000.0;
        // 中文标注（局部变量）：`wave`，含义：用于表示wave。
        double wave = Math.cos(dayPhase * Math.PI * 2.0);
        ambient = (float) (0.6 + 0.4 * ((wave + 1.0) * 0.5));
    }

    // 中文标注（方法）：`isWithinWorldY`，参数：y；用途：判断within、世界、Y坐标是否满足条件。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    public boolean isWithinWorldY(int y) {
        return y >= World.MIN_Y && y <= World.MAX_Y;
    }

    // 中文标注（方法）：`ambient`，参数：无；用途：执行环境光相关逻辑。
    public float ambient() {
        return ambient;
    }
}