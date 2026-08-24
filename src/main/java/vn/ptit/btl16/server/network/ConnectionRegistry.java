package vn.ptit.btl16.server.network;

import vn.ptit.btl16.common.protocol.WireMessage;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectionRegistry {
    private final ConcurrentHashMap<String, ServerConnection> connections = new ConcurrentHashMap<>();

    public void add(ServerConnection connection) {
        ServerConnection previous = connections.putIfAbsent(connection.getConnectionId(), connection);
        if (previous != null) {
            throw new IllegalStateException("Duplicate connectionId: " + connection.getConnectionId());
        }
    }

    public void remove(String connectionId) {
        connections.remove(connectionId);
    }

    public Optional<ServerConnection> find(String connectionId) {
        return Optional.ofNullable(connections.get(connectionId));
    }

    public List<ServerConnection> snapshot() {
        return List.copyOf(connections.values());
    }

    public int size() {
        return connections.size();
    }

    public int countTransport(String transportName) {
        int count = 0;
        for (ServerConnection connection : connections.values()) {
            if (connection.getTransportName().equalsIgnoreCase(transportName)) {
                count++;
            }
        }
        return count;
    }

    public boolean sendTo(String connectionId, WireMessage message) {
        ServerConnection connection = connections.get(connectionId);
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
        Collection<ServerConnection> copy = List.copyOf(connections.values());
        for (ServerConnection connection : copy) {
            connection.close();
        }
    }
}
