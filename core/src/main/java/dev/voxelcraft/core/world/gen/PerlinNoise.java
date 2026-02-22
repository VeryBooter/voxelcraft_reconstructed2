package dev.voxelcraft.core.world.gen;

import java.util.SplittableRandom;

public final class PerlinNoise {
    private final int[] permutation = new int[512];

    public PerlinNoise(long seed) {
        int[] source = new int[256];
        for (int i = 0; i < source.length; i++) {
            source[i] = i;
        }

        SplittableRandom random = new SplittableRandom(seed);
        for (int i = source.length - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            int temp = source[i];
            source[i] = source[swapIndex];
            source[swapIndex] = temp;
        }

        for (int i = 0; i < permutation.length; i++) {
            permutation[i] = source[i & 255];
        }
    }

    public double noise(double x, double y, double z) {
        int xi = fastFloor(x) & 255;
        int yi = fastFloor(y) & 255;
        int zi = fastFloor(z) & 255;

        double xf = x - fastFloor(x);
        double yf = y - fastFloor(y);
        double zf = z - fastFloor(z);

        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        int aaa = permutation[permutation[permutation[xi] + yi] + zi];
        int aab = permutation[permutation[permutation[xi] + yi] + zi + 1];
        int aba = permutation[permutation[permutation[xi] + yi + 1] + zi];
        int abb = permutation[permutation[permutation[xi] + yi + 1] + zi + 1];
        int baa = permutation[permutation[permutation[xi + 1] + yi] + zi];
        int bab = permutation[permutation[permutation[xi + 1] + yi] + zi + 1];
        int bba = permutation[permutation[permutation[xi + 1] + yi + 1] + zi];
        int bbb = permutation[permutation[permutation[xi + 1] + yi + 1] + zi + 1];

        double x1 = lerp(
            grad(aaa, xf, yf, zf),
            grad(baa, xf - 1.0, yf, zf),
            u
        );
        double x2 = lerp(
            grad(aba, xf, yf - 1.0, zf),
            grad(bba, xf - 1.0, yf - 1.0, zf),
            u
        );
        double y1 = lerp(x1, x2, v);

        double x3 = lerp(
            grad(aab, xf, yf, zf - 1.0),
            grad(bab, xf - 1.0, yf, zf - 1.0),
            u
        );
        double x4 = lerp(
            grad(abb, xf, yf - 1.0, zf - 1.0),
            grad(bbb, xf - 1.0, yf - 1.0, zf - 1.0),
            u
        );
        double y2 = lerp(x3, x4, v);

        return lerp(y1, y2, w);
    }

    public double fbm2d(double x, double y, int octaves, double lacunarity, double gain) {
        double amplitude = 1.0;
        double frequency = 1.0;
        double sum = 0.0;
        double amplitudeSum = 0.0;

        for (int i = 0; i < octaves; i++) {
            sum += noise(x * frequency, y * frequency, 0.0) * amplitude;
            amplitudeSum += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }

        if (amplitudeSum == 0.0) {
            return 0.0;
        }
        return sum / amplitudeSum;
    }

    private static int fastFloor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
