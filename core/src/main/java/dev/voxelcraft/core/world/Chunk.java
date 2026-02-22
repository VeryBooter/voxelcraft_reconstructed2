package dev.voxelcraft.core.world;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Chunk {
    @FunctionalInterface
    public interface NonAirBlockConsumer {
        void accept(int localX, int y, int localZ, Block block);
    }

    private final ChunkPos pos;
    private final Map<Integer, Section> sections = new HashMap<>();

    public Chunk(ChunkPos pos) {
        this.pos = Objects.requireNonNull(pos, "pos");
    }

    public ChunkPos pos() {
        return pos;
    }

    public Block getBlock(int localX, int y, int localZ) {
        if (y < World.MIN_Y || y > World.MAX_Y) {
            return Blocks.AIR;
        }

        int sectionY = Math.floorDiv(y, Section.SIZE);
        Section section = sections.get(sectionY);
        if (section == null) {
            return Blocks.AIR;
        }

        int localY = Math.floorMod(y, Section.SIZE);
        return section.getBlock(localX, localY, localZ);
    }

    public void setBlock(int localX, int y, int localZ, Block block) {
        if (y < World.MIN_Y || y > World.MAX_Y) {
            return;
        }

        int sectionY = Math.floorDiv(y, Section.SIZE);
        int localY = Math.floorMod(y, Section.SIZE);
        Section section = sections.computeIfAbsent(sectionY, unused -> new Section());
        section.setBlock(localX, localY, localZ, block);
    }

    public void fillSection(int sectionY, Block block) {
        if (block == null) {
            throw new NullPointerException("block");
        }
        Section section = sections.computeIfAbsent(sectionY, unused -> new Section());
        section.fill(block);
    }

    public void forEachNonAir(NonAirBlockConsumer consumer) {
        forEachNonAirInRange(World.MIN_Y, World.MAX_Y, consumer);
    }

    public void forEachNonAirInRange(int minY, int maxY, NonAirBlockConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (maxY < minY) {
            return;
        }
        for (Entry<Integer, Section> entry : sections.entrySet()) {
            int sectionY = entry.getKey();
            Section section = entry.getValue();

            int sectionMinY = sectionY * Section.SIZE;
            int sectionMaxY = sectionMinY + Section.SIZE - 1;
            if (sectionMaxY < minY || sectionMinY > maxY) {
                continue;
            }

            int localYStart = Math.max(0, minY - sectionMinY);
            int localYEnd = Math.min(Section.SIZE - 1, maxY - sectionMinY);
            if (localYStart > localYEnd) {
                continue;
            }

            if (section.isUniform()) {
                Block block = section.uniformBlock();
                if (block == Blocks.AIR) {
                    continue;
                }
                for (int localY = localYStart; localY <= localYEnd; localY++) {
                    int worldY = sectionMinY + localY;
                    for (int localZ = 0; localZ < Section.SIZE; localZ++) {
                        for (int localX = 0; localX < Section.SIZE; localX++) {
                            consumer.accept(localX, worldY, localZ, block);
                        }
                    }
                }
                continue;
            }

            for (int localY = localYStart; localY <= localYEnd; localY++) {
                for (int localZ = 0; localZ < Section.SIZE; localZ++) {
                    for (int localX = 0; localX < Section.SIZE; localX++) {
                        Block block = section.getBlock(localX, localY, localZ);
                        if (block == Blocks.AIR) {
                            continue;
                        }
                        int worldY = sectionMinY + localY;
                        consumer.accept(localX, worldY, localZ, block);
                    }
                }
            }
        }
    }
}
