package vn.ptit.btl16.server.auction.service;

public final class RoomMember {
    private final String connectionId;
    private final long userId;
    private final String username;

    public RoomMember(String connectionId, long userId, String username) {
        this.connectionId = connectionId;
        this.userId = userId;
        this.username = username == null ? "" : username;
    }

    public String getConnectionId() { return connectionId; }
    public long getUserId() { return userId; }
    public String getUsername() { return username; }
}
