package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.*;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.repository.JdbcAuctionRepository;
import vn.ptit.btl16.server.notification.PersonalNotificationStore;
import vn.ptit.btl16.server.wallet.WalletTransactions;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class CompletionSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        lobby();
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        Path directory = Files.createTempDirectory("btl16-completion-"), settings = directory.resolve("server.properties");
        Files.writeString(settings, "server.tcp.enabled=false\nserver.websocket.bindAddress=127.0.0.1\nserver.websocket.enabled=true\nserver.websocket.port=0\nmedia.port=0\n"
                + "db.driver=org.h2.Driver\ndb.name=completion\ndb.user=sa\ndb.password=\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", settings.toString()); System.setProperty("btl16.dataDir", directory.toString());
        long firstId, notification;
        try {
            var config = ServerConfig.loadDefault(); var factory = new JdbcConnectionFactory(config);
            try (var server = ServerApplication.create(config)) {
                server.start(); String endpoint = "ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws";
                try (var host = client(endpoint); var first = client(endpoint); var second = client(endpoint); var stranger = client(endpoint)) {
                    register(host, "finish_host"); firstId = register(first, "finish_first"); long secondId = register(second, "finish_second");
                    register(stranger, "finish_stranger"); TestMedia.fundWallets(server.wallets());
                    var hostApi = new AuctionApi(host); var firstApi = new AuctionApi(first); var secondApi = new AuctionApi(second);
                    long product = Long.parseLong(ok(TestMedia.create(hostApi, "COMPLETE", "Completion test", TestMedia.DESCRIPTION)).get("productId"));
                    long room = Long.parseLong(ok(hostApi.createAuction(product, amount("100"), amount("10"), 5, "PRIVATE", "room1234", true)).get("auctionId"));
                    ok(firstApi.join(room, "room1234")); ok(secondApi.join(room, "room1234"));
                    ok(firstApi.bid(room, amount("110"))); ok(secondApi.bid(room, amount("120")));
                    var notifications = request(first, MessageType.GET_MY_NOTIFICATIONS, Map.of("userId", Long.toString(secondId)));
                    TestSupport.equals("OUTBID", notifications.get("notification.0.kind"), "outbid persisted, not runtime only");
                    TestSupport.check(!notifications.getWireMessage().getData().toString().contains("finish_"), "anonymous inbox has no account names");
                    notification = Long.parseLong(notifications.get("notification.0.id"));
                    var denied = stranger.sendRequest(MessageType.READ_MY_NOTIFICATION, Map.of("notificationId", Long.toString(notification), "read", "true")).get(10, TimeUnit.SECONDS);
                    TestSupport.equals("AUCTION_FORBIDDEN", denied.get("errorCode"), "notification ownership checked on server");
                    for (int retry = 0; retry < 2; retry++) { request(first, MessageType.READ_MY_NOTIFICATION, Map.of("notificationId", Long.toString(notification), "read", "true")); }
                    var entries = request(first, MessageType.GET_WALLET_ENTRIES, Map.of("auctionId", Long.toString(room), "type", "RELEASE", "userId", Long.toString(secondId)));
                    TestSupport.equals(Long.toString(firstId), entries.get("userId"), "filtered ledger owner comes from session");
                    TestSupport.equals("1", entries.get("entryCount"), "room release belongs to previous leader");
                    String topup = UUID.randomUUID().toString(); ok(new WalletApi(first).submit(topup, amount("1000")));
                    server.wallets().review(topup, false, "Sai noi dung demo"); server.wallets().review(topup, true);
                    TestSupport.equals("Sai noi dung demo", server.wallets().requests(firstId, false).stream().filter(row -> row.id().equals(topup)).findFirst().orElseThrow().rejectionReason(), "reason persists across repeated review");
                    try (var connection = factory.openDatabase()) {
                        connection.setAutoCommit(false);
                        for (int index = 0; index < 65; index++) { WalletTransactions.change(connection, firstId, BigDecimal.ZERO, BigDecimal.ZERO, "TOPUP", "FILTER_TEST:" + index, Instant.now()); }
                        PersonalNotificationStore.save(connection, firstId, "TOPUP:" + topup, "TOPUP_REJECTED", null, topup, Instant.now()); connection.commit();
                    }
                    entries = request(first, MessageType.GET_WALLET_ENTRIES, Map.of("auctionId", Long.toString(room), "type", "RELEASE"));
                    TestSupport.equals("1", entries.get("entryCount"), "server filters before limiting even beyond newest page");
                    var page = request(first, MessageType.GET_WALLET_ENTRIES, Map.of("type", "TOPUP"));
                    TestSupport.equals("50", page.get("entryCount"), "wallet page bound"); TestSupport.equals("true", page.get("hasMore"), "wallet keyset has next");
                    var next = request(first, MessageType.GET_WALLET_ENTRIES, Map.of("type", "TOPUP", "beforeId", page.get("nextBeforeId")));
                    TestSupport.equals("16", next.get("entryCount"), "next wallet page filtered without loss");
                    TestSupport.equals("0", request(first, MessageType.GET_WALLET_ENTRIES, Map.of("from", Instant.now().plusSeconds(60).toString())).get("entryCount"), "time filter on server");
                    var events = new JdbcAuctionRepository(factory).operationEvents(room, "BID_ACCEPTED", Instant.now().minusSeconds(120), Instant.now().plusSeconds(120), Long.MAX_VALUE, 1);
                    TestSupport.equals(1, events.size(), "operator filters + pagination");
                    TestSupport.equals(Set.of("id", "auctionId", "type", "time", "amount"), events.get(0).keySet(), "operator log omits all sensitive payload and identities");
                    long emptyProduct = Long.parseLong(ok(TestMedia.create(hostApi, "EMPTY_COMPLETE", "Empty test", TestMedia.DESCRIPTION)).get("productId"));
                    long empty = Long.parseLong(ok(hostApi.createAuction(emptyProduct, amount("100"), amount("10"), 5)).get("auctionId"));
                    ok(hostApi.endAuction(empty));
                    TestSupport.equals("AUCTION_NOT_OPEN", hostApi.endAuction(empty).get(10, TimeUnit.SECONDS).getErrorCode(), "repeated close is rejected without duplicate result");
                    TestSupport.check(request(host, MessageType.GET_MY_NOTIFICATIONS, Map.of()).getWireMessage().getData().containsValue("AUCTION_RESULT"), "confirmed result produces notification");
                    ok(new AccountApi(stranger).logout());
                    TestSupport.equals("AUTH_REQUIRED", stranger.sendRequest(MessageType.GET_MY_NOTIFICATIONS, Map.of()).get(10, TimeUnit.SECONDS).get("errorCode"), "expired session cannot read inbox");
                }
            }
            try (var restarted = ServerApplication.create(config)) {
                restarted.start(); try (var first = client("ws://127.0.0.1:" + restarted.getWebSocketBoundPort() + "/ws")) {
                    ok(new AccountApi(first).login("finish_first", "Password123!"));
                    var page = request(first, MessageType.GET_MY_NOTIFICATIONS, Map.of());
                    long found = 0; int count = Integer.parseInt(page.get("count"));
                    for (int index = 0; index < count; index++) { if (Long.toString(notification).equals(page.get("notification." + index + ".id"))) { found++; TestSupport.equals("true", page.get("notification." + index + ".read"), "read survives restart"); } }
                    TestSupport.equals(1L, found, "outbid not duplicated after restart"); TestSupport.equals(3, count, "topup source retries do not duplicate notification");
                }
            }
        } finally { restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData); }
        System.out.println("[PASS] CompletionSelfTest: global lobby paging, private anonymous inbox, ownership/restart/dedup, wallet filters/reason, local safe logs");
    }
    private static void lobby() {
        List<ClientAuction> auctions = new ArrayList<>(); Instant now = Instant.now();
        for (int index = 1; index <= 75; index++) { auctions.add(new ClientAuction(index, index, "CODE", "Product " + index, "", 1, "host", amount("1"), amount("1"), BigDecimal.valueOf(index), null, "", now, now.plusSeconds(index), "OPEN", null, 1, 0)); }
        var ordered = LobbyQuery.filter(auctions, 1, "ALL", "ALL", true, "ALL", "", "PRICE_DESC", now);
        TestSupport.equals(75L, LobbyQuery.page(ordered, 0, 30).get(0).getAuctionId(), "global sort before pagination");
        TestSupport.equals(45L, LobbyQuery.page(ordered, 1, 30).get(0).getAuctionId(), "second page correctly sorted");
        TestSupport.equals(0, LobbyQuery.filter(auctions, 2, "PRIVATE", "ANONYMOUS", false, "ALL", "", "ENDING", now).size(), "existing visibility and identity filters");
    }
    private static BigDecimal amount(String value) { return new BigDecimal(value); }
    private static WebSocketClientTransport client(String endpoint) throws Exception { var client = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000); client.connect().get(10, TimeUnit.SECONDS); return client; }
    private static long register(WebSocketClientTransport client, String name) throws Exception { var api = new AccountApi(client); ok(api.register(name, "Password123!", name, "", "")); return Long.parseLong(ok(api.login(name, "Password123!")).get("userId")); }
    private static ApiResponse request(WebSocketClientTransport client, MessageType type, Map<String, String> data) throws Exception { return ok(client.sendRequest(type, data).thenApply(ApiResponse::new)); }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception { var response = future.get(20, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + " " + response.getMessageText()); return response; }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
