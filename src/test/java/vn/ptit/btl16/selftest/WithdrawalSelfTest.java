package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.common.wallet.WalletWire;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.account.repository.RepositoryException;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.wallet.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Real authenticated requests, durable approvals, concurrent debits, holds and transactional rollback. */
public final class WithdrawalSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String previous = System.getProperty("btl16.server.config"), previousData = System.getProperty("btl16.dataDir");
        Path folder = Files.createTempDirectory("btl16-withdrawals-"); Path file = folder.resolve("server.properties");
        Files.writeString(file, "server.tcp.enabled=false\nserver.websocket.port=0\nmedia.port=0\ndb.driver=org.h2.Driver\ndb.user=sa\ndb.name=withdrawals\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", file.toString()); System.setProperty("btl16.dataDir", folder.toString());
        try {
            ServerConfig config = ServerConfig.loadDefault(); var factory = new JdbcConnectionFactory(config);
            long owner; BigDecimal finalBalance; int finalEntries, finalRequests;
            try (var server = ServerApplication.create(config)) {
                server.start(); String endpoint = "ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws";
                try (var first = connect(endpoint); var second = connect(endpoint); var guest = connect(endpoint)) {
                    owner = register(first, "withdraw_owner"); long other = register(second, "withdraw_other");
                    var api = new WalletApi(first); var otherApi = new WalletApi(second); var wallets = server.wallets();
                    error(ErrorCode.AUTH_REQUIRED, new WalletApi(guest).withdraw(id(), money("1000")));
                    for (String invalid : List.of("-1", "0", "999.99", "1000000001", "1000.001")) {
                        error(ErrorCode.INVALID_AMOUNT, api.withdraw(id(), money(invalid)));
                    }
                    error(ErrorCode.VALIDATION_ERROR, api.withdraw("bad-id", money("1000")));
                    error(ErrorCode.INSUFFICIENT_FUNDS, api.withdraw(id(), money("1000")));
                    String topup = id(); ok(api.submit(topup, money("10000"))); wallets.review(topup, true);
                    error(ErrorCode.INSUFFICIENT_FUNDS, api.withdraw(id(), money("10001")));
                    String approved = id();
                    var submitted = ok(first.sendRequest(MessageType.REQUEST_WITHDRAWAL, Map.of("requestId", approved, "amount", "5000", "userId", Long.toString(other))).thenApply(ApiResponse::new));
                    TestSupport.equals(1, WalletWire.withdrawals(submitted.getWireMessage().getData()).size(), "withdrawal returned in wallet response");
                    TestSupport.check(wallets.withdrawals(other, false).isEmpty(), "forged userId cannot submit for another account");
                    amount("10000", wallets.account(owner).balance(), "submitting never debits money");
                    amount("0", wallets.account(owner).reserved(), "pending withdrawal does not reserve auction funds");
                    ok(api.withdraw(approved, money("5000")));
                    error(ErrorCode.VALIDATION_ERROR, api.withdraw(approved, money("4000")));
                    error(ErrorCode.VALIDATION_ERROR, otherApi.withdraw(approved, money("5000")));
                    error(ErrorCode.WITHDRAWAL_PENDING, api.withdraw(id(), money("1000")));
                    TestSupport.equals(1, wallets.withdrawals(owner, false).size(), "retries do not duplicate requests");
                    TestSupport.check(WalletWire.withdrawals(ok(otherApi.get()).getWireMessage().getData()).isEmpty(), "other users cannot see withdrawal history");
                    CountDownLatch gate = new CountDownLatch(1);
                    var one = CompletableFuture.runAsync(() -> { await(gate); wallets.reviewWithdrawal(approved, true, ""); });
                    var two = CompletableFuture.runAsync(() -> { await(gate); wallets.reviewWithdrawal(approved, true, ""); });
                    gate.countDown(); CompletableFuture.allOf(one, two).get(15, TimeUnit.SECONDS);
                    amount("5000", wallets.account(owner).balance(), "concurrent approval debits exactly once");
                    var debit = wallets.entries(owner).stream().filter(entry -> entry.type().equals("WITHDRAWAL")).toList();
                    TestSupport.equals(1, debit.size(), "exactly one withdrawal ledger entry"); amount("-5000", debit.getFirst().amount(), "ledger debit has the exact negative amount");
                    TestSupport.equals(1L, scalar(factory, "SELECT COUNT(*) FROM personal_notifications WHERE source_key='WITHDRAWAL:" + approved + "'"), "one notification for repeated approval");
                    ok(api.withdraw(approved, money("5000"))); amount("5000", wallets.account(owner).balance(), "retry after approval never debits again");
                    var filtered = ok(api.entries(Map.of("type", "WITHDRAWAL")));
                    TestSupport.equals(1, WalletWire.entries(filtered.getWireMessage().getData()).size(), "withdrawal ledger filter is supported");

                    String rejected = id(); ok(api.withdraw(rejected, money("2000")));
                    expect(ErrorCode.VALIDATION_ERROR, () -> wallets.reviewWithdrawal(rejected, false, ""));
                    wallets.reviewWithdrawal(rejected, false, "Không xác nhận được yêu cầu");
                    wallets.reviewWithdrawal(rejected, true, "");
                    amount("5000", wallets.account(owner).balance(), "rejection and attempted later approval never debit");
                    TestSupport.check(wallets.withdrawals(owner, false).stream().anyMatch(request -> request.id().equals(rejected) && request.rejectionReason().equals("Không xác nhận được yêu cầu")), "rejection reason retained");

                    reserve(factory, owner, "4000", "HOLD:ONE");
                    error(ErrorCode.INSUFFICIENT_FUNDS, api.withdraw(id(), money("2000")));
                    String held = id(); ok(api.withdraw(held, money("1000")));
                    reserve(factory, owner, "500", "HOLD:TWO");
                    expect(ErrorCode.INSUFFICIENT_FUNDS, () -> wallets.reviewWithdrawal(held, true, ""));
                    amount("5000", wallets.account(owner).balance(), "approval rechecks latest available funds");
                    TestSupport.check(wallets.withdrawals(owner, true).stream().anyMatch(request -> request.id().equals(held)), "insufficient approval leaves request pending");
                    reserve(factory, owner, "-4500", "HOLD:RELEASE");
                    wallets.reviewWithdrawal(held, true, ""); amount("4000", wallets.account(owner).balance(), "approval succeeds after holds release");

                    String rollback = id(); ok(api.withdraw(rollback, money("1000")));
                    change(factory, owner, BigDecimal.ZERO, BigDecimal.ZERO, "WITHDRAWAL", "WITHDRAWAL:" + rollback);
                    try { wallets.reviewWithdrawal(rollback, true, ""); throw new AssertionError("ledger conflict should fail"); }
                    catch (RepositoryException expected) { }
                    amount("4000", wallets.account(owner).balance(), "ledger failure rolls back the debit");
                    TestSupport.check(wallets.withdrawals(owner, true).stream().anyMatch(request -> request.id().equals(rollback) && request.reviewedAt() == null), "ledger failure rolls back request status");
                    TestSupport.equals(0L, scalar(factory, "SELECT COUNT(*) FROM personal_notifications WHERE source_key='WITHDRAWAL:" + rollback + "'"), "rollback creates no approval notification");
                    try (var connection = factory.openDatabase(); var query = connection.prepareStatement("DELETE FROM wallet_entries WHERE reference_key=?")) { query.setString(1, "WITHDRAWAL:" + rollback); query.executeUpdate(); }
                    wallets.reviewWithdrawal(rollback, true, ""); amount("3000", wallets.account(owner).balance(), "retry after rollback debits once");

                    String raced = id(); ok(api.withdraw(raced, money("2500")));
                    CountDownLatch race = new CountDownLatch(1);
                    var approval = CompletableFuture.supplyAsync(() -> attempt(race, () -> wallets.reviewWithdrawal(raced, true, "")));
                    var hold = CompletableFuture.supplyAsync(() -> attempt(race, () -> reserve(factory, owner, "2000", "HOLD:RACE")));
                    race.countDown();
                    TestSupport.check(approval.get(15, TimeUnit.SECONDS) ^ hold.get(15, TimeUnit.SECONDS), "only one competing withdrawal or reservation can consume the available balance");
                    var afterRace = wallets.account(owner);
                    TestSupport.check(afterRace.available().signum() >= 0 && afterRace.balance().compareTo(afterRace.reserved()) >= 0, "concurrent operations never overdraw or spend reserved money");
                    if (afterRace.reserved().signum() > 0) { reserve(factory, owner, afterRace.reserved().negate().toPlainString(), "HOLD:RACE:RELEASE"); wallets.reviewWithdrawal(raced, false, "Hủy yêu cầu sau kiểm tra đồng thời"); }
                    finalBalance = wallets.account(owner).balance(); finalEntries = wallets.entries(owner).size(); finalRequests = wallets.withdrawals(owner, false).size();
                }
            }
            try (var restarted = ServerApplication.create(config)) {
                TestSupport.equals(finalBalance, restarted.wallets().account(owner).balance(), "withdrawal balance survives restart");
                TestSupport.equals(finalEntries, restarted.wallets().entries(owner).size(), "withdrawal ledger survives restart");
                TestSupport.equals(finalRequests, restarted.wallets().withdrawals(owner, false).size(), "withdrawal statuses survive restart");
            }
            System.out.println("[PASS] WithdrawalSelfTest: authenticated privacy, validation, pending-only, approve/reject, exact-once concurrent debit, reserved funds, bid-hold race, rollback, notifications, ledger filters and restart");
        } finally { restore("btl16.server.config", previous); restore("btl16.dataDir", previousData); }
    }
    private static boolean attempt(CountDownLatch gate, Runnable action) {
        await(gate); try { action.run(); return true; } catch (AuctionException error) { TestSupport.equals(ErrorCode.INSUFFICIENT_FUNDS, error.getErrorCode(), "race loser cannot spend unavailable funds"); return false; }
    }
    private static void reserve(JdbcConnectionFactory factory, long user, String amount, String reference) { change(factory, user, BigDecimal.ZERO, money(amount), "RESERVE", reference); }
    private static void change(JdbcConnectionFactory factory, long user, BigDecimal amount, BigDecimal reserved, String type, String reference) {
        try (var connection = factory.openDatabase()) {
            connection.setAutoCommit(false);
            try { WalletTransactions.lockUsers(connection, user); WalletTransactions.change(connection, user, amount, reserved, type, reference, Instant.now()); connection.commit(); }
            catch (Exception error) { connection.rollback(); throw error; }
        } catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
    }
    private static void expect(ErrorCode code, Runnable action) { try { action.run(); throw new AssertionError("Expected " + code); } catch (AuctionException error) { TestSupport.equals(code, error.getErrorCode(), "expected rejection"); } }
    private static long scalar(JdbcConnectionFactory factory, String sql) throws Exception { try (var connection = factory.openDatabase(); var query = connection.prepareStatement(sql); var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); } }
    private static WebSocketClientTransport connect(String endpoint) throws Exception { var client = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000); client.connect().get(10, TimeUnit.SECONDS); return client; }
    private static long register(WebSocketClientTransport client, String username) throws Exception { var api = new AccountApi(client); ok(api.register(username, "Password123!", username, "", "")); return Long.parseLong(ok(api.login(username, "Password123!")).get("userId")); }
    private static BigDecimal money(String text) { return new BigDecimal(text); }
    private static void amount(String expected, BigDecimal actual, String message) { TestSupport.check(money(expected).compareTo(actual) == 0, message + ": " + actual); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception { var response = future.get(15, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + ": " + response.getMessageText()); return response; }
    private static void error(ErrorCode code, CompletableFuture<ApiResponse> future) throws Exception { TestSupport.equals(code.name(), future.get(15, TimeUnit.SECONDS).getErrorCode(), "expected withdrawal error"); }
    private static void await(CountDownLatch gate) { try { gate.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); } }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
