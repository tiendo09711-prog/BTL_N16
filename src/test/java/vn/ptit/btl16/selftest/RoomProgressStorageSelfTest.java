package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.model.*;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.auction.service.RoomManager;
import vn.ptit.btl16.server.db.DatabaseSchema;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

public final class RoomProgressStorageSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String previousConfig = System.getProperty("btl16.server.config"), previousData = System.getProperty("btl16.dataDir");
        Path folder = Files.createTempDirectory("btl16-progress-jdbc-").toRealPath();
        Path configFile = folder.resolve("server.properties");
        Files.writeString(configFile, "db.driver=org.h2.Driver\ndb.name=progress\ndb.user=sa\ndb.password=\ndb.autoInitialize=false\n");
        System.setProperty("btl16.server.config", configFile.toString()); System.setProperty("btl16.dataDir", folder.toString());
        try {
            var config = ServerConfig.loadDefault(); TestSupport.check(config.isEmbeddedDatabase(), "temporary H2 only");
            var factory = new JdbcConnectionFactory(config); DatabaseSchema.initialize(factory); DatabaseSchema.initialize(factory);
            try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users(user_id,username,display_name,password_hash,password_salt,password_iterations,active,wallet_balance) VALUES "
                        + "(1,'demo','Host','test','test',30000,TRUE,1000000),(2,'alice','Alice','test','test',30000,TRUE,1000000),(3,'bob','Bob','test','test',30000,TRUE,1000000)");
            }
            var repository = new JdbcAuctionRepository(factory);
            AuctionSnapshot auction = room(repository, "PROGRESS_JDBC"); long room = auction.getAuctionId();
            Instant end = auction.getEndTime();
            TestSupport.check(repository.extendAuction(new ExtendAuctionCommit(room, end, end.plusSeconds(10))), "manual extension commits"); end = end.plusSeconds(10);
            long count = repository.findRoomEvents(room, Long.MAX_VALUE, 0, 1000).size();
            TestSupport.check(!repository.extendAuction(new ExtendAuctionCommit(room, end.minusSeconds(10), end.plusSeconds(10))), "stale manual extension rejected");
            TestSupport.equals(count, (long) repository.findRoomEvents(room, Long.MAX_VALUE, 0, 1000).size(), "failed mutation adds no event");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            for (int index = 0; index < 135; index++) {
                if (index == 10) { end = end.plusSeconds(10); }
                repository.commitAcceptedBid(new BidCommit(room, BigDecimal.valueOf(100 + index * 10L), index % 2 == 0 ? 2 : 3,
                        index % 2 == 0 ? "alice" : "bob", BigDecimal.valueOf(110 + index * 10L), index + 1, now.plusMillis(index), end));
            }
            var stats = repository.roomStats(room);
            TestSupport.equals(135L, stats.bidCount(), "JDBC full count not recent-history limit");
            TestSupport.equals(2L, stats.bidderCount(), "JDBC distinct accounts"); TestSupport.equals(2L, stats.extensionCount(), "manual plus automatic counted once");
            TestSupport.check(stats.extensionsComplete(), "new room full extension coverage");
            TestSupport.equals(new BigDecimal("1350.00"), stats.increase().setScale(2), "full-session growth");
            Set<Long> ids = new HashSet<>(); long before = Long.MAX_VALUE;
            while (true) {
                var page = repository.bidPage(room, before, 135, null, 50); if (page.isEmpty()) { break; }
                for (var row : page) { TestSupport.check(ids.add(row.bid().getBidId()), "stable JDBC bid page"); TestSupport.equals(new BigDecimal("10.00"), row.increase().setScale(2), "cross-page increase"); }
                before = page.get(page.size() - 1).bid().getServerSequence();
            }
            TestSupport.equals(135, ids.size(), "all JDBC pages covered");
            TestSupport.equals(68, repository.bidPage(room, Long.MAX_VALUE, 135, 2L, 100).size(), "JDBC mine filter before limit");
            long high = repository.findRoomEvents(room, Long.MAX_VALUE, 0, 1).get(0).id();
            var restarted = new JdbcAuctionRepository(factory);
            TestSupport.equals(high, restarted.findRoomEvents(room, Long.MAX_VALUE, 0, 1).get(0).id(), "event identity survives repository restart");
            var presence = new RoomManager(); presence.setPresenceListener(repository::appendPresence);
            presence.join(room, "one", 2, "alice"); presence.join(room, "one", 2, "alice"); presence.join(room, "two", 2, "alice");
            TestSupport.equals(2, presence.subscriberCount(room), "two watcher connections for one account");
            presence.removeConnection("one"); presence.removeConnection("two"); presence.removeConnection("two");
            var transitions = repository.findRoomEvents(room, Long.MAX_VALUE, high, 100);
            TestSupport.equals(2, transitions.size(), "first account connection joins, last leaves, repeated resync/removal idempotent");
            TestSupport.equals("JOINED", transitions.get(0).type(), "server join event"); TestSupport.equals("LEFT", transitions.get(1).type(), "server leave event");
            long eventsBeforeFailure = repository.findRoomEvents(room, Long.MAX_VALUE, 0, 1000).size();
            try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE room_events ADD CONSTRAINT test_reject_bid_event CHECK (event_type <> 'BID_ACCEPTED' OR amount <> 1460)");
            }
            try {
                repository.commitAcceptedBid(new BidCommit(room, new BigDecimal("1450"), 3, "bob", new BigDecimal("1460"), 136, now.plusSeconds(1), end));
                throw new AssertionError("event insert failure must roll back bid and wallet");
            } catch (AuctionRepositoryException expected) { }
            try (var connection = factory.openDatabase(); var statement = connection.createStatement()) { statement.executeUpdate("ALTER TABLE room_events DROP CONSTRAINT test_reject_bid_event"); }
            TestSupport.equals(135L, repository.roomStats(room).bidCount(), "bid rollback after event failure");
            TestSupport.equals(new BigDecimal("1450.00"), repository.findAuctionById(room).orElseThrow().getCurrentPrice().setScale(2), "price rollback");
            TestSupport.equals(eventsBeforeFailure, (long) repository.findRoomEvents(room, Long.MAX_VALUE, 0, 1000).size(), "no partial journal transaction");
            try (var connection = factory.openDatabase(); var query = connection.prepareStatement("SELECT user_id, amount FROM wallet_holds WHERE auction_id = ?")) {
                query.setLong(1, room); try (var rows = query.executeQuery()) { rows.next(); TestSupport.equals(2L, rows.getLong(1), "wallet hold owner rolled back"); TestSupport.equals(new BigDecimal("1450.00"), rows.getBigDecimal(2).setScale(2), "wallet hold amount rolled back"); }
            }
            var current = repository.findAuctionById(room).orElseThrow();
            repository.closeAuction(new CloseAuctionCommit(room, current.getCurrentWinnerId(), current.getCurrentWinnerUsername(), current.getCurrentPrice(), end, end.plusSeconds(1))).orElseThrow();
            TestSupport.equals("ENDED", repository.findRoomEvents(room, Long.MAX_VALUE, 0, 1).get(0).type(), "timer-close transaction records end");
            AuctionSnapshot cancelled = room(repository, "PROGRESS_CANCEL");
            TestSupport.check(repository.cancelAuction(new CancelAuctionCommit(cancelled.getAuctionId(), Instant.now())), "cancel commits");
            TestSupport.equals("CANCELLED", repository.findRoomEvents(cancelled.getAuctionId(), Long.MAX_VALUE, 0, 1).get(0).type(), "cancel journal persisted");
            AuctionSnapshot race = room(repository, "PROGRESS_JDBC_RACE"); var gate = new CountDownLatch(1);
            var first = race(repository, race, 2, 500, gate); var second = race(repository, race, 3, 501, gate); gate.countDown();
            TestSupport.equals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS), "JDBC simultaneous bids serialize");
            TestSupport.equals(1L, repository.roomStats(race.getAuctionId()).bidCount(), "concurrent count exactly one");
            TestSupport.equals(3, repository.findRoomEvents(race.getAuctionId(), Long.MAX_VALUE, 0, 100).size(), "one creation, one bid, one leader event");
            Product snipingProduct = TestMedia.create(repository, new CreateProductCommit(1, "demo", "PROGRESS_AUTO", "Auto extension", TestMedia.DESCRIPTION, Instant.now()));
            Instant snipingNow = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            AuctionSnapshot sniping = repository.createAuction(new CreateAuctionCommit(1, "demo", snipingProduct.getProductId(), new BigDecimal("100"), new BigDecimal("10"), snipingNow.minusSeconds(1), snipingNow.plusSeconds(5)));
            var fixture = new AuctionRulesSelfTest.Fixture(repository); fixture.rooms.join(sniping.getAuctionId(), "buyer", 2, "buyer");
            TestSupport.check(fixture.bids.placeBid(fixture.buyer, "buyer", sniping.getAuctionId(), new BigDecimal("110")).isExtended(), "server anti-sniping actually extends");
            TestSupport.equals(1L, repository.roomStats(sniping.getAuctionId()).extensionCount(), "actual automatic extension counted once");
            TestSupport.equals("EXTENDED_AUTO", repository.findRoomEvents(sniping.getAuctionId(), Long.MAX_VALUE, 0, 1).get(0).type(), "automatic event follows accepted bid and leader change");
            try (var connection = factory.openDatabase(); var statement = connection.createStatement()) { statement.executeUpdate("DELETE FROM room_events WHERE event_type = 'CREATED' AND auction_id = " + cancelled.getAuctionId()); }
            TestSupport.check(!repository.roomStats(cancelled.getAuctionId()).extensionsComplete(), "legacy coverage not falsely presented as full");
            DatabaseSchema.initialize(factory);
        } finally { restore("btl16.server.config", previousConfig); restore("btl16.dataDir", previousData); }
        System.out.println("[PASS] RoomProgressStorageSelfTest: idempotent migration, durable ordered journal, full SQL stats/history/mine, atomic bid+wallet+events rollback, extensions, closure and concurrent JDBC bids");
    }
    private static CompletableFuture<Integer> race(JdbcAuctionRepository repository, AuctionSnapshot auction, long user, long sequence, CountDownLatch gate) {
        return CompletableFuture.supplyAsync(() -> {
            try { gate.await(); repository.commitAcceptedBid(new BidCommit(auction.getAuctionId(), new BigDecimal("100"), user, "buyer", new BigDecimal("110"), sequence, Instant.now(), auction.getEndTime())); return 1; }
            catch (AuctionConflictException expected) { return 0; } catch (InterruptedException exception) { throw new RuntimeException(exception); }
        });
    }
    private static AuctionSnapshot room(JdbcAuctionRepository repository, String code) {
        Product product = TestMedia.create(repository, new CreateProductCommit(1, "demo", code, code, TestMedia.DESCRIPTION, Instant.now()));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return repository.createAuction(new CreateAuctionCommit(1, "demo", product.getProductId(), new BigDecimal("100"), new BigDecimal("10"), now.minusSeconds(1), now.plusSeconds(600)));
    }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
