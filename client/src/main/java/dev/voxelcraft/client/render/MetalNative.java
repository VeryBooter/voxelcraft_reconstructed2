package dev.voxelcraft.client.render;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Small JNI boundary; geometry ByteBuffers point at MTLBuffer.contents, not staging memory. */
final class MetalNative {
    private static boolean loaded;

    static synchronized void load() {
        if (loaded) return;
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) {
            throw new UnsupportedOperationException("Metal requires macOS with a unified-memory GPU");
        }
        String arch = System.getProperty("os.arch").contains("aarch64")
            || System.getProperty("os.arch").contains("arm64") ? "arm64" : "x86_64";
        String resource = "/native/macos-" + arch + "/libvoxelcraft_metal.dylib";
        try (InputStream input = MetalNative.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing " + resource + "; build on macOS using ./gradlew :client:classes");
            Path library = Files.createTempFile("voxelcraft-metal-", ".dylib");
            library.toFile().deleteOnExit();
            Files.copy(input, library, StandardCopyOption.REPLACE_EXISTING);
            System.load(library.toAbsolutePath().toString());
            loaded = true;
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load Metal renderer", failure);
        }
    }

    static String shaderSource() {
        try (InputStream input = MetalNative.class.getResourceAsStream("/shaders/voxelcraft.metal")) {
            if (input == null) throw new IOException("Missing Metal shader source");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static native long create(long cocoaWindow, String shaderSource, boolean vsync);
    static native String deviceName(long context);
    static native long allocate(long context, int bytes);
    static native ByteBuffer contents(long buffer);
    static native boolean idle(long buffer);
    static native void releaseBuffer(long buffer);
    static native void setTexture(long context, int slot, int width, int height, ByteBuffer rgba);
    static native void render(long context, int width, int height, float[] uniforms,
                              long[] vertices, long[] indices, int[] counts,
                              long hud, int hudWidth, int hudHeight);
    /** Offscreen verification only: waits for completion and returns BGRA pixels. */
    static native void readPixels(long context, ByteBuffer destination);
    static native void waitIdle(long context);
    static native void destroy(long context);

    private MetalNative() { }
}
