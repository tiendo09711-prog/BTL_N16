package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.TestUserRepository;
import vn.ptit.btl16.server.account.security.PasswordHasher;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.repository.TestAuctionRepository;

import java.math.BigDecimal;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebSocketUpgradeSelfTest {
    private static final byte[] PNG = TestMedia.png();

    private WebSocketUpgradeSelfTest() {
    }

    public static void run() throws Exception {
        int tcpPort = freePort();
        int webSocketPort = freePort();
        ServerConfig config = ServerConfig.forTransportTests(tcpPort, webSocketPort);
        PasswordHasher hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        TestUserRepository users = TestUserRepository.withDemoUsers(hasher);
        TestAuctionRepository repository = TestAuctionRepository.withDemoAuctions(config);
        String wsUrl = "ws://127.0.0.1:" + webSocketPort + "/ws";

        try (ServerApplication server = ServerApplication.createForTests(
                config, users, repository, hasher)) {
            server.start();
            WebSocketConnectionSelfTest.run(wsUrl);
            basicWebSocketFlow(wsUrl);

            CountDownLatch tcpCreated = new CountDownLatch(1);
            CountDownLatch webSocketBid = new CountDownLatch(1);
            try (WebSocketClientTransport hostTransport = webSocket(wsUrl);
                 NetworkClient bidderTransport = new NetworkClient(2_097_152, 3000, 6000)) {
                hostTransport.connect().get(8, TimeUnit.SECONDS);
                bidderTransport.connect("127.0.0.1", tcpPort);
                hostTransport.addEventListener(message -> {
                    if (message.getType() == MessageType.BID_UPDATE) {
                        webSocketBid.countDown();
                    }
                });
                bidderTransport.addEventListener(message -> {
                    if (message.getType() == MessageType.AUCTION_CREATED) {
                        tcpCreated.countDown();
                    }
                });

                AccountApi hostAccount = new AccountApi(hostTransport);
                AccountApi bidderAccount = new AccountApi(bidderTransport);
                AuctionApi hostAuctions = new AuctionApi(hostTransport);
                AuctionApi bidderAuctions = new AuctionApi(bidderTransport);
                requireSuccess(hostAccount.login("demo", "demo123").get(8, TimeUnit.SECONDS),
                        "host WebSocket login");
                ApiResponse bidderLogin = bidderAccount.login("alice", "alice123")
                        .get(8, TimeUnit.SECONDS);
                requireSuccess(bidderLogin, "bidder TCP login");
                String bidderToken = bidderLogin.get("sessionToken");

                ApiResponse product = TestMedia.create(hostAuctions,
                        "WS-PNG", "Laptop Gaming WebSocket", "Cross transport product",
                        PNG, "image/png", "product.png").get(8, TimeUnit.SECONDS);
                requireSuccess(product, "create image product");
                long productId = Long.parseLong(product.get("productId"));
                requireFailure(bidderAuctions.getProductImage(productId).get(8, TimeUnit.SECONDS),
                        "ROOM_ACCESS_DENIED", "unlisted image is visible only to its owner");

                ApiResponse created = hostAuctions.createAuction(
                        productId,
                        new BigDecimal("100.00"),
                        new BigDecimal("10.00"),
                        1,
                        "PRIVATE",
                        "room1234").get(8, TimeUnit.SECONDS);
                requireSuccess(created, "create private auction");
                long auctionId = Long.parseLong(created.get("auctionId"));
                TestSupport.check(tcpCreated.await(3, TimeUnit.SECONDS),
                        "TCP client receives WebSocket auction creation");

                ApiResponse list = hostAuctions.listAuctions().get(8, TimeUnit.SECONDS);
                requireSuccess(list, "auction list without image payload");
                TestSupport.check(!list.getWireMessage().getData().containsKey("imageBase64"),
                        "auction list excludes image Base64");
                List<ClientAuction> values = ClientWireParser.auctions(list.getWireMessage().getData());
                ClientAuction privateAuction = values.stream()
                        .filter(value -> value.getAuctionId() == auctionId)
                        .findFirst().orElseThrow();
                TestSupport.equals("PRIVATE", privateAuction.getVisibility(), "private metadata");
                TestSupport.check(privateAuction.requiresPassword(), "private room requires password");

                requireFailure(bidderAuctions.history(auctionId).get(8, TimeUnit.SECONDS),
                        "ROOM_ACCESS_DENIED", "private history requires a grant");
                requireFailure(bidderAuctions.resync(auctionId).get(8, TimeUnit.SECONDS),
                        "ROOM_ACCESS_DENIED", "private resync cannot bypass password");
                requireFailure(bidderAuctions.getProductImage(productId).get(8, TimeUnit.SECONDS),
                        "ROOM_ACCESS_DENIED", "private image requires a grant");
                requireFailure(bidderAuctions.join(auctionId).get(8, TimeUnit.SECONDS),
                        "ROOM_PASSWORD_REQUIRED", "join private without password");
                requireFailure(bidderAuctions.join(auctionId, "wrong")
                                .get(8, TimeUnit.SECONDS),
                        "ROOM_PASSWORD_INVALID", "join private wrong password");
                requireSuccess(hostAuctions.join(auctionId).get(8, TimeUnit.SECONDS),
                        "host joins private without password");
                requireSuccess(bidderAuctions.join(auctionId, "room1234")
                                .get(8, TimeUnit.SECONDS),
                        "join private correct password");
                requireSuccess(bidderAuctions.history(auctionId).get(8, TimeUnit.SECONDS),
                        "private history after join");
                requireSuccess(bidderAuctions.getProductImage(productId).get(8, TimeUnit.SECONDS),
                        "private image after join");

                requireSuccess(bidderAuctions.bid(auctionId, new BigDecimal("110.00"))
                                .get(8, TimeUnit.SECONDS),
                        "TCP bid in private room");
                TestSupport.check(webSocketBid.await(3, TimeUnit.SECONDS),
                        "WebSocket host receives TCP bid update");

                ApiResponse searchName = hostAuctions.searchAuctions("PRODUCT_NAME", "gaming")
                        .get(8, TimeUnit.SECONDS);
                requireSuccess(searchName, "case-insensitive product search");
                TestSupport.check(ClientWireParser.auctions(searchName.getWireMessage().getData())
                                .stream().anyMatch(value -> value.getAuctionId() == auctionId),
                        "search product name finds private room");
                ApiResponse searchRoom = hostAuctions.searchAuctions("ROOM_ID", Long.toString(auctionId))
                        .get(8, TimeUnit.SECONDS);
                requireSuccess(searchRoom, "room ID search");
                TestSupport.equals(1,
                        ClientWireParser.auctions(searchRoom.getWireMessage().getData()).size(),
                        "room ID exact result");

                ApiResponse image = hostAuctions.getProductImage(productId).get(8, TimeUnit.SECONDS);
                requireSuccess(image, "get product image");
                TestSupport.check(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(image.get("imageBase64")))) != null,
                        "product image roundtrip");

                bidderTransport.disconnect();
                Thread.sleep(150L);
                try (WebSocketClientTransport resumedTransport = webSocket(wsUrl)) {
                    resumedTransport.connect().get(8, TimeUnit.SECONDS);
                    AccountApi resumedAccount = new AccountApi(resumedTransport);
                    AuctionApi resumedAuctions = new AuctionApi(resumedTransport);
                    requireSuccess(resumedAccount.resumeSession(bidderToken)
                                    .get(8, TimeUnit.SECONDS),
                            "resume TCP session over WebSocket");
                    requireSuccess(resumedAuctions.resync(auctionId).get(8, TimeUnit.SECONDS),
                            "private room resync uses session grant");
                    requireFailure(hostAuctions.kickUser(auctionId, "alice")
                                    .get(8, TimeUnit.SECONDS),
                            "CANNOT_KICK_BIDDER", "private bidder is protected from kick");
                    requireSuccess(resumedAuctions.resync(auctionId).get(8, TimeUnit.SECONDS),
                            "private bidder retains access after rejected kick");
                    closedPrivateRoom(hostAuctions, resumedAuctions);
                }
                blockedPrivateViewer(wsUrl, hostAuctions, auctionId, productId);
            }
        }
        System.out.println("[PASS] WebSocketUpgradeSelfTest");
    }

    private static void blockedPrivateViewer(String wsUrl, AuctionApi hostAuctions,
            long auctionId, long productId) throws Exception {
        try (WebSocketClientTransport transport = webSocket(wsUrl)) {
            transport.connect().get(8, TimeUnit.SECONDS);
            AccountApi account = new AccountApi(transport);
            AuctionApi auctions = new AuctionApi(transport);
            requireSuccess(account.login("bob", "bob123").get(8, TimeUnit.SECONDS), "viewer login");
            requireSuccess(auctions.join(auctionId, "room1234").get(8, TimeUnit.SECONDS), "viewer joins private room");
            requireSuccess(auctions.history(auctionId).get(8, TimeUnit.SECONDS), "viewer has history grant");
            requireSuccess(hostAuctions.kickUser(auctionId, "bob").get(8, TimeUnit.SECONDS), "kick non-bidding viewer");
            requireFailure(auctions.history(auctionId).get(8, TimeUnit.SECONDS),
                    "USER_BLOCKED_FROM_AUCTION", "kick revokes history grant");
            requireFailure(auctions.resync(auctionId).get(8, TimeUnit.SECONDS),
                    "USER_BLOCKED_FROM_AUCTION", "kick blocks detailed resync");
            requireFailure(auctions.join(auctionId, "room1234").get(8, TimeUnit.SECONDS),
                    "USER_BLOCKED_FROM_AUCTION", "knowing the password cannot undo kick");
            requireFailure(auctions.bid(auctionId, new BigDecimal("120")).get(8, TimeUnit.SECONDS),
                    "USER_BLOCKED_FROM_AUCTION", "kick blocks bids");
            requireFailure(auctions.getProductImage(productId).get(8, TimeUnit.SECONDS),
                    "ROOM_ACCESS_DENIED", "kick revokes private image access");
        }
    }

    private static void closedPrivateRoom(AuctionApi host, AuctionApi outsider) throws Exception {
        ApiResponse product = TestMedia.create(host, "WS-CLOSED", "Closed private room", "")
                .get(8, TimeUnit.SECONDS);
        requireSuccess(product, "create closed-room product");
        ApiResponse created = host.createAuction(Long.parseLong(product.get("productId")),
                new BigDecimal("100"), new BigDecimal("10"), 1, "PRIVATE", "room1234")
                .get(8, TimeUnit.SECONDS);
        requireSuccess(created, "create private room to close");
        long auctionId = Long.parseLong(created.get("auctionId"));
        requireSuccess(host.cancelAuction(auctionId).get(8, TimeUnit.SECONDS), "cancel empty private room");
        requireSuccess(host.resync(auctionId).get(8, TimeUnit.SECONDS), "host can resync closed private room");
        requireFailure(outsider.resync(auctionId).get(8, TimeUnit.SECONDS),
                "ROOM_ACCESS_DENIED", "closure must not bypass private resync access");
        requireFailure(outsider.history(auctionId).get(8, TimeUnit.SECONDS),
                "ROOM_ACCESS_DENIED", "closure must not make private history public");
    }

    private static void basicWebSocketFlow(String wsUrl) throws Exception {
        try (WebSocketClientTransport transport = webSocket(wsUrl)) {
            AtomicBoolean welcome = new AtomicBoolean(false);
            transport.addEventListener(message -> {
                if (message.getType() == MessageType.CONNECTION_WELCOME) {
                    welcome.set(true);
                }
            });
            transport.connect().get(8, TimeUnit.SECONDS);
            TestSupport.waitUntil(welcome::get, 2000L, "WebSocket welcome");
            AccountApi accounts = new AccountApi(transport);
            requireSuccess(accounts.register(
                    "ws_user", "ws_user123", "WS User", "ws@example.com", "")
                    .get(8, TimeUnit.SECONDS), "WebSocket register");
            requireSuccess(accounts.login("ws_user", "ws_user123")
                    .get(8, TimeUnit.SECONDS), "WebSocket login");
            requireSuccess(accounts.ping().get(8, TimeUnit.SECONDS), "WebSocket ping");
            requireSuccess(accounts.logout().get(8, TimeUnit.SECONDS), "WebSocket logout");
        }
    }

    private static WebSocketClientTransport webSocket(String url) {
        return new WebSocketClientTransport(url, 2_097_152, 3000, 6000);
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void requireSuccess(ApiResponse response, String label) {
        if (!response.isSuccess()) {
            throw new AssertionError(label + " failed: "
                    + response.getErrorCode() + " " + response.getMessageText());
        }
    }

    private static void requireFailure(ApiResponse response, String code, String label) {
        TestSupport.check(!response.isSuccess(), label + " must fail");
        TestSupport.equals(code, response.getErrorCode(), label + " error code");
    }
}
