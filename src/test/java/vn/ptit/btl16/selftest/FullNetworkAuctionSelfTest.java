package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientAuction;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class FullNetworkAuctionSelfTest {
    private FullNetworkAuctionSelfTest() {
    }

    public static void run() throws Exception {
        ServerConfig config = ServerConfig.forTests(0, 2, 2, 1);
        PasswordHasher hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        TestUserRepository users = TestUserRepository.withDemoUsers(hasher);
        TestAuctionRepository auctionRepository =
                TestAuctionRepository.withDemoAuctions(config);

        try (ServerApplication server = ServerApplication.createForTests(
                config,
                users,
                auctionRepository,
                hasher)) {
            server.start();
            int port = server.getBoundPort();

            AtomicBoolean clientOneOutbid = new AtomicBoolean(false);
            AtomicInteger bidUpdateEvents = new AtomicInteger();
            CountDownLatch endedEvent = new CountDownLatch(1);

            String demoToken;
            long auctionId;
            try (NetworkClient clientOne = client(port);
                 NetworkClient clientTwo = client(port)) {
                clientOne.addEventListener(message -> {
                    if (message.getType() == MessageType.OUTBID_NOTIFICATION) {
                        clientOneOutbid.set(true);
                    }
                    if (message.getType() == MessageType.BID_UPDATE) {
                        bidUpdateEvents.incrementAndGet();
                    }
                    if (message.getType() == MessageType.AUCTION_ENDED) {
                        endedEvent.countDown();
                    }
                });
                clientTwo.addEventListener(message -> {
                    if (message.getType() == MessageType.BID_UPDATE) {
                        bidUpdateEvents.incrementAndGet();
                    }
                    if (message.getType() == MessageType.AUCTION_ENDED) {
                        endedEvent.countDown();
                    }
                });
                clientOne.connect("127.0.0.1", port);
                clientTwo.connect("127.0.0.1", port);

                AccountApi accountOne = new AccountApi(clientOne);
                AccountApi accountTwo = new AccountApi(clientTwo);
                AuctionApi auctionOne = new AuctionApi(clientOne);
                AuctionApi auctionTwo = new AuctionApi(clientTwo);

                ApiResponse loginOne = accountOne.login("demo", "demo123")
                        .get(8, TimeUnit.SECONDS);
                ApiResponse loginTwo = accountTwo.login("alice", "alice123")
                        .get(8, TimeUnit.SECONDS);
                requireSuccess(loginOne, "demo login");
                requireSuccess(loginTwo, "alice login");
                demoToken = loginOne.get("sessionToken");

                ApiResponse listResponse = auctionOne.listAuctions().get(8, TimeUnit.SECONDS);
                requireSuccess(listResponse, "auction list");
                List<ClientAuction> list = ClientWireParser.auctions(
                        listResponse.getWireMessage().getData());
                TestSupport.check(!list.isEmpty(), "demo auctions exist");
                auctionId = list.get(0).getAuctionId();

                requireSuccess(auctionOne.join(auctionId).get(8, TimeUnit.SECONDS), "client one join");
                requireSuccess(auctionTwo.join(auctionId).get(8, TimeUnit.SECONDS), "client two join");

                requireSuccess(auctionOne.bid(auctionId, new BigDecimal("1100000.00"))
                        .get(8, TimeUnit.SECONDS), "first bid");
                requireSuccess(auctionTwo.bid(auctionId, new BigDecimal("1200000.00"))
                        .get(8, TimeUnit.SECONDS), "second bid");
                TestSupport.waitUntil(clientOneOutbid::get, 2000L, "outbid notification");

                CountDownLatch start = new CountDownLatch(1);
                CompletableFuture<ApiResponse> low = CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return auctionOne.bid(auctionId, new BigDecimal("1300000.00")).join();
                });
                CompletableFuture<ApiResponse> high = CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return auctionTwo.bid(auctionId, new BigDecimal("1400000.00")).join();
                });
                start.countDown();
                ApiResponse lowResponse = low.get(8, TimeUnit.SECONDS);
                ApiResponse highResponse = high.get(8, TimeUnit.SECONDS);
                TestSupport.check(highResponse.isSuccess(), "highest concurrent bid is accepted");
                TestSupport.check(lowResponse.isSuccess()
                                || "BID_TOO_LOW".equals(lowResponse.getErrorCode()),
                        "lower concurrent bid is either accepted first or rejected after high bid");

                ApiResponse fresh = auctionTwo.resync(auctionId).get(8, TimeUnit.SECONDS);
                requireSuccess(fresh, "fresh snapshot");
                ClientAuction finalAuction = ClientWireParser.auction(
                        fresh.getWireMessage().getData(), "");
                TestSupport.check(
                        finalAuction.getCurrentPrice().compareTo(new BigDecimal("1400000")) == 0,
                        "final price after concurrent bids");
                TestSupport.check(bidUpdateEvents.get() >= 4, "room received realtime bid events");

                clientOne.disconnect();
            }

            Thread.sleep(250L);
            try (NetworkClient resumedClient = client(port)) {
                resumedClient.addEventListener(message -> {
                    if (message.getType() == MessageType.AUCTION_ENDED) {
                        endedEvent.countDown();
                    }
                });
                resumedClient.connect("127.0.0.1", port);
                AccountApi account = new AccountApi(resumedClient);
                AuctionApi auction = new AuctionApi(resumedClient);
                ApiResponse resume = account.resumeSession(demoToken).get(8, TimeUnit.SECONDS);
                requireSuccess(resume, "resume session");
                ApiResponse resync = auction.resync(auctionId).get(8, TimeUnit.SECONDS);
                requireSuccess(resync, "resync auction");
                ClientAuction synced = ClientWireParser.auction(
                        resync.getWireMessage().getData(), "");
                TestSupport.check(
                        synced.getCurrentPrice().compareTo(new BigDecimal("1400000")) == 0,
                        "resync receives authoritative price");

                TestSupport.check(endedEvent.await(6, TimeUnit.SECONDS), "timer ends auction");
                ApiResponse endedSnapshot = auction.resync(auctionId).get(8, TimeUnit.SECONDS);
                requireSuccess(endedSnapshot, "ended snapshot");
                ClientAuction ended = ClientWireParser.auction(
                        endedSnapshot.getWireMessage().getData(), "");
                TestSupport.equals("ENDED", ended.getStatus(), "auction ended exactly once");
            }
        }
        System.out.println("[PASS] FullNetworkAuctionSelfTest");
    }

    private static NetworkClient client(int port) {
        return new NetworkClient(2_097_152, 3000, 6000);
    }

    private static void requireSuccess(ApiResponse response, String label) {
        if (!response.isSuccess()) {
            throw new AssertionError(label + " failed: "
                    + response.getErrorCode() + " " + response.getMessageText());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
