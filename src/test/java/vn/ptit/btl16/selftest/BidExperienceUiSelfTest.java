package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import vn.ptit.btl16.client.fx.FxClientController;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.network.ConnectionState;
import vn.ptit.btl16.client.network.ConnectionStateListener;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.common.protocol.MessageKind;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;
import vn.ptit.btl16.common.util.Money;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class BidExperienceUiSelfTest {
    private static final Instant NOW = Instant.now();
    private static final String MAXIMUM = "999999999999999.00";
    private record Pending(MessageType type, Map<String, String> data, CompletableFuture<WireMessage> result) {
        void reply(MessageType responseType, Map<String, String> values) { result.complete(message(responseType, values)); }
        void reject(String code) { reply(MessageType.BID_REJECTED, Map.of("success", "false", "errorCode", code)); }
    }
    private record View(Stage stage, FxClientController controller, ClientAppModel model, Network network) { }

    public static void main(String[] args) throws Exception {
        System.setProperty("btl16.client.manualConnect", "true");
        Platform.startup(() -> Platform.setImplicitExit(false));
        View view = null;
        try {
            moneyInput();
            view = fx(() -> {
                ClientAppModel model = new ClientAppModel();
                model.applyIdentity(new ApiResponse(message(MessageType.LOGIN_RESULT,
                        Map.of("success", "true", "userId", "2", "username", "buyer", "sessionToken", "session-one"))));
                Network network = new Network();
                Stage stage = new Stage();
                FxClientController controller = new FxClientController(stage, ClientConfig.loadDefault(), model,
                        network, new AccountApi(network), new AuctionApi(network));
                invoke(controller, "buildScenes");
                stage.setScene((Scene) field(controller, "mainScene"));
                stage.setWidth(1240); stage.setHeight(760); stage.show();
                return new View(stage, controller, model, network);
            });
            pendingAndErrors(view);
            watchdogAndLateReply(view);
            uncertainAndReconnect(view);
            roomAndSessionGuards(view);
            readCallbackGuards(view);
            anonymousAndLayout(view);
            System.out.println("[PASS] BidExperienceUiSelfTest: duplicate/Enter/quick-step guard, inline errors, concurrent prices, uncertain timeout/disconnect, resync gates, stale callbacks, anonymous self-id, exact money, laptop history");
        } finally {
            View closing = view;
            if (closing != null) { fx(() -> { closing.controller().close(); closing.stage().close(); return null; }); }
            Platform.exit();
        }
    }

    private static void moneyInput() {
        TestSupport.equals(new BigDecimal("1234.56"), Money.parseInput("1.234,56"), "Vietnamese formatted input");
        TestSupport.equals(new BigDecimal("1234.56"), Money.parseInput("1234.56"), "existing plain decimals");
        TestSupport.equals(new BigDecimal(MAXIMUM), Money.parseInput("999.999.999.999.999"), "maximum is exact");
        TestSupport.equals("1.234,56 \u20ab", WalletUi.money(new BigDecimal("1234.56")), "shared money format");
        for (String invalid : List.of("0.001", "1234,567", "1,234.56", "1e3", "-10", "0", "NaN", "9".repeat(80))) {
            try { Money.parseInput(invalid); throw new AssertionError("Invalid amount accepted: " + invalid); }
            catch (IllegalArgumentException expected) { }
        }
    }

    private static void pendingAndErrors(View view) throws Exception {
        ready(view, data(1, "100", null, 1));
        fx(() -> {
            TextField input = input(view); input.setText("110"); button(view, "bidButton").fire();
            TestSupport.check(button(view, "bidButton").isDisabled(), "button locks synchronously");
            TestSupport.check(button(view, "bidButton").getText().contains("g\u1eedi"), "pending button text");
            input.fireEvent(new ActionEvent()); invoke(view.controller(), "placeBid");
            for (Button quick : quick(view)) { quick.fire(); TestSupport.check(quick.isDisabled(), "quick steps lock"); }
            TestSupport.equals("110", input.getText(), "quick actions cannot alter pending amount");
            return null;
        });
        TestSupport.equals(1, view.network().count(MessageType.PLACE_BID), "only one request for repeated submits");
        Pending bid = view.network().take(MessageType.PLACE_BID);
        fx(() -> {
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class},
                    message(MessageType.BID_UPDATE, accepted(1, "120", 3, 2)));
            TestSupport.check(button(view, "bidButton").isDisabled(), "price event cannot unlock pending send"); return null;
        });
        view.network().take(MessageType.GET_WALLET).reply(MessageType.WALLET_RESULT, funds(1, "10000", "0"));
        bid.reject("BID_TOO_LOW"); drain();
        TestSupport.check(fx(() -> feedback(view).contains("thay \u0111\u1ed5i")), "price race is explained inline");
        finishSync(view, data(1, "120", 3L, 2));
        fx(() -> {
            TestSupport.check(!button(view, "bidButton").isDisabled(), "fresh state unlocks next manual bid");
            quick(view).get(0).fire(); TestSupport.equals("130", input(view).getText(), "quick amount uses latest price");
            input(view).setText("120"); button(view, "bidButton").fire();
            TestSupport.check(feedback(view).contains("130"), "local minimum feedback uses latest server price");
            input(view).setText("1000000000000000"); button(view, "bidButton").fire();
            TestSupport.check(feedback(view).contains("v\u01b0\u1ee3t"), "server money limit checked exactly");
            return null;
        });
        TestSupport.equals(0, view.network().count(MessageType.PLACE_BID), "invalid local amounts never sent");
        for (String code : List.of("INSUFFICIENT_FUNDS", "BID_ALREADY_LEADING", "AUCTION_NOT_OPEN")) {
            send(view, "130").reject(code); drain();
            TestSupport.check(fx(() -> !feedback(view).isBlank()), "business failure remains inline: " + code);
            finishSync(view, data(1, "120", 3L, 2));
        }
    }

    private static void watchdogAndLateReply(View view) throws Exception {
        Pending delayed = send(view, "130");
        int timeout = fx(() -> ((ClientConfig) field(view.controller(), "config")).getRequestTimeoutMillis());
        TestSupport.waitUntil(() -> view.network().count(MessageType.RESYNC) == 1, timeout + 3000, "watchdog starts a read-only resync when no callback arrives");
        TestSupport.check(fx(() -> feedback(view).contains("ch\u01b0a x\u00e1c nh\u1eadn")), "watchdog reports uncertainty");
        finishSync(view, data(1, "120", 3L, 2));
        Pending newer = send(view, "130");
        delayed.reject("BID_TOO_LOW"); drain();
        fx(() -> {
            TestSupport.check((boolean) field(view.controller(), "bidPending"), "late reply cannot complete newer request");
            TestSupport.check(feedback(view).contains("\u0110ang g\u1eedi"), "late reply cannot replace newer feedback"); return null;
        });
        newer.reject("INSUFFICIENT_FUNDS"); drain(); finishSync(view, data(1, "120", 3L, 2));
    }

    private static void uncertainAndReconnect(View view) throws Exception {
        Pending bid = send(view, "130");
        bid.result().completeExceptionally(new TimeoutException("lost acknowledgement")); drain();
        fx(() -> {
            TestSupport.check(feedback(view).contains("ch\u01b0a x\u00e1c nh\u1eadn"), "timeout is not declared rejected");
            TestSupport.check(button(view, "bidButton").isDisabled(), "blocked until resync and wallet"); return null;
        });
        finishSync(view, data(1, "120", 3L, 2));
        TestSupport.equals(0, view.network().count(MessageType.PLACE_BID), "resync never retries bid");
        TestSupport.check(fx(() -> feedback(view).contains("ch\u01b0a x\u00e1c nh\u1eadn")), "missing history is not proof of rejection");
        Pending disconnected = send(view, "130");
        fx(() -> {
            view.network().state = ConnectionState.DISCONNECTED;
            invoke(view.controller(), "onConnectionState", new Class<?>[]{ConnectionState.class, String.class}, ConnectionState.DISCONNECTED, "test");
            return null;
        }); drain();
        TestSupport.check(fx(() -> feedback(view).contains("ch\u01b0a x\u00e1c nh\u1eadn")), "disconnect preserves unknown result");
        fx(() -> {
            view.network().state = ConnectionState.CONNECTED;
            invoke(view.controller(), "onConnectionState", new Class<?>[]{ConnectionState.class, String.class}, ConnectionState.CONNECTED, "test");
            return null;
        }); drain();
        disconnected.reply(MessageType.BID_ACCEPTED, accepted(1, "130", 2, 3)); drain();
        TestSupport.check(fx(() -> button(view, "bidButton").isDisabled()), "socket connected is not resynced");
        fx(() -> { invoke(view.controller(), "synchronizeRoom"); return null; });
        Map<String, String> snapshot = accepted(1, "130", 2, 3);
        snapshot.put("bidCount", "1");
        for (var entry : new ArrayList<>(snapshot.entrySet())) {
            if (entry.getKey().startsWith("bid.")) { snapshot.put(entry.getKey().replace("bid.", "bid.0."), entry.getValue()); }
        }
        finishSync(view, snapshot);
        TestSupport.check(fx(() -> feedback(view).contains("Server \u0111\u00e3 x\u00e1c nh\u1eadn")), "accepted history confirms amount without claiming win");
        TestSupport.check(fx(() -> button(view, "bidButton").isDisabled()), "confirmed leader cannot self-raise");
        fx(() -> { set(view.controller(), "lastRoomSignal", System.nanoTime() - TimeUnit.SECONDS.toNanos(20));
            invoke(view.controller(), "refreshBidControls");
            TestSupport.check(((Label) field(view.controller(), "roomConnection")).getText().contains("c\u0169"), "stale differs from connected");
            return null; });
    }

    private static void roomAndSessionGuards(View view) throws Exception {
        ready(view, data(2, "100", null, 1));
        Pending old = send(view, "110");
        fx(() -> {
            view.model().applySnapshot(ClientWireParser.auction(data(3, "500", null, 1), ""), List.of(), NOW, false);
            invoke(view.controller(), "renderAuctions"); table(view).getSelectionModel().select(view.model().auctionSnapshot().stream()
                    .filter(auction -> auction.getAuctionId() == 3).findFirst().orElseThrow());
            invoke(view.controller(), "joinSelected"); return null;
        });
        view.network().take(MessageType.JOIN_AUCTION).reply(MessageType.AUCTION_SNAPSHOT, data(3, "500", null, 1)); drain();
        view.network().take(MessageType.GET_WALLET).reply(MessageType.WALLET_RESULT, funds(3, "9000", "0")); drain();
        old.reply(MessageType.BID_ACCEPTED, accepted(2, "110", 2, 2)); drain();
        fx(() -> {
            TestSupport.equals(3L, view.model().getJoinedAuctionId(), "late bid cannot switch rooms");
            TestSupport.equals("500", view.model().joinedAuction().getCurrentPrice().toPlainString(), "late amount cannot overwrite new room");
            TestSupport.check(feedback(view).isBlank(), "late callback cannot show success in another room"); return null;
        });
        Pending leaving = send(view, "510");
        fx(() -> { invoke(view.controller(), "leaveJoined"); return null; });
        leaving.reject("INSUFFICIENT_FUNDS"); drain();
        TestSupport.check(fx(() -> feedback(view).isBlank()), "callback ignored as soon as leave starts");
        view.network().take(MessageType.LEAVE_AUCTION).reply(MessageType.LEAVE_AUCTION_RESULT, Map.of("success", "true")); drain();
        ready(view, data(4, "100", null, 1));
        Pending loggingOut = send(view, "110");
        fx(() -> { invoke(view.controller(), "logout"); return null; });
        TestSupport.check(fx(() -> !((Label) field(view.controller(), "walletBalance")).getText().contains("10.000")), "logout clears private balance immediately");
        loggingOut.reply(MessageType.BID_ACCEPTED, accepted(4, "110", 2, 2)); drain();
        TestSupport.check(fx(() -> feedback(view).isBlank()), "logout invalidates callbacks before response");
        view.network().take(MessageType.LOGOUT).reply(MessageType.LOGOUT_RESULT, Map.of("success", "true")); drain();
        fx(() -> { view.model().applyIdentity(new ApiResponse(message(MessageType.LOGIN_RESULT,
                Map.of("success", "true", "userId", "2", "username", "buyer", "sessionToken", "session-two"))));
            view.stage().setScene((Scene) field(view.controller(), "mainScene")); return null; });
    }

    private static void readCallbackGuards(View view) throws Exception {
        ready(view, data(7, "100", null, 1));
        fx(() -> { invoke(view.controller(), "synchronizeRoom"); return null; });
        Pending oldSnapshot = view.network().take(MessageType.RESYNC);
        ready(view, data(8, "200", null, 1));
        oldSnapshot.reply(MessageType.RESYNC_RESULT, data(7, "100", null, 1)); drain();
        TestSupport.equals(8L, fx(() -> view.model().getJoinedAuctionId()), "late resync cannot rejoin old room");
        TestSupport.equals(0, view.network().count(MessageType.GET_WALLET), "late snapshot cannot trigger another room wallet read");
        fx(() -> { invoke(view.controller(), "synchronizeRoom"); return null; });
        view.network().take(MessageType.RESYNC).reply(MessageType.RESYNC_RESULT, data(8, "200", null, 1)); drain();
        Pending oldWallet = view.network().take(MessageType.GET_WALLET);
        ready(view, data(9, "300", null, 1));
        oldWallet.reply(MessageType.WALLET_RESULT, funds(8, "42", "500")); drain();
        TestSupport.check(fx(() -> ((BigDecimal) field(view.controller(), "roomAvailable")).compareTo(new BigDecimal("10000")) == 0), "old wallet cannot overwrite new room data");
        fx(() -> {
            set(view.controller(), "roomAvailable", null);
            invoke(view.controller(), "refreshBidControls"); invoke(view.controller(), "refreshRoomWallet"); return null;
        });
        view.network().take(MessageType.GET_WALLET).reply(MessageType.WALLET_RESULT, Map.of("success", "false", "errorCode", "ROOM_ACCESS_DENIED")); drain();
        TestSupport.check(fx(() -> button(view, "bidButton").isDisabled()), "failed wallet access never unlocks bid");
    }

    private static void anonymousAndLayout(View view) throws Exception {
        Map<String, String> anonymous = data(5, "100", -1L, 2);
        anonymous.put("anonymousGuests", "true"); anonymous.put("selfParticipantId", "-1");
        ready(view, anonymous);
        fx(() -> { TestSupport.check(button(view, "bidButton").isDisabled(), "anonymous leader compares room-local self id"); return null; });
        Map<String, String> hosting = data(10, "120", 3L, 2); hosting.put("hostUserId", "2");
        ready(view, hosting);
        fx(() -> {
            TestSupport.check(button(view, "endButton").isDisabled() && button(view, "cancelButton").isDisabled()
                    && button(view, "extendButton").isDisabled(), "host cannot change rules after a bid");
            TestSupport.check(((Label) field(view.controller(), "hostLockReason")).getText().contains("\u0110\u00e3 c\u00f3"), "host lock reason is visible");
            TestSupport.check(!button(view, "kickButton").isDisabled(), "eligible moderation still available"); return null;
        });
        ready(view, data(6, "100", null, 1));
        Thread.sleep(450);
        fx(() -> {
            view.stage().setWidth(1240); view.stage().setHeight(760);
            var root = view.stage().getScene().getRoot(); root.applyCss(); root.layout();
            HBox columns = (HBox) field(view.controller(), "roomColumns");
            TestSupport.equals(2, columns.getChildren().size(), "room keeps two columns");
            ScrollPane history = (ScrollPane) field(view.controller(), "roomControlsScroll");
            TestSupport.check(history.getHeight() >= 159, "history keeps dedicated visible viewport on laptop");
            ((javafx.scene.control.TitledPane) field(view.controller(), "roomRules")).setExpanded(true);
            root.applyCss(); root.layout();
            TestSupport.check(history.getHeight() >= 159, "expanded rules do not push history out of view");
            ((javafx.scene.control.TitledPane) field(view.controller(), "roomRules")).setExpanded(false);
            root.lookupAll(".room-status-title, .room-live-notice, .room-live-bid, .price").forEach(node -> {
                node.setOpacity(1); node.setTranslateY(0);
            });
            TestSupport.check(columns.getChildren().get(1).getBoundsInParent().getMaxX() <= columns.getWidth() + 1, "right column does not overflow");
            Path directory = Path.of("target", "bid-experience-preview"); Files.createDirectories(directory);
            var image = view.stage().getScene().snapshot(null);
            var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int row = 0; row < bitmap.getHeight(); row++) {
                for (int column = 0; column < bitmap.getWidth(); column++) { bitmap.setRGB(column, row, image.getPixelReader().getArgb(column, row)); }
            }
            javax.imageio.ImageIO.write(bitmap, "png", directory.resolve("laptop.png").toFile()); return null;
        });
    }

    private static void ready(View view, Map<String, String> snapshot) throws Exception {
        fx(() -> {
            invoke(view.controller(), "invalidateRoom", new Class<?>[]{boolean.class}, true);
            view.model().leaveJoinedAuction();
            view.model().applySnapshot(ClientWireParser.auction(snapshot, ""), List.of(), NOW, true);
            invoke(view.controller(), "renderAuctions");
            table(view).getSelectionModel().select(view.model().joinedAuction());
            invoke(view.controller(), "synchronizeRoom"); return null;
        });
        finishSync(view, snapshot);
    }

    private static void finishSync(View view, Map<String, String> snapshot) throws Exception {
        view.network().take(MessageType.RESYNC).reply(MessageType.RESYNC_RESULT, snapshot); drain();
        fx(() -> { TestSupport.check(button(view, "bidButton").isDisabled(), "snapshot alone is insufficient without wallet"); return null; });
        long room = Long.parseLong(snapshot.get("auctionId"));
        view.network().take(MessageType.GET_WALLET).reply(MessageType.WALLET_RESULT, funds(room, "10000", "0")); drain();
    }

    private static Pending send(View view, String amount) throws Exception {
        fx(() -> { input(view).setText(amount); button(view, "bidButton").fire(); return null; });
        return view.network().take(MessageType.PLACE_BID);
    }

    private static Map<String, String> data(long room, String price, Long winner, long version) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("success", "true"); values.put("auctionId", Long.toString(room)); values.put("productId", Long.toString(room));
        values.put("productName", "Demo product"); values.put("hostUserId", "1"); values.put("hostUsername", "host");
        values.put("startPrice", "100"); values.put("currentPrice", price); values.put("minBidIncrement", "10");
        values.put("currentWinnerId", winner == null ? "" : Long.toString(winner)); values.put("currentWinnerUsername", winner == null ? "" : "participant");
        values.put("status", "OPEN"); values.put("version", Long.toString(version)); values.put("serverNow", Instant.now().toString());
        values.put("startTime", NOW.minusSeconds(10).toString()); values.put("endTime", NOW.plusSeconds(300).toString());
        values.put("hardEndTime", NOW.plusSeconds(600).toString()); values.put("bidCount", "0"); return values;
    }

    private static Map<String, String> accepted(long room, String amount, long user, long version) {
        Map<String, String> values = data(room, amount, user, version);
        values.put("bid.bidId", Long.toString(version)); values.put("bid.auctionId", Long.toString(room)); values.put("bid.userId", Long.toString(user));
        values.put("bid.username", "participant"); values.put("bid.amount", amount); values.put("bid.serverSequence", Long.toString(version));
        values.put("bid.createdAt", Instant.now().toString()); return values;
    }

    private static Map<String, String> funds(long room, String available, String held) {
        return Map.of("success", "true", "auctionId", Long.toString(room), "available", available, "roomHeld", held, "maxBidAmount", MAXIMUM);
    }
    private static WireMessage message(MessageType type, Map<String, String> data) {
        return new WireMessage(1, MessageKind.RESPONSE, type, "test", 1, System.currentTimeMillis(), data);
    }
    private static TextField input(View view) throws Exception { return (TextField) field(view.controller(), "bidField"); }
    private static Button button(View view, String name) throws Exception { return (Button) field(view.controller(), name); }
    private static String feedback(View view) throws Exception { return ((Label) field(view.controller(), "bidFeedback")).getText(); }
    @SuppressWarnings("unchecked")
    private static List<Button> quick(View view) throws Exception { return (List<Button>) field(view.controller(), "quickBidButtons"); }
    @SuppressWarnings("unchecked")
    private static TableView<ClientAuction> table(View view) throws Exception { return (TableView<ClientAuction>) field(view.controller(), "auctionTable"); }
    private static Object field(Object target, String name) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void set(Object target, String name, Object value) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
    private static void invoke(Object target, String name) throws Exception { invoke(target, name, new Class<?>[0]); }
    private static void invoke(Object target, String name, Class<?>[] types, Object... values) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(target, values);
    }
    private static <Result> Result fx(Callable<Result> action) throws Exception {
        CompletableFuture<Result> result = new CompletableFuture<>();
        Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result.get(10, TimeUnit.SECONDS);
    }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }

    private static final class Network implements ClientTransport {
        private final List<Pending> requests = new ArrayList<>();
        private ConnectionState state = ConnectionState.CONNECTED;
        synchronized Pending take(MessageType type) {
            Pending request = requests.stream().filter(value -> value.type() == type).findFirst()
                    .orElseThrow(() -> new AssertionError("Missing request " + type + "; queued: " + requests));
            requests.remove(request); return request;
        }
        synchronized int count(MessageType type) { return (int) requests.stream().filter(value -> value.type() == type).count(); }
        @Override public synchronized CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) {
            if (type == MessageType.PLACE_BID || type == MessageType.RESYNC || type == MessageType.JOIN_AUCTION
                    || type == MessageType.LEAVE_AUCTION || type == MessageType.LOGOUT
                    || type == MessageType.GET_WALLET && data.containsKey("auctionId")) {
                CompletableFuture<WireMessage> result = new CompletableFuture<>(); requests.add(new Pending(type, data, result)); return result;
            }
            return CompletableFuture.completedFuture(message(type, Map.of("success", "true", "available", "10000", "memberCount", "0", "chatCount", "0")));
        }
        @Override public CompletableFuture<Void> connect() { state = ConnectionState.CONNECTED; return CompletableFuture.completedFuture(null); }
        @Override public void disconnect() { state = ConnectionState.DISCONNECTED; }
        @Override public boolean isConnected() { return state == ConnectionState.CONNECTED; }
        @Override public ConnectionState getState() { return state; }
        @Override public String getEndpoint() { return "ws://test"; }
        @Override public void setEndpoint(String endpoint) { }
        @Override public CompletableFuture<WireMessage> ping() { return sendRequest(MessageType.PING, Map.of()); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { }
        @Override public void addStateListener(ConnectionStateListener listener) { }
        @Override public void close() { state = ConnectionState.CLOSED; }
    }
}
