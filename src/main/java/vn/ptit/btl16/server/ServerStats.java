package vn.ptit.btl16.server;

/** Immutable status snapshot used by the optional server dashboard. */
public final class ServerStats {
    private final int port;
    private final int activeConnections;
    private final int activeSessions;
    private final int detachedSessions;
    private final int rooms;
    private final int subscriptions;
    private final int openAuctions;
    private final int endedAuctions;
    private final long serverSequence;
    private final String repositoryMode;

    public ServerStats(
            int port,
            int activeConnections,
            int activeSessions,
            int detachedSessions,
            int rooms,
            int subscriptions,
            int openAuctions,
            int endedAuctions,
            long serverSequence,
            String repositoryMode) {
        this.port = port;
        this.activeConnections = activeConnections;
        this.activeSessions = activeSessions;
        this.detachedSessions = detachedSessions;
        this.rooms = rooms;
        this.subscriptions = subscriptions;
        this.openAuctions = openAuctions;
        this.endedAuctions = endedAuctions;
        this.serverSequence = serverSequence;
        this.repositoryMode = repositoryMode;
    }

    public int getPort() { return port; }
    public int getActiveConnections() { return activeConnections; }
    public int getActiveSessions() { return activeSessions; }
    public int getDetachedSessions() { return detachedSessions; }
    public int getRooms() { return rooms; }
    public int getSubscriptions() { return subscriptions; }
    public int getOpenAuctions() { return openAuctions; }
    public int getEndedAuctions() { return endedAuctions; }
    public long getServerSequence() { return serverSequence; }
    public String getRepositoryMode() { return repositoryMode; }
}
