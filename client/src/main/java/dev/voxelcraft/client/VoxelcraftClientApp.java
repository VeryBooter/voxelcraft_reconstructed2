package dev.voxelcraft.client;

import dev.voxelcraft.client.network.NetworkClient;
import dev.voxelcraft.client.platform.InputState;
import dev.voxelcraft.client.platform.Window;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
/**
 * 中文说明：客户端组件：负责 VoxelcraftClientApp 的启动、状态或子系统逻辑。
 */

// 中文标注（类）：`VoxelcraftClientApp`，职责：封装voxelcraft、客户端、应用相关逻辑。
public final class VoxelcraftClientApp {
    // 中文标注（字段）：`TARGET_FPS`，含义：用于表示target、fps。
    private static final int TARGET_FPS = 60; // meaning
    // 中文标注（字段）：`MIN_FRAME_SECONDS`，含义：用于表示最小、帧、seconds。
    private static final double MIN_FRAME_SECONDS = 1.0 / TARGET_FPS; // meaning

    // 中文标注（字段）：`config`，含义：用于表示config。
    private final LaunchConfig config; // meaning

    // 中文标注（构造方法）：`VoxelcraftClientApp`，参数：无；用途：初始化`VoxelcraftClientApp`实例。
    public VoxelcraftClientApp() {
        this(LaunchConfig.defaults());
    }

    // 中文标注（构造方法）：`VoxelcraftClientApp`，参数：config；用途：初始化`VoxelcraftClientApp`实例。
    // 中文标注（参数）：`config`，含义：用于表示config。
    public VoxelcraftClientApp(LaunchConfig config) {
        this.config = config;
    }

    // 中文标注（方法）：`run`，参数：无；用途：执行run相关逻辑。
    public void run() {
        if (GraphicsEnvironment.isHeadless()) {
            runHeadlessFallback();
            return;
        }

        // 中文标注（局部变量）：`gameClient`，含义：用于表示game、客户端。
        try (GameClient gameClient = createGameClient()) {
            // 中文标注（局部变量）：`launchedGpu`，含义：用于表示launched、GPU。
            boolean launchedGpu = false; // meaning
            if (config.renderMode != RenderMode.SOFTWARE) {
                launchedGpu = tryRunGpuRuntime(gameClient);
                if (!launchedGpu && config.renderMode == RenderMode.GPU) {
                    throw new IllegalStateException("GPU mode requested but OpenGL runtime failed to start");
                }
            }

            if (!launchedGpu) {
                runSoftwareRuntime(gameClient);
            }
        // 中文标注（异常参数）：`exception`，含义：用于表示exception。
        } catch (Exception exception) {
            System.err.println("[client] fatal error: " + exception.getMessage());
            exception.printStackTrace(System.err);
            throw new RuntimeException("Client startup failed", exception);
        }
    }

    // 中文标注（方法）：`createGameClient`，参数：无；用途：构建或创建创建、game、客户端。
    private GameClient createGameClient() throws IOException {
        // 中文标注（局部变量）：`gameClient`，含义：用于表示game、客户端。
        GameClient gameClient = new GameClient(); // meaning
        if (config.hasRemote()) {
            try {
                // 中文标注（局部变量）：`networkClient`，含义：用于表示网络、客户端。
                NetworkClient networkClient = NetworkClient.connect(config.connectHost, config.connectPort); // meaning
                gameClient.attachNetwork(networkClient);
            // 中文标注（异常参数）：`ioException`，含义：用于表示io、exception。
            } catch (IOException ioException) {
                throw new IOException(
                    "Failed to connect to " + config.connectHost + ":" + config.connectPort
                        + ". Start server with './gradlew :server:runLocal' "
                        + "or run singleplayer with './gradlew :client:runSoftware'.",
                    ioException
                );
            }
        }
        return gameClient;
    }

    // 中文标注（方法）：`tryRunGpuRuntime`，参数：gameClient；用途：执行try、run、GPU、运行时相关逻辑。
    // 中文标注（参数）：`gameClient`，含义：用于表示game、客户端。
    private boolean tryRunGpuRuntime(GameClient gameClient) {
        try {
            // 中文标注（局部变量）：`runtimeClass`，含义：用于表示运行时、class。
            Class<?> runtimeClass = Class.forName("dev.voxelcraft.client.runtime.GpuClientRuntime"); // meaning
            // 中文标注（局部变量）：`runtime`，含义：用于表示运行时。
            Object runtime = runtimeClass.getConstructor(String.class).newInstance("Voxelcraft"); // meaning
            try {
                runtimeClass.getMethod("run", GameClient.class).invoke(runtime, gameClient);
            } finally {
                // 中文标注（局部变量）：`autoCloseable`，含义：用于表示auto、closeable。
                if (runtime instanceof AutoCloseable autoCloseable) {
                    autoCloseable.close();
                }
            }
            return true;
        // 中文标注（异常参数）：`throwable`，含义：用于表示throwable。
        } catch (ClassNotFoundException | NoClassDefFoundError throwable) {
            System.err.println("[client] GPU runtime unavailable, falling back to software: " + throwable.getMessage());
            return false;
        // 中文标注（异常参数）：`invocationTargetException`，含义：用于表示invocation、target、exception。
        } catch (InvocationTargetException invocationTargetException) {
            // 中文标注（局部变量）：`cause`，含义：用于表示cause。
            Throwable cause = invocationTargetException.getCause(); // meaning
            System.err.println("[client] GPU runtime failed: " + (cause == null ? invocationTargetException.getMessage() : cause.getMessage()));
            return false;
        // 中文标注（异常参数）：`exception`，含义：用于表示exception。
        } catch (Exception exception) {
            System.err.println("[client] GPU runtime failed: " + exception.getMessage());
            return false;
        }
    }

    // 中文标注（方法）：`runSoftwareRuntime`，参数：gameClient；用途：执行run、software、运行时相关逻辑。
    // 中文标注（参数）：`gameClient`，含义：用于表示game、客户端。
    private void runSoftwareRuntime(GameClient gameClient) {
        // 中文标注（局部变量）：`window`，含义：用于表示窗口。
        try (Window window = new Window("Voxelcraft", 960, 540)) {
            // 中文标注（局部变量）：`previousNanos`，含义：用于表示previous、nanos。
            long previousNanos = System.nanoTime(); // meaning
            // 中文标注（局部变量）：`fpsWindowStart`，含义：用于表示fps、窗口、开始。
            long fpsWindowStart = previousNanos; // meaning
            // 中文标注（局部变量）：`frames`，含义：用于表示frames。
            int frames = 0; // meaning
            int displayedFps = 0; // meaning

            while (window.isOpen()) {
                window.setMouseCaptureEnabled(!gameClient.isAnyUiOpen());

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

                window.pollEvents();
                // 中文标注（局部变量）：`input`，含义：用于表示输入。
                InputState input = window.input(); // meaning
                gameClient.tick(input, deltaSeconds);
                window.setMouseCaptureEnabled(!gameClient.isAnyUiOpen());

                // 中文标注（局部变量）：`graphics`，含义：用于表示graphics。
                Graphics2D graphics = window.beginRender(); // meaning
                gameClient.render(graphics, window.width(), window.height());
                window.endRender(graphics);

                window.endFrame();

                frames++;
                if (now - fpsWindowStart >= 1_000_000_000L) {
                    displayedFps = frames;
                    frames = 0;
                    fpsWindowStart = now;
                }
                window.setTitle(buildSoftwareWindowTitle(gameClient, displayedFps));

                sleepBriefly();
            }
        }
    }

    // 中文标注（方法）：`runHeadlessFallback`，参数：无；用途：执行run、headless、fallback相关逻辑。
    private void runHeadlessFallback() {
        // 中文标注（局部变量）：`gameClient`，含义：用于表示game、客户端。
        try (GameClient gameClient = createGameClient()) {
            // 中文标注（局部变量）：`input`，含义：用于表示输入。
            InputState input = new InputState(); // meaning
            // 中文标注（局部变量）：`i`，含义：用于表示i。
            for (int i = 0; i < 240; i++) { // meaning
                gameClient.tick(input, MIN_FRAME_SECONDS);
                input.endFrame();
            }
            System.out.println("Headless environment detected: simulated 240 client ticks.");
        // 中文标注（异常参数）：`exception`，含义：用于表示exception。
        } catch (IOException exception) {
            System.err.println("[client] headless setup failed: " + exception.getMessage());
        }
    }

    // 中文标注（方法）：`sleepBriefly`，参数：无；用途：执行sleep、briefly相关逻辑。
    private static void sleepBriefly() {
        try {
            Thread.sleep(1L);
        // 中文标注（异常参数）：`interrupted`，含义：用于表示interrupted。
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String buildSoftwareWindowTitle(GameClient gameClient, int fps) {
        if (gameClient.isSettingsOpen()) {
            return "Voxelcraft | Settings | " + gameClient.settingsSummaryText();
        }
        var player = gameClient.playerController(); // meaning
        StringBuilder title = new StringBuilder("Voxelcraft"); // meaning
        if (gameClient.showFpsSetting()) {
            title.append(" | FPS ").append(fps);
        }
        if (gameClient.showLocationSetting()) {
            title.append(String.format(" | WXYZ %d %.2f %.2f %.2f", gameClient.activeSliceW(), player.x(), player.y(), player.z()));
        }
        if (gameClient.showStatsSetting()) {
            title.append(
                " | cg p/r/i "
                    + gameClient.pendingChunkGenerationCount() + "/"
                    + gameClient.readyGeneratedChunkCount() + "/"
                    + gameClient.chunkGenerationJobsInFlight()
            );
        }
        title.append(" | ").append(gameClient.networkStatusLine());
        return title.toString();
    }

    // 中文标注（类）：`LaunchConfig`，职责：封装launch、config相关逻辑。
    public static final class LaunchConfig {
        // 中文标注（字段）：`renderMode`，含义：用于表示渲染、模式。
        private final RenderMode renderMode; // meaning
        // 中文标注（字段）：`connectHost`，含义：用于表示connect、host。
        private final String connectHost; // meaning
        // 中文标注（字段）：`connectPort`，含义：用于表示connect、port。
        private final int connectPort; // meaning

        // 中文标注（构造方法）：`LaunchConfig`，参数：renderMode、connectHost、connectPort；用途：初始化`LaunchConfig`实例。
        // 中文标注（参数）：`renderMode`，含义：用于表示渲染、模式。
        // 中文标注（参数）：`connectHost`，含义：用于表示connect、host。
        // 中文标注（参数）：`connectPort`，含义：用于表示connect、port。
        private LaunchConfig(RenderMode renderMode, String connectHost, int connectPort) {
            this.renderMode = renderMode;
            this.connectHost = connectHost;
            this.connectPort = connectPort;
        }

        // 中文标注（方法）：`defaults`，参数：无；用途：执行defaults相关逻辑。
        public static LaunchConfig defaults() {
            return new LaunchConfig(RenderMode.AUTO, null, -1);
        }

        // 中文标注（方法）：`parse`，参数：args；用途：执行parse相关逻辑。
        // 中文标注（参数）：`args`，含义：用于表示args。
        public static LaunchConfig parse(String[] args) {
            // 中文标注（局部变量）：`renderMode`，含义：用于表示渲染、模式。
            RenderMode renderMode = RenderMode.AUTO; // meaning
            // 中文标注（局部变量）：`host`，含义：用于表示host。
            String host = null; // meaning
            // 中文标注（局部变量）：`port`，含义：用于表示port。
            int port = -1; // meaning

            // 中文标注（局部变量）：`i`，含义：用于表示i。
            for (int i = 0; i < args.length; i++) { // meaning
                // 中文标注（局部变量）：`arg`，含义：用于表示arg。
                String arg = args[i]; // meaning
                if (arg.startsWith("--render=")) {
                    renderMode = RenderMode.parse(arg.substring("--render=".length()));
                    continue;
                }
                if ("--render".equals(arg) && i + 1 < args.length) {
                    renderMode = RenderMode.parse(args[++i]);
                    continue;
                }

                if (arg.startsWith("--connect=")) {
                    // 中文标注（局部变量）：`parsed`，含义：用于表示parsed。
                    HostPort parsed = HostPort.parse(arg.substring("--connect=".length())); // meaning
                    host = parsed.host;
                    port = parsed.port;
                    continue;
                }
                if ("--connect".equals(arg) && i + 1 < args.length) {
                    // 中文标注（局部变量）：`parsed`，含义：用于表示parsed。
                    HostPort parsed = HostPort.parse(args[++i]); // meaning
                    host = parsed.host;
                    port = parsed.port;
                }
            }

            return new LaunchConfig(renderMode, host, port);
        }

        // 中文标注（方法）：`hasRemote`，参数：无；用途：判断remote是否满足条件。
        public boolean hasRemote() {
            return connectHost != null && connectPort > 0;
        }
    }

    // 中文标注（枚举）：`RenderMode`，职责：封装渲染、模式相关逻辑。
    public enum RenderMode {
        // 中文标注（字段）：`AUTO`，含义：用于表示auto。
        AUTO,
        // 中文标注（字段）：`SOFTWARE`，含义：用于表示software。
        SOFTWARE,
        // 中文标注（字段）：`GPU`，含义：用于表示GPU。
        GPU; // meaning

        // 中文标注（方法）：`parse`，参数：raw；用途：执行parse相关逻辑。
        // 中文标注（参数）：`raw`，含义：用于表示raw。
        public static RenderMode parse(String raw) {
            if (raw == null) {
                return AUTO;
            }

            return switch (raw.trim().toLowerCase()) {
                case "gpu", "opengl", "gl" -> GPU;
                case "software", "sw", "cpu" -> SOFTWARE;
                default -> AUTO;
            };
        }
    }

    // 中文标注（类）：`HostPort`，职责：封装host、port相关逻辑。
    private static final class HostPort {
        // 中文标注（字段）：`host`，含义：用于表示host。
        private final String host; // meaning
        // 中文标注（字段）：`port`，含义：用于表示port。
        private final int port; // meaning

        // 中文标注（构造方法）：`HostPort`，参数：host、port；用途：初始化`HostPort`实例。
        // 中文标注（参数）：`host`，含义：用于表示host。
        // 中文标注（参数）：`port`，含义：用于表示port。
        private HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }

        // 中文标注（方法）：`parse`，参数：raw；用途：执行parse相关逻辑。
        // 中文标注（参数）：`raw`，含义：用于表示raw。
        private static HostPort parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new HostPort(null, -1);
            }

            // 中文标注（局部变量）：`separator`，含义：用于表示separator。
            int separator = raw.lastIndexOf(':'); // meaning
            if (separator <= 0 || separator == raw.length() - 1) {
                return new HostPort(raw, 25_565);
            }

            // 中文标注（局部变量）：`host`，含义：用于表示host。
            String host = raw.substring(0, separator); // meaning
            // 中文标注（局部变量）：`portText`，含义：用于表示port、text。
            String portText = raw.substring(separator + 1); // meaning
            // 中文标注（局部变量）：`port`，含义：用于表示port。
            int port; // meaning
            try {
                port = Integer.parseInt(portText);
            // 中文标注（异常参数）：`ignored`，含义：用于表示ignored。
            } catch (NumberFormatException ignored) {
                port = 25_565;
            }

            if (port < 1 || port > 65_535) {
                port = 25_565;
            }

            return new HostPort(host, port);
        }
    }
}
