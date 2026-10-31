package vn.ptit.btl16.server.fx;

import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.network.ConnectionState;
import vn.ptit.btl16.client.network.ConnectionStateListener;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;
import vn.ptit.btl16.server.ServerApplication;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class ServerRoomHistoryTransport implements ClientTransport {
    private final ServerApplication server;
    private final long room;
    private final Executor worker;
    private final BooleanSupplier available;

    ServerRoomHistoryTransport(ServerApplication server, long room, Executor worker, BooleanSupplier available) {
        this.server = server;
        this.room = room;
        this.worker = worker;
        this.available = available;
    }

    @Override public CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) {
        if (!isConnected() || !Long.toString(room).equals(data.get("auctionId"))
                || (type != MessageType.GET_ROOM_EVENTS && type != MessageType.GET_AUCTION_STATS)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Read-only local room history"));
        }
        return CompletableFuture.supplyAsync(() -> WireMessage.response(
                type == MessageType.GET_ROOM_EVENTS ? MessageType.ROOM_EVENTS_RESULT : MessageType.AUCTION_STATS_RESULT,
                null, 0, server.auctionSessions().history(room, type, data)), worker);
    }

    @Override public boolean isConnected() { return available.getAsBoolean(); }
    @Override public ConnectionState getState() { return isConnected() ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED; }
    @Override public String getEndpoint() { return "server-local-history"; }
    @Override public CompletableFuture<Void> connect() { return CompletableFuture.completedFuture(null); }
    @Override public CompletableFuture<WireMessage> ping() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Local history has no network connection"));
    }
    @Override public void setEndpoint(String endpoint) { throw new UnsupportedOperationException("Local history only"); }
    @Override public void addEventListener(Consumer<WireMessage> listener) { }
    @Override public void addStateListener(ConnectionStateListener listener) { }
    @Override public void disconnect() { }
    @Override public void close() { }
}
