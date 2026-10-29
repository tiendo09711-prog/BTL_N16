package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.common.wallet.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.*;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.wallet.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class VaultSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        Path folder = Files.createTempDirectory("btl16-vault-"); Path configFile = folder.resolve("server.properties");
        Files.writeString(configFile, "server.tcp.enabled=false\nserver.websocket.port=0\nmedia.port=0\ndb.driver=org.h2.Driver\ndb.user=sa\ndb.name=vault\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", configFile.toString()); System.setProperty("btl16.dataDir", folder.toString());
        try {
            var config = ServerConfig.loadDefault(); var factory = new JdbcConnectionFactory(config);
            long user; BigDecimal endWallet, endVault; int count;
            try (var server = ServerApplication.create(config)) {
                server.start(); String endpoint = "ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws";
                try (var first = connect(endpoint); var second = connect(endpoint); var guest = connect(endpoint)) {
                    user = register(first, "vault_owner"); register(second, "vault_other");
                    var api = new WalletApi(first); var other = new WalletApi(second); var wallets = server.wallets();
                    error(ErrorCode.AUTH_REQUIRED, new WalletApi(guest).vault(""));
                    error(ErrorCode.AUTH_REQUIRED, new WalletApi(guest).initializeVault(id(), money("1"), "012345", "012345"));
                    TestSupport.equals("false", ok(api.vault("")).get("configured"), "new user has no PIN");
                    for (String bad : List.of("12345", "1234567", "12345a", "１２３４５６")) { error(ErrorCode.VALIDATION_ERROR, api.initializeVault(id(), money("1"), bad, bad)); }
                    error(ErrorCode.VALIDATION_ERROR, api.initializeVault(id(), money("1"), "012345", "012346"));
                    error(ErrorCode.INVALID_AMOUNT, api.initializeVault(id(), money("0.001"), "012345", "012345"));
                    error(ErrorCode.INSUFFICIENT_FUNDS, api.initializeVault(id(), money("1000"), "012345", "012345"));
                    TestSupport.equals("false", ok(api.vault("")).get("configured"), "failed first transfer never creates a PIN");
                    String topup = id(); ok(api.submit(topup, money("20000"))); wallets.review(topup, true);
                    String initial = id(); String token = ok(api.initializeVault(initial, money("6000"), "012345", "012345")).get("vaultToken");
                    funds(wallets, user, "14000", "6000");
                    token = ok(api.initializeVault(initial, money("6000"), "012345", "012345")).get("vaultToken"); funds(wallets, user, "14000", "6000");
                    error(ErrorCode.VALIDATION_ERROR, api.initializeVault(initial, money("5000"), "012345", "012345"));
                    error(ErrorCode.VAULT_ALREADY_CONFIGURED, api.initializeVault(id(), money("1000"), "012345", "012345"));
                    try (var connection = factory.openDatabase(); var query = connection.prepareStatement("SELECT pin_hash,pin_salt FROM wallet_vaults WHERE user_id=?")) {
                        query.setLong(1, user); try (var rows = query.executeQuery()) { rows.next(); TestSupport.check(!rows.getString(1).equals("012345") && !rows.getString(2).equals("012345"), "database never stores the plaintext PIN"); }
                    }
                    error(ErrorCode.VAULT_LOCKED, other.transferVault(id(), "DEPOSIT", money("1"), token, ""));
                    var forged = ok(second.sendRequest(MessageType.GET_VAULT, Map.of("userId", Long.toString(user), "vaultToken", token)).thenApply(ApiResponse::new));
                    TestSupport.equals("0.00", forged.get("vaultBalance"), "changing userId cannot read someone else's vault");
                    TestSupport.equals("false", forged.get("unlocked"), "vault access is tied to the authenticated owner and connection");
                    String deposit = id(); ok(api.transferVault(deposit, "DEPOSIT", money("2000"), token, "")); ok(api.transferVault(deposit, "DEPOSIT", money("2000"), token, "")); funds(wallets, user, "12000", "8000");
                    error(ErrorCode.VALIDATION_ERROR, api.transferVault(deposit, "DEPOSIT", money("1000"), token, ""));
                    String withdrawal = id(); error(ErrorCode.VALIDATION_ERROR, api.transferVault(withdrawal, "WITHDRAW", money("1000"), token, ""));
                    error(ErrorCode.VAULT_PIN_INVALID, api.transferVault(withdrawal, "WITHDRAW", money("1000"), token, "111111")); funds(wallets, user, "12000", "8000");
                    ok(api.transferVault(withdrawal, "WITHDRAW", money("1000"), token, "012345")); ok(api.transferVault(withdrawal, "WITHDRAW", money("1000"), token, "012345")); funds(wallets, user, "13000", "7000");
                    ok(api.lockVault(token)); error(ErrorCode.VAULT_LOCKED, api.transferVault(id(), "DEPOSIT", money("1"), token, ""));
                    token = ok(api.unlockVault("012345")).get("vaultToken");
                    for (int index = 0; index < 4; index++) { error(ErrorCode.VAULT_PIN_INVALID, api.unlockVault("111111")); }
                    error(ErrorCode.VAULT_PIN_LOCKED, api.unlockVault("111111")); error(ErrorCode.VAULT_PIN_LOCKED, api.unlockVault("012345"));
                    try (var connection = factory.openDatabase(); var query = connection.prepareStatement("UPDATE wallet_vaults SET locked_until=? WHERE user_id=?")) { query.setTimestamp(1, java.sql.Timestamp.from(Instant.now().minusSeconds(1))); query.setLong(2, user); query.executeUpdate(); }
                    token = ok(api.unlockVault("012345")).get("vaultToken");
                    reserve(factory, user, "8000", "VAULT:TEST:HOLD");
                    error(ErrorCode.INSUFFICIENT_FUNDS, api.transferVault(id(), "DEPOSIT", money("6000"), token, "")); funds(wallets, user, "13000", "7000");
                    ok(api.transferVault(id(), "DEPOSIT", money("5000"), token, "")); funds(wallets, user, "8000", "12000");
                    error(ErrorCode.INSUFFICIENT_FUNDS, api.withdraw(id(), money("1000")));
                    try { reserve(factory, user, "1", "VAULT:TEST:OVERSPEND"); throw new AssertionError("vault must not fund a bid reservation"); } catch (AuctionException expected) { TestSupport.equals(ErrorCode.INSUFFICIENT_FUNDS, expected.getErrorCode(), "vault money is unavailable to bid holds"); }
                    ok(api.transferVault(id(), "WITHDRAW", money("1000"), token, "012345")); funds(wallets, user, "9000", "11000");
                    amount("8000", wallets.account(user).reserved(), "vault withdrawal preserves all bid holds"); reserve(factory, user, "-8000", "VAULT:TEST:RELEASE");

                    String cashout = id(); ok(api.withdraw(cashout, money("2000"))); String raceToken = token; CountDownLatch gate = new CountDownLatch(1); long owner = user;
                    var debitRace = CompletableFuture.supplyAsync(() -> attempt(gate, () -> wallets.reviewWithdrawal(cashout, true, "")));
                    var vaultRace = CompletableFuture.supplyAsync(() -> attempt(gate, () -> wallets.vault().transfer(owner, id(), "DEPOSIT", money("8000"), "")));
                    gate.countDown(); TestSupport.check(debitRace.get(15, TimeUnit.SECONDS) ^ vaultRace.get(15, TimeUnit.SECONDS), "only one competing cashout or vault transfer may spend the same available money");
                    if (wallets.withdrawals(user, true).stream().anyMatch(value -> value.id().equals(cashout))) { wallets.reviewWithdrawal(cashout, false, "Không đủ khả dụng sau chuyển két"); }
                    BigDecimal withdrawn = wallets.entries(user).stream().filter(entry -> entry.type().equals("WITHDRAWAL")).map(entry -> entry.amount().negate()).reduce(BigDecimal.ZERO, BigDecimal::add);
                    amount("20000", wallets.account(user).balance().add(wallets.account(user).vaultBalance()).add(withdrawn), "wallet plus vault plus approved cashouts conserve all funded money");

                    String failureId = id(); var beforeFailure = wallets.account(user);
                    sql(factory, "ALTER TABLE wallet_entries ADD CONSTRAINT vault_test_failure CHECK(reference_key<>'VAULT:" + failureId + "')");
                    try { wallets.vault().transfer(user, failureId, "DEPOSIT", money("100"), ""); throw new AssertionError("injected ledger failure must fail"); } catch (RepositoryException expected) { }
                    TestSupport.equals(beforeFailure.balance(), wallets.account(user).balance(), "ledger failure rolls back wallet debit");
                    TestSupport.equals(beforeFailure.vaultBalance(), wallets.account(user).vaultBalance(), "ledger failure rolls back vault credit");
                    sql(factory, "ALTER TABLE wallet_entries DROP CONSTRAINT vault_test_failure");
                    ok(api.transferVault(failureId, "DEPOSIT", money("100"), raceToken, ""));
                    String concurrentId = id(); CountDownLatch replayGate = new CountDownLatch(1);
                    var replayOne = CompletableFuture.runAsync(() -> { await(replayGate); wallets.vault().transfer(owner, concurrentId, "DEPOSIT", money("10"), ""); });
                    var replayTwo = CompletableFuture.runAsync(() -> { await(replayGate); wallets.vault().transfer(owner, concurrentId, "DEPOSIT", money("10"), ""); });
                    replayGate.countDown(); CompletableFuture.allOf(replayOne, replayTwo).get(15, TimeUnit.SECONDS);
                    TestSupport.equals(1L, wallets.entries(user).stream().filter(entry -> entry.reference().equals("VAULT:" + concurrentId)).count(), "concurrent retries create one transfer");
                    var history = WalletWire.entries(ok(api.entries(Map.of("type", "VAULT_WITHDRAWAL"))).getWireMessage().getData());
                    TestSupport.check(history.stream().allMatch(entry -> entry.amount().signum() > 0 && entry.vaultAfter().signum() > 0), "withdrawal history shows wallet credits and exact vault balances");
                    try (var connection = factory.openDatabase()) {
                        connection.setAutoCommit(false); WalletTransactions.lockUsers(connection, user);
                        WalletTransactions.change(connection, user, BigDecimal.ZERO, BigDecimal.ZERO, "LEGACY_REVIEW", "LEGACY:TEST:" + id(), Instant.now()); connection.commit();
                    }
                    for (boolean hideReservations : List.of(false, true)) {
                        Set<Long> seen = new HashSet<>(); long cursor = Long.MAX_VALUE; boolean more;
                        do {
                            var page = wallets.entryPage(user, null, "", null, null, cursor, 2, hideReservations, hideReservations);
                            var pageEntries = WalletWire.entries(page);
                            TestSupport.check(!pageEntries.isEmpty(), "filtered pages never become empty between visible transactions");
                            for (var entry : pageEntries) {
                                TestSupport.check(seen.add(entry.id()), "ledger pagination never duplicates a transaction");
                                if (hideReservations) { TestSupport.check(!Set.of("RESERVE", "RELEASE", "LEGACY_REVIEW").contains(entry.type()), "personal history hides internal entries before pagination"); }
                            }
                            more = Boolean.parseBoolean(page.get("hasMore")); cursor = Long.parseLong(page.get("nextBeforeId"));
                        } while (more);
                        long expected = wallets.entries(user).stream().filter(entry -> !hideReservations || !Set.of("RESERVE", "RELEASE", "LEGACY_REVIEW").contains(entry.type())).count();
                        TestSupport.equals(expected, (long) seen.size(), "all matching transactions remain reachable through filtered pagination");
                    }
                    var personalHistory = WalletWire.entries(ok(api.entries(Map.of("hideReservations", "true", "hideLegacyReview", "true"))).getWireMessage().getData());
                    TestSupport.check(personalHistory.stream().noneMatch(entry -> Set.of("RESERVE", "RELEASE", "LEGACY_REVIEW").contains(entry.type())), "personal history request hides internal entries");
                    TestSupport.check(wallets.entries(user).stream().anyMatch(entry -> entry.type().equals("RESERVE")), "reservation audit data remains available on the server");
                    TestSupport.check(wallets.entries(user).stream().anyMatch(entry -> entry.type().equals("LEGACY_REVIEW")), "legacy audit data remains available on the server");
                    TestSupport.check(WalletWire.entries(ok(other.entries(Map.of("type", "VAULT_DEPOSIT"))).getWireMessage().getData()).isEmpty(), "ledger filters never expose another owner's transfers");
                    ok(new AccountApi(first).logout()); ok(new AccountApi(first).login("vault_owner", "Password123!"));
                    error(ErrorCode.VAULT_LOCKED, api.transferVault(id(), "DEPOSIT", money("1"), token, ""));
                    endWallet = wallets.account(user).balance(); endVault = wallets.account(user).vaultBalance(); count = wallets.entries(user).size();
                }
            }
            try (var restarted = ServerApplication.create(config)) {
                TestSupport.equals(endWallet, restarted.wallets().account(user).balance(), "wallet persists after restart"); TestSupport.equals(endVault, restarted.wallets().account(user).vaultBalance(), "vault persists after restart");
                restarted.wallets().vault().unlock(user, "012345"); TestSupport.equals(count, restarted.wallets().entries(user).size(), "PIN verification never creates a monetary transaction");
            }
            System.out.println("[PASS] VaultSelfTest: authenticated grants, atomic first deposit/PIN, hashed PIN, per-withdrawal PIN, lockout, holds, cashout race, exact-once retries, rollback, conservation, filtered/paged ledger, logout and restart");
        } finally { restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData); }
    }
    private static boolean attempt(CountDownLatch gate, Runnable action) { await(gate); try { action.run(); return true; } catch (AuctionException error) { TestSupport.equals(ErrorCode.INSUFFICIENT_FUNDS, error.getErrorCode(), "race loser cannot spend unavailable money"); return false; } }
    private static void reserve(JdbcConnectionFactory factory, long user, String amount, String reference) {
        try (var connection = factory.openDatabase()) { connection.setAutoCommit(false); try { WalletTransactions.lockUsers(connection, user); WalletTransactions.change(connection, user, BigDecimal.ZERO, money(amount), "RESERVE", reference, Instant.now()); connection.commit(); } catch (Exception error) { connection.rollback(); throw error; } }
        catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
    }
    private static void sql(JdbcConnectionFactory factory, String sql) throws Exception { try (var connection = factory.openDatabase(); var statement = connection.createStatement()) { statement.executeUpdate(sql); } }
    private static void funds(WalletRepository wallets, long user, String wallet, String vault) { amount(wallet, wallets.account(user).balance(), "wallet balance"); amount(vault, wallets.account(user).vaultBalance(), "vault balance"); }
    private static void amount(String expected, BigDecimal actual, String message) { TestSupport.check(money(expected).compareTo(actual) == 0, message + ": " + actual); }
    private static BigDecimal money(String text) { return new BigDecimal(text); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static WebSocketClientTransport connect(String endpoint) throws Exception { var client = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000); client.connect().get(10, TimeUnit.SECONDS); return client; }
    private static long register(WebSocketClientTransport client, String username) throws Exception { var api = new AccountApi(client); ok(api.register(username, "Password123!", username, "", "")); return Long.parseLong(ok(api.login(username, "Password123!")).get("userId")); }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception { var response = future.get(15, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + ": " + response.getMessageText()); return response; }
    private static void error(ErrorCode expected, CompletableFuture<ApiResponse> future) throws Exception { TestSupport.equals(expected.name(), future.get(15, TimeUnit.SECONDS).getErrorCode(), "expected vault error"); }
    private static void await(CountDownLatch gate) { try { gate.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); } }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
