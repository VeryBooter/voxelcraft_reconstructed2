package dev.voxelcraft.client;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
/**
 * 中文说明：客户端组件：负责 HardwareStatsSampler 的启动、状态或子系统逻辑。
 */

// 中文标注（类）：`HardwareStatsSampler`，职责：封装hardware、stats、sampler相关逻辑。
public final class HardwareStatsSampler {
    // 中文标注（字段）：`BYTES_PER_MEGABYTE`，含义：用于表示字节数据、per、megabyte。
    private static final double BYTES_PER_MEGABYTE = 1024.0 * 1024.0;

    // 中文标注（字段）：`runtime`，含义：用于表示运行时。
    private final Runtime runtime = Runtime.getRuntime();
    // 中文标注（字段）：`logicalCores`，含义：用于表示logical、cores。
    private final int logicalCores = runtime.availableProcessors();
    // 中文标注（字段）：`extendedBean`，含义：用于表示extended、bean。
    private final com.sun.management.OperatingSystemMXBean extendedBean;

    // 中文标注（构造方法）：`HardwareStatsSampler`，参数：无；用途：初始化`HardwareStatsSampler`实例。
    public HardwareStatsSampler() {
        // 中文标注（局部变量）：`osBean`，含义：用于表示os、bean。
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        // 中文标注（局部变量）：`bean`，含义：用于表示bean。
        if (osBean instanceof com.sun.management.OperatingSystemMXBean bean) {
            extendedBean = bean;
        } else {
            extendedBean = null;
        }
    }

    // 中文标注（方法）：`sample`，参数：无；用途：执行sample相关逻辑。
    public Snapshot sample() {
        // 中文标注（局部变量）：`heapUsedBytes`，含义：用于表示heap、used、字节数据。
        long heapUsedBytes = runtime.totalMemory() - runtime.freeMemory();
        // 中文标注（局部变量）：`heapMaxBytes`，含义：用于表示heap、最大、字节数据。
        long heapMaxBytes = runtime.maxMemory();

        // 中文标注（局部变量）：`processCpuLoad`，含义：用于表示process、CPU、加载。
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

    // 中文标注（记录类）：`Snapshot`，职责：封装快照相关逻辑。
    // 中文标注（字段）：`heapUsedMb`，含义：用于表示heap、used、mb。
    // 中文标注（字段）：`heapMaxMb`，含义：用于表示heap、最大、mb。
    // 中文标注（字段）：`logicalCores`，含义：用于表示logical、cores。
    // 中文标注（字段）：`processCpuLoad`，含义：用于表示process、CPU、加载。
    public record Snapshot(int heapUsedMb, int heapMaxMb, int logicalCores, double processCpuLoad) {
        // 中文标注（方法）：`cpuText`，参数：无；用途：执行CPU、text相关逻辑。
        public String cpuText() {
            if (processCpuLoad < 0.0) {
                return "n/a";
            }
            // 中文标注（局部变量）：`percent`，含义：用于表示percent。
            int percent = (int) Math.round(processCpuLoad * 100.0);
            return percent + "%";
        }
    }
}