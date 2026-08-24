package vn.ptit.btl16.server.account.service;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.validation.InputValidation;
import vn.ptit.btl16.server.account.model.UserAccount;
import vn.ptit.btl16.server.account.repository.UserRepository;
import vn.ptit.btl16.server.account.security.PasswordHash;
import vn.ptit.btl16.server.account.security.PasswordHasher;
import vn.ptit.btl16.server.session.SessionManager;
import vn.ptit.btl16.server.session.UserSession;

import java.util.Arrays;

public final class AccountService {
    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionManager sessions;

    public AccountService(
            UserRepository users,
            PasswordHasher passwordHasher,
            SessionManager sessions) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.sessions = sessions;
    }

    public UserAccount register(
            String usernameRaw,
            char[] password,
            String displayNameRaw,
            String emailRaw,
            String phoneRaw) {
        String username = InputValidation.normalizeUsername(usernameRaw);
        InputValidation.requireValidUsername(username);
        InputValidation.requireValidPassword(password);
        String displayName = InputValidation.cleanDisplayName(displayNameRaw);
        String email = InputValidation.cleanEmail(emailRaw);
        String phone = InputValidation.cleanPhone(phoneRaw);

        if (users.existsByUsername(username)) {
            throw new AccountException(ErrorCode.USERNAME_ALREADY_EXISTS, "Username already exists");
        }
        PasswordHash passwordHash = passwordHasher.hash(password);
        long userId = users.createUser(username, displayName, email, phone, passwordHash);
        return users.findById(userId)
                .orElseThrow(() -> new AccountException(
                        ErrorCode.PROFILE_NOT_FOUND,
                        "User was created but cannot be loaded"));
    }

    public LoginResult login(
            String usernameRaw,
            char[] password,
            String connectionId,
            String remoteAddress) {
        String username = InputValidation.normalizeUsername(usernameRaw);
        InputValidation.requireValidUsername(username);
        InputValidation.requireValidPassword(password);

        UserAccount user = users.findByUsername(username).orElse(null);
        if (user == null) {
            users.recordFailedLogin(username, remoteAddress, "USER_NOT_FOUND");
            throw new AccountException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
        }
        if (!user.isActive()) {
            users.recordFailedLogin(username, remoteAddress, "ACCOUNT_DISABLED");
            throw new AccountException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
        }
        if (!passwordHasher.verify(
                password,
                user.getPasswordSalt(),
                user.getPasswordHash(),
                user.getPasswordIterations())) {
            users.recordFailedLogin(username, remoteAddress, "WRONG_PASSWORD");
            throw new AccountException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
        }

        UserSession session = sessions.createSession(
                user.getUserId(),
                user.getUsername(),
                connectionId,
                remoteAddress);
        try {
            users.recordSuccessfulLogin(user.getUserId(), user.getUsername(), remoteAddress);
            UserAccount refreshed = users.findById(user.getUserId()).orElse(user);
            return new LoginResult(refreshed, session);
        } catch (RuntimeException exception) {
            sessions.logoutByConnection(connectionId);
            throw exception;
        }
    }

    public LoginResult resume(String sessionToken, String connectionId, String remoteAddress) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new AccountException(ErrorCode.SESSION_NOT_FOUND, "sessionToken is missing");
        }
        UserSession session = sessions.resumeSession(sessionToken, connectionId, remoteAddress);
        UserAccount user = users.findById(session.getUserId())
                .orElseThrow(() -> new AccountException(
                        ErrorCode.PROFILE_NOT_FOUND,
                        "Session user no longer exists"));
        return new LoginResult(user, session);
    }

    public void logout(String connectionId) {
        sessions.logoutByConnection(connectionId);
    }

    public UserAccount getProfile(long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new AccountException(
                        ErrorCode.PROFILE_NOT_FOUND,
                        "User profile was not found"));
    }

    public UserAccount updateProfile(
            long userId,
            String displayNameRaw,
            String emailRaw,
            String phoneRaw) {
        String displayName = InputValidation.cleanDisplayName(displayNameRaw);
        String email = InputValidation.cleanEmail(emailRaw);
        String phone = InputValidation.cleanPhone(phoneRaw);
        if (!users.updateProfile(userId, displayName, email, phone)) {
            throw new AccountException(ErrorCode.PROFILE_NOT_FOUND, "User profile was not found");
        }
        return getProfile(userId);
    }

    public void changePassword(long userId, char[] currentPassword, char[] newPassword) {
        InputValidation.requireValidPassword(currentPassword);
        InputValidation.requireValidPassword(newPassword);
        if (Arrays.equals(currentPassword, newPassword)) {
            throw new AccountException(
                    ErrorCode.VALIDATION_ERROR,
                    "New password must differ from current password");
        }
        UserAccount user = getProfile(userId);
        if (!passwordHasher.verify(
                currentPassword,
                user.getPasswordSalt(),
                user.getPasswordHash(),
                user.getPasswordIterations())) {
            throw new AccountException(
                    ErrorCode.CURRENT_PASSWORD_WRONG,
                    "Current password is incorrect");
        }
        PasswordHash newHash = passwordHasher.hash(newPassword);
        if (!users.updatePassword(userId, newHash)) {
            throw new AccountException(ErrorCode.PROFILE_NOT_FOUND, "User profile was not found");
        }
    }
}
