package vn.ptit.btl16.selftest;

import com.fasterxml.jackson.databind.JsonNode;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.client.service.ProductMediaApi;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.ServerApplication;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class AuctionJourneySelfTest {
    private AuctionJourneySelfTest() { }
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        runCase(false); runCase(true);
    }

    private static void runCase(boolean anonymous) throws Exception {
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        Path data = Files.createTempDirectory("btl16-journey-");
        Path settings = data.resolve("server.properties");
        Files.writeString(settings, "server.tcp.enabled=false\nserver.websocket.port=0\nmedia.port=0\n"
                + "db.driver=org.h2.Driver\ndb.name=auction\ndb.user=sa\ndb.password=\nsecurity.passwordIterations=10000\n"
                + "auction.antiSnipingWindowSeconds=0\nauction.closedVisibilitySeconds=3600\n");
        System.setProperty("btl16.server.config", settings.toString()); System.setProperty("btl16.dataDir", data.toString());
        try {
            ServerConfig config = ServerConfig.loadDefault();
            long productId, room;
            String winningUser, winningDisplay;
            try (ServerApplication server = ServerApplication.create(config)) {
                server.start();
                String endpoint = "ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws";
                try (var seller = client(endpoint); var first = client(endpoint); var second = client(endpoint); var viewer = client(endpoint)) {
                    ApiResponse identity = register(seller, "journey_host");
                    register(first, "journey_first"); ApiResponse secondIdentity = register(second, "journey_second"); register(viewer, "journey_viewer");
                    TestMedia.fundWallets(server.wallets());
                    AuctionApi host = new AuctionApi(seller), buyer = new AuctionApi(first), rival = new AuctionApi(second), observer = new AuctionApi(viewer);
                    long mediaPort = Long.parseLong(ok(host.mediaEndpoint()).get("mediaPort"));
                    ProductMediaApi media = new ProductMediaApi(URI.create("http://127.0.0.1:" + mediaPort + "/"), () -> identity.get("sessionToken"));
                    String draft = media.begin(0).get(10, TimeUnit.SECONDS);
                    Path photo = data.resolve("PHONE.HEIC");
                    try (var input = AuctionJourneySelfTest.class.getResourceAsStream("/media/rainbow.heic")) { Files.copy(input, photo); }
                    JsonNode upload = media.upload(draft, photo, "IMAGE", progress -> { }).get(30, TimeUnit.SECONDS);
                    JsonNode committed = media.commit(draft, "JOURNEY_PHONE", "Journey camera", TestMedia.DESCRIPTION,
                            List.of(upload.path("id").asText())).get(15, TimeUnit.SECONDS);
                    productId = committed.path("productId").asLong(); TestSupport.check(productId > 0, "HTTP product commit returns a real product");
                    TestSupport.equals(1, media.manifest(productId, 0).get(10, TimeUnit.SECONDS).path("media").size(), "uploaded HEIC attached to seller product");
                    error(ErrorCode.AUCTION_FORBIDDEN, rival.updateProduct(productId, "STOLEN", "Stolen", TestMedia.DESCRIPTION));
                    error(ErrorCode.PRODUCT_CODE_EXISTS, TestMedia.create(host, "JOURNEY_PHONE", "Duplicate", TestMedia.DESCRIPTION));
                    error(ErrorCode.INVALID_INCREMENT, host.createAuction(productId, money("100"), money("0"), 1));
                    ApiResponse created = ok(host.createAuction(productId, money("100"), money("10"), 1, "PRIVATE", "room1234", anonymous));
                    room = Long.parseLong(created.get("auctionId"));
                    error(ErrorCode.AUCTION_ALREADY_EXISTS, host.createAuction(productId, money("100"), money("10"), 1));
                    error(ErrorCode.PRODUCT_FROZEN, host.updateProduct(productId, "MODIFIED", "Changed", TestMedia.DESCRIPTION));
                    error(ErrorCode.ROOM_PASSWORD_REQUIRED, buyer.join(room));
                    error(ErrorCode.ROOM_PASSWORD_INVALID, buyer.join(room, "wrong"));
                    error(ErrorCode.ROOM_ACCESS_DENIED, buyer.history(room));
                    ok(host.join(room, "room1234")); var firstJoin = ok(buyer.join(room, "room1234")); ok(rival.join(room, "room1234")); var viewerJoin = ok(observer.join(room, "room1234"));
                    long firstCode = Long.parseLong(firstJoin.get("selfParticipantId")), viewerCode = Long.parseLong(viewerJoin.get("selfParticipantId"));
                    var hostSocial = new vn.ptit.btl16.client.service.RoomSocialApi(seller); var guestSocial = new vn.ptit.btl16.client.service.RoomSocialApi(first);
                    var chat = ok(guestSocial.send(room, "Mo ta san pham va gia duoc server xac nhan", "journey-chat-01"));
                    ok(hostSocial.send(room, "Tra loi theo tin that", "journey-reply-01", Long.parseLong(chat.get("chat.id"))));
                    if (anonymous) { error(ErrorCode.ROOM_ACCESS_DENIED, guestSocial.profile(room, firstCode)); }
                    else {
                        var profile = ok(guestSocial.profile(room, firstCode));
                        for (String key : List.of("email", "phone", "balance", "sessionToken")) { TestSupport.check(!profile.getWireMessage().getData().containsKey(key), "public profile excludes " + key); }
                    }
                    Thread.sleep(450); ok(hostSocial.mute(room, firstCode, 60, "Demo mute chat only", "journey-mute-01"));
                    error(ErrorCode.ROOM_ACCESS_DENIED, guestSocial.send(room, "Muted chat", "journey-muted-01"));
                    TestSupport.equals(1, ClientWireParser.auctions(ok(buyer.searchAuctions("ROOM_ID", "#" + room)).getWireMessage().getData()).size(), "search by room id");
                    error(ErrorCode.AUCTION_FORBIDDEN, host.bid(room, money("110")));
                    error(ErrorCode.AUCTION_FORBIDDEN, buyer.cancelAuction(room));
                    error(ErrorCode.BID_TOO_LOW, buyer.bid(room, money("100")));
                    CountDownLatch ended = new CountDownLatch(1), outbid = new CountDownLatch(1);
                    seller.addEventListener(event -> { if (event.getType() == MessageType.AUCTION_ENDED && Long.toString(room).equals(event.getOrDefault("auctionId", ""))) { ended.countDown(); } });
                    first.addEventListener(event -> { if (event.getType() == MessageType.OUTBID_NOTIFICATION) { outbid.countDown(); } });
                    ok(buyer.bid(room, money("110")));
                    error(ErrorCode.BID_ALREADY_LEADING, buyer.bid(room, money("120")));
                    error(ErrorCode.AUCTION_HAS_BIDS, host.endAuction(room));
                    error(ErrorCode.AUCTION_HAS_BIDS, host.cancelAuction(room));
                    error(ErrorCode.AUCTION_HAS_BIDS, host.extendAuction(room, 10));
                    error(ErrorCode.CANNOT_KICK_BIDDER, hostSocial.kick(room, firstCode));
                    ok(rival.bid(room, money("120")));
                    TestSupport.check(outbid.await(5, TimeUnit.SECONDS), "outbid event arrives on actual WebSocket");
                    error(ErrorCode.CANNOT_KICK_BIDDER, hostSocial.kick(room, firstCode));
                    ok(buyer.leave(room)); ok(buyer.join(room, "room1234")); ok(buyer.bid(room, money("130")));
                    ok(hostSocial.kick(room, viewerCode));
                    error(ErrorCode.USER_BLOCKED_FROM_AUCTION, observer.join(room, "room1234"));
                    error(ErrorCode.USER_BLOCKED_FROM_AUCTION, observer.bid(room, money("140")));
                    ok(buyer.leave(room));
                    CompletableFuture<ApiResponse> competingOne = buyer.bid(room, money("140"));
                    error(ErrorCode.NOT_IN_AUCTION_ROOM, competingOne);
                    ok(buyer.join(room, "room1234"));
                    ok(rival.bid(room, money("140")));
                    String secondToken = secondIdentity.get("sessionToken");
                    second.disconnect();
                    try (var reconnected = client(endpoint); var contender = client(endpoint)) {
                        ok(new AccountApi(reconnected).resumeSession(secondToken));
                        ok(new AuctionApi(reconnected).resync(room));
                        ok(new AuctionApi(reconnected).join(room, "room1234"));
                        register(contender, "journey_racer"); TestMedia.fundWallets(server.wallets()); AuctionApi racer = new AuctionApi(contender); ok(racer.join(room, "room1234"));
                        CompletableFuture<ApiResponse> samePriceOne = buyer.bid(room, money("150"));
                        CompletableFuture<ApiResponse> samePriceTwo = racer.bid(room, money("150"));
                        ApiResponse responseOne = samePriceOne.get(10, TimeUnit.SECONDS), responseTwo = samePriceTwo.get(10, TimeUnit.SECONDS);
                        TestSupport.equals(1, (responseOne.isSuccess() ? 1 : 0) + (responseTwo.isSuccess() ? 1 : 0), "competing bids at one price commit once");
                        winningUser = responseOne.isSuccess() ? "journey_first" : "journey_racer";
                        lifecycleWithoutBids(host);
                        checkStored(config, room, "OPEN");
                        System.out.println("[INFO] AuctionJourneySelfTest: waiting for the real one-minute auction deadline");
                        TestSupport.check(ended.await(75, TimeUnit.SECONDS), "real auction timer publishes the result");
                        var result = ClientWireParser.auction(ok(host.resync(room)).getWireMessage().getData(), "");
                        TestSupport.check(!result.isOpen(), "auction closes naturally");
                        winningDisplay = result.getCurrentWinnerUsername();
                        if (anonymous) { TestSupport.check(winningDisplay.startsWith("Kh\u00e1ch #"), "anonymous winner stays alias in raw result"); }
                        else { TestSupport.equals(winningUser, winningDisplay, "correct highest bidder wins"); }
                        TestSupport.check(money("150").compareTo(result.getCurrentPrice()) == 0, "winner pays their accepted bid");
                        error(ErrorCode.AUCTION_NOT_OPEN, buyer.bid(room, money("160")));
                        error(ErrorCode.PRODUCT_ALREADY_SOLD, host.createAuction(productId, money("100"), money("10"), 1));
                        checkStored(config, room, "ENDED");
                        TestSupport.equals(5, ClientWireParser.bids(ok(host.history(room)).getWireMessage().getData()).size(), "only accepted bids enter history");
                        var winner = responseOne.isSuccess() ? first : contender;
                        var receipt = ok(new vn.ptit.btl16.client.service.AuctionReceiptApi(winner).get(room));
                        TestSupport.equals("WINNER", receipt.get("personalRole"), "personal winner is server confirmed");
                        TestSupport.equals("PAID", receipt.get("walletStatus"), "receipt reconciles actual simulated purchase");
                        TestSupport.equals(winningDisplay, receipt.get("winnerDisplay"), "summary matches authoritative result");
                        var ownLedger = ok(new vn.ptit.btl16.client.service.WalletApi(winner).entries(java.util.Map.of("auctionId", Long.toString(room), "type", "PURCHASE")));
                        TestSupport.equals("1", ownLedger.get("entryCount"), "winning payment exactly once");
                        TestSupport.equals("PAID", ok(new vn.ptit.btl16.client.service.AuctionReceiptApi(seller).get(room)).get("walletStatus"), "host reconciles own sale");
                        if (anonymous) {
                            for (String value : receipt.getWireMessage().getData().values()) { TestSupport.check(!value.contains("journey_"), "anonymous receipt raw payload hides account"); }
                        }
                    }
                    ok(new AccountApi(first).logout());
                    error(ErrorCode.AUTH_REQUIRED, buyer.myProducts());
                }
            }
            try (ServerApplication restarted = ServerApplication.create(config)) {
                restarted.start();
                try (var seller = client("ws://127.0.0.1:" + restarted.getWebSocketBoundPort() + "/ws")) {
                    ok(new AccountApi(seller).login("journey_host", "Password123!"));
                    AuctionApi host = new AuctionApi(seller);
                    var result = ClientWireParser.auctions(ok(host.myAuctions()).getWireMessage().getData()).stream()
                            .filter(value -> value.getAuctionId() == room).findFirst().orElseThrow();
                    TestSupport.equals(winningDisplay, result.getCurrentWinnerUsername(), "winner persists after server restart");
                    TestSupport.equals(5, ClientWireParser.bids(ok(host.history(room)).getWireMessage().getData()).size(), "bid history persists after restart");
                    TestSupport.equals(winningDisplay, ok(new vn.ptit.btl16.client.service.AuctionReceiptApi(seller).get(room)).get("winnerDisplay"), "receipt survives full server restart");
                }
            }
            System.out.println("[PASS] AuctionJourneySelfTest " + (anonymous ? "ANONYMOUS" : "NAMED") + ": product/private room/chat/profile/muted bid, race/reconnect, real expiry, receipt/payment and restart");
        } finally {
            restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData);
        }
    }

    private static void lifecycleWithoutBids(AuctionApi host) throws Exception {
        long product = Long.parseLong(ok(TestMedia.create(host, "JOURNEY_UNSOLD", "Unsold item", TestMedia.DESCRIPTION)).get("productId"));
        long empty = Long.parseLong(ok(host.createAuction(product, money("100"), money("10"), 1)).get("auctionId"));
        ok(host.extendAuction(empty, 600)); error(ErrorCode.AUCTION_EXTENSION_LIMIT, host.extendAuction(empty, 10));
        ok(host.cancelAuction(empty));
        long next = Long.parseLong(ok(host.createAuction(product, money("100"), money("10"), 1)).get("auctionId"));
        ok(host.endAuction(next)); ok(host.deactivateProduct(product)); ok(host.restoreProduct(product));
        long restored = Long.parseLong(ok(host.createAuction(product, money("100"), money("10"), 1)).get("auctionId"));
        TestSupport.check(restored != next && next != empty, "reauction creates a new room, never reopens historical rooms");
        ok(host.endAuction(restored));
    }

    private static void checkStored(ServerConfig config, long room, String status) throws Exception {
        var factory = new vn.ptit.btl16.server.account.repository.JdbcConnectionFactory(config);
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("SELECT status, (SELECT COUNT(*) FROM auctions) AS rooms, (SELECT COUNT(*) FROM bids) AS bids FROM auctions WHERE auction_id=?")) {
            query.setLong(1, room);
            try (var rows = query.executeQuery()) {
                TestSupport.check(rows.next(), "auction persists in JDBC");
                System.out.println("[INFO] Journey JDBC state: " + rows.getString("status") + ", rooms=" + rows.getLong("rooms") + ", bids=" + rows.getLong("bids"));
                TestSupport.equals(status, rows.getString("status"), "JDBC matches authoritative runtime");
                TestSupport.equals(4L, rows.getLong("rooms"), "room lifecycle is durable");
                TestSupport.equals(5L, rows.getLong("bids"), "accepted bids are durable");
            }
        }
    }

    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static WebSocketClientTransport client(String endpoint) throws Exception {
        var client = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000); client.connect().get(10, TimeUnit.SECONDS); return client;
    }
    private static ApiResponse register(WebSocketClientTransport client, String username) throws Exception {
        AccountApi api = new AccountApi(client); ok(api.register(username, "Password123!", username, "", "")); return ok(api.login(username, "Password123!"));
    }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception {
        ApiResponse response = future.get(20, TimeUnit.SECONDS);
        TestSupport.check(response.isSuccess(), response.getType() + " " + response.getErrorCode() + ": " + response.getMessageText()); return response;
    }
    private static void error(ErrorCode code, CompletableFuture<ApiResponse> future) throws Exception {
        ApiResponse response = future.get(15, TimeUnit.SECONDS);
        TestSupport.check(!response.isSuccess(), "expected rejection: " + code); TestSupport.equals(code.name(), response.getErrorCode(), response.getType().name());
    }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
