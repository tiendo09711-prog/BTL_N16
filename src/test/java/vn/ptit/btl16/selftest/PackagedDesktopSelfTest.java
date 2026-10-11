package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.protocol.MessageType;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class PackagedDesktopSelfTest {
    private PackagedDesktopSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("endpoint create|verify state-file");
        String endpoint = args[0];
        Path stateFile = Path.of(args[2]);
        if ("verify".equals(args[1])) {
            Properties state = new Properties();
            try (var stream = Files.newInputStream(stateFile)) { state.load(stream); }
            try (var host = client(endpoint)) {
                ok(new AccountApi(host).login("packaged_host", "Password123!"));
                AuctionApi api = new AuctionApi(host);
                var products = ClientWireParser.products(ok(api.myProducts()).getWireMessage().getData());
                TestSupport.equals(1, products.size(), "packaged product survives process restart");
                var auctions = ClientWireParser.auctions(ok(api.myAuctions()).getWireMessage().getData());
                TestSupport.equals(1, auctions.size(), "packaged auction survives process restart");
                TestSupport.equals(state.getProperty("winner"), auctions.get(0).getCurrentWinnerUsername(), "packaged winner persists");
                TestSupport.check(new BigDecimal("110000").compareTo(auctions.get(0).getCurrentPrice()) == 0, "packaged price persists");
                TestSupport.equals(1, ClientWireParser.bids(ok(api.history(Long.parseLong(state.getProperty("room"))))
                        .getWireMessage().getData()).size(), "packaged bid history persists");
            }
            System.out.println("[PASS] Single-file packaged server restart preserves accounts, products, prices, winner and history");
            return;
        }
        TestSupport.equals("create", args[1], "supported smoke-test mode");
        try (var host = client(endpoint); var first = client(endpoint); var second = client(endpoint)) {
            register(host, "packaged_host");
            register(first, "packaged_buyer1");
            register(second, "packaged_buyer2");
            AuctionApi hostApi = new AuctionApi(host);
            AuctionApi firstApi = new AuctionApi(first);
            AuctionApi secondApi = new AuctionApi(second);
            ApiResponse product = ok(TestMedia.create(hostApi, "PACKAGED_01", "Portable runtime test", ""));
            ApiResponse room = ok(hostApi.createAuction(Long.parseLong(product.get("productId")),
                    new BigDecimal("100000"), new BigDecimal("10000"), 5));
            long roomId = Long.parseLong(room.get("auctionId"));
            ok(firstApi.join(roomId));
            ok(secondApi.join(roomId));
            CountDownLatch updates = new CountDownLatch(2);
            first.addEventListener(message -> { if (message.getType() == MessageType.BID_UPDATE) updates.countDown(); });
            second.addEventListener(message -> { if (message.getType() == MessageType.BID_UPDATE) updates.countDown(); });
            var bidOne = firstApi.bid(roomId, new BigDecimal("110000"));
            var bidTwo = secondApi.bid(roomId, new BigDecimal("110000"));
            ApiResponse resultOne = bidOne.get(15, TimeUnit.SECONDS);
            ApiResponse resultTwo = bidTwo.get(15, TimeUnit.SECONDS);
            TestSupport.equals(1, (resultOne.isSuccess() ? 1 : 0) + (resultTwo.isSuccess() ? 1 : 0), "packaged atomic bidding");
            TestSupport.check(updates.await(10, TimeUnit.SECONDS), "packaged realtime broadcasting");
            Properties state = new Properties();
            state.setProperty("room", Long.toString(roomId));
            state.setProperty("winner", resultOne.isSuccess() ? "packaged_buyer1" : "packaged_buyer2");
            try (var stream = Files.newOutputStream(stateFile)) { state.store(stream, null); }
        }
        System.out.println("[PASS] Single-file packaged server accepts 3 real WebSocket clients and competing bids");
    }

    private static WebSocketClientTransport client(String endpoint) throws Exception {
        var transport = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000);
        transport.connect().get(10, TimeUnit.SECONDS);
        return transport;
    }

    private static void register(WebSocketClientTransport transport, String username) throws Exception {
        AccountApi api = new AccountApi(transport);
        ok(api.register(username, "Password123!", username, "", ""));
        ok(api.login(username, "Password123!"));
    }

    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception {
        ApiResponse response = future.get(15, TimeUnit.SECONDS);
        TestSupport.check(response.isSuccess(), response.getErrorCode() + ": " + response.getMessageText());
        return response;
    }
}
