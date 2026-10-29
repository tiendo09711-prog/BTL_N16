package vn.ptit.btl16.client.model;

import vn.ptit.btl16.client.network.ConnectionState;
import vn.ptit.btl16.client.service.ApiResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Thread-safe client state. Swing renders snapshots of this model. */
public final class ClientAppModel {
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private String connectionDetail = "Chưa kết nối";
    private String sessionToken = "";
    private long userId;
    private String username = "";
    private String displayName = "";
    private String friendCode = "";
    private String email = "";
    private String phone = "";
    private String createdAt = "";
    private String lastLoginAt = "";
    private final Map<Long, ClientAuction> auctions = new LinkedHashMap<>();
    private final Set<Long> archivedAuctionIds = new HashSet<>();
    private final Set<Long> kickedAuctionIds = new HashSet<>();
    private final Map<Long, Instant> observedAt = new LinkedHashMap<>();
    private Instant lastListAt = Instant.MIN;
    private Instant lastServerAt = Instant.MIN;
    private List<ClientBid> currentBids = new ArrayList<>();
    private Long joinedAuctionId;
    private long serverClockOffsetMillis;

    public synchronized void applyIdentity(ApiResponse response) {
        userId = parseLong(response.get("userId"));
        username = response.get("username");
        displayName = response.get("displayName");
        friendCode = response.get("friendCode");
        email = response.get("email");
        phone = response.get("phone");
        createdAt = response.get("createdAt");
        lastLoginAt = response.get("lastLoginAt");
        String token = response.get("sessionToken");
        if (!token.isBlank()) {
            sessionToken = token;
        }
    }

    public synchronized void clearIdentity() {
        sessionToken = "";
        userId = 0L;
        username = "";
        displayName = "";
        friendCode = "";
        email = "";
        phone = "";
        createdAt = "";
        lastLoginAt = "";
        joinedAuctionId = null;
        currentBids = new ArrayList<>();
        archivedAuctionIds.clear();
        kickedAuctionIds.clear();
        auctions.clear();
        observedAt.clear();
        lastListAt = Instant.MIN;
        lastServerAt = Instant.MIN;
    }

    public synchronized void replaceAuctions(List<ClientAuction> values, Instant serverNow) {
        updateServerClock(serverNow);
        if (serverNow != null && serverNow.isBefore(lastListAt)) {
            return;
        }
        Map<Long, ClientAuction> next = new LinkedHashMap<>();
        for (ClientAuction value : values) {
            if (!archivedAuctionIds.contains(value.getAuctionId())) {
                ClientAuction previous = auctions.get(value.getAuctionId());
                next.put(value.getAuctionId(), canApply(previous, value) ? value : previous);
                observe(value.getAuctionId(), serverNow);
            }
        }
        for (ClientAuction previous : auctions.values()) {
            Instant observed = observedAt.get(previous.getAuctionId());
            boolean recentResult = previous.hasRecentResult(serverNow == null ? serverNow() : serverNow)
                    || (java.util.Objects.equals(joinedAuctionId, previous.getAuctionId()) && !previous.isOpen());
            if (!next.containsKey(previous.getAuctionId()) && (recentResult || (observed != null
                    && serverNow != null && observed.isAfter(serverNow)))) {
                next.put(previous.getAuctionId(), previous);
            }
        }
        auctions.clear();
        auctions.putAll(next);
        if (serverNow != null) {
            lastListAt = serverNow;
        }
    }

    public synchronized void applySnapshot(
            ClientAuction auction,
            List<ClientBid> bids,
            Instant serverNow,
            boolean markJoined) {
        updateServerClock(serverNow);
        if (archivedAuctionIds.contains(auction.getAuctionId())) {
            return;
        }
        ClientAuction previous = auctions.get(auction.getAuctionId());
        boolean accepted = canApply(previous, auction);
        if (accepted) {
            auctions.put(auction.getAuctionId(), auction);
            observe(auction.getAuctionId(), serverNow);
        }
        if (markJoined && !kickedAuctionIds.contains(auction.getAuctionId())) {
            if (joinedAuctionId == null || joinedAuctionId.longValue() != auction.getAuctionId()) {
                currentBids = new ArrayList<>();
            }
            joinedAuctionId = auction.getAuctionId();
        }
        if (joinedAuctionId != null && joinedAuctionId.longValue() == auction.getAuctionId()) {
            mergeBids(bids.stream().filter(bid -> bid.getAuctionId() == auction.getAuctionId()).toList());
        }
    }

    public synchronized void applyAuctionUpdate(
            ClientAuction auction,
            ClientBid bid,
            Instant serverNow) {
        updateServerClock(serverNow);
        if (archivedAuctionIds.contains(auction.getAuctionId())) {
            return;
        }
        ClientAuction previous = auctions.get(auction.getAuctionId());
        if (canApply(previous, auction)) {
            auctions.put(auction.getAuctionId(), auction);
            observe(auction.getAuctionId(), serverNow);
        }
        if (joinedAuctionId != null && joinedAuctionId.longValue() == auction.getAuctionId()) {
            mergeBids(List.of(bid));
        }
    }

    public synchronized void applyHistory(List<ClientBid> bids, Instant serverNow) {
        updateServerClock(serverNow);
        mergeBids(bids.stream().filter(bid -> joinedAuctionId != null
                && bid.getAuctionId() == joinedAuctionId.longValue()).toList());
    }

    public synchronized void applyTick(
            long auctionId,
            Instant endTime,
            String status,
            int watcherCount,
            Instant serverNow) {
        applyTick(auctionId, endTime, status, watcherCount, -1L, serverNow);
    }

    public synchronized void applyTick(long auctionId, Instant endTime, String status,
            int watcherCount, long version, Instant serverNow) {
        updateServerClock(serverNow);
        ClientAuction old = auctions.get(auctionId);
        if (old != null && old.isOpen() && "OPEN".equals(status) && endTime != null
                && !endTime.isBefore(old.getEndTime()) && version == old.getVersion()) {
            auctions.put(auctionId, old.withTiming(endTime, status, watcherCount));
            observe(auctionId, serverNow);
        }
    }

    private boolean canApply(ClientAuction previous, ClientAuction incoming) {
        return previous == null || (incoming.getVersion() >= previous.getVersion()
                && (previous.isOpen() || !incoming.isOpen()));
    }

    private void observe(long auctionId, Instant serverNow) {
        if (serverNow != null) {
            observedAt.merge(auctionId, serverNow, (previous, next) -> previous.isAfter(next) ? previous : next);
        }
    }

    private void mergeBids(List<ClientBid> incoming) {
        Map<Long, ClientBid> merged = new LinkedHashMap<>();
        for (ClientBid bid : currentBids) {
            merged.put(bid.getBidId(), bid);
        }
        for (ClientBid bid : incoming) {
            merged.put(bid.getBidId(), bid);
        }
        currentBids = new ArrayList<>(merged.values());
        currentBids.sort(Comparator.comparingLong(ClientBid::getServerSequence).reversed());
    }

    public synchronized void markKicked(long auctionId) {
        kickedAuctionIds.add(auctionId);
        if (joinedAuctionId != null && joinedAuctionId.longValue() == auctionId) {
            leaveJoinedAuction();
        }
    }

    public synchronized void leaveJoinedAuction() {
        joinedAuctionId = null;
        currentBids = new ArrayList<>();
    }

    public synchronized void removeAuction(long auctionId, Instant serverNow) {
        updateServerClock(serverNow);
        archivedAuctionIds.add(auctionId);
        auctions.remove(auctionId);
        if (joinedAuctionId != null && joinedAuctionId.longValue() == auctionId) {
            joinedAuctionId = null;
            currentBids = new ArrayList<>();
        }
    }

    public synchronized void setConnectionState(ConnectionState state, String detail) {
        this.connectionState = state;
        this.connectionDetail = detail == null ? "" : detail;
    }

    public synchronized void updateServerClock(Instant serverNow) {
        if (serverNow != null && !serverNow.isBefore(lastServerAt)) {
            lastServerAt = serverNow;
            serverClockOffsetMillis = serverNow.toEpochMilli() - System.currentTimeMillis();
        }
    }

    public synchronized Instant serverNow() {
        return Instant.ofEpochMilli(System.currentTimeMillis() + serverClockOffsetMillis);
    }

    public synchronized List<ClientAuction> auctionSnapshot() {
        return auctions.values().stream()
                .sorted(Comparator
                        .comparing((ClientAuction value) -> value.isOpen() ? 0 : 1)
                        .thenComparing(ClientAuction::getEndTime,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public synchronized ClientAuction joinedAuction() {
        return joinedAuctionId == null ? null : auctions.get(joinedAuctionId);
    }

    public synchronized List<ClientBid> currentBidSnapshot() {
        return List.copyOf(currentBids);
    }

    private long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    public synchronized ConnectionState getConnectionState() { return connectionState; }
    public synchronized String getConnectionDetail() { return connectionDetail; }
    public synchronized String getSessionToken() { return sessionToken; }
    public synchronized boolean hasSessionToken() { return !sessionToken.isBlank(); }
    public synchronized long getUserId() { return userId; }
    public synchronized String getUsername() { return username; }
    public synchronized String getDisplayName() { return displayName; }
    public synchronized String getFriendCode() { return friendCode; }
    public synchronized String getEmail() { return email; }
    public synchronized String getPhone() { return phone; }
    public synchronized String getCreatedAt() { return createdAt; }
    public synchronized String getLastLoginAt() { return lastLoginAt; }
    public synchronized Long getJoinedAuctionId() { return joinedAuctionId; }
}
