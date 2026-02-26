package dev.voxelcraft.core.block;

import dev.voxelcraft.core.state.BlockState;
import dev.voxelcraft.core.util.ResourceLocation;
import java.util.Map;
import java.util.Objects;
/**
 * 中文说明：方块模块组件：定义 Block 的方块数据、注册或默认集合。
 */

// 中文标注（类）：`Block`，职责：封装方块相关逻辑。
public class Block {
    // 中文标注（字段）：`id`，含义：用于表示标识。
    private final ResourceLocation id; // meaning
    // 中文标注（字段）：`solid`，含义：用于表示实体。
    private final boolean solid; // meaning
    // 中文标注（字段）：`defaultState`，含义：用于表示默认、状态。
    private final BlockState defaultState; // meaning

    // 中文标注（构造方法）：`Block`，参数：id、solid；用途：初始化`Block`实例。
    // 中文标注（参数）：`id`，含义：用于表示标识。
    // 中文标注（参数）：`solid`，含义：用于表示实体。
    public Block(String id, boolean solid) {
        this(ResourceLocation.of(id), solid);
    }

    // 中文标注（构造方法）：`Block`，参数：id、solid；用途：初始化`Block`实例。
    // 中文标注（参数）：`id`，含义：用于表示标识。
    // 中文标注（参数）：`solid`，含义：用于表示实体。
    public Block(ResourceLocation id, boolean solid) {
        this.id = Objects.requireNonNull(id, "id");
        this.solid = solid;
        this.defaultState = new BlockState(this, Map.of());
    }

    // 中文标注（方法）：`id`，参数：无；用途：执行标识相关逻辑。
    public ResourceLocation id() {
        return id;
    }

    // 中文标注（方法）：`solid`，参数：无；用途：执行实体相关逻辑。
    public boolean solid() {
        return solid;
    }

    // 中文标注（方法）：`defaultState`，参数：无；用途：执行默认、状态相关逻辑。
    public BlockState defaultState() {
        return defaultState;
    }
}
