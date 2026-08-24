package vn.ptit.btl16.server.account.security;

public interface PasswordHasher {
    PasswordHash hash(char[] password);

    boolean verify(char[] password, String saltBase64, String expectedHashBase64, int iterations);
}
