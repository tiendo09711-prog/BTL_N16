package vn.ptit.btl16.server.account.repository;

import vn.ptit.btl16.server.account.model.UserAccount;
import vn.ptit.btl16.server.account.security.PasswordHash;
import vn.ptit.btl16.server.account.security.PasswordHasher;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TestUserRepository implements UserRepository {
    private final ConcurrentHashMap<Long, UserAccount> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> idByUsername = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(0L);

    public static TestUserRepository withDemoUsers(PasswordHasher hasher) {
        TestUserRepository repository = new TestUserRepository();
        repository.createDemo("demo", "Demo User", "demo123", hasher);
        repository.createDemo("alice", "Alice", "alice123", hasher);
        repository.createDemo("bob", "Bob", "bob123", hasher);
        return repository;
    }

    private void createDemo(String username, String displayName, String plainPassword, PasswordHasher hasher) {
        char[] password = plainPassword.toCharArray();
        try {
            createUser(username, displayName, username + "@local.test", "", hasher.hash(password));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    @Override
    public Optional<UserAccount> findByUsername(String normalizedUsername) {
        Long id = idByUsername.get(normalize(normalizedUsername));
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<UserAccount> findById(long userId) {
        return Optional.ofNullable(byId.get(userId));
    }

    @Override
    public Optional<UserAccount> findByDisplayNameAndFriendCode(String displayName, String friendCode) {
        return byId.values().stream().filter(user -> user.getDisplayName().equalsIgnoreCase(displayName)
                && user.getFriendCode().equals(friendCode)).findFirst();
    }

    @Override
    public boolean existsByUsername(String normalizedUsername) {
        return idByUsername.containsKey(normalize(normalizedUsername));
    }

    @Override
    public synchronized long createUser(
            String normalizedUsername,
            String displayName,
            String email,
            String phone,
            PasswordHash passwordHash) {
        String username = normalize(normalizedUsername);
        if (idByUsername.containsKey(username)) {
            throw new RepositoryException("Username already exists", null);
        }
        long id = ids.incrementAndGet();
        int start = AccountTags.randomStart(); String friendCode = null;
        for (int attempt = 0; attempt < 10_000; attempt++) {
            String candidate = AccountTags.candidate(start, attempt);
            if (findByDisplayNameAndFriendCode(displayName, candidate).isEmpty()) { friendCode = candidate; break; }
        }
        if (friendCode == null) { throw new RepositoryException("No friend tags available", null); }
        Instant now = Instant.now();
        UserAccount user = new UserAccount(
                id,
                username,
                displayName,
                email,
                phone,
                passwordHash.getHashBase64(),
                passwordHash.getSaltBase64(),
                passwordHash.getIterations(),
                true,
                now,
                now,
                null, friendCode);
        byId.put(id, user);
        idByUsername.put(username, id);
        return id;
    }

    @Override
    public synchronized boolean updateProfile(long userId, String displayName, String email, String phone) {
        UserAccount old = byId.get(userId);
        if (old == null) {
            return false;
        }
        if (findByDisplayNameAndFriendCode(displayName, old.getFriendCode()).filter(user -> user.getUserId() != userId).isPresent()) {
            throw new vn.ptit.btl16.server.account.service.AccountException(vn.ptit.btl16.common.protocol.ErrorCode.VALIDATION_ERROR, "Tên hiển thị và mã kết bạn đã tồn tại");
        }
        byId.put(userId, copy(
                old,
                displayName,
                email,
                phone,
                old.getPasswordHash(),
                old.getPasswordSalt(),
                old.getPasswordIterations(),
                old.getLastLoginAt()));
        return true;
    }

    @Override
    public synchronized boolean updatePassword(long userId, PasswordHash passwordHash) {
        UserAccount old = byId.get(userId);
        if (old == null) {
            return false;
        }
        byId.put(userId, copy(
                old,
                old.getDisplayName(),
                old.getEmail(),
                old.getPhone(),
                passwordHash.getHashBase64(),
                passwordHash.getSaltBase64(),
                passwordHash.getIterations(),
                old.getLastLoginAt()));
        return true;
    }

    @Override
    public synchronized void recordSuccessfulLogin(long userId, String username, String remoteAddress) {
        UserAccount old = byId.get(userId);
        if (old != null) {
            byId.put(userId, copy(
                    old,
                    old.getDisplayName(),
                    old.getEmail(),
                    old.getPhone(),
                    old.getPasswordHash(),
                    old.getPasswordSalt(),
                    old.getPasswordIterations(),
                    Instant.now()));
        }
    }

    @Override
    public void recordFailedLogin(String username, String remoteAddress, String reason) {
        // The isolated test repository does not persist audit history.
    }

    private UserAccount copy(
            UserAccount old,
            String displayName,
            String email,
            String phone,
            String hash,
            String salt,
            int iterations,
            Instant lastLogin) {
        return new UserAccount(
                old.getUserId(),
                old.getUsername(),
                displayName,
                email,
                phone,
                hash,
                salt,
                iterations,
                old.isActive(),
                old.getCreatedAt(),
                Instant.now(),
                lastLogin, old.getFriendCode());
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
