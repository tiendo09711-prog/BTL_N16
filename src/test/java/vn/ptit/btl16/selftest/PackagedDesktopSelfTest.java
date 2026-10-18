package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.client.service.ProductMediaApi;
import vn.ptit.btl16.common.protocol.MessageType;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.List;
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
                ApiResponse identity = ok(new AccountApi(host).login("packaged_host", "Password123!"));
                AuctionApi api = new AuctionApi(host);
                var products = ClientWireParser.products(ok(api.myProducts()).getWireMessage().getData());
                TestSupport.equals(1, products.size(), "packaged product survives process restart");
                var auctions = ClientWireParser.auctions(ok(api.myAuctions()).getWireMessage().getData());
                TestSupport.equals(1, auctions.size(), "packaged auction survives process restart");
                TestSupport.equals(state.getProperty("winner"), auctions.get(0).getCurrentWinnerUsername(), "packaged winner persists");
                TestSupport.check(new BigDecimal("110000").compareTo(auctions.get(0).getCurrentPrice()) == 0, "packaged price persists");
                TestSupport.equals(1, ClientWireParser.bids(ok(api.history(Long.parseLong(state.getProperty("room"))))
                        .getWireMessage().getData()).size(), "packaged bid history persists");
                ProductMediaApi media = media(endpoint, api, identity.get("sessionToken"));
                var manifest = media.manifest(Long.parseLong(state.getProperty("product")), 0).get(10, TimeUnit.SECONDS);
                var image = manifest.path("media").get(0);
                TestSupport.equals(4032, image.path("width").asInt(), "packaged HEIC grid width persists after restart");
                byte[] detail = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(media.url(image.path("detail").asText()))).GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body();
                TestSupport.equals(4032, ImageIO.read(new ByteArrayInputStream(detail)).getWidth(), "packaged HTTP serves the persisted HEIC-derived JPEG");
            }
            System.out.println("[PASS] Single-file packaged server restart preserves accounts, products, prices, winner and history");
            return;
        }
        TestSupport.equals("create", args[1], "supported smoke-test mode");
        try (var host = client(endpoint); var first = client(endpoint); var second = client(endpoint)) {
            ApiResponse identity = register(host, "packaged_host");
            register(first, "packaged_buyer1");
            register(second, "packaged_buyer2");
            AuctionApi hostApi = new AuctionApi(host);
            AuctionApi firstApi = new AuctionApi(first);
            AuctionApi secondApi = new AuctionApi(second);
            ProductMediaApi media = media(endpoint, hostApi, identity.get("sessionToken"));
            Path photo = stateFile.toAbsolutePath().getParent().resolve("PHONE.HEIC");
            try (var input = PackagedDesktopSelfTest.class.getResourceAsStream("/media/tiled-phone.heic")) { Files.copy(input, photo); }
            String draft = media.begin(0).get(10, TimeUnit.SECONDS);
            var uploaded = media.upload(draft, photo, "IMAGE", progress -> { }).get(30, TimeUnit.SECONDS);
            TestSupport.equals(4032, uploaded.path("width").asInt(), "single-file server decodes every HEIC tile using bundled native tools");
            long product = media.commit(draft, "PACKAGED_01", "Portable runtime test", TestMedia.DESCRIPTION,
                    List.of(uploaded.path("id").asText())).get(10, TimeUnit.SECONDS).path("productId").asLong();
            ApiResponse room = ok(hostApi.createAuction(product,
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
            state.setProperty("product", Long.toString(product));
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

    private static ApiResponse register(WebSocketClientTransport transport, String username) throws Exception {
        AccountApi api = new AccountApi(transport);
        ok(api.register(username, "Password123!", username, "", ""));
        return ok(api.login(username, "Password123!"));
    }

    private static ProductMediaApi media(String endpoint, AuctionApi api, String token) throws Exception {
        URI address = URI.create(endpoint);
        URI base = new URI("http", null, address.getHost(), Integer.parseInt(ok(api.mediaEndpoint()).get("mediaPort")), "/", null, null);
        return new ProductMediaApi(base, () -> token);
    }

    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception {
        ApiResponse response = future.get(15, TimeUnit.SECONDS);
        TestSupport.check(response.isSuccess(), response.getErrorCode() + ": " + response.getMessageText());
        return response;
    }
}
