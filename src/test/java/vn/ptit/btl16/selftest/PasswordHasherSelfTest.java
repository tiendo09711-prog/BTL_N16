package vn.ptit.btl16.selftest;

import vn.ptit.btl16.server.account.security.PasswordHash;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;

import java.util.Arrays;

public final class PasswordHasherSelfTest {
    private PasswordHasherSelfTest() {
    }

    public static void run() {
        Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(20_000);
        char[] correct = "abc12345".toCharArray();
        char[] wrong = "abc12346".toCharArray();
        try {
            PasswordHash hash = hasher.hash(correct);
            TestSupport.check(hasher.verify(
                    correct,
                    hash.getSaltBase64(),
                    hash.getHashBase64(),
                    hash.getIterations()), "correct password must verify");
            TestSupport.check(!hasher.verify(
                    wrong,
                    hash.getSaltBase64(),
                    hash.getHashBase64(),
                    hash.getIterations()), "wrong password must fail");
        } finally {
            Arrays.fill(correct, '\0');
            Arrays.fill(wrong, '\0');
        }
        System.out.println("[PASS] PasswordHasherSelfTest");
    }
}
