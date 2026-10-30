package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.common.util.AuctionFees;
import vn.ptit.btl16.common.util.RevenuePeriod;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Paid, non-anonymous sales; monthly ranking determines settlement benefits. */
public final class SellerRevenueStore {
    private static final String TOTALS = """
            SELECT a.host_user_id AS seller_id, SUM(s.final_price) AS revenue,
                COUNT(*) AS sale_count, MIN(s.ended_at) AS first_sale
            FROM auction_receipt_summaries s JOIN auctions a ON a.auction_id = s.auction_id
            JOIN wallet_settlements w ON w.auction_id = s.auction_id
            WHERE s.outcome = 'WON' AND s.final_price > 0 AND w.status = 'PAID'
                AND w.amount = s.final_price AND w.seller_id = a.host_user_id
                AND a.anonymous_guests = FALSE AND s.anonymous_guests = FALSE
                AND s.ended_at >= ? AND s.ended_at <= ?
            GROUP BY a.host_user_id
            """;
    private static final String ORDER = " ORDER BY t.revenue DESC, t.first_sale ASC, t.seller_id ASC LIMIT 10";
    private SellerRevenueStore() { }

    /** Caller holds the global closure lock and uses READ_COMMITTED, so concurrent sales see prior commits. */
    public static int rankBeforeSale(Connection connection, long seller) throws SQLException {
        return rankBeforeSale(connection, seller, Instant.now());
    }

    public static int rankBeforeSale(Connection connection, long seller, Instant at) throws SQLException {
        return monthlyRanks(connection, at).getOrDefault(seller, 0);
    }

    private static Map<Long, Integer> monthlyRanks(Connection connection, Instant at) throws SQLException {
        try (var query = connection.prepareStatement("SELECT t.seller_id FROM (" + TOTALS + ") t" + ORDER)) {
            bounds(query, RevenuePeriod.DEFAULT, at);
            Map<Long, Integer> ranks = new LinkedHashMap<>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) { ranks.put(rows.getLong(1), ranks.size() + 1); }
            }
            return ranks;
        }
    }

    public static Map<String, String> topSellers(JdbcConnectionFactory factory) {
        return topSellers(factory, RevenuePeriod.DEFAULT, Instant.now());
    }

    public static Map<String, String> topSellers(JdbcConnectionFactory factory, RevenuePeriod period, Instant at) {
        String sql = "SELECT t.*, u.username, u.display_name, p.avatar_base64 FROM (" + TOTALS + ") t "
                + "JOIN users u ON u.user_id = t.seller_id LEFT JOIN user_public_profiles p ON p.user_id = t.seller_id" + ORDER;
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement(sql)) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            var monthly = monthlyRanks(connection, at);
            bounds(query, period, at);
            Map<String, String> data = new LinkedHashMap<>(); int count = 0;
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    String prefix = "ranking." + count++ + ".";
                    data.put(prefix + "sellerId", Long.toString(rows.getLong("seller_id")));
                    data.put(prefix + "username", rows.getString("username"));
                    data.put(prefix + "displayName", rows.getString("display_name"));
                    data.put(prefix + "revenue", rows.getBigDecimal("revenue").toPlainString());
                    data.put(prefix + "saleCount", Long.toString(rows.getLong("sale_count")));
                    data.put(prefix + "firstSaleAt", rows.getTimestamp("first_sale").toInstant().toString());
                    String avatar = rows.getString("avatar_base64");
                    data.put(prefix + "avatarBase64", avatar == null ? "" : avatar);
                    int benefitRank = monthly.getOrDefault(rows.getLong("seller_id"), 0);
                    data.put(prefix + "benefitRank", Integer.toString(benefitRank));
                    data.put(prefix + "discountRate", AuctionFees.discount(benefitRank).toPlainString());
                }
            }
            connection.commit();
            data.put("period", period.code());
            data.put("periodStart", period.startAt(at).toString());
            data.put("periodEnd", at.toString());
            data.put("benefitPeriod", RevenuePeriod.DEFAULT.code());
            data.put("count", Integer.toString(count)); return data;
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot read seller revenue ranking", error); }
    }

    private static void bounds(PreparedStatement query, RevenuePeriod period, Instant at) throws SQLException {
        query.setTimestamp(1, Timestamp.from(period.startAt(at)));
        query.setTimestamp(2, Timestamp.from(at));
    }
}
