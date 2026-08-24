package vn.ptit.btl16.server.session;

import java.time.Instant;
import java.util.Objects;

public final class UserSession {
    private final String sessionToken;
    private final long userId;
    private final String username;
    private final String connectionId;
    private final String remoteAddress;
    private final SessionStatus status;
    private final Instant createdAt;
    private final Instant lastSeenAt;
    private final Instant resumeDeadline;

    public UserSession(
            String sessionToken,
            long userId,
            String username,
            String connectionId,
            String remoteAddress,
            SessionStatus status,
            Instant createdAt,
            Instant lastSeenAt,
            Instant resumeDeadline) {
        this.sessionToken = Objects.requireNonNull(sessionToken, "sessionToken");
        this.userId = userId;
        this.username = Objects.requireNonNull(username, "username");
        this.connectionId = connectionId == null ? "" : connectionId;
        this.remoteAddress = remoteAddress == null ? "" : remoteAddress;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        this.resumeDeadline = resumeDeadline;
    }

    public String getSessionToken() { return sessionToken; }
    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getConnectionId() { return connectionId; }
    public String getRemoteAddress() { return remoteAddress; }
    public SessionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getResumeDeadline() { return resumeDeadline; }
    public boolean isActive() { return status == SessionStatus.ACTIVE; }
}
