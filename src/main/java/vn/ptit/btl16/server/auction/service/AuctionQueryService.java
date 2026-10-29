package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;

import java.util.List;
import java.util.Locale;

public final class AuctionQueryService {
    private final AuctionManager auctions;
    private final AuctionRepository repository;
    private final int historyLimit;

    public AuctionQueryService(
            AuctionManager auctions,
            AuctionRepository repository,
            int historyLimit) {
        this.auctions = auctions;
        this.repository = repository;
        this.historyLimit = historyLimit;
    }

    public List<AuctionSnapshot> listAuctions() {
        return auctions.snapshots().stream().filter(value -> value.getStatus() == vn.ptit.btl16.server.auction.model.AuctionStatus.OPEN).toList();
    }

    public java.util.Map<String, String> topProducts() { return repository.topProducts(); }
    public java.util.Map<String, String> topSellers() { return repository.topSellers(); }

    /** Each current participant, valid bid and recorded join contributes one point; leaves add none. */
    public java.util.Map<String, String> featured(long userId, RoomManager rooms, java.time.Instant now) {
        var activity = repository.auctionActivity();
        record Candidate(AuctionSnapshot auction, int people, vn.ptit.btl16.server.auction.model.AuctionActivity stats) {
            long score() { return stats.score(people); }
        }
        var top = listAuctions().stream()
                .filter(a -> a.getVisibility() == vn.ptit.btl16.server.auction.model.RoomVisibility.PUBLIC && a.isOpenAt(now))
                .filter(a -> !repository.isAuctionUserBlocked(a.getAuctionId(), userId))
                .map(a -> new Candidate(a, rooms.participantCount(a.getAuctionId()), activity.getOrDefault(a.getAuctionId(), new vn.ptit.btl16.server.auction.model.AuctionActivity(0, 0))))
                .sorted(java.util.Comparator.comparingLong(Candidate::score).reversed()
                        .thenComparing(java.util.Comparator.comparingInt(Candidate::people).reversed())
                        .thenComparing(c -> c.auction().getEndTime()).thenComparingLong(c -> c.auction().getAuctionId()))
                .limit(5).toList();
        var data = AuctionWireData.auctionList(top.stream().map(Candidate::auction).toList(), rooms, now);
        for (int i = 0; i < top.size(); i++) {
            var c = top.get(i); String prefix = "auction." + i + ".";
            data.put(prefix + "watcherCount", Integer.toString(c.people()));
            data.put(prefix + "featuredScore", Long.toString(c.score()));
            data.put(prefix + "totalBids", Long.toString(c.stats().bidCount()));
            data.put(prefix + "movementCount", Long.toString(c.stats().movementCount()));
        }
        return data;
    }

    public List<AuctionSnapshot> searchAuctions(String rawMode, String rawQuery) {
        String mode = rawMode == null ? "ALL" : rawMode.trim().toUpperCase(Locale.ROOT);
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isBlank() || query.length() > 100) {
            throw new AuctionException(
                    vn.ptit.btl16.common.protocol.ErrorCode.INVALID_SEARCH_QUERY,
                    "Từ khóa tìm kiếm phải có 1-100 ký tự");
        }
        List<AuctionSnapshot> values = listAuctions();
        return switch (mode) {
            case "PRODUCT_NAME" -> {
                String normalized = query.toLowerCase(Locale.ROOT);
                yield values.stream()
                        .filter(value -> value.getProduct().getName()
                                .toLowerCase(Locale.ROOT).contains(normalized))
                        .toList();
            }
            case "ROOM_ID" -> {
                long auctionId;
                try {
                    auctionId = Long.parseLong(query.startsWith("#") ? query.substring(1) : query);
                } catch (NumberFormatException exception) {
                    throw new AuctionException(
                            vn.ptit.btl16.common.protocol.ErrorCode.INVALID_SEARCH_QUERY,
                            "Mã phòng phải là số");
                }
                yield values.stream().filter(value -> value.getAuctionId() == auctionId).toList();
            }
            case "ALL" -> {
                String normalized = query.toLowerCase(Locale.ROOT);
                Long id = null;
                try {
                    id = Long.parseLong(query.startsWith("#") ? query.substring(1) : query);
                } catch (NumberFormatException ignored) {
                    // Product-name search remains available.
                }
                Long roomId = id;
                yield values.stream()
                        .filter(value -> value.getProduct().getName().toLowerCase(Locale.ROOT)
                                .contains(normalized)
                                || (roomId != null && value.getAuctionId() == roomId))
                        .toList();
            }
            default -> throw new AuctionException(
                    vn.ptit.btl16.common.protocol.ErrorCode.INVALID_SEARCH_QUERY,
                    "Chế độ tìm kiếm không hợp lệ");
        };
    }

    public AuctionSnapshot getSnapshot(long auctionId) {
        return auctions.requireRuntime(auctionId).snapshot();
    }

    public List<BidRecord> getRecentBids(long auctionId) {
        auctions.requireRuntime(auctionId);
        return repository.findRecentBids(auctionId, historyLimit);
    }
}
