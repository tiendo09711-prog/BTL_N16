package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.TestUserRepository;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.repository.TestAuctionRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

public final class RoomInteractionSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        var config = ServerConfig.forTests(0, 3, 3, 1);
        var hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        try (var server = ServerApplication.createForTests(config, TestUserRepository.withDemoUsers(hasher), TestAuctionRepository.withDemoAuctions(config), hasher)) {
            server.start();
            try (var host = client(server.getBoundPort()); var guest = client(server.getBoundPort()); var other = client(server.getBoundPort())) {
                ok(new AccountApi(host).login("demo", "demo123")); ok(new AccountApi(guest).login("alice", "alice123")); ok(new AccountApi(other).login("bob", "bob123"));
                var hostAuctions = new AuctionApi(host); var guestAuctions = new AuctionApi(guest); var otherAuctions = new AuctionApi(other);
                var hostSocial = new RoomSocialApi(host); var guestSocial = new RoomSocialApi(guest); var otherSocial = new RoomSocialApi(other);
                long room = create(hostAuctions, "INTERACTION_ANON", true), otherRoom = create(hostAuctions, "INTERACTION_OTHER", false);
                var hostJoined = ok(hostAuctions.join(room)); long hostId = Long.parseLong(hostJoined.get("selfParticipantId"));
                TestSupport.check(hostId < 0, "host uses room alias instead of account identity");
                var joined = ok(guestAuctions.join(room)); long guestId = Long.parseLong(joined.get("selfParticipantId"));
                TestSupport.check(guestId < 0, "room guest code, not username");
                List<WireMessage> events = new CopyOnWriteArrayList<>(); guest.addEventListener(events::add);
                denied(otherSocial.announcement(room, "no member", "outsider-announce"), "NOT_IN_AUCTION_ROOM");
                denied(guestSocial.announcement(room, "fake host", "guest-announce-01"), "AUCTION_FORBIDDEN");
                denied(guestSocial.mute(room, guestId, 10, "fake host", "guest-mute-0001"), "AUCTION_FORBIDDEN");
                ok(hostSocial.announcement(room, "Phong giu lich su", "pin-message-0001"));
                ok(hostSocial.announcement(room, "Phong giu lich su", "pin-message-0001"));
                denied(hostSocial.announcement(room, "nonce conflict", "pin-message-0001"), "VALIDATION_ERROR");
                ok(otherAuctions.join(room)); TestSupport.equals("Phong giu lich su", ok(otherSocial.social(room)).get("announcement.body"), "late join pin");
                Thread.sleep(430); ok(hostSocial.announcement(room, "Thong bao da sua", "pin-message-0002"));
                TestSupport.equals("Thong bao da sua", ok(guestSocial.social(room)).get("announcement.body"), "edited pin");
                var original = ok(guestSocial.send(room, "Trich dan tu server", "original-message-01")); long originalId = Long.parseLong(original.get("chat.id"));
                var reply = ok(host.sendRequest(MessageType.SEND_ROOM_CHAT, Map.of(
                        "auctionId", Long.toString(room), "body", "Tra loi", "nonce", "reply-message-001", "replyToId", Long.toString(originalId),
                        "senderId", "2", "name", "Impersonated", "reply.name", "Forged", "reply.body", "Fabricated quote")).thenApply(ApiResponse::new));
                TestSupport.equals(Long.toString(hostId), reply.get("chat.participantId"), "author from session uses anonymous host alias");
                TestSupport.equals(Long.toString(guestId), reply.get("chat.reply.participantId"), "reply room alias");
                TestSupport.equals("Trich dan tu server", reply.get("chat.reply.body"), "quote from stored message");
                TestSupport.check(reply.get("chat.reply.name").startsWith("Kh\u00e1ch #"), "server-derived reply alias"); safe(reply);
                var retried = ok(hostSocial.send(room, "Tra loi", "reply-message-001", originalId));
                TestSupport.equals(reply.get("chat.id"), retried.get("chat.id"), "reply retry idempotent");
                denied(hostSocial.send(room, "Tra loi", "reply-message-001", 0), "VALIDATION_ERROR");
                denied(otherSocial.send(room, "invalid", "missing-reply-01", 999999), "VALIDATION_ERROR");
                ok(hostAuctions.join(otherRoom)); var foreign = ok(hostSocial.send(otherRoom, "Other room", "foreign-message-01"));
                denied(guestSocial.send(room, "foreign", "foreign-reply-001", Long.parseLong(foreign.get("chat.id"))), "VALIDATION_ERROR");
                denied(guestSocial.reaction(room, Long.parseLong(foreign.get("chat.id")), "LIKE", true, "foreign-react-01"), "VALIDATION_ERROR");
                denied(guestSocial.social(room, 0, List.of(Long.parseLong(foreign.get("chat.id")))), "VALIDATION_ERROR");
                denied(guestSocial.reaction(room, originalId, "INVALID", true, "invalid-react-01"), "VALIDATION_ERROR");
                var reaction = ok(guestSocial.reaction(room, originalId, "LIKE", true, "reaction-first-01"));
                TestSupport.equals("1", reaction.get("reaction.0.LIKE.count"), "first reaction count");
                TestSupport.equals("true", reaction.get("reaction.0.LIKE.mine"), "own reaction"); safe(reaction);
                ok(guestSocial.reaction(room, originalId, "LIKE", true, "reaction-first-01"));
                denied(guestSocial.reaction(room, originalId, "LIKE", false, "reaction-first-01"), "VALIDATION_ERROR");
                denied(guestSocial.reaction(room, originalId, "HEART", true, "reaction-too-fast"), "VALIDATION_ERROR");
                var concurrent = new ArrayList<CompletableFuture<ApiResponse>>();
                for (int index = 0; index < 8; index++) { concurrent.add(guestSocial.reaction(room, originalId, "LIKE", true, "reaction-first-01")); }
                for (var future : concurrent) { TestSupport.equals("1", ok(future).get("reaction.0.LIKE.count"), "concurrent duplicate"); }
                var second = ok(otherSocial.reaction(room, originalId, "LIKE", true, "reaction-second-01"));
                TestSupport.equals("2", second.get("reaction.0.LIKE.count"), "aggregate count");
                var hostView = ok(hostSocial.social(room, 0, List.of(originalId))); safe(hostView);
                TestSupport.equals("false", hostView.get("reaction.0.LIKE.mine"), "viewer-specific reaction");
                Thread.sleep(430);
                TestSupport.equals("1", ok(guestSocial.reaction(room, originalId, "LIKE", false, "reaction-remove-01")).get("reaction.0.LIKE.count"), "toggle off own only");
                ok(hostAuctions.join(room));
                Thread.sleep(430);
                denied(hostSocial.mute(room, 2, 10, "must use guest code", "mute-real-id-01"), "VALIDATION_ERROR");
                denied(hostSocial.mute(room, guestId, 0x7fffffff, "too long", "mute-too-long-01"), "VALIDATION_ERROR");
                var muted = ok(hostSocial.mute(room, guestId, 2, "Spam chat", "mute-guest-0001")); safe(muted);
                String expires = muted.get("target.mute.until");
                TestSupport.equals(expires, ok(hostSocial.mute(room, guestId, 2, "Spam chat", "mute-guest-0001")).get("target.mute.until"), "retry does not extend mute");
                var ownMute = ok(guestSocial.social(room)); TestSupport.equals("false", ownMute.get("canChat"), "muted canChat false");
                TestSupport.equals("Spam chat", ownMute.get("selfMute.reason"), "reason shown to guest");
                TestSupport.equals(expires, ownMute.get("selfMute.until"), "expiry shown to guest");
                denied(guestSocial.send(room, "muted", "muted-chat-0001"), "ROOM_ACCESS_DENIED");
                denied(guestSocial.send(room, "muted reply", "muted-reply-001", originalId), "ROOM_ACCESS_DENIED");
                denied(guestSocial.reaction(room, originalId, "HEART", true, "muted-react-001"), "ROOM_ACCESS_DENIED");
                var bid = ok(guestAuctions.bid(room, new BigDecimal("110000")));
                TestSupport.equals("true", bid.get("success"), "muted user can bid");
                Thread.sleep(2100);
                TestSupport.equals("true", ok(guestSocial.social(room)).get("canChat"), "mute expires without a write");
                ok(guestSocial.send(room, "After expiry", "expired-chat-001"));
                Thread.sleep(430); ok(hostSocial.mute(room, guestId, 60, "Again", "mute-guest-0002"));
                Thread.sleep(430); ok(hostSocial.mute(room, guestId, 0, "", "unmute-guest-001"));
                TestSupport.equals("false", ok(guestSocial.social(room)).get("selfMute.active"), "host unmute");
                Thread.sleep(430); ok(hostSocial.announcement(room, "", "pin-remove-0001"));
                TestSupport.equals("", ok(guestSocial.social(room)).get("announcement.body"), "unpin stored");
                long cursor = Long.parseLong(ok(guestSocial.social(room)).get("nextChatId"));
                TestSupport.equals("0", ok(guestSocial.social(room, cursor, List.of(originalId))).get("chatCount"), "poll does not reload history");
                denied(guestSocial.profile(room, guestId), "ROOM_ACCESS_DENIED");
                ok(guestAuctions.leave(room)); denied(guestSocial.reaction(room, originalId, "LIKE", true, "departed-react-01"), "NOT_IN_AUCTION_ROOM");
                ok(guestAuctions.join(room)); ok(guestAuctions.join(otherRoom)); ok(hostAuctions.endAuction(otherRoom));
                denied(guestSocial.send(otherRoom, "closed", "closed-chat-0001"), "AUCTION_NOT_OPEN");
                denied(guestSocial.reaction(otherRoom, Long.parseLong(foreign.get("chat.id")), "LIKE", true, "closed-react-001"), "AUCTION_NOT_OPEN");
                denied(hostSocial.announcement(otherRoom, "closed", "closed-pin-00001"), "AUCTION_NOT_OPEN");
                denied(hostSocial.mute(otherRoom, 2, 1, "closed", "closed-mute-0001"), "AUCTION_NOT_OPEN");
                TestSupport.equals("false", ok(guestSocial.social(otherRoom)).get("canInteract"), "closed read only");
                for (WireMessage event : events) { if (Set.of(MessageType.ROOM_CHAT_MESSAGE, MessageType.ROOM_INTERACTION_CHANGED).contains(event.getType())) { safe(new ApiResponse(event)); } }
            }
        }
        System.out.println("[PASS] RoomInteractionSelfTest: socket permissions, canonical replies, cross-room rejection, durable nonces, reaction counts/rate, timed mute/unmute, muted bid, close and anonymous safety");
    }
    static long create(AuctionApi api, String code, boolean anonymous) throws Exception {
        long product = Long.parseLong(ok(TestMedia.create(api, code, "Interaction room", TestMedia.DESCRIPTION)).get("productId"));
        return Long.parseLong(ok(api.createAuction(product, new BigDecimal("100000"), new BigDecimal("10000"), 5, "PUBLIC", "", anonymous)).get("auctionId"));
    }
    static NetworkClient client(int port) throws Exception { var client = new NetworkClient(2_097_152, 3000, 6000); client.connect("127.0.0.1", port); return client; }
    static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception {
        var response = future.get(15, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + ": " + response.getMessageText()); return response;
    }
    private static void denied(CompletableFuture<ApiResponse> future, String error) throws Exception {
        var response = future.get(15, TimeUnit.SECONDS); TestSupport.check(!response.isSuccess(), "request must fail: " + error); TestSupport.equals(error, response.getErrorCode(), "rejection code");
    }
    private static void safe(ApiResponse response) {
        for (var entry : response.getWireMessage().getData().entrySet()) {
            TestSupport.check(!entry.getKey().toLowerCase(Locale.ROOT).matches(".*(avatar|profileurl|username|senderid|actorid).*"), "private field: " + entry);
            TestSupport.check(!entry.getValue().toLowerCase(Locale.ROOT).matches(".*(alice|bob|@local.test|fabricated|forged|impersonated).*"), "private identity: " + entry);
            if (entry.getKey().endsWith("participantId") && !entry.getValue().isEmpty()) { long id = Long.parseLong(entry.getValue()); TestSupport.check(id < 0 || id == 1, "guest id leaked"); }
        }
    }
}
