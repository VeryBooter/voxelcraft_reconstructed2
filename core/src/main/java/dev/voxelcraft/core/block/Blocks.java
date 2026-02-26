package dev.voxelcraft.core.block;

import dev.voxelcraft.core.registry.Registries;
import dev.voxelcraft.core.util.ResourceLocation;
import java.util.Objects;
/**
 * 中文说明：方块模块组件：定义 Blocks 的方块数据、注册或默认集合。
 */

// 中文标注（类）：`Blocks`，职责：封装方块集合相关逻辑。
public final class Blocks {
    // 中文标注（字段）：`bootstrapped`，含义：用于表示bootstrapped。
    private static boolean bootstrapped; // meaning

    // 中文标注（字段）：`AIR`，含义：用于表示空气。
    public static Block AIR; // meaning
    // 中文标注（字段）：`STONE`，含义：用于表示石头。
    public static Block STONE; // meaning
    // 中文标注（字段）：`DIRT`，含义：用于表示泥土。
    public static Block DIRT; // meaning
    // 中文标注（字段）：`GRASS`，含义：用于表示草地。
    public static Block GRASS; // meaning
    // 中文标注（字段）：`SAND`，含义：用于表示沙子。
    public static Block SAND; // meaning
    // 中文标注（字段）：`WOOD`，含义：用于表示木头。
    public static Block WOOD; // meaning
    // 中文标注（字段）：`LEAVES`，含义：用于表示树叶。
    public static Block LEAVES; // meaning

    // 中文标注（构造方法）：`Blocks`，参数：无；用途：初始化`Blocks`实例。
    private Blocks() {
    }

    // 中文标注（方法）：`bootstrap`，参数：无；用途：执行引导初始化相关逻辑。
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }

        AIR = register(new Block("voxelcraft:air", false));
        STONE = register(new Block("voxelcraft:stone", true));
        DIRT = register(new Block("voxelcraft:dirt", true));
        GRASS = register(new Block("voxelcraft:grass", true));
        SAND = register(new Block("voxelcraft:sand", true));
        WOOD = register(new Block("voxelcraft:wood", true));
        LEAVES = register(new Block("voxelcraft:leaves", true));

        Registries.BLOCKS.freeze();
        bootstrapped = true;
    }

    // 中文标注（方法）：`register`，参数：block；用途：设置、写入或注册register。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    private static Block register(Block block) {
        return Registries.BLOCKS.register(block.id(), block);
    }

    // 中文标注（方法）：`byIdOrAir`，参数：id；用途：获取或读取by、标识、or、空气。
    // 中文标注（参数）：`id`，含义：用于表示标识。
    public static Block byIdOrAir(String id) {
        Objects.requireNonNull(id, "id");
        bootstrap();
        // 中文标注（局部变量）：`found`，含义：用于表示found。
        Block found = Registries.BLOCKS.entries().get(ResourceLocation.of(id)); // meaning
        return found == null ? AIR : found;
    }
}
