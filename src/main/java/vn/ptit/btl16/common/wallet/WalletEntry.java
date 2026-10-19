package vn.ptit.btl16.common.wallet;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletEntry(long id, String type, BigDecimal amount, BigDecimal reservedDelta,
                          BigDecimal balanceAfter, BigDecimal reservedAfter, String reference,
                          Instant createdAt) { }
