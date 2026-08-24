package vn.ptit.btl16.server.account.model;

import java.time.Instant;
import java.util.Objects;

public final class UserAccount {
    private final long userId;
    private final String username;
    private final String displayName;
    private final String email;
    private final String phone;
    private final String passwordHash;
    private final String passwordSalt;
    private final int passwordIterations;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastLoginAt;

    public UserAccount(
            long userId,
            String username,
            String displayName,
            String email,
            String phone,
            String passwordHash,
            String passwordSalt,
            int passwordIterations,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant lastLoginAt) {
        this.userId = userId;
        this.username = Objects.requireNonNull(username, "username");
        this.displayName = displayName == null ? username : displayName;
        this.email = email == null ? "" : email;
        this.phone = phone == null ? "" : phone;
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.passwordSalt = Objects.requireNonNull(passwordSalt, "passwordSalt");
        this.passwordIterations = passwordIterations;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastLoginAt = lastLoginAt;
    }

    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public String getPasswordSalt() { return passwordSalt; }
    public int getPasswordIterations() { return passwordIterations; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
