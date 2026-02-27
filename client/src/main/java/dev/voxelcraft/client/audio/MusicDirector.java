package dev.voxelcraft.client.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

public final class MusicDirector implements AutoCloseable {
    private static final double DEFAULT_GAIN = 0.72;
    private static final double CAVE_Y_THRESHOLD = 24.0;
    private static final float DAY_AMBIENT_THRESHOLD = 0.78f;

    private final boolean enabled;
    private final MusicManifest manifest;
    private final Random random = new Random(0x56C3A9D2L);
    private final EnumSet<MusicCue> warnedMissingCue = EnumSet.noneOf(MusicCue.class);
    private final List<Clip> activeStingers = new ArrayList<>();

    private Clip activeClip;
    private MusicTrack activeTrack;
    private MusicCue activeCue;
    private MusicCue pendingCue;
    private double gain;
    private boolean audioAvailable = true;

    private MusicDirector(boolean enabled, MusicManifest manifest, double gain) {
        this.enabled = enabled;
        this.manifest = manifest;
        this.gain = Math.max(0.0, Math.min(1.0, gain));
    }

    public static MusicDirector createDefault() {
        boolean enabled = booleanProperty("vc.music.enabled", false);
        double gain = doubleProperty("vc.music.gain", DEFAULT_GAIN);
        return new MusicDirector(enabled, MusicManifest.load(), gain);
    }

    public void update(double deltaSeconds, boolean inWormhole, double playerY, float ambient) {
        if (!enabled || !audioAvailable) {
            return;
        }
        update(resolveCue(inWormhole, playerY, ambient), deltaSeconds);
    }

    public void update(MusicCue desiredCue, double deltaSeconds) {
        if (!enabled || !audioAvailable) {
            return;
        }
        if (desiredCue == null) {
            return;
        }
        if (activeCue != desiredCue) {
            pendingCue = desiredCue;
        }
        if (pendingCue != null && shouldSwitchAtThisTick(deltaSeconds)) {
            switchCueNow(pendingCue);
            pendingCue = null;
        }
    }

    public void triggerStinger(String stingerName) {
        if (!enabled || !audioAvailable) {
            return;
        }
        List<MusicTrack> tracks = manifest.stingerTracks(stingerName);
        if (tracks.isEmpty()) {
            return;
        }
        MusicTrack track = tracks.get(random.nextInt(tracks.size()));
        try {
            Clip clip = openClip(track.resourcePath());
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    synchronized (activeStingers) {
                        clip.close();
                        activeStingers.remove(clip);
                    }
                }
            });
            clip.start();
            synchronized (activeStingers) {
                activeStingers.add(clip);
            }
        } catch (Exception exception) {
            audioAvailable = false;
            System.err.println("[music] disabled after stinger error: " + exception.getMessage());
        }
    }

    public MusicCue activeCue() {
        return activeCue;
    }

    @Override
    public void close() {
        stopActiveClip();
        synchronized (activeStingers) {
            for (Clip clip : activeStingers) {
                clip.stop();
                clip.close();
            }
            activeStingers.clear();
        }
    }

    private boolean shouldSwitchAtThisTick(double deltaSeconds) {
        if (activeClip == null || activeTrack == null) {
            return true;
        }
        long barMicros = activeTrack.barMicroseconds();
        if (barMicros <= 0L) {
            return true;
        }
        long pos = activeClip.getMicrosecondPosition();
        long remain = barMicros - (pos % barMicros);
        long tickMicros = Math.max(1_000L, (long) Math.round(Math.max(0.0, deltaSeconds) * 1_000_000.0));
        return remain <= tickMicros;
    }

    private void switchCueNow(MusicCue cue) {
        stopActiveClip();
        List<MusicTrack> tracks = manifest.cueTracks(cue);
        if (tracks.isEmpty()) {
            activeCue = cue;
            activeTrack = null;
            if (!warnedMissingCue.contains(cue)) {
                warnedMissingCue.add(cue);
                System.out.println("[music] no tracks configured for cue=" + cue.key());
            }
            return;
        }
        MusicTrack selected = tracks.get(random.nextInt(tracks.size()));
        try {
            Clip clip = openClip(selected.resourcePath());
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            activeClip = clip;
            activeTrack = selected;
            activeCue = cue;
        } catch (Exception exception) {
            audioAvailable = false;
            System.err.println("[music] disabled after playback error: " + exception.getMessage());
        }
    }

    private Clip openClip(String resourcePath) throws Exception {
        try (InputStream stream = MusicDirector.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("resource not found: " + resourcePath);
            }
            try (AudioInputStream raw = AudioSystem.getAudioInputStream(new BufferedInputStream(stream))) {
                AudioInputStream decoded = decodePcmIfNeeded(raw);
                Clip clip = AudioSystem.getClip();
                clip.open(decoded);
                applyGain(clip, gain);
                return clip;
            }
        }
    }

    private static AudioInputStream decodePcmIfNeeded(AudioInputStream input) throws Exception {
        AudioFormat format = input.getFormat();
        if (format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) {
            return input;
        }
        AudioFormat decodedFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            format.getSampleRate(),
            16,
            format.getChannels(),
            format.getChannels() * 2,
            format.getSampleRate(),
            false
        );
        return AudioSystem.getAudioInputStream(decodedFormat, input);
    }

    private static void applyGain(Clip clip, double linearGain) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float dB;
        if (linearGain <= 0.0001) {
            dB = control.getMinimum();
        } else {
            dB = (float) (20.0 * Math.log10(linearGain));
        }
        dB = Math.max(control.getMinimum(), Math.min(control.getMaximum(), dB));
        control.setValue(dB);
    }

    private void stopActiveClip() {
        if (activeClip != null) {
            activeClip.stop();
            activeClip.close();
            activeClip = null;
        }
        activeTrack = null;
    }

    private static MusicCue resolveCue(boolean inWormhole, double playerY, float ambient) {
        if (inWormhole) {
            return MusicCue.WORMHOLE;
        }
        if (playerY < CAVE_Y_THRESHOLD) {
            return MusicCue.CAVE;
        }
        if (ambient >= DAY_AMBIENT_THRESHOLD) {
            return MusicCue.EXPLORE_DAY;
        }
        return MusicCue.EXPLORE_NIGHT;
    }

    private static boolean booleanProperty(String key, boolean defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on")) {
            return true;
        }
        if (normalized.equals("0") || normalized.equals("false") || normalized.equals("no") || normalized.equals("off")) {
            return false;
        }
        return defaultValue;
    }

    private static double doubleProperty(String key, double defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
