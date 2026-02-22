package dev.voxelcraft.client;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public final class HardwareStatsSampler {
    private static final double BYTES_PER_MEGABYTE = 1024.0 * 1024.0;

    private final Runtime runtime = Runtime.getRuntime();
    private final int logicalCores = runtime.availableProcessors();
    private final com.sun.management.OperatingSystemMXBean extendedBean;

    public HardwareStatsSampler() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean bean) {
            extendedBean = bean;
        } else {
            extendedBean = null;
        }
    }

    public Snapshot sample() {
        long heapUsedBytes = runtime.totalMemory() - runtime.freeMemory();
        long heapMaxBytes = runtime.maxMemory();

        double processCpuLoad = -1.0;
        if (extendedBean != null) {
            processCpuLoad = extendedBean.getProcessCpuLoad();
        }

        return new Snapshot(
            (int) Math.round(heapUsedBytes / BYTES_PER_MEGABYTE),
            (int) Math.round(heapMaxBytes / BYTES_PER_MEGABYTE),
            logicalCores,
            processCpuLoad
        );
    }

    public record Snapshot(int heapUsedMb, int heapMaxMb, int logicalCores, double processCpuLoad) {
        public String cpuText() {
            if (processCpuLoad < 0.0) {
                return "n/a";
            }
            int percent = (int) Math.round(processCpuLoad * 100.0);
            return percent + "%";
        }
    }
}
