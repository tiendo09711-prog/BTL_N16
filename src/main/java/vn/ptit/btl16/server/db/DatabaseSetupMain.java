package vn.ptit.btl16.server.db;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;

public final class DatabaseSetupMain {
    private DatabaseSetupMain() {
    }

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.loadDefault();
        JdbcConnectionFactory factory = new JdbcConnectionFactory(config);
        DatabaseSchema.initialize(factory);
        printResult(config, false);
    }

    static void printResult(ServerConfig config, boolean reset) {
        System.out.println("====================================================");
        System.out.println(reset ? "DATABASE RESET COMPLETE" : "DATABASE SETUP COMPLETE");
        System.out.println("Database: " + config.getDbName());
        System.out.println("MySQL: " + config.getDbHost() + ':' + config.getDbPort());
        System.out.println("Schema ready. No demo accounts, products or auctions are created.");
        System.out.println("Register an account in the client to get started.");
        System.out.println("====================================================");
    }
}
