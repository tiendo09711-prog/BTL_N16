package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.protocol.ErrorCode;

public final class AuctionException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode;

    public AuctionException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
