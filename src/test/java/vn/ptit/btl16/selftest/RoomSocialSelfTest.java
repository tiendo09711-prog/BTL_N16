package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.TestUserRepository;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.repository.TestAuctionRepository;
import vn.ptit.btl16.server.social.AvatarImages;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

public final class RoomSocialSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        ServerConfig config = ServerConfig.forTests(0, 2, 2, 1);
        var hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        var users = TestUserRepository.withDemoUsers(hasher);
        var repository = TestAuctionRepository.withDemoAuctions(config);
        try (ServerApplication server = ServerApplication.createForTests(config, users, repository, hasher)) {
            server.start();
            try (NetworkClient host = client(server.getBoundPort()); NetworkClient alice = client(server.getBoundPort()); NetworkClient bob = client(server.getBoundPort())) {
                var hostAccount = new AccountApi(host); var aliceAccount = new AccountApi(alice); var bobAccount = new AccountApi(bob);
                success(hostAccount.login("demo", "demo123"), "host login"); success(aliceAccount.login("alice", "alice123"), "alice login"); success(bobAccount.login("bob", "bob123"), "bob login");
                var hostAuctions = new AuctionApi(host); var aliceAuctions = new AuctionApi(alice); var bobAuctions = new AuctionApi(bob);
                var hostSocial = new RoomSocialApi(host); var aliceSocial = new RoomSocialApi(alice); var bobSocial = new RoomSocialApi(bob);
                List<WireMessage> hostEvents = new CopyOnWriteArrayList<>(); host.addEventListener(hostEvents::add);
                List<WireMessage> aliceEvents = new CopyOnWriteArrayList<>(); alice.addEventListener(aliceEvents::add);
                String avatar = avatar();
                success(aliceSocial.saveProfile("Gioi thieu cong khai", avatar), "save public profile");
                failure(aliceSocial.saveProfile("x".repeat(501), null), "VALIDATION_ERROR", "long biography");
                failure(aliceSocial.saveProfile("ok", "not-an-image"), "VALIDATION_ERROR", "invalid avatar");
                long normal = create(hostAuctions, "SOCIAL_NORMAL", false, false);
                failure(aliceSocial.social(normal), "NOT_IN_AUCTION_ROOM", "nonmember history");
                success(hostAuctions.join(normal), "host join"); success(aliceAuctions.join(normal), "alice join");
                ApiResponse profile = success(hostSocial.profile(normal, 2), "public profile");
                TestSupport.equals("Alice", profile.get("displayName"), "public display name");
                TestSupport.equals("Gioi thieu cong khai", profile.get("biography"), "public biography");
                TestSupport.check(!profile.get("avatarBase64").isBlank(), "avatar included");
                for (String key : profile.getWireMessage().getData().keySet()) {
                    TestSupport.check(!key.toLowerCase(Locale.ROOT).matches(".*(email|phone|password|salt|wallet|session|login|address).*"), "profile whitelist: " + key);
                }
                failure(bobSocial.profile(normal, 2), "NOT_IN_AUCTION_ROOM", "nonmember profile");
                failure(hostSocial.profile(normal, 3), "USER_NOT_IN_AUCTION_ROOM", "cannot enumerate users");
                ApiResponse sent = success(aliceSocial.send(normal, "Xin chao ca phong", "normal-message-001"), "normal chat");
                TestSupport.equals("Alice", sent.get("chat.name"), "normal chat real display name");
                TestSupport.equals("2", sent.get("chat.participantId"), "normal public id");
                TestSupport.waitUntil(() -> hostEvents.stream().anyMatch(event -> event.getType() == MessageType.ROOM_CHAT_MESSAGE), 3000, "live chat broadcast");
                ApiResponse duplicate = success(aliceSocial.send(normal, "Xin chao ca phong", "normal-message-001"), "idempotent retry");
                TestSupport.equals(sent.get("chat.id"), duplicate.get("chat.id"), "retry preserves message id");
                failure(aliceSocial.send(normal, "different", "normal-message-001"), "VALIDATION_ERROR", "nonce reuse different text");
                failure(aliceSocial.send(normal, "too soon", "normal-message-002"), "VALIDATION_ERROR", "rate limit");
                failure(aliceSocial.send(normal, "x".repeat(501), "normal-message-003"), "VALIDATION_ERROR", "length");
                failure(aliceSocial.send(normal, "   ", "normal-message-004"), "VALIDATION_ERROR", "blank");
                success(aliceAuctions.leave(normal), "leave normal"); failure(aliceSocial.send(normal, "no access", "normal-message-005"), "NOT_IN_AUCTION_ROOM", "left member cannot send");
                ApiResponse forged = success(host.sendRequest(MessageType.SEND_ROOM_CHAT, Map.of(
                        "auctionId", Long.toString(normal), "body", "Server derives the author", "nonce", "spoof-author-001",
                        "senderId", "2", "username", "alice", "avatarBase64", avatar)).thenApply(ApiResponse::new), "spoofed author ignored");
                TestSupport.equals("1", forged.get("chat.participantId"), "author derives from authenticated session");
                TestSupport.equals("Demo User", forged.get("chat.name"), "client cannot forge display name");

                long anonymous = create(hostAuctions, "SOCIAL_ANON", true, true);
                failure(bobSocial.social(anonymous), "ROOM_ACCESS_DENIED", "private chat denied");
                failure(bobSocial.profile(anonymous, 2), "ROOM_ACCESS_DENIED", "private profile denied");
                var hostJoined=success(hostAuctions.join(anonymous), "host anonymous join");
                var hostView=ClientWireParser.auction(hostJoined.getWireMessage().getData(),"");
                TestSupport.check(hostView.getHostUserId()<0 && hostView.isHostedBy(1),"anonymous host keeps controls with masked identity");
                ApiResponse joined = success(aliceAuctions.join(anonymous, "room-secret"), "anonymous join");
                TestSupport.equals("true", joined.get("anonymousGuests"), "mode persisted in snapshot");
                long alias = Long.parseLong(joined.get("selfParticipantId")); TestSupport.check(alias < 0, "negative room-local public id");
                ApiResponse social = success(hostSocial.social(anonymous), "anonymous roster"); noIdentities(social.getWireMessage());
                TestSupport.equals("2", social.get("memberCount"), "only host and first guest are present");
                TestSupport.equals("Kh\u00e1ch #1", participantName(social, alias), "host does not consume the first guest number");
                TestSupport.equals("Ch\u1ee7 ph\u00f2ng", participantName(social, hostView.getHostUserId()), "host keeps its separate label");
                ApiResponse bobJoined = success(bobAuctions.join(anonymous, "room-secret"), "second anonymous join");
                long bobAlias = Long.parseLong(bobJoined.get("selfParticipantId"));
                TestSupport.equals("Kh\u00e1ch #2", participantName(success(hostSocial.social(anonymous), "two guest roster"), bobAlias), "second guest follows the first guest");
                failure(hostSocial.profile(anonymous, 2), "ROOM_ACCESS_DENIED", "host cannot reveal profile");
                failure(aliceSocial.profile(anonymous, 3), "ROOM_ACCESS_DENIED", "guest cannot reveal profile");
                ApiResponse anonChat = success(aliceSocial.send(anonymous, "Thong diep an toan", "anonymous-message-1"), "anonymous chat"); noIdentities(anonChat.getWireMessage());
                TestSupport.equals(Long.toString(alias), anonChat.get("chat.participantId"), "chat alias agrees with snapshot");
                TestSupport.equals("Kh\u00e1ch #1", anonChat.get("chat.name"), "chat uses guest-only numbering");
                success(aliceAuctions.leave(anonymous), "anonymous leave");
                ApiResponse rejoin = success(aliceAuctions.join(anonymous, "room-secret"), "anonymous rejoin");
                TestSupport.equals(Long.toString(alias), rejoin.get("selfParticipantId"), "alias stable on rejoin");
                ApiResponse rejoinedSocial = success(aliceSocial.social(anonymous), "anonymous roster after rejoin");
                TestSupport.equals("Kh\u00e1ch #1", participantName(rejoinedSocial, alias), "guest number stable on rejoin");
                TestSupport.equals("Kh\u00e1ch #2", participantName(rejoinedSocial, bobAlias), "remaining guest is not renumbered");
                failure(hostAuctions.kickUser(anonymous, "bob"), "VALIDATION_ERROR", "anonymous username oracle blocked");
                ApiResponse roster = success(hostSocial.social(anonymous), "roster before kick");
                long otherAlias = 0;
                for (int index = 0; index < Integer.parseInt(roster.get("memberCount")); index++) {
                    long id = Long.parseLong(roster.get("member." + index + ".participantId")); if (id == bobAlias) { otherAlias = id; }
                }
                TestSupport.check(otherAlias < 0, "other guest has distinct alias");
                ApiResponse kicked = success(hostSocial.kick(anonymous, otherAlias), "kick by alias"); noIdentities(kicked.getWireMessage());
                failure(bobSocial.social(anonymous), "USER_BLOCKED_FROM_AUCTION", "kicked history denied");
                failure(bobSocial.send(anonymous, "blocked", "anonymous-message-2"), "USER_BLOCKED_FROM_AUCTION", "kicked chat denied");
                failure(bobSocial.profile(anonymous, 2), "USER_BLOCKED_FROM_AUCTION", "kicked profile denied");
                failure(bobAuctions.join(anonymous, "room-secret"), "USER_BLOCKED_FROM_AUCTION", "kicked cannot rejoin");
                ApiResponse bid = success(aliceAuctions.bid(anonymous, new BigDecimal("110000")), "anonymous bid"); noIdentities(bid.getWireMessage());
                var auction = ClientWireParser.auction(bid.getWireMessage().getData(), "");
                TestSupport.equals(auction.getCurrentWinnerId(), Long.valueOf(auction.viewerId(2)), "own leading state survives masking");
                noIdentities(success(hostAuctions.history(anonymous), "anonymous history").getWireMessage());
                noIdentities(success(hostAuctions.listAuctions(), "anonymous lists").getWireMessage(), anonymous);
                noIdentities(success(hostAuctions.resync(anonymous), "anonymous resync").getWireMessage());
                failure(hostSocial.kick(anonymous, alias), "CANNOT_KICK_BIDDER", "bidder protection retained");
                success(hostAuctions.join(normal), "host normal rejoin");
                success(hostAuctions.endAuction(normal), "close normal room");
                failure(hostSocial.send(normal, "closed", "closed-message-01"), "AUCTION_NOT_OPEN", "closed chat read-only");
                success(hostSocial.social(normal), "closed history readable");
                success(aliceAccount.logout(), "logout"); failure(aliceSocial.social(anonymous), "AUTH_REQUIRED", "logged out chat denied");
                for (WireMessage event : hostEvents) { if (Long.toString(anonymous).equals(event.get("auctionId"))) { noIdentities(event); } }
                for (WireMessage event : aliceEvents) { if (Long.toString(anonymous).equals(event.get("auctionId"))) { noIdentities(event); } }
            }
        }
        TestSupport.equals(128, ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(AvatarImages.normalize(avatar())))).getWidth(), "normalized avatar size");
        System.out.println("[PASS] RoomSocialSelfTest: chat, public-profile whitelist, anonymous raw packets, stable aliases, private/kicked/logout permissions, idempotency, limits and own-leading state");
    }
    private static long create(AuctionApi api, String code, boolean anonymous, boolean privateRoom) throws Exception {
        long productId = Long.parseLong(success(TestMedia.create(api, code, code, TestMedia.DESCRIPTION), "create social product").get("productId"));
        return Long.parseLong(success(api.createAuction(productId, new BigDecimal("100000"), new BigDecimal("10000"), 1,
                privateRoom ? "PRIVATE" : "PUBLIC", privateRoom ? "room-secret" : "", anonymous), "create social room").get("auctionId"));
    }
    private static NetworkClient client(int port) throws Exception {
        var client = new NetworkClient(2_097_152, 3000, 6000); client.connect("127.0.0.1", port); return client;
    }
    private static String participantName(ApiResponse roster, long participantId) {
        for (int index = 0; index < Integer.parseInt(roster.get("memberCount")); index++) {
            String prefix = "member." + index + ".";
            if (Long.toString(participantId).equals(roster.get(prefix + "participantId"))) { return roster.get(prefix + "name"); }
        }
        throw new AssertionError("Missing participant " + participantId);
    }
    private static ApiResponse success(CompletableFuture<ApiResponse> task, String label) throws Exception {
        ApiResponse response = task.get(15, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), label + ": " + response.getErrorCode() + " " + response.getMessageText()); return response;
    }
    private static void failure(CompletableFuture<ApiResponse> task, String code, String label) throws Exception {
        ApiResponse response = task.get(8, TimeUnit.SECONDS); TestSupport.check(!response.isSuccess(), label); TestSupport.equals(code, response.getErrorCode(), label);
    }
    static String avatar() throws Exception {
        var image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB); var graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.ORANGE); graphics.fillRect(0, 0, 24, 24); graphics.dispose();
        var output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output); return Base64.getEncoder().encodeToString(output.toByteArray());
    }
    private static void noIdentities(WireMessage message) { noIdentities(message, 0); }
    private static void noIdentities(WireMessage message, long listAuction) {
        String prefix = "";
        if (listAuction != 0) {
            for (var entry : message.getData().entrySet()) {
                if (entry.getKey().endsWith(".auctionId") && entry.getValue().equals(Long.toString(listAuction))) { prefix = entry.getKey().substring(0, entry.getKey().length() - 9); break; }
            }
            TestSupport.check(!prefix.isEmpty(), "anonymous auction in list");
        }
        for (var entry : message.getData().entrySet()) {
            if (!entry.getKey().startsWith(prefix)) { continue; }
            String value = entry.getValue();
            TestSupport.check(!value.toLowerCase(Locale.ROOT).contains("alice") && !value.toLowerCase(Locale.ROOT).contains("bob")
                    && !value.contains("@local.test") && !entry.getKey().endsWith("avatarBase64"), "anonymous packet leaks identity: " + entry);
            if (entry.getKey().endsWith("hostUsername")) { TestSupport.equals("Chủ phòng",value,"anonymous seller name hidden"); }
            if (entry.getKey().matches(".*(hostUserId|currentWinnerId|previousWinnerId|winnerId|userId|participantId)$") && !value.isBlank()) {
                TestSupport.check(Long.parseLong(value) < 0, "actual identity leaked: " + entry);
            }
        }
    }
}
