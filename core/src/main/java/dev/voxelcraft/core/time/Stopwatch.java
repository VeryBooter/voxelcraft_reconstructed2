package dev.voxelcraft.core.time;

public final class Stopwatch {
    private long startedAtNanos;

    public static Stopwatch startNew() {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        return stopwatch;
    }

    public void start() {
        startedAtNanos = System.nanoTime();
    }

    public long elapsedNanos() {
        return System.nanoTime() - startedAtNanos;
    }

    public double elapsedMillis() {
        return elapsedNanos() / 1_000_000.0;
    }
}
