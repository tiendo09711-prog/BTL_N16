package vn.ptit.btl16.server.auction.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomManager {
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, RoomMember>> membersByAuction =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Long>> auctionsByConnection = new ConcurrentHashMap<>();

    public void join(
            long auctionId,
            String connectionId,
            long userId,
            String username) {
        membersByAuction
                .computeIfAbsent(auctionId, key -> new ConcurrentHashMap<>())
                .put(connectionId, new RoomMember(connectionId, userId, username));
        auctionsByConnection
                .computeIfAbsent(connectionId, key -> ConcurrentHashMap.newKeySet())
                .add(auctionId);
    }

    public void leave(long auctionId, String connectionId) {
        ConcurrentHashMap<String, RoomMember> room = membersByAuction.get(auctionId);
        if (room != null) {
            room.remove(connectionId);
            if (room.isEmpty()) {
                membersByAuction.remove(auctionId, room);
            }
        }
        Set<Long> subscriptions = auctionsByConnection.get(connectionId);
        if (subscriptions != null) {
            subscriptions.remove(auctionId);
            if (subscriptions.isEmpty()) {
                auctionsByConnection.remove(connectionId, subscriptions);
            }
        }
    }

    public void removeConnection(String connectionId) {
        Set<Long> subscriptions = auctionsByConnection.remove(connectionId);
        if (subscriptions == null) {
            return;
        }
        for (Long auctionId : subscriptions) {
            ConcurrentHashMap<String, RoomMember> room = membersByAuction.get(auctionId);
            if (room != null) {
                room.remove(connectionId);
                if (room.isEmpty()) {
                    membersByAuction.remove(auctionId, room);
                }
            }
        }
    }

    public void removeAuction(long auctionId) {
        ConcurrentHashMap<String, RoomMember> room = membersByAuction.remove(auctionId);
        if (room == null) {
            return;
        }
        for (String connectionId : room.keySet()) {
            Set<Long> subscriptions = auctionsByConnection.get(connectionId);
            if (subscriptions != null) {
                subscriptions.remove(auctionId);
                if (subscriptions.isEmpty()) {
                    auctionsByConnection.remove(connectionId, subscriptions);
                }
            }
        }
    }

    public boolean isMember(long auctionId, String connectionId) {
        ConcurrentHashMap<String, RoomMember> room = membersByAuction.get(auctionId);
        return room != null && room.containsKey(connectionId);
    }

    public Optional<RoomMember> findByUsername(long auctionId, String username) {
        ConcurrentHashMap<String, RoomMember> room = membersByAuction.get(auctionId);
        if (room == null) {
            return Optional.empty();
        }
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return room.values().stream()
                .filter(value -> value.getUsername().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    public Set<String> kickUser(long auctionId, long userId) {
        ConcurrentHashMap<String, RoomMember> room = membersByAuction.get(auctionId);
        if (room == null) {
            return Collections.emptySet();
        }
        Set<String> removed = new HashSet<>();
        for (RoomMember member : room.values()) {
            if (member.getUserId() == userId && room.remove(member.getConnectionId(), member)) {
                removed.add(member.getConnectionId());
                Set<Long> subscriptions = auctionsByConnection.get(member.getConnectionId());
                if (subscriptions != null) {
                    subscriptions.remove(auctionId);
                    if (subscriptions.isEmpty()) {
                        auctionsByConnection.remove(member.getConnectionId(), subscriptions);
                    }
                }
            }
        }
        if (room.isEmpty()) {
            membersByAuction.remove(auctionId, room);
        }
        return Collections.unmodifiableSet(removed);
    }

    public Set<String> subscribersSnapshot(long auctionId) {
        ConcurrentHashMap<String, RoomMember> room = membersByAuction.get(auctionId);
        if (room == null || room.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(room.keySet()));
    }

    public int subscriberCount(long auctionId) {
        ConcurrentHashMap<String, RoomMember> room = membersByAuction.get(auctionId);
        return room == null ? 0 : room.size();
    }

    public int roomCount() {
        return membersByAuction.size();
    }

    public int totalSubscriptions() {
        int count = 0;
        for (ConcurrentHashMap<String, RoomMember> room : membersByAuction.values()) {
            count += room.size();
        }
        return count;
    }
}
