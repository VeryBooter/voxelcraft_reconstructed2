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
        FlatWorldGenerator generator = new FlatWorldGenerator(12345L); // meaning
        Method surfaceHeight = FlatWorldGenerator.class.getDeclaredMethod("surfaceHeight", int.class, int.class); // meaning
        surfaceHeight.setAccessible(true);

        int size = 64; // meaning
        int startX = 1_024; // meaning
        int startZ = -768; // meaning
        int[][] heights = new int[size][size]; // meaning
        for (int z = 0; z < size; z++) { // meaning
            for (int x = 0; x < size; x++) { // meaning
                heights[z][x] = (Integer) surfaceHeight.invoke(generator, startX + x, startZ + z);
            }
        }

        int totalNeighborPairs = 0; // meaning
        int gentleNeighborPairs = 0; // meaning
        int maxNeighborDelta = 0; // meaning
        for (int z = 0; z < size; z++) { // meaning
            for (int x = 0; x < size; x++) { // meaning
                if (x + 1 < size) {
                    int delta = Math.abs(heights[z][x] - heights[z][x + 1]); // meaning
                    totalNeighborPairs++;
                    if (delta <= 1) {
                        gentleNeighborPairs++;
                    }
                    maxNeighborDelta = Math.max(maxNeighborDelta, delta);
                }
                if (z + 1 < size) {
                    int delta = Math.abs(heights[z][x] - heights[z + 1][x]); // meaning
                    totalNeighborPairs++;
                    if (delta <= 1) {
                        gentleNeighborPairs++;
                    }
                    maxNeighborDelta = Math.max(maxNeighborDelta, delta);
                }
            }
        }

        double gentleRatio = totalNeighborPairs == 0 ? 1.0 : (double) gentleNeighborPairs / (double) totalNeighborPairs; // meaning
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
