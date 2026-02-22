package dev.voxelcraft.core.block;

import dev.voxelcraft.core.registry.Registries;
import dev.voxelcraft.core.util.ResourceLocation;
import java.util.Objects;

public final class Blocks {
    private static boolean bootstrapped;

    public static Block AIR;
    public static Block STONE;
    public static Block DIRT;
    public static Block GRASS;
    public static Block SAND;
    public static Block WOOD;
    public static Block LEAVES;

    private Blocks() {
    }

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

    private static Block register(Block block) {
        return Registries.BLOCKS.register(block.id(), block);
    }

    public static Block byIdOrAir(String id) {
        Objects.requireNonNull(id, "id");
        bootstrap();
        Block found = Registries.BLOCKS.entries().get(ResourceLocation.of(id));
        return found == null ? AIR : found;
    }
}
