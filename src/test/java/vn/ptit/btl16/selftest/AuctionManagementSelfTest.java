package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientProduct;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.NetworkClient;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class AuctionManagementSelfTest {
    private AuctionManagementSelfTest() {
    }

    public static void run() throws Exception {
        ServerConfig config = ServerConfig.forTests(0, 2, 2, 1);
        PasswordHasher hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        TestUserRepository users = TestUserRepository.withDemoUsers(hasher);
        TestAuctionRepository auctionsRepository =
                TestAuctionRepository.withDemoAuctions(config);

        try (ServerApplication server = ServerApplication.createForTests(
                config, users, auctionsRepository, hasher)) {
            server.start();
            int port = server.getBoundPort();
            CountDownLatch kickedEvent = new CountDownLatch(1);

            try (NetworkClient hostClient = client(port);
                 NetworkClient guestClient = client(port)) {
                guestClient.addEventListener(message -> {
                    if (message.getType() == MessageType.AUCTION_KICKED) {
                        kickedEvent.countDown();
                    }
                });
                hostClient.connect("127.0.0.1", port);
                guestClient.connect("127.0.0.1", port);

                AccountApi hostAccount = new AccountApi(hostClient);
                AccountApi guestAccount = new AccountApi(guestClient);
                AuctionApi host = new AuctionApi(hostClient);
                AuctionApi guest = new AuctionApi(guestClient);

                requireSuccess(hostAccount.login("demo", "demo123").get(8, TimeUnit.SECONDS),
                        "host login");
                requireSuccess(guestAccount.login("alice", "alice123").get(8, TimeUnit.SECONDS),
                        "guest login");

                ApiResponse createdProduct = host.createProduct(
                                "MGMT_TEST", "San pham quan tri", "Dung de test host control")
                        .get(8, TimeUnit.SECONDS);
                requireSuccess(createdProduct, "create product");
                long productId = Long.parseLong(createdProduct.get("productId"));

                ApiResponse listedProducts = host.myProducts().get(8, TimeUnit.SECONDS);
                requireSuccess(listedProducts, "list owner products");
                List<ClientProduct> products = ClientWireParser.products(
                        listedProducts.getWireMessage().getData());
                TestSupport.check(
                        products.stream().anyMatch(value -> value.getProductId() == productId),
                        "created product appears in owner list");

                requireSuccess(host.updateProduct(
                                productId,
                                "MGMT_TEST_2",
                                "San pham da sua",
                                "OP01 update product")
                        .get(8, TimeUnit.SECONDS), "update product");

                ApiResponse createdAuction = host.createAuction(
                                productId,
                                new BigDecimal("1000000.00"),
                                new BigDecimal("50000.00"),
                                1)
                        .get(8, TimeUnit.SECONDS);
                requireSuccess(createdAuction, "create auction");
                long auctionId = Long.parseLong(createdAuction.get("auctionId"));

                requireSuccess(host.join(auctionId).get(8, TimeUnit.SECONDS), "host join");
                requireSuccess(guest.join(auctionId).get(8, TimeUnit.SECONDS), "guest join");

                ApiResponse hostBid = host.bid(auctionId, new BigDecimal("1050000.00"))
                        .get(8, TimeUnit.SECONDS);
                requireFailure(hostBid, "AUCTION_FORBIDDEN", "host cannot bid own auction");

                ApiResponse lowBid = guest.bid(auctionId, new BigDecimal("1020000.00"))
                        .get(8, TimeUnit.SECONDS);
                requireFailure(lowBid, "BID_TOO_LOW", "minimum increment is enforced");
                requireSuccess(guest.bid(auctionId, new BigDecimal("1050000.00"))
                        .get(8, TimeUnit.SECONDS), "valid minimum increment bid");

                ApiResponse guestProduct = guest.createProduct(
                                "ALICE_MARKET", "San pham cua Alice", "Buyer can also become a seller")
                        .get(8, TimeUnit.SECONDS);
                requireSuccess(guestProduct, "buyer creates own product");
                ApiResponse guestAuction = guest.createAuction(
                                Long.parseLong(guestProduct.get("productId")),
                                new BigDecimal("200000.00"),
                                new BigDecimal("10000.00"),
                                1)
                        .get(8, TimeUnit.SECONDS);
                requireSuccess(guestAuction, "buyer creates own auction");
                long guestAuctionId = Long.parseLong(guestAuction.get("auctionId"));
                requireSuccess(guest.join(guestAuctionId).get(8, TimeUnit.SECONDS),
                        "seller joins own auction");
                requireFailure(
                        guest.bid(guestAuctionId, new BigDecimal("210000.00"))
                                .get(8, TimeUnit.SECONDS),
                        "AUCTION_FORBIDDEN",
                        "seller cannot bid own auction");
                requireSuccess(host.join(guestAuctionId).get(8, TimeUnit.SECONDS),
                        "host of another room joins as buyer");
                requireSuccess(host.bid(guestAuctionId, new BigDecimal("210000.00"))
                        .get(8, TimeUnit.SECONDS), "seller in one room can buy in another room");
                ApiResponse guestAuctions = guest.myAuctions().get(8, TimeUnit.SECONDS);
                requireSuccess(guestAuctions, "buyer lists newly hosted auctions");
                TestSupport.check(
                        ClientWireParser.auctions(guestAuctions.getWireMessage().getData()).stream()
                                .anyMatch(value -> value.getAuctionId() == guestAuctionId),
                        "buyer-created auction appears in hosted list");

                ApiResponse guestExtend = guest.extendAuction(auctionId, 60)
                        .get(8, TimeUnit.SECONDS);
                requireFailure(guestExtend, "AUCTION_FORBIDDEN", "guest cannot extend auction");
                requireSuccess(host.extendAuction(auctionId, 60).get(8, TimeUnit.SECONDS),
                        "host extends auction");

                requireSuccess(host.kickUser(auctionId, "alice").get(8, TimeUnit.SECONDS),
                        "host kicks guest");
                TestSupport.check(kickedEvent.await(3, TimeUnit.SECONDS), "guest receives kicked event");
                ApiResponse rejoin = guest.join(auctionId).get(8, TimeUnit.SECONDS);
                requireFailure(rejoin, "USER_BLOCKED_FROM_AUCTION", "kicked guest cannot rejoin");

                ApiResponse cancelWithBid = host.cancelAuction(auctionId).get(8, TimeUnit.SECONDS);
                requireFailure(cancelWithBid, "AUCTION_HAS_BIDS", "auction with bids cannot cancel");
                requireSuccess(host.endAuction(auctionId).get(8, TimeUnit.SECONDS),
                        "host manually ends auction");

                ApiResponse inactiveProduct = host.createProduct(
                                "INACTIVE_TEST", "San pham an", "OP02 soft delete")
                        .get(8, TimeUnit.SECONDS);
                requireSuccess(inactiveProduct, "create product for deactivate");
                long inactiveProductId = Long.parseLong(inactiveProduct.get("productId"));
                requireSuccess(host.deactivateProduct(inactiveProductId).get(8, TimeUnit.SECONDS),
                        "deactivate product");
                ApiResponse inactiveCreate = host.createAuction(
                                inactiveProductId,
                                new BigDecimal("200000.00"),
                                new BigDecimal("10000.00"),
                                1)
                        .get(8, TimeUnit.SECONDS);
                requireFailure(inactiveCreate, "PRODUCT_INACTIVE", "inactive product cannot auction");

                ApiResponse cancelProduct = host.createProduct(
                                "CANCEL_TEST", "San pham huy", "Auction cancellation test")
                        .get(8, TimeUnit.SECONDS);
                requireSuccess(cancelProduct, "create cancel product");
                ApiResponse cancellableAuction = host.createAuction(
                                Long.parseLong(cancelProduct.get("productId")),
                                new BigDecimal("300000.00"),
                                new BigDecimal("10000.00"),
                                1)
                        .get(8, TimeUnit.SECONDS);
                requireSuccess(cancellableAuction, "create cancellable auction");
                requireSuccess(host.cancelAuction(Long.parseLong(cancellableAuction.get("auctionId")))
                        .get(8, TimeUnit.SECONDS), "cancel auction without bids");

                ApiResponse myAuctions = host.myAuctions().get(8, TimeUnit.SECONDS);
                requireSuccess(myAuctions, "list hosted auctions");
                TestSupport.check(
                        ClientWireParser.auctions(myAuctions.getWireMessage().getData()).size() >= 2,
                        "host auction list contains created rooms");
            }
        }
        System.out.println("[PASS] AuctionManagementSelfTest");
    }

    private static NetworkClient client(int port) {
        return new NetworkClient(2_097_152, 3000, 6000);
    }

    private static void requireSuccess(ApiResponse response, String label) {
        if (!response.isSuccess()) {
            throw new AssertionError(label + " failed: "
                    + response.getErrorCode() + ' ' + response.getMessageText());
        }
    }

    private static void requireFailure(ApiResponse response, String errorCode, String label) {
        TestSupport.check(!response.isSuccess(), label + " must fail");
        TestSupport.equals(errorCode, response.getErrorCode(), label + " error code");
    }
}
