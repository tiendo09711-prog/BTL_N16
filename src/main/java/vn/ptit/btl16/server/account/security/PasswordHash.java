package vn.ptit.btl16.server.account.security;

public final class PasswordHash {
    private final String saltBase64;
    private final String hashBase64;
    private final int iterations;

    public PasswordHash(String saltBase64, String hashBase64, int iterations) {
        this.saltBase64 = saltBase64;
        this.hashBase64 = hashBase64;
        this.iterations = iterations;
    }

    public String getSaltBase64() { return saltBase64; }
    public String getHashBase64() { return hashBase64; }
    public int getIterations() { return iterations; }
}
