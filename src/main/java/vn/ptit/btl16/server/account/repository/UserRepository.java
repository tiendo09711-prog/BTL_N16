package vn.ptit.btl16.server.account.repository;

import vn.ptit.btl16.server.account.model.UserAccount;
import vn.ptit.btl16.server.account.security.PasswordHash;

import java.util.Optional;

public interface UserRepository {
    Optional<UserAccount> findByUsername(String normalizedUsername);

    Optional<UserAccount> findById(long userId);

    boolean existsByUsername(String normalizedUsername);

    long createUser(
            String normalizedUsername,
            String displayName,
            String email,
            String phone,
            PasswordHash passwordHash);

    boolean updateProfile(long userId, String displayName, String email, String phone);

    boolean updatePassword(long userId, PasswordHash passwordHash);

    void recordSuccessfulLogin(long userId, String username, String remoteAddress);

    void recordFailedLogin(String username, String remoteAddress, String reason);
}
