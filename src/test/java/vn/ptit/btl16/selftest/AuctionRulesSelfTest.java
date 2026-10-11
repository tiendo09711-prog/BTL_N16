package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.protocol.WireValues;
import vn.ptit.btl16.common.util.Money;
import vn.ptit.btl16.server.ServerSequence;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.model.AuctionRuntime;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.model.RoomVisibility;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.auction.repository.CloseAuctionCommit;
import vn.ptit.btl16.server.auction.repository.TestAuctionRepository;
import vn.ptit.btl16.server.auction.service.AuctionBroadcastService;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.auction.service.AuctionManagementService;
import vn.ptit.btl16.server.auction.service.AuctionManager;
import vn.ptit.btl16.server.auction.service.AuctionRules;
import vn.ptit.btl16.server.auction.service.BidService;
import vn.ptit.btl16.server.auction.service.RoomManager;
import vn.ptit.btl16.server.network.ConnectionRegistry;
import vn.ptit.btl16.server.network.ServerMessagingService;
import vn.ptit.btl16.server.session.SessionManager;
import vn.ptit.btl16.server.session.UserSession;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AuctionRulesSelfTest {
    private AuctionRulesSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        basicRules(new TestAuctionRepository());
        outbidProtection();
        joinKickRace();
        antiSnipingCap();
        clientOrdering();
        TestSupport.equals(new BigDecimal("100.25"), WireValues.requireDecimal(Map.of("amount", "100.25"), "amount"),
                "GUI and protocol preserve decimal values instead of stripping the decimal point");
        TestSupport.check(!Money.display(new BigDecimal("100.25")).equals(Money.display(new BigDecimal("100"))),
                "display must not hide fractional bid amounts");
        for (String raw : List.of("1e10000000", "1,000", "1.000,50", "9".repeat(33))) {
            try {
                WireValues.requireDecimal(Map.of("amount", raw), "amount");
                throw new AssertionError("Unsafe numeric syntax accepted: " + raw);
            } catch (IllegalArgumentException expected) {
                TestSupport.check(expected.getMessage() != null, "numeric rejection explains input");
            }
        }
        System.out.println("[PASS] AuctionRulesSelfTest: permissions, bid/host rules, caps, race and stale client state");
    }

    static void basicRules(AuctionRepository repository) {
        Fixture fixture = new Fixture(repository);
        AuctionSnapshot auction = fixture.create("RULES_MAIN", RoomVisibility.PRIVATE);
        long auctionId = auction.getAuctionId();
        expect(ErrorCode.ROOM_ACCESS_DENIED, () -> fixture.management.requireReadAccess(fixture.buyer, auctionId));
        fixture.management.joinAuction(fixture.buyer, "buyer", auctionId, "room1234");
        fixture.management.requireReadAccess(fixture.buyer, auctionId);
        expect(ErrorCode.AUCTION_FORBIDDEN,
                () -> fixture.management.extendAuction(fixture.buyer, auctionId, 10));
        fixture.management.extendAuction(fixture.host, auctionId, 600);
        TestSupport.equals(auction.getHardEndTime(), repository.findAuctionById(auctionId).orElseThrow().getHardEndTime(),
                "hard deadline survives repository reload and manual extension");
        expect(ErrorCode.AUCTION_EXTENSION_LIMIT,
                () -> fixture.management.extendAuction(fixture.host, auctionId, 10));
        fixture.bids.placeBid(fixture.buyer, "buyer", auctionId, new BigDecimal("110"));
        expect(ErrorCode.BID_ALREADY_LEADING,
                () -> fixture.bids.placeBid(fixture.buyer, "buyer", auctionId, new BigDecimal("120")));
        expect(ErrorCode.AUCTION_HAS_BIDS, () -> fixture.management.endAuction(fixture.host, auctionId));
        expect(ErrorCode.AUCTION_HAS_BIDS, () -> fixture.management.cancelAuction(fixture.host, auctionId));
        expect(ErrorCode.AUCTION_HAS_BIDS, () -> fixture.management.extendAuction(fixture.host, auctionId, 10));
        expect(ErrorCode.CANNOT_KICK_BIDDER, () -> fixture.management.kickUser(fixture.host, auctionId, "buyer"));
        fixture.management.joinAuction(fixture.viewer, "viewer", auctionId, "room1234");
        fixture.management.kickUser(fixture.host, auctionId, "viewer");
        expect(ErrorCode.USER_BLOCKED_FROM_AUCTION,
                () -> fixture.management.joinAuction(fixture.viewer, "viewer", auctionId, "room1234"));
        expect(ErrorCode.USER_BLOCKED_FROM_AUCTION,
                () -> fixture.management.requireReadAccess(fixture.viewer, auctionId));
        fixture.rooms.join(auctionId, "viewer", fixture.viewer.getUserId(), fixture.viewer.getUsername());
        expect(ErrorCode.USER_BLOCKED_FROM_AUCTION,
                () -> fixture.bids.placeBid(fixture.viewer, "viewer", auctionId, new BigDecimal("120")));
        expect(ErrorCode.PRODUCT_FROZEN, () -> fixture.management.updateProduct(fixture.host,
                auction.getProduct().getProductId(), "NEW_CODE", "Changed item", "Changed description"));
        AuctionSnapshot accepted = repository.findAuctionById(auctionId).orElseThrow();
        TestSupport.check(repository.closeAuction(new CloseAuctionCommit(auctionId, fixture.buyer.getUserId(),
                fixture.buyer.getUsername(), new BigDecimal("110"), accepted.getEndTime(),
                accepted.getEndTime().plusMillis(1))).isPresent(), "expired auction closes with correct winner");
        TestSupport.check(repository.closeAuction(new CloseAuctionCommit(auctionId, fixture.buyer.getUserId(),
                fixture.buyer.getUsername(), new BigDecimal("110"), accepted.getEndTime(),
                accepted.getEndTime().plusMillis(2))).isEmpty(), "auction closes once");
        expect(ErrorCode.PRODUCT_ALREADY_SOLD, () -> fixture.management.createAuction(fixture.host,
                auction.getProduct().getProductId(), new BigDecimal("100"), new BigDecimal("10"), 1));
        Product impossible = TestMedia.create(fixture.management, fixture.host, "IMPOSSIBLE", "Impossible", "");
        expect(ErrorCode.INVALID_INCREMENT, () -> fixture.management.createAuction(fixture.host,
                impossible.getProductId(), AuctionRules.MAX_MONEY, new BigDecimal("0.01"), 1));
        AuctionSnapshot empty = fixture.create("EMPTY_END", RoomVisibility.PUBLIC);
        fixture.management.endAuction(fixture.host, empty.getAuctionId());
        TestSupport.check(repository.findAuctionById(empty.getAuctionId()).orElseThrow().getCurrentWinnerId() == null,
                "empty early end does not invent a winner");
    }

    private static void outbidProtection() {
        Fixture fixture = new Fixture(new TestAuctionRepository());
        long auctionId = fixture.create("OUTBID_PROTECTED", RoomVisibility.PUBLIC).getAuctionId();
        fixture.management.joinAuction(fixture.buyer, "buyer", auctionId);
        fixture.management.joinAuction(fixture.viewer, "viewer", auctionId);
        fixture.bids.placeBid(fixture.buyer, "buyer", auctionId, new BigDecimal("110"));
        fixture.bids.placeBid(fixture.viewer, "viewer", auctionId, new BigDecimal("120"));
        expect(ErrorCode.CANNOT_KICK_BIDDER, () -> fixture.management.kickUser(fixture.host, auctionId, "buyer"));
        fixture.bids.placeBid(fixture.buyer, "buyer", auctionId, new BigDecimal("130"));
        TestSupport.equals(fixture.buyer.getUserId(),
                fixture.auctions.requireRuntime(auctionId).snapshot().getCurrentWinnerId(),
                "outbid users remain eligible and can regain the lead");
    }

    private static void joinKickRace() throws Exception {
        TestAuctionRepository storage = new TestAuctionRepository();
        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch continueJoin = new CountDownLatch(1);
        AtomicBoolean pause = new AtomicBoolean(false);
        AuctionRepository repository = (AuctionRepository) Proxy.newProxyInstance(
                AuctionRepository.class.getClassLoader(), new Class<?>[]{AuctionRepository.class},
                (proxy, method, arguments) -> {
                    try {
                        Object result = method.invoke(storage, arguments);
                        if (method.getName().equals("isAuctionUserBlocked") && pause.compareAndSet(true, false)) {
                            checked.countDown();
                            TestSupport.check(continueJoin.await(5, TimeUnit.SECONDS), "race gate released");
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
        Fixture fixture = new Fixture(repository);
        long auctionId = fixture.create("RACE", RoomVisibility.PUBLIC).getAuctionId();
        fixture.management.joinAuction(fixture.viewer, "viewer", auctionId);
        pause.set(true);
        CompletableFuture<Void> joining = CompletableFuture.runAsync(() -> expect(
                ErrorCode.USER_BLOCKED_FROM_AUCTION,
                () -> fixture.management.joinAuction(fixture.viewer, "viewer", auctionId)));
        try {
            TestSupport.check(checked.await(5, TimeUnit.SECONDS), "join reached the old permission check");
            fixture.management.kickUser(fixture.host, auctionId, "viewer");
        } finally {
            continueJoin.countDown();
        }
        joining.get(5, TimeUnit.SECONDS);
        TestSupport.check(!fixture.rooms.isMember(auctionId, "viewer"), "pending join cannot undo a kick");
    }

    private static void antiSnipingCap() {
        TestAuctionRepository repository = new TestAuctionRepository();
        Fixture fixture = new Fixture(repository);
        Product product = TestMedia.create(fixture.management, fixture.host, "CAP", "Cap item", "");
        Instant now = Instant.now();
        AuctionSnapshot nearEnd = new AuctionSnapshot(999L, product, 1L, "host", new BigDecimal("100"),
                new BigDecimal("10"), new BigDecimal("100"), null, "", now.minusSeconds(1),
                now.plusSeconds(3), AuctionStatus.OPEN, null, 0L, RoomVisibility.PUBLIC, "", "", 0,
                now.plusSeconds(4));
        repository.addAuction(nearEnd);
        fixture.auctions.addRuntime(nearEnd);
        fixture.management.joinAuction(fixture.buyer, "buyer", 999L);
        fixture.bids.placeBid(fixture.buyer, "buyer", 999L, new BigDecimal("110"));
        TestSupport.equals(nearEnd.getHardEndTime(), repository.findAuctionById(999L).orElseThrow().getEndTime(),
                "automatic extension is clamped to the persistent cap");
        AuctionRuntime reloaded = new AuctionRuntime(repository.findAuctionById(999L).orElseThrow());
        TestSupport.equals(nearEnd.getHardEndTime(), reloaded.getHardEndTimeUnsafe(), "reload cannot reset cap");
    }

    private static void clientOrdering() {
        ClientAppModel model = new ClientAppModel();
        Instant now = Instant.now();
        ClientAuction old = clientAuction(1L, "OPEN", "110");
        ClientAuction newest = clientAuction(2L, "OPEN", "120");
        model.applySnapshot(newest, List.of(), now, true);
        model.applySnapshot(old, List.of(), now.minusSeconds(1), false);
        model.replaceAuctions(List.of(old), now.minusSeconds(1));
        TestSupport.equals(new BigDecimal("120"), model.joinedAuction().getCurrentPrice(), "old list/snapshot cannot lower price");
        model.replaceAuctions(List.of(), now.minusMillis(1));
        TestSupport.check(model.joinedAuction() != null, "a list begun before the room update cannot remove that room");
        model.applyTick(1L, newest.getEndTime().plusSeconds(30), "OPEN", 4, 3L, now.plusMillis(1));
        TestSupport.equals(newest.getEndTime(), model.joinedAuction().getEndTime(),
                "a higher-version partial tick waits for a complete snapshot");
        model.applyTick(1L, newest.getEndTime(), "OPEN", 4, 2L, now.plusMillis(2));
        TestSupport.equals(4, model.joinedAuction().getWatcherCount(), "matching-version ticks update watchers");
        model.applySnapshot(clientAuction(3L, "ENDED", "120"), List.of(), now.plusSeconds(1), false);
        model.applyTick(1L, old.getEndTime(), "OPEN", 1, 1L, now.minusSeconds(1));
        TestSupport.equals("ENDED", model.joinedAuction().getStatus(), "old tick cannot reopen an ended auction");
        model.markKicked(1L);
        model.applySnapshot(newest, List.of(), now, true);
        TestSupport.check(model.getJoinedAuctionId() == null, "late join response cannot undo a kick");
    }

    private static ClientAuction clientAuction(long version, String status, String price) {
        Instant end = Instant.parse("2026-10-11T12:00:00Z");
        return new ClientAuction(1L, 1L, "CODE", "Name", "Description", 1L, "host",
                new BigDecimal("100"), new BigDecimal("10"), new BigDecimal(price), 2L, "buyer",
                end.minusSeconds(60), end, status, "ENDED".equals(status) ? end : null, version, 1);
    }

    static void expect(ErrorCode code, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected " + code);
        } catch (AuctionException exception) {
            TestSupport.equals(code, exception.getErrorCode(), "rule error code");
        }
    }

    static final class Fixture {
        final RoomManager rooms = new RoomManager();
        final SessionManager sessions = new SessionManager(Duration.ofSeconds(120));
        final UserSession host = sessions.createSession(1L, "host", "host", "local");
        final UserSession buyer = sessions.createSession(2L, "buyer", "buyer", "local");
        final UserSession viewer = sessions.createSession(3L, "viewer", "viewer", "local");
        final AuctionManager auctions;
        final AuctionManagementService management;
        final BidService bids;

        Fixture(AuctionRepository repository) {
            ServerConfig config = ServerConfig.forTests(0, 10, 10, 60);
            ServerSequence sequence = new ServerSequence(repository.findMaxServerSequence());
            auctions = new AuctionManager(repository, 120);
            AuctionBroadcastService broadcasts = new AuctionBroadcastService(rooms,
                    new ServerMessagingService(new ConnectionRegistry(), sessions, sequence));
            management = new AuctionManagementService(auctions, repository, rooms, broadcasts,
                    new Pbkdf2PasswordHasher(30000), sessions);
            bids = new BidService(auctions, repository, rooms, broadcasts, sequence, config);
        }

        AuctionSnapshot create(String code, RoomVisibility visibility) {
            Product product = TestMedia.create(management, host, code, "Rule item", "Rule description");
            return management.createAuction(host, product.getProductId(), new BigDecimal("100"),
                    new BigDecimal("10"), 1, visibility, "room1234");
        }
    }
}
