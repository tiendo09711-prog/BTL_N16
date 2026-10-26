package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.routing.*;
import vn.ptit.btl16.server.session.SessionManager;
import vn.ptit.btl16.server.session.UserSession;
import java.util.Map;

public final class AuctionReceiptService {
    private final AuctionRepository repository;
    private final SessionManager sessions;
    public AuctionReceiptService(AuctionRepository repository, SessionManager sessions) {
        this.repository = repository; this.sessions = sessions;
    }
    public void register(MessageRouter router) {
        router.register(MessageType.GET_PERSONAL_RECEIPT, true, context -> {
            var session = current(context);
            var result = repository.personalReceipt(session.getUserId(), WireValues.requireLong(context.getRequest().getData(), "auctionId"));
            reply(context, session, MessageType.PERSONAL_RECEIPT_RESULT, result);
        });
        router.register(MessageType.MY_AUCTION_RECEIPTS, true, context -> {
            var session = current(context);
            long before = context.value("beforeId").isBlank() ? Long.MAX_VALUE : Long.parseLong(context.value("beforeId"));
            if (before <= 0 || context.value("search").length() > 100) { throw new IllegalArgumentException("Invalid history query"); }
            int limit = context.value("limit").isBlank() ? 30 : Integer.parseInt(context.value("limit"));
            if (limit < 1 || limit > 100) { throw new IllegalArgumentException("Invalid page size"); }
            var result = repository.personalReceipts(session.getUserId(), context.value("scope").isBlank() ? "PARTICIPATED" : context.value("scope"),
                    context.value("outcome").isBlank() ? "ALL" : context.value("outcome"), context.value("search").trim(), before, limit);
            reply(context, session, MessageType.MY_AUCTION_RECEIPTS_RESULT, result);
        });
    }
    private UserSession current(RequestContext context) {
        var expected = context.requireSession();
        return sessions.findByConnection(context.getConnection().getConnectionId())
                .filter(value -> value.getSessionToken().equals(expected.getSessionToken()))
                .orElseThrow(() -> new AuctionException(ErrorCode.AUTH_REQUIRED, "Session expired"));
    }
    private void reply(RequestContext context, UserSession expected, MessageType type, Map<String, String> data) throws Exception {
        if (!current(context).getSessionToken().equals(expected.getSessionToken())) { throw new AuctionException(ErrorCode.AUTH_REQUIRED, "Session changed"); }
        context.replySuccess(type, data);
    }
}
