package dev.voxelcraft.core.time;
/**
 * 中文说明：时间工具：提供 Stopwatch 的计时或性能测量能力。
 */

// 中文标注（类）：`Stopwatch`，职责：封装秒表相关逻辑。
public final class Stopwatch {
    // 中文标注（字段）：`startedAtNanos`，含义：用于表示started、at、nanos。
    private long startedAtNanos; // meaning

    // 中文标注（方法）：`startNew`，参数：无；用途：执行开始、new相关逻辑。
    public static Stopwatch startNew() {
        // 中文标注（局部变量）：`stopwatch`，含义：用于表示秒表。
        Stopwatch stopwatch = new Stopwatch(); // meaning
        stopwatch.start();
        return stopwatch;
    }

    // 中文标注（方法）：`start`，参数：无；用途：执行开始相关逻辑。
    public void start() {
        startedAtNanos = System.nanoTime();
    }

    // 中文标注（方法）：`elapsedNanos`，参数：无；用途：执行已耗时、nanos相关逻辑。
    public long elapsedNanos() {
        return System.nanoTime() - startedAtNanos;
    }

    // 中文标注（方法）：`elapsedMillis`，参数：无；用途：执行已耗时、millis相关逻辑。
    public double elapsedMillis() {
        return elapsedNanos() / 1_000_000.0;
    }
}
