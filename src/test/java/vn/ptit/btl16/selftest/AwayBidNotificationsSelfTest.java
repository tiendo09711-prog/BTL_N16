package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.repository.CloseAuctionCommit;
import vn.ptit.btl16.server.auction.repository.JdbcAuctionRepository;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real server, isolated database: only former bidders receive one price alert per absence. */
public final class AwayBidNotificationsSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        Path directory = Files.createTempDirectory("btl16-away-notifications-"), settings = directory.resolve("server.properties");
        Files.writeString(settings, "server.tcp.enabled=false\nserver.websocket.bindAddress=127.0.0.1\nserver.websocket.port=0\nmedia.port=0\n"
                + "db.driver=org.h2.Driver\ndb.name=away_notifications\ndb.user=sa\ndb.password=\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", settings.toString()); System.setProperty("btl16.dataDir", directory.toString());
        long room;
        try {
            var config = ServerConfig.loadDefault();
            try (var server = ServerApplication.create(config)) {
                server.start(); String endpoint = endpoint(server);
                try (var host = client(endpoint); var first = client(endpoint); var second = client(endpoint);
                     var third = client(endpoint); var spectator = client(endpoint)) {
                    register(host, "away_host"); register(first, "away_first"); register(second, "away_second");
                    register(third, "away_third"); register(spectator, "away_spectator"); TestMedia.fundWallets(server.wallets());
                    var hostApi = new AuctionApi(host); var firstApi = new AuctionApi(first); var secondApi = new AuctionApi(second); var thirdApi = new AuctionApi(third);
                    long product = Long.parseLong(ok(TestMedia.create(hostApi, "AWAY", "Sản phẩm kiểm tra thông báo", TestMedia.DESCRIPTION)).get("productId"));
                    room = Long.parseLong(ok(hostApi.createAuction(product, money(100), money(10), 10, "PRIVATE", "room1234", true)).get("auctionId"));
                    for (var client : List.of(first, second, third, spectator)) { ok(new AuctionApi(client).join(room, "room1234")); }
                    TestSupport.check(!new AuctionApi(spectator).bid(room, money(100)).get(10, TimeUnit.SECONDS).isSuccess(), "rejected bids do not subscribe a spectator");
                    ok(new AuctionApi(spectator).leave(room));
                    ok(firstApi.bid(room, money(110))); ok(firstApi.leave(room));
                    ok(secondApi.bid(room, money(120)));
                    var alert = alerts(first, room, 1); TestSupport.equals("PRICE_CHANGED", alert.get("notification.0.kind"), "away bidder gets the generic price-change notice instead of an outbid notice");
                    String notification = alert.get("notification.0.id"); alerts(spectator, room, 0);
                    ok(thirdApi.bid(room, money(130))); ok(secondApi.bid(room, money(140))); alerts(first, room, 1);
                    request(first, MessageType.READ_MY_NOTIFICATION, Map.of("notificationId", notification, "read", "true"));
                    ok(thirdApi.bid(room, money(150))); TestSupport.equals("true", alerts(first, room, 1).get("notification.0.read"), "reading the notice does not rearm it");
                    request(first, MessageType.DELETE_MY_NOTIFICATION, Map.of("notificationId", notification));
                    ok(secondApi.bid(room, money(160))); alerts(first, room, 0);
                    // The same session keeps its private-room access; joining from the notification needs no lobby search.
                    ok(firstApi.join(room)); ok(thirdApi.bid(room, money(170))); alerts(first, room, 0);
                    ok(firstApi.leave(room)); ok(secondApi.bid(room, money(180)));
                    var rearmed = alerts(first, room, 1); TestSupport.equals("PRICE_CHANGED", rearmed.get("notification.0.kind"), "returning and leaving again permits one new notice, even when the bidder is no longer the leader");
                    request(first, MessageType.DELETE_MY_NOTIFICATION, Map.of("notificationId", rearmed.get("notification.0.id")));
                }
            }
            try (var server = ServerApplication.create(config)) {
                server.start(); String endpoint = endpoint(server);
                try (var host = client(endpoint); var first = client(endpoint); var third = client(endpoint); var spectator = client(endpoint)) {
                    login(host, "away_host"); login(first, "away_first"); login(third, "away_third"); login(spectator, "away_spectator");
                    ok(new AuctionApi(third).join(room, "room1234")); ok(new AuctionApi(third).bid(room, money(190)));
                    alerts(first, room, 0); alerts(spectator, room, 0);
                    // Expire only this isolated fixture; live auctions with bids cannot be ended early by the host.
                    var factory = new JdbcConnectionFactory(config);
                    try (var connection = factory.openDatabase(); var update = connection.prepareStatement("UPDATE auctions SET end_time=? WHERE auction_id=?")) {
                        update.setTimestamp(1, java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1))); update.setLong(2, room); update.executeUpdate();
                    }
                    var repository = new JdbcAuctionRepository(factory); var current = repository.findAuctionById(room).orElseThrow();
                    repository.closeAuction(new CloseAuctionCommit(room, current.getCurrentWinnerId(), current.getCurrentWinnerUsername(),
                            current.getCurrentPrice(), current.getEndTime(), java.time.Instant.now())).orElseThrow();
                    alerts(spectator, room, 0);
                    TestSupport.equals("AUCTION_RESULT", alerts(first, room, 1).get("notification.0.kind"), "confirmed results remain available to actual bidders");
                }
            }
        } finally { restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData); }
        System.out.println("[PASS] AwayBidNotificationsSelfTest: spectators/rejected bids excluded, one notice per absence, read/delete/restart dedup, return/rearm, private rooms and final results");
    }
    private static ApiResponse alerts(WebSocketClientTransport client, long room, int expected) throws Exception {
        var page = request(client, MessageType.GET_MY_NOTIFICATIONS, Map.of());
        int found = 0; for (int i = 0; i < Integer.parseInt(page.get("count")); i++) { if (Long.toString(room).equals(page.get("notification." + i + ".auctionId"))) { found++; } }
        TestSupport.equals(expected, found, "room notification count"); return page;
    }
    private static String endpoint(ServerApplication server) { return "ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws"; }
    private static WebSocketClientTransport client(String endpoint) throws Exception { var client = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000); client.connect().get(10, TimeUnit.SECONDS); return client; }
    private static void register(WebSocketClientTransport client, String name) throws Exception { ok(new AccountApi(client).register(name, "Password123!", name, "", "")); login(client, name); }
    private static void login(WebSocketClientTransport client, String name) throws Exception { ok(new AccountApi(client).login(name, "Password123!")); }
    private static BigDecimal money(long value) { return BigDecimal.valueOf(value); }
    private static ApiResponse request(WebSocketClientTransport client, MessageType type, Map<String, String> values) throws Exception { return ok(client.sendRequest(type, values).thenApply(ApiResponse::new)); }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception { var response = future.get(20, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + " " + response.getMessageText()); return response; }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
