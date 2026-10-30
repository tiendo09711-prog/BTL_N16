package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.wallet.WalletTransactions;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AuctionAdmissionStore {
    private AuctionAdmissionStore() { }

    public static void initialize(JdbcConnectionFactory factory) {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
            column(connection, "max_participants", "INT NOT NULL DEFAULT 100");
            column(connection, "deposit_amount", "DECIMAL(18,2) NOT NULL DEFAULT 0");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS auction_deposits (
                        auction_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
                        amount DECIMAL(18,2) NOT NULL, status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP(3) NOT NULL, released_at TIMESTAMP(3) NULL,
                        PRIMARY KEY(auction_id, user_id),
                        FOREIGN KEY(auction_id) REFERENCES auctions(auction_id),
                        FOREIGN KEY(user_id) REFERENCES users(user_id)
                    )
                    """);
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot initialize room admission", error); }
    }

    private static void column(Connection connection, String name, String type) throws SQLException {
        try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "%", "%")) {
            while (columns.next()) {
                if ("auctions".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && name.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) { return; }
            }
        }
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE auctions ADD COLUMN " + name + " " + type);
        }
    }

    public static BigDecimal held(Connection connection, long auctionId, long userId) throws SQLException {
        try (var query = connection.prepareStatement("SELECT amount FROM auction_deposits WHERE auction_id=? AND user_id=? AND status='HELD'")) {
            query.setLong(1, auctionId); query.setLong(2, userId);
            try (var rows = query.executeQuery()) { return rows.next() ? rows.getBigDecimal(1) : BigDecimal.ZERO; }
        }
    }

    public static BigDecimal place(Connection connection, long auctionId, long userId) throws SQLException {
        BigDecimal required;
        Instant startTime;
        try (var query = connection.prepareStatement("SELECT status, start_time, deposit_amount, host_user_id FROM auctions WHERE auction_id=? FOR UPDATE")) {
            query.setLong(1, auctionId);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) { throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND, "Auction not found"); }
                if (rows.getLong("host_user_id") == userId) { throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Host does not pay a deposit"); }
                required = rows.getBigDecimal("deposit_amount");
                startTime = rows.getTimestamp("start_time").toInstant();
                BigDecimal paid = held(connection, auctionId, userId);
                if ("OPEN".equals(rows.getString("status")) && required.signum() > 0 && paid.compareTo(required) >= 0) { return paid; }
                if (!"OPEN".equals(rows.getString("status")) || required.signum() <= 0
                        || !Instant.now().isBefore(startTime)) {
                    throw new AuctionException(ErrorCode.ROOM_DEPOSIT_CLOSED, "Deposit registration closes at the scheduled start time");
                }
            }
        }
        try (var query = connection.prepareStatement("SELECT 1 FROM auction_blocked_users WHERE auction_id=? AND user_id=?")) {
            query.setLong(1, auctionId); query.setLong(2, userId);
            try (var rows = query.executeQuery()) {
                if (rows.next()) { throw new AuctionException(ErrorCode.USER_BLOCKED_FROM_AUCTION, "Blocked from this room"); }
            }
        }
        WalletTransactions.lockUsers(connection, userId);
        Instant now = Instant.now();
        if (!now.isBefore(startTime)) {
            throw new AuctionException(ErrorCode.ROOM_DEPOSIT_CLOSED, "Deposit registration closes at the scheduled start time");
        }
        WalletTransactions.change(connection, userId, BigDecimal.ZERO, required, "ROOM_DEPOSIT", "DEPOSIT:" + auctionId + ":" + userId, now);
        try (var insert = connection.prepareStatement("INSERT INTO auction_deposits(auction_id,user_id,amount,status,created_at) VALUES(?,?,?,'HELD',?)")) {
            insert.setLong(1, auctionId); insert.setLong(2, userId); insert.setBigDecimal(3, required);
            insert.setTimestamp(4, Timestamp.from(now)); insert.executeUpdate();
        }
        return required;
    }

    public static long[] depositors(Connection connection, long auctionId) throws SQLException {
        List<Long> users = new ArrayList<>();
        try (var query = connection.prepareStatement("SELECT user_id FROM auction_deposits WHERE auction_id=? AND status='HELD' ORDER BY user_id")) {
            query.setLong(1, auctionId);
            try (var rows = query.executeQuery()) { while (rows.next()) { users.add(rows.getLong(1)); } }
        }
        return users.stream().mapToLong(Long::longValue).toArray();
    }

    public static void release(Connection connection, long auctionId, Long winner, BigDecimal price, Instant now) throws SQLException {
        long[] users = depositors(connection, auctionId);
        WalletTransactions.lockUsers(connection, users);
        for (long userId : users) { releaseUser(connection, auctionId, userId, winner != null && winner == userId ? price : BigDecimal.ZERO, now); }
    }

    public static void releaseUser(Connection connection, long auctionId, long userId, BigDecimal applied, Instant now) throws SQLException {
        BigDecimal deposit = held(connection, auctionId, userId);
        if (deposit.signum() <= 0) { return; }
        WalletTransactions.lockUsers(connection, userId);
        BigDecimal refund = deposit.subtract(applied).max(BigDecimal.ZERO);
        if (refund.signum() > 0) {
            WalletTransactions.change(connection, userId, BigDecimal.ZERO, refund.negate(), "DEPOSIT_RELEASE", "DEPOSIT_RELEASE:" + auctionId + ":" + userId, now);
        }
        try (var update = connection.prepareStatement("UPDATE auction_deposits SET status=?, released_at=? WHERE auction_id=? AND user_id=? AND status='HELD'")) {
            update.setString(1, applied.signum() > 0 ? "APPLIED" : "REFUNDED"); update.setTimestamp(2, Timestamp.from(now));
            update.setLong(3, auctionId); update.setLong(4, userId); update.executeUpdate();
        }
    }
}
