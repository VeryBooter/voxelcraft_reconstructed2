package dev.voxelcraft.core.state;

import dev.voxelcraft.core.block.Block;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
/**
 * 中文说明：状态系统组件：定义 BlockState 的状态数据与属性访问模型。
 */

// 中文标注（类）：`BlockState`，职责：封装方块、状态相关逻辑。
public final class BlockState {
    // 中文标注（字段）：`block`，含义：用于表示方块。
    private final Block block;
    // 中文标注（字段）：`values`，含义：用于表示values。
    private final Map<StateProperty<?>, Object> values;

    // 中文标注（构造方法）：`BlockState`，参数：block、values；用途：初始化`BlockState`实例。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    // 中文标注（参数）：`values`，含义：用于表示values。
    public BlockState(Block block, Map<StateProperty<?>, Object> values) {
        this.block = Objects.requireNonNull(block, "block");
        this.values = Map.copyOf(values);
    }

    // 中文标注（方法）：`block`，参数：无；用途：执行方块相关逻辑。
    public Block block() {
        return block;
    }

    // 中文标注（方法）：`get`，参数：property；用途：获取或读取get。
    @SuppressWarnings("unchecked")
    // 中文标注（参数）：`property`，含义：用于表示属性。
    public <T> T get(StateProperty<T> property) {
        return (T) values.get(property);
    }

    // 中文标注（方法）：`with`，参数：property、value；用途：执行with相关逻辑。
    // 中文标注（参数）：`property`，含义：用于表示属性。
    // 中文标注（参数）：`value`，含义：用于表示值。
    public <T> BlockState with(StateProperty<T> property, T value) {
        // 中文标注（局部变量）：`next`，含义：用于表示next。
        HashMap<StateProperty<?>, Object> next = new HashMap<>(values);
        next.put(property, value);
        return new BlockState(block, next);
    }
}