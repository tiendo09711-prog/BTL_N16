package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.common.fx.WalletUi;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Product covers are reused across price/countdown updates instead of downloading on every tick. */
public final class AuctionMarketplacePane extends ScrollPane implements AutoCloseable {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private final ObservableList<ClientAuction> items = FXCollections.observableArrayList();
    private final SingleSelectionModel<ClientAuction> selection = new SingleSelectionModel<>() {
        @Override protected ClientAuction getModelItem(int index) { return index < 0 || index >= items.size() ? null : items.get(index); }
        @Override protected int getItemCount() { return items.size(); }
    };
    private final GridPane grid = new GridPane();
    private final FeaturedAuctionsPane featured = new FeaturedAuctionsPane();
    private final VBox content = new VBox(16, featured, grid);
    private final VBox empty = new VBox(8, new Label("Chưa có sản phẩm phù hợp"), new Label("Thử tìm kiếm khác hoặc xóa bộ lọc."));
    private final Map<Long, ProductCard> cards = new LinkedHashMap<>();
    private Function<ClientAuction, CompletableFuture<Image>> covers = auction -> CompletableFuture.completedFuture(null);
    private Supplier<Instant> now = Instant::now;
    private Consumer<ClientAuction> onEnter = auction -> { };
    private Runnable onFavorites = () -> { };
    private final Set<Long> favorites = new HashSet<>();
    private boolean listMode;
    private int columns;

    public AuctionMarketplacePane() {
        setId("auction-marketplace"); getStyleClass().add("auction-marketplace");
        setFitToWidth(true); setHbarPolicy(ScrollBarPolicy.NEVER); setMinSize(0, 0);
        grid.setHgap(14); grid.setVgap(16); grid.setId("auction-product-grid");
        content.setMinHeight(Region.USE_PREF_SIZE); content.setFillWidth(true);
        empty.setId("auction-marketplace-empty"); empty.getStyleClass().add("marketplace-empty"); empty.setAlignment(Pos.CENTER);
        setContent(content);
        viewportBoundsProperty().addListener((observable, before, after) -> arrange());
        selection.selectedItemProperty().addListener((observable, before, after) -> refreshSelection());
        items.addListener((ListChangeListener<ClientAuction>) change -> rebuild());
        rebuild();
    }

    public ObservableList<ClientAuction> getItems() { return items; }
    public SingleSelectionModel<ClientAuction> getSelectionModel() { return selection; }
    public void configure(Function<ClientAuction, CompletableFuture<Image>> loader, Supplier<Instant> clock) {
        covers = loader; now = clock;
        featured.configure(loader, clock, auction -> { selection.select(auction); onEnter.accept(auction); });
    }
    public void setFeatured(Map<String, String> data) { featured.setData(data); }
    public void applyOccupancy(long auctionId, int count, long version) { featured.applyOccupancy(auctionId, count, version); }
    public void clearFeatured() { featured.close(); }
    public void featuredUnavailable() { featured.unavailable(); }
    public void showFeatured(boolean visible) { featured.setVisible(visible); featured.setManaged(visible); }
    public void onEnter(Consumer<ClientAuction> action) { onEnter = action; }
    public void onFavorites(Runnable action) { onFavorites = action; }
    public boolean isFavorite(long id) { return favorites.contains(id); }
    public void toggleFavorite(long id) {
        if (!favorites.remove(id)) { favorites.add(id); }
        cards.values().forEach(card -> card.favorite.setSelected(favorites.contains(card.auction.getAuctionId())));
        onFavorites.run();
    }
    public void clearFavorites() { favorites.clear(); onFavorites.run(); }
    public void setListMode(boolean value) { if (listMode != value) { listMode = value; cards.values().forEach(ProductCard::layoutCard); arrange(); } }
    public void refresh() { cards.values().forEach(ProductCard::refresh); featured.tick(); }
    public void retryImages() { cards.values().forEach(card -> { if (!card.loading && card.image.getImage() == null) { card.imageKey = ""; card.update(card.auction); } }); }

    private void rebuild() {
        ClientAuction selected = selection.getSelectedItem();
        long id = selected == null ? 0 : selected.getAuctionId();
        cards.entrySet().removeIf(entry -> {
            if (items.stream().anyMatch(item -> item.getAuctionId() == entry.getKey())) { return false; }
            entry.getValue().dispose(); return true;
        });
        for (ClientAuction item : items) {
            ProductCard card = cards.computeIfAbsent(item.getAuctionId(), ignored -> new ProductCard());
            card.update(item);
        }
        selection.clearSelection();
        for (int index = 0; index < items.size(); index++) { if (items.get(index).getAuctionId() == id) { selection.select(index); break; } }
        boolean hasItems = !items.isEmpty(); grid.setVisible(hasItems); grid.setManaged(hasItems);
        if (hasItems) { content.getChildren().setAll(featured, grid); } else { content.getChildren().setAll(featured, empty); }
        arrange();
    }

    private void arrange() {
        double width = getViewportBounds().getWidth();
        int count = listMode ? 1 : width >= 1500 ? 3 : width >= 560 ? 2 : 1;
        if (columns != count) {
            columns = count; grid.getColumnConstraints().clear();
            for (int index = 0; index < count; index++) {
                ColumnConstraints column = new ColumnConstraints(); column.setPercentWidth(100.0 / count);
                column.setHgrow(Priority.ALWAYS); column.setMinWidth(0); grid.getColumnConstraints().add(column);
            }
        }
        grid.getChildren().clear();
        for (int index = 0; index < items.size(); index++) { grid.add(cards.get(items.get(index).getAuctionId()).button, index % count, index / count); }
    }

    private void refreshSelection() {
        ClientAuction chosen = selection.getSelectedItem();
        cards.forEach((id, card) -> card.button.pseudoClassStateChanged(SELECTED, chosen != null && chosen.getAuctionId() == id));
    }

    @Override public void close() { items.clear(); featured.close(); }

    private final class ProductCard {
        private final Button button = new Button();
        private final ImageView image = new ImageView();
        private final Label placeholder = new Label("Đang tải ảnh…");
        private final Label title = new Label(), price = new Label(), status = new Label(), room = new Label();
        private final Label state = new Label(), host = new Label(), watchers = new Label(), step = new Label(), access = new Label();
        private final Button enter = new Button("Vào phòng");
        private final ToggleButton favorite = new ToggleButton();
        private final VBox information = new VBox(7), gridBody = new VBox();
        private final HBox rowBody = new HBox(12);
        private final StackPane frame = new StackPane(placeholder, image) {
            @Override public Orientation getContentBias() { return Orientation.HORIZONTAL; }
            @Override protected double computePrefHeight(double width) { return listMode ? 142 : Math.max(1, width < 0 ? getWidth() : width) * .54; }
        };
        private ClientAuction auction;
        private String imageKey = "";
        private long generation;
        private boolean loading;

        ProductCard() {
            button.getStyleClass().add("auction-product-card"); button.setMaxWidth(440); button.setMinWidth(0); button.setPrefWidth(310);
            button.setWrapText(true); button.setMaxHeight(Region.USE_PREF_SIZE); GridPane.setFillHeight(button, false);
            frame.getStyleClass().add("product-cover"); frame.setMinSize(0, 0);
            frame.setMaxHeight(Region.USE_PREF_SIZE);
            Rectangle clip = new Rectangle(); clip.widthProperty().bind(frame.widthProperty()); clip.heightProperty().bind(frame.heightProperty());
            clip.setArcWidth(22); clip.setArcHeight(22); frame.setClip(clip);
            image.setManaged(false); image.setSmooth(true);
            image.fitWidthProperty().bind(frame.widthProperty()); image.fitHeightProperty().bind(frame.heightProperty());
            frame.widthProperty().addListener((observable, before, after) -> crop());
            frame.heightProperty().addListener((observable, before, after) -> crop());
            title.getStyleClass().add("product-card-title"); title.setWrapText(true); title.setMinWidth(0); title.setMaxWidth(Double.MAX_VALUE);
            price.getStyleClass().add("product-card-price"); price.setWrapText(true);
            status.getStyleClass().add("product-card-status"); room.getStyleClass().add("product-card-room");
            state.getStyleClass().add("market-live-badge");
            state.setGraphic(MarketplaceIcons.icon("clock", 10, "#fffaf7"));
            favorite.getStyleClass().addAll("market-round-button", "market-favorite");
            favorite.setGraphic(MarketplaceIcons.icon("heart", 15, "#7a6851"));
            favorite.setTooltip(new Tooltip("Đánh dấu yêu thích trong phiên đăng nhập này")); favorite.setAccessibleText("Yêu thích sản phẩm");
            favorite.setOnAction(event -> { if (auction != null) { toggleFavorite(auction.getAuctionId()); } event.consume(); });
            HBox badges = new HBox(state, spacer(), favorite); badges.setAlignment(Pos.TOP_CENTER);
            badges.setPickOnBounds(false); StackPane.setAlignment(badges, Pos.TOP_CENTER); StackPane.setMargin(badges, new Insets(9));
            status.setGraphic(MarketplaceIcons.icon("clock", 13, "#fff8ed"));
            StackPane.setAlignment(status, Pos.BOTTOM_RIGHT); StackPane.setMargin(status, new Insets(8));
            frame.getChildren().addAll(badges, status);
            access.getStyleClass().add("market-category-tag"); room.getStyleClass().add("market-room-tag");
            HBox tags = new HBox(7, room, access); tags.setAlignment(Pos.CENTER_LEFT);
            host.getStyleClass().add("market-card-host"); host.setGraphic(MarketplaceIcons.icon("profile", 16, "#939393"));
            Label caption = new Label("Giá hiện tại"); caption.getStyleClass().add("market-card-caption");
            step.getStyleClass().add("market-card-step");
            VBox increment = new VBox(2, new Label("Bước giá"), step); increment.getStyleClass().add("market-increment");
            HBox stepBlock = new HBox(7, MarketplaceIcons.icon("gavel", 22, "#89602a"), increment); stepBlock.setAlignment(Pos.CENTER_LEFT);
            HBox pricing = new HBox(8, new VBox(1, caption, price), spacer(), stepBlock); pricing.setAlignment(Pos.CENTER_LEFT);
            watchers.getStyleClass().add("market-card-watchers"); watchers.setGraphic(MarketplaceIcons.icon("users", 14, "#aa7a35"));
            enter.getStyleClass().add("market-gold-button"); enter.setGraphic(MarketplaceIcons.icon("right", 13, "#fff9ef")); enter.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
            enter.setOnAction(event -> { if (auction != null) { selection.select(auction); onEnter.accept(auction); } event.consume(); });
            HBox footer = new HBox(8, watchers, spacer(), enter); footer.setAlignment(Pos.CENTER_LEFT);
            information.getStyleClass().add("market-card-information"); information.setMinWidth(0);
            information.getChildren().setAll(title, tags, host, pricing, footer); HBox.setHgrow(information, Priority.ALWAYS);
            gridBody.setMinWidth(0); rowBody.setMinWidth(0);
            gridBody.prefWidthProperty().bind(button.widthProperty().subtract(2)); rowBody.prefWidthProperty().bind(button.widthProperty().subtract(2));
            layoutCard(); button.setAlignment(Pos.TOP_LEFT);
            button.setOnAction(event -> { if (event.getTarget() == button && auction != null) { selection.select(auction); } });
        }

        void layoutCard() {
            gridBody.getChildren().clear(); rowBody.getChildren().clear();
            frame.setMinWidth(listMode ? 180 : 0); frame.setPrefWidth(listMode ? 180 : Region.USE_COMPUTED_SIZE); frame.setMaxWidth(listMode ? 180 : Double.MAX_VALUE);
            button.setMaxWidth(listMode ? Double.MAX_VALUE : 440);
            if (listMode) { rowBody.getChildren().setAll(frame, information); button.setGraphic(rowBody); }
            else { gridBody.getChildren().setAll(frame, information); button.setGraphic(gridBody); }
        }

        void update(ClientAuction value) {
            auction = value; button.setId("auction-card-" + value.getAuctionId());
            title.setText(value.getProductName()); button.setAccessibleText(value.getProductName() + ", " + WalletUi.money(value.getCurrentPrice()) + ". Xem chi tiết và vào phòng.");
            room.setText("#" + value.getAuctionId()); access.setText(value.requiresPassword() ? "Riêng tư" : "Công khai");
            host.setText("Phòng: " + value.getHostUsername());
            favorite.setSelected(favorites.contains(value.getAuctionId())); enter.setId("auction-enter-" + value.getAuctionId()); refresh();
            String key = value.getProductId() + ":" + value.getImageVersion() + ":" + value.hasImage();
            if (key.equals(imageKey)) { return; }
            imageKey = key; long expected = ++generation;
            image.setImage(null); placeholder.setVisible(true); placeholder.setText(value.hasImage() ? "Đang tải ảnh…" : "Chưa có ảnh sản phẩm");
            if (!value.hasImage()) { loading = false; return; }
            loading = true;
            covers.apply(value).whenComplete((cover, error) -> Platform.runLater(() -> {
                if (generation != expected) { if (cover != null) { cover.cancel(); } return; }
                if (error != null || cover == null) { unavailable(); return; }
                image.setImage(cover); crop();
                Runnable show = () -> {
                    if (generation != expected) { return; }
                    if (cover.isError()) { unavailable(); }
                    else if (cover.getProgress() == 1) { loading = false; placeholder.setVisible(false); crop(); }
                };
                cover.progressProperty().addListener((observable, before, after) -> show.run());
                cover.errorProperty().addListener((observable, before, after) -> show.run()); show.run();
            }));
        }

        void refresh() {
            if (auction == null) { return; }
            price.setText(WalletUi.money(auction.getCurrentPrice()));
            step.setText(WalletUi.money(auction.getMinBidIncrement()));
            watchers.setText(auction.getWatcherCount() + "/" + auction.getMaxParticipants() + " người" + (auction.isFull() ? " · Đầy" : ""));
            Instant instant = now.get();
            boolean starting = auction.getStartTime() != null && instant.isBefore(auction.getStartTime());
            long seconds = auction.getEndTime() == null ? 0 : Math.max(0, java.time.Duration.between(instant, auction.getEndTime()).getSeconds());
            status.setText(!auction.isOpen() ? "Đã kết thúc" : starting ? "Bắt đầu " + java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(java.time.ZoneId.systemDefault()).format(auction.getStartTime()) : seconds == 0 ? "Đang chốt kết quả" : "Còn " + String.format("%02d:%02d", seconds / 60, seconds % 60));
            state.setText(!auction.isOpen() ? "ĐÃ KẾT THÚC" : starting ? "SẮP DIỄN RA" : "Đang đấu giá");
            state.pseudoClassStateChanged(PseudoClass.getPseudoClass("upcoming"), starting);
            state.pseudoClassStateChanged(PseudoClass.getPseudoClass("ended"), !auction.isOpen());
            enter.setText(auction.isOpen() && !starting ? "Vào phòng" : "Xem phiên");
            AuctionDeadlineStyle.update(status, auction, instant);
        }

        private void crop() {
            Image cover = image.getImage();
            if (cover == null || cover.getWidth() <= 0 || cover.getHeight() <= 0 || frame.getWidth() <= 0 || frame.getHeight() <= 0) { return; }
            double ratio = frame.getWidth() / frame.getHeight(), width = cover.getWidth(), height = cover.getHeight();
            if (width / height > ratio) { double visible = height * ratio; image.setViewport(new Rectangle2D((width - visible) / 2, 0, visible, height)); }
            else { double visible = width / ratio; image.setViewport(new Rectangle2D(0, (height - visible) / 2, width, visible)); }
            image.relocate(0, 0);
        }

        private void unavailable() { loading = false; image.setImage(null); placeholder.setVisible(true); placeholder.setText(auction.requiresPassword() ? "Vào phòng để xem ảnh" : "Ảnh chưa sẵn sàng"); }
        void dispose() { generation++; loading = false; Image previous = image.getImage(); if (previous != null) { previous.cancel(); } image.setImage(null); }
    }

    private static Region spacer() { Region region = new Region(); HBox.setHgrow(region, Priority.ALWAYS); return region; }
}
