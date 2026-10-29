package vn.ptit.btl16.server.wallet;

import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.routing.*;
import vn.ptit.btl16.server.session.SessionManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Opening a vault grants access only to the same authenticated connection and session. */
public final class VaultController {
    private final VaultRepository vault;
    private final SessionManager sessions;
    private final ConcurrentHashMap<String, Grant> grants = new ConcurrentHashMap<>();
    private record Grant(long user, String session, String token, Instant expires) { }
    public VaultController(VaultRepository vault, SessionManager sessions) { this.vault = vault; this.sessions = sessions; }
    public void register(MessageRouter router) {
        router.register(MessageType.GET_VAULT, true, context -> reply(context, MessageType.VAULT_RESULT, access(context, false)));
        router.register(MessageType.INITIALIZE_VAULT, true, context -> {
            current(context);
            vault.initialize(context.requireSession().getUserId(), context.value("requestId"), new BigDecimal(context.value("amount")), context.value("pin"), context.value("pinConfirmation"));
            current(context); reply(context, MessageType.INITIALIZE_VAULT_RESULT, open(context));
        });
        router.register(MessageType.UNLOCK_VAULT, true, context -> {
            current(context); vault.unlock(context.requireSession().getUserId(), context.value("pin"));
            current(context); reply(context, MessageType.UNLOCK_VAULT_RESULT, open(context));
        });
        router.register(MessageType.LOCK_VAULT, true, context -> {
            Grant grant = access(context, false);
            if (grant != null) { grants.remove(context.getConnection().getConnectionId(), grant); }
            reply(context, MessageType.LOCK_VAULT_RESULT, null);
        });
        router.register(MessageType.TRANSFER_VAULT, true, context -> {
            Grant grant = access(context, true);
            try {
                vault.transfer(grant.user(), context.value("requestId"), context.value("direction"), new BigDecimal(context.value("amount")), context.value("pin"));
            } catch (AuctionException error) {
                if (error.getErrorCode() == ErrorCode.VAULT_PIN_LOCKED) { grants.remove(context.getConnection().getConnectionId(), grant); }
                throw error;
            }
            reply(context, MessageType.TRANSFER_VAULT_RESULT, access(context, true));
        });
    }
    private Grant open(RequestContext context) {
        Instant now = Instant.now();
        grants.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expires())
                || sessions.findByConnection(entry.getKey()).filter(session -> session.getSessionToken().equals(entry.getValue().session())).isEmpty());
        Grant grant = new Grant(context.requireSession().getUserId(), context.requireSession().getSessionToken(), UUID.randomUUID().toString(), now.plusSeconds(900));
        grants.put(context.getConnection().getConnectionId(), grant); return grant;
    }
    private Grant access(RequestContext context, boolean required) {
        current(context); String connection = context.getConnection().getConnectionId();
        Grant grant = grants.get(connection);
        boolean valid = grant != null && grant.user() == context.requireSession().getUserId()
                && grant.session().equals(context.requireSession().getSessionToken())
                && grant.token().equals(context.value("vaultToken")) && Instant.now().isBefore(grant.expires());
        if (!valid) {
            if (grant != null && (!Instant.now().isBefore(grant.expires()) || !grant.session().equals(context.requireSession().getSessionToken()))) { grants.remove(connection, grant); }
            if (required) { throw new AuctionException(ErrorCode.VAULT_LOCKED, "Két đã khóa. Nhập mã PIN để mở lại két."); }
            return null;
        }
        Grant renewed = new Grant(grant.user(), grant.session(), grant.token(), Instant.now().plusSeconds(900));
        grants.replace(connection, grant, renewed); return renewed;
    }
    private void reply(RequestContext context, MessageType type, Grant grant) throws java.io.IOException {
        current(context); var data = vault.summary(context.requireSession().getUserId());
        data.put("unlocked", Boolean.toString(grant != null)); data.put("vaultToken", grant == null ? "" : grant.token());
        current(context); context.replySuccess(type, data);
    }
    private void current(RequestContext context) {
        if (sessions.findByConnection(context.getConnection().getConnectionId()).filter(session -> session.getSessionToken().equals(context.requireSession().getSessionToken())).isEmpty()) {
            throw new AuctionException(ErrorCode.AUTH_REQUIRED, "Phiên đăng nhập đã hết hạn");
        }
    }
}
