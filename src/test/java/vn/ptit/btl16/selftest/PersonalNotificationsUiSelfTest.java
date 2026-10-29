package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.fx.FxClientController;
import vn.ptit.btl16.client.fx.PersonalNotificationsPane;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.network.*;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class PersonalNotificationsUiSelfTest {
    private record Pending(MessageType type, Map<String, String> data, CompletableFuture<WireMessage> future) {
        void reply(Map<String, String> values) {
            var response = new LinkedHashMap<>(values); response.putIfAbsent("success", "true");
            future.complete(WireMessage.response(type, null, 1, response));
        }
    }
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            exerciseBell();
            var network = new Network(); var session = new AtomicBoolean(true); List<String> opened = new ArrayList<>();
            var pane = fx(() -> {
                var view = new PersonalNotificationsPane(null, network, session::get, (kind, room) -> opened.add(kind + ":" + room)); view.show();
                var pollField = PersonalNotificationsPane.class.getDeclaredField("poll"); pollField.setAccessible(true); ((javafx.animation.Timeline) pollField.get(view)).stop(); return view;
            });
            var initial = network.take(MessageType.GET_MY_NOTIFICATIONS);
            TestSupport.equals("6", initial.data().get("limit"), "six notifications per page"); initial.reply(page(false, true, 16, 15, 14, 12, 13, 9)); drain();
            TestSupport.equals(6, fx(() -> table(pane).getItems().size()), "stored notifications rendered");
            fire(pane, "notification-view-15");
            var receiptRequest = network.take(MessageType.GET_PERSONAL_RECEIPT);
            TestSupport.equals("15", receiptRequest.data().get("auctionId"), "detail requests the selected personal receipt");
            receiptRequest.reply(receipt(15, "WINNER", "WON", "PAID")); drain();
            TestSupport.check(fx(() -> labels(pane)).contains("Bạn đã thắng"), "winner banner uses confirmed receipt role");
            TestSupport.check(fx(() -> pane.lookup("#notification-product-image") instanceof ImageView), "first product image is decoded");
            TestSupport.check(fx(() -> labels(pane)).contains("3.900 ₫") && fx(() -> labels(pane)).contains("126.100 ₫"), "recorded fee and seller proceeds displayed");
            fx(() -> { pane.applyCss(); pane.layout(); return null; });
            screenshot(pane, "target/personal-notifications.png");
            fx(() -> {
                var tableBounds = table(pane).localToScene(table(pane).getBoundsInLocal());
                var drawer = pane.lookup("#notification-drawer"); var drawerBounds = drawer.localToScene(drawer.getBoundsInLocal());
                TestSupport.check(drawerBounds.getMinX() >= tableBounds.getMaxX(), "details sit to the right of the list");
                ((Hyperlink) pane.lookup("#notification-product-link")).fire();
                TestSupport.check(pane.lookup("#notification-product-description").isVisible(), "product link expands the actual product description");
                TestSupport.check(((ScrollPane) pane.lookup("#notification-detail-scroll")).isFitToWidth(), "details scroll vertically");
                ((Stage) pane.getScene().getWindow()).setWidth(970); return null;
            });
            for (int attempt = 0; attempt < 40 && fx(() -> pane.getWidth() > 1000); attempt++) { Thread.sleep(25); }
            drain(); screenshot(pane, "target/personal-notifications-narrow.png");
            fx(() -> {
                TestSupport.check(pane.getWidth() < 1000, "native stage actually resized before inspecting the layout");
                var drawer = pane.lookup("#notification-drawer");
                TestSupport.check(drawer.localToScene(drawer.getLayoutBounds()).getMaxX() <= pane.getWidth() + 1, "drawer remains inside a narrow window");
                var action = pane.lookup("#notification-view-15");
                TestSupport.check(action.localToScene(action.getLayoutBounds()).getMaxX() < drawer.localToScene(drawer.getLayoutBounds()).getMinX(), "row actions stay within the list at narrow widths");
                ((Stage) pane.getScene().getWindow()).setWidth(1180); return null;
            }); drain();
            fire(pane, "notifications-related"); TestSupport.equals(List.of("AUCTION_RESULT:15"), opened, "related action retains the existing receipt navigation");
            fx(() -> { ((CheckBox) pane.lookup("#notifications-select-all")).fire(); ((MenuButton) pane.lookup("#notifications-mark")).getItems().get(0).fire(); return null; });
            var markedIds = new HashSet<String>();
            for (int index = 0; index < 6; index++) {
                var mark = network.take(MessageType.READ_MY_NOTIFICATION); markedIds.add(mark.data().get("notificationId"));
                TestSupport.equals("true", mark.data().get("read"), "bulk mark explicitly reads each checked notification"); mark.reply(Map.of());
            }
            TestSupport.equals(Set.of("16", "15", "14", "12", "13", "9"), markedIds, "only the six displayed notifications are marked");
            drain(); network.take(MessageType.GET_MY_NOTIFICATIONS).reply(page(true, true, 16, 15, 14, 12, 13, 9)); drain();
            fx(() -> { var menu = (MenuButton) pane.lookup("#notification-more-15"); menu.getItems().get(1).fire(); return null; });
            var unread = network.take(MessageType.READ_MY_NOTIFICATION); TestSupport.equals("15", unread.data().get("notificationId"), "row menu targets its own notification");
            TestSupport.equals("false", unread.data().get("read"), "row menu can restore unread state"); unread.reply(Map.of()); drain();
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(page(false, true, 16, 15, 14, 12, 13, 9)); drain();
            fire(pane, "notifications-next"); var next = network.take(MessageType.GET_MY_NOTIFICATIONS);
            TestSupport.equals("9", next.data().get("beforeId"), "next page uses the server cursor"); next.reply(page(false, false, 8, 7)); drain();
            fire(pane, "notifications-previous"); var previous = network.take(MessageType.GET_MY_NOTIFICATIONS);
            TestSupport.equals(Long.toString(Long.MAX_VALUE), previous.data().get("beforeId"), "previous page restores the original cursor"); previous.reply(page(false, true, 16, 15, 14, 12, 13, 9)); drain();
            fx(() -> { ((CheckBox) pane.lookup("#notifications-unread")).fire(); return null; });
            var filtered = network.take(MessageType.GET_MY_NOTIFICATIONS); TestSupport.equals("true", filtered.data().get("unreadOnly"), "unread filtering remains on the server"); filtered.reply(page(false, true, 16, 15, 14, 12, 13, 9)); drain();
            fire(pane, "notification-view-15"); var lateClosed = network.take(MessageType.GET_PERSONAL_RECEIPT); fire(pane, "notification-close-detail");
            lateClosed.reply(receipt(15, "WINNER", "WON", "PAID")); drain();
            TestSupport.check(!fx(() -> pane.lookup("#notification-drawer").isVisible()), "late reply never reopens a closed drawer");
            fire(pane, "notification-view-16"); var old = network.take(MessageType.GET_PERSONAL_RECEIPT);
            fire(pane, "notification-view-14"); var latest = network.take(MessageType.GET_PERSONAL_RECEIPT);
            old.reply(receipt(16, "WINNER", "WON", "PAID")); drain();
            TestSupport.check(fx(() -> pane.lookup("#notification-result") == null), "superseded response never shows another result");
            latest.reply(receipt(14, "NOT_WON", "WON", "RELEASED")); drain();
            TestSupport.check(fx(() -> labels(pane)).contains("Bạn không thắng"), "nonwinner has a distinct result");
            fire(pane, "notification-view-12"); network.take(MessageType.GET_PERSONAL_RECEIPT).reply(receipt(12, "WATCHED", "NO_BIDS", "NONE")); drain();
            TestSupport.check(fx(() -> labels(pane)).contains("không có lượt ra giá"), "no-bid outcome stays distinct");
            fire(pane, "notification-view-13"); network.take(MessageType.GET_PERSONAL_RECEIPT).reply(receipt(13, "HOST", "CANCELLED", "NONE")); drain();
            TestSupport.check(fx(() -> labels(pane)).contains("Phiên bị hủy"), "cancelled outcome stays distinct");
            fire(pane, "notification-view-15"); network.take(MessageType.GET_PERSONAL_RECEIPT).reply(receipt(15, "WINNER", "WON", "REVIEW_REQUIRED")); drain();
            TestSupport.check(fx(() -> ((Label) pane.lookup("#notification-wallet-status")).getText()).contains("chưa xác nhận thanh toán"), "unreconciled receipt never claims paid");
            TestSupport.check(!fx(() -> labels(pane)).contains("Người bán thực nhận"), "unconfirmed payment has no fabricated proceeds");
            fire(pane, "notification-view-16"); network.take(MessageType.GET_PERSONAL_RECEIPT).reply(Map.of("success", "false", "message", "Không có quyền xem biên nhận")); drain();
            TestSupport.check(fx(() -> pane.lookup("#notification-detail-retry") != null && pane.lookup("#notification-result") == null), "access denial clears previous private receipt and offers retry");
            fire(pane, "notifications-refresh"); network.take(MessageType.GET_MY_NOTIFICATIONS).reply(generalPage()); drain();
            fire(pane, "notification-view-1"); fire(pane, "notification-general-link"); TestSupport.check(opened.contains("TOPUP_REJECTED:0"), "topup notification navigates to the wallet");
            fire(pane, "notification-view-2"); fire(pane, "notification-general-link"); TestSupport.check(opened.contains("OUTBID:15"), "outbid notification retains room access flow");
            fx(() -> { deleteItem(pane, "2").fire(); return null; });
            var failedDelete = network.take(MessageType.DELETE_MY_NOTIFICATION); TestSupport.equals("2", failedDelete.data().get("notificationId"), "delete targets its own row");
            failedDelete.reply(Map.of("success", "false")); drain();
            TestSupport.equals(2, fx(() -> table(pane).getItems().size()), "failed deletion preserves visible notifications");
            fire(pane, "notifications-refresh"); network.take(MessageType.GET_MY_NOTIFICATIONS).reply(page(false, false, 16)); drain();
            fire(pane, "notification-view-16"); var deletedDetail = network.take(MessageType.GET_PERSONAL_RECEIPT);
            fx(() -> { deleteItem(pane, "16").fire(); deleteItem(pane, "16").fire(); return null; });
            var deletion = network.take(MessageType.DELETE_MY_NOTIFICATION);
            TestSupport.check(network.pending.stream().noneMatch(p -> p.type() == MessageType.DELETE_MY_NOTIFICATION), "duplicate deletion is suppressed");
            deletion.reply(Map.of()); drain();
            deletedDetail.reply(receipt(16, "WINNER", "WON", "PAID")); drain();
            TestSupport.check(!fx(() -> pane.lookup("#notification-drawer").isVisible()), "deleted notification cannot reopen through a late detail reply");
            var afterDelete = network.take(MessageType.GET_MY_NOTIFICATIONS);
            TestSupport.equals(Long.toString(Long.MAX_VALUE), afterDelete.data().get("beforeId"), "deletion resets pagination to a valid page");
            afterDelete.reply(page(false, false)); drain();
            TestSupport.check(fx(() -> table(pane).getItems().isEmpty()), "deleting the last notification renders the empty state");
            bulkDelete(pane, network);
            fire(pane, "notifications-refresh"); network.take(MessageType.GET_MY_NOTIFICATIONS).reply(page(false, false, 16)); drain();
            fire(pane, "notification-view-16"); var privateReply = network.take(MessageType.GET_PERSONAL_RECEIPT); session.set(false);
            privateReply.reply(receipt(16, "WINNER", "WON", "PAID")); drain();
            TestSupport.check(!fx(() -> pane.getScene().getWindow().isShowing()) && fx(() -> table(pane).getItems().isEmpty()), "logout closes the window and clears private data");
            System.out.println("[PASS] PersonalNotificationsUiSelfTest: bell/unread synchronization, price-change link/direct join/leave previous room, stale replies, bulk deletion, receipt image/fee, paging/filter and session guards");
        } finally { Platform.exit(); }
    }
    private static void exerciseBell() throws Exception {
        var network = new Network(); var model = new ClientAppModel();
        model.applyIdentity(new ApiResponse(WireMessage.response(MessageType.LOGIN_RESULT, null, 1,
                Map.of("success", "true", "userId", "1", "username", "bell-user", "sessionToken", "bell-session"))));
        var owner = fx(Stage::new);
        var controller = fx(() -> {
            var view = new FxClientController(owner, ClientConfig.loadDefault(), model, network, new AccountApi(network), new AuctionApi(network));
            invoke(view, "buildScenes"); owner.setScene((Scene) field(view, "mainScene")); owner.show(); return view;
        });
        try {
            var bell = fx(() -> (Button) owner.getScene().lookup("#marketplace-notification-bell"));
            var badge = fx(() -> (Label) owner.getScene().lookup("#marketplace-notification-count"));
            TestSupport.check(fx(() -> owner.getScene().getRoot().lookupAll(".sidebar-navigation .button").stream()
                    .map(Button.class::cast).noneMatch(button -> button.getText().contains("Thông báo"))), "sidebar notification entry is removed");
            TestSupport.check(fx(() -> bell.getWidth() >= 36 && bell.getHeight() >= 36 && !badge.isVisible()), "larger bell starts without an empty badge");
            fx(() -> { invoke(controller, "refreshNotificationCount"); return null; });
            var initial = network.take(MessageType.GET_MY_NOTIFICATIONS);
            TestSupport.equals("1", initial.data().get("limit"), "bell only requests a minimal page for the global unread count");
            initial.reply(Map.of("unreadCount", "3")); drain();
            TestSupport.check(fx(() -> badge.isVisible() && badge.getText().equals("3")), "new notifications show the red count");
            fx(() -> { owner.getScene().getRoot().applyCss(); owner.getScene().getRoot().layout(); return null; });
            TestSupport.equals(javafx.scene.paint.Color.web("#dc2626"), fx(() -> badge.getBackground().getFills().get(0).getFill()), "unread badge is red");
            screenshot(owner.getScene().getRoot(), "target/notification-bell.png");
            fx(() -> { bell.fire(); return null; });
            var inbox = fx(() -> (PersonalNotificationsPane) Window.getWindows().stream().filter(window -> window != owner && window.isShowing())
                    .map(window -> window.getScene().getRoot()).filter(PersonalNotificationsPane.class::isInstance).findFirst().orElseThrow());
            fx(() -> { ((javafx.animation.Timeline) field(inbox, "poll")).stop(); return null; });
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(page(false, false, 16)); drain();
            TestSupport.equals("11", fx(badge::getText), "bell opens the existing inbox and uses its global unread count");
            fx(() -> { table(inbox).getSelectionModel().selectFirst(); ((MenuButton) inbox.lookup("#notifications-mark")).getItems().get(0).fire(); return null; });
            network.take(MessageType.READ_MY_NOTIFICATION).reply(Map.of()); drain();
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(page(true, false, 16)); drain();
            TestSupport.check(!fx(badge::isVisible), "marking the last unread notification read immediately hides the badge");
            fx(() -> { invoke(controller, "refreshNotificationCount"); return null; });
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(Map.of("unreadCount", "120")); drain();
            TestSupport.check(fx(() -> badge.getText().equals("99+") && bell.getTooltip().getText().contains("120")), "large counts stay compact with the exact total in the tooltip");
            fx(() -> { invoke(controller, "refreshNotificationCount"); return null; });
            var oldCount = network.take(MessageType.GET_MY_NOTIFICATIONS);
            fx(() -> { deleteItem(inbox, "16").fire(); return null; });
            network.take(MessageType.DELETE_MY_NOTIFICATION).reply(Map.of()); drain();
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(page(true, false)); drain();
            oldCount.reply(Map.of("unreadCount", "120")); drain();
            TestSupport.check(!fx(badge::isVisible), "deletion updates the bell and superseded polling cannot restore an old count");
            fire(inbox, "notifications-refresh");
            var priceChange = new LinkedHashMap<>(page(false, false, 17));
            priceChange.put("notification.0.kind", "PRICE_CHANGED"); priceChange.put("notification.0.auctionId", "15");
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(priceChange); drain();
            fx(() -> { inbox.applyCss(); inbox.layout(); ((Hyperlink) inbox.lookup("#notification-price-change-17")).fire(); return null; });
            TestSupport.check(fx(() -> labels(inbox)).contains("Đã có giá thay đổi"), "price-change notification opens its room details");
            TestSupport.equals("Quay lại phòng #15", fx(() -> ((Button) inbox.lookup("#notification-general-link")).getText()), "notification contains a direct room link");
            screenshot(inbox, "target/price-change-notification.png");
            var now = java.time.Instant.now();
            var snapshot = new LinkedHashMap<>(Map.of("auctionId", "15", "productId", "15", "productName", "Bình gốm",
                    "status", "OPEN", "hostUserId", "2", "startPrice", "100", "currentPrice", "120", "minBidIncrement", "10", "bidCount", "0", "serverNow", now.toString()));
            snapshot.put("startTime", now.minusSeconds(30).toString()); snapshot.put("endTime", now.plusSeconds(600).toString()); snapshot.put("hardEndTime", now.plusSeconds(1200).toString());
            var previousRoom = new LinkedHashMap<>(snapshot); previousRoom.put("auctionId", "12");
            fx(() -> { model.applySnapshot(vn.ptit.btl16.client.model.ClientWireParser.auction(previousRoom, ""), List.of(), now, true); return null; });
            fire(inbox, "notification-general-link");
            var leavePrevious = network.take(MessageType.LEAVE_AUCTION);
            TestSupport.equals("12", leavePrevious.data().get("auctionId"), "return link leaves the currently displayed room first");
            leavePrevious.reply(Map.of()); drain();
            var joining = network.take(MessageType.JOIN_AUCTION);
            TestSupport.equals("15", joining.data().get("auctionId"), "return link joins the notified room directly without a lobby search");
            joining.reply(snapshot); drain();
            TestSupport.equals(15L, fx(model::getJoinedAuctionId), "server-confirmed snapshot opens the target room");
            TestSupport.check(!fx(() -> inbox.getScene().getWindow().isShowing()), "following the link closes the inbox to reveal the room");
            fx(() -> { invoke(controller, "refreshNotificationCount"); return null; });
            var lateSession = network.take(MessageType.GET_MY_NOTIFICATIONS);
            fx(() -> { model.clearIdentity(); invoke(controller, "refreshNotificationCount"); return null; });
            lateSession.reply(Map.of("unreadCount", "9")); drain();
            TestSupport.check(!fx(badge::isVisible), "late replies after logout cannot restore the private unread count");
        } finally {
            fx(() -> { controller.close(); List.copyOf(Window.getWindows()).forEach(Window::hide); return null; });
        }
    }
    private static Object field(Object target, String name) throws Exception { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void invoke(Object target, String name) throws Exception { var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target); }
    private static void bulkDelete(PersonalNotificationsPane pane, Network network) throws Exception {
        fire(pane, "notifications-refresh"); network.take(MessageType.GET_MY_NOTIFICATIONS).reply(page(false, false, 31, 30, 29)); drain();
        TestSupport.check(!fx(() -> pane.lookup("#notifications-delete-selected").isVisible()), "bulk delete is hidden until checkboxes are selected");
        fx(() -> {
            pane.applyCss(); pane.layout();
            ((CheckBox) pane.lookup("#notification-check-31")).fire();
            ((CheckBox) pane.lookup("#notification-check-30")).fire();
            TestSupport.equals("Xóa đã chọn (2)", ((Button) pane.lookup("#notifications-delete-selected")).getText(), "bulk button shows selected count");
            return null;
        });
        screenshot(pane, "target/notifications-bulk-delete.png");
        fire(pane, "notifications-delete-selected");
        TestSupport.check(fx(() -> pane.lookup("#notifications-delete-selected").isDisabled()), "bulk button cannot submit a duplicate while deleting");
        var first = network.take(MessageType.DELETE_MY_NOTIFICATION); var second = network.take(MessageType.DELETE_MY_NOTIFICATION);
        TestSupport.equals(Set.of("31", "30"), Set.of(first.data().get("notificationId"), second.data().get("notificationId")), "only checked IDs are sent to the authenticated deletion endpoint");
        first.reply(Map.of()); second.reply(Map.of("success", "false")); drain();
        TestSupport.equals(2, fx(() -> table(pane).getItems().size()), "only confirmed deletions disappear after partial failure");
        TestSupport.check(fx(() -> table(pane).getItems().stream().anyMatch(row -> row.get("id").equals("29"))), "unchecked notification is preserved");
        TestSupport.equals("Xóa đã chọn (1)", fx(() -> ((Button) pane.lookup("#notifications-delete-selected")).getText()), "failed notification remains checked for retry");
        fire(pane, "notifications-delete-selected"); var retry = network.take(MessageType.DELETE_MY_NOTIFICATION);
        TestSupport.equals(second.data().get("notificationId"), retry.data().get("notificationId"), "retry only resubmits the failed deletion");
        retry.reply(Map.of()); drain(); network.take(MessageType.GET_MY_NOTIFICATIONS).reply(page(false, false, 29)); drain();
        TestSupport.check(!fx(() -> pane.lookup("#notifications-delete-selected").isVisible()), "success clears bulk selection");
        fx(() -> { ((CheckBox) pane.lookup("#notifications-select-all")).fire(); return null; });
        fire(pane, "notifications-delete-selected"); var all = network.take(MessageType.DELETE_MY_NOTIFICATION);
        TestSupport.equals("29", all.data().get("notificationId"), "select all targets the remaining page rows"); all.reply(Map.of()); drain();
        network.take(MessageType.GET_MY_NOTIFICATIONS).reply(page(false, false)); drain();
    }

    private static Map<String, String> page(boolean read, boolean more, int... ids) {
        var data = new LinkedHashMap<String, String>(); data.putAll(Map.of("count", Integer.toString(ids.length), "unreadCount", read ? "0" : "11", "hasMore", Boolean.toString(more), "nextBeforeId", Integer.toString(ids.length == 0 ? 0 : ids[ids.length - 1])));
        for (int index = 0; index < ids.length; index++) { String p = "notification." + index + "."; data.putAll(Map.of(p + "id", Integer.toString(ids[index]), p + "auctionId", Integer.toString(ids[index]), p + "kind", "AUCTION_RESULT", p + "createdAt", "2026-10-27T07:18:30Z", p + "read", Boolean.toString(read), p + "reference", "")); } return data;
    }
    private static Map<String, String> generalPage() {
        var data = page(false, false, 1, 2); data.putAll(Map.of("notification.0.kind", "TOPUP_REJECTED", "notification.0.auctionId", "", "notification.0.reference", "TOPUP-01", "notification.1.kind", "OUTBID", "notification.1.auctionId", "15")); return data;
    }
    private static Map<String, String> receipt(int room, String role, String outcome, String wallet) {
        var data = new LinkedHashMap<String, String>(); data.putAll(Map.of("auctionId", Integer.toString(room), "productName", "Đồng hồ cổ điển", "productCode", "DH15", "productDescription", "Đồng hồ cổ đã qua sử dụng, máy hoạt động tốt.", "productThumbnail", Base64.getEncoder().encodeToString(TestMedia.png()), "endedAt", "2026-10-27T07:18:30Z", "outcome", outcome, "personalRole", role, "walletStatus", wallet));
        data.putAll(Map.of("finalPrice", outcome.equals("WON") ? "130000" : "", "winnerDisplay", outcome.equals("WON") ? "Khách #7" : "", "feeRate", "0.03", "platformFee", "3900", "sellerNet", "126100", "totalBids", "12", "uniqueBidders", "3", "extensionCount", "1", "extensionsComplete", "true", "heldAmount", "0")); return data;
    }
    private static void fire(PersonalNotificationsPane pane, String id) throws Exception { fx(() -> { pane.applyCss(); pane.layout(); ((Button) pane.lookup("#" + id)).fire(); return null; }); drain(); }
    private static MenuItem deleteItem(PersonalNotificationsPane pane, String id) {
        pane.applyCss(); pane.layout();
        return ((MenuButton) pane.lookup("#notification-more-" + id)).getItems().stream().filter(item -> ("notification-delete-" + id).equals(item.getId())).findFirst().orElseThrow();
    }
    @SuppressWarnings("unchecked") private static TableView<Map<String, String>> table(PersonalNotificationsPane pane) { return (TableView<Map<String, String>>) pane.lookup("#personal-notifications-table"); }
    private static String labels(PersonalNotificationsPane pane) { return pane.lookupAll(".label").stream().map(node -> ((Label) node).getText()).collect(java.util.stream.Collectors.joining(" ")); }
    private static void screenshot(javafx.scene.Parent pane, String path) throws Exception {
        fx(() -> { pane.applyCss(); pane.layout(); var image = pane.snapshot(null, null); var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < output.getHeight(); y++) { for (int x = 0; x < output.getWidth(); x++) { output.setRGB(x, y, image.getPixelReader().getArgb(x, y)); } }
            javax.imageio.ImageIO.write(output, "png", new java.io.File(path)); return null; });
    }
    private static <T> T fx(Callable<T> action) throws Exception { var future = new CompletableFuture<T>(); Platform.runLater(() -> { try { future.complete(action.call()); } catch (Throwable error) { future.completeExceptionally(error); } }); return future.get(15, TimeUnit.SECONDS); }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }
    private static final class Network implements ClientTransport {
        private final List<Pending> pending = new ArrayList<>();
        synchronized Pending take(MessageType type) { var request = pending.stream().filter(value -> value.type() == type).findFirst().orElseThrow(() -> new AssertionError("Missing " + type)); pending.remove(request); return request; }
        @Override public synchronized CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) { var future = new CompletableFuture<WireMessage>(); pending.add(new Pending(type, Map.copyOf(data), future)); return future; }
        @Override public CompletableFuture<Void> connect() { return CompletableFuture.completedFuture(null); }
        @Override public void disconnect() { }
        @Override public boolean isConnected() { return true; }
        @Override public ConnectionState getState() { return ConnectionState.CONNECTED; }
        @Override public String getEndpoint() { return "test"; }
        @Override public void setEndpoint(String endpoint) { }
        @Override public CompletableFuture<WireMessage> ping() { return sendRequest(MessageType.PING, Map.of()); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { }
        @Override public void addStateListener(ConnectionStateListener listener) { }
        @Override public void close() { }
    }
}
