package vn.ptit.btl16.server.auction.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

public final class AuctionRuntime {
    private final long auctionId;
    private final Product product;
    private final long hostUserId;
    private final String hostUsername;
    private final BigDecimal startPrice;
    private final BigDecimal minBidIncrement;
    private final Instant startTime;
    private final ReentrantLock lock = new ReentrantLock(true);

    private BigDecimal currentPrice;
    private Long currentWinnerId;
    private String currentWinnerUsername;
    private Instant endTime;
    private AuctionStatus status;
    private Instant endedAt;
    private long version;

    public AuctionRuntime(AuctionSnapshot snapshot) {
        this.auctionId = snapshot.getAuctionId();
        this.product = snapshot.getProduct();
        this.hostUserId = snapshot.getHostUserId();
        this.hostUsername = snapshot.getHostUsername();
        this.startPrice = snapshot.getStartPrice();
        this.minBidIncrement = snapshot.getMinBidIncrement();
        this.currentPrice = snapshot.getCurrentPrice();
        this.currentWinnerId = snapshot.getCurrentWinnerId();
        this.currentWinnerUsername = snapshot.getCurrentWinnerUsername();
        this.startTime = snapshot.getStartTime();
        this.endTime = snapshot.getEndTime();
        this.status = snapshot.getStatus();
        this.endedAt = snapshot.getEndedAt();
        this.version = snapshot.getVersion();
    }

    public ReentrantLock getLock() { return lock; }
    public long getAuctionId() { return auctionId; }
    public long getHostUserId() { return hostUserId; }
    public String getHostUsername() { return hostUsername; }
    public BigDecimal getMinBidIncrement() { return minBidIncrement; }
    public Product getProduct() { return product; }
    public BigDecimal getStartPrice() { return startPrice; }
    public BigDecimal getCurrentPriceUnsafe() { return currentPrice; }
    public Long getCurrentWinnerIdUnsafe() { return currentWinnerId; }
    public String getCurrentWinnerUsernameUnsafe() { return currentWinnerUsername; }
    public Instant getEndTimeUnsafe() { return endTime; }
    public AuctionStatus getStatusUnsafe() { return status; }
    public long getVersionUnsafe() { return version; }

    public void applyAcceptedBid(
            BigDecimal newPrice,
            long winnerId,
            String winnerUsername,
            Instant newEndTime) {
        requireHeld();
        this.currentPrice = newPrice;
        this.currentWinnerId = winnerId;
        this.currentWinnerUsername = winnerUsername;
        this.endTime = newEndTime;
        this.version++;
    }

    public void markEnded(Instant endedAt) {
        requireHeld();
        this.status = AuctionStatus.ENDED;
        this.endedAt = endedAt;
        this.version++;
    }

    public void applyExtended(Instant newEndTime) {
        requireHeld();
        this.endTime = newEndTime;
        this.version++;
    }

    public void markCancelled(Instant cancelledAt) {
        requireHeld();
        this.status = AuctionStatus.CANCELLED;
        this.endedAt = cancelledAt;
        this.version++;
    }

    public AuctionSnapshot snapshot() {
        lock.lock();
        try {
            return new AuctionSnapshot(
                    auctionId,
                    product,
                    hostUserId,
                    hostUsername,
                    startPrice,
                    minBidIncrement,
                    currentPrice,
                    currentWinnerId,
                    currentWinnerUsername,
                    startTime,
                    endTime,
                    status,
                    endedAt,
                    version);
        } finally {
            lock.unlock();
        }
    }

    private void requireHeld() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Auction lock must be held");
        }
    }
}
