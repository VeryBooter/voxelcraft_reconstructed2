package dev.voxelcraft.core.world.gen;

import java.util.SplittableRandom;
/**
 * 中文说明：柏林噪声实现：为地形高度与细节扰动提供连续噪声采样。
 */

// 中文标注（类）：`PerlinNoise`，职责：封装柏林、噪声相关逻辑。
public final class PerlinNoise {
    // 中文标注（字段）：`permutation`，含义：用于表示permutation。
    private final int[] permutation = new int[512]; // meaning

    // 中文标注（构造方法）：`PerlinNoise`，参数：seed；用途：初始化`PerlinNoise`实例。
    // 中文标注（参数）：`seed`，含义：用于表示seed。
    public PerlinNoise(long seed) {
        // 中文标注（局部变量）：`source`，含义：用于表示source。
        int[] source = new int[256]; // meaning
        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = 0; i < source.length; i++) { // meaning
            source[i] = i;
        }

        // 中文标注（局部变量）：`random`，含义：用于表示random。
        SplittableRandom random = new SplittableRandom(seed); // meaning
        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = source.length - 1; i > 0; i--) { // meaning
            // 中文标注（局部变量）：`swapIndex`，含义：用于表示swap、索引。
            int swapIndex = random.nextInt(i + 1); // meaning
            // 中文标注（局部变量）：`temp`，含义：用于表示临时。
            int temp = source[i]; // meaning
            source[i] = source[swapIndex];
            source[swapIndex] = temp;
        }

        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = 0; i < permutation.length; i++) { // meaning
            permutation[i] = source[i & 255];
        }
    }

    // 中文标注（方法）：`noise`，参数：x、y、z；用途：执行噪声相关逻辑。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    public double noise(double x, double y, double z) {
        // 中文标注（局部变量）：`xi`，含义：用于表示xi。
        int xi = fastFloor(x) & 255; // meaning
        // 中文标注（局部变量）：`yi`，含义：用于表示yi。
        int yi = fastFloor(y) & 255; // meaning
        // 中文标注（局部变量）：`zi`，含义：用于表示zi。
        int zi = fastFloor(z) & 255; // meaning

        // 中文标注（局部变量）：`xf`，含义：用于表示xf。
        double xf = x - fastFloor(x); // meaning
        // 中文标注（局部变量）：`yf`，含义：用于表示yf。
        double yf = y - fastFloor(y); // meaning
        // 中文标注（局部变量）：`zf`，含义：用于表示zf。
        double zf = z - fastFloor(z); // meaning

        // 中文标注（局部变量）：`u`，含义：用于表示u。
        double u = fade(xf); // meaning
        // 中文标注（局部变量）：`v`，含义：用于表示v。
        double v = fade(yf); // meaning
        // 中文标注（局部变量）：`w`，含义：用于表示w。
        double w = fade(zf); // meaning

        // 中文标注（局部变量）：`aaa`，含义：用于表示aaa。
        int aaa = permutation[permutation[permutation[xi] + yi] + zi]; // meaning
        // 中文标注（局部变量）：`aab`，含义：用于表示aab。
        int aab = permutation[permutation[permutation[xi] + yi] + zi + 1]; // meaning
        // 中文标注（局部变量）：`aba`，含义：用于表示aba。
        int aba = permutation[permutation[permutation[xi] + yi + 1] + zi]; // meaning
        // 中文标注（局部变量）：`abb`，含义：用于表示abb。
        int abb = permutation[permutation[permutation[xi] + yi + 1] + zi + 1]; // meaning
        // 中文标注（局部变量）：`baa`，含义：用于表示baa。
        int baa = permutation[permutation[permutation[xi + 1] + yi] + zi]; // meaning
        // 中文标注（局部变量）：`bab`，含义：用于表示bab。
        int bab = permutation[permutation[permutation[xi + 1] + yi] + zi + 1]; // meaning
        // 中文标注（局部变量）：`bba`，含义：用于表示bba。
        int bba = permutation[permutation[permutation[xi + 1] + yi + 1] + zi]; // meaning
        // 中文标注（局部变量）：`bbb`，含义：用于表示bbb。
        int bbb = permutation[permutation[permutation[xi + 1] + yi + 1] + zi + 1]; // meaning

        // 中文标注（局部变量）：`x1`，含义：用于表示X坐标、1。
        double x1 = lerp(
            grad(aaa, xf, yf, zf),
            grad(baa, xf - 1.0, yf, zf),
            u
        );
        // 中文标注（局部变量）：`x2`，含义：用于表示X坐标、2。
        double x2 = lerp(
            grad(aba, xf, yf - 1.0, zf),
            grad(bba, xf - 1.0, yf - 1.0, zf),
            u
        );
        // 中文标注（局部变量）：`y1`，含义：用于表示Y坐标、1。
        double y1 = lerp(x1, x2, v); // meaning

        // 中文标注（局部变量）：`x3`，含义：用于表示X坐标、3。
        double x3 = lerp(
            grad(aab, xf, yf, zf - 1.0),
            grad(bab, xf - 1.0, yf, zf - 1.0),
            u
        );
        // 中文标注（局部变量）：`x4`，含义：用于表示X坐标、4。
        double x4 = lerp(
            grad(abb, xf, yf - 1.0, zf - 1.0),
            grad(bbb, xf - 1.0, yf - 1.0, zf - 1.0),
            u
        );
        // 中文标注（局部变量）：`y2`，含义：用于表示Y坐标、2。
        double y2 = lerp(x3, x4, v); // meaning

        return lerp(y1, y2, w);
    }

    // 中文标注（方法）：`fbm2d`，参数：x、y、octaves、lacunarity、gain；用途：执行fbm、2、d相关逻辑。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`octaves`，含义：用于表示octaves。
    // 中文标注（参数）：`lacunarity`，含义：用于表示lacunarity。
    // 中文标注（参数）：`gain`，含义：用于表示gain。
    public double fbm2d(double x, double y, int octaves, double lacunarity, double gain) {
        // 中文标注（局部变量）：`amplitude`，含义：用于表示amplitude。
        double amplitude = 1.0; // meaning
        // 中文标注（局部变量）：`frequency`，含义：用于表示frequency。
        double frequency = 1.0; // meaning
        // 中文标注（局部变量）：`sum`，含义：用于表示sum。
        double sum = 0.0; // meaning
        // 中文标注（局部变量）：`amplitudeSum`，含义：用于表示amplitude、sum。
        double amplitudeSum = 0.0; // meaning

        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = 0; i < octaves; i++) { // meaning
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

    // 中文标注（方法）：`fastFloor`，参数：value；用途：执行fast、floor相关逻辑。
    // 中文标注（参数）：`value`，含义：用于表示值。
    private static int fastFloor(double value) {
        // 中文标注（局部变量）：`integer`，含义：用于表示integer。
        int integer = (int) value; // meaning
        return value < integer ? integer - 1 : integer;
    }

    // 中文标注（方法）：`fade`，参数：value；用途：执行fade相关逻辑。
    // 中文标注（参数）：`value`，含义：用于表示值。
    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    // 中文标注（方法）：`lerp`，参数：a、b、t；用途：执行lerp相关逻辑。
    // 中文标注（参数）：`a`，含义：用于表示a。
    // 中文标注（参数）：`b`，含义：用于表示b。
    // 中文标注（参数）：`t`，含义：用于表示t。
    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // 中文标注（方法）：`grad`，参数：hash、x、y、z；用途：执行grad相关逻辑。
    // 中文标注（参数）：`hash`，含义：用于表示hash。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    private static double grad(int hash, double x, double y, double z) {
        // 中文标注（局部变量）：`h`，含义：用于表示h。
        int h = hash & 15; // meaning
        // 中文标注（局部变量）：`u`，含义：用于表示u。
        double u = h < 8 ? x : y; // meaning
        // 中文标注（局部变量）：`v`，含义：用于表示v。
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z); // meaning
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
