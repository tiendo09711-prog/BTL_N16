package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.ServerApplication;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class RoomCodesSelfTest {
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        String oldConfig = System.getProperty("btl16.server.config");
        String oldData = System.getProperty("btl16.dataDir");
        var directory = Files.createTempDirectory("btl16-room-codes-network-");
        var settings = directory.resolve("server.properties");
        Files.writeString(settings, "server.tcp.enabled=false\nserver.websocket.bindAddress=127.0.0.1\nserver.websocket.enabled=true\nserver.websocket.port=0\nmedia.port=0\n"
                + "db.driver=org.h2.Driver\ndb.name=room_codes\ndb.user=sa\ndb.password=\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", settings.toString());
        System.setProperty("btl16.dataDir", directory.toString());
        String persistedCode;
        long persistedRoom;
        try {
            var config = ServerConfig.loadDefault();
            try (var server = ServerApplication.create(config)) {
                server.start();
                try (var host = client(server); var buyer = client(server); var guest = client(server)) {
                    register(host, "code_host"); register(buyer, "code_buyer");
                    var sellerApi = new AuctionApi(host);
                    var buyerApi = new AuctionApi(buyer);
                    TestMedia.fundWallets(server.wallets());
                    var created = create(sellerApi, "ROOM_CODE_FIRST", false);
                    persistedCode = created.get("roomCode");
                    persistedRoom = Long.parseLong(created.get("auctionId"));
                    TestSupport.check(persistedCode.matches("[0-9]{6}"), "creation returns six-digit room code");
                    var codes = new HashSet<String>(); codes.add(persistedCode);
                    for (int index = 0; index < 4; index++) {
                        var other = create(sellerApi, "ROOM_CODE_OTHER_" + index, index == 0);
                        TestSupport.check(codes.add(other.get("roomCode")), "rooms with identical names have unique codes");
                        if (index == 0) {
                            var privateRoom = ok(buyerApi.findByRoomCode(other.get("roomCode")));
                            long privateId = Long.parseLong(privateRoom.get("auctionId"));
                            TestSupport.equals("ROOM_PASSWORD_REQUIRED", reply(buyerApi.join(privateId, "")).getErrorCode(), "code does not bypass private room password");
                            ok(buyerApi.join(privateId, "secret"));
                            ok(buyerApi.leave(privateId));
                        }
                    }
                    String missing = "000000";
                    while (codes.contains(missing)) { missing = String.format(java.util.Locale.ROOT, "%06d", Integer.parseInt(missing) + 1); }
                    for (String invalid : List.of("", "12345", "1234567", "abcdef", "12 456", missing)) {
                        var response = reply(buyerApi.findByRoomCode(invalid));
                        TestSupport.equals("AUCTION_NOT_FOUND", response.getErrorCode(), "invalid or unknown code is not a room ID");
                        TestSupport.equals("Ph\u00f2ng kh\u00f4ng t\u1ed3n t\u1ea1i", response.getMessageText(), "exact not-found feedback");
                    }
                    TestSupport.equals("AUTH_REQUIRED", reply(new AuctionApi(guest).findByRoomCode(persistedCode)).getErrorCode(), "lookup requires authentication");
                    var found = ok(buyerApi.findByRoomCode(persistedCode));
                    TestSupport.equals(Long.toString(persistedRoom), found.get("auctionId"), "code resolves exact room without text search");
                    var joined = ok(buyerApi.join(Long.parseLong(found.get("auctionId")), ""));
                    var parsed = ClientWireParser.auction(joined.getWireMessage().getData(), "");
                    TestSupport.equals(persistedCode, parsed.getRoomCode(), "joined room retains its code");
                    TestSupport.equals(persistedCode, parsed.withTiming(null, null, 2).withOccupancy(3, 10).getRoomCode(), "live timing and occupancy updates preserve leading zeroes and code");
                    var lobby = ClientWireParser.auctions(ok(buyerApi.listAuctions()).getWireMessage().getData());
                    TestSupport.equals(persistedCode, lobby.stream().filter(room -> room.getAuctionId() == persistedRoom).findFirst().orElseThrow().getRoomCode(), "lobby includes codes");
                }
            }
            try (var restarted = ServerApplication.create(config)) {
                restarted.start();
                try (var buyer = client(restarted)) {
                    ok(new AccountApi(buyer).login("code_buyer", "Password123!"));
                    var found = ok(new AuctionApi(buyer).findByRoomCode(persistedCode));
                    TestSupport.equals(Long.toString(persistedRoom), found.get("auctionId"), "same code resolves after server restart");
                    ok(new AuctionApi(buyer).join(persistedRoom, ""));
                }
            }
        } finally {
            restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData);
        }
        System.out.println("[PASS] RoomCodesSelfTest: unique codes, exact lookup, malformed/unknown codes, direct join, private room security and persistence");
    }

    private static ApiResponse create(AuctionApi seller, String productCode, boolean privateRoom) throws Exception {
        long product = Long.parseLong(ok(TestMedia.create(seller, productCode, "Same product name", TestMedia.DESCRIPTION)).get("productId"));
        return ok(seller.createAuction(product, new BigDecimal("100"), new BigDecimal("10"), 5,
                privateRoom ? "PRIVATE" : "PUBLIC", privateRoom ? "secret" : "", false, null, 20, BigDecimal.ZERO));
    }

    private static WebSocketClientTransport client(ServerApplication server) throws Exception {
        var client = new WebSocketClientTransport("ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws", 2097152, 5000, 10000);
        client.connect().get(10, TimeUnit.SECONDS); return client;
    }

    private static void register(WebSocketClientTransport client, String username) throws Exception {
        var accounts = new AccountApi(client);
        ok(accounts.register(username, "Password123!", username, "", ""));
        ok(accounts.login(username, "Password123!"));
    }

    private static ApiResponse reply(CompletableFuture<ApiResponse> future) throws Exception { return future.get(15, TimeUnit.SECONDS); }

    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception {
        var response = reply(future);
        TestSupport.check(response.isSuccess(), response.getErrorCode() + " " + response.getMessageText());
        return response;
    }

    private static void restore(String key, String value) {
        if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); }
    }
}
