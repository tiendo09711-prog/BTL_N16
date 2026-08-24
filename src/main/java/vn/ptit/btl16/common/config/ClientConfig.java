package vn.ptit.btl16.common.config;

public final class ClientConfig {
    private final String transport;
    private final String webSocketUrl;
    private final String serverHost;
    private final int serverPort;
    private final int connectTimeoutMillis;
    private final int requestTimeoutMillis;
    private final int maxFrameBytes;
    private final int heartbeatIntervalMillis;
    private final int reconnectInitialDelayMillis;
    private final int reconnectMaxDelayMillis;
    private final int reconnectMaxAttempts;

    private ClientConfig(AppProperties p) {
        this.transport = stringOverride(
                "btl16.client.transport",
                "BTL16_CLIENT_TRANSPORT",
                p.get("client.transport", "websocket")).toLowerCase();
        this.webSocketUrl = stringOverride(
                "btl16.client.url",
                "BTL16_CLIENT_URL",
                p.get("client.websocketUrl", "ws://127.0.0.1:8890/ws"));
        this.serverHost = stringOverride(
                "btl16.client.host",
                "BTL16_CLIENT_HOST",
                p.get("client.serverHost", "127.0.0.1"));
        this.serverPort = intOverride(
                "btl16.client.port",
                "BTL16_CLIENT_PORT",
                p.getInt("client.serverPort", 8888, 1, 65535),
                1,
                65535);
        this.connectTimeoutMillis = p.getInt("client.connectTimeoutMillis", 5000, 100, 120000);
        this.requestTimeoutMillis = p.getInt("client.requestTimeoutMillis", 8000, 100, 120000);
        this.maxFrameBytes = p.getInt("client.maxFrameBytes", 2_097_152, 1024, 64 * 1024 * 1024);
        this.heartbeatIntervalMillis = p.getInt("client.heartbeatIntervalMillis", 5000, 500, 120000);
        this.reconnectInitialDelayMillis = p.getInt("client.reconnectInitialDelayMillis", 1000, 100, 60000);
        this.reconnectMaxDelayMillis = p.getInt("client.reconnectMaxDelayMillis", 5000, 100, 120000);
        this.reconnectMaxAttempts = p.getInt("client.reconnectMaxAttempts", 30, 1, 10000);
    }

    private static String stringOverride(
            String systemProperty,
            String environmentVariable,
            String fallback) {
        String value = System.getProperty(systemProperty);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int intOverride(
            String systemProperty,
            String environmentVariable,
            int fallback,
            int min,
            int max) {
        String value = System.getProperty(systemProperty);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(
                        systemProperty + " must be in [" + min + ", " + max + "]");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    systemProperty + " must be an integer: " + value,
                    exception);
        }
    }

    public static ClientConfig loadDefault() {
        return new ClientConfig(AppProperties.load(
                "btl16.client.config",
                "config/client.properties"));
    }

    public String getServerHost() { return serverHost; }
    public String getTransport() { return transport; }
    public String getWebSocketUrl() { return webSocketUrl; }
    public int getServerPort() { return serverPort; }
    public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
    public int getRequestTimeoutMillis() { return requestTimeoutMillis; }
    public int getMaxFrameBytes() { return maxFrameBytes; }
    public int getHeartbeatIntervalMillis() { return heartbeatIntervalMillis; }
    public int getReconnectInitialDelayMillis() { return reconnectInitialDelayMillis; }
    public int getReconnectMaxDelayMillis() { return reconnectMaxDelayMillis; }
    public int getReconnectMaxAttempts() { return reconnectMaxAttempts; }
}
