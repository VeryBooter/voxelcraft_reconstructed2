package dev.voxelcraft.client.audio;

// 中文标注：本文件已标记。

record MusicTrack(String resourcePath, int bpm, int beatsPerBar) {
    long barMicroseconds() {
        if (bpm <= 0 || beatsPerBar <= 0) {
            return -1L;
        }
        return Math.round((60_000_000.0 / bpm) * beatsPerBar);
    }
}

