package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientWireParser;
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
import vn.ptit.btl16.server.notification.PersonalNotificationStore;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Real JDBC state transitions, combined room scores, privacy and authenticated notification deletion. */
public final class MarketplaceFeaturesSelfTest {
    private static int sequence;
    private static long bidSequence;
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        Path folder = Files.createTempDirectory("btl16-marketplace-features-"); Path config = folder.resolve("server.properties");
        Files.writeString(config, "db.driver=org.h2.Driver\ndb.name=features\ndb.user=sa\ndb.password=\ndb.autoInitialize=false\n");
        System.setProperty("btl16.server.config", config.toString()); System.setProperty("btl16.dataDir", folder.toString());
        try {
            var factory = new JdbcConnectionFactory(ServerConfig.loadDefault()); DatabaseSchema.initialize(factory);
            sql(factory, "INSERT INTO users(user_id,username,display_name,password_hash,password_salt,password_iterations,active,wallet_balance) VALUES(1,'demo','Host','x','x',30000,TRUE,1000000),(2,'alice','Alice','x','x',30000,TRUE,1000000),(3,'bob','Bob','x','x',30000,TRUE,1000000)");
            var repository = new JdbcAuctionRepository(factory);
            long unused = product(repository);
            state(repository, unused, ProductTransactionStatus.UNTRADED);
            repository.deactivateProduct(unused, 1); state(repository, unused, ProductTransactionStatus.UNTRADED);
            long scheduledProduct = product(repository); var scheduled = room(repository, scheduledProduct, true, RoomVisibility.PUBLIC);
            state(repository, scheduledProduct, ProductTransactionStatus.AUCTIONING);
            repository.cancelAuction(new CancelAuctionCommit(scheduled.getAuctionId(), Instant.now()));
            state(repository, scheduledProduct, ProductTransactionStatus.UNTRADED);
            var empty = room(repository, scheduledProduct, false, RoomVisibility.PUBLIC); close(factory, repository, empty);
            state(repository, scheduledProduct, ProductTransactionStatus.UNTRADED);
            var relisted = room(repository, scheduledProduct, false, RoomVisibility.PUBLIC);
            TestSupport.check(relisted.getAuctionId() != empty.getAuctionId(), "unsold product gets a new auction without reopening its old room");
            state(repository, scheduledProduct, ProductTransactionStatus.AUCTIONING);
            repository.cancelAuction(new CancelAuctionCommit(relisted.getAuctionId(), Instant.now()));
            state(repository, scheduledProduct, ProductTransactionStatus.UNTRADED);
            long soldProduct = product(repository); var sold = room(repository, soldProduct, false, RoomVisibility.PUBLIC); bid(repository, sold, 1); close(factory, repository, sold);
            state(repository, soldProduct, ProductTransactionStatus.SOLD);
            try {
                room(repository, soldProduct, false, RoomVisibility.PUBLIC);
                throw new AssertionError("sold product must not get another auction");
            } catch (AuctionException expected) {
                TestSupport.equals(ErrorCode.PRODUCT_ALREADY_SOLD, expected.getErrorCode(), "repository forbids relisting a sold product");
            }
            var receipt = repository.personalReceipt(2, sold.getAuctionId());

            List<AuctionSnapshot> live = new ArrayList<>();
            for (int i = 0; i < 7; i++) { live.add(room(repository, product(repository), false, RoomVisibility.PUBLIC)); }
            bid(repository, live.get(1), 1); bid(repository, live.get(2), 2); bid(repository, live.get(4), 3); bid(repository, live.get(5), 1);
            var rooms = new RoomManager(); rooms.setPresenceListener(new RoomProgressStore(factory)::presence);
            long busiest = live.get(3).getAuctionId();
            rooms.join(busiest, "first", 2, "alice"); rooms.join(busiest, "duplicate", 2, "alice");
            TestSupport.equals(1, rooms.participantCount(busiest), "one person across two connections");
            rooms.leave(busiest, "duplicate"); rooms.leave(busiest, "first"); rooms.join(busiest, "again", 2, "alice");
            rooms.join(live.get(5).getAuctionId(), "visitor", 2, "alice"); rooms.leave(live.get(5).getAuctionId(), "visitor");
            rooms.join(live.get(6).getAuctionId(), "active", 3, "bob");
            var privateRoom = room(repository, product(repository), false, RoomVisibility.PRIVATE); bid(repository, privateRoom, 8);
            var future = room(repository, product(repository), true, RoomVisibility.PUBLIC);
            for (int i = 0; i < 10; i++) { new RoomProgressStore(factory).presence(future.getAuctionId(), "JOINED", 2, Instant.now()); }
            var ended = room(repository, product(repository), false, RoomVisibility.PUBLIC); bid(repository, ended, 8); close(factory, repository, ended);
            var cancelled = room(repository, product(repository), false, RoomVisibility.PUBLIC);
            for (int i = 0; i < 10; i++) { new RoomProgressStore(factory).presence(cancelled.getAuctionId(), "JOINED", 2, Instant.now()); }
            TestSupport.check(repository.cancelAuction(new CancelAuctionCommit(cancelled.getAuctionId(), Instant.now())), "cancelled fixture closes successfully");
            var queries = new AuctionQueryService(new AuctionManager(repository, 60), repository, 1);
            var featured = queries.featured(1, rooms, Instant.now());
            TestSupport.equals(List.of(busiest, live.get(4).getAuctionId(), live.get(6).getAuctionId(), live.get(2).getAuctionId(), live.get(5).getAuctionId()), ids(featured), "top five combine current participants, bids and joins with deterministic ties");
            TestSupport.equals("3", featured.get("auction.0.featuredScore"), "one current participant plus two genuine joins; leaving adds no point");
            TestSupport.equals("2", featured.get("auction.0.movementCount"), "duplicate connections and leaving do not add entry points");
            TestSupport.equals("3", repository.auctionActivity().get(live.get(4).getAuctionId()).bidCount() + "", "full bid count ignores history page limit");
            repository.blockAuctionUser(new BlockAuctionUserCommit(busiest, 3, 1, Instant.now()));
            TestSupport.check(!ids(queries.featured(3, rooms, Instant.now())).contains(busiest), "blocked room is excluded for that viewer");
            TestSupport.check(ids(queries.featured(1, rooms, future.getStartTime())).contains(future.getAuctionId()), "scheduled room becomes eligible at its start time");
            rooms.removeConnection("again");
            TestSupport.equals(0, rooms.participantCount(busiest), "disconnect removes current occupancy");
            TestSupport.equals(2L, repository.auctionActivity().get(busiest).movementCount(), "disconnect leaves the join count unchanged");
            TestSupport.equals(2L, repository.auctionActivity().get(busiest).score(rooms.participantCount(busiest)), "empty room contributes zero occupancy points and retains only its two past entries");
            rooms.join(busiest, "alice-return", 2, "alice"); rooms.join(busiest, "bob-enters", 3, "bob");
            TestSupport.equals(2, rooms.participantCount(busiest), "two distinct current users contribute two occupancy points");
            TestSupport.equals(6L, repository.auctionActivity().get(busiest).score(rooms.participantCount(busiest)), "four past entries plus two current users produce six points");

            long own, other;
            try (var c = factory.openDatabase()) {
                PersonalNotificationStore.save(c, 1, "DELETE_OWN", "TOPUP_APPROVED", null, "KEEP_WALLET", Instant.now());
                PersonalNotificationStore.save(c, 2, "DELETE_OTHER", "TOPUP_REJECTED", null, "OTHER", Instant.now());
            }
            own = scalar(factory, "SELECT notification_id FROM personal_notifications WHERE source_key='DELETE_OWN'");
            other = scalar(factory, "SELECT notification_id FROM personal_notifications WHERE source_key='DELETE_OTHER'");
            long ledger = scalar(factory, "SELECT COUNT(*) FROM wallet_entries"), results = scalar(factory, "SELECT COUNT(*) FROM auction_results");
            var serverConfig = ServerConfig.forTransportTests(0, 0); var hasher = new Pbkdf2PasswordHasher(serverConfig.getPasswordIterations());
            try (var server = ServerApplication.createForTests(serverConfig, TestUserRepository.withDemoUsers(hasher), repository, hasher)) {
                server.start();
                try (var host = client(server.getBoundPort()); var alice = client(server.getBoundPort()); var guest = client(server.getBoundPort())) {
                    ok(new AccountApi(host).login("demo", "demo123")); ok(new AccountApi(alice).login("alice", "alice123"));
                    fail(request(guest, MessageType.DELETE_MY_NOTIFICATION, Map.of("notificationId", own + "")), "AUTH_REQUIRED");
                    fail(new AuctionApi(guest).featuredAuctions(), "AUTH_REQUIRED");
                    fail(request(alice, MessageType.DELETE_MY_NOTIFICATION, Map.of("notificationId", own + "", "userId", "1")), "AUCTION_FORBIDDEN");
                    ok(request(host, MessageType.DELETE_MY_NOTIFICATION, Map.of("notificationId", own + "")));
                    long resultNotification = scalar(factory, "SELECT notification_id FROM personal_notifications WHERE source_key='RESULT:" + sold.getAuctionId() + ":1'");
                    ok(request(host, MessageType.DELETE_MY_NOTIFICATION, Map.of("notificationId", resultNotification + "")));
                    TestSupport.equals(0L, scalar(factory, "SELECT COUNT(*) FROM personal_notifications WHERE notification_id=" + own), "own notification actually removed");
                    TestSupport.equals(1L, scalar(factory, "SELECT COUNT(*) FROM personal_notifications WHERE notification_id=" + other), "other person's notification remains");
                    var list = ok(request(host, MessageType.GET_MY_NOTIFICATIONS, Map.of("limit", "100")));
                    TestSupport.equals(scalar(factory, "SELECT COUNT(*) FROM personal_notifications WHERE user_id=1 AND read_at IS NULL") + "", list.get("unreadCount"), "unread total recomputes after deletion");
                    var catalog = ClientWireParser.products(ok(new AuctionApi(host).myProducts()).getWireMessage().getData());
                    TestSupport.equals(ProductTransactionStatus.SOLD, catalog.stream().filter(p -> p.getProductId() == soldProduct).findFirst().orElseThrow().getTransactionStatus(), "sold state reaches client even when product active");
                    TestSupport.check(!catalog.stream().filter(p -> p.getProductId() == soldProduct).findFirst().orElseThrow().canAuction(), "sold product is excluded from auction creation");
                    TestSupport.check(catalog.stream().filter(p -> p.getProductId() == scheduledProduct).findFirst().orElseThrow().canAuction(), "unsold product can be selected for auction creation again");
                    TestSupport.check(!catalog.stream().filter(p -> p.getProductId() == unused).findFirst().orElseThrow().canAuction(), "inactive product cannot be selected despite being untraded");
                    var api = new AuctionApi(host);
                    long auctionCount = scalar(factory, "SELECT COUNT(*) FROM auctions");
                    fail(api.createAuction(soldProduct, new BigDecimal("100"), new BigDecimal("10"), 5), "PRODUCT_ALREADY_SOLD");
                    fail(api.createAuction(soldProduct, new BigDecimal("100"), new BigDecimal("10"), 5,
                            "PUBLIC", "", false, Instant.now().plusSeconds(600)), "PRODUCT_ALREADY_SOLD");
                    TestSupport.equals(auctionCount, scalar(factory, "SELECT COUNT(*) FROM auctions"), "rejected sold product requests create no auction");
                    long newRoom = Long.parseLong(ok(api.createAuction(scheduledProduct, new BigDecimal("100"),
                            new BigDecimal("10"), 5, "PUBLIC", "", false, Instant.now().plusSeconds(600))).get("auctionId"));
                    TestSupport.check(newRoom != empty.getAuctionId(), "authenticated relisting creates a separate scheduled auction");
                    var relistedProduct = ClientWireParser.products(ok(api.myProducts()).getWireMessage().getData()).stream()
                            .filter(p -> p.getProductId() == scheduledProduct).findFirst().orElseThrow();
                    TestSupport.equals(ProductTransactionStatus.AUCTIONING, relistedProduct.getTransactionStatus(), "relisted product is auctioning in the seller catalog");
                    TestSupport.check(!relistedProduct.canAuction(), "product with a new open auction cannot be selected again");
                    fail(api.createAuction(scheduledProduct, new BigDecimal("100"), new BigDecimal("10"), 5), "AUCTION_ALREADY_EXISTS");
                    var networkFeatured = ok(new AuctionApi(host).featuredAuctions());
                    TestSupport.equals(5, ids(networkFeatured.getWireMessage().getData()).size(), "authenticated featured endpoint returns five");
                }
            }
            TestSupport.equals(ledger, scalar(factory, "SELECT COUNT(*) FROM wallet_entries"), "notification deletion preserves financial ledger");
            TestSupport.equals(results, scalar(factory, "SELECT COUNT(*) FROM auction_results"), "notification deletion preserves results");
            TestSupport.equals(receipt, repository.personalReceipt(2, sold.getAuctionId()), "notification deletion preserves receipt");
            DatabaseSchema.initialize(factory); state(new JdbcAuctionRepository(factory), soldProduct, ProductTransactionStatus.SOLD);
            TestSupport.equals(0L, scalar(factory, "SELECT COUNT(*) FROM personal_notifications WHERE notification_id=" + own), "deleted notification stays deleted after restart");
            System.out.println("[PASS] MarketplaceFeaturesSelfTest: transaction states, unsold relisting/sold product rejection through JDBC and authenticated API, client auction eligibility, top-five combined score/full history/presence/privacy/scheduling, authenticated notification deletion and preserved receipts/ledger");
        } finally { restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData); }
    }
    private static List<Long> ids(Map<String,String> data) { return ClientWireParser.auctions(data).stream().map(a -> a.getAuctionId()).toList(); }
    private static long product(JdbcAuctionRepository r) { String code = "FEATURE_" + ++sequence; return TestMedia.create(r, new CreateProductCommit(1, "demo", code, code, TestMedia.DESCRIPTION, Instant.now())).getProductId(); }
    private static AuctionSnapshot room(JdbcAuctionRepository r, long product, boolean scheduled, RoomVisibility visibility) {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        return r.createAuction(new CreateAuctionCommit(1, "demo", product, new BigDecimal("100"), new BigDecimal("10"), now.plusSeconds(scheduled ? 300 : -1), now.plusSeconds(scheduled ? 900 : 600), visibility, "hash", "salt", null, false));
    }
    private static void bid(JdbcAuctionRepository r, AuctionSnapshot a, int count) {
        for (int i = 0; i < count; i++) { r.commitAcceptedBid(new BidCommit(a.getAuctionId(), BigDecimal.valueOf(100 + i * 10L), i % 2 == 0 ? 2 : 3, i % 2 == 0 ? "alice" : "bob", BigDecimal.valueOf(110 + i * 10L), ++bidSequence, Instant.now(), a.getEndTime())); }
    }
    private static void state(JdbcAuctionRepository r, long product, ProductTransactionStatus expected) { TestSupport.equals(expected, r.productTransactionStatus(product), "authoritative product transaction state"); }
    private static void close(JdbcConnectionFactory f, JdbcAuctionRepository r, AuctionSnapshot a) throws Exception {
        sql(f, "UPDATE auctions SET end_time=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE auction_id=" + a.getAuctionId());
        var current = r.findAuctionById(a.getAuctionId()).orElseThrow();
        r.closeAuction(new CloseAuctionCommit(current.getAuctionId(), current.getCurrentWinnerId(), current.getCurrentWinnerUsername(), current.getCurrentPrice(), current.getEndTime(), Instant.now())).orElseThrow();
    }
    private static CompletableFuture<ApiResponse> request(NetworkClient c, MessageType t, Map<String,String> data) { return c.sendRequest(t, data).thenApply(ApiResponse::new); }
    private static NetworkClient client(int port) throws Exception { var c = new NetworkClient(2097152, 5000, 10000); c.connect("127.0.0.1", port); return c; }
    private static ApiResponse ok(CompletableFuture<ApiResponse> f) throws Exception { var r = f.get(15, TimeUnit.SECONDS); TestSupport.check(r.isSuccess(), r.getErrorCode() + ": " + r.getMessageText()); return r; }
    private static void fail(CompletableFuture<ApiResponse> f, String code) throws Exception { TestSupport.equals(code, f.get(15, TimeUnit.SECONDS).getErrorCode(), "authenticated endpoint rejects invalid access"); }
    private static void sql(JdbcConnectionFactory f, String sql) throws Exception { try (var c = f.openDatabase(); var s = c.createStatement()) { s.executeUpdate(sql); } }
    private static long scalar(JdbcConnectionFactory f, String sql) throws Exception { try (var c = f.openDatabase(); var s = c.createStatement(); var rows = s.executeQuery(sql)) { rows.next(); return rows.getLong(1); } }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
