package vn.ptit.btl16.common.wallet;

import java.math.BigDecimal;
import java.time.Instant;

public record WithdrawalRequest(String id, long userId, String username, BigDecimal amount, String status,
                                Instant createdAt, Instant reviewedAt, String rejectionReason) { }
