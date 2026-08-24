package vn.ptit.btl16.server.auction.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class BidRecord {
    private final long bidId;
    private final long auctionId;
    private final long userId;
    private final String username;
    private final BigDecimal amount;
    private final long serverSequence;
    private final Instant createdAt;

    public BidRecord(
            long bidId,
            long auctionId,
            long userId,
            String username,
            BigDecimal amount,
            long serverSequence,
            Instant createdAt) {
        this.bidId = bidId;
        this.auctionId = auctionId;
        this.userId = userId;
        this.username = Objects.requireNonNull(username, "username");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.serverSequence = serverSequence;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public long getBidId() { return bidId; }
    public long getAuctionId() { return auctionId; }
    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public BigDecimal getAmount() { return amount; }
    public long getServerSequence() { return serverSequence; }
    public Instant getCreatedAt() { return createdAt; }
}
