package vn.ptit.btl16.server.db;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.account.repository.JdbcUserRepository;
import vn.ptit.btl16.server.account.repository.UserRepository;
import vn.ptit.btl16.server.account.security.PasswordHasher;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;

public final class DatabaseSetupMain {
    private DatabaseSetupMain() {
    }

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.loadDefault();
        JdbcConnectionFactory factory = new JdbcConnectionFactory(config);
        DatabaseSchema.initialize(factory);
        UserRepository users = new JdbcUserRepository(factory);
        PasswordHasher hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        DemoDataSeeder.seed(config, factory, users, hasher);
        printResult(config, false);
    }

    static void printResult(ServerConfig config, boolean reset) {
        System.out.println("====================================================");
        System.out.println(reset ? "DATABASE RESET COMPLETE" : "DATABASE SETUP COMPLETE");
        System.out.println("Database: " + config.getDbName());
        System.out.println("MySQL: " + config.getDbHost() + ':' + config.getDbPort());
        System.out.println("Accounts: demo/demo123, alice/alice123, bob/bob123");
        System.out.println("Three live auctions were seeded when none were active.");
        System.out.println("====================================================");
    }
}
