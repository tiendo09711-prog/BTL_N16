package vn.ptit.btl16.server.db;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public final class DatabaseCheckMain {
    private DatabaseCheckMain() {
    }

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.loadDefault();
        JdbcConnectionFactory factory = new JdbcConnectionFactory(config);
        try (Connection connection = factory.openMysqlServer()) {
            DatabaseMetaData metadata = connection.getMetaData();
            System.out.println("JDBC connected to " + config.getDbHost() + ':' + config.getDbPort());
            System.out.println("Database server: " + metadata.getDatabaseProductVersion());
            System.out.println("Driver: " + metadata.getDriverName() + ' ' + metadata.getDriverVersion());
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Cannot connect to XAMPP MySQL at " + config.getDbHost() + ':' + config.getDbPort()
                            + ". Start MySQL in XAMPP and check db.host, db.port, db.user and db.password"
                            + " in config/server.properties (or btl16.server.config).", exception);
        }
    }
}
