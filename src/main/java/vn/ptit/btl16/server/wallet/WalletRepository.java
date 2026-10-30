package vn.ptit.btl16.server.wallet;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.wallet.*;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.account.repository.RepositoryException;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.auction.service.AuctionRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WalletRepository {
    private final JdbcConnectionFactory factory;
    private static final String ACCOUNT = "SELECT user_id, username, display_name, email, phone, created_at, last_login_at, wallet_balance, wallet_reserved FROM users";
    private static final String TOPUPS = "SELECT t.*, u.username FROM wallet_topups t JOIN users u ON u.user_id = t.user_id";
    private static final String WITHDRAWALS = "SELECT w.*, u.username FROM wallet_withdrawals w JOIN users u ON u.user_id = w.user_id";
    public WalletRepository(JdbcConnectionFactory factory) { this.factory = factory; }
    public PlatformCashFlowRepository.Snapshot cashFlow(vn.ptit.btl16.common.util.RevenuePeriod period, long before) {
        return new PlatformCashFlowRepository(factory).snapshot(period, before);
    }

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
                if (!rows.next()) { throw new AuctionException(ErrorCode.PROFILE_NOT_FOUND, "Không tìm thấy tài khoản"); }
                return account(rows);
            }
        } catch (SQLException exception) { throw failure(exception); }
    }

    public Map<String, String> roomSummary(long userId, long auctionId) {
        try (Connection connection = factory.openDatabase(); var query = connection.prepareStatement("""
                SELECT u.wallet_balance, u.wallet_reserved, COALESCE(h.amount, 0) AS room_held
                FROM users u LEFT JOIN wallet_holds h ON h.user_id = u.user_id AND h.auction_id = ?
                WHERE u.user_id = ?
                """)) {
            query.setLong(1, auctionId); query.setLong(2, userId);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) { throw new AuctionException(ErrorCode.PROFILE_NOT_FOUND, "Không tìm thấy tài khoản"); }
                BigDecimal balance = rows.getBigDecimal("wallet_balance");
                BigDecimal reserved = rows.getBigDecimal("wallet_reserved");
                return Map.of("auctionId", Long.toString(auctionId),
                        "available", balance.subtract(reserved).toPlainString(),
                        "roomHeld", rows.getBigDecimal("room_held").max(
                                vn.ptit.btl16.server.auction.repository.AuctionAdmissionStore.held(connection, auctionId, userId)).toPlainString(),
                        "maxBidAmount", AuctionRules.MAX_MONEY.toPlainString());
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

    public Map<String, String> entryPage(long userId, Long room, String type, Instant from, Instant to, long before, int limit) {
        return entryPage(userId, room, type, from, to, before, limit, false);
    }

    public Map<String, String> entryPage(long userId, Long room, String type, Instant from, Instant to, long before, int limit, boolean hideReservations) {
        return entryPage(userId, room, type, from, to, before, limit, hideReservations, false);
    }

    public Map<String, String> entryPage(long userId, Long room, String type, Instant from, Instant to, long before, int limit, boolean hideReservations, boolean hideLegacyReview) {
        if (before <= 0 || limit < 1 || limit > 100 || (room != null && room <= 0)
                || (!type.isEmpty() && !java.util.Set.of("TOPUP", "WITHDRAWAL", "RESERVE", "RELEASE", "PURCHASE", "SALE", "LEGACY_REVIEW", "ROOM_DEPOSIT", "DEPOSIT_RELEASE").contains(type))
                || (from != null && to != null && !from.isBefore(to))) {
            throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Bộ lọc giao dịch không hợp lệ");
        }
        StringBuilder sql = new StringBuilder("SELECT e.* FROM wallet_entries e WHERE e.user_id=? AND e.entry_id<?");
        List<Object> parameters = new ArrayList<>(List.of(userId, before));
        if (hideReservations) { sql.append(" AND e.entry_type NOT IN ('RESERVE','RELEASE')"); }
        if (hideLegacyReview) { sql.append(" AND e.entry_type<>'LEGACY_REVIEW'"); }
        if (!type.isEmpty()) { sql.append(" AND e.entry_type=?"); parameters.add(type); }
        if (from != null) { sql.append(" AND e.created_at>=?"); parameters.add(Timestamp.from(from)); }
        if (to != null) { sql.append(" AND e.created_at<?"); parameters.add(Timestamp.from(to)); }
        if (room != null) {
            sql.append("""
                     AND (e.reference_key IN (?,?,?,?,?,?) OR EXISTS (SELECT 1 FROM bids b WHERE b.auction_id=?
                     AND e.reference_key IN (CONCAT('BID:', b.server_sequence, ':RESERVE'), CONCAT('BID:', b.server_sequence, ':RELEASE'))))
                    """);
            parameters.addAll(List.of("BUY:" + room, "SELL:" + room, "LEGACY:BUY:" + room, "LEGACY:SELL:" + room,
                    "DEPOSIT:" + room + ":" + userId, "DEPOSIT_RELEASE:" + room + ":" + userId, room));
        }
        sql.append(" ORDER BY e.entry_id DESC LIMIT ?"); parameters.add(limit + 1);
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < parameters.size(); index++) { query.setObject(index + 1, parameters.get(index)); }
            List<WalletEntry> values = new ArrayList<>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) { values.add(new WalletEntry(rows.getLong("entry_id"), rows.getString("entry_type"), rows.getBigDecimal("amount"),
                        rows.getBigDecimal("reserved_delta"), rows.getBigDecimal("balance_after"), rows.getBigDecimal("reserved_after"),
                        rows.getString("reference_key"), rows.getTimestamp("created_at").toInstant())); }
            }
            boolean more = values.size() > limit;
            if (more) { values.remove(values.size() - 1); }
            Map<String, String> result = WalletWire.encode(account(userId), List.of(), values);
            result.put("hasMore", Boolean.toString(more));
            result.put("nextBeforeId", values.isEmpty() ? "0" : Long.toString(values.get(values.size() - 1).id()));
            return result;
        } catch (SQLException exception) { throw failure(exception); }
    }

    public TopUpRequest submit(long userId, String requestId, BigDecimal rawAmount) {
        try { if (!UUID.fromString(requestId).toString().equals(requestId)) { throw new IllegalArgumentException(); } }
        catch (RuntimeException exception) { throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Mã yêu cầu nạp tiền không hợp lệ"); }
        BigDecimal amount;
        try { amount = rawAmount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (RuntimeException exception) { throw new AuctionException(ErrorCode.INVALID_AMOUNT, "Tiền nạp chỉ có tối đa 2 chữ số thập phân"); }
        if (amount.compareTo(new BigDecimal("1000")) < 0 || amount.compareTo(new BigDecimal("1000000000")) > 0) {
            throw new AuctionException(ErrorCode.INVALID_AMOUNT, "Mỗi yêu cầu mô phỏng từ 1.000 đến 1.000.000.000 đồng");
        }
        try (Connection connection = factory.openDatabase()) {
            connection.setAutoCommit(false);
            try {
                WalletTransactions.lockUsers(connection, userId);
                TopUpRequest existing = find(connection, requestId, true);
                if (existing != null) {
                    if (existing.userId() != userId || existing.amount().compareTo(amount) != 0) {
                        throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Mã yêu cầu đã được sử dụng với nội dung khác");
                    }
                    connection.commit(); return existing;
                }
                try (var query = connection.prepareStatement("SELECT request_id FROM wallet_topups WHERE user_id = ? AND status = 'PENDING' FOR UPDATE")) {
                    query.setLong(1, userId);
                    try (var rows = query.executeQuery()) { if (rows.next()) { throw new AuctionException(ErrorCode.TOPUP_PENDING, "Bạn đã có yêu cầu đang chờ Server duyệt"); } }
                }
                try (var insert = connection.prepareStatement("INSERT INTO wallet_topups(request_id, user_id, amount, status, created_at) VALUES(?, ?, ?, 'PENDING', ?)")) {
                    insert.setString(1, requestId); insert.setLong(2, userId); insert.setBigDecimal(3, amount); insert.setTimestamp(4, Timestamp.from(Instant.now())); insert.executeUpdate();
                }
                TopUpRequest result = find(connection, requestId, false); connection.commit(); return result;
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw failure(exception); }
    }

    public TopUpRequest review(String requestId, boolean approve) {
        return review(requestId, approve, approve ? "" : "Yêu cầu nạp mô phỏng chưa được xác nhận");
    }

    public TopUpRequest review(String requestId, boolean approve, String reason) {
        String rejection = approve ? "" : (reason == null ? "" : reason.strip());
        if (!approve && (rejection.isEmpty() || rejection.length() > 300 || rejection.chars().anyMatch(Character::isISOControl))) {
            throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Lý do từ chối cần từ 1 đến 300 ký tự, không chứa ký tự điều khiển");
        }
        try (Connection connection = factory.openDatabase()) {
            TopUpRequest owner = find(connection, requestId, false);
            if (owner == null) { throw new AuctionException(ErrorCode.TOPUP_NOT_FOUND, "Không tìm thấy yêu cầu"); }
            connection.setAutoCommit(false);
            try {
                WalletTransactions.lockUsers(connection, owner.userId());
                TopUpRequest request = find(connection, requestId, true);
                if (!request.status().equals("PENDING")) { connection.commit(); return request; }
                Instant now = Instant.now();
                if (approve) { WalletTransactions.change(connection, request.userId(), request.amount(), BigDecimal.ZERO, "TOPUP", "TOPUP:" + requestId, now); }
                try (var update = connection.prepareStatement("UPDATE wallet_topups SET status = ?, reviewed_at = ?, rejection_reason = ? WHERE request_id = ? AND status = 'PENDING'")) {
                    update.setString(1, approve ? "APPROVED" : "REJECTED"); update.setTimestamp(2, Timestamp.from(now)); update.setString(3, rejection); update.setString(4, requestId);
                    if (update.executeUpdate() != 1) { throw new SQLException("Topup state changed"); }
                }
                vn.ptit.btl16.server.notification.PersonalNotificationStore.save(connection, request.userId(),
                        "TOPUP:" + requestId, approve ? "TOPUP_APPROVED" : "TOPUP_REJECTED", null, requestId, now);
                TopUpRequest result = find(connection, requestId, false); connection.commit(); return result;
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw failure(exception); }
    }

    public List<WithdrawalRequest> withdrawals(long userId, boolean pendingOnly) {
        String condition = pendingOnly ? " WHERE w.status='PENDING'" : " WHERE w.user_id=?";
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement(WITHDRAWALS + condition + " ORDER BY w.created_at DESC, w.request_id LIMIT 100")) {
            if (!pendingOnly) { query.setLong(1, userId); }
            List<WithdrawalRequest> result = new ArrayList<>();
            try (var rows = query.executeQuery()) { while (rows.next()) { result.add(withdrawal(rows)); } }
            return result;
        } catch (SQLException exception) { throw failure(exception); }
    }

    public WithdrawalRequest submitWithdrawal(long userId, String requestId, BigDecimal rawAmount) {
        try { if (!UUID.fromString(requestId).toString().equals(requestId)) { throw new IllegalArgumentException(); } }
        catch (RuntimeException exception) { throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Mã yêu cầu rút tiền không hợp lệ"); }
        BigDecimal amount;
        try { amount = rawAmount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (RuntimeException exception) { throw new AuctionException(ErrorCode.INVALID_AMOUNT, "Tiền rút chỉ có tối đa 2 chữ số thập phân"); }
        if (amount.compareTo(new BigDecimal("1000")) < 0 || amount.compareTo(new BigDecimal("1000000000")) > 0) {
            throw new AuctionException(ErrorCode.INVALID_AMOUNT, "Mỗi yêu cầu rút mô phỏng từ 1.000 đến 1.000.000.000 đồng");
        }
        try (var connection = factory.openDatabase()) {
            connection.setAutoCommit(false);
            try {
                WalletTransactions.lockUsers(connection, userId);
                WithdrawalRequest existing = findWithdrawal(connection, requestId, true);
                if (existing != null) {
                    if (existing.userId() != userId || existing.amount().compareTo(amount) != 0) {
                        throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Mã yêu cầu đã được sử dụng với nội dung khác");
                    }
                    connection.commit(); return existing;
                }
                try (var query = connection.prepareStatement("SELECT request_id FROM wallet_withdrawals WHERE user_id=? AND status='PENDING' FOR UPDATE")) {
                    query.setLong(1, userId);
                    try (var rows = query.executeQuery()) {
                        if (rows.next()) { throw new AuctionException(ErrorCode.WITHDRAWAL_PENDING, "Bạn đã có yêu cầu rút tiền đang chờ Server duyệt"); }
                    }
                }
                requireWithdrawable(connection, userId, amount);
                try (var insert = connection.prepareStatement("INSERT INTO wallet_withdrawals(request_id,user_id,amount,status,created_at) VALUES(?,?,?,'PENDING',?)")) {
                    insert.setString(1, requestId); insert.setLong(2, userId); insert.setBigDecimal(3, amount); insert.setTimestamp(4, Timestamp.from(Instant.now())); insert.executeUpdate();
                }
                WithdrawalRequest result = findWithdrawal(connection, requestId, false); connection.commit(); return result;
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw failure(exception); }
    }

    public WithdrawalRequest reviewWithdrawal(String requestId, boolean approve, String reason) {
        String rejection = approve ? "" : (reason == null ? "" : reason.strip());
        if (!approve && (rejection.isEmpty() || rejection.length() > 300 || rejection.chars().anyMatch(Character::isISOControl))) {
            throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Lý do từ chối cần từ 1 đến 300 ký tự, không chứa ký tự điều khiển");
        }
        try (var connection = factory.openDatabase()) {
            WithdrawalRequest owner = findWithdrawal(connection, requestId, false);
            if (owner == null) { throw new AuctionException(ErrorCode.WITHDRAWAL_NOT_FOUND, "Không tìm thấy yêu cầu rút tiền"); }
            connection.setAutoCommit(false);
            try {
                WalletTransactions.lockUsers(connection, owner.userId());
                WithdrawalRequest request = findWithdrawal(connection, requestId, true);
                if (!request.status().equals("PENDING")) { connection.commit(); return request; }
                Instant now = Instant.now();
                if (approve) {
                    requireWithdrawable(connection, request.userId(), request.amount());
                    WalletTransactions.change(connection, request.userId(), request.amount().negate(), BigDecimal.ZERO, "WITHDRAWAL", "WITHDRAWAL:" + requestId, now);
                }
                try (var update = connection.prepareStatement("UPDATE wallet_withdrawals SET status=?, reviewed_at=?, rejection_reason=? WHERE request_id=? AND status='PENDING'")) {
                    update.setString(1, approve ? "APPROVED" : "REJECTED"); update.setTimestamp(2, Timestamp.from(now)); update.setString(3, rejection); update.setString(4, requestId);
                    if (update.executeUpdate() != 1) { throw new SQLException("Withdrawal state changed"); }
                }
                vn.ptit.btl16.server.notification.PersonalNotificationStore.save(connection, request.userId(),
                        "WITHDRAWAL:" + requestId, approve ? "WITHDRAWAL_APPROVED" : "WITHDRAWAL_REJECTED", null, requestId, now);
                WithdrawalRequest result = findWithdrawal(connection, requestId, false); connection.commit(); return result;
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw failure(exception); }
    }

    private void requireWithdrawable(Connection connection, long userId, BigDecimal amount) throws SQLException {
        try (var query = connection.prepareStatement("SELECT wallet_balance-wallet_reserved FROM users WHERE user_id=?")) {
            query.setLong(1, userId);
            try (var rows = query.executeQuery()) {
                if (!rows.next() || rows.getBigDecimal(1).compareTo(amount) < 0) {
                    throw new AuctionException(ErrorCode.INSUFFICIENT_FUNDS, "Số dư khả dụng không đủ để rút. Không thể rút khoản tiền đang giữ chỗ đấu giá.");
                }
            }
        }
    }

    private WithdrawalRequest findWithdrawal(Connection connection, String requestId, boolean lock) throws SQLException {
        try (var query = connection.prepareStatement(WITHDRAWALS + " WHERE w.request_id=?" + (lock ? " FOR UPDATE" : ""))) {
            query.setString(1, requestId);
            try (var rows = query.executeQuery()) { return rows.next() ? withdrawal(rows) : null; }
        }
    }

    private WithdrawalRequest withdrawal(ResultSet rows) throws SQLException {
        Timestamp reviewed = rows.getTimestamp("reviewed_at");
        return new WithdrawalRequest(rows.getString("request_id"), rows.getLong("user_id"), rows.getString("username"), rows.getBigDecimal("amount"),
                rows.getString("status"), rows.getTimestamp("created_at").toInstant(), reviewed == null ? null : reviewed.toInstant(), rows.getString("rejection_reason"));
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
                rows.getString("status"), rows.getTimestamp("created_at").toInstant(), reviewed == null ? null : reviewed.toInstant(), rows.getString("rejection_reason"));
    }
    private RepositoryException failure(SQLException exception) { return new RepositoryException("Wallet database operation failed", exception); }
}
