package vn.ptit.btl16.server.fx;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import vn.ptit.btl16.client.fx.LiveAuctionRoom;
import vn.ptit.btl16.client.fx.RoomProgressPane;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.dashboard.AuctionSessionService;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

final class AuctionSessionDetailPane extends BorderPane {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final Label title = SessionUi.text("", "session-detail-title");
    private final Label badge = DesktopTheme.label("", "pill");
    private final Label productName = SessionUi.text("", "session-product-name");
    private final Label productCode = SessionUi.text("", "session-field-label");
    private final Label description = SessionUi.text("", "session-description");
    private final Label currentCaption = SessionUi.text("", "session-price-caption");
    private final Label currentPrice = SessionUi.text("", "session-current-price");
    private final Label startingPrice = SessionUi.text("", "session-starting-price");
    private final Label leaderCaption = SessionUi.text("", "session-price-caption");
    private final Label leader = SessionUi.text("", "session-leader-name");
    private final Label visibility = DesktopTheme.label("", "session-access-badge");
    private final VBox timing = new VBox(16);
    private final VBox product = new VBox(18);
    private final VBox seller = new VBox(18);
    private final VBox participants = new VBox(12);
    private final VBox pictures = new VBox(12);
    private final LiveAuctionRoom live = new LiveAuctionRoom();
    private final RoomProgressPane journal;
    private final ScrollPane historyScroll;
    private final TabPane tabs = new TabPane();
    private final List<ToggleButton> tabButtons = new ArrayList<>();
    private List<AuctionSessionService.Participant> lastParticipants;
    private AuctionSessionService.Profile lastSeller;
    private boolean rendered;

    AuctionSessionDetailPane(ServerApplication server, long room, Executor worker,
                             BooleanSupplier available, Runnable back, Runnable refresh) {
        setId("server-session-detail"); setMinSize(0, 0);
        Button goBack = new Button("Về danh sách", ServerIcons.icon("back"));
        goBack.setId("server-session-back"); goBack.getStyleClass().add("session-secondary-button");
        goBack.setOnAction(event -> back.run());
        Button reload = new Button("Làm mới", ServerIcons.icon("refresh"));
        reload.setId("server-session-refresh"); reload.getStyleClass().add("session-secondary-button");
        reload.setOnAction(event -> refresh.run());
        badge.setMinWidth(Region.USE_PREF_SIZE);
        HBox heading = new HBox(14, goBack, title, DesktopTheme.spacer(), badge, reload);
        heading.setAlignment(Pos.CENTER_LEFT); heading.getStyleClass().add("session-detail-heading");

        journal = new RoomProgressPane(new ServerRoomHistoryTransport(server, room, worker, available));
        journal.setFlowLayout(true); journal.onStatistics(live::setStatistics); live.onBidSelected(journal::focusBid);
        VBox journalCard = SessionUi.card(SessionUi.heading("Nhật ký phiên", "log"), journal);
        journalCard.getStyleClass().add("room-journal-card");
        participants.setId("server-session-participants"); participants.getStyleClass().add("session-surface");
        participants.setMinHeight(Region.USE_PREF_SIZE);
        VBox history = new VBox(18, live.insights(), journalCard, participants);
        history.getStyleClass().add("session-tab-body"); history.setMinHeight(Region.USE_PREF_SIZE);
        historyScroll = scroll(history); journal.onReveal(this::reveal);
        product.getStyleClass().add("session-tab-body"); product.setMinHeight(Region.USE_PREF_SIZE);
        seller.getStyleClass().add("session-tab-body"); seller.setMinHeight(Region.USE_PREF_SIZE);
        tabs.setId("server-session-tabs"); tabs.getStyleClass().add("session-content-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().setAll(new Tab("Chi tiết sản phẩm", scroll(product)),
                new Tab("Thông tin người bán", scroll(seller)), new Tab("Lịch sử phiên", historyScroll));
        ToggleGroup group = new ToggleGroup();
        HBox navigation = new HBox(5); navigation.setId("server-session-tab-buttons");
        navigation.getStyleClass().addAll("room-side-tabs", "session-detail-tabs");
        String[] icons = {"box", "user", "chart"};
        for (int index = 0; index < tabs.getTabs().size(); index++) {
            int selected = index;
            ToggleButton button = new ToggleButton(tabs.getTabs().get(index).getText(), ServerIcons.icon(icons[index]));
            button.setId("server-session-tab-" + index); button.setToggleGroup(group);
            button.setMinWidth(0); button.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(button, Priority.ALWAYS);
            button.setOnAction(event -> { tabs.getSelectionModel().select(selected); button.setSelected(true); });
            tabButtons.add(button); navigation.getChildren().add(button);
        }
        tabButtons.getFirst().setSelected(true);
        tabs.getSelectionModel().selectedIndexProperty().addListener((observable, before, selected) -> {
            if (selected.intValue() < 0) { return; }
            tabButtons.get(selected.intValue()).setSelected(true);
            ScrollPane viewport = (ScrollPane) tabs.getTabs().get(selected.intValue()).getContent();
            viewport.setVvalue(0); viewport.setHvalue(0);
            Platform.runLater(() -> { if (tabs.getSelectionModel().getSelectedIndex() == selected.intValue()) { viewport.setVvalue(0); } });
        });
        VBox top = new VBox(14, heading, navigation); top.getStyleClass().add("session-detail-top");
        setTop(top); setCenter(tabs);
        buildProduct();
    }

    private void buildProduct() {
        HBox metadata = new HBox(10, productCode, DesktopTheme.spacer(), visibility); metadata.setAlignment(Pos.CENTER_LEFT);
        VBox gallery = SessionUi.card(productName, metadata, pictures); gallery.setId("server-session-gallery-card");
        VBox prices = new VBox(10, currentCaption, currentPrice,
                new HBox(8, SessionUi.text("Giá khởi điểm", "session-price-caption"), startingPrice),
                new Separator(), leaderCaption, leader);
        prices.setId("server-session-price-card"); prices.getStyleClass().add("session-price-card");
        prices.setMinHeight(Region.USE_PREF_SIZE);
        timing.getStyleClass().add("session-surface"); timing.setMinWidth(0); timing.setMinHeight(Region.USE_PREF_SIZE);
        VBox aside = new VBox(18, prices, timing); aside.setMinWidth(0);
        GridPane columns = SessionUi.columns(gallery, aside, 58, 780); columns.setId("server-session-product-columns");
        VBox about = SessionUi.card(SessionUi.heading("Về sản phẩm", "box"), description);
        product.getChildren().setAll(columns, about);
    }

    void update(AuctionSessionService.Details details, boolean updatePictures) {
        var auction = details.auction();
        title.setText("Phòng " + AuctionSessionsPage.roomCode(auction));
        badge.setText(AuctionSessionsPage.state(auction));
        badge.getStyleClass().removeAll("session-state-open", "session-state-ended", "session-state-cancelled");
        badge.getStyleClass().add(AuctionSessionsPage.stateStyle(auction));
        productName.setText(auction.getProduct().getName());
        productCode.setText("Mã sản phẩm: " + auction.getProduct().getCode());
        description.setText(auction.getProduct().getDescription().isBlank() ? "Chưa có mô tả sản phẩm." : auction.getProduct().getDescription());
        visibility.setText(auction.requiresPassword() ? "PHÒNG RIÊNG TƯ" : "PHÒNG CÔNG KHAI");
        currentCaption.setText(auction.getStatus() == AuctionStatus.ENDED ? "Giá đã chốt" : "Giá hiện tại");
        currentPrice.setText(WalletUi.money(auction.getCurrentPrice()));
        startingPrice.setText(WalletUi.money(auction.getStartPrice()));
        leaderCaption.setText(auction.getStatus() == AuctionStatus.OPEN ? "Người dẫn đầu" : "Người chiến thắng");
        String winner = details.chartData().getOrDefault("currentWinnerUsername", "");
        if (auction.getStatus() == AuctionStatus.CANCELLED) { winner = "Không áp dụng - phiên đã hủy"; }
        else if (auction.getCurrentWinnerId() == null) {
            winner = auction.getStatus() == AuctionStatus.ENDED ? "Không có người chiến thắng" : "Chưa có người đặt giá";
        }
        leader.setText(winner);
        timing.getChildren().setAll(SessionUi.heading("Thông tin phiên", "clock"), SessionUi.facts(
                SessionUi.field("Bắt đầu", time(auction.getStartTime())),
                SessionUi.field("Kết thúc", time(auction.getEndedAt() == null ? auction.getEndTime() : auction.getEndedAt())),
                SessionUi.field("Bước giá", WalletUi.money(auction.getMinBidIncrement())),
                SessionUi.field("Đặt cọc", WalletUi.money(auction.getDepositAmount()))),
                SessionUi.text(auction.hasAnonymousGuests() ? "Bảo mật danh tính người tham gia" : "Hiển thị danh tính người tham gia", "session-field-label"));
        if (updatePictures || !rendered) { renderPictures(details); }
        if (!rendered || !java.util.Objects.equals(lastSeller, details.seller())) {
            renderSeller(details.seller()); lastSeller = details.seller();
        }
        if (!rendered || !details.participants().equals(lastParticipants)) {
            renderParticipants(details.participants(), auction.hasAnonymousGuests()); lastParticipants = details.participants();
        }
        live.update(ClientWireParser.auction(details.chartData(), ""), ClientWireParser.bids(details.chartData()),
                Long.MIN_VALUE, true, Instant.now());
        journal.setRoom(auction.getAuctionId()); rendered = true;
    }

    void tick() { if (rendered) { journal.tick(); live.tick(Instant.now(), true); } }
    void suspend() { journal.invalidateRequests(); }

    private void renderPictures(AuctionSessionService.Details details) {
        pictures.getChildren().clear();
        SessionImagePane preview = new SessionImagePane(false, 258);
        preview.view().setId("server-session-product-image");
        Label counter = SessionUi.text("", "session-field-label");
        FlowPane thumbnails = new FlowPane(8, 8); thumbnails.getStyleClass().add("session-thumbnails");
        ToggleGroup group = new ToggleGroup();
        List<Image> images = new ArrayList<>();
        for (var picture : details.pictures()) {
            Image image = new Image(new ByteArrayInputStream(picture.bytes()));
            if (image.isError()) { continue; }
            int position = images.size(); images.add(image);
            ImageView thumbnail = new ImageView(image); thumbnail.setPreserveRatio(true);
            thumbnail.setFitWidth(58); thumbnail.setFitHeight(42);
            ToggleButton choose = new ToggleButton("", thumbnail); choose.setToggleGroup(group);
            choose.getStyleClass().add("session-thumbnail"); choose.setAccessibleText(picture.name());
            Tooltip.install(choose, new Tooltip(picture.name()));
            choose.setOnAction(event -> {
                preview.setImage(image); choose.setSelected(true); counter.setText("Ảnh " + (position + 1) + " / " + images.size());
            });
            thumbnails.getChildren().add(choose);
        }
        if (!images.isEmpty()) {
            preview.setImage(images.getFirst()); ((ToggleButton) thumbnails.getChildren().getFirst()).setSelected(true);
            counter.setText("Ảnh 1 / " + images.size());
        } else { counter.setText("Chưa có ảnh sản phẩm"); }
        HBox caption = new HBox(8, ServerIcons.icon("image"), counter); caption.setAlignment(Pos.CENTER_LEFT);
        pictures.getChildren().setAll(preview, thumbnails, caption);
        if (images.isEmpty()) { thumbnails.setManaged(false); }
        if (!details.mediaWarning().isBlank()) { pictures.getChildren().add(SessionUi.text(details.mediaWarning(), "session-field-label")); }
    }

    private void renderSeller(AuctionSessionService.Profile profile) {
        seller.getChildren().clear();
        if (profile == null) {
            seller.getChildren().add(SessionUi.card(SessionUi.text("Hồ sơ người bán không còn tồn tại", "session-field-label"))); return;
        }
        Label state = DesktopTheme.label(profile.active() ? "Đang hoạt động" : "Đã vô hiệu hóa", "pill");
        state.getStyleClass().add(profile.active() ? "session-state-open" : "session-state-ended");
        VBox identity = new VBox(12, SessionUi.avatar(profile.avatar(), profile.name(), false, 80),
                SessionUi.text(profile.name(), "session-seller-name"), SessionUi.text("@" + profile.username(), "session-seller-username"), state);
        identity.setAlignment(Pos.CENTER); identity.getStyleClass().addAll("session-surface", "session-seller-identity");
        identity.setMinWidth(0); identity.setMinHeight(Region.USE_PREF_SIZE);
        VBox contact = SessionUi.card(SessionUi.heading("Thông tin người bán", "user"), SessionUi.facts(
                SessionUi.field("Tên hiển thị", profile.name()), SessionUi.field("Username", profile.username()),
                SessionUi.field("Email", profile.email()), SessionUi.field("Số điện thoại", profile.phone()),
                SessionUi.field("Mã kết bạn", profile.friendCode()),
                SessionUi.field("Trạng thái tài khoản", profile.active() ? "Đang hoạt động" : "Đã vô hiệu hóa")));
        GridPane columns = SessionUi.columns(identity, contact, 31, 710); columns.setId("server-session-seller-columns");
        VBox biography = SessionUi.card(SessionUi.heading("Giới thiệu", "log"),
                SessionUi.text(profile.biography().isBlank() ? "Người bán chưa thêm giới thiệu." : profile.biography(), "session-description"));
        VBox activity = SessionUi.card(SessionUi.heading("Thông tin tài khoản", "clock"), SessionUi.facts(
                SessionUi.field("Ngày tạo tài khoản", time(profile.createdAt())),
                SessionUi.field("Cập nhật gần nhất", time(profile.updatedAt())),
                SessionUi.field("Đăng nhập gần nhất", time(profile.lastLoginAt()))));
        HBox privacy = new HBox(8, ServerIcons.icon("shield"), SessionUi.text("Hồ sơ chỉ dành cho quản trị. Không hiển thị mật khẩu.", "session-field-label"));
        privacy.setAlignment(Pos.CENTER_LEFT); privacy.getStyleClass().add("session-privacy-note");
        seller.getChildren().setAll(columns, biography, activity, privacy);
    }

    private void renderParticipants(List<AuctionSessionService.Participant> members, boolean anonymous) {
        HBox heading = SessionUi.heading("Người đã tham gia", "users");
        Label count = DesktopTheme.label(Integer.toString(members.size()), "session-count-badge");
        heading.getChildren().addAll(DesktopTheme.spacer(), count);
        Label note = SessionUi.text(anonymous
                ? "Phòng ẩn danh: avatar, username và tên thật được bảo mật. Thời điểm tham gia vẫn được hiển thị đầy đủ."
                : "Bao gồm cả người đã rời phòng. Lưu đầy đủ thời điểm vào lần đầu và lần quay lại gần nhất.", "session-field-label");
        participants.getChildren().setAll(heading, note);
        if (members.isEmpty()) { participants.getChildren().add(SessionUi.text("Chưa ghi nhận người tham gia", "session-field-label")); }
        for (var member : members) {
            VBox identity = new VBox(5, SessionUi.text((member.anonymous() ? "" : "@") + member.username(), "session-person-username"),
                    SessionUi.text(member.name(), "session-field-label"));
            HBox person = new HBox(12, SessionUi.avatar(member.avatar(), member.name(), member.anonymous(), 44), identity);
            person.setAlignment(Pos.CENTER_LEFT); person.setMinWidth(0); HBox.setHgrow(identity, Priority.ALWAYS);
            VBox joined = SessionUi.field("Vào phòng", time(member.joinedAt()));
            if (member.visits() > 1) { joined.getChildren().add(SessionUi.text("Gần nhất: " + time(member.lastJoinedAt())
                    + " · " + member.visits() + " lần vào", "session-field-label")); }
            GridPane row = SessionUi.columns(person, joined, 53, 560); row.getStyleClass().add("session-participant");
            participants.getChildren().add(row);
        }
    }

    private void reveal(Node node) {
        historyScroll.applyCss(); historyScroll.layout();
        double height = historyScroll.getContent().getBoundsInLocal().getHeight() - historyScroll.getViewportBounds().getHeight();
        if (height > 0) {
            double top = historyScroll.getContent().sceneToLocal(node.localToScene(node.getBoundsInLocal())).getMinY();
            historyScroll.setVvalue(Math.max(0, Math.min(1, top / height)));
        }
    }

    private static ScrollPane scroll(Node content) {
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true);
        scroll.getStyleClass().add("session-content-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.setMinSize(0, 0); return scroll;
    }

    private static String time(Instant value) { return value == null ? "Chưa cập nhật" : TIME.format(value); }
}
