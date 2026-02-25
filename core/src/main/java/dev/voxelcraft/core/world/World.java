package dev.voxelcraft.core.world;

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.gen.FlatWorldGenerator;
import dev.voxelcraft.core.world.gen.WorldGenerator;
import java.util.Collection;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;
/**
 * 中文说明：世界对象：管理区块容器、生成器、方块读写与全局版本号。
 */

// 中文标注（类）：`World`，职责：封装世界相关逻辑。
public final class World {
    // 中文标注（字段）：`MIN_Y`，含义：用于表示最小、Y坐标。
    public static final int MIN_Y = -2048;
    // 中文标注（字段）：`MAX_Y`，含义：用于表示最大、Y坐标。
    public static final int MAX_Y = 319;
    // 中文标注（字段）：`DEFAULT_SOLID_BELOW_Y`，含义：用于表示默认、实体、below、Y坐标。
    public static final int DEFAULT_SOLID_BELOW_Y = -16;

    // 中文标注（字段）：`chunkManager`，含义：用于表示区块、管理器。
    private final ChunkManager chunkManager = new ChunkManager();
    // 中文标注（字段）：`worldGenerator`，含义：用于表示世界、生成器。
    private final WorldGenerator worldGenerator;
    // 中文标注（字段）：`seed`，含义：用于表示seed。
    private final long seed;
    // 中文标注（字段）：`ticks`，含义：用于表示ticks。
    private long ticks;
    // 中文标注（字段）：`blockUpdateVersion`，含义：用于表示方块、更新、版本。
    private final AtomicLong blockUpdateVersion = new AtomicLong();

    // 中文标注（构造方法）：`World`，参数：无；用途：初始化`World`实例。
    public World() {
        this(new SplittableRandom().nextLong());
    }

    // 中文标注（构造方法）：`World`，参数：seed；用途：初始化`World`实例。
    // 中文标注（参数）：`seed`，含义：用于表示seed。
    public World(long seed) {
        Blocks.bootstrap();
        this.seed = seed;
        this.worldGenerator = new FlatWorldGenerator(seed);
        // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
        for (int chunkX = -2; chunkX <= 2; chunkX++) {
            // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
            for (int chunkZ = -2; chunkZ <= 2; chunkZ++) {
                ensureChunkGenerated(chunkX, chunkZ);
            }
        }
        blockUpdateVersion.set(0L);
    }

    // 中文标注（方法）：`tick`，参数：无；用途：更新刻相关状态。
    public void tick() {
        ticks++;
    }

    // 中文标注（方法）：`ticks`，参数：无；用途：更新ticks相关状态。
    public long ticks() {
        return ticks;
    }

    // 中文标注（方法）：`seed`，参数：无；用途：执行seed相关逻辑。
    public long seed() {
        return seed;
    }

    // 中文标注（方法）：`chunkManager`，参数：无；用途：执行区块、管理器相关逻辑。
    public ChunkManager chunkManager() {
        return chunkManager;
    }

    // 中文标注（方法）：`getBlock`，参数：pos；用途：获取或读取方块。
    // 中文标注（参数）：`pos`，含义：用于表示位置。
    public Block getBlock(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        return getBlock(pos.x(), pos.y(), pos.z());
    }

    // 中文标注（方法）：`getBlock`，参数：x、y、z；用途：获取或读取方块。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    public Block getBlock(int x, int y, int z) {
        if (!isWithinWorldY(y)) {
            return Blocks.AIR;
        }
        // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
        Chunk chunk = ensureChunkGenerated(
            Math.floorDiv(x, Section.SIZE),
            Math.floorDiv(z, Section.SIZE)
        );

        // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
        int localX = Math.floorMod(x, Section.SIZE);
        // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
        int localZ = Math.floorMod(z, Section.SIZE);
        return chunk.getBlock(localX, y, localZ);
    }

    // 中文标注（方法）：`peekBlock`，参数：pos；用途：执行peek、方块相关逻辑。
    // 中文标注（参数）：`pos`，含义：用于表示位置。
    public Block peekBlock(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        return peekBlock(pos.x(), pos.y(), pos.z());
    }

    // 中文标注（方法）：`peekBlock`，参数：x、y、z；用途：执行peek、方块相关逻辑。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    public Block peekBlock(int x, int y, int z) {
        if (!isWithinWorldY(y)) {
            return Blocks.AIR;
        }

        // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
        int chunkX = Math.floorDiv(x, Section.SIZE);
        // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
        int chunkZ = Math.floorDiv(z, Section.SIZE);
        // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
        Chunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return Blocks.AIR;
        }

        // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
        int localX = Math.floorMod(x, Section.SIZE);
        // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
        int localZ = Math.floorMod(z, Section.SIZE);
        return chunk.getBlock(localX, y, localZ);
    }

    // 中文标注（方法）：`setBlock`，参数：pos、block；用途：设置、写入或注册方块。
    // 中文标注（参数）：`pos`，含义：用于表示位置。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    public boolean setBlock(BlockPos pos, Block block) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(block, "block");
        return setBlock(pos.x(), pos.y(), pos.z(), block);
    }

    // 中文标注（方法）：`setBlock`，参数：x、y、z、block；用途：设置、写入或注册方块。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    public boolean setBlock(int x, int y, int z, Block block) {
        Objects.requireNonNull(block, "block");
        if (!isWithinWorldY(y)) {
            return false;
        }
        // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
        int chunkX = Math.floorDiv(x, Section.SIZE);
        // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
        int chunkZ = Math.floorDiv(z, Section.SIZE);
        // 中文标注（局部变量）：`chunk`，含义：用于表示区块。
        Chunk chunk = ensureChunkGenerated(
            chunkX,
            chunkZ
        );
        // 中文标注（局部变量）：`localX`，含义：用于表示局部、X坐标。
        int localX = Math.floorMod(x, Section.SIZE);
        // 中文标注（局部变量）：`localZ`，含义：用于表示局部、Z坐标。
        int localZ = Math.floorMod(z, Section.SIZE);
        if (chunk.getBlock(localX, y, localZ) == block) {
            return false;
        }
        chunk.setBlock(localX, y, localZ, block);
        blockUpdateVersion.incrementAndGet();
        return true;
    }

    // 中文标注（方法）：`isWithinWorldY`，参数：y；用途：判断within、世界、Y坐标是否满足条件。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    public boolean isWithinWorldY(int y) {
        return y >= MIN_Y && y <= MAX_Y;
    }

    // 中文标注（方法）：`blockUpdateVersion`，参数：无；用途：执行方块、更新、版本相关逻辑。
    public long blockUpdateVersion() {
        return blockUpdateVersion.get();
    }

    // 中文标注（方法）：`loadedChunks`，参数：无；用途：获取或读取loaded、区块集合。
    public Collection<Chunk> loadedChunks() {
        return chunkManager.chunks();
    }

    // 中文标注（方法）：`getOrGenerateChunk`，参数：chunkX、chunkZ；用途：获取或读取or、generate、区块。
    // 中文标注（参数）：`chunkX`，含义：用于表示区块、X坐标。
    // 中文标注（参数）：`chunkZ`，含义：用于表示区块、Z坐标。
    public Chunk getOrGenerateChunk(int chunkX, int chunkZ) {
        return ensureChunkGenerated(chunkX, chunkZ);
    }

    // 中文标注（方法）：`generateChunkDetached`，参数：chunkX、chunkZ；用途：执行generate、区块、detached相关逻辑。
    // 中文标注（参数）：`chunkX`，含义：用于表示区块、X坐标。
    // 中文标注（参数）：`chunkZ`，含义：用于表示区块、Z坐标。
    public Chunk generateChunkDetached(int chunkX, int chunkZ) {
        // 中文标注（局部变量）：`detached`，含义：用于表示detached。
        Chunk detached = new Chunk(new ChunkPos(chunkX, chunkZ));
        worldGenerator.generate(detached);
        return detached;
    }

    // 中文标注（方法）：`installGeneratedChunkIfAbsent`，参数：chunk；用途：执行install、generated、区块、if、absent相关逻辑。
    // 中文标注（参数）：`chunk`，含义：用于表示区块。
    public boolean installGeneratedChunkIfAbsent(Chunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        // 中文标注（局部变量）：`installed`，含义：用于表示installed。
        Chunk installed = chunkManager.installChunkIfAbsent(chunk);
        if (installed != chunk) {
            return false;
        }
        blockUpdateVersion.incrementAndGet();
        return true;
    }

    // 中文标注（方法）：`ensureChunkGenerated`，参数：chunkX、chunkZ；用途：执行ensure、区块、generated相关逻辑。
    // 中文标注（参数）：`chunkX`，含义：用于表示区块、X坐标。
    // 中文标注（参数）：`chunkZ`，含义：用于表示区块、Z坐标。
    private Chunk ensureChunkGenerated(int chunkX, int chunkZ) {
        // 中文标注（局部变量）：`existing`，含义：用于表示existing。
        Chunk existing = chunkManager.getChunk(chunkX, chunkZ);
        if (existing != null) {
            return existing;
        }
        // 不要把“空 chunk”提前暴露到 chunkManager：先 detached 生成，再 install-if-absent。
        // 这样可避免半生成状态被读到，也避免竞态下把已安装 chunk 再次生成覆盖。
        // 中文标注（局部变量）：`generated`，含义：用于表示generated。
        Chunk generated = generateChunkDetached(chunkX, chunkZ);
        // 中文标注（局部变量）：`installed`，含义：用于表示installed。
        Chunk installed = chunkManager.installChunkIfAbsent(generated);
        if (installed == generated) {
            blockUpdateVersion.incrementAndGet();
        }
        return installed;
    }
}
