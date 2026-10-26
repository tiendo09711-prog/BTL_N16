package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.auction.model.*;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.account.repository.UserRepository;
import vn.ptit.btl16.server.routing.*;
import java.time.Instant;
import java.util.*;

public final class RoomProgressService {
    private final AuctionManager auctions;
    private final AuctionManagementService management;
    private final AuctionRepository repository;
    private final RoomManager rooms;
    private final UserRepository users;

    public RoomProgressService(AuctionManager auctions, AuctionManagementService management,
                               AuctionRepository repository, RoomManager rooms, UserRepository users) {
        this.auctions = auctions; this.management = management; this.repository = repository;
        this.rooms = rooms; this.users = users;
    }

    public void register(MessageRouter router) {
        router.register(MessageType.GET_ROOM_EVENTS, true, context -> read(context, false));
        router.register(MessageType.GET_AUCTION_STATS, true, context -> read(context, true));
    }

    public void history(RequestContext context) throws Exception {
        long room = WireValues.requireLong(context.getRequest().getData(), "auctionId");
        var runtime = readRuntime(room); runtime.getLock().lock();
        try {
            management.requireHistoryReadAccess(context.requireSession(), room);
            int limit = limit(context);
            long before = cursor(context, "beforeSequence", Long.MAX_VALUE);
            long through = context.value("throughSequence").isBlank()
                    ? repository.findRecentBids(room, 1).stream().findFirst().map(BidRecord::getServerSequence).orElse(0L)
                    : cursor(context, "throughSequence", 0);
            boolean mine = Boolean.parseBoolean(context.value("mine"));
            var rows = repository.bidPage(room, before, through, mine ? context.requireSession().getUserId() : null, limit + 1);
            boolean more = rows.size() > limit; rows = rows.subList(0, Math.min(rows.size(), limit));
            var data = AuctionWireData.history(rows.stream().map(RoomProgress.HistoryBid::bid).toList(), Instant.now());
            data.put("auctionId", Long.toString(room)); data.put("throughSequence", Long.toString(through));
            data.put("hasMore", Boolean.toString(more)); data.put("mine", Boolean.toString(mine));
            data.put("nextBeforeSequence", rows.isEmpty() ? "0" : Long.toString(rows.get(rows.size() - 1).bid().getServerSequence()));
            for (int index = 0; index < rows.size(); index++) { data.put("bid." + index + ".increase", rows.get(index).increase().toPlainString()); }
            context.replySuccess(MessageType.BID_HISTORY_RESULT, data);
        } finally { runtime.getLock().unlock(); }
    }

    private void read(RequestContext context, boolean statsOnly) throws Exception {
        long room = WireValues.requireLong(context.getRequest().getData(), "auctionId");
        var runtime = readRuntime(room); runtime.getLock().lock();
        try {
            management.requireHistoryReadAccess(context.requireSession(), room);
            Map<String, String> data = new LinkedHashMap<>();
            data.put("auctionId", Long.toString(room)); data.put("serverNow", Instant.now().toString());
            if (statsOnly) {
                var stats = repository.roomStats(room);
                data.put("totalBids", Long.toString(stats.bidCount()));
                data.put("uniqueBidders", Long.toString(stats.bidderCount()));
                data.put("extensionCount", Long.toString(stats.extensionCount()));
                data.put("extensionsComplete", Boolean.toString(stats.extensionsComplete()));
                data.put("priceIncrease", stats.increase().toPlainString());
                data.put("watcherConnectionCount", Integer.toString(rooms.subscriberCount(room)));
                data.put("presentAccountCount", Long.toString(rooms.membersSnapshot(room).stream().map(RoomMember::getUserId).distinct().count()));
            } else {
                int limit = limit(context); long after = cursor(context, "afterId", 0);
                long before = cursor(context, "beforeId", Long.MAX_VALUE);
                if (after > 0 && before != Long.MAX_VALUE) { throw new IllegalArgumentException("Choose beforeId or afterId"); }
                var events = repository.findRoomEvents(room, before, after, limit + 1);
                boolean more = events.size() > limit; events = events.subList(0, Math.min(limit, events.size()));
                data.put("hasMore", Boolean.toString(more)); data.put("eventCount", Integer.toString(events.size()));
                data.put("nextBeforeId", events.isEmpty() ? "0" : Long.toString(events.stream().mapToLong(RoomProgress.Event::id).min().orElse(0)));
                data.put("nextAfterId", events.isEmpty() ? Long.toString(after) : Long.toString(events.stream().mapToLong(RoomProgress.Event::id).max().orElse(after)));
                for (int index = 0; index < events.size(); index++) {
                    var event = events.get(index); String prefix = "event." + index + ".";
                    data.put(prefix + "id", Long.toString(event.id())); data.put(prefix + "type", event.type());
                    data.put(prefix + "time", event.time().toString()); data.put(prefix + "detail", event.detail());
                    data.put(prefix + "amount", event.amount() == null ? "" : event.amount().toPlainString());
                    data.put(prefix + "userId", event.userId() == null ? "" : Long.toString(event.userId()));
                    String username = event.username();
                    if (event.userId() != null && (username == null || username.isBlank())) {
                        username = users.findById(event.userId()).map(user -> user.getUsername()).orElse("Participant");
                    }
                    data.put(prefix + "username", event.userId() == null ? "" : username);
                }
            }
            context.replySuccess(statsOnly ? MessageType.AUCTION_STATS_RESULT : MessageType.ROOM_EVENTS_RESULT, data);
        } finally { runtime.getLock().unlock(); }
    }

    private static long cursor(RequestContext context, String key, long fallback) {
        if (context.value(key).isBlank()) { return fallback; }
        long value = Long.parseLong(context.value(key));
        if (value < 0) { throw new IllegalArgumentException("Invalid cursor"); }
        return value;
    }
    private AuctionRuntime readRuntime(long room) {
        return auctions.findRuntimeIncludingArchived(room).orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND, "Auction was not found"));
    }
    private static int limit(RequestContext context) {
        return (int) Math.max(1, Math.min(100, cursor(context, "limit", 50)));
    }
}
