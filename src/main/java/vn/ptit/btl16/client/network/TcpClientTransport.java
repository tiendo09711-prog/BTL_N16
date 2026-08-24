package vn.ptit.btl16.client.network;

public final class TcpClientTransport extends NetworkClient {
    public TcpClientTransport(
            String host,
            int port,
            int maxFrameBytes,
            int connectTimeoutMillis,
            int requestTimeoutMillis) {
        super(maxFrameBytes, connectTimeoutMillis, requestTimeoutMillis);
        setEndpoint("tcp://" + host + ':' + port);
    }
}
