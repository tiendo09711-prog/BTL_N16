package vn.ptit.btl16.server.auction.service;

public final class RoomMember {
    private final String connectionId;
    private final long userId;
    private final String username;
    private final boolean host;

    public RoomMember(String connectionId, long userId, String username) {
        this(connectionId, userId, username, false);
    }

    public RoomMember(String connectionId, long userId, String username, boolean host) {
        this.connectionId = connectionId;
        this.userId = userId;
        this.username = username == null ? "" : username;
        this.host = host;
    }

    public String getConnectionId() { return connectionId; }
    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public boolean isHost() { return host; }
}
