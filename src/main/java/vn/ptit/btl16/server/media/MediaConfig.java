package vn.ptit.btl16.server.media;

import vn.ptit.btl16.common.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        if (!isolated && "com.mysql.cj.jdbc.Driver".equals(properties.get("db.driver", "com.mysql.cj.jdbc.Driver"))) {
            try { root = Files.createTempDirectory("btl16-mysql-media-"); }
            catch (IOException exception) { throw new IllegalStateException("Cannot prepare temporary media workspace", exception); }
        }
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
                resolveTool(properties, "ffmpeg", tools, suffix),
                resolveTool(properties, "ffprobe", tools, suffix),
                isolated ? 0 : properties.getInt("media.port", 8891, 0, 65535),
                properties.getLong("media.imageMaxBytes", 32L << 20, 1024, 1L << 30),
                properties.getLong("media.videoMaxBytes", 200L << 20, 1024, 4L << 30),
                properties.getLong("media.imageMaxPixels", 64_000_000L, 100, 100_000_000L),
                properties.getLong("media.videoMaxPixels", 8_500_000L, 100, 100_000_000L),
                properties.getLong("media.storageMaxBytes", 5L << 30, 1L << 20, 1L << 44),
                properties.getLong("media.productMaxBytes", 400L << 20, 1024, 8L << 30),
                properties.getInt("media.processingTimeoutSeconds", 180, 1, 1800),
                properties.getInt("media.videoMaxSeconds", 180, 1, 3600),
                properties.getInt("media.detailMaxEdge", 4096, 512, 8192),
                properties.getInt("media.workers", 2, 1, 8));
    }

    private static Path resolveTool(AppProperties properties, String name, Path tools, String suffix) {
        String configured = System.getProperty("btl16." + name, properties.get("media." + name, ""));
        if (!configured.isBlank()) { return Path.of(configured).toAbsolutePath().normalize(); }
        List<Path> candidates = new ArrayList<>(List.of(tools.resolve(name + suffix)));
        if (System.getProperty("os.name").startsWith("Mac")) {
            candidates.add(Path.of("/opt/homebrew/bin", name));
            candidates.add(Path.of("/usr/local/bin", name));
        }
        String environment = System.getenv("PATH");
        if (environment != null) {
            for (String folder : environment.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
                if (!folder.isBlank()) { candidates.add(Path.of(folder).resolve(name + suffix)); }
            }
        }
        return candidates.stream().filter(path -> Files.isRegularFile(path) && Files.isExecutable(path))
                .findFirst().orElse(candidates.get(0)).toAbsolutePath().normalize();
    }
}
