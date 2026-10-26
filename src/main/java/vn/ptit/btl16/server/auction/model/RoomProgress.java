package vn.ptit.btl16.server.auction.model;

import java.math.BigDecimal;
import java.time.Instant;

public final class RoomProgress {
    private RoomProgress() { }
    public record Event(long id, long auctionId, String type, Long userId, String username,
                        BigDecimal amount, String detail, Instant time) { }
    public record HistoryBid(BidRecord bid, BigDecimal increase) { }
    public record Stats(long bidCount, long bidderCount, long extensionCount,
                        boolean extensionsComplete, BigDecimal increase, long lastBidSequence) { }
}
