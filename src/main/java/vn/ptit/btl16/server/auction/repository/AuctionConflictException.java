package vn.ptit.btl16.server.auction.repository;

public final class AuctionConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AuctionConflictException(String message) {
        super(message);
    }
}
