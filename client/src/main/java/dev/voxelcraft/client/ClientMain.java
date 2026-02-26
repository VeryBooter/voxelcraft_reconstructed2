package dev.voxelcraft.client;
/**
 * 中文说明：客户端组件：负责 ClientMain 的启动、状态或子系统逻辑。
 */

// 中文标注（类）：`ClientMain`，职责：封装客户端、主相关逻辑。
public final class ClientMain {
    // 中文标注（构造方法）：`ClientMain`，参数：无；用途：初始化`ClientMain`实例。
    private ClientMain() {
    }

    // 中文标注（方法）：`main`，参数：args；用途：执行主相关逻辑。
    // 中文标注（参数）：`args`，含义：用于表示args。
    public static void main(String[] args) {
        // 中文标注（局部变量）：`config`，含义：用于表示config。
        VoxelcraftClientApp.LaunchConfig config = VoxelcraftClientApp.LaunchConfig.parse(args); // meaning
        new VoxelcraftClientApp(config).run();
    }
}
