package vn.ptit.btl16.server.auction.repository;

import java.time.Instant;

public final class BlockAuctionUserCommit {
    private final long auctionId;
    private final long userId;
    private final long blockedBy;
    private final Instant blockedAt;

    public BlockAuctionUserCommit(long auctionId, long userId, long blockedBy, Instant blockedAt) {
        this.auctionId = auctionId;
        this.userId = userId;
        this.blockedBy = blockedBy;
        this.blockedAt = blockedAt;
    }

    public long getAuctionId() { return auctionId; }
    public long getUserId() { return userId; }
    public long getBlockedBy() { return blockedBy; }
    public Instant getBlockedAt() { return blockedAt; }
}
