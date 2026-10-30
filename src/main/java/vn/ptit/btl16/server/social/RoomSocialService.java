package vn.ptit.btl16.server.social;

import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.account.model.UserAccount;
import vn.ptit.btl16.server.account.repository.UserRepository;
import vn.ptit.btl16.server.auction.model.*;
import vn.ptit.btl16.server.auction.service.*;
import vn.ptit.btl16.server.network.ServerMessagingService;
import vn.ptit.btl16.server.routing.*;
import vn.ptit.btl16.server.session.*;

import java.time.Instant;
import java.util.*;

public final class RoomSocialService {
    private final AuctionManager auctions;
    private final AuctionManagementService management;
    private final RoomManager rooms;
    private final UserRepository users;
    private final SessionManager sessions;
    private final RoomSocialStore store;
    private final ServerMessagingService messaging;
    private final RoomInteractionStore interactions;
    private vn.ptit.btl16.server.auction.repository.AuctionRepository auctionRepository;
    public void setAuctionRepository(vn.ptit.btl16.server.auction.repository.AuctionRepository repository) { auctionRepository = repository; }

    public RoomSocialService(AuctionManager auctions, AuctionManagementService management, RoomManager rooms,
            UserRepository users, SessionManager sessions, RoomSocialStore store, ServerMessagingService messaging) {
        this.auctions = auctions; this.management = management; this.rooms = rooms;
        this.users = users; this.sessions = sessions; this.store = store; this.messaging = messaging;
        this.interactions = new RoomInteractionStore(store.connectionFactory());
    }

    public void register(MessageRouter router) {
        router.register(MessageType.GET_ROOM_SOCIAL, true, this::social);
        router.register(MessageType.SEND_ROOM_CHAT, true, this::chat);
        router.register(MessageType.SET_ROOM_ANNOUNCEMENT, true, this::interaction);
        router.register(MessageType.SET_ROOM_REACTION, true, this::interaction);
        router.register(MessageType.SET_ROOM_MUTE, true, this::interaction);
        router.register(MessageType.GET_ROOM_PROFILE, true, this::profile);
        router.register(MessageType.GET_PUBLIC_PROFILE, true, context ->
                context.replySuccess(MessageType.PUBLIC_PROFILE_RESULT, publicProfile(context.requireSession().getUserId())));
        router.register(MessageType.UPDATE_PUBLIC_PROFILE, true, context -> {
            String biography = context.value("biography").strip();
            if (biography.codePointCount(0, biography.length()) > 500 || hasControls(biography)) {
                throw new IllegalArgumentException("Biography must contain at most 500 characters");
            }
            String avatar = context.getRequest().getData().containsKey("avatarBase64")
                    ? (context.value("avatarBase64").isEmpty() ? "" : AvatarImages.normalize(context.value("avatarBase64"))) : null;
            store.saveDetails(context.requireSession().getUserId(), biography, avatar);
            context.replySuccess(MessageType.UPDATE_PUBLIC_PROFILE_RESULT, publicProfile(context.requireSession().getUserId()));
        });
    }

    private AuctionSnapshot requireMember(RequestContext context, long auctionId) {
        UserSession session = context.requireSession();
        if (sessions.findByConnection(context.getConnection().getConnectionId())
                .filter(active -> active.getSessionToken().equals(session.getSessionToken())).isEmpty()) {
            throw new AuctionException(ErrorCode.AUTH_REQUIRED, "Session is no longer active");
        }
        management.requireReadAccess(session, auctionId);
        AuctionSnapshot snapshot = auctions.requireRuntime(auctionId).snapshot();
        if (!rooms.isMember(auctionId, context.getConnection().getConnectionId(), session.getUserId())) {
            throw new AuctionException(ErrorCode.NOT_IN_AUCTION_ROOM, "Join this room first");
        }
        return snapshot;
    }

    private void social(RequestContext context) throws Exception {
        long auctionId = WireValues.requireLong(context.getRequest().getData(), "auctionId");
        AuctionRuntime runtime = auctions.requireRuntime(auctionId);
        runtime.getLock().lock();
        try {
            AuctionSnapshot auction = requireMember(context, auctionId);
            Map<String, String> data = new LinkedHashMap<>();
            data.put("auctionId", Long.toString(auctionId));
            data.put("anonymousGuests", Boolean.toString(auction.hasAnonymousGuests()));
            putInteractionState(data, auction, context.requireSession().getUserId(), List.of());
            Map<Long, RoomMember> members = new TreeMap<>();
            for (RoomMember member : rooms.membersSnapshot(auctionId)) { members.put(member.getUserId(), member); }
            int index = 0;
            for (RoomMember member : members.values()) {
                if (index >= 200) { break; }
                String prefix = "member." + index++ + ".";
                putIdentity(data, prefix, auction, member.getUserId());
                boolean hasBid = auctionRepository != null && auctionRepository.hasBidsByUser(auctionId, member.getUserId());
                boolean host = member.getUserId() == auction.getHostUserId();
                boolean open = auction.isOpenAt(Instant.now());
                String reason = host ? "HOST" : hasBid ? "HAS_VALID_BID" : !open ? "CLOSED"
                        : context.requireSession().getUserId() != auction.getHostUserId() ? "NOT_HOST" : "";
                data.put(prefix + "self", Boolean.toString(member.getUserId() == context.requireSession().getUserId()));
                data.put(prefix + "leading", Boolean.toString(Objects.equals(auction.getCurrentWinnerId(), member.getUserId())));
                data.put(prefix + "hasValidBid", Boolean.toString(hasBid));
                data.put(prefix + "canKick", Boolean.toString(reason.isEmpty()));
                data.put(prefix + "kickReason", reason);
                if (context.requireSession().getUserId() == auction.getHostUserId() || member.getUserId() == context.requireSession().getUserId()) {
                    putMute(data, prefix + "mute.", interactions.mute(auctionId, member.getUserId()));
                }
            }
            data.put("memberCount", Integer.toString(index));
            data.put("memberTotal", Integer.toString(members.size()));
            long afterId = context.value("afterChatId").isBlank() ? -1 : WireValues.requireLong(context.getRequest().getData(), "afterChatId");
            if (afterId < -1) { throw new IllegalArgumentException("Invalid chat cursor"); }
            List<RoomSocialStore.Chat> history = new ArrayList<>(afterId < 0 ? store.recent(auctionId) : store.after(auctionId, afterId));
            history.sort(Comparator.comparingLong(RoomSocialStore.Chat::id));
            index = 0;
            for (RoomSocialStore.Chat message : history) { putChat(data, "chat." + index++ + ".", auction, message); }
            data.put("chatCount", Integer.toString(index));
            data.put("hasMoreChat", Boolean.toString(afterId >= 0 && history.size() == 50));
            data.put("nextChatId", Long.toString(history.stream().mapToLong(RoomSocialStore.Chat::id).max().orElse(Math.max(0, afterId))));
            Set<Long> reactionIds = requestedChatIds(context, auctionId);
            history.forEach(message -> reactionIds.add(message.id()));
            while (reactionIds.size() > 200) { reactionIds.remove(reactionIds.iterator().next()); }
            putReactions(data, reactionIds, context.requireSession().getUserId());
            context.replySuccess(MessageType.ROOM_SOCIAL_RESULT, data);
        } finally { runtime.getLock().unlock(); }
    }

    private void chat(RequestContext context) throws Exception {
        long auctionId = WireValues.requireLong(context.getRequest().getData(), "auctionId");
        String body = context.value("body").strip();
        String nonce = context.value("nonce");
        if (body.isBlank() || body.codePointCount(0, body.length()) > 500 || hasControls(body)) {
            throw new IllegalArgumentException("Message must contain 1 to 500 characters");
        }
        if (!nonce.matches("[A-Za-z0-9_-]{8,64}")) { throw new IllegalArgumentException("Invalid message nonce"); }
        AuctionRuntime runtime = auctions.requireRuntime(auctionId);
        runtime.getLock().lock();
        try {
            AuctionSnapshot auction = requireMember(context, auctionId);
            if (auction.getStatus() != AuctionStatus.OPEN || !Instant.now().isBefore(auction.getEndTime())) {
                throw new AuctionException(ErrorCode.AUCTION_NOT_OPEN, "Chat is read-only after the auction ends");
            }
            requireNotMuted(auctionId, context.requireSession().getUserId());
            long replyId = context.value("replyToId").isBlank() ? 0 : WireValues.requireLong(context.getRequest().getData(), "replyToId");
            if (replyId != 0) { requireChat(auctionId, replyId); }
            RoomSocialStore.Chat message = store.append(auctionId, context.requireSession().getUserId(), nonce, body, Instant.now(), replyId);
            Map<String, String> data = new LinkedHashMap<>();
            data.put("auctionId", Long.toString(auctionId));
            putChat(data, "chat.", auction, message);
            if (message.fresh()) { messaging.sendEventToConnections(rooms.subscribersSnapshot(auctionId), MessageType.ROOM_CHAT_MESSAGE, data); }
            context.replySuccess(MessageType.SEND_ROOM_CHAT_RESULT, data);
        } finally { runtime.getLock().unlock(); }
    }

    private void profile(RequestContext context) throws Exception {
        long auctionId = WireValues.requireLong(context.getRequest().getData(), "auctionId");
        AuctionRuntime runtime = auctions.requireRuntime(auctionId);
        runtime.getLock().lock();
        try {
            AuctionSnapshot auction = requireMember(context, auctionId);
            if (auction.hasAnonymousGuests()) { throw new AuctionException(ErrorCode.ROOM_ACCESS_DENIED, "Profiles are hidden in anonymous rooms"); }
            long target = WireValues.requireLong(context.getRequest().getData(), "participantId");
            boolean known = target == auction.getHostUserId() || rooms.membersSnapshot(auctionId).stream().anyMatch(member -> member.getUserId() == target);
            if (!known) { throw new AuctionException(ErrorCode.USER_NOT_IN_AUCTION_ROOM, "Participant is no longer in this room"); }
            Map<String, String> data = publicProfile(target);
            data.put("auctionId", Long.toString(auctionId));
            data.put("host", Boolean.toString(target == auction.getHostUserId()));
            context.replySuccess(MessageType.ROOM_PROFILE_RESULT, data);
        } finally { runtime.getLock().unlock(); }
    }

    public String kickUsername(UserSession session, long auctionId, String username, String participantId) {
        management.requireReadAccess(session, auctionId);
        AuctionSnapshot auction = auctions.requireRuntime(auctionId).snapshot();
        if (session.getUserId() != auction.getHostUserId()) { throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Only the host can remove participants"); }
        if (!auction.hasAnonymousGuests() && participantId.isBlank()) { return username; }
        long publicId;
        try { publicId = Long.parseLong(participantId); } catch (NumberFormatException exception) { throw new IllegalArgumentException("Choose a participant from the room list"); }
        long actual = auction.hasAnonymousGuests() && publicId < 0 && publicId != Long.MIN_VALUE ? store.userForOrdinal(auctionId, -publicId)
                : (auction.hasAnonymousGuests() ? 0 : publicId);
        return rooms.membersSnapshot(auctionId).stream().filter(member -> member.getUserId() == actual)
                .map(RoomMember::getUsername).findFirst().orElseThrow(() -> new AuctionException(ErrorCode.USER_NOT_IN_AUCTION_ROOM, "Participant is no longer in this room"));
    }

    private Map<String, String> publicProfile(long userId) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new AuctionException(ErrorCode.PROFILE_NOT_FOUND, "Profile was not found"));
        RoomSocialStore.Details details = store.details(userId);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("participantId", Long.toString(userId));
        data.put("displayName", user.getDisplayName());
        data.put("friendCode", user.getFriendCode());
        data.put("biography", details.biography());
        data.put("avatarBase64", details.avatar());
        data.put("profileVersion", Long.toString(details.version()));
        data.put("joinedAt", user.getCreatedAt() == null ? "" : user.getCreatedAt().toString());
        return data;
    }

    private void putIdentity(Map<String, String> data, String prefix, AuctionSnapshot auction, long userId) {
        boolean host = userId == auction.getHostUserId();
        data.put(prefix + "participantId", Long.toString(publicId(auction, userId)));
        data.put(prefix + "name", auction.hasAnonymousGuests() ? (host ? "Ch\u1ee7 ph\u00f2ng" : alias(auction, userId))
                : users.findById(userId).map(UserAccount::getDisplayName).orElse("Ng\u01b0\u1eddi ch\u01a1i"));
        data.put(prefix + "host", Boolean.toString(host));
        data.put(prefix + "profileVisible", Boolean.toString(!auction.hasAnonymousGuests()));
        data.put(prefix + "profileVersion", Long.toString(auction.hasAnonymousGuests() ? 0 : store.details(userId).version()));
    }

    private void putChat(Map<String, String> data, String prefix, AuctionSnapshot auction, RoomSocialStore.Chat message) {
        data.put(prefix + "id", Long.toString(message.id()));
        data.put(prefix + "body", message.body());
        data.put(prefix + "sentAt", message.sentAt().toString());
        putIdentity(data, prefix, auction, message.senderId());
        if (message.replyToId() != 0) {
            RoomSocialStore.Chat original = store.findChat(auction.getAuctionId(), message.replyToId());
            if (original != null) {
                data.put(prefix + "reply.id", Long.toString(original.id()));
                data.put(prefix + "reply.body", shortText(original.body(), 160));
                putIdentity(data, prefix + "reply.", auction, original.senderId());
            }
        }
    }

    private static String shortText(String text, int limit) {
        return text.codePointCount(0, text.length()) <= limit ? text : text.substring(0, text.offsetByCodePoints(0, limit)) + "...";
    }

    private RoomSocialStore.Chat requireChat(long room, long chatId) {
        RoomSocialStore.Chat original = store.findChat(room, chatId);
        if (original == null) { throw new IllegalArgumentException("Message does not belong to this room"); }
        return original;
    }

    private void requireNotMuted(long room, long user) {
        RoomInteractionStore.Mute mute = interactions.mute(room, user);
        if (mute.active(Instant.now())) { throw new AuctionException(ErrorCode.ROOM_ACCESS_DENIED, "Trò chuyện bị tắt tiếng đến " + mute.until() + ": " + mute.reason()); }
    }

    private Set<Long> requestedChatIds(RequestContext context, long room) {
        Set<Long> ids = new LinkedHashSet<>();
        String raw = context.value("chatIds");
        if (raw.isBlank()) { return ids; }
        String[] values = raw.split(",", -1);
        if (values.length > 200) { throw new IllegalArgumentException("Too many message references"); }
        for (String value : values) { long id = Long.parseLong(value); if (id <= 0) { throw new IllegalArgumentException("Invalid message reference"); } ids.add(id); }
        if (!store.containsChats(room, ids)) { throw new IllegalArgumentException("Message does not belong to this room"); }
        return ids;
    }

    private void putMute(Map<String, String> data, String prefix, RoomInteractionStore.Mute mute) {
        boolean active = mute.active(Instant.now());
        data.put(prefix + "active", Boolean.toString(active));
        data.put(prefix + "reason", active ? mute.reason() : "");
        data.put(prefix + "until", active ? mute.until().toString() : "");
    }

    private void putInteractionState(Map<String, String> data, AuctionSnapshot auction, long viewer, Collection<Long> ids) {
        RoomInteractionStore.State state = interactions.state(auction.getAuctionId());
        data.put("interactionRevision", Long.toString(state.revision()));
        data.put("announcement.body", state.announcement());
        data.put("announcement.updatedAt", state.updatedAt().toString());
        data.put("canInteract", Boolean.toString(auction.isOpenAt(Instant.now())));
        putMute(data, "selfMute.", interactions.mute(auction.getAuctionId(), viewer));
        data.put("canChat", Boolean.toString(auction.isOpenAt(Instant.now()) && !interactions.mute(auction.getAuctionId(), viewer).active(Instant.now())));
        putReactions(data, ids, viewer);
    }

    private void putReactions(Map<String, String> data, Collection<Long> ids, long viewer) {
        int index = 0;
        for (var entry : interactions.reactions(ids, viewer).entrySet()) {
            String prefix = "reaction." + index++ + ".";
            data.put(prefix + "chatId", Long.toString(entry.getKey()));
            for (String emoji : RoomInteractionStore.EMOJI) {
                var value = entry.getValue().stream().filter(reaction -> reaction.emoji().equals(emoji)).findFirst().orElse(new RoomInteractionStore.Reaction(emoji, 0, false));
                data.put(prefix + emoji + ".count", Integer.toString(value.count()));
                data.put(prefix + emoji + ".mine", Boolean.toString(value.mine()));
            }
        }
        data.put("reactionCount", Integer.toString(index));
    }

    private void interaction(RequestContext context) throws Exception {
        long room = WireValues.requireLong(context.getRequest().getData(), "auctionId");
        AuctionRuntime runtime = auctions.requireRuntime(room);
        runtime.getLock().lock();
        try {
            AuctionSnapshot auction = requireMember(context, room);
            if (!auction.isOpenAt(Instant.now())) { throw new AuctionException(ErrorCode.AUCTION_NOT_OPEN, "Room interactions are read-only"); }
            long actor = context.requireSession().getUserId();
            String nonce = context.value("nonce");
            Map<String, String> data = new LinkedHashMap<>();
            Set<Long> ids = requestedChatIds(context, room);
            boolean fresh;
            MessageType result;
            switch (context.getRequest().getType()) {
                case SET_ROOM_ANNOUNCEMENT -> {
                    requireHost(auction, actor);
                    String body = context.value("body").strip();
                    if (body.codePointCount(0, body.length()) > 500 || hasControls(body)) { throw new IllegalArgumentException("Announcement must contain at most 500 characters"); }
                    fresh = interactions.announcement(room, actor, nonce, body, Instant.now());
                    result = MessageType.SET_ROOM_ANNOUNCEMENT_RESULT;
                }
                case SET_ROOM_REACTION -> {
                    requireNotMuted(room, actor);
                    long chatId = WireValues.requireLong(context.getRequest().getData(), "chatId"); requireChat(room, chatId); ids.add(chatId);
                    if (ids.size() > 200) { throw new IllegalArgumentException("Too many messages"); }
                    String active = context.value("active");
                    if (!Set.of("true", "false").contains(active)) { throw new IllegalArgumentException("Expected reaction state"); }
                    fresh = interactions.reaction(room, actor, nonce, chatId, context.value("emoji"), Boolean.parseBoolean(active), Instant.now());
                    result = MessageType.SET_ROOM_REACTION_RESULT;
                }
                case SET_ROOM_MUTE -> {
                    requireHost(auction, actor);
                    long targetId = WireValues.requireLong(context.getRequest().getData(), "participantId");
                    long target = auction.hasAnonymousGuests() ? (targetId < 0 && targetId != Long.MIN_VALUE ? store.userForOrdinal(room, -targetId) : 0) : targetId;
                    if (target <= 0 || target == auction.getHostUserId()) { throw new IllegalArgumentException("Choose a room guest"); }
                    if (rooms.membersSnapshot(room).stream().noneMatch(member -> member.getUserId() == target) && interactions.mute(room, target).until() == null) {
                        throw new AuctionException(ErrorCode.USER_NOT_IN_AUCTION_ROOM, "Participant is not in this room");
                    }
                    int seconds = WireValues.requireInt(context.getRequest().getData(), "seconds");
                    String reason = context.value("reason").strip();
                    if (seconds < 0 || seconds > 86400 || reason.codePointCount(0, reason.length()) > 250 || hasControls(reason) || (seconds > 0 && reason.isBlank())) {
                        throw new IllegalArgumentException("Mute needs a reason and duration of 1 to 86400 seconds; 0 to unmute");
                    }
                    fresh = interactions.setMute(room, actor, nonce, target, reason, seconds, Instant.now());
                    putIdentity(data, "target.", auction, target); putMute(data, "target.mute.", interactions.mute(room, target));
                    result = MessageType.SET_ROOM_MUTE_RESULT;
                }
                default -> throw new IllegalArgumentException("Unsupported interaction");
            }
            data.put("auctionId", Long.toString(room));
            putInteractionState(data, auction, actor, ids);
            if (fresh) { messaging.sendEventToConnections(rooms.subscribersSnapshot(room), MessageType.ROOM_INTERACTION_CHANGED,
                    Map.of("auctionId", Long.toString(room), "interactionRevision", data.get("interactionRevision"))); }
            context.replySuccess(result, data);
        } finally { runtime.getLock().unlock(); }
    }

    private static void requireHost(AuctionSnapshot auction, long user) {
        if (auction.getHostUserId() != user) { throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Only the room host can do this"); }
    }

    private long publicId(AuctionSnapshot auction, long userId) {
        return auction.hasAnonymousGuests() ? -store.ordinal(auction.getAuctionId(), userId, true) : userId;
    }

    private String alias(AuctionSnapshot auction, long userId) {
        return userId == auction.getHostUserId() ? "Ch\u1ee7 ph\u00f2ng" : "Kh\u00e1ch #" + store.ordinal(auction.getAuctionId(), userId, true);
    }

    public WireMessage project(String connectionId, WireMessage message) {
        if (message.getType() == MessageType.PRIVATE_MESSAGE || message.getType() == MessageType.FRIENDS_CHANGED) {
            long target = Long.parseLong(message.get(message.getType() == MessageType.PRIVATE_MESSAGE ? "recipientId" : "recipientUserId"));
            if (sessions.findByConnection(connectionId).filter(session -> session.getUserId() == target).isEmpty()) {
                throw new AuctionException(ErrorCode.AUTH_REQUIRED, "Private event recipient changed");
            }
            return message;
        }
        if (message.getType() == MessageType.PERSONAL_RECEIPT_RESULT || message.getType() == MessageType.MY_AUCTION_RECEIPTS_RESULT) {
            if (sessions.findByConnection(connectionId).isEmpty()) { throw new AuctionException(ErrorCode.AUTH_REQUIRED, "Session expired"); }
            return message;
        }
        Map<String, String> data = new LinkedHashMap<>(message.getData());
        if (message.getType() == MessageType.AUCTION_ENDED) {
            // Compare before anonymous identities are projected, even if the winner has left the room.
            var recipient = sessions.findByConnection(connectionId);
            data.put("winnerIsSelf", Boolean.toString(recipient.isPresent()
                    && Long.toString(recipient.get().getUserId()).equals(data.get("winnerId"))));
        }
        if (Set.of(MessageType.ROOM_EVENTS_RESULT, MessageType.AUCTION_STATS_RESULT, MessageType.BID_HISTORY_RESULT).contains(message.getType())) {
            var recipient = sessions.findByConnection(connectionId).orElseThrow(() -> new AuctionException(ErrorCode.AUTH_REQUIRED, "Session expired"));
            management.requireHistoryReadAccess(recipient, WireValues.requireLong(data, "auctionId"));
        }
        if (message.getType() == MessageType.ROOM_CHAT_MESSAGE || message.getType() == MessageType.ROOM_INTERACTION_CHANGED) {
            long roomId = WireValues.requireLong(data, "auctionId");
            UserSession recipient = sessions.findByConnection(connectionId).orElseThrow(() ->
                    new AuctionException(ErrorCode.AUTH_REQUIRED, "Chat recipient is no longer authenticated"));
            if (!rooms.isMember(roomId, connectionId, recipient.getUserId())) {
                throw new AuctionException(ErrorCode.NOT_IN_AUCTION_ROOM, "Chat recipient has left the room");
            }
            management.requireReadAccess(recipient, roomId);
        }
        List<String> prefixes = new ArrayList<>();
        for (String key : data.keySet()) {
            if (key.equals("auctionId") || key.matches("auction\\.\\d+\\.auctionId")) { prefixes.add(key.substring(0, key.length() - "auctionId".length())); }
        }
        for (String prefix : prefixes) {
            long auctionId = Long.parseLong(data.get(prefix + "auctionId"));
            AuctionRuntime runtime = auctions.findRuntimeIncludingArchived(auctionId).orElse(null);
            if (runtime == null) { throw new IllegalStateException("Cannot project unknown auction"); }
            AuctionSnapshot auction = runtime.snapshot();
            sessions.findByConnection(connectionId).ifPresent(session -> {
                long self = session.getUserId()==auction.getHostUserId() || rooms.isMember(auctionId, connectionId, session.getUserId())
                        ? publicId(auction, session.getUserId()) : 0;
                data.put(prefix + "selfParticipantId", Long.toString(self));
            });
            if (!auction.hasAnonymousGuests()) { continue; }
            data.put(prefix + "anonymousGuests", "true");
            maskPair(data, prefix + "hostUserId", prefix + "hostUsername", auction);
            maskPair(data, prefix + "currentWinnerId", prefix + "currentWinnerUsername", auction);
            if (!prefix.isEmpty()) { continue; }
            maskPair(data, "previousWinnerId", "previousWinnerUsername", auction);
            maskPair(data, "winnerId", "winnerUsername", auction);
            maskPair(data, "userId", "username", auction);
            maskBid(data, "bid.", auction);
            for (String key : new ArrayList<>(data.keySet())) {
                if (key.matches("bid\\.\\d+\\.userId")) { maskBid(data, key.substring(0, key.length() - 6), auction); }
                if (key.matches("event\\.\\d+\\.userId")) { maskPair(data, key, key.substring(0, key.length() - 6) + "username", auction); }
            }
            for (String key : List.of("newLeader", "username")) {
                if (data.containsKey(key) && (!key.equals("username") || !data.containsKey("userId"))) {
                    String name = data.get(key);
                    data.put(key, name.isEmpty() ? "" : users.findByUsername(name.toLowerCase(Locale.ROOT)).map(user -> alias(auction, user.getUserId())).orElse("Kh\u00e1ch"));
                }
            }
        }
        return new WireMessage(message.getVersion(), message.getKind(), message.getType(), message.getRequestId(),
                message.getServerSequence(), message.getSentAtEpochMillis(), data);
    }

    private void maskBid(Map<String, String> data, String prefix, AuctionSnapshot auction) {
        maskPair(data, prefix + "userId", prefix + "username", auction);
        if (data.containsKey(prefix + "displayName")) {
            data.put(prefix + "displayName", data.getOrDefault(prefix + "username", ""));
        }
    }

    private void maskPair(Map<String, String> data, String idKey, String nameKey, AuctionSnapshot auction) {
        String raw = data.get(idKey);
        if (raw == null) { return; }
        if (raw.isEmpty()) { if (data.containsKey(nameKey)) { data.put(nameKey, ""); } return; }
        long userId = Long.parseLong(raw);
        data.put(idKey, Long.toString(publicId(auction, userId)));
        data.put(nameKey, alias(auction, userId));
    }

    private static boolean hasControls(String text) {
        return text.codePoints().anyMatch(value -> Character.isISOControl(value) && value != '\n' && value != '\t');
    }
}
