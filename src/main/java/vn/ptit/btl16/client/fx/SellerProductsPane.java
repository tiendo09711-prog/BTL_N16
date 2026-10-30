package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;
import javafx.util.Duration;
import vn.ptit.btl16.client.model.ClientProduct;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.protocol.ProductTransactionStatus;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

final class SellerProductsPane extends BorderPane implements AutoCloseable {
    record Preview(Image image, int images, int videos) { }
    private final TableView<ClientProduct> table = new TableView<>();
    private final TextField search = new TextField();
    private final ComboBox<String> status = new ComboBox<>();
    private final VBox detail = new VBox(13);
    private final Label selectedCount = DesktopTheme.label("Chưa chọn sản phẩm", "account-note");
    private final TilePane cards = new TilePane(10, 10);
    private final List<ClientProduct> products = new ArrayList<>();
    private final Map<String, CompletableFuture<Preview>> previews = new HashMap<>();
    private final Function<ClientProduct, CompletableFuture<Preview>> loader;
    private final Button edit, media, delete;
    private final Timeline poll = new Timeline();
    private boolean closed;
    private long detailGeneration;

    SellerProductsPane(Function<ClientProduct, CompletableFuture<Preview>> loader,
                       Button add, Button edit, Button media, Button delete, Button refresh, Runnable close) {
        this.loader = loader; this.edit = edit; this.media = media; this.delete = delete;
        getStyleClass().addAll("account-page", "seller-products-page"); setId("seller-products-page"); setMinSize(0, 0);
        Button closeIcon = AccountUi.close(close); closeIcon.setId("seller-products-close");
        HBox heading = AccountUi.heading("Sản phẩm bán", "Quản lý sản phẩm bạn đăng bán đấu giá", "box", closeIcon);
        heading.getStyleClass().add("seller-heading"); setTop(heading);
        search.setId("seller-product-search"); search.setPromptText("Tìm theo mã, tên sản phẩm…"); search.setMinWidth(80); HBox.setHgrow(search, Priority.ALWAYS);
        HBox searchBox = new HBox(9, MarketplaceIcons.icon("search", 15, "#8a602b"), search); searchBox.setAlignment(Pos.CENTER_LEFT); searchBox.getStyleClass().add("seller-search"); searchBox.setMinWidth(0); HBox.setHgrow(searchBox, Priority.ALWAYS);
        status.getItems().setAll("ALL", "UNTRADED", "AUCTIONING", "SOLD"); status.setValue("ALL"); status.setId("seller-product-filter");
        status.setConverter(new StringConverter<>() {
            @Override public String toString(String raw) { return raw == null || raw.equals("ALL") ? "Tất cả trạng thái" : vn.ptit.btl16.common.protocol.ProductTransactionStatus.parse(raw).label(); }
            @Override public String fromString(String raw) { return raw; }
        }); status.setPrefWidth(150);
        ToggleButton list = new ToggleButton(), grid = new ToggleButton(); list.setGraphic(MarketplaceIcons.icon("list", 17, "#8a602b")); grid.setGraphic(MarketplaceIcons.icon("grid", 17, "#8a602b"));
        list.setId("seller-list-view"); grid.setId("seller-grid-view"); list.setAccessibleText("Dạng danh sách"); grid.setAccessibleText("Dạng lưới");
        ToggleGroup mode = new ToggleGroup(); list.setToggleGroup(mode); grid.setToggleGroup(mode); list.setSelected(true); HBox toggle = new HBox(list, grid); toggle.getStyleClass().add("account-view-toggle");
        HBox filters = new HBox(9, searchBox, status, toggle); filters.setAlignment(Pos.CENTER_LEFT); filters.getStyleClass().add("seller-filters");
        configureTable();
        cards.setPrefColumns(2); cards.setPrefTileWidth(180); cards.setAlignment(Pos.TOP_LEFT); cards.getStyleClass().add("seller-grid");
        ScrollPane gridScroll = new ScrollPane(cards); gridScroll.setFitToWidth(true); gridScroll.setVisible(false); gridScroll.setManaged(false); gridScroll.setId("seller-product-grid");
        cards.prefWidthProperty().bind(gridScroll.widthProperty().subtract(18));
        mode.selectedToggleProperty().addListener((o, old, current) -> {
            if (current == null) { mode.selectToggle(old); return; } boolean useGrid = current == grid;
            table.setVisible(!useGrid); table.setManaged(!useGrid); gridScroll.setVisible(useGrid); gridScroll.setManaged(useGrid);
        });
        StackPane listing = new StackPane(table, gridScroll); VBox.setVgrow(listing, Priority.ALWAYS);
        VBox left = AccountUi.card(filters, listing); left.setMinHeight(0); left.getStyleClass().add("seller-list-card"); HBox.setHgrow(left, Priority.ALWAYS);
        HBox detailHeading = new HBox(10, AccountUi.emblem("box", 36), DesktopTheme.label("Chi tiết sản phẩm", "account-section-title")); detailHeading.setAlignment(Pos.CENTER_LEFT);
        detail.setId("seller-product-detail"); detail.getStyleClass().add("seller-detail-body");
        ScrollPane detailScroll = new ScrollPane(detail); detailScroll.setMinHeight(0); detailScroll.setFitToWidth(true); detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); VBox.setVgrow(detailScroll, Priority.ALWAYS);
        VBox right = AccountUi.card(detailHeading, detailScroll); right.setMinHeight(0); right.setMinWidth(275); right.setPrefWidth(290); right.setMaxWidth(300); right.setId("seller-product-drawer");
        HBox body = new HBox(12, left, right); body.getStyleClass().add("seller-body"); setCenter(body);
        for (Button button : List.of(add, edit, media, delete, refresh)) { button.getStyleClass().add("account-button"); }
        add.setGraphic(MarketplaceIcons.icon("plus", 15, "#fff8ed")); add.getStyleClass().add("account-gold"); edit.setGraphic(MarketplaceIcons.icon("edit", 15, "#80572c"));
        media.setGraphic(MarketplaceIcons.icon("image", 15, "#80572c")); delete.setGraphic(MarketplaceIcons.icon("trash", 15, "#b65a45")); delete.getStyleClass().add("seller-delete");
        refresh.setGraphic(MarketplaceIcons.icon("refresh", 15, "#80572c"));
        add.setId("seller-add-product"); refresh.setId("seller-refresh-products");
        FlowPane actions = new FlowPane(8, 8, add, refresh); actions.getStyleClass().add("seller-actions");
        VBox footer = new VBox(9, selectedCount, actions); footer.getStyleClass().add("seller-footer"); setBottom(footer);
        table.getSelectionModel().selectedItemProperty().addListener((o, old, product) -> render(product));
        search.textProperty().addListener((o, old, value) -> filter()); status.setOnAction(event -> filter()); render(null);
    }
    TableView<ClientProduct> table() { return table; }
    void startPolling(Runnable reload) {
        poll.getKeyFrames().setAll(new KeyFrame(Duration.seconds(5), event -> reload.run()));
        poll.setCycleCount(Timeline.INDEFINITE); poll.play();
    }
    void setProducts(List<ClientProduct> values, Long selectedId) {
        products.clear(); values.stream().filter(product -> product.getTransactionStatus() != vn.ptit.btl16.common.protocol.ProductTransactionStatus.OWNED).forEach(products::add);
        previews.entrySet().removeIf(entry -> products.stream().noneMatch(value -> key(value).equals(entry.getKey())));
        filter(); if (selectedId != null) { table.getItems().stream().filter(value -> value.getProductId() == selectedId).findFirst().ifPresent(value -> table.getSelectionModel().select(value)); }
    }
    private void filter() {
        ClientProduct selected = table.getSelectionModel().getSelectedItem(); String text = search.getText().strip().toLowerCase(Locale.ROOT);
        var visible = products.stream().filter(p -> (p.getCode() + " " + p.getName()).toLowerCase(Locale.ROOT).contains(text))
                .filter(p -> "ALL".equals(status.getValue()) || p.getTransactionStatus().name().equals(status.getValue())).toList();
        table.getItems().setAll(visible); cards.getChildren().clear();
        for (ClientProduct product : visible) {
            Label name = DesktopTheme.label(product.getName(), "seller-grid-title");
            HBox cardActions = new HBox(8, badge(product), DesktopTheme.spacer()); cardActions.setAlignment(Pos.CENTER_LEFT);
            MenuButton more = actions(product); if (more != null) { cardActions.getChildren().add(more); }
            VBox card = new VBox(8, cover(product, 150), name, cardActions); card.getStyleClass().add("seller-grid-card");
            card.setOnMouseClicked(event -> { table.getSelectionModel().select(product); if (event.getClickCount() == 2) { media.fire(); } }); cards.getChildren().add(card);
        }
        if (selected != null) { visible.stream().filter(p -> p.getProductId() == selected.getProductId()).findFirst().ifPresent(p -> table.getSelectionModel().select(p)); }
        if (table.getSelectionModel().isEmpty() && !visible.isEmpty()) { table.getSelectionModel().selectFirst(); }
        if (visible.isEmpty()) { render(null); }
    }
    private void configureTable() {
        table.setId("seller-products"); table.getStyleClass().addAll("account-table", "seller-table"); table.setFixedCellSize(78); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column("Mã", 65, 47, p -> text(p.getCode())); column("Tên", 98, 68, p -> text(p.getName()));
        column("Ảnh", 72, 58, p -> cover(p, 48)); column("Trạng thái", 116, 96, SellerProductsPane::badge);
        column("Cập nhật", 126, 99, p -> text(date(p.getUpdatedAt()).replace(' ', '\n')));
        column("Thao tác", 66, 53, this::actions); table.setPlaceholder(DesktopTheme.label("Chưa có sản phẩm bán phù hợp", "account-note"));
    }
    private MenuButton actions(ClientProduct product) {
        ProductTransactionStatus transactionStatus = product.getTransactionStatus();
        if (transactionStatus != ProductTransactionStatus.UNTRADED && transactionStatus != ProductTransactionStatus.SOLD) { return null; }
        List<Button> available = transactionStatus == ProductTransactionStatus.UNTRADED ? List.of(edit, delete) : List.of(delete);
        MenuButton more = new MenuButton(); more.setGraphic(MarketplaceIcons.icon("more", 15, "#8b612d")); more.getStyleClass().addAll("account-button", "account-more"); more.setAccessibleText("Thao tác sản phẩm " + product.getName());
        for (Button action : available) {
            MenuItem item = new MenuItem(action.getText()); item.setOnAction(event -> { table.getSelectionModel().select(product); action.fire(); }); more.getItems().add(item);
        }
        more.setOnShowing(event -> { table.getSelectionModel().select(product); for (int index = 0; index < available.size(); index++) { more.getItems().get(index).setDisable(available.get(index).isDisabled()); } }); return more;
    }
    private void column(String title, double width, double min, Function<ClientProduct, Node> graphic) {
        TableColumn<ClientProduct, ClientProduct> column = new TableColumn<>(title); column.setPrefWidth(width); column.setMinWidth(min); column.setSortable(false); column.setReorderable(false);
        column.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue()));
        column.setCellFactory(ignored -> new TableCell<>() { @Override protected void updateItem(ClientProduct product, boolean empty) { super.updateItem(product, empty); setText(null); setGraphic(empty || product == null ? null : graphic.apply(product)); } }); table.getColumns().add(column);
    }
    private CompletableFuture<Preview> preview(ClientProduct product) {
        return previews.computeIfAbsent(key(product), ignored -> !product.hasImage() ? CompletableFuture.completedFuture(new Preview(null, 0, 0)) : loader.apply(product));
    }
    private Node cover(ClientProduct product, double size) {
        StackPane frame = new StackPane(MarketplaceIcons.icon("box", size > 60 ? 30 : 18, "#b29b7d")); frame.setMinSize(size, size); frame.setPrefSize(size, size); frame.setMaxSize(size, size); frame.getStyleClass().add("seller-product-image");
        Rectangle clip = new Rectangle(size, size); clip.setArcWidth(12); clip.setArcHeight(12); frame.setClip(clip);
        preview(product).whenComplete((value, error) -> Platform.runLater(() -> {
            if (closed || error != null || value == null || value.image() == null || value.image().isError()) { return; }
            ImageView image = new ImageView(value.image()); image.setFitWidth(size); image.setFitHeight(size); image.setPreserveRatio(true); image.setSmooth(true); image.setAccessibleText("Ảnh sản phẩm " + product.getName()); frame.getChildren().setAll(image);
        })); return frame;
    }
    private void render(ClientProduct product) {
        long token = ++detailGeneration; detail.getChildren().clear(); selectedCount.setText(product == null ? "Chưa chọn sản phẩm" : "✓ Đã chọn 1 sản phẩm");
        if (product == null) { detail.getChildren().add(DesktopTheme.label("Chọn sản phẩm để xem chi tiết.", "account-note")); return; }
        VBox identity = new VBox(5, DesktopTheme.label("Mã sản phẩm", "account-note"), DesktopTheme.label(product.getCode(), "account-section-title"), DesktopTheme.label("Tên sản phẩm", "account-note"), DesktopTheme.label(product.getName(), "account-section-title"), badge(product));
        Node image = cover(product, 106); image.setId("seller-detail-image"); image.setCursor(Cursor.HAND); image.setOnMouseClicked(event -> media.fire());
        Tooltip.install(image, new Tooltip("Xem toàn bộ ảnh / video"));
        identity.setMinWidth(0); HBox.setHgrow(identity, Priority.ALWAYS); HBox productHeader = new HBox(13, image, identity); productHeader.setAlignment(Pos.CENTER_LEFT);
        Label count = text(product.hasImage() ? "Đang tải…" : "Chưa có ảnh"); count.setId("seller-media-count");
        count.setCursor(Cursor.HAND); count.setOnMouseClicked(event -> media.fire()); count.setTooltip(new Tooltip("Xem toàn bộ ảnh / video"));
        detail.getChildren().addAll(productHeader, new Separator(), field("calendar", "Cập nhật lần cuối", date(product.getUpdatedAt())), field("image", "Ảnh / video", count), field("rules", "Mô tả", product.getDescription().isBlank() ? "Chưa có mô tả" : product.getDescription()), new Separator(),
                AccountUi.hint("Chỉ được sửa thông tin và ảnh/video trước khi tạo phiên đầu tiên. Tạo phiên sẽ khóa nội dung để giữ nguyên lịch sử."));
        preview(product).whenComplete((value, error) -> Platform.runLater(() -> {
            if (closed || token != detailGeneration) { return; }
            count.setText(error != null || value == null ? "Không tải được — bấm Làm mới để thử lại" : value.images() + " ảnh" + (value.videos() > 0 ? " · " + value.videos() + " video" : ""));
            if (error != null) { previews.remove(key(product)); }
        }));
    }
    private static Label text(String value) { return DesktopTheme.label(value, "seller-cell-text"); }
    private static Label badge(ClientProduct product) { return DesktopTheme.label("● " + product.getTransactionStatus().label(), "seller-status seller-status-" + product.getTransactionStatus().name().toLowerCase(Locale.ROOT)); }
    private static HBox field(String icon, String title, String value) { return field(icon, title, text(value)); }
    private static HBox field(String icon, String title, Label value) {
        Label label = DesktopTheme.label(title, "account-note"); label.setMinWidth(0); label.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(label, Priority.ALWAYS);
        value.setMinWidth(0); value.setMaxWidth(145); value.setAlignment(Pos.CENTER_RIGHT); HBox row = new HBox(9, MarketplaceIcons.icon(icon, 14, "#9b7342"), label, value); row.setAlignment(Pos.CENTER_LEFT); return row;
    }
    private static String date(java.time.Instant instant) { return instant == null ? "—" : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(instant); }
    private static String key(ClientProduct product) { return product.getProductId() + ":" + product.getImageVersion() + ":" + product.getUpdatedAt(); }
    @Override public void close() { closed = true; poll.stop(); detailGeneration++; table.getItems().clear(); cards.getChildren().clear(); detail.getChildren().clear(); products.clear(); previews.clear(); }
}
