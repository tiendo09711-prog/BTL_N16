package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.network.ServerMessagingService;

import java.util.Map;
import java.util.Set;

public final class AuctionBroadcastService {
    private final RoomManager rooms;
    private final ServerMessagingService messaging;

    public AuctionBroadcastService(RoomManager rooms, ServerMessagingService messaging) {
        this.rooms = rooms;
        this.messaging = messaging;
    }

    public int broadcastToRoom(long auctionId, MessageType type, Map<String, String> data) {
        Set<String> subscribers = rooms.subscribersSnapshot(auctionId);
        if (subscribers.isEmpty()) {
            return 0;
        }
        return messaging.sendEventToConnections(subscribers, type, data);
    }

    public int broadcastToRoom(
            long auctionId,
            MessageType type,
            long sequence,
            Map<String, String> data) {
        Set<String> subscribers = rooms.subscribersSnapshot(auctionId);
        if (subscribers.isEmpty()) {
            return 0;
        }
        return messaging.sendEventToConnections(subscribers, type, sequence, data);
    }

    public boolean notifyUser(long userId, MessageType type, Map<String, String> data) {
        return messaging.sendEventToUser(userId, type, data);
    }

    public int notifyConnections(
            Set<String> connectionIds,
            MessageType type,
            Map<String, String> data) {
        return messaging.sendEventToConnections(connectionIds, type, data);
    }

    public int broadcastAll(MessageType type, Map<String, String> data) {
        return messaging.broadcastAll(type, data);
    }
}
