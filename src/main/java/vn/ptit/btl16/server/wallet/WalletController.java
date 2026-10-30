package vn.ptit.btl16.server.wallet;

import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.wallet.WalletWire;
import vn.ptit.btl16.server.routing.MessageRouter;
import vn.ptit.btl16.server.routing.RequestContext;
import java.math.BigDecimal;
import vn.ptit.btl16.common.protocol.WireValues;
import vn.ptit.btl16.server.auction.service.AuctionManagementService;

public final class WalletController {
    private final WalletRepository wallets;
    private final AuctionManagementService auctions;
    private final vn.ptit.btl16.server.session.SessionManager sessions;
    public WalletController(WalletRepository wallets, AuctionManagementService auctions, vn.ptit.btl16.server.session.SessionManager sessions) {
        this.wallets = wallets;
        this.auctions = auctions;
        this.sessions = sessions;
    }
    public void register(MessageRouter router) {
        router.register(MessageType.GET_WALLET, true, context -> {
            if (context.value("auctionId").isBlank()) {
                reply(context, MessageType.WALLET_RESULT);
            } else {
                long auctionId = WireValues.requireLong(context.getRequest().getData(), "auctionId");
                var session = context.requireSession();
                auctions.requireReadAccess(session, auctionId);
                context.replySuccess(MessageType.WALLET_RESULT, wallets.roomSummary(session.getUserId(), auctionId));
            }
        });
        router.register(MessageType.REQUEST_TOPUP, true, context -> {
            wallets.submit(context.requireSession().getUserId(), context.value("requestId"), new BigDecimal(context.value("amount")));
            reply(context, MessageType.REQUEST_TOPUP_RESULT);
        });
        router.register(MessageType.REQUEST_WITHDRAWAL, true, context -> {
            current(context);
            wallets.submitWithdrawal(context.requireSession().getUserId(), context.value("requestId"), new BigDecimal(context.value("amount")));
            reply(context, MessageType.REQUEST_WITHDRAWAL_RESULT);
        });
        router.register(MessageType.GET_WALLET_ENTRIES, true, context -> {
            var data = context.getRequest().getData();
            var result = wallets.entryPage(context.requireSession().getUserId(),
                    context.value("auctionId").isBlank() ? null : WireValues.requireLong(data, "auctionId"), context.value("type"),
                    context.value("from").isBlank() ? null : java.time.Instant.parse(context.value("from")),
                    context.value("to").isBlank() ? null : java.time.Instant.parse(context.value("to")),
                    context.value("beforeId").isBlank() ? Long.MAX_VALUE : WireValues.requireLong(data, "beforeId"), 50,
                    Boolean.parseBoolean(context.value("hideReservations")), Boolean.parseBoolean(context.value("hideLegacyReview")));
            current(context); context.replySuccess(MessageType.WALLET_ENTRIES_RESULT, result);
        });
    }
    private void reply(RequestContext context, MessageType type) throws Exception {
        long userId = context.requireSession().getUserId();
        var result = WalletWire.encode(wallets.account(userId), wallets.requests(userId, false), wallets.entries(userId), wallets.withdrawals(userId, false));
        current(context); context.replySuccess(type, result);
    }
    private void current(RequestContext context) {
        if (sessions.findByConnection(context.getConnection().getConnectionId()).filter(value -> value.getSessionToken().equals(context.requireSession().getSessionToken())).isEmpty()) {
            throw new vn.ptit.btl16.server.auction.service.AuctionException(vn.ptit.btl16.common.protocol.ErrorCode.AUTH_REQUIRED, "Session expired");
        }
    }
}
