package vn.ptit.btl16.server.account.repository;

public final class RepositoryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
