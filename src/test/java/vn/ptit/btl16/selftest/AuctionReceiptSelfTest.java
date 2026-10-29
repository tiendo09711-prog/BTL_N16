package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.*;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.model.*;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.auction.service.*;
import vn.ptit.btl16.server.db.DatabaseSchema;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

public final class AuctionReceiptSelfTest {
    private static long sequence = 1000;
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        Path folder = Files.createTempDirectory("btl16-receipts-").toRealPath(), configFile = folder.resolve("server.properties");
        Files.writeString(configFile, "db.driver=org.h2.Driver\ndb.name=receipts\ndb.user=sa\ndb.password=\ndb.autoInitialize=false\n");
        System.setProperty("btl16.server.config", configFile.toString()); System.setProperty("btl16.dataDir", folder.toString());
        try {
            var factory = new JdbcConnectionFactory(ServerConfig.loadDefault()); DatabaseSchema.initialize(factory); DatabaseSchema.initialize(factory);
            execute(factory, "INSERT INTO users(user_id,username,display_name,password_hash,password_salt,password_iterations,active,wallet_balance) VALUES "
                    + "(1,'demo','Host','x','x',30000,TRUE,1000000),(2,'alice','Alice','x','x',30000,TRUE,1000000),(3,'bob','Bob','x','x',30000,TRUE,1000000),"
                    + "(4,'watcher','Watcher','x','x',30000,TRUE,1000000),(5,'stranger','Stranger','x','x',30000,TRUE,1000000)");
            var repository = new JdbcAuctionRepository(factory);
            var auction = room(repository, "ANON_PRIVATE", true, 600); long room = auction.getAuctionId();
            TestSupport.check(repository.extendAuction(new ExtendAuctionCommit(room, auction.getEndTime(), auction.getEndTime().plusSeconds(10))), "extension before capture");
            repository.appendPresence(room, "JOINED", 4, Instant.now()); repository.appendPresence(room, "LEFT", 4, Instant.now());
            bid(repository, room, 2, 110); bid(repository, room, 3, 120); bid(repository, room, 2, 130);
            denied(() -> repository.personalReceipt(2, room), ErrorCode.AUCTION_FORBIDDEN);
            TestSupport.equals("0", repository.personalReceipts(2, "WON", "ALL", "", Long.MAX_VALUE, 30).get("count"), "leader is never a winner before commit");
            CloseAuctionCommit commit = expire(factory, repository, room);
            var gate = new CountDownLatch(1);
            var first = closeAsync(repository, commit, gate); var second = closeAsync(new JdbcAuctionRepository(factory), commit, gate); gate.countDown();
            TestSupport.equals(1, first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS), "concurrent close commits exactly once");
            TestSupport.check(repository.closeAuction(commit).isEmpty(), "repeat close returns no new payment");
            TestSupport.equals(1L, count(factory, "SELECT COUNT(*) FROM wallet_entries WHERE reference_key = 'BUY:" + room + "'"), "one purchase entry");
            TestSupport.equals(1L, count(factory, "SELECT COUNT(*) FROM wallet_entries WHERE reference_key = 'SELL:" + room + "'"), "one sale entry");
            var winner = repository.personalReceipt(2, room); var loser = repository.personalReceipt(3, room); var host = repository.personalReceipt(1, room); var watcher = repository.personalReceipt(4, room);
            TestSupport.equals(Long.toString(auction.getProduct().getProductId()), winner.get("productId"), "receipt includes its own product");
            TestSupport.equals(TestMedia.DESCRIPTION, winner.get("productDescription"), "receipt includes the frozen product description");
            byte[] previewBytes = Base64.getDecoder().decode(winner.get("productThumbnail"));
            var preview = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(previewBytes));
            TestSupport.check(preview != null && preview.getWidth() <= 120 && preview.getHeight() <= 120 && previewBytes.length <= 24_000, "receipt includes a bounded first-image thumbnail");
            TestSupport.equals(winner.get("productThumbnail"), repository.personalReceipts(2, "WON", "ALL", "", Long.MAX_VALUE, 30).get("receipt.0.productThumbnail"), "list and detail show the same first product image");
            TestSupport.equals("WINNER", winner.get("personalRole"), "confirmed winner role"); TestSupport.equals("PAID", winner.get("walletStatus"), "purchase ledger confirms payment");
            TestSupport.equals("NOT_WON", loser.get("personalRole"), "loser role"); TestSupport.equals("RELEASED", loser.get("walletStatus"), "loser sees real release");
            TestSupport.equals("HOST", host.get("personalRole"), "host role"); TestSupport.equals("PAID", host.get("walletStatus"), "sale ledger confirms credit");
            TestSupport.equals("13.00", host.get("platformFee"), "first sale pays ten percent without a prior rank");
            TestSupport.equals("117.00", host.get("sellerNet"), "seller receives net proceeds");
            TestSupport.equals("WATCHED", watcher.get("personalRole"), "departed watcher retains minimal receipt"); TestSupport.equals("NONE", watcher.get("walletStatus"), "watcher never sees participant wallet");
            TestSupport.equals("0", watcher.get("entryCount"), "watcher ledger empty"); TestSupport.equals("3", winner.get("totalBids"), "whole-session count from cluster2");
            TestSupport.equals("2", winner.get("uniqueBidders"), "distinct bidders"); TestSupport.equals("1", winner.get("extensionCount"), "persisted extension count");
            TestSupport.check(winner.get("winnerDisplay").startsWith("Kh\u00e1ch #"), "stable room-local alias"); anonymous(winner); anonymous(host);
            denied(() -> repository.personalReceipt(5, room), ErrorCode.AUCTION_FORBIDDEN);
            var empty = room(repository, "EMPTY", false, 600); close(repository, factory, empty.getAuctionId());
            var emptyReceipt = repository.personalReceipt(1, empty.getAuctionId()); TestSupport.equals("NO_BIDS", emptyReceipt.get("outcome"), "no bids outcome"); TestSupport.equals("", emptyReceipt.get("finalPrice"), "start price is not a sale price");
            var cancelled = room(repository, "CANCEL", false, 600); repository.appendPresence(cancelled.getAuctionId(), "JOINED", 4, Instant.now());
            TestSupport.check(repository.cancelAuction(new CancelAuctionCommit(cancelled.getAuctionId(), Instant.now())), "cancel commits receipt");
            TestSupport.check(!repository.cancelAuction(new CancelAuctionCommit(cancelled.getAuctionId(), Instant.now())), "repeat cancel is idempotent");
            TestSupport.equals("CANCELLED", repository.personalReceipt(4, cancelled.getAuctionId()).get("outcome"), "cancelled watcher outcome");
            rollback(factory, repository);
            var legacy = room(repository, "LEGACY", true, 600);
            execute(factory, "INSERT INTO bids(auction_id,user_id,amount,server_sequence,created_at) VALUES (" + legacy.getAuctionId() + ",3,110," + ++sequence + ",CURRENT_TIMESTAMP)");
            execute(factory, "UPDATE auctions SET current_price=110,current_winner_id=3,wallet_required=FALSE WHERE auction_id=" + legacy.getAuctionId());
            close(repository, factory, legacy.getAuctionId());
            TestSupport.equals("REVIEW_REQUIRED", repository.personalReceipt(3, legacy.getAuctionId()).get("walletStatus"), "legacy winner not fabricated as paid");
            String alias = winner.get("winnerDisplay");
            execute(factory, "DELETE FROM auction_receipt_summaries WHERE auction_id=" + room); DatabaseSchema.initialize(factory); DatabaseSchema.initialize(factory);
            TestSupport.equals(alias, repository.personalReceipt(2, room).get("winnerDisplay"), "legacy backfill keeps alias");
            TestSupport.equals(1L, count(factory, "SELECT COUNT(*) FROM wallet_entries WHERE reference_key = 'BUY:" + room + "'"), "backfill never settles again");
            execute(factory, "DELETE FROM room_events WHERE auction_id=" + room);
            var restarted = new JdbcAuctionRepository(new JdbcConnectionFactory(ServerConfig.loadDefault()));
            TestSupport.equals("3", repository.personalReceipt(2, room).get("totalBids"), "immutable receipt does not recompute statistics");
            var manager = new AuctionManager(restarted, 0);
            denied(() -> manager.requireRuntime(room), ErrorCode.AUCTION_NOT_FOUND);
            TestSupport.equals(alias, restarted.personalReceipt(2, room).get("winnerDisplay"), "receipt survives archive/repository restart");
            TestSupport.equals("1", repository.personalReceipts(2, "WON", "WON", "ANON", Long.MAX_VALUE, 30).get("count"), "won/name filters");
            TestSupport.equals("1", repository.personalReceipts(2, "WON", "ALL", Long.toString(room), Long.MAX_VALUE, 30).get("count"), "room ID filter");
            TestSupport.equals("1", repository.personalReceipts(4, "PARTICIPATED", "CANCELLED", "", Long.MAX_VALUE, 30).get("count"), "cancelled history filter");
            var page = repository.personalReceipts(1, "HOSTED", "ALL", "", Long.MAX_VALUE, 1); TestSupport.equals("true", page.get("hasMore"), "bounded keyset history");
            var next = repository.personalReceipts(1, "HOSTED", "ALL", "", Long.parseLong(page.get("nextBeforeId")), 1);
            TestSupport.check(!page.get("receipt.0.auctionId").equals(next.get("receipt.0.auctionId")), "history pages never overlap");
            execute(factory, "UPDATE wallet_entries SET amount=0 WHERE reference_key='BUY:" + room + "'");
            TestSupport.equals("REVIEW_REQUIRED", repository.personalReceipt(2, room).get("walletStatus"), "mismatched ledger cannot imply paid");
            execute(factory, "UPDATE wallet_entries SET amount=-130 WHERE reference_key='BUY:" + room + "'");
            execute(factory, "UPDATE wallet_entries SET reserved_delta=0 WHERE user_id=3 AND entry_type='RELEASE'");
            TestSupport.equals("REVIEW_REQUIRED", repository.personalReceipt(3, room).get("walletStatus"), "incomplete release requires reconciliation");
            execute(factory, "UPDATE wallet_entries SET reserved_delta=-120 WHERE user_id=3 AND entry_type='RELEASE'");
            repository.blockAuctionUser(new BlockAuctionUserCommit(room, 4, 1, Instant.now()));
            TestSupport.equals("WATCHED", repository.personalReceipt(4, room).get("personalRole"), "kick does not erase own minimal receipt");
            network(factory, repository, room);
            feeTiers(factory, repository);
            System.out.println("[PASS] AuctionReceiptSelfTest: durable atomic receipts/restart/backfill, exactly-once concurrent close, rollback, roles, real ledger/reconciliation, private/anonymous authorization and disconnect at closure");
        } finally { restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData); }
    }
    private static void feeTiers(JdbcConnectionFactory factory, JdbcAuctionRepository repository) throws Exception {
        TestSupport.equals(new BigDecimal("0.05"), vn.ptit.btl16.common.util.AuctionFees.fee(new BigDecimal("0.50")), "fee rounds half up to cents");
        TestSupport.equals(new BigDecimal("100000000.00"), vn.ptit.btl16.common.util.AuctionFees.fee(new BigDecimal("999999999.99")), "price below a billion uses ten percent before discount");
        execute(factory, "UPDATE users SET wallet_balance=wallet_balance+4000000000 WHERE user_id=2");
        for (int value : new int[]{999999999, 1000000000, 1000000001}) {
            long room = room(repository, "FEE_" + value, false, 600).getAuctionId();
            bid(repository, room, 2, value); close(repository, factory, room);
            var receipt = repository.personalReceipt(1, room);
            BigDecimal price = BigDecimal.valueOf(value);
            TestSupport.equals("PAID", receipt.get("walletStatus"), "net sale ledger reconciles at threshold " + value);
            TestSupport.check(new BigDecimal(receipt.get("feeRate")).compareTo(new BigDecimal(value <= 1000000000 ? "0.05" : "0.10")) == 0, "base tax minus rank one reduction at " + value);
            TestSupport.equals(Map.of(999999999, "49999999.95", 1000000000, "50000000.00", 1000000001, "100000000.10").get(value), receipt.get("platformFee"), "persisted tier fee");
            TestSupport.check(new BigDecimal(receipt.get("sellerNet")).add(new BigDecimal(receipt.get("platformFee"))).compareTo(price) == 0, "net plus fee equals buyer payment");
            TestSupport.check(repository.closeAuction(expire(factory, repository, room)).isEmpty(), "fee cannot be charged twice");
            execute(factory, "UPDATE wallet_settlements SET platform_fee=0,fee_rate=0 WHERE auction_id=" + room);
            execute(factory, "UPDATE wallet_entries SET amount=" + value + " WHERE reference_key='SELL:" + room + "'");
            DatabaseSchema.initialize(factory);
            var legacy = repository.personalReceipt(1, room);
            TestSupport.equals("0.00", legacy.get("platformFee"), "old zero-fee settlement is not recalculated during migration");
            TestSupport.equals("PAID", legacy.get("walletStatus"), "zero-fee historical receipt still reconciles");
        }
    }

    private static void rollback(JdbcConnectionFactory factory, JdbcAuctionRepository repository) throws Exception {
        long room = room(repository, "ROLLBACK", false, 600).getAuctionId(); bid(repository, room, 2, 110);
        repository.appendPresence(room, "JOINED", 999, Instant.now()); var commit = expire(factory, repository, room);
        boolean failed = false; try { repository.closeAuction(commit); } catch (AuctionRepositoryException expected) { failed = true; }
        TestSupport.check(failed, "receipt FK failure injected"); TestSupport.equals(AuctionStatus.OPEN, repository.findAuctionById(room).orElseThrow().getStatus(), "receipt failure rolls back closure");
        TestSupport.equals(0L, count(factory, "SELECT COUNT(*) FROM wallet_entries WHERE reference_key='BUY:" + room + "'"), "receipt failure rolls back payment");
        TestSupport.equals(0L, count(factory, "SELECT COUNT(*) FROM wallet_settlements WHERE auction_id=" + room), "receipt failure also rolls back platform fee");
        TestSupport.equals(0L, count(factory, "SELECT COUNT(*) FROM auction_results WHERE auction_id=" + room), "receipt failure rolls back result");
        TestSupport.equals(1L, count(factory, "SELECT COUNT(*) FROM wallet_holds WHERE auction_id=" + room), "hold restored by rollback");
        execute(factory, "DELETE FROM room_events WHERE actor_id=999"); TestSupport.check(repository.closeAuction(commit).isPresent(), "retry after rollback succeeds once");
    }

    private static void network(JdbcConnectionFactory factory, JdbcAuctionRepository repository, long closedRoom) throws Exception {
        var config = ServerConfig.forTransportTests(0, 0); var hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        var users = TestUserRepository.withDemoUsers(hasher); users.createUser("watcher", "Watcher", "", "", hasher.hash("Password123!".toCharArray())); users.createUser("stranger", "Stranger", "", "", hasher.hash("Password123!".toCharArray()));
        long ending = room(repository, "DISCONNECT_END", false, 4).getAuctionId(); bid(repository, ending, 2, 110);
        try (var server = ServerApplication.createForTests(config, users, repository, hasher)) {
            server.start();
            try (var winner = client(server.getBoundPort()); var stranger = client(server.getBoundPort()); var loser = client(server.getBoundPort())) {
                var account = new AccountApi(winner); String token = ok(account.login("alice", "alice123")).get("sessionToken");
                ok(new AccountApi(loser).login("bob", "bob123"));
                ok(new AuctionReceiptApi(winner).get(closedRoom));
                var forged = ok(winner.sendRequest(MessageType.GET_PERSONAL_RECEIPT, Map.of("auctionId", Long.toString(closedRoom), "userId", "1")).thenApply(ApiResponse::new));
                TestSupport.equals("WINNER", forged.get("personalRole"), "userId spoof ignored"); anonymous(forged.getWireMessage().getData());
                anonymous(ok(new AuctionReceiptApi(winner).list("WON", "WON", "ANON_PRIVATE", Long.MAX_VALUE)).getWireMessage().getData());
                var deniedHistory = new AuctionApi(loser).history(closedRoom).get(10, TimeUnit.SECONDS);
                TestSupport.equals("ROOM_ACCESS_DENIED", deniedHistory.getErrorCode(), "own receipt does not grant private history access");
                ok(new AuctionReceiptApi(loser).get(closedRoom));
                TestSupport.equals("ROOM_ACCESS_DENIED", new RoomSocialApi(loser).social(closedRoom).get(10, TimeUnit.SECONDS).getErrorCode(), "receipt does not grant private chat/roster access");
                var auth = new AuctionReceiptApi(stranger).get(closedRoom).get(10, TimeUnit.SECONDS); TestSupport.equals("AUTH_REQUIRED", auth.getErrorCode(), "unauthenticated receipt denied");
                ok(new AccountApi(stranger).login("stranger", "Password123!"));
                var forbidden = new AuctionReceiptApi(stranger).get(closedRoom).get(10, TimeUnit.SECONDS); TestSupport.equals("AUCTION_FORBIDDEN", forbidden.getErrorCode(), "unrelated user cannot enumerate receipt");
                TestSupport.equals("0", ok(new AuctionReceiptApi(stranger).list("PARTICIPATED", "ALL", "", Long.MAX_VALUE)).get("count"), "no other users receipts");
                try (var kicked = client(server.getBoundPort())) {
                    ok(new AccountApi(kicked).login("watcher", "Password123!"));
                    TestSupport.equals("WATCHED", ok(new AuctionReceiptApi(kicked).get(closedRoom)).get("personalRole"), "kicked account may read own minimal receipt");
                    TestSupport.equals("USER_BLOCKED_FROM_AUCTION", new AuctionApi(kicked).history(closedRoom).get(10, TimeUnit.SECONDS).getErrorCode(), "kicked account still cannot read room history");
                }
                ok(new AuctionApi(winner).join(ending)); winner.disconnect();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
                while (repository.findAuctionById(ending).orElseThrow().getStatus() == AuctionStatus.OPEN && System.nanoTime() < deadline) { Thread.sleep(100); }
                TestSupport.equals(AuctionStatus.ENDED, repository.findAuctionById(ending).orElseThrow().getStatus(), "timer closes while winning client offline");
                winner.connect("127.0.0.1", server.getBoundPort()); ok(account.resumeSession(token));
                var recovered = ok(new AuctionReceiptApi(winner).get(ending)); TestSupport.equals("WINNER", recovered.get("personalRole"), "offline-at-end personal result recovered");
                TestSupport.equals("PAID", recovered.get("walletStatus"), "offline-at-end payment confirmed from ledger");
            }
        }
        try (var server = ServerApplication.createForTests(config, users, new JdbcAuctionRepository(factory), hasher)) {
            server.start(); try (var client = client(server.getBoundPort())) {
                ok(new AccountApi(client).login("alice", "alice123"));
                TestSupport.equals("WINNER", ok(new AuctionReceiptApi(client).get(ending)).get("personalRole"), "server restart/fresh login retains result");
                ok(new AuctionReceiptApi(client).list("WON", "ALL", "", Long.MAX_VALUE));
            }
        }
    }
    private static AuctionSnapshot room(JdbcAuctionRepository repository, String code, boolean anonymous, int duration) {
        var product = TestMedia.create(repository, new CreateProductCommit(1, "demo", code, code, TestMedia.DESCRIPTION, Instant.now()));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return repository.createAuction(new CreateAuctionCommit(1, "demo", product.getProductId(), new BigDecimal("100"), new BigDecimal("10"), now.minusSeconds(1), now.plusSeconds(duration), anonymous ? RoomVisibility.PRIVATE : RoomVisibility.PUBLIC, anonymous ? "hash" : "", anonymous ? "salt" : "", anonymous ? 30000 : null, anonymous));
    }
    private static void bid(JdbcAuctionRepository repository, long room, long user, int amount) {
        var current = repository.findAuctionById(room).orElseThrow(); repository.commitAcceptedBid(new BidCommit(room, current.getCurrentPrice(), user, user == 2 ? "alice" : "bob", BigDecimal.valueOf(amount), ++sequence, Instant.now(), current.getEndTime()));
    }
    private static CloseAuctionCommit expire(JdbcConnectionFactory factory, JdbcAuctionRepository repository, long room) throws Exception {
        try (var connection = factory.openDatabase(); var update = connection.prepareStatement("UPDATE auctions SET end_time=? WHERE auction_id=?")) {
            update.setTimestamp(1, Timestamp.from(Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS))); update.setLong(2, room); update.executeUpdate();
        }
        var current = repository.findAuctionById(room).orElseThrow(); return new CloseAuctionCommit(room, current.getCurrentWinnerId(), current.getCurrentWinnerUsername(), current.getCurrentPrice(), current.getEndTime(), Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
    private static void close(JdbcAuctionRepository repository, JdbcConnectionFactory factory, long room) throws Exception { repository.closeAuction(expire(factory, repository, room)).orElseThrow(); }
    private static CompletableFuture<Integer> closeAsync(JdbcAuctionRepository repository, CloseAuctionCommit commit, CountDownLatch gate) {
        return CompletableFuture.supplyAsync(() -> { try { gate.await(); return repository.closeAuction(commit).isPresent() ? 1 : 0; } catch (InterruptedException error) { throw new RuntimeException(error); } });
    }
    private static void anonymous(Map<String, String> data) {
        for (var entry : data.entrySet()) {
            TestSupport.check(!entry.getKey().matches(".*(winnerId|WinnerId|userId|username|profileUrl)$"), "receipt has no identity lookup key");
            TestSupport.check(!entry.getValue().contains("alice") && !entry.getValue().contains("bob"), "anonymous receipt never reveals account");
        }
    }
    private static NetworkClient client(int port) throws Exception { var client = new NetworkClient(2_097_152, 3000, 6000); client.connect("127.0.0.1", port); return client; }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception { var response = future.get(15, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + " " + response.getMessageText()); return response; }
    private static void denied(Runnable action, ErrorCode code) { try { action.run(); throw new AssertionError("Expected " + code); } catch (AuctionException expected) { TestSupport.equals(code, expected.getErrorCode(), "denial"); } }
    private static long count(JdbcConnectionFactory factory, String sql) throws Exception { try (var connection = factory.openDatabase(); var query = connection.prepareStatement(sql); var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); } }
    private static void execute(JdbcConnectionFactory factory, String sql) throws Exception { try (var connection = factory.openDatabase(); var statement = connection.createStatement()) { statement.executeUpdate(sql); } }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
