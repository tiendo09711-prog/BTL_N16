package vn.ptit.btl16.tools;

import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.util.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates many real TCP clients and sends bids at nearly the same time.
 * Run while ServerMain is active. Defaults: 5 clients and first open auction.
 */
public final class ConcurrentBidLoadTestMain {
    private ConcurrentBidLoadTestMain() {
    }

    public static void main(String[] args) throws Exception {
        int clientCount = args.length >= 1 ? Integer.parseInt(args[0]) : 5;
        Long requestedAuctionId = args.length >= 2 ? Long.parseLong(args[1]) : null;
        if (clientCount < 2 || clientCount > 50) {
            throw new IllegalArgumentException("clientCount must be between 2 and 50");
        }

        ClientConfig config = ClientConfig.loadDefault();
        List<NetworkClient> clients = new ArrayList<>();
        List<AuctionApi> auctionApis = new ArrayList<>();
        String runId = Long.toString(System.currentTimeMillis());
        try {
            long auctionId = 0L;
            BigDecimal currentPrice = BigDecimal.ZERO;
            for (int i = 0; i < clientCount; i++) {
                NetworkClient network = new NetworkClient(
                        config.getMaxFrameBytes(),
                        config.getConnectTimeoutMillis(),
                        config.getRequestTimeoutMillis());
                network.connect(config.getServerHost(), config.getServerPort());
                clients.add(network);
                AccountApi account = new AccountApi(network);
                AuctionApi auction = new AuctionApi(network);
                auctionApis.add(auction);

                String username = "load_" + runId + '_' + i;
                String password = "load12345";
                ApiResponse register = account.register(
                        username,
                        password,
                        "Load Client " + i,
                        username + "@test.local",
                        "").get(10, TimeUnit.SECONDS);
                if (!register.isSuccess() && !"USERNAME_ALREADY_EXISTS".equals(register.getErrorCode())) {
                    throw new IllegalStateException("Register failed: " + register.getMessageText());
                }
                requireSuccess(account.login(username, password).get(10, TimeUnit.SECONDS), "login");

                if (i == 0) {
                    ApiResponse list = auction.listAuctions().get(10, TimeUnit.SECONDS);
                    requireSuccess(list, "list");
                    List<ClientAuction> values = ClientWireParser.auctions(list.getWireMessage().getData());
                    ClientAuction selected = values.stream()
                            .filter(ClientAuction::isOpen)
                            .filter(value -> requestedAuctionId == null
                                    || value.getAuctionId() == requestedAuctionId)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No matching open auction"));
                    auctionId = selected.getAuctionId();
                    currentPrice = selected.getCurrentPrice();
                }
                requireSuccess(auction.join(auctionId).get(10, TimeUnit.SECONDS), "join");
            }

            long finalAuctionId = auctionId;
            BigDecimal basePrice = currentPrice;
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger accepted = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < clientCount; i++) {
                int index = i;
                tasks.add(CompletableFuture.runAsync(() -> {
                    await(start);
                    BigDecimal amount = basePrice.add(
                            new BigDecimal((index + 1) * 10000L)).setScale(2);
                    ApiResponse response = auctionApis.get(index)
                            .bid(finalAuctionId, amount)
                            .join();
                    if (response.isSuccess()) {
                        accepted.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                    System.out.println("client=" + index
                            + " amount=" + Money.display(amount)
                            + " result=" + (response.isSuccess()
                            ? "ACCEPTED" : response.getErrorCode()));
                }));
            }

            long started = System.nanoTime();
            start.countDown();
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
                    .get(20, TimeUnit.SECONDS);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            ApiResponse fresh = auctionApis.get(0).resync(auctionId).get(10, TimeUnit.SECONDS);
            requireSuccess(fresh, "resync");
            ClientAuction finalState = ClientWireParser.auction(fresh.getWireMessage().getData(), "");
            System.out.println("====================================================");
            System.out.println("Concurrent bid load test complete");
            System.out.println("Clients: " + clientCount);
            System.out.println("Accepted: " + accepted.get() + ", rejected: " + rejected.get());
            System.out.println("Elapsed: " + elapsedMillis + " ms");
            System.out.println("Authoritative final price: " + Money.display(finalState.getCurrentPrice()));
            System.out.println("Winner: " + finalState.getCurrentWinnerUsername());
            System.out.println("====================================================");
        } finally {
            for (NetworkClient client : clients) {
                client.close();
            }
        }
    }

    private static void requireSuccess(ApiResponse response, String operation) {
        if (!response.isSuccess()) {
            throw new IllegalStateException(operation + " failed: "
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
