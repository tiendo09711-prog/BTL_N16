package vn.ptit.btl16.server.wallet;

import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.wallet.WalletWire;
import vn.ptit.btl16.server.routing.MessageRouter;
import vn.ptit.btl16.server.routing.RequestContext;
import java.math.BigDecimal;

public final class WalletController {
    private final WalletRepository wallets;
    public WalletController(WalletRepository wallets) { this.wallets = wallets; }
    public void register(MessageRouter router) {
        router.register(MessageType.GET_WALLET, true, context -> reply(context, MessageType.WALLET_RESULT));
        router.register(MessageType.REQUEST_TOPUP, true, context -> {
            wallets.submit(context.requireSession().getUserId(), context.value("requestId"), new BigDecimal(context.value("amount")));
            reply(context, MessageType.REQUEST_TOPUP_RESULT);
        });
    }
    private void reply(RequestContext context, MessageType type) throws Exception {
        long userId = context.requireSession().getUserId();
        context.replySuccess(type, WalletWire.encode(wallets.account(userId), wallets.requests(userId, false), wallets.entries(userId)));
    }
}
