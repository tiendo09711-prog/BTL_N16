package vn.ptit.btl16.server.wallet;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.common.util.AuctionFees;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;

public final class WalletTransactions {
    public static final BigDecimal MAX_BALANCE = new BigDecimal("9999999999999999.99");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private WalletTransactions() { }

    public static void lockUsers(Connection connection, long... users) throws SQLException {
        for (long userId : Arrays.stream(users).distinct().sorted().toArray()) {
            try (var query = connection.prepareStatement("SELECT user_id FROM users WHERE user_id = ? FOR UPDATE")) {
                query.setLong(1, userId);
                try (var rows = query.executeQuery()) { if (!rows.next()) { throw new SQLException("Wallet owner not found"); } }
            }
        }
    }

    public static void change(Connection connection, long userId, BigDecimal amount, BigDecimal reservedDelta,
                              String type, String reference, Instant time) throws SQLException {
        try (var update = connection.prepareStatement("""
                UPDATE users SET wallet_balance = wallet_balance + ?, wallet_reserved = wallet_reserved + ?
                WHERE user_id = ? AND wallet_balance + ? >= 0 AND wallet_reserved + ? >= 0
                  AND wallet_balance + ? >= wallet_reserved + ? AND wallet_balance + ? <= ?
                """)) {
            update.setBigDecimal(1, amount); update.setBigDecimal(2, reservedDelta); update.setLong(3, userId);
            update.setBigDecimal(4, amount); update.setBigDecimal(5, reservedDelta);
            update.setBigDecimal(6, amount); update.setBigDecimal(7, reservedDelta);
            update.setBigDecimal(8, amount); update.setBigDecimal(9, MAX_BALANCE);
            if (update.executeUpdate() != 1) { throw new AuctionException(ErrorCode.INSUFFICIENT_FUNDS,
                    "Số dư khả dụng không đủ hoặc ví vượt giới hạn. Nạp tiền và chờ Server duyệt trước khi đặt giá."); }
        }
        try (var entry = connection.prepareStatement("""
                INSERT INTO wallet_entries(user_id, entry_type, amount, reserved_delta, balance_after,
                    reserved_after, reference_key, created_at, vault_after)
                SELECT user_id, ?, ?, ?, wallet_balance, wallet_reserved, ?, ?, vault_balance FROM users WHERE user_id = ?
                """)) {
            entry.setString(1, type); entry.setBigDecimal(2, amount); entry.setBigDecimal(3, reservedDelta);
            entry.setString(4, reference); entry.setTimestamp(5, Timestamp.from(time)); entry.setLong(6, userId); entry.executeUpdate();
        }
    }

    public static void reserve(Connection connection, long auctionId, long bidder, BigDecimal amount,
                               long sequence, Instant time) throws SQLException {
        long previous = bidder; BigDecimal previousAmount = ZERO;
        try (var query = connection.prepareStatement("SELECT user_id, amount FROM wallet_holds WHERE auction_id = ? FOR UPDATE")) {
            query.setLong(1, auctionId);
            try (var rows = query.executeQuery()) { if (rows.next()) { previous = rows.getLong(1); previousAmount = rows.getBigDecimal(2); } }
        }
        lockUsers(connection, previous, bidder);
        if (previousAmount.signum() > 0) {
            change(connection, previous, ZERO, previousAmount.negate(), "RELEASE", "BID:" + sequence + ":RELEASE", time);
        }
        change(connection, bidder, ZERO, amount, "RESERVE", "BID:" + sequence + ":RESERVE", time);
        try (var delete = connection.prepareStatement("DELETE FROM wallet_holds WHERE auction_id = ?")) {
            delete.setLong(1, auctionId); delete.executeUpdate();
        }
        try (var insert = connection.prepareStatement("INSERT INTO wallet_holds(auction_id, user_id, amount) VALUES(?, ?, ?)")) {
            insert.setLong(1, auctionId); insert.setLong(2, bidder); insert.setBigDecimal(3, amount); insert.executeUpdate();
        }
    }

    public static void settle(Connection connection, long auctionId, long buyer, long seller, BigDecimal amount,
                              Instant time, boolean required) throws SQLException {
        boolean held = false;
        try (var query = connection.prepareStatement("SELECT user_id, amount FROM wallet_holds WHERE auction_id = ? FOR UPDATE")) {
            query.setLong(1, auctionId);
            try (var rows = query.executeQuery()) {
                if (rows.next()) {
                    held = rows.getLong(1) == buyer && rows.getBigDecimal(2).compareTo(amount) == 0;
                    if (!held) { throw new SQLException("Auction hold does not match winning bid"); }
                }
            }
        }
        if (buyer == seller || (required && !held)) { throw new SQLException("Missing or invalid funded winning bid"); }
        // Serialize the rank snapshot through receipt capture and commit, across all sellers.
        try (var query = connection.prepareStatement("SELECT lock_id FROM revenue_settlement_lock WHERE lock_id=1 FOR UPDATE");
             var rows = query.executeQuery()) {
            if (!rows.next()) { throw new SQLException("Missing revenue settlement lock"); }
        }
        int rank = held ? vn.ptit.btl16.server.auction.repository.SellerRevenueStore.rankBeforeSale(connection, seller) : 0;
        BigDecimal baseRate = held ? AuctionFees.rate(amount) : ZERO;
        BigDecimal discount = held ? AuctionFees.discount(rank) : ZERO;
        BigDecimal fee = held ? AuctionFees.fee(amount, rank) : ZERO;
        BigDecimal feeRate = baseRate.subtract(discount);
        lockUsers(connection, buyer, seller);
        if (held) {
            change(connection, buyer, amount.negate(), amount.negate(), "PURCHASE", "BUY:" + auctionId, time);
            change(connection, seller, amount.subtract(fee), ZERO, "SALE", "SELL:" + auctionId, time);
            try (var delete = connection.prepareStatement("DELETE FROM wallet_holds WHERE auction_id = ?")) {
                delete.setLong(1, auctionId); delete.executeUpdate();
            }
        } else {
            change(connection, buyer, ZERO, ZERO, "LEGACY_REVIEW", "LEGACY:BUY:" + auctionId, time);
            change(connection, seller, ZERO, ZERO, "LEGACY_REVIEW", "LEGACY:SELL:" + auctionId, time);
        }
        try (var insert = connection.prepareStatement("INSERT INTO wallet_settlements(auction_id, buyer_id, seller_id, amount, status, created_at, fee_rate, platform_fee, base_fee_rate, fee_discount_rate, revenue_rank) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            insert.setLong(1, auctionId); insert.setLong(2, buyer); insert.setLong(3, seller); insert.setBigDecimal(4, amount);
            insert.setString(5, held ? "PAID" : "REVIEW_REQUIRED"); insert.setTimestamp(6, Timestamp.from(time));
            insert.setBigDecimal(7, feeRate); insert.setBigDecimal(8, fee);
            insert.setBigDecimal(9, baseRate); insert.setBigDecimal(10, discount); insert.setInt(11, rank); insert.executeUpdate();
        }
    }
}
