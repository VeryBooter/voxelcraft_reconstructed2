package dev.voxelcraft.client.render;

import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.World;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Executes real Metal commands, then checks pixels written by the GPU. */
@Tag("metal-native")
class MetalSharedBufferTest {
    private long context;
    private MetalSharedBuffers buffers;

    @BeforeEach
    void createDevice() {
        MetalNative.load();
        context = MetalNative.create(0, MetalNative.shaderSource(), false);
        buffers = new MetalSharedBuffers(context);
        System.out.println("Testing shared buffers on " + MetalNative.deviceName(context));
        ByteBuffer red = ByteBuffer.allocateDirect(4).put(new byte[] {(byte)255, 0, 0, (byte)255});
        red.flip();
        MetalNative.setTexture(context, 0, 1, 1, red);
    }

    @AfterEach
    void destroyDevice() {
        if (buffers != null) buffers.close();
        if (context != 0) MetalNative.destroy(context);
    }

    @Test
    void gpuReadsCpuEditsFromTheSameSharedAllocation() throws Exception {
        ByteBuffer vertices = triangle(2.0f, 0);
        ByteBuffer indices = indices();
        long handle = buffers.handle(vertices);
        ByteBuffer secondView = MetalNative.contents(handle).order(ByteOrder.nativeOrder());
        assertEquals(vertices.getFloat(0), secondView.getFloat(0));
        render(vertices, indices, 64, 64);
        ByteBuffer first = pixels(64, 64, "shared-triangle");
        assertRed(first, 64, 32, 32);

        // No upload API is called: mutate the MTLBuffer.contents address after the prior GPU read completes.
        MetalNative.waitIdle(context);
        for (int i = 0; i < 3; i++) vertices.putFloat(i * 24, vertices.getFloat(i * 24) + 10.0f);
        assertEquals(vertices.getFloat(0), secondView.getFloat(0));
        assertEquals(handle, buffers.handle(vertices));
        render(vertices, indices, 64, 64);
        ByteBuffer moved = pixels(64, 64, "shared-triangle-moved");
        assertTrue(channel(moved, 64, 32, 32, 0) > 150, "Moving shared vertices must reveal blue sky");
        assertTrue(channel(moved, 64, 32, 32, 2) < 120);
        buffers.release(vertices);
        buffers.release(indices);
    }

    @Test
    void nearGeometryWinsDepthTestEvenWhenFarGeometryIsDrawnLast() throws Exception {
        ByteBuffer atlas = ByteBuffer.allocateDirect(8).put(new byte[] {(byte)255, 0, 0, (byte)255, 0, (byte)255, 0, (byte)255});
        atlas.flip();
        MetalNative.setTexture(context, 0, 2, 1, atlas);
        ByteBuffer lut = ByteBuffer.allocateDirect(4096 * 6 * 4);
        for (int face = 0; face < 6; face++) lut.put((face * 4096 + 1) * 4, (byte)1);
        MetalNative.setTexture(context, 1, 4096, 6, lut);
        ByteBuffer near = triangle(2.0f, 1), far = triangle(3.0f, 0), indices = indices();
        float[] u = MetalChunkRenderer.uniforms(0, 0, 0, 0, 0, 1, 1, 2, 1);
        MetalNative.render(context, 64, 64, u,
            new long[] {buffers.handle(near), buffers.handle(far)},
            new long[] {buffers.handle(indices), buffers.handle(indices)}, new int[] {3, 3}, 0, 0, 0);
        ByteBuffer result = pixels(64, 64, "depth");
        assertTrue(channel(result, 64, 32, 32, 1) > 200, "Nearest triangle should be green");
        assertTrue(channel(result, 64, 32, 32, 2) < 10);
        buffers.release(near); buffers.release(far); buffers.release(indices);
    }

    @Test
    void sharedHudSurvivesMultipleFramesAndResizeWithCorrectOrientation() throws Exception {
        float[] u = MetalChunkRenderer.uniforms(0, 0, 0, 0, 0, 2, 1, 1, 1);
        for (int frame = 0; frame < 12; frame++) {
            ByteBuffer hud = buffers.acquire(16 * 16 * 4);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                hud.putInt((y * 16 + x) * 4, y < 8 ? 0xffff0000 : 0xff0000ff);
            MetalNative.render(context, frame < 6 ? 64 : 128, 64, u,
                new long[0], new long[0], new int[0], buffers.handle(hud), 16, 16);
            long handle = buffers.handle(hud);
            boolean pendingRead = !MetalNative.idle(handle);
            buffers.release(hud);
            ByteBuffer next = buffers.acquire(16 * 16 * 4);
            if (pendingRead && buffers.handle(next) == handle)
                assertTrue(MetalNative.idle(handle), "Pool must never hand out a buffer still read by the GPU");
            buffers.release(next);
        }
        ByteBuffer result = pixels(128, 64, "hud-resize");
        assertRed(result, 128, 32, 16);
        assertEquals(255, channel(result, 128, 32, 48, 0));
        assertEquals(0, channel(result, 128, 32, 48, 2));
    }

    @Test
    void existingMesherWritesIdenticalGeometryIntoMetalBuffersAndRendersCube() throws Exception {
        World world = new World(1L, chunk -> {
            if (chunk.pos().x() == 0 && chunk.pos().z() == 0) chunk.setBlock(0, 0, 2, Blocks.STONE);
        });
        try (ClientWorldView view = new ClientWorldView(world)) {
            ChunkMesher mesher = new ChunkMesher();
            DirectByteBufferPool ordinary = new DirectByteBufferPool(2);
            var chunk = view.getChunk(0, 0);
            var reference = mesher.buildChunkMesh(mesher.captureChunkSnapshot(view, chunk, 0, 2), ordinary);
            var shared = mesher.buildChunkMesh(mesher.captureChunkSnapshot(view, chunk, 0, 2), buffers);
            try {
                assertEquals(reference.vertexBytes(), shared.vertexBytes());
                assertEquals(reference.indexBytes(), shared.indexBytes());
                float[] u = MetalChunkRenderer.uniforms(0.5, 0.5, 0, 0, 0, 1, 1, 1, 1);
                MetalNative.render(context, 96, 96, u, new long[] {buffers.handle(shared.vertexBytes())},
                    new long[] {buffers.handle(shared.indexBytes())}, new int[] {shared.indexCount()}, 0, 0, 0);
                ByteBuffer result = pixels(96, 96, "mesher-cube");
                assertTrue(channel(result, 96, 48, 48, 2) > 100, "Cube face should be visible with correct winding");
                assertEquals(0, channel(result, 96, 48, 48, 0));
            } finally {
                reference.releaseBuffers(ordinary);
                shared.releaseBuffers(buffers);
            }
        }
    }

    @Test
    void releasedAndClosedBuffersCannotBeUsedAgain() {
        ByteBuffer bytes = buffers.acquire(32);
        buffers.release(bytes);
        assertThrows(IllegalStateException.class, () -> buffers.handle(bytes));
        assertThrows(IllegalStateException.class, () -> buffers.release(bytes));
        buffers.close();
        assertThrows(IllegalStateException.class, () -> buffers.acquire(32));
        buffers.close();
    }

    private ByteBuffer triangle(float depth, int blockId) {
        ByteBuffer data = buffers.acquire(3 * 24);
        for (float[] point : new float[][] {{-0.8f, -0.8f}, {0, 0.8f}, {0.8f, -0.8f}})
            data.putFloat(point[0]).putFloat(point[1]).putFloat(depth).putFloat(0).putFloat(0).putInt(0xffff0000 | blockId);
        data.flip();
        return data;
    }
    private ByteBuffer indices() {
        ByteBuffer data = buffers.acquire(12);
        data.putInt(0).putInt(1).putInt(2).flip();
        return data;
    }
    private void render(ByteBuffer vertices, ByteBuffer indices, int width, int height) {
        MetalNative.render(context, width, height, MetalChunkRenderer.uniforms(0, 0, 0, 0, 0,
            (float)width / height, 1, 1, 1), new long[] {buffers.handle(vertices)}, new long[] {buffers.handle(indices)},
            new int[] {3}, 0, 0, 0);
    }
    private ByteBuffer pixels(int width, int height, String name) throws Exception {
        ByteBuffer data = ByteBuffer.allocateDirect(width * height * 4);
        MetalNative.readPixels(context, data);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int base = (y * width + x) * 4;
            int argb = 0xff000000 | ((data.get(base + 2) & 255) << 16) | ((data.get(base + 1) & 255) << 8) | (data.get(base) & 255);
            image.setRGB(x, y, argb);
        }
        Path directory = Path.of(System.getProperty("vc.metal.test.output", "build/metal-test"));
        Files.createDirectories(directory);
        ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
        return data;
    }
    private static int channel(ByteBuffer data, int width, int x, int y, int channel) {
        return data.get((y * width + x) * 4 + channel) & 255;
    }
    private static void assertRed(ByteBuffer data, int width, int x, int y) {
        assertTrue(channel(data, width, x, y, 2) > 200, "Expected red triangle/HUD at sample");
        assertEquals(0, channel(data, width, x, y, 1));
        assertEquals(0, channel(data, width, x, y, 0));
    }
}
