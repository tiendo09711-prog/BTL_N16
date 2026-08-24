package vn.ptit.btl16.server.auction.repository;

import java.math.BigDecimal;
import java.time.Instant;

public final class BidCommit {
    private final long auctionId;
    private final BigDecimal expectedCurrentPrice;
    private final long bidderId;
    private final String bidderUsername;
    private final BigDecimal amount;
    private final long serverSequence;
    private final Instant createdAt;
    private final Instant newEndTime;

    public BidCommit(
            long auctionId,
            BigDecimal expectedCurrentPrice,
            long bidderId,
            String bidderUsername,
            BigDecimal amount,
            long serverSequence,
            Instant createdAt,
            Instant newEndTime) {
        this.auctionId = auctionId;
        this.expectedCurrentPrice = expectedCurrentPrice;
        this.bidderId = bidderId;
        this.bidderUsername = bidderUsername;
        this.amount = amount;
        this.serverSequence = serverSequence;
        this.createdAt = createdAt;
        this.newEndTime = newEndTime;
    }

    public long getAuctionId() { return auctionId; }
    public BigDecimal getExpectedCurrentPrice() { return expectedCurrentPrice; }
    public long getBidderId() { return bidderId; }
    public String getBidderUsername() { return bidderUsername; }
    public BigDecimal getAmount() { return amount; }
    public long getServerSequence() { return serverSequence; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getNewEndTime() { return newEndTime; }
}
