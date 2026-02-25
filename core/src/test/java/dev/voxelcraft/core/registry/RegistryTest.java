package dev.voxelcraft.core.registry;

import dev.voxelcraft.core.util.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
/**
 * 中文说明：测试用例：用于验证 RegistryTest 相关行为与回归约束。
 */

// 中文标注（类）：`RegistryTest`，职责：用于测试与回归验证。
class RegistryTest {
    // 中文标注（方法）：`registerGetAndFreeze`，参数：无；用途：设置、写入或注册register、get、and、冻结。
    @Test
    void registerGetAndFreeze() {
        // 中文标注（局部变量）：`registry`，含义：用于表示注册表。
        Registry<String> registry = new Registry<>("test");
        registry.register(ResourceLocation.of("voxelcraft:demo"), "value");

        Assertions.assertEquals("value", registry.get(ResourceLocation.of("voxelcraft:demo")));

        registry.freeze();
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> registry.register(ResourceLocation.of("voxelcraft:next"), "x")
        );
    }
}