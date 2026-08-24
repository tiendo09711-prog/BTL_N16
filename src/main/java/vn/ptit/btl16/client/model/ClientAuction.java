package vn.ptit.btl16.client.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable auction state received from the authoritative server. */
public final class ClientAuction {
    private final long auctionId;
    private final long productId;
    private final String productCode;
    private final String productName;
    private final String description;
    private final long hostUserId;
    private final String hostUsername;
    private final BigDecimal startPrice;
    private final BigDecimal minBidIncrement;
    private final BigDecimal currentPrice;
    private final Long currentWinnerId;
    private final String currentWinnerUsername;
    private final Instant startTime;
    private final Instant endTime;
    private final String status;
    private final Instant endedAt;
    private final long version;
    private final int watcherCount;

    public ClientAuction(
            long auctionId,
            long productId,
            String productCode,
            String productName,
            String description,
            long hostUserId,
            String hostUsername,
            BigDecimal startPrice,
            BigDecimal minBidIncrement,
            BigDecimal currentPrice,
            Long currentWinnerId,
            String currentWinnerUsername,
            Instant startTime,
            Instant endTime,
            String status,
            Instant endedAt,
            long version,
            int watcherCount) {
        this.auctionId = auctionId;
        this.productId = productId;
        this.productCode = text(productCode);
        this.productName = text(productName);
        this.description = text(description);
        this.hostUserId = hostUserId;
        this.hostUsername = text(hostUsername);
        this.startPrice = startPrice;
        this.minBidIncrement = minBidIncrement;
        this.currentPrice = currentPrice;
        this.currentWinnerId = currentWinnerId;
        this.currentWinnerUsername = text(currentWinnerUsername);
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = text(status);
        this.endedAt = endedAt;
        this.version = version;
        this.watcherCount = watcherCount;
    }

    public ClientAuction withTiming(Instant newEndTime, String newStatus, int newWatcherCount) {
        return new ClientAuction(
                auctionId,
                productId,
                productCode,
                productName,
                description,
                hostUserId,
                hostUsername,
                startPrice,
                minBidIncrement,
                currentPrice,
                currentWinnerId,
                currentWinnerUsername,
                startTime,
                newEndTime == null ? endTime : newEndTime,
                newStatus == null || newStatus.isBlank() ? status : newStatus,
                endedAt,
                version,
                newWatcherCount < 0 ? watcherCount : newWatcherCount);
    }

    private String text(String value) { return value == null ? "" : value; }

    public long getAuctionId() { return auctionId; }
    public long getProductId() { return productId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public String getDescription() { return description; }
    public long getHostUserId() { return hostUserId; }
    public String getHostUsername() { return hostUsername; }
    public BigDecimal getStartPrice() { return startPrice; }
    public BigDecimal getMinBidIncrement() { return minBidIncrement; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public Long getCurrentWinnerId() { return currentWinnerId; }
    public String getCurrentWinnerUsername() { return currentWinnerUsername; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getStatus() { return status; }
    public Instant getEndedAt() { return endedAt; }
    public long getVersion() { return version; }
    public int getWatcherCount() { return watcherCount; }
    public boolean isOpen() { return "OPEN".equals(status); }
    public boolean isHostedBy(long userId) { return hostUserId == userId; }
}
