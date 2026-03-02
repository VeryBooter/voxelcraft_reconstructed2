package dev.voxelcraft.client.audio;

// 中文标注：本文件已标记。

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

