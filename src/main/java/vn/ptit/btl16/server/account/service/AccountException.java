package vn.ptit.btl16.server.account.service;

import vn.ptit.btl16.common.protocol.ErrorCode;

public final class AccountException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode;

    public AccountException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
