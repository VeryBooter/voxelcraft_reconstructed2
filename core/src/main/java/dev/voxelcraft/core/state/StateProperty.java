package dev.voxelcraft.core.state;

import java.util.Objects;
/**
 * 中文说明：状态系统组件：定义 StateProperty 的状态数据与属性访问模型。
 */

// 中文标注（记录类）：`StateProperty`，职责：封装状态、属性相关逻辑。
// 中文标注（字段）：`name`，含义：用于表示名称。
// 中文标注（字段）：`type`，含义：用于表示类型。
// 中文标注（参数）：`name`，含义：用于表示名称。
// 中文标注（参数）：`type`，含义：用于表示类型。
public record StateProperty<T>(String name, Class<T> type) {
    // 中文标注（构造方法）：`StateProperty`，参数：name、type；用途：初始化`StateProperty`实例。
    public StateProperty {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Property name must not be blank");
        }
    }
}