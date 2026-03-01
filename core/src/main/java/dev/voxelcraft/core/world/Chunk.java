package dev.voxelcraft.core.world;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
/**
 * 中文说明：区块数据结构：管理 section 存储、方块访问、遍历与区块级版本。
 */

// 中文标注（类）：`Chunk`，职责：封装区块相关逻辑。
public final class Chunk {
    // 中文标注（接口）：`NonAirBlockConsumer`，职责：封装non、空气、方块、consumer相关逻辑。
    @FunctionalInterface
    public interface NonAirBlockConsumer {
        // 中文标注（方法）：`accept`，参数：localX、y、localZ、block；用途：执行accept相关逻辑。
        // 中文标注（参数）：`localX`，含义：用于表示局部、X坐标。
        // 中文标注（参数）：`y`，含义：用于表示Y坐标。
        // 中文标注（参数）：`localZ`，含义：用于表示局部、Z坐标。
        // 中文标注（参数）：`block`，含义：用于表示方块。
        void accept(int localX, int y, int localZ, Block block); // meaning
    }

    // 中文标注（字段）：`pos`，含义：用于表示位置。
    private final ChunkPos pos; // meaning
    // 中文标注（字段）：`sections`，含义：用于表示sections。
    private final Map<Integer, Section> sections = new HashMap<>(); // meaning
    // 中文标注（字段）：`version`，含义：用于表示版本。
    private volatile long version; // meaning

    // 中文标注（构造方法）：`Chunk`，参数：pos；用途：初始化`Chunk`实例。
    // 中文标注（参数）：`pos`，含义：用于表示位置。
    public Chunk(ChunkPos pos) {
        this.pos = Objects.requireNonNull(pos, "pos");
    }

    // 中文标注（方法）：`pos`，参数：无；用途：执行位置相关逻辑。
    public ChunkPos pos() {
        return pos;
    }

    // 中文标注（方法）：`version`，参数：无；用途：执行版本相关逻辑。
    public long version() {
        return version;
    }

    // 中文标注（方法）：`getBlock`，参数：localX、y、localZ；用途：获取或读取方块。
    // 中文标注（参数）：`localX`，含义：用于表示局部、X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`localZ`，含义：用于表示局部、Z坐标。
    public Block getBlock(int localX, int y, int localZ) {
        if (y < World.MIN_Y || y > World.MAX_Y) {
            return Blocks.AIR;
        }

        // 中文标注（局部变量）：`sectionY`，含义：用于表示分段、Y坐标。
        int sectionY = Math.floorDiv(y, Section.SIZE); // meaning
        // 中文标注（局部变量）：`section`，含义：用于表示分段。
        Section section = sections.get(sectionY); // meaning
        if (section == null) {
            return y <= World.DEFAULT_SOLID_BELOW_Y ? Blocks.STONE : Blocks.AIR;
        }

        // 中文标注（局部变量）：`localY`，含义：用于表示局部、Y坐标。
        int localY = Math.floorMod(y, Section.SIZE); // meaning
        return section.getBlock(localX, localY, localZ);
    }

    // 中文标注（方法）：`sectionOrNull`，参数：sectionY；用途：执行分段、or、null相关逻辑。
    // 中文标注（参数）：`sectionY`，含义：用于表示分段、Y坐标。
    public Section sectionOrNull(int sectionY) {
        return sections.get(sectionY);
    }

    // 中文标注（方法）：`setBlock`，参数：localX、y、localZ、block；用途：设置、写入或注册方块。
    // 中文标注（参数）：`localX`，含义：用于表示局部、X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`localZ`，含义：用于表示局部、Z坐标。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    public void setBlock(int localX, int y, int localZ, Block block) {
        if (y < World.MIN_Y || y > World.MAX_Y) {
            return;
        }

        // 中文标注（局部变量）：`sectionY`，含义：用于表示分段、Y坐标。
        int sectionY = Math.floorDiv(y, Section.SIZE); // meaning
        // 中文标注（局部变量）：`localY`，含义：用于表示局部、Y坐标。
        int localY = Math.floorMod(y, Section.SIZE); // meaning
        // 中文标注（Lambda参数）：`unused`，含义：用于表示unused。
        // 中文标注（局部变量）：`section`，含义：用于表示分段。
        Section section = sections.computeIfAbsent(sectionY, unused -> new Section()); // meaning
        // 中文标注（局部变量）：`previous`，含义：用于表示previous。
        Block previous = section.getBlock(localX, localY, localZ); // meaning
        section.setBlock(localX, localY, localZ, block);
        if (previous != block) {
            version++;
        }
    }

    // 中文标注（方法）：`fillSection`，参数：sectionY、block；用途：执行fill、分段相关逻辑。
    // 中文标注（参数）：`sectionY`，含义：用于表示分段、Y坐标。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    public void fillSection(int sectionY, Block block) {
        if (block == null) {
            throw new NullPointerException("block");
        }
        // 中文标注（Lambda参数）：`unused`，含义：用于表示unused。
        // 中文标注（局部变量）：`section`，含义：用于表示分段。
        Section section = sections.computeIfAbsent(sectionY, unused -> new Section()); // meaning
        // 中文标注（局部变量）：`previous`，含义：用于表示previous。
        Block previous = section.uniformBlock(); // meaning
        section.fill(block);
        if (previous != block) {
            version++;
        }
    }

    // 中文标注（方法）：`forEachNonAir`，参数：consumer；用途：执行for、each、non、空气相关逻辑。
    // 中文标注（参数）：`consumer`，含义：用于表示consumer。
    public void forEachNonAir(NonAirBlockConsumer consumer) {
        forEachNonAirInRange(World.MIN_Y, World.MAX_Y, consumer);
    }

    // 中文标注（方法）：`forEachNonAirInRange`，参数：minY、maxY、consumer；用途：执行for、each、non、空气、in、范围相关逻辑。
    // 中文标注（参数）：`minY`，含义：用于表示最小、Y坐标。
    // 中文标注（参数）：`maxY`，含义：用于表示最大、Y坐标。
    // 中文标注（参数）：`consumer`，含义：用于表示consumer。
    public void forEachNonAirInRange(int minY, int maxY, NonAirBlockConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (maxY < minY) {
            return;
        }
        // 中文标注（局部变量）：`entry`，含义：用于表示entry。
        for (Entry<Integer, Section> entry : sections.entrySet()) {
            // 中文标注（局部变量）：`sectionY`，含义：用于表示分段、Y坐标。
            int sectionY = entry.getKey(); // meaning
            // 中文标注（局部变量）：`section`，含义：用于表示分段。
            Section section = entry.getValue(); // meaning

            // 中文标注（局部变量）：`sectionMinY`，含义：用于表示分段、最小、Y坐标。
            int sectionMinY = sectionY * Section.SIZE; // meaning
            // 中文标注（局部变量）：`sectionMaxY`，含义：用于表示分段、最大、Y坐标。
            int sectionMaxY = sectionMinY + Section.SIZE - 1; // meaning
            if (sectionMaxY < minY || sectionMinY > maxY) {
                continue;
            }

            // 中文标注（局部变量）：`localYStart`，含义：用于表示局部、ystart。
            int localYStart = Math.max(0, minY - sectionMinY); // meaning
            // 中文标注（局部变量）：`localYEnd`，含义：用于表示局部、yend。
            int localYEnd = Math.min(Section.SIZE - 1, maxY - sectionMinY); // meaning
            if (localYStart > localYEnd) {
                continue;
            }

            if (section.isUniform()) {
                // 中文标注（局部变量）：`block`，含义：用于表示方块。
                Block block = section.uniformBlock(); // meaning
                if (block == Blocks.AIR) {
                    continue;
                }
                // 中文标注（局部变量）：`localY`，含义：用于表示局部、Y坐标。
                for (int localY = localYStart; localY <= localYEnd; localY++) { // meaning
                    // 中文标注（局部变量）：`worldY`，含义：用于表示世界、Y坐标。
                    int worldY = sectionMinY + localY; // meaning
                    // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
                    for (int localZ = 0; localZ < Section.SIZE; localZ++) { // meaning
                        // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
                        for (int localX = 0; localX < Section.SIZE; localX++) { // meaning
                            consumer.accept(localX, worldY, localZ, block);
                        }
                    }
                }
                continue;
            }

            // 中文标注（局部变量）：`localY`，含义：用于表示局部、Y坐标。
            for (int localY = localYStart; localY <= localYEnd; localY++) { // meaning
                // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
                for (int localZ = 0; localZ < Section.SIZE; localZ++) { // meaning
                    // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
                    for (int localX = 0; localX < Section.SIZE; localX++) { // meaning
                        // 中文标注（局部变量）：`block`，含义：用于表示方块。
                        Block block = section.getBlock(localX, localY, localZ); // meaning
                        if (block == Blocks.AIR) {
                            continue;
                        }
                        // 中文标注（局部变量）：`worldY`，含义：用于表示世界、Y坐标。
                        int worldY = sectionMinY + localY; // meaning
                        consumer.accept(localX, worldY, localZ, block);
                    }
                }
            }
        }
    }
}
