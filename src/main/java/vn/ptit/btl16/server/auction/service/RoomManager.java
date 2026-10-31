package vn.ptit.btl16.server.auction.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomManager implements AutoCloseable {
    @FunctionalInterface
    public interface PresenceListener { void changed(long auctionId, String type, long userId, java.time.Instant time); }
    private PresenceListener presence = (room, type, user, time) -> { };
    @FunctionalInterface
    public interface OccupancyListener { void changed(long auctionId, int count, long version); }
    private OccupancyListener occupancy;
    private final long initialOccupancyVersion = System.currentTimeMillis() * 1000L;
    private final java.util.concurrent.ExecutorService occupancyNotifications = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "room-occupancy"); thread.setDaemon(true); return thread;
    });
    private final java.util.Map<Long, Long> occupancyVersions = new java.util.HashMap<>();
    public synchronized void setOccupancyListener(OccupancyListener listener) { occupancy = listener; }
    public synchronized long occupancyVersion(long auctionId) { return occupancyVersions.getOrDefault(auctionId, initialOccupancyVersion); }
    private void changed(long auctionId) {
        long version = occupancyVersion(auctionId) + 1;
        occupancyVersions.put(auctionId, version);
        int count = participantCount(auctionId);
        OccupancyListener listener = occupancy;
        if (listener != null) {
            occupancyNotifications.execute(() -> listener.changed(auctionId, count, version));
        }
    }

    @Override public synchronized void close() { occupancy = null; occupancyNotifications.shutdownNow(); }
    private void departed(long auctionId, long userId) {
        try { presence.changed(auctionId, "LEFT", userId, java.time.Instant.now()); }
        catch (RuntimeException error) { System.err.println("[ROOM PRESENCE] Cannot record departure: " + error.getMessage()); }
    }
    public synchronized void setPresenceListener(PresenceListener listener) { presence = listener; }
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, RoomMember>> membersByAuction =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Long>> auctionsByConnection = new ConcurrentHashMap<>();

    public synchronized void join(
            long auctionId,
            String connectionId,
            long userId,
            String username) {
        join(auctionId, connectionId, userId, username, Integer.MAX_VALUE);
    }

    public synchronized void join(long auctionId, String connectionId, long userId, String username, int maxParticipants) {
        join(auctionId, connectionId, userId, username, maxParticipants, false);
    }

    public synchronized void join(long auctionId, String connectionId, long userId, String username, int maxParticipants, boolean host) {
        var existing = membersByAuction.get(auctionId);
        boolean sameUser = membersSnapshot(auctionId).stream().anyMatch(member -> member.getUserId() == userId);
        if (!host && !sameUser && participantCount(auctionId) >= maxParticipants) {
            throw new AuctionException(vn.ptit.btl16.common.protocol.ErrorCode.ROOM_FULL,
                    "Phòng đã đủ số lượng người tham gia. Hãy chờ có người rời phòng.");
        }
        if (existing != null && existing.containsKey(connectionId) && existing.get(connectionId).getUserId() != userId) {
            leave(auctionId, connectionId);
        }
        boolean alreadyPresent = membersSnapshot(auctionId).stream().anyMatch(member -> member.getUserId() == userId);
        if (!alreadyPresent) { presence.changed(auctionId, "JOINED", userId, java.time.Instant.now()); }
        membersByAuction
                .computeIfAbsent(auctionId, key -> new ConcurrentHashMap<>())
                .put(connectionId, new RoomMember(connectionId, userId, username, host));
        auctionsByConnection
                .computeIfAbsent(connectionId, key -> ConcurrentHashMap.newKeySet())
                .add(auctionId);
        if (!alreadyPresent && !host) { changed(auctionId); }
    }

    public synchronized void leave(long auctionId, String connectionId) {
        int before = participantCount(auctionId);
        ConcurrentHashMap<String, RoomMember> room = membersByAuction.get(auctionId);
        if (room != null) {
            RoomMember member = room.get(connectionId);
            if (member != null && room.values().stream().filter(value -> value.getUserId() == member.getUserId()).count() == 1) {
                departed(auctionId, member.getUserId());
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
        if (participantCount(auctionId) != before) { changed(auctionId); }
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
            departed(auctionId, userId);
        }
        membersByAuction.remove(auctionId);
        changed(auctionId);
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
            departed(auctionId, userId);
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
        if (!removed.isEmpty()) { changed(auctionId); }
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
        var room = membersByAuction.get(auctionId);
        return room == null ? 0 : room.size();
    }

    public int participantCount(long auctionId) {
        return (int) membersSnapshot(auctionId).stream().filter(member -> !member.isHost())
                .map(RoomMember::getUserId).distinct().count();
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
