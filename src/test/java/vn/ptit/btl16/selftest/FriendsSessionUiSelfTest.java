package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import vn.ptit.btl16.client.fx.FriendsPane;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.network.*;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.common.protocol.*;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class FriendsSessionUiSelfTest {
    private record Pending(MessageType type, Map<String, String> data, CompletableFuture<WireMessage> future) {
        void reply(Map<String, String> data) { var values = new HashMap<>(data); values.put("success", "true"); future.complete(WireMessage.response(type, null, 1, values)); }
    }
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var network = new Network(); var model = new ClientAppModel();
        var pane = fx(() -> { identity(model, 1, "old-session"); var view = new FriendsPane(network, model); var stage = new Stage(); stage.setScene(new Scene(new StackPane(view), 500, 650)); stage.show(); view.tick(); return view; });
        try {
            var oldList = network.take(MessageType.GET_FRIENDS);
            fx(() -> { model.clearIdentity(); identity(model, 3, "new-session"); pane.tick(); return null; });
            var newList = network.take(MessageType.GET_FRIENDS);
            oldList.reply(page(2)); drain();
            TestSupport.check(fx(() -> ((javafx.scene.layout.VBox) field(pane, "peopleRows")).getChildren().isEmpty()), "late previous-account response does not enter the new user's directory");
            newList.reply(page(4)); drain();
            TestSupport.check(fx(() -> pane.lookup("#friend-person-4") != null && pane.lookup("#friend-person-2") == null), "new identity sees only its own friend list");
            fx(() -> { pane.showPerson(4, "Friend"); return null; });
            // showPerson refreshes the directory and looks up the clicked user separately.
            network.take(MessageType.GET_FRIENDS).reply(page(4));
            var lookup = network.take(MessageType.GET_FRIENDS); var target = new HashMap<>(page(4));
            person(target, "target.", 4); lookup.reply(target); drain();
            TestSupport.check(network.pending.isEmpty(), "viewing a friend does not automatically open private history");
            fx(() -> { ((Button) pane.lookup("#friend-message-4")).fire(); return null; });
            network.take(MessageType.GET_PRIVATE_MESSAGES).reply(Map.of("count", "0", "hasMore", "false")); drain();
            network.replyAll(); drain();
            fx(() -> { ((TextArea) pane.lookup("#private-chat-input")).setText("Tin cần gửi lại"); ((Button) pane.lookup("#private-chat-send")).fire(); return null; });
            var uncertain = network.take(MessageType.SEND_PRIVATE_MESSAGE);
            uncertain.future.completeExceptionally(new TimeoutException("Simulated lost response")); drain();
            fx(() -> { ((Button) pane.lookup("#private-chat-send")).fire(); return null; });
            var retry = network.take(MessageType.SEND_PRIVATE_MESSAGE);
            TestSupport.equals(uncertain.data.get("nonce"), retry.data.get("nonce"), "uncertain private send retries with the same nonce");
            retry.reply(Map.of("id", "8", "senderId", "3", "recipientId", "4", "body", "Tin cần gửi lại", "sentAt", Instant.now().toString())); drain();
            TestSupport.check(fx(() -> ((TextArea) pane.lookup("#private-chat-input")).getText().isEmpty()), "confirmed send clears the composer");
            // The successful initial history schedules a list refresh, and send schedules incremental history.
            network.replyAll(); drain();
            fx(() -> { network.connected = false; pane.tick(); return null; });
            TestSupport.check(fx(() -> pane.button().isDisabled() && ((TextArea) pane.lookup("#private-chat-input")).isDisabled()), "disconnect immediately disables private actions");
            fx(() -> { network.connected = true; pane.tick(); return null; });
            var lateList = network.take(MessageType.GET_FRIENDS); var lateHistory = network.take(MessageType.GET_PRIVATE_MESSAGES);
            fx(() -> { model.clearIdentity(); pane.tick(); return null; });
            lateList.reply(page(4)); lateHistory.reply(Map.of("count", "1", "message.0.id", "9", "message.0.senderId", "4", "message.0.body", "Private old message", "message.0.sentAt", Instant.now().toString())); drain();
            TestSupport.check(!fx(pane::isVisible) && fx(() -> ((javafx.scene.layout.VBox) field(pane, "messageRows")).getChildren().isEmpty()), "late reconnect replies cannot restore private content after logout");
            System.out.println("[PASS] FriendsSessionUiSelfTest: account isolation, late-response guards, same-nonce retries and disconnect/logout cleanup");
        } finally { fx(() -> { pane.close(); new ArrayList<>(javafx.stage.Window.getWindows()).forEach(javafx.stage.Window::hide); return null; }); Platform.exit(); }
    }
    private static void identity(ClientAppModel model, long id, String token) { model.applyIdentity(new ApiResponse(WireMessage.response(MessageType.LOGIN_RESULT, null, 1, Map.of("userId", Long.toString(id), "username", "user" + id, "displayName", "User " + id, "sessionToken", token, "success", "true")))); }
    private static Map<String, String> page(long id) { var data = new HashMap<String, String>(); data.putAll(Map.of("count", "1", "friendCount", "1", "requestCount", "0", "unreadCount", "0")); person(data, "person.0.", id); return data; }
    private static void person(Map<String, String> data, String prefix, long id) { data.put(prefix + "userId", Long.toString(id)); data.put(prefix + "displayName", "Friend " + id); data.put(prefix + "username", "friend" + id); data.put(prefix + "state", "FRIEND"); data.put(prefix + "online", "true"); data.put(prefix + "unread", "0"); }
    private static Object field(Object object, String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static <T> T fx(Callable<T> action) throws Exception { var future = new CompletableFuture<T>(); Platform.runLater(() -> { try { future.complete(action.call()); } catch (Throwable error) { future.completeExceptionally(error); } }); return future.get(15, TimeUnit.SECONDS); }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }
    private static final class Network implements ClientTransport {
        boolean connected = true; final BlockingQueue<Pending> pending = new LinkedBlockingQueue<>();
        Pending take(MessageType type) throws Exception { var request = pending.poll(5, TimeUnit.SECONDS); TestSupport.check(request != null && request.type == type, "expected request " + type + ", got " + (request == null ? "none" : request.type)); return request; }
        void replyAll() { Pending request; while ((request = pending.poll()) != null) { request.reply(request.type == MessageType.GET_FRIENDS ? page(4) : Map.of("count", "0", "hasMore", "false")); } }
        public CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) { var future = new CompletableFuture<WireMessage>(); pending.add(new Pending(type, data, future)); return future; }
        public CompletableFuture<Void> connect() { connected = true; return CompletableFuture.completedFuture(null); }
        public void disconnect() { connected = false; }
        public boolean isConnected() { return connected; }
        public ConnectionState getState() { return connected ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED; }
        public String getEndpoint() { return "test"; }
        public void setEndpoint(String value) { }
        public CompletableFuture<WireMessage> ping() { return sendRequest(MessageType.PING, Map.of()); }
        public void addEventListener(Consumer<WireMessage> listener) { }
        public void addStateListener(ConnectionStateListener listener) { }
        public void close() { connected = false; }
    }
}
