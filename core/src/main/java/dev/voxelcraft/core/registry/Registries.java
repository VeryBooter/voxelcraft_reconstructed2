package dev.voxelcraft.core.registry;

import dev.voxelcraft.core.block.Block;
/**
 * 中文说明：注册表模块组件：负责 Registries 的注册、查询与生命周期管理。
 */

// 中文标注（类）：`Registries`，职责：封装注册表集合相关逻辑。
public final class Registries {
    // 中文标注（字段）：`BLOCKS`，含义：用于表示方块集合。
    public static final Registry<Block> BLOCKS = new Registry<>("blocks"); // meaning

    // 中文标注（构造方法）：`Registries`，参数：无；用途：初始化`Registries`实例。
    private Registries() {
    }
}
