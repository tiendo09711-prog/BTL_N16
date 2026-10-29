package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import vn.ptit.btl16.client.fx.AuctionReceiptsPane;
import vn.ptit.btl16.client.network.*;
import vn.ptit.btl16.common.protocol.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

public final class AuctionReceiptUiSelfTest {
    private record Pending(MessageType type, Map<String, String> data, CompletableFuture<WireMessage> future) { }
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var network = new Network(); var session = new AtomicBoolean(true);
            var pane = fx(() -> new AuctionReceiptsPane(null, network, session::get));
            fx(() -> { pane.show(12); return null; });
            TestSupport.check(!fx(() -> labels(pane)).contains("B\u1ea1n \u0111\u00e3 th\u1eafng"), "pending request never displays a winner");
            TestSupport.equals("PARTICIPATED", network.peek(MessageType.MY_AUCTION_RECEIPTS).data().get("scope"), "default personal history filter");
            network.complete(MessageType.MY_AUCTION_RECEIPTS, displayPage());
            network.complete(MessageType.GET_PERSONAL_RECEIPT, receipt(12, "WINNER", "WON", "PAID")); drain();
            TestSupport.equals("B\u1ea1n \u0111\u00e3 th\u1eafng", fx(() -> ((Label) pane.lookup("#receipt-personal-result")).getText()), "confirmed personal winner rendered");
            TestSupport.check(fx(() -> ((Label) pane.lookup("#receipt-winner")).getText()).contains("Kh\u00e1ch #7"), "anonymous winner stays alias");
            TestSupport.check(fx(() -> ((Label) pane.lookup("#receipt-wallet")).getText()).contains("sổ giao dịch"), "payment source explicitly shown");
            TestSupport.check(fx(() -> labels(pane)).contains("Phí sàn (3%): 37.037,04"), "receipt displays exact seller fee");
            TestSupport.check(fx(() -> labels(pane)).contains("Người bán thực nhận: 1.197.530,85"), "receipt displays net proceeds");
            TestSupport.check(paneLookup(pane, "#receipt-ledger-link") == null && paneLookup(pane, "#receipt-ledger") == null, "receipt no longer includes wallet transaction controls");
            fx(() -> {
                pane.applyCss(); pane.layout();
                var drawer = pane.lookup("#receipt-drawer"); var table = history(pane);
                TestSupport.check(drawer.localToScene(drawer.getLayoutBounds()).getMinX() >= table.localToScene(table.getLayoutBounds()).getMaxX(), "receipt detail opens to the right of the list");
                TestSupport.equals(6, table.getColumns().size(), "view and more actions share one column");
                ((Button) pane.lookup("#receipt-close-detail")).fire();
                TestSupport.check(!drawer.isVisible() && !drawer.isManaged(), "closing drawer restores list width");
                pane.applyCss(); pane.layout();
                ((Button) pane.lookup("#receipt-more-12")).fire(); return null;
            });
            TestSupport.equals("12", network.peek(MessageType.GET_PERSONAL_RECEIPT).data().get("auctionId"), "three-dot action requests the clicked receipt");
            network.complete(MessageType.GET_PERSONAL_RECEIPT, receipt(12, "WINNER", "WON", "PAID")); drain();
            TestSupport.check(fx(() -> pane.lookup("#receipt-drawer").isVisible()), "three-dot action reopens drawer");
            screenshot(pane);
            fx(() -> { ((Stage) pane.getScene().getWindow()).setWidth(940); return null; });
            Thread.sleep(150); drain();
            fx(() -> {
                pane.applyCss(); pane.layout(); var drawer = pane.lookup("#receipt-drawer");
                var bounds = drawer.localToScene(drawer.getLayoutBounds());
                TestSupport.check(bounds.getMaxX() <= pane.getScene().getWidth() && history(pane).getWidth() >= 550,
                        "drawer and all row actions stay inside a narrow window");
                TestSupport.check(((ScrollPane) pane.lookup("#receipt-detail-scroll")).isFitToWidth(), "long receipt details scroll inside the right panel");
                ((Stage) pane.getScene().getWindow()).setWidth(1120); return null;
            });
            fx(() -> { scope(pane).setValue("WON"); scope(pane).setValue("HOSTED"); return null; });
            network.complete(MessageType.MY_AUCTION_RECEIPTS, page(20, "WINNER")); drain();
            TestSupport.equals(0, fx(() -> history(pane).getItems().size()), "old filter response discarded");
            network.complete(MessageType.MY_AUCTION_RECEIPTS, page(21, "HOST")); drain();
            TestSupport.equals("21", fx(() -> history(pane).getItems().get(0).get("auctionId")), "hosted filter uses matching response");
            fx(() -> { ((Button) pane.lookup("#receipts-next")).fire(); return null; });
            TestSupport.equals("21", network.peek(MessageType.MY_AUCTION_RECEIPTS).data().get("beforeId"), "history next uses server keyset");
            network.complete(MessageType.MY_AUCTION_RECEIPTS, page(11, "HOST")); drain();
            TestSupport.equals(1, fx(() -> history(pane).getItems().size()), "pagination stays bounded rather than appending forever");
            fx(() -> { ((Button) pane.lookup("#receipts-previous")).fire(); return null; });
            TestSupport.equals(Long.toString(Long.MAX_VALUE), network.peek(MessageType.MY_AUCTION_RECEIPTS).data().get("beforeId"), "previous page restores its original keyset cursor");
            network.complete(MessageType.MY_AUCTION_RECEIPTS, page(21, "HOST")); drain();
            fx(() -> { pane.loadReceipt(22); ((Button) pane.lookup("#receipt-close-detail")).fire(); return null; });
            network.complete(MessageType.GET_PERSONAL_RECEIPT, receipt(22, "WINNER", "WON", "PAID")); drain();
            TestSupport.check(!fx(() -> pane.lookup("#receipt-drawer").isVisible()) && paneLookup(pane, "#receipt-personal-result") == null, "late response cannot reopen a closed drawer");
            fx(() -> { pane.loadReceipt(13); pane.loadReceipt(14); return null; });
            network.complete(MessageType.GET_PERSONAL_RECEIPT, receipt(13, "WINNER", "WON", "PAID")); drain();
            TestSupport.check(paneLookup(pane, "#receipt-personal-result") == null, "old receipt response discarded");
            network.complete(MessageType.GET_PERSONAL_RECEIPT, receipt(14, "NOT_WON", "WON", "RELEASED")); drain();
            TestSupport.equals("B\u1ea1n kh\u00f4ng th\u1eafng", fx(() -> ((Label) pane.lookup("#receipt-personal-result")).getText()), "loser result distinct");
            fx(() -> { pane.loadReceipt(15); return null; }); network.complete(MessageType.GET_PERSONAL_RECEIPT, receipt(15, "WATCHED", "NO_BIDS", "NONE")); drain();
            TestSupport.check(fx(() -> labels(pane)).contains("không có lượt ra giá"), "no bids distinct from cancellation");
            fx(() -> { pane.loadReceipt(16); return null; }); network.complete(MessageType.GET_PERSONAL_RECEIPT, receipt(16, "HOST", "CANCELLED", "NONE")); drain();
            TestSupport.check(fx(() -> labels(pane)).contains("Phi\u00ean b\u1ecb h\u1ee7y"), "cancelled outcome rendered");
            fx(() -> { pane.loadReceipt(17); return null; }); network.complete(MessageType.GET_PERSONAL_RECEIPT, receipt(17, "WINNER", "WON", "REVIEW_REQUIRED")); drain();
            TestSupport.check(fx(() -> ((Label) pane.lookup("#receipt-wallet")).getText()).contains("ch\u01b0a x\u00e1c nh\u1eadn thanh to\u00e1n"), "legacy/review never rendered paid");
            network.connected = false; fx(() -> { pane.loadReceipt(18); return null; });
            TestSupport.check(paneLookup(pane, "#receipt-personal-result") == null, "offline request clears old confirmed result");
            TestSupport.check(fx(() -> labels(pane)).contains("M\u1ea5t k\u1ebft n\u1ed1i"), "offline receipt is not fabricated");
            network.connected = true;
            fx(() -> { pane.loadReceipt(18); return null; }); network.fail(MessageType.GET_PERSONAL_RECEIPT); drain();
            TestSupport.check(fx(() -> labels(pane)).contains("Khong co quyen"), "server access denial remains visible");
            fx(() -> { pane.loadReceipt(19); session.set(false); return null; }); network.complete(MessageType.GET_PERSONAL_RECEIPT, receipt(19, "WINNER", "WON", "PAID")); drain();
            TestSupport.check(paneLookup(pane, "#receipt-personal-result") == null, "prior session cannot render private receipt");
            Thread.sleep(1200); drain(); TestSupport.check(!fx(() -> pane.getScene().getWindow().isShowing()), "logout closes and clears receipt window");
            session.set(true);
            var reopened = fx(() -> new AuctionReceiptsPane(null, network, session::get));
            fx(() -> { reopened.show(12); return null; });
            var taxed = receipt(12, "WINNER", "WON", "PAID"); taxed.putAll(Map.of("baseFeeRate", "0.10", "feeDiscountRate", "0.05", "revenueRank", "1", "feeRate", "0.05", "platformFee", "61728.39", "sellerNet", "1172839.50"));
            network.complete(MessageType.GET_PERSONAL_RECEIPT, taxed); drain();
            TestSupport.check(fx(() -> labels(reopened)).contains("B\u1ea1n \u0111\u00e3 th\u1eafng"), "fresh window restores server receipt without room runtime");
            TestSupport.check(fx(() -> labels(reopened)).contains("Top 1 · Giảm 5 điểm %"), "receipt explains frozen revenue discount");
            TestSupport.check(fx(() -> labels(reopened)).contains("Thuế thực thu (5%): 61.728,39"), "new receipt displays effective tax separately from historical fees");
            fx(() -> { ((Stage) reopened.getScene().getWindow()).close(); return null; });
            System.out.println("[PASS] AuctionReceiptUiSelfTest: confirmed-only results, four personal roles, no-bid/cancel/reconciliation, detail drawer, offline/denial/retry, stale response/session guards, reopen and screenshot");
        } finally { Platform.exit(); }
    }
    private static Map<String, String> receipt(long room, String role, String outcome, String wallet) {
        Map<String, String> data = new LinkedHashMap<>(Map.of("auctionId", Long.toString(room), "productCode", "WATCH", "productName", "Đồng hồ cổ điển", "endedAt", "2026-10-25T10:05:00Z", "outcome", outcome, "personalRole", role, "winnerDisplay", outcome.equals("WON") ? "Kh\u00e1ch #7" : "", "finalPrice", outcome.equals("WON") ? "1234567.89" : ""));
        data.put("productId", "31"); data.put("productDescription", "Đồng hồ cổ, đã qua sử dụng. Máy hoạt động tốt, mặt sau có một vết xước nhỏ.");
        data.put("productThumbnail", Base64.getEncoder().encodeToString(TestMedia.png()));
        data.putAll(Map.of("totalBids", "125", "uniqueBidders", "8", "extensionCount", "2", "extensionsComplete", "true", "anonymousGuests", "true", "walletStatus", wallet, "heldAmount", "0", "entryCount", "1", "entriesHasMore", "false"));
        if ("PAID".equals(wallet)) { data.putAll(Map.of("feeRate", "0.03", "platformFee", "37037.04", "sellerNet", "1197530.85")); }
        data.putAll(Map.of("entry.0.id", "5", "entry.0.type", "PURCHASE", "entry.0.amount", "-1234567.89", "entry.0.reservedDelta", "-1234567.89", "entry.0.reference", "BUY:" + room, "entry.0.time", "2026-10-25T10:05:00Z")); return data;
    }
    private static Map<String, String> page(long room, String role) {
        Map<String, String> result = new LinkedHashMap<>(Map.of("count", "1", "nextBeforeId", Long.toString(room), "hasMore", "true"));
        receipt(room, role, "WON", "PAID").forEach((key, value) -> result.put("receipt.0." + key, value)); return result;
    }
    private static Map<String, String> displayPage() {
        Map<String, String> result = new LinkedHashMap<>(Map.of("count", "6", "nextBeforeId", "7", "hasMore", "false"));
        String[] names = {"Đồng hồ cổ điển", "Bình gốm men lam", "Tủ gỗ chạm khắc", "Ngựa đồng", "Tranh sơn dầu", "Lọ hoa cổ"};
        for (int index = 0; index < names.length; index++) {
            String prefix = "receipt." + index + "."; var row = receipt(12 - index, index == 0 ? "WINNER" : "WATCHED", index == 3 ? "NO_BIDS" : index == 5 ? "CANCELLED" : "WON", "PAID");
            row.put("productName", names[index]); row.forEach((key, value) -> result.put(prefix + key, value));
        }
        return result;
    }
    @SuppressWarnings("unchecked") private static ComboBox<String> scope(AuctionReceiptsPane pane) { return (ComboBox<String>) pane.lookup("#receipt-scope"); }
    @SuppressWarnings("unchecked") private static TableView<Map<String, String>> history(AuctionReceiptsPane pane) { return (TableView<Map<String, String>>) pane.lookup("#receipts-history"); }
    private static void screenshot(AuctionReceiptsPane pane) throws Exception {
        fx(() -> {
            pane.applyCss(); pane.layout(); var image = pane.snapshot(null, null);
            var buffered = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int row = 0; row < buffered.getHeight(); row++) {
                for (int column = 0; column < buffered.getWidth(); column++) { buffered.setRGB(column, row, image.getPixelReader().getArgb(column, row)); }
            }
            javax.imageio.ImageIO.write(buffered, "png", new java.io.File("target/auction-receipt.png")); return null;
        });
    }
    private static Object paneLookup(AuctionReceiptsPane pane, String id) throws Exception { return fx(() -> pane.lookup(id)); }
    private static String labels(AuctionReceiptsPane pane) { return pane.lookupAll(".label").stream().map(node -> ((Label) node).getText()).collect(java.util.stream.Collectors.joining(" ")); }
    private static <Result> Result fx(Callable<Result> action) throws Exception { var result = new CompletableFuture<Result>(); Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } }); return result.get(15, TimeUnit.SECONDS); }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }
    private static final class Network implements ClientTransport {
        private final List<Pending> pending = new ArrayList<>(); private boolean connected = true;
        synchronized Pending peek(MessageType type) { return pending.stream().filter(request -> request.type() == type).findFirst().orElseThrow(() -> new AssertionError("Missing request " + type)); }
        void complete(MessageType type, Map<String, String> values) { respond(type, values, true); }
        void fail(MessageType type) { respond(type, Map.of("message", "Khong co quyen", "errorCode", "AUCTION_FORBIDDEN"), false); }
        private void respond(MessageType type, Map<String, String> values, boolean success) {
            Pending request; synchronized (this) { request = peek(type); pending.remove(request); }
            Map<String, String> data = new LinkedHashMap<>(values); data.put("success", Boolean.toString(success)); request.future().complete(WireMessage.response(type, null, 1, data));
        }
        @Override public synchronized CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) { var future = new CompletableFuture<WireMessage>(); pending.add(new Pending(type, data, future)); return future; }
        @Override public CompletableFuture<Void> connect() { connected = true; return CompletableFuture.completedFuture(null); }
        @Override public void disconnect() { connected = false; }
        @Override public boolean isConnected() { return connected; }
        @Override public ConnectionState getState() { return connected ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED; }
        @Override public String getEndpoint() { return "test"; }
        @Override public void setEndpoint(String endpoint) { }
        @Override public CompletableFuture<WireMessage> ping() { return sendRequest(MessageType.PING, Map.of()); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { }
        @Override public void addStateListener(ConnectionStateListener listener) { }
        @Override public void close() { connected = false; }
    }
}
