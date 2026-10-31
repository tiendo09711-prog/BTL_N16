package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.TestUserRepository;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.RoomVisibility;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.dashboard.AuctionSessionService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;

public final class AuctionSessionsSelfTest {
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        try (var fixture = new Fixture(565, 525)) {
            var service = fixture.server.auctionSessions();
            var all = new AuctionSessionService.Filter("", null, null, null);
            TestSupport.equals(4, service.list(all).size(), "admin includes archived, cancelled, anonymous and open rooms");
            TestSupport.equals(2, service.list(new AuctionSessionService.Filter("", null, null, false)).size(), "open filter");
            TestSupport.equals(2, service.list(new AuctionSessionService.Filter("", null, null, true)).size(), "ended filter includes cancellations");
            TestSupport.equals(1, service.list(new AuctionSessionService.Filter(fixture.open.getRoomCode().toLowerCase(), null, null, null)).size(), "case-insensitive exact room code");
            TestSupport.equals(1, service.list(new AuctionSessionService.Filter(Long.toString(fixture.open.getAuctionId()), null, null, null)).size(), "numeric legacy identifier");
            TestSupport.equals(0, service.list(new AuctionSessionService.Filter("missing", null, null, null)).size(), "unknown room code");
            TestSupport.equals(2, service.list(new AuctionSessionService.Filter("", fixture.now.minusSeconds(3600), fixture.now.plusSeconds(3600), null)).size(), "date interval filters start time");
            TestSupport.equals(0, service.list(new AuctionSessionService.Filter("", fixture.open.getStartTime().minusSeconds(1), fixture.open.getStartTime(), null)).size(), "end bound is exclusive");
            try {
                new AuctionSessionService.Filter("", fixture.now, fixture.now, null);
                throw new AssertionError("invalid dates must be rejected");
            } catch (IllegalArgumentException expected) { }

            var details = service.details(fixture.open.getAuctionId(), true);
            TestSupport.equals(565, details.participants().size(), "attendance is complete beyond 500 rows");
            var alice = details.participants().stream().filter(person -> person.id() == 2).findFirst().orElseThrow();
            TestSupport.equals("alice", alice.username(), "visible username");
            TestSupport.equals("Alice", alice.name(), "visible display name");
            TestSupport.equals(fixture.now.minusSeconds(20), alice.joinedAt(), "first room entry survives leaving");
            TestSupport.equals(fixture.now.minusSeconds(1), alice.lastJoinedAt(), "last reentry is preserved");
            TestSupport.equals(2L, alice.visits(), "reentry does not duplicate member");
            TestSupport.equals("demo", details.seller().username(), "full seller identity");
            TestSupport.equals("demo@local.test", details.seller().email(), "seller email");
            TestSupport.check(java.util.Arrays.stream(AuctionSessionService.Profile.class.getRecordComponents())
                    .noneMatch(component -> component.getName().toLowerCase().contains("password")
                            || component.getName().toLowerCase().contains("salt")), "seller DTO excludes password material");
            TestSupport.equals("525", details.chartData().get("bidCount"), "chart includes every bid beyond one page");
            TestSupport.check(!details.pictures().isEmpty(), "product pictures loaded locally");
            TestSupport.check(service.cover(details.auction().getProduct().getProductId()).length > 0, "photo cards use lightweight local thumbnail bytes");
            TestSupport.check(service.details(fixture.open.getAuctionId(), false).pictures().isEmpty(), "poll does not reread image bytes");
            TestSupport.equals("525", service.history(fixture.open.getAuctionId(), MessageType.GET_AUCTION_STATS, Map.of()).get("totalBids"), "full-session statistics");

            Map<String, String> page = service.history(fixture.open.getAuctionId(), MessageType.GET_ROOM_EVENTS,
                    Map.of("beforeId", Long.toString(Long.MAX_VALUE), "afterId", "0", "limit", "50"));
            TestSupport.equals("50", page.get("eventCount"), "journal pages remain bounded");
            TestSupport.equals("true", page.get("hasMore"), "older journal remains accessible");
            var eventIds = new HashSet<String>();
            while (true) {
                int count = Integer.parseInt(page.get("eventCount"));
                long before = Long.MAX_VALUE;
                for (int index = 0; index < count; index++) {
                    String id = page.get("event." + index + ".id");
                    TestSupport.check(eventIds.add(id), "journal cursor never duplicates events");
                    before = Math.min(before, Long.parseLong(id));
                }
                if (!Boolean.parseBoolean(page.get("hasMore"))) { break; }
                page = service.history(fixture.open.getAuctionId(), MessageType.GET_ROOM_EVENTS,
                        Map.of("beforeId", Long.toString(before), "afterId", "0"));
            }
            TestSupport.equals(fixture.repository.findRoomEvents(fixture.open.getAuctionId(), Long.MAX_VALUE, 0, 10000).size(),
                    eventIds.size(), "all journal events can be read");

            var hidden = service.details(fixture.anonymous.getAuctionId(), false);
            TestSupport.equals(3, hidden.participants().size(), "anonymous attendance remains complete");
            TestSupport.check(hidden.participants().stream().allMatch(person -> person.anonymous() && person.id() < 0
                    && person.avatar().isEmpty() && !person.username().equals("alice") && !person.name().equals("Alice")), "anonymous roster hides all identities");
            TestSupport.check(!hidden.chartData().containsValue("alice") && !hidden.chartData().containsValue("bob")
                    && !hidden.chartData().containsValue("demo"), "chart and leader use anonymous aliases");
            String events = service.history(fixture.anonymous.getAuctionId(), MessageType.GET_ROOM_EVENTS, Map.of()).toString();
            TestSupport.check(!events.contains("alice") && !events.contains("bob") && !events.contains("demo"), "journal masks identities");
            TestSupport.check(service.leader(hidden.auction()).startsWith("Khách #"), "leader is anonymous");
            int attendanceBefore = fixture.repository.findRoomEvents(fixture.open.getAuctionId(), Long.MAX_VALUE, 0, 10000).size();
            service.details(fixture.open.getAuctionId(), false);
            TestSupport.equals(attendanceBefore, fixture.repository.findRoomEvents(fixture.open.getAuctionId(), Long.MAX_VALUE, 0, 10000).size(), "admin inspection creates no presence events");
        }
        System.out.println("[PASS] AuctionSessionsSelfTest: archived rooms, filters, full attendance/chart, safe seller DTO and anonymous history");
    }

    static final class Fixture implements AutoCloseable {
        final Instant now = Instant.now();
        final TestAuctionRepository repository = new TestAuctionRepository();
        final AuctionSnapshot open;
        final AuctionSnapshot anonymous;
        final AuctionSnapshot ended;
        final ServerApplication server;

        Fixture(int participants, int bids) {
            var config = ServerConfig.forTests(0, 2, 2, 1, 1);
            var hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
            var users = TestUserRepository.withDemoUsers(hasher);
            open = room("ADMIN_OPEN", now.minusSeconds(60), false);
            for (int index = 0; index < participants; index++) {
                repository.appendPresence(open.getAuctionId(), "JOINED", index + 1, index == 1 ? now.minusSeconds(20) : now.minusSeconds(30));
            }
            repository.appendPresence(open.getAuctionId(), "LEFT", 2, now.minusSeconds(10));
            repository.appendPresence(open.getAuctionId(), "JOINED", 2, now.minusSeconds(1));
            placeBids(open, bids, 1);
            anonymous = room("ADMIN_ANONYMOUS", now.minusSeconds(60), true);
            for (int index = 1; index <= 3; index++) { repository.appendPresence(anonymous.getAuctionId(), "JOINED", index, now.minusSeconds(20)); }
            placeBids(anonymous, 2, bids + 1);
            ended = room("ADMIN_ENDED", now.minusSeconds(86400), false);
            repository.closeAuction(new CloseAuctionCommit(ended.getAuctionId(), null, "", ended.getCurrentPrice(), ended.getEndTime(), now.minusSeconds(10000), false));
            var cancelled = room("ADMIN_CANCELLED", now.minusSeconds(86400), false);
            repository.cancelAuction(new CancelAuctionCommit(cancelled.getAuctionId(), now.minusSeconds(10000)));
            repository.findAllAuctions().forEach(this::coded);
            server = ServerApplication.createForTests(config, users, repository, hasher);
        }

        private AuctionSnapshot room(String code, Instant start, boolean anonymous) {
            var product = TestMedia.create(repository, new CreateProductCommit(1, "demo", code, code, TestMedia.DESCRIPTION, now));
            return coded(repository.createAuction(new CreateAuctionCommit(1, "demo", product.getProductId(),
                    new BigDecimal("100000"), new BigDecimal("10000"), start, now.plusSeconds(600),
                    RoomVisibility.PUBLIC, "", "", 0, anonymous)));
        }

        private AuctionSnapshot coded(AuctionSnapshot auction) {
            var coded = new AuctionSnapshot(auction.getAuctionId(), auction.getProduct(), auction.getHostUserId(),
                    auction.getHostUsername(), auction.getStartPrice(), auction.getMinBidIncrement(), auction.getCurrentPrice(),
                    auction.getCurrentWinnerId(), auction.getCurrentWinnerUsername(), auction.getStartTime(), auction.getEndTime(),
                    auction.getStatus(), auction.getEndedAt(), auction.getVersion(), auction.getVisibility(), "", "", 0,
                    auction.getHardEndTime(), auction.hasAnonymousGuests(), auction.getMaxParticipants(), auction.getDepositAmount(),
                    "ROOM_" + auction.getProduct().getCode());
            repository.addAuction(coded);
            return coded;
        }

        private void placeBids(AuctionSnapshot room, int count, long sequence) {
            for (int index = 0; index < count; index++) {
                long bidder = index % 2 == 0 ? 2 : 3;
                repository.commitAcceptedBid(new BidCommit(room.getAuctionId(), BigDecimal.valueOf(100000L + index * 10000L),
                        bidder, bidder == 2 ? "alice" : "bob", BigDecimal.valueOf(110000L + index * 10000L),
                        sequence + index, now.minusSeconds(5), room.getEndTime()));
            }
        }

        @Override public void close() { server.close(); }
    }
}
