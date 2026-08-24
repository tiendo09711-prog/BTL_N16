package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.auction.model.AuctionRuntime;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionManager {
    private final ConcurrentHashMap<Long, AuctionRuntime> runtimes = new ConcurrentHashMap<>();
    private final Set<Long> archivedAuctionIds = ConcurrentHashMap.newKeySet();
    private final int closedVisibilitySeconds;

    public AuctionManager(AuctionRepository repository, int closedVisibilitySeconds) {
        this.closedVisibilitySeconds = Math.max(0, closedVisibilitySeconds);
        Instant now = Instant.now();
        for (AuctionSnapshot snapshot : repository.findAllAuctions()) {
            runtimes.put(snapshot.getAuctionId(), new AuctionRuntime(snapshot));
            if (shouldArchive(snapshot, now)) {
                archivedAuctionIds.add(snapshot.getAuctionId());
            }
        }
    }

    public AuctionRuntime requireRuntime(long auctionId) {
        AuctionRuntime runtime = runtimes.get(auctionId);
        if (runtime == null || archivedAuctionIds.contains(auctionId)) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND, "Auction was not found");
        }
        return runtime;
    }

    public AuctionRuntime addRuntime(AuctionSnapshot snapshot) {
        AuctionRuntime runtime = new AuctionRuntime(snapshot);
        AuctionRuntime previous = runtimes.putIfAbsent(snapshot.getAuctionId(), runtime);
        if (previous != null) {
            throw new IllegalStateException("Auction runtime already exists: " + snapshot.getAuctionId());
        }
        archivedAuctionIds.remove(snapshot.getAuctionId());
        return runtime;
    }

    public List<AuctionSnapshot> snapshotsByHost(long hostUserId) {
        return runtimes.values().stream()
                .map(AuctionRuntime::snapshot)
                .filter(value -> !archivedAuctionIds.contains(value.getAuctionId()))
                .filter(value -> value.getHostUserId() == hostUserId)
                .sorted(Comparator
                        .comparing((AuctionSnapshot value) -> value.getStatus() == AuctionStatus.OPEN ? 0 : 1)
                        .thenComparing(AuctionSnapshot::getEndTime))
                .toList();
    }

    public List<AuctionSnapshot> snapshots() {
        return runtimes.values().stream()
                .map(AuctionRuntime::snapshot)
                .filter(value -> !archivedAuctionIds.contains(value.getAuctionId()))
                .sorted(Comparator
                        .comparing((AuctionSnapshot value) -> value.getStatus() == AuctionStatus.OPEN ? 0 : 1)
                        .thenComparing(AuctionSnapshot::getEndTime))
                .toList();
    }

    public Collection<AuctionRuntime> runtimes() {
        return List.copyOf(runtimes.values());
    }

    public List<AuctionSnapshot> archiveClosedAuctions(Instant now) {
        List<AuctionSnapshot> archived = new ArrayList<>();
        for (AuctionRuntime runtime : runtimes.values()) {
            AuctionSnapshot snapshot = runtime.snapshot();
            if (shouldArchive(snapshot, now)
                    && archivedAuctionIds.add(snapshot.getAuctionId())) {
                archived.add(snapshot);
            }
        }
        return List.copyOf(archived);
    }

    public int openCount() {
        return (int) snapshots().stream()
                .filter(value -> value.getStatus() == AuctionStatus.OPEN)
                .count();
    }

    public int endedCount() {
        return (int) snapshots().stream()
                .filter(value -> value.getStatus() != AuctionStatus.OPEN)
                .count();
    }

    public int totalCount() {
        return snapshots().size();
    }

    private boolean shouldArchive(AuctionSnapshot snapshot, Instant now) {
        if (snapshot.getStatus() == AuctionStatus.OPEN) {
            return false;
        }
        Instant closedAt = snapshot.getEndedAt() == null
                ? snapshot.getEndTime()
                : snapshot.getEndedAt();
        return !now.isBefore(closedAt.plusSeconds(closedVisibilitySeconds));
    }
}
