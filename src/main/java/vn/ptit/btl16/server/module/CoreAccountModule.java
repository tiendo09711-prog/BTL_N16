package vn.ptit.btl16.server.module;

import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.ServerSequence;
import vn.ptit.btl16.server.account.controller.AccountController;
import vn.ptit.btl16.server.auction.service.AuctionManager;
import vn.ptit.btl16.server.auction.service.RoomManager;
import vn.ptit.btl16.server.network.ConnectionRegistry;
import vn.ptit.btl16.server.routing.MessageRouter;
import vn.ptit.btl16.server.session.SessionManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Routes owned by Do Tien: TCP/account/session and health checking. */
public final class CoreAccountModule implements ServerModule {
    private final AccountController controller;
    private final SessionManager sessions;
    private final ConnectionRegistry connections;
    private final RoomManager rooms;
    private final AuctionManager auctions;
    private final ServerSequence sequence;

    public CoreAccountModule(
            AccountController controller,
            SessionManager sessions,
            ConnectionRegistry connections,
            RoomManager rooms,
            AuctionManager auctions,
            ServerSequence sequence) {
        this.controller = controller;
        this.sessions = sessions;
        this.connections = connections;
        this.rooms = rooms;
        this.auctions = auctions;
        this.sequence = sequence;
    }

    @Override
    public void register(MessageRouter router) {
        router.register(MessageType.REGISTER, false, controller::handleRegister);
        router.register(MessageType.LOGIN, false, controller::handleLogin);
        router.register(MessageType.RESUME_SESSION, false, controller::handleResume);
        router.register(MessageType.LOGOUT, true, controller::handleLogout);
        router.register(MessageType.GET_PROFILE, true, controller::handleGetProfile);
        router.register(MessageType.UPDATE_PROFILE, true, controller::handleUpdateProfile);
        router.register(MessageType.CHANGE_PASSWORD, true, controller::handleChangePassword);
        router.register(MessageType.PING, false, context -> {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("serverStatus", "UP");
            data.put("serverNow", Instant.now().toString());
            data.put("activeConnections", Integer.toString(connections.size()));
            data.put("onlineUsers", Integer.toString(sessions.activeCount()));
            data.put("detachedSessions", Integer.toString(sessions.detachedCount()));
            data.put("rooms", Integer.toString(rooms.roomCount()));
            data.put("subscriptions", Integer.toString(rooms.totalSubscriptions()));
            data.put("openAuctions", Integer.toString(auctions.openCount()));
            data.put("serverSequence", Long.toString(sequence.current()));
            context.replySuccess(MessageType.PONG, data);
        });
    }
}
