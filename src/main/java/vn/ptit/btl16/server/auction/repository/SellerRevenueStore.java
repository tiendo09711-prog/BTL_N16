package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.common.util.AuctionFees;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lifetime gross revenue from successful sales. Both the board and settlement use the same ordering. */
public final class SellerRevenueStore {
    private static final String TOTALS = """
            SELECT a.host_user_id AS seller_id, SUM(s.final_price) AS revenue,
                COUNT(*) AS sale_count, MIN(s.ended_at) AS first_sale
            FROM auction_receipt_summaries s JOIN auctions a ON a.auction_id = s.auction_id
            WHERE s.outcome = 'WON' AND s.final_price > 0
            GROUP BY a.host_user_id
            """;
    private static final String ORDER = " ORDER BY t.revenue DESC, t.first_sale ASC, t.seller_id ASC LIMIT 10";
    private SellerRevenueStore() { }

    /** Caller holds the global closure lock and uses READ_COMMITTED, so concurrent sales see prior commits. */
    public static int rankBeforeSale(Connection connection, long seller) throws SQLException {
        try (var query = connection.prepareStatement("SELECT t.seller_id FROM (" + TOTALS + ") t" + ORDER);
             var rows = query.executeQuery()) {
            int rank = 0;
            while (rows.next()) { rank++; if (rows.getLong(1) == seller) { return rank; } }
            return 0;
        }
    }

    public static Map<String, String> topSellers(JdbcConnectionFactory factory) {
        String sql = "SELECT t.*, u.username, u.display_name, p.avatar_base64 FROM (" + TOTALS + ") t "
                + "JOIN users u ON u.user_id = t.seller_id LEFT JOIN user_public_profiles p ON p.user_id = t.seller_id" + ORDER;
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement(sql); var rows = query.executeQuery()) {
            Map<String, String> data = new LinkedHashMap<>(); int count = 0;
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
                data.put(prefix + "discountRate", AuctionFees.discount(count).toPlainString());
            }
            data.put("count", Integer.toString(count)); return data;
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot read seller revenue ranking", error); }
    }
}
