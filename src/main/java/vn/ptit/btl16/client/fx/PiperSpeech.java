package vn.ptit.btl16.client.fx;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/** Runs the bundled Vietnamese model locally; no bidder data leaves the client. */
public final class PiperSpeech implements ClientBidVoice.Speech {
    private static final String BID_QUESTION = "Có ai trả cao hơn không ạ?";
    private static final String AUDIO_ROOT = "/audio/client/";
    private final Path directory;
    private final Path executable;
    private final Path model;
    private Process process;
    private SourceDataLine playing;

    public PiperSpeech() {
        directory = directory();
        executable = directory.resolve("piper").resolve(System.getProperty("os.name").startsWith("Windows") ? "piper.exe" : "piper");
        model = directory.resolve("vi_VN-vais1000-medium.onnx");
    }

    private static Path directory() {
        String configured = System.getProperty("btl16.speech.dir", "");
        if (!configured.isBlank()) { return Path.of(configured).toAbsolutePath().normalize(); }
        try {
            Path source = Path.of(PiperSpeech.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path application = Files.isDirectory(source) ? source.getParent().getParent() : source.getParent();
            return application.resolve("tools/speech");
        } catch (Exception error) { throw new IllegalStateException("Cannot locate local speech files", error); }
    }

    @Override
    public boolean available() { return Files.isRegularFile(executable) && Files.isRegularFile(model) && Files.isRegularFile(Path.of(model + ".json")); }

    @Override
    public void speak(String text, DoubleSupplier volume, BooleanSupplier cancelled) throws Exception {
        if (cancelled.getAsBoolean()) { return; }
        if (ClientBidVoice.CLOSING_REMINDER.equals(text)) {
            playResource("auction-closing-reminder.wav", volume, cancelled);
            return;
        }
        // Keep the name and amount together, then give the invitation a slower, warmer cadence.
        String invitation = " " + BID_QUESTION;
        if (text.endsWith(invitation)) {
            synthesize(text.substring(0, text.length() - invitation.length()), volume, cancelled);
            if (!cancelled.getAsBoolean()) { playResource("auction-bid-invitation.wav", volume, cancelled); }
        } else {
            synthesize(text, volume, cancelled);
        }
    }

    private void synthesize(String text, DoubleSupplier volume, BooleanSupplier cancelled) throws Exception {
        Path work = Files.createTempDirectory("auction-voice-");
        Path wav = work.resolve("announcement.wav"), log = work.resolve("synthesis.log");
        Process running = null;
        try {
            synchronized (this) {
                if (cancelled.getAsBoolean()) { return; }
                // Relative paths let eSpeak's Windows file API work inside Vietnamese directory names.
                running = new ProcessBuilder(executable.toString(), "--model", relative(model), "--config", relative(Path.of(model + ".json")),
                        "--output_file", relative(wav), "--espeak_data", "espeak-ng-data", "--length_scale", "0.94",
                        "--noise_scale", "0.75", "--noise_w", "0.85", "--sentence_silence", "0.18")
                        .directory(executable.getParent().toFile()).redirectOutput(log.toFile()).redirectErrorStream(true).start();
                process = running;
            }
            try (var input = running.outputWriter(StandardCharsets.UTF_8)) { input.write(text); input.newLine(); }
            if (!running.waitFor(30, TimeUnit.SECONDS)) { throw new IOException("Local speech synthesis timed out"); }
            if (cancelled.getAsBoolean()) { return; }
            if (running.exitValue() != 0 || !Files.isRegularFile(wav)) { throw new IOException("Local speech synthesis failed"); }
            play(wav, volume, cancelled);
        } finally {
            synchronized (this) { if (process == running) { process = null; } }
            if (running != null && running.isAlive()) { running.destroyForcibly().waitFor(2, TimeUnit.SECONDS); }
            Files.deleteIfExists(wav); Files.deleteIfExists(log); Files.deleteIfExists(work);
        }
    }

    private String relative(Path path) {
        return executable.getParent().getRoot().equals(path.getRoot()) ? executable.getParent().relativize(path).toString() : path.toString();
    }

    private void play(Path wav, DoubleSupplier volume, BooleanSupplier cancelled) throws Exception {
        try (var source = AudioSystem.getAudioInputStream(wav.toFile())) {
            play(source, volume, cancelled);
        }
    }

    private void playResource(String name, DoubleSupplier volume, BooleanSupplier cancelled) throws Exception {
        var resource = PiperSpeech.class.getResource(AUDIO_ROOT + name);
        if (resource == null) { throw new IOException("Missing local speech clip: " + name); }
        try (var source = AudioSystem.getAudioInputStream(resource)) {
            play(source, volume, cancelled);
        }
    }

    private void play(javax.sound.sampled.AudioInputStream source, DoubleSupplier volume, BooleanSupplier cancelled) throws Exception {
        AudioFormat original = source.getFormat();
        AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, original.getSampleRate(), 16,
                original.getChannels(), original.getChannels() * 2, original.getSampleRate(), false);
        try (var audio = AudioSystem.getAudioInputStream(pcm, source)) {
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, pcm));
            try {
                synchronized (this) {
                    if (cancelled.getAsBoolean()) { return; }
                    line.open(pcm); playing = line; line.start();
                }
                byte[] buffer = new byte[4096];
                int count;
                while (!cancelled.getAsBoolean() && (count = audio.read(buffer)) != -1) {
                    double gain = Math.max(0, Math.min(1, volume.getAsDouble()));
                    for (int offset = 0; offset + 1 < count; offset += 2) {
                        short sample = (short) ((buffer[offset] & 255) | buffer[offset + 1] << 8);
                        int scaled = (int) Math.round(sample * gain);
                        buffer[offset] = (byte) scaled; buffer[offset + 1] = (byte) (scaled >> 8);
                    }
                    int written = 0;
                    while (written < count && !cancelled.getAsBoolean()) {
                        int step = line.write(buffer, written, count - written);
                        if (step <= 0) { break; }
                        written += step;
                    }
                }
                if (!cancelled.getAsBoolean()) { line.drain(); }
            } finally {
                synchronized (this) { if (playing == line) { playing = null; } }
                line.close();
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (process != null) { process.destroyForcibly(); }
        if (playing != null) { playing.stop(); playing.flush(); playing.close(); playing = null; }
    }
}
