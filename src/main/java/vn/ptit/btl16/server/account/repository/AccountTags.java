package vn.ptit.btl16.server.account.repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Stable four-digit discriminators, unique together with the display name. */
public final class AccountTags {
    private AccountTags() { }

    public static int randomStart() { return ThreadLocalRandom.current().nextInt(10_000); }
    public static String candidate(int start, int attempt) { return String.format(Locale.ROOT, "%04d", (start + attempt) % 10_000); }
    public static boolean duplicate(SQLException error) { return error.getErrorCode() == 1062 || "23505".equals(error.getSQLState()); }

    public static void initialize(JdbcConnectionFactory factory) {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "users", "friend_code")) {
                if (!columns.next()) { statement.executeUpdate("ALTER TABLE users ADD COLUMN friend_code CHAR(4) NULL"); }
            }
            boolean indexed = false;
            try (var indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "users", false, false)) {
                while (indexes.next()) { if ("uq_users_friend_tag".equalsIgnoreCase(indexes.getString("INDEX_NAME"))) { indexed = true; } }
            }
            if (!indexed) { statement.executeUpdate("CREATE UNIQUE INDEX uq_users_friend_tag ON users(display_name, friend_code)"); }
            var missing = new ArrayList<Long>();
            try (var rows = statement.executeQuery("SELECT user_id FROM users WHERE friend_code IS NULL ORDER BY user_id")) {
                while (rows.next()) { missing.add(rows.getLong(1)); }
            }
            try (var update = connection.prepareStatement("UPDATE users SET friend_code=?, updated_at=updated_at WHERE user_id=? AND friend_code IS NULL")) {
                for (long user : missing) {
                    int start = randomStart(); boolean assigned = false;
                    for (int attempt = 0; attempt < 10_000; attempt++) {
                        update.setString(1, candidate(start, attempt)); update.setLong(2, user);
                        try { update.executeUpdate(); assigned = true; break; }
                        catch (SQLException error) { if (!duplicate(error)) { throw error; } }
                    }
                    if (!assigned) { throw new SQLException("Không còn mã kết bạn bốn số cho tên hiển thị của tài khoản " + user); }
                }
            }
        } catch (SQLException error) { throw new RepositoryException("Không thể cấp mã kết bạn cho tài khoản", error); }
    }
}
