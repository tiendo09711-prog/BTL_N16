package vn.ptit.btl16.server.media;

import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class JdbcMediaStorage {
    private static final int CHUNK_BYTES = 256 * 1024;
    private static final List<String> VARIANTS = List.of("source", "thumbnail.jpg", "detail.jpg", "video.mp4");
    private final JdbcConnectionFactory factory;

    public JdbcMediaStorage(JdbcConnectionFactory factory) { this.factory = factory; }

    public static void initialize(JdbcConnectionFactory factory) {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS media_content (
                        media_id VARCHAR(36) NOT NULL, variant VARCHAR(32) NOT NULL,
                        chunk_no INT NOT NULL, payload LONGBLOB NOT NULL,
                        PRIMARY KEY(media_id, variant, chunk_no),
                        FOREIGN KEY(media_id) REFERENCES product_media(media_id) ON DELETE CASCADE
                    ) ENGINE=InnoDB
                    """);
        } catch (SQLException exception) { throw new IllegalStateException("Cannot initialize database media storage", exception); }
    }

    public static void write(Connection connection, String mediaId, Map<String, byte[]> content) throws SQLException {
        if (content == null || !content.containsKey("source") || !content.containsKey("thumbnail.jpg")
                || (!content.containsKey("detail.jpg") && !content.containsKey("video.mp4"))) {
            throw new SQLException("Missing media payload for product commit");
        }
        try (var insert = connection.prepareStatement("INSERT INTO media_content(media_id, variant, chunk_no, payload) VALUES(?, ?, ?, ?)")) {
            for (var variant : content.entrySet()) {
                byte[] bytes = variant.getValue();
                if (!VARIANTS.contains(variant.getKey()) || bytes.length == 0) { throw new SQLException("Invalid media payload"); }
                for (int offset = 0; offset < bytes.length; offset += CHUNK_BYTES) {
                    insert.setString(1, mediaId); insert.setString(2, variant.getKey());
                    insert.setInt(3, offset / CHUNK_BYTES);
                    insert.setBytes(4, Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + CHUNK_BYTES)));
                    insert.executeUpdate();
                }
            }
        }
    }

    public MediaContent content(String mediaId, String variant) throws IOException {
        if (!VARIANTS.contains(variant)) { throw new IOException("Invalid media variant"); }
        long size;
        try (var connection = factory.openDatabase();
             var query = connection.prepareStatement("SELECT COALESCE(SUM(OCTET_LENGTH(payload)), 0) FROM media_content WHERE media_id = ? AND variant = ?")) {
            query.setString(1, mediaId); query.setString(2, variant);
            try (var rows = query.executeQuery()) { rows.next(); size = rows.getLong(1); }
        } catch (SQLException exception) { throw new IOException("Cannot read database media size", exception); }
        if (size == 0) { throw new IOException("Missing database media payload"); }
        return new MediaContent(size, offset -> new InputStream() {
            private long position = offset;
            private byte[] chunk = new byte[0];
            private int cursor;
            private boolean closed;

            @Override public int read() throws IOException {
                byte[] single = new byte[1];
                return read(single, 0, 1) < 0 ? -1 : single[0] & 255;
            }

            @Override public int read(byte[] target, int start, int length) throws IOException {
                java.util.Objects.checkFromIndexSize(start, length, target.length);
                if (closed) { throw new IOException("Media stream is closed"); }
                if (length == 0) { return 0; }
                if (position >= size) { return -1; }
                if (cursor >= chunk.length) {
                    try (var connection = factory.openDatabase();
                         var query = connection.prepareStatement("SELECT payload FROM media_content WHERE media_id = ? AND variant = ? AND chunk_no = ?")) {
                        query.setString(1, mediaId); query.setString(2, variant); query.setLong(3, position / CHUNK_BYTES);
                        try (var rows = query.executeQuery()) {
                            if (!rows.next()) { throw new IOException("Incomplete database media payload"); }
                            chunk = rows.getBytes(1); cursor = (int) (position % CHUNK_BYTES);
                        }
                    } catch (SQLException exception) { throw new IOException("Cannot stream database media", exception); }
                    if (cursor >= chunk.length) { throw new IOException("Incomplete database media chunk"); }
                }
                int count = (int) Math.min(Math.min(length, chunk.length - cursor), size - position);
                System.arraycopy(chunk, cursor, target, start, count);
                cursor += count; position += count;
                return count;
            }

            @Override public void close() { closed = true; chunk = new byte[0]; }
        });
    }

    public long usedBytes() throws IOException {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COALESCE(SUM(OCTET_LENGTH(payload)), 0) FROM media_content")) {
            rows.next(); return rows.getLong(1);
        } catch (SQLException exception) { throw new IOException("Cannot check database media quota", exception); }
    }
}
