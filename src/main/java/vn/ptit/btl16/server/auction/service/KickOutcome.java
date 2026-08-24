package vn.ptit.btl16.server.auction.service;

import java.util.Set;

public final class KickOutcome {
    private final long userId;
    private final String username;
    private final Set<String> connectionIds;

    public KickOutcome(long userId, String username, Set<String> connectionIds) {
        this.userId = userId;
        this.username = username;
        this.connectionIds = Set.copyOf(connectionIds);
    }

    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public Set<String> getConnectionIds() { return connectionIds; }
}
