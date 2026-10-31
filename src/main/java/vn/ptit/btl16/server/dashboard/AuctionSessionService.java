package vn.ptit.btl16.server.dashboard;

import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.account.repository.UserRepository;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.auction.service.AuctionManager;
import vn.ptit.btl16.server.auction.service.AuctionWireData;
import vn.ptit.btl16.server.social.RoomSocialStore;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AuctionSessionService {
    public record Filter(String roomCode, Instant from, Instant to, Boolean ended) {
        public Filter {
            roomCode = roomCode == null ? "" : roomCode.strip();
            if (from != null && to != null && !from.isBefore(to)) {
                throw new IllegalArgumentException("Khoảng ngày không hợp lệ");
            }
        }
    }
    public record Profile(long id, String username, String name, String email, String phone,
                          String avatar, String biography, String friendCode, boolean active,
                          Instant createdAt, Instant updatedAt, Instant lastLoginAt) { }
    public record Participant(long id, String username, String name, String avatar,
                              Instant joinedAt, Instant lastJoinedAt, long visits, boolean anonymous) { }
    public record Picture(String name, byte[] bytes) { }
    public record Details(AuctionSnapshot auction, Map<String, String> chartData, Profile seller,
                          List<Participant> participants, List<Picture> pictures, String mediaWarning) { }

    private final AuctionManager auctions;
    private final AuctionRepository repository;
    private final UserRepository users;
    private final RoomSocialStore profiles;

    public AuctionSessionService(AuctionManager auctions, AuctionRepository repository,
                                 UserRepository users, RoomSocialStore profiles) {
        this.auctions = auctions;
        this.repository = repository;
        this.users = users;
        this.profiles = profiles;
    }

    public List<AuctionSnapshot> list(Filter filter) {
        return auctions.runtimes().stream().map(runtime -> runtime.snapshot())
                .filter(auction -> filter.roomCode().isBlank()
                        || auction.getRoomCode().equalsIgnoreCase(filter.roomCode())
                        || Long.toString(auction.getAuctionId()).equals(filter.roomCode()))
                .filter(auction -> filter.from() == null || !auction.getStartTime().isBefore(filter.from()))
                .filter(auction -> filter.to() == null || auction.getStartTime().isBefore(filter.to()))
                .filter(auction -> filter.ended() == null || filter.ended() == (auction.getStatus() != AuctionStatus.OPEN))
                .sorted(Comparator.comparing(AuctionSnapshot::getStartTime).reversed()
                        .thenComparing(Comparator.comparingLong(AuctionSnapshot::getAuctionId).reversed()))
                .toList();
    }

    public Details details(long room, boolean includePictures) {
        var runtime = auctions.findRuntimeIncludingArchived(room).orElseThrow(
                () -> new IllegalArgumentException("Không tìm thấy phiên đấu giá"));
        AuctionSnapshot auction;
        long through;
        runtime.getLock().lock();
        try {
            auction = runtime.snapshot();
            through = repository.findRecentBids(room, 1).stream().findFirst().map(BidRecord::getServerSequence).orElse(0L);
        } finally { runtime.getLock().unlock(); }
        List<BidRecord> bids = new ArrayList<>();
        long before = Long.MAX_VALUE;
        while (true) {
            var page = repository.bidPage(room, before, through, null, 500);
            if (page.isEmpty()) { break; }
            for (var row : page) {
                var bid = row.bid();
                bids.add(auction.hasAnonymousGuests() ? new BidRecord(bid.getBidId(), room,
                        publicId(auction, bid.getUserId()), actorName(auction, bid.getUserId(), bid.getUsername()),
                        bid.getAmount(), bid.getServerSequence(), bid.getCreatedAt()) : bid);
            }
            long next = page.stream().mapToLong(row -> row.bid().getServerSequence()).min().orElse(0);
            if (next >= before) { throw new IllegalStateException("Bid cursor did not advance"); }
            before = next;
        }
        Map<String, String> data = AuctionWireData.snapshot(auction, bids, 0, Instant.now());
        if (auction.hasAnonymousGuests()) {
            data.put("hostUserId", Long.toString(publicId(auction, auction.getHostUserId())));
            data.put("hostUsername", "Chủ phòng");
            if (auction.getCurrentWinnerId() != null) {
                data.put("currentWinnerId", Long.toString(publicId(auction, auction.getCurrentWinnerId())));
                data.put("currentWinnerUsername", leader(auction));
            }
        }
        List<Participant> participants = repository.roomAttendance(room).stream().map(member -> {
            if (auction.hasAnonymousGuests()) {
                String alias = actorName(auction, member.userId(), "");
                return new Participant(publicId(auction, member.userId()), alias, "Đã ẩn danh tính", "",
                        member.firstJoinedAt(), member.lastJoinedAt(), member.visits(), true);
            }
            var user = users.findById(member.userId()).orElse(null);
            return new Participant(member.userId(), user == null ? "Tài khoản không còn tồn tại" : user.getUsername(),
                    user == null ? "Không còn hồ sơ" : user.getDisplayName(), profiles.details(member.userId()).avatar(),
                    member.firstJoinedAt(), member.lastJoinedAt(), member.visits(), false);
        }).toList();
        List<Picture> pictures = new ArrayList<>();
        String warning = "";
        if (includePictures) {
            for (var media : repository.findProductMedia(auction.getProduct().getProductId())) {
                try (var input = repository.mediaContent(media.id(), media.image() ? "detail.jpg" : "thumbnail.jpg").open(0)) {
                    pictures.add(new Picture(media.name(), input.readAllBytes()));
                } catch (IOException | RuntimeException error) { warning = "Một số hình ảnh chưa tải được. Hãy thử làm mới."; }
            }
            if (pictures.isEmpty() && auction.getProduct().hasImage()) {
                repository.findProductImage(auction.getProduct().getProductId())
                        .ifPresent(image -> pictures.add(new Picture(image.getName(), image.getData())));
            }
        }
        return new Details(auction, data, profile(auction.getHostUserId()), participants, pictures, warning);
    }

    public String leader(AuctionSnapshot auction) {
        return auction.getCurrentWinnerId() == null ? "Chưa có người đặt giá"
                : actorName(auction, auction.getCurrentWinnerId(), auction.getCurrentWinnerUsername());
    }

    public byte[] cover(long productId) {
        var media = repository.findProductMedia(productId).stream().filter(item -> item.image()).findFirst();
        if (media.isPresent()) {
            try (var input = repository.mediaContent(media.get().id(), "thumbnail.jpg").open(0)) {
                return input.readAllBytes();
            } catch (IOException error) { throw new IllegalStateException("Cannot load product cover", error); }
        }
        return repository.findProductImage(productId).map(image -> image.getData()).orElseGet(() -> new byte[0]);
    }

    public Map<String, String> history(long room, MessageType type, Map<String, String> request) {
        var auction = snapshot(room);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("success", "true");
        data.put("auctionId", Long.toString(room));
        if (type == MessageType.GET_AUCTION_STATS) {
            var stats = repository.roomStats(room);
            data.put("totalBids", Long.toString(stats.bidCount()));
            data.put("uniqueBidders", Long.toString(stats.bidderCount()));
            data.put("extensionCount", Long.toString(stats.extensionCount()));
            data.put("extensionsComplete", Boolean.toString(stats.extensionsComplete()));
            data.put("priceIncrease", stats.increase().toPlainString());
        } else if (type == MessageType.GET_ROOM_EVENTS) {
            long before = Long.parseLong(request.getOrDefault("beforeId", Long.toString(Long.MAX_VALUE)));
            long after = Long.parseLong(request.getOrDefault("afterId", "0"));
            int limit = Math.max(1, Math.min(100, Integer.parseInt(request.getOrDefault("limit", "50"))));
            var rows = repository.findRoomEvents(room, before, after, limit + 1);
            data.put("hasMore", Boolean.toString(rows.size() > limit));
            var events = rows.stream().limit(limit).toList();
            data.put("eventCount", Integer.toString(events.size()));
            for (int index = 0; index < events.size(); index++) {
                var event = events.get(index);
                String prefix = "event." + index + ".";
                data.put(prefix + "id", Long.toString(event.id()));
                data.put(prefix + "type", event.type());
                data.put(prefix + "time", event.time().toString());
                data.put(prefix + "detail", event.detail());
                data.put(prefix + "amount", event.amount() == null ? "" : event.amount().toPlainString());
                data.put(prefix + "username", event.userId() == null ? "" : actorName(auction, event.userId(), event.username()));
            }
        } else { throw new IllegalArgumentException("Read-only room history"); }
        return data;
    }

    private AuctionSnapshot snapshot(long room) {
        return auctions.findRuntimeIncludingArchived(room).orElseThrow(
                () -> new IllegalArgumentException("Không tìm thấy phiên đấu giá")).snapshot();
    }

    private Profile profile(long userId) {
        return users.findById(userId).map(user -> {
            var extra = profiles.details(userId);
            return new Profile(userId, user.getUsername(), user.getDisplayName(), user.getEmail(), user.getPhone(),
                    extra.avatar(), extra.biography(), user.getFriendCode(), user.isActive(), user.getCreatedAt(),
                    user.getUpdatedAt(), user.getLastLoginAt());
        }).orElse(null);
    }

    private long publicId(AuctionSnapshot auction, long userId) {
        return auction.hasAnonymousGuests() ? -profiles.ordinal(auction.getAuctionId(), userId, true) : userId;
    }

    private String actorName(AuctionSnapshot auction, long userId, String fallback) {
        if (!auction.hasAnonymousGuests()) {
            return fallback == null || fallback.isBlank()
                    ? users.findById(userId).map(user -> user.getUsername()).orElse("Tài khoản không còn tồn tại") : fallback;
        }
        if (userId == auction.getHostUserId()) { return "Chủ phòng"; }
        long ordinal = profiles.ordinal(auction.getAuctionId(), userId, true);
        long hostOrdinal = profiles.ordinal(auction.getAuctionId(), auction.getHostUserId(), false);
        return "Khách #" + (hostOrdinal > 0 && hostOrdinal < ordinal ? ordinal - 1 : ordinal);
    }
}
