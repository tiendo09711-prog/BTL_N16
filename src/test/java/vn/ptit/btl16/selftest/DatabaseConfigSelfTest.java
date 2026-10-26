package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.ServerConfig;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DatabaseConfigSelfTest {
    private DatabaseConfigSelfTest() {
    }

    public static void run() throws Exception {
        String previous = System.getProperty("btl16.server.config");
        Path configFile = Files.createTempFile("btl16-db-config-", ".properties");
        try {
            System.setProperty("btl16.server.config", configFile.toString());
            Files.writeString(configFile, "db.host=127.0.0.2\ndb.port=3307\ndb.name=custom_auction\n");
            ServerConfig config = ServerConfig.loadDefault();
            TestSupport.equals(3307, config.getDbPort(), "read each machine's XAMPP port");
            TestSupport.check(config.databaseJdbcUrl().startsWith(
                    "jdbc:mysql://127.0.0.2:3307/custom_auction?"), "database URL uses configured endpoint");
            TestSupport.check(config.mysqlServerJdbcUrl().startsWith(
                    "jdbc:mysql://127.0.0.2:3307/?"), "setup/check uses the same configured endpoint");
            TestSupport.equals(60, config.getClosedVisibilitySeconds(), "closed room defaults to a one-minute countdown");
            Files.writeString(configFile, "auction.closedVisibilitySeconds=3600\n");
            TestSupport.equals(60, ServerConfig.loadDefault().getClosedVisibilitySeconds(), "old settings cannot retain closed rooms beyond one minute");
            Files.writeString(configFile, "auction.closedVisibilitySeconds=2\n");
            TestSupport.equals(2, ServerConfig.loadDefault().getClosedVisibilitySeconds(), "short retention remains available for isolated tests");
            Files.writeString(configFile, "db.driver=org.h2.Driver\n");
            TestSupport.check(ServerConfig.loadDefault().databaseJdbcUrl().contains("AUTO_COMPACT_FILL_RATE=0"), "test-only H2 avoids close-time compaction data loss");
            Files.writeString(configFile, "");
            String previousRoot = System.getProperty("btl16.media.root");
            Path sentinel = configFile.getParent().resolve("media-production-sentinel").toAbsolutePath();
            boolean existed = Files.exists(sentinel);
            try {
                System.setProperty("btl16.media.root", sentinel.toString());
                var isolated = vn.ptit.btl16.server.media.MediaConfig.load(true);
                TestSupport.equals(0, isolated.port(), "isolated media uses a dynamic port without a filesystem root");
                var production = vn.ptit.btl16.server.media.MediaConfig.load(false);
                TestSupport.equals(8891, production.port(), "production media configuration remains available without disk storage");
                TestSupport.equals(existed, Files.exists(sentinel), "media configuration ignores legacy disk root overrides");
            } finally {
                if (previousRoot == null) { System.clearProperty("btl16.media.root"); } else { System.setProperty("btl16.media.root", previousRoot); }
            }
            for (String invalidPort : new String[]{"0", "65536", "not-a-port"}) {
                Files.writeString(configFile, "db.port=" + invalidPort + '\n');
                boolean rejected = false;
                try {
                    ServerConfig.loadDefault();
                } catch (IllegalArgumentException expected) {
                    rejected = true;
                }
                TestSupport.check(rejected, "invalid DB port must fail: " + invalidPort);
            }
        } finally {
            if (previous == null) {
                System.clearProperty("btl16.server.config");
            } else {
                System.setProperty("btl16.server.config", previous);
            }
            Files.deleteIfExists(configFile);
        }
        System.out.println("[PASS] DatabaseConfigSelfTest");
    }
}
