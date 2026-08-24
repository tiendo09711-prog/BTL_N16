package vn.ptit.btl16.server.auction.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class AuctionResult {
    private final long auctionId;
    private final Long winnerId;
    private final String winnerUsername;
    private final BigDecimal finalPrice;
    private final Instant endedAt;

    public AuctionResult(
            long auctionId,
            Long winnerId,
            String winnerUsername,
            BigDecimal finalPrice,
            Instant endedAt) {
        this.auctionId = auctionId;
        this.winnerId = winnerId;
        this.winnerUsername = winnerUsername == null ? "" : winnerUsername;
        this.finalPrice = Objects.requireNonNull(finalPrice, "finalPrice");
        this.endedAt = Objects.requireNonNull(endedAt, "endedAt");
    }

    public long getAuctionId() { return auctionId; }
    public Long getWinnerId() { return winnerId; }
    public String getWinnerUsername() { return winnerUsername; }
    public BigDecimal getFinalPrice() { return finalPrice; }
    public Instant getEndedAt() { return endedAt; }
}
