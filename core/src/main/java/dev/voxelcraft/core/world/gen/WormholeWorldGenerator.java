package dev.voxelcraft.core.world.gen;

import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.Section;

/**
 * 中文说明：虫洞世界生成器：在原点附近生成固定十字形走廊/河道空间（3D），用于 wPhase 漂移中介。
 */
public final class WormholeWorldGenerator implements WorldGenerator {
    private static final int FLOOR_Y = 64; // meaning
    private static final int CEILING_Y = 71; // meaning
    private static final int WALL_MIN_Y = FLOOR_Y + 1; // meaning
    private static final int WALL_MAX_Y = CEILING_Y - 1; // meaning

    private static final int CENTER_INTERIOR_HALF = 3; // meaning
    private static final int CENTER_ENVELOPE_HALF = 4; // meaning
    private static final int CORRIDOR_INTERIOR_HALF = 2; // meaning
    private static final int CORRIDOR_ENVELOPE_HALF = 3; // meaning
    private static final int CORRIDOR_EXTENT = 32; // meaning
    private static final int CORRIDOR_GAP = 4; // meaning

    private final long seed; // meaning

    public WormholeWorldGenerator(long seed) {
        this.seed = seed;
    }

    @Override
    public void generate(Chunk chunk) {
        int chunkBaseX = chunk.pos().x() * Section.SIZE; // meaning
        int chunkBaseZ = chunk.pos().z() * Section.SIZE; // meaning

        for (int localX = 0; localX < Section.SIZE; localX++) { // meaning
            int worldX = chunkBaseX + localX; // meaning
            for (int localZ = 0; localZ < Section.SIZE; localZ++) { // meaning
                int worldZ = chunkBaseZ + localZ; // meaning

                boolean interior = isWormholeInterior(worldX, worldZ); // meaning
                boolean envelope = isWormholeEnvelope(worldX, worldZ); // meaning
                if (!interior && !envelope) {
                    continue;
                }

                // Floor / ceiling always solid where the wormhole structure exists.
                chunk.setBlock(localX, FLOOR_Y, localZ, Blocks.STONE);
                chunk.setBlock(localX, CEILING_Y, localZ, Blocks.STONE);

                // Interior volume is air; envelope-only cells become walls.
                for (int y = WALL_MIN_Y; y <= WALL_MAX_Y; y++) { // meaning
                    if (interior) {
                        chunk.setBlock(localX, y, localZ, Blocks.AIR);
                    } else {
                        chunk.setBlock(localX, y, localZ, Blocks.STONE);
                    }
                }
            }
        }
    }

    public long seed() {
        return seed;
    }

    private static boolean isWormholeInterior(int x, int z) {
        if (Math.abs(x) <= CENTER_INTERIOR_HALF && Math.abs(z) <= CENTER_INTERIOR_HALF) {
            return true;
        }
        if (Math.abs(x) <= CORRIDOR_INTERIOR_HALF && z <= -CORRIDOR_GAP && z >= -CORRIDOR_EXTENT) {
            return true; // north
        }
        if (Math.abs(x) <= CORRIDOR_INTERIOR_HALF && z >= CORRIDOR_GAP && z <= CORRIDOR_EXTENT) {
            return true; // south
        }
        if (Math.abs(z) <= CORRIDOR_INTERIOR_HALF && x >= CORRIDOR_GAP && x <= CORRIDOR_EXTENT) {
            return true; // east
        }
        if (Math.abs(z) <= CORRIDOR_INTERIOR_HALF && x <= -CORRIDOR_GAP && x >= -CORRIDOR_EXTENT) {
            return true; // west
        }
        return false;
    }

    private static boolean isWormholeEnvelope(int x, int z) {
        if (Math.abs(x) <= CENTER_ENVELOPE_HALF && Math.abs(z) <= CENTER_ENVELOPE_HALF) {
            return true;
        }
        if (Math.abs(x) <= CORRIDOR_ENVELOPE_HALF && z <= -CORRIDOR_GAP && z >= -CORRIDOR_EXTENT) {
            return true; // north envelope
        }
        if (Math.abs(x) <= CORRIDOR_ENVELOPE_HALF && z >= CORRIDOR_GAP && z <= CORRIDOR_EXTENT) {
            return true; // south envelope
        }
        if (Math.abs(z) <= CORRIDOR_ENVELOPE_HALF && x >= CORRIDOR_GAP && x <= CORRIDOR_EXTENT) {
            return true; // east envelope
        }
        if (Math.abs(z) <= CORRIDOR_ENVELOPE_HALF && x <= -CORRIDOR_GAP && x >= -CORRIDOR_EXTENT) {
            return true; // west envelope
        }
        return false;
    }
}
