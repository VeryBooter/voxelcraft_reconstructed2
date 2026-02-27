package dev.voxelcraft.client.audio;

record MusicTrack(String resourcePath, int bpm, int beatsPerBar) {
    long barMicroseconds() {
        if (bpm <= 0 || beatsPerBar <= 0) {
            return -1L;
        }
        return Math.round((60_000_000.0 / bpm) * beatsPerBar);
    }
}

