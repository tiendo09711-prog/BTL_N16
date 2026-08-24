package vn.ptit.btl16.common.config;

import java.util.Locale;
import java.util.Objects;

public final class ServerConfig {
    private final String bindAddress;
    private final int port;
    private final int backlog;
    private final int workerThreads;
    private final int maxFrameBytes;
    private final int resumeGraceSeconds;
    private final int cleanupIntervalSeconds;
    private final String repositoryMode;
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
    private final boolean demoAutoSeed;
    private final int demoShortAuctionSeconds;
    private final int demoMediumAuctionSeconds;
    private final int demoLongAuctionSeconds;

    private ServerConfig(AppProperties p) {
        this(
                p.get("server.bindAddress", "0.0.0.0"),
                p.getInt("server.port", 8888, 0, 65535),
                p.getInt("server.backlog", 100, 1, 10000),
                p.getInt("server.workerThreads", 64, 1, 10000),
                p.getInt("server.maxFrameBytes", 2_097_152, 1024, 64 * 1024 * 1024),
                p.getInt("session.resumeGraceSeconds", 120, 0, 86400),
                p.getInt("session.cleanupIntervalSeconds", 15, 1, 3600),
                p.get("repository.mode", "jdbc").toLowerCase(Locale.ROOT),
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
                p.getBoolean("demo.autoSeed", true),
                p.getInt("demo.shortAuctionSeconds", 90, 15, 86400),
                p.getInt("demo.mediumAuctionSeconds", 300, 15, 86400),
                p.getInt("demo.longAuctionSeconds", 480, 15, 86400));
    }

    private ServerConfig(
            String bindAddress,
            int port,
            int backlog,
            int workerThreads,
            int maxFrameBytes,
            int resumeGraceSeconds,
            int cleanupIntervalSeconds,
            String repositoryMode,
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
            boolean demoAutoSeed,
            int demoShortAuctionSeconds,
            int demoMediumAuctionSeconds,
            int demoLongAuctionSeconds) {
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        this.port = port;
        this.backlog = backlog;
        this.workerThreads = workerThreads;
        this.maxFrameBytes = maxFrameBytes;
        this.resumeGraceSeconds = resumeGraceSeconds;
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
        this.repositoryMode = Objects.requireNonNull(repositoryMode, "repositoryMode");
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
        this.demoAutoSeed = demoAutoSeed;
        this.demoShortAuctionSeconds = demoShortAuctionSeconds;
        this.demoMediumAuctionSeconds = demoMediumAuctionSeconds;
        this.demoLongAuctionSeconds = demoLongAuctionSeconds;

        if (!repositoryMode.equals("jdbc") && !repositoryMode.equals("memory")) {
            throw new IllegalArgumentException("repository.mode must be jdbc or memory");
        }
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
        return new ServerConfig(
                "127.0.0.1",
                port,
                100,
                64,
                2_097_152,
                2,
                1,
                "memory",
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
                true,
                shortAuctionSeconds,
                Math.max(shortAuctionSeconds + 10, 15),
                Math.max(shortAuctionSeconds + 20, 30));
    }

    private static String validateIdentifier(String value) {
        Objects.requireNonNull(value, "db.name");
        if (!value.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("db.name may contain letters, numbers and underscore only");
        }
        return value;
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

    public String getBindAddress() { return bindAddress; }
    public int getPort() { return port; }
    public int getBacklog() { return backlog; }
    public int getWorkerThreads() { return workerThreads; }
    public int getMaxFrameBytes() { return maxFrameBytes; }
    public int getResumeGraceSeconds() { return resumeGraceSeconds; }
    public int getCleanupIntervalSeconds() { return cleanupIntervalSeconds; }
    public String getRepositoryMode() { return repositoryMode; }
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
    public boolean isDemoAutoSeed() { return demoAutoSeed; }
    public int getDemoShortAuctionSeconds() { return demoShortAuctionSeconds; }
    public int getDemoMediumAuctionSeconds() { return demoMediumAuctionSeconds; }
    public int getDemoLongAuctionSeconds() { return demoLongAuctionSeconds; }
}
