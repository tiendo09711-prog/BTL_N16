package vn.ptit.btl16.server.session;

import vn.ptit.btl16.common.protocol.ErrorCode;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public final class SessionManager {
    private final ConcurrentHashMap<String, SessionRecord> byToken = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> tokenByConnection = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> tokenByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Long>> privateRoomGrantsByToken =
            new ConcurrentHashMap<>();
    private final ReentrantLock mutationLock = new ReentrantLock();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration resumeGrace;

    public SessionManager(Duration resumeGrace) {
        this.resumeGrace = resumeGrace == null ? Duration.ZERO : resumeGrace;
    }

    public UserSession createSession(
            long userId,
            String username,
            String connectionId,
            String remoteAddress) {
        Instant now = Instant.now();
        mutationLock.lock();
        try {
            if (tokenByConnection.containsKey(connectionId)) {
                throw new SessionException(
                        ErrorCode.CONNECTION_ALREADY_AUTHENTICATED,
                        "This connection already has a session");
            }

            String oldToken = tokenByUser.get(userId);
            if (oldToken != null) {
                SessionRecord old = byToken.get(oldToken);
                if (old != null && !isExpired(old, now)) {
                    throw new SessionException(
                            ErrorCode.ACCOUNT_ALREADY_ONLINE,
                            "This account already has an active or resumable session");
                }
                removeRecord(oldToken, old);
            }

            String token = newToken();
            SessionRecord record = new SessionRecord(
                    token,
                    userId,
                    username,
                    connectionId,
                    remoteAddress,
                    SessionStatus.ACTIVE,
                    now,
                    now,
                    null);
            byToken.put(token, record);
            tokenByConnection.put(connectionId, token);
            tokenByUser.put(userId, token);
            return record.snapshot();
        } finally {
            mutationLock.unlock();
        }
    }

    public UserSession resumeSession(String token, String newConnectionId, String remoteAddress) {
        Instant now = Instant.now();
        mutationLock.lock();
        try {
            if (tokenByConnection.containsKey(newConnectionId)) {
                throw new SessionException(
                        ErrorCode.CONNECTION_ALREADY_AUTHENTICATED,
                        "New connection is already authenticated");
            }
            SessionRecord record = byToken.get(token);
            if (record == null) {
                throw new SessionException(ErrorCode.SESSION_NOT_FOUND, "Session was not found");
            }
            if (isExpired(record, now)) {
                removeRecord(token, record);
                throw new SessionException(ErrorCode.SESSION_EXPIRED, "Session resume window has expired");
            }
            if (record.status == SessionStatus.ACTIVE) {
                throw new SessionException(
                        ErrorCode.ACCOUNT_ALREADY_ONLINE,
                        "Session is still attached to another connection");
            }

            record.connectionId = newConnectionId;
            record.remoteAddress = remoteAddress == null ? "" : remoteAddress;
            record.status = SessionStatus.ACTIVE;
            record.lastSeenAt = now;
            record.resumeDeadline = null;
            tokenByConnection.put(newConnectionId, token);
            tokenByUser.put(record.userId, token);
            return record.snapshot();
        } finally {
            mutationLock.unlock();
        }
    }

    public Optional<UserSession> findByConnection(String connectionId) {
        String token = tokenByConnection.get(connectionId);
        SessionRecord record = token == null ? null : byToken.get(token);
        return record == null ? Optional.empty() : Optional.of(record.snapshot());
    }

    public Optional<UserSession> findByToken(String token) {
        SessionRecord record = byToken.get(token);
        if (record == null || isExpired(record, Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(record.snapshot());
    }

    public Optional<String> activeConnectionIdForUser(long userId) {
        String token = tokenByUser.get(userId);
        SessionRecord record = token == null ? null : byToken.get(token);
        if (record == null || record.status != SessionStatus.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(record.connectionId);
    }

    public void touchByConnection(String connectionId) {
        String token = tokenByConnection.get(connectionId);
        SessionRecord record = token == null ? null : byToken.get(token);
        if (record != null) {
            record.lastSeenAt = Instant.now();
        }
    }

    public Optional<UserSession> detachByConnection(String connectionId) {
        Instant now = Instant.now();
        mutationLock.lock();
        try {
            String token = tokenByConnection.remove(connectionId);
            if (token == null) {
                return Optional.empty();
            }
            SessionRecord record = byToken.get(token);
            if (record == null) {
                return Optional.empty();
            }
            record.connectionId = "";
            record.status = SessionStatus.DETACHED;
            record.lastSeenAt = now;
            record.resumeDeadline = now.plus(resumeGrace);
            return Optional.of(record.snapshot());
        } finally {
            mutationLock.unlock();
        }
    }

    public Optional<UserSession> logoutByConnection(String connectionId) {
        mutationLock.lock();
        try {
            String token = tokenByConnection.remove(connectionId);
            if (token == null) {
                return Optional.empty();
            }
            SessionRecord record = byToken.remove(token);
            if (record != null) {
                privateRoomGrantsByToken.remove(token);
                tokenByUser.remove(record.userId, token);
                return Optional.of(record.snapshot());
            }
            return Optional.empty();
        } finally {
            mutationLock.unlock();
        }
    }

    public int cleanupExpired() {
        Instant now = Instant.now();
        List<String> expired = new ArrayList<>();
        for (SessionRecord record : byToken.values()) {
            if (isExpired(record, now)) {
                expired.add(record.sessionToken);
            }
        }
        int removed = 0;
        mutationLock.lock();
        try {
            for (String token : expired) {
                SessionRecord record = byToken.get(token);
                if (record != null && isExpired(record, now)) {
                    removeRecord(token, record);
                    removed++;
                }
            }
        } finally {
            mutationLock.unlock();
        }
        return removed;
    }

    public int activeCount() {
        int count = 0;
        for (SessionRecord record : byToken.values()) {
            if (record.status == SessionStatus.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    public int detachedCount() {
        int count = 0;
        for (SessionRecord record : byToken.values()) {
            if (record.status == SessionStatus.DETACHED) {
                count++;
            }
        }
        return count;
    }

    public int totalCount() {
        return byToken.size();
    }

    public void grantPrivateRoomAccess(UserSession session, long auctionId) {
        if (session == null || !byToken.containsKey(session.getSessionToken())) {
            throw new SessionException(ErrorCode.SESSION_NOT_FOUND, "Session was not found");
        }
        privateRoomGrantsByToken
                .computeIfAbsent(session.getSessionToken(), key -> ConcurrentHashMap.newKeySet())
                .add(auctionId);
    }

    public boolean hasPrivateRoomAccess(UserSession session, long auctionId) {
        if (session == null) {
            return false;
        }
        Set<Long> grants = privateRoomGrantsByToken.get(session.getSessionToken());
        return grants != null && grants.contains(auctionId);
    }

    public void revokePrivateRoomAccess(long userId, long auctionId) {
        String token = tokenByUser.get(userId);
        Set<Long> grants = token == null ? null : privateRoomGrantsByToken.get(token);
        if (grants != null) {
            grants.remove(auctionId);
        }
    }

    private boolean isExpired(SessionRecord record, Instant now) {
        return record != null
                && record.status == SessionStatus.DETACHED
                && record.resumeDeadline != null
                && !record.resumeDeadline.isAfter(now);
    }

    private void removeRecord(String token, SessionRecord record) {
        if (token == null || record == null) {
            return;
        }
        byToken.remove(token, record);
        privateRoomGrantsByToken.remove(token);
        if (record.connectionId != null && !record.connectionId.isBlank()) {
            tokenByConnection.remove(record.connectionId, token);
        }
        tokenByUser.remove(record.userId, token);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class SessionRecord {
        private final String sessionToken;
        private final long userId;
        private final String username;
        private volatile String connectionId;
        private volatile String remoteAddress;
        private volatile SessionStatus status;
        private final Instant createdAt;
        private volatile Instant lastSeenAt;
        private volatile Instant resumeDeadline;

        private SessionRecord(
                String sessionToken,
                long userId,
                String username,
                String connectionId,
                String remoteAddress,
                SessionStatus status,
                Instant createdAt,
                Instant lastSeenAt,
                Instant resumeDeadline) {
            this.sessionToken = sessionToken;
            this.userId = userId;
            this.username = username;
            this.connectionId = connectionId;
            this.remoteAddress = remoteAddress == null ? "" : remoteAddress;
            this.status = status;
            this.createdAt = createdAt;
            this.lastSeenAt = lastSeenAt;
            this.resumeDeadline = resumeDeadline;
        }

        private UserSession snapshot() {
            return new UserSession(
                    sessionToken,
                    userId,
                    username,
                    connectionId,
                    remoteAddress,
                    status,
                    createdAt,
                    lastSeenAt,
                    resumeDeadline);
        }
    }
}
