package vn.ptit.btl16.server.account.repository;

import vn.ptit.btl16.server.account.model.UserAccount;
import vn.ptit.btl16.server.account.security.PasswordHash;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;

public final class JdbcUserRepository implements UserRepository {
    private static final String SELECT_COLUMNS =
            "user_id, username, display_name, email, phone, password_hash, password_salt, "
                    + "password_iterations, active, created_at, updated_at, last_login_at";

    private static final String FIND_BY_USERNAME =
            "SELECT " + SELECT_COLUMNS + " FROM users WHERE username = ?";
    private static final String FIND_BY_ID =
            "SELECT " + SELECT_COLUMNS + " FROM users WHERE user_id = ?";
    private static final String EXISTS_BY_USERNAME =
            "SELECT 1 FROM users WHERE username = ? LIMIT 1";
    private static final String INSERT_USER =
            "INSERT INTO users(username, display_name, email, phone, password_hash, password_salt, "
                    + "password_iterations, active) VALUES(?, ?, ?, ?, ?, ?, ?, TRUE)";
    private static final String UPDATE_PROFILE =
            "UPDATE users SET display_name = ?, email = ?, phone = ?, updated_at = CURRENT_TIMESTAMP "
                    + "WHERE user_id = ?";
    private static final String UPDATE_PASSWORD =
            "UPDATE users SET password_hash = ?, password_salt = ?, password_iterations = ?, "
                    + "updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
    private static final String UPDATE_LAST_LOGIN =
            "UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE user_id = ?";
    private static final String INSERT_LOGIN_HISTORY =
            "INSERT INTO login_history(user_id, username_attempt, remote_address, success, failure_reason) "
                    + "VALUES(?, ?, ?, ?, ?)";

    private final JdbcConnectionFactory connectionFactory;

    public JdbcUserRepository(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<UserAccount> findByUsername(String normalizedUsername) {
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USERNAME)) {
            statement.setString(1, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapUser(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Cannot find user by username", exception);
        }
    }

    @Override
    public Optional<UserAccount> findById(long userId) {
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapUser(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Cannot find user by id", exception);
        }
    }

    @Override
    public boolean existsByUsername(String normalizedUsername) {
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_USERNAME)) {
            statement.setString(1, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Cannot check username", exception);
        }
    }

    @Override
    public long createUser(
            String normalizedUsername,
            String displayName,
            String email,
            String phone,
            PasswordHash passwordHash) {
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(
                     INSERT_USER,
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, normalizedUsername);
            statement.setString(2, displayName);
            setNullable(statement, 3, email);
            setNullable(statement, 4, phone);
            statement.setString(5, passwordHash.getHashBase64());
            statement.setString(6, passwordHash.getSaltBase64());
            statement.setInt(7, passwordHash.getIterations());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new RepositoryException("Database did not return user_id", null);
                }
                return keys.getLong(1);
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Cannot create user", exception);
        }
    }

    @Override
    public boolean updateProfile(long userId, String displayName, String email, String phone) {
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(UPDATE_PROFILE)) {
            statement.setString(1, displayName);
            setNullable(statement, 2, email);
            setNullable(statement, 3, phone);
            statement.setLong(4, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new RepositoryException("Cannot update profile", exception);
        }
    }

    @Override
    public boolean updatePassword(long userId, PasswordHash passwordHash) {
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement statement = connection.prepareStatement(UPDATE_PASSWORD)) {
            statement.setString(1, passwordHash.getHashBase64());
            statement.setString(2, passwordHash.getSaltBase64());
            statement.setInt(3, passwordHash.getIterations());
            statement.setLong(4, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new RepositoryException("Cannot update password", exception);
        }
    }

    @Override
    public void recordSuccessfulLogin(long userId, String username, String remoteAddress) {
        try (Connection connection = connectionFactory.openDatabase()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement(UPDATE_LAST_LOGIN);
                 PreparedStatement history = connection.prepareStatement(INSERT_LOGIN_HISTORY)) {
                update.setLong(1, userId);
                update.executeUpdate();

                history.setLong(1, userId);
                history.setString(2, username);
                history.setString(3, remoteAddress);
                history.setBoolean(4, true);
                history.setNull(5, Types.VARCHAR);
                history.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Cannot record successful login", exception);
        }
    }

    @Override
    public void recordFailedLogin(String username, String remoteAddress, String reason) {
        try (Connection connection = connectionFactory.openDatabase();
             PreparedStatement history = connection.prepareStatement(INSERT_LOGIN_HISTORY)) {
            history.setNull(1, Types.BIGINT);
            history.setString(2, username);
            history.setString(3, remoteAddress);
            history.setBoolean(4, false);
            history.setString(5, reason);
            history.executeUpdate();
        } catch (SQLException exception) {
            throw new RepositoryException("Cannot record failed login", exception);
        }
    }

    private UserAccount mapUser(ResultSet rs) throws SQLException {
        return new UserAccount(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("display_name"),
                emptyIfNull(rs.getString("email")),
                emptyIfNull(rs.getString("phone")),
                rs.getString("password_hash"),
                rs.getString("password_salt"),
                rs.getInt("password_iterations"),
                rs.getBoolean("active"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                toInstant(rs.getTimestamp("last_login_at")));
    }

    private void setNullable(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
