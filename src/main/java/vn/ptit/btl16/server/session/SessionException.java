package vn.ptit.btl16.server.session;

import vn.ptit.btl16.common.protocol.ErrorCode;

public final class SessionException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode;

    public SessionException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
