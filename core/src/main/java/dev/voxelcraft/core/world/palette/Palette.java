package dev.voxelcraft.core.world.palette;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/**
 * 中文说明：调色板压缩组件：提供 Palette 的存储压缩或索引映射能力。
 */

// 中文标注（类）：`Palette`，职责：封装palette相关逻辑。
public final class Palette<T> {
    // 中文标注（字段）：`valueToId`，含义：用于表示值、to、标识。
    private final Map<T, Integer> valueToId = new HashMap<>();
    // 中文标注（字段）：`idToValue`，含义：用于表示标识、to、值。
    private final List<T> idToValue = new ArrayList<>();

    // 中文标注（方法）：`idFor`，参数：value；用途：执行标识、for相关逻辑。
    // 中文标注（参数）：`value`，含义：用于表示值。
    public int idFor(T value) {
        Objects.requireNonNull(value, "value");
        // 中文标注（局部变量）：`existing`，含义：用于表示existing。
        Integer existing = valueToId.get(value);
        if (existing != null) {
            return existing;
        }
        // 中文标注（局部变量）：`id`，含义：用于表示标识。
        int id = idToValue.size();
        idToValue.add(value);
        valueToId.put(value, id);
        return id;
    }

    // 中文标注（方法）：`valueFor`，参数：id；用途：执行值、for相关逻辑。
    // 中文标注（参数）：`id`，含义：用于表示标识。
    public T valueFor(int id) {
        if (id < 0 || id >= idToValue.size()) {
            throw new IllegalArgumentException("Invalid palette id: " + id);
        }
        return idToValue.get(id);
    }

    // 中文标注（方法）：`size`，参数：无；用途：执行大小相关逻辑。
    public int size() {
        return idToValue.size();
    }
}