package vn.ptit.btl16.server.network;

import vn.ptit.btl16.common.protocol.WireMessage;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectionRegistry {
    private final ConcurrentHashMap<String, ClientConnection> connections = new ConcurrentHashMap<>();

    public void add(ClientConnection connection) {
        ClientConnection previous = connections.putIfAbsent(connection.getConnectionId(), connection);
        if (previous != null) {
            throw new IllegalStateException("Duplicate connectionId: " + connection.getConnectionId());
        }
    }

    public void remove(String connectionId) {
        connections.remove(connectionId);
    }

    public Optional<ClientConnection> find(String connectionId) {
        return Optional.ofNullable(connections.get(connectionId));
    }

    public List<ClientConnection> snapshot() {
        return List.copyOf(connections.values());
    }

    public int size() {
        return connections.size();
    }

    public boolean sendTo(String connectionId, WireMessage message) {
        ClientConnection connection = connections.get(connectionId);
        if (connection == null || connection.isClosed()) {
            return false;
        }
        try {
            connection.send(message);
            return true;
        } catch (IOException exception) {
            connection.close();
            return false;
        }
    }

    public void closeAll() {
        Collection<ClientConnection> copy = List.copyOf(connections.values());
        for (ClientConnection connection : copy) {
            connection.close();
        }
    }
}
