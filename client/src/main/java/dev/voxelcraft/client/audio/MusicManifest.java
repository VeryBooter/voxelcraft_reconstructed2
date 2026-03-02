package dev.voxelcraft.client.audio;

// 中文标注：本文件已标记。

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

final class MusicManifest {
    private static final String RESOURCE = "/music/music_manifest.properties";

    private final EnumMap<MusicCue, List<MusicTrack>> cueTracks;
    private final Map<String, List<MusicTrack>> stingerTracks;

    private MusicManifest(EnumMap<MusicCue, List<MusicTrack>> cueTracks, Map<String, List<MusicTrack>> stingerTracks) {
        this.cueTracks = cueTracks;
        this.stingerTracks = stingerTracks;
    }

    static MusicManifest load() {
        EnumMap<MusicCue, List<MusicTrack>> cueTracks = new EnumMap<>(MusicCue.class);
        Map<String, List<MusicTrack>> stingerTracks = new HashMap<>();
        Properties properties = new Properties();
        try (InputStream inputStream = MusicManifest.class.getResourceAsStream(RESOURCE)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException ignored) {
        }
        for (MusicCue cue : MusicCue.values()) {
            cueTracks.put(cue, parseTracks(properties, "cue." + cue.key() + "."));
        }
        for (String name : parseCsv(properties.getProperty("stinger.names", ""))) {
            String key = normalizeKey(name);
            stingerTracks.put(key, parseTracks(properties, "stinger." + key + "."));
        }
        return new MusicManifest(cueTracks, stingerTracks);
    }

    List<MusicTrack> cueTracks(MusicCue cue) {
        return cueTracks.getOrDefault(cue, List.of());
    }

    List<MusicTrack> stingerTracks(String stingerName) {
        return stingerTracks.getOrDefault(normalizeKey(stingerName), List.of());
    }

    private static List<MusicTrack> parseTracks(Properties properties, String prefix) {
        List<String> paths = parseCsv(properties.getProperty(prefix + "paths", ""));
        int bpm = parseInt(properties.getProperty(prefix + "bpm"), 0);
        int beatsPerBar = parseMeter(properties.getProperty(prefix + "meter", "4/4"));
        List<MusicTrack> tracks = new ArrayList<>();
        for (String path : paths) {
            String normalized = normalizeResourcePath(path);
            if (!normalized.isEmpty()) {
                tracks.add(new MusicTrack(normalized, bpm, beatsPerBar));
            }
        }
        return List.copyOf(tracks);
    }

    private static List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> output = new ArrayList<>();
        for (String raw : value.split(",")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                output.add(trimmed);
            }
        }
        return output;
    }

    private static int parseMeter(String meter) {
        if (meter == null || meter.isBlank()) {
            return 4;
        }
        String[] split = meter.trim().split("/");
        if (split.length < 1) {
            return 4;
        }
        return Math.max(1, parseInt(split[0], 4));
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String normalizeResourcePath(String path) {
        String value = path.trim();
        if (value.isEmpty()) {
            return value;
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private static String normalizeKey(String key) {
        return key.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}

