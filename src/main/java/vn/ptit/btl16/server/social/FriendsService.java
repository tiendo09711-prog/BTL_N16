package vn.ptit.btl16.server.social;

import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.account.model.UserAccount;
import vn.ptit.btl16.server.account.repository.UserRepository;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.network.ServerMessagingService;
import vn.ptit.btl16.server.routing.*;
import vn.ptit.btl16.server.session.SessionManager;

import java.util.*;

/** All private conversations are authorized using the authenticated user, never a supplied sender ID. */
public final class FriendsService {
    private final FriendsStore store;
    private final UserRepository users;
    private final SessionManager sessions;
    private final ServerMessagingService messaging;

    public FriendsService(FriendsStore store, UserRepository users, SessionManager sessions, ServerMessagingService messaging) {
        this.store = store; this.users = users; this.sessions = sessions; this.messaging = messaging;
    }
    public void register(MessageRouter router) {
        router.register(MessageType.GET_FRIENDS, true, this::list);
        router.register(MessageType.REQUEST_FRIEND, true, this::request);
        router.register(MessageType.RESPOND_FRIEND, true, this::respond);
        router.register(MessageType.GET_PRIVATE_MESSAGES, true, this::history);
        router.register(MessageType.SEND_PRIVATE_MESSAGE, true, this::send);
    }

    private void list(RequestContext context) throws Exception {
        long actor = context.requireSession().getUserId();
        Map<String, String> data = new LinkedHashMap<>();
        var unread = store.unread(actor); int index = 0, friends = 0, incoming = 0;
        for (var link : store.list(actor)) {
            var user = users.findById(link.other(actor)).filter(UserAccount::isActive).orElse(null);
            if (user == null) { continue; }
            String state = link.state(actor);
            putPerson(data, "person." + index++ + ".", user, state, unread.getOrDefault(user.getUserId(), 0));
            if (state.equals("FRIEND")) { friends++; }
            if (state.equals("INCOMING")) { incoming++; }
        }
        data.put("count", Integer.toString(index)); data.put("friendCount", Integer.toString(friends));
        data.put("requestCount", Integer.toString(incoming)); data.put("unreadCount", Integer.toString(unread.values().stream().mapToInt(Integer::intValue).sum()));
        if (!context.value("username").isBlank() || !context.value("targetId").isBlank()
                || !context.value("displayName").isBlank() || !context.value("friendCode").isBlank()) {
            var user = target(context); var link = store.find(actor, user.getUserId());
            putPerson(data, "target.", user, link == null ? "NONE" : link.state(actor), unread.getOrDefault(user.getUserId(), 0));
        }
        context.replySuccess(MessageType.FRIENDS_RESULT, data);
    }

    private void request(RequestContext context) throws Exception {
        long actor = context.requireSession().getUserId(); var user = target(context);
        var link = store.request(actor, user.getUserId());
        Map<String, String> data = new LinkedHashMap<>(); putPerson(data, "target.", user, link.state(actor), 0);
        context.replySuccess(MessageType.REQUEST_FRIEND_RESULT, data);
        changed(actor, user.getUserId());
    }

    private void respond(RequestContext context) throws Exception {
        long actor = context.requireSession().getUserId(); var user = target(context);
        store.respond(actor, user.getUserId(), context.value("action"));
        context.replySuccess(MessageType.RESPOND_FRIEND_RESULT, Map.of()); changed(actor, user.getUserId());
    }

    private void history(RequestContext context) throws Exception {
        long actor = context.requireSession().getUserId(); var user = target(context);
        long before = cursor(context, "beforeId"), after = cursor(context, "afterId");
        boolean markRead = context.value("markRead").equals("true");
        var messages = store.history(actor, user.getUserId(), before, after, markRead);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("targetId", Long.toString(user.getUserId())); data.put("count", Integer.toString(messages.size()));
        data.put("hasMore", Boolean.toString(messages.size() == 50));
        int index = 0; for (var message : messages) { putMessage(data, "message." + index++ + ".", message); }
        context.replySuccess(MessageType.PRIVATE_MESSAGES_RESULT, data);
    }

    private void send(RequestContext context) throws Exception {
        long actor = context.requireSession().getUserId(); var user = target(context);
        var message = store.send(actor, user.getUserId(), context.value("nonce"), context.value("body"));
        Map<String, String> data = new LinkedHashMap<>(); putMessage(data, "", message);
        context.replySuccess(MessageType.SEND_PRIVATE_MESSAGE_RESULT, data);
        if (message.fresh()) {
            data.put("displayName", users.findById(actor).map(UserAccount::getDisplayName).orElse("Bạn bè"));
            messaging.sendEventToUser(user.getUserId(), MessageType.PRIVATE_MESSAGE, data);
        }
    }

    private UserAccount target(RequestContext context) {
        Optional<UserAccount> found;
        if (!context.value("targetId").isBlank()) {
            found = users.findById(Long.parseLong(context.value("targetId")));
        } else {
            String name = context.value("displayName").strip(), code = context.value("friendCode").strip();
            if (name.isEmpty()) { name = context.value("username").strip(); }
            if (code.startsWith("#")) { code = code.substring(1); }
            if (name.isBlank() || name.length() > 100 || !code.matches("[0-9]{4}")) {
                throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Hãy nhập tên hiển thị và mã kết bạn gồm đúng 4 số, ví dụ #0123");
            }
            found = users.findByDisplayNameAndFriendCode(name, code);
        }
        var user = found.filter(UserAccount::isActive).orElseThrow(() -> new AuctionException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy tài khoản này"));
        if (user.getUserId() == context.requireSession().getUserId()) { throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Hãy chọn một tài khoản khác"); }
        return user;
    }
    private void putPerson(Map<String, String> data, String prefix, UserAccount user, String state, int unread) {
        data.put(prefix + "userId", Long.toString(user.getUserId())); data.put(prefix + "username", user.getUsername());
        data.put(prefix + "displayName", user.getDisplayName()); data.put(prefix + "state", state);
        data.put(prefix + "friendCode", user.getFriendCode());
        data.put(prefix + "online", Boolean.toString(sessions.activeConnectionIdForUser(user.getUserId()).isPresent()));
        data.put(prefix + "unread", Integer.toString(unread));
    }
    private void changed(long first, long second) {
        for (long user : new long[]{first, second}) { messaging.sendEventToUser(user, MessageType.FRIENDS_CHANGED, Map.of("recipientUserId", Long.toString(user))); }
    }
    private static long cursor(RequestContext context, String field) { return context.value(field).isBlank() ? 0 : Long.parseLong(context.value(field)); }
    private static void putMessage(Map<String, String> data, String prefix, FriendsStore.Message message) {
        data.put(prefix + "id", Long.toString(message.id())); data.put(prefix + "senderId", Long.toString(message.sender()));
        data.put(prefix + "recipientId", Long.toString(message.recipient())); data.put(prefix + "body", message.body());
        data.put(prefix + "sentAt", message.time().toString());
    }
}
