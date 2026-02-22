package dev.voxelcraft.client.light;

import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.world.World;

public final class LightEngine {
    private float ambient = 1.0f;

    public void tick(ClientWorldView worldView) {
        long ticks = worldView.world().ticks();
        double dayPhase = (ticks % 24_000L) / 24_000.0;
        double wave = Math.cos(dayPhase * Math.PI * 2.0);
        ambient = (float) (0.6 + 0.4 * ((wave + 1.0) * 0.5));
    }

    public boolean isWithinWorldY(int y) {
        return y >= World.MIN_Y && y <= World.MAX_Y;
    }

    public float ambient() {
        return ambient;
    }
}
