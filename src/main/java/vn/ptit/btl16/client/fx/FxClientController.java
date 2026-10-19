package vn.ptit.btl16.client.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TableCell;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.util.ConnectionCode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.model.ClientBid;
import vn.ptit.btl16.client.model.ClientProduct;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.network.ConnectionState;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.client.service.ProductMediaApi;
import vn.ptit.btl16.client.service.WalletApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;
import vn.ptit.btl16.common.protocol.WireValues;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class FxClientController implements AutoCloseable {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final Stage stage;
    private final ClientConfig config;
    private final ClientAppModel model;
    private final ClientTransport transport;
    private final AccountApi accounts;
    private final AuctionApi auctions;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private long connectionGeneration;
    private ScheduledFuture<?> reconnectTask;

    private final TextField endpointField = new TextField();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label loginStateLabel = new Label("● Chưa kết nối");
    private final Label loginRttLabel = new Label("— ms");
    private final Button loginButton = new Button("Đăng nhập");
    private final Button registerButton = new Button("Đăng ký");
    private final Button connectButton = new Button("Kết nối lại");

    private final Label userLabel = new Label("Tài khoản");
    private final Label walletBalance = DesktopTheme.label("Ví: đang đồng bộ…", "muted");
    private long lastWalletRefresh;
    private boolean walletLoading;
    private final Label connectionLabel = new Label("● Chưa kết nối");
    private final Label rttLabel = new Label("— ms");
    private final TextField searchField = new TextField();
    private final ComboBox<String> searchMode = new ComboBox<>();
    private final TableView<ClientAuction> auctionTable = new TableView<>();
    private final ImageView productImage = new ImageView();
    private final Label detailName = new Label("Chọn một phòng");
    private final Label detailHost = new Label();
    private final Label detailPrice = new Label();
    private final Label detailLeader = new Label();
    private final Label detailTime = new Label();
    private final Label detailVisibility = new Label();
    private final TextArea detailDescription = new TextArea();
    private final TextArea eventLog = new TextArea();
    private final TextField bidField = new TextField();
    private final Button joinButton = new Button("Vào phòng");
    private final Button leaveButton = new Button("Rời");
    private final Button historyButton = new Button("Lịch sử");
    private final Button bidButton = new Button("Đặt giá");
    private final Button extendButton = new Button("Gia hạn");
    private final Button endButton = new Button("Kết thúc");
    private final Button cancelButton = new Button("Hủy");
    private final Button kickButton = new Button("Mời ra khỏi phòng");
    private final TitledPane managementPane = new TitledPane();
    private final Timeline clock = new Timeline();
    private final Label openCount = DesktopTheme.label("0", "stat-value");
    private final Label roomCount = DesktopTheme.label("0", "stat-value");
    private final Label joinedRoom = DesktopTheme.label("—", "stat-value");
    private final java.util.Set<String> imageRequests = new java.util.HashSet<>();
    private final Label pageTitle = DesktopTheme.label("Sàn đấu giá", "page-title");
    private final Label pageEyebrow = DesktopTheme.label("KHÁM PHÁ & ĐẤU GIÁ", "eyebrow");
    private final Label bidHint = DesktopTheme.label("", "bid-hint");
    private final FlowPane bidSuggestions = new FlowPane(8, 8);
    private final VBox roomHistory = new VBox(10);
    private final VBox priceBlock = new VBox(8);
    private final HBox roomColumns = new HBox(16);
    private final BorderPane detailPanel = new BorderPane();
    private final StackPane roomImage = new StackPane();
    private final SplitPane listingSplit = new SplitPane();
    private VBox listingPanel, bidding, roomSide, productDetails;
    private HBox statsPanel, roomHeading;
    private ScrollPane detailsScroll, roomControlsScroll;
    private TitledPane productDescription;
    private boolean roomMode, renderingAuctions;
    private String productImageKey = "";

    private Scene loginScene;
    private Scene mainScene;
    private volatile boolean autoReconnect;

    public FxClientController(
            Stage stage,
            ClientConfig config,
            ClientAppModel model,
            ClientTransport transport,
            AccountApi accounts,
            AuctionApi auctions) {
        this.stage = stage;
        this.config = config;
        this.model = model;
        this.transport = transport;
        this.accounts = accounts;
        this.auctions = auctions;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "javafx-client-background");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        buildScenes();
        transport.addStateListener(this::onConnectionState);
        transport.addEventListener(this::onServerEvent);
        onConnectionState(transport.getState(), "");
        clock.getKeyFrames().add(new KeyFrame(Duration.millis(500), event -> refreshClock()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        stage.setTitle("Auction • Sàn đấu giá");
        stage.getIcons().add(DesktopTheme.icon());
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(loginScene);
        DesktopTheme.fitOnScreen(stage, 1120, 780);
        stage.heightProperty().addListener((observable, before, now) -> updateRoomImageSize());
        stage.show();
        DesktopTheme.reveal(loginScene.getRoot());
        scheduler.scheduleAtFixedRate(this::heartbeat, 2,
                Math.max(1, config.getHeartbeatIntervalMillis() / 1000), TimeUnit.SECONDS);
        if (!Boolean.getBoolean("btl16.client.manualConnect")) {
            connect(false);
        }
    }

    private void buildScenes() {
        loginScene = new Scene(buildLoginRoot(), 1100, 740);
        mainScene = new Scene(buildMainRoot(), 1420, 880);
        DesktopTheme.apply(loginScene);
        DesktopTheme.apply(mainScene);
    }

    private Region buildLoginRoot() {
        endpointField.setText(Boolean.getBoolean("btl16.client.manualConnect") ? "" : transport.getEndpoint());
        endpointField.setPromptText("AUC-… hoặc ws://…");
        connectButton.setText("Kết nối");
        connectButton.getStyleClass().add("teal");
        usernameField.setPromptText("Tên đăng nhập");
        passwordField.setPromptText("Mật khẩu");
        loginButton.getStyleClass().add("primary");
        registerButton.getStyleClass().add("ghost");
        loginStateLabel.getStyleClass().add("pill");
        loginRttLabel.getStyleClass().add("muted");
        HBox endpoint = new HBox(8, endpointField, connectButton);
        HBox.setHgrow(endpointField, Priority.ALWAYS);
        HBox state = new HBox(14, loginStateLabel, DesktopTheme.spacer(), loginRttLabel);
        state.setAlignment(Pos.CENTER_LEFT);
        VBox connection = new VBox(12, DesktopTheme.label("MÃ KẾT NỐI", "eyebrow"), endpoint, state);
        connection.getStyleClass().add("card");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setPrefHeight(47);
        VBox form = new VBox(13, DesktopTheme.label("CHÀO MỪNG TRỞ LẠI", "eyebrow"),
                DesktopTheme.label("Vào sàn đấu giá", "page-title"), connection, new Separator(),
                DesktopTheme.label("Tên đăng nhập", "eyebrow"), usernameField,
                DesktopTheme.label("Mật khẩu", "eyebrow"), passwordField, loginButton, registerButton);
        form.setMaxWidth(470);
        form.setPadding(new Insets(24, 0, 24, 0));
        StackPane formPanel = new StackPane(form);
        formPanel.setPadding(new Insets(28, 42, 28, 42));
        HBox.setHgrow(formPanel, Priority.ALWAYS);
        Circle orbit = new Circle(105, Color.TRANSPARENT);
        orbit.setStroke(Color.web("#648c86", 0.35));
        Circle inner = new Circle(78, Color.web("#ffffff", 0.035));
        StackPane art = new StackPane(orbit, inner, DesktopTheme.gavel(88, "#bee3a3"));
        art.setPrefHeight(242);
        Label liveTag = DesktopTheme.label("●  LIVE AUCTIONS", "hero-tag");
        liveTag.setMaxWidth(180);
        VBox hero = new VBox(22, DesktopTheme.brand("LIVE"), new Region(),
                DesktopTheme.label("Mỗi lượt giá.", "hero-title"),
                DesktopTheme.label("Một cơ hội mới.", "hero-title"), art, liveTag,
                DesktopTheme.label("ĐẤU GIÁ / THỜI GIAN THỰC", "eyebrow"));
        hero.getChildren().get(3).getStyleClass().add("hero-accent");
        hero.getStyleClass().add("login-hero");
        hero.setPrefWidth(440);
        hero.setMinWidth(400);
        HBox root = new HBox(hero, formPanel);
        loginButton.setOnAction(event -> login());
        registerButton.setOnAction(event -> register());
        connectButton.setOnAction(event -> connect(true));
        endpointField.setOnAction(event -> connect(true));
        passwordField.setOnAction(event -> login());
        return root;
    }

    private Region buildMainRoot() {
        Button profileButton = new Button("◎   Tài khoản");
        Button productsButton = new Button("▣   Sản phẩm của tôi");
        Button walletButton = new Button("◈   Ví & nạp tiền");
        Button createAuctionButton = new Button("+   Tạo phòng đấu giá");
        Button refreshButton = new Button("↻  Làm mới");
        Button logoutButton = new Button("↗   Đăng xuất");
        Button marketplace = new Button("◉   Sàn đấu giá");
        marketplace.getStyleClass().add("nav-active");
        createAuctionButton.getStyleClass().add("primary");
        VBox sidebar = new VBox(18, DesktopTheme.brand("LIVE"));
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(215);
        Region gap = new Region();
        gap.setPrefHeight(24);
        Region filler = new Region();
        VBox.setVgrow(filler, Priority.ALWAYS);
        for (Button button : List.of(marketplace, productsButton, walletButton, profileButton, logoutButton)) {
            button.setMaxWidth(Double.MAX_VALUE);
        }
        userLabel.getStyleClass().add("section-title");
        sidebar.getChildren().addAll(gap, DesktopTheme.label("KHÔNG GIAN CỦA BẠN", "eyebrow"),
                marketplace, productsButton, walletButton, profileButton, filler, new Separator(),
                DesktopTheme.label("ĐANG ĐĂNG NHẬP", "eyebrow"), userLabel, walletBalance, logoutButton);
        marketplace.setOnAction(event -> loadAuctionList());
        connectionLabel.getStyleClass().add("pill");
        rttLabel.getStyleClass().add("muted");
        HBox header = new HBox(14, new VBox(5, pageEyebrow, pageTitle), DesktopTheme.spacer(),
                connectionLabel, rttLabel, createAuctionButton);
        header.setAlignment(Pos.CENTER_LEFT);
        statsPanel = new HBox(14, statCard("PHIÊN ĐANG MỞ", openCount, true),
                statCard("PHÒNG ĐẤU GIÁ", roomCount, false), statCard("PHÒNG CỦA BẠN", joinedRoom, false));
        searchMode.getItems().setAll("ALL", "PRODUCT_NAME", "ROOM_ID");
        searchMode.setValue("ALL");
        searchMode.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                if (value == null) return "";
                return switch (value) {
                    case "PRODUCT_NAME" -> "Tên sản phẩm";
                    case "ROOM_ID" -> "Mã phòng";
                    default -> "Tất cả";
                };
            }
            @Override
            public String fromString(String value) { return value; }
        });
        searchField.setPromptText("Tìm sản phẩm hoặc mã phòng…");
        Button searchButton = new Button("Tìm kiếm");
        Button clearButton = new Button("Xóa lọc");
        clearButton.getStyleClass().add("ghost");
        HBox search = new HBox(8, searchField, searchButton);
        HBox filters = new HBox(8, searchMode, clearButton);
        search.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        configureAuctionTable();
        listingPanel = new VBox(16,
                new HBox(10, DesktopTheme.label("Phòng đấu giá", "section-title"), DesktopTheme.spacer(), refreshButton),
                search, filters, auctionTable);
        listingPanel.getStyleClass().add("card");
        listingPanel.setMinWidth(400);
        VBox.setVgrow(auctionTable, Priority.ALWAYS);
        productDetails = buildDetails();
        detailsScroll = new ScrollPane(productDetails);
        detailsScroll.setFitToWidth(true);
        detailsScroll.setMinSize(300, 0);
        detailsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox membership = new HBox(7, joinButton, leaveButton, historyButton);
        HBox bid = new HBox(8, bidField, bidButton);
        HBox.setHgrow(bidField, Priority.ALWAYS);
        membership.setAlignment(Pos.CENTER_LEFT);
        bidding = new VBox(10, membership, bid, bidSuggestions, bidHint);
        bidding.getStyleClass().add("card"); bidding.setPadding(new Insets(16));
        bidSuggestions.setVisible(false); bidSuggestions.setManaged(false);
        bidHint.setVisible(false); bidHint.setManaged(false);
        detailPanel.setCenter(detailsScroll); detailPanel.setBottom(bidding);
        BorderPane.setMargin(bidding, new Insets(10, 0, 0, 0));
        detailPanel.setMinWidth(300);
        roomHistory.getStyleClass().addAll("card", "room-history");
        roomSide = new VBox(14, roomHistory);
        roomControlsScroll = new ScrollPane(roomSide); roomControlsScroll.setFitToWidth(true);
        roomControlsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        roomControlsScroll.setMinWidth(340); roomControlsScroll.setPrefWidth(390); roomControlsScroll.setMaxWidth(430);
        HBox.setHgrow(detailsScroll, Priority.ALWAYS); HBox.setHgrow(roomControlsScroll, Priority.ALWAYS);
        listingSplit.getItems().setAll(listingPanel, detailPanel);
        listingSplit.setDividerPositions(0.60);
        VBox.setVgrow(listingSplit, Priority.ALWAYS);
        roomHeading = new HBox(10, DesktopTheme.label("ĐẤU GIÁ TRỰC TIẾP", "eyebrow"), DesktopTheme.spacer(),
                DesktopTheme.label("Giá và thời gian được đồng bộ từ server", "muted"));
        roomHeading.getStyleClass().add("room-heading"); roomHeading.setAlignment(Pos.CENTER_LEFT);
        roomHeading.setVisible(false); roomHeading.setManaged(false);
        eventLog.setEditable(false);
        eventLog.setWrapText(true);
        eventLog.setPrefRowCount(4);
        TitledPane notifications = new TitledPane("Hoạt động gần đây", eventLog);
        notifications.setExpanded(false);
        VBox workspace = new VBox(16, header, statsPanel, roomHeading, listingSplit, notifications);
        workspace.setPadding(new Insets(20));
        BorderPane root = new BorderPane(workspace);
        root.setLeft(sidebar);
        profileButton.setOnAction(event -> showProfile());
        productsButton.setOnAction(event -> showProducts());
        walletButton.setOnAction(event -> {
            long userId = model.getUserId(); String token = model.getSessionToken();
            new WalletPane(stage, new WalletApi(transport), userId, model.getUsername(),
                    () -> model.getUserId() == userId && model.getSessionToken().equals(token)).show();
        });
        createAuctionButton.setOnAction(event -> showCreateAuction());
        refreshButton.setOnAction(event -> loadAuctionList());
        logoutButton.setOnAction(event -> logout());
        searchButton.setOnAction(event -> search());
        clearButton.setOnAction(event -> {
            searchField.clear();
            loadAuctionList();
        });
        searchField.setOnAction(event -> search());
        joinButton.setOnAction(event -> joinSelected());
        leaveButton.setOnAction(event -> leaveJoined());
        historyButton.setOnAction(event -> showHistory());
        bidButton.setOnAction(event -> placeBid());
        bidField.setOnAction(event -> placeBid());
        extendButton.setOnAction(event -> extendAuction());
        endButton.setOnAction(event -> endAuction());
        cancelButton.setOnAction(event -> cancelAuction());
        kickButton.setOnAction(event -> kickUser());
        auctionTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, value) -> { if (!renderingAuctions) { renderDetails(value); } });
        return root;
    }

    private VBox statCard(String title, Label value, boolean accent) {
        VBox box = new VBox(6, DesktopTheme.label(title, "eyebrow"), value);
        box.getStyleClass().add("stat-card");
        if (accent) box.getStyleClass().add("accent");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void configureAuctionTable() {
        addColumn("MÃ", value -> "#" + value.getAuctionId(), 55);
        addColumn("SẢN PHẨM", ClientAuction::getProductName, 150);
        addColumn("GIÁ HIỆN TẠI", value -> money(value.getCurrentPrice()), 120);
        addColumn("CÒN LẠI", this::remainingText, 80);
        auctionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox empty = new VBox(12, DesktopTheme.label("Chưa có phòng đấu giá", "section-title"));
        empty.setAlignment(Pos.CENTER);
        auctionTable.setPlaceholder(empty);
    }

    private void addColumn(String title, java.util.function.Function<ClientAuction, String> mapper, double width) {
        TableColumn<ClientAuction, String> column = new TableColumn<>(title);
        column.setCellValueFactory(value -> new SimpleStringProperty(mapper.apply(value.getValue())));
        column.setPrefWidth(width);
        if (title.equals("GIÁ HIỆN TẠI")) {
            column.setCellFactory(ignored -> new TableCell<>() {
                @Override
                protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(empty ? null : value);
                    if (!getStyleClass().contains("price-cell")) getStyleClass().add("price-cell");
                }
            });
        }
        auctionTable.getColumns().add(column);
    }

    private VBox buildDetails() {
        productImage.fitWidthProperty().bind(roomImage.widthProperty().subtract(24));
        productImage.fitHeightProperty().bind(roomImage.heightProperty().subtract(24));
        productImage.setPreserveRatio(true);
        productImage.setStyle("-fx-cursor: hand;");
        productImage.setOnMouseClicked(event -> {
            ClientAuction selected = auctionTable.getSelectionModel().getSelectedItem();
            if (selected != null) { showMediaGallery(selected.getProductId(), selected.getAuctionId(), selected.getProductName()); }
        });
        var placeholder = DesktopTheme.gavel(34, "#8baa97");
        productImage.imageProperty().addListener((observable, before, image) -> placeholder.setVisible(image == null));
        roomImage.getChildren().addAll(placeholder, productImage);
        roomImage.getStyleClass().add("room-image");
        roomImage.setMinSize(0, 150); roomImage.setPrefHeight(160); roomImage.setMaxHeight(160);
        detailName.getStyleClass().add("detail-title");
        detailName.setWrapText(true);
        detailHost.getStyleClass().add("muted");
        detailPrice.getStyleClass().add("price");
        detailPrice.setWrapText(true);
        detailTime.getStyleClass().add("countdown");
        detailVisibility.getStyleClass().add("pill");
        detailLeader.setWrapText(true);
        detailHost.setWrapText(true);
        detailDescription.setEditable(false);
        detailDescription.setWrapText(true);
        detailDescription.setPrefRowCount(3);
        joinButton.getStyleClass().add("teal");
        bidButton.getStyleClass().add("primary");
        cancelButton.getStyleClass().add("danger");
        bidField.setPromptText("Nhập giá của bạn");
        Button gallery = new Button("Ảnh / video");
        gallery.getStyleClass().add("ghost");
        gallery.setOnAction(event -> {
            ClientAuction selected = auctionTable.getSelectionModel().getSelectedItem();
            if (selected != null) { showMediaGallery(selected.getProductId(), selected.getAuctionId(), selected.getProductName()); }
        });
        HBox productHeader = new HBox(8, detailVisibility, DesktopTheme.spacer(), gallery);
        productHeader.setAlignment(Pos.CENTER_LEFT);
        FlowPane hostActions = new FlowPane(8, 8, extendButton, endButton, cancelButton, kickButton);
        managementPane.setText("Quản lý phòng");
        managementPane.setContent(hostActions);
        managementPane.setExpanded(false);
        productDescription = new TitledPane("Thông tin sản phẩm", detailDescription);
        productDescription.setExpanded(false);
        priceBlock.getChildren().addAll(DesktopTheme.label("GIÁ HIỆN TẠI", "eyebrow"), detailPrice,
                detailTime, detailLeader);
        VBox details = new VBox(12, productHeader, detailName, detailHost, roomImage, priceBlock,
                productDescription, managementPane);
        details.getStyleClass().add("card");
        details.setPadding(new Insets(19));
        return details;
    }

    private String money(BigDecimal value) {
        return java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("vi-VN"))
                .format(value) + " ₫";
    }

    private void connect(boolean manual) {
        if (closed.get()) {
            return;
        }
        if (endpointField.getText().isBlank()) {
            showError("Hãy nhập địa chỉ được sao chép từ ứng dụng Server trước khi kết nối.");
            return;
        }
        if (manual) {
            connectionGeneration++;
            autoReconnect = false;
            reconnecting.set(false);
            if (reconnectTask != null) {
                reconnectTask.cancel(false);
            }
            transport.disconnect();
        }
        try {
            transport.setEndpoint(ConnectionCode.decode(endpointField.getText()));
        } catch (RuntimeException exception) {
            setLoginBusy(false);
            showError(exception.getMessage());
            return;
        }
        setLoginBusy(true);
        long generation = connectionGeneration;
        transport.connect().whenComplete((value, error) -> Platform.runLater(() -> {
            if (closed.get() || generation != connectionGeneration) {
                return;
            }
            setLoginBusy(false);
            if (error != null) {
                appendEvent("Kết nối không thành công: " + rootMessage(error));
                showError("Không thể kết nối tới " + transport.getEndpoint()
                        + "\nChi tiết: " + rootMessage(error)
                        + "\nKiểm tra địa chỉ IP/cổng trên Server, mạng LAN và Windows Firewall."
                        + "\nSửa địa chỉ rồi bấm Kết nối để thử lại.");
                return;
            }
            autoReconnect = true;
            reconnecting.set(false);
            if (model.hasSessionToken()) {
                resumeSession();
            }
        }));
    }

    private void login() {
        if (!transport.isConnected()) {
            connect(true);
            return;
        }
        setLoginBusy(true);
        accounts.login(usernameField.getText(), passwordField.getText())
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    setLoginBusy(false);
                    if (!success(response, error)) {
                        return;
                    }
                    model.applyIdentity(response);
                    autoReconnect = true;
                    passwordField.clear();
                    showMain();
                    loadAuctionList();
                }));
    }

    private void register() {
        if (!transport.isConnected()) {
            showError("Kết nối vào sàn trước khi đăng ký tài khoản");
            return;
        }
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Đăng ký tài khoản");
        TextField username = new TextField(usernameField.getText());
        PasswordField password = new PasswordField();
        PasswordField confirm = new PasswordField();
        TextField displayName = new TextField();
        TextField email = new TextField();
        TextField phone = new TextField();
        GridPane grid = formGrid();
        addRow(grid, 0, "Tên đăng nhập", username);
        addRow(grid, 1, "Mật khẩu", password);
        addRow(grid, 2, "Xác nhận", confirm);
        addRow(grid, 3, "Tên hiển thị", displayName);
        addRow(grid, 4, "Email", email);
        addRow(grid, 5, "Điện thoại", phone);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Đăng ký", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        dialog.setResultConverter(button -> {
            if (button.getButtonData() != ButtonBar.ButtonData.OK_DONE) {
                return null;
            }
            if (!password.getText().equals(confirm.getText())) {
                showError("Mật khẩu xác nhận không khớp");
                return null;
            }
            return Map.of(
                    "username", username.getText(),
                    "password", password.getText(),
                    "displayName", displayName.getText(),
                    "email", email.getText(),
                    "phone", phone.getText());
        });
        showDialog(dialog).ifPresent(data -> {
            if (!transport.isConnected()) {
                showError("Hãy kết nối server trước khi đăng ký");
                return;
            }
            accounts.register(data.get("username"), data.get("password"),
                            data.get("displayName"), data.get("email"), data.get("phone"))
                    .whenComplete((response, error) -> Platform.runLater(() -> {
                        if (success(response, error)) {
                            usernameField.setText(data.get("username"));
                            showInfo("Đăng ký thành công. Bạn có thể đăng nhập.");
                        }
                    }));
        });
    }

    private void resumeSession() {
        accounts.resumeSession(model.getSessionToken())
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (!success(response, error)) {
                        autoReconnect = false;
                        model.clearIdentity();
                        stage.setScene(loginScene);
                        return;
                    }
                    model.applyIdentity(response);
                    showMain();
                    Long joined = model.getJoinedAuctionId();
                    auctions.resync(joined).whenComplete((resync, resyncError) ->
                            Platform.runLater(() -> {
                                if (success(resync, resyncError)) {
                                    applyResync(resync, joined != null);
                                }
                                loadAuctionList();
                            }));
                }));
    }

    private void showMain() {
        userLabel.setText(model.getDisplayName().isBlank() ? model.getUsername() : model.getDisplayName());
        stage.setScene(mainScene);
        DesktopTheme.fitOnScreen(stage, 1420, 880);
        DesktopTheme.reveal(mainScene.getRoot());
        renderAuctions();
        refreshWallet();
    }

    private void refreshWallet() {
        if (walletLoading || !model.hasSessionToken() || !transport.isConnected()) { return; }
        walletLoading = true; String token = model.getSessionToken();
        new WalletApi(transport).get().whenComplete((response, error) -> Platform.runLater(() -> {
            walletLoading = false;
            if (!model.getSessionToken().equals(token)) { return; }
            if (error == null && response != null && response.isSuccess()) {
                walletBalance.setText("Khả dụng: " + money(new BigDecimal(response.get("available"))));
            }
        }));
    }

    private void loadAuctionList() {
        if (!transport.isConnected() || !model.hasSessionToken()) {
            return;
        }
        auctions.listAuctions().whenComplete((response, error) -> Platform.runLater(() -> {
            if (!success(response, error)) {
                return;
            }
            applyAuctionList(response);
            renderAuctions();
        }));
    }

    private void search() {
        String query = searchField.getText().trim();
        if (query.isBlank()) {
            loadAuctionList();
            return;
        }
        auctions.searchAuctions(searchMode.getValue(), query)
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (success(response, error)) {
                        applyAuctionList(response);
                        renderAuctions();
                    }
                }));
    }

    private void joinSelected() {
        ClientAuction selected = selectedAuction();
        if (selected == null) {
            return;
        }
        String password = "";
        if (selected.requiresPassword() && !selected.isHostedBy(model.getUserId())) {
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Phòng riêng tư");
            PasswordField field = new PasswordField();
            field.setPromptText("Mật khẩu phòng");
            dialog.getDialogPane().setContent(new VBox(8,
                    new Label("Phòng này yêu cầu mật khẩu"), field));
            dialog.getDialogPane().getButtonTypes().addAll(
                    new ButtonType("Vào phòng", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
            dialog.setResultConverter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE
                    ? field.getText() : null);
            Optional<String> result = showDialog(dialog);
            if (result.isEmpty()) {
                return;
            }
            password = result.get();
        }
        auctions.join(selected.getAuctionId(), password)
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (success(response, error)) {
                        applySnapshot(response, true);
                        renderAuctions();
                        auctionTable.getSelectionModel().select(model.joinedAuction());
                        appendEvent("Đã vào phòng #" + selected.getAuctionId());
                    }
                }));
    }

    private void leaveJoined() {
        Long auctionId = model.getJoinedAuctionId();
        if (auctionId == null) {
            showError("Bạn chưa vào phòng nào");
            return;
        }
        auctions.leave(auctionId).whenComplete((response, error) -> Platform.runLater(() -> {
            if (success(response, error)) {
                model.leaveJoinedAuction();
                appendEvent("Đã rời phòng #" + auctionId);
                renderAuctions();
            }
        }));
    }

    private void placeBid() {
        Long auctionId = model.getJoinedAuctionId();
        if (auctionId == null) {
            showError("Hãy vào phòng trước khi đặt giá");
            return;
        }
        try {
            BigDecimal amount = WireValues.requireDecimal(Map.of("amount", bidField.getText()), "amount");
            auctions.bid(auctionId, amount).whenComplete((response, error) ->
                    Platform.runLater(() -> {
                        if (success(response, error)) {
                            applyBidUpdate(response.getWireMessage().getData());
                            bidField.clear();
                            renderAuctions();
                        }
                    }));
        } catch (IllegalArgumentException exception) {
            showError("Nhập giá bằng chữ số, không có dấu phân cách hàng nghìn");
        }
    }

    private void showHistory() {
        Long auctionId = model.getJoinedAuctionId();
        if (auctionId == null) {
            ClientAuction selected = selectedAuction();
            auctionId = selected == null ? null : selected.getAuctionId();
        }
        if (auctionId == null) {
            showError("Chọn một phòng để xem lịch sử");
            return;
        }
        long id = auctionId;
        auctions.history(id).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!success(response, error)) {
                return;
            }
            List<ClientBid> bids = ClientWireParser.bids(response.getWireMessage().getData());
            StringBuilder text = new StringBuilder();
            for (ClientBid bid : bids) {
                text.append(DATE_TIME.format(bid.getCreatedAt())).append(" | ")
                        .append(bid.getUsername()).append(" | ")
                        .append(bid.getAmount()).append('\n');
            }
            TextArea area = new TextArea(text.isEmpty() ? "Chưa có lượt bid" : text.toString());
            area.setEditable(false);
            area.setPrefSize(520, 360);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Lịch sử bid phòng #" + id);
            alert.getDialogPane().setContent(area);
            showDialog(alert);
        }));
    }

    private void extendAuction() {
        ClientAuction selected = requireHostedAuction();
        if (selected == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog("60");
        dialog.setTitle("Gia hạn");
        dialog.setHeaderText("Số giây gia hạn (10-600)");
        showDialog(dialog).ifPresent(value -> {
            try {
                auctions.extendAuction(selected.getAuctionId(), Integer.parseInt(value.trim()))
                        .whenComplete((response, error) -> Platform.runLater(() -> {
                            if (success(response, error)) {
                                applySnapshot(response, false);
                                renderAuctions();
                            }
                        }));
            } catch (NumberFormatException exception) {
                showError("Số giây không hợp lệ");
            }
        });
    }

    private void endAuction() {
        ClientAuction selected = requireHostedAuction();
        if (selected != null && confirm("Kết thúc phòng #" + selected.getAuctionId() + "?")) {
            auctions.endAuction(selected.getAuctionId()).whenComplete((response, error) ->
                    Platform.runLater(() -> {
                        if (success(response, error)) {
                            applySnapshot(response, false);
                            renderAuctions();
                        }
                    }));
        }
    }

    private void cancelAuction() {
        ClientAuction selected = requireHostedAuction();
        if (selected != null && confirm("Hủy phòng #" + selected.getAuctionId() + "?")) {
            auctions.cancelAuction(selected.getAuctionId()).whenComplete((response, error) ->
                    Platform.runLater(() -> {
                        if (success(response, error)) {
                            applySnapshot(response, false);
                            renderAuctions();
                        }
                    }));
        }
    }

    private void kickUser() {
        ClientAuction selected = requireHostedAuction();
        if (selected == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Kick user");
        dialog.setHeaderText("Username cần mời khỏi phòng");
        showDialog(dialog).ifPresent(username -> auctions
                .kickUser(selected.getAuctionId(), username)
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (success(response, error)) {
                        appendEvent(response.getMessageText().isBlank()
                                ? "Đã kick " + username : response.getMessageText());
                    }
                })));
    }

    private void showProfile() {
        accounts.getProfile().whenComplete((response, error) -> Platform.runLater(() -> {
            if (!success(response, error)) {
                return;
            }
            model.applyIdentity(response);
            Dialog<Map<String, String>> dialog = new Dialog<>();
            dialog.setTitle("Hồ sơ tài khoản");
            TextField displayName = new TextField(model.getDisplayName());
            TextField email = new TextField(model.getEmail());
            TextField phone = new TextField(model.getPhone());
            GridPane grid = formGrid();
            addRow(grid, 0, "Tên đăng nhập", new Label(model.getUsername()));
            addRow(grid, 1, "Tên hiển thị", displayName);
            addRow(grid, 2, "Email", email);
            addRow(grid, 3, "Điện thoại", phone);
            addRow(grid, 4, "Ngày tạo", new Label(model.getCreatedAt()));
            dialog.getDialogPane().setContent(grid);
            ButtonType save = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
            ButtonType changePassword = new ButtonType("Đổi mật khẩu", ButtonBar.ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().addAll(save, changePassword, ButtonType.CANCEL);
            dialog.setResultConverter(button -> {
                if (button == changePassword) {
                    showChangePassword();
                    return null;
                }
                return button == save ? Map.of(
                        "displayName", displayName.getText(),
                        "email", email.getText(),
                        "phone", phone.getText()) : null;
            });
            showDialog(dialog).ifPresent(data -> accounts.updateProfile(
                            data.get("displayName"), data.get("email"), data.get("phone"))
                    .whenComplete((updated, updateError) -> Platform.runLater(() -> {
                        if (success(updated, updateError)) {
                            model.applyIdentity(updated);
                            userLabel.setText(model.getDisplayName().isBlank() ? model.getUsername() : model.getDisplayName());
                        }
                    })));
        }));
    }

    private void showChangePassword() {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Đổi mật khẩu");
        PasswordField current = new PasswordField();
        PasswordField next = new PasswordField();
        PasswordField confirm = new PasswordField();
        GridPane grid = formGrid();
        addRow(grid, 0, "Mật khẩu hiện tại", current);
        addRow(grid, 1, "Mật khẩu mới", next);
        addRow(grid, 2, "Xác nhận", confirm);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Đổi", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        dialog.setResultConverter(button -> {
            if (button.getButtonData() != ButtonBar.ButtonData.OK_DONE) {
                return null;
            }
            if (!next.getText().equals(confirm.getText())) {
                showError("Mật khẩu xác nhận không khớp");
                return null;
            }
            return Map.of("current", current.getText(), "next", next.getText());
        });
        showDialog(dialog).ifPresent(data -> accounts.changePassword(
                        data.get("current"), data.get("next"))
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (success(response, error)) {
                        showInfo("Đổi mật khẩu thành công");
                    }
                })));
    }

    private void showProducts() {
        Stage window = new Stage();
        window.initOwner(stage);
        window.initModality(Modality.WINDOW_MODAL);
        window.setTitle("Sản phẩm của tôi");
        TableView<ClientProduct> table = new TableView<>();
        table.setId("seller-products");
        productColumn(table, "Mã", ClientProduct::getCode, 110);
        productColumn(table, "Tên", ClientProduct::getName, 220);
        productColumn(table, "Ảnh", value -> value.hasImage() ? "Có" : "Chưa có", 80);
        productColumn(table, "Trạng thái", value -> value.isActive() ? "Đang sử dụng" : "Đã ngừng", 130);
        productColumn(table, "Khôi phục", this::restorationLabel, 180);
        productColumn(table, "Cập nhật", value -> value.getUpdatedAt() == null
                ? "" : DATE_TIME.format(value.getUpdatedAt()), 160);
        Button add = new Button("Thêm sản phẩm");
        Button edit = new Button("Sửa");
        Button media = new Button("Ảnh / video");
        media.setOnAction(event -> {
            ClientProduct selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) { showMediaGallery(selected.getProductId(), 0, selected.getName()); }
        });
        Button deactivate = new Button("Ngừng sử dụng");
        Button restore = new Button("Khôi phục mặt hàng");
        restore.setId("restore-product");
        Button refresh = new Button("Làm mới");
        Button close = new Button("Đóng");
        Label restorationHint = new Label("Chọn mặt hàng để xem điều kiện khôi phục.");
        restorationHint.setId("product-restoration-status");
        restorationHint.setWrapText(true);
        restorationHint.setMinHeight(48);
        restorationHint.getStyleClass().add("muted");
        HBox actions = new HBox(8, add, edit, media, deactivate, restore, refresh, close);
        VBox root = new VBox(10, table, restorationHint, actions);
        root.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        Scene productsScene = new Scene(root, 1030, 560);
        DesktopTheme.apply(productsScene);
        add.getStyleClass().add("primary");
        restore.getStyleClass().add("primary");
        deactivate.getStyleClass().add("danger");
        window.getIcons().add(DesktopTheme.icon());
        window.setScene(productsScene);
        SimpleBooleanProperty busy = new SimpleBooleanProperty(true);
        Runnable updateActions = () -> {
            ClientProduct selected = table.getSelectionModel().getSelectedItem();
            add.setDisable(busy.get());
            edit.setDisable(busy.get() || selected == null);
            media.setDisable(busy.get() || selected == null);
            deactivate.setDisable(busy.get() || selected == null || !selected.isActive());
            restore.setDisable(busy.get() || selected == null || !selected.canRestore());
            refresh.setDisable(busy.get());
            restorationHint.setText(selected == null
                    ? "Chọn mặt hàng để xem điều kiện khôi phục." : restorationDescription(selected));
        };
        busy.addListener((observable, previous, current) -> updateActions.run());
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> updateActions.run());
        Runnable reload = () -> {
            ClientProduct selected = table.getSelectionModel().getSelectedItem();
            busy.set(true);
            auctions.myProducts().whenComplete((response, error) -> Platform.runLater(() -> {
                    busy.set(false);
                    if (!window.isShowing()) {
                        return;
                    }
                    if (success(response, error)) {
                        table.getItems().setAll(
                                ClientWireParser.products(response.getWireMessage().getData()));
                        table.getItems().stream()
                                .filter(value -> selected != null && value.getProductId() == selected.getProductId())
                                .findFirst().ifPresent(value -> table.getSelectionModel().select(value));
                    }
                    updateActions.run();
                }));
        };
        add.setOnAction(event -> showProductForm(null, reload));
        table.setRowFactory(view -> {
            TableRow<ClientProduct> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    ClientProduct selected = row.getItem();
                    showMediaGallery(selected.getProductId(), 0, selected.getName());
                }
            });
            return row;
        });
        edit.setOnAction(event -> {
            ClientProduct selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError("Chọn sản phẩm cần sửa");
            } else {
                showProductForm(selected, reload);
            }
        });
        deactivate.setOnAction(event -> {
            ClientProduct selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && confirm("Ngừng sử dụng " + selected.getName() + "?")) {
                busy.set(true);
                auctions.deactivateProduct(selected.getProductId()).whenComplete((response, error) ->
                        Platform.runLater(() -> {
                            success(response, error);
                            reload.run();
                        }));
            }
        });
        restore.setOnAction(event -> {
            ClientProduct selected = table.getSelectionModel().getSelectedItem();
            if (selected == null || !selected.canRestore()
                    || !confirm("Khôi phục " + selected.getName()
                            + " để có thể tạo phiên mới? Các phiên cũ và lịch sử vẫn được giữ nguyên.")) {
                return;
            }
            busy.set(true);
            auctions.restoreProduct(selected.getProductId()).whenComplete((response, error) ->
                    Platform.runLater(() -> {
                        if (error != null || response == null) {
                            success(response, error);
                        } else if (!response.isSuccess()) {
                            showError(switch (response.getErrorCode()) {
                                case "PRODUCT_IN_USE" -> "Không thể khôi phục: mặt hàng vẫn còn phiên đang mở.";
                                case "PRODUCT_ALREADY_SOLD" -> "Mặt hàng đã có người thắng nên không được khôi phục để đấu giá lại, kể cả chưa thanh toán.";
                                case "AUCTION_FORBIDDEN" -> "Chỉ chủ sở hữu mới được khôi phục mặt hàng này.";
                                case "AUTH_REQUIRED" -> "Bạn cần đăng nhập lại trước khi khôi phục mặt hàng.";
                                default -> response.getMessageText();
                            });
                        } else {
                            showInfo("Mặt hàng đang hoạt động. Bạn có thể tạo phiên mới nếu đủ điều kiện; các phiên cũ không được mở lại.");
                        }
                        reload.run();
                    }));
        });
        refresh.setOnAction(event -> reload.run());
        close.setOnAction(event -> window.close());
        updateActions.run();
        window.show();
        reload.run();
    }

    private String restorationLabel(ClientProduct product) {
        return switch (product.getRestorationStatus()) {
            case ACTIVE -> "Không cần khôi phục";
            case ELIGIBLE -> "Có thể khôi phục";
            case OPEN_AUCTION -> "Còn phiên đang mở";
            case SOLD -> "Đã có người thắng";
            case UNKNOWN -> "Cần kiểm tra điều kiện";
        };
    }

    private String restorationDescription(ClientProduct product) {
        return switch (product.getRestorationStatus()) {
            case ACTIVE -> "Mặt hàng đang hoạt động, không cần khôi phục. Việc mở phiên mới vẫn phải đáp ứng đầy đủ điều kiện đấu giá.";
            case ELIGIBLE -> "Có thể khôi phục để tạo phiên mới. Khôi phục không mở lại phiên cũ, không xóa lịch sử và không cho sửa thông tin hoặc ảnh đã đóng băng.";
            case OPEN_AUCTION -> "Không thể khôi phục vì mặt hàng vẫn còn phiên đang mở. Hãy kiểm tra lại sau khi phiên kết thúc hoặc được hủy hợp lệ.";
            case SOLD -> "Không thể khôi phục để đấu giá lại: mặt hàng đã có phiên kết thúc với người thắng, kể cả chưa thanh toán hoặc giao nhận.";
            case UNKNOWN -> "Máy chủ sẽ kiểm tra điều kiện khi khôi phục. Bạn phải là chủ sở hữu; mặt hàng không được có phiên mở hoặc đã có người thắng.";
        };
    }

    private void showProductForm(ClientProduct product, Runnable onSaved) {
        ProductMediaApi.connect(auctions, transport, model).whenComplete((api, error) -> Platform.runLater(() -> {
            if (error != null) { showError(ProductMediaApi.message(error)); return; }
            new ProductMediaEditor(stage, api, product, onSaved).show();
        }));
    }

    private void showMediaGallery(long productId, long auctionId, String title) {
        ProductMediaApi.connect(auctions, transport, model).whenComplete((api, error) -> Platform.runLater(() -> {
            if (error != null) { showError(ProductMediaApi.message(error)); return; }
            MediaGalleryPane.show(stage, api, productId, auctionId, title);
        }));
    }

    private void showCreateAuction() {
        auctions.myProducts().whenComplete((response, error) -> Platform.runLater(() -> {
            if (!success(response, error)) {
                return;
            }
            List<ClientProduct> products = ClientWireParser.products(response.getWireMessage().getData())
                    .stream().filter(ClientProduct::isActive).toList();
            if (products.isEmpty()) {
                showError("Thêm sản phẩm trước khi tạo phòng đấu giá");
                return;
            }
            Dialog<AuctionFormData> dialog = new Dialog<>();
            dialog.setTitle("Tạo phòng đấu giá");
            ComboBox<ClientProduct> product = new ComboBox<>(FXCollections.observableArrayList(products));
            product.getSelectionModel().selectFirst();
            TextField startPrice = new TextField("100000");
            TextField increment = new TextField("10000");
            TextField duration = new TextField("5");
            RadioButton publicRoom = new RadioButton("Công khai");
            RadioButton privateRoom = new RadioButton("Riêng tư");
            ToggleGroup group = new ToggleGroup();
            publicRoom.setToggleGroup(group);
            privateRoom.setToggleGroup(group);
            publicRoom.setSelected(true);
            PasswordField roomPassword = new PasswordField();
            PasswordField confirmPassword = new PasswordField();
            roomPassword.setDisable(true);
            confirmPassword.setDisable(true);
            privateRoom.selectedProperty().addListener((observable, oldValue, selected) -> {
                roomPassword.setDisable(!selected);
                confirmPassword.setDisable(!selected);
            });
            GridPane grid = formGrid();
            addRow(grid, 0, "Sản phẩm", product);
            addRow(grid, 1, "Giá khởi điểm", startPrice);
            addRow(grid, 2, "Bước giá", increment);
            addRow(grid, 3, "Thời lượng (phút)", duration);
            addRow(grid, 4, "Loại phòng", new HBox(10, publicRoom, privateRoom));
            addRow(grid, 5, "Mật khẩu", roomPassword);
            addRow(grid, 6, "Xác nhận", confirmPassword);
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(
                    new ButtonType("Tạo phòng", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
            dialog.setResultConverter(button -> {
                if (button.getButtonData() != ButtonBar.ButtonData.OK_DONE) {
                    return null;
                }
                if (privateRoom.isSelected()
                        && !roomPassword.getText().equals(confirmPassword.getText())) {
                    showError("Mật khẩu phòng xác nhận không khớp");
                    return null;
                }
                try {
                    return new AuctionFormData(
                            product.getValue().getProductId(),
                            WireValues.requireDecimal(Map.of("startPrice", startPrice.getText()), "startPrice"),
                            WireValues.requireDecimal(Map.of("minBidIncrement", increment.getText()), "minBidIncrement"),
                            Integer.parseInt(duration.getText().trim()),
                            privateRoom.isSelected() ? "PRIVATE" : "PUBLIC",
                            privateRoom.isSelected() ? roomPassword.getText() : "");
                } catch (IllegalArgumentException exception) {
                    showError("Giá hoặc thời lượng không hợp lệ");
                    return null;
                }
            });
            showDialog(dialog).ifPresent(data -> auctions.createAuction(
                            data.productId, data.startPrice, data.increment, data.durationMinutes,
                            data.visibility, data.roomPassword)
                    .whenComplete((created, createError) -> Platform.runLater(() -> {
                        if (success(created, createError)) {
                            applySnapshot(created, false);
                            renderAuctions();
                            appendEvent("Đã tạo phòng #" + created.get("auctionId"));
                        }
                    })));
        }));
    }

    private void logout() {
        autoReconnect = false;
        accounts.logout().whenComplete((response, error) -> Platform.runLater(() -> {
            if (error == null && response != null && response.isSuccess()) {
                model.clearIdentity();
                auctionTable.getItems().clear();
                stage.setScene(loginScene);
                appendEvent("Đã đăng xuất");
            } else {
                success(response, error);
            }
        }));
    }

    private void onConnectionState(ConnectionState state, String detail) {
        model.setConnectionState(state, detail);
        Platform.runLater(() -> {
            if (closed.get() || transport.getState() != state) {
                return;
            }
            String text = switch (state) {
                case CONNECTED -> "● Đã kết nối";
                case CONNECTING -> "● Đang kết nối";
                case DISCONNECTED -> reconnecting.get() ? "● Đang kết nối lại" : "● Chưa kết nối";
                case CLOSED -> "● Đã đóng";
            };
            loginStateLabel.setText(text);
            connectionLabel.setText(text);
            for (Label badge : List.of(loginStateLabel, connectionLabel)) {
                badge.getStyleClass().remove("offline");
                if (state != ConnectionState.CONNECTED) badge.getStyleClass().add("offline");
            }
            updateHostButtons(auctionTable.getSelectionModel().getSelectedItem());
            if (state == ConnectionState.DISCONNECTED && autoReconnect) {
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (closed.get() || !autoReconnect || transport.isConnected()
                || !reconnecting.compareAndSet(false, true)) {
            return;
        }
        scheduleReconnectAttempt(1, config.getReconnectInitialDelayMillis(), connectionGeneration);
    }

    private void scheduleReconnectAttempt(int attempt, long delayMillis, long generation) {
        if (closed.get() || !autoReconnect || generation != connectionGeneration
                || transport.isConnected()) {
            reconnecting.set(false);
            return;
        }
        Platform.runLater(() -> {
            loginStateLabel.setText("● Reconnecting " + attempt + '/' + config.getReconnectMaxAttempts());
            connectionLabel.setText(loginStateLabel.getText());
        });
        reconnectTask = scheduler.schedule(() -> Platform.runLater(() -> {
            if (closed.get() || !autoReconnect || generation != connectionGeneration
                    || transport.isConnected()) {
                return;
            }
            transport.connect().whenComplete((value, error) -> Platform.runLater(() -> {
                if (closed.get() || generation != connectionGeneration) {
                    return;
                }
                if (error == null) {
                    reconnecting.set(false);
                    if (model.hasSessionToken()) {
                        resumeSession();
                    }
                    return;
                }
                if (attempt >= config.getReconnectMaxAttempts()) {
                    autoReconnect = false;
                    reconnecting.set(false);
                    loginStateLabel.setText("● Disconnected");
                    connectionLabel.setText(loginStateLabel.getText());
                    showError("Không thể kết nối lại: " + rootMessage(error));
                    return;
                }
                long nextDelay = Math.min(config.getReconnectMaxDelayMillis(), delayMillis * 2L);
                scheduleReconnectAttempt(attempt + 1, nextDelay, generation);
            }));
        }), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void heartbeat() {
        if (!transport.isConnected()) {
            return;
        }
        accounts.ping().whenComplete((response, error) -> Platform.runLater(() -> {
            if (error == null && response != null && response.isSuccess()) {
                String rtt = response.get("roundTripMillis") + " ms";
                loginRttLabel.setText(rtt);
                rttLabel.setText(rtt);
            }
        }));
    }

    private void onServerEvent(WireMessage message) {
        Platform.runLater(() -> handleServerEvent(message));
    }

    private void handleServerEvent(WireMessage message) {
        Map<String, String> data = message.getData();
        switch (message.getType()) {
            case CONNECTION_WELCOME -> appendEvent("Đã kết nối vào sàn");
            case AUCTION_CREATED -> {
                ClientAuction auction = ClientWireParser.auction(data, "");
                model.applySnapshot(auction, List.of(), ClientWireParser.serverNow(data), false);
                renderAuctions();
                appendEvent("Phòng đấu giá mới #" + auction.getAuctionId());
            }
            case BID_UPDATE -> {
                applyBidUpdate(data);
                renderAuctions();
                appendEvent("Giá mới: " + data.getOrDefault("currentPrice", ""));
            }
            case AUCTION_TICK -> {
                model.applyTick(
                        ClientWireParser.longValue(data, "auctionId", 0L),
                        ClientWireParser.instant(data, "endTime"),
                        data.getOrDefault("status", ""),
                        ClientWireParser.integer(data, "watcherCount", -1),
                        ClientWireParser.longValue(data, "version", -1L),
                        ClientWireParser.serverNow(data));
                auctionTable.refresh();
                renderDetails(auctionTable.getSelectionModel().getSelectedItem());
            }
            case AUCTION_EXTENDED -> {
                loadAuctionList();
                appendEvent("Đã gia hạn phòng #" + data.getOrDefault("auctionId", ""));
            }
            case AUCTION_ENDED, AUCTION_CANCELLED -> {
                ClientAuction auction = ClientWireParser.auction(data, "");
                model.applySnapshot(auction, model.currentBidSnapshot(),
                        ClientWireParser.serverNow(data), false);
                renderAuctions();
                refreshWallet();
                appendEvent("Phòng #" + auction.getAuctionId() + " đã kết thúc");
            }
            case AUCTION_ARCHIVED -> {
                model.removeAuction(ClientWireParser.longValue(data, "auctionId", 0L),
                        ClientWireParser.serverNow(data));
                renderAuctions();
                appendEvent("Phòng #" + data.getOrDefault("auctionId", "") + " đã được lưu trữ");
            }
            case AUCTION_KICKED -> {
                long auctionId = ClientWireParser.longValue(data, "auctionId", 0L);
                model.markKicked(auctionId);
                renderAuctions();
                showError(data.getOrDefault("message", "Bạn đã bị kick khỏi phòng"));
            }
            case OUTBID_NOTIFICATION -> appendEvent("BẠN ĐÃ BỊ VƯỢT GIÁ: "
                    + data.getOrDefault("productName", ""));
            case ERROR -> appendEvent("Thông báo: " + data.getOrDefault("message", ""));
            default -> {
                // Request responses are correlated by ClientTransport.
            }
        }
    }

    private void applyAuctionList(ApiResponse response) {
        Map<String, String> data = response.getWireMessage().getData();
        model.replaceAuctions(ClientWireParser.auctions(data), ClientWireParser.serverNow(data));
    }

    private void applySnapshot(ApiResponse response, boolean joined) {
        Map<String, String> data = response.getWireMessage().getData();
        model.applySnapshot(ClientWireParser.auction(data, ""), ClientWireParser.bids(data),
                ClientWireParser.serverNow(data), joined);
    }

    private void applyResync(ApiResponse response, boolean joined) {
        if (response.getWireMessage().getData().containsKey("auctionId")) {
            applySnapshot(response, joined);
        } else {
            applyAuctionList(response);
        }
        renderAuctions();
    }

    private void applyBidUpdate(Map<String, String> data) {
        model.applyAuctionUpdate(
                ClientWireParser.auction(data, ""),
                ClientWireParser.bid(data, "bid."),
                ClientWireParser.serverNow(data));
    }

    private void renderAuctions() {
        var snapshot = model.auctionSnapshot().stream().filter(value -> value.isOpen()
                || java.util.Objects.equals(model.getJoinedAuctionId(), value.getAuctionId())).toList();
        roomCount.setText(Integer.toString(snapshot.size()));
        openCount.setText(Long.toString(snapshot.stream().filter(ClientAuction::isOpen).count()));
        joinedRoom.setText(model.getJoinedAuctionId() == null ? "—" : "#" + model.getJoinedAuctionId());
        ClientAuction selected = auctionTable.getSelectionModel().getSelectedItem();
        long selectedId = selected == null ? 0L : selected.getAuctionId();
        long preferredId = selectedId != 0 ? selectedId : model.getJoinedAuctionId() == null ? 0 : model.getJoinedAuctionId();
        renderingAuctions = true;
        try {
            auctionTable.getItems().setAll(snapshot);
            auctionTable.getItems().stream().filter(value -> value.getAuctionId() == preferredId)
                    .findFirst().ifPresent(value -> auctionTable.getSelectionModel().select(value));
        } finally {
            renderingAuctions = false;
        }
        renderDetails(auctionTable.getSelectionModel().getSelectedItem());
    }

    private void renderDetails(ClientAuction auction) {
        if (auction == null) {
            detailName.setText("Chọn một phòng");
            detailHost.setText("");
            detailPrice.setText("");
            detailLeader.setText("");
            detailTime.setText("");
            detailVisibility.setText("");
            detailDescription.clear();
            productImage.setImage(null);
            productImageKey = "";
            updateHostButtons(null);
            return;
        }
        detailName.setText("#" + auction.getAuctionId() + " - " + auction.getProductName());
        detailHost.setText("Chủ phòng: " + auction.getHostUsername()
                + "  •  Bước giá " + money(auction.getMinBidIncrement()));
        String price = money(auction.getCurrentPrice());
        if (!price.equals(detailPrice.getText())) {
            detailPrice.setText(price);
            DesktopTheme.reveal(detailPrice);
        }
        detailLeader.setText("Người dẫn đầu: " + (auction.getCurrentWinnerUsername().isBlank()
                ? "Chưa có" : auction.getCurrentWinnerUsername()));
        detailTime.setText(countdownText(auction));
        detailVisibility.setText(!auction.isOpen() ? "ĐÃ KẾT THÚC" :
                auction.requiresPassword() ? "PHÒNG RIÊNG TƯ" : "PHÒNG CÔNG KHAI");
        detailDescription.setText(auction.getDescription());
        updateHostButtons(auction);
        loadProductImage(auction);
    }

    private void loadProductImage(ClientAuction auction) {
        String key = auction.getAuctionId() + ":" + auction.getImageVersion() + ":" + roomMode;
        if (!auction.hasImage()) { productImageKey = ""; productImage.setImage(null); return; }
        if (productImageKey.equals(key)) { return; }
        productImageKey = key;
        productImage.setImage(null);
        if (!imageRequests.add(key)) { return; }
        String variant = roomMode ? "detail" : "thumbnail";
        ProductMediaApi.connect(auctions, transport, model).thenCompose(api -> api.manifest(auction.getProductId(), auction.getAuctionId())
                .thenApply(body -> body.path("media").isEmpty() ? null : new Image(api.url(body.path("media").get(0).path(variant).asText()), true)))
                .whenComplete((image, error) -> Platform.runLater(() -> {
                    imageRequests.remove(key);
                    ClientAuction current = auctionTable.getSelectionModel().getSelectedItem();
                    if (current != null && current.getAuctionId() == auction.getAuctionId() && productImageKey.equals(key)) {
                        productImage.setImage(error == null ? image : null);
                        if (error != null || image == null) { productImageKey = ""; }
                        else { image.errorProperty().addListener((observable, before, failed) -> {
                            if (failed && productImageKey.equals(key)) { productImageKey = ""; }
                        }); }
                    }
                }));
    }

    private void refreshClock() {
        long now = System.nanoTime();
        if (now - lastWalletRefresh > TimeUnit.SECONDS.toNanos(4)) { lastWalletRefresh = now; refreshWallet(); }
        auctionTable.refresh();
        ClientAuction selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (!selected.isOpen() && selected.getEndedAt() != null
                    && !model.serverNow().isBefore(selected.getEndedAt().plusSeconds(60))) {
                model.removeAuction(selected.getAuctionId(), model.serverNow()); renderAuctions();
                appendEvent("Đã đóng màn hình kết quả sau 60 giây. Lịch sử và thanh toán vẫn được lưu trên server.");
            } else { detailTime.setText(countdownText(selected)); }
        }
    }

    private String countdownText(ClientAuction auction) {
        if (auction.isOpen()) { return "Còn lại: " + remainingText(auction); }
        Instant closed = auction.getEndedAt() == null ? auction.getEndTime() : auction.getEndedAt();
        long seconds = closed == null ? 0 : Math.max(0, java.time.Duration.between(model.serverNow(), closed.plusSeconds(60)).getSeconds());
        return "Tự rời phòng sau " + String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private String remainingText(ClientAuction auction) {
        if (auction.getEndTime() == null || !auction.isOpen()) {
            return "00:00";
        }
        long seconds = Math.max(0L,
                java.time.Duration.between(model.serverNow(), auction.getEndTime()).getSeconds());
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private ClientAuction selectedAuction() {
        ClientAuction selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Chọn một phòng đấu giá");
        }
        return selected;
    }

    private ClientAuction requireHostedAuction() {
        ClientAuction selected = selectedAuction();
        if (selected != null && !selected.isHostedBy(model.getUserId())) {
            showError("Chỉ chủ phòng mới được thực hiện thao tác này");
            return null;
        }
        return selected;
    }

    private void updateHostButtons(ClientAuction auction) {
        boolean joined = auction != null && java.util.Objects.equals(model.getJoinedAuctionId(), auction.getAuctionId());
        boolean available = auction != null && auction.isOpen() && transport.isConnected();
        boolean hosted = auction != null && auction.isHostedBy(model.getUserId());
        joinButton.setDisable(!available || joined);
        leaveButton.setDisable(model.getJoinedAuctionId() == null);
        boolean leading = auction != null && java.util.Objects.equals(auction.getCurrentWinnerId(), model.getUserId());
        bidButton.setDisable(!available || !joined || hosted || leading);
        bidField.setDisable(bidButton.isDisabled());
        historyButton.setDisable(auction == null);
        managementPane.setVisible(hosted);
        managementPane.setManaged(hosted);
        joinedRoom.setText(model.getJoinedAuctionId() == null ? "—" : "#" + model.getJoinedAuctionId());
        detailVisibility.setVisible(auction != null);
        boolean disabled = auction == null || !auction.isOpen()
                || !auction.isHostedBy(model.getUserId());
        boolean hasBids = auction != null && auction.getCurrentWinnerId() != null;
        extendButton.setDisable(disabled || hasBids);
        endButton.setDisable(disabled || hasBids);
        cancelButton.setDisable(disabled || hasBids);
        kickButton.setDisable(disabled);
        updateRoomLayout(joined);
        renderRoomActivity(auction, joined, hosted, leading);
    }

    private void updateRoomLayout(boolean joined) {
        if (roomMode == joined) { return; }
        roomMode = joined;
        statsPanel.setVisible(!joined); statsPanel.setManaged(!joined);
        roomHeading.setVisible(joined); roomHeading.setManaged(joined);
        joinButton.setVisible(!joined); joinButton.setManaged(!joined);
        bidSuggestions.setVisible(joined); bidSuggestions.setManaged(joined);
        bidHint.setVisible(joined); bidHint.setManaged(joined);
        productDescription.setExpanded(joined);
        updateRoomImageSize();
        detailPanel.setCenter(null); detailPanel.setBottom(null);
        if (joined) {
            listingSplit.getItems().remove(listingPanel);
            productDetails.getChildren().remove(priceBlock); bidding.getChildren().add(0, priceBlock);
            bidding.getStyleClass().setAll("bid-card"); bidding.setPadding(new Insets(22));
            productDetails.getStyleClass().add("room-product");
            roomSide.getChildren().add(0, bidding);
            roomColumns.getChildren().setAll(detailsScroll, roomControlsScroll);
            detailPanel.setCenter(roomColumns);
            detailsScroll.setVvalue(0); roomControlsScroll.setVvalue(0);
        } else {
            roomColumns.getChildren().clear(); roomSide.getChildren().remove(bidding);
            bidding.getChildren().remove(priceBlock); productDetails.getChildren().add(4, priceBlock);
            bidding.getStyleClass().setAll("card"); bidding.setPadding(new Insets(16));
            productDetails.getStyleClass().remove("room-product");
            detailPanel.setCenter(detailsScroll); detailPanel.setBottom(bidding);
            listingSplit.getItems().add(0, listingPanel); listingSplit.setDividerPositions(0.60);
            pageTitle.setText("Sàn đấu giá"); pageEyebrow.setText("KHÁM PHÁ & ĐẤU GIÁ");
        }
    }

    private void renderRoomActivity(ClientAuction auction, boolean joined, boolean hosted, boolean leading) {
        if (!joined || auction == null) { return; }
        pageTitle.setText("Phòng đấu giá #" + auction.getAuctionId());
        pageEyebrow.setText(hosted ? "KHÔNG GIAN CHỦ PHÒNG" : "PHIÊN ĐẤU GIÁ CỦA BẠN");
        BigDecimal minimum = auction.getCurrentPrice().add(auction.getMinBidIncrement());
        bidField.setPromptText("Tối thiểu " + money(minimum));
        bidHint.setText(!transport.isConnected() ? "Mất kết nối. Đang chờ kết nối lại với server."
                : !auction.isOpen() ? "Phiên đã kết thúc. Không nhận thêm lượt đặt giá."
                : hosted ? "Bạn là chủ phòng, không thể đặt giá sản phẩm của mình."
                : leading ? "Bạn đang dẫn đầu. Chờ người khác đặt giá trước khi trả giá tiếp."
                : "Giá tối thiểu " + money(minimum) + " · Bước giá " + money(auction.getMinBidIncrement()));
        bidSuggestions.getChildren().clear();
        for (int steps : new int[]{1, 3, 5}) {
            BigDecimal amount = auction.getCurrentPrice().add(auction.getMinBidIncrement().multiply(BigDecimal.valueOf(steps)));
            Button suggestion = new Button("+" + money(auction.getMinBidIncrement().multiply(BigDecimal.valueOf(steps))));
            suggestion.getStyleClass().add("ghost"); suggestion.setDisable(bidButton.isDisabled());
            suggestion.setOnAction(event -> bidField.setText(amount.toPlainString()));
            bidSuggestions.getChildren().add(suggestion);
        }
        roomHistory.getChildren().setAll(DesktopTheme.label("Lượt đặt giá mới nhất", "section-title"));
        List<ClientBid> bids = model.currentBidSnapshot().stream().filter(bid -> bid.getAuctionId() == auction.getAuctionId()).limit(6).toList();
        if (bids.isEmpty()) {
            roomHistory.getChildren().add(DesktopTheme.label("Chưa có lượt đặt giá. Các lượt hợp lệ sẽ xuất hiện tại đây theo thời gian thực.", "muted"));
        }
        for (ClientBid bid : bids) {
            VBox identity = new VBox(4, new Label(bid.getUsername()), DesktopTheme.label(DATE_TIME.format(bid.getCreatedAt()), "muted"));
            HBox row = new HBox(10, identity, DesktopTheme.spacer(), DesktopTheme.label(money(bid.getAmount()), "bid-amount"));
            row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("bid-row"); roomHistory.getChildren().add(row);
        }
    }

    private void updateRoomImageSize() {
        double height = roomMode ? Math.max(190, Math.min(285, (stage.getHeight() - 360) * 0.72)) : 160;
        roomImage.setPrefHeight(height); roomImage.setMaxHeight(height);
    }

    private boolean success(ApiResponse response, Throwable error) {
        if (error != null) {
            showError(rootMessage(error));
            return false;
        }
        if (response == null || !response.isSuccess()) {
            String code = response == null ? "NO_RESPONSE" : response.getErrorCode();
            String message = response == null ? "Server không trả dữ liệu" : response.getMessageText();
            showError(code + (message.isBlank() ? "" : ": " + message));
            return false;
        }
        return true;
    }

    private void setLoginBusy(boolean busy) {
        loginButton.setDisable(busy);
        registerButton.setDisable(busy);
        connectButton.setDisable(busy);
    }

    private void appendEvent(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String line = DATE_TIME.format(Instant.now()) + " | " + message + System.lineSeparator();
        eventLog.appendText(line);
        if (eventLog.getLength() > 24000) {
            eventLog.deleteText(0, eventLog.getLength() - 16000);
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message == null ? "Lỗi không xác định" : message);
        alert.setHeaderText("Auction");
        showDialog(alert);
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText("Auction");
        showDialog(alert);
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Xác nhận");
        return showDialog(alert).orElse(ButtonType.NO) == ButtonType.YES;
    }

    private <T> Optional<T> showDialog(Dialog<T> dialog) {
        DesktopTheme.dialog(dialog, stage);
        return dialog.showAndWait();
    }

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        return grid;
    }

    private void addRow(GridPane grid, int row, String label, javafx.scene.Node node) {
        grid.add(new Label(label), 0, row);
        grid.add(node, 1, row);
        GridPane.setHgrow(node, Priority.ALWAYS);
    }

    private void productColumn(
            TableView<ClientProduct> table,
            String title,
            java.util.function.Function<ClientProduct, String> mapper,
            double width) {
        TableColumn<ClientProduct, String> column = new TableColumn<>(title);
        column.setCellValueFactory(value -> new SimpleStringProperty(mapper.apply(value.getValue())));
        column.setPrefWidth(width);
        table.getColumns().add(column);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        autoReconnect = false;
        clock.stop();
        scheduler.shutdownNow();
        transport.close();
    }

    private static final class AuctionFormData {
        private final long productId;
        private final BigDecimal startPrice;
        private final BigDecimal increment;
        private final int durationMinutes;
        private final String visibility;
        private final String roomPassword;

        private AuctionFormData(
                long productId,
                BigDecimal startPrice,
                BigDecimal increment,
                int durationMinutes,
                String visibility,
                String roomPassword) {
            this.productId = productId;
            this.startPrice = startPrice;
            this.increment = increment;
            this.durationMinutes = durationMinutes;
            this.visibility = visibility;
            this.roomPassword = roomPassword;
        }
    }
}
