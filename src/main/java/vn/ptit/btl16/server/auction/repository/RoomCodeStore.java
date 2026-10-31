package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.IntSupplier;

public final class RoomCodeStore {
    private static final SecureRandom RANDOM = new SecureRandom();

    private RoomCodeStore() { }

    public static void initialize(JdbcConnectionFactory factory) {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
            boolean hasColumn = false;
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "%", "%")) {
                while (columns.next()) {
                    if ("auctions".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                            && "room_code".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) { hasColumn = true; break; }
                }
            }
            if (!hasColumn) { statement.executeUpdate("ALTER TABLE auctions ADD COLUMN room_code VARCHAR(6) NULL"); }
            boolean hasIndex = false;
            try (var indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "auctions", true, false)) {
                while (indexes.next()) {
                    if ("room_code".equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) { hasIndex = true; break; }
                }
            }
            if (!hasIndex) { statement.executeUpdate("CREATE UNIQUE INDEX uq_auction_room_code ON auctions(room_code)"); }
            var missing = new ArrayList<Long>();
            try (var rows = statement.executeQuery("SELECT auction_id FROM auctions WHERE room_code IS NULL ORDER BY auction_id")) {
                while (rows.next()) { missing.add(rows.getLong(1)); }
            }
            connection.setAutoCommit(false);
            try {
                for (long auctionId : missing) { allocate(connection, auctionId); }
                connection.commit();
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw new AuctionRepositoryException("Cannot initialize room codes", error);
        }
    }

    static String allocate(Connection connection, long auctionId) throws SQLException {
        return allocate(connection, auctionId, () -> RANDOM.nextInt(1_000_000));
    }

    static String allocate(Connection connection, long auctionId, IntSupplier numbers) throws SQLException {
        if (connection.getAutoCommit()) { throw new SQLException("Room code allocation requires a transaction"); }
        try (var update = connection.prepareStatement("UPDATE auctions SET room_code=? WHERE auction_id=? AND room_code IS NULL")) {
            for (int attempt = 0; attempt < 128; attempt++) {
                String code = String.format(Locale.ROOT, "%06d", numbers.getAsInt());
                update.setString(1, code);
                update.setLong(2, auctionId);
                try {
                    if (update.executeUpdate() != 1) { throw new SQLException("Room code is already assigned or auction is missing"); }
                    return code;
                } catch (SQLException error) {
                    if (error.getErrorCode() != 1062 && !"23505".equals(error.getSQLState())) { throw error; }
                }
            }
        }
        throw new SQLException("Cannot allocate an unused six-digit room code");
    }
}
