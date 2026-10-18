package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.util.ConnectionCode;
import vn.ptit.btl16.server.ServerApplication;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DesktopLanSelfTest {
    private DesktopLanSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        for (String endpoint : new String[]{"ws://192.168.1.17:8890/ws", "ws://10.0.0.3:65535/ws",
                "ws://127.0.0.1:1/ws"}) {
            TestSupport.equals(endpoint, ConnectionCode.decode(ConnectionCode.encode(endpoint)), "LAN code round trip");
        }
        TestSupport.equals("ws://example.org/custom", ConnectionCode.decode("ws://example.org/custom"), "URLs still work");
        for (String invalid : new String[]{"AUC-XYZ-8890", "AUC-C0A80111-0", "AUC-C0A80111-65536"}) {
            try {
                ConnectionCode.decode(invalid);
                throw new AssertionError("Invalid connection code accepted");
            } catch (IllegalArgumentException expected) {
                TestSupport.check(true, "invalid code rejected");
            }
        }
        Path directory = Files.createTempDirectory("btl16-desktop-test-");
        Path properties = directory.resolve("server.properties");
        Files.writeString(properties, """
                server.tcp.enabled=false
                server.websocket.port=0
                media.port=0
                db.driver=org.h2.Driver
                db.name=auction
                db.user=sa
                db.password=
                db.autoInitialize=true
                security.passwordIterations=10000
                auction.closedVisibilitySeconds=3600
                """, StandardCharsets.UTF_8);
        String previousConfig = System.getProperty("btl16.server.config");
        String previousDirectory = System.getProperty("btl16.dataDir");
        try {
            System.setProperty("btl16.server.config", properties.toString());
            System.setProperty("btl16.dataDir", directory.toString());
            ServerConfig config = ServerConfig.loadDefault();
            long auctionId;
            long productId;
            String winningUser;
            try (ServerApplication server = ServerApplication.create(config)) {
                server.start();
                String endpoint = "ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws";
                try (WebSocketClientTransport host = client(endpoint);
                     WebSocketClientTransport first = client(endpoint);
                     WebSocketClientTransport second = client(endpoint)) {
                    registerAndLogin(host, "host");
                    registerAndLogin(first, "buyer1");
                    registerAndLogin(second, "buyer2");
                    AuctionApi hostApi = new AuctionApi(host);
                    AuctionApi firstApi = new AuctionApi(first);
                    AuctionApi secondApi = new AuctionApi(second);
                    byte[] image;
                    try (var stream = DesktopLanSelfTest.class.getResourceAsStream("/ui/auction.png")) {
                        image = stream.readAllBytes();
                    }
                    ApiResponse product = ok(TestMedia.create(hostApi, "ITEM_01", "Study auction", "Network test", image,
                            "image/png", "auction.png"));
                    productId = Long.parseLong(product.get("productId"));
                    ApiResponse auction = ok(hostApi.createAuction(productId, new BigDecimal("100000"),
                            new BigDecimal("10000"), 5, "PRIVATE", "room1234"));
                    auctionId = Long.parseLong(auction.get("auctionId"));
                    TestSupport.check(!firstApi.join(auctionId, "wrong").get(10, TimeUnit.SECONDS).isSuccess(),
                            "private rooms reject wrong passwords");
                    ok(hostApi.join(auctionId, "room1234"));
                    ok(firstApi.join(auctionId, "room1234"));
                    ok(secondApi.join(auctionId, "room1234"));
                    TestSupport.check(!hostApi.bid(auctionId, new BigDecimal("110000")).get(10, TimeUnit.SECONDS).isSuccess(),
                            "host cannot bid on own room");
                    TestSupport.check(!firstApi.bid(auctionId, new BigDecimal("100001")).get(10, TimeUnit.SECONDS).isSuccess(),
                            "minimum increment enforced");
                    CountDownLatch updates = new CountDownLatch(2);
                    AtomicInteger eventCount = new AtomicInteger();
                    first.addEventListener(message -> {
                        if (message.getType() == MessageType.BID_UPDATE) {
                            updates.countDown();
                            eventCount.incrementAndGet();
                        }
                    });
                    second.addEventListener(message -> {
                        if (message.getType() == MessageType.BID_UPDATE) updates.countDown();
                    });
                    CompletableFuture<ApiResponse> bidOne = firstApi.bid(auctionId, new BigDecimal("110000"));
                    CompletableFuture<ApiResponse> bidTwo = secondApi.bid(auctionId, new BigDecimal("110000"));
                    ApiResponse resultOne = bidOne.get(15, TimeUnit.SECONDS);
                    ApiResponse resultTwo = bidTwo.get(15, TimeUnit.SECONDS);
                    TestSupport.equals(1, (resultOne.isSuccess() ? 1 : 0) + (resultTwo.isSuccess() ? 1 : 0),
                            "competing bids have exactly one winner");
                    winningUser = resultOne.isSuccess() ? "buyer1" : "buyer2";
                    TestSupport.check(updates.await(10, TimeUnit.SECONDS), "both clients receive realtime bid updates");
                    TestSupport.equals(1, eventCount.get(), "one bid event per accepted bid");
                    ApiResponse imageResponse = ok(firstApi.getProductImage(productId));
                    TestSupport.check(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(imageResponse.get("imageBase64")))) != null,
                            "normalized thumbnail survives file/JDBC storage");
                    ApiResponse history = ok(firstApi.history(auctionId));
                    TestSupport.equals(1, ClientWireParser.bids(history.getWireMessage().getData()).size(), "bid history persists");
                    TestSupport.check(!hostApi.endAuction(auctionId).get(10, TimeUnit.SECONDS).isSuccess(),
                            "host cannot end a room early after bids");
                    TestSupport.equals("Local / H2", server.stats().getRepositoryName(), "desktop uses embedded database");
                }
            }
            var factory = new vn.ptit.btl16.server.account.repository.JdbcConnectionFactory(config);
            try (var connection = factory.openDatabase();
                 var statement = connection.prepareStatement("UPDATE auctions SET end_time = ? WHERE auction_id = ?")) {
                statement.setTimestamp(1, java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)));
                statement.setLong(2, auctionId);
                statement.executeUpdate();
            }
            try (ServerApplication restarted = ServerApplication.create(config)) {
                restarted.start();
                TestSupport.waitUntil(() -> restarted.stats().getEndedAuctions() == 1, 5000,
                        "expired auctions close automatically after restart");
                try (WebSocketClientTransport host = client("ws://127.0.0.1:" + restarted.getWebSocketBoundPort() + "/ws")) {
                    ok(new AccountApi(host).login("host", "Password123!"));
                    AuctionApi api = new AuctionApi(host);
                    TestSupport.equals(1, ClientWireParser.products(ok(api.myProducts()).getWireMessage().getData()).size(),
                            "products persist after restart");
                    var saved = restarted.auctionSnapshots().stream().filter(item -> item.getAuctionId() == auctionId)
                            .findFirst().orElseThrow();
                    TestSupport.equals(winningUser, saved.getCurrentWinnerUsername(), "winner persists after restart");
                    TestSupport.equals(new BigDecimal("110000.00"), saved.getCurrentPrice(), "final price persists");
                    TestSupport.equals(1, ClientWireParser.bids(ok(api.history(auctionId)).getWireMessage().getData()).size(),
                            "history persists after restart");
                    TestSupport.check(Files.exists(directory.resolve("auction.mv.db")), "database is on disk");
                }
            }
        } finally {
            restore("btl16.server.config", previousConfig);
            restore("btl16.dataDir", previousDirectory);
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
        System.out.println("[PASS] Desktop LAN: connection codes, 3 WebSocket clients, private room, images, concurrent bids, restart persistence");
    }

    private static WebSocketClientTransport client(String endpoint) throws Exception {
        WebSocketClientTransport transport = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000);
        transport.connect().get(10, TimeUnit.SECONDS);
        return transport;
    }

    private static void registerAndLogin(WebSocketClientTransport transport, String username) throws Exception {
        AccountApi account = new AccountApi(transport);
        ok(account.register(username, "Password123!", username, "", ""));
        ok(account.login(username, "Password123!"));
    }

    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception {
        ApiResponse response = future.get(15, TimeUnit.SECONDS);
        TestSupport.check(response.isSuccess(), response.getErrorCode() + ": " + response.getMessageText());
        return response;
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }
}
