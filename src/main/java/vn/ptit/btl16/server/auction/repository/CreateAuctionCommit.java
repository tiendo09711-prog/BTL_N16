package vn.ptit.btl16.server.auction.repository;

import java.math.BigDecimal;
import java.time.Instant;
import vn.ptit.btl16.server.auction.model.RoomVisibility;

public final class CreateAuctionCommit {
    private final long hostUserId;
    private final String hostUsername;
    private final long productId;
    private final BigDecimal startPrice;
    private final BigDecimal minBidIncrement;
    private final Instant startTime;
    private final Instant endTime;
    private final RoomVisibility visibility;
    private final String roomPasswordHash;
    private final String roomPasswordSalt;
    private final Integer roomPasswordIterations;

    public CreateAuctionCommit(
            long hostUserId,
            String hostUsername,
            long productId,
            BigDecimal startPrice,
            BigDecimal minBidIncrement,
            Instant startTime,
            Instant endTime) {
        this(hostUserId, hostUsername, productId, startPrice, minBidIncrement,
                startTime, endTime, RoomVisibility.PUBLIC, "", "", null);
    }

    public CreateAuctionCommit(
            long hostUserId,
            String hostUsername,
            long productId,
            BigDecimal startPrice,
            BigDecimal minBidIncrement,
            Instant startTime,
            Instant endTime,
            RoomVisibility visibility,
            String roomPasswordHash,
            String roomPasswordSalt,
            Integer roomPasswordIterations) {
        this.hostUserId = hostUserId;
        this.hostUsername = hostUsername;
        this.productId = productId;
        this.startPrice = startPrice;
        this.minBidIncrement = minBidIncrement;
        this.startTime = startTime;
        this.endTime = endTime;
        this.visibility = visibility == null ? RoomVisibility.PUBLIC : visibility;
        this.roomPasswordHash = roomPasswordHash == null ? "" : roomPasswordHash;
        this.roomPasswordSalt = roomPasswordSalt == null ? "" : roomPasswordSalt;
        this.roomPasswordIterations = roomPasswordIterations;
    }

    public long getHostUserId() { return hostUserId; }
    public String getHostUsername() { return hostUsername; }
    public long getProductId() { return productId; }
    public BigDecimal getStartPrice() { return startPrice; }
    public BigDecimal getMinBidIncrement() { return minBidIncrement; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public RoomVisibility getVisibility() { return visibility; }
    public String getRoomPasswordHash() { return roomPasswordHash; }
    public String getRoomPasswordSalt() { return roomPasswordSalt; }
    public Integer getRoomPasswordIterations() { return roomPasswordIterations; }
}
