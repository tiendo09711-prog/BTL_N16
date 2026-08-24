package vn.ptit.btl16.server;

/** Immutable status snapshot used by the optional server dashboard. */
public final class ServerStats {
    private final int port;
    private final int webSocketPort;
    private final int activeConnections;
    private final int tcpConnections;
    private final int webSocketConnections;
    private final int activeSessions;
    private final int detachedSessions;
    private final int rooms;
    private final int subscriptions;
    private final int openAuctions;
    private final int endedAuctions;
    private final long serverSequence;
    private final String repositoryName;

    public ServerStats(
            int port,
            int webSocketPort,
            int activeConnections,
            int tcpConnections,
            int webSocketConnections,
            int activeSessions,
            int detachedSessions,
            int rooms,
            int subscriptions,
            int openAuctions,
            int endedAuctions,
            long serverSequence,
            String repositoryName) {
        this.port = port;
        this.webSocketPort = webSocketPort;
        this.activeConnections = activeConnections;
        this.tcpConnections = tcpConnections;
        this.webSocketConnections = webSocketConnections;
        this.activeSessions = activeSessions;
        this.detachedSessions = detachedSessions;
        this.rooms = rooms;
        this.subscriptions = subscriptions;
        this.openAuctions = openAuctions;
        this.endedAuctions = endedAuctions;
        this.serverSequence = serverSequence;
        this.repositoryName = repositoryName;
    }

    public int getPort() { return port; }
    public int getWebSocketPort() { return webSocketPort; }
    public int getActiveConnections() { return activeConnections; }
    public int getTcpConnections() { return tcpConnections; }
    public int getWebSocketConnections() { return webSocketConnections; }
    public int getActiveSessions() { return activeSessions; }
    public int getDetachedSessions() { return detachedSessions; }
    public int getRooms() { return rooms; }
    public int getSubscriptions() { return subscriptions; }
    public int getOpenAuctions() { return openAuctions; }
    public int getEndedAuctions() { return endedAuctions; }
    public long getServerSequence() { return serverSequence; }
    public String getRepositoryName() { return repositoryName; }
}
