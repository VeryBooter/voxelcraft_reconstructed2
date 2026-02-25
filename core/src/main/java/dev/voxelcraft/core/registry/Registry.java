package dev.voxelcraft.core.registry;

import dev.voxelcraft.core.util.ResourceLocation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
/**
 * 中文说明：注册表模块组件：负责 Registry 的注册、查询与生命周期管理。
 */

// 中文标注（类）：`Registry`，职责：封装注册表相关逻辑。
public final class Registry<T> {
    // 中文标注（字段）：`debugName`，含义：用于表示debug、名称。
    private final String debugName;
    // 中文标注（字段）：`entries`，含义：用于表示entries。
    private final Map<ResourceLocation, T> entries = new LinkedHashMap<>();
    // 中文标注（字段）：`frozen`，含义：用于表示frozen。
    private boolean frozen;

    // 中文标注（构造方法）：`Registry`，参数：debugName；用途：初始化`Registry`实例。
    // 中文标注（参数）：`debugName`，含义：用于表示debug、名称。
    public Registry(String debugName) {
        this.debugName = Objects.requireNonNull(debugName, "debugName");
    }

    // 中文标注（方法）：`register`，参数：id、value；用途：设置、写入或注册register。
    // 中文标注（参数）：`id`，含义：用于表示标识。
    // 中文标注（参数）：`value`，含义：用于表示值。
    public synchronized T register(ResourceLocation id, T value) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, "value");
        ensureMutable();
        if (entries.putIfAbsent(id, value) != null) {
            throw new IllegalStateException("Duplicate registry key in " + debugName + ": " + id);
        }
        return value;
    }

    // 中文标注（方法）：`get`，参数：id；用途：获取或读取get。
    // 中文标注（参数）：`id`，含义：用于表示标识。
    public synchronized T get(ResourceLocation id) {
        // 中文标注（局部变量）：`value`，含义：用于表示值。
        T value = entries.get(id);
        if (value == null) {
            throw new IllegalStateException("Missing registry key in " + debugName + ": " + id);
        }
        return value;
    }

    // 中文标注（方法）：`entries`，参数：无；用途：执行entries相关逻辑。
    public synchronized Map<ResourceLocation, T> entries() {
        return Collections.unmodifiableMap(entries);
    }

    // 中文标注（方法）：`freeze`，参数：无；用途：执行冻结相关逻辑。
    public synchronized void freeze() {
        this.frozen = true;
    }

    // 中文标注（方法）：`isFrozen`，参数：无；用途：判断frozen是否满足条件。
    public synchronized boolean isFrozen() {
        return frozen;
    }

    // 中文标注（方法）：`ensureMutable`，参数：无；用途：执行ensure、mutable相关逻辑。
    private void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException("Registry is frozen: " + debugName);
        }
    }
}