package vn.ptit.btl16.server.wallet;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.account.repository.RepositoryException;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.service.AuctionException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Vault money is separate from the wallet balance used by bids and approved withdrawals. */
public final class VaultRepository {
    private static final Pbkdf2PasswordHasher PINS = new Pbkdf2PasswordHasher(120_000);
    private final JdbcConnectionFactory factory;
    public VaultRepository(JdbcConnectionFactory factory) { this.factory = factory; }

    public Map<String, String> summary(long user) {
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("""
                SELECT u.wallet_balance, u.wallet_reserved, u.vault_balance, v.user_id AS configured
                FROM users u LEFT JOIN wallet_vaults v ON v.user_id=u.user_id WHERE u.user_id=?
                """)) {
            query.setLong(1, user);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) { throw new AuctionException(ErrorCode.PROFILE_NOT_FOUND, "Không tìm thấy tài khoản"); }
                var result = new LinkedHashMap<String, String>();
                result.put("userId", Long.toString(user)); result.put("configured", Boolean.toString(rows.getObject("configured") != null));
                result.put("balance", rows.getBigDecimal("wallet_balance").toPlainString());
                result.put("available", rows.getBigDecimal("wallet_balance").subtract(rows.getBigDecimal("wallet_reserved")).toPlainString());
                result.put("vaultBalance", rows.getBigDecimal("vault_balance").toPlainString()); return result;
            }
        } catch (SQLException error) { throw failure(error); }
    }

    /** Creating the PIN and making the first deposit either both commit or neither does. */
    public void initialize(long user, String requestId, BigDecimal rawAmount, String pin, String confirmation) {
        validatePin(pin);
        if (!pin.equals(confirmation)) { throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Hai lần nhập mã PIN chưa khớp"); }
        BigDecimal amount = amount(rawAmount); validateId(requestId);
        try (var connection = factory.openDatabase()) {
            connection.setAutoCommit(false);
            try {
                WalletTransactions.lockUsers(connection, user);
                if (configured(connection, user)) {
                    checkPin(connection, user, pin);
                    if (replayed(connection, user, requestId, "VAULT_DEPOSIT", amount.negate())) { connection.commit(); return; }
                    throw new AuctionException(ErrorCode.VAULT_ALREADY_CONFIGURED, "Két đã có mã PIN. Hãy mở két để chuyển tiền.");
                }
                replayed(connection, user, requestId, "VAULT_DEPOSIT", amount.negate());
                char[] secret = pin.toCharArray();
                try {
                    var hash = PINS.hash(secret);
                    try (var insert = connection.prepareStatement("INSERT INTO wallet_vaults(user_id,pin_salt,pin_hash,pin_iterations) VALUES(?,?,?,?)")) {
                        insert.setLong(1, user); insert.setString(2, hash.getSaltBase64()); insert.setString(3, hash.getHashBase64()); insert.setInt(4, hash.getIterations()); insert.executeUpdate();
                    }
                } finally { Arrays.fill(secret, '\0'); }
                move(connection, user, requestId, amount, true); connection.commit();
            } catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        } catch (SQLException error) { throw failure(error); }
    }

    public void unlock(long user, String pin) {
        validatePin(pin);
        try (var connection = factory.openDatabase()) {
            connection.setAutoCommit(false);
            try { WalletTransactions.lockUsers(connection, user); checkPin(connection, user, pin); connection.commit(); }
            catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        } catch (SQLException error) { throw failure(error); }
    }

    /** Caller must validate the open-vault grant; withdrawing also verifies the PIN in this transaction. */
    public void transfer(long user, String requestId, String direction, BigDecimal rawAmount, String pin) {
        boolean deposit = "DEPOSIT".equals(direction);
        if (!deposit && !"WITHDRAW".equals(direction)) { throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Hướng chuyển tiền không hợp lệ"); }
        BigDecimal amount = amount(rawAmount); validateId(requestId);
        if (!deposit) { validatePin(pin); }
        try (var connection = factory.openDatabase()) {
            connection.setAutoCommit(false);
            try {
                WalletTransactions.lockUsers(connection, user);
                if (!configured(connection, user)) { throw new AuctionException(ErrorCode.VAULT_NOT_CONFIGURED, "Bạn chưa tạo mã PIN cho két"); }
                if (!deposit) { checkPin(connection, user, pin); }
                String type = deposit ? "VAULT_DEPOSIT" : "VAULT_WITHDRAWAL";
                if (!replayed(connection, user, requestId, type, deposit ? amount.negate() : amount)) {
                    move(connection, user, requestId, amount, deposit);
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        } catch (SQLException error) { throw failure(error); }
    }

    private void move(Connection connection, long user, String id, BigDecimal amount, boolean deposit) throws SQLException {
        BigDecimal delta = deposit ? amount : amount.negate();
        try (var update = connection.prepareStatement("UPDATE users SET vault_balance=vault_balance+? WHERE user_id=? AND vault_balance+?>=0 AND vault_balance+?<=?")) {
            update.setBigDecimal(1, delta); update.setLong(2, user); update.setBigDecimal(3, delta); update.setBigDecimal(4, delta); update.setBigDecimal(5, WalletTransactions.MAX_BALANCE);
            if (update.executeUpdate() != 1) { throw new AuctionException(ErrorCode.INSUFFICIENT_FUNDS, "Tiền trong két không đủ hoặc số dư két vượt giới hạn"); }
        }
        WalletTransactions.change(connection, user, delta.negate(), BigDecimal.ZERO,
                deposit ? "VAULT_DEPOSIT" : "VAULT_WITHDRAWAL", "VAULT:" + id, Instant.now());
    }

    private boolean replayed(Connection connection, long user, String id, String type, BigDecimal amount) throws SQLException {
        try (var query = connection.prepareStatement("SELECT user_id,entry_type,amount FROM wallet_entries WHERE reference_key=?")) {
            query.setString(1, "VAULT:" + id);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) { return false; }
                if (rows.getLong("user_id") != user || !type.equals(rows.getString("entry_type")) || amount.compareTo(rows.getBigDecimal("amount")) != 0) {
                    throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Mã giao dịch đã được sử dụng với nội dung khác");
                }
                return true;
            }
        }
    }

    private boolean configured(Connection connection, long user) throws SQLException {
        try (var query = connection.prepareStatement("SELECT user_id FROM wallet_vaults WHERE user_id=?")) {
            query.setLong(1, user); try (var rows = query.executeQuery()) { return rows.next(); }
        }
    }

    private void checkPin(Connection connection, long user, String pin) throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM wallet_vaults WHERE user_id=?")) {
            query.setLong(1, user);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) { throw new AuctionException(ErrorCode.VAULT_NOT_CONFIGURED, "Bạn chưa tạo mã PIN cho két"); }
                Timestamp blocked = rows.getTimestamp("locked_until"); Instant now = Instant.now();
                if (blocked != null && now.isBefore(blocked.toInstant())) { throw new AuctionException(ErrorCode.VAULT_PIN_LOCKED, "Nhập sai PIN nhiều lần. Hãy chờ 60 giây rồi thử lại."); }
                char[] secret = pin.toCharArray(); boolean correct;
                try { correct = PINS.verify(secret, rows.getString("pin_salt"), rows.getString("pin_hash"), rows.getInt("pin_iterations")); }
                finally { Arrays.fill(secret, '\0'); }
                int failures = correct ? 0 : rows.getInt("failed_attempts") + 1;
                boolean locked = failures >= 5;
                try (var update = connection.prepareStatement("UPDATE wallet_vaults SET failed_attempts=?,locked_until=? WHERE user_id=?")) {
                    update.setInt(1, locked ? 0 : failures);
                    update.setTimestamp(2, locked ? Timestamp.from(now.plusSeconds(60)) : null); update.setLong(3, user); update.executeUpdate();
                }
                if (!correct) {
                    // Keep the failed-attempt counter even though the requested operation is rejected.
                    connection.commit();
                    throw new AuctionException(locked ? ErrorCode.VAULT_PIN_LOCKED : ErrorCode.VAULT_PIN_INVALID,
                            locked ? "Nhập sai PIN 5 lần. Két tạm khóa trong 60 giây." : "Mã PIN không đúng");
                }
            }
        }
    }

    private static void validatePin(String pin) { if (pin == null || !pin.matches("[0-9]{6}")) { throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Mã PIN phải có đúng 6 chữ số"); } }
    private static void validateId(String id) {
        try { if (!UUID.fromString(id).toString().equals(id)) { throw new IllegalArgumentException(); } }
        catch (RuntimeException error) { throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Mã giao dịch không hợp lệ"); }
    }
    private static BigDecimal amount(BigDecimal raw) {
        BigDecimal result;
        try { result = raw.setScale(2, RoundingMode.UNNECESSARY); }
        catch (RuntimeException error) { throw new AuctionException(ErrorCode.INVALID_AMOUNT, "Số tiền chỉ có tối đa 2 chữ số thập phân"); }
        if (result.signum() <= 0 || result.compareTo(WalletTransactions.MAX_BALANCE) > 0) { throw new AuctionException(ErrorCode.INVALID_AMOUNT, "Số tiền chuyển phải lớn hơn 0 và nằm trong giới hạn ví"); }
        return result;
    }
    private static RepositoryException failure(SQLException error) { return new RepositoryException("Vault database operation failed", error); }
}
