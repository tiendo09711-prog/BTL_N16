package vn.ptit.btl16.server.media;

import vn.ptit.btl16.common.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record MediaConfig(Path root, Path ffmpeg, Path ffprobe, int port, long imageBytes, long videoBytes,
                          long imagePixels, long videoPixels, long storageBytes, long productBytes,
                          int timeoutSeconds, int videoSeconds, int detailEdge, int workers) {
    public static MediaConfig load(boolean isolated) {
        AppProperties properties = AppProperties.load("btl16.server.config", "config/server.properties");
        Path data;
        try {
            String local = System.getenv("LOCALAPPDATA");
            data = isolated ? Files.createTempDirectory("btl16-media-test-") : Path.of(System.getProperty("btl16.dataDir",
                    Path.of(local == null ? System.getProperty("user.home") : local, "BTL16", "AuctionServer").toString()));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create isolated media directory", exception);
        }
        if (!data.isAbsolute()) { data = Path.of(System.getProperty("user.home")).resolve(data); }
        data = data.toAbsolutePath().normalize();
        Path root = isolated ? data.resolve("media") : Path.of(System.getProperty("btl16.media.root", properties.get("media.root", data.resolve("media").toString())));
        if (!root.isAbsolute()) {
            root = data.resolve(root);
        }
        Path application;
        try {
            application = Path.of(MediaConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            application = Files.isDirectory(application) ? application.getParent().getParent() : application.getParent();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot locate media tools", exception);
        }
        String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
        Path tools = application.resolve("tools/media");
        return new MediaConfig(root.toAbsolutePath().normalize(),
                Path.of(System.getProperty("btl16.ffmpeg", properties.get("media.ffmpeg", tools.resolve("ffmpeg" + suffix).toString()))),
                Path.of(System.getProperty("btl16.ffprobe", properties.get("media.ffprobe", tools.resolve("ffprobe" + suffix).toString()))),
                isolated ? 0 : properties.getInt("media.port", 8891, 0, 65535),
                properties.getLong("media.imageMaxBytes", 32L << 20, 1024, 1L << 30),
                properties.getLong("media.videoMaxBytes", 200L << 20, 1024, 4L << 30),
                properties.getLong("media.imageMaxPixels", 24_000_000L, 100, 100_000_000L),
                properties.getLong("media.videoMaxPixels", 8_500_000L, 100, 100_000_000L),
                properties.getLong("media.storageMaxBytes", 5L << 30, 1L << 20, 1L << 44),
                properties.getLong("media.productMaxBytes", 400L << 20, 1024, 8L << 30),
                properties.getInt("media.processingTimeoutSeconds", 180, 1, 1800),
                properties.getInt("media.videoMaxSeconds", 180, 1, 3600),
                properties.getInt("media.detailMaxEdge", 4096, 512, 8192),
                properties.getInt("media.workers", 2, 1, 8));
    }
}
