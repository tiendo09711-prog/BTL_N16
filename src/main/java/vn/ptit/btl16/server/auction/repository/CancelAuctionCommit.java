package vn.ptit.btl16.server.auction.repository;

import java.time.Instant;

public final class CancelAuctionCommit {
    private final long auctionId;
    private final Instant cancelledAt;

    public CancelAuctionCommit(long auctionId, Instant cancelledAt) {
        this.auctionId = auctionId;
        this.cancelledAt = cancelledAt;
    }

    public long getAuctionId() { return auctionId; }
    public Instant getCancelledAt() { return cancelledAt; }
}
