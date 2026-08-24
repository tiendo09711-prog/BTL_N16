package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.model.AuctionResult;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.model.ProductImage;
import vn.ptit.btl16.server.auction.model.RoomVisibility;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcAuctionRepository implements AuctionRepository {
    private static final String PRODUCT_SELECT = """
            SELECT p.product_id, p.created_by AS product_owner_id,
                   owner.username AS product_owner_username,
                   p.code AS product_code, p.name AS product_name,
                   p.description AS product_description, p.active AS product_active,
                   p.created_at AS product_created_at, p.updated_at AS product_updated_at,
                   p.image_mime AS product_image_mime, p.image_name AS product_image_name,
                   p.image_size AS product_image_size, p.image_version AS product_image_version
            FROM products p
            JOIN users owner ON owner.user_id = p.created_by
            """;

    private static final String AUCTION_SELECT = """
            SELECT a.auction_id, a.host_user_id, host.username AS host_username,
                   a.product_id, p.created_by AS product_owner_id,
                   owner.username AS product_owner_username,
                   p.code AS product_code, p.name AS product_name,
                   p.description AS product_description, p.active AS product_active,
                   p.created_at AS product_created_at, p.updated_at AS product_updated_at,
                   p.image_mime AS product_image_mime, p.image_name AS product_image_name,
                   p.image_size AS product_image_size, p.image_version AS product_image_version,
                   a.start_price, a.min_bid_increment, a.current_price,
                   a.current_winner_id, winner.username AS current_winner_username,
                   a.start_time, a.end_time, a.status, a.ended_at, a.version,
                   a.visibility, a.room_password_hash, a.room_password_salt,
                   a.room_password_iterations
            FROM auctions a
            JOIN products p ON p.product_id = a.product_id
            JOIN users owner ON owner.user_id = p.created_by
            JOIN users host ON host.user_id = a.host_user_id
            LEFT JOIN users winner ON winner.user_id = a.current_winner_id
            """;

    private final JdbcConnectionFactory connectionFactory;

    public JdbcAuctionRepository(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Product createProduct(CreateProductCommit commit) {
        String sql = """
                INSERT INTO products(
                    created_by, code, name, description, image_data, image_mime,
                    image_name, image_size, image_version, active, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """;
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, commit.getOwnerId());
            statement.setString(2, commit.getCode());
            statement.setString(3, commit.getName());
            statement.setString(4, commit.getDescription());
            byte[] image = commit.getImageData();
            statement.setBytes(5, image);
            statement.setString(6, commit.hasImage() ? commit.getImageMime() : null);
            statement.setString(7, commit.hasImage() ? commit.getImageName() : null);
            if (commit.hasImage()) {
                statement.setInt(8, image.length);
            } else {
                statement.setNull(8, Types.INTEGER);
            }
            statement.setLong(9, commit.hasImage() ? 1L : 0L);
            statement.setTimestamp(10, Timestamp.from(commit.getCreatedAt()));
            statement.setTimestamp(11, Timestamp.from(commit.getCreatedAt()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return product_id");
                }
                return new Product(
                        keys.getLong(1), commit.getOwnerId(), commit.getOwnerUsername(),
                        commit.getCode(), commit.getName(), commit.getDescription(), true,
                        commit.getCreatedAt(), commit.getCreatedAt(), commit.hasImage(),
                        commit.getImageMime(), commit.getImageName(),
                        commit.hasImage() ? image.length : 0, commit.hasImage() ? 1L : 0L);
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot create product", exception);
        }
    }

    @Override
    public Product updateProduct(UpdateProductCommit commit) {
        String sql = commit.isReplaceImage() ? """
                UPDATE products
                SET code = ?, name = ?, description = ?, updated_at = ?,
                    image_data = ?, image_mime = ?, image_name = ?, image_size = ?,
                    image_version = image_version + 1
                WHERE product_id = ? AND created_by = ?
                """ : """
                UPDATE products
                SET code = ?, name = ?, description = ?, updated_at = ?
                WHERE product_id = ? AND created_by = ?
                """;
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, commit.getCode());
            statement.setString(2, commit.getName());
            statement.setString(3, commit.getDescription());
            statement.setTimestamp(4, Timestamp.from(commit.getUpdatedAt()));
            int index = 5;
            if (commit.isReplaceImage()) {
                byte[] image = commit.getImageData();
                statement.setBytes(index++, image);
                statement.setString(index++, commit.getImageMime());
                statement.setString(index++, commit.getImageName());
                statement.setInt(index++, image == null ? 0 : image.length);
            }
            statement.setLong(index++, commit.getProductId());
            statement.setLong(index, commit.getOwnerId());
            if (statement.executeUpdate() != 1) {
                throw new AuctionRepositoryException("Product was not found for owner");
            }
            return findProductById(commit.getProductId())
                    .orElseThrow(() -> new AuctionRepositoryException("Updated product could not be loaded"));
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot update product", exception);
        }
    }

    @Override
    public Optional<Product> deactivateProduct(long productId, long ownerId) {
        String sql = """
                UPDATE products
                SET active = FALSE, updated_at = CURRENT_TIMESTAMP(3)
                WHERE product_id = ? AND created_by = ?
                """;
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);
            statement.setLong(2, ownerId);
            if (statement.executeUpdate() != 1) {
                return Optional.empty();
            }
            return findProductById(productId);
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot deactivate product", exception);
        }
    }

    @Override
    public List<Product> findProductsByOwner(long ownerId) {
        String sql = PRODUCT_SELECT + " WHERE p.created_by = ? ORDER BY p.created_at DESC";
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Product> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(mapProduct(resultSet));
                }
                return values;
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot load owner products", exception);
        }
    }

    @Override
    public Optional<Product> findProductById(long productId) {
        String sql = PRODUCT_SELECT + " WHERE p.product_id = ?";
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapProduct(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot load product", exception);
        }
    }

    @Override
    public Optional<Product> findProductByCode(String code) {
        String sql = PRODUCT_SELECT + " WHERE UPPER(p.code) = UPPER(?)";
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapProduct(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot load product by code", exception);
        }
    }

    @Override
    public Optional<ProductImage> findProductImage(long productId) {
        String sql = """
                SELECT product_id, image_data, image_mime, image_name, image_version
                FROM products WHERE product_id = ? AND image_data IS NOT NULL
                """;
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ProductImage(
                        resultSet.getLong("product_id"),
                        resultSet.getBytes("image_data"),
                        resultSet.getString("image_mime"),
                        resultSet.getString("image_name"),
                        resultSet.getLong("image_version")));
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot load product image " + productId, exception);
        }
    }

    @Override
    public boolean hasOpenAuctionForProduct(long productId) {
        String sql = "SELECT 1 FROM auctions WHERE product_id = ? AND status = 'OPEN' LIMIT 1";
        return exists(sql, productId);
    }

    @Override
    public AuctionSnapshot createAuction(CreateAuctionCommit commit) {
        String sql = """
                INSERT INTO auctions(
                    host_user_id, product_id, start_price, min_bid_increment,
                    current_price, current_winner_id, start_time, end_time,
                    status, ended_at, version, visibility, room_password_hash,
                    room_password_salt, room_password_iterations)
                VALUES(?, ?, ?, ?, ?, NULL, ?, ?, 'OPEN', NULL, 0, ?, ?, ?, ?)
                """;
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, commit.getHostUserId());
            statement.setLong(2, commit.getProductId());
            statement.setBigDecimal(3, commit.getStartPrice());
            statement.setBigDecimal(4, commit.getMinBidIncrement());
            statement.setBigDecimal(5, commit.getStartPrice());
            statement.setTimestamp(6, Timestamp.from(commit.getStartTime()));
            statement.setTimestamp(7, Timestamp.from(commit.getEndTime()));
            statement.setString(8, commit.getVisibility().name());
            statement.setString(9, commit.getRoomPasswordHash().isBlank()
                    ? null : commit.getRoomPasswordHash());
            statement.setString(10, commit.getRoomPasswordSalt().isBlank()
                    ? null : commit.getRoomPasswordSalt());
            if (commit.getRoomPasswordIterations() == null) {
                statement.setNull(11, Types.INTEGER);
            } else {
                statement.setInt(11, commit.getRoomPasswordIterations());
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return auction_id");
                }
                long auctionId = keys.getLong(1);
                return findAuctionById(auctionId)
                        .orElseThrow(() -> new AuctionRepositoryException("Created auction could not be loaded"));
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot create auction", exception);
        }
    }

    @Override
    public List<AuctionSnapshot> findAllAuctions() {
        return loadAuctions(AUCTION_SELECT
                + " ORDER BY CASE WHEN a.status = 'OPEN' THEN 0 ELSE 1 END, a.end_time", null);
    }

    @Override
    public List<AuctionSnapshot> findAuctionsByHost(long hostUserId) {
        return loadAuctions(AUCTION_SELECT
                + " WHERE a.host_user_id = ? ORDER BY CASE WHEN a.status = 'OPEN' THEN 0 ELSE 1 END, a.end_time",
                hostUserId);
    }

    private List<AuctionSnapshot> loadAuctions(String sql, Long hostUserId) {
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (hostUserId != null) {
                statement.setLong(1, hostUserId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AuctionSnapshot> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(mapAuction(resultSet));
                }
                return values;
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot load auctions", exception);
        }
    }

    @Override
    public Optional<AuctionSnapshot> findAuctionById(long auctionId) {
        String sql = AUCTION_SELECT + " WHERE a.auction_id = ?";
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, auctionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapAuction(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot load auction " + auctionId, exception);
        }
    }

    @Override
    public List<BidRecord> findRecentBids(long auctionId, int limit) {
        String sql = """
                SELECT b.bid_id, b.auction_id, b.user_id, u.username,
                       b.amount, b.server_sequence, b.created_at
                FROM bids b
                JOIN users u ON u.user_id = b.user_id
                WHERE b.auction_id = ?
                ORDER BY b.server_sequence DESC
                LIMIT ?
                """;
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, auctionId);
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<BidRecord> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(new BidRecord(
                            resultSet.getLong("bid_id"), resultSet.getLong("auction_id"),
                            resultSet.getLong("user_id"), resultSet.getString("username"),
                            resultSet.getBigDecimal("amount"), resultSet.getLong("server_sequence"),
                            resultSet.getTimestamp("created_at").toInstant()));
                }
                return values;
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot load bid history", exception);
        }
    }

    @Override
    public BidRecord commitAcceptedBid(BidCommit commit) {
        String lockSql = """
                SELECT current_price, end_time, status
                FROM auctions WHERE auction_id = ? FOR UPDATE
                """;
        String insertBid = """
                INSERT INTO bids(auction_id, user_id, amount, server_sequence, created_at)
                VALUES(?, ?, ?, ?, ?)
                """;
        String updateAuction = """
                UPDATE auctions
                SET current_price = ?, current_winner_id = ?, end_time = ?,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
                WHERE auction_id = ?
                """;
        try (Connection connection = connectionFactory.openDatabase()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
                    lock.setLong(1, commit.getAuctionId());
                    try (ResultSet resultSet = lock.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new AuctionConflictException("Auction does not exist");
                        }
                        if (!"OPEN".equals(resultSet.getString("status"))) {
                            throw new AuctionConflictException("Auction is not open");
                        }
                        if (resultSet.getBigDecimal("current_price")
                                .compareTo(commit.getExpectedCurrentPrice()) != 0) {
                            throw new AuctionConflictException("Auction price changed concurrently");
                        }
                        if (!commit.getCreatedAt().isBefore(
                                resultSet.getTimestamp("end_time").toInstant())) {
                            throw new AuctionConflictException("Bid arrived after end time");
                        }
                    }
                }
                long bidId;
                try (PreparedStatement insert = connection.prepareStatement(
                        insertBid, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, commit.getAuctionId());
                    insert.setLong(2, commit.getBidderId());
                    insert.setBigDecimal(3, commit.getAmount());
                    insert.setLong(4, commit.getServerSequence());
                    insert.setTimestamp(5, Timestamp.from(commit.getCreatedAt()));
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Database did not return bid_id");
                        }
                        bidId = keys.getLong(1);
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(updateAuction)) {
                    update.setBigDecimal(1, commit.getAmount());
                    update.setLong(2, commit.getBidderId());
                    update.setTimestamp(3, Timestamp.from(commit.getNewEndTime()));
                    update.setLong(4, commit.getAuctionId());
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("Auction update count was not 1");
                    }
                }
                connection.commit();
                return new BidRecord(
                        bidId, commit.getAuctionId(), commit.getBidderId(), commit.getBidderUsername(),
                        commit.getAmount(), commit.getServerSequence(), commit.getCreatedAt());
            } catch (AuctionConflictException | SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (AuctionConflictException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot commit accepted bid", exception);
        }
    }

    @Override
    public boolean extendAuction(ExtendAuctionCommit commit) {
        String sql = """
                UPDATE auctions
                SET end_time = ?, version = version + 1,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE auction_id = ? AND status = 'OPEN' AND end_time = ?
                """;
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(commit.getNewEndTime()));
            statement.setLong(2, commit.getAuctionId());
            statement.setTimestamp(3, Timestamp.from(commit.getExpectedEndTime()));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot extend auction", exception);
        }
    }

    @Override
    public Optional<AuctionResult> closeAuction(CloseAuctionCommit commit) {
        String lockSql = """
                SELECT end_time, status FROM auctions
                WHERE auction_id = ? FOR UPDATE
                """;
        String updateSql = """
                UPDATE auctions
                SET status = 'ENDED', ended_at = ?, version = version + 1,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE auction_id = ?
                """;
        String resultSql = """
                INSERT INTO auction_results(auction_id, winner_id, final_price, ended_at)
                VALUES(?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE winner_id = VALUES(winner_id),
                    final_price = VALUES(final_price), ended_at = VALUES(ended_at)
                """;
        try (Connection connection = connectionFactory.openDatabase()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
                    lock.setLong(1, commit.getAuctionId());
                    try (ResultSet resultSet = lock.executeQuery()) {
                        if (!resultSet.next() || !"OPEN".equals(resultSet.getString("status"))) {
                            connection.rollback();
                            return Optional.empty();
                        }
                        Instant databaseEnd = resultSet.getTimestamp("end_time").toInstant();
                        if (commit.isRequireExpired() && commit.getEndedAt().isBefore(databaseEnd)) {
                            connection.rollback();
                            return Optional.empty();
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    update.setTimestamp(1, Timestamp.from(commit.getEndedAt()));
                    update.setLong(2, commit.getAuctionId());
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("Auction close update count was not 1");
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(resultSql)) {
                    insert.setLong(1, commit.getAuctionId());
                    if (commit.getWinnerId() == null) {
                        insert.setNull(2, Types.BIGINT);
                    } else {
                        insert.setLong(2, commit.getWinnerId());
                    }
                    insert.setBigDecimal(3, commit.getFinalPrice());
                    insert.setTimestamp(4, Timestamp.from(commit.getEndedAt()));
                    insert.executeUpdate();
                }
                connection.commit();
                return Optional.of(new AuctionResult(
                        commit.getAuctionId(), commit.getWinnerId(), commit.getWinnerUsername(),
                        commit.getFinalPrice(), commit.getEndedAt()));
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot close auction", exception);
        }
    }

    @Override
    public boolean cancelAuction(CancelAuctionCommit commit) {
        String sql = """
                UPDATE auctions a
                SET a.status = 'CANCELLED', a.ended_at = ?, a.version = a.version + 1,
                    a.updated_at = CURRENT_TIMESTAMP(3)
                WHERE a.auction_id = ? AND a.status = 'OPEN'
                  AND NOT EXISTS (SELECT 1 FROM bids b WHERE b.auction_id = a.auction_id)
                """;
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(commit.getCancelledAt()));
            statement.setLong(2, commit.getAuctionId());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot cancel auction", exception);
        }
    }

    @Override
    public boolean hasBids(long auctionId) {
        return exists("SELECT 1 FROM bids WHERE auction_id = ? LIMIT 1", auctionId);
    }

    @Override
    public void blockAuctionUser(BlockAuctionUserCommit commit) {
        String sql = """
                INSERT INTO auction_blocked_users(auction_id, user_id, blocked_by, blocked_at)
                VALUES(?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE blocked_by = VALUES(blocked_by), blocked_at = VALUES(blocked_at)
                """;
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, commit.getAuctionId());
            statement.setLong(2, commit.getUserId());
            statement.setLong(3, commit.getBlockedBy());
            statement.setTimestamp(4, Timestamp.from(commit.getBlockedAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot block auction user", exception);
        }
    }

    @Override
    public boolean isAuctionUserBlocked(long auctionId, long userId) {
        String sql = "SELECT 1 FROM auction_blocked_users WHERE auction_id = ? AND user_id = ? LIMIT 1";
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, auctionId);
            statement.setLong(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot check blocked auction user", exception);
        }
    }

    @Override
    public long findMaxServerSequence() {
        String sql = "SELECT COALESCE(MAX(server_sequence), 0) AS max_sequence FROM bids";
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong("max_sequence") : 0L;
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot load max server sequence", exception);
        }
    }

    private boolean exists(String sql, long value) {
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot execute existence query", exception);
        }
    }

    private Product mapProduct(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("product_created_at");
        Timestamp updatedAt = resultSet.getTimestamp("product_updated_at");
        return new Product(
                resultSet.getLong("product_id"),
                resultSet.getLong("product_owner_id"),
                resultSet.getString("product_owner_username"),
                resultSet.getString("product_code"),
                resultSet.getString("product_name"),
                resultSet.getString("product_description"),
                resultSet.getBoolean("product_active"),
                createdAt == null ? null : createdAt.toInstant(),
                updatedAt == null ? null : updatedAt.toInstant(),
                resultSet.getLong("product_image_version") > 0,
                resultSet.getString("product_image_mime"),
                resultSet.getString("product_image_name"),
                resultSet.getInt("product_image_size"),
                resultSet.getLong("product_image_version"));
    }

    private AuctionSnapshot mapAuction(ResultSet resultSet) throws SQLException {
        Product product = mapProduct(resultSet);
        long winnerRaw = resultSet.getLong("current_winner_id");
        Long winnerId = resultSet.wasNull() ? null : winnerRaw;
        Timestamp endedAt = resultSet.getTimestamp("ended_at");
        return new AuctionSnapshot(
                resultSet.getLong("auction_id"),
                product,
                resultSet.getLong("host_user_id"),
                resultSet.getString("host_username"),
                resultSet.getBigDecimal("start_price"),
                resultSet.getBigDecimal("min_bid_increment"),
                resultSet.getBigDecimal("current_price"),
                winnerId,
                resultSet.getString("current_winner_username"),
                resultSet.getTimestamp("start_time").toInstant(),
                resultSet.getTimestamp("end_time").toInstant(),
                AuctionStatus.valueOf(resultSet.getString("status")),
                endedAt == null ? null : endedAt.toInstant(),
                resultSet.getLong("version"),
                RoomVisibility.valueOf(resultSet.getString("visibility")),
                resultSet.getString("room_password_hash"),
                resultSet.getString("room_password_salt"),
                resultSet.getInt("room_password_iterations"));
    }
}
