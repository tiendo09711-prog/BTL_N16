package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.fx.ClientSounds;
import vn.ptit.btl16.client.fx.ClientSounds.Cue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.AbstractPreferences;

public final class ClientSoundsSelfTest {
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        preferences();
        policy();
        failure();
        resources();
        System.out.println("[PASS] ClientSoundsSelfTest: local settings, mute/zero volume, cooldown/priority/dedup bounds, silent fallback, ten small PCM assets");
    }

    private static void preferences() {
        MemoryPreferences preferences = new MemoryPreferences();
        ClientSounds sounds = new ClientSounds(preferences, (cue, volume) -> { }, System::nanoTime);
        TestSupport.check(sounds.isEnabled(), "enabled by default");
        TestSupport.equals(.32, sounds.getVolume(), "quiet default volume");
        sounds.setVolume(.47); sounds.setEnabled(false); sounds.close();
        ClientSounds restored = new ClientSounds(preferences, (cue, volume) -> { }, System::nanoTime);
        TestSupport.check(!restored.isEnabled(), "mute persists across restart");
        TestSupport.equals(.47, restored.getVolume(), "volume persists across restart");
        restored.setVolume(7); TestSupport.equals(1.0, restored.getVolume(), "volume upper bound");
        restored.setVolume(-2); TestSupport.equals(0.0, restored.getVolume(), "volume lower bound");
        restored.setVolume(Double.NaN); TestSupport.equals(.32, restored.getVolume(), "NaN is safe");
        restored.setVolume(Double.POSITIVE_INFINITY); TestSupport.equals(.32, restored.getVolume(), "infinity is safe");
        restored.close();
    }

    private static void policy() {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        List<Cue> played = new ArrayList<>();
        List<Double> levels = new ArrayList<>();
        ClientSounds sounds = new ClientSounds(null, (cue, volume) -> { played.add(cue); levels.add(volume); }, clock::get);
        TestSupport.check(sounds.play(Cue.TAP), "first tap plays");
        TestSupport.check(!sounds.play(Cue.TAP), "rapid taps rate limited");
        TestSupport.check(sounds.playOnce(Cue.BID_ACCEPTED, "bid:1:110"), "confirmed bid interrupts neutral tap");
        TestSupport.check(!sounds.play(Cue.TAP), "neutral action cannot cover confirmed bid");
        clock.addAndGet(2_000_000_000L);
        TestSupport.check(!sounds.playOnce(Cue.BID_ACCEPTED, "bid:1:110"), "duplicate response remains silent after cooldown");
        TestSupport.check(sounds.playOnce(Cue.OUTBID, "outbid:1:120"), "outbid event plays");
        TestSupport.check(!sounds.play(Cue.CHAT_RECEIVED), "chat cannot cover outbid warning");
        TestSupport.check(!sounds.playOnce(Cue.OUTBID, "outbid:1:130"), "burst of bids is throttled");
        clock.addAndGet(2_000_000_000L);
        TestSupport.check(!sounds.playOnce(Cue.OUTBID, "outbid:1:130"), "throttled notification does not replay later");
        sounds.setEnabled(false);
        TestSupport.check(!sounds.play(Cue.ERROR), "mute suppresses even errors");
        sounds.playOnce(Cue.CHAT_RECEIVED, "chat:1:99");
        sounds.setEnabled(true);
        TestSupport.check(!sounds.playOnce(Cue.CHAT_RECEIVED, "chat:1:99"), "enabling does not replay muted history");
        sounds.setVolume(0); TestSupport.check(!sounds.play(Cue.WARNING), "zero volume is silent");
        sounds.setVolume(.4);
        for (int index = 0; index < 300; index++) {
            clock.addAndGet(100_000_000L);
            sounds.playOnce(Cue.TAP, "bounded:" + index);
        }
        clock.addAndGet(100_000_000L);
        TestSupport.check(!sounds.playOnce(Cue.TAP, "bounded:299"), "recent events remain deduplicated");
        TestSupport.check(sounds.playOnce(Cue.TAP, "bounded:0"), "old keys are evicted from bounded cache");
        TestSupport.check(levels.stream().allMatch(value -> value > 0 && value <= .4), "cue gains respect user volume");
        int before = played.size(); sounds.close(); clock.addAndGet(9_000_000_000L);
        TestSupport.check(!sounds.play(Cue.SUCCESS) && !sounds.playOnce(Cue.SUCCESS, "closed"), "no audio after close");
        TestSupport.equals(before, played.size(), "close prevents further playback");
    }

    private static void failure() {
        ClientSounds broken = new ClientSounds(null, (cue, volume) -> { throw new IllegalStateException("no device"); }, System::nanoTime);
        TestSupport.check(!broken.play(Cue.SUCCESS), "audio device failure does not escape");
        TestSupport.check(!broken.availableProperty().get(), "unavailable status exposed to UI");
        TestSupport.check(!broken.play(Cue.ERROR), "no repeated failing device initialization");
        broken.close();
        ClientSounds noNatives = new ClientSounds(null, (cue, volume) -> { throw new UnsatisfiedLinkError("missing native"); }, System::nanoTime);
        TestSupport.check(!noNatives.play(Cue.TAP), "missing media native fails silently"); noNatives.close();
    }

    private static void resources() throws Exception {
        int total = 0;
        for (Cue cue : Cue.values()) {
            byte[] bytes;
            try (var stream = ClientSounds.class.getResourceAsStream(cue.resource())) {
                TestSupport.check(stream != null, "sound resource bundled: " + cue);
                bytes = stream.readAllBytes();
            }
            total += bytes.length;
            ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            TestSupport.equals("RIFF", new String(bytes, 0, 4, StandardCharsets.US_ASCII), "RIFF header");
            TestSupport.equals("WAVE", new String(bytes, 8, 4, StandardCharsets.US_ASCII), "WAVE header");
            TestSupport.equals(bytes.length - 8, data.getInt(4), "RIFF length");
            TestSupport.equals((short) 1, data.getShort(20), "PCM format");
            TestSupport.equals((short) 1, data.getShort(22), "mono");
            TestSupport.equals(22050, data.getInt(24), "sample rate");
            TestSupport.equals((short) 16, data.getShort(34), "16-bit samples");
            TestSupport.equals(bytes.length - 44, data.getInt(40), "data length");
            TestSupport.check(bytes.length < 22050, "each effect under half a second");
            TestSupport.equals((short) 0, data.getShort(44), "smooth start without a pop");
        }
        TestSupport.check(total < 120_000, "all sounds remain small and local");
    }

    private static final class MemoryPreferences extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();
        MemoryPreferences() { super(null, ""); }
        @Override protected void putSpi(String key, String value) { values.put(key, value); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected void removeNodeSpi() { values.clear(); }
        @Override protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { throw new UnsupportedOperationException(); }
        @Override protected void syncSpi() { }
        @Override protected void flushSpi() { }
    }
}
