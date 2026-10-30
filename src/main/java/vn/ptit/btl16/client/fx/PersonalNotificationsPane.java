package vn.ptit.btl16.client.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionReceiptApi;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.common.protocol.MessageType;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

public final class PersonalNotificationsPane extends BorderPane {
    private static final int PAGE_SIZE = 6;
    private final Stage stage = new Stage();
    private final ClientTransport transport;
    private final BooleanSupplier sameSession;
    private final BiConsumer<String, Long> open;
    private final LongConsumer unreadCountChanged;
    private final TableView<Map<String, String>> table = new TableView<>();
    private final Label status = new Label(), pageNumber = new Label("1"), pageSummary = new Label();
    private final CheckBox unread = new CheckBox("Chỉ hiện chưa đọc"), selectAll = new CheckBox();
    private final Button next = action("", "right"), previous = action("", "left");
    private final Button related = action("Mở nội dung liên quan", "enter");
    private final Button deleteSelected = action("Xóa đã chọn", "trash");
    private final MenuButton markMenu = new MenuButton("Đánh dấu đã đọc / chưa đọc");
    private final Set<String> checked = new HashSet<>();
    private final List<Long> pageCursors = new ArrayList<>(List.of(Long.MAX_VALUE));
    private final BorderPane drawer = new BorderPane();
    private final VBox detail = new VBox(10);
    private final ScrollPane detailScroll = new ScrollPane(detail);
    private long generation, detailGeneration, markGeneration, cursor = Long.MAX_VALUE, nextCursor;
    private int pageIndex;
    private boolean loading, marking;
    private final Timeline poll = new Timeline(new KeyFrame(Duration.seconds(5), event -> {
        if (active() && cursor == Long.MAX_VALUE && !loading && !marking) { load(cursor, true); }
    }));

    public PersonalNotificationsPane(Window owner, ClientTransport transport, BooleanSupplier sameSession, BiConsumer<String, Long> open) {
        this(owner, transport, sameSession, open, count -> { });
    }

    public PersonalNotificationsPane(Window owner, ClientTransport transport, BooleanSupplier sameSession,
            BiConsumer<String, Long> open, LongConsumer unreadCountChanged) {
        this.transport = transport; this.sameSession = sameSession; this.open = open;
        this.unreadCountChanged = unreadCountChanged;
        stage.initOwner(owner); stage.setTitle("Thông báo cá nhân"); getStyleClass().add("notifications-page");
        configureTable();
        Button refresh = action("Trang đầu / Tải lại", "refresh"); refresh.setId("notifications-refresh"); refresh.setOnAction(event -> reset());
        unread.setId("notifications-unread"); unread.getStyleClass().add("notification-unread-filter"); unread.setOnAction(event -> reset());
        markMenu.setId("notifications-mark"); markMenu.setGraphic(MarketplaceIcons.icon("receipt", 13, "#775631")); markMenu.getStyleClass().add("notification-button");
        MenuItem read = new MenuItem("Đánh dấu đã đọc"), notRead = new MenuItem("Đánh dấu chưa đọc");
        read.setId("notifications-mark-read"); notRead.setId("notifications-mark-unread");
        read.setOnAction(event -> markRows(targetRows(), true)); notRead.setOnAction(event -> markRows(targetRows(), false)); markMenu.getItems().addAll(read, notRead);
        related.setId("notifications-related"); related.setOnAction(event -> openRelated(table.getSelectionModel().getSelectedItem()));
        deleteSelected.setId("notifications-delete-selected"); deleteSelected.getStyleClass().add("notification-delete-selected");
        deleteSelected.setOnAction(event -> deleteRows(table.getItems().stream().filter(row -> checked.contains(row.get("id"))).toList()));
        deleteSelected.setVisible(false); deleteSelected.setManaged(false);
        FlowPane tools = new FlowPane(8, 8, unread, refresh, markMenu, deleteSelected, related); tools.getStyleClass().add("notification-tools");
        status.setId("notifications-status"); status.getStyleClass().add("notification-status"); status.setWrapText(true);
        VBox top = new VBox(13, heading(), tools, status); top.getStyleClass().add("notification-top");
        BorderPane list = new BorderPane(table, top, null, footer(), null); list.getStyleClass().add("notification-list-card");
        list.setMinWidth(0); HBox.setHgrow(list, Priority.ALWAYS);
        configureDrawer();
        HBox body = new HBox(0, list, drawer); body.setAlignment(Pos.TOP_LEFT); setCenter(DesktopTheme.viewport(body));
        next.setId("notifications-next"); previous.setId("notifications-previous");
        next.setAccessibleText("Trang tiếp"); previous.setAccessibleText("Trang trước"); next.setDisable(true); previous.setDisable(true);
        next.setOnAction(event -> {
            if (nextCursor <= 0) { return; }
            while (pageCursors.size() > pageIndex + 1) { pageCursors.remove(pageCursors.size() - 1); }
            pageCursors.add(nextCursor); pageIndex++; clearSelection(); load(nextCursor);
        });
        previous.setOnAction(event -> { if (pageIndex > 0) { clearSelection(); load(pageCursors.get(--pageIndex)); } });
        stage.setOnHidden(event -> {
            generation++; detailGeneration++; markGeneration++; loading = false; marking = false; poll.stop();
            table.getItems().clear(); detail.getChildren().clear(); checked.clear(); status.setText("");
        });
        poll.setCycleCount(Timeline.INDEFINITE);
        Scene scene = new Scene(this, 1180, 740); DesktopTheme.apply(scene); stage.setScene(scene); stage.setMinWidth(960); stage.setMinHeight(580);
        updateSelection();
    }
    private Node heading() {
        StackPane bell = new StackPane(MarketplaceIcons.icon("bell", 29, "#8c5e27")); bell.getStyleClass().add("notification-bell");
        Label note = DesktopTheme.label("Theo dõi kết quả đấu giá và trạng thái nạp tiền. Bạn cần có quyền tham gia để mở nội dung phòng riêng.", "notification-note");
        VBox copy = new VBox(6, DesktopTheme.label("Thông báo của tôi", "notification-title"), note); copy.setMinWidth(0); HBox.setHgrow(copy, Priority.ALWAYS);
        Button close = action("", "close"); close.setId("notifications-close"); close.setAccessibleText("Đóng thông báo"); close.getStyleClass().add("notification-icon-button"); close.setOnAction(event -> stage.close());
        HBox heading = new HBox(17, bell, copy, close); heading.setAlignment(Pos.CENTER_LEFT); return heading;
    }
    private Node footer() {
        pageNumber.setId("notifications-page-number"); pageNumber.getStyleClass().add("notification-page-number");
        previous.getStyleClass().add("notification-icon-button"); next.getStyleClass().add("notification-icon-button");
        pageSummary.getStyleClass().add("notification-note"); pageSummary.setId("notifications-page-summary");
        HBox pages = new HBox(9, previous, pageNumber, next); pages.setAlignment(Pos.CENTER);
        HBox footer = new HBox(10, MarketplaceIcons.icon("list", 16, "#9a866c"), pageSummary, DesktopTheme.spacer(), pages, DesktopTheme.spacer());
        footer.setAlignment(Pos.CENTER_LEFT); footer.getStyleClass().add("notification-footer"); return footer;
    }
    private void configureTable() {
        table.setId("personal-notifications-table"); table.getStyleClass().add("notification-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); table.setFixedCellSize(64);
        TableColumn<Map<String, String>, String> checks = column("", 35, 32, row -> row.get("id"), row -> {
            CheckBox box = new CheckBox(); box.setSelected(checked.contains(row.get("id"))); box.setId("notification-check-" + row.get("id"));
            box.setAccessibleText("Chọn thông báo " + title(row.get("kind")));
            box.setOnAction(event -> { if (box.isSelected()) { checked.add(row.get("id")); } else { checked.remove(row.get("id")); } updateSelection(); }); return box;
        });
        selectAll.setId("notifications-select-all"); selectAll.setAccessibleText("Chọn tất cả thông báo trên trang"); checks.setGraphic(selectAll);
        selectAll.setOnAction(event -> { checked.clear(); if (selectAll.isSelected()) { table.getItems().forEach(row -> checked.add(row.get("id"))); } updateSelection(); table.refresh(); });
        column("TRẠNG THÁI", 108, 82, row -> isRead(row) ? "Đã đọc" : "Chưa đọc", row -> {
            Region dot = new Region(); dot.getStyleClass().addAll("notification-dot", isRead(row) ? "notification-dot-read" : "notification-dot-unread");
            HBox cell = new HBox(7, dot, DesktopTheme.label(isRead(row) ? "Đã đọc" : "Chưa đọc", "notification-cell-text")); cell.setAlignment(Pos.CENTER_LEFT); return cell;
        });
        column("NỘI DUNG", 280, 160, row -> title(row.get("kind")), row -> {
            if ("PRICE_CHANGED".equals(row.get("kind"))) {
                Hyperlink link = new Hyperlink(title(row.get("kind"))); link.setId("notification-price-change-" + row.get("id"));
                link.getStyleClass().add("notification-price-change"); link.setOnAction(event -> showDetail(currentRow(row))); return link;
            }
            return DesktopTheme.label(title(row.get("kind")), "notification-cell-text");
        });
        column("PHIÊN", 62, 45, row -> row.get("auctionId"), row -> DesktopTheme.label(row.get("auctionId").isBlank() ? "—" : row.get("auctionId"), "notification-cell-text"));
        column("THỜI GIAN", 135, 111, row -> time(row.get("createdAt"), false), row -> DesktopTheme.label(time(row.get("createdAt"), false), "notification-cell-time"));
        column("THAO TÁC", 94, 86, row -> "", row -> {
            MenuButton more = new MenuButton(); more.setGraphic(MarketplaceIcons.icon("more", 15, "#775631")); more.getStyleClass().addAll("notification-button", "notification-icon-button", "notification-more");
            more.setId("notification-more-" + row.get("id")); more.setAccessibleText("Thao tác thông báo");
            MenuItem detailItem = new MenuItem("Xem chi tiết"), markItem = new MenuItem(isRead(row) ? "Đánh dấu chưa đọc" : "Đánh dấu đã đọc"), relatedItem = new MenuItem("Mở nội dung liên quan");
            detailItem.setOnAction(event -> showDetail(currentRow(row)));
            markItem.setOnAction(event -> { var current = currentRow(row); if (current != null) { markRows(List.of(current), !isRead(current)); } });
            relatedItem.setOnAction(event -> openRelated(currentRow(row)));
            more.setOnShowing(event -> { var current = currentRow(row); if (current != null) { markItem.setText(isRead(current) ? "Đánh dấu chưa đọc" : "Đánh dấu đã đọc"); } });
            MenuItem deleteItem = new MenuItem("Xóa", MarketplaceIcons.icon("trash", 14, "#bf382d"));
            deleteItem.setId("notification-delete-" + row.get("id"));
            deleteItem.setOnAction(event -> deleteRow(currentRow(row)));
            more.getItems().addAll(detailItem, markItem, relatedItem, new SeparatorMenuItem(), deleteItem);
            return more;
        }).getStyleClass().add("notification-actions-column");
        table.setPlaceholder(DesktopTheme.label("Chưa có thông báo phù hợp", "notification-note"));
        table.getSelectionModel().selectedItemProperty().addListener((observable, old, row) -> updateSelection());
        BorderPane.setMargin(table, new Insets(0, 18, 0, 18));
    }
    private TableColumn<Map<String, String>, String> column(String title, double width, double min, Function<Map<String, String>, String> value, Function<Map<String, String>, Node> graphic) {
        TableColumn<Map<String, String>, String> column = new TableColumn<>(title); column.setPrefWidth(width); column.setMinWidth(min); column.setSortable(false); column.setReorderable(false);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setText(null);
                int index = getIndex();
                var row = empty || index < 0 || index >= table.getItems().size() ? null : table.getItems().get(index);
                setGraphic(row == null ? null : graphic.apply(row));
            }
        }); table.getColumns().add(column); return column;
    }
    private void configureDrawer() {
        Button close = action("", "close"); close.setId("notification-close-detail"); close.getStyleClass().add("notification-icon-button");
        close.setAccessibleText("Đóng chi tiết thông báo"); close.setOnAction(event -> closeDetail());
        HBox heading = new HBox(9, MarketplaceIcons.icon("receipt", 16, "#98672e"), DesktopTheme.label("Chi tiết thông báo", "notification-drawer-title"), DesktopTheme.spacer(), close);
        heading.setAlignment(Pos.CENTER_LEFT); heading.getStyleClass().add("notification-drawer-heading");
        detail.setId("notification-detail"); detail.getStyleClass().add("notification-detail-body");
        detailScroll.setFitToWidth(true); detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); detailScroll.setId("notification-detail-scroll");
        drawer.setTop(heading); drawer.setCenter(detailScroll); drawer.getStyleClass().add("notification-drawer"); drawer.setId("notification-drawer");
        drawer.prefWidthProperty().bind(javafx.beans.binding.Bindings.min(340, javafx.beans.binding.Bindings.max(310, widthProperty().multiply(.29))));
        drawer.minWidthProperty().bind(drawer.prefWidthProperty()); drawer.maxWidthProperty().bind(drawer.prefWidthProperty()); drawer.setMaxHeight(Double.MAX_VALUE);
        drawer.setManaged(false); drawer.setVisible(false);
    }
    public void show() { DesktopTheme.fitOnScreen(stage, 1180, 740); stage.show(); reset(); poll.play(); }
    private boolean active() { if (!sameSession.getAsBoolean()) { stage.close(); return false; } return stage.isShowing(); }
    private static boolean isRead(Map<String, String> row) { return Boolean.parseBoolean(row.get("read")); }
    private static String title(String kind) {
        return switch (kind == null ? "" : kind) {
            case "OUTBID" -> "Bạn đã bị vượt giá"; case "AUCTION_RESULT" -> "Phiên đấu giá đã chốt kết quả";
            case "PRICE_CHANGED" -> "Đã có giá thay đổi";
            case "TOPUP_APPROVED" -> "Yêu cầu nạp tiền mô phỏng đã được duyệt"; case "TOPUP_REJECTED" -> "Yêu cầu nạp tiền bị từ chối — xem lý do trong ví";
            case "WITHDRAWAL_APPROVED" -> "Yêu cầu rút tiền đã được duyệt và trừ khỏi ví";
            case "WITHDRAWAL_REJECTED" -> "Yêu cầu rút tiền bị từ chối — xem lý do trong ví";
            default -> "Thông báo";
        };
    }
    private void reset() { pageIndex = 0; pageCursors.clear(); pageCursors.add(Long.MAX_VALUE); clearSelection(); load(Long.MAX_VALUE); }
    private void clearSelection() { checked.clear(); table.getSelectionModel().clearSelection(); closeDetail(); updateSelection(); }
    private void closeDetail() { detailGeneration++; detail.getChildren().clear(); drawer.setVisible(false); drawer.setManaged(false); }
    private void updateSelection() {
        selectAll.setSelected(!table.getItems().isEmpty() && checked.size() == table.getItems().size());
        selectAll.setIndeterminate(!checked.isEmpty() && checked.size() < table.getItems().size());
        markMenu.setDisable(marking || (checked.isEmpty() && table.getSelectionModel().getSelectedItem() == null));
        deleteSelected.setText("Xóa đã chọn (" + checked.size() + ")");
        deleteSelected.setVisible(!checked.isEmpty()); deleteSelected.setManaged(!checked.isEmpty());
        deleteSelected.setDisable(marking || checked.isEmpty());
        related.setDisable(table.getSelectionModel().getSelectedItem() == null);
    }
    private List<Map<String, String>> targetRows() {
        if (!checked.isEmpty()) { return table.getItems().stream().filter(row -> checked.contains(row.get("id"))).toList(); }
        var row = table.getSelectionModel().getSelectedItem(); return row == null ? List.of() : List.of(row);
    }
    private Map<String, String> currentRow(Map<String, String> row) {
        return table.getItems().stream().filter(current -> current.get("id").equals(row.get("id"))).findFirst().orElse(null);
    }
    private void load(long before) { load(before, false); }
    private void load(long before, boolean quiet) {
        if (!active()) { return; } cursor = before; long token = ++generation; loading = true; next.setDisable(true); previous.setDisable(true);
        if (!quiet) { status.setText("Đang tải thông báo…"); }
        transport.sendRequest(MessageType.GET_MY_NOTIFICATIONS, Map.of("beforeId", Long.toString(before), "unreadOnly", Boolean.toString(unread.isSelected()), "limit", Integer.toString(PAGE_SIZE)))
                .whenComplete((wire, error) -> Platform.runLater(() -> {
                    if (!active() || token != generation) { return; } loading = false;
                    var response = wire == null ? null : new ApiResponse(wire);
                    if (error != null || response == null || !response.isSuccess()) { status.setText("Không tải được thông báo. Bấm Trang đầu / Tải lại để thử lại."); previous.setDisable(pageIndex == 0); return; }
                    String selectedId = table.getSelectionModel().getSelectedItem() == null ? "" : table.getSelectionModel().getSelectedItem().get("id");
                    List<Map<String, String>> rows = new ArrayList<>();
                    for (int index = 0; index < Integer.parseInt(response.get("count")); index++) {
                        Map<String, String> row = new LinkedHashMap<>(); String prefix = "notification." + index + ".";
                        for (String key : List.of("id", "kind", "auctionId", "reference", "createdAt", "read")) { row.put(key, response.get(prefix + key)); } rows.add(row);
                    }
                    checked.retainAll(rows.stream().map(row -> row.get("id")).toList()); table.getItems().setAll(rows); table.refresh();
                    rows.stream().filter(row -> row.get("id").equals(selectedId)).findFirst().ifPresent(row -> table.getSelectionModel().select(row));
                    nextCursor = Long.parseLong(response.get("nextBeforeId")); next.setDisable(!Boolean.parseBoolean(response.get("hasMore"))); previous.setDisable(pageIndex == 0);
                    pageNumber.setText(Integer.toString(pageIndex + 1)); pageSummary.setText("Hiển thị " + rows.size() + " thông báo · Trang " + (pageIndex + 1));
                    status.setText(response.get("unreadCount") + " thông báo chưa đọc" + (rows.isEmpty() ? " · Không có thông báo phù hợp" : "")); updateSelection();
                    unreadCountChanged.accept(Long.parseLong(response.get("unreadCount")));
                }));
    }
    private void markRows(List<Map<String, String>> rows, boolean read) {
        if (rows.isEmpty() || !active() || marking) { return; }
        marking = true; generation++; loading = false; long token = ++markGeneration; updateSelection(); status.setText("Đang cập nhật trạng thái thông báo…");
        var requests = rows.stream().map(row -> transport.sendRequest(MessageType.READ_MY_NOTIFICATION, Map.of("notificationId", row.get("id"), "read", Boolean.toString(read)))).toList();
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (!active() || token != markGeneration) { return; } marking = false; updateSelection();
            if (error != null || requests.stream().anyMatch(request -> request.join() == null || !new ApiResponse(request.join()).isSuccess())) {
                status.setText("Chưa cập nhật được tất cả thông báo. Bấm Trang đầu / Tải lại để kiểm tra và thử lại."); return;
            }
            checked.clear(); load(cursor);
        }));
    }
    private void openRelated(Map<String, String> row) {
        if (row != null && active()) {
            String kind = row.get("kind");
            long auctionId = row.get("auctionId").isBlank() ? 0L : Long.parseLong(row.get("auctionId"));
            stage.close();
            open.accept(kind, auctionId);
        }
    }

    private void deleteRow(Map<String, String> row) {
        if (row != null) { deleteRows(List.of(row)); }
    }

    private void deleteRows(List<Map<String, String>> rows) {
        if (rows.isEmpty() || !active() || marking) { return; }
        boolean nextWasDisabled = next.isDisabled();
        marking = true; generation++; loading = false; long token = ++markGeneration;
        closeDetail();
        next.setDisable(true); previous.setDisable(true); updateSelection(); status.setText("Đang xóa thông báo…");
        Map<String, CompletableFuture<Boolean>> requests = new LinkedHashMap<>();
        for (var row : rows) {
            requests.computeIfAbsent(row.get("id"), id -> transport.sendRequest(MessageType.DELETE_MY_NOTIFICATION, Map.of("notificationId", id))
                    .handle((wire, error) -> error == null && wire != null && new ApiResponse(wire).isSuccess()));
        }
        CompletableFuture.allOf(requests.values().toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> Platform.runLater(() -> {
                    if (!active() || token != markGeneration) { return; }
                    Set<String> deleted = new HashSet<>();
                    requests.forEach((id, result) -> { if (!result.isCompletedExceptionally() && Boolean.TRUE.equals(result.getNow(false))) { deleted.add(id); } });
                    table.getItems().removeIf(item -> deleted.contains(item.get("id"))); checked.removeAll(deleted);
                    marking = false; table.refresh(); updateSelection();
                    if (deleted.size() != requests.size()) {
                        status.setText("Đã xóa " + deleted.size() + "/" + requests.size() + " thông báo. Những thông báo chưa xóa được vẫn được giữ lại để thử lại.");
                        pageSummary.setText("Hiển thị " + table.getItems().size() + " thông báo · Trang " + (pageIndex + 1));
                        next.setDisable(nextWasDisabled); previous.setDisable(pageIndex == 0); return;
                    }
                    reset();
                }));
    }
    private void showDetail(Map<String, String> row) {
        if (row == null || !active()) { return; }
        table.getSelectionModel().select(row); long token = ++detailGeneration;
        drawer.setVisible(true); drawer.setManaged(true); detailScroll.setVvalue(0); detail.getChildren().clear();
        if (!"AUCTION_RESULT".equals(row.get("kind"))) { renderGeneral(row); return; }
        detail.getChildren().add(DesktopTheme.label("Đang tải kết quả đã chốt…", "notification-note"));
        new AuctionReceiptApi(transport).get(Long.parseLong(row.get("auctionId"))).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!active() || token != detailGeneration) { return; }
            if (error != null || response == null || !response.isSuccess()) {
                Label message = DesktopTheme.label(error == null && response != null ? response.getMessageText() : "Không tải được chi tiết. Hãy thử lại.", "notification-note");
                Button retry = action("Thử lại", "refresh"); retry.setId("notification-detail-retry"); retry.setOnAction(event -> showDetail(row)); detail.getChildren().setAll(message, retry); return;
            }
            renderReceipt(response.getWireMessage().getData());
        }));
    }
    private void renderGeneral(Map<String, String> row) {
        boolean topup = row.get("kind").startsWith("TOPUP") || row.get("kind").startsWith("WITHDRAWAL");
        boolean priceChanged = "PRICE_CHANGED".equals(row.get("kind"));
        detail.getChildren().setAll(resultCard(title(row.get("kind")), topup ? "Ví mô phỏng" : "Phòng đấu giá #" + row.get("auctionId"), topup ? "wallet" : "bell", "neutral"),
                card(field("clock", "Thời gian", time(row.get("createdAt"), true)), field(topup ? "receipt" : "gavel", topup ? "Mã yêu cầu" : "Mã phiên", topup ? row.get("reference") : "#" + row.get("auctionId"))),
                DesktopTheme.label(topup ? "Mở ví để xem giao dịch và lý do xử lý yêu cầu nạp/rút tiền." : priceChanged
                        ? "Phòng bạn đã đặt giá có mức giá mới. Bấm bên dưới để quay lại phòng và xem giá hiện tại."
                        : "Mở phòng để xem giá hiện tại. Phòng riêng vẫn yêu cầu quyền tham gia.", "notification-note"));
        Button link = action(topup ? "Mở ví của tôi" : "Quay lại phòng #" + row.get("auctionId"), topup ? "wallet" : "enter"); link.setId("notification-general-link"); link.setOnAction(event -> openRelated(row)); detail.getChildren().add(link);
    }
    private void renderReceipt(Map<String, String> data) {
        String outcome = data.getOrDefault("outcome", ""), role = data.getOrDefault("personalRole", "");
        boolean won = "WON".equals(outcome) && "WINNER".equals(role), paid = "PAID".equals(data.get("walletStatus"));
        String result = "WON".equals(outcome) ? AuctionReceiptsPane.roleText(role) : AuctionReceiptsPane.outcomeText(outcome);
        Node banner = resultCard(result, AuctionReceiptsPane.outcomeText(outcome), won ? "trophy" : "gavel", won ? "won" : "NOT_WON".equals(role) ? "lost" : "neutral"); banner.setId("notification-result");
        Label description = DesktopTheme.label(data.getOrDefault("productDescription", "").isBlank() ? "Chưa có mô tả sản phẩm trong biên nhận." : data.get("productDescription"), "notification-note");
        VBox expandedProduct = card(field("box", "Mã sản phẩm", data.getOrDefault("productCode", "—")), description); expandedProduct.setId("notification-product-description"); expandedProduct.setManaged(false); expandedProduct.setVisible(false);
        Hyperlink productLink = new Hyperlink("Xem sản phẩm →"); productLink.setId("notification-product-link"); productLink.getStyleClass().add("notification-product-link");
        productLink.setOnAction(event -> { boolean visible = !expandedProduct.isVisible(); expandedProduct.setVisible(visible); expandedProduct.setManaged(visible); productLink.setText(visible ? "Thu gọn sản phẩm ↑" : "Xem sản phẩm →"); });
        Label name = DesktopTheme.label("#" + data.get("auctionId") + " · " + data.get("productName"), "notification-product-name"); name.setId("notification-product-name");
        Label code = DesktopTheme.label("Mã sản phẩm: " + data.getOrDefault("productCode", "—"), "notification-note");
        VBox productCopy = new VBox(6, name, code, productLink); productCopy.setMinWidth(0); HBox.setHgrow(productCopy, Priority.ALWAYS);
        HBox product = new HBox(12, preview(data), productCopy); product.setAlignment(Pos.CENTER_LEFT); product.getStyleClass().add("notification-product-card");
        Label badge = DesktopTheme.label(result, "notification-result-badge " + (won ? "notification-badge-won" : "notification-badge-neutral"));
        VBox session = card(field("calendar", "Mã phiên", "#" + data.get("auctionId")), field("clock", "Thời gian kết thúc", time(data.get("endedAt"), true)), field("receipt", "Trạng thái", badge));
        VBox transactions = card(field("box", "Tên sản phẩm", "#" + data.get("auctionId") + " · " + data.get("productName")), field("profile", "Người thắng", data.getOrDefault("winnerDisplay", "").isBlank() ? "Không có" : data.get("winnerDisplay")), field("wallet", "Giá chốt", money(data.get("finalPrice"))));
        transactions.setId("notification-transactions");
        if (paid && data.containsKey("platformFee") && data.containsKey("feeRate") && data.containsKey("sellerNet")) {
            String percent = new BigDecimal(data.get("feeRate")).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
            boolean hasFeeBreakdown = data.containsKey("baseFeeRate");
            if (hasFeeBreakdown) {
                String base = new BigDecimal(data.get("baseFeeRate")).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
                String reduction = new BigDecimal(data.get("feeDiscountRate")).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
                transactions.getChildren().add(field("gavel", "Tỷ lệ phí sàn ban đầu", base + "%"));
                transactions.getChildren().add(field("trophy", "Ưu đãi doanh thu", "0".equals(data.get("revenueRank")) ? "Chưa thuộc Top 10" : "Top " + data.get("revenueRank") + " · Giảm " + reduction + " điểm %"));
            }
            transactions.getChildren().addAll(field("gavel", "Phí sàn do người bán trả (" + percent + "%)", money(data.get("platformFee"))), field("wallet", "Người mua thanh toán", money(data.get("finalPrice"))), field("wallet", "Người bán thực nhận", money(data.get("sellerNet"))));
        }
        HBox transactionTitle = new HBox(7, MarketplaceIcons.icon("receipt", 14, "#875e2d"), DesktopTheme.label("Thông tin giao dịch", "notification-section-title")); transactionTitle.setAlignment(Pos.CENTER_LEFT);
        HBox stats = new HBox(statistic("gavel", "Tổng lượt ra giá", data.get("totalBids")), statistic("users", "Người ra giá", data.get("uniqueBidders")), statistic("clock", "Gia hạn", data.get("extensionCount"))); stats.getStyleClass().add("notification-stats");
        Label walletStatus = DesktopTheme.label(walletText(data.get("walletStatus")), "notification-note"); walletStatus.setId("notification-wallet-status");
        VBox walletCopy = new VBox(3, DesktopTheme.label("Ví mô phỏng", "notification-section-title"), walletStatus); walletCopy.setMinWidth(0); HBox.setHgrow(walletCopy, Priority.ALWAYS);
        Label held = DesktopTheme.label("Còn giữ: " + money(data.get("heldAmount")), "notification-note");
        HBox wallet = new HBox(8, MarketplaceIcons.icon("wallet", 21, "#a16b2b"), walletCopy, held); wallet.setAlignment(Pos.CENTER_LEFT); wallet.getStyleClass().add("notification-wallet");
        detail.getChildren().setAll(banner, product, expandedProduct, session, transactionTitle, transactions, stats, wallet);
        if (!"true".equals(data.get("extensionsComplete"))) { detail.getChildren().add(DesktopTheme.label("Nhật ký cũ chưa đủ dữ liệu gia hạn.", "notification-note")); }
    }
    private static Node preview(Map<String, String> data) {
        StackPane cover = new StackPane(MarketplaceIcons.icon("box", 25, "#9e8669")); cover.getStyleClass().add("notification-product-cover"); cover.setMinSize(100, 100); cover.setMaxSize(100, 100);
        try {
            String encoded = data.getOrDefault("productThumbnail", "");
            if (!encoded.isBlank()) {
                Image image = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
                if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0) {
                    ImageView view = new ImageView(image); view.setFitWidth(100); view.setFitHeight(100); view.setPreserveRatio(false); view.setSmooth(true); view.setId("notification-product-image"); view.setAccessibleText("Ảnh sản phẩm " + data.get("productName"));
                    double size = Math.min(image.getWidth(), image.getHeight()); view.setViewport(new javafx.geometry.Rectangle2D((image.getWidth() - size) / 2, (image.getHeight() - size) / 2, size, size)); cover.getChildren().setAll(view);
                }
            }
        } catch (IllegalArgumentException ignored) { }
        Rectangle clip = new Rectangle(100, 100); clip.setArcWidth(12); clip.setArcHeight(12); cover.setClip(clip); return cover;
    }
    private static Node resultCard(String title, String subtitle, String icon, String state) {
        VBox copy = new VBox(4, DesktopTheme.label(title, "notification-result-title"), DesktopTheme.label(subtitle, "notification-note")); copy.setMinWidth(0); HBox.setHgrow(copy, Priority.ALWAYS);
        HBox card = new HBox(12, MarketplaceIcons.icon(icon, 31, "won".equals(state) ? "#c18b21" : "#8f6c49"), copy); card.setAlignment(Pos.CENTER_LEFT); card.getStyleClass().addAll("notification-result-card", "notification-result-" + state); return card;
    }
    private static VBox card(Node... nodes) { VBox card = new VBox(10, nodes); card.getStyleClass().add("notification-info-card"); return card; }
    private static HBox field(String icon, String caption, String value) { return field(icon, caption, DesktopTheme.label(value == null || value.isBlank() ? "—" : value, "notification-field-value")); }
    private static HBox field(String icon, String caption, Label value) {
        Label label = DesktopTheme.label(caption, "notification-field-caption"); label.setMinWidth(0); label.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(label, Priority.ALWAYS);
        value.setWrapText(true); value.setMinWidth(0); value.setMaxWidth(151); value.setAlignment(Pos.CENTER_RIGHT); value.getStyleClass().add("notification-field-value");
        HBox row = new HBox(8, MarketplaceIcons.icon(icon, 12, "#947d60"), label, value); row.setAlignment(Pos.CENTER_LEFT); return row;
    }
    private static Node statistic(String icon, String caption, String value) {
        VBox copy = new VBox(3, DesktopTheme.label(caption, "notification-stat-caption"), DesktopTheme.label(value == null ? "—" : value, "notification-stat-value"));
        HBox stat = new HBox(7, MarketplaceIcons.icon(icon, 18, "#936227"), copy); stat.setAlignment(Pos.CENTER); stat.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(stat, Priority.ALWAYS); return stat;
    }
    private static Button action(String text, String icon) { Button button = new Button(text, MarketplaceIcons.icon(icon, 14, "#775631")); button.getStyleClass().add("notification-button"); return button; }
    private static String money(String raw) { return raw == null || raw.isBlank() ? "—" : WalletUi.money(new BigDecimal(raw)); }
    private static String time(String raw, boolean full) {
        if (raw == null || raw.isBlank()) { return "—"; }
        return DateTimeFormatter.ofPattern(full ? "dd/MM/yyyy HH:mm:ss" : "dd/MM HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.parse(raw));
    }
    private static String walletText(String raw) {
        return switch (raw == null ? "" : raw) { case "PAID" -> "Đã thanh toán (sổ giao dịch xác nhận)"; case "RELEASED" -> "Đã hoàn tiền giữ chỗ"; case "REVIEW_REQUIRED" -> "Cần đối soát, chưa xác nhận thanh toán"; default -> "Không có giao dịch liên quan"; };
    }
}
