package dev.voxelcraft.client.runtime;

import dev.voxelcraft.client.GameClient;
import dev.voxelcraft.client.platform.InputState;
import dev.voxelcraft.client.render.ChunkRenderSystem.RenderStats;
import dev.voxelcraft.client.render.GpuChunkRenderer;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_2;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_3;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_4;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_5;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_6;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_2;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_3;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_5;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_6;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_O;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetInputMode;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwSetWindowTitle;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.system.MemoryUtil.NULL;
/**
 * 中文说明：GPU 运行时：负责 GLFW/OpenGL 生命周期、主循环与性能日志输出。
 */

// 中文标注（类）：`GpuClientRuntime`，职责：封装GPU、客户端、运行时相关逻辑。
public final class GpuClientRuntime implements AutoCloseable {
    // 中文标注（字段）：`TARGET_FPS`，含义：用于表示target、fps。
    private static final int TARGET_FPS = 60; // meaning
    // 中文标注（字段）：`MIN_FRAME_SECONDS`，含义：用于表示最小、帧、seconds。
    private static final double MIN_FRAME_SECONDS = 1.0 / TARGET_FPS; // meaning
    // 中文标注（字段）：`HITCH_FRAME_MS`，含义：用于表示hitch、帧、ms。
    private static final double HITCH_FRAME_MS = 50.0; // meaning

    // 中文标注（字段）：`title`，含义：用于表示title。
    private final String title; // meaning
    // 中文标注（字段）：`input`，含义：用于表示输入。
    private final InputState input = new InputState(); // meaning
    // 中文标注（字段）：`renderer`，含义：用于表示渲染器。
    private final GpuChunkRenderer renderer = new GpuChunkRenderer(); // meaning
    // 中文标注（字段）：`frameTimeWindowMs`，含义：用于表示帧、时间、窗口、ms。
    private final double[] frameTimeWindowMs = new double[512]; // meaning
    // 中文标注（字段）：`frameTimeSortScratchMs`，含义：用于表示帧、时间、sort、临时工作区、ms。
    private final double[] frameTimeSortScratchMs = new double[512]; // meaning

    // 中文标注（字段）：`windowHandle`，含义：用于表示窗口、handle。
    private long windowHandle; // meaning
    // 中文标注（字段）：`initialized`，含义：用于表示initialized。
    private boolean initialized; // meaning
    // 中文标注（字段）：`firstMouseSample`，含义：用于表示first、鼠标、sample。
    private boolean firstMouseSample = true; // meaning
    private boolean cursorCaptured = true; // meaning
    // 中文标注（字段）：`frameTimeWindowIndex`，含义：用于表示帧、时间、窗口、索引。
    private int frameTimeWindowIndex; // meaning
    // 中文标注（字段）：`frameTimeWindowCount`，含义：用于表示帧、时间、窗口、数量。
    private int frameTimeWindowCount; // meaning
    // 中文标注（字段）：`framePerfWindowStartNanos`，含义：用于表示帧、perf、窗口、开始、nanos。
    private long framePerfWindowStartNanos; // meaning
    // 中文标注（字段）：`framePerfFrames`，含义：用于表示帧、perf、frames。
    private int framePerfFrames; // meaning
    // 中文标注（字段）：`framePerfWorstMs`，含义：用于表示帧、perf、worst、ms。
    private double framePerfWorstMs; // meaning
    // 中文标注（字段）：`lastChunkPendingCount`，含义：用于表示last、区块、pending、数量。
    private int lastChunkPendingCount; // meaning
    // 中文标注（字段）：`lastChunkReadyCount`，含义：用于表示last、区块、ready、数量。
    private int lastChunkReadyCount; // meaning
    // 中文标注（字段）：`lastChunkGenInFlightCount`，含义：用于表示last、区块、gen、in、flight、数量。
    private int lastChunkGenInFlightCount; // meaning

    // 中文标注（构造方法）：`GpuClientRuntime`，参数：title；用途：初始化`GpuClientRuntime`实例。
    // 中文标注（参数）：`title`，含义：用于表示title。
    public GpuClientRuntime(String title) {
        this.title = title;
    }

    // 中文标注（方法）：`run`，参数：gameClient；用途：执行run相关逻辑。
    // 中文标注（参数）：`gameClient`，含义：用于表示game、客户端。
    public void run(GameClient gameClient) {
        initialize();

        // 中文标注（局部变量）：`previousNanos`，含义：用于表示previous、nanos。
        long previousNanos = System.nanoTime(); // meaning
        // 中文标注（局部变量）：`fpsWindowStart`，含义：用于表示fps、窗口、开始。
        long fpsWindowStart = previousNanos; // meaning
        // 中文标注（局部变量）：`frames`，含义：用于表示frames。
        int frames = 0; // meaning
        int displayedFps = 0; // meaning
        framePerfWindowStartNanos = previousNanos;
        framePerfFrames = 0;
        framePerfWorstMs = 0.0;

        while (!glfwWindowShouldClose(windowHandle)) {
            // 中文标注（局部变量）：`frameStartedNanos`，含义：用于表示帧、started、nanos。
            long frameStartedNanos = System.nanoTime(); // meaning
            // 中文标注（局部变量）：`now`，含义：用于表示now。
            long now = System.nanoTime(); // meaning
            // 中文标注（局部变量）：`deltaSeconds`，含义：用于表示增量、seconds。
            double deltaSeconds = (now - previousNanos) / 1_000_000_000.0; // meaning
            previousNanos = now;

            if (deltaSeconds <= 0.0) {
                deltaSeconds = MIN_FRAME_SECONDS;
            }
            if (deltaSeconds > 0.05) {
                deltaSeconds = 0.05;
            }

            glfwPollEvents();
            boolean shouldCaptureCursor = !gameClient.isAnyUiOpen(); // meaning
            if (shouldCaptureCursor != cursorCaptured) {
                glfwSetInputMode(windowHandle, GLFW_CURSOR, shouldCaptureCursor ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
                cursorCaptured = shouldCaptureCursor;
                if (shouldCaptureCursor) {
                    firstMouseSample = true;
                }
            }
            boolean hadUiOpen = gameClient.isAnyUiOpen(); // meaning

            // 中文标注（局部变量）：`tickStartedNanos`，含义：用于表示刻、started、nanos。
            long tickStartedNanos = System.nanoTime(); // meaning
            gameClient.tick(input, deltaSeconds);
            // 中文标注（局部变量）：`tickNanos`，含义：用于表示刻、nanos。
            long tickNanos = System.nanoTime() - tickStartedNanos; // meaning
            if (input.wasKeyPressed(KeyEvent.VK_ESCAPE) && !hadUiOpen && !gameClient.isAnyUiOpen()) {
                glfwSetWindowShouldClose(windowHandle, true);
            }

            // 中文标注（局部变量）：`width`，含义：用于表示宽度。
            int width; // meaning
            // 中文标注（局部变量）：`height`，含义：用于表示高度。
            int height; // meaning
            // 中文标注（局部变量）：`stack`，含义：用于表示栈。
            try (MemoryStack stack = MemoryStack.stackPush()) {
                // 中文标注（局部变量）：`widthBuffer`，含义：用于表示宽度、缓冲区。
                var widthBuffer = stack.mallocInt(1); // meaning
                // 中文标注（局部变量）：`heightBuffer`，含义：用于表示高度、缓冲区。
                var heightBuffer = stack.mallocInt(1); // meaning
                glfwGetFramebufferSize(windowHandle, widthBuffer, heightBuffer);
                width = Math.max(1, widthBuffer.get(0));
                height = Math.max(1, heightBuffer.get(0));
            }

            // 中文标注（局部变量）：`renderStartedNanos`，含义：用于表示渲染、started、nanos。
            long renderStartedNanos = System.nanoTime(); // meaning
            // 中文标注（局部变量）：`stats`，含义：用于表示stats。
            RenderStats stats = renderer.render(width, height, gameClient); // meaning
            // 中文标注（局部变量）：`renderNanos`，含义：用于表示渲染、nanos。
            long renderNanos = System.nanoTime() - renderStartedNanos; // meaning
            // 中文标注（局部变量）：`swapStartedNanos`，含义：用于表示swap、started、nanos。
            long swapStartedNanos = System.nanoTime(); // meaning
            glfwSwapBuffers(windowHandle);
            // 中文标注（局部变量）：`swapNanos`，含义：用于表示swap、nanos。
            long swapNanos = System.nanoTime() - swapStartedNanos; // meaning

            input.endFrame();
            lastChunkPendingCount = gameClient.pendingChunkGenerationCount();
            lastChunkReadyCount = gameClient.readyGeneratedChunkCount();
            lastChunkGenInFlightCount = gameClient.chunkGenerationJobsInFlight();
            // 中文标注（局部变量）：`frameTotalNanos`，含义：用于表示帧、total、nanos。
            long frameTotalNanos = System.nanoTime() - frameStartedNanos; // meaning
            // 中文标注（局部变量）：`frameMs`，含义：用于表示帧、ms。
            double frameMs = frameTotalNanos / 1_000_000.0; // meaning
            recordFrameTime(frameMs);
            emitFramePerfLineIfDue();
            if (frameMs > HITCH_FRAME_MS) {
                System.out.printf(
                    "[frame-hitch] frameMs=%.2f dtMs=%.2f tickMs=%.2f ensureLocalChunksMs=%.2f chunkGenDrainMs=%.2f chunkGenSubmitMs=%.2f chunkInstallMs=%.2f chunkSubmitCount=%d chunkInstallCount=%d chunkPending=%d chunkReady=%d chunkGenInFlight=%d renderMs=%.2f meshingSubmitMs=%.2f uploadQueueDrainMs=%.2f drawLoopMs=%.2f swapBuffersMs=%.2f%n",
                    frameMs,
                    deltaSeconds * 1_000.0,
                    tickNanos / 1_000_000.0,
                    gameClient.lastEnsureLocalChunksNanos() / 1_000_000.0,
                    gameClient.lastChunkGenerationDrainNanos() / 1_000_000.0,
                    gameClient.lastChunkGenSubmitNanos() / 1_000_000.0,
                    gameClient.lastChunkInstallNanos() / 1_000_000.0,
                    gameClient.lastChunkGenSubmittedCount(),
                    gameClient.lastChunkInstalledCount(),
                    gameClient.pendingChunkGenerationCount(),
                    gameClient.readyGeneratedChunkCount(),
                    gameClient.chunkGenerationJobsInFlight(),
                    renderNanos / 1_000_000.0,
                    renderer.lastMeshingSubmitNanos() / 1_000_000.0,
                    renderer.lastUploadQueueDrainNanos() / 1_000_000.0,
                    renderer.lastDrawLoopNanos() / 1_000_000.0,
                    swapNanos / 1_000_000.0
                );
            }

            frames++;
            if (now - fpsWindowStart >= 1_000_000_000L) {
                displayedFps = frames;
                frames = 0;
                fpsWindowStart = now;
            }
            glfwSetWindowTitle(windowHandle, buildGpuWindowTitle(gameClient, stats, displayedFps));
        }
    }

    private String buildGpuWindowTitle(GameClient gameClient, RenderStats stats, int fps) {
        if (gameClient.isSettingsOpen()) {
            return title + " | Settings | " + gameClient.settingsSummaryText();
        }
        var player = gameClient.playerController(); // meaning
        StringBuilder out = new StringBuilder(title).append(" | GPU"); // meaning
        if (gameClient.showFpsSetting()) {
            out.append(" FPS ").append(fps);
        }
        if (gameClient.showLocationSetting()) {
            out.append(
                String.format(
                    " | WXYZ %d %.2f %.2f %.2f | Y/P %.1f %.1f",
                    gameClient.activeSliceW(),
                    player.eyeX(),
                    player.eyeY(),
                    player.eyeZ(),
                    player.yaw(),
                    player.pitch()
                )
            );
        }
        if (gameClient.showStatsSetting()) {
            out.append(
                String.format(
                    " | Faces t/f/d %d/%d/%d",
                    stats.totalFaces(),
                    stats.frustumCandidates(),
                    stats.drawnFaces()
                )
            );
            out.append(
                " | cg p/r/i "
                    + gameClient.pendingChunkGenerationCount() + "/"
                    + gameClient.readyGeneratedChunkCount() + "/"
                    + gameClient.chunkGenerationJobsInFlight()
            );
            out.append(" | ").append(renderer.latestTitleStats());
        }
        out.append(" | ").append(gameClient.networkStatusLine());
        return out.toString();
    }

    // 中文标注（方法）：`close`，参数：无；用途：执行close相关逻辑。
    @Override
    public void close() {
        if (!initialized) {
            return;
        }

        renderer.close();

        if (windowHandle != NULL) {
            glfwDestroyWindow(windowHandle);
            windowHandle = NULL;
        }
        glfwTerminate();
        initialized = false;
    }

    // 中文标注（方法）：`initialize`，参数：无；用途：执行initialize相关逻辑。
    private void initialize() {
        if (initialized) {
            return;
        }

        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

        windowHandle = glfwCreateWindow(1280, 720, title, NULL, NULL);
        if (windowHandle == NULL) {
            throw new IllegalStateException("Failed to create GLFW window");
        }

        glfwMakeContextCurrent(windowHandle);
        glfwSwapInterval(resolveSwapInterval());
        glfwShowWindow(windowHandle);
        glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        cursorCaptured = true;

        GL.createCapabilities();
        firstMouseSample = true;
        installInputCallbacks();
        initialized = true;
    }

    // 中文标注（方法）：`resolveSwapInterval`，参数：无；用途：执行resolve、swap、interval相关逻辑。
    private static int resolveSwapInterval() {
        // 中文标注（局部变量）：`configured`，含义：用于表示configured。
        String configured = System.getProperty("voxelcraft.vsync"); // meaning
        if (configured != null) {
            try {
                // 中文标注（局部变量）：`parsed`，含义：用于表示parsed。
                int parsed = Integer.parseInt(configured.trim()); // meaning
                if (parsed >= 0 && parsed <= 2) {
                    return parsed;
                }
            // 中文标注（异常参数）：`ignored`，含义：用于表示ignored。
            } catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }

    // 中文标注（方法）：`recordFrameTime`，参数：frameMs；用途：执行record、帧、时间相关逻辑。
    // 中文标注（参数）：`frameMs`，含义：用于表示帧、ms。
    private void recordFrameTime(double frameMs) {
        frameTimeWindowMs[frameTimeWindowIndex] = frameMs;
        frameTimeWindowIndex = (frameTimeWindowIndex + 1) % frameTimeWindowMs.length;
        if (frameTimeWindowCount < frameTimeWindowMs.length) {
            frameTimeWindowCount++;
        }
        framePerfFrames++;
        if (frameMs > framePerfWorstMs) {
            framePerfWorstMs = frameMs;
        }
    }

    // 中文标注（方法）：`emitFramePerfLineIfDue`，参数：无；用途：执行emit、帧、perf、line、if、due相关逻辑。
    private void emitFramePerfLineIfDue() {
        // 中文标注（局部变量）：`now`，含义：用于表示now。
        long now = System.nanoTime(); // meaning
        // 中文标注（局部变量）：`elapsed`，含义：用于表示已耗时。
        long elapsed = now - framePerfWindowStartNanos; // meaning
        if (elapsed < 1_000_000_000L) {
            return;
        }
        // 中文标注（局部变量）：`percentiles`，含义：用于表示percentiles。
        FramePercentiles percentiles = framePercentiles(); // meaning
        // 中文标注（局部变量）：`fps`，含义：用于表示fps。
        double fps = framePerfFrames <= 0 ? 0.0 : (framePerfFrames * 1_000_000_000.0) / elapsed; // meaning
        System.out.printf(
            "[frame-perf] fps=%.1f p50=%.2fms p95=%.2fms p99=%.2fms worst=%.2fms windowFrames=%d chunkPending=%d chunkReady=%d chunkGenInFlight=%d%n",
            fps,
            percentiles.p50Ms,
            percentiles.p95Ms,
            percentiles.p99Ms,
            framePerfWorstMs,
            frameTimeWindowCount,
            lastChunkPendingCount,
            lastChunkReadyCount,
            lastChunkGenInFlightCount
        );
        framePerfWindowStartNanos = now;
        framePerfFrames = 0;
        framePerfWorstMs = 0.0;
    }

    // 中文标注（方法）：`framePercentiles`，参数：无；用途：执行帧、percentiles相关逻辑。
    private FramePercentiles framePercentiles() {
        if (frameTimeWindowCount == 0) {
            return new FramePercentiles(0.0, 0.0, 0.0);
        }
        System.arraycopy(frameTimeWindowMs, 0, frameTimeSortScratchMs, 0, frameTimeWindowCount);
        java.util.Arrays.sort(frameTimeSortScratchMs, 0, frameTimeWindowCount);
        return new FramePercentiles(
            percentile(frameTimeSortScratchMs, frameTimeWindowCount, 0.50),
            percentile(frameTimeSortScratchMs, frameTimeWindowCount, 0.95),
            percentile(frameTimeSortScratchMs, frameTimeWindowCount, 0.99)
        );
    }

    // 中文标注（方法）：`percentile`，参数：sortedValues、count、quantile；用途：执行percentile相关逻辑。
    // 中文标注（参数）：`sortedValues`，含义：用于表示sorted、values。
    // 中文标注（参数）：`count`，含义：用于表示数量。
    // 中文标注（参数）：`quantile`，含义：用于表示quantile。
    private static double percentile(double[] sortedValues, int count, double quantile) {
        if (count <= 0) {
            return 0.0;
        }
        // 中文标注（局部变量）：`index`，含义：用于表示索引。
        int index = (int) Math.ceil((count - 1) * quantile); // meaning
        index = Math.max(0, Math.min(count - 1, index));
        return sortedValues[index];
    }

    // 中文标注（类）：`FramePercentiles`，职责：封装帧、percentiles相关逻辑。
    private static final class FramePercentiles {
        // 中文标注（字段）：`p50Ms`，含义：用于表示p、50、ms。
        private final double p50Ms; // meaning
        // 中文标注（字段）：`p95Ms`，含义：用于表示p、95、ms。
        private final double p95Ms; // meaning
        // 中文标注（字段）：`p99Ms`，含义：用于表示p、99、ms。
        private final double p99Ms; // meaning

        // 中文标注（构造方法）：`FramePercentiles`，参数：p50Ms、p95Ms、p99Ms；用途：初始化`FramePercentiles`实例。
        // 中文标注（参数）：`p50Ms`，含义：用于表示p、50、ms。
        // 中文标注（参数）：`p95Ms`，含义：用于表示p、95、ms。
        // 中文标注（参数）：`p99Ms`，含义：用于表示p、99、ms。
        private FramePercentiles(double p50Ms, double p95Ms, double p99Ms) {
            this.p50Ms = p50Ms;
            this.p95Ms = p95Ms;
            this.p99Ms = p99Ms;
        }
    }


    // 中文标注（方法）：`installInputCallbacks`，参数：无；用途：执行install、输入、callbacks相关逻辑。
    private void installInputCallbacks() {
        // 中文标注（Lambda参数）：`window`，含义：用于表示窗口。
        // 中文标注（Lambda参数）：`key`，含义：用于表示键。
        // 中文标注（Lambda参数）：`scancode`，含义：用于表示scancode。
        // 中文标注（Lambda参数）：`action`，含义：用于表示action。
        // 中文标注（Lambda参数）：`mods`，含义：用于表示mods。
        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            // 中文标注（局部变量）：`mapped`，含义：用于表示mapped。
            int mapped = mapKeyCode(key); // meaning
            if (mapped < 0) {
                return;
            }
            if (action == GLFW_PRESS) {
                input.onKeyPressed(mapped);
            } else if (action == GLFW_RELEASE) {
                input.onKeyReleased(mapped);
            }
        });

        // 中文标注（Lambda参数）：`window`，含义：用于表示窗口。
        // 中文标注（Lambda参数）：`button`，含义：用于表示button。
        // 中文标注（Lambda参数）：`action`，含义：用于表示action。
        // 中文标注（Lambda参数）：`mods`，含义：用于表示mods。
        glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
            // 中文标注（局部变量）：`mapped`，含义：用于表示mapped。
            int mapped = mapMouseButton(button); // meaning
            if (mapped < 0) {
                return;
            }
            if (action == GLFW_PRESS) {
                input.onMousePressed(mapped);
            } else if (action == GLFW_RELEASE) {
                input.onMouseReleased(mapped);
            }
        });

        // 中文标注（Lambda参数）：`window`，含义：用于表示窗口。
        // 中文标注（Lambda参数）：`xpos`，含义：用于表示xpos。
        // 中文标注（Lambda参数）：`ypos`，含义：用于表示ypos。
        glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            // 中文标注（局部变量）：`mouseX`，含义：用于表示鼠标、X坐标。
            int mouseX = (int) Math.round(xpos); // meaning
            // 中文标注（局部变量）：`mouseY`，含义：用于表示鼠标、Y坐标。
            int mouseY = (int) Math.round(ypos); // meaning
            if (firstMouseSample) {
                input.setMousePosition(mouseX, mouseY);
                firstMouseSample = false;
                return;
            }
            input.onMouseMoved(mouseX, mouseY);
        });
    }

    // 中文标注（方法）：`mapMouseButton`，参数：glfwButton；用途：执行映射、鼠标、button相关逻辑。
    // 中文标注（参数）：`glfwButton`，含义：用于表示glfw、button。
    private static int mapMouseButton(int glfwButton) {
        return switch (glfwButton) {
            case GLFW_MOUSE_BUTTON_LEFT -> MouseEvent.BUTTON1;
            case GLFW_MOUSE_BUTTON_RIGHT -> MouseEvent.BUTTON3;
            default -> -1;
        };
    }

    // 中文标注（方法）：`mapKeyCode`，参数：glfwKey；用途：执行映射、键、code相关逻辑。
    // 中文标注（参数）：`glfwKey`，含义：用于表示glfw、键。
    private static int mapKeyCode(int glfwKey) {
        return switch (glfwKey) {
            case GLFW_KEY_W -> KeyEvent.VK_W;
            case GLFW_KEY_S -> KeyEvent.VK_S;
            case GLFW_KEY_A -> KeyEvent.VK_A;
            case GLFW_KEY_D -> KeyEvent.VK_D;
            case GLFW_KEY_E -> KeyEvent.VK_E;
            case GLFW_KEY_O -> KeyEvent.VK_O;
            case GLFW_KEY_SPACE -> KeyEvent.VK_SPACE;
            case GLFW_KEY_LEFT_SHIFT -> KeyEvent.VK_SHIFT;
            case GLFW_KEY_LEFT -> KeyEvent.VK_LEFT;
            case GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT;
            case GLFW_KEY_UP -> KeyEvent.VK_UP;
            case GLFW_KEY_DOWN -> KeyEvent.VK_DOWN;
            case GLFW_KEY_ESCAPE -> KeyEvent.VK_ESCAPE;
            case GLFW_KEY_1 -> KeyEvent.VK_1;
            case GLFW_KEY_2 -> KeyEvent.VK_2;
            case GLFW_KEY_3 -> KeyEvent.VK_3;
            case GLFW_KEY_4 -> KeyEvent.VK_4;
            case GLFW_KEY_5 -> KeyEvent.VK_5;
            case GLFW_KEY_6 -> KeyEvent.VK_6;
            case GLFW_KEY_KP_1 -> KeyEvent.VK_NUMPAD1;
            case GLFW_KEY_KP_2 -> KeyEvent.VK_NUMPAD2;
            case GLFW_KEY_KP_3 -> KeyEvent.VK_NUMPAD3;
            case GLFW_KEY_KP_4 -> KeyEvent.VK_NUMPAD4;
            case GLFW_KEY_KP_5 -> KeyEvent.VK_NUMPAD5;
            case GLFW_KEY_KP_6 -> KeyEvent.VK_NUMPAD6;
            default -> -1;
        };
    }
}
