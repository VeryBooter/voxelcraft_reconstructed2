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
import static org.lwjgl.glfw.GLFW.GLFW_KEY_1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_2;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_3;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_4;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_5;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_2;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_3;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_5;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
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

public final class GpuClientRuntime implements AutoCloseable {
    private static final int TARGET_FPS = 60;
    private static final double MIN_FRAME_SECONDS = 1.0 / TARGET_FPS;

    private final String title;
    private final InputState input = new InputState();
    private final GpuChunkRenderer renderer = new GpuChunkRenderer();

    private long windowHandle;
    private boolean initialized;
    private boolean firstMouseSample = true;

    public GpuClientRuntime(String title) {
        this.title = title;
    }

    public void run(GameClient gameClient) {
        initialize();

        long previousNanos = System.nanoTime();
        long fpsWindowStart = previousNanos;
        int frames = 0;

        while (!glfwWindowShouldClose(windowHandle)) {
            long now = System.nanoTime();
            double deltaSeconds = (now - previousNanos) / 1_000_000_000.0;
            previousNanos = now;

            if (deltaSeconds <= 0.0) {
                deltaSeconds = MIN_FRAME_SECONDS;
            }
            if (deltaSeconds > 0.05) {
                deltaSeconds = 0.05;
            }

            glfwPollEvents();

            if (input.wasKeyPressed(KeyEvent.VK_ESCAPE)) {
                glfwSetWindowShouldClose(windowHandle, true);
            }

            gameClient.tick(input, deltaSeconds);

            int width;
            int height;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var widthBuffer = stack.mallocInt(1);
                var heightBuffer = stack.mallocInt(1);
                glfwGetFramebufferSize(windowHandle, widthBuffer, heightBuffer);
                width = Math.max(1, widthBuffer.get(0));
                height = Math.max(1, heightBuffer.get(0));
            }

            RenderStats stats = renderer.render(width, height, gameClient);
            glfwSwapBuffers(windowHandle);

            input.endFrame();

            frames++;
            if (now - fpsWindowStart >= 1_000_000_000L) {
                glfwSetWindowTitle(
                    windowHandle,
                    title + " | GPU FPS " + frames
                        + " | faces " + stats.drawnFaces()
                        + " | " + gameClient.networkStatusLine()
                );
                frames = 0;
                fpsWindowStart = now;
            }
        }
    }

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

        GL.createCapabilities();
        firstMouseSample = true;
        installInputCallbacks();
        initialized = true;
    }

    private static int resolveSwapInterval() {
        String configured = System.getProperty("voxelcraft.vsync");
        if (configured != null) {
            try {
                int parsed = Integer.parseInt(configured.trim());
                if (parsed >= 0 && parsed <= 2) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }

    private void installInputCallbacks() {
        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            int mapped = mapKeyCode(key);
            if (mapped < 0) {
                return;
            }
            if (action == GLFW_PRESS) {
                input.onKeyPressed(mapped);
            } else if (action == GLFW_RELEASE) {
                input.onKeyReleased(mapped);
            }
        });

        glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
            int mapped = mapMouseButton(button);
            if (mapped < 0) {
                return;
            }
            if (action == GLFW_PRESS) {
                input.onMousePressed(mapped);
            } else if (action == GLFW_RELEASE) {
                input.onMouseReleased(mapped);
            }
        });

        glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            int mouseX = (int) Math.round(xpos);
            int mouseY = (int) Math.round(ypos);
            if (firstMouseSample) {
                input.setMousePosition(mouseX, mouseY);
                firstMouseSample = false;
                return;
            }
            input.onMouseMoved(mouseX, mouseY);
        });
    }

    private static int mapMouseButton(int glfwButton) {
        return switch (glfwButton) {
            case GLFW_MOUSE_BUTTON_LEFT -> MouseEvent.BUTTON1;
            case GLFW_MOUSE_BUTTON_RIGHT -> MouseEvent.BUTTON3;
            default -> -1;
        };
    }

    private static int mapKeyCode(int glfwKey) {
        return switch (glfwKey) {
            case GLFW_KEY_W -> KeyEvent.VK_W;
            case GLFW_KEY_S -> KeyEvent.VK_S;
            case GLFW_KEY_A -> KeyEvent.VK_A;
            case GLFW_KEY_D -> KeyEvent.VK_D;
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
            case GLFW_KEY_KP_1 -> KeyEvent.VK_NUMPAD1;
            case GLFW_KEY_KP_2 -> KeyEvent.VK_NUMPAD2;
            case GLFW_KEY_KP_3 -> KeyEvent.VK_NUMPAD3;
            case GLFW_KEY_KP_4 -> KeyEvent.VK_NUMPAD4;
            case GLFW_KEY_KP_5 -> KeyEvent.VK_NUMPAD5;
            default -> -1;
        };
    }
}
