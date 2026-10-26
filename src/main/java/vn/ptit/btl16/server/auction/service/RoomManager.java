package vn.ptit.btl16.server.auction.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomManager {
    @FunctionalInterface
    public interface PresenceListener { void changed(long auctionId, String type, long userId, java.time.Instant time); }
    private PresenceListener presence = (room, type, user, time) -> { };
    public synchronized void setPresenceListener(PresenceListener listener) { presence = listener; }
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, RoomMember>> membersByAuction =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Long>> auctionsByConnection = new ConcurrentHashMap<>();

    public synchronized void join(
            long auctionId,
            String connectionId,
            long userId,
            String username) {
        var existing = membersByAuction.get(auctionId);
        if (existing != null && existing.containsKey(connectionId) && existing.get(connectionId).getUserId() != userId) {
            leave(auctionId, connectionId);
        }
        boolean alreadyPresent = membersSnapshot(auctionId).stream().anyMatch(member -> member.getUserId() == userId);
        if (!alreadyPresent) { presence.changed(auctionId, "JOINED", userId, java.time.Instant.now()); }
        membersByAuction
                .computeIfAbsent(auctionId, key -> new ConcurrentHashMap<>())
                .put(connectionId, new RoomMember(connectionId, userId, username));
        auctionsByConnection
                .computeIfAbsent(connectionId, key -> ConcurrentHashMap.newKeySet())
                .add(auctionId);
    }

    public synchronized void leave(long auctionId, String connectionId) {
        ConcurrentHashMap<String, RoomMember> room = membersByAuction.get(auctionId);
        if (room != null) {
            RoomMember member = room.get(connectionId);
            if (member != null && room.values().stream().filter(value -> value.getUserId() == member.getUserId()).count() == 1) {
                presence.changed(auctionId, "LEFT", member.getUserId(), java.time.Instant.now());
            }
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

    public synchronized void removeConnection(String connectionId) {
        Set<Long> subscriptions = auctionsByConnection.get(connectionId);
        if (subscriptions == null) {
            return;
        }
        for (Long auctionId : Set.copyOf(subscriptions)) { leave(auctionId, connectionId); }
    }

    public synchronized void removeAuction(long auctionId) {
        ConcurrentHashMap<String, RoomMember> room = membersByAuction.get(auctionId);
        if (room == null) {
            return;
        }
        for (long userId : room.values().stream().mapToLong(RoomMember::getUserId).distinct().toArray()) {
            presence.changed(auctionId, "LEFT", userId, java.time.Instant.now());
        }
        membersByAuction.remove(auctionId);
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

    public boolean isMember(long auctionId, String connectionId, long userId) {
        ConcurrentHashMap<String, RoomMember> room = membersByAuction.get(auctionId);
        RoomMember member = room == null ? null : room.get(connectionId);
        return member != null && member.getUserId() == userId;
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

    public synchronized Set<String> kickUser(long auctionId, long userId) {
        ConcurrentHashMap<String, RoomMember> room = membersByAuction.get(auctionId);
        if (room == null) {
            return Collections.emptySet();
        }
        Set<String> removed = new HashSet<>();
        if (room.values().stream().anyMatch(member -> member.getUserId() == userId)) {
            presence.changed(auctionId, "LEFT", userId, java.time.Instant.now());
        }
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

    public java.util.List<RoomMember> membersSnapshot(long auctionId) {
        var room = membersByAuction.get(auctionId);
        return room == null ? java.util.List.of() : java.util.List.copyOf(room.values());
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
