package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.wallet.WalletRepository;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class WalletSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String previous = System.getProperty("btl16.server.config"), previousData = System.getProperty("btl16.dataDir");
        Path directory = Files.createTempDirectory("btl16-wallet-test-"); Path configFile = directory.resolve("server.properties");
        Files.writeString(configFile, "server.tcp.enabled=false\nserver.websocket.port=0\nmedia.port=0\ndb.driver=org.h2.Driver\ndb.user=sa\ndb.name=wallet\nsecurity.passwordIterations=10000\nauction.closedVisibilitySeconds=2\n");
        System.setProperty("btl16.server.config", configFile.toString()); System.setProperty("btl16.dataDir", directory.toString());
        try {
            ServerConfig config = ServerConfig.loadDefault();
            try (ServerApplication server = ServerApplication.create(config)) {
                server.start(); String endpoint = "ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws";
                try (var seller = connect(endpoint); var first = connect(endpoint); var second = connect(endpoint); var anonymous = connect(endpoint)) {
                    long sellerId = register(seller, "wallet_seller"), firstId = register(first, "wallet_first"), secondId = register(second, "wallet_second");
                    WalletRepository wallets = server.wallets(); WalletApi firstWallet = new WalletApi(first), secondWallet = new WalletApi(second);
                    error(ErrorCode.AUTH_REQUIRED, new WalletApi(anonymous).get());
                    error(ErrorCode.AUTH_REQUIRED, new WalletApi(anonymous).get(1));
                    money("0", wallets.account(firstId).balance(), "new account starts at zero");
                    var own = ok(first.sendRequest(MessageType.GET_WALLET, Map.of("userId", Long.toString(secondId))).thenApply(ApiResponse::new));
                    TestSupport.equals(Long.toString(firstId), own.get("userId"), "client cannot read another wallet by changing userId");
                    String id = UUID.randomUUID().toString();
                    ok(firstWallet.submit(id, decimal("1000"))); ok(firstWallet.submit(id, decimal("1000")));
                    money("0", wallets.account(firstId).balance(), "QR confirmation only creates pending request");
                    error(ErrorCode.TOPUP_PENDING, firstWallet.submit(UUID.randomUUID().toString(), decimal("1000")));
                    error(ErrorCode.VALIDATION_ERROR, firstWallet.submit(id, decimal("2000")));
                    error(ErrorCode.INVALID_AMOUNT, secondWallet.submit(UUID.randomUUID().toString(), decimal("1000.001")));
                    error(ErrorCode.INVALID_AMOUNT, secondWallet.submit(UUID.randomUUID().toString(), decimal("-1")));
                    CountDownLatch gate = new CountDownLatch(1);
                    CompletableFuture<?> approveOne = CompletableFuture.runAsync(() -> { await(gate); wallets.review(id, true); });
                    CompletableFuture<?> approveTwo = CompletableFuture.runAsync(() -> { await(gate); wallets.review(id, true); });
                    gate.countDown(); CompletableFuture.allOf(approveOne, approveTwo).get(15, TimeUnit.SECONDS);
                    ok(firstWallet.submit(id, decimal("1000"))); money("1000", wallets.account(firstId).balance(), "approval/retry cannot credit twice");
                    TestSupport.equals(1, wallets.entries(firstId).size(), "approval has exactly one ledger entry");
                    String rejected = UUID.randomUUID().toString(); ok(secondWallet.submit(rejected, decimal("1000"))); wallets.review(rejected, false);
                    wallets.review(rejected, true); money("0", wallets.account(secondId).balance(), "rejected request cannot later be credited");
                    String approved = UUID.randomUUID().toString(); ok(secondWallet.submit(approved, decimal("1000"))); wallets.review(approved, true);
                    AuctionApi host = new AuctionApi(seller), buyer = new AuctionApi(first), rival = new AuctionApi(second);
                    long room = room(host, "WALLET_SALE", "100", "10"); ok(buyer.join(room)); ok(rival.join(room));
                    ok(new AccountApi(first).updateProfile("phước", "", ""));
                    List<WireMessage> bidderEvents = new CopyOnWriteArrayList<>(); second.addEventListener(bidderEvents::add);
                    var acceptedBid = ok(buyer.bid(room, decimal("110")));
                    TestSupport.equals("phước", ClientWireParser.bid(acceptedBid.getWireMessage().getData(), "bid.").getDisplayName(), "accepted bid includes current profile display name");
                    TestSupport.equals("wallet_first", acceptedBid.get("bid.username"), "display name does not replace login identity");
                    TestSupport.waitUntil(() -> bidderEvents.stream().anyMatch(event -> event.getType() == MessageType.BID_UPDATE
                            && event.get("bid.displayName").equals("phước")), 3000, "other room members receive bidder display name");
                    ok(new AccountApi(first).updateProfile("Phước mới", "", ""));
                    money("110", wallets.account(firstId).reserved(), "accepted bid reserves full price");
                    money("890", wallets.account(firstId).available(), "available balance excludes hold");
                    var roomWallet = ok(firstWallet.get(room));
                    money("110", decimal(roomWallet.get("roomHeld")), "room API reports own committed hold");
                    money("890", decimal(roomWallet.get("available")), "room API reports spendable balance");
                    TestSupport.check(!roomWallet.getWireMessage().getData().containsKey("userId"), "room summary does not expose account identity");
                    var forgedRoomWallet = ok(first.sendRequest(MessageType.GET_WALLET,
                            Map.of("auctionId", Long.toString(room), "userId", Long.toString(secondId))).thenApply(ApiResponse::new));
                    money("110", decimal(forgedRoomWallet.get("roomHeld")), "room hold owner is always the session");
                    money("0", decimal(ok(secondWallet.get(room)).get("roomHeld")), "other bidder cannot read leader hold");
                    ok(buyer.leave(room)); money("110", wallets.account(firstId).reserved(), "leaving never cancels a funded bid");
                    ok(rival.bid(room, decimal("120"))); money("0", wallets.account(firstId).reserved(), "outbid bidder is released atomically");
                    long expensive = room(host, "WALLET_EXPENSIVE", "800", "100"); ok(rival.join(expensive));
                    var expensiveWallet = ok(secondWallet.get(expensive));
                    money("0", decimal(expensiveWallet.get("roomHeld")), "other-room hold is not displayed as this-room hold");
                    money("880", decimal(expensiveWallet.get("available")), "availability includes holds from all rooms");
                    error(ErrorCode.INSUFFICIENT_FUNDS, rival.bid(expensive, decimal("900")));
                    money("120", wallets.account(secondId).reserved(), "rejected bid preserves other holds");
                    JdbcAuctionRepository repository = new JdbcAuctionRepository(new JdbcConnectionFactory(config));
                    TestSupport.equals(0, repository.findRecentBids(expensive, 10).size(), "unfunded bid is never persisted");
                    TestSupport.equals("Phước mới", repository.findRecentBids(room, 10).stream().filter(bid -> bid.getUserId() == firstId)
                            .findFirst().orElseThrow().getDisplayName(), "history reads saved profile display name");
                    long raceOne = room(host, "WALLET_RACE_ONE", "500", "100"), raceTwo = room(host, "WALLET_RACE_TWO", "500", "100");
                    AuctionSnapshot firstRoom = repository.findAuctionById(raceOne).orElseThrow(), secondRoom = repository.findAuctionById(raceTwo).orElseThrow();
                    CountDownLatch bidGate = new CountDownLatch(1);
                    CompletableFuture<Boolean> bidOne = CompletableFuture.supplyAsync(() -> bid(repository, firstRoom, firstId, 100001, bidGate));
                    CompletableFuture<Boolean> bidTwo = CompletableFuture.supplyAsync(() -> bid(repository, secondRoom, firstId, 100002, bidGate));
                    bidGate.countDown(); TestSupport.equals(1, (bidOne.get(15, TimeUnit.SECONDS) ? 1 : 0) + (bidTwo.get(15, TimeUnit.SECONDS) ? 1 : 0), "cross-room bids cannot spend same balance twice");
                    money("600", wallets.account(firstId).reserved(), "only one concurrent hold commits");
                    long fundedRoom = bidOne.join() ? raceOne : raceTwo, unfundedRoom = bidOne.join() ? raceTwo : raceOne;
                    TestSupport.check(repository.findPurchasedProducts(firstId).isEmpty(), "leading an unfinished auction does not grant ownership");
                    TestSupport.equals("Phước mới", repository.findRecentBids(fundedRoom, 10).getFirst().getDisplayName(), "new bids reflect profile changes without logging in again");
                    AuctionSnapshot empty = repository.findAuctionById(unfundedRoom).orElseThrow();
                    BigDecimal heldBefore = wallets.account(secondId).reserved();
                    try {
                        repository.commitAcceptedBid(new BidCommit(empty.getAuctionId(), empty.getCurrentPrice(), secondId, "wallet_second", decimal("600"),
                                bidOne.join() ? 100001 : 100002, java.time.Instant.now(), empty.getEndTime()));
                        throw new AssertionError("duplicate ledger/sequence should fail");
                    } catch (AuctionRepositoryException expected) { }
                    money(heldBefore.toPlainString(), wallets.account(secondId).reserved(), "DB failure rolls back reservation and bid together");
                    close(repository, room); close(repository, fundedRoom); close(repository, unfundedRoom); close(repository, expensive);
                    var buyerCatalog = ClientWireParser.products(ok(buyer.myProducts()).getWireMessage().getData());
                    long acquiredProduct = repository.findAuctionById(fundedRoom).orElseThrow().getProduct().getProductId();
                    var acquired = buyerCatalog.stream().filter(product -> product.getProductId() == acquiredProduct).findFirst().orElseThrow();
                    TestSupport.equals(vn.ptit.btl16.common.protocol.ProductTransactionStatus.OWNED, acquired.getTransactionStatus(), "winner catalog contains the acquired product with owned status");
                    TestSupport.check(!acquired.canAuction(), "acquired product is excluded from auction creation");
                    TestSupport.equals(sellerId, acquired.getOwnerId(), "original seller identity remains intact for receipts and revenue");
                    TestSupport.check(repository.findPurchasedProducts(secondId).stream().noneMatch(product -> product.getProductId() == acquiredProduct), "other buyers do not own the winner's item");
                    var sellerCatalog = ClientWireParser.products(ok(host.myProducts()).getWireMessage().getData());
                    TestSupport.equals(ProductTransactionStatus.SOLD, sellerCatalog.stream().filter(product -> product.getProductId() == acquiredProduct).findFirst().orElseThrow().getTransactionStatus(), "seller still sees sold status");
                    error(ErrorCode.AUCTION_FORBIDDEN, buyer.deleteProduct(acquiredProduct));
                    error(ErrorCode.AUCTION_FORBIDDEN, buyer.createAuction(acquiredProduct, decimal("100"), decimal("10"), 1));
                    money("678.00", wallets.account(sellerId).balance(), "first sale pays ten percent platform fee, next sale gets rank one reduction");
                    money("400", wallets.account(firstId).balance(), "first buyer charged once"); money("880", wallets.account(secondId).balance(), "second buyer charged once");
                    money("0", wallets.account(firstId).reserved(), "purchase releases its reservation");
                    int entriesBefore = wallets.entries(sellerId).size(); close(repository, room);
                    TestSupport.equals(entriesBefore, wallets.entries(sellerId).size(), "repeated close never settles twice");
                    money("42.00", fees(config), "platform fees with rank reduction are stored durably");
                    money("2000", wallets.accounts().stream().map(value -> value.balance()).reduce(BigDecimal.ZERO, BigDecimal::add).add(fees(config)), "wallets plus platform fees conserve all money");
                    long privateProduct = Long.parseLong(ok(TestMedia.create(host, "PRIVATE_WALLET", "Private room", TestMedia.DESCRIPTION)).get("productId"));
                    long privateRoom = Long.parseLong(ok(host.createAuction(privateProduct, decimal("1"), decimal("0.01"), 1, "PRIVATE", "secret", true)).get("auctionId"));
                    error(ErrorCode.ROOM_ACCESS_DENIED, secondWallet.get(privateRoom));
                    List<WireMessage> events = new CopyOnWriteArrayList<>(); seller.addEventListener(events::add);
                    ok(host.join(privateRoom)); ok(buyer.join(privateRoom, "secret"));
                    var joinedRival = ok(rival.join(privateRoom, "secret"));
                    ok(new RoomSocialApi(seller).kick(privateRoom, Long.parseLong(joinedRival.get("selfParticipantId"))));
                    error(ErrorCode.USER_BLOCKED_FROM_AUCTION, secondWallet.get(privateRoom));
                    var anonymousBid = ok(buyer.bid(privateRoom, decimal("1.01")));
                    TestSupport.check(anonymousBid.get("bid.displayName").startsWith("Khách #"), "anonymous voice receives only the guest alias");
                    TestSupport.equals(anonymousBid.get("bid.username"), anonymousBid.get("bid.displayName"), "anonymous display name matches public alias");
                    var anonymousWallet = ok(firstWallet.get(privateRoom));
                    money("1.01", decimal(anonymousWallet.get("roomHeld")), "anonymous owner sees exact cents in own hold");
                    TestSupport.check(!anonymousWallet.getWireMessage().getData().containsKey("userId")
                            && !anonymousWallet.getWireMessage().getData().containsKey("username"), "anonymous wallet does not expose real identity");
                    var forgedHostWallet = ok(seller.sendRequest(MessageType.GET_WALLET,
                            Map.of("auctionId", Long.toString(privateRoom), "userId", Long.toString(firstId))).thenApply(ApiResponse::new));
                    money("0", decimal(forgedHostWallet.get("roomHeld")), "host cannot query anonymous leader wallet");
                    for (WireMessage event : events) {
                        TestSupport.check(!event.getData().containsKey("available") && !event.getData().containsKey("roomHeld"), "private wallet never enters broadcast payloads");
                        TestSupport.check(!event.getData().containsValue("Phước mới"), "anonymous broadcasts never expose profile display name");
                    }
                    close(repository, privateRoom);
                    ok(new AccountApi(first).logout()); ok(new AccountApi(first).login("wallet_first", "Password123!"));
                    var wonPrivate = ClientWireParser.products(ok(buyer.myProducts()).getWireMessage().getData()).stream()
                            .filter(product -> product.getProductId() == privateProduct).findFirst().orElseThrow();
                    TestSupport.equals(ProductTransactionStatus.OWNED, wonPrivate.getTransactionStatus(), "private purchase persists after a new login");
                    var cover = ok(first.sendRequest(MessageType.GET_PRODUCT_IMAGE, Map.of("productId", Long.toString(privateProduct))).thenApply(ApiResponse::new));
                    TestSupport.check(!cover.get("imageBase64").isBlank(), "winner can read purchased private product images after room access expires");
                    repository.deleteProduct(privateProduct, sellerId);
                    TestSupport.check(repository.findPurchasedProducts(firstId).stream().anyMatch(product -> product.getProductId() == privateProduct), "seller catalog deletion preserves the winner's acquired product");
                    new vn.ptit.btl16.server.auction.service.AuctionQueryService(new vn.ptit.btl16.server.auction.service.AuctionManager(repository, 60), repository, 50)
                            .listAuctions().forEach(value -> TestSupport.check(value.getStatus().name().equals("OPEN"), "marketplace excludes closed rooms immediately"));
                }
            }
            try (ServerApplication restarted = ServerApplication.create(config)) {
                var accounts = restarted.wallets().accounts();
                money("2000", accounts.stream().map(value -> value.balance()).reduce(BigDecimal.ZERO, BigDecimal::add).add(fees(config)), "wallets and platform fees persist across server restart");
                TestSupport.check(accounts.stream().allMatch(value -> value.reserved().signum() == 0), "no dangling holds after paid closes");
            }
            System.out.println("[PASS] WalletSelfTest: zero initial balance, auth/privacy, pending/reject/approve/idempotency, durable ledger, holds/outbid/leave, insufficient funds, cross-room race, rollback, exact-once settlement and restart");
        } finally { restore("btl16.server.config", previous); restore("btl16.dataDir", previousData); }
    }
    private static WebSocketClientTransport connect(String endpoint) throws Exception { var client = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000); client.connect().get(10, TimeUnit.SECONDS); return client; }
    private static BigDecimal fees(ServerConfig config) throws Exception {
        try (var connection = new JdbcConnectionFactory(config).openDatabase();
             var query = connection.prepareStatement("SELECT COALESCE(SUM(platform_fee), 0) FROM wallet_settlements WHERE status='PAID'"); var rows = query.executeQuery()) {
            rows.next(); return rows.getBigDecimal(1);
        }
    }
    private static long register(WebSocketClientTransport client, String username) throws Exception { AccountApi api = new AccountApi(client); ok(api.register(username, "Password123!", username, "", "")); return Long.parseLong(ok(api.login(username, "Password123!")).get("userId")); }
    private static long room(AuctionApi api, String code, String price, String increment) throws Exception { long product = Long.parseLong(ok(TestMedia.create(api, code, code, TestMedia.DESCRIPTION)).get("productId")); return Long.parseLong(ok(api.createAuction(product, decimal(price), decimal(increment), 1)).get("auctionId")); }
    private static boolean bid(JdbcAuctionRepository repository, AuctionSnapshot room, long userId, long sequence, CountDownLatch gate) {
        await(gate);
        try { repository.commitAcceptedBid(new BidCommit(room.getAuctionId(), room.getCurrentPrice(), userId, "wallet_first", decimal("600"), sequence, java.time.Instant.now(), room.getEndTime())); return true; }
        catch (AuctionException exception) { TestSupport.equals(ErrorCode.INSUFFICIENT_FUNDS, exception.getErrorCode(), "race loser has insufficient available money"); return false; }
    }
    private static void close(JdbcAuctionRepository repository, long room) { AuctionSnapshot auction = repository.findAuctionById(room).orElseThrow(); repository.closeAuction(new CloseAuctionCommit(room, auction.getCurrentWinnerId(), auction.getCurrentWinnerUsername(), auction.getCurrentPrice(), auction.getEndTime(), auction.getEndTime())); }
    private static BigDecimal decimal(String text) { return new BigDecimal(text); }
    private static void money(String expected, BigDecimal actual, String message) { TestSupport.check(decimal(expected).compareTo(actual) == 0, message + ": " + actual); }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception { ApiResponse response = future.get(15, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + ": " + response.getMessageText()); return response; }
    private static void error(ErrorCode code, CompletableFuture<ApiResponse> future) throws Exception { ApiResponse response = future.get(15, TimeUnit.SECONDS); TestSupport.equals(code.name(), response.getErrorCode(), "expected wallet error"); }
    private static void await(CountDownLatch gate) { try { gate.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); } }
    private static void restore(String name, String value) { if (value == null) { System.clearProperty(name); } else { System.setProperty(name, value); } }
}
