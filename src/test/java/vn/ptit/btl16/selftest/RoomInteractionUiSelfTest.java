package vn.ptit.btl16.selftest;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.*;
import javafx.util.Duration;
import vn.ptit.btl16.client.fx.RoomSocialPane;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.*;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.TestUserRepository;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.repository.TestAuctionRepository;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class RoomInteractionUiSelfTest {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var config = ServerConfig.forTests(0, 3, 3, 1); var hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        try (var server = ServerApplication.createForTests(config, TestUserRepository.withDemoUsers(hasher), TestAuctionRepository.withDemoAuctions(config), hasher)) {
            server.start();
            try (var host = RoomInteractionSelfTest.client(server.getBoundPort()); var guest = RoomInteractionSelfTest.client(server.getBoundPort())) {
                ok(new AccountApi(host).login("demo", "demo123")); ok(new AccountApi(guest).login("alice", "alice123"));
                var hostAuctions = new AuctionApi(host); var guestAuctions = new AuctionApi(guest); var hostApi = new RoomSocialApi(host);
                long room = RoomInteractionSelfTest.create(hostAuctions, "UI_INTERACTION_ANON", true);
                var hostJoin = ok(hostAuctions.join(room)); var guestJoin = ok(guestAuctions.join(room)); long guestId = Long.parseLong(guestJoin.get("selfParticipantId"));
                var original = ok(new RoomSocialApi(guest).send(room, "Original quote", "ui-original-0001")); long chatId = Long.parseLong(original.get("chat.id"));
                TrackingTransport transport = new TrackingTransport(guest);
                RoomSocialPane hostPane = fx(() -> new RoomSocialPane(host)), pane = fx(() -> new RoomSocialPane(transport));
                Stage hostStage = fx(() -> stage(hostPane)), stage = fx(() -> stage(pane));
                Timeline timer = fx(() -> {
                    Timeline ticks = new Timeline(new KeyFrame(Duration.millis(150), event -> { hostPane.tick(); pane.tick(); })); ticks.setCycleCount(Animation.INDEFINITE); ticks.play(); return ticks;
                });
                host.addEventListener(event -> Platform.runLater(() -> route(hostPane, event)));
                transport.addEventListener(event -> Platform.runLater(() -> route(pane, event)));
                try {
                    fx(() -> { hostPane.setRoom(ClientWireParser.auction(hostJoin.getWireMessage().getData(), ""), 1); pane.setRoom(ClientWireParser.auction(guestJoin.getWireMessage().getData(), ""), 2); return null; });
                    await(() -> !field(pane, "loading", Boolean.class) && pane.lookup("#room-reply-" + chatId) != null, "history ready");
                    fx(() -> {
                        TestSupport.check(!pane.lookup("#room-announcement-edit").isVisible(), "guest cannot edit pin");
                        ((TitledPane) pane.lookup("#room-chat-members")).setExpanded(true); return null;
                    });
                    Platform.runLater(() -> ((Button) hostPane.lookup("#room-announcement-edit")).fire());
                    await(() -> Window.getWindows().stream().anyMatch(window -> window instanceof Stage dialog && dialog.getScene().getRoot().lookup("#room-announcement-body") != null), "pin editor");
                    fx(() -> {
                        Stage editor = (Stage) Window.getWindows().stream().filter(window -> window instanceof Stage dialog && dialog.getScene().getRoot().lookup("#room-announcement-body") != null).findFirst().orElseThrow();
                        ((TextArea) editor.getScene().getRoot().lookup("#room-announcement-body")).setText("Pin visible outside chat");
                        DialogPane dialog = (DialogPane) editor.getScene().getRoot(); ButtonType save = dialog.getButtonTypes().stream().filter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow(); ((Button) dialog.lookupButton(save)).fire(); return null;
                    });
                    await(() -> ((Label) pane.lookup("#room-announcement")).getText().contains("Pin visible outside chat"), "pin sync while member tab selected");
                    var incoming = ok(hostApi.send(room, "Unread while another tab", "ui-hidden-chat-001"));
                    await(() -> field(pane, "unreadCount", Integer.class) == 1, "hidden tab unread");
                    fx(() -> { pane.onMessage(incoming.getWireMessage().getData()); fieldSet(pane, "lastRefresh", 0L); pane.tick(); return null; });
                    await(() -> !field(pane, "loading", Boolean.class), "delta reload");
                    fx(() -> {
                        TestSupport.equals(1, field(pane, "unreadCount", Integer.class), "event and reload do not double count");
                        TestSupport.check(((Button) field(pane, "unread", Button.class)).getText().startsWith("1 "), "unread button shows new message count");
                        ((Button) field(pane, "unread", Button.class)).fire(); return null;
                    });
                    await(() -> field(pane, "unreadCount", Integer.class) == 0, "reading at bottom clears badge");
                    for (int index = 0; index < 3; index++) { Thread.sleep(800); ok(hostApi.send(room, "Long visible message " + index + " text ".repeat(40), "ui-scroll-message-" + index)); }
                    await(() -> field(pane, "messages", Map.class).size() == 5, "scroll history");
                    fx(() -> { field(pane, "scroll", ScrollPane.class).setVvalue(0); return null; });
                    Thread.sleep(800); ok(hostApi.send(room, "Unread while scrolled up", "ui-scrolled-chat-001"));
                    await(() -> field(pane, "unreadCount", Integer.class) == 1, "scrolled-up unread");
                    fx(() -> {
                        ((Button) field(pane, "unread", Button.class)).fire();
                        ((Button) pane.lookup("#room-reply-" + chatId)).fire();
                        TestSupport.check(pane.lookup("#room-chat-reply-preview").isVisible(), "reply preview shown");
                        TestSupport.check(field(pane, "replyPreview", Label.class).getText().contains("Original quote"), "short canonical quote");
                        ((Button) pane.lookup("#room-chat-cancel-reply")).fire(); TestSupport.equals(0L, field(pane, "replyToId", Long.class), "cancel reply");
                        ((Button) pane.lookup("#room-reply-" + chatId)).fire();
                        ((TextField) pane.lookup("#room-chat-input")).setText("Reply from UI"); ((Button) pane.lookup("#room-chat-send")).fire(); return null;
                    });
                    await(() -> field(pane, "retryNonce", String.class).isEmpty() && !field(pane, "sending", Boolean.class), "reply acknowledged");
                    TestSupport.check(ok(new RoomSocialApi(guest).social(room)).getWireMessage().getData().values().contains("Reply from UI"), "reply persisted by UI");
                    fx(() -> {
                        transport.holdType = MessageType.SET_ROOM_REACTION;
                        ((Button) pane.lookup("#room-reaction-" + chatId + "-LIKE")).fire();
                        TestSupport.check(field(pane, "mutating", Boolean.class), "reaction sending state");
                        TestSupport.check(((Button) pane.lookup("#room-reaction-" + chatId + "-LIKE")).getText().endsWith(" 0"), "no optimistic count"); return null;
                    });
                    String nonce = transport.nonces.get(transport.nonces.size() - 1);
                    transport.holdType = null; transport.held.completeExceptionally(new java.io.IOException("Lost acknowledgement"));
                    await(() -> pane.lookup("#room-interaction-retry").isVisible(), "failed reaction retry");
                    fx(() -> { ((Button) pane.lookup("#room-interaction-retry")).fire(); return null; });
                    await(() -> ((Button) pane.lookup("#room-reaction-" + chatId + "-LIKE")).getText().endsWith(" 1") && !field(pane, "mutating", Boolean.class), "confirmed reaction count");
                    TestSupport.equals(nonce, transport.nonces.get(transport.nonces.size() - 1), "retry preserves reaction nonce");
                    Thread.sleep(430); ok(hostApi.mute(room, guestId, 2, "UI spam reason", "ui-mute-expiry-001"));
                    await(() -> pane.lookup("#room-chat-input").isDisabled() && ((Label) pane.lookup("#room-chat-mute-status")).getText().contains("UI spam reason"), "mute reason/expiry shown");
                    fx(() -> { TestSupport.check(((Button) pane.lookup("#room-reaction-" + chatId + "-LIKE")).isDisabled(), "muted reaction disabled"); return null; });
                    await(() -> !pane.lookup("#room-chat-input").isDisabled(), "mute UI expires");
                    fx(() -> { ((TitledPane) hostPane.lookup("#room-chat-members")).setExpanded(true); return null; });
                    await(() -> hostPane.lookup("#room-mute-" + guestId) != null, "host mute by anonymous code");
                    Platform.runLater(() -> ((Button) hostPane.lookup("#room-mute-" + guestId)).fire());
                    await(() -> Window.getWindows().stream().anyMatch(window -> window instanceof Stage dialog && dialog.getScene().getRoot().lookup("#room-mute-reason") != null), "mute editor");
                    fx(() -> {
                        Stage editor = (Stage) Window.getWindows().stream().filter(window -> window instanceof Stage dialog && dialog.getScene().getRoot().lookup("#room-mute-reason") != null).findFirst().orElseThrow();
                        ((TextField) editor.getScene().getRoot().lookup("#room-mute-reason")).setText("Manual unmute");
                        ((Button) ((DialogPane) editor.getScene().getRoot()).lookupButton(ButtonType.OK)).fire(); return null;
                    });
                    await(() -> pane.lookup("#room-chat-input").isDisabled(), "second mute");
                    await(() -> ((Button) hostPane.lookup("#room-mute-" + guestId)).getText().startsWith("B\u1ecf"), "host unmute control");
                    Thread.sleep(430); fx(() -> { ((Button) hostPane.lookup("#room-mute-" + guestId)).fire(); return null; });
                    await(() -> !pane.lookup("#room-chat-input").isDisabled(), "unmute sync");
                    fx(() -> {
                        transport.online = false; pane.tick();
                        TestSupport.check(pane.lookup("#room-chat-send").isDisabled() && ((Button) pane.lookup("#room-reaction-" + chatId + "-LIKE")).isDisabled(), "offline cannot imply accepted actions");
                        transport.online = true; pane.tick(); return null;
                    });
                    ok(hostAuctions.endAuction(room));
                    await(() -> !field(pane, "serverCanInteract", Boolean.class) && !field(hostPane, "serverCanInteract", Boolean.class), "closed server state");
                    fx(() -> {
                        pane.tick(); TestSupport.check(pane.lookup("#room-chat-input").isDisabled() && pane.lookup("#room-reaction-" + chatId + "-LIKE").isDisabled(), "closed read only");
                        TestSupport.check(hostPane.lookup("#room-announcement-edit").isDisabled(), "closed host controls read only");
                        TestSupport.check(transport.profileRequests == 0, "anonymous operations never fetch profiles");
                        TestSupport.check(!labels(stage.getScene()).contains("Alice") && !labels(stage.getScene()).contains("alice"), "anonymous UI no real names");
                        stage.setWidth(520); stage.setHeight(600); stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                        pane.clear(); TestSupport.equals(0, field(pane, "unreadCount", Integer.class), "room reset clears unread"); return null;
                    });
                } finally { fx(() -> { timer.stop(); pane.clear(); hostPane.clear(); stage.close(); hostStage.close(); return null; }); }
            }
            System.out.println("[PASS] RoomInteractionUiSelfTest: host pin editor, hidden-tab/scroll unread, dedup delta, reply/cancel, reaction pending/error/retry, mute expiry/unmute, disconnect, close, compact anonymous UI");
        } finally { Platform.exit(); }
    }
    private static void route(RoomSocialPane pane, WireMessage event) { if (event.getType() == MessageType.ROOM_CHAT_MESSAGE) { pane.onMessage(event.getData()); } else if (event.getType() == MessageType.ROOM_INTERACTION_CHANGED) { pane.onInteraction(event.getData()); } }
    private static Stage stage(RoomSocialPane pane) { Stage stage = new Stage(); Scene scene = new Scene(pane, 700, 650); DesktopTheme.apply(scene); stage.setScene(scene); stage.show(); return stage; }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception { return RoomInteractionSelfTest.ok(future); }
    private static String labels(Scene scene) { return scene.getRoot().lookupAll(".label").stream().filter(node -> node instanceof Label).map(node -> ((Label) node).getText()).collect(java.util.stream.Collectors.joining(" ")); }
    private static void await(Callable<Boolean> condition, String label) throws Exception { long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15); while (System.nanoTime() < deadline) { if (fx(condition)) { return; } Thread.sleep(35); } throw new AssertionError("Timeout: " + label); }
    private static <T> T fx(Callable<T> action) throws Exception { CompletableFuture<T> result = new CompletableFuture<>(); Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } }); return result.get(20, TimeUnit.SECONDS); }
    @SuppressWarnings("unchecked") private static <T> T field(Object object, String name, Class<T> type) throws Exception { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(object); }
    private static void fieldSet(Object object, String name, Object value) throws Exception { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
    private static final class TrackingTransport implements ClientTransport {
        private final ClientTransport delegate; volatile boolean online = true; volatile MessageType holdType; volatile CompletableFuture<WireMessage> held;
        final List<String> nonces = new CopyOnWriteArrayList<>(); int profileRequests;
        TrackingTransport(ClientTransport delegate) { this.delegate = delegate; }
        @Override public CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) {
            if (type == MessageType.GET_ROOM_PROFILE) { profileRequests++; }
            if (type == MessageType.SET_ROOM_REACTION) { nonces.add(data.get("nonce")); }
            if (type == holdType) { held = new CompletableFuture<>(); return held; }
            return delegate.sendRequest(type, data);
        }
        @Override public boolean isConnected() { return online && delegate.isConnected(); }
        @Override public CompletableFuture<Void> connect() { return delegate.connect(); }
        @Override public void disconnect() { delegate.disconnect(); }
        @Override public ConnectionState getState() { return delegate.getState(); }
        @Override public String getEndpoint() { return delegate.getEndpoint(); }
        @Override public void setEndpoint(String endpoint) { delegate.setEndpoint(endpoint); }
        @Override public CompletableFuture<WireMessage> ping() { return delegate.ping(); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { delegate.addEventListener(event -> { if (online) { listener.accept(event); } }); }
        @Override public void addStateListener(ConnectionStateListener listener) { delegate.addStateListener(listener); }
        @Override public void close() { delegate.close(); }
    }
}
