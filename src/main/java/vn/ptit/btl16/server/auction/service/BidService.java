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
            if (repository.isAuctionUserBlocked(auctionId, session.getUserId())) {
                throw new AuctionException(ErrorCode.USER_BLOCKED_FROM_AUCTION, "Bạn đã bị chặn khỏi phòng");
            }
            if (!rooms.isMember(auctionId, connectionId, session.getUserId())) {
                throw new AuctionException(
                        ErrorCode.NOT_IN_AUCTION_ROOM,
                        "Join the auction room before placing a bid");
            }
            if (runtime.getHostUserId() == session.getUserId()) {
                throw new AuctionException(
                        ErrorCode.AUCTION_FORBIDDEN,
                        "Chủ trì không được tự đặt giá sản phẩm của mình");
            }
            if (runtime.getStatusUnsafe() != AuctionStatus.OPEN) {
                throw new AuctionException(ErrorCode.AUCTION_NOT_OPEN, "Auction is not open");
            }
            if (now.isBefore(runtime.getStartTime())) {
                throw new AuctionException(ErrorCode.AUCTION_NOT_OPEN, "Phiên đấu giá chưa bắt đầu");
            }
            if (Long.valueOf(session.getUserId()).equals(runtime.getCurrentWinnerIdUnsafe())) {
                throw new AuctionException(ErrorCode.BID_ALREADY_LEADING, "Bạn đang dẫn đầu, không thể tự nâng giá");
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
            if (newEndTime.isAfter(runtime.getHardEndTimeUnsafe())) {
                newEndTime = runtime.getHardEndTimeUnsafe();
            }
            extended = newEndTime.isAfter(oldEndTime);
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
                    extended, (int) Duration.between(oldEndTime, newEndTime).getSeconds());
        } finally {
            runtime.getLock().unlock();
        }

        publish(outcome);
        return outcome;
    }

    private void publish(BidOutcome outcome) {
        Instant now = Instant.now();
        AuctionSnapshot auction = outcome.getAuction();
        int watchers = rooms.participantCount(auction.getAuctionId());
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
                    AuctionWireData.extended(auction, outcome.getAddedSeconds(), now));
        }

        Long previousWinnerId = outcome.getPreviousWinnerId();
        if (previousWinnerId != null && previousWinnerId.longValue() != outcome.getBid().getUserId()
                && rooms.membersSnapshot(auction.getAuctionId()).stream().anyMatch(member -> member.getUserId() == previousWinnerId)) {
            broadcasts.notifyUser(
                    previousWinnerId,
                    MessageType.OUTBID_NOTIFICATION,
                    AuctionWireData.outbid(auction, outcome.getBid().getUsername(), now));
        }
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        return AuctionRules.money(amount, ErrorCode.INVALID_AMOUNT);
    }
}
