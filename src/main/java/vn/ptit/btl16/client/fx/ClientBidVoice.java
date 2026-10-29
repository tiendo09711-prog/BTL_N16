package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import vn.ptit.btl16.client.model.ClientBid;
import vn.ptit.btl16.client.model.ClientAuction;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.prefs.Preferences;

/** Sequential local narration for the active room and confirmed personal wins. */
public final class ClientBidVoice implements AutoCloseable {
    public static final String CLOSING_REMINDER = "Có ai muốn đưa ra mức giá cao hơn không ạ?";
    private static final List<String> BID_ANNOUNCEMENTS = loadBidAnnouncements();
    public interface Speech {
        boolean available();
        void speak(String text, DoubleSupplier volume, BooleanSupplier cancelled) throws Exception;
        void stop();
    }

    private final ClientSounds sounds;
    private final Speech speech;
    private final Preferences preferences;
    private final Consumer<Runnable> dispatch;
    private final boolean forcedSilent;
    private final SimpleBooleanProperty enabled = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty available = new SimpleBooleanProperty();
    private final SimpleStringProperty status = new SimpleStringProperty();
    private final LinkedHashSet<Long> observed = new LinkedHashSet<>();
    private final LinkedHashSet<Long> remindedRooms = new LinkedHashSet<>();
    private final AtomicLong generation = new AtomicLong();
    private final LinkedBlockingDeque<Runnable> queue = new LinkedBlockingDeque<>();
    private final Set<Completion> completions = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            queue, task -> { Thread thread = new Thread(task, "auction-bid-voice"); thread.setDaemon(true); return thread; });
    private volatile double volume;
    private final ChangeListener<Boolean> muteListener = (observable, before, after) -> { if (!after) { cancel(); } };
    private final ChangeListener<Number> volumeListener = (observable, before, after) -> {
        volume = after.doubleValue();
        if (volume == 0) { cancel(); }
    };
    private volatile boolean closed;
    private long roomId;

    public ClientBidVoice(ClientSounds sounds) {
        this(sounds, new PiperSpeech(), userPreferences(), Platform::runLater, Boolean.getBoolean("btl16.client.sound.disabled"));
    }

    public ClientBidVoice(ClientSounds sounds, Speech speech, Consumer<Runnable> dispatch) {
        this(sounds, speech, null, dispatch, false);
    }

    private ClientBidVoice(ClientSounds sounds, Speech speech, Preferences preferences, Consumer<Runnable> dispatch, boolean forcedSilent) {
        this.sounds = sounds; this.speech = speech; this.preferences = preferences; this.dispatch = dispatch; this.forcedSilent = forcedSilent;
        available.set(speech.available());
        volume = sounds.getVolume();
        if (preferences != null) {
            try { enabled.set(preferences.getBoolean("enabled", true)); }
            catch (RuntimeException ignored) { }
        }
        status.set(readyStatus());
        sounds.enabledProperty().addListener(muteListener);
        sounds.volumeProperty().addListener(volumeListener);
    }

    private static Preferences userPreferences() {
        try { return Preferences.userRoot().node("vn/ptit/btl16/client/bid-voice"); }
        catch (RuntimeException ignored) { return null; }
    }

    public boolean isEnabled() { return enabled.get(); }
    public ReadOnlyBooleanProperty enabledProperty() { return enabled; }
    public ReadOnlyBooleanProperty availableProperty() { return available; }
    public ReadOnlyStringProperty statusProperty() { return status; }

    public void setEnabled(boolean value) {
        enabled.set(value);
        if (!value) { cancel(); }
        if (preferences != null) {
            try { preferences.putBoolean("enabled", value); }
            catch (RuntimeException ignored) { }
        }
    }

    public void setRoom(long room, List<ClientBid> history) {
        if (room != roomId) { clear(); roomId = room; }
        for (ClientBid bid : history) { if (bid.getAuctionId() == room && bid.getBidId() > 0) { remember(bid.getBidId()); } }
    }

    public boolean announce(ClientBid bid, BigDecimal currentPrice) {
        return announce(bid, currentPrice, null);
    }

    /** Completion runs on the UI dispatcher after playback ends, fails or is cancelled, including queued bids. */
    public boolean announce(ClientBid bid, BigDecimal currentPrice, Runnable onFinished) {
        if (closed || bid == null || roomId <= 0 || bid.getAuctionId() != roomId || bid.getBidId() <= 0
                || bid.getAmount() == null || bid.getAmount().signum() <= 0 || !remember(bid.getBidId())) { return false; }
        if (currentPrice == null || bid.getAmount().compareTo(currentPrice) < 0 || !audible()) { return false; }
        enqueue(announcement(bid.getDisplayName(), bid.getAmount()), () -> true, false, onFinished);
        return true;
    }

    public static String announcement(String name, BigDecimal amount) {
        String spokenName = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", " ").replace('_', ' ').replace("#", " số ").strip().replaceAll("\\s+", " ");
        if (spokenName.isBlank()) { spokenName = "Người tham gia"; }
        String template = BID_ANNOUNCEMENTS.get(ThreadLocalRandom.current().nextInt(BID_ANNOUNCEMENTS.size()));
        return template.replace("{ten_khach}", spokenName);
    }

    private static List<String> loadBidAnnouncements() {
        try (var resource = ClientBidVoice.class.getResourceAsStream("/audio/client/cau_noi_MC_khi_khach_vua_tra_gia.txt")) {
            if (resource != null) {
                List<String> templates = new String(resource.readAllBytes(), StandardCharsets.UTF_8).lines()
                        .map(line -> line.replace("\uFEFF", "").strip())
                        .filter(line -> !line.isBlank() && line.contains("{ten_khach}"))
                        .toList();
                if (!templates.isEmpty()) { return templates; }
            }
        } catch (IOException ignored) { }
        return List.of("Xin ghi nhận mức giá mới từ quý khách {ten_khach}!");
    }

    /** Called once for a server-confirmed personal win; the winner may already be in the lobby. */
    public boolean congratulate(String productName) {
        String name = productName == null ? "" : productName.replaceAll("[\\p{Cntrl}]", " ").strip().replaceAll("\\s+", " ");
        if (name.isBlank() || !audible()) { return false; }
        enqueue("Chúc mừng bạn đã đấu giá thành công " + name + "!", () -> true, true);
        return true;
    }

    /** Once per auction, even after extension, resync or leaving and rejoining the same room. */
    public boolean remindClosing(ClientAuction auction, Instant now) {
        if (closed || auction == null || now == null || roomId <= 0 || auction.getAuctionId() != roomId || !auction.isOpen()
                || auction.getStartTime() == null || now.isBefore(auction.getStartTime()) || auction.getEndTime() == null) { return false; }
        long remaining = java.time.Duration.between(now, auction.getEndTime()).toMillis();
        if (remaining <= 0 || remaining > 30_000 || !remindedRooms.add(roomId)) { return false; }
        while (remindedRooms.size() > 512) { remindedRooms.remove(remindedRooms.iterator().next()); }
        if (!audible()) { return false; }
        long expires = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(remaining);
        enqueue(CLOSING_REMINDER, () -> System.nanoTime() < expires, true);
        return true;
    }

    public void preview() {
        if (audible()) { cancel(); enqueue(announcement("Người tham gia", new BigDecimal("1500000"))); }
    }

    private boolean remember(long id) {
        if (!observed.add(id)) { return false; }
        while (observed.size() > 512) { observed.remove(observed.iterator().next()); }
        return true;
    }

    private boolean audible() { return !closed && !forcedSilent && enabled.get() && available.get() && !sounds.isSilent(); }
    private String readyStatus() { return available.get() ? "Giọng Việt · phong cách MC" : "Chưa cài giọng đọc tiếng Việt"; }

    private void enqueue(String text) { enqueue(text, () -> true, false); }

    private void enqueue(String text, BooleanSupplier relevant, boolean priority) {
        enqueue(text, relevant, priority, null);
    }

    private void enqueue(String text, BooleanSupplier relevant, boolean priority, Runnable onFinished) {
        long expected = generation.get();
        Completion completion = onFinished == null ? null : new Completion(onFinished);
        if (completion != null) { completions.add(completion); }
        Runnable task = () -> {
            try {
                if (closed || expected != generation.get() || !relevant.getAsBoolean()) { return; }
                update(expected, "Đang đọc thông báo đấu giá…");
                speech.speak(text, () -> volume, () -> closed || expected != generation.get() || !relevant.getAsBoolean());
                update(expected, readyStatus());
            } catch (Exception | LinkageError error) {
                if (expected == generation.get() && !closed) { update(expected, "Chưa phát được giọng đọc. Bấm nghe thử để thử lại."); }
            } finally {
                if (completion != null) { completion.finish(); }
            }
        };
        // Closing reminders and personal wins go before pending bids without overlapping the current speaker.
        if (priority) { worker.prestartCoreThread(); queue.addFirst(task); }
        else { worker.execute(task); }
    }

    private void update(long expected, String message) {
        dispatch.accept(() -> { if (!closed && expected == generation.get()) { status.set(message); } });
    }

    private void cancel() {
        generation.incrementAndGet();
        worker.getQueue().clear();
        speech.stop();
        // Cleared queue entries never execute their finally block, so complete them here as well.
        for (Completion completion : completions.toArray(Completion[]::new)) { completion.finish(); }
        status.set(readyStatus());
    }

    private final class Completion {
        private final Runnable action;
        private Completion(Runnable action) { this.action = action; }
        private void finish() { if (completions.remove(this)) { dispatch.accept(action); } }
    }

    public void clear() { cancel(); roomId = 0; observed.clear(); }
    public void clearRoom(long room) { if (room > 0 && roomId == room) { clear(); } }
    public void resetSession() { clear(); remindedRooms.clear(); }

    @Override
    public void close() {
        closed = true;
        resetSession();
        sounds.enabledProperty().removeListener(muteListener);
        sounds.volumeProperty().removeListener(volumeListener);
        worker.shutdownNow();
    }
}
