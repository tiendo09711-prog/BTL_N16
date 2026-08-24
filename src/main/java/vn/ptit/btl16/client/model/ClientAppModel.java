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
    private String connectionDetail = "Chua ket noi";
    private String sessionToken = "";
    private long userId;
    private String username = "";
    private String displayName = "";
    private String email = "";
    private String phone = "";
    private String createdAt = "";
    private String lastLoginAt = "";
    private final Map<Long, ClientAuction> auctions = new LinkedHashMap<>();
    private final Set<Long> archivedAuctionIds = new HashSet<>();
    private List<ClientBid> currentBids = new ArrayList<>();
    private Long joinedAuctionId;
    private long serverClockOffsetMillis;

    public synchronized void applyIdentity(ApiResponse response) {
        userId = parseLong(response.get("userId"));
        username = response.get("username");
        displayName = response.get("displayName");
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
        email = "";
        phone = "";
        createdAt = "";
        lastLoginAt = "";
        joinedAuctionId = null;
        currentBids = new ArrayList<>();
        archivedAuctionIds.clear();
    }

    public synchronized void replaceAuctions(List<ClientAuction> values, Instant serverNow) {
        updateServerClock(serverNow);
        auctions.clear();
        for (ClientAuction value : values) {
            if (!archivedAuctionIds.contains(value.getAuctionId())) {
                auctions.put(value.getAuctionId(), value);
            }
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
        auctions.put(auction.getAuctionId(), auction);
        if (markJoined) {
            joinedAuctionId = auction.getAuctionId();
        }
        if (joinedAuctionId != null && joinedAuctionId.longValue() == auction.getAuctionId()) {
            currentBids = new ArrayList<>(bids);
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
        if (previous == null || auction.getVersion() >= previous.getVersion()) {
            auctions.put(auction.getAuctionId(), auction);
        }
        if (joinedAuctionId != null && joinedAuctionId.longValue() == auction.getAuctionId()) {
            List<ClientBid> next = new ArrayList<>(currentBids);
            boolean exists = next.stream().anyMatch(value -> value.getBidId() == bid.getBidId());
            if (!exists) {
                next.add(0, bid);
            }
            currentBids = next;
        }
    }

    public synchronized void applyHistory(List<ClientBid> bids, Instant serverNow) {
        updateServerClock(serverNow);
        currentBids = new ArrayList<>(bids);
    }

    public synchronized void applyTick(
            long auctionId,
            Instant endTime,
            String status,
            int watcherCount,
            Instant serverNow) {
        updateServerClock(serverNow);
        ClientAuction old = auctions.get(auctionId);
        if (old != null) {
            auctions.put(auctionId, old.withTiming(endTime, status, watcherCount));
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
        if (serverNow != null) {
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
    public synchronized String getEmail() { return email; }
    public synchronized String getPhone() { return phone; }
    public synchronized String getCreatedAt() { return createdAt; }
    public synchronized String getLastLoginAt() { return lastLoginAt; }
    public synchronized Long getJoinedAuctionId() { return joinedAuctionId; }
}
