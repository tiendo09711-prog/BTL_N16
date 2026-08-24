package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.ServerSequence;
import vn.ptit.btl16.server.auction.model.AuctionRuntime;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.repository.AuctionConflictException;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.auction.repository.BidCommit;
import vn.ptit.btl16.server.session.UserSession;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public final class BidService {
    private static final BigDecimal MAX_BID = new BigDecimal("999999999999999.00");

    private final AuctionManager auctions;
    private final AuctionRepository repository;
    private final RoomManager rooms;
    private final AuctionBroadcastService broadcasts;
    private final ServerSequence sequence;
    private final int antiSnipingWindowSeconds;
    private final int extensionSeconds;

    public BidService(
            AuctionManager auctions,
            AuctionRepository repository,
            RoomManager rooms,
            AuctionBroadcastService broadcasts,
            ServerSequence sequence,
            ServerConfig config) {
        this.auctions = auctions;
        this.repository = repository;
        this.rooms = rooms;
        this.broadcasts = broadcasts;
        this.sequence = sequence;
        this.antiSnipingWindowSeconds = config.getAntiSnipingWindowSeconds();
        this.extensionSeconds = config.getExtensionSeconds();
    }

    public BidOutcome placeBid(
            UserSession session,
            String connectionId,
            long auctionId,
            BigDecimal rawAmount) {
        BigDecimal amount = validateAmount(rawAmount);
        AuctionRuntime runtime = auctions.requireRuntime(auctionId);
        BidOutcome outcome;
        runtime.getLock().lock();
        try {
            Instant now = Instant.now();
            if (!rooms.isMember(auctionId, connectionId)) {
                throw new AuctionException(
                        ErrorCode.NOT_IN_AUCTION_ROOM,
                        "Join the auction room before placing a bid");
            }
            if (runtime.getHostUserId() == session.getUserId()) {
                throw new AuctionException(
                        ErrorCode.AUCTION_FORBIDDEN,
                        "Chu tri khong duoc tu dat gia san pham cua minh");
            }
            if (runtime.getStatusUnsafe() != AuctionStatus.OPEN) {
                throw new AuctionException(ErrorCode.AUCTION_NOT_OPEN, "Auction is not open");
            }
            if (!now.isBefore(runtime.getEndTimeUnsafe())) {
                throw new AuctionException(ErrorCode.BID_AFTER_END, "Bid arrived after auction end time");
            }
            BigDecimal minimumBid = runtime.getCurrentPriceUnsafe()
                    .add(runtime.getMinBidIncrement());
            if (amount.compareTo(minimumBid) < 0) {
                throw new AuctionException(
                        ErrorCode.BID_TOO_LOW,
                        "Bid must be at least " + minimumBid.toPlainString());
            }

            BigDecimal expectedPrice = runtime.getCurrentPriceUnsafe();
            Long previousWinnerId = runtime.getCurrentWinnerIdUnsafe();
            String previousWinnerUsername = runtime.getCurrentWinnerUsernameUnsafe();
            Instant oldEndTime = runtime.getEndTimeUnsafe();
            long remainingMillis = Duration.between(now, oldEndTime).toMillis();
            boolean extended = antiSnipingWindowSeconds > 0
                    && extensionSeconds > 0
                    && remainingMillis <= antiSnipingWindowSeconds * 1000L;
            Instant newEndTime = extended
                    ? oldEndTime.plusSeconds(extensionSeconds)
                    : oldEndTime;
            long bidSequence = sequence.next();

            BidRecord bid;
            try {
                bid = repository.commitAcceptedBid(new BidCommit(
                        auctionId,
                        expectedPrice,
                        session.getUserId(),
                        session.getUsername(),
                        amount,
                        bidSequence,
                        now,
                        newEndTime));
            } catch (AuctionConflictException conflict) {
                throw new AuctionException(
                        ErrorCode.DATABASE_CONFLICT,
                        "Auction state changed; request a fresh snapshot and retry");
            }

            runtime.applyAcceptedBid(
                    amount,
                    session.getUserId(),
                    session.getUsername(),
                    newEndTime);
            outcome = new BidOutcome(
                    runtime.snapshot(),
                    bid,
                    previousWinnerId,
                    previousWinnerUsername,
                    extended);
        } finally {
            runtime.getLock().unlock();
        }

        publish(outcome);
        return outcome;
    }

    private void publish(BidOutcome outcome) {
        Instant now = Instant.now();
        AuctionSnapshot auction = outcome.getAuction();
        int watchers = rooms.subscriberCount(auction.getAuctionId());
        broadcasts.broadcastToRoom(
                auction.getAuctionId(),
                MessageType.BID_UPDATE,
                outcome.getBid().getServerSequence(),
                AuctionWireData.bidUpdate(
                        auction,
                        outcome.getBid(),
                        outcome.getPreviousWinnerId(),
                        outcome.getPreviousWinnerUsername(),
                        outcome.isExtended(),
                        now,
                        watchers));

        if (outcome.isExtended()) {
            broadcasts.broadcastToRoom(
                    auction.getAuctionId(),
                    MessageType.AUCTION_EXTENDED,
                    AuctionWireData.extended(auction, extensionSeconds, now));
        }

        Long previousWinnerId = outcome.getPreviousWinnerId();
        if (previousWinnerId != null && previousWinnerId.longValue() != outcome.getBid().getUserId()) {
            broadcasts.notifyUser(
                    previousWinnerId,
                    MessageType.OUTBID_NOTIFICATION,
                    AuctionWireData.outbid(auction, outcome.getBid().getUsername(), now));
        }
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new AuctionException(ErrorCode.INVALID_AMOUNT, "Bid amount is missing");
        }
        BigDecimal normalized;
        try {
            normalized = amount.setScale(2);
        } catch (ArithmeticException exception) {
            throw new AuctionException(ErrorCode.INVALID_AMOUNT, "Bid amount has too many decimals");
        }
        if (normalized.signum() <= 0 || normalized.compareTo(MAX_BID) > 0) {
            throw new AuctionException(ErrorCode.INVALID_AMOUNT, "Bid amount is outside allowed range");
        }
        return normalized;
    }
}
