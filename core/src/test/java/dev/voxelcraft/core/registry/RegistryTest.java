package dev.voxelcraft.core.registry;

import dev.voxelcraft.core.util.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RegistryTest {
    @Test
    void registerGetAndFreeze() {
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
