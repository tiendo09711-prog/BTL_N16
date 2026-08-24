package vn.ptit.btl16.common.config;

import java.util.Objects;

public final class ServerConfig {
    private final boolean tcpEnabled;
    private final String bindAddress;
    private final int port;
    private final int backlog;
    private final int workerThreads;
    private final int maxFrameBytes;
    private final boolean webSocketEnabled;
    private final String webSocketBindAddress;
    private final int webSocketPort;
    private final String webSocketPath;
    private final int resumeGraceSeconds;
    private final int cleanupIntervalSeconds;
    private final String dbDriver;
    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;
    private final boolean dbUseSsl;
    private final boolean dbAllowPublicKeyRetrieval;
    private final boolean dbAutoInitialize;
    private final int passwordIterations;
    private final int antiSnipingWindowSeconds;
    private final int extensionSeconds;
    private final int timerCheckMillis;
    private final int tickBroadcastMillis;
    private final int bidHistoryLimit;
    private final int closedVisibilitySeconds;
    private final int demoShortAuctionSeconds;
    private final int demoMediumAuctionSeconds;
    private final int demoLongAuctionSeconds;

    private ServerConfig(AppProperties p) {
        this(
                p.getBoolean("server.tcp.enabled", true),
                p.get("server.tcp.bindAddress", p.get("server.bindAddress", "0.0.0.0")),
                p.getInt("server.tcp.port", p.getInt("server.port", 8888, 0, 65535), 0, 65535),
                p.getInt("server.backlog", 100, 1, 10000),
                p.getInt("server.workerThreads", 64, 1, 10000),
                p.getInt("server.maxFrameBytes", 2_097_152, 1024, 64 * 1024 * 1024),
                p.getBoolean("server.websocket.enabled", true),
                p.get("server.websocket.bindAddress", "0.0.0.0"),
                p.getInt("server.websocket.port", 8890, 0, 65535),
                p.get("server.websocket.path", "/ws"),
                p.getInt("session.resumeGraceSeconds", 120, 0, 86400),
                p.getInt("session.cleanupIntervalSeconds", 15, 1, 3600),
                p.get("db.driver", "com.mysql.cj.jdbc.Driver"),
                p.get("db.host", "127.0.0.1"),
                p.getInt("db.port", 3306, 1, 65535),
                validateIdentifier(p.get("db.name", "btl_16")),
                p.get("db.user", "root"),
                p.get("db.password", ""),
                p.getBoolean("db.useSsl", false),
                p.getBoolean("db.allowPublicKeyRetrieval", true),
                p.getBoolean("db.autoInitialize", true),
                p.getInt("security.passwordIterations", 120000, 10000, 2_000_000),
                p.getInt("auction.antiSnipingWindowSeconds", 10, 0, 3600),
                p.getInt("auction.extensionSeconds", 10, 0, 3600),
                p.getInt("auction.timerCheckMillis", 200, 20, 60_000),
                p.getInt("auction.tickBroadcastMillis", 1000, 100, 60_000),
                p.getInt("auction.bidHistoryLimit", 50, 1, 1000),
                p.getInt("auction.closedVisibilitySeconds", 120, 0, 86400),
                p.getInt("demo.shortAuctionSeconds", 90, 15, 86400),
                p.getInt("demo.mediumAuctionSeconds", 300, 15, 86400),
                p.getInt("demo.longAuctionSeconds", 480, 15, 86400));
    }

    private ServerConfig(
            boolean tcpEnabled,
            String bindAddress,
            int port,
            int backlog,
            int workerThreads,
            int maxFrameBytes,
            boolean webSocketEnabled,
            String webSocketBindAddress,
            int webSocketPort,
            String webSocketPath,
            int resumeGraceSeconds,
            int cleanupIntervalSeconds,
            String dbDriver,
            String dbHost,
            int dbPort,
            String dbName,
            String dbUser,
            String dbPassword,
            boolean dbUseSsl,
            boolean dbAllowPublicKeyRetrieval,
            boolean dbAutoInitialize,
            int passwordIterations,
            int antiSnipingWindowSeconds,
            int extensionSeconds,
            int timerCheckMillis,
            int tickBroadcastMillis,
            int bidHistoryLimit,
            int closedVisibilitySeconds,
            int demoShortAuctionSeconds,
            int demoMediumAuctionSeconds,
            int demoLongAuctionSeconds) {
        this.tcpEnabled = tcpEnabled;
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        this.port = port;
        this.backlog = backlog;
        this.workerThreads = workerThreads;
        this.maxFrameBytes = maxFrameBytes;
        this.webSocketEnabled = webSocketEnabled;
        this.webSocketBindAddress = Objects.requireNonNull(
                webSocketBindAddress, "webSocketBindAddress");
        this.webSocketPort = webSocketPort;
        this.webSocketPath = normalizePath(webSocketPath);
        this.resumeGraceSeconds = resumeGraceSeconds;
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
        this.dbDriver = Objects.requireNonNull(dbDriver, "dbDriver");
        this.dbHost = Objects.requireNonNull(dbHost, "dbHost");
        this.dbPort = dbPort;
        this.dbName = Objects.requireNonNull(dbName, "dbName");
        this.dbUser = Objects.requireNonNull(dbUser, "dbUser");
        this.dbPassword = dbPassword == null ? "" : dbPassword;
        this.dbUseSsl = dbUseSsl;
        this.dbAllowPublicKeyRetrieval = dbAllowPublicKeyRetrieval;
        this.dbAutoInitialize = dbAutoInitialize;
        this.passwordIterations = passwordIterations;
        this.antiSnipingWindowSeconds = antiSnipingWindowSeconds;
        this.extensionSeconds = extensionSeconds;
        this.timerCheckMillis = timerCheckMillis;
        this.tickBroadcastMillis = tickBroadcastMillis;
        this.bidHistoryLimit = bidHistoryLimit;
        this.closedVisibilitySeconds = closedVisibilitySeconds;
        this.demoShortAuctionSeconds = demoShortAuctionSeconds;
        this.demoMediumAuctionSeconds = demoMediumAuctionSeconds;
        this.demoLongAuctionSeconds = demoLongAuctionSeconds;
    }

    public static ServerConfig loadDefault() {
        return new ServerConfig(AppProperties.load(
                "btl16.server.config",
                "config/server.properties"));
    }

    public static ServerConfig forTests(int port) {
        return forTests(port, 5, 10, 10);
    }

    public static ServerConfig forTests(
            int port,
            int shortAuctionSeconds,
            int antiSnipingWindowSeconds,
            int extensionSeconds) {
        return forTests(
                port,
                shortAuctionSeconds,
                antiSnipingWindowSeconds,
                extensionSeconds,
                120);
    }

    public static ServerConfig forTests(
            int port,
            int shortAuctionSeconds,
            int antiSnipingWindowSeconds,
            int extensionSeconds,
            int closedVisibilitySeconds) {
        return new ServerConfig(
                true,
                "127.0.0.1",
                port,
                100,
                64,
                2_097_152,
                false,
                "127.0.0.1",
                0,
                "/ws",
                2,
                1,
                "com.mysql.cj.jdbc.Driver",
                "127.0.0.1",
                3306,
                "btl_16",
                "root",
                "",
                false,
                true,
                false,
                30000,
                antiSnipingWindowSeconds,
                extensionSeconds,
                50,
                200,
                100,
                closedVisibilitySeconds,
                shortAuctionSeconds,
                Math.max(shortAuctionSeconds + 10, 15),
                Math.max(shortAuctionSeconds + 20, 30));
    }

    public static ServerConfig forTransportTests(int tcpPort, int webSocketPort) {
        return new ServerConfig(
                true,
                "127.0.0.1",
                tcpPort,
                100,
                64,
                2_097_152,
                true,
                "127.0.0.1",
                webSocketPort,
                "/ws",
                10,
                1,
                "com.mysql.cj.jdbc.Driver",
                "127.0.0.1",
                3306,
                "btl_16",
                "root",
                "",
                false,
                true,
                false,
                30000,
                10,
                10,
                50,
                200,
                100,
                120,
                90,
                300,
                480);
    }

    private static String validateIdentifier(String value) {
        Objects.requireNonNull(value, "db.name");
        if (!value.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("db.name may contain letters, numbers and underscore only");
        }
        return value;
    }

    private static String normalizePath(String value) {
        String normalized = value == null || value.isBlank() ? "/ws" : value.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    public String databaseJdbcUrl() {
        return "jdbc:mysql://" + dbHost + ':' + dbPort + '/' + dbName
                + "?useSSL=" + dbUseSsl
                + "&allowPublicKeyRetrieval=" + dbAllowPublicKeyRetrieval
                + "&serverTimezone=UTC&characterEncoding=UTF-8";
    }

    public String mysqlServerJdbcUrl() {
        return "jdbc:mysql://" + dbHost + ':' + dbPort + '/'
                + "?useSSL=" + dbUseSsl
                + "&allowPublicKeyRetrieval=" + dbAllowPublicKeyRetrieval
                + "&serverTimezone=UTC&characterEncoding=UTF-8";
    }

    public boolean isTcpEnabled() { return tcpEnabled; }
    public String getBindAddress() { return bindAddress; }
    public int getPort() { return port; }
    public int getBacklog() { return backlog; }
    public int getWorkerThreads() { return workerThreads; }
    public int getMaxFrameBytes() { return maxFrameBytes; }
    public boolean isWebSocketEnabled() { return webSocketEnabled; }
    public String getWebSocketBindAddress() { return webSocketBindAddress; }
    public int getWebSocketPort() { return webSocketPort; }
    public String getWebSocketPath() { return webSocketPath; }
    public int getResumeGraceSeconds() { return resumeGraceSeconds; }
    public int getCleanupIntervalSeconds() { return cleanupIntervalSeconds; }
    public String getDbDriver() { return dbDriver; }
    public String getDbHost() { return dbHost; }
    public int getDbPort() { return dbPort; }
    public String getDbName() { return dbName; }
    public String getDbUser() { return dbUser; }
    public String getDbPassword() { return dbPassword; }
    public boolean isDbAutoInitialize() { return dbAutoInitialize; }
    public int getPasswordIterations() { return passwordIterations; }
    public int getAntiSnipingWindowSeconds() { return antiSnipingWindowSeconds; }
    public int getExtensionSeconds() { return extensionSeconds; }
    public int getTimerCheckMillis() { return timerCheckMillis; }
    public int getTickBroadcastMillis() { return tickBroadcastMillis; }
    public int getBidHistoryLimit() { return bidHistoryLimit; }
    public int getClosedVisibilitySeconds() { return closedVisibilitySeconds; }
    public int getDemoShortAuctionSeconds() { return demoShortAuctionSeconds; }
    public int getDemoMediumAuctionSeconds() { return demoMediumAuctionSeconds; }
    public int getDemoLongAuctionSeconds() { return demoLongAuctionSeconds; }
}
