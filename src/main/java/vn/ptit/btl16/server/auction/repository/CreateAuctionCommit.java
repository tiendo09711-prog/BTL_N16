package vn.ptit.btl16.server.auction.repository;

import java.math.BigDecimal;
import java.time.Instant;

public final class CreateAuctionCommit {
    private final long hostUserId;
    private final String hostUsername;
    private final long productId;
    private final BigDecimal startPrice;
    private final BigDecimal minBidIncrement;
    private final Instant startTime;
    private final Instant endTime;

    public CreateAuctionCommit(
            long hostUserId,
            String hostUsername,
            long productId,
            BigDecimal startPrice,
            BigDecimal minBidIncrement,
            Instant startTime,
            Instant endTime) {
        this.hostUserId = hostUserId;
        this.hostUsername = hostUsername;
        this.productId = productId;
        this.startPrice = startPrice;
        this.minBidIncrement = minBidIncrement;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public long getHostUserId() { return hostUserId; }
    public String getHostUsername() { return hostUsername; }
    public long getProductId() { return productId; }
    public BigDecimal getStartPrice() { return startPrice; }
    public BigDecimal getMinBidIncrement() { return minBidIncrement; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
}
