package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.auction.model.AuctionRuntime;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionManager {
    private final ConcurrentHashMap<Long, AuctionRuntime> runtimes = new ConcurrentHashMap<>();

    public AuctionManager(AuctionRepository repository) {
        for (AuctionSnapshot snapshot : repository.findAllAuctions()) {
            runtimes.put(snapshot.getAuctionId(), new AuctionRuntime(snapshot));
        }
    }

    public AuctionRuntime requireRuntime(long auctionId) {
        AuctionRuntime runtime = runtimes.get(auctionId);
        if (runtime == null) {
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
        return runtime;
    }

    public List<AuctionSnapshot> snapshotsByHost(long hostUserId) {
        return runtimes.values().stream()
                .map(AuctionRuntime::snapshot)
                .filter(value -> value.getHostUserId() == hostUserId)
                .sorted(Comparator
                        .comparing((AuctionSnapshot value) -> value.getStatus() == AuctionStatus.OPEN ? 0 : 1)
                        .thenComparing(AuctionSnapshot::getEndTime))
                .toList();
    }

    public List<AuctionSnapshot> snapshots() {
        return runtimes.values().stream()
                .map(AuctionRuntime::snapshot)
                .sorted(Comparator
                        .comparing((AuctionSnapshot value) -> value.getStatus() == AuctionStatus.OPEN ? 0 : 1)
                        .thenComparing(AuctionSnapshot::getEndTime))
                .toList();
    }

    public Collection<AuctionRuntime> runtimes() {
        return List.copyOf(runtimes.values());
    }

    public int openCount() {
        int count = 0;
        for (AuctionRuntime runtime : runtimes.values()) {
            if (runtime.snapshot().getStatus() == AuctionStatus.OPEN) {
                count++;
            }
        }
        return count;
    }

    public int endedCount() {
        return Math.max(0, runtimes.size() - openCount());
    }

    public int totalCount() {
        return runtimes.size();
    }
}
