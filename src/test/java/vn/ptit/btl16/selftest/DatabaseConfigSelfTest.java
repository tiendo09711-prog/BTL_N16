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
            String previousRoot = System.getProperty("btl16.media.root");
            Path sentinel = configFile.getParent().resolve("media-production-sentinel").toAbsolutePath();
            try {
                System.setProperty("btl16.media.root", sentinel.toString());
                var isolated = vn.ptit.btl16.server.media.MediaConfig.load(true);
                TestSupport.check(!isolated.root().startsWith(sentinel), "isolated media ignores a production root override");
                TestSupport.equals(sentinel, vn.ptit.btl16.server.media.MediaConfig.load(false).root(), "production respects explicit root");
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
