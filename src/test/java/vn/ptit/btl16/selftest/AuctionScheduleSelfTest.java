package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.repository.JdbcAuctionRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class AuctionScheduleSelfTest {
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        var directory = Files.createTempDirectory("btl16-schedule-"); var settings = directory.resolve("server.properties");
        Files.writeString(settings, "server.tcp.enabled=false\nserver.websocket.bindAddress=127.0.0.1\nserver.websocket.enabled=true\nserver.websocket.port=0\nmedia.port=0\n"
                + "db.driver=org.h2.Driver\ndb.name=schedule\ndb.user=sa\ndb.password=\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", settings.toString()); System.setProperty("btl16.dataDir", directory.toString());
        long scheduledId; Instant savedStart = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS);
        try {
            var config = ServerConfig.loadDefault();
            try (var server = ServerApplication.create(config)) {
                server.start();
                try (var host = client(server); var buyer = client(server)) {
                    register(host, "schedule_host"); register(buyer, "schedule_buyer"); TestMedia.fundWallets(server.wallets());
                    var api = new AuctionApi(host); var guest = new AuctionApi(buyer);
                    long product = product(api, "SCHEDULE");
                    failure(api.createAuction(product, money("100"), money("10"), 5, "PRIVATE", "secret", true,
                            Instant.now().minusSeconds(1)), "VALIDATION_ERROR");
                    failure(host.sendRequest(MessageType.CREATE_AUCTION, Map.of("productId", Long.toString(product), "startPrice", "100",
                            "minBidIncrement", "10", "durationMinutes", "5", "startTime", "invalid-time")).thenApply(ApiResponse::new), "VALIDATION_ERROR");
                    var created = ok(api.createAuction(product, money("100"), money("10"), 5, "PRIVATE", "secret", true, savedStart));
                    scheduledId = Long.parseLong(created.get("auctionId"));
                    TestSupport.equals(savedStart, Instant.parse(created.get("startTime")), "server honors selected UTC start");
                    TestSupport.equals(savedStart.plusSeconds(300), Instant.parse(created.get("endTime")), "duration counted from scheduled start");
                    TestSupport.equals("true", created.get("anonymousGuests"), "scheduling preserves guest identity setting");
                    failure(guest.join(scheduledId, "secret"), "AUCTION_NOT_OPEN");
                    failure(guest.bid(scheduledId, money("110")), "NOT_IN_AUCTION_ROOM");
                    failure(api.createAuction(product, money("100"), money("10"), 1), "AUCTION_ALREADY_EXISTS");
                    var list = ClientWireParser.auctions(ok(guest.listAuctions()).getWireMessage().getData());
                    TestSupport.check(list.stream().anyMatch(room -> room.getAuctionId() == scheduledId && room.getStartTime().equals(savedStart)), "scheduled room is discoverable in lobby");
                    long immediateProduct = product(api, "IMMEDIATE"); Instant before = Instant.now();
                    var immediate = ok(api.createAuction(immediateProduct, money("100"), money("10"), 1));
                    TestSupport.check(!Instant.parse(immediate.get("startTime")).isBefore(before), "old clients still create immediate rooms");
                    ok(guest.join(Long.parseLong(immediate.get("auctionId"))));
                    long soonProduct = product(api, "SOON"); Instant soon = Instant.now().plusSeconds(3).truncatedTo(ChronoUnit.MILLIS);
                    long soonId = Long.parseLong(ok(api.createAuction(soonProduct, money("100"), money("10"), 1, "PUBLIC", "", false, soon)).get("auctionId"));
                    failure(guest.join(soonId), "AUCTION_NOT_OPEN");
                    ok(guest.placeDeposit(soonId, ""));
                    TestSupport.waitUntil(() -> !Instant.now().isBefore(soon), 6000, "scheduled start reached");
                    ok(guest.join(soonId)); ok(guest.bid(soonId, money("110")));
                    var stored = new JdbcAuctionRepository(new JdbcConnectionFactory(config)).findAuctionById(scheduledId).orElseThrow();
                    TestSupport.equals(savedStart, stored.getStartTime(), "chosen start persisted in database");
                    TestSupport.check(!stored.isOpenAt(Instant.now()), "future room is still upcoming after timer ticks");
                }
            }
            try (var restarted = ServerApplication.create(config)) {
                restarted.start();
                try (var host = client(restarted)) {
                    ok(new AccountApi(host).login("schedule_host", "Password123!"));
                    var restored = ClientWireParser.auctions(ok(new AuctionApi(host).listAuctions()).getWireMessage().getData()).stream()
                            .filter(room -> room.getAuctionId() == scheduledId).findFirst().orElseThrow();
                    TestSupport.equals(savedStart, restored.getStartTime(), "server restart preserves start");
                    TestSupport.equals(savedStart.plusSeconds(300), restored.getEndTime(), "server restart preserves full duration");
                }
            }
        } finally { restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData); }
        System.out.println("[PASS] AuctionScheduleSelfTest: future/past validation, lobby discovery, early join/bid gates, automatic opening, full duration, old clients and JDBC restart");
    }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static long product(AuctionApi api, String code) throws Exception { return Long.parseLong(ok(TestMedia.create(api, code, code, TestMedia.DESCRIPTION)).get("productId")); }
    private static WebSocketClientTransport client(ServerApplication server) throws Exception {
        var client = new WebSocketClientTransport("ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws", 2097152, 5000, 10000);
        client.connect().get(10, TimeUnit.SECONDS); return client;
    }
    private static void register(WebSocketClientTransport client, String name) throws Exception {
        var api = new AccountApi(client); ok(api.register(name, "Password123!", name, "", "")); ok(api.login(name, "Password123!"));
    }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception {
        var response = future.get(15, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + " " + response.getMessageText()); return response;
    }
    private static void failure(CompletableFuture<ApiResponse> future, String expected) throws Exception {
        var response = future.get(15, TimeUnit.SECONDS); TestSupport.check(!response.isSuccess(), "request must fail"); TestSupport.equals(expected, response.getErrorCode(), "rejected request error code");
    }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
