package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.BidRecord;

public final class BidOutcome {
    private final AuctionSnapshot auction;
    private final BidRecord bid;
    private final Long previousWinnerId;
    private final String previousWinnerUsername;
    private final boolean extended;

    public BidOutcome(
            AuctionSnapshot auction,
            BidRecord bid,
            Long previousWinnerId,
            String previousWinnerUsername,
            boolean extended) {
        this.auction = auction;
        this.bid = bid;
        this.previousWinnerId = previousWinnerId;
        this.previousWinnerUsername = previousWinnerUsername == null ? "" : previousWinnerUsername;
        this.extended = extended;
    }

    public AuctionSnapshot getAuction() { return auction; }
    public BidRecord getBid() { return bid; }
    public Long getPreviousWinnerId() { return previousWinnerId; }
    public String getPreviousWinnerUsername() { return previousWinnerUsername; }
    public boolean isExtended() { return extended; }
}
