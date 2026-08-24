package vn.ptit.btl16.server.auction.repository;

public final class AuctionRepositoryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AuctionRepositoryException(String message) {
        super(message);
    }

    public AuctionRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
