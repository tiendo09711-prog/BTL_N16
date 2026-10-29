package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.fx.ClientBidVoice;
import vn.ptit.btl16.client.fx.ClientSounds;
import vn.ptit.btl16.client.model.ClientBid;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.common.util.VietnameseMoneyWords;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

public final class ClientBidVoiceSelfTest {
    private ClientBidVoiceSelfTest() { }

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        randomBidAnnouncements();
        TestSupport.equals("một trăm mười nghìn đồng", read("110000"), "six digit bid");
        TestSupport.equals("một triệu năm trăm nghìn đồng", read("1500000"), "million bid");
        TestSupport.equals("một triệu không trăm linh một đồng", read("1000001"), "zero groups");
        TestSupport.equals("hai mươi mốt đồng", read("21"), "Vietnamese ones after twenty");
        TestSupport.equals("mười lăm đồng", read("15"), "Vietnamese five after ten");
        TestSupport.equals("không phẩy không năm đồng", read("0.05"), "fractional dong is not rounded");
        TestSupport.equals("một tỷ hai trăm ba mươi bốn triệu năm trăm sáu mươi bảy nghìn tám trăm chín mươi phẩy không năm đồng",
                read("1234567890.05"), "billions and decimal digits");
        try { read("1.001"); throw new AssertionError("Speech must not round bids"); }
        catch (ArithmeticException expected) { }

        Playback speech = new Playback();
        try (ClientSounds sounds = new ClientSounds(null, (cue, volume) -> { }, System::nanoTime);
             ClientBidVoice voice = new ClientBidVoice(sounds, speech, Runnable::run)) {
            voice.setRoom(7, List.of(bid(1, 7, "old", "100")));
            TestSupport.check(!voice.announce(bid(1, 7, "old", "100"), new BigDecimal("100")), "loaded history is silent");
            TestSupport.check(!voice.announce(bid(2, 8, "elsewhere", "200"), new BigDecimal("200")), "other room is silent");
            TestSupport.check(!voice.announce(bid(3, 7, "stale", "100"), new BigDecimal("200")), "late lower bid is silent");
            TestSupport.check(voice.announce(bid(4, 7, "Khách #2", "1500000"), new BigDecimal("1500000")), "live bid queued");
            speech.started.await(3, TimeUnit.SECONDS);
            var namedBid = ClientWireParser.bid(Map.of("bid.bidId", "5", "bid.auctionId", "7", "bid.userId", "2",
                    "bid.username", "tien1", "bid.displayName", "phước", "bid.amount", "1600000"), "bid.");
            TestSupport.equals("tien1", namedBid.getUsername(), "account login remains distinct from profile display name");
            TestSupport.check(voice.announce(namedBid, new BigDecimal("1600000")), "next accepted bid queued");
            TestSupport.check(!voice.announce(bid(4, 7, "Khách #2", "1500000"), new BigDecimal("1500000")), "replayed event is silent");
            TestSupport.equals(1, speech.texts.size(), "announcements never overlap");
            checkAnnouncement(speech.texts.getFirst(), "Khách số 2", "anonymous room uses public alias in an uploaded MC sentence");
            speech.release.countDown();
            TestSupport.waitUntil(() -> speech.texts.size() == 2, 3000, "sequential second bid");
            checkAnnouncement(speech.texts.get(1), "phước", "voice uses bidder profile display name instead of login name");
            sounds.setEnabled(false);
            TestSupport.check(!voice.announce(bid(6, 7, "muted", "1700000"), new BigDecimal("1700000")), "master mute applies to speech");
            sounds.setEnabled(true); sounds.setVolume(0);
            TestSupport.check(!voice.announce(bid(7, 7, "zero", "1800000"), new BigDecimal("1800000")), "zero volume applies to speech");
            sounds.setVolume(.7); voice.setEnabled(false);
            TestSupport.check(!voice.announce(bid(8, 7, "disabled", "1900000"), new BigDecimal("1900000")), "narration can be disabled separately");
            voice.setEnabled(true); voice.setRoom(9, List.of());
            TestSupport.check(!voice.announce(bid(9, 7, "left", "2000000"), new BigDecimal("2000000")), "leaving invalidates old room");
            TestSupport.check(voice.announce(bid(10, 9, "new", "2100000"), new BigDecimal("2100000")), "new room can speak");
            TestSupport.waitUntil(() -> speech.texts.size() == 3, 3000, "new room voice");
            TestSupport.equals(.7, speech.lastVolume, "speech shares volume control");
            TestSupport.check(speech.stops.get() >= 4, "mute and room switches stop current playback");
        }
        closingReminder();
        reminderQueue();
        personalWin();
        bundledInvitations();
        System.out.println("[PASS] ClientBidVoiceSelfTest: random uploaded MC sentences, profile display names, username fallback, anonymous aliases, Vietnamese amounts, live bids, room-only 30s reminder, personal win voice, mute, cancellation, priority/expiry and bundled WAVs");
    }

    private static List<String> bidTemplates() throws Exception {
        try (var resource = ClientBidVoice.class.getResourceAsStream("/audio/client/cau_noi_MC_khi_khach_vua_tra_gia.txt")) {
            TestSupport.check(resource != null, "uploaded MC sentences are bundled with the client");
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8).lines().filter(line -> !line.isBlank()).toList();
        }
    }

    static void checkAnnouncement(String text, String name, String message) throws Exception {
        TestSupport.check(bidTemplates().stream().anyMatch(template -> template.replace("{ten_khach}", name).equals(text)), message);
    }

    private static void randomBidAnnouncements() throws Exception {
        List<String> templates = bidTemplates();
        TestSupport.equals(20, templates.size(), "all twenty uploaded sentences are available");
        TestSupport.equals(20, new HashSet<>(templates).size(), "uploaded sentences remain distinct");
        TestSupport.check(templates.stream().allMatch(template -> template.contains("{ten_khach}")), "every sentence names the bidder");
        HashSet<String> chosen = new HashSet<>();
        for (int attempt = 0; attempt < 200; attempt++) {
            String text = ClientBidVoice.announcement("  phuoc_auction\n  ", new BigDecimal("1500000"));
            checkAnnouncement(text, "phuoc auction", "selected sentence matches the uploaded text with the account name");
            chosen.add(text);
        }
        TestSupport.check(chosen.size() > 1, "successive bids choose varying sentences");
        for (String name : java.util.Arrays.asList(null, "  ", "\n\t")) {
            checkAnnouncement(ClientBidVoice.announcement(name, BigDecimal.ONE), "Người tham gia", "missing account has a readable fallback");
        }
        checkAnnouncement(ClientBidVoice.announcement("Khách #2", BigDecimal.ONE), "Khách số 2", "anonymous alias is pronounced without exposing identity");
        checkAnnouncement(ClientBidVoice.announcement("khach$1\\ten", BigDecimal.ONE), "khach$1\\ten", "account name is substituted literally");
        for (String displayName : List.of("", "   ")) {
            var legacy = ClientWireParser.bid(Map.of("bid.username", "tien1", "bid.displayName", displayName), "bid.");
            TestSupport.equals("tien1", legacy.getDisplayName(), "missing display name falls back to account login");
        }
        TestSupport.equals("tien1", ClientWireParser.bid(Map.of("username", "tien1"), "").getDisplayName(), "older server payload remains readable");
    }

    private static void closingReminder() throws Exception {
        Playback speech = new Playback(); speech.release.countDown();
        Instant now = Instant.now();
        try (ClientSounds sounds = new ClientSounds(null, (cue, volume) -> { }, System::nanoTime);
             ClientBidVoice voice = new ClientBidVoice(sounds, speech, Runnable::run)) {
            TestSupport.check(!voice.remindClosing(auction(7, now, 30_000, "OPEN"), now), "lobby cannot hear reminders");
            voice.setRoom(7, List.of());
            TestSupport.check(!voice.remindClosing(auction(8, now, 30_000, "OPEN"), now), "other auction cannot be heard");
            TestSupport.check(!voice.remindClosing(auction(7, now, 30_001, "OPEN"), now), "not before thirty seconds");
            TestSupport.check(!voice.remindClosing(auction(7, now, 30_000, "ENDED"), now), "closed auction is silent");
            TestSupport.check(!voice.remindClosing(auction(7, now, 30_000, "SCHEDULED"), now), "scheduled auction is silent");
            TestSupport.check(!voice.remindClosing(auction(7, now, 0, "OPEN"), now), "expired auction is silent");
            TestSupport.check(!voice.remindClosing(auction(7, now.plusSeconds(60), 30_000, "OPEN"), now), "future start is silent");
            TestSupport.check(voice.remindClosing(auction(7, now, 30_000, "OPEN"), now), "thirty seconds queues a reminder without any bids");
            TestSupport.waitUntil(() -> speech.texts.size() == 1, 3000, "closing reminder playback");
            TestSupport.equals("Có ai muốn đưa ra mức giá cao hơn không ạ?", speech.texts.getFirst(), "exact requested closing phrase");
            TestSupport.check(!voice.remindClosing(auction(7, now, 29_500, "OPEN"), now), "ticks never repeat the reminder");
            TestSupport.check(!voice.remindClosing(auction(7, now, 60_000, "OPEN"), now), "extension is silent");
            TestSupport.check(!voice.remindClosing(auction(7, now, 25_000, "OPEN"), now), "extension cannot replay reminder");
            voice.clear(); voice.setRoom(7, List.of());
            TestSupport.check(!voice.remindClosing(auction(7, now, 20_000, "OPEN"), now), "rejoining does not replay reminder");
            voice.setRoom(8, List.of());
            TestSupport.check(voice.remindClosing(auction(8, now, 29_500, "OPEN"), now), "a new auction has its own reminder");
            TestSupport.waitUntil(() -> speech.texts.size() == 2, 3000, "new auction reminder");
            voice.setRoom(9, List.of()); sounds.setEnabled(false);
            TestSupport.check(!voice.remindClosing(auction(9, now, 30_000, "OPEN"), now), "master mute suppresses reminder");
            sounds.setEnabled(true);
            TestSupport.check(!voice.remindClosing(auction(9, now, 29_000, "OPEN"), now), "unmuting does not replay missed reminders");
            voice.setRoom(10, List.of()); sounds.setVolume(0);
            TestSupport.check(!voice.remindClosing(auction(10, now, 30_000, "OPEN"), now), "zero volume suppresses reminder");
            sounds.setVolume(.6); voice.setRoom(11, List.of()); voice.setEnabled(false);
            TestSupport.check(!voice.remindClosing(auction(11, now, 30_000, "OPEN"), now), "narration off suppresses reminder");
            voice.setEnabled(true); voice.resetSession(); voice.setRoom(7, List.of());
            TestSupport.check(voice.remindClosing(auction(7, now, 30_000, "OPEN"), now), "new login session resets reminder memory");
            TestSupport.waitUntil(() -> speech.texts.size() == 3, 3000, "new session reminder");
        }
    }

    private static void reminderQueue() throws Exception {
        for (String scenario : List.of("priority", "leave", "expiry")) {
            Playback speech = new Playback();
            try (ClientSounds sounds = new ClientSounds(null, (cue, volume) -> { }, System::nanoTime);
                 ClientBidVoice voice = new ClientBidVoice(sounds, speech, Runnable::run)) {
                voice.setRoom(7, List.of());
                voice.announce(bid(1, 7, "first", "100"), new BigDecimal("100"));
                TestSupport.check(speech.started.await(3, TimeUnit.SECONDS), "first speaker started");
                voice.announce(bid(2, 7, "second", "200"), new BigDecimal("200"));
                Instant now = Instant.now();
                boolean expiry = scenario.equals("expiry");
                voice.remindClosing(auction(7, now, expiry ? 40 : 30_000, "OPEN"), now);
                TestSupport.equals(1, speech.texts.size(), "reminder cannot overlap current speaker");
                if (scenario.equals("leave")) {
                    voice.clear(); voice.setRoom(8, List.of());
                    voice.announce(bid(3, 8, "new room", "300"), new BigDecimal("300"));
                } else if (expiry) {
                    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(80);
                    TestSupport.waitUntil(() -> System.nanoTime() > deadline, 1000, "queued reminder expires");
                }
                speech.release.countDown();
                int expected = scenario.equals("priority") ? 3 : 2;
                TestSupport.waitUntil(() -> speech.texts.size() == expected, 3000, "queued narration completed");
                if (scenario.equals("priority")) {
                    TestSupport.equals(ClientBidVoice.CLOSING_REMINDER, speech.texts.get(1), "reminder goes before pending bid");
                    checkAnnouncement(speech.texts.get(2), "second", "pending bid preserved");
                } else {
                    TestSupport.check(!speech.texts.contains(ClientBidVoice.CLOSING_REMINDER), "left or expired reminder never plays");
                    checkAnnouncement(speech.texts.get(1), scenario.equals("leave") ? "new room" : "second", "remaining valid bid plays");
                }
            }
        }
    }

    private static void personalWin() throws Exception {
        Playback speech = new Playback(); speech.release.countDown();
        try (ClientSounds sounds = new ClientSounds(null, (cue, volume) -> { }, System::nanoTime);
             ClientBidVoice voice = new ClientBidVoice(sounds, speech, Runnable::run)) {
            TestSupport.check(!voice.congratulate(null) && !voice.congratulate("  "), "missing product never creates a broken announcement");
            TestSupport.check(voice.congratulate("  Bình gốm\n cổ  "), "personal winner in lobby can hear congratulations");
            TestSupport.waitUntil(() -> speech.texts.size() == 1, 3000, "personal win in lobby");
            TestSupport.equals("Chúc mừng bạn đã đấu giá thành công Bình gốm cổ!", speech.texts.getFirst(), "requested phrase with cleaned product name");
            voice.setRoom(7, List.of()); voice.clearRoom(7);
            TestSupport.check(voice.congratulate("Đồng hồ cổ"), "closed-room winner can hear congratulations");
            TestSupport.waitUntil(() -> speech.texts.size() == 2, 3000, "closed-room congratulations");
            int stops = speech.stops.get(); voice.clearRoom(7);
            TestSupport.equals(stops, speech.stops.get(), "duplicate end cannot cancel congratulations after room voice was cleared");
            sounds.setEnabled(false);
            TestSupport.check(!voice.congratulate("Muted"), "master mute applies to congratulations");
            sounds.setEnabled(true); sounds.setVolume(0);
            TestSupport.check(!voice.congratulate("Zero"), "zero volume applies to congratulations");
            sounds.setVolume(.5); voice.setEnabled(false);
            TestSupport.check(!voice.congratulate("Disabled"), "voice toggle applies to congratulations");
        }
    }

    private static void bundledInvitations() throws Exception {
        for (String name : List.of("auction-closing-reminder.wav", "auction-bid-invitation.wav")) {
            var resource = ClientBidVoice.class.getResource("/audio/client/" + name);
            TestSupport.check(resource != null, "invitation is bundled: " + name);
            try (var audio = javax.sound.sampled.AudioSystem.getAudioInputStream(resource)) {
                double seconds = audio.getFrameLength() / audio.getFormat().getFrameRate();
                TestSupport.check(seconds > 1 && seconds < 10, "short decodable invitation");
                byte[] pcm = audio.readAllBytes();
                int peak = 0, saturated = 0;
                for (int i = 0; i + 1 < pcm.length; i += 2) {
                    int magnitude = Math.abs((short) ((pcm[i] & 255) | pcm[i + 1] << 8));
                    peak = Math.max(peak, magnitude);
                    if (magnitude >= 32767) { saturated++; }
                }
                TestSupport.check(peak > 1000 && saturated < pcm.length / 2 * .001, "audible invitation without sustained clipping");
            }
        }
    }

    private static ClientAuction auction(long room, Instant now, long remainingMillis, String status) {
        return new ClientAuction(room, 1, "TEST", "Voice test", "", 1, "seller", BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, null, "", now.minusSeconds(10), now.plusMillis(remainingMillis),
                status, null, 1, 1);
    }

    private static String read(String value) { return VietnameseMoneyWords.read(new BigDecimal(value)); }
    private static ClientBid bid(long id, long room, String name, String amount) {
        return new ClientBid(id, room, 2, name, new BigDecimal(amount), id, Instant.now());
    }

    private static final class Playback implements ClientBidVoice.Speech {
        private final List<String> texts = new CopyOnWriteArrayList<>();
        private final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        private final AtomicInteger stops = new AtomicInteger();
        private volatile double lastVolume;
        public boolean available() { return true; }
        public void speak(String text, DoubleSupplier volume, BooleanSupplier cancelled) throws Exception {
            lastVolume = volume.getAsDouble(); texts.add(text); started.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!cancelled.getAsBoolean() && System.nanoTime() < deadline && !release.await(10, TimeUnit.MILLISECONDS)) { }
        }
        public void stop() { stops.incrementAndGet(); }
    }
}
