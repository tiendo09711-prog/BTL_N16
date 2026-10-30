package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.model.AuctionResult;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.model.ProductImage;
import vn.ptit.btl16.server.auction.model.RoomVisibility;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.auction.service.AuctionRules;
import vn.ptit.btl16.server.media.ProductMedia;
import vn.ptit.btl16.server.media.MediaProductCommit;
import vn.ptit.btl16.server.media.ProductRequirements;
import vn.ptit.btl16.server.wallet.WalletTransactions;

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
    @Override public void deleteNotification(long user, long id) {
        new vn.ptit.btl16.server.notification.PersonalNotificationStore(connectionFactory).delete(user, id);
    }
    @Override public vn.ptit.btl16.common.protocol.ProductTransactionStatus productTransactionStatus(long productId) {
        try (var connection = connectionFactory.openDatabase(); var query = connection.prepareStatement("""
                SELECT CASE WHEN EXISTS(SELECT 1 FROM auctions WHERE product_id=? AND status='ENDED' AND current_winner_id IS NOT NULL)
                    THEN 'SOLD' WHEN EXISTS(SELECT 1 FROM auctions WHERE product_id=? AND status='OPEN')
                    THEN 'AUCTIONING' ELSE 'UNTRADED' END
                """)) {
            query.setLong(1, productId); query.setLong(2, productId);
            try (var rows = query.executeQuery()) { rows.next(); return vn.ptit.btl16.common.protocol.ProductTransactionStatus.valueOf(rows.getString(1)); }
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot load product transaction status", error); }
    }
    @Override public java.util.Map<Long, vn.ptit.btl16.server.auction.model.AuctionActivity> auctionActivity() {
        try (var connection = connectionFactory.openDatabase(); var query = connection.prepareStatement("""
                SELECT a.auction_id,
                    (SELECT COUNT(*) FROM bids b WHERE b.auction_id=a.auction_id) AS bid_count,
                    (SELECT COUNT(*) FROM room_events e WHERE e.auction_id=a.auction_id AND e.event_type='JOINED') AS movement_count
                FROM auctions a WHERE a.status='OPEN' AND a.visibility='PUBLIC'
                """); var rows = query.executeQuery()) {
            java.util.Map<Long, vn.ptit.btl16.server.auction.model.AuctionActivity> result = new java.util.HashMap<>();
            while (rows.next()) { result.put(rows.getLong(1), new vn.ptit.btl16.server.auction.model.AuctionActivity(rows.getLong(2), rows.getLong(3))); }
            return result;
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot load featured auction activity", error); }
    }
    @Override public java.util.Map<String, String> topProducts() {
        return new AuctionReceiptStore(connectionFactory).topProducts();
    }
    @Override public java.util.Map<String, String> topSellers() { return SellerRevenueStore.topSellers(connectionFactory); }
    @Override public java.util.Map<String, String> topSellers(vn.ptit.btl16.common.util.RevenuePeriod period) {
        return SellerRevenueStore.topSellers(connectionFactory, period, Instant.now());
    }
    @Override public java.util.List<java.util.Map<String,String>> operationEvents(Long room, String type, Instant from, Instant to, long before, int limit) {
        if (before <= 0 || limit < 1 || limit > 100 || (room != null && room <= 0) || (from != null && to != null && !from.isBefore(to))) {
            throw new IllegalArgumentException("Invalid operation log filters");
        }
        StringBuilder sql = new StringBuilder("SELECT event_id, auction_id, event_type, occurred_at, amount FROM room_events WHERE event_id<?");
        List<Object> values = new ArrayList<>(List.of(before));
        if (room != null) { sql.append(" AND auction_id=?"); values.add(room); }
        if (!type.isBlank()) { sql.append(" AND event_type=?"); values.add(type); }
        if (from != null) { sql.append(" AND occurred_at>=?"); values.add(Timestamp.from(from)); }
        if (to != null) { sql.append(" AND occurred_at<?"); values.add(Timestamp.from(to)); }
        sql.append(" ORDER BY event_id DESC LIMIT ?"); values.add(limit);
        try (var connection = connectionFactory.openDatabase(); var query = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) { query.setObject(index + 1, values.get(index)); }
            List<java.util.Map<String,String>> result = new ArrayList<>();
            try (var rows = query.executeQuery()) { while (rows.next()) {
                var amount = rows.getBigDecimal("amount");
                result.add(java.util.Map.of("id", Long.toString(rows.getLong("event_id")), "auctionId", Long.toString(rows.getLong("auction_id")),
                        "type", rows.getString("event_type"), "time", rows.getTimestamp("occurred_at").toInstant().toString(),
                        "amount", amount == null ? "" : amount.toPlainString()));
            } }
            return result;
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot read operation log", error); }
    }
    @Override public java.util.Map<String,String> notifications(long user, long before, boolean unread, int limit) {
        return new vn.ptit.btl16.server.notification.PersonalNotificationStore(connectionFactory).list(user,before,unread,limit);
    }
    @Override public void readNotification(long user, long id, boolean read) {
        new vn.ptit.btl16.server.notification.PersonalNotificationStore(connectionFactory).read(user,id,read);
    }
    @Override public java.util.Map<String, String> personalReceipt(long user, long room) {
        return new AuctionReceiptStore(connectionFactory).receipt(user, room);
    }
    @Override public java.util.Map<String, String> personalReceipts(long user, String scope, String outcome,
                                                               String search, long before, int limit) {
        return new AuctionReceiptStore(connectionFactory).list(user, scope, outcome, search, before, limit);
    }
    @Override public List<vn.ptit.btl16.server.auction.model.RoomProgress.Event> findRoomEvents(long room, long before, long after, int limit) {
        return new RoomProgressStore(connectionFactory).events(room, before, after, limit);
    }
    @Override public void appendPresence(long room, String type, long user, Instant time) {
        new RoomProgressStore(connectionFactory).presence(room, type, user, time);
    }
    @Override public vn.ptit.btl16.server.auction.model.RoomProgress.Stats roomStats(long room) {
        return new RoomProgressStore(connectionFactory).stats(room);
    }
    @Override public List<vn.ptit.btl16.server.auction.model.RoomProgress.HistoryBid> bidPage(long room, long before, long through, Long user, int limit) {
        return new RoomProgressStore(connectionFactory).history(room, before, through, user, limit);
    }
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
                   a.start_time, a.end_time, a.hard_end_time, a.status, a.ended_at, a.version,
                   a.visibility, a.room_password_hash, a.room_password_salt,
                   a.room_password_iterations, a.anonymous_guests, a.max_participants, a.deposit_amount
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

    public vn.ptit.btl16.server.media.JdbcMediaStorage mediaStorage() {
        return new vn.ptit.btl16.server.media.JdbcMediaStorage(connectionFactory);
    }

    @Override public vn.ptit.btl16.server.media.MediaContent mediaContent(String mediaId, String variant) throws java.io.IOException {
        return mediaStorage().content(mediaId, variant);
    }

    @Override public long mediaBytes() throws java.io.IOException { return mediaStorage().usedBytes(); }

    @Override
    public Product saveMediaProduct(MediaProductCommit commit) {
        ProductRequirements.validate(commit.description(), commit.media());
        long productId;
        if (commit.productId() > 0) {
            productId = productTransaction(commit.productId(), commit.ownerId(), (connection, active) -> {
                if (exists(connection, "SELECT 1 FROM auctions WHERE product_id = ?", commit.productId())) {
                    throw new AuctionException(ErrorCode.PRODUCT_FROZEN, "Mặt hàng đã từng đấu giá; không được sửa nội dung, ảnh/video, ảnh đại diện hoặc thứ tự");
                }
                return writeMediaProduct(connection, commit);
            });
        } else {
            try (Connection connection = connectionFactory.openDatabase()) {
                connection.setAutoCommit(false);
                try {
                    productId = writeMediaProduct(connection, commit);
                    connection.commit();
                } catch (SQLException | RuntimeException exception) {
                    connection.rollback();
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new AuctionRepositoryException("Cannot save media product", exception);
            }
        }
        return findProductById(productId).orElseThrow();
    }

    private long writeMediaProduct(Connection connection, MediaProductCommit commit) throws SQLException {
        long productId = commit.productId();
        for (ProductMedia media : commit.media()) {
            try (PreparedStatement check = connection.prepareStatement("SELECT product_id FROM product_media WHERE media_id = ?")) {
                check.setString(1, media.id());
                try (ResultSet result = check.executeQuery()) {
                    if (result.next() && (productId == 0 || result.getLong(1) != productId)) {
                        throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Không được lấy ảnh/video của sản phẩm khác");
                    }
                }
            }
        }
        ProductMedia cover = commit.media().stream().filter(ProductMedia::image).findFirst().orElseThrow();
        String sql = productId == 0 ? """
                INSERT INTO products(created_by, code, name, description, image_mime, image_name, image_size, image_version, active, created_at, updated_at)
                VALUES(?, ?, ?, ?, 'image/jpeg', ?, ?, 1, TRUE, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """ : """
                UPDATE products SET created_by = ?, name = ?, description = ?, image_mime = 'image/jpeg',
                    image_name = ?, image_size = ?, image_version = image_version + 1, updated_at = CURRENT_TIMESTAMP(3)
                WHERE product_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            statement.setLong(index++, commit.ownerId());
            if (productId == 0) { statement.setString(index++, ProductCodeStore.allocate(connection)); }
            statement.setString(index++, commit.name());
            statement.setString(index++, commit.description());
            statement.setString(index++, cover.name());
            statement.setInt(index++, (int) Math.min(Integer.MAX_VALUE, cover.bytes()));
            if (productId > 0) { statement.setLong(index, productId); }
            statement.executeUpdate();
            if (productId == 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) { throw new SQLException("No generated product id"); }
                    productId = keys.getLong(1);
                }
            }
        }
        List<String> previous = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("SELECT media_id FROM product_media WHERE product_id = ?")) {
            query.setLong(1, productId);
            try (ResultSet rows = query.executeQuery()) { while (rows.next()) { previous.add(rows.getString(1)); } }
        }
        int order = 0;
        for (ProductMedia media : commit.media()) {
            if (previous.contains(media.id())) {
                try (PreparedStatement update = connection.prepareStatement("UPDATE product_media SET sort_order = ? WHERE media_id = ? AND product_id = ?")) {
                    update.setInt(1, order++); update.setString(2, media.id()); update.setLong(3, productId); update.executeUpdate();
                }
            } else {
                insertMedia(connection, media.attached(productId, order++));
                vn.ptit.btl16.server.media.JdbcMediaStorage.write(connection, media.id(), commit.content().get(media.id()));
            }
        }
        for (String id : previous) {
            if (commit.media().stream().noneMatch(media -> media.id().equals(id))) {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM product_media WHERE media_id = ? AND product_id = ?")) {
                    delete.setString(1, id); delete.setLong(2, productId); delete.executeUpdate();
                }
            }
        }
        return productId;
    }

    private void insertMedia(Connection connection, ProductMedia media) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO product_media(media_id, product_id, kind, original_name, source_type, stored_bytes, width, height, duration_seconds, sort_order)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, media.id());
            statement.setLong(2, media.productId());
            statement.setString(3, media.kind());
            statement.setString(4, media.name());
            statement.setString(5, media.sourceType());
            statement.setLong(6, media.bytes());
            statement.setInt(7, media.width());
            statement.setInt(8, media.height());
            statement.setDouble(9, media.duration());
            statement.setInt(10, media.position());
            statement.executeUpdate();
        }
    }

    @Override
    public List<ProductMedia> findProductMedia(long productId) {
        return loadMedia("SELECT * FROM product_media WHERE product_id = ? ORDER BY sort_order", productId);
    }

    @Override
    public Optional<ProductMedia> findMedia(String mediaId) {
        return loadMedia("SELECT * FROM product_media WHERE media_id = ?", mediaId).stream().findFirst();
    }

    private List<ProductMedia> loadMedia(String sql, Object key) {
        try (Connection connection = connectionFactory.openDatabase(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, key);
            try (ResultSet result = statement.executeQuery()) {
                List<ProductMedia> values = new ArrayList<>();
                while (result.next()) {
                    values.add(new ProductMedia(result.getString("media_id"), result.getLong("product_id"), result.getString("kind"),
                            result.getString("original_name"), result.getString("source_type"), result.getLong("stored_bytes"),
                            result.getInt("width"), result.getInt("height"), result.getDouble("duration_seconds"), result.getInt("sort_order")));
                }
                return values;
            }
        } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot read product media", exception); }
    }

    @Override
    public List<Long> findLegacyImageProductIds() {
        try (Connection connection = connectionFactory.openDatabase(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT product_id FROM products p WHERE image_data IS NOT NULL AND image_size > 0 AND NOT EXISTS (SELECT 1 FROM product_media m WHERE m.product_id = p.product_id)")) {
            List<Long> values = new ArrayList<>();
            while (result.next()) { values.add(result.getLong(1)); }
            return values;
        } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot list legacy images", exception); }
    }

    @Override
    public void migrateLegacyImage(long productId, long expectedVersion, ProductMedia media, java.util.Map<String, byte[]> content) {
        Product product = findProductById(productId).orElseThrow();
        productTransaction(productId, product.getOwnerId(), (connection, active) -> {
            try (PreparedStatement check = connection.prepareStatement("SELECT image_version FROM products WHERE product_id = ?")) {
                check.setLong(1, productId);
                try (ResultSet result = check.executeQuery()) {
                    if (!result.next() || result.getLong(1) != expectedVersion
                            || exists(connection, "SELECT 1 FROM product_media WHERE product_id = ?", productId)) {
                        throw new AuctionException(ErrorCode.DATABASE_CONFLICT, "Ảnh cũ đã thay đổi trong khi chuyển đổi");
                    }
                }
            }
            insertMedia(connection, media.attached(productId, 0));
            vn.ptit.btl16.server.media.JdbcMediaStorage.write(connection, media.id(), content);
            return null;
        });
    }

    @Override
    public Product createProduct(CreateProductCommit commit) {
        String sql = """
                INSERT INTO products(
                    created_by, code, name, description, image_data, image_mime,
                    image_name, image_size, image_version, active, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """;
        return transaction(connection -> {
            String code = ProductCodeStore.allocate(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, commit.getOwnerId());
                statement.setString(2, code);
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
                            code, commit.getName(), commit.getDescription(), true,
                            commit.getCreatedAt(), commit.getCreatedAt(), commit.hasImage(),
                            commit.getImageMime(), commit.getImageName(),
                            commit.hasImage() ? image.length : 0, commit.hasImage() ? 1L : 0L);
                }
            }
        });
    }

    @Override
    public Product updateProduct(UpdateProductCommit commit) {
        String sql = commit.isReplaceImage() ? """
                UPDATE products
                SET name = ?, description = ?, updated_at = ?,
                    image_data = ?, image_mime = ?, image_name = ?, image_size = ?,
                    image_version = image_version + 1
                WHERE product_id = ? AND created_by = ?
                """ : """
                UPDATE products
                SET name = ?, description = ?, updated_at = ?
                WHERE product_id = ? AND created_by = ?
                """;
        productTransaction(commit.getProductId(), commit.getOwnerId(), (connection, active) -> {
            if (exists(connection, "SELECT 1 FROM auctions WHERE product_id = ?", commit.getProductId())) {
                throw new AuctionException(ErrorCode.PRODUCT_FROZEN, "Sản phẩm đã từng đấu giá không được sửa");
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, commit.getName());
                statement.setString(2, commit.getDescription());
                statement.setTimestamp(3, Timestamp.from(commit.getUpdatedAt()));
                int index = 4;
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
                return null;
            }
        });
        return findProductById(commit.getProductId())
                .orElseThrow(() -> new AuctionRepositoryException("Updated product could not be loaded"));
    }

    @Override
    public Optional<Product> deactivateProduct(long productId, long ownerId) {
        String sql = """
                UPDATE products
                SET active = FALSE, updated_at = CURRENT_TIMESTAMP(3)
                WHERE product_id = ? AND created_by = ?
                """;
        productTransaction(productId, ownerId, (connection, active) -> {
            if (exists(connection, "SELECT 1 FROM auctions WHERE product_id = ? AND status = 'OPEN'", productId)) {
                throw new AuctionException(ErrorCode.PRODUCT_IN_USE, "Sản phẩm đang có phiên mở");
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, productId);
                statement.setLong(2, ownerId);
                if (statement.executeUpdate() != 1) {
                    throw new AuctionRepositoryException("Product was not found for owner");
                }
                return null;
            }
        });
        return findProductById(productId);
    }

    @Override
    public Product restoreProduct(long productId, long ownerId) {
        return productTransaction(productId, ownerId, (connection, active) -> {
            if (!active) {
                if (exists(connection, "SELECT 1 FROM auctions WHERE product_id = ? AND status = 'OPEN'", productId)) {
                    throw new AuctionException(ErrorCode.PRODUCT_IN_USE,
                            "Không thể khôi phục mặt hàng đang có phiên đấu giá mở");
                }
                if (exists(connection, "SELECT 1 FROM auctions WHERE product_id = ? AND status = 'ENDED' AND current_winner_id IS NOT NULL", productId)) {
                    throw new AuctionException(ErrorCode.PRODUCT_ALREADY_SOLD,
                            "Mặt hàng đã có người thắng, không được khôi phục để đấu giá lại kể cả chưa thanh toán");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE products SET active = TRUE, updated_at = CURRENT_TIMESTAMP(3) WHERE product_id = ?")) {
                    statement.setLong(1, productId);
                    if (statement.executeUpdate() != 1) {
                        throw new AuctionRepositoryException("Product could not be restored");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(PRODUCT_SELECT + " WHERE p.product_id = ?")) {
                statement.setLong(1, productId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new AuctionRepositoryException("Restored product could not be loaded");
                    }
                    return mapProduct(result);
                }
            }
        });
    }

    @Override
    public boolean isProductDeleted(long productId) { return exists("SELECT 1 FROM deleted_products WHERE product_id = ?", productId); }

    @Override
    public void deleteProduct(long productId, long ownerId) {
        productTransaction(productId, ownerId, (connection, active) -> {
            if (exists(connection, "SELECT 1 FROM auctions WHERE product_id = ? AND status = 'OPEN'", productId)) {
                throw new AuctionException(ErrorCode.PRODUCT_IN_USE, "Sản phẩm còn phiên đang mở hoặc sắp diễn ra. Hãy kết thúc hoặc hủy phiên trước khi xóa.");
            }
            if (exists(connection, "SELECT 1 FROM auctions WHERE product_id = ?", productId)) {
                try (var mark = connection.prepareStatement("INSERT INTO deleted_products(product_id, deleted_at) VALUES(?, CURRENT_TIMESTAMP(3))");
                     var update = connection.prepareStatement("UPDATE products SET active=FALSE,updated_at=CURRENT_TIMESTAMP(3) WHERE product_id=?")) {
                    mark.setLong(1, productId); mark.executeUpdate(); update.setLong(1, productId); update.executeUpdate();
                }
            } else {
                try (var media = connection.prepareStatement("DELETE FROM product_media WHERE product_id=?");
                     var product = connection.prepareStatement("DELETE FROM products WHERE product_id=?")) {
                    media.setLong(1, productId); media.executeUpdate(); // media_content cascades with product_media.
                    product.setLong(1, productId); product.executeUpdate();
                }
            }
            return null;
        });
    }

    @Override
    public List<Product> findProductsByOwner(long ownerId) {
        String sql = PRODUCT_SELECT + " WHERE p.created_by = ? AND NOT EXISTS (SELECT 1 FROM deleted_products d WHERE d.product_id=p.product_id) ORDER BY p.created_at DESC";
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
    public List<Product> findPurchasedProducts(long userId) {
        String sql = PRODUCT_SELECT + " WHERE EXISTS (SELECT 1 FROM auctions a JOIN auction_results r ON r.auction_id=a.auction_id"
                + " WHERE a.product_id=p.product_id AND a.status='ENDED' AND r.winner_id=?) ORDER BY p.updated_at DESC, p.product_id DESC";
        try (var connection = connectionFactory.openDatabase(); var query = connection.prepareStatement(sql)) {
            query.setLong(1, userId);
            try (var rows = query.executeQuery()) {
                List<Product> products = new ArrayList<>();
                while (rows.next()) { products.add(mapProduct(rows)); }
                return products;
            }
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot load purchased products", error); }
    }

    @Override
    public boolean hasPurchasedProduct(long userId, long productId) {
        try (var connection = connectionFactory.openDatabase(); var query = connection.prepareStatement(
                "SELECT 1 FROM auctions a JOIN auction_results r ON r.auction_id=a.auction_id"
                + " WHERE a.product_id=? AND a.status='ENDED' AND r.winner_id=? LIMIT 1")) {
            query.setLong(1, productId); query.setLong(2, userId);
            try (var rows = query.executeQuery()) { return rows.next(); }
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot check purchased product", error); }
    }

    @Override
    public java.util.Set<Long> anonymousPurchasedProducts(long userId) {
        try (var connection=connectionFactory.openDatabase(); var query=connection.prepareStatement("""
                SELECT a.product_id FROM auctions a JOIN auction_results r ON r.auction_id=a.auction_id
                WHERE a.status='ENDED' AND a.anonymous_guests=TRUE AND r.winner_id=?
                """)) {
            query.setLong(1,userId); java.util.Set<Long> products=new java.util.HashSet<>();
            try (var rows=query.executeQuery()) { while (rows.next()) { products.add(rows.getLong(1)); } }
            return products;
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot check anonymous purchases",error); }
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
    public boolean hasAuctionsForProduct(long productId) {
        return exists("SELECT 1 FROM auctions WHERE product_id = ? LIMIT 1", productId);
    }

    @Override
    public boolean hasSoldAuctionForProduct(long productId) {
        return exists("SELECT 1 FROM auctions WHERE product_id = ? AND status = 'ENDED' "
                + "AND current_winner_id IS NOT NULL LIMIT 1", productId);
    }

    @Override
    public AuctionSnapshot createAuction(CreateAuctionCommit commit) {
        String sql = """
                INSERT INTO auctions(
                    host_user_id, product_id, start_price, min_bid_increment,
                    current_price, current_winner_id, start_time, end_time,
                    status, ended_at, version, visibility, room_password_hash,
                    room_password_salt, room_password_iterations, hard_end_time, wallet_required, anonymous_guests,
                    max_participants, deposit_amount)
                VALUES(?, ?, ?, ?, ?, NULL, ?, ?, 'OPEN', NULL, 0, ?, ?, ?, ?, ?, TRUE, ?, ?, ?)
                """;
        long createdId = productTransaction(commit.getProductId(), commit.getHostUserId(), (connection, active) -> {
            if (!active) {
                throw new AuctionException(ErrorCode.PRODUCT_INACTIVE, "Sản phẩm đã bị vô hiệu hóa");
            }
            try (PreparedStatement ready = connection.prepareStatement("SELECT description FROM products WHERE product_id = ?")) {
                ready.setLong(1, commit.getProductId());
                try (ResultSet result = ready.executeQuery()) {
                    if (!result.next()) { throw new SQLException("Missing product"); }
                    List<ProductMedia> media = new ArrayList<>();
                    try (PreparedStatement query = connection.prepareStatement("SELECT media_id, kind FROM product_media WHERE product_id = ?")) {
                        query.setLong(1, commit.getProductId());
                        try (ResultSet rows = query.executeQuery()) {
                            while (rows.next()) { media.add(new ProductMedia(rows.getString(1), commit.getProductId(), rows.getString(2), "", "", 0, 0, 0, 0, 0)); }
                        }
                    }
                    ProductRequirements.validate(result.getString(1), media);
                }
            }
            if (exists(connection, "SELECT 1 FROM auctions WHERE product_id = ? AND status = 'OPEN'", commit.getProductId())) {
                throw new AuctionException(ErrorCode.AUCTION_ALREADY_EXISTS, "Sản phẩm đã có phiên mở");
            }
            if (exists(connection, "SELECT 1 FROM auctions WHERE product_id = ? AND status = 'ENDED' AND current_winner_id IS NOT NULL", commit.getProductId())) {
                throw new AuctionException(ErrorCode.PRODUCT_ALREADY_SOLD, "Sản phẩm đã có người thắng");
            }
            AuctionRules.requireCompetitivePrice(
                    AuctionRules.money(commit.getStartPrice(), ErrorCode.INVALID_AMOUNT),
                    AuctionRules.money(commit.getMinBidIncrement(), ErrorCode.INVALID_INCREMENT));
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
                statement.setTimestamp(12, Timestamp.from(commit.getEndTime()
                        .plusSeconds(AuctionRules.MAX_AUTO_EXTENSION_SECONDS)));
                statement.setBoolean(13, commit.hasAnonymousGuests());
                statement.setInt(14, commit.getMaxParticipants());
                statement.setBigDecimal(15, commit.getDepositAmount());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Database did not return auction_id");
                    }
                    long auctionId = keys.getLong(1);
                    RoomProgressStore.append(connection, auctionId, "CREATED", commit.getHostUserId(), null, "", Instant.now());
                    return auctionId;
                }
            }
        });
        return findAuctionById(createdId)
                .orElseThrow(() -> new AuctionRepositoryException("Created auction could not be loaded"));
    }

    @Override
    public BigDecimal auctionDeposit(long auctionId, long userId) {
        try (var connection = connectionFactory.openDatabase()) {
            return AuctionAdmissionStore.held(connection, auctionId, userId);
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot read room deposit", error); }
    }

    @Override
    public BigDecimal placeAuctionDeposit(long auctionId, long userId) {
        return transaction(connection -> AuctionAdmissionStore.place(connection, auctionId, userId));
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
                SELECT b.bid_id, b.auction_id, b.user_id, u.username, u.display_name,
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
                            resultSet.getString("display_name"),
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
                SELECT current_price, current_winner_id, host_user_id, min_bid_increment,
                       start_time, end_time, hard_end_time, status, deposit_amount
                FROM auctions WHERE auction_id = ? FOR UPDATE
                """;
        String insertBid = """
                INSERT INTO bids(auction_id, user_id, amount, server_sequence, created_at)
                VALUES(?, ?, ?, ?, ?)
                """;
        String updateAuction = """
                UPDATE auctions
                SET current_price = ?, current_winner_id = ?, end_time = ?,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP(3), wallet_required = TRUE
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
                        BigDecimal amount = AuctionRules.money(commit.getAmount(), ErrorCode.INVALID_AMOUNT);
                        if (AuctionAdmissionStore.held(connection, commit.getAuctionId(), commit.getBidderId())
                                .compareTo(resultSet.getBigDecimal("deposit_amount")) < 0) {
                            throw new AuctionException(ErrorCode.ROOM_DEPOSIT_REQUIRED, "Chưa đặt cọc đủ cho phòng hẹn giờ.");
                        }
                        if (commit.getCreatedAt().isBefore(resultSet.getTimestamp("start_time").toInstant())
                                || resultSet.getLong("host_user_id") == commit.getBidderId()
                                || resultSet.getLong("current_winner_id") == commit.getBidderId()
                                || amount.compareTo(resultSet.getBigDecimal("current_price")
                                        .add(resultSet.getBigDecimal("min_bid_increment"))) < 0
                                || commit.getNewEndTime().isBefore(resultSet.getTimestamp("end_time").toInstant())
                                || commit.getNewEndTime().isAfter(resultSet.getTimestamp("hard_end_time").toInstant())
                                || isBlocked(connection, commit.getAuctionId(), commit.getBidderId())) {
                            throw new AuctionConflictException("Bid violates auction rules");
                        }
                    }
                }
                Instant previousEnd;
                Long previousLeader;
                try (var query = connection.prepareStatement("SELECT end_time, current_winner_id FROM auctions WHERE auction_id = ?")) {
                    query.setLong(1, commit.getAuctionId());
                    try (var result = query.executeQuery()) {
                        result.next(); previousEnd = result.getTimestamp(1).toInstant();
                        long leader = result.getLong(2); previousLeader = result.wasNull() ? null : leader;
                    }
                }
                WalletTransactions.reserve(connection, commit.getAuctionId(), commit.getBidderId(),
                        commit.getAmount(), commit.getServerSequence(), commit.getCreatedAt());
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
                RoomProgressStore.append(connection, commit.getAuctionId(), "BID_ACCEPTED", commit.getBidderId(), commit.getAmount(), "", commit.getCreatedAt());
                RoomProgressStore.append(connection, commit.getAuctionId(), "LEADER_CHANGED", commit.getBidderId(), commit.getAmount(), "", commit.getCreatedAt());
                vn.ptit.btl16.server.notification.PersonalNotificationStore.acceptedBid(connection, commit.getAuctionId(),
                        commit.getBidderId(), previousLeader, commit.getServerSequence(), commit.getCreatedAt());
                if (commit.getNewEndTime().isAfter(previousEnd)) {
                    RoomProgressStore.append(connection, commit.getAuctionId(), "EXTENDED_AUTO", null, null, commit.getNewEndTime().toString(), commit.getCreatedAt());
                }
                String displayName = commit.getBidderUsername();
                try (var query = connection.prepareStatement("SELECT display_name FROM users WHERE user_id = ?")) {
                    query.setLong(1, commit.getBidderId());
                    try (var result = query.executeQuery()) {
                        if (result.next()) { displayName = result.getString("display_name"); }
                    }
                }
                connection.commit();
                return new BidRecord(
                        bidId, commit.getAuctionId(), commit.getBidderId(), commit.getBidderUsername(), displayName,
                        commit.getAmount(), commit.getServerSequence(), commit.getCreatedAt());
            } catch (RuntimeException | SQLException exception) {
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
        long addedSeconds = java.time.Duration.between(commit.getExpectedEndTime(), commit.getNewEndTime()).getSeconds();
        if (addedSeconds < AuctionRules.MIN_EXTENSION_SECONDS || addedSeconds > AuctionRules.MAX_EXTENSION_SECONDS
                || !commit.getExpectedEndTime().plusSeconds(addedSeconds).equals(commit.getNewEndTime())) {
            return false;
        }
        String sql = """
                UPDATE auctions
                SET end_time = ?, version = version + 1,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE auction_id = ? AND status = 'OPEN' AND end_time = ?
                  AND start_time <= ? AND end_time > ?
                  AND ? <= hard_end_time
                  AND NOT EXISTS (SELECT 1 FROM bids b WHERE b.auction_id = auctions.auction_id)
                """;
        return transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(commit.getNewEndTime()));
                statement.setLong(2, commit.getAuctionId());
                statement.setTimestamp(3, Timestamp.from(commit.getExpectedEndTime()));
                Instant now = Instant.now();
                statement.setTimestamp(4, Timestamp.from(now));
                statement.setTimestamp(5, Timestamp.from(now));
                statement.setTimestamp(6, Timestamp.from(commit.getNewEndTime()));
                if (statement.executeUpdate() != 1) { return false; }
                RoomProgressStore.append(connection, commit.getAuctionId(), "EXTENDED_MANUAL", null, null, commit.getNewEndTime().toString(), now);
                return true;
            }
        });
    }

    @Override
    public Optional<AuctionResult> closeAuction(CloseAuctionCommit commit) {
        String lockSql = """
                SELECT end_time, status, current_price, current_winner_id, host_user_id, wallet_required FROM auctions
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
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
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
                        long winnerRaw = resultSet.getLong("current_winner_id");
                        Long winnerId = resultSet.wasNull() ? null : winnerRaw;
                        if (!java.util.Objects.equals(winnerId, commit.getWinnerId())
                                || resultSet.getBigDecimal("current_price").compareTo(commit.getFinalPrice()) != 0
                                || !databaseEnd.equals(commit.getExpectedEndTime())
                                || (!commit.isRequireExpired() && exists(connection,
                                        "SELECT 1 FROM bids WHERE auction_id = ?", commit.getAuctionId()))) {
                            connection.rollback();
                            return Optional.empty();
                        }
                        if (commit.isRequireExpired() && commit.getEndedAt().isBefore(databaseEnd)) {
                            connection.rollback();
                            return Optional.empty();
                        }
                        if (winnerId != null) {
                            WalletTransactions.settle(connection, commit.getAuctionId(), winnerId,
                                    resultSet.getLong("host_user_id"), commit.getFinalPrice(), commit.getEndedAt(),
                                    resultSet.getBoolean("wallet_required"));
                        } else {
                            AuctionAdmissionStore.release(connection, commit.getAuctionId(), null, BigDecimal.ZERO, commit.getEndedAt());
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
                RoomProgressStore.append(connection, commit.getAuctionId(), "ENDED", commit.getWinnerId(), commit.getFinalPrice(), "", commit.getEndedAt());
                AuctionReceiptStore.capture(connection, commit.getAuctionId());
                vn.ptit.btl16.server.notification.PersonalNotificationStore.result(connection, commit.getAuctionId(), commit.getEndedAt());
                connection.commit();
                return Optional.of(new AuctionResult(
                        commit.getAuctionId(), commit.getWinnerId(), commit.getWinnerUsername(),
                        commit.getFinalPrice(), commit.getEndedAt()));
            } catch (RuntimeException | SQLException exception) {
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
        return transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(commit.getCancelledAt()));
                statement.setLong(2, commit.getAuctionId());
                if (statement.executeUpdate() != 1) { return false; }
                AuctionAdmissionStore.release(connection, commit.getAuctionId(), null, BigDecimal.ZERO, commit.getCancelledAt());
                RoomProgressStore.append(connection, commit.getAuctionId(), "CANCELLED", null, null, "", commit.getCancelledAt());
                AuctionReceiptStore.capture(connection, commit.getAuctionId());
                vn.ptit.btl16.server.notification.PersonalNotificationStore.result(connection, commit.getAuctionId(), commit.getCancelledAt());
                return true;
            }
        });
    }

    @Override
    public boolean hasBids(long auctionId) {
        return exists("SELECT 1 FROM bids WHERE auction_id = ? LIMIT 1", auctionId);
    }

    @Override
    public boolean hasBidsByUser(long auctionId, long userId) {
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM bids WHERE auction_id = ? AND user_id = ? LIMIT 1")) {
            statement.setLong(1, auctionId);
            statement.setLong(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot check bidder", exception);
        }
    }

    private boolean isBlocked(Connection connection, long auctionId, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM auction_blocked_users WHERE auction_id = ? AND user_id = ?")) {
            statement.setLong(1, auctionId);
            statement.setLong(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean exists(Connection connection, String sql, long value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    @FunctionalInterface
    private interface Transaction<Result> { Result execute(Connection connection) throws SQLException; }

    private <Result> Result transaction(Transaction<Result> operation) {
        try (Connection connection = connectionFactory.openDatabase()) {
            connection.setAutoCommit(false);
            try {
                Result result = operation.execute(connection); connection.commit(); return result;
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot commit room change", exception); }
    }

    @FunctionalInterface
    private interface ProductTransaction<Result> {
        Result execute(Connection connection, boolean active) throws SQLException;
    }

    private <Result> Result productTransaction(long productId, long ownerId, ProductTransaction<Result> operation) {
        try (Connection connection = connectionFactory.openDatabase()) {
            connection.setAutoCommit(false);
            try {
                boolean active;
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT created_by, active FROM products WHERE product_id = ? FOR UPDATE")) {
                    lock.setLong(1, productId);
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next() || result.getLong("created_by") != ownerId) {
                            throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Sản phẩm không thuộc tài khoản này");
                        }
                        active = result.getBoolean("active");
                    }
                }
                if (exists(connection, "SELECT 1 FROM deleted_products WHERE product_id=?", productId)) {
                    throw new AuctionException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm đã được xóa và không thể khôi phục");
                }
                Result result = operation.execute(connection, active);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new AuctionRepositoryException("Cannot commit product transaction", exception);
        }
    }

    @Override
    public void blockAuctionUser(BlockAuctionUserCommit commit) {
        String sql = """
                INSERT INTO auction_blocked_users(auction_id, user_id, blocked_by, blocked_at)
                VALUES(?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE blocked_by = VALUES(blocked_by), blocked_at = VALUES(blocked_at)
                """;
        try (Connection connection = connectionFactory.openDatabase()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT host_user_id FROM auctions WHERE auction_id = ? FOR UPDATE")) {
                    lock.setLong(1, commit.getAuctionId());
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next() || result.getLong(1) != commit.getBlockedBy()) {
                            throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Chỉ chủ trì được mời người khác ra khỏi phòng");
                        }
                        if (commit.getUserId() == commit.getBlockedBy()) {
                            throw new AuctionException(ErrorCode.CANNOT_KICK_HOST, "Không được mời chủ trì ra khỏi phòng");
                        }
                    }
                }
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT 1 FROM bids WHERE auction_id = ? AND user_id = ? LIMIT 1")) {
                    check.setLong(1, commit.getAuctionId());
                    check.setLong(2, commit.getUserId());
                    try (ResultSet result = check.executeQuery()) {
                        if (result.next()) {
                            throw new AuctionException(ErrorCode.CANNOT_KICK_BIDDER, "Không được mời người đã ra giá ra khỏi phòng");
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, commit.getAuctionId());
                    statement.setLong(2, commit.getUserId());
                    statement.setLong(3, commit.getBlockedBy());
                    statement.setTimestamp(4, Timestamp.from(commit.getBlockedAt()));
                    statement.executeUpdate();
                }
                AuctionAdmissionStore.releaseUser(connection, commit.getAuctionId(), commit.getUserId(), BigDecimal.ZERO, commit.getBlockedAt());
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
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
                resultSet.getInt("room_password_iterations"),
                resultSet.getTimestamp("hard_end_time").toInstant(), resultSet.getBoolean("anonymous_guests"),
                resultSet.getInt("max_participants"), resultSet.getBigDecimal("deposit_amount"));
    }
}
