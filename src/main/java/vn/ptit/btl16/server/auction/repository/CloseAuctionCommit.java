package vn.ptit.btl16.server.auction.repository;

import java.math.BigDecimal;
import java.time.Instant;

public final class CloseAuctionCommit {
    private final long auctionId;
    private final Long winnerId;
    private final String winnerUsername;
    private final BigDecimal finalPrice;
    private final Instant expectedEndTime;
    private final Instant endedAt;
    private final boolean requireExpired;

    public CloseAuctionCommit(
            long auctionId,
            Long winnerId,
            String winnerUsername,
            BigDecimal finalPrice,
            Instant expectedEndTime,
            Instant endedAt) {
        this(auctionId, winnerId, winnerUsername, finalPrice, expectedEndTime, endedAt, true);
    }

    public CloseAuctionCommit(
            long auctionId,
            Long winnerId,
            String winnerUsername,
            BigDecimal finalPrice,
            Instant expectedEndTime,
            Instant endedAt,
            boolean requireExpired) {
        this.auctionId = auctionId;
        this.winnerId = winnerId;
        this.winnerUsername = winnerUsername;
        this.finalPrice = finalPrice;
        this.expectedEndTime = expectedEndTime;
        this.endedAt = endedAt;
        this.requireExpired = requireExpired;
    }

    public long getAuctionId() { return auctionId; }
    public Long getWinnerId() { return winnerId; }
    public String getWinnerUsername() { return winnerUsername; }
    public BigDecimal getFinalPrice() { return finalPrice; }
    public Instant getExpectedEndTime() { return expectedEndTime; }
    public Instant getEndedAt() { return endedAt; }
    public boolean isRequireExpired() { return requireExpired; }
}
