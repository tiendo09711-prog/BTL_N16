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

public final class RoomProgressSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        var config = ServerConfig.forTests(0, 2, 2, 1, 1);
        var hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        var repository = TestAuctionRepository.withDemoAuctions(config);
        try (var server = ServerApplication.createForTests(config, TestUserRepository.withDemoUsers(hasher), repository, hasher)) {
            server.start();
            try (var host = client(server.getBoundPort()); var alice = client(server.getBoundPort()); var bob = client(server.getBoundPort())) {
                ok(new AccountApi(host).login("demo", "demo123"));
                String session = ok(new AccountApi(alice).login("alice", "alice123")).get("sessionToken");
                ok(new AccountApi(bob).login("bob", "bob123"));
                var owner = new AuctionApi(host); var first = new AuctionApi(alice); var second = new AuctionApi(bob);
                long room = room(owner, "PROGRESS", true, false);
                ok(owner.join(room)); ApiResponse joined = ok(first.join(room)); ok(second.join(room));
                long alias = Long.parseLong(joined.get("selfParticipantId"));
                int before = repository.findRoomEvents(room, Long.MAX_VALUE, 0, 1000).size();
                for (int index = 0; index < 3; index++) { ok(first.resync(room)); ok(first.events(room, Long.MAX_VALUE, 0)); ok(first.stats(room)); }
                TestSupport.equals(before, repository.findRoomEvents(room, Long.MAX_VALUE, 0, 1000).size(), "polling and repeated resync never invent presence events");
                ok(owner.extendAuction(room, 10));
                for (int index = 0; index < 125; index++) {
                    ok((index % 2 == 0 ? first : second).bid(room, BigDecimal.valueOf(110000L + index * 10000L)));
                }
                ApiResponse stats = ok(owner.stats(room)); anonymous(stats);
                TestSupport.equals("125", stats.get("totalBids"), "full-session bids exceed loaded window");
                TestSupport.equals("2", stats.get("uniqueBidders"), "unique bidder accounts, not connections");
                TestSupport.equals("3", stats.get("watcherConnectionCount"), "watchers count connections separately");
                TestSupport.equals("1", stats.get("extensionCount"), "manual extension counted once");
                TestSupport.equals("true", stats.get("extensionsComplete"), "new room statistics are complete");
                TestSupport.equals("1250000.00", new BigDecimal(stats.get("priceIncrease")).setScale(2).toPlainString(), "absolute growth from starting price");
                ApiResponse page = ok(owner.history(room, Long.MAX_VALUE, null, false)); anonymous(page);
                TestSupport.equals("50", page.get("bidCount"), "history page is bounded");
                long through = Long.parseLong(page.get("throughSequence"));
                ok(second.bid(room, new BigDecimal("1360000")));
                Set<String> bidIds = new HashSet<>(); int total = 0;
                while (true) {
                    var data = page.getWireMessage().getData();
                    for (int index = 0; index < Integer.parseInt(page.get("bidCount")); index++) {
                        TestSupport.check(bidIds.add(data.get("bid." + index + ".bidId")), "no overlapping bid pages");
                        TestSupport.equals("10000.00", new BigDecimal(data.get("bid." + index + ".increase")).setScale(2).toPlainString(), "increase relative to actual previous bid"); total++;
                    }
                    if (!Boolean.parseBoolean(page.get("hasMore"))) { break; }
                    page = ok(owner.history(room, Long.parseLong(page.get("nextBeforeSequence")), through, false)); anonymous(page);
                }
                TestSupport.equals(125, total, "snapshot pagination unaffected by concurrent later bid");
                page = ok(first.history(room, Long.MAX_VALUE, null, true)); int mineCount = 0;
                while (true) {
                    for (int index = 0; index < Integer.parseInt(page.get("bidCount")); index++) {
                        TestSupport.equals(Long.toString(alias), page.get("bid." + index + ".userId"), "mine filter derives from session before masking"); mineCount++;
                    }
                    if (!Boolean.parseBoolean(page.get("hasMore"))) { break; }
                    page = ok(first.history(room, Long.parseLong(page.get("nextBeforeSequence")), Long.parseLong(page.get("throughSequence")), true));
                }
                TestSupport.equals(63, mineCount, "mine filter spans more than one page");
                var forged = ok(alice.sendRequest(MessageType.GET_BID_HISTORY, Map.of("auctionId", Long.toString(room), "mine", "true", "userId", "3")).thenApply(ApiResponse::new));
                TestSupport.equals(Long.toString(alias), forged.get("bid.0.userId"), "mine ignores forged account id");
                var bounded = ok(host.sendRequest(MessageType.GET_ROOM_EVENTS, Map.of("auctionId", Long.toString(room), "limit", "999999")).thenApply(ApiResponse::new));
                TestSupport.equals("100", bounded.get("eventCount"), "server clamps event page size"); anonymous(bounded);
                denied(host.sendRequest(MessageType.GET_ROOM_EVENTS, Map.of("auctionId", Long.toString(room), "beforeId", "-1")).thenApply(ApiResponse::new), "VALIDATION_ERROR");
                ApiResponse roster = ok(new RoomSocialApi(host).social(room)); anonymous(roster);
                boolean protectedBidder = false;
                for (int index = 0; index < Integer.parseInt(roster.get("memberCount")); index++) {
                    if (roster.get("member." + index + ".participantId").equals(Long.toString(alias))) {
                        TestSupport.equals("HAS_VALID_BID", roster.get("member." + index + ".kickReason"), "losing bidder still cannot be removed");
                        TestSupport.equals("false", roster.get("member." + index + ".canKick"), "kick disabled by server"); protectedBidder = true;
                    }
                }
                TestSupport.check(protectedBidder, "bidder in roster");
                denied(new RoomSocialApi(host).kick(room, alias), "CANNOT_KICK_BIDDER");
                Set<String> eventIds = new HashSet<>(); long cursor = Long.MAX_VALUE; long highest = 0;
                while (true) {
                    var events = ok(owner.events(room, cursor, 0)); anonymous(events);
                    for (int index = 0; index < Integer.parseInt(events.get("eventCount")); index++) {
                        String id = events.get("event." + index + ".id");
                        TestSupport.check(eventIds.add(id), "stable event pages do not overlap"); highest = Math.max(highest, Long.parseLong(id));
                    }
                    if (!Boolean.parseBoolean(events.get("hasMore"))) { break; }
                    cursor = Long.parseLong(events.get("nextBeforeId"));
                }
                TestSupport.equals(257, eventIds.size(), "creation, three joins, manual extension and two events per accepted bid");
                alice.disconnect();
                TestSupport.waitUntil(() -> repository.findRoomEvents(room, Long.MAX_VALUE, highestCursor(eventIds), 100).stream().anyMatch(event -> event.type().equals("LEFT")), 3000, "disconnect recorded by server");
                alice.connect("127.0.0.1", server.getBoundPort()); ok(new AccountApi(alice).resumeSession(session)); ok(first.resync(room));
                ApiResponse recovered = ok(first.events(room, Long.MAX_VALUE, highest)); anonymous(recovered);
                TestSupport.equals("2", recovered.get("eventCount"), "reconnect recovers leave and join");
                ApiResponse replay = ok(first.events(room, Long.MAX_VALUE, highest));
                TestSupport.equals(recovered.get("event.0.id"), replay.get("event.0.id"), "retry returns identical persistent event ids");
                TestSupport.equals("0", ok(first.events(room, Long.MAX_VALUE, Long.parseLong(recovered.get("nextAfterId")))).get("eventCount"), "incremental cursor excludes replay");
                long privateRoom = room(owner, "PROGRESS_PRIVATE", true, true);
                denied(second.stats(privateRoom), "ROOM_ACCESS_DENIED"); denied(second.events(privateRoom, Long.MAX_VALUE, 0), "ROOM_ACCESS_DENIED");
                denied(second.history(privateRoom), "ROOM_ACCESS_DENIED");
                ok(first.join(privateRoom, "secret-room")); anonymous(ok(first.events(privateRoom, Long.MAX_VALUE, 0)));
                ok(second.join(privateRoom, "secret-room")); ok(owner.join(privateRoom));
                ApiResponse privateRoster = ok(new RoomSocialApi(host).social(privateRoom));
                long bobAlias = 0; long privateSelf = Long.parseLong(ok(first.resync(privateRoom)).get("selfParticipantId"));
                for (int index = 0; index < Integer.parseInt(privateRoster.get("memberCount")); index++) {
                    long participant = Long.parseLong(privateRoster.get("member." + index + ".participantId"));
                    if (participant < 0 && participant != privateSelf) { bobAlias = participant; }
                }
                ok(new RoomSocialApi(host).kick(privateRoom, bobAlias));
                denied(second.stats(privateRoom), "USER_BLOCKED_FROM_AUCTION"); denied(second.events(privateRoom, Long.MAX_VALUE, 0), "USER_BLOCKED_FROM_AUCTION");
                denied(second.history(privateRoom), "USER_BLOCKED_FROM_AUCTION");
                ok(owner.cancelAuction(privateRoom));
                TestSupport.check(repository.findRoomEvents(privateRoom, Long.MAX_VALUE, 0, 100).stream().anyMatch(event -> event.type().equals("CANCELLED")), "cancellation recorded");
                long race = room(owner, "PROGRESS_RACE", false, false); ok(first.join(race)); ok(second.join(race));
                var bidOne = first.bid(race, new BigDecimal("110000")); var bidTwo = second.bid(race, new BigDecimal("110000"));
                TestSupport.equals(1, (bidOne.get().isSuccess() ? 1 : 0) + (bidTwo.get().isSuccess() ? 1 : 0), "simultaneous equal bids accept once");
                TestSupport.equals("1", ok(owner.stats(race)).get("totalBids"), "rejected concurrent bid does not affect totals");
                long ended = room(owner, "PROGRESS_END", false, false); ok(owner.endAuction(ended));
                TestSupport.check(repository.findRoomEvents(ended, Long.MAX_VALUE, 0, 100).stream().anyMatch(event -> event.type().equals("ENDED")), "end event persisted");
                Thread.sleep(1500);
                TestSupport.equals("0", ok(owner.stats(ended)).get("totalBids"), "archived room statistics remain readable");
                ok(owner.events(ended, Long.MAX_VALUE, 0)); ok(owner.history(ended));
            }
        }
        System.out.println("[PASS] RoomProgressSelfTest: full totals, stable pages/mine/deltas, concurrent bids, server presence, reconnect cursors, private rights, anonymous APIs and bidder kick protection");
    }
    private static long highestCursor(Set<String> ids) { return ids.stream().mapToLong(Long::parseLong).max().orElse(0); }
    private static NetworkClient client(int port) throws Exception { var client = new NetworkClient(2_097_152, 3000, 6000); client.connect("127.0.0.1", port); return client; }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception {
        var response = future.get(15, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + " " + response.getMessageText()); return response;
    }
    private static void denied(CompletableFuture<ApiResponse> future, String code) throws Exception { TestSupport.equals(code, future.get(10, TimeUnit.SECONDS).getErrorCode(), "server denial"); }
    private static long room(AuctionApi api, String code, boolean anonymous, boolean privateRoom) throws Exception {
        long product = Long.parseLong(ok(TestMedia.create(api, code, code, TestMedia.DESCRIPTION)).get("productId"));
        return Long.parseLong(ok(api.createAuction(product, new BigDecimal("100000"), new BigDecimal("10000"), 5,
                privateRoom ? "PRIVATE" : "PUBLIC", privateRoom ? "secret-room" : "", anonymous)).get("auctionId"));
    }
    private static void anonymous(ApiResponse response) {
        for (var entry : response.getWireMessage().getData().entrySet()) {
            TestSupport.check(!entry.getValue().toLowerCase(Locale.ROOT).contains("alice") && !entry.getValue().toLowerCase(Locale.ROOT).contains("bob"), "anonymous name leak: " + entry);
            if (entry.getKey().matches(".*(userId|participantId|WinnerId|winnerId)$") && !entry.getValue().isBlank()) {
                long id = Long.parseLong(entry.getValue()); TestSupport.check(id < 0 || id == 1, "anonymous id leak: " + entry);
            }
        }
    }
}
