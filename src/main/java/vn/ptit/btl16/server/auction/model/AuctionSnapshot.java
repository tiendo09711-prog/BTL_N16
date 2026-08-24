package vn.ptit.btl16.server.auction.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class AuctionSnapshot {
    private final long auctionId;
    private final Product product;
    private final long hostUserId;
    private final String hostUsername;
    private final BigDecimal startPrice;
    private final BigDecimal minBidIncrement;
    private final BigDecimal currentPrice;
    private final Long currentWinnerId;
    private final String currentWinnerUsername;
    private final Instant startTime;
    private final Instant endTime;
    private final AuctionStatus status;
    private final Instant endedAt;
    private final long version;

    public AuctionSnapshot(
            long auctionId,
            Product product,
            BigDecimal startPrice,
            BigDecimal currentPrice,
            Long currentWinnerId,
            String currentWinnerUsername,
            Instant startTime,
            Instant endTime,
            AuctionStatus status,
            Instant endedAt,
            long version) {
        this(
                auctionId,
                product,
                0L,
                "",
                startPrice,
                new BigDecimal("0.01"),
                currentPrice,
                currentWinnerId,
                currentWinnerUsername,
                startTime,
                endTime,
                status,
                endedAt,
                version);
    }

    public AuctionSnapshot(
            long auctionId,
            Product product,
            long hostUserId,
            String hostUsername,
            BigDecimal startPrice,
            BigDecimal minBidIncrement,
            BigDecimal currentPrice,
            Long currentWinnerId,
            String currentWinnerUsername,
            Instant startTime,
            Instant endTime,
            AuctionStatus status,
            Instant endedAt,
            long version) {
        this.auctionId = auctionId;
        this.product = Objects.requireNonNull(product, "product");
        this.hostUserId = hostUserId;
        this.hostUsername = hostUsername == null ? "" : hostUsername;
        this.startPrice = Objects.requireNonNull(startPrice, "startPrice");
        this.minBidIncrement = Objects.requireNonNull(minBidIncrement, "minBidIncrement");
        this.currentPrice = Objects.requireNonNull(currentPrice, "currentPrice");
        this.currentWinnerId = currentWinnerId;
        this.currentWinnerUsername = currentWinnerUsername == null ? "" : currentWinnerUsername;
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        this.status = Objects.requireNonNull(status, "status");
        this.endedAt = endedAt;
        this.version = version;
    }

    public long getAuctionId() { return auctionId; }
    public Product getProduct() { return product; }
    public long getHostUserId() { return hostUserId; }
    public String getHostUsername() { return hostUsername; }
    public BigDecimal getStartPrice() { return startPrice; }
    public BigDecimal getMinBidIncrement() { return minBidIncrement; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public Long getCurrentWinnerId() { return currentWinnerId; }
    public String getCurrentWinnerUsername() { return currentWinnerUsername; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public AuctionStatus getStatus() { return status; }
    public Instant getEndedAt() { return endedAt; }
    public long getVersion() { return version; }

    public long remainingMillis(Instant now) {
        return Math.max(0L, Duration.between(now, endTime).toMillis());
    }

    public boolean isOpenAt(Instant now) {
        return status == AuctionStatus.OPEN && now.isBefore(endTime);
    }

    public boolean isHostedBy(long userId) {
        return hostUserId == userId;
    }
}
