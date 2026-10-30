package vn.ptit.btl16.server.wallet;

import vn.ptit.btl16.common.util.RevenuePeriod;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PlatformCashFlowRepository {
    public record Entry(long auctionId, Instant collectedAt, String seller, String buyer,
                        boolean anonymous, BigDecimal saleAmount, BigDecimal feeRate,
                        BigDecimal feeAmount, BigDecimal sellerNet) { }
    public record Snapshot(Instant from, Instant to, BigDecimal balance, BigDecimal gross,
                           BigDecimal fees, BigDecimal sellerNet, long paidCount, long reviewCount,
                           List<Entry> entries, boolean hasMore) { }
    private final JdbcConnectionFactory factory;

    public PlatformCashFlowRepository(JdbcConnectionFactory factory) { this.factory = factory; }

    public static void initialize(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS platform_fee_entries (
                        auction_id BIGINT PRIMARY KEY, sale_amount DECIMAL(18,2) NOT NULL,
                        fee_amount DECIMAL(18,2) NOT NULL, seller_net DECIMAL(18,2) NOT NULL,
                        fee_rate DECIMAL(5,4) NOT NULL, collected_at DATETIME(3) NOT NULL,
                        FOREIGN KEY(auction_id) REFERENCES wallet_settlements(auction_id),
                        INDEX idx_platform_fee_time(collected_at, auction_id)
                    ) ENGINE=InnoDB
                    """);
        }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (var lock = connection.prepareStatement("SELECT lock_id FROM revenue_settlement_lock WHERE lock_id=1 FOR UPDATE");
                 var rows = lock.executeQuery()) {
                if (!rows.next()) { throw new SQLException("Missing settlement lock"); }
            }
            try (var migrate = connection.prepareStatement("""
                    INSERT INTO platform_fee_entries(auction_id,sale_amount,fee_amount,seller_net,fee_rate,collected_at)
                    SELECT w.auction_id,w.amount,w.platform_fee,w.amount-w.platform_fee,w.fee_rate,w.created_at
                    FROM wallet_settlements w WHERE w.status='PAID' AND w.amount>0
                        AND w.platform_fee>=0 AND w.platform_fee<=w.amount
                        AND w.fee_rate>=0 AND w.fee_rate<=1
                        AND ROUND(w.amount*w.fee_rate,2)=w.platform_fee
                        AND NOT EXISTS (SELECT 1 FROM platform_fee_entries p WHERE p.auction_id=w.auction_id)
                    """)) { migrate.executeUpdate(); }
            connection.commit();
        } catch (SQLException error) { connection.rollback(); throw error; }
        finally { connection.setAutoCommit(autoCommit); }
    }

    public static void collect(Connection connection, long auctionId, BigDecimal fee, Instant at) throws SQLException {
        if (connection.getAutoCommit()) { throw new SQLException("Fee collection requires the settlement transaction"); }
        try (var insert = connection.prepareStatement("""
                INSERT INTO platform_fee_entries(auction_id,sale_amount,fee_amount,seller_net,fee_rate,collected_at)
                SELECT auction_id,amount,platform_fee,amount-platform_fee,fee_rate,?
                FROM wallet_settlements WHERE auction_id=? AND status='PAID' AND platform_fee=?
                """)) {
            insert.setTimestamp(1, Timestamp.from(at)); insert.setLong(2, auctionId); insert.setBigDecimal(3, fee);
            if (insert.executeUpdate() != 1) { throw new SQLException("Cannot collect unsettled platform fee"); }
        }
    }

    public Snapshot snapshot(RevenuePeriod period, long before) {
        Instant to = Instant.now(), from = period.startAt(to);
        try (var connection = factory.openDatabase()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            BigDecimal balance;
            try (var query = connection.prepareStatement("SELECT COALESCE(SUM(fee_amount),0) FROM platform_fee_entries");
                 var rows = query.executeQuery()) { rows.next(); balance = rows.getBigDecimal(1); }
            BigDecimal gross, fees, net; long count;
            try (var query = connection.prepareStatement("""
                    SELECT COALESCE(SUM(sale_amount),0),COALESCE(SUM(fee_amount),0),
                        COALESCE(SUM(seller_net),0),COUNT(*) FROM platform_fee_entries
                    WHERE collected_at>=? AND collected_at<=?
                    """)) {
                query.setTimestamp(1, Timestamp.from(from)); query.setTimestamp(2, Timestamp.from(to));
                try (var rows = query.executeQuery()) {
                    rows.next(); gross=rows.getBigDecimal(1); fees=rows.getBigDecimal(2);
                    net=rows.getBigDecimal(3); count=rows.getLong(4);
                }
            }
            long review;
            try (var query = connection.prepareStatement("""
                    SELECT COUNT(*) FROM auctions a JOIN auction_results r ON r.auction_id=a.auction_id
                    LEFT JOIN wallet_settlements w ON w.auction_id=a.auction_id
                    LEFT JOIN platform_fee_entries p ON p.auction_id=a.auction_id
                    WHERE r.winner_id IS NOT NULL AND (w.auction_id IS NULL OR w.status<>'PAID' OR p.auction_id IS NULL)
                    """); var rows = query.executeQuery()) { rows.next(); review=rows.getLong(1); }
            List<Entry> entries = new ArrayList<>();
            try (var query = connection.prepareStatement("""
                    SELECT p.*,a.anonymous_guests,seller.username AS seller_name,buyer.username AS buyer_name
                    FROM platform_fee_entries p JOIN wallet_settlements w ON w.auction_id=p.auction_id
                    JOIN auctions a ON a.auction_id=p.auction_id JOIN users seller ON seller.user_id=w.seller_id
                    JOIN users buyer ON buyer.user_id=w.buyer_id
                    WHERE p.collected_at>=? AND p.collected_at<=? AND p.auction_id<?
                    ORDER BY p.auction_id DESC LIMIT 51
                    """)) {
                query.setTimestamp(1, Timestamp.from(from)); query.setTimestamp(2, Timestamp.from(to));
                query.setLong(3, before <= 0 ? Long.MAX_VALUE : before);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        boolean anonymous = rows.getBoolean("anonymous_guests");
                        entries.add(new Entry(rows.getLong("auction_id"), rows.getTimestamp("collected_at").toInstant(),
                                anonymous ? "\u1ea8n danh" : rows.getString("seller_name"),
                                anonymous ? "\u1ea8n danh" : rows.getString("buyer_name"), anonymous,
                                rows.getBigDecimal("sale_amount"), rows.getBigDecimal("fee_rate"),
                                rows.getBigDecimal("fee_amount"), rows.getBigDecimal("seller_net")));
                    }
                }
            }
            connection.commit();
            boolean more = entries.size()>50;
            return new Snapshot(from,to,balance,gross,fees,net,count,review,
                    List.copyOf(more ? entries.subList(0,50) : entries),more);
        } catch (SQLException error) { throw new IllegalStateException("Cannot read platform cash flow", error); }
    }
}
