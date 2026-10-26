package vn.ptit.btl16.client.fx;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.media.AudioClip;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.prefs.Preferences;

public final class ClientSounds implements AutoCloseable {
    public enum Cue {
        TAP(55, 65, 0, .32), NAVIGATE(110, 120, 0, .55),
        SUCCESS(240, 350, 1, .75), BID_ACCEPTED(200, 250, 3, .85),
        OUTBID(320, 1800, 4, .9), CHAT_SENT(100, 180, 1, .55),
        CHAT_RECEIVED(160, 900, 1, .65), WARNING(260, 2000, 3, .8),
        ERROR(230, 600, 3, .75), AUCTION_END(420, 1000, 5, .85);

        private final int durationMillis, cooldownMillis, priority;
        private final double gain;

        Cue(int durationMillis, int cooldownMillis, int priority, double gain) {
            this.durationMillis = durationMillis;
            this.cooldownMillis = cooldownMillis;
            this.priority = priority;
            this.gain = gain;
        }

        public String resource() { return "/audio/client/" + name().toLowerCase(Locale.ROOT).replace('_', '-') + ".wav"; }
    }

    @FunctionalInterface
    public interface Playback {
        void play(Cue cue, double volume);
        default void stop() { }
    }

    private final Preferences preferences;
    private final Playback playback;
    private final LongSupplier clock;
    private final boolean forcedSilent;
    private final SimpleBooleanProperty enabled = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty available = new SimpleBooleanProperty(true);
    private final SimpleDoubleProperty volume = new SimpleDoubleProperty(.32);
    private final EnumMap<Cue, Long> lastPlayed = new EnumMap<>(Cue.class);
    private final LinkedHashMap<String, Boolean> observed = new LinkedHashMap<>();
    private long activeUntil;
    private int activePriority;
    private boolean closed;

    public ClientSounds() {
        this(userPreferences(), new ClipPlayback(), System::nanoTime, Boolean.getBoolean("btl16.client.sound.disabled"));
    }

    public ClientSounds(Preferences preferences, Playback playback, LongSupplier clock) {
        this(preferences, playback, clock, false);
    }

    private ClientSounds(Preferences preferences, Playback playback, LongSupplier clock, boolean forcedSilent) {
        this.preferences = preferences;
        this.playback = playback;
        this.clock = clock;
        this.forcedSilent = forcedSilent;
        if (preferences != null) {
            try {
                enabled.set(preferences.getBoolean("enabled", true));
                volume.set(normalize(preferences.getDouble("volume", .32)));
            } catch (RuntimeException ignored) { }
        }
    }

    private static Preferences userPreferences() {
        try { return Preferences.userRoot().node("vn/ptit/btl16/client/sounds"); }
        catch (RuntimeException exception) { return null; }
    }

    private static double normalize(double value) { return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : .32; }
    public boolean isEnabled() { return enabled.get(); }
    public double getVolume() { return volume.get(); }
    public ReadOnlyBooleanProperty enabledProperty() { return enabled; }
    public ReadOnlyBooleanProperty availableProperty() { return available; }
    public ReadOnlyDoubleProperty volumeProperty() { return volume; }

    public void setEnabled(boolean value) {
        enabled.set(value);
        if (!value) { stop(); }
        if (preferences != null) {
            try { preferences.putBoolean("enabled", value); }
            catch (RuntimeException ignored) { }
        }
    }

    public void setVolume(double value) {
        volume.set(normalize(value));
        if (volume.get() == 0) { stop(); }
        if (preferences != null) {
            try { preferences.putDouble("volume", volume.get()); }
            catch (RuntimeException ignored) { }
        }
    }

    public boolean play(Cue cue) {
        if (closed || forcedSilent || !enabled.get() || !available.get() || volume.get() == 0) { return false; }
        long now = clock.getAsLong();
        Long previous = lastPlayed.get(cue);
        if (previous != null && now - previous < cue.cooldownMillis * 1_000_000L) { return false; }
        if (now < activeUntil && cue.priority < activePriority) { return false; }
        try {
            playback.stop();
            playback.play(cue, volume.get() * cue.gain);
            lastPlayed.put(cue, now);
            activePriority = cue.priority;
            activeUntil = now + cue.durationMillis * 1_000_000L;
            return true;
        } catch (RuntimeException | LinkageError exception) {
            available.set(false);
            stop();
            return false;
        }
    }

    public boolean playOnce(Cue cue, String eventKey) {
        if (closed) { return false; }
        String key = cue.name() + ":" + eventKey;
        if (observed.putIfAbsent(key, Boolean.TRUE) != null) { return false; }
        while (observed.size() > 256) { observed.remove(observed.keySet().iterator().next()); }
        return play(cue);
    }

    private void stop() {
        try { playback.stop(); }
        catch (RuntimeException | LinkageError ignored) { }
        activeUntil = 0;
    }

    @Override
    public void close() {
        closed = true;
        stop();
        observed.clear();
        lastPlayed.clear();
    }

    private static final class ClipPlayback implements Playback {
        private final EnumMap<Cue, AudioClip> clips = new EnumMap<>(Cue.class);

        @Override
        public void play(Cue cue, double volume) {
            AudioClip clip = clips.computeIfAbsent(cue, sound -> {
                var resource = ClientSounds.class.getResource(sound.resource());
                if (resource == null) { throw new IllegalStateException("Missing bundled sound: " + sound.resource()); }
                return new AudioClip(resource.toExternalForm());
            });
            clip.setPriority(cue.priority);
            clip.play(volume);
        }

        @Override
        public void stop() { clips.values().forEach(AudioClip::stop); }
    }
}
