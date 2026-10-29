package vn.ptit.btl16.client.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.fx.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class AuctionReceiptsPane extends BorderPane implements AutoCloseable {
    private final AuctionReceiptApi api;
    private final ClientTransport transport;
    private final BooleanSupplier sameSession;
    private final boolean embedded;
    private final Stage stage;
    private final ComboBox<String> scope = new ComboBox<>(), outcome = new ComboBox<>();
    private final TextField search = new TextField();
    private final TableView<Map<String, String>> history = new TableView<>();
    private final VBox detail = new VBox(12);
    private final BorderPane drawer = new BorderPane();
    private final ScrollPane detailScroll = new ScrollPane(detail);
    private final Label message = new Label();
    private final Label pageNumber = new Label("1");
    private final Button previousPage = new Button();
    private final List<Long> pageCursors = new ArrayList<>(List.of(Long.MAX_VALUE));
    private final Map<String, Image> previews = new LinkedHashMap<>();
    private final Button next = new Button("Trang ti\u1ebfp"), refresh = new Button("L\u00e0m m\u1edbi");
    private final Timeline sessionGuard;
    private long listGeneration, receiptGeneration, before = Long.MAX_VALUE, nextBefore, selectedRoom;
    private boolean closed;
    private int pageIndex;

    public AuctionReceiptsPane(Window owner, ClientTransport transport, BooleanSupplier sameSession) {
        this(owner, transport, sameSession, false);
    }
    public AuctionReceiptsPane(Window owner, ClientTransport transport, BooleanSupplier sameSession, boolean embedded) {
        this.api = new AuctionReceiptApi(transport); this.transport = transport; this.sameSession = sameSession; this.embedded = embedded;
        stage = embedded ? null : new Stage();
        setId("account-receipts"); getStyleClass().add("receipts-page");
        if (stage != null) { stage.initOwner(owner); stage.setTitle("Kết quả phiên của tôi"); }
        scope.getItems().setAll("PARTICIPATED", "WON", "HOSTED"); scope.setValue("PARTICIPATED");
        outcome.getItems().setAll("ALL", "WON", "NO_BIDS", "CANCELLED"); outcome.setValue("ALL");
        scope.setConverter(converter(true)); outcome.setConverter(converter(false));
        scope.setId("receipt-scope"); outcome.setId("receipt-outcome"); search.setId("receipt-search");
        scope.setPrefWidth(200); outcome.setPrefWidth(176); search.setPrefWidth(220);
        search.setPromptText("Tên sản phẩm / mã phiên…");
        StackPane searchBox = new StackPane(search, MarketplaceIcons.icon("search", 14, "#8c7c68"));
        StackPane.setAlignment(searchBox.getChildren().get(1), Pos.CENTER_LEFT); StackPane.setMargin(searchBox.getChildren().get(1), new Insets(0, 0, 0, 12));
        searchBox.getStyleClass().add("receipt-search-box");
        Button first = action("Trang đầu", "home"); first.setId("receipts-first");
        next.setGraphic(MarketplaceIcons.icon("right", 13, "#735b3d")); next.setDisable(true); next.setId("receipts-next"); refresh.setId("receipts-refresh");
        refresh.setGraphic(MarketplaceIcons.icon("refresh", 13, "#fff9ed")); refresh.getStyleClass().add("receipt-refresh");
        FlowPane filters = new FlowPane(8, 8, scope, outcome, searchBox, refresh, first, next); filters.getStyleClass().add("receipt-filters");
        message.getStyleClass().add("receipt-message"); message.setWrapText(true);
        HBox information = new HBox(10, MarketplaceIcons.icon("info", 17, "#98703e"), message); information.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(message, Priority.ALWAYS);
        VBox controls = new VBox(12, filters, information); controls.getStyleClass().add("receipt-controls"); setTop(new VBox(hero(), controls));
        configureHistory();
        previousPage.setGraphic(MarketplaceIcons.icon("left", 13, "#76552d")); previousPage.setId("receipts-previous"); previousPage.setAccessibleText("Trang trước");
        previousPage.setTooltip(new Tooltip("Trang trước")); previousPage.getStyleClass().add("receipt-page-button"); previousPage.setDisable(true);
        pageNumber.getStyleClass().add("receipt-page-number"); pageNumber.setId("receipts-page-number");
        Button followingPage = action("", "right"); followingPage.getStyleClass().add("receipt-page-button"); followingPage.setAccessibleText("Trang tiếp");
        followingPage.disableProperty().bind(next.disableProperty()); followingPage.setOnAction(event -> next.fire());
        HBox pagination = new HBox(8, previousPage, pageNumber, followingPage); pagination.setAlignment(Pos.CENTER); pagination.getStyleClass().add("receipt-pagination");
        VBox tableCard = new VBox(history); tableCard.getStyleClass().add("receipt-history-card"); tableCard.setMinWidth(0); tableCard.setMinHeight(140); tableCard.setMaxHeight(Region.USE_PREF_SIZE);
        history.prefHeightProperty().bind(javafx.beans.binding.Bindings.size(history.getItems()).multiply(84).add(46));
        VBox.setVgrow(history, Priority.ALWAYS);
        Region breathingRoom = new Region(); VBox.setVgrow(breathingRoom, Priority.ALWAYS);
        VBox listing = new VBox(6, tableCard, breathingRoom, pagination); listing.setMinWidth(0); HBox.setHgrow(listing, Priority.ALWAYS);
        Button close = action("", "close"); close.setId("receipt-close-detail"); close.getStyleClass().add("receipt-close");
        close.setAccessibleText("Đóng chi tiết giao dịch"); close.setTooltip(new Tooltip("Đóng chi tiết")); close.setOnAction(event -> closeDetail());
        HBox drawerHeading = new HBox(8, MarketplaceIcons.icon("receipt", 16, "#72512c"), DesktopTheme.label("Chi tiết phiên đấu giá", "receipt-drawer-title"), DesktopTheme.spacer(), close);
        drawerHeading.setAlignment(Pos.CENTER_LEFT); drawerHeading.getStyleClass().add("receipt-drawer-heading");
        detailScroll.setFitToWidth(true); detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); detailScroll.setId("receipt-detail-scroll");
        detail.setId("receipt-detail"); detail.getStyleClass().add("receipt-detail-body");
        drawer.setTop(drawerHeading); drawer.setCenter(detailScroll); drawer.setId("receipt-drawer"); drawer.getStyleClass().add("receipt-drawer");
        drawer.prefWidthProperty().bind(javafx.beans.binding.Bindings.min(350, javafx.beans.binding.Bindings.max(300, widthProperty().multiply(.30))));
        drawer.minWidthProperty().bind(drawer.prefWidthProperty()); drawer.maxWidthProperty().bind(drawer.prefWidthProperty()); drawer.setVisible(false); drawer.setManaged(false);
        HBox body = new HBox(12, listing, drawer); body.getStyleClass().add("receipt-content"); setCenter(body);
        refresh.setOnAction(event -> { reload(); if (selectedRoom > 0) { loadReceipt(selectedRoom); } });
        first.setOnAction(event -> reset());
        next.setOnAction(event -> {
            if (nextBefore <= 0) { return; }
            while (pageCursors.size() > pageIndex + 1) { pageCursors.remove(pageCursors.size() - 1); }
            pageCursors.add(nextBefore); before = nextBefore; pageIndex++; reload();
        });
        previousPage.setOnAction(event -> { if (pageIndex > 0) { before = pageCursors.get(--pageIndex); reload(); } });
        scope.setOnAction(event -> reset()); outcome.setOnAction(event -> reset()); search.setOnAction(event -> reset());
        sessionGuard = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (!sameSession.getAsBoolean()) { if (stage != null) { stage.close(); } close(); }
        }));
        sessionGuard.setCycleCount(Timeline.INDEFINITE);
        if (stage != null) {
            stage.setOnHidden(event -> close());
            Scene scene = new Scene(this, 1120, 800); DesktopTheme.apply(scene); stage.setScene(scene); stage.setMinWidth(920); stage.setMinHeight(560);
        }
    }
    private Node hero() {
        StackPane hero = new StackPane(); hero.getStyleClass().add("receipt-hero"); hero.setMinHeight(112); hero.setPrefHeight(112);
        BorderPane.setMargin(hero, new Insets(12, 14, 0, 14)); VBox.setMargin(hero, new Insets(12, 14, 0, 14));
        Pane artwork = new Pane(); artwork.setMouseTransparent(true);
        for (int index = 0; index < 3; index++) {
            Ellipse wash = new Ellipse(270 + index * 50, 86 + index * 15); wash.setRotate(-19);
            wash.centerXProperty().bind(hero.widthProperty().subtract(70 + index * 25)); wash.setCenterY(120);
            wash.setFill(Color.web(index == 1 ? "#fff9ed" : "#b88a49", index == 1 ? .4 : .10)); artwork.getChildren().add(wash);
        }
        var watermark = MarketplaceIcons.icon("gavel", 94, "#a97833"); watermark.setOpacity(.10); watermark.setRotate(-15);
        watermark.layoutXProperty().bind(hero.widthProperty().multiply(.55)); watermark.setLayoutY(20);
        var hammer = MarketplaceIcons.icon("gavel", 102, "#b48243"); hammer.setOpacity(.80); hammer.setRotate(-16);
        hammer.layoutXProperty().bind(hero.widthProperty().subtract(248)); hammer.setLayoutY(9);
        hammer.setEffect(new javafx.scene.effect.DropShadow(3, 1, 3, Color.web("#8c5f2e", .28)));
        var hourglass = MarketplaceIcons.icon("hourglass", 91, "#ac8554"); hourglass.setOpacity(.70);
        hourglass.layoutXProperty().bind(hero.widthProperty().subtract(110)); hourglass.setLayoutY(10);
        for (Node ornament : List.of(hammer, hourglass)) { ornament.visibleProperty().bind(hero.widthProperty().greaterThanOrEqualTo(1000)); }
        var sparkle = MarketplaceIcons.icon("sparkles", 25, "#fff8e8"); sparkle.layoutXProperty().bind(hero.widthProperty().subtract(265)); sparkle.setLayoutY(12);
        artwork.getChildren().addAll(watermark, hammer, hourglass, sparkle);
        Label title = DesktopTheme.label("Kết quả & biên nhận cá nhân", "receipt-title"); title.setWrapText(false);
        Label subtitle = DesktopTheme.label("Chỉ hiển thị kết quả đã chốt. Ví mô phỏng, không chuyển tiền thật.", "receipt-subtitle");
        VBox copy = new VBox(7, title, subtitle); copy.setMinWidth(0); copy.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(copy, Priority.ALWAYS);
        HBox content = new HBox(18, AccountUi.emblem("gavel", 70), copy); content.setAlignment(Pos.CENTER_LEFT); content.setPadding(new Insets(15, 24, 15, 20));
        hero.getChildren().addAll(artwork, content);
        Rectangle clip = new Rectangle(); clip.widthProperty().bind(hero.widthProperty()); clip.heightProperty().bind(hero.heightProperty()); clip.setArcWidth(32); clip.setArcHeight(32); hero.setClip(clip); return hero;
    }
    private void configureHistory() {
        history.setId("receipts-history"); history.getStyleClass().add("receipt-history"); history.setMinHeight(120);
        history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); history.setFixedCellSize(84); history.setMinWidth(0);
        column("#", 42, 36, row -> DesktopTheme.label("#" + row.get("auctionId"), "receipt-room-number"));
        column("PHIÊN / SẢN PHẨM", 215, 130, row -> {
            Label name = DesktopTheme.label(row.get("productName"), "receipt-product-name"); name.setWrapText(false); name.setMaxWidth(Double.MAX_VALUE);
            Label code = DesktopTheme.label("Mã phiên: #" + row.get("auctionId"), "receipt-product-code");
            VBox text = new VBox(4, name, code); text.setAlignment(Pos.CENTER_LEFT); text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
            HBox product = new HBox(10, preview(row, 48, 56), text); product.setAlignment(Pos.CENTER_LEFT); return product;
        });
        column("KẾT QUẢ", 140, 100, row -> outcomeBadge(row.get("outcome"), false));
        column("CỦA BẠN", 118, 80, row -> roleBadge(row.get("personalRole"), false));
        column("KẾT THÚC", 131, 104, row -> {
            Label date = DesktopTheme.label(time(row.get("endedAt")).replace(' ', '\n'), "receipt-ended-time");
            HBox ended = new HBox(7, MarketplaceIcons.icon("calendar", 14, "#796247"), date); ended.setAlignment(Pos.CENTER_LEFT); return ended;
        });
        column("THAO TÁC", 96, 88, row -> {
            Button view = action("", "eye"); view.getStyleClass().add("receipt-view"); view.setId("receipt-view-" + row.get("auctionId"));
            view.setAccessibleText("Xem kết quả phiên #" + row.get("auctionId")); view.setTooltip(new Tooltip("Xem kết quả phiên"));
            Button more = action("", "more"); more.getStyleClass().add("receipt-more"); more.setId("receipt-more-" + row.get("auctionId"));
            more.setAccessibleText("Chi tiết giao dịch phiên #" + row.get("auctionId")); more.setTooltip(new Tooltip("Chi tiết giao dịch"));
            for (Button button : List.of(view, more)) { button.setOnAction(event -> { history.getSelectionModel().select(row); loadReceipt(Long.parseLong(row.get("auctionId"))); }); }
            HBox actions = new HBox(7, view, more); actions.setAlignment(Pos.CENTER_LEFT); return actions;
        });
        history.setPlaceholder(DesktopTheme.label("Chưa có biên nhận đã chốt theo bộ lọc.", "muted"));
    }
    private void column(String title, double width, double minimum, Function<Map<String, String>, Node> graphic) {
        TableColumn<Map<String, String>, Map<String, String>> column = new TableColumn<>(title);
        column.setPrefWidth(width); column.setMinWidth(minimum); column.setSortable(false); column.setReorderable(false);
        column.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(Map<String, String> row, boolean empty) {
                super.updateItem(row, empty); setText(null); setGraphic(empty || row == null ? null : graphic.apply(row));
            }
        }); history.getColumns().add(column);
    }
    private StackPane preview(Map<String, String> data, double width, double height) {
        StackPane cover = new StackPane(MarketplaceIcons.icon("box", Math.min(24, width / 2), "#a28a6b"));
        cover.getStyleClass().add("receipt-product-cover"); cover.setMinSize(width, height); cover.setPrefSize(width, height); cover.setMaxSize(width, height);
        String encoded = data.getOrDefault("productThumbnail", "");
        if (!encoded.isBlank()) {
            try {
                Image image = previews.get(encoded);
                if (image == null) {
                    image = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
                    if (!image.isError()) { if (previews.size() >= 90) { previews.remove(previews.keySet().iterator().next()); } previews.put(encoded, image); }
                }
                if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0) {
                    ImageView picture = new ImageView(image); picture.setFitWidth(width); picture.setFitHeight(height); picture.setPreserveRatio(false); picture.setSmooth(true);
                    double ratio = width / height, iw = image.getWidth(), ih = image.getHeight();
                    picture.setViewport(iw / ih > ratio ? new javafx.geometry.Rectangle2D((iw - ih * ratio) / 2, 0, ih * ratio, ih)
                            : new javafx.geometry.Rectangle2D(0, (ih - iw / ratio) / 2, iw, iw / ratio));
                    picture.setAccessibleText("Ảnh sản phẩm " + data.getOrDefault("productName", "")); cover.getChildren().setAll(picture);
                }
            } catch (IllegalArgumentException ignored) { }
        }
        Rectangle clip = new Rectangle(width, height); clip.setArcWidth(10); clip.setArcHeight(10); cover.setClip(clip); return cover;
    }
    private static Button action(String text, String icon) {
        Button button = new Button(text, MarketplaceIcons.icon(icon, 14, "#76552f")); button.getStyleClass().add("receipt-button"); return button;
    }
    private static VBox section(String title, String icon, Node... content) {
        HBox heading = new HBox(7, MarketplaceIcons.icon(icon, 13, "#705333"), DesktopTheme.label(title, "receipt-section-title")); heading.setAlignment(Pos.CENTER_LEFT);
        VBox section = new VBox(7, heading); section.getChildren().addAll(content); section.getStyleClass().add("receipt-section"); return section;
    }
    private static HBox field(String caption, Label value) {
        Label name = DesktopTheme.label(caption, "receipt-field-caption"); name.setMinWidth(105); name.setPrefWidth(105); name.setMaxWidth(105);
        value.setWrapText(true); value.setMinWidth(0); value.setMaxWidth(Double.MAX_VALUE); value.getStyleClass().add("receipt-field-value"); HBox.setHgrow(value, Priority.ALWAYS);
        HBox row = new HBox(7, name, value); row.setAlignment(Pos.TOP_LEFT); return row;
    }
    private static VBox statistic(String caption, String value) {
        VBox box = new VBox(3, DesktopTheme.label(value == null ? "—" : value, "receipt-stat-number"), DesktopTheme.label(caption, "receipt-stat-caption"));
        box.setAlignment(Pos.CENTER); box.setMinWidth(0); box.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(box, Priority.ALWAYS); return box;
    }
    private static Label outcomeBadge(String raw, boolean full) {
        boolean won = "WON".equals(raw), cancelled = "CANCELLED".equals(raw);
        Label badge = DesktopTheme.label(full ? outcomeText(raw) : won ? "Có người thắng" : cancelled ? "Đã hủy" : "Không có người thắng", "receipt-badge");
        badge.getStyleClass().add(won ? "receipt-badge-green" : cancelled ? "receipt-badge-neutral" : "receipt-badge-red");
        badge.setGraphic(MarketplaceIcons.icon(won ? "trophy" : "close", 12, won ? "#267236" : cancelled ? "#806748" : "#be3f40")); return badge;
    }
    private static Label roleBadge(String raw, boolean full) {
        String text = full || "WINNER".equals(raw) || "NOT_WON".equals(raw) ? roleText(raw) : "HOST".equals(raw) ? "Chủ phòng" : "Theo dõi";
        Label badge = DesktopTheme.label(text, "receipt-badge"); badge.getStyleClass().add("WINNER".equals(raw) ? "receipt-badge-green" : "receipt-badge-neutral");
        if ("WINNER".equals(raw) && full) { badge.setGraphic(MarketplaceIcons.icon("trophy", 12, "#267236")); } return badge;
    }
    public void show(long room) {
        if (embedded) { throw new IllegalStateException("Embedded receipts"); }
        DesktopTheme.fitOnScreen(stage, 1120, 800); stage.show(); start(room);
    }
    public void startEmbedded(long room) { if (!embedded) { throw new IllegalStateException("Standalone receipts"); } start(room); }
    private void start(long room) { sessionGuard.play(); reload(); if (room > 0) { loadReceipt(room); } }
    @Override public void close() {
        if (closed) { return; }
        closed = true; listGeneration++; receiptGeneration++; sessionGuard.stop();
        history.getItems().clear(); detail.getChildren().clear(); previews.clear(); selectedRoom = 0; drawer.setVisible(false); drawer.setManaged(false);
    }
    private boolean active() { return !closed && sameSession.getAsBoolean() && getScene() != null; }
    private void reset() { before = Long.MAX_VALUE; pageIndex = 0; pageCursors.clear(); pageCursors.add(before); closeDetail(); reload(); }
    private void closeDetail() { selectedRoom = 0; receiptGeneration++; detail.getChildren().clear(); drawer.setVisible(false); drawer.setManaged(false); history.getSelectionModel().clearSelection(); }
    private void reload() {
        long generation = ++listGeneration; next.setDisable(true); previousPage.setDisable(pageIndex == 0); pageNumber.setText(Integer.toString(pageIndex + 1));
        if (!active()) { return; }
        history.getItems().clear(); message.setText("\u0110ang t\u1ea3i l\u1ecbch s\u1eed t\u1eeb server...");
        if (!transport.isConnected()) { message.setText("M\u1ea5t k\u1ebft n\u1ed1i. K\u1ebft n\u1ed1i l\u1ea1i v\u00e0 b\u1ea5m L\u00e0m m\u1edbi; ch\u01b0a x\u00e1c nh\u1eadn k\u1ebft qu\u1ea3."); return; }
        api.list(scope.getValue(), outcome.getValue(), search.getText().trim(), before).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!active() || generation != listGeneration) { return; }
            if (error != null || !response.isSuccess()) { message.setText(error != null ? "Ch\u01b0a t\u1ea3i \u0111\u01b0\u1ee3c. B\u1ea5m L\u00e0m m\u1edbi \u0111\u1ec3 th\u1eed l\u1ea1i." : response.getMessageText()); return; }
            var data = response.getWireMessage().getData(); int count = number(data, "count");
            List<Map<String, String>> rows = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                String prefix = "receipt." + index + "."; Map<String, String> row = new LinkedHashMap<>();
                data.forEach((key, value) -> { if (key.startsWith(prefix)) { row.put(key.substring(prefix.length()), value); } }); rows.add(row);
            }
            history.getItems().setAll(rows); nextBefore = Long.parseLong(data.getOrDefault("nextBeforeId", "0")); next.setDisable(!"true".equals(data.get("hasMore")));
            for (var row : rows) { if (Long.toString(selectedRoom).equals(row.get("auctionId"))) { history.getSelectionModel().select(row); break; } }
            message.setText(count + " biên nhận trên trang này. Nhấn nút ba chấm để xem chi tiết giao dịch.");
        }));
    }
    public void loadReceipt(long room) {
        if (!active()) { return; }
        selectedRoom = room; long generation = ++receiptGeneration;
        drawer.setVisible(true); drawer.setManaged(true); detailScroll.setVvalue(0);
        detail.getChildren().setAll(new Label("\u0110ang ch\u1edd bi\u00ean nh\u1eadn server x\u00e1c nh\u1eadn cho #" + room));
        Button retry = new Button("Th\u1eed l\u1ea1i bi\u00ean nh\u1eadn"); retry.setOnAction(event -> loadReceipt(room)); detail.getChildren().add(retry);
        if (!transport.isConnected()) { detail.getChildren().add(new Label("M\u1ea5t k\u1ebft n\u1ed1i; kh\u00f4ng suy ra th\u1eafng hay thanh to\u00e1n t\u1eeb d\u1eef li\u1ec7u c\u0169.")); return; }
        api.get(room).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!active() || generation != receiptGeneration) { return; }
            if (error != null || !response.isSuccess()) {
                detail.getChildren().setAll(new Label(error != null ? "Ch\u01b0a x\u00e1c nh\u1eadn \u0111\u01b0\u1ee3c k\u1ebft qu\u1ea3. H\u00e3y th\u1eed l\u1ea1i." : response.getMessageText()), retry); return;
            }
            render(response.getWireMessage().getData());
        }));
    }
    private void render(Map<String, String> data) {
        Label title = DesktopTheme.label("#" + data.get("auctionId") + " · " + data.get("productName"), "receipt-product-name");
        Label personal = roleBadge(data.get("personalRole"), true); personal.setId("receipt-personal-result");
        VBox identity = new VBox(7, title, personal); identity.setAlignment(Pos.CENTER_LEFT); identity.setMinWidth(0); HBox.setHgrow(identity, Priority.ALWAYS);
        HBox productHeading = new HBox(12, preview(data, 88, 66), identity); productHeading.setAlignment(Pos.CENTER_LEFT);
        VBox session = section("Thông tin phiên", "gavel", field("Mã phiên:", new Label("#" + data.get("auctionId"))),
                field("Thời gian kết thúc:", new Label(time(data.get("endedAt")))), field("Trạng thái:", outcomeBadge(data.get("outcome"), true)));
        VBox product = section("Thông tin sản phẩm", "box", field("Tên sản phẩm:", new Label(data.get("productName"))),
                field("Mã sản phẩm:", new Label(data.getOrDefault("productCode", "—"))),
                field("Mô tả:", new Label(data.getOrDefault("productDescription", "").isBlank() ? "Chưa có mô tả trong biên nhận này." : data.get("productDescription"))));
        Label winner = new Label(data.getOrDefault("winnerDisplay", "").isBlank() ? "Không có" : data.get("winnerDisplay")); winner.setId("receipt-winner");
        VBox result = section("Kết quả của bạn", "profile", field("Người thắng:", winner), field("Giá chốt:", new Label(money(data.get("finalPrice")))));
        if ("WINNER".equals(data.get("personalRole"))) { result.getStyleClass().add("receipt-result-won"); }
        HBox numbers = new HBox(0, statistic("Tổng lượt ra giá", data.get("totalBids")), statistic("Người ra giá", data.get("uniqueBidders")), statistic("Gia hạn", data.get("extensionCount")));
        VBox stats = section("Thông tin phiên đấu", "gavel", numbers);
        if (!"true".equals(data.get("extensionsComplete"))) { stats.getChildren().add(DesktopTheme.label("Nhật ký cũ chưa đủ dữ liệu gia hạn.", "muted")); }
        Label wallet = new Label("V\u00ed m\u00f4 ph\u1ecfng: " + walletText(data.get("walletStatus")) + " \u00b7 C\u00f2n gi\u1eef: " + money(data.get("heldAmount"))); wallet.setId("receipt-wallet"); wallet.setWrapText(true);
        wallet.getStyleClass().add("receipt-wallet-status");
        VBox payment = new VBox(6);
        payment.setId("receipt-platform-fee");
        if (data.containsKey("platformFee") && "PAID".equals(data.get("walletStatus"))) {
            String percent = new BigDecimal(data.get("feeRate")).multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString();
            boolean tax = data.containsKey("baseFeeRate");
            if (tax) {
                String base = new BigDecimal(data.get("baseFeeRate")).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
                String reduction = new BigDecimal(data.get("feeDiscountRate")).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
                payment.getChildren().add(field("Thuế suất ban đầu:", new Label(base + "%")));
                payment.getChildren().add(field("Ưu đãi doanh thu:", new Label("0".equals(data.get("revenueRank")) ? "Chưa thuộc Top 10" : "Top " + data.get("revenueRank") + " · Giảm " + reduction + " điểm %")));
            }
            payment.getChildren().addAll(field((tax ? "Thuế thực thu (" : "Phí sàn (") + percent + "%):", new Label(money(data.get("platformFee")))),
                    field("Người mua thanh toán:", new Label(money(data.get("finalPrice")))),
                    field("Người bán thực nhận:", new Label(money(data.get("sellerNet")))));
            payment.setAccessibleText("Thuế và phí được trừ vào tiền người bán.");
        }
        payment.setVisible(!payment.getChildren().isEmpty()); payment.setManaged(payment.isVisible());
        result.getChildren().add(payment);
        detail.getChildren().setAll(productHeading, session, product, result, stats, wallet);
    }
    private static int number(Map<String, String> data, String key) { return Integer.parseInt(data.getOrDefault(key, "0")); }
    private static String money(String raw) { return raw == null || raw.isBlank() ? "--" : WalletUi.money(new BigDecimal(raw)); }
    private static String time(String raw) {
        return raw == null || raw.isBlank() ? "--" : java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault()).format(Instant.parse(raw));
    }
    public static String outcomeText(String raw) {
        return switch (raw == null ? "" : raw) { case "WON" -> "Kết thúc có người thắng"; case "NO_BIDS" -> "Kết thúc không có lượt ra giá"; case "CANCELLED" -> "Phiên bị hủy"; default -> "Tất cả trạng thái"; };
    }
    public static String roleText(String raw) {
        return switch (raw == null ? "" : raw) { case "WINNER" -> "B\u1ea1n \u0111\u00e3 th\u1eafng"; case "NOT_WON" -> "B\u1ea1n kh\u00f4ng th\u1eafng"; case "HOST" -> "B\u1ea1n l\u00e0 ch\u1ee7 ph\u00f2ng"; default -> "B\u1ea1n ch\u1ec9 theo d\u00f5i"; };
    }
    private static String walletText(String raw) {
        return switch (raw == null ? "" : raw) { case "PAID" -> "Đã thanh toán (sổ giao dịch xác nhận)"; case "RELEASED" -> "Đã giải phóng giữ chỗ"; case "REVIEW_REQUIRED" -> "Cần đối soát, chưa xác nhận thanh toán"; default -> "Không có giao dịch liên quan"; };
    }
    private static StringConverter<String> converter(boolean scopes) {
        return new StringConverter<>() {
            @Override public String toString(String value) { return !scopes ? outcomeText(value) : switch (value == null ? "" : value) {
                case "WON" -> "Phi\u00ean t\u00f4i \u0111\u00e3 th\u1eafng"; case "HOSTED" -> "Phi\u00ean t\u00f4i t\u1ed5 ch\u1ee9c"; default -> "Phi\u00ean t\u00f4i \u0111\u00e3 tham gia"; }; }
            @Override public String fromString(String value) { return value; }
        };
    }
}
