package vn.ptit.btl16.server.account.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class Pbkdf2PasswordHasher implements PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final SecureRandom random = new SecureRandom();
    private final int defaultIterations;

    public Pbkdf2PasswordHasher(int defaultIterations) {
        this.defaultIterations = defaultIterations;
    }

    @Override
    public PasswordHash hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] derived = derive(password, salt, defaultIterations);
        try {
            return new PasswordHash(
                    Base64.getEncoder().encodeToString(salt),
                    Base64.getEncoder().encodeToString(derived),
                    defaultIterations);
        } finally {
            Arrays.fill(derived, (byte) 0);
            Arrays.fill(salt, (byte) 0);
        }
    }

    @Override
    public boolean verify(
            char[] password,
            String saltBase64,
            String expectedHashBase64,
            int iterations) {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        byte[] expected = Base64.getDecoder().decode(expectedHashBase64);
        byte[] actual = derive(password, salt, iterations);
        try {
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(expected, (byte) 0);
            Arrays.fill(actual, (byte) 0);
        }
    }

    private byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("JDK does not support " + ALGORITHM, exception);
        } finally {
            spec.clearPassword();
        }
    }
}
