package dev.voxelcraft.client;

public final class ClientMain {
    private ClientMain() {
    }

    public static void main(String[] args) {
        VoxelcraftClientApp.LaunchConfig config = VoxelcraftClientApp.LaunchConfig.parse(args);
        new VoxelcraftClientApp(config).run();
    }
}
