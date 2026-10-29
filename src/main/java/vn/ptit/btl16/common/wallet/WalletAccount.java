package vn.ptit.btl16.common.wallet;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletAccount(long userId, String username, String displayName, String email, String phone,
                            Instant createdAt, Instant lastLoginAt, BigDecimal balance, BigDecimal reserved, BigDecimal vaultBalance) {
    public WalletAccount(long userId, String username, String displayName, String email, String phone,
                         Instant createdAt, Instant lastLoginAt, BigDecimal balance, BigDecimal reserved) {
        this(userId, username, displayName, email, phone, createdAt, lastLoginAt, balance, reserved, BigDecimal.ZERO);
    }
    public BigDecimal available() { return balance.subtract(reserved); }
}
