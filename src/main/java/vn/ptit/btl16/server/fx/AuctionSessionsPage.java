package vn.ptit.btl16.server.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;
import javafx.util.Duration;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.dashboard.AuctionSessionService;

import java.time.Instant;
import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

public final class AuctionSessionsPage extends ServerAdminPage {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());
    private final Supplier<ServerApplication> server;
    private final Executor worker;
    private final TextField room = new TextField();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final ComboBox<String> type = new ComboBox<>();
    private final Label status = DesktopTheme.label("", "muted");
    private final Label count = DesktopTheme.label("0 phiên", "session-count-badge");
    private final Label summary = SessionUi.text("Theo dõi sản phẩm, người bán và toàn bộ diễn biến phiên.", "session-field-label");
    private final Button filter = new Button("Lọc");
    private final TableView<AuctionSnapshot> table = new TableView<>();
    private final TilePane cards = new TilePane(14, 14);
    private final ScrollPane cardScroll = new ScrollPane(cards);
    private final BorderPane listing = new BorderPane();
    private final ToggleButton listMode = new ToggleButton("Danh sách");
    private final ToggleButton cardMode = new ToggleButton("Dạng thẻ");
    private final BorderPane tableSurface = new BorderPane(table);
    private final Map<Long, RoomCard> roomCards = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<Image>> covers = new LinkedHashMap<>();
    private final Timeline poll = new Timeline(new KeyFrame(Duration.seconds(1), event -> tick()));
    private AuctionSessionService.Filter selectedFilter = new AuctionSessionService.Filter("", null, null, null);
    private ServerApplication loadedServer;
    private AuctionSessionDetailPane detail;
    private List<AuctionSnapshot> rows = List.of();
    private long selectedRoom;
    private long generation;
    private long ticks;
    private boolean loading;
    private boolean detailPicturesLoaded;

    public AuctionSessionsPage(Window owner, Supplier<ServerApplication> server, Executor worker) {
        super(owner);
        this.server = server;
        this.worker = worker;
        setId("server-auction-sessions");
        getStyleClass().add("session-workspace"); setPadding(new Insets(0, 24, 16, 24));
        room.setId("server-session-room-filter"); room.setPromptText("Nhập mã phòng"); room.setPrefWidth(155);
        from.setId("server-session-from"); from.setPromptText("Từ ngày"); from.setPrefWidth(160);
        to.setId("server-session-to"); to.setPromptText("Đến ngày"); to.setPrefWidth(160);
        type.setId("server-session-type"); type.getItems().setAll("Đang diễn ra", "Đã kết thúc");
        type.setPromptText("Tất cả phiên"); type.setPrefWidth(170);
        filter.setId("server-session-filter"); filter.setOnAction(event -> applyFilters());
        room.setOnAction(event -> applyFilters());
        filter.setGraphic(ServerIcons.icon("search")); filter.getStyleClass().add("session-primary-button");
        Button reset = new Button("Bỏ lọc", ServerIcons.icon("refresh")); reset.getStyleClass().add("session-clear-button");
        reset.setId("server-session-reset");
        reset.setOnAction(event -> {
            room.clear(); from.setValue(null); from.getEditor().clear(); to.setValue(null); to.getEditor().clear();
            type.getSelectionModel().clearSelection(); applyFilters();
        });
        FlowPane filters = new FlowPane(10, 10, filterField("Mã phòng", room), filterField("Từ ngày", from),
                filterField("Đến ngày", to), filterField("Loại phòng", type), filter, reset);
        filters.setAlignment(Pos.BOTTOM_LEFT);
        filters.setRowValignment(javafx.geometry.VPos.BOTTOM);
        VBox filterCard = new VBox(filters); filterCard.getStyleClass().add("session-filter-card");
        ToggleGroup modes = new ToggleGroup(); listMode.setToggleGroup(modes); cardMode.setToggleGroup(modes);
        listMode.setId("server-session-list-mode"); cardMode.setId("server-session-card-mode");
        listMode.setGraphic(ServerIcons.icon("list")); cardMode.setGraphic(ServerIcons.icon("grid"));
        listMode.getStyleClass().add("session-view-toggle"); cardMode.getStyleClass().add("session-view-toggle");
        cardMode.setSelected(true);
        modes.selectedToggleProperty().addListener((observable, before, selected) -> {
            if (selected == null) { modes.selectToggle(before); return; }
            showListingMode();
        });
        HBox modesBar = new HBox(4, cardMode, listMode); modesBar.getStyleClass().add("session-view-switch");
        HBox titleRow = new HBox(10, SessionUi.text("Các phiên đấu giá", "session-list-title"), count); titleRow.setAlignment(Pos.CENTER_LEFT);
        VBox introduction = new VBox(6, titleRow, summary); introduction.setMinWidth(0);
        HBox toolbar = new HBox(10, introduction, DesktopTheme.spacer(), modesBar);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox top = new VBox(14, toolbar, filterCard); top.getStyleClass().add("session-list-heading");
        listing.setTop(top);
        table.setId("server-session-list");
        table.getStyleClass().add("session-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Không có phiên đấu giá phù hợp"));
        var roomColumn = column("Mã phòng", AuctionSessionsPage::roomCode, 95);
        roomColumn.setCellFactory(view -> textCell("session-room-number"));
        var productColumn = column("Sản phẩm", auction -> auction.getProduct().getName(), 255);
        productColumn.setCellFactory(view -> new TableCell<>() {
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty); setText(null); setGraphic(null);
                var auction = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || auction == null) { return; }
                SessionImagePane image = image(auction, 46); image.setMinWidth(58); image.setMaxWidth(58); image.setPrefWidth(58);
                Label title = SessionUi.text(name, "session-table-product"); title.setWrapText(false);
                VBox text = new VBox(5, title, SessionUi.text(auction.getProduct().getCode(), "session-field-label"));
                text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
                HBox row = new HBox(11, image, text); row.setAlignment(Pos.CENTER_LEFT); row.setMinWidth(0); setGraphic(row);
                setTooltip(new Tooltip(name));
            }
        });
        var stateColumn = column("Trạng thái", AuctionSessionsPage::state, 125);
        stateColumn.setCellFactory(view -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty); setText(null); setGraphic(null);
                var auction = getTableRow() == null ? null : getTableRow().getItem();
                if (!empty && auction != null) { Label badge = DesktopTheme.label(value, "pill"); badge.getStyleClass().add(stateStyle(auction)); setGraphic(badge); }
            }
        });
        dateColumn("Bắt đầu", AuctionSnapshot::getStartTime);
        dateColumn("Kết thúc", auction -> auction.getEndedAt() == null ? auction.getEndTime() : auction.getEndedAt());
        column("Giá hiện tại / chốt", auction -> WalletUi.money(auction.getCurrentPrice()), 150);
        table.getColumns().getLast().getStyleClass().add("money-column");
        table.setRowFactory(view -> {
            TableRow<AuctionSnapshot> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY) { open(row.getItem().getAuctionId()); }
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER && table.getSelectionModel().getSelectedItem() != null) {
                open(table.getSelectionModel().getSelectedItem().getAuctionId());
            }
        });
        cards.setId("server-session-cards"); cards.setPrefTileWidth(290); cards.setTileAlignment(Pos.TOP_LEFT);
        cards.setPrefColumns(3); cardScroll.setFitToWidth(true); cardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardScroll.getStyleClass().add("session-cards-scroll"); cards.setMinHeight(Region.USE_PREF_SIZE);
        cardScroll.viewportBoundsProperty().addListener((observable, before, after) -> {
            double available = Math.max(0, after.getWidth() - 16);
            int columns = Math.max(1, (int) ((available + 14) / 278));
            double width = Math.max(220, Math.floor((available - 14 * (columns - 1)) / columns));
            cards.setPrefColumns(columns); cards.setPrefTileWidth(width);
            roomCards.values().forEach(card -> card.setPrefWidth(width));
        });
        tableSurface.getStyleClass().add("session-results-surface");
        Rectangle tableClip = new Rectangle(); tableClip.widthProperty().bind(table.widthProperty()); tableClip.heightProperty().bind(table.heightProperty());
        tableClip.setArcWidth(30); tableClip.setArcHeight(30); table.setClip(tableClip);
        listing.setCenter(cardScroll);
        setCenter(listing);
        status.setId("server-sessions-status"); status.setWrapText(true); status.getStyleClass().add("admin-status"); setBottom(status);
        renderCards();
        poll.setCycleCount(Timeline.INDEFINITE);
    }

    @Override protected void onActivate() {
        if (loadedServer != server.get()) { resetServer(); }
        poll.play();
        if (selectedRoom == 0) { loadList(false); } else { loadDetail(false); }
    }

    @Override protected void onDeactivate() {
        poll.stop(); generation++; loading = false; filter.setDisable(false);
        if (detail != null) { detail.suspend(); }
    }

    private void tick() {
        if (loadedServer != null && loadedServer != server.get()) { resetServer(); }
        if (detail != null) { detail.tick(); }
        if (++ticks % 5 == 0 && !loading) {
            if (selectedRoom == 0) { loadList(true); } else { loadDetail(false); }
        }
    }

    private void resetServer() {
        generation++; loading = false; loadedServer = server.get(); selectedRoom = 0;
        if (detail != null) { detail.suspend(); detail = null; }
        rows = List.of(); table.getItems().clear(); roomCards.clear(); covers.clear(); renderCards(); setCenter(listing);
    }

    private void applyFilters() {
        try {
            from.commitValue(); to.commitValue();
            var zone = ZoneId.systemDefault();
            selectedFilter = new AuctionSessionService.Filter(room.getText(),
                    from.getValue() == null ? null : from.getValue().atStartOfDay(zone).toInstant(),
                    to.getValue() == null ? null : to.getValue().plusDays(1).atStartOfDay(zone).toInstant(),
                    type.getValue() == null ? null : type.getSelectionModel().getSelectedIndex() == 1);
            generation++; loading = false; loadList(false);
        } catch (RuntimeException error) { status.setText("Ngày không hợp lệ. Từ ngày phải nhỏ hơn hoặc bằng đến ngày."); }
    }

    private void loadList(boolean silent) {
        if (!isActive() || loading) { return; }
        var running = server.get();
        if (running == null) { status.setText("Server đang tạm dừng"); return; }
        var query = selectedFilter;
        long token = ++generation; loading = true; filter.setDisable(true);
        if (!silent) { status.setText("Đang tải phiên đấu giá..."); }
        CompletableFuture.supplyAsync(() -> running.auctionSessions().list(query), worker)
                .whenComplete((values, error) -> Platform.runLater(() -> {
                    if (!isActive() || token != generation || running != server.get()) { return; }
                    loading = false; filter.setDisable(false);
                    if (error != null) { status.setText("Chưa tải được phiên đấu giá. Hãy thử lọc lại."); return; }
                    loadedServer = running;
                    count.setText(values.size() + " phiên");
                    long open = values.stream().filter(value -> value.getStatus() == AuctionStatus.OPEN).count();
                    summary.setText(open + " đang diễn ra · " + (values.size() - open) + " đã kết thúc · Bao gồm các phiên đã lưu trữ");
                    var selected = table.getSelectionModel().getSelectedItem();
                    if (!values.equals(rows)) {
                        rows = values; table.getItems().setAll(values); renderCards();
                        if (selected != null) {
                            values.stream().filter(value -> value.getAuctionId() == selected.getAuctionId())
                                    .findFirst().ifPresent(value -> table.getSelectionModel().select(value));
                        }
                    }
                    status.setText("Lọc theo ngày bắt đầu phiên · Dữ liệu được tự động cập nhật");
                }));
    }

    public void open(long roomId) {
        if (!isActive() || server.get() == null) { return; }
        if (detail != null) { detail.suspend(); }
        generation++; loading = false; selectedRoom = roomId;
        detailPicturesLoaded = false;
        var running = server.get(); loadedServer = running;
        detail = new AuctionSessionDetailPane(running, roomId, worker,
                () -> isActive() && selectedRoom == roomId && server.get() == running,
                this::back, () -> loadDetail(true));
        setCenter(detail); loadDetail(true);
    }

    private void back() {
        generation++; loading = false; selectedRoom = 0;
        if (detail != null) { detail.suspend(); detail = null; }
        setCenter(listing); loadList(false);
    }

    public void showListing() { back(); }

    private void loadDetail(boolean pictures) {
        if (!isActive() || loading || detail == null || server.get() == null) { return; }
        var running = server.get(); var target = detail; long roomId = selectedRoom;
        boolean includePictures = pictures || !detailPicturesLoaded;
        long token = ++generation; loading = true;
        if (pictures) { status.setText("Đang tải chi tiết phiên..."); }
        CompletableFuture.supplyAsync(() -> running.auctionSessions().details(roomId, includePictures), worker)
                .whenComplete((data, error) -> Platform.runLater(() -> {
                    if (!isActive() || token != generation || running != server.get() || detail != target) { return; }
                    loading = false;
                    if (error != null) { status.setText("Chưa tải được chi tiết phiên. Hãy bấm Làm mới để thử lại."); return; }
                    target.update(data, includePictures);
                    detailPicturesLoaded = true;
                    status.setText("Cập nhật lúc " + TIME.format(Instant.now())
                            + " - " + data.participants().size() + " người từng tham gia - Bao gồm người đã rời phòng");
                }));
    }

    private void showListingMode() { listing.setCenter(cardMode.isSelected() ? cardScroll : tableSurface); }

    private void renderCards() {
        roomCards.keySet().removeIf(id -> rows.stream().noneMatch(auction -> auction.getAuctionId() == id));
        for (var auction : rows) {
            roomCards.computeIfAbsent(auction.getAuctionId(), RoomCard::new).update(auction);
        }
        var ordered = rows.stream().map(auction -> roomCards.get(auction.getAuctionId())).toList();
        if (!cards.getChildren().equals(ordered)) { cards.getChildren().setAll(ordered); }
        if (rows.isEmpty()) {
            VBox empty = new VBox(12, ServerIcons.icon("search"), SessionUi.text("Không có phiên phù hợp", "session-section-title"),
                    SessionUi.text("Thử thay đổi bộ lọc hoặc tìm theo mã phòng khác.", "session-field-label"));
            empty.setAlignment(Pos.CENTER); empty.getStyleClass().add("session-empty-state"); cards.getChildren().setAll(empty);
        }
    }

    private TableColumn<AuctionSnapshot, String> column(String title, Function<AuctionSnapshot, String> value, double width) {
        TableColumn<AuctionSnapshot, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width); table.getColumns().add(column); return column;
    }

    private static VBox filterField(String title, javafx.scene.Node control) {
        return new VBox(7, SessionUi.text(title, "session-field-label"), control);
    }

    private static TableCell<AuctionSnapshot, String> textCell(String style) {
        return new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty); setText(null); setGraphic(empty ? null : SessionUi.text(value, style));
            }
        };
    }

    private void dateColumn(String title, Function<AuctionSnapshot, Instant> value) {
        var column = column(title, auction -> TIME.format(value.apply(auction)), 135);
        column.setCellFactory(view -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setText(null); setGraphic(null);
                if (empty || item == null) { return; }
                String[] parts = item.split(" ");
                setGraphic(new VBox(5, SessionUi.text(parts[0], "session-table-date"), SessionUi.text(parts[1], "session-field-label")));
            }
        });
    }

    private SessionImagePane image(AuctionSnapshot auction, double height) {
        SessionImagePane frame = new SessionImagePane(true, height); installImage(frame, auction); return frame;
    }

    private void installImage(SessionImagePane frame, AuctionSnapshot auction) {
        var running = server.get(); if (running == null) { return; }
        String key = auction.getProduct().getProductId() + ":" + auction.getProduct().getImageVersion();
        if (key.equals(frame.getProperties().get("session-image-key"))) { return; }
        frame.getProperties().put("session-image-key", key);
        if (covers.size() >= 96 && !covers.containsKey(key)) { covers.remove(covers.keySet().iterator().next()); }
        var load = covers.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> {
            byte[] bytes = running.auctionSessions().cover(auction.getProduct().getProductId());
            if (bytes.length == 0) { return null; }
            Image image = new Image(new ByteArrayInputStream(bytes)); return image.isError() ? null : image;
        }, worker).exceptionally(error -> null));
        load.thenAccept(image -> Platform.runLater(() -> {
            if (server.get() == running && key.equals(frame.getProperties().get("session-image-key"))) { frame.setImage(image); }
        }));
    }

    private final class RoomCard extends VBox {
        private final SessionImagePane cover = new SessionImagePane(true, 144);
        private final Label name = SessionUi.text("", "session-card-title");
        private final Label code = DesktopTheme.label("", "session-card-room");
        private final Label state = DesktopTheme.label("", "pill");
        private final Label price = SessionUi.text("", "session-card-price");
        private final Label caption = SessionUi.text("", "session-field-label");
        private final Label date = SessionUi.text("", "session-field-label");

        RoomCard(long roomId) {
            super(9); getStyleClass().add("session-list-card");
            setMinWidth(220); setPrefWidth(cards.getPrefTileWidth()); setMaxWidth(Double.MAX_VALUE);
            StackPane image = new StackPane(cover, state); StackPane.setAlignment(state, Pos.TOP_LEFT);
            StackPane.setMargin(state, new Insets(10)); state.setMaxWidth(Region.USE_PREF_SIZE);
            name.setMinHeight(36); name.setMaxHeight(36);
            HBox roomRow = new HBox(8, ServerIcons.icon("auction"), code); roomRow.setAlignment(Pos.CENTER_LEFT);
            VBox amount = new VBox(5, caption, price); amount.getStyleClass().add("session-card-price-panel");
            HBox when = new HBox(8, ServerIcons.icon("clock"), date); when.setAlignment(Pos.CENTER_LEFT);
            Button view = new Button("Xem phiên đấu giá", ServerIcons.icon("arrow"));
            view.setMaxWidth(Double.MAX_VALUE); view.getStyleClass().add("session-card-open");
            view.setContentDisplay(ContentDisplay.RIGHT); view.setOnAction(event -> open(roomId));
            getChildren().setAll(image, roomRow, name, amount, when, view);
            setOnMouseClicked(event -> { if (event.getButton() == MouseButton.PRIMARY) { open(roomId); } });
        }

        void update(AuctionSnapshot auction) {
            name.setText(auction.getProduct().getName()); code.setText("Phòng " + roomCode(auction));
            state.setText(state(auction)); state.getStyleClass().removeAll("session-state-open", "session-state-ended", "session-state-cancelled");
            state.getStyleClass().add(stateStyle(auction)); price.setText(WalletUi.money(auction.getCurrentPrice()));
            caption.setText(auction.getStatus() == AuctionStatus.ENDED ? "Giá đã chốt" : "Giá hiện tại");
            date.setText(TIME.format(auction.getStartTime())); installImage(cover, auction);
        }
    }

    static String roomCode(AuctionSnapshot auction) {
        return auction.getRoomCode().isBlank() ? Long.toString(auction.getAuctionId()) : auction.getRoomCode();
    }

    static String state(AuctionSnapshot auction) {
        return switch (auction.getStatus()) {
            case OPEN -> "Đang diễn ra";
            case ENDED -> "Đã kết thúc";
            case CANCELLED -> "Đã hủy";
        };
    }

    static String stateStyle(AuctionSnapshot auction) {
        return switch (auction.getStatus()) {
            case OPEN -> "session-state-open";
            case ENDED -> "session-state-ended";
            case CANCELLED -> "session-state-cancelled";
        };
    }
}
