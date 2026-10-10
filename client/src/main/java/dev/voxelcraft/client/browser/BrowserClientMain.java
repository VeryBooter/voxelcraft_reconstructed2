package dev.voxelcraft.client.browser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.voxelcraft.client.GameClient;
import dev.voxelcraft.client.network.NetworkClient;
import dev.voxelcraft.client.platform.InputState;
import dev.voxelcraft.client.render.MetalChunkRenderer;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.BitSet;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Browser transport for the existing Java game. All world, player and UI logic stays in GameClient. */
public final class BrowserClientMain {
    private static final int WIDTH = boundedProperty("vc.browser.width", 960, 320, 1920);
    private static final int HEIGHT = boundedProperty("vc.browser.height", 540, 240, 1080);
    private static final int FPS = boundedProperty("vc.browser.fps", 30, 1, 60);
    private static final ArrayBlockingQueue<String> EVENTS = new ArrayBlockingQueue<>(4096);
    private static volatile Frame latest;
    private static volatile long lastRequest;
    private static volatile long lastInput;
    private static volatile boolean running = true;
    private static volatile String failure;

    private record Frame(byte[] jpeg, String status, boolean uiOpen) {}

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        int port = boundedProperty("vc.browser.port", 4173, 1024, 65535);
        // Loopback by default; explicit LAN opt-in allows phone/iPad on the same trusted network.
        String host = System.getProperty("vc.browser.host", "127.0.0.1");
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        var executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor);
        server.createContext("/", BrowserClientMain::handle);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            server.stop(0);
            executor.shutdownNow();
        }, "browser-shutdown"));
        server.start();
        System.out.println("Original Java client: http://" + host + ":" + port);
        try {
            renderLoop();
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            if (path.equals("/input")) {
                if (!exchange.getRequestMethod().equals("POST")) { reply(exchange, 405, "text/plain", new byte[0]); return; }
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                if (origin != null && !URI.create(origin).getAuthority().equals(exchange.getRequestHeaders().getFirst("Host"))) {
                    reply(exchange, 403, "text/plain", new byte[0]); return;
                }
                byte[] body = exchange.getRequestBody().readNBytes(65537);
                if (body.length > 65536) { reply(exchange, 413, "text/plain", new byte[0]); return; }
                String[] commands = new String(body, StandardCharsets.UTF_8).split("\n");
                for (String command : commands) {
                    if (!validCommand(command)) { reply(exchange, 400, "text/plain", new byte[0]); return; }
                }
                if (EVENTS.remainingCapacity() < commands.length) { reply(exchange, 429, "text/plain", new byte[0]); return; }
                for (String command : commands) EVENTS.offer(command);
                lastInput = System.nanoTime();
                reply(exchange, 204, "text/plain", new byte[0]);
                return;
            }
            if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
                reply(exchange, 405, "text/plain", new byte[0]); return;
            }
            if (path.equals("/frame") || path.equals("/status")) {
                lastRequest = System.nanoTime();
                Frame frame = latest;
                if (frame == null || failure != null) {
                    reply(exchange, 503, "text/plain; charset=utf-8", (failure == null ? "Java 客户端正在启动…" : failure).getBytes(StandardCharsets.UTF_8));
                } else if (path.equals("/status")) {
                    reply(exchange, 200, "application/json", frame.status.getBytes(StandardCharsets.UTF_8));
                } else {
                    exchange.getResponseHeaders().set("X-UI-Open", Boolean.toString(frame.uiOpen));
                    reply(exchange, 200, "image/jpeg", frame.jpeg);
                }
                return;
            }
            String resource = switch (path) {
                case "/", "/index.html" -> "index.html";
                case "/game.js" -> "game.js";
                case "/style.css" -> "style.css";
                default -> null;
            };
            if (resource == null) { reply(exchange, 404, "text/plain", new byte[0]); return; }
            try (var stream = BrowserClientMain.class.getResourceAsStream("/browser/" + resource)) {
                if (stream == null) { reply(exchange, 404, "text/plain", new byte[0]); return; }
                String type = resource.endsWith("html") ? "text/html; charset=utf-8" : resource.endsWith("js") ? "text/javascript; charset=utf-8" : "text/css";
                reply(exchange, 200, type, stream.readAllBytes());
            }
        } catch (IllegalArgumentException invalid) {
            // Malformed request URI or origin; no changes reach the game thread.
        }
    }

    private static void reply(HttpExchange exchange, int code, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        boolean noBody = code == 204 || exchange.getRequestMethod().equals("HEAD");
        exchange.sendResponseHeaders(code, noBody ? -1 : body.length);
        if (!noBody) exchange.getResponseBody().write(body);
    }

    static boolean validCommand(String command) {
        if (command.equals("clear") || command.equals("ping")) return true;
        String[] parts = command.split(" ");
        if (parts.length != 2 && parts.length != 3) return false;
        try {
            int a = Integer.parseInt(parts[1]);
            return switch (parts[0]) {
                case "kd", "ku" -> parts.length == 2 && a >= 0 && a < 1024;
                case "md", "mu" -> parts.length == 2 && a >= 1 && a <= 3;
                case "look", "pos" -> parts.length == 3 && Math.abs((long) a) <= 10000 && Math.abs((long) Integer.parseInt(parts[2])) <= 10000;
                default -> false;
            };
        } catch (NumberFormatException invalid) { return false; }
    }

    static void applyCommand(InputState input, String command) {
        String[] parts = command.split(" ");
        switch (parts[0]) {
            case "clear" -> input.clearAll();
            case "kd" -> input.onKeyPressed(Integer.parseInt(parts[1]));
            case "ku" -> input.onKeyReleased(Integer.parseInt(parts[1]));
            case "md" -> input.onMousePressed(Integer.parseInt(parts[1]));
            case "mu" -> input.onMouseReleased(Integer.parseInt(parts[1]));
            case "look" -> input.onMouseDelta(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            case "pos" -> input.setMousePosition(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            default -> { }
        }
    }

    static void drainInput(Queue<String> events, InputState input) {
        BitSet pressedKeys = new BitSet();
        BitSet pressedButtons = new BitSet();
        for (String event; (event = events.peek()) != null;) {
            String[] parts = event.split(" ");
            // The original hotbar/interactions inspect held state. Preserve a fast tap for at
            // least one tick instead of cancelling it when down/up arrive in the same HTTP batch.
            if (parts[0].equals("ku") && pressedKeys.get(Integer.parseInt(parts[1]))) break;
            if (parts[0].equals("mu") && pressedButtons.get(Integer.parseInt(parts[1]))) break;
            events.poll();
            applyCommand(input, event);
            if (parts[0].equals("kd")) pressedKeys.set(Integer.parseInt(parts[1]));
            if (parts[0].equals("md")) pressedButtons.set(Integer.parseInt(parts[1]));
            if (parts[0].equals("clear")) { pressedKeys.clear(); pressedButtons.clear(); }
        }
    }

    private static void renderLoop() throws Exception {
        boolean metal = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")
            && !System.getProperty("vc.browser.renderer", "metal").equals("software");
        InputState input = new InputState();
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        int[] rgb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        ByteBuffer pixels = metal ? ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4) : null;
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam jpegParameters = writer.getDefaultWriteParam();
        jpegParameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpegParameters.setCompressionQuality(0.85f);
        try (GameClient game = new GameClient(); MetalChunkRenderer renderer = metal ? new MetalChunkRenderer() : null) {
            if (renderer != null) renderer.initialize(0);
            String connect = System.getProperty("vc.browser.connect");
            if (connect != null && !connect.isBlank()) {
                String[] address = connect.split(":", 2);
                game.attachNetwork(NetworkClient.connect(address[0], address.length == 2 ? Integer.parseInt(address[1]) : 25565));
            }
            System.out.println("Browser displays existing " + (metal ? "Metal/shared-memory" : "Java2D") + " renderer; " + WIDTH + "x" + HEIGHT + " @ " + FPS + " fps maximum");
            long previous = System.nanoTime();
            long count = 0;
            while (running) {
                long start = System.nanoTime();
                if (start - lastRequest > 1_500_000_000L) {
                    input.clearAll();
                    previous = start;
                    LockSupport.parkNanos(20_000_000L);
                    continue;
                }
                if (start - lastInput > 1_500_000_000L) input.clearAll();
                drainInput(EVENTS, input);
                game.tick(input, Math.min(0.1, (start - previous) / 1e9));
                previous = start;
                long afterTick = System.nanoTime();
                if (renderer != null) renderer.render(WIDTH, HEIGHT, game);
                else {
                    Graphics2D graphics = image.createGraphics();
                    try { game.render(graphics, WIDTH, HEIGHT); } finally { graphics.dispose(); }
                }
                long afterRender = System.nanoTime();
                if (renderer != null) {
                    renderer.readOffscreenPixels(pixels);
                    for (int i = 0, p = 0; i < rgb.length; i++, p += 4) {
                        rgb[i] = (pixels.get(p + 2) & 255) << 16 | (pixels.get(p + 1) & 255) << 8 | pixels.get(p) & 255;
                    }
                }
                long afterReadback = System.nanoTime();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(128 * 1024);
                try (var output = new MemoryCacheImageOutputStream(bytes)) {
                    writer.setOutput(output);
                    writer.write(null, new IIOImage(image, null, null), jpegParameters);
                }
                long finish = System.nanoTime();
                String status = String.format(Locale.ROOT,
                    "{\"backend\":\"%s\",\"frame\":%d,\"width\":%d,\"height\":%d,\"uiOpen\":%s,\"yaw\":%.2f,\"hotbarSlot\":%d,\"tickMs\":%.2f,\"renderSubmitMs\":%.2f,\"meshSnapshotSubmitMs\":%.2f,\"readbackConvertMs\":%.2f,\"jpegMs\":%.2f,\"workMs\":%.2f}",
                    metal ? "original-java-metal" : "original-java-software", ++count, WIDTH, HEIGHT, game.isAnyUiOpen(), game.playerController().yaw(), game.selectedHotbarSlot(),
                    (afterTick - start) / 1e6, (afterRender - afterTick) / 1e6, renderer == null ? 0 : renderer.lastMeshingSubmitNanos() / 1e6,
                    (afterReadback - afterRender) / 1e6, (finish - afterReadback) / 1e6, (finish - start) / 1e6);
                latest = new Frame(bytes.toByteArray(), status, game.isAnyUiOpen());
                input.endFrame();
                if (count % FPS == 0) System.out.println(status);
                LockSupport.parkNanos(Math.max(0, 1_000_000_000L / FPS - (System.nanoTime() - start)));
            }
        } catch (Throwable error) {
            failure = "Java 客户端失败：" + error;
            throw error;
        } finally { writer.dispose(); }
    }

    private static int boundedProperty(String name, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, Integer.getInteger(name, fallback)));
    }
}
