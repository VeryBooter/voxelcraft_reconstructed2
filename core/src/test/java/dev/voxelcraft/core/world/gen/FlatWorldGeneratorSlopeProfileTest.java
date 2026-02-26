package dev.voxelcraft.core.world.gen;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 中文说明：测试用例：验证平滑地形高度分布，减少碎坡且保持连续。
 */
class FlatWorldGeneratorSlopeProfileTest {
    @Test
    void surfaceHeightsAreMostlyGradualAcrossNeighbors() throws Exception {
        FlatWorldGenerator generator = new FlatWorldGenerator(12345L);
        Method surfaceHeight = FlatWorldGenerator.class.getDeclaredMethod("surfaceHeight", int.class, int.class);
        surfaceHeight.setAccessible(true);

        int size = 64;
        int startX = 1_024;
        int startZ = -768;
        int[][] heights = new int[size][size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                heights[z][x] = (Integer) surfaceHeight.invoke(generator, startX + x, startZ + z);
            }
        }

        int totalNeighborPairs = 0;
        int gentleNeighborPairs = 0;
        int maxNeighborDelta = 0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                if (x + 1 < size) {
                    int delta = Math.abs(heights[z][x] - heights[z][x + 1]);
                    totalNeighborPairs++;
                    if (delta <= 1) {
                        gentleNeighborPairs++;
                    }
                    maxNeighborDelta = Math.max(maxNeighborDelta, delta);
                }
                if (z + 1 < size) {
                    int delta = Math.abs(heights[z][x] - heights[z + 1][x]);
                    totalNeighborPairs++;
                    if (delta <= 1) {
                        gentleNeighborPairs++;
                    }
                    maxNeighborDelta = Math.max(maxNeighborDelta, delta);
                }
            }
        }

        double gentleRatio = totalNeighborPairs == 0 ? 1.0 : (double) gentleNeighborPairs / (double) totalNeighborPairs;
        Assertions.assertTrue(
            gentleRatio >= 0.90,
            "Expected >=90% neighbor deltas <=1, got ratio=" + gentleRatio
        );
        Assertions.assertTrue(
            maxNeighborDelta <= 3,
            "Expected max neighbor delta <=3, got " + maxNeighborDelta
        );
    }
}
