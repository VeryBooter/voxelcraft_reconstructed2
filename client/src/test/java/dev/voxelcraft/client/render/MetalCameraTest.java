package dev.voxelcraft.client.render;

import dev.voxelcraft.client.VoxelcraftClientApp.RenderMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetalCameraTest {
    @Test
    void metalIsExplicitWhileExistingModesKeepTheirMeaning() {
        assertEquals(RenderMode.METAL, RenderMode.parse("metal"));
        assertEquals(RenderMode.GPU, RenderMode.parse("opengl"));
        assertEquals(RenderMode.GPU, RenderMode.parse("gpu"));
        assertEquals(RenderMode.AUTO, RenderMode.parse(null));
        assertEquals(RenderMode.SOFTWARE, RenderMode.parse("software"));
    }

    @Test
    void cameraMatchesExistingFrustumAndMapsDepthToMetalRange() {
        for (float yaw : new float[] {0, 90, 135, -70}) for (float pitch : new float[] {0, 45, -30}) {
            Frustum f = new Frustum();
            f.setCamera(12, 8, -9, yaw, pitch, 75, 1.5, 0.05, 4800);
            float[] u = MetalChunkRenderer.uniforms(12, 8, -9, yaw, pitch, 1.5f, 1, 1, 1);
            var expected = f.toCameraSpace(16, 11, 25);
            float dx = 16 - u[0], dy = 11 - u[1], dz = 25 - u[2];
            float x = dx * u[4] - dz * u[5];
            float z = dx * u[5] + dz * u[4];
            float y = dy * u[6] - z * u[7];
            z = dy * u[7] + z * u[6];
            assertEquals(expected.x, x, 0.00001);
            assertEquals(expected.y, y, 0.00001);
            assertEquals(expected.z, z, 0.00001);
            assertEquals(0, (0.05 * u[10] + u[11]) / 0.05, 0.000001);
            assertEquals(1, (4800 * u[10] + u[11]) / 4800, 0.000001);
        }
    }
}
