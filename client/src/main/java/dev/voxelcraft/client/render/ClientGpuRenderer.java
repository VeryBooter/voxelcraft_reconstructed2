package dev.voxelcraft.client.render;

import dev.voxelcraft.client.GameClient;
import dev.voxelcraft.client.render.ChunkRenderSystem.RenderStats;

/** A renderer driven by the shared GLFW input and game loop. */
public interface ClientGpuRenderer extends AutoCloseable {
    default void initialize(long glfwWindow) { }
    RenderStats render(int width, int height, GameClient gameClient);
    String latestTitleStats();
    long lastMeshingSubmitNanos();
    long lastUploadQueueDrainNanos();
    long lastDrawLoopNanos();
    @Override void close();
}
