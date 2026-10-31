package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.auction.service.RoomManager;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

public final class RoomAdmissionSelfTest {
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        buyerCapacity();
        occupancyOrdering();
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        var directory = Files.createTempDirectory("btl16-admission-"); var settings = directory.resolve("server.properties");
        Files.writeString(settings, "server.tcp.enabled=false\nserver.websocket.bindAddress=127.0.0.1\nserver.websocket.enabled=true\nserver.websocket.port=0\nmedia.port=0\n"
                + "db.driver=org.h2.Driver\ndb.name=admission\ndb.user=sa\ndb.password=\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", settings.toString()); System.setProperty("btl16.dataDir", directory.toString());
        long persistedRoom, firstId;
        try {
            var config = ServerConfig.loadDefault();
            try (var server = ServerApplication.create(config)) {
                server.start();
                var repository = new JdbcAuctionRepository(new JdbcConnectionFactory(config));
                try (var host = client(server); var first = client(server); var second = client(server); var observer = client(server)) {
                    register(host, "admit_host"); firstId = register(first, "admit_first");
                    long secondId = register(second, "admit_second");
                    TestMedia.fundWallets(server.wallets());
                    long observerId = register(observer, "admit_observer");
                    var seller = new AuctionApi(host); var buyer = new AuctionApi(first); var rival = new AuctionApi(second);
                    var outside = new AuctionApi(observer);
                    List<WireMessage> events = new CopyOnWriteArrayList<>(); observer.addEventListener(events::add);
                    long product = product(seller, "ADMIT_VALIDATION");
                    Map<String, String> raw = new LinkedHashMap<>(Map.of("productId", Long.toString(product), "startPrice", "100",
                            "minBidIncrement", "10", "durationMinutes", "5"));
                    error("VALIDATION_ERROR", host.sendRequest(MessageType.CREATE_AUCTION, raw).thenApply(ApiResponse::new));
                    for (String invalid : List.of("0", "-1", "2.5", "10001", "2147483648", "")) {
                        raw.put("maxParticipants", invalid);
                        error("VALIDATION_ERROR", host.sendRequest(MessageType.CREATE_AUCTION, raw).thenApply(ApiResponse::new));
                    }
                    raw.put("maxParticipants", "1"); raw.put("startTime", Instant.now().plusSeconds(3600).toString());
                    error("VALIDATION_ERROR", host.sendRequest(MessageType.CREATE_AUCTION, raw).thenApply(ApiResponse::new));
                    raw.put("depositAmount", "0");
                    error("INVALID_AMOUNT", host.sendRequest(MessageType.CREATE_AUCTION, raw).thenApply(ApiResponse::new));
                    raw.remove("startTime"); raw.put("depositAmount", "1");
                    error("VALIDATION_ERROR", host.sendRequest(MessageType.CREATE_AUCTION, raw).thenApply(ApiResponse::new));

                    long immediate = room(seller, product, null, 1, "0", false);
                    TestSupport.equals("0", ok(seller.join(immediate)).get("watcherCount"), "host does not consume buyer capacity");
                    var race = raceJoin(buyer, rival, immediate, "");
                    TestSupport.equals(1L, race.stream().filter(ApiResponse::isSuccess).count(), "one last-slot winner");
                    TestSupport.equals(1L, race.stream().filter(value -> "ROOM_FULL".equals(value.getErrorCode())).count(), "one full rejection");
                    AuctionApi admitted = race.getFirst().isSuccess() ? buyer : rival;
                    AuctionApi waiting = admitted == buyer ? rival : buyer;
                    long admittedId = admitted == buyer ? firstId : secondId;
                    ok(admitted.join(immediate));
                    ok(seller.leave(immediate));
                    TestSupport.equals("1", ok(seller.join(immediate)).get("watcherCount"), "host can rejoin a full room without changing occupancy");
                    ok(seller.leave(immediate));
                    error("ROOM_FULL", waiting.join(immediate));
                    ok(admitted.bid(immediate, money("110"))); ok(admitted.leave(immediate));
                    amount("110", server.wallets().account(admittedId).reserved(), "leaving releases slot, not funded bid");
                    awaitCount(events, immediate, 0);
                    ok(waiting.join(immediate)); ok(waiting.leave(immediate)); awaitCount(events, immediate, 0);
                    try (var disconnecting = client(server)) {
                        register(disconnecting, "admit_disconnect");
                        ok(new AuctionApi(disconnecting).join(immediate));
                    }
                    TestSupport.waitUntil(() -> server.stats().getSubscriptions() == 0, 5000, "disconnect frees slot");
                    ok(outside.join(immediate)); ok(new AccountApi(observer).logout());
                    ok(waiting.join(immediate)); ok(waiting.leave(immediate));
                    ok(new AccountApi(observer).login("admit_observer", "Password123!"));
                    close(repository, immediate);

                    long scheduledProduct = product(seller, "ADMIT_SCHEDULED");
                    Instant start = Instant.now().plusSeconds(8).truncatedTo(ChronoUnit.MILLIS);
                    long scheduled = room(seller, scheduledProduct, start, 1, "50", true);
                    error("ROOM_PASSWORD_INVALID", buyer.placeDeposit(scheduled, "wrong"));
                    amount("0", repository.auctionDeposit(scheduled, firstId), "wrong password never reserves money");
                    error("INSUFFICIENT_FUNDS", outside.placeDeposit(scheduled, "secret"));
                    amount("0", server.wallets().account(observerId).reserved(), "failed deposit rolls back");
                    var depositOne = buyer.placeDeposit(scheduled, "secret"); var depositTwo = buyer.placeDeposit(scheduled, "secret");
                    ok(depositOne); ok(depositTwo); ok(rival.placeDeposit(scheduled, "secret"));
                    amount("50", server.wallets().account(firstId).reserved(), "duplicate deposit charged once");
                    TestSupport.equals(1L, server.wallets().entries(firstId).stream().filter(entry -> entry.type().equals("ROOM_DEPOSIT")
                            && entry.reference().equals("DEPOSIT:" + scheduled + ":" + firstId)).count(), "one deposit ledger entry");
                    TestSupport.equals(0, lobby(outside, scheduled).getWatcherCount(), "deposit does not consume capacity");
                    error("AUCTION_NOT_OPEN", buyer.join(scheduled));
                    TestSupport.waitUntil(() -> !Instant.now().isBefore(start), 12000, "scheduled start reached");
                    error("ROOM_DEPOSIT_REQUIRED", outside.join(scheduled, "secret"));
                    error("ROOM_DEPOSIT_CLOSED", outside.placeDeposit(scheduled, "secret"));
                    race = raceJoin(buyer, rival, scheduled, "secret");
                    TestSupport.equals(1L, race.stream().filter(ApiResponse::isSuccess).count(), "deposit and capacity both enforced");
                    admitted = race.getFirst().isSuccess() ? buyer : rival;
                    waiting = admitted == buyer ? rival : buyer;
                    admittedId = admitted == buyer ? firstId : secondId;
                    long waitingId = admitted == buyer ? secondId : firstId;
                    TestSupport.equals("1", ok(seller.join(scheduled)).get("watcherCount"), "scheduled host needs neither deposit nor a buyer slot");
                    ok(seller.leave(scheduled));
                    error("ROOM_FULL", waiting.join(scheduled, "secret"));
                    ok(admitted.bid(scheduled, money("110")));
                    amount("110", server.wallets().account(admittedId).reserved(), "deposit included in bid, not held twice");
                    ok(admitted.leave(scheduled)); ok(waiting.join(scheduled, "secret"));
                    error("NOT_IN_AUCTION_ROOM", admitted.bid(scheduled, money("120")));
                    ok(waiting.bid(scheduled, money("120")));
                    amount("50", server.wallets().account(admittedId).reserved(), "outbid retains only registration deposit");
                    amount("120", server.wallets().account(waitingId).reserved(), "new leader deposit offsets bid hold");
                    BigDecimal balance = server.wallets().account(waitingId).balance();
                    close(repository, scheduled); close(repository, scheduled);
                    amount("0", server.wallets().account(admittedId).reserved(), "loser deposit refunded at close");
                    amount("0", server.wallets().account(waitingId).reserved(), "winner has no residual hold");
                    TestSupport.equals(0, balance.subtract(money("120")).compareTo(server.wallets().account(waitingId).balance()), "winner pays once");
                    ok(waiting.leave(scheduled));

                    long large = room(seller, product(seller, "ADMIT_LARGE"), Instant.now().plusSeconds(3600), 2, "200", false);
                    ok(buyer.placeDeposit(large, ""));
                    var stored = repository.findAuctionById(large).orElseThrow();
                    repository.commitAcceptedBid(new BidCommit(large, money("100"), firstId, "admit_first", money("110"),
                            900001, stored.getStartTime().plusSeconds(1), stored.getEndTime()));
                    amount("200", server.wallets().account(firstId).reserved(), "large deposit covers whole bid");
                    close(repository, large); amount("0", server.wallets().account(firstId).reserved(), "excess winning deposit released");

                    long empty = room(seller, product(seller, "ADMIT_EMPTY"), Instant.now().plusSeconds(3600), 2, "75", false);
                    ok(buyer.placeDeposit(empty, "")); close(repository, empty);
                    amount("0", server.wallets().account(firstId).reserved(), "no-bid expiry refunds deposit");
                    long cancel = room(seller, product(seller, "ADMIT_CANCEL"), Instant.now().plusSeconds(3600), 2, "80", false);
                    ok(buyer.placeDeposit(cancel, "")); ok(seller.cancelAuction(cancel));
                    amount("0", server.wallets().account(firstId).reserved(), "upcoming cancellation refunds deposit");

                    persistedRoom = room(seller, product(seller, "ADMIT_RESTART"), Instant.now().plusSeconds(3600), 3, "90", true);
                    ok(buyer.placeDeposit(persistedRoom, "secret"));
                    amount("90", server.wallets().account(firstId).reserved(), "persisted deposit held before restart");
                }
            }
            try (var restarted = ServerApplication.create(config)) {
                restarted.start();
                try (var host = client(restarted); var first = client(restarted)) {
                    ok(new AccountApi(host).login("admit_host", "Password123!")); ok(new AccountApi(first).login("admit_first", "Password123!"));
                    var restored = lobby(new AuctionApi(first), persistedRoom);
                    TestSupport.equals(3, restored.getMaxParticipants(), "capacity survives restart");
                    amount("90", restored.getDepositAmount(), "deposit requirement survives restart");
                    ok(new AuctionApi(first).placeDeposit(persistedRoom, "secret"));
                    amount("90", restarted.wallets().account(firstId).reserved(), "retry after restart does not charge twice");
                    ok(new AuctionApi(host).cancelAuction(persistedRoom));
                    amount("0", restarted.wallets().account(firstId).reserved(), "persisted deposits refund correctly");
                }
            }
        } finally { restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData); }
        System.out.println("[PASS] RoomAdmissionSelfTest: strict creation, concurrent capacity, presence, deposit gates, escrow, settlement, cancellation and restart");
    }

    private static void buyerCapacity() {
        try (var rooms = new RoomManager()) {
            rooms.join(1, "host", 1, "host", 5, true);
            TestSupport.equals(0, rooms.participantCount(1), "host alone leaves all five buyer slots available");
            for (long buyerId = 2; buyerId <= 6; buyerId++) {
                rooms.join(1, "buyer-" + buyerId, buyerId, "buyer-" + buyerId, 5);
            }
            TestSupport.equals(5, rooms.participantCount(1), "capacity five admits five buyers plus the host");
            TestSupport.equals(6, rooms.subscriberCount(1), "host still receives room broadcasts");
            long revision = rooms.occupancyVersion(1);
            rooms.leave(1, "host");
            TestSupport.equals(5, rooms.participantCount(1), "host departure cannot free a buyer slot");
            rooms.join(1, "host-return", 1, "host", 5, true);
            TestSupport.check(rooms.isMember(1, "host-return"), "host can enter a full room");
            TestSupport.equals(revision, rooms.occupancyVersion(1), "host arrival and departure do not change buyer occupancy");
            try {
                rooms.join(1, "sixth-buyer", 7, "sixth-buyer", 5);
                throw new AssertionError("A sixth buyer must not enter a room with five buyer slots");
            } catch (AuctionException error) {
                TestSupport.equals(ErrorCode.ROOM_FULL, error.getErrorCode(), "sixth buyer is rejected");
            }
            rooms.join(1, "buyer-2-extra", 2, "buyer-2", 5);
            rooms.leave(1, "buyer-2");
            TestSupport.equals(5, rooms.participantCount(1), "a buyer with another connection still occupies one slot");
            rooms.removeConnection("buyer-2-extra");
            TestSupport.equals(4, rooms.participantCount(1), "last buyer connection leaving frees exactly one slot");
            rooms.join(1, "sixth-buyer", 7, "sixth-buyer", 5);
            TestSupport.equals(5, rooms.participantCount(1), "another buyer takes the freed slot while the host remains present");
            rooms.removeConnection("host-return");
            TestSupport.equals(5, rooms.participantCount(1), "host disconnect does not change buyer occupancy");
        }
    }

    private static void occupancyOrdering() {
        var model = new ClientAppModel(); Instant now = Instant.now();
        var room = ClientWireParser.auction(Map.of("auctionId", "1", "status", "OPEN", "version", "1",
                "maxParticipants", "2", "watcherCount", "0", "startTime", now.minusSeconds(1).toString(), "endTime", now.plusSeconds(60).toString()), "");
        model.applyOccupancy(1, 2, 4, now); model.applySnapshot(room, List.of(), now, false);
        TestSupport.check(model.auctionSnapshot().getFirst().isFull(), "event arriving before snapshot is retained");
        model.applyOccupancy(1, 1, 5, now); model.applyOccupancy(1, 2, 4, now);
        model.replaceAuctions(List.of(room), now);
        TestSupport.equals(1, model.auctionSnapshot().getFirst().getWatcherCount(), "stale events and lists cannot restore a full room");
    }
    private static List<ApiResponse> raceJoin(AuctionApi first, AuctionApi second, long room, String password) throws Exception {
        var one = first.join(room, password); var two = second.join(room, password);
        return List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
    }
    private static void awaitCount(List<WireMessage> events, long room, int count) throws Exception {
        TestSupport.waitUntil(() -> events.stream().anyMatch(event -> event.getType() == MessageType.ROOM_OCCUPANCY_CHANGED
                && event.get("auctionId").equals(Long.toString(room)) && event.get("watcherCount").equals(Integer.toString(count))), 5000, "outside client receives occupancy");
    }
    private static vn.ptit.btl16.client.model.ClientAuction lobby(AuctionApi api, long room) throws Exception {
        return ClientWireParser.auctions(ok(api.listAuctions()).getWireMessage().getData()).stream().filter(value -> value.getAuctionId() == room).findFirst().orElseThrow();
    }
    private static void close(JdbcAuctionRepository repository, long room) {
        var snapshot = repository.findAuctionById(room).orElseThrow();
        repository.closeAuction(new CloseAuctionCommit(room, snapshot.getCurrentWinnerId(), snapshot.getCurrentWinnerUsername(),
                snapshot.getCurrentPrice(), snapshot.getEndTime(), snapshot.getEndTime()));
    }
    private static long room(AuctionApi api, long product, Instant start, int capacity, String deposit, boolean privateRoom) throws Exception {
        return Long.parseLong(ok(api.createAuction(product, money("100"), money("10"), 5, privateRoom ? "PRIVATE" : "PUBLIC",
                privateRoom ? "secret" : "", false, start, capacity, money(deposit))).get("auctionId"));
    }
    private static long product(AuctionApi api, String code) throws Exception { return Long.parseLong(ok(TestMedia.create(api, code, code, TestMedia.DESCRIPTION)).get("productId")); }
    private static WebSocketClientTransport client(ServerApplication server) throws Exception {
        var client = new WebSocketClientTransport("ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws", 2097152, 5000, 10000);
        client.connect().get(10, TimeUnit.SECONDS); return client;
    }
    private static long register(WebSocketClientTransport client, String name) throws Exception {
        var api = new AccountApi(client); ok(api.register(name, "Password123!", name, "", ""));
        return Long.parseLong(ok(api.login(name, "Password123!")).get("userId"));
    }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception {
        var response = future.get(15, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + " " + response.getMessageText()); return response;
    }
    private static void error(String expected, CompletableFuture<ApiResponse> future) throws Exception {
        var response = future.get(15, TimeUnit.SECONDS); TestSupport.equals(expected, response.getErrorCode(), "request rejection");
    }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static void amount(String expected, BigDecimal actual, String message) { TestSupport.check(money(expected).compareTo(actual) == 0, message + ": " + actual); }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
