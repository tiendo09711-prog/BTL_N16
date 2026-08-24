package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;

import java.util.List;

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

    public AuctionSnapshot getSnapshot(long auctionId) {
        return auctions.requireRuntime(auctionId).snapshot();
    }

    public List<BidRecord> getRecentBids(long auctionId) {
        auctions.requireRuntime(auctionId);
        return repository.findRecentBids(auctionId, historyLimit);
    }
}
