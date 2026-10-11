package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.model.ClientProduct;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.ProductRestorationStatus;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.TestUserRepository;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.repository.TestAuctionRepository;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ProductRestorationNetworkSelfTest {
    private ProductRestorationNetworkSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        ServerConfig config = ServerConfig.forTransportTests(0, 0);
        var hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        TestAuctionRepository repository = new TestAuctionRepository();
        try (ServerApplication server = ServerApplication.createForTests(config,
                TestUserRepository.withDemoUsers(hasher), repository, hasher)) {
            server.start();
            String tcp = "tcp://127.0.0.1:" + server.getBoundPort();
            String websocket = "ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws";
            try (ClientTransport host = tcp(tcp); ClientTransport other = tcp(tcp); ClientTransport anonymous = tcp(tcp)) {
                verify(host, other, anonymous, "TCP", repository);
            }
            try (ClientTransport host = websocket(websocket); ClientTransport other = websocket(websocket);
                 ClientTransport anonymous = websocket(websocket)) {
                verify(host, other, anonymous, "WS", repository);
            }
        }
        System.out.println("[PASS] ProductRestorationNetworkSelfTest: TCP/WebSocket routing, auth, ownership, eligibility payload and restore/reauction");
    }

    private static void verify(ClientTransport hostTransport, ClientTransport otherTransport,
                               ClientTransport anonymousTransport, String prefix, TestAuctionRepository repository) throws Exception {
        hostTransport.connect().get(10, TimeUnit.SECONDS);
        otherTransport.connect().get(10, TimeUnit.SECONDS);
        anonymousTransport.connect().get(10, TimeUnit.SECONDS);
        AccountApi hostAccount = new AccountApi(hostTransport);
        AccountApi otherAccount = new AccountApi(otherTransport);
        ok(hostAccount.login("demo", "demo123"));
        ok(otherAccount.login("alice", "alice123"));
        AuctionApi host = new AuctionApi(hostTransport);
        AuctionApi other = new AuctionApi(otherTransport);
        AuctionApi anonymous = new AuctionApi(anonymousTransport);
        long productId = Long.parseLong(ok(TestMedia.create(host, prefix + "_RESTORE", "Network restore", "Original")).get("productId"));
        fail(other.restoreProduct(productId), "AUCTION_FORBIDDEN");
        fail(anonymous.restoreProduct(productId), "AUTH_REQUIRED");
        ApiResponse original = ok(host.restoreProduct(productId));
        TestSupport.equals(MessageType.RESTORE_PRODUCT_RESULT, original.getWireMessage().getType(), "restore reply uses its own message type");
        TestSupport.equals(original.get("updatedAt"), ok(host.restoreProduct(productId)).get("updatedAt"), "network active restore is idempotent");
        ok(host.deactivateProduct(productId));
        ClientProduct inactive = ownerProduct(host, productId);
        TestSupport.equals(ProductRestorationStatus.ELIGIBLE, inactive.getRestorationStatus(), "seller receives restoration eligibility");
        TestSupport.check(inactive.canRestore(), "eligible item enables the client restore action");
        fail(other.restoreProduct(productId), "AUCTION_FORBIDDEN");
        fail(anonymous.restoreProduct(productId), "AUTH_REQUIRED");
        fail(host.createAuction(productId, new BigDecimal("100"), new BigDecimal("10"), 1), "PRODUCT_INACTIVE");
        int beforeCount = repository.findAllAuctions().size();
        ApiResponse restored = ok(host.restoreProduct(productId));
        TestSupport.equals("true", restored.get("active"), "restoration persists active state");
        TestSupport.equals(beforeCount, repository.findAllAuctions().size(), "network restoration does not create a room");
        TestSupport.equals(ProductRestorationStatus.ACTIVE, ownerProduct(host, productId).getRestorationStatus(), "seller sees refreshed active state");
        long auctionId = Long.parseLong(ok(host.createAuction(productId,
                new BigDecimal("100"), new BigDecimal("10"), 1)).get("auctionId"));
        ok(host.restoreProduct(productId));
        ok(host.cancelAuction(auctionId));
        ok(host.deactivateProduct(productId));
        ok(host.restoreProduct(productId));
        fail(host.updateProduct(productId, "CHANGED", "Changed", "Changed"), "PRODUCT_FROZEN");
        long nextId = Long.parseLong(ok(host.createAuction(productId,
                new BigDecimal("100"), new BigDecimal("10"), 1)).get("auctionId"));
        TestSupport.check(nextId != auctionId, "new room has its own identity");
        ok(other.join(nextId));
        ok(other.bid(nextId, new BigDecimal("110")));
        ProductRestorationSelfTest.closeExpired(repository, nextId);
        ok(host.deactivateProduct(productId));
        ClientProduct sold = ownerProduct(host, productId);
        TestSupport.equals(ProductRestorationStatus.SOLD, sold.getRestorationStatus(), "seller sees why a sold item cannot be restored");
        TestSupport.check(!sold.canRestore(), "sold item disables client restore action");
        fail(host.restoreProduct(productId), "PRODUCT_ALREADY_SOLD");
        ok(hostAccount.logout());
        fail(host.restoreProduct(productId), "AUTH_REQUIRED");
        ok(otherAccount.logout());
    }

    private static ClientProduct ownerProduct(AuctionApi api, long productId) throws Exception {
        return ClientWireParser.products(ok(api.myProducts()).getWireMessage().getData()).stream()
                .filter(product -> product.getProductId() == productId).findFirst().orElseThrow();
    }

    private static ClientTransport tcp(String endpoint) {
        NetworkClient client = new NetworkClient(2_097_152, 3000, 10000);
        client.setEndpoint(endpoint);
        return client;
    }

    private static ClientTransport websocket(String endpoint) {
        return new WebSocketClientTransport(endpoint, 2_097_152, 3000, 10000);
    }

    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception {
        ApiResponse response = future.get(12, TimeUnit.SECONDS);
        TestSupport.check(response.isSuccess(), "network operation failed: " + response.getErrorCode() + " " + response.getMessageText());
        return response;
    }

    private static void fail(CompletableFuture<ApiResponse> future, String code) throws Exception {
        ApiResponse response = future.get(12, TimeUnit.SECONDS);
        TestSupport.check(!response.isSuccess(), "network request should be rejected");
        TestSupport.equals(code, response.getErrorCode(), "network domain rejection");
    }
}
