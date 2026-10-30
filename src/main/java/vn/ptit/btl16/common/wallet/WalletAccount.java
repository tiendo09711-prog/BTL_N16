package vn.ptit.btl16.common.wallet;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletAccount(long userId, String username, String displayName, String email, String phone,
                            Instant createdAt, Instant lastLoginAt, BigDecimal balance, BigDecimal reserved) {
    public BigDecimal available() { return balance.subtract(reserved); }
}
