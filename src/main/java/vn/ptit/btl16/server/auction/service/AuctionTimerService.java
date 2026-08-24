package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.auction.model.AuctionResult;
import vn.ptit.btl16.server.auction.model.AuctionRuntime;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.auction.repository.CloseAuctionCommit;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class AuctionTimerService implements AutoCloseable {
    private final AuctionManager auctions;
    private final AuctionRepository repository;
    private final RoomManager rooms;
    private final AuctionBroadcastService broadcasts;
    private final int checkMillis;
    private final int tickMillis;
    private final ScheduledExecutorService scheduler;
    private volatile long lastTickAt;

    public AuctionTimerService(
            AuctionManager auctions,
            AuctionRepository repository,
            RoomManager rooms,
            AuctionBroadcastService broadcasts,
            ServerConfig config) {
        this.auctions = auctions;
        this.repository = repository;
        this.rooms = rooms;
        this.broadcasts = broadcasts;
        this.checkMillis = config.getTimerCheckMillis();
        this.tickMillis = config.getTickBroadcastMillis();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "auction-timer");
            thread.setDaemon(true);
            return thread;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::safeRun, 0L, checkMillis, TimeUnit.MILLISECONDS);
    }

    private void safeRun() {
        try {
            Instant now = Instant.now();
            for (AuctionRuntime runtime : auctions.runtimes()) {
                closeIfExpired(runtime, now);
            }
            archiveClosedAuctions(now);
            long currentMillis = System.currentTimeMillis();
            if (currentMillis - lastTickAt >= tickMillis) {
                lastTickAt = currentMillis;
                broadcastTicks(now);
            }
        } catch (RuntimeException exception) {
            System.err.println("[TIMER] Error: " + exception.getMessage());
        }
    }

    private void archiveClosedAuctions(Instant now) {
        for (AuctionSnapshot snapshot : auctions.archiveClosedAuctions(now)) {
            rooms.removeAuction(snapshot.getAuctionId());
            broadcasts.broadcastAll(
                    MessageType.AUCTION_ARCHIVED,
                    AuctionWireData.archived(snapshot, now));
            System.out.println("[TIMER] Auction archived: " + snapshot.getAuctionId());
        }
    }

    private void closeIfExpired(AuctionRuntime runtime, Instant now) {
        AuctionResult result = null;
        AuctionSnapshot endedSnapshot = null;
        runtime.getLock().lock();
        try {
            if (runtime.getStatusUnsafe() != AuctionStatus.OPEN
                    || now.isBefore(runtime.getEndTimeUnsafe())) {
                return;
            }
            Optional<AuctionResult> saved = repository.closeAuction(new CloseAuctionCommit(
                    runtime.getAuctionId(),
                    runtime.getCurrentWinnerIdUnsafe(),
                    runtime.getCurrentWinnerUsernameUnsafe(),
                    runtime.getCurrentPriceUnsafe(),
                    runtime.getEndTimeUnsafe(),
                    now));
            if (saved.isEmpty()) {
                return;
            }
            runtime.markEnded(now);
            result = saved.get();
            endedSnapshot = runtime.snapshot();
        } finally {
            runtime.getLock().unlock();
        }

        if (result != null && endedSnapshot != null) {
            broadcasts.broadcastToRoom(
                    endedSnapshot.getAuctionId(),
                    MessageType.AUCTION_ENDED,
                    AuctionWireData.ended(endedSnapshot, result, now));
            System.out.println("[TIMER] Auction ended: " + endedSnapshot.getAuctionId());
        }
    }

    private void broadcastTicks(Instant now) {
        for (AuctionRuntime runtime : auctions.runtimes()) {
            AuctionSnapshot snapshot = runtime.snapshot();
            if (snapshot.getStatus() == AuctionStatus.OPEN
                    && rooms.subscriberCount(snapshot.getAuctionId()) > 0) {
                broadcasts.broadcastToRoom(
                        snapshot.getAuctionId(),
                        MessageType.AUCTION_TICK,
                        AuctionWireData.tick(
                                snapshot,
                                now,
                                rooms.subscriberCount(snapshot.getAuctionId())));
            }
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
