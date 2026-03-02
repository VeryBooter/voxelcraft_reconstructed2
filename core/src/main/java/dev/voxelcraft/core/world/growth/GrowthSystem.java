package dev.voxelcraft.core.world.growth;

// 中文标注：本文件已标记。

import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.BlockDef;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.Section;
import dev.voxelcraft.core.world.World;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public final class GrowthSystem {
    private static final double TICKS_PER_SECOND = 20.0;

    private final SplittableRandom random;
    private final LinkedHashSet<GrowthCell> activeCells = new LinkedHashSet<>();

    public GrowthSystem(long seed) {
        this.random = new SplittableRandom(seed ^ 0xB10F5EED1234ABCDL);
    }

    public synchronized void onChunkInstalled(Chunk chunk) {
        int chunkBaseX = chunk.pos().x() * Section.SIZE;
        int chunkBaseZ = chunk.pos().z() * Section.SIZE;
        chunk.forEachNonAir((localX, y, localZ, block) -> {
            if (!isGrowable(block)) {
                return;
            }
            activeCells.add(new GrowthCell(chunkBaseX + localX, y, chunkBaseZ + localZ));
        });
    }

    public synchronized void onBlockChanged(int x, int y, int z, Block block) {
        GrowthCell cell = new GrowthCell(x, y, z);
        if (isGrowable(block)) {
            activeCells.add(cell);
            return;
        }
        activeCells.remove(cell);
    }

    public void tick(World world, double deltaSeconds) {
        List<GrowthCell> snapshot = snapshotForTick();
        if (snapshot.isEmpty()) {
            return;
        }

        int budget = Math.max(32, Math.min(snapshot.size(), 512));
        for (int i = 0; i < budget; i++) {
            GrowthCell cell = snapshot.get(random.nextInt(snapshot.size()));
            applyGrowthRule(world, cell, deltaSeconds);
        }
    }

    private synchronized List<GrowthCell> snapshotForTick() {
        return new ArrayList<>(activeCells);
    }

    private void applyGrowthRule(World world, GrowthCell cell, double deltaSeconds) {
        Block block = world.peekBlock(cell.x, cell.y, cell.z);
        BlockDef def = block == null ? null : block.def();
        if (def == null) {
            return;
        }

        String rule = def.growthRuleId();
        if (rule == null || rule.equals("NONE") || rule.isEmpty()) {
            return;
        }

        switch (rule) {
            case "LAYER_ACCUMULATE" -> runLayerAccumulate(world, cell, block, def, deltaSeconds);
            case "SPREAD_MOSS_HUMID" -> runSpreadMossHumid(world, cell, block, def, deltaSeconds);
            case "COLONIZE_SURFACE_BIOFILM",
                 "FUNGAL_SPREAD",
                 "AQUATIC_GROW",
                 "CORAL_LIVE_OR_DIE",
                 "ACCUMULATE_GUANO",
                 "COMPOSTING" -> {
                // Placeholder handlers for future expansion.
            }
            default -> {
                // Unknown rule id, keep active for forward compatibility.
            }
        }
    }

    private void runLayerAccumulate(World world, GrowthCell cell, Block block, BlockDef def, double deltaSeconds) {
        Map<String, Object> params = def.growthParams();
        int maxLayers = intParam(params, "max_layers", 8);
        double chancePerTick = doubleParam(params, "chance_per_tick", 0.004);
        if (!roll(chancePerTick, deltaSeconds)) {
            return;
        }

        int stacked = 1;
        while (stacked < maxLayers) {
            int targetY = cell.y + stacked;
            if (!world.isWithinWorldY(targetY)) {
                return;
            }
            Block above = world.peekBlock(cell.x, targetY, cell.z);
            if (above == block) {
                stacked++;
                continue;
            }
            if (above != Blocks.AIR) {
                return;
            }
            if (world.setBlock(cell.x, targetY, cell.z, block)) {
                onBlockChanged(cell.x, targetY, cell.z, block);
            }
            return;
        }
    }

    private void runSpreadMossHumid(World world, GrowthCell cell, Block block, BlockDef def, double deltaSeconds) {
        Map<String, Object> params = def.growthParams();
        double chancePerTick = doubleParam(params, "chance_per_tick", 0.003);
        if (!isHumid(world, cell)) {
            return;
        }
        if (!roll(chancePerTick, deltaSeconds)) {
            return;
        }

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int start = random.nextInt(dirs.length);
        for (int i = 0; i < dirs.length; i++) {
            int[] dir = dirs[(start + i) % dirs.length];
            int nx = cell.x + dir[0];
            int nz = cell.z + dir[1];
            if (!world.isWithinWorldY(cell.y) || !world.isWithinWorldY(cell.y - 1)) {
                continue;
            }
            Block target = world.peekBlock(nx, cell.y, nz);
            Block below = world.peekBlock(nx, cell.y - 1, nz);
            if (target != Blocks.AIR || below == null || !below.solid()) {
                continue;
            }
            if (world.setBlock(nx, cell.y, nz, block)) {
                onBlockChanged(nx, cell.y, nz, block);
            }
            return;
        }
    }

    private boolean isHumid(World world, GrowthCell cell) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                Block nearby = world.peekBlock(cell.x + dx, cell.y, cell.z + dz);
                if (nearby == null || nearby == Blocks.AIR) {
                    continue;
                }
                String id = nearby.id().toString();
                if (id.contains("water") || id.contains("wet")) {
                    return true;
                }
            }
        }

        long rainEpoch = world.ticks() / 400L;
        long hash = 1469598103934665603L;
        hash ^= cell.x;
        hash *= 1099511628211L;
        hash ^= cell.z;
        hash *= 1099511628211L;
        hash ^= rainEpoch;
        hash *= 1099511628211L;
        return (hash & 7L) == 0L;
    }

    private boolean roll(double chancePerTick, double deltaSeconds) {
        if (chancePerTick <= 0.0 || deltaSeconds <= 0.0) {
            return false;
        }
        double scaled = Math.min(1.0, chancePerTick * (deltaSeconds * TICKS_PER_SECOND));
        return random.nextDouble() < scaled;
    }

    private static boolean isGrowable(Block block) {
        if (block == null || block == Blocks.AIR) {
            return false;
        }
        BlockDef def = block.def();
        return def != null && def.growthRuleId() != null && !def.growthRuleId().equals("NONE");
    }

    private static int intParam(Map<String, Object> params, String key, int fallback) {
        Object raw = params.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String value) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double doubleParam(Map<String, Object> params, String key, double fallback) {
        Object raw = params.get(key);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String value) {
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private record GrowthCell(int x, int y, int z) {
    }
}
