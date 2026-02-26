package dev.voxelcraft.server;

import dev.voxelcraft.server.net.VoxelcraftServer;
import java.net.BindException;
/**
 * 中文说明：服务器组件：负责 ServerMain 的启动或服务端运行逻辑。
 */

// 中文标注（类）：`ServerMain`，职责：封装服务器、主相关逻辑。
public final class ServerMain {
    // 中文标注（字段）：`DEFAULT_PORT`，含义：用于表示默认、port。
    private static final int DEFAULT_PORT = 25_565; // meaning

    // 中文标注（构造方法）：`ServerMain`，参数：无；用途：初始化`ServerMain`实例。
    private ServerMain() {
    }

    // 中文标注（方法）：`main`，参数：args；用途：执行主相关逻辑。
    // 中文标注（参数）：`args`，含义：用于表示args。
    public static void main(String[] args) {
        // 中文标注（局部变量）：`port`，含义：用于表示port。
        int port = parsePort(args); // meaning

        // 中文标注（局部变量）：`server`，含义：用于表示服务器。
        try (VoxelcraftServer server = new VoxelcraftServer(port)) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "voxelcraft-server-shutdown"));
            server.start();
            server.blockUntilShutdown();
        // 中文标注（异常参数）：`bindException`，含义：用于表示绑定、exception。
        } catch (BindException bindException) {
            System.err.println("[server] port " + port + " is already in use.");
            System.err.println("[server] if another voxelcraft server is already running, connect client directly.");
            System.err.println("[server] otherwise run with another port, e.g. --port 25566");
            System.exit(2);
        // 中文标注（异常参数）：`exception`，含义：用于表示exception。
        } catch (Exception exception) {
            System.err.println("[server] fatal error: " + exception.getMessage());
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    // 中文标注（方法）：`parsePort`，参数：args；用途：执行parse、port相关逻辑。
    // 中文标注（参数）：`args`，含义：用于表示args。
    private static int parsePort(String[] args) {
        // 中文标注（局部变量）：`port`，含义：用于表示port。
        int port = DEFAULT_PORT; // meaning
        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = 0; i < args.length; i++) { // meaning
            // 中文标注（局部变量）：`arg`，含义：用于表示arg。
            String arg = args[i]; // meaning
            if ("--port".equals(arg) && i + 1 < args.length) {
                port = parsePortValue(args[++i], port);
                continue;
            }
            if (arg.startsWith("--port=")) {
                port = parsePortValue(arg.substring("--port=".length()), port);
            }
        }
        return port;
    }

    // 中文标注（方法）：`parsePortValue`，参数：raw、fallback；用途：执行parse、port、值相关逻辑。
    // 中文标注（参数）：`raw`，含义：用于表示raw。
    // 中文标注（参数）：`fallback`，含义：用于表示fallback。
    private static int parsePortValue(String raw, int fallback) {
        try {
            // 中文标注（局部变量）：`port`，含义：用于表示port。
            int port = Integer.parseInt(raw); // meaning
            if (port < 1 || port > 65_535) {
                return fallback;
            }
            return port;
        // 中文标注（异常参数）：`ignored`，含义：用于表示ignored。
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
