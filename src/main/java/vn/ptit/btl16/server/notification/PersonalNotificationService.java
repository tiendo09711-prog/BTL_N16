package vn.ptit.btl16.server.notification;

import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.routing.*;
import vn.ptit.btl16.server.session.SessionManager;

public final class PersonalNotificationService {
    private final AuctionRepository repository;
    private final SessionManager sessions;
    public PersonalNotificationService(AuctionRepository repository, SessionManager sessions) { this.repository = repository; this.sessions = sessions; }
    public void register(MessageRouter router) {
        router.register(MessageType.GET_MY_NOTIFICATIONS, true, context -> {
            long before = context.value("beforeId").isBlank() ? Long.MAX_VALUE : Long.parseLong(context.value("beforeId"));
            if (before <= 0) { throw new IllegalArgumentException("Invalid cursor"); }
            var result = repository.notifications(context.requireSession().getUserId(), before, Boolean.parseBoolean(context.value("unreadOnly")), 30);
            current(context); context.replySuccess(MessageType.MY_NOTIFICATIONS_RESULT, result);
        });
        router.register(MessageType.READ_MY_NOTIFICATION, true, context -> {
            current(context); String read = context.value("read");
            if (!read.equals("true") && !read.equals("false")) { throw new IllegalArgumentException("Invalid read state"); }
            long id = WireValues.requireLong(context.getRequest().getData(), "notificationId");
            repository.readNotification(context.requireSession().getUserId(), id, Boolean.parseBoolean(read)); current(context);
            context.replySuccess(MessageType.READ_MY_NOTIFICATION_RESULT, java.util.Map.of("notificationId", Long.toString(id), "read", read));
        });
    }
    private void current(RequestContext context) {
        if (sessions.findByConnection(context.getConnection().getConnectionId()).filter(value -> value.getSessionToken().equals(context.requireSession().getSessionToken())).isEmpty()) {
            throw new AuctionException(ErrorCode.AUTH_REQUIRED, "Session expired");
        }
    }
}
