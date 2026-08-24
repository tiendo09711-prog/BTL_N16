package vn.ptit.btl16.server.network;

import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;
import vn.ptit.btl16.server.ServerSequence;
import vn.ptit.btl16.server.session.SessionManager;

import java.util.Collection;
import java.util.Map;

public final class ServerMessagingService {
    private final ConnectionRegistry connections;
    private final SessionManager sessions;
    private final ServerSequence sequence;

    public ServerMessagingService(
            ConnectionRegistry connections,
            SessionManager sessions,
            ServerSequence sequence) {
        this.connections = connections;
        this.sessions = sessions;
        this.sequence = sequence;
    }

    public long nextSequence() {
        return sequence.next();
    }

    public boolean sendEventToUser(long userId, MessageType type, Map<String, String> data) {
        long eventSequence = sequence.next();
        return sendEventToUser(userId, type, eventSequence, data);
    }

    public boolean sendEventToUser(
            long userId,
            MessageType type,
            long eventSequence,
            Map<String, String> data) {
        WireMessage event = WireMessage.event(type, eventSequence, data);
        return sessions.activeConnectionIdForUser(userId)
                .map(connectionId -> connections.sendTo(connectionId, event))
                .orElse(false);
    }

    public int sendEventToConnections(
            Collection<String> connectionIds,
            MessageType type,
            Map<String, String> data) {
        return sendEventToConnections(connectionIds, type, sequence.next(), data);
    }

    public int sendEventToConnections(
            Collection<String> connectionIds,
            MessageType type,
            long eventSequence,
            Map<String, String> data) {
        WireMessage event = WireMessage.event(type, eventSequence, data);
        int sent = 0;
        for (String connectionId : connectionIds) {
            if (connections.sendTo(connectionId, event)) {
                sent++;
            }
        }
        return sent;
    }

    public int broadcastAll(MessageType type, Map<String, String> data) {
        WireMessage event = WireMessage.event(type, sequence.next(), data);
        int sent = 0;
        for (ClientConnection connection : connections.snapshot()) {
            if (connections.sendTo(connection.getConnectionId(), event)) {
                sent++;
            }
        }
        return sent;
    }
}
