package dev.voxelcraft.client;

import dev.voxelcraft.client.network.NetworkClient;
import dev.voxelcraft.client.platform.InputState;
import dev.voxelcraft.client.platform.Window;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public final class VoxelcraftClientApp {
    private static final int TARGET_FPS = 60;
    private static final double MIN_FRAME_SECONDS = 1.0 / TARGET_FPS;

    private final LaunchConfig config;

    public VoxelcraftClientApp() {
        this(LaunchConfig.defaults());
    }

    public VoxelcraftClientApp(LaunchConfig config) {
        this.config = config;
    }

    public void run() {
        if (GraphicsEnvironment.isHeadless()) {
            runHeadlessFallback();
            return;
        }

        try (GameClient gameClient = createGameClient()) {
            boolean launchedGpu = false;
            if (config.renderMode != RenderMode.SOFTWARE) {
                launchedGpu = tryRunGpuRuntime(gameClient);
                if (!launchedGpu && config.renderMode == RenderMode.GPU) {
                    throw new IllegalStateException("GPU mode requested but OpenGL runtime failed to start");
                }
            }

            if (!launchedGpu) {
                runSoftwareRuntime(gameClient);
            }
        } catch (Exception exception) {
            System.err.println("[client] fatal error: " + exception.getMessage());
            exception.printStackTrace(System.err);
            throw new RuntimeException("Client startup failed", exception);
        }
    }

    private GameClient createGameClient() throws IOException {
        GameClient gameClient = new GameClient();
        if (config.hasRemote()) {
            try {
                NetworkClient networkClient = NetworkClient.connect(config.connectHost, config.connectPort);
                gameClient.attachNetwork(networkClient);
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

    private boolean tryRunGpuRuntime(GameClient gameClient) {
        try {
            Class<?> runtimeClass = Class.forName("dev.voxelcraft.client.runtime.GpuClientRuntime");
            Object runtime = runtimeClass.getConstructor(String.class).newInstance("Voxelcraft");
            try {
                runtimeClass.getMethod("run", GameClient.class).invoke(runtime, gameClient);
            } finally {
                if (runtime instanceof AutoCloseable autoCloseable) {
                    autoCloseable.close();
                }
            }
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError throwable) {
            System.err.println("[client] GPU runtime unavailable, falling back to software: " + throwable.getMessage());
            return false;
        } catch (InvocationTargetException invocationTargetException) {
            Throwable cause = invocationTargetException.getCause();
            System.err.println("[client] GPU runtime failed: " + (cause == null ? invocationTargetException.getMessage() : cause.getMessage()));
            return false;
        } catch (Exception exception) {
            System.err.println("[client] GPU runtime failed: " + exception.getMessage());
            return false;
        }
    }

    private void runSoftwareRuntime(GameClient gameClient) {
        try (Window window = new Window("Voxelcraft", 960, 540)) {
            long previousNanos = System.nanoTime();
            long fpsWindowStart = previousNanos;
            int frames = 0;

            while (window.isOpen()) {
                long now = System.nanoTime();
                double deltaSeconds = (now - previousNanos) / 1_000_000_000.0;
                previousNanos = now;

                if (deltaSeconds <= 0.0) {
                    deltaSeconds = MIN_FRAME_SECONDS;
                }
                if (deltaSeconds > 0.05) {
                    deltaSeconds = 0.05;
                }

                window.pollEvents();
                InputState input = window.input();
                gameClient.tick(input, deltaSeconds);

                Graphics2D graphics = window.beginRender();
                gameClient.render(graphics, window.width(), window.height());
                window.endRender(graphics);

                window.endFrame();

                frames++;
                if (now - fpsWindowStart >= 1_000_000_000L) {
                    window.setTitle("Voxelcraft | FPS " + frames + " | " + gameClient.networkStatusLine());
                    frames = 0;
                    fpsWindowStart = now;
                }

                sleepBriefly();
            }
        }
    }

    private void runHeadlessFallback() {
        try (GameClient gameClient = createGameClient()) {
            InputState input = new InputState();
            for (int i = 0; i < 240; i++) {
                gameClient.tick(input, MIN_FRAME_SECONDS);
                input.endFrame();
            }
            System.out.println("Headless environment detected: simulated 240 client ticks.");
        } catch (IOException exception) {
            System.err.println("[client] headless setup failed: " + exception.getMessage());
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(1L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public static final class LaunchConfig {
        private final RenderMode renderMode;
        private final String connectHost;
        private final int connectPort;

        private LaunchConfig(RenderMode renderMode, String connectHost, int connectPort) {
            this.renderMode = renderMode;
            this.connectHost = connectHost;
            this.connectPort = connectPort;
        }

        public static LaunchConfig defaults() {
            return new LaunchConfig(RenderMode.AUTO, null, -1);
        }

        public static LaunchConfig parse(String[] args) {
            RenderMode renderMode = RenderMode.AUTO;
            String host = null;
            int port = -1;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.startsWith("--render=")) {
                    renderMode = RenderMode.parse(arg.substring("--render=".length()));
                    continue;
                }
                if ("--render".equals(arg) && i + 1 < args.length) {
                    renderMode = RenderMode.parse(args[++i]);
                    continue;
                }

                if (arg.startsWith("--connect=")) {
                    HostPort parsed = HostPort.parse(arg.substring("--connect=".length()));
                    host = parsed.host;
                    port = parsed.port;
                    continue;
                }
                if ("--connect".equals(arg) && i + 1 < args.length) {
                    HostPort parsed = HostPort.parse(args[++i]);
                    host = parsed.host;
                    port = parsed.port;
                }
            }

            return new LaunchConfig(renderMode, host, port);
        }

        public boolean hasRemote() {
            return connectHost != null && connectPort > 0;
        }
    }

    public enum RenderMode {
        AUTO,
        SOFTWARE,
        GPU;

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

    private static final class HostPort {
        private final String host;
        private final int port;

        private HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }

        private static HostPort parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new HostPort(null, -1);
            }

            int separator = raw.lastIndexOf(':');
            if (separator <= 0 || separator == raw.length() - 1) {
                return new HostPort(raw, 25_565);
            }

            String host = raw.substring(0, separator);
            String portText = raw.substring(separator + 1);
            int port;
            try {
                port = Integer.parseInt(portText);
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
