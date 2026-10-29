package vn.ptit.btl16.client.fx;

import javafx.beans.InvalidationListener;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** A single PCM stream stays open across loop boundaries; there is no decoder restart or end timer. */
public final class ClientBackgroundMusic implements AutoCloseable {
    public static final String RESOURCE = "/audio/client/background-music.wav";
    public static final String ROOM_RESOURCE = "/audio/client/auction-room-music.wav";
    public interface Output extends AutoCloseable {
        void open(AudioFormat format) throws Exception;
        int write(byte[] bytes, int offset, int length);
        @Override void close();
    }

    private final ClientSounds sounds;
    private final Supplier<Output> outputFactory;
    private final boolean forcedSilent;
    private final AtomicBoolean started = new AtomicBoolean(), closed = new AtomicBoolean();
    private final InvalidationListener settings = ignored -> refreshGain();
    private volatile double gain;
    private volatile Output output;
    private volatile boolean available = true;
    private volatile boolean inAuctionRoom;

    public ClientBackgroundMusic(ClientSounds sounds) {
        this(sounds, NativeOutput::new, Boolean.getBoolean("btl16.client.sound.disabled"));
    }

    /** Alternate output supports deterministic loop verification without an audio device. */
    public ClientBackgroundMusic(ClientSounds sounds, Supplier<Output> outputFactory) {
        this(sounds, outputFactory, false);
    }

    private ClientBackgroundMusic(ClientSounds sounds, Supplier<Output> outputFactory, boolean forcedSilent) {
        this.sounds = sounds; this.outputFactory = outputFactory; this.forcedSilent = forcedSilent;
        sounds.enabledProperty().addListener(settings); sounds.volumeProperty().addListener(settings);
        sounds.musicEnabledProperty().addListener(settings); sounds.musicVolumeProperty().addListener(settings);
        refreshGain();
    }

    private void refreshGain() { gain = sounds.isSilent() || !sounds.isMusicEnabled() ? 0 : sounds.getVolume() * sounds.getMusicVolume(); }
    public boolean isAvailable() { return available; }
    public void setInAuctionRoom(boolean value) { inAuctionRoom = value; }
    public boolean isInAuctionRoom() { return inAuctionRoom; }

    public void start() {
        if (forcedSilent || closed.get() || !started.compareAndSet(false, true)) { return; }
        Thread worker = new Thread(this::play, "client-background-music");
        worker.setDaemon(true); worker.setPriority(Thread.NORM_PRIORITY + 1); worker.start();
    }

    private record Track(AudioFormat format, byte[] bytes) { }

    private static Track load(String resource) throws Exception {
        try (var input = new BufferedInputStream(Objects.requireNonNull(ClientBackgroundMusic.class.getResourceAsStream(resource)));
             var audio = AudioSystem.getAudioInputStream(input)) {
            AudioFormat format = audio.getFormat();
            if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()) || format.getSampleSizeInBits() != 16 || format.isBigEndian()) {
                throw new IOException("Background music must be little-endian 16-bit PCM");
            }
            byte[] track = audio.readAllBytes(); int frameSize = format.getFrameSize();
            if (track.length == 0 || track.length % frameSize != 0) { throw new IOException("Incomplete background music frames"); }
            return new Track(format, track);
        }
    }

    private void play() {
        try {
            Track lobby = load(RESOURCE), room = load(ROOM_RESOURCE);
            AudioFormat format = lobby.format(); int frameSize = format.getFrameSize();
            if (!format.matches(room.format())) { throw new IOException("Background tracks must share one PCM format"); }
            try (Output device = outputFactory.get()) {
                output = device;
                if (closed.get()) { return; }
                device.open(format);
                byte[] buffer = new byte[2048 * frameSize], track = lobby.bytes(); int cursor = 0;
                boolean playingRoom = false;
                double level = 0, ramp = 1.0 / (format.getSampleRate() * .01);
                while (!closed.get()) {
                    boolean requestedRoom = inAuctionRoom;
                    if (playingRoom != requestedRoom) {
                        playingRoom = requestedRoom; track = playingRoom ? room.bytes() : lobby.bytes();
                        cursor = 0; level = 0;
                    }
                    for (int frame = 0; frame < buffer.length; frame += frameSize) {
                        double target = gain;
                        level += Math.max(-ramp, Math.min(ramp, target - level));
                        for (int channel = 0; channel < frameSize; channel += 2) {
                            int sample = (short) ((track[cursor + channel] & 255) | track[cursor + channel + 1] << 8);
                            int scaled = (int) Math.round(sample * level);
                            buffer[frame + channel] = (byte) scaled; buffer[frame + channel + 1] = (byte) (scaled >> 8);
                        }
                        cursor += frameSize;
                        if (cursor == track.length) { cursor = 0; }
                    }
                    int written = 0;
                    while (written < buffer.length && !closed.get()) {
                        int count = device.write(buffer, written, buffer.length - written);
                        if (count <= 0 || count % frameSize != 0) { throw new IOException("Audio device stopped accepting frames"); }
                        written += count;
                    }
                }
            } finally { output = null; }
        } catch (Exception | LinkageError error) {
            if (!closed.get()) { available = false; }
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) { return; }
        sounds.enabledProperty().removeListener(settings); sounds.volumeProperty().removeListener(settings);
        sounds.musicEnabledProperty().removeListener(settings); sounds.musicVolumeProperty().removeListener(settings);
        Output device = output;
        if (device != null) { try { device.close(); } catch (RuntimeException ignored) { } }
    }

    private static final class NativeOutput implements Output {
        private SourceDataLine line;
        private boolean closed;
        @Override public synchronized void open(AudioFormat format) throws Exception {
            if (closed) { throw new IOException("Background music already closed"); }
            line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
            line.open(format, 4096 * format.getFrameSize()); line.start();
        }
        @Override public int write(byte[] bytes, int offset, int length) { return line.write(bytes, offset, length); }
        @Override public synchronized void close() {
            closed = true;
            if (line != null) { line.stop(); line.flush(); line.close(); }
        }
    }
}
