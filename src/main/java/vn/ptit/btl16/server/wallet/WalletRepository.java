package vn.ptit.btl16.server.wallet;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.wallet.*;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.account.repository.RepositoryException;
import vn.ptit.btl16.server.auction.service.AuctionException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WalletRepository {
    private final JdbcConnectionFactory factory;
    private static final String ACCOUNT = "SELECT user_id, username, display_name, email, phone, created_at, last_login_at, wallet_balance, wallet_reserved FROM users";
    private static final String TOPUPS = "SELECT t.*, u.username FROM wallet_topups t JOIN users u ON u.user_id = t.user_id";
    public WalletRepository(JdbcConnectionFactory factory) { this.factory = factory; }

    public List<WalletAccount> accounts() {
        try (Connection connection = factory.openDatabase(); var query = connection.prepareStatement(ACCOUNT + " ORDER BY user_id")) {
            List<WalletAccount> result = new ArrayList<>();
            try (var rows = query.executeQuery()) { while (rows.next()) { result.add(account(rows)); } }
            return result;
        } catch (SQLException exception) { throw failure(exception); }
    }

    public WalletAccount account(long userId) {
        try (Connection connection = factory.openDatabase(); var query = connection.prepareStatement(ACCOUNT + " WHERE user_id = ?")) {
            query.setLong(1, userId);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) { throw new AuctionException(ErrorCode.PROFILE_NOT_FOUND, "Khong tim thay tai khoan"); }
                return account(rows);
            }
        } catch (SQLException exception) { throw failure(exception); }
    }

    public List<TopUpRequest> requests(long userId, boolean pendingOnly) {
        String condition = pendingOnly ? " WHERE t.status = 'PENDING'" : " WHERE t.user_id = ?";
        try (Connection connection = factory.openDatabase(); var query = connection.prepareStatement(TOPUPS + condition + " ORDER BY t.created_at DESC, t.request_id LIMIT 100")) {
            if (!pendingOnly) { query.setLong(1, userId); }
            List<TopUpRequest> result = new ArrayList<>();
            try (var rows = query.executeQuery()) { while (rows.next()) { result.add(topup(rows)); } }
            return result;
        } catch (SQLException exception) { throw failure(exception); }
    }

    public List<WalletEntry> entries(long userId) {
        try (Connection connection = factory.openDatabase(); var query = connection.prepareStatement("SELECT * FROM wallet_entries WHERE user_id = ? ORDER BY entry_id DESC LIMIT 100")) {
            query.setLong(1, userId); List<WalletEntry> result = new ArrayList<>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) { result.add(new WalletEntry(rows.getLong("entry_id"), rows.getString("entry_type"), rows.getBigDecimal("amount"),
                        rows.getBigDecimal("reserved_delta"), rows.getBigDecimal("balance_after"), rows.getBigDecimal("reserved_after"),
                        rows.getString("reference_key"), rows.getTimestamp("created_at").toInstant())); }
            }
            return result;
        } catch (SQLException exception) { throw failure(exception); }
    }

    public TopUpRequest submit(long userId, String requestId, BigDecimal rawAmount) {
        try { if (!UUID.fromString(requestId).toString().equals(requestId)) { throw new IllegalArgumentException(); } }
        catch (RuntimeException exception) { throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Ma yeu cau nap tien khong hop le"); }
        BigDecimal amount;
        try { amount = rawAmount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (RuntimeException exception) { throw new AuctionException(ErrorCode.INVALID_AMOUNT, "Tien nap chi co toi da 2 chu so thap phan"); }
        if (amount.compareTo(new BigDecimal("1000")) < 0 || amount.compareTo(new BigDecimal("1000000000")) > 0) {
            throw new AuctionException(ErrorCode.INVALID_AMOUNT, "Moi yeu cau demo tu 1.000 den 1.000.000.000 dong");
        }
        try (Connection connection = factory.openDatabase()) {
            connection.setAutoCommit(false);
            try {
                WalletTransactions.lockUsers(connection, userId);
                TopUpRequest existing = find(connection, requestId, true);
                if (existing != null) {
                    if (existing.userId() != userId || existing.amount().compareTo(amount) != 0) {
                        throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Ma yeu cau da duoc su dung voi noi dung khac");
                    }
                    connection.commit(); return existing;
                }
                try (var query = connection.prepareStatement("SELECT request_id FROM wallet_topups WHERE user_id = ? AND status = 'PENDING' FOR UPDATE")) {
                    query.setLong(1, userId);
                    try (var rows = query.executeQuery()) { if (rows.next()) { throw new AuctionException(ErrorCode.TOPUP_PENDING, "Ban da co yeu cau dang cho server duyet"); } }
                }
                try (var insert = connection.prepareStatement("INSERT INTO wallet_topups(request_id, user_id, amount, status, created_at) VALUES(?, ?, ?, 'PENDING', ?)")) {
                    insert.setString(1, requestId); insert.setLong(2, userId); insert.setBigDecimal(3, amount); insert.setTimestamp(4, Timestamp.from(Instant.now())); insert.executeUpdate();
                }
                TopUpRequest result = find(connection, requestId, false); connection.commit(); return result;
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw failure(exception); }
    }

    public TopUpRequest review(String requestId, boolean approve) {
        try (Connection connection = factory.openDatabase()) {
            TopUpRequest owner = find(connection, requestId, false);
            if (owner == null) { throw new AuctionException(ErrorCode.TOPUP_NOT_FOUND, "Khong tim thay yeu cau"); }
            connection.setAutoCommit(false);
            try {
                WalletTransactions.lockUsers(connection, owner.userId());
                TopUpRequest request = find(connection, requestId, true);
                if (!request.status().equals("PENDING")) { connection.commit(); return request; }
                Instant now = Instant.now();
                if (approve) { WalletTransactions.change(connection, request.userId(), request.amount(), BigDecimal.ZERO, "TOPUP", "TOPUP:" + requestId, now); }
                try (var update = connection.prepareStatement("UPDATE wallet_topups SET status = ?, reviewed_at = ? WHERE request_id = ? AND status = 'PENDING'")) {
                    update.setString(1, approve ? "APPROVED" : "REJECTED"); update.setTimestamp(2, Timestamp.from(now)); update.setString(3, requestId);
                    if (update.executeUpdate() != 1) { throw new SQLException("Topup state changed"); }
                }
                TopUpRequest result = find(connection, requestId, false); connection.commit(); return result;
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw failure(exception); }
    }

    private TopUpRequest find(Connection connection, String requestId, boolean lock) throws SQLException {
        try (var query = connection.prepareStatement(TOPUPS + " WHERE t.request_id = ?" + (lock ? " FOR UPDATE" : ""))) {
            query.setString(1, requestId);
            try (var rows = query.executeQuery()) { return rows.next() ? topup(rows) : null; }
        }
    }
    private WalletAccount account(ResultSet rows) throws SQLException {
        Timestamp last = rows.getTimestamp("last_login_at");
        return new WalletAccount(rows.getLong("user_id"), rows.getString("username"), rows.getString("display_name"),
                rows.getString("email"), rows.getString("phone"), rows.getTimestamp("created_at").toInstant(), last == null ? null : last.toInstant(),
                rows.getBigDecimal("wallet_balance"), rows.getBigDecimal("wallet_reserved"));
    }
    private TopUpRequest topup(ResultSet rows) throws SQLException {
        Timestamp reviewed = rows.getTimestamp("reviewed_at");
        return new TopUpRequest(rows.getString("request_id"), rows.getLong("user_id"), rows.getString("username"), rows.getBigDecimal("amount"),
                rows.getString("status"), rows.getTimestamp("created_at").toInstant(), reviewed == null ? null : reviewed.toInstant());
    }
    private RepositoryException failure(SQLException exception) { return new RepositoryException("Wallet database operation failed", exception); }
}
