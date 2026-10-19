package vn.ptit.btl16.server.media;

import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.LinkedHashSet;

public final class JdbcMediaStorage {
    private static final int CHUNK_BYTES = 256 * 1024;
    private static final List<String> FILES = List.of("source", "thumbnail.jpg", "detail.jpg", "video.mp4");
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
        } catch (SQLException exception) { throw new IllegalStateException("Cannot initialize MySQL media storage", exception); }
    }

    public static void write(Connection connection, String mediaId, Path folder) throws SQLException {
        if (folder == null) { throw new SQLException("Missing media payload for MySQL product commit"); }
        if (!Files.isRegularFile(folder.resolve("source"), LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(folder.resolve("thumbnail.jpg"), LinkOption.NOFOLLOW_LINKS)
                || (!Files.isRegularFile(folder.resolve("detail.jpg"), LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(folder.resolve("video.mp4"), LinkOption.NOFOLLOW_LINKS))) {
            throw new SQLException("Incomplete MySQL media payload");
        }
        try (var insert = connection.prepareStatement("INSERT INTO media_content(media_id, variant, chunk_no, payload) VALUES(?, ?, ?, ?)")) {
            for (String filename : FILES) {
                Path file = folder.resolve(filename);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { continue; }
                int sequence = 0;
                try (var input = Files.newInputStream(file)) {
                    byte[] chunk;
                    while ((chunk = input.readNBytes(CHUNK_BYTES)).length != 0) {
                        insert.setString(1, mediaId); insert.setString(2, filename);
                        insert.setInt(3, sequence++); insert.setBytes(4, chunk); insert.executeUpdate();
                    }
                }
            }
        } catch (IOException exception) { throw new SQLException("Cannot read media payload", exception); }
    }

    public void materialize(String mediaId, Path folder) throws IOException {
        if (Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) { return; }
        Path temporary = Files.createTempDirectory(folder.getParent(), "download-");
        boolean moved = false;
        try (var connection = factory.openDatabase()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            var variants = new LinkedHashSet<String>();
            try (var query = connection.prepareStatement("SELECT DISTINCT variant FROM media_content WHERE media_id = ?")) {
                query.setString(1, mediaId);
                try (var rows = query.executeQuery()) { while (rows.next()) { variants.add(rows.getString(1)); } }
            }
            if (variants.isEmpty()) { return; }
            for (String variant : variants) {
                if (!FILES.contains(variant)) { throw new IOException("Invalid database media variant"); }
                try (OutputStream output = Files.newOutputStream(temporary.resolve(variant))) {
                    int next = 0;
                    while (true) {
                        try (var query = connection.prepareStatement("SELECT chunk_no, payload FROM media_content WHERE media_id = ? AND variant = ? AND chunk_no >= ? ORDER BY chunk_no LIMIT 1")) {
                            query.setString(1, mediaId); query.setString(2, variant); query.setInt(3, next);
                            try (var rows = query.executeQuery()) {
                                if (!rows.next()) { break; }
                                if (rows.getInt(1) != next++) { throw new IOException("Incomplete database media payload"); }
                                try (var input = rows.getBinaryStream(2)) { input.transferTo(output); }
                            }
                        }
                    }
                }
            }
            connection.commit();
            Files.move(temporary, folder, java.nio.file.StandardCopyOption.ATOMIC_MOVE); moved = true;
        } catch (SQLException exception) { throw new IOException("Cannot load media from MySQL", exception); }
        finally {
            if (!moved) {
                try (var paths = Files.walk(temporary)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.deleteIfExists(path); }
                }
            }
        }
    }

    public long usedBytes() throws IOException {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COALESCE(SUM(OCTET_LENGTH(payload)), 0) FROM media_content")) {
            rows.next(); return rows.getLong(1);
        } catch (SQLException exception) { throw new IOException("Cannot check MySQL media quota", exception); }
    }
}
