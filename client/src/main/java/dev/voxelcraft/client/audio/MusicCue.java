package dev.voxelcraft.client.audio;

import java.util.Locale;

public enum MusicCue {
    MENU,
    LOADING,
    EXPLORE_DAY,
    EXPLORE_NIGHT,
    TOWN,
    CAVE,
    COMBAT,
    BOSS,
    VICTORY,
    DEFEAT,
    WORMHOLE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}

