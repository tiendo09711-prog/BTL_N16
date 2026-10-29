package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.fx.ClientBackgroundMusic;
import vn.ptit.btl16.client.fx.ClientSounds;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class ClientBackgroundMusicSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        byte[] track;
        try (var input = new BufferedInputStream(ClientBackgroundMusic.class.getResourceAsStream(ClientBackgroundMusic.RESOURCE));
             var audio = AudioSystem.getAudioInputStream(input)) {
            TestSupport.equals(44100f, audio.getFormat().getSampleRate(), "music preserves source sample rate");
            TestSupport.equals(2, audio.getFormat().getChannels(), "music preserves source stereo channels");
            TestSupport.equals(16, audio.getFormat().getSampleSizeInBits(), "music uses predecoded PCM");
            track = audio.readAllBytes();
            TestSupport.equals(73.5, track.length / (double) audio.getFormat().getFrameSize() / audio.getFormat().getSampleRate(), "exact 73.5-second supplied loop without MP3 encoder padding");
        }
        byte[] room;
        try (var audio = AudioSystem.getAudioInputStream(ClientBackgroundMusic.class.getResource(ClientBackgroundMusic.ROOM_RESOURCE))) {
            TestSupport.equals(44100f, audio.getFormat().getSampleRate(), "room music sample rate");
            TestSupport.equals(2, audio.getFormat().getChannels(), "room music remains stereo");
            TestSupport.equals(16, audio.getFormat().getSampleSizeInBits(), "room music is predecoded PCM");
            TestSupport.equals(2394524L, audio.getFrameLength(), "all uploaded room music frames are preserved without padding");
            room = audio.readAllBytes();
        }
        loops(track, false); loops(room, true); switching(track, room); settings(track); failures();
        System.out.println("[PASS] ClientBackgroundMusicSelfTest: lobby and uploaded room PCM, sample-exact loop boundaries, room/lobby switching, partial writes, one device, no inserted gaps, volume/mute, smoothing and cleanup");
    }

    private static ClientSounds sounds() { return new ClientSounds(null, (cue, volume) -> { }, System::nanoTime); }
    private static void loops(byte[] track, boolean room) throws Exception {
        ClientSounds sounds = sounds(); sounds.setVolume(1); sounds.setMusicVolume(1);
        Capture output = new Capture(track.length * 2 + 8192);
        try (var music = new ClientBackgroundMusic(sounds, () -> output)) {
            music.setInAuctionRoom(room);
            music.start(); music.start();
            TestSupport.check(output.complete.await(8, TimeUnit.SECONDS), "two complete loops reach the same stream");
            TestSupport.equals(1, output.opens.get(), "looping and repeated start never reopen or overlap devices");
            for (int index = 4096; index < output.bytes.length; index++) {
                if (output.bytes[index] != track[index % track.length]) { throw new AssertionError("PCM mismatch or loop gap at byte " + index); }
            }
        } finally { sounds.close(); }
        TestSupport.check(output.closed.get(), "closing releases device and blocked writer");
    }

    private static void switching(byte[] lobby, byte[] room) throws Exception {
        ClientSounds sounds = sounds(); sounds.setVolume(1); sounds.setMusicVolume(1);
        Steps output = new Steps();
        try (var music = new ClientBackgroundMusic(sounds, () -> output)) {
            music.start(); Step first = output.next(); scale(first.bytes, lobby, 0, 1);
            music.setInAuctionRoom(true); first.release.countDown();
            Step entered = output.next(); scale(entered.bytes, room, 0, 1);
            music.setInAuctionRoom(true); entered.release.countDown();
            Step continuing = output.next(); scale(continuing.bytes, room, 8192, 1);
            music.setInAuctionRoom(false); continuing.release.countDown();
            Step left = output.next(); scale(left.bytes, lobby, 0, 1);
            music.setInAuctionRoom(true); left.release.countDown();
            Step rejoined = output.next(); scale(rejoined.bytes, room, 0, 1);
            TestSupport.equals(1, output.opens.get(), "room transitions never create a second device or restart the stream");
        } finally { sounds.close(); }
    }

    private static void settings(byte[] track) throws Exception {
        ClientSounds sounds = sounds(); sounds.setVolume(.5); sounds.setMusicVolume(.4);
        Steps output = new Steps();
        try (var music = new ClientBackgroundMusic(sounds, () -> output)) {
            music.start(); Step first = output.next(); scale(first.bytes, track, 0, .2);
            sounds.play(ClientSounds.Cue.SUCCESS); sounds.setMusicEnabled(false); first.release.countDown();
            Step muted = output.next(); scale(muted.bytes, track, 8192, 0);
            sounds.setMusicEnabled(true); sounds.setMusicVolume(.8); muted.release.countDown();
            Step restored = output.next(); scale(restored.bytes, track, 16384, .4);
            sounds.setEnabled(false); restored.release.countDown();
            Step masterMute = output.next(); scale(masterMute.bytes, track, 24576, 0);
            sounds.setEnabled(true); sounds.setVolume(0); masterMute.release.countDown();
            Step zero = output.next(); scale(zero.bytes, track, 32768, 0);
            sounds.setVolume(1); zero.release.countDown();
            Step audible = output.next(); scale(audible.bytes, track, 40960, .8);
            TestSupport.equals(1, output.opens.get(), "effects and settings never restart the background track");
        } finally { sounds.close(); }
        TestSupport.check(output.closed.get(), "music stops on close");
    }

    private static void scale(byte[] actual, byte[] source, int position, double gain) {
        // Ignore the first 10ms volume ramp; the rest must match the current track position exactly.
        for (int index = 4096; index < actual.length; index += 2) {
            int offset = (position + index) % source.length;
            int original = (short) ((source[offset] & 255) | source[offset + 1] << 8);
            int scaled = (short) ((actual[index] & 255) | actual[index + 1] << 8);
            TestSupport.equals((int) Math.round(original * gain), scaled, "master and music gain apply without resetting playback");
        }
    }
    private static void failures() throws Exception {
        ClientSounds sounds = sounds(); AtomicInteger created = new AtomicInteger();
        var unopened = new ClientBackgroundMusic(sounds, () -> { created.incrementAndGet(); return new Steps(); });
        unopened.close(); unopened.close(); unopened.start(); TestSupport.equals(0, created.get(), "closed player cannot start");
        try (var music = new ClientBackgroundMusic(sounds, () -> new ClientBackgroundMusic.Output() {
            public void open(AudioFormat format) { throw new IllegalStateException("simulated missing sound card"); }
            public int write(byte[] bytes, int offset, int length) { throw new AssertionError("unavailable output must not receive audio"); }
            public void close() { }
        })) {
            music.start(); TestSupport.waitUntil(() -> !music.isAvailable(), 3000, "audio device failure is contained");
            TestSupport.check(sounds.play(ClientSounds.Cue.SUCCESS), "background failure preserves other sounds");
        } finally { sounds.close(); }
    }
    private static final class Capture implements ClientBackgroundMusic.Output {
        final byte[] bytes; int cursor;
        final AtomicInteger opens = new AtomicInteger(); final AtomicBoolean closed = new AtomicBoolean();
        final CountDownLatch complete = new CountDownLatch(1), stopped = new CountDownLatch(1);
        Capture(int size) { bytes = new byte[size]; }
        public void open(AudioFormat format) { opens.incrementAndGet(); }
        public int write(byte[] buffer, int offset, int length) {
            int count = Math.min(length, 1020), copy = Math.min(count, bytes.length - cursor);
            System.arraycopy(buffer, offset, bytes, cursor, copy); cursor += copy;
            if (cursor == bytes.length) {
                complete.countDown();
                try { stopped.await(10, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
            return count;
        }
        public void close() { closed.set(true); stopped.countDown(); }
    }
    private record Step(byte[] bytes, CountDownLatch release) { }
    private static final class Steps implements ClientBackgroundMusic.Output {
        final BlockingQueue<Step> writes = new LinkedBlockingQueue<>();
        final AtomicInteger opens = new AtomicInteger(); final AtomicBoolean closed = new AtomicBoolean();
        volatile Step active;
        public void open(AudioFormat format) { opens.incrementAndGet(); }
        public int write(byte[] buffer, int offset, int length) {
            Step step = new Step(Arrays.copyOfRange(buffer, offset, offset + length), new CountDownLatch(1)); active = step; writes.add(step);
            try { if (!closed.get()) { step.release.await(5, TimeUnit.SECONDS); } } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            return length;
        }
        Step next() throws Exception { Step step = writes.poll(3, TimeUnit.SECONDS); TestSupport.check(step != null, "next audio buffer is ready"); return step; }
        public void close() { closed.set(true); Step step = active; if (step != null) { step.release.countDown(); } }
    }
}
