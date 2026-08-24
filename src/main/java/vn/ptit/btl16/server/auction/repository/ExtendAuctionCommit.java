package vn.ptit.btl16.server.auction.repository;

import java.time.Instant;

public final class ExtendAuctionCommit {
    private final long auctionId;
    private final Instant expectedEndTime;
    private final Instant newEndTime;

    public ExtendAuctionCommit(long auctionId, Instant expectedEndTime, Instant newEndTime) {
        this.auctionId = auctionId;
        this.expectedEndTime = expectedEndTime;
        this.newEndTime = newEndTime;
    }

    public long getAuctionId() { return auctionId; }
    public Instant getExpectedEndTime() { return expectedEndTime; }
    public Instant getNewEndTime() { return newEndTime; }
}
