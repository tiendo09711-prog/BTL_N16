package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.ptit.btl16.client.fx.*;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.*;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.protocol.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class RoomProgressUiSelfTest {
    private record Pending(MessageType type, Map<String, String> data, CompletableFuture<WireMessage> future) { }
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try { journal(); history(); roster(); System.out.println("[PASS] RoomProgressUiSelfTest: separate bounded journal, reconnect deduplication/cursors, older pages, stale callbacks, paginated money table/mine and participant roles/kick reasons"); }
        finally { Platform.exit(); }
    }

    private static void journal() throws Exception {
        var network = new Network(); var pane = fx(() -> new RoomProgressPane(network));
        List<Map<String, String>> stats = new ArrayList<>();
        fx(() -> { pane.onStatistics(stats::add); pane.setRoom(1); return null; });
        network.complete(MessageType.GET_AUCTION_STATS, Map.of("totalBids", "999", "uniqueBidders", "22"));
        network.complete(MessageType.GET_ROOM_EVENTS, events(151, 200, true)); drain();
        TestSupport.equals(50, fx(() -> list(pane).getItems().size()), "first journal page");
        TestSupport.equals("999", stats.get(stats.size() - 1).get("totalBids"), "server stats not local event count");
        for (int start = 201; start <= 401; start += 50) {
            fx(() -> { pane.invalidateRequests(); pane.tick(); return null; });
            TestSupport.equals(Integer.toString(start - 1), network.peek(MessageType.GET_ROOM_EVENTS).data().get("afterId"), "resume from stable high watermark");
            network.complete(MessageType.GET_ROOM_EVENTS, events(start, start + 49, false));
            network.complete(MessageType.GET_AUCTION_STATS, Map.of("totalBids", "999")); drain();
        }
        TestSupport.equals(200, fx(() -> list(pane).getItems().size()), "journal storage capped at two hundred");
        fx(() -> { pane.invalidateRequests(); pane.tick(); return null; });
        network.complete(MessageType.GET_ROOM_EVENTS, events(401, 450, false)); network.complete(MessageType.GET_AUCTION_STATS, Map.of()); drain();
        TestSupport.equals(200, fx(() -> list(pane).getItems().size()), "replayed IDs cannot add duplicate entries");
        fx(() -> { ((Button) pane.lookup("#room-events-older")).fire(); return null; });
        TestSupport.equals("251", network.peek(MessageType.GET_ROOM_EVENTS).data().get("beforeId"), "older page keyset cursor");
        network.complete(MessageType.GET_ROOM_EVENTS, events(201, 250, true)); drain();
        TestSupport.equals(50, fx(() -> list(pane).getItems().size()), "older page replaces window instead of growing forever");
        network.connected = false; fx(() -> { pane.tick(); return null; });
        TestSupport.check(fx(() -> ((Label) pane.lookup("#room-events-status")).getText().contains("M\u1ea5t")), "offline log state");
        network.connected = true;
        fx(() -> { pane.tick(); ((Button) pane.lookup("#room-events-latest")).fire(); pane.clear(); pane.setRoom(2); return null; });
        network.complete(MessageType.GET_ROOM_EVENTS, events(900, 949, false)); drain();
        TestSupport.equals(0, fx(() -> list(pane).getItems().size()), "previous room response discarded");
        network.complete(MessageType.GET_ROOM_EVENTS, events(1000, 1002, false)); drain();
        TestSupport.equals(3, fx(() -> list(pane).getItems().size()), "new room independent log");
        fx(() -> { pane.clear(); return null; });
    }

    private static void history() throws Exception {
        var network = new Network(); var pane = fx(() -> new BidHistoryPane(network, 1, () -> true));
        Map<String, String> page = new LinkedHashMap<>(Map.of("bidCount", "2", "throughSequence", "99", "nextBeforeSequence", "98", "hasMore", "true"));
        for (int index = 0; index < 2; index++) {
            String prefix = "bid." + index + "."; page.put(prefix + "username", "Khach #1"); page.put(prefix + "amount", "1234.56");
            page.put(prefix + "increase", "10.01"); page.put(prefix + "createdAt", "2026-10-25T10:00:00Z");
        }
        network.complete(MessageType.GET_BID_HISTORY, page); drain();
        TestSupport.equals(4, fx(() -> table(pane).getColumns().size()), "time, user, amount, increase columns");
        TestSupport.equals("1.234,56 \u20ab", fx(() -> table(pane).getItems().get(0).get("amount")), "shared exact decimal money formatting");
        fx(() -> { ((Button) pane.lookup("#bid-history-older")).fire(); return null; });
        Pending older = network.peek(MessageType.GET_BID_HISTORY);
        TestSupport.equals("98", older.data().get("beforeSequence"), "history older cursor");
        TestSupport.equals("99", older.data().get("throughSequence"), "snapshot upper bound preserved");
        network.complete(MessageType.GET_BID_HISTORY, Map.of("bidCount", "0", "throughSequence", "99", "nextBeforeSequence", "0", "hasMore", "false")); drain();
        TestSupport.equals(0, fx(() -> table(pane).getItems().size()), "history page replaces records");
        TestSupport.check(fx(() -> ((Button) pane.lookup("#bid-history-older")).isDisabled()), "last page disables older");
        fx(() -> { ((CheckBox) pane.lookup("#bid-history-mine")).fire(); return null; });
        TestSupport.equals("true", network.peek(MessageType.GET_BID_HISTORY).data().get("mine"), "mine filter requested from server");
        TestSupport.check(!network.peek(MessageType.GET_BID_HISTORY).data().containsKey("throughSequence"), "filter resets page anchor");
        fx(() -> { pane.dispose(); return null; }); network.complete(MessageType.GET_BID_HISTORY, page); drain();
        TestSupport.equals(0, fx(() -> table(pane).getItems().size()), "closed history rejects late reply");
    }

    private static void roster() throws Exception {
        var network = new Network(); var pane = fx(() -> new RoomSocialPane(network));
        Map<String, String> room = new LinkedHashMap<>(Map.of("auctionId", "1", "hostUserId", "1", "status", "OPEN", "startTime", "2026-10-25T00:00:00Z", "endTime", "2027-01-01T00:00:00Z", "anonymousGuests", "true", "selfParticipantId", "1"));
        fx(() -> { pane.setRoom(ClientWireParser.auction(room, ""), 1); return null; });
        Map<String, String> data = new LinkedHashMap<>(Map.of("memberCount", "2", "memberTotal", "2", "chatCount", "0", "canChat", "true"));
        data.putAll(Map.of("member.0.participantId", "1", "member.0.name", "Chu phong", "member.0.host", "true", "member.0.self", "true", "member.0.kickReason", "HOST"));
        data.putAll(Map.of("member.1.participantId", "-1", "member.1.name", "Khach #1", "member.1.leading", "true", "member.1.canKick", "false", "member.1.kickReason", "HAS_VALID_BID"));
        network.complete(MessageType.GET_ROOM_SOCIAL, data); drain();
        var scene = fx(() -> { var sceneValue = new Scene(pane, 620, 480); DesktopTheme.apply(sceneValue); ((TitledPane) pane.lookup("#room-chat-members")).setExpanded(true); pane.applyCss(); pane.layout(); return sceneValue; });
        TestSupport.check(fx(() -> labels(scene).contains("B\u1ea1n") && labels(scene).contains("\u0110ang d\u1eabn \u0111\u1ea7u")), "self and leader badges");
        TestSupport.check(fx(() -> labels(scene).contains("bid h\u1ee3p l\u1ec7")), "cannot kick reason visible");
        TestSupport.check(fx(() -> pane.lookupAll(".button").stream().filter(node -> ((Button) node).getText().equals("M\u1eddi ra")).allMatch(javafx.scene.Node::isDisabled)), "valid bidder kick button disabled");
        TestSupport.equals("Trò chuyện (2)", fx(() -> ((Label) pane.lookup("#room-chat-title")).getText()), "conversation header includes participant count");
        TestSupport.check(fx(() -> pane.auctionContent().lookupAll(".scroll-pane").isEmpty()), "left auction content flows without nested viewports");
        fx(() -> { pane.clear(); return null; });
    }

    private static Map<String, String> events(int first, int last, boolean more) {
        Map<String, String> data = new LinkedHashMap<>(Map.of("eventCount", Integer.toString(last - first + 1), "hasMore", Boolean.toString(more)));
        for (int id = first; id <= last; id++) {
            String prefix = "event." + (id - first) + "."; data.put(prefix + "id", Integer.toString(id)); data.put(prefix + "type", "JOINED");
            data.put(prefix + "time", "2026-10-25T01:02:03Z"); data.put(prefix + "username", "Khach #1");
        }
        return data;
    }
    @SuppressWarnings("unchecked") private static TableView<Map<String, String>> table(BidHistoryPane pane) { return (TableView<Map<String, String>>) pane.lookup("#bid-history-table"); }
    private static ListView<?> list(RoomProgressPane pane) { return (ListView<?>) pane.lookup("#room-system-events"); }
    private static Object field(Object value, String key) throws Exception { Field field = value.getClass().getDeclaredField(key); field.setAccessible(true); return field.get(value); }
    private static String labels(Scene scene) { return scene.getRoot().lookupAll(".label").stream().map(node -> ((Label) node).getText()).collect(java.util.stream.Collectors.joining(" ")); }
    private static <Result> Result fx(Callable<Result> action) throws Exception {
        CompletableFuture<Result> result = new CompletableFuture<>(); Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } }); return result.get(15, TimeUnit.SECONDS);
    }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }
    private static final class Network implements ClientTransport {
        private final List<Pending> pending = new ArrayList<>(); private boolean connected = true;
        synchronized Pending peek(MessageType type) { return pending.stream().filter(request -> request.type() == type).findFirst().orElseThrow(() -> new AssertionError("Missing request " + type)); }
        void complete(MessageType type, Map<String, String> values) {
            Pending request; synchronized (this) { request = peek(type); pending.remove(request); }
            Map<String, String> data = new LinkedHashMap<>(values); data.put("success", "true");
            request.future().complete(WireMessage.response(type, null, 1, data));
        }
        @Override public synchronized CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) { var future = new CompletableFuture<WireMessage>(); pending.add(new Pending(type, data, future)); return future; }
        @Override public CompletableFuture<Void> connect() { connected = true; return CompletableFuture.completedFuture(null); }
        @Override public void disconnect() { connected = false; }
        @Override public boolean isConnected() { return connected; }
        @Override public ConnectionState getState() { return connected ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED; }
        @Override public String getEndpoint() { return "test"; }
        @Override public void setEndpoint(String endpoint) { }
        @Override public CompletableFuture<WireMessage> ping() { return sendRequest(MessageType.PING, Map.of()); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { }
        @Override public void addStateListener(ConnectionStateListener listener) { }
        @Override public void close() { connected = false; }
    }
}
