package dev.voxelcraft.core.world;

import dev.voxelcraft.core.world.gen.FlatWorldGenerator;
import dev.voxelcraft.core.world.gen.WormholeWorldGenerator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 中文说明：世界切片栈：按离散整数 w 管理多个 3D World 切片实例。
 */
public final class WorldStack {
    public static final int W_WORMHOLE = -1_000_000_000; // meaning
    private static final long WORMHOLE_SEED_SALT = 0x77A1B10C5EED1234L; // meaning

    private final long baseSeed; // meaning
    private final ConcurrentMap<Integer, World> slices = new ConcurrentHashMap<>(); // meaning

    public WorldStack(long baseSeed) {
        this.baseSeed = baseSeed;
    }

    public long baseSeed() {
        return baseSeed;
    }

    public World get(int w) {
        return slices.computeIfAbsent(w, this::createSlice);
    }

    public World slice(int w) {
        return get(w);
    }

    private World createSlice(int w) {
        if (w == W_WORMHOLE) {
            long wormholeSeed = mixSeed(baseSeed ^ WORMHOLE_SEED_SALT, w); // meaning
            return new World(wormholeSeed, new WormholeWorldGenerator(wormholeSeed));
        }
        long sliceSeed = mixSeed(baseSeed, w); // meaning
        return new World(sliceSeed, new FlatWorldGenerator(sliceSeed));
    }

    public static long mixSeed(long baseSeed, int w) {
        if (w == 0) {
            return baseSeed;
        }
        long value = baseSeed; // meaning
        value ^= (long) w * 0x9E3779B97F4A7C15L;
        value ^= (value >>> 33);
        value *= 0xFF51AFD7ED558CCDL;
        value ^= (value >>> 33);
        value *= 0xC4CEB9FE1A85EC53L;
        value ^= (value >>> 33);
        return value;
    }
}
