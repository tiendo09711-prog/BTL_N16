package vn.ptit.btl16.server.db;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.account.repository.JdbcUserRepository;
import vn.ptit.btl16.server.account.repository.UserRepository;
import vn.ptit.btl16.server.account.security.PasswordHasher;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;

/** Destructive reset intended only for classroom demo data. */
public final class DatabaseResetMain {
    private DatabaseResetMain() {
    }

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.loadDefault();
        JdbcConnectionFactory factory = new JdbcConnectionFactory(config);
        DatabaseSchema.dropDatabase(factory);
        DatabaseSchema.initialize(factory);
        UserRepository users = new JdbcUserRepository(factory);
        PasswordHasher hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        DemoDataSeeder.seed(config, factory, users, hasher);
        DatabaseSetupMain.printResult(config, true);
    }
}
