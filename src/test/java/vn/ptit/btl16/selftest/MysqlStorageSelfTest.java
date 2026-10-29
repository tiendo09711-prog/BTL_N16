package vn.ptit.btl16.selftest;

import com.fasterxml.jackson.databind.JsonNode;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.db.DatabaseSchema;
import vn.ptit.btl16.server.media.MediaConfig;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public final class MysqlStorageSelfTest {
    public static void main(String[] args) throws Exception {
        String previous = System.getProperty("btl16.server.config");
        Properties settings = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(previous == null ? "config/server.properties" : previous))) { settings.load(reader); }
        String database = "btl16_verify_" + UUID.randomUUID().toString().replace("-", "");
        Path work = Files.createTempDirectory("btl16-mysql-storage-test-");
        Path configFile = work.resolve("server.properties");
        settings.setProperty("db.driver", "com.mysql.cj.jdbc.Driver"); settings.setProperty("db.name", database);
        settings.setProperty("db.autoInitialize", "true"); settings.setProperty("server.tcp.enabled", "false");
        settings.setProperty("server.websocket.port", "0"); settings.setProperty("media.port", "0");
        settings.setProperty("security.passwordIterations", "10000");
        try (var writer = Files.newBufferedWriter(configFile)) { settings.store(writer, null); }
        System.setProperty("btl16.server.config", configFile.toString());
        JdbcConnectionFactory factory = null;
        try {
            ServerConfig config = ServerConfig.loadDefault(); factory = new JdbcConnectionFactory(config);
            long productId, sellerId, buyerId, roomId;
            byte[] original, videoBytes;
            Set<Path> firstWorkspaces = mediaWorkspaces();
            try (ServerApplication server = ServerApplication.create(config)) {
                server.start(); TestSupport.equals(firstWorkspaces, mediaWorkspaces(), "server creates no temporary media workspace");
                try (var seller = connect(server); var buyer = connect(server)) {
                    ApiResponse sellerIdentity = register(seller, "mysql_seller"); sellerId = Long.parseLong(sellerIdentity.get("userId"));
                    ApiResponse buyerIdentity = register(buyer, "mysql_buyer"); buyerId = Long.parseLong(buyerIdentity.get("userId"));
                    WalletApi wallet = new WalletApi(buyer); String topup = UUID.randomUUID().toString();
                    ok(wallet.submit(topup, new BigDecimal("2000")));
                    TestSupport.check(server.wallets().account(buyerId).balance().signum() == 0, "MySQL deposit requires server approval");
                    server.wallets().review(topup, true); server.wallets().review(topup, true);
                    String rejected = UUID.randomUUID().toString(); ok(wallet.submit(rejected, new BigDecimal("1000")));
                    server.wallets().review(rejected, false, "Sai noi dung demo"); server.wallets().review(rejected, true);
                    TestSupport.equals("Sai noi dung demo", server.wallets().requests(buyerId, false).stream().filter(row -> row.id().equals(rejected)).findFirst().orElseThrow().rejectionReason(), "MySQL rejection reason and idempotent review");
                    AuctionApi host = new AuctionApi(seller); ProductMediaApi media = media(host, sellerIdentity);
                    Path photo = work.resolve("source.png");
                    java.awt.image.BufferedImage noise = new java.awt.image.BufferedImage(700, 700, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    Random random = new Random(42);
                    for (int row = 0; row < 700; row++) { for (int column = 0; column < 700; column++) { noise.setRGB(column, row, random.nextInt()); } }
                    javax.imageio.ImageIO.write(noise, "png", photo.toFile()); original = Files.readAllBytes(photo);
                    TestSupport.check(original.length > 1_048_576, "payload exceeds default XAMPP max_allowed_packet; chunked writes must work");
                    Path video = work.resolve("demo.mp4"); MediaConfig mediaConfig = MediaConfig.load(false);
                    MediaSystemSelfTest.tool(mediaConfig.ffmpeg(), "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=15", "-t", "1", "-c:v", "mpeg4", video.toString());
                    String draft = media.begin(0).get(15, TimeUnit.SECONDS);
                    JsonNode image = media.upload(draft, photo, "IMAGE", progress -> { }).get(60, TimeUnit.SECONDS);
                    JsonNode clip = media.upload(draft, video, "VIDEO", progress -> { }).get(60, TimeUnit.SECONDS);
                    JsonNode committed = media.commit(draft, "MYSQL_ONLY", "MySQL storage", TestMedia.DESCRIPTION,
                            List.of(image.path("id").asText(), clip.path("id").asText())).get(60, TimeUnit.SECONDS);
                    productId = committed.path("productId").asLong();
                    JsonNode manifest = media.manifest(productId, 0).get(15, TimeUnit.SECONDS);
                    assertBytes(original, download(media, manifest.path("media").get(0).path("original").asText(), null).body(), "original bytes served after commit");
                    String edit = media.begin(productId).get(15, TimeUnit.SECONDS);
                    media.commit(edit, "MYSQL_ONLY", "MySQL storage edited", TestMedia.DESCRIPTION,
                            List.of(image.path("id").asText(), clip.path("id").asText())).get(60, TimeUnit.SECONDS);
                    manifest = media.manifest(productId, 0).get(15, TimeUnit.SECONDS);
                    videoBytes = download(media, manifest.path("media").get(1).path("video").asText(), null).body();
                    roomId = Long.parseLong(ok(host.createAuction(productId, new BigDecimal("100"), new BigDecimal("10"), 1)).get("auctionId"));
                    AuctionApi bidder = new AuctionApi(buyer); ok(bidder.join(roomId)); ok(bidder.bid(roomId, new BigDecimal("110")));
                    var repository = new JdbcAuctionRepository(factory); var room = repository.findAuctionById(roomId).orElseThrow();
                    try {
                        var missingPayload = new vn.ptit.btl16.server.media.ProductMedia(UUID.randomUUID().toString(), 0,
                                "IMAGE", "missing.png", "image/png", 100, 160, 100, 0, 0);
                        repository.saveMediaProduct(new vn.ptit.btl16.server.media.MediaProductCommit(0, sellerId,
                                "mysql_seller", "MISSING_PAYLOAD", "Must rollback", TestMedia.DESCRIPTION, List.of(missingPayload)));
                        throw new AssertionError("missing binary payload must fail");
                    } catch (AuctionRepositoryException expected) {
                        TestSupport.check(repository.findProductsByOwner(sellerId).stream().noneMatch(value -> value.getName().equals("Must rollback")), "failed BLOB write rolls back product and metadata");
                    }
                    var close = new CloseAuctionCommit(roomId, room.getCurrentWinnerId(), room.getCurrentWinnerUsername(), room.getCurrentPrice(), room.getEndTime(), room.getEndTime());
                    repository.closeAuction(close); repository.closeAuction(close);
                    TestSupport.equals("PAID", ok(new AuctionReceiptApi(buyer).get(roomId)).get("walletStatus"), "MySQL confirmed receipt reconciles payment");
                    TestSupport.equals("1", ok(wallet.entries(Map.of("auctionId", Long.toString(roomId), "type", "PURCHASE"))).get("entryCount"), "MySQL own session ledger filter");
                    var inbox = ok(buyer.sendRequest(vn.ptit.btl16.common.protocol.MessageType.GET_MY_NOTIFICATIONS, Map.of()).thenApply(ApiResponse::new));
                    TestSupport.equals("3", inbox.get("count"), "MySQL notifications exactly once for approval, rejection, result");
                    for (int retry = 0; retry < 2; retry++) { ok(buyer.sendRequest(vn.ptit.btl16.common.protocol.MessageType.READ_MY_NOTIFICATION,
                            Map.of("notificationId", inbox.get("notification.0.id"), "read", "true")).thenApply(ApiResponse::new)); }
                    TestSupport.equals(1, repository.operationEvents(roomId, "BID_ACCEPTED", null, null, Long.MAX_VALUE, 10).size(), "MySQL operator log filter");
                    money("1890", server.wallets().account(buyerId).balance()); money("106.70", server.wallets().account(sellerId).balance());
                    try (var connection = factory.openDatabase(); var query = connection.createStatement();
                         var rows = query.executeQuery("SELECT COUNT(*), MAX(OCTET_LENGTH(payload)) FROM media_content")) {
                        rows.next(); TestSupport.check(rows.getLong(1) > 5 && rows.getLong(2) <= 262144, "media binary chunks reside in MySQL, bounded below packet size");
                    }
                }
            }
            TestSupport.equals(firstWorkspaces, mediaWorkspaces(), "upload, HTTP and shutdown create no temporary media files");
            try (ServerApplication restarted = ServerApplication.create(config)) {
                restarted.start(); TestSupport.equals(firstWorkspaces, mediaWorkspaces(), "restart creates no disk cache");
                try (var seller = connect(restarted)) {
                    ApiResponse identity = ok(new AccountApi(seller).login("mysql_seller", "Password123!"));
                    ProductMediaApi media = media(new AuctionApi(seller), identity); JsonNode manifest = media.manifest(productId, 0).get(15, TimeUnit.SECONDS);
                    String originalUrl = manifest.path("media").get(0).path("original").asText();
                    assertBytes(original, download(media, originalUrl, null).body(), "original streamed exclusively from MySQL after restart");
                    assertBytes(Arrays.copyOfRange(original, 262137, 262170),
                            download(media, originalUrl, "bytes=262137-262169").body(), "Range spans database chunk boundary");
                    assertBytes(Arrays.copyOfRange(original, original.length - 19, original.length),
                            download(media, originalUrl, "bytes=-19").body(), "suffix Range streams directly from database");
                    var head = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(media.url(originalUrl)))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
                    TestSupport.equals(200, head.statusCode(), "HEAD reads database media metadata");
                    TestSupport.equals(Long.toString(original.length), head.headers().firstValue("Content-Length").orElse(""), "HEAD reports original length");
                    TestSupport.equals(0, head.body().length, "HEAD does not stream payload");
                    assertBytes(videoBytes, download(media, manifest.path("media").get(1).path("video").asText(), null).body(), "video restored exclusively from MySQL");
                    var range = download(media, manifest.path("media").get(1).path("video").asText(), "bytes=7-37");
                    TestSupport.equals(206, range.statusCode(), "video Range survives database storage");
                    assertBytes(Arrays.copyOfRange(videoBytes, 7, 38), range.body(), "video Range bytes match");
                    money("1890", restarted.wallets().account(buyerId).balance()); money("106.70", restarted.wallets().account(sellerId).balance());
                    TestSupport.equals("PAID", ok(new AuctionReceiptApi(seller).get(roomId)).get("walletStatus"), "MySQL receipt survives restart");
                }
            }
            TestSupport.equals(firstWorkspaces, mediaWorkspaces(), "restart and all reads leave no media cache");
            System.out.println("[PASS] MysqlStorageSelfTest: real XAMPP only, >1MB image chunks, video, wallet, exact-once transfer, zero media files, restart, HEAD and cross-chunk/suffix Range");
        } finally {
            if (factory != null && factory.getConfig().getDbName().equals(database)) { DatabaseSchema.dropDatabase(factory); }
            if (previous == null) { System.clearProperty("btl16.server.config"); } else { System.setProperty("btl16.server.config", previous); }
        }
    }
    private static Set<Path> mediaWorkspaces() throws Exception {
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.filter(path -> path.getFileName().toString().startsWith("btl16-mysql-media-")).collect(java.util.stream.Collectors.toSet());
        }
    }
    private static WebSocketClientTransport connect(ServerApplication server) throws Exception { var client = new WebSocketClientTransport("ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws", 2097152, 5000, 10000); client.connect().get(15, TimeUnit.SECONDS); return client; }
    private static ApiResponse register(WebSocketClientTransport client, String username) throws Exception { var account = new AccountApi(client); ok(account.register(username, "Password123!", username, "", "")); return ok(account.login(username, "Password123!")); }
    private static ProductMediaApi media(AuctionApi api, ApiResponse identity) throws Exception { return new ProductMediaApi(URI.create("http://127.0.0.1:" + ok(api.mediaEndpoint()).get("mediaPort") + "/"), () -> identity.get("sessionToken")); }
    private static HttpResponse<byte[]> download(ProductMediaApi api, String url, String range) throws Exception { var request = HttpRequest.newBuilder(URI.create(api.url(url))); if (range != null) { request.header("Range", range); } var response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofByteArray()); TestSupport.check(response.statusCode() == (range == null ? 200 : 206), "media HTTP response: " + response.statusCode()); return response; }
    private static ApiResponse ok(CompletableFuture<ApiResponse> response) throws Exception { var result = response.get(30, TimeUnit.SECONDS); TestSupport.check(result.isSuccess(), result.getErrorCode() + ": " + result.getMessageText()); return result; }
    private static void assertBytes(byte[] expected, byte[] actual, String message) throws Exception { TestSupport.check(Arrays.equals(MessageDigest.getInstance("SHA-256").digest(expected), MessageDigest.getInstance("SHA-256").digest(actual)), message); }
    private static void money(String expected, BigDecimal actual) { TestSupport.check(new BigDecimal(expected).compareTo(actual) == 0, "persistent exact wallet balance: " + actual); }
}
