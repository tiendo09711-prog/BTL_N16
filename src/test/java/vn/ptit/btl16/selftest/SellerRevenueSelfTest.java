package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.*;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.common.util.AuctionFees;
import vn.ptit.btl16.client.network.TcpClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.*;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.auction.model.*;
import vn.ptit.btl16.server.db.DatabaseSchema;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

/** Isolated real JDBC closures, including concurrent rank-dependent settlement. */
public final class SellerRevenueSelfTest {
    private static long sequence = 50000;
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String previousConfig = System.getProperty("btl16.server.config"), previousData = System.getProperty("btl16.dataDir");
        Path data = Files.createTempDirectory("btl16-seller-revenue-"); Path config = data.resolve("server.properties");
        Files.writeString(config, "db.driver=org.h2.Driver\ndb.name=revenue\ndb.user=sa\ndb.password=\ndb.autoInitialize=false\n");
        System.setProperty("btl16.server.config", config.toString()); System.setProperty("btl16.dataDir", data.toString());
        try {
            JdbcConnectionFactory factory = new JdbcConnectionFactory(ServerConfig.loadDefault()); DatabaseSchema.initialize(factory);
            for (int seller = 1; seller <= 20; seller++) { sql(factory, "INSERT INTO users(user_id,username,display_name,password_hash,password_salt,password_iterations,active) VALUES(" + seller + ",'seller" + seller + "','Người bán " + seller + "','x','x',30000,TRUE)"); }
            sql(factory, "INSERT INTO users(user_id,username,display_name,password_hash,password_salt,password_iterations,active,wallet_balance) VALUES(100,'buyer','Buyer','x','x',30000,TRUE,100000000000)");
            JdbcAuctionRepository repository = new JdbcAuctionRepository(factory);
            TestSupport.equals("0", repository.topSellers().get("count"), "no sellers before successful sales");
            long firstRoom = 0;
            for (int seller = 1; seller <= 12; seller++) {
                long room = room(factory, repository, seller); bid(repository, room, BigDecimal.valueOf((13 - seller) * 1000)); close(factory, repository, room);
                if (seller == 1) { firstRoom = room; }
            }
            var board = repository.topSellers(); TestSupport.equals("10", board.get("count"), "bounded ten sellers");
            for (int rank = 1; rank <= 10; rank++) { TestSupport.equals(Integer.toString(rank), board.get("ranking." + (rank - 1) + ".sellerId"), "numeric lifetime revenue order"); }
            money("1200", repository.personalReceipt(1, firstRoom).get("platformFee"), "first sale gets no advance ranking discount");
            int[] reduction = {5,4,3,2,2,1,1,1,1,1,0,0};
            for (int seller = 1; seller <= 12; seller++) {
                long room = room(factory, repository, seller); BigDecimal price = new BigDecimal("100.15"); bid(repository, room, price); close(factory, repository, room);
                var receipt = repository.personalReceipt(seller, room);
                TestSupport.equals("PAID", receipt.get("walletStatus"), "discounted seller receipt reconciles");
                TestSupport.equals(seller <= 10 ? Integer.toString(seller) : "0", receipt.get("revenueRank"), "rank frozen before current sale");
                BigDecimal expectedRate = BigDecimal.valueOf(10 - reduction[seller - 1], 2);
                money(expectedRate.toPlainString(), receipt.get("feeRate"), "all rank discount bands");
                money(price.multiply(expectedRate).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), receipt.get("platformFee"), "discounted rounded tax");
                money(price.toPlainString(), new BigDecimal(receipt.get("sellerNet")).add(new BigDecimal(receipt.get("platformFee"))).toPlainString(), "buyer payment equals seller net plus tax");
            }
            TestSupport.equals("2", repository.topSellers().get("ranking.0.saleCount"), "one row per seller aggregates multiple products");
            money("12100.15", repository.topSellers().get("ranking.0.revenue"), "board uses gross cumulative revenue");
            try (var connection = factory.openDatabase()) { TestSupport.equals(0, SellerRevenueStore.rankBeforeSale(connection, 11), "outside the ten has no discount"); }
            long noBids = room(factory, repository, 13); close(factory, repository, noBids);
            long cancelled = room(factory, repository, 13); repository.cancelAuction(new CancelAuctionCommit(cancelled, Instant.now()));
            room(factory, repository, 13);
            TestSupport.check(!repository.topSellers().containsValue("seller13"), "open, unsold and cancelled do not generate revenue");
            // Equal real sales and first-sale timestamps fall back to the stable account ID.
            for (int seller : new int[]{15,16}) { long room = room(factory, repository, seller); bid(repository, room, new BigDecimal("20000.50")); close(factory, repository, room); }
            sql(factory, "UPDATE auction_receipt_summaries SET ended_at='2026-10-01 10:00:00' WHERE auction_id IN (SELECT auction_id FROM auctions WHERE host_user_id IN (15,16))");
            TestSupport.equals("15", repository.topSellers().get("ranking.0.sellerId"), "equal revenue and first-sale time prefer lower seller ID");
            TestSupport.equals("16", repository.topSellers().get("ranking.1.sellerId"), "equal revenue keeps separate sellers");
            sql(factory, "UPDATE auction_receipt_summaries SET ended_at='2026-09-01 10:00:00' WHERE auction_id IN (SELECT auction_id FROM auctions WHERE host_user_id=16)");
            TestSupport.equals("16", repository.topSellers().get("ranking.0.sellerId"), "earlier first sale wins equal revenue even with higher ID");
            for (String priceText : List.of("999999999.99", "1000000000.00", "1000000000.01")) {
                BigDecimal price = new BigDecimal(priceText); long room = room(factory, repository, 14); bid(repository, room, price); close(factory, repository, room);
                var receipt = repository.personalReceipt(14, room);
                money(price.compareTo(AuctionFees.THRESHOLD) <= 0 ? "0.10" : "0.15", receipt.get("baseFeeRate"), "inclusive billion threshold");
            }
            money("0.05", AuctionFees.fee(new BigDecimal("0.50")).toPlainString(), "half-up rounding");
            // Same seller's simultaneous sales must see the first committed sale before the second rank snapshot.
            long one = room(factory, repository, 20), two = room(factory, repository, 20);
            bid(repository, one, new BigDecimal("4000000000")); bid(repository, two, new BigDecimal("4000000000"));
            CloseAuctionCommit first = expired(factory, repository, one), second = expired(factory, repository, two);
            CountDownLatch gate = new CountDownLatch(1);
            var a = CompletableFuture.supplyAsync(() -> concurrentClose(repository, first, gate));
            var b = CompletableFuture.supplyAsync(() -> concurrentClose(repository, second, gate)); gate.countDown();
            TestSupport.check(a.get(20, TimeUnit.SECONDS) && b.get(20, TimeUnit.SECONDS), "both concurrent sales complete");
            List<String> rates = new ArrayList<>();
            for (long room : List.of(one, two)) { rates.add(new BigDecimal(repository.personalReceipt(20, room).get("feeRate")).stripTrailingZeros().toPlainString()); }
            Collections.sort(rates); TestSupport.equals(List.of("0.1", "0.15"), rates, "first sale unranked, next uses new rank one");
            TestSupport.check(repository.closeAuction(first).isEmpty(), "retry never charges again");
            // Simulate an already paid three-percent settlement from the previous release.
            sql(factory, "UPDATE wallet_settlements SET base_fee_rate=0,fee_discount_rate=0,revenue_rank=0,fee_rate=0.03,platform_fee=360 WHERE auction_id=" + firstRoom);
            sql(factory, "UPDATE wallet_entries SET amount=11640 WHERE reference_key='SELL:" + firstRoom + "'");
            sql(factory, "UPDATE users SET wallet_balance=wallet_balance+840 WHERE user_id=1");
            var history = repository.personalReceipt(20, one); var finalBoard = repository.topSellers();
            DatabaseSchema.initialize(factory); DatabaseSchema.initialize(factory);
            var old = repository.personalReceipt(1, firstRoom);
            TestSupport.equals("PAID", old.get("walletStatus"), "historical three-percent receipt still reconciles");
            money("360", old.get("platformFee"), "historical fee never recalculated during upgrade");
            TestSupport.check(!old.containsKey("baseFeeRate"), "historical receipt not assigned a new discount");
            TestSupport.equals(finalBoard, new JdbcAuctionRepository(factory).topSellers(), "ranking survives schema restart");
            TestSupport.equals(history, repository.personalReceipt(20, one), "historical rates and rank do not change on restart");
            network(factory, repository);
            System.out.println("[PASS] SellerRevenueSelfTest: seller totals/Top10, all discounts, exact billion boundary, rounding, atomic concurrent rank snapshots, immutable history, authenticated public API");
        } finally { restore("btl16.server.config", previousConfig); restore("btl16.dataDir", previousData); }
    }
    private static void network(JdbcConnectionFactory factory, JdbcAuctionRepository repository) throws Exception {
        var config = ServerConfig.forTransportTests(0, 0); var hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        try (var server = ServerApplication.createForTests(config, TestUserRepository.withDemoUsers(hasher), repository, hasher)) {
            server.start();
            try (var client = new TcpClientTransport("127.0.0.1", server.getBoundPort(), 2097152, 5000, 10000)) {
                client.connect().get(10, TimeUnit.SECONDS);
                TestSupport.equals("AUTH_REQUIRED", new ApiResponse(client.sendRequest(MessageType.GET_REVENUE_RANKING, Map.of()).get(10, TimeUnit.SECONDS)).getErrorCode(), "revenue endpoint authenticated");
                TestSupport.check(new AccountApi(client).login("alice", "alice123").get(10, TimeUnit.SECONDS).isSuccess(), "fixture login");
                var response = new ApiResponse(client.sendRequest(MessageType.GET_REVENUE_RANKING, Map.of()).get(10, TimeUnit.SECONDS)); TestSupport.check(response.isSuccess(), "revenue endpoint succeeds");
                TestSupport.equals(repository.topSellers(), response.getWireMessage().getData().entrySet().stream().filter(e -> !Set.of("success", "serverTime", "message").contains(e.getKey())).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), "network board matches settlement ordering");
                TestSupport.check(response.getWireMessage().getData().keySet().stream().noneMatch(k -> k.matches(".*(?i:email|phone|password|balance|buyer|winner).*")), "board contains only public seller data");
            }
        }
    }
    private static long room(JdbcConnectionFactory factory, JdbcAuctionRepository repository, long seller) throws Exception {
        String code = "REV_" + ++sequence;
        var product = TestMedia.create(repository, new CreateProductCommit(1, "seller1", code, code, TestMedia.DESCRIPTION, Instant.now()));
        sql(factory, "UPDATE products SET created_by=" + seller + " WHERE product_id=" + product.getProductId());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return repository.createAuction(new CreateAuctionCommit(seller, "seller" + seller, product.getProductId(), BigDecimal.ONE, BigDecimal.ONE, now.minusSeconds(1), now.plusSeconds(600), RoomVisibility.PUBLIC, "", "", null, false)).getAuctionId();
    }
    private static void bid(JdbcAuctionRepository repository, long room, BigDecimal price) {
        var current = repository.findAuctionById(room).orElseThrow(); repository.commitAcceptedBid(new BidCommit(room, current.getCurrentPrice(), 100, "buyer", price, ++sequence, Instant.now(), current.getEndTime()));
    }
    private static CloseAuctionCommit expired(JdbcConnectionFactory factory, JdbcAuctionRepository repository, long room) throws Exception {
        Instant end = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("UPDATE auctions SET end_time=? WHERE auction_id=?")) { query.setTimestamp(1, Timestamp.from(end)); query.setLong(2, room); query.executeUpdate(); }
        var value = repository.findAuctionById(room).orElseThrow(); return new CloseAuctionCommit(room, value.getCurrentWinnerId(), value.getCurrentWinnerUsername(), value.getCurrentPrice(), end, Instant.now());
    }
    private static void close(JdbcConnectionFactory factory, JdbcAuctionRepository repository, long room) throws Exception { repository.closeAuction(expired(factory, repository, room)).orElseThrow(); }
    private static boolean concurrentClose(JdbcAuctionRepository repository, CloseAuctionCommit commit, CountDownLatch gate) { try { gate.await(); return repository.closeAuction(commit).isPresent(); } catch (Exception error) { throw new CompletionException(error); } }
    private static void sql(JdbcConnectionFactory factory, String sql) throws Exception { try (var connection = factory.openDatabase(); var statement = connection.createStatement()) { statement.executeUpdate(sql); } }
    private static void money(String expected, String actual, String message) { TestSupport.check(new BigDecimal(expected).compareTo(new BigDecimal(actual)) == 0, message + ": " + actual); }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
