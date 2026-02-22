package dev.voxelcraft.server;

import dev.voxelcraft.server.net.VoxelcraftServer;
import java.net.BindException;

public final class ServerMain {
    private static final int DEFAULT_PORT = 25_565;

    private ServerMain() {
    }

    public static void main(String[] args) {
        int port = parsePort(args);

        try (VoxelcraftServer server = new VoxelcraftServer(port)) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "voxelcraft-server-shutdown"));
            server.start();
            server.blockUntilShutdown();
        } catch (BindException bindException) {
            System.err.println("[server] port " + port + " is already in use.");
            System.err.println("[server] if another voxelcraft server is already running, connect client directly.");
            System.err.println("[server] otherwise run with another port, e.g. --port 25566");
            System.exit(2);
        } catch (Exception exception) {
            System.err.println("[server] fatal error: " + exception.getMessage());
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static int parsePort(String[] args) {
        int port = DEFAULT_PORT;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
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

    private static int parsePortValue(String raw, int fallback) {
        try {
            int port = Integer.parseInt(raw);
            if (port < 1 || port > 65_535) {
                return fallback;
            }
            return port;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
