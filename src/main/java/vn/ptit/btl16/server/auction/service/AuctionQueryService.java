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
        return auctions.snapshots();
    }

    public List<AuctionSnapshot> searchAuctions(String rawMode, String rawQuery) {
        String mode = rawMode == null ? "ALL" : rawMode.trim().toUpperCase(Locale.ROOT);
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isBlank() || query.length() > 100) {
            throw new AuctionException(
                    vn.ptit.btl16.common.protocol.ErrorCode.INVALID_SEARCH_QUERY,
                    "Tu khoa tim kiem phai co 1-100 ky tu");
        }
        List<AuctionSnapshot> values = auctions.snapshots();
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
                            "Ma phong phai la so");
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
                    "Che do tim kiem khong hop le");
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
