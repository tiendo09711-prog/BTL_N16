package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.social.FriendsStore;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class FriendsSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        Path directory = Files.createTempDirectory("btl16-friends-").toRealPath(), settings = directory.resolve("server.properties");
        Files.writeString(settings, "server.tcp.enabled=false\nserver.websocket.bindAddress=127.0.0.1\nserver.websocket.port=0\nmedia.port=0\n"
                + "db.driver=org.h2.Driver\ndb.name=friends\ndb.user=sa\ndb.password=\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", settings.toString()); System.setProperty("btl16.dataDir", directory.toString());
        long aliceId, bobId, carolId; String bobCode;
        try {
            var config = ServerConfig.loadDefault();
            try (var server = ServerApplication.create(config)) {
                server.start(); String endpoint = "ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws";
                try (var alice = client(endpoint); var bob = client(endpoint); var carol = client(endpoint); var guest = client(endpoint)) {
                    aliceId = register(alice, "friends_alice"); bobId = register(bob, "friends_bob", "Nguyễn Minh Anh"); carolId = register(carol, "friends_carol", "Nguyễn Minh Anh");
                    var a = new FriendsApi(alice); var b = new FriendsApi(bob); var c = new FriendsApi(carol);
                    error(ErrorCode.AUTH_REQUIRED, new FriendsApi(guest).list());
                    error(ErrorCode.AUTH_REQUIRED, new FriendsApi(guest).send(aliceId, "unauthorized", nonce()));
                    error(ErrorCode.AUCTION_FORBIDDEN, a.send(bobId, "Chưa kết bạn", nonce()));
                    error(ErrorCode.AUCTION_FORBIDDEN, a.history(bobId, 0, 0, true));
                    error(ErrorCode.VALIDATION_ERROR, a.add(aliceId)); error(ErrorCode.VALIDATION_ERROR, a.lookup("missing_user"));
                    bobCode = ok(new AccountApi(bob).getProfile()).get("friendCode");
                    String carolCode = ok(new AccountApi(carol).getProfile()).get("friendCode");
                    TestSupport.check(bobCode.matches("[0-9]{4}"), "accounts expose a persistent four-digit friend code");
                    TestSupport.check(!bobCode.equals(carolCode), "duplicate display names receive distinct codes");
                    error(ErrorCode.VALIDATION_ERROR, a.lookup("friends_bob"));
                    error(ErrorCode.VALIDATION_ERROR, a.lookup("friends_bob", "123"));
                    error(ErrorCode.VALIDATION_ERROR, a.lookup("wrong_name", bobCode));
                    var publicUser = ok(a.lookup("Nguyễn Minh Anh", "#" + bobCode));
                    TestSupport.equals(Long.toString(bobId), publicUser.get("target.userId"), "search resolves display name together with its code");
                    TestSupport.equals(Long.toString(carolId), ok(a.lookup("Nguyễn Minh Anh", carolCode)).get("target.userId"), "the second code selects the other identically named account");
                    TestSupport.check(publicUser.getWireMessage().getData().keySet().stream().noneMatch(key -> key.contains("email") || key.contains("phone") || key.contains("password") || key.contains("wallet")), "friend lookup exposes only public identity");
                    ok(new AccountApi(bob).logout());
                    TestSupport.equals("false", ok(a.lookup("Nguyễn Minh Anh#" + bobCode)).get("target.online"), "offline accounts can still be found by name and code");
                    ok(a.add(bobId)); ok(a.add(bobId));
                    ok(new AccountApi(bob).login("friends_bob", "Password123!"));
                    TestSupport.equals("1", ok(b.list()).get("requestCount"), "duplicate invitations are idempotent");
                    TestSupport.equals("OUTGOING", ok(a.lookup(bobId)).get("target.state"), "sender sees outgoing invitation");
                    error(ErrorCode.AUCTION_FORBIDDEN, a.respond(bobId, "accept"));
                    error(ErrorCode.AUCTION_FORBIDDEN, a.send(bobId, "Lời mời chưa được duyệt", nonce()));
                    ok(b.respond(aliceId, "decline")); TestSupport.equals("NONE", ok(a.lookup(bobId)).get("target.state"), "declining removes a pending invitation");
                    ok(a.add(bobId)); ok(a.respond(bobId, "cancel"));
                    TestSupport.equals("0", ok(b.list()).get("requestCount"), "sender can cancel a pending invitation");
                    ok(a.add(bobId)); ok(b.respond(aliceId, "accept")); ok(b.respond(aliceId, "accept"));
                    TestSupport.equals("1", ok(a.list()).get("friendCount"), "accepted friendship is mutual and acceptance is idempotent");
                    TestSupport.equals("FRIEND", ok(b.lookup(aliceId)).get("target.state"), "recipient sees accepted friendship");

                    BlockingQueue<WireMessage> delivered = new LinkedBlockingQueue<>(), leaked = new LinkedBlockingQueue<>();
                    bob.addEventListener(event -> { if (event.getType() == MessageType.PRIVATE_MESSAGE) { delivered.add(event); } });
                    carol.addEventListener(event -> { if (event.getType() == MessageType.PRIVATE_MESSAGE) { leaked.add(event); } });
                    String token = nonce(), body = "Xin chào bạn!\nGiá sản phẩm hôm nay thế nào? 🌷";
                    var sent = ok(a.send(bobId, body, token)); var duplicate = ok(a.send(bobId, body, token));
                    TestSupport.equals(sent.get("id"), duplicate.get("id"), "retry uses the original private message");
                    var event = delivered.poll(5, TimeUnit.SECONDS); TestSupport.check(event != null && event.get("body").equals(body), "private message delivered live to its recipient");
                    TestSupport.check(delivered.isEmpty() && leaked.isEmpty(), "duplicate send emits no second event and no other user receives the message");
                    error(ErrorCode.VALIDATION_ERROR, a.send(bobId, "Khác nội dung", token));
                    error(ErrorCode.VALIDATION_ERROR, a.send(bobId, "x".repeat(2001), nonce()));
                    error(ErrorCode.VALIDATION_ERROR, a.send(bobId, "  ", nonce()));
                    error(ErrorCode.AUCTION_FORBIDDEN, c.history(aliceId, 0, 0, true));
                    error(ErrorCode.AUCTION_FORBIDDEN, carol.sendRequest(MessageType.GET_PRIVATE_MESSAGES,
                            Map.of("targetId", Long.toString(bobId), "senderId", Long.toString(aliceId), "markRead", "true")).thenApply(ApiResponse::new));
                    TestSupport.equals("1", ok(b.list()).get("unreadCount"), "unread count persists until the recipient opens the chat");
                    TestSupport.equals(body, ok(b.history(aliceId, 0, 0, false)).get("message.0.body"), "history preserves Vietnamese, line breaks and Unicode");
                    TestSupport.equals("1", ok(b.list()).get("unreadCount"), "fetch without markRead leaves the count unchanged");
                    ok(b.history(aliceId, 0, 0, true)); TestSupport.equals("0", ok(b.list()).get("unreadCount"), "opening the chat marks only received messages read");
                    ok(b.send(aliceId, "Mình nhận được rồi!", nonce()));
                    ok(new AccountApi(bob).logout()); ok(a.send(bobId, "Tin nhắn khi bạn ngoại tuyến", nonce()));
                    ok(new AccountApi(bob).login("friends_bob", "Password123!"));
                    TestSupport.equals("1", ok(b.list()).get("unreadCount"), "offline messages become unread after logging back in");
                    TestSupport.check(ok(b.history(aliceId, 0, 0, false)).getWireMessage().getData().containsValue("Tin nhắn khi bạn ngoại tuyến"),
                            "offline message body is present when the recipient returns");

                    // Separate stores/connections exercise crossings and nonce retries under the database locks.
                    var factory = new JdbcConnectionFactory(config); var store1 = new FriendsStore(factory); var store2 = new FriendsStore(factory);
                    var pool = Executors.newFixedThreadPool(6);
                    try {
                        long firstId = aliceId, secondId = carolId;
                        var firstRequest = pool.submit(() -> store1.request(firstId, secondId));
                        var reverseRequest = pool.submit(() -> store2.request(secondId, firstId));
                        var pair = firstRequest.get(10, TimeUnit.SECONDS); reverseRequest.get(10, TimeUnit.SECONDS);
                        TestSupport.check(!store1.find(aliceId, carolId).accepted(), "crossed invitations still require explicit acceptance");
                        store2.respond(pair.requester() == aliceId ? carolId : aliceId, pair.requester(), "accept");
                        error(ErrorCode.VALIDATION_ERROR, a.send(carolId, body, token));
                        String retryNonce = nonce(); List<Future<FriendsStore.Message>> attempts = new ArrayList<>();
                        for (int index = 0; index < 12; index++) {
                            var store = index % 2 == 0 ? store1 : store2;
                            attempts.add(pool.submit(() -> store.send(firstId, secondId, retryNonce, "Một tin duy nhất")));
                        }
                        Set<Long> ids = new HashSet<>(); for (var attempt : attempts) { ids.add(attempt.get(10, TimeUnit.SECONDS).id()); }
                        TestSupport.equals(1, ids.size(), "concurrent retries create exactly one message across store instances");
                    } finally { pool.shutdownNow(); pool.awaitTermination(5, TimeUnit.SECONDS); }

                    for (int index = 0; index < 61; index++) { store1.send(aliceId, bobId, nonce(), "Lịch sử " + index); }
                    var latest = ok(b.history(aliceId, 0, 0, false)); TestSupport.equals("50", latest.get("count"), "latest history is paginated");
                    var previous = ok(b.history(aliceId, Long.parseLong(latest.get("message.0.id")), 0, false));
                    TestSupport.check(Integer.parseInt(previous.get("count")) > 0, "older page remains available");
                    long lastId = Long.parseLong(latest.get("message.49.id"));
                    ok(a.send(bobId, "Tin sau mốc", nonce()));
                    TestSupport.equals("1", ok(b.history(aliceId, 0, lastId, true)).get("count"), "incremental history starts strictly after the last received ID");
                    TestSupport.equals("0", ok(b.list()).get("unreadCount"), "reading through a cursor clears only that conversation");
                }
            }
            try (var server = ServerApplication.create(config)) {
                server.start();
                try (var alice = client("ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws")) {
                    ok(new AccountApi(alice).login("friends_alice", "Password123!")); var api = new FriendsApi(alice);
                    TestSupport.equals("2", ok(api.list()).get("friendCount"), "friendships persist across a server restart");
                    TestSupport.equals("50", ok(api.history(bobId, 0, 0, false)).get("count"), "message history survives restart");
                    TestSupport.equals(bobCode, ok(api.lookup(bobId)).get("target.friendCode"), "friend codes survive a server restart");
                }
            }
            var memory = new FriendsStore(null); memory.request(1, 2); memory.respond(2, 1, "accept");
            var message = memory.send(1, 2, nonce(), "In-memory fixture");
            TestSupport.equals(message.id(), memory.send(1, 2, message.nonce(), message.body()).id(), "isolated repository fixture uses the same retry semantics");
            System.out.println("[PASS] FriendsSelfTest: requests/accept/decline/cancel, authorization, private delivery, offline/unread history, pagination, cross-connection races and restart");
        } finally {
            restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData);
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    TestSupport.check(path.toAbsolutePath().normalize().startsWith(directory), "cleanup stays inside the isolated test directory"); Files.deleteIfExists(path);
                }
            }
        }
    }
    private static WebSocketClientTransport client(String endpoint) throws Exception { var client = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000); client.connect().get(10, TimeUnit.SECONDS); return client; }
    private static long register(WebSocketClientTransport client, String name) throws Exception {
        return register(client, name, name);
    }
    private static long register(WebSocketClientTransport client, String name, String displayName) throws Exception {
        ok(new AccountApi(client).register(name, "Password123!", displayName, "", "")); return Long.parseLong(ok(new AccountApi(client).login(name, "Password123!")).get("userId"));
    }
    private static String nonce() { return UUID.randomUUID().toString(); }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception { var response = future.get(20, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + " " + response.getMessageText()); return response; }
    private static void error(ErrorCode code, CompletableFuture<ApiResponse> future) throws Exception { var response = future.get(20, TimeUnit.SECONDS); TestSupport.check(!response.isSuccess(), "expected rejection " + code); TestSupport.equals(code.name(), response.getErrorCode(), "expected error code"); }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
