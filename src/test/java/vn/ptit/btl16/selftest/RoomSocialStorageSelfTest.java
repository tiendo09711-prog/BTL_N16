package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.ServerSequence;
import vn.ptit.btl16.server.account.repository.*;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.model.*;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.auction.service.*;
import vn.ptit.btl16.server.db.DatabaseSchema;
import vn.ptit.btl16.server.network.*;
import vn.ptit.btl16.server.social.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class RoomSocialStorageSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String previousConfig = System.getProperty("btl16.server.config");
        String previousDirectory = System.getProperty("btl16.dataDir");
        Path folder = Files.createTempDirectory("btl16-social-jdbc-").toRealPath();
        Path settings = folder.resolve("server.properties");
        Files.writeString(settings, "db.driver=org.h2.Driver\ndb.name=btl16_verify_social_" + UUID.randomUUID().toString().replace("-", "") + "\ndb.user=sa\ndb.password=\ndb.autoInitialize=false\n");
        System.setProperty("btl16.server.config", settings.toString()); System.setProperty("btl16.dataDir", folder.toString());
        try {
            var config = ServerConfig.loadDefault(); var factory = new JdbcConnectionFactory(config);
            TestSupport.check(config.isEmbeddedDatabase() && config.databaseJdbcUrl().startsWith("jdbc:h2:file:" + folder.toString().replace('\\', '/') + "/"), "isolated H2 only");
            DatabaseSchema.initialize(factory); DatabaseSchema.initialize(factory);
            try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users(user_id,username,display_name,password_hash,password_salt,password_iterations,active) VALUES "
                        + "(1,'demo','Host','test','test',30000,TRUE),(2,'alice','Alice','test','test',30000,TRUE),(3,'bob','Bob','test','test',30000,TRUE)");
            }
            var repository = new JdbcAuctionRepository(factory);
            Product product = TestMedia.create(repository, new CreateProductCommit(1, "demo", "SOCIAL_JDBC", "Social persistence", TestMedia.DESCRIPTION, Instant.now()));
            Instant now = Instant.now();
            AuctionSnapshot auction = repository.createAuction(new CreateAuctionCommit(1, "demo", product.getProductId(),
                    new BigDecimal("100"), new BigDecimal("10"), now, now.plusSeconds(60), RoomVisibility.PUBLIC, "", "", null, true));
            TestSupport.check(new JdbcAuctionRepository(factory).findAuctionById(auction.getAuctionId()).orElseThrow().hasAnonymousGuests(), "mode persists");
            long room = auction.getAuctionId();
            var store = new RoomSocialStore(factory);
            var first = CompletableFuture.supplyAsync(() -> store.ordinal(room, 2, true));
            var second = CompletableFuture.supplyAsync(() -> new RoomSocialStore(factory).ordinal(room, 3, true));
            long alice = first.get(8, TimeUnit.SECONDS), bob = second.get(8, TimeUnit.SECONDS);
            TestSupport.check(alice != bob && alice > 0 && bob > 0, "parallel allocation unique");
            var restarted = new RoomSocialStore(factory);
            TestSupport.equals(alice, restarted.ordinal(room, 2, true), "alias survives store restart");
            TestSupport.equals(2L, restarted.userForOrdinal(room, alice), "alias reverse lookup survives restart");
            String normalized = AvatarImages.normalize(RoomSocialSelfTest.avatar());
            store.saveDetails(2, "A public biography", normalized);
            TestSupport.equals(normalized, restarted.details(2).avatar(), "avatar persists");
            restarted.saveDetails(2, "Updated", null);
            TestSupport.equals(normalized, store.details(2).avatar(), "no avatar field preserves image");
            var saved = store.append(room, 2, "persisted-message-01", "Persistent chat", now);
            TestSupport.equals(saved.id(), restarted.recent(room).get(0).id(), "chat survives restart");
            TestSupport.check(!restarted.append(room, 2, "persisted-message-01", "Persistent chat", now).fresh(), "retry across instances idempotent");
            var reply = store.append(room, 3, "persisted-reply-001", "Reply", now.plusSeconds(1), saved.id());
            TestSupport.equals(saved.id(), restarted.findChat(room, reply.id()).replyToId(), "reply reference survives restart");
            TestSupport.equals(1, restarted.after(room, saved.id()).size(), "keyset returns new messages only");
            var interactions = new RoomInteractionStore(factory); var restartedInteractions = new RoomInteractionStore(factory);
            interactions.announcement(room, 1, "persisted-pin-0001", "Stored pin", now);
            Instant announcementTime = restartedInteractions.state(room).updatedAt();
            TestSupport.equals("Stored pin", restartedInteractions.state(room).announcement(), "announcement persists");
            long revision = restartedInteractions.state(room).revision();
            TestSupport.check(!restartedInteractions.announcement(room, 1, "persisted-pin-0001", "Stored pin", now.plusSeconds(1)), "pin receipt persists");
            TestSupport.equals(revision, interactions.state(room).revision(), "duplicate does not change revision");
            rejected(() -> restartedInteractions.announcement(room, 1, "persisted-pin-0001", "Conflict", now.plusSeconds(1)), "pin nonce conflict");
            interactions.setMute(room, 1, "persisted-mute-001", 2, "Spam", 2, now.plusSeconds(1));
            var mute = restartedInteractions.mute(room, 2);
            TestSupport.check(mute.active(mute.until().minusMillis(1)) && !mute.active(mute.until()), "expiry boundary uses server time");
            restartedInteractions.setMute(room, 1, "persisted-mute-001", 2, "Spam", 2, now.plusSeconds(10));
            TestSupport.equals(mute.until(), interactions.mute(room, 2).until(), "expired mute retry cannot extend duration");
            interactions.reaction(room, 2, "persisted-react-001", saved.id(), "LIKE", true, now.plusSeconds(2));
            TestSupport.check(!restartedInteractions.reaction(room, 2, "persisted-react-001", saved.id(), "LIKE", true, now.plusSeconds(2)), "reaction receipt survives restart");
            rejected(() -> restartedInteractions.reaction(room, 2, "persisted-react-fast", saved.id(), "HEART", true, now.plusMillis(2100)), "persistent reaction rate limit");
            var concurrentReactions = List.of(
                    CompletableFuture.supplyAsync(() -> interactions.reaction(room, 3, "concurrent-react-001", saved.id(), "LIKE", true, now.plusSeconds(4))),
                    CompletableFuture.supplyAsync(() -> restartedInteractions.reaction(room, 3, "concurrent-react-001", saved.id(), "LIKE", true, now.plusSeconds(4))));
            TestSupport.check(concurrentReactions.get(0).get() != concurrentReactions.get(1).get(), "concurrent JDBC nonce commits once");
            var votes = restartedInteractions.reactions(List.of(saved.id()), 2).get(saved.id());
            TestSupport.equals(2, votes.stream().filter(value -> value.emoji().equals("LIKE")).findFirst().orElseThrow().count(), "persistent aggregate count");
            TestSupport.check(votes.stream().anyMatch(value -> value.emoji().equals("LIKE") && value.mine()), "own reaction survives restart");
            TestSupport.equals(announcementTime, restartedInteractions.state(room).updatedAt(), "reactions and mute do not change announcement edit time");
            long beforeFailure = interactions.state(room).revision();
            try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE room_chat_reactions ADD CONSTRAINT test_reaction_failure CHECK (emoji <> 'HEART')");
            }
            boolean failedWrite = false;
            try { interactions.reaction(room, 2, "rollback-react-0001", saved.id(), "HEART", true, now.plusSeconds(5)); }
            catch (AuctionRepositoryException expected) { failedWrite = true; }
            TestSupport.check(failedWrite, "forced reaction storage failure");
            TestSupport.equals(beforeFailure, restartedInteractions.state(room).revision(), "failed reaction rolls back revision");
            try (var connection = factory.openDatabase(); var statement = connection.createStatement()) { statement.executeUpdate("ALTER TABLE room_chat_reactions DROP CONSTRAINT test_reaction_failure"); }
            TestSupport.check(restartedInteractions.reaction(room, 2, "rollback-react-0001", saved.id(), "HEART", true, now.plusSeconds(5)), "failed transaction does not consume nonce");
            interactions.setMute(room, 1, "persisted-unmute-01", 2, "", 0, now.plusSeconds(11));
            TestSupport.check(!restartedInteractions.mute(room, 2).active(now.plusSeconds(11)), "persistent unmute");
            rejected(() -> restarted.append(room, 2, "persisted-message-02", "Fast", now.plusMillis(100)), "JDBC rate limit");
            rejected(() -> restarted.append(room, 2, "persisted-message-01", "Different", now.plusSeconds(1)), "JDBC nonce content conflict");
            for (int index = 1; index < 55; index++) { store.append(room, 2, "history-message-" + index, "line " + index, now.plusSeconds(index)); }
            TestSupport.equals(50, restarted.recent(room).size(), "history window bounded to fifty");
            var fixture = new AuctionRulesSelfTest.Fixture(repository);
            var users = TestUserRepository.withDemoUsers(new Pbkdf2PasswordHasher(30000));
            var messaging = new ServerMessagingService(new ConnectionRegistry(), fixture.sessions, new ServerSequence(0));
            var social = new RoomSocialService(fixture.auctions, fixture.management, fixture.rooms, users, fixture.sessions, restarted, messaging);
            fixture.rooms.join(room, "buyer", 2, "alice");
            Map<String, String> raw = new LinkedHashMap<>();
            raw.put("auctionId", Long.toString(room)); raw.put("currentWinnerId", "2"); raw.put("currentWinnerUsername", "alice");
            raw.put("winnerId", "2"); raw.put("winnerUsername", "alice"); raw.put("previousWinnerId", "3"); raw.put("previousWinnerUsername", "bob");
            raw.put("bid.0.userId", "2"); raw.put("bid.0.username", "alice"); raw.put("newLeader", "alice");
            raw.put("bid.0.displayName", "Alice");
            raw.put("bid.userId", "2"); raw.put("bid.username", "alice"); raw.put("bid.displayName", "Alice");
            WireMessage projected = social.project("buyer", WireMessage.event(MessageType.AUCTION_ENDED, 15, raw));
            TestSupport.equals(Long.toString(-alice), projected.get("winnerId"), "final winner masked");
            TestSupport.equals(Long.toString(-bob), projected.get("previousWinnerId"), "previous leader masked");
            TestSupport.equals(Long.toString(-alice), projected.get("bid.0.userId"), "history identity masked");
            TestSupport.equals(projected.get("bid.0.username"), projected.get("bid.0.displayName"), "history display name is replaced with anonymous alias");
            TestSupport.equals(projected.get("bid.username"), projected.get("bid.displayName"), "live display name is replaced with anonymous alias");
            TestSupport.check(!projected.get("bid.displayName").equals("Alice"), "voice cannot expose anonymous bidder display name");
            TestSupport.equals(Long.toString(-alice), projected.get("selfParticipantId"), "viewer identity masked");
            TestSupport.equals("Kh\u00e1ch #" + alice, projected.get("winnerUsername"), "guests allocated before the host keep their original numbers");
            store.ordinal(room, 1, true);
            WireMessage afterHost = social.project("buyer", WireMessage.event(MessageType.AUCTION_ENDED, 15, raw));
            TestSupport.equals(projected.get("winnerUsername"), afterHost.get("winnerUsername"), "a later host identity does not renumber earlier guests");
            TestSupport.equals(projected.get("winnerId"), afterHost.get("winnerId"), "a later host identity does not change public IDs");
            TestSupport.check(projected.get("newLeader").startsWith("Kh\u00e1ch #"), "outbid name masked");
            TestSupport.equals(15L, projected.getServerSequence(), "projection preserves sequence");
            rejected(() -> social.project("viewer", WireMessage.event(MessageType.ROOM_CHAT_MESSAGE, 16,
                    Map.of("auctionId", Long.toString(room)))), "outbound chat refuses a departed recipient");
            fixture.rooms.join(room, "buyer", 3, "bob");
            rejected(() -> social.project("buyer", WireMessage.event(MessageType.ROOM_CHAT_MESSAGE, 17,
                    Map.of("auctionId", Long.toString(room)))), "outbound chat refuses mismatched session/membership");
            store.saveDetails(2, "", ""); TestSupport.equals("", restarted.details(2).avatar(), "avatar removal persists");
            rejected(() -> AvatarImages.normalize("!"), "invalid base64");
            rejected(() -> AvatarImages.normalize(Base64.getEncoder().encodeToString(new byte[1_048_577])), "oversized avatar");
            byte[] excessiveDimensions = Base64.getDecoder().decode(RoomSocialSelfTest.avatar());
            java.nio.ByteBuffer.wrap(excessiveDimensions).putInt(16, 5000).putInt(20, 5000);
            var checksum = new java.util.zip.CRC32(); checksum.update(excessiveDimensions, 12, 17);
            java.nio.ByteBuffer.wrap(excessiveDimensions).putInt(29, (int) checksum.getValue());
            rejected(() -> AvatarImages.normalize(Base64.getEncoder().encodeToString(excessiveDimensions)), "avatar pixel limit before decompression");
        } finally {
            restore("btl16.server.config", previousConfig); restore("btl16.dataDir", previousDirectory);
        }
        System.out.println("[PASS] RoomSocialStorageSelfTest: idempotent schema migration, persistent mode/chat/profiles, concurrent stable aliases, final/outbid projection, avatar validation");
    }
    private static void rejected(Runnable action, String label) {
        try { action.run(); } catch (IllegalArgumentException | AuctionException expected) { return; }
        throw new AssertionError(label + " must fail");
    }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
