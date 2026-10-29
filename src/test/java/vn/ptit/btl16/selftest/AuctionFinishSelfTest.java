package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.*;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.model.*;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.auction.service.AuctionManager;
import vn.ptit.btl16.server.db.DatabaseSchema;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

public final class AuctionFinishSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String previousConfig = System.getProperty("btl16.server.config"), previousData = System.getProperty("btl16.dataDir");
        Path folder = Files.createTempDirectory("btl16-finish-"), configFile = folder.resolve("server.properties");
        Files.writeString(configFile, "db.driver=org.h2.Driver\ndb.name=finish\ndb.user=sa\ndb.password=\ndb.autoInitialize=false\n");
        System.setProperty("btl16.server.config", configFile.toString()); System.setProperty("btl16.dataDir", folder.toString());
        try {
            var factory = new JdbcConnectionFactory(ServerConfig.loadDefault()); DatabaseSchema.initialize(factory);
            sql(factory, "INSERT INTO users(user_id,username,display_name,password_hash,password_salt,password_iterations,active,wallet_balance) VALUES "
                    + "(1,'demo','Host','x','x',30000,TRUE,1000000),(2,'alice','Alice','x','x',30000,TRUE,1000000),(3,'bob','Bob','x','x',30000,TRUE,1000000)");
            var repository = new JdbcAuctionRepository(factory);
            TestSupport.equals("0", repository.topProducts().get("count"), "empty ranking before any sale");
            List<Long> sold = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                long id = room(repository, "RANK_" + index, false);
                var current = repository.findAuctionById(id).orElseThrow();
                BigDecimal price = new BigDecimal(index >= 10 ? "1500.25" : Integer.toString(200 + index * 100));
                repository.commitAcceptedBid(new BidCommit(id, current.getCurrentPrice(), 2, "alice", price, 100 + index, Instant.now(), current.getEndTime()));
                close(factory, repository, id); sold.add(id);
            }
            long noBids = room(repository, "NO_BIDS", false); close(factory, repository, noBids);
            long cancelled = room(repository, "CANCELLED", false); repository.cancelAuction(new CancelAuctionCommit(cancelled, Instant.now()));
            long open = room(repository, "STILL_OPEN", false);
            // Equal amounts and timestamps have a stable auction-ID tie break.
            sql(factory, "UPDATE auction_receipt_summaries SET ended_at='2026-10-01 10:00:00' WHERE auction_id IN (" + sold.get(10) + "," + sold.get(11) + ")");
            Map<String, String> ranking = repository.topProducts();
            TestSupport.equals("10", ranking.get("count"), "ranking is bounded to ten products");
            TestSupport.equals(sold.get(10).toString(), ranking.get("ranking.0.auctionId"), "equal-price earlier ID first");
            TestSupport.equals(sold.get(11).toString(), ranking.get("ranking.1.auctionId"), "equal-price later ID second");
            TestSupport.equals("1500.25", ranking.get("ranking.0.finalPrice"), "ranking keeps the gross final price and decimal cents");
            BigDecimal previous = new BigDecimal("999999999999999");
            for (int index = 0; index < 10; index++) {
                BigDecimal price = new BigDecimal(ranking.get("ranking." + index + ".finalPrice"));
                TestSupport.check(price.compareTo(previous) <= 0, "descending numeric prices"); previous = price;
                String id = ranking.get("ranking." + index + ".auctionId");
                TestSupport.check(!List.of(Long.toString(noBids), Long.toString(cancelled), Long.toString(open)).contains(id), "only successful closed sales rank");
            }
            DatabaseSchema.initialize(factory);
            TestSupport.equals(ranking, new JdbcAuctionRepository(factory).topProducts(), "ranking survives repository/schema restart and result archive age");
            network(factory, repository, ranking);
            System.out.println("[PASS] AuctionFinishSelfTest: durable exact Top 10, ties, excluded unsold/open/cancelled, auth, anonymous winner-only projection outside the room");
        } finally { restore("btl16.server.config", previousConfig); restore("btl16.dataDir", previousData); }
    }

    private static void network(JdbcConnectionFactory factory, JdbcAuctionRepository repository, Map<String, String> expectedRanking) throws Exception {
        long room = room(repository, "ANON_WIN", true);
        var current = repository.findAuctionById(room).orElseThrow();
        repository.commitAcceptedBid(new BidCommit(room, current.getCurrentPrice(), 2, "alice", new BigDecimal("110"), 500, Instant.now(), current.getEndTime()));
        var config = ServerConfig.forTransportTests(0, 0); var hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        try (var server = ServerApplication.createForTests(config, TestUserRepository.withDemoUsers(hasher), repository, hasher)) {
            server.start();
            try (var winner = client(server.getBoundPort()); var loser = client(server.getBoundPort())) {
                TestSupport.equals("AUTH_REQUIRED", new ApiResponse(loser.sendRequest(MessageType.GET_PRODUCT_RANKING, Map.of()).get(10, TimeUnit.SECONDS)).getErrorCode(), "ranking requires authentication");
                ok(new AccountApi(winner).login("alice", "alice123")); ok(new AccountApi(loser).login("bob", "bob123"));
                var ranking = new ApiResponse(winner.sendRequest(MessageType.GET_PRODUCT_RANKING, Map.of()).get(10, TimeUnit.SECONDS));
                TestSupport.check(ranking.isSuccess(), "ranking request succeeds");
                for (var entry : expectedRanking.entrySet()) { TestSupport.equals(entry.getValue(), ranking.get(entry.getKey()), "ranking protocol field"); }
                TestSupport.check(ranking.getWireMessage().getData().keySet().stream().noneMatch(key -> key.matches(".*(?i:winner|username|password|wallet).*")), "ranking reveals no private account/access data");
                var win = new CompletableFuture<WireMessage>(); var lose = new CompletableFuture<WireMessage>();
                winner.addEventListener(event -> { if (event.getType() == MessageType.AUCTION_ENDED && Long.toString(room).equals(event.get("auctionId"))) { win.complete(event); } });
                loser.addEventListener(event -> { if (event.getType() == MessageType.AUCTION_ENDED && Long.toString(room).equals(event.get("auctionId"))) { lose.complete(event); } });
                var field = ServerApplication.class.getDeclaredField("auctions"); field.setAccessible(true);
                var runtime = ((AuctionManager) field.get(server)).requireRuntime(room);
                Instant end = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
                runtime.getLock().lock();
                try { updateEnd(factory, room, end); runtime.applyExtended(end); } finally { runtime.getLock().unlock(); }
                var result = win.get(10, TimeUnit.SECONDS); var other = lose.get(10, TimeUnit.SECONDS);
                TestSupport.equals("true", result.get("winnerIsSelf"), "winner identified while outside anonymous room");
                TestSupport.equals("false", other.get("winnerIsSelf"), "other recipient cannot see personal win");
                TestSupport.equals("0", result.get("selfParticipantId"), "winner never joined this room");
                TestSupport.check(Long.parseLong(result.get("winnerId")) < 0 && !result.get("winnerUsername").contains("alice"), "raw anonymous winner identity stays masked");
            }
        }
    }
    private static long room(JdbcAuctionRepository repository, String code, boolean anonymous) {
        var product = TestMedia.create(repository, new CreateProductCommit(1, "demo", code, code, TestMedia.DESCRIPTION, Instant.now()));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return repository.createAuction(new CreateAuctionCommit(1, "demo", product.getProductId(), new BigDecimal("100"), new BigDecimal("10"), now.minusSeconds(1), now.plusSeconds(600), RoomVisibility.PUBLIC, "", "", null, anonymous)).getAuctionId();
    }
    private static void close(JdbcConnectionFactory factory, JdbcAuctionRepository repository, long room) throws Exception {
        Instant end = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS); updateEnd(factory, room, end);
        var current = repository.findAuctionById(room).orElseThrow();
        repository.closeAuction(new CloseAuctionCommit(room, current.getCurrentWinnerId(), current.getCurrentWinnerUsername(), current.getCurrentPrice(), end, Instant.now())).orElseThrow();
    }
    private static void updateEnd(JdbcConnectionFactory factory, long room, Instant end) throws Exception {
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("UPDATE auctions SET end_time=? WHERE auction_id=?")) { query.setTimestamp(1, Timestamp.from(end)); query.setLong(2, room); query.executeUpdate(); }
    }
    private static void sql(JdbcConnectionFactory factory, String sql) throws Exception { try (var connection = factory.openDatabase(); var query = connection.createStatement()) { query.executeUpdate(sql); } }
    private static NetworkClient client(int port) throws Exception { var client = new NetworkClient(2097152, 3000, 6000); client.connect("127.0.0.1", port); return client; }
    private static void ok(CompletableFuture<ApiResponse> future) throws Exception { var value = future.get(10, TimeUnit.SECONDS); TestSupport.check(value.isSuccess(), value.getMessageText()); }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
