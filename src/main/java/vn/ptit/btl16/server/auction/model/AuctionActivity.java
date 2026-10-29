package vn.ptit.btl16.server.auction.model;

public record AuctionActivity(long bidCount, long movementCount) {
    public long score(long people) { return people + bidCount + movementCount; }
}
