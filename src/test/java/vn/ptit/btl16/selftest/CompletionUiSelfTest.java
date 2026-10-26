package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.*;
import vn.ptit.btl16.client.fx.*;
import vn.ptit.btl16.client.model.*;
import vn.ptit.btl16.client.network.*;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class CompletionUiSelfTest {
    private record Pending(MessageType type, Map<String,String> data, CompletableFuture<WireMessage> future) {
        void reply(Map<String,String> values) { Map<String,String> result = new LinkedHashMap<>(values); result.put("success", "true"); future.complete(WireMessage.response(type, null, 1, result)); }
    }
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false)); System.setProperty("btl16.client.manualConnect", "true");
        try {
            var network = new Network(); var active = new AtomicBoolean(true);
            var inbox = fx(() -> { var pane = new PersonalNotificationsPane(null, network, active::get, (kind, room) -> { }); pane.show(); return pane; });
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(notifications("OUTBID")); drain();
            TestSupport.equals(1, fx(() -> table(inbox, "table").getItems().size()), "inbox renders stored notification once");
            fx(() -> { invoke(inbox, "load", new Class<?>[]{long.class}, Long.MAX_VALUE); return null; });
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(notifications("OUTBID")); drain();
            TestSupport.equals(1, fx(() -> table(inbox, "table").getItems().size()), "refresh/reconnect does not duplicate rows");
            fx(() -> { invoke(inbox, "load", new Class<?>[]{long.class}, Long.MAX_VALUE); return null; });
            active.set(false); network.take(MessageType.GET_MY_NOTIFICATIONS).reply(notifications("AUCTION_RESULT")); drain();
            TestSupport.check(!fx(() -> ((Stage) field(inbox, "stage")).isShowing()), "old session inbox closes before rendering");
            TestSupport.equals(0, fx(() -> table(inbox, "table").getItems().size()), "closed inbox clears private rows");
            active.set(true);
            var pagedInbox = fx(() -> { var pane = new PersonalNotificationsPane(null, network, active::get, (kind, room) -> { }); pane.show(); return pane; });
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(notifications("OUTBID")); drain();
            fx(() -> { invoke(pagedInbox, "load", new Class<?>[]{long.class}, 10L); return null; });
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(notifications("OUTBID")); drain(); active.set(false);
            fx(() -> { var poll = (javafx.animation.Timeline) field(pagedInbox, "poll"); poll.getKeyFrames().get(0).getOnFinished().handle(new javafx.event.ActionEvent()); return null; });
            TestSupport.check(!fx(() -> ((Stage) field(pagedInbox, "stage")).isShowing()), "idle second-page inbox closes on session expiry without fetching");
            TestSupport.equals(0, fx(() -> table(pagedInbox, "table").getItems().size()), "idle session invalidation clears inbox cache");
            var wallet = fx(() -> { var pane = new WalletPane(null, new WalletApi(network), 2, "test", () -> true); pane.show(); return pane; }); drain();
            fx(() -> { ((TextField) field(wallet, "roomFilter")).setText("1"); invoke(wallet, "loadEntries", new Class<?>[]{boolean.class}, false); ((TextField) field(wallet, "roomFilter")).setText("2"); invoke(wallet, "loadEntries", new Class<?>[]{boolean.class}, false); return null; });
            var old = network.take(MessageType.GET_WALLET_ENTRIES); var latest = network.take(MessageType.GET_WALLET_ENTRIES);
            TestSupport.equals("2", latest.data().get("auctionId"), "wallet sends selected session filter"); old.reply(ledger("OLD")); drain();
            TestSupport.equals(0, fx(() -> table(wallet, "entries").getItems().size()), "stale ledger response ignored"); latest.reply(ledger("BUY:2")); drain();
            TestSupport.equals(1, fx(() -> table(wallet, "entries").getItems().size()), "current filtered ledger rendered");
            screenshot(wallet, "target/completion-wallet.png"); fx(() -> { ((Stage) field(wallet, "stage")).close(); return null; });
            var model = new ClientAppModel(); model.applyIdentity(new ApiResponse(WireMessage.response(MessageType.LOGIN_RESULT, null, 1, Map.of("success", "true", "userId", "2", "username", "test", "sessionToken", "same-session"))));
            var controller = fx(() -> { var stage = new Stage(); var view = new FxClientController(stage, ClientConfig.loadDefault(), model, network, new AccountApi(network), new AuctionApi(network)); invoke(view, "buildScenes", new Class<?>[0]); stage.setScene((Scene) field(view, "mainScene")); stage.setWidth(1100); stage.setHeight(740); stage.show(); return view; });
            fx(() -> {
                var now = java.time.Instant.now(); List<ClientAuction> rooms = new ArrayList<>();
                for (int index = 1; index <= 75; index++) { rooms.add(new ClientAuction(index, index, "CODE", "Product " + index, "", 1, "host", java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, java.math.BigDecimal.valueOf(index), null, "", now, now.plusSeconds(index + 600), "OPEN", null, 1, 0)); }
                model.replaceAuctions(rooms, now); model.applySnapshot(rooms.get(74), List.of(), now, true);
                invoke(controller, "renderAuctions", new Class<?>[0]);
                @SuppressWarnings("unchecked") var visibility = (ComboBox<String>) field(controller, "lobbyVisibility"); visibility.setValue("PRIVATE");
                var selected = (ClientAuction) table(controller, "auctionTable").getSelectionModel().getSelectedItem();
                TestSupport.equals(75L, selected.getAuctionId(), "active live room survives filter excluding it and being beyond first page");
                TestSupport.check(((Label) field(controller, "detailName")).getText().contains("#75"), "active product/result details are not cleared by lobby paging");
                model.leaveJoinedAuction(); visibility.setValue("ALL"); invoke(controller, "renderAuctions", new Class<?>[0]);
                TestSupport.equals(30, table(controller, "auctionTable").getItems().size(), "leaving restores bounded lobby page"); return null;
            });
            fx(() -> { invoke(controller, "showCreateAuction", new Class<?>[0]); invoke(controller, "showCreateAuction", new Class<?>[0]); return null; });
            TestSupport.equals(1, network.count(MessageType.MY_PRODUCTS), "create guard prevents duplicate product form load");
            network.take(MessageType.MY_PRODUCTS).reply(product()); drain();
            fx(() -> { dialogButton(ButtonBar.ButtonData.OK_DONE).fire(); return null; }); drain();
            TestSupport.equals(0, network.count(MessageType.CREATE_AUCTION), "review step never submits before confirmation");
            fx(() -> { dialogButton(ButtonBar.ButtonData.YES).fire(); return null; }); drain();
            TestSupport.equals(1, network.count(MessageType.CREATE_AUCTION), "review confirmation sends exactly one request");
            fx(() -> { invoke(controller, "showCreateAuction", new Class<?>[0]); return null; });
            TestSupport.equals(0, network.count(MessageType.MY_PRODUCTS), "pending create prevents second dialog/request");
            fx(() -> { Stage stage = (Stage) field(controller, "stage"); stage.setWidth(940); stage.setHeight(650); return null; }); drain();
            screenshot(fx(() -> ((Stage) field(controller, "stage")).getScene().getRoot()), "target/completion-lobby-small.png");
            fx(() -> { controller.close(); return null; });
            System.out.println("[PASS] CompletionUiSelfTest: persisted inbox dedup/session gate, wallet filter stale callbacks, create review and duplicate guard, small lobby screenshot");
        } finally { fx(() -> { for (Window window : List.copyOf(Window.getWindows())) { if (window instanceof Stage stage) { stage.close(); } } return null; }); Platform.exit(); }
    }
    private static Map<String,String> product() { return Map.of("productCount", "1", "product.0.productId", "1", "product.0.ownerId", "2", "product.0.code", "CODE", "product.0.name", "Test product", "product.0.active", "true"); }
    private static Map<String,String> notifications(String kind) { return Map.of("count", "1", "hasMore", "false", "nextBeforeId", "1", "unreadCount", "1", "notification.0.id", "1", "notification.0.kind", kind, "notification.0.auctionId", "2", "notification.0.reference", "", "notification.0.createdAt", "2026-10-25T10:00:00Z", "notification.0.read", "false"); }
    private static Map<String,String> ledger(String reference) {
        var result = new LinkedHashMap<>(Map.of("userId", "2", "entryCount", "1", "hasMore", "false", "nextBeforeId", "1"));
        result.putAll(Map.of("entry.0.id", "1", "entry.0.type", "PURCHASE", "entry.0.amount", "-100", "entry.0.reservedDelta", "-100", "entry.0.balanceAfter", "900", "entry.0.reservedAfter", "0", "entry.0.reference", reference, "entry.0.createdAt", "2026-10-25T10:00:00Z")); return result;
    }
    private static Button dialogButton(ButtonBar.ButtonData data) {
        for (Window window : List.copyOf(Window.getWindows())) {
            if (!window.isShowing() || window.getScene() == null) { continue; }
            var root = window.getScene().getRoot(); DialogPane pane = root instanceof DialogPane dialog ? dialog : (DialogPane) root.lookup(".dialog-pane");
            if (pane != null) { for (var type : pane.getButtonTypes()) { if (type.getButtonData() == data) { return (Button) pane.lookupButton(type); } } }
        }
        throw new AssertionError("No dialog button " + data);
    }
    private static void screenshot(javafx.scene.Node node, String path) throws Exception { fx(() -> { node.applyCss(); if (node instanceof javafx.scene.Parent parent) { parent.layout(); } var image = node.snapshot(null, null); var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB); for (int row = 0; row < output.getHeight(); row++) { for (int column = 0; column < output.getWidth(); column++) { output.setRGB(column, row, image.getPixelReader().getArgb(column, row)); } } javax.imageio.ImageIO.write(output, "png", new java.io.File(path)); return null; }); }
    private static Object field(Object target, String name) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static TableView<?> table(Object target, String name) throws Exception { return (TableView<?>) field(target, name); }
    private static void invoke(Object target, String name, Class<?>[] types, Object... values) throws Exception { Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(target, values); }
    private static <Result> Result fx(Callable<Result> action) throws Exception { var result = new CompletableFuture<Result>(); Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } }); return result.get(15, TimeUnit.SECONDS); }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }
    private static final class Network implements ClientTransport {
        private final List<Pending> requests = new ArrayList<>();
        synchronized int count(MessageType type) { return (int) requests.stream().filter(value -> value.type() == type).count(); }
        synchronized Pending take(MessageType type) { var value = requests.stream().filter(item -> item.type() == type).findFirst().orElseThrow(() -> new AssertionError("Missing " + type)); requests.remove(value); return value; }
        @Override public synchronized CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String,String> data) {
            if (Set.of(MessageType.GET_MY_NOTIFICATIONS, MessageType.GET_WALLET_ENTRIES, MessageType.MY_PRODUCTS, MessageType.CREATE_AUCTION).contains(type)) { var future = new CompletableFuture<WireMessage>(); requests.add(new Pending(type, Map.copyOf(data), future)); return future; }
            return CompletableFuture.completedFuture(WireMessage.response(type, null, 1, Map.of("success", "true", "userId", "2", "balance", "1000", "reserved", "0", "available", "1000", "requestCount", "0", "entryCount", "0", "memberCount", "0", "chatCount", "0")));
        }
        @Override public CompletableFuture<Void> connect() { return CompletableFuture.completedFuture(null); }
        @Override public void disconnect() { }
        @Override public boolean isConnected() { return true; }
        @Override public ConnectionState getState() { return ConnectionState.CONNECTED; }
        @Override public String getEndpoint() { return "test"; }
        @Override public void setEndpoint(String endpoint) { }
        @Override public CompletableFuture<WireMessage> ping() { return sendRequest(MessageType.PING, Map.of()); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { }
        @Override public void addStateListener(ConnectionStateListener listener) { }
        @Override public void close() { }
    }
}
