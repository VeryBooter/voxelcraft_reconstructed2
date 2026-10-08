package dev.voxelcraft.client.render;

import dev.voxelcraft.client.GameClient;
import dev.voxelcraft.client.platform.InputState;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("metal-native")
class MetalWorldTest {
    @Test
    void completeGameRendersSharedMeshesAndSurvivesWorldSwitchAndShutdown() throws Exception {
        try (GameClient game = new GameClient(); MetalChunkRenderer renderer = new MetalChunkRenderer()) {
            renderer.initialize(0);
            renderUntilReady(game, renderer);
            save(renderer.readOffscreenPixels(640, 360), "world");
            game.switchSlice(game.activeSliceW() + 1);
            renderUntilReady(game, renderer);
            save(renderer.readOffscreenPixels(640, 360), "world-after-switch");
            // Closing with mesh jobs still active exercises snapshot/buffer ownership across shutdown.
        }
    }

    private static void renderUntilReady(GameClient game, MetalChunkRenderer renderer) {
        InputState input = new InputState();
        long deadline = System.nanoTime() + 15_000_000_000L;
        int frames = 0;
        int faces = 0;
        while (System.nanoTime() < deadline && (frames < 40 || faces < 100)) {
            game.tick(input, 1.0 / 60.0);
            faces = renderer.render(640, 360, game).drawnFaces();
            input.endFrame();
            frames++;
        }
        assertTrue(faces >= 100, "Expected visible world geometry, got " + faces + " faces");
        System.out.println("[metal-world-test] frames=" + frames + " faces=" + faces + " " + renderer.latestTitleStats());
    }

    private static void save(ByteBuffer bgra, String name) throws Exception {
        BufferedImage image = new BufferedImage(640, 360, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 360; y++) for (int x = 0; x < 640; x++) {
            int offset = (y * 640 + x) * 4;
            image.setRGB(x, y, 0xff000000 | ((bgra.get(offset + 2) & 255) << 16)
                | ((bgra.get(offset + 1) & 255) << 8) | (bgra.get(offset) & 255));
        }
        Path directory = Path.of(System.getProperty("vc.metal.test.output", "build/metal-test"));
        Files.createDirectories(directory);
        ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
    }
}
