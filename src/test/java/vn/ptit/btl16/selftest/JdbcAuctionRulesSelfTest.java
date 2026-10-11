package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.repository.BidCommit;
import vn.ptit.btl16.server.auction.repository.CreateAuctionCommit;
import vn.ptit.btl16.server.auction.repository.CreateProductCommit;
import vn.ptit.btl16.server.auction.repository.JdbcAuctionRepository;
import vn.ptit.btl16.server.auction.repository.AuctionConflictException;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.db.DatabaseSchema;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class JdbcAuctionRulesSelfTest {
    private JdbcAuctionRulesSelfTest() {
    }

    public static void run(JdbcConnectionFactory factory) throws Exception {
        TestSupport.check(factory.getConfig().getDbName().startsWith("btl16_verify_"), "only an isolated test database is allowed");
        try (Connection connection = factory.openDatabase(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users(user_id, username, display_name, password_hash, password_salt, password_iterations, active) "
                    + "VALUES(2, 'buyer', 'Buyer', 'test', 'test', 30000, TRUE), (3, 'viewer', 'Viewer', 'test', 'test', 30000, TRUE)");
        }
        JdbcAuctionRepository repository = new JdbcAuctionRepository(factory);
        AuctionRulesSelfTest.basicRules(repository);
        Product product = TestMedia.create(repository, new CreateProductCommit(1L, "host", "DB_RACE", "Database race", "", Instant.now()));
        Instant start = Instant.now();
        CreateAuctionCommit commit = new CreateAuctionCommit(1L, "host", product.getProductId(),
                new BigDecimal("100"), new BigDecimal("10"), start, start.plusSeconds(60));
        CountDownLatch gate = new CountDownLatch(1);
        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> create(repository, commit, gate));
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(() -> create(repository, commit, gate));
        gate.countDown();
        int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
        TestSupport.equals(1, successes, "concurrent creation can open only one auction per product");
        AuctionSnapshot auction = repository.findAllAuctions().stream()
                .filter(value -> value.getProduct().getProductId() == product.getProductId()).findFirst().orElseThrow();
        try {
            repository.commitAcceptedBid(new BidCommit(auction.getAuctionId(), auction.getCurrentPrice(), 2L, "buyer",
                    new BigDecimal("101"), 999L, Instant.now(), auction.getEndTime()));
            throw new AssertionError("JDBC accepted a below-increment bid");
        } catch (AuctionConflictException expected) {
            TestSupport.equals(0, repository.findRecentBids(auction.getAuctionId(), 10).size(), "rejected transaction inserts no bid");
        }
        CountDownLatch bidGate = new CountDownLatch(1);
        long sequence = repository.findMaxServerSequence() + 1;
        CompletableFuture<Boolean> buyerBid = CompletableFuture.supplyAsync(() ->
                bid(repository, auction, 2L, "buyer", sequence, bidGate));
        CompletableFuture<Boolean> viewerBid = CompletableFuture.supplyAsync(() ->
                bid(repository, auction, 3L, "viewer", sequence + 1, bidGate));
        bidGate.countDown();
        int acceptedBids = (buyerBid.get(10, TimeUnit.SECONDS) ? 1 : 0)
                + (viewerBid.get(10, TimeUnit.SECONDS) ? 1 : 0);
        TestSupport.equals(1, acceptedBids, "same-price competing JDBC bids have one winner");
        TestSupport.equals(1, repository.findRecentBids(auction.getAuctionId(), 10).size(),
                "losing concurrent transaction leaves no bid");
        AuctionSnapshot afterBids = repository.findAuctionById(auction.getAuctionId()).orElseThrow();
        TestSupport.equals(new BigDecimal("110.00"), afterBids.getCurrentPrice(), "atomic price update");
        TestSupport.equals(auction.getVersion() + 1, afterBids.getVersion(), "one accepted bid increments version once");
        try (Connection connection = factory.openDatabase(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE auctions DROP COLUMN hard_end_time");
        }
        DatabaseSchema.initialize(factory);
        Instant migratedCap = repository.findAuctionById(auction.getAuctionId()).orElseThrow().getHardEndTime();
        TestSupport.equals(auction.getEndTime().plusSeconds(600), migratedCap, "legacy rows receive a one-time deadline cap");
        DatabaseSchema.initialize(factory);
        TestSupport.equals(migratedCap, repository.findAuctionById(auction.getAuctionId()).orElseThrow().getHardEndTime(),
                "reinitialization does not reset an existing cap");
        try (Connection connection = factory.openDatabase(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM auctions WHERE product_id = " + product.getProductId())) {
            result.next();
            TestSupport.equals(1L, result.getLong(1), "database contains exactly one competing room");
        }
        System.out.println("[PASS] JdbcAuctionRulesSelfTest: real transactions, ownership/freeze, caps, concurrent creation/bids and migration");
    }

    private static boolean bid(JdbcAuctionRepository repository, AuctionSnapshot auction,
            long userId, String username, long sequence, CountDownLatch gate) {
        try {
            TestSupport.check(gate.await(5, TimeUnit.SECONDS), "bid gate released");
            repository.commitAcceptedBid(new BidCommit(auction.getAuctionId(), auction.getCurrentPrice(),
                    userId, username, new BigDecimal("110"), sequence, Instant.now(), auction.getEndTime()));
            return true;
        } catch (AuctionConflictException expected) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static boolean create(JdbcAuctionRepository repository, CreateAuctionCommit commit, CountDownLatch gate) {
        try {
            TestSupport.check(gate.await(5, TimeUnit.SECONDS), "creation gate released");
            repository.createAuction(commit);
            return true;
        } catch (AuctionException expected) {
            TestSupport.equals(ErrorCode.AUCTION_ALREADY_EXISTS, expected.getErrorCode(), "duplicate auction rejection");
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
