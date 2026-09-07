package vn.ptit.btl16.server.db;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;

public final class DatabaseResetMain {
    private DatabaseResetMain() {
    }

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.loadDefault();
        JdbcConnectionFactory factory = new JdbcConnectionFactory(config);
        DatabaseSchema.dropDatabase(factory);
        DatabaseSchema.initialize(factory);
        DatabaseSetupMain.printResult(config, true);
    }
}
