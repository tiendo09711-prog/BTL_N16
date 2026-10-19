package vn.ptit.btl16.server.account.repository;

import vn.ptit.btl16.common.config.ServerConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class JdbcConnectionFactory {
    private final ServerConfig config;

    public JdbcConnectionFactory(ServerConfig config) {
        this.config = config;
        if (!"com.mysql.cj.jdbc.Driver".equals(config.getDbDriver())
                && !(config.isEmbeddedDatabase() && Boolean.getBoolean("btl16.test.allowEmbedded"))) {
            throw new IllegalStateException("Server chi luu du lieu trong MySQL/XAMPP. Hay bat MySQL va kiem tra config/server.properties; khong ho tro database local.");
        }
        try {
            Class.forName(config.getDbDriver());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "JDBC driver was not found: " + config.getDbDriver(),
                    exception);
        }
    }

    public Connection openDatabase() throws SQLException {
        return DriverManager.getConnection(
                config.databaseJdbcUrl(),
                config.getDbUser(),
                config.getDbPassword());
    }

    public Connection openMysqlServer() throws SQLException {
        return DriverManager.getConnection(
                config.mysqlServerJdbcUrl(),
                config.getDbUser(),
                config.getDbPassword());
    }

    public ServerConfig getConfig() {
        return config;
    }
}
