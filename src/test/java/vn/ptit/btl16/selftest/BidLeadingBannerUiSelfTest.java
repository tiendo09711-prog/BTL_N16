package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.fx.BidLeadingBanner;
import vn.ptit.btl16.client.fx.ClientBidVoice;
import vn.ptit.btl16.client.fx.ClientSounds;
import vn.ptit.btl16.client.fx.FxClientController;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.*;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Actual controller events and request replies, without a live server or user database. */
public final class BidLeadingBannerUiSelfTest {
    private record View(Stage stage, FxClientController controller, ClientAppModel model, Network network,
                        BidLeadingBanner banner, ClientBidVoice voice, GateSpeech speech) { }
    private static long sequence = 10;

    public static void main(String[] args) throws Exception {
        System.setProperty("btl16.client.manualConnect", "true");
        Platform.startup(() -> Platform.setImplicitExit(false));
        View view = fx(() -> {
            var model = new ClientAppModel();
            model.applyIdentity(new ApiResponse(response(MessageType.LOGIN_RESULT,
                    Map.of("userId", "2", "username", "buyer", "sessionToken", "banner-test"))));
            var network = new Network(); Stage stage = new Stage();
            var sounds = new ClientSounds(null, (cue, volume) -> { }, System::nanoTime);
            var speech = new GateSpeech(); var voice = new ClientBidVoice(sounds, speech, Platform::runLater); voice.setEnabled(false);
            var controller = new FxClientController(stage, ClientConfig.loadDefault(), model, network,
                    new AccountApi(network), new AuctionApi(network), sounds, voice);
            invoke(controller, "buildScenes"); stage.setScene((Scene) field(controller, "mainScene"));
            stage.setWidth(1200); stage.setHeight(780); stage.show();
            return new View(stage, controller, model, network, (BidLeadingBanner) field(controller, "bidLeadingBanner"), voice, speech);
        });
        try {
            drain();
            incomingAndAnimation(view);
            ownBids(view);
            historyAndScopes(view);
            lifecycle(view);
            timeoutAndLargeValues(view);
            speechDuration(view);
            System.out.println("[PASS] BidLeadingBannerUiSelfTest: original transparent animation, live username/exact money, newest bid, reply/broadcast dedup, pending/rejected/history/lobby/stale silent, anonymous identity, cleanup, five-second silent fallback, voice longer than five seconds, actual speech completion, queued newer bid, mute/leave/failure cleanup");
        } finally {
            fx(() -> { view.controller().close(); for (Window window : List.copyOf(Window.getWindows())) { window.hide(); } return null; });
            Platform.exit();
        }
    }

    private static void incomingAndAnimation(View view) throws Exception {
        ready(view, 1, "100", null);
        TestSupport.check(((vn.ptit.btl16.client.fx.ClientBackgroundMusic) field(view.controller(), "backgroundMusic")).isInAuctionRoom(), "confirmed room entry selects the room music");
        var update = bid(1, "5000000", 42);
        fx(() -> {
            event(view, update); TestSupport.check(view.banner().isVisible(), "confirmed bid appears for joined viewers");
            TestSupport.equals("phuoc", text(view, "bid-leading-user").getText(), "uses accepted bidder's account name");
            TestSupport.equals("5.000.000 ₫", text(view, "bid-leading-amount").getText(), "uses accepted bid amount");
            TestSupport.check(view.banner().isMouseTransparent(), "banner cannot block bidding or chat");
            var image = (ImageView) view.banner().lookup("#bid-leading-image");
            TestSupport.check(!image.getImage().isError() && image.getImage().getPixelReader().getArgb(0, 0) == 0, "bundled artwork loads with transparent background");
            screenshot(view, "target/bid-leading-room.png");
            var frame = view.banner().lookup("#bid-leading-frame"); var bounds = frame.localToScene(frame.getLayoutBounds());
            TestSupport.check(Math.abs(bounds.getCenterX() - view.stage().getScene().getWidth() / 2) < 2
                    && Math.abs(bounds.getCenterY() - view.stage().getScene().getHeight() / 2) < 2,
                    "banner is centered across the whole window including the sidebar");
            TestSupport.check(text(view, "bid-leading-amount").getFont().getSize() >= 23, "price remains prominent after application CSS");
            var priceBounds = text(view, "bid-leading-amount").getLayoutBounds();
            TestSupport.check(Math.abs(priceBounds.getMinX() + priceBounds.getWidth() / 2 - 265) < 1, "price stays centered after CSS");
            return null;
        });
        var first = fx(() -> ((ImageView) view.banner().lookup("#bid-leading-image")).getViewport());
        Thread.sleep(180);
        TestSupport.check(fx(() -> !first.equals(((ImageView) view.banner().lookup("#bid-leading-image")).getViewport())), "44-frame artwork actually animates");
        fx(() -> {
            view.banner().dismiss(); event(view, update);
            TestSupport.check(!view.banner().isVisible(), "duplicate never reopens banner");
            event(view, bid(1, "6000000.25", -7));
            TestSupport.equals("Khách #7", text(view, "bid-leading-user").getText(), "anonymous rooms retain public guest ID");
            TestSupport.equals("6.000.000,25 ₫", text(view, "bid-leading-amount").getText(), "decimal amount remains exact");
            event(view, update);
            TestSupport.equals("Khách #7", text(view, "bid-leading-user").getText(), "late previous bid cannot replace newest bidder");
            screenshot(view, "target/bid-leading-anonymous.png"); return null;
        });
    }

    private static void ownBids(View view) throws Exception {
        ready(view, 2, "100", null);
        place(view, "110");
        TestSupport.check(fx(() -> !view.banner().isVisible()), "pending bid has no banner");
        view.network().pending.complete(response(MessageType.BID_REJECTED, Map.of("success", "false", "errorCode", "BID_TOO_LOW")));
        drain(); TestSupport.check(fx(() -> !view.banner().isVisible()), "rejected bid has no banner");
        ready(view, 2, "100", null); place(view, "110");
        var accepted = bid(2, "110", 2);
        view.network().pending.complete(response(MessageType.BID_ACCEPTED, accepted)); drain();
        fx(() -> {
            TestSupport.check(view.banner().isVisible(), "own accepted response displays banner without waiting for broadcast");
            TestSupport.equals("buyer", text(view, "bid-leading-user").getText(), "own confirmed account name");
            view.banner().dismiss(); event(view, accepted);
            TestSupport.check(!view.banner().isVisible(), "broadcast after response does not replay"); return null;
        });
        ready(view, 3, "100", null); place(view, "110");
        var broadcastFirst = bid(3, "110", 2);
        fx(() -> { event(view, broadcastFirst); TestSupport.check(view.banner().isVisible(), "broadcast may arrive before reply"); view.banner().dismiss(); return null; });
        view.network().pending.complete(response(MessageType.BID_ACCEPTED, broadcastFirst)); drain();
        TestSupport.check(fx(() -> !view.banner().isVisible()), "reply after broadcast does not replay");
    }

    private static void historyAndScopes(View view) throws Exception {
        var old = bid(4, "5000000", 77); ready(view, 4, "5000000", old);
        fx(() -> {
            TestSupport.check(!view.banner().isVisible(), "join snapshot never replays old bids"); event(view, old);
            TestSupport.check(!view.banner().isVisible(), "known history remains silent when rebroadcast");
            event(view, bid(4, "4000000", 88));
            TestSupport.check(!view.banner().isVisible(), "stale cheaper event is silent");
            event(view, bid(8, "9000000", 99));
            TestSupport.check(!view.banner().isVisible(), "another room cannot trigger joined-room banner");
            event(view, bid(4, "6000000", 88)); TestSupport.check(view.banner().isVisible(), "fresh joined-room event still works");
            view.model().leaveJoinedAuction(); invoke(view.controller(), "renderAuctions");
            TestSupport.check(!((vn.ptit.btl16.client.fx.ClientBackgroundMusic) field(view.controller(), "backgroundMusic")).isInAuctionRoom(), "leaving restores lobby music");
            TestSupport.check(!view.banner().isVisible(), "leaving hides banner immediately");
            event(view, bid(4, "7000000", 99));
            TestSupport.check(!view.banner().isVisible(), "lobby remains silent"); return null;
        });
    }

    private static void lifecycle(View view) throws Exception {
        for (MessageType end : List.of(MessageType.AUCTION_ENDED, MessageType.AUCTION_CANCELLED)) {
            long room = end == MessageType.AUCTION_ENDED ? 5 : 6; ready(view, room, "100", null);
            fx(() -> {
                var update = bid(room, "110", 42); event(view, update);
                TestSupport.check(view.banner().isVisible(), "banner visible before closure");
                var ended = new LinkedHashMap<>(update); ended.put("status", end == MessageType.AUCTION_ENDED ? "ENDED" : "CANCELLED");
                ended.put("version", Long.toString(++sequence)); ended.put("endedAt", Instant.now().toString());
                invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, WireMessage.event(end, sequence, ended));
                TestSupport.check(!view.banner().isVisible(), "closure hides banner"); event(view, update);
                TestSupport.check(!view.banner().isVisible(), "late bid cannot reopen an ended room banner"); return null;
            });
        }
        ready(view, 7, "100", null);
        fx(() -> {
            event(view, bid(7, "110", 42)); view.network().state = ConnectionState.DISCONNECTED;
            invoke(view.controller(), "onConnectionState", new Class<?>[]{ConnectionState.class, String.class}, ConnectionState.DISCONNECTED, "test disconnect");
            return null;
        }); drain();
        TestSupport.check(fx(() -> !view.banner().isVisible()), "disconnect cancels active animation and timeout");
        TestSupport.check(!((vn.ptit.btl16.client.fx.ClientBackgroundMusic) field(view.controller(), "backgroundMusic")).isInAuctionRoom(), "disconnect leaves room music");
    }

    private static void timeoutAndLargeValues(View view) throws Exception {
        ready(view, 9, "100", null);
        fx(() -> {
            String longName = "W".repeat(30);
            var update = bid(9, "999999999999999.99", Long.MAX_VALUE); update.put("bid.username", longName);
            event(view, update);
            TestSupport.equals(longName, text(view, "bid-leading-user").getText(), "full long account name");
            TestSupport.equals("999.999.999.999.999,99 ₫", text(view, "bid-leading-amount").getText(), "maximum money retains all digits");
            TestSupport.check(text(view, "bid-leading-user").getLayoutBounds().getWidth() <= 128
                    && text(view, "bid-leading-amount").getLayoutBounds().getWidth() <= 175, "large values fit inside original panels");
            view.stage().setWidth(940); view.stage().setHeight(700); screenshot(view, "target/bid-leading-large.png");
            var frame = view.banner().lookup("#bid-leading-frame"); var bounds = frame.localToScene(frame.getLayoutBounds());
            TestSupport.check(bounds.getMinX() >= 0 && bounds.getMaxX() < view.stage().getScene().getWidth()
                    && bounds.getMinY() >= 0 && bounds.getMaxY() <= view.stage().getScene().getHeight(), "banner stays inside resized window");
            return null;
        });
        Thread.sleep(500); TestSupport.check(fx(() -> view.banner().isVisible()), "banner stays visible while animation runs");
        TestSupport.waitUntil(() -> { try { return fx(() -> !view.banner().isVisible()); } catch (Exception error) { throw new RuntimeException(error); } }, 6500, "banner automatically hides after five seconds");
    }

    private static void speechDuration(View view) throws Exception {
        ready(view, 10, "100", null);
        var update = bid(10, "110", 42);
        update.put("bid.displayName", "phước");
        fx(() -> { view.voice().setEnabled(true); event(view, update); return null; });
        var longSpeech = view.speech().take();
        TestSupport.check(longSpeech.text.contains("phước") && !longSpeech.text.contains("phuoc"), "live broadcast voice reads profile display name");
        Thread.sleep(5300);
        TestSupport.check(fx(() -> view.banner().isVisible()), "GIF stays visible past five seconds while voice is still reading");
        longSpeech.release.countDown(); awaitHidden(view, "GIF hides when voice actually finishes");
        fx(() -> { event(view, update); TestSupport.check(!view.banner().isVisible(), "completed bid cannot replay GIF or voice"); return null; });

        ready(view, 11, "100", null);
        fx(() -> { event(view, bid(11, "110", 42)); return null; });
        var first = view.speech().take();
        fx(() -> { event(view, bid(11, "120", 99)); TestSupport.equals("tien", text(view, "bid-leading-user").getText(), "queued bid updates banner immediately"); return null; });
        first.release.countDown(); var second = view.speech().take(); drain();
        TestSupport.check(fx(() -> view.banner().isVisible()), "older voice completion cannot hide newer bid's GIF");
        second.release.countDown(); awaitHidden(view, "newest voice completion hides newest GIF");

        ready(view, 12, "100", null);
        fx(() -> { event(view, bid(12, "110", 42)); return null; });
        var cancelled = view.speech().take();
        fx(() -> { event(view, bid(12, "120", 99)); view.voice().setEnabled(false); return null; });
        awaitHidden(view, "muting completes both active and queued GIF holds"); cancelled.release.countDown();

        ready(view, 13, "100", null);
        fx(() -> { view.voice().setEnabled(true); event(view, bid(13, "110", 42)); return null; });
        var failed = view.speech().take(); failed.fail = true; failed.release.countDown();
        awaitHidden(view, "failed voice cannot leave GIF stuck on screen");

        ready(view, 14, "100", null); place(view, "110");
        var own = bid(14, "110", 2); own.put("bid.displayName", "Người mua");
        view.network().pending.complete(response(MessageType.BID_ACCEPTED, own)); drain();
        var ownSpeech = view.speech().take();
        TestSupport.check(ownSpeech.text.contains("Người mua") && !ownSpeech.text.contains("buyer"), "accepted reply voice also reads profile display name");
        fx(() -> { event(view, own); TestSupport.check(view.banner().isVisible(), "own reply starts GIF and voice before duplicate broadcast"); return null; });
        ownSpeech.release.countDown(); awaitHidden(view, "own accepted bid shares voice completion lifecycle");

        ready(view, 15, "100", null);
        fx(() -> { event(view, bid(15, "110", 42)); return null; }); var leaving = view.speech().take();
        fx(() -> { invoke(view.controller(), "invalidateRoom", new Class<?>[]{boolean.class}, true); return null; });
        awaitHidden(view, "leaving while voice is active hides GIF immediately"); leaving.release.countDown();
        TestSupport.check(view.speech().started.isEmpty(), "no duplicate or cancelled queued narration starts");
    }

    private static void awaitHidden(View view, String message) throws Exception {
        TestSupport.waitUntil(() -> { try { return fx(() -> !view.banner().isVisible()); } catch (Exception error) { throw new RuntimeException(error); } }, 3000, message);
    }

    private static void ready(View view, long room, String price, Map<String, String> history) throws Exception {
        fx(() -> {
            view.network().state = ConnectionState.CONNECTED; invoke(view.controller(), "invalidateRoom", new Class<?>[]{boolean.class}, true);
            Map<String, String> snapshot = data(room, price, 0); view.network().snapshot = snapshot;
            if (history != null) {
                snapshot.put("bidCount", "1"); history.forEach((key, value) -> { if (key.startsWith("bid.")) { snapshot.put(key.replace("bid.", "bid.0."), value); } });
            }
            invoke(view.controller(), "applySnapshot", new Class<?>[]{ApiResponse.class, boolean.class}, new ApiResponse(response(MessageType.RESYNC_RESULT, snapshot)), true);
            invoke(view.controller(), "renderAuctions");
            set(view.controller(), "roomSnapshotReady", true); set(view.controller(), "roomChanging", false); set(view.controller(), "roomSyncing", false);
            set(view.controller(), "roomAvailable", new BigDecimal("100000000")); set(view.controller(), "roomHeld", BigDecimal.ZERO);
            set(view.controller(), "maxBidAmount", new BigDecimal("999999999999999.99"));
            set(view.controller(), "lastRoomSignal", System.nanoTime()); set(view.controller(), "lastRoomWallet", System.nanoTime());
            invoke(view.controller(), "refreshBidControls"); return null;
        }); drain();
    }

    private static void place(View view, String price) throws Exception {
        fx(() -> {
            ((TextField) field(view.controller(), "bidField")).setText(price);
            Button button = (Button) field(view.controller(), "bidButton"); TestSupport.check(!button.isDisabled(), "test bid is available"); button.fire(); return null;
        });
    }

    private static Map<String, String> data(long room, String price, long winner) {
        var result = new LinkedHashMap<>(Map.of("auctionId", Long.toString(room), "productId", Long.toString(room), "productName", "Bình gốm cổ", "hostUserId", "1", "hostUsername", "host",
                "status", "OPEN", "currentPrice", price, "startPrice", "100", "minBidIncrement", "10"));
        result.putAll(Map.of("version", Long.toString(++sequence), "startTime", Instant.now().minusSeconds(60).toString(), "endTime", Instant.now().plusSeconds(300).toString(),
                "serverNow", Instant.now().toString(), "currentWinnerId", winner == 0 ? "" : Long.toString(winner), "currentWinnerUsername", "participant")); return result;
    }

    private static Map<String, String> bid(long room, String price, long user) {
        Map<String, String> result = data(room, price, user);
        result.putAll(Map.of("bid.bidId", Long.toString(sequence), "bid.auctionId", Long.toString(room), "bid.userId", Long.toString(user), "bid.username", user == 2 ? "buyer" : user == 42 ? "phuoc" : user == 99 ? "tien" : "participant",
                "bid.amount", price, "bid.serverSequence", Long.toString(sequence), "bid.createdAt", Instant.now().toString())); return result;
    }
    private static void event(View view, Map<String, String> data) throws Exception { invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, WireMessage.event(MessageType.BID_UPDATE, sequence, data)); }
    private static Text text(View view, String id) { return (Text) view.banner().lookup("#" + id); }
    private static WireMessage response(MessageType type, Map<String, String> data) { var result = new LinkedHashMap<>(data); result.putIfAbsent("success", "true"); return WireMessage.response(type, null, 1, result); }
    private static Object field(Object target, String name) throws Exception { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void set(Object target, String name, Object value) throws Exception { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
    private static void invoke(Object target, String name) throws Exception { invoke(target, name, new Class<?>[0]); }
    private static void invoke(Object target, String name, Class<?>[] types, Object... values) throws Exception { var method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(target, values); }
    private static <T> T fx(Callable<T> action) throws Exception { var result = new CompletableFuture<T>(); Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } }); return result.get(30, TimeUnit.SECONDS); }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }
    private static void screenshot(View view, String path) throws Exception {
        var root = view.stage().getScene().getRoot(); root.applyCss(); root.layout(); var image = root.snapshot(null, null);
        var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bitmap.getHeight(); y++) { for (int x = 0; x < bitmap.getWidth(); x++) { bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y)); } }
        javax.imageio.ImageIO.write(bitmap, "png", new java.io.File(path));
    }

    private static final class GateSpeech implements ClientBidVoice.Speech {
        private final BlockingQueue<Utterance> started = new LinkedBlockingQueue<>();
        public boolean available() { return true; }
        public void speak(String text, java.util.function.DoubleSupplier volume, java.util.function.BooleanSupplier cancelled) throws Exception {
            var utterance = new Utterance(text); started.add(utterance);
            while (!cancelled.getAsBoolean() && !utterance.release.await(10, TimeUnit.MILLISECONDS)) { }
            if (utterance.fail) { throw new java.io.IOException("simulated playback error"); }
        }
        public void stop() { }
        private Utterance take() throws Exception {
            Utterance utterance = started.poll(3, TimeUnit.SECONDS); TestSupport.check(utterance != null, "expected speech starts"); return utterance;
        }
    }
    private static final class Utterance {
        private final String text;
        private Utterance(String text) { this.text = text; }
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile boolean fail;
    }

    private static final class Network implements ClientTransport {
        private ConnectionState state = ConnectionState.CONNECTED;
        private Map<String, String> snapshot = Map.of();
        private CompletableFuture<WireMessage> pending;
        @Override public CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) {
            if (type == MessageType.PLACE_BID) { pending = new CompletableFuture<>(); return pending; }
            if (type == MessageType.RESYNC) { return CompletableFuture.completedFuture(response(MessageType.RESYNC_RESULT, snapshot)); }
            return CompletableFuture.completedFuture(response(type, Map.of("auctionId", snapshot.getOrDefault("auctionId", "1"), "count", "0", "available", "100000000", "roomHeld", "0", "maxBidAmount", "999999999999999.99", "memberCount", "0", "chatCount", "0", "entryCount", "0")));
        }
        @Override public CompletableFuture<Void> connect() { state = ConnectionState.CONNECTED; return CompletableFuture.completedFuture(null); }
        @Override public void disconnect() { state = ConnectionState.DISCONNECTED; }
        @Override public boolean isConnected() { return state == ConnectionState.CONNECTED; }
        @Override public ConnectionState getState() { return state; }
        @Override public String getEndpoint() { return "test"; }
        @Override public void setEndpoint(String endpoint) { }
        @Override public CompletableFuture<WireMessage> ping() { return sendRequest(MessageType.PING, Map.of()); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { }
        @Override public void addStateListener(ConnectionStateListener listener) { }
        @Override public void close() { state = ConnectionState.CLOSED; }
    }
}
