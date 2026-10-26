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
import javafx.scene.control.CheckBox;
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
import vn.ptit.btl16.common.util.Money;
import vn.ptit.btl16.common.fx.WalletUi;
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
    private final ClientSounds sounds;
    private ClientSoundUi soundUi;
    private boolean soundConnected;
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
    private long walletRequestSequence;
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
    private final ComboBox<String> lobbyVisibility = new ComboBox<>(), lobbyIdentity = new ComboBox<>(), lobbyOrder = new ComboBox<>();
    private final CheckBox lobbyHosted = new CheckBox("Toi to chuc");
    private final Label lobbyPageLabel = new Label(), lobbyStatus = new Label();
    private final Button lobbyPrevious = new Button("Truoc"), lobbyNext = new Button("Tiep");
    private int lobbyPage;
    private long lobbyGeneration;
    private boolean creatingAuction;
    private Button createAuctionAction;
    private final Label openCount = DesktopTheme.label("0", "stat-value");
    private final Label roomCount = DesktopTheme.label("0", "stat-value");
    private final Label joinedRoom = DesktopTheme.label("—", "stat-value");
    private final java.util.Set<String> imageRequests = new java.util.HashSet<>();
    private final Label pageTitle = DesktopTheme.label("Sàn đấu giá", "page-title");
    private final Label pageEyebrow = DesktopTheme.label("KHÁM PHÁ & ĐẤU GIÁ", "eyebrow");
    private final Label bidHint = DesktopTheme.label("", "bid-hint");
    private final FlowPane bidSuggestions = new FlowPane(6, 6);
    private final List<Button> quickBidButtons = List.of(new Button(), new Button(), new Button());
    private final Label bidFeedback = DesktopTheme.label("", "bid-feedback");
    private final Label roomConnection = DesktopTheme.label("", "bid-hint");
    private final Label roomFunds = DesktopTheme.label("", "bid-hint");
    private final Label hostLockReason = DesktopTheme.label("", "muted");
    private final Button syncRoomButton = new Button("\u0110\u1ed3ng b\u1ed9 l\u1ea1i");
    private final TitledPane roomRules = new TitledPane();
    private long roomGeneration, roomWalletRequest;
    private volatile long roomConnectionGeneration;
    private long lastRoomSignal, lastRoomWallet;
    private boolean roomChanging, roomSyncing, roomSnapshotReady, roomWalletLoading, bidPending;
    private BigDecimal roomAvailable, roomHeld, maxBidAmount, uncertainBidAmount;
    private ScheduledFuture<?> bidTimeout;
    private long bidRequestSequence;
    private static final long ROOM_FRESH_NANOS = TimeUnit.SECONDS.toNanos(10);
    private record RoomRequest(long generation, long connection, String token, long auctionId) { }
    private final LiveAuctionRoom liveRoom = new LiveAuctionRoom();
    private final java.util.Set<String> shownReceipts = new java.util.HashSet<>();
    private final RoomSocialPane socialRoom;
    private final VBox roomMain = new VBox(12);
    private final VBox roomHistory = liveRoom.history();
    private final VBox priceBlock = new VBox(6);
    private final Label priceCaption = DesktopTheme.label("GI\u00c1 HI\u1ec6N T\u1ea0I", "eyebrow");
    private final HBox roomColumns = new HBox(16);
    private final BorderPane detailPanel = new BorderPane();
    private final StackPane roomImage = new StackPane();
    private final HBox roomProductSummary = new HBox(14);
    private final VBox roomProductIdentity = new VBox(8);
    private final HBox productHeader = new HBox(8);
    private final SplitPane listingSplit = new SplitPane();
    private VBox listingPanel, bidding, roomSide, productDetails;
    private HBox statsPanel, roomHeading;
    private ScrollPane detailsScroll, roomControlsScroll, roomBidScroll;
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
        this(stage, config, model, transport, accounts, auctions, new ClientSounds());
    }

    public FxClientController(Stage stage, ClientConfig config, ClientAppModel model,
            ClientTransport transport, AccountApi accounts, AuctionApi auctions, ClientSounds sounds) {
        this.stage = stage;
        this.config = config;
        this.model = model;
        this.transport = transport;
        this.accounts = accounts;
        this.auctions = auctions;
        this.sounds = sounds;
        this.socialRoom = new RoomSocialPane(transport);
        this.socialRoom.onSound(sounds::playOnce);
        this.socialRoom.setOwner(stage);
        this.socialRoom.onStatistics(liveRoom::setStatistics);
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
        if (soundUi == null) { soundUi = new ClientSoundUi(stage, sounds); }
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
        form.getChildren().add(ClientSoundUi.controls(sounds));
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
        createAuctionAction = createAuctionButton;
        Button refreshButton = new Button("↻  Làm mới");
        Button logoutButton = new Button("↗   Đăng xuất");
        Button marketplace = new Button("◉   Sàn đấu giá");
        marketplace.getStyleClass().add("nav-active");
        createAuctionButton.getStyleClass().add("primary");
        VBox sidebar = new VBox(18, DesktopTheme.brand("LIVE"));
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(215);
        for (Button navigation : List.of(marketplace, productsButton, walletButton, profileButton)) {
            navigation.getProperties().put("client.sound.cue", ClientSounds.Cue.NAVIGATE);
        }
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
                DesktopTheme.label("ĐANG ĐĂNG NHẬP", "eyebrow"), userLabel, walletBalance, ClientSoundUi.controls(sounds), logoutButton);
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
        lobbyVisibility.getItems().setAll("ALL", "PUBLIC", "PRIVATE"); lobbyVisibility.setValue("ALL");
        lobbyIdentity.getItems().setAll("ALL", "NAMED", "ANONYMOUS"); lobbyIdentity.setValue("ALL");
        lobbyOrder.getItems().setAll("ENDING", "PRICE_ASC", "PRICE_DESC"); lobbyOrder.setValue("ENDING");
        lobbyVisibility.setConverter(lobbyLabels(Map.of("ALL", "Moi quyen vao", "PUBLIC", "Cong khai", "PRIVATE", "Rieng tu")));
        lobbyIdentity.setConverter(lobbyLabels(Map.of("ALL", "Moi danh tinh", "NAMED", "Khach thuong", "ANONYMOUS", "Khach an danh")));
        lobbyOrder.setConverter(lobbyLabels(Map.of("ENDING", "Sap het gio", "PRICE_ASC", "Gia tang dan", "PRICE_DESC", "Gia giam dan")));
        lobbyVisibility.setPromptText("Quyen vao"); lobbyIdentity.setPromptText("Danh tinh");
        FlowPane filters = new FlowPane(8, 8, searchMode, lobbyVisibility, lobbyIdentity, lobbyOrder, lobbyHosted, clearButton);
        for (ComboBox<String> filter : List.of(lobbyVisibility, lobbyIdentity, lobbyOrder)) {
            filter.setOnAction(event -> { lobbyPage = 0; renderAuctions(); });
        }
        lobbyHosted.setOnAction(event -> { lobbyPage = 0; renderAuctions(); });
        lobbyPrevious.setOnAction(event -> { lobbyPage = Math.max(0, lobbyPage - 1); renderAuctions(); });
        lobbyNext.setOnAction(event -> { lobbyPage++; renderAuctions(); });
        search.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        configureAuctionTable();
        listingPanel = new VBox(16,
                new HBox(10, DesktopTheme.label("Phòng đấu giá", "section-title"), DesktopTheme.spacer(), refreshButton),
                search, filters, lobbyStatus, auctionTable, new HBox(8, lobbyPrevious, lobbyPageLabel, lobbyNext));
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
        for (int index = 0; index < quickBidButtons.size(); index++) {
            int steps = new int[]{1, 3, 5}[index];
            Button suggestion = quickBidButtons.get(index);
            suggestion.getStyleClass().add("ghost");
            suggestion.setOnAction(event -> {
                ClientAuction current = model.joinedAuction();
                if (current == null || bidPending || !roomReady() || bidButton.isDisabled()) { return; }
                BigDecimal amount = current.getCurrentPrice().add(current.getMinBidIncrement().multiply(BigDecimal.valueOf(steps)));
                if (amount.compareTo(maxBidAmount) <= 0) { bidField.setText(Money.input(amount)); }
            });
            bidSuggestions.getChildren().add(suggestion);
        }
        HBox.setHgrow(bidField, Priority.ALWAYS);
        membership.setAlignment(Pos.CENTER_LEFT);
        bidFeedback.setId("bid-feedback"); bidFeedback.setWrapText(true);
        bidFeedback.setVisible(false); bidFeedback.setManaged(false);
        roomConnection.setId("room-connection"); roomConnection.setWrapText(true);
        roomFunds.setId("room-funds"); roomFunds.setWrapText(true);
        bidHint.setWrapText(true);
        hostLockReason.setId("host-lock-reason"); hostLockReason.setWrapText(true);
        VBox management = new VBox(8, (javafx.scene.Node) managementPane.getContent(), hostLockReason);
        managementPane.setContent(management);
        syncRoomButton.setOnAction(event -> synchronizeRoom());
        membership.getChildren().add(syncRoomButton);
        HBox syncRow = new HBox(8, roomConnection);
        HBox.setHgrow(roomConnection, Priority.ALWAYS); roomConnection.setMinWidth(0);
        Label rules = DesktopTheme.label("Gi\u00e1 \u0111\u1ea7u \u2265 kh\u1edfi \u0111i\u1ec3m + b\u01b0\u1edbc gi\u00e1. L\u01b0\u1ee3t sau \u2265 gi\u00e1 hi\u1ec7n t\u1ea1i + b\u01b0\u1edbc; kh\u00f4ng b\u1eaft bu\u1ed9c b\u1ed9i s\u1ed1.\n"
                + "T\u1ed1i \u0111a 2 s\u1ed1 th\u1eadp ph\u00e2n; d\u00f9ng d\u1ea5u ph\u1ea9y (v\u00ed d\u1ee5 1.234,56). L\u01b0\u1ee3t h\u1ee3p l\u1ec7 kh\u00f4ng th\u1ec3 r\u00fat.\n"
                + "Ch\u1ee7 ph\u00f2ng v\u00e0 ng\u01b0\u1eddi \u0111ang d\u1eabn \u0111\u1ea7u kh\u00f4ng \u0111\u01b0\u1ee3c tr\u1ea3 gi\u00e1. Server quy\u1ebft \u0111\u1ecbnh gi\u00e1, gi\u1eef ti\u1ec1n v\u00e0 gia h\u1ea1n s\u00e1t gi\u1edd; \u0111\u1ed3ng h\u1ed3 v\u1ec1 0 ch\u01b0a ph\u1ea3i k\u1ebft qu\u1ea3.", "bid-hint");
        rules.setWrapText(true);
        roomRules.setText("Lu\u1eadt \u0111\u1eb7t gi\u00e1 ng\u1eafn"); roomRules.setContent(rules); roomRules.setExpanded(false);
        bidding = new VBox(6, membership, syncRow, bid, bidFeedback, bidSuggestions, bidHint, roomFunds, roomRules);
        roomBidScroll = new ScrollPane(); roomBidScroll.setFitToWidth(true);
        roomBidScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        roomBidScroll.setMinHeight(180); roomBidScroll.setPrefViewportHeight(380);
        roomBidScroll.maxHeightProperty().bind(javafx.beans.binding.Bindings.max(220, roomColumns.heightProperty().subtract(168)));
        roomBidScroll.getStyleClass().add("room-bid-scroll");
        bidding.getStyleClass().add("card"); bidding.setPadding(new Insets(16));
        bidSuggestions.setVisible(false); bidSuggestions.setManaged(false);
        bidHint.setVisible(false); bidHint.setManaged(false);
        detailPanel.setCenter(detailsScroll); detailPanel.setBottom(bidding);
        BorderPane.setMargin(bidding, new Insets(10, 0, 0, 0));
        detailPanel.setMinWidth(300);
        liveRoom.onHistory(this::showHistory);
        liveRoom.onProfile(socialRoom::openProfile);
        roomSide = new VBox(8);
        roomControlsScroll = new ScrollPane(roomHistory); roomControlsScroll.setFitToWidth(true);
        roomControlsScroll.setMinHeight(160); VBox.setVgrow(roomControlsScroll, Priority.ALWAYS);
        roomSide.getChildren().add(roomControlsScroll);
        roomSide.setMinWidth(340); roomSide.setPrefWidth(400); roomSide.setMaxWidth(430);
        roomControlsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        roomControlsScroll.setMinWidth(340); roomControlsScroll.setPrefWidth(390); roomControlsScroll.setMaxWidth(430);
        roomMain.setMinWidth(300); roomMain.setMinHeight(0); HBox.setHgrow(roomMain, Priority.ALWAYS);
        VBox.setVgrow(socialRoom, Priority.ALWAYS);
        roomProductSummary.getStyleClass().addAll("card", "room-product");
        HBox.setHgrow(detailsScroll, Priority.ALWAYS); HBox.setHgrow(roomSide, Priority.ALWAYS);
        listingSplit.getItems().setAll(listingPanel, detailPanel);
        listingSplit.setDividerPositions(0.60);
        VBox.setVgrow(listingSplit, Priority.ALWAYS);
        roomHeading = new HBox(10, DesktopTheme.label("ĐẤU GIÁ TRỰC TIẾP", "eyebrow"), DesktopTheme.spacer(),
                DesktopTheme.label("Giá và thời gian được đồng bộ từ server", "muted"));
        roomHeading.getChildren().setAll(liveRoom.heading());
        HBox.setHgrow(liveRoom.heading(), Priority.ALWAYS);
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
        Button receiptsButton = new Button("K\u1ebft qu\u1ea3 phi\u00ean c\u1ee7a t\u00f4i");
        receiptsButton.setId("my-auction-receipts"); receiptsButton.setMaxWidth(Double.MAX_VALUE);
        sidebar.getChildren().add(5, receiptsButton);
        receiptsButton.setOnAction(event -> showPersonalReceipt(0));
        Button notificationsButton = new Button("Thong bao ca nhan"); notificationsButton.setMaxWidth(Double.MAX_VALUE);
        sidebar.getChildren().add(6, notificationsButton);
        notificationsButton.setOnAction(event -> {
            String session = model.getSessionToken(); long user = model.getUserId();
            java.util.function.BooleanSupplier same = () -> model.getUserId() == user && session.equals(model.getSessionToken());
            new PersonalNotificationsPane(stage, transport, same, (kind, room) -> {
                if (!same.getAsBoolean()) { return; }
                if (kind.startsWith("TOPUP")) { new WalletPane(stage, new WalletApi(transport), user, model.getUsername(), same).show(); }
                else if (kind.equals("AUCTION_RESULT")) { showPersonalReceipt(room); }
                else {
                    lobbyPage = 0; lobbyVisibility.setValue("ALL"); lobbyIdentity.setValue("ALL"); lobbyHosted.setSelected(false);
                    searchMode.setValue("ROOM_ID"); searchField.setText(Long.toString(room)); loadAuctionList();
                    showError("Tim phong #" + room + " trong sanh va bam Vao phong. Phong rieng van can quyen/mat khau; neu da dong hay mo Ket qua phien cua toi.");
                }
            }).show();
        });
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
            lobbyVisibility.setValue("ALL"); lobbyIdentity.setValue("ALL"); lobbyHosted.setSelected(false); lobbyOrder.setValue("ENDING"); lobbyPage = 0;
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

    private static StringConverter<String> lobbyLabels(Map<String, String> labels) {
        return new StringConverter<>() {
            @Override public String toString(String value) { return labels.getOrDefault(value == null ? "" : value, ""); }
            @Override public String fromString(String value) { return value; }
        };
    }

    private void configureAuctionTable() {
        addColumn("MÃ", value -> "#" + value.getAuctionId(), 55);
        addColumn("SẢN PHẨM", ClientAuction::getProductName, 150);
        addColumn("GIÁ HIỆN TẠI", value -> money(value.getCurrentPrice()), 120);
        addColumn("CÒN LẠI", this::remainingText, 80);
        addColumn("CHE DO", value -> value.getVisibility() + (value.hasAnonymousGuests() ? " / ANONYMOUS" : " / NAMED"), 140);
        auctionTable.setRowFactory(view -> new TableRow<>() {
            @Override protected void updateItem(ClientAuction item, boolean empty) {
                super.updateItem(item, empty);
                boolean soon = !empty && item != null && item.isOpen() && item.getEndTime() != null
                        && !item.getEndTime().isAfter(model.serverNow().plusSeconds(60));
                setStyle(soon ? "-fx-background-color: #fff0d5; -fx-font-weight: bold;" : "");
            }
        });
        auctionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox empty = new VBox(12, DesktopTheme.label("Chưa có phòng đấu giá", "section-title"));
        empty.setAlignment(Pos.CENTER);
        auctionTable.setPlaceholder(empty);
    }

    private void addColumn(String title, java.util.function.Function<ClientAuction, String> mapper, double width) {
        TableColumn<ClientAuction, String> column = new TableColumn<>(title);
        column.setCellValueFactory(value -> new SimpleStringProperty(mapper.apply(value.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
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
        bidButton.setMinWidth(86);
        bidField.setMinWidth(0);
        cancelButton.getStyleClass().add("danger");
        bidField.setPromptText("Nhập giá của bạn");
        Button gallery = new Button("Ảnh / video");
        gallery.getStyleClass().add("ghost");
        gallery.setOnAction(event -> {
            ClientAuction selected = auctionTable.getSelectionModel().getSelectedItem();
            if (selected != null) { showMediaGallery(selected.getProductId(), selected.getAuctionId(), selected.getProductName()); }
        });
        productHeader.getChildren().setAll(detailVisibility, DesktopTheme.spacer(), gallery);
        productHeader.setAlignment(Pos.CENTER_LEFT);
        FlowPane hostActions = new FlowPane(8, 8, extendButton, endButton, cancelButton, kickButton);
        managementPane.setText("Quản lý phòng");
        managementPane.setContent(hostActions);
        managementPane.setExpanded(false);
        productDescription = new TitledPane("Thông tin sản phẩm", detailDescription);
        productDescription.setExpanded(false);
        priceBlock.getChildren().addAll(priceCaption, detailPrice,
                detailTime, detailLeader);
        liveRoom.insights().setVisible(false);
        liveRoom.insights().setManaged(false);
        VBox details = new VBox(12, productHeader, detailName, detailHost, roomImage, priceBlock,
                liveRoom.insights(), productDescription, managementPane);
        details.getStyleClass().add("card");
        details.setPadding(new Insets(19));
        return details;
    }

    private String money(BigDecimal value) {
        return WalletUi.money(value);
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
                    sounds.play(ClientSounds.Cue.SUCCESS);
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
        RoomRequest request = roomRequest(0);
        accounts.resumeSession(model.getSessionToken())
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (!current(request, false)) { return; }
                    if (error != null || response == null) { refreshBidControls(); return; }
                    if (!response.isSuccess()) {
                        autoReconnect = false; invalidateRoom(true);
                        model.clearIdentity(); socialRoom.clear(); stage.setScene(loginScene);
                        return;
                    }
                    model.applyIdentity(response);
                    showMain();
                    synchronizeRoom();
                    loadAuctionList();
                }));
    }

    private void showMain() {
        walletLoading = false; walletRequestSequence++;
        walletBalance.setText("V\u00ed: \u0111ang \u0111\u1ed3ng b\u1ed9...");
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
        long sequence = ++walletRequestSequence;
        long connection = roomConnectionGeneration;
        new WalletApi(transport).get().whenComplete((response, error) -> Platform.runLater(() -> {
            if (sequence != walletRequestSequence) { return; }
            walletLoading = false;
            if (!model.getSessionToken().equals(token) || connection != roomConnectionGeneration || closed.get()) { return; }
            if (error == null && response != null && response.isSuccess()) {
                walletBalance.setText("Khả dụng: " + money(new BigDecimal(response.get("available"))));
            }
        }));
    }

    private void loadAuctionList() {
        if (!transport.isConnected() || !model.hasSessionToken()) {
            return;
        }
        RoomRequest request = roomRequest(0);
        long generation = ++lobbyGeneration; lobbyStatus.setText("Dang tai danh sach...");
        auctions.listAuctions().whenComplete((response, error) -> Platform.runLater(() -> {
            if (!current(request, false) || generation != lobbyGeneration) { return; }
            if (!success(response, error)) {
                lobbyStatus.setText("Khong tai duoc sanh. Bam Lam moi de thu lai.");
                return;
            }
            applyAuctionList(response);
            lobbyStatus.setText("Loc va sap xep toan bo du lieu truoc khi chia trang (30 phien/trang).");
            renderAuctions();
        }));
    }

    private void search() {
        lobbyPage = 0; loadAuctionList();
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
        if (roomChanging) { return; }
        invalidateRoom(true); roomChanging = true;
        RoomRequest request = roomRequest(selected.getAuctionId());
        refreshBidControls();
        auctions.join(selected.getAuctionId(), password)
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (!current(request, false)) { return; }
                    roomChanging = false;
                    if (success(response, error)) {
                        applySnapshot(response, true);
                        sounds.play(ClientSounds.Cue.NAVIGATE);
                        roomSnapshotReady = true; lastRoomSignal = System.nanoTime();
                        refreshRoomWallet();
                        renderAuctions();
                        auctionTable.getSelectionModel().select(model.joinedAuction());
                        appendEvent("Đã vào phòng #" + selected.getAuctionId());
                    } else { synchronizeRoom(); }
                    refreshBidControls();
                }));
    }

    private void leaveJoined() {
        Long auctionId = model.getJoinedAuctionId();
        if (auctionId == null) {
            showError("Bạn chưa vào phòng nào");
            return;
        }
        if (roomChanging) { return; }
        invalidateRoom(true); roomChanging = true;
        RoomRequest request = roomRequest(auctionId);
        refreshBidControls();
        auctions.leave(auctionId).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!current(request, true)) { return; }
            roomChanging = false;
            if (success(response, error)) {
                model.leaveJoinedAuction();
                sounds.play(ClientSounds.Cue.NAVIGATE);
                appendEvent("Đã rời phòng #" + auctionId);
                renderAuctions();
            } else { synchronizeRoom(); }
            refreshBidControls();
        }));
    }

    private RoomRequest roomRequest(long auctionId) {
        return new RoomRequest(roomGeneration, roomConnectionGeneration, model.getSessionToken(), auctionId);
    }

    private boolean current(RoomRequest request, boolean joined) {
        return !closed.get() && request.generation() == roomGeneration
                && request.connection() == roomConnectionGeneration
                && !request.token().isBlank() && request.token().equals(model.getSessionToken())
                && (!joined || java.util.Objects.equals(model.getJoinedAuctionId(), request.auctionId()));
    }

    private void invalidateRoom(boolean clearFeedback) {
        socialRoom.invalidateRequests();
        roomGeneration++;
        walletLoading = false; walletRequestSequence++;
        walletBalance.setText("V\u00ed: \u0111ang \u0111\u1ed3ng b\u1ed9...");
        roomWalletRequest++;
        roomSnapshotReady = false;
        roomSyncing = false;
        roomWalletLoading = false;
        bidPending = false;
        roomAvailable = null; roomHeld = null; maxBidAmount = null;
        if (bidTimeout != null) { bidTimeout.cancel(false); bidTimeout = null; }
        if (clearFeedback) { uncertainBidAmount = null; feedback("", ""); bidField.clear(); }
    }

    private boolean roomReady() {
        long now = System.nanoTime();
        return transport.isConnected() && model.hasSessionToken() && !roomChanging && !roomSyncing
                && roomSnapshotReady && roomAvailable != null && roomHeld != null && maxBidAmount != null
                && now - lastRoomSignal < ROOM_FRESH_NANOS && now - lastRoomWallet < ROOM_FRESH_NANOS;
    }

    private void feedback(String text, String tone) {
        if (!text.isBlank() && !text.equals(bidFeedback.getText())) {
            if (tone.equals("error")) { sounds.play(ClientSounds.Cue.ERROR); }
            else if (tone.equals("warning")) { sounds.play(ClientSounds.Cue.WARNING); }
        }
        bidFeedback.setText(text);
        bidFeedback.getStyleClass().setAll("bid-feedback", "bid-feedback-" + tone);
        bidFeedback.setVisible(!text.isBlank()); bidFeedback.setManaged(!text.isBlank());
    }

    private void refreshBidControls() {
        updateHostButtons(auctionTable.getSelectionModel().getSelectedItem());
    }

    private void synchronizeRoom() {
        Long auctionId = model.getJoinedAuctionId();
        if (auctionId == null || roomChanging || roomSyncing || !transport.isConnected() || !model.hasSessionToken()) { return; }
        roomSyncing = true; roomSnapshotReady = false;
        roomAvailable = null; roomHeld = null;
        roomWalletRequest++; roomWalletLoading = false;
        RoomRequest request = roomRequest(auctionId);
        refreshBidControls();
        auctions.resync(auctionId).orTimeout(config.getRequestTimeoutMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (!current(request, true)) { return; }
                    roomSyncing = false;
                    if (error != null || response == null || !response.isSuccess()
                            || !Long.toString(auctionId).equals(response.get("auctionId"))) {
                        feedback("Ch\u01b0a \u0111\u1ed3ng b\u1ed9 \u0111\u01b0\u1ee3c ph\u00f2ng. D\u1eef li\u1ec7u c\u0169; b\u1ea5m \u0110\u1ed3ng b\u1ed9 l\u1ea1i."
                                + (uncertainBidAmount == null ? "" : " L\u01b0\u1ee3t g\u1eedi tr\u01b0\u1edbc v\u1eabn ch\u01b0a x\u00e1c nh\u1eadn."), "warning");
                        refreshBidControls(); return;
                    }
                    applySnapshot(response, true);
                    roomSnapshotReady = true; lastRoomSignal = System.nanoTime();
                    confirmUncertainBid();
                    renderAuctions();
                    refreshRoomWallet();
                }));
    }

    private void refreshRoomWallet() {
        Long auctionId = model.getJoinedAuctionId();
        if (auctionId == null || roomChanging || roomSyncing || roomWalletLoading || !roomSnapshotReady
                || !transport.isConnected() || !model.hasSessionToken()) { return; }
        RoomRequest request = roomRequest(auctionId);
        long sequence = ++roomWalletRequest;
        roomWalletLoading = true;
        new WalletApi(transport).get(auctionId).orTimeout(config.getRequestTimeoutMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (!current(request, true) || sequence != roomWalletRequest) { return; }
                    roomWalletLoading = false;
                    try {
                        if (error != null || response == null || !response.isSuccess()
                                || !Long.toString(auctionId).equals(response.get("auctionId"))) {
                            throw new IllegalArgumentException("Invalid room wallet response");
                        }
                        BigDecimal available = Money.normalize(new BigDecimal(response.get("available")));
                        BigDecimal held = Money.normalize(new BigDecimal(response.get("roomHeld")));
                        BigDecimal maximum = Money.normalize(new BigDecimal(response.get("maxBidAmount")));
                        if (available.signum() < 0 || held.signum() < 0 || maximum.signum() <= 0) {
                            throw new IllegalArgumentException("Invalid room wallet amount");
                        }
                        roomAvailable = available; roomHeld = held; maxBidAmount = maximum;
                        lastRoomWallet = System.nanoTime();
                        walletBalance.setText("Kh\u1ea3 d\u1ee5ng: " + money(available));
                    } catch (RuntimeException exception) {
                        roomAvailable = null; roomHeld = null;
                        feedback("Ch\u01b0a t\u1ea3i \u0111\u01b0\u1ee3c s\u1ed1 d\u01b0 ri\u00eang. B\u1ea5m \u0110\u1ed3ng b\u1ed9 l\u1ea1i tr\u01b0\u1edbc khi \u0111\u1eb7t gi\u00e1."
                                + (uncertainBidAmount == null ? "" : " L\u01b0\u1ee3t g\u1eedi tr\u01b0\u1edbc ch\u01b0a x\u00e1c nh\u1eadn."), "warning");
                    }
                    refreshBidControls();
                }));
    }

    private void roomWalletChanged() {
        roomWalletRequest++; roomWalletLoading = false;
        roomAvailable = null; roomHeld = null;
        refreshRoomWallet();
    }

    private void confirmUncertainBid() {
        ClientAuction auction = model.joinedAuction();
        if (uncertainBidAmount == null || auction == null) { return; }
        BigDecimal amount = uncertainBidAmount;
        if (model.currentBidSnapshot().stream().anyMatch(bid -> bid.getAuctionId() == auction.getAuctionId()
                && bid.getUserId() == auction.viewerId(model.getUserId()) && bid.getAmount().compareTo(amount) == 0)) {
            uncertainBidAmount = null;
            bidConfirmedSound(auction.getAuctionId(), amount);
            feedback("Server \u0111\u00e3 x\u00e1c nh\u1eadn l\u01b0\u1ee3t " + money(amount) + ". Ch\u01b0a ph\u1ea3i k\u1ebft qu\u1ea3 th\u1eafng.", "success");
        }
    }

    private void bidUnconfirmed(BigDecimal amount) {
        bidPending = false; uncertainBidAmount = amount;
        if (bidTimeout != null) { bidTimeout.cancel(false); bidTimeout = null; }
        feedback("L\u01b0\u1ee3t " + money(amount) + " ch\u01b0a x\u00e1c nh\u1eadn. Kh\u00f4ng t\u1ef1 g\u1eedi l\u1ea1i; \u0111ang \u0111\u1ed3ng b\u1ed9. Ki\u1ec3m tra l\u1ecbch s\u1eed tr\u01b0\u1edbc khi \u0111\u1eb7t ti\u1ebfp.", "warning");
        roomSnapshotReady = false;
        refreshBidControls();
        synchronizeRoom();
    }

    private String bidError(ApiResponse response) {
        return switch (response.getErrorCode()) {
            case "BID_TOO_LOW", "DATABASE_CONFLICT" -> "Gi\u00e1 \u0111\u00e3 thay \u0111\u1ed5i. \u0110ang l\u1ea5y gi\u00e1 t\u1ed1i thi\u1ec3u m\u1edbi; h\u00e3y ki\u1ec3m tra r\u1ed3i \u0111\u1eb7t l\u1ea1i.";
            case "INSUFFICIENT_FUNDS" -> "S\u1ed1 d\u01b0 kh\u1ea3 d\u1ee5ng kh\u00f4ng \u0111\u1ee7. H\u00e3y n\u1ea1p v\u00ed demo v\u00e0 ch\u1edd duy\u1ec7t.";
            case "BID_ALREADY_LEADING" -> "B\u1ea1n \u0111ang d\u1eabn \u0111\u1ea7u, kh\u00f4ng th\u1ec3 t\u1ef1 n\u00e2ng gi\u00e1.";
            case "BID_AFTER_END", "AUCTION_NOT_OPEN", "AUCTION_NOT_FOUND" -> "Phi\u00ean \u0111\u00e3 \u0111\u00f3ng ho\u1eb7c h\u1ebft th\u1eddi gian nh\u1eadn gi\u00e1. Ch\u1edd server c\u1eadp nh\u1eadt.";
            case "AUCTION_FORBIDDEN" -> "Ch\u1ee7 ph\u00f2ng kh\u00f4ng \u0111\u01b0\u1ee3c \u0111\u1eb7t gi\u00e1 s\u1ea3n ph\u1ea9m c\u1ee7a m\u00ecnh.";
            case "USER_BLOCKED_FROM_AUCTION", "NOT_IN_AUCTION_ROOM", "ROOM_ACCESS_DENIED" -> "B\u1ea1n ch\u01b0a c\u00f3 quy\u1ec1n \u0111\u1eb7t gi\u00e1 t\u1ea1i ph\u00f2ng n\u00e0y.";
            case "INVALID_AMOUNT" -> "Gi\u00e1 kh\u00f4ng h\u1ee3p l\u1ec7: s\u1ed1 d\u01b0\u01a1ng, t\u1ed1i \u0111a 2 s\u1ed1 th\u1eadp ph\u00e2n v\u00e0 trong gi\u1edbi h\u1ea1n.";
            default -> "Server ch\u01b0a ch\u1ea5p nh\u1eadn l\u01b0\u1ee3t gi\u00e1 (" + response.getErrorCode() + "). H\u00e3y \u0111\u1ed3ng b\u1ed9 v\u00e0 ki\u1ec3m tra l\u1ea1i.";
        };
    }

    private void placeBid() {
        ClientAuction auction = model.joinedAuction();
        if (bidPending) { return; }
        refreshBidControls();
        if (auction == null || bidButton.isDisabled() || !roomReady()) {
            feedback("Ch\u01b0a th\u1ec3 \u0111\u1eb7t gi\u00e1. Ki\u1ec3m tra tr\u1ea1ng th\u00e1i ph\u00f2ng v\u00e0 \u0111\u1ed3ng b\u1ed9 d\u1eef li\u1ec7u.", "warning"); return;
        }
        BigDecimal amount;
        try {
            amount = Money.parseInput(bidField.getText());
            if (amount.compareTo(maxBidAmount) > 0) {
                feedback("Gi\u00e1 kh\u00f4ng \u0111\u01b0\u1ee3c v\u01b0\u1ee3t " + money(maxBidAmount) + ".", "error"); return;
            }
            BigDecimal minimum = auction.getCurrentPrice().add(auction.getMinBidIncrement());
            if (amount.compareTo(minimum) < 0) {
                feedback("Gi\u00e1 t\u1ed1i thi\u1ec3u hi\u1ec7n t\u1ea1i l\u00e0 " + money(minimum) + ". Ki\u1ec3m tra gi\u00e1 m\u1edbi tr\u01b0\u1edbc khi g\u1eedi.", "error"); return;
            }
        } catch (IllegalArgumentException | ArithmeticException exception) {
            feedback("Nh\u1eadp s\u1ed1 ti\u1ec1n d\u01b0\u01a1ng, t\u1ed1i \u0111a 2 s\u1ed1 th\u1eadp ph\u00e2n. V\u00ed d\u1ee5: 1.234,56 ho\u1eb7c 1234,56.", "error"); return;
        }
        RoomRequest request = roomRequest(auction.getAuctionId());
        long sequence = ++bidRequestSequence;
        bidPending = true; uncertainBidAmount = null;
        bidField.setText(Money.input(amount));
        feedback("\u0110ang g\u1eedi " + money(amount) + ". Ch\u1edd server x\u00e1c nh\u1eadn, kh\u00f4ng b\u1ea5m l\u1ea1i.", "pending");
        refreshBidControls();
        bidTimeout = scheduler.schedule(() -> Platform.runLater(() -> {
            if (current(request, true) && sequence == bidRequestSequence && bidPending) { bidUnconfirmed(amount); }
        }), config.getRequestTimeoutMillis(), TimeUnit.MILLISECONDS);
        auctions.bid(auction.getAuctionId(), amount).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!current(request, true) || sequence != bidRequestSequence) { return; }
            if (bidTimeout != null) { bidTimeout.cancel(false); bidTimeout = null; }
            if (error != null || response == null || switch (response.getErrorCode()) {
                case "REQUEST_TIMEOUT", "SERVER_UNAVAILABLE", "INTERNAL_ERROR", "DATABASE_ERROR" -> true;
                default -> false;
            }) { bidUnconfirmed(amount); return; }
            bidPending = false;
            uncertainBidAmount = null;
            if (response.isSuccess()) {
                if (!Long.toString(request.auctionId()).equals(response.get("auctionId"))) { bidUnconfirmed(amount); return; }
                applyBidUpdate(response.getWireMessage().getData());
                lastRoomSignal = System.nanoTime();
                bidField.clear();
                bidConfirmedSound(request.auctionId(), amount);
                feedback("\u0110\u00e3 ch\u1ea5p nh\u1eadn " + money(amount) + ". Theo d\u00f5i gi\u00e1 m\u1edbi; ch\u01b0a ph\u1ea3i k\u1ebft qu\u1ea3 th\u1eafng.", "success");
                renderAuctions();
            } else {
                feedback(bidError(response), "error");
                synchronizeRoom();
            }
            refreshBidControls();
        }));
    }

    private void bidConfirmedSound(long room, BigDecimal amount) {
        sounds.playOnce(ClientSounds.Cue.BID_ACCEPTED, "bid:" + room + ":" + amount.stripTrailingZeros().toPlainString());
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
        RoomRequest request = roomRequest(id);
        BidHistoryPane history = new BidHistoryPane(transport, id, () -> current(request, false));
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Lịch sử bid phòng #" + id);
        dialog.setResizable(true); dialog.getDialogPane().setContent(history);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setOnHidden(event -> history.dispose()); showDialog(dialog);
    }

    private void extendAuction() {
        ClientAuction selected = requireHostedAuction();
        if (selected == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog("60");
        dialog.setTitle("Gia hạn");
        dialog.setHeaderText("Số giây gia hạn (10-600)");
        RoomRequest request = roomRequest(selected.getAuctionId());
        showDialog(dialog).ifPresent(value -> {
            if (!current(request, true) || !roomReady()) { return; }
            try {
                auctions.extendAuction(selected.getAuctionId(), Integer.parseInt(value.trim()))
                        .whenComplete((response, error) -> Platform.runLater(() -> {
                            if (!current(request, true)) { return; }
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
            RoomRequest request = roomRequest(selected.getAuctionId());
            if (!current(request, true) || !roomReady()) { return; }
            auctions.endAuction(selected.getAuctionId()).whenComplete((response, error) ->
                    Platform.runLater(() -> {
                        if (!current(request, true)) { return; }
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
            RoomRequest request = roomRequest(selected.getAuctionId());
            if (!current(request, true) || !roomReady()) { return; }
            auctions.cancelAuction(selected.getAuctionId()).whenComplete((response, error) ->
                    Platform.runLater(() -> {
                        if (!current(request, true)) { return; }
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
        if (selected.hasAnonymousGuests()) {
            if (roomMode) { socialRoom.chooseKick(); }
            else { showError("V\u00e0o ph\u00f2ng \u0111\u1ec3 ch\u1ecdn kh\u00e1ch theo s\u1ed1 th\u1ee9 t\u1ef1."); }
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Kick user");
        dialog.setHeaderText("Username cần mời khỏi phòng");
        RoomRequest request = roomRequest(selected.getAuctionId());
        showDialog(dialog).ifPresent(username -> auctions
                .kickUser(selected.getAuctionId(), username)
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (!current(request, true)) { return; }
                    if (success(response, error)) {
                        appendEvent(response.getMessageText().isBlank()
                                ? "Đã kick " + username : response.getMessageText());
                    }
                })));
    }

    private void showProfile() {
        RoomRequest request = roomRequest(0);
        accounts.getProfile().whenComplete((response, error) -> Platform.runLater(() -> {
            if (!current(request, false) || !success(response, error)) {
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
            Button publicProfile = new Button("Avatar v\u00e0 gi\u1edbi thi\u1ec7u c\u00f4ng khai");
            publicProfile.setOnAction(event -> socialRoom.editOwnProfile());
            addRow(grid, 5, "H\u1ed3 s\u01a1 c\u00f4ng khai", publicProfile);
            Label privacy = new Label("Email, dien thoai, vi va session khong xuat hien trong ho so cong khai. Xem truoc tai muc Avatar va gioi thieu.");
            privacy.setWrapText(true); privacy.setMaxWidth(350); addRow(grid, 6, "Quyen rieng tu", privacy);
            addRow(grid, 7, "Thong tin rieng", new Label("Chi ban va nguoi van hanh duoc xem email/dien thoai."));
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
                        if (current(request, false) && success(updated, updateError)) {
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
            restorationHint.setText((selected == null
                    ? "Chon mat hang de xem dieu kien khoi phuc." : restorationDescription(selected))
                    + "\nDu lieu va anh bi khoa vinh vien sau khi tao phien dau tien, ke ca huy/khong bid, de giu nguyen noi dung lich su. Server tu choi sua; khoi phuc khong mo khoa.");
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
        if (creatingAuction) { return; }
        creatingAuction = true; if (createAuctionAction != null) { createAuctionAction.setDisable(true); }
        RoomRequest request = roomRequest(0);
        auctions.myProducts().whenComplete((response, error) -> Platform.runLater(() -> {
            if (!current(request, false) || !success(response, error)) {
                finishCreatingAuction();
                return;
            }
            List<ClientProduct> products = ClientWireParser.products(response.getWireMessage().getData())
                    .stream().filter(ClientProduct::isActive).toList();
            if (products.isEmpty()) {
                finishCreatingAuction();
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
            ComboBox<String> guestMode = new ComboBox<>(FXCollections.observableArrayList(
                    "Kh\u00e1ch th\u01b0\u1eddng", "Kh\u00e1ch \u1ea9n danh"));
            guestMode.getSelectionModel().selectFirst();
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
            addRow(grid, 7, "Danh t\u00ednh kh\u00e1ch", guestMode);
            Label privacyHelp = new Label("\u1ea8n danh: ch\u1ec9 hi\u1ec7n Kh\u00e1ch #1, #2...; avatar tr\u1ed1ng v\u00e0 kh\u00f4ng xem h\u1ed3 s\u01a1. Kh\u00f4ng th\u1ec3 \u0111\u1ed5i ch\u1ebf \u0111\u1ed9 sau khi t\u1ea1o.");
            privacyHelp.setWrapText(true); privacyHelp.setMaxWidth(330);
            addRow(grid, 8, "L\u01b0u \u00fd", privacyHelp);
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(
                    new ButtonType("Xem lai", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
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
                            privateRoom.isSelected() ? roomPassword.getText() : "",
                            guestMode.getSelectionModel().getSelectedIndex() == 1);
                } catch (IllegalArgumentException exception) {
                    showError("Giá hoặc thời lượng không hợp lệ");
                    return null;
                }
            });
            var chosen = showDialog(dialog);
            if (chosen.isEmpty()) { finishCreatingAuction(); return; }
            var data = chosen.get();
            ClientProduct selected = product.getValue();
            String review = "San pham: " + selected.getCode() + " - " + selected.getName()
                    + "\nGia khoi diem: " + money(data.startPrice) + "\nBuoc gia: " + money(data.increment)
                    + "\nThoi luong: " + data.durationMinutes + " phut\nQuyen vao: " + data.visibility
                    + "\nDanh tinh: " + (data.anonymousGuests ? "Khach an danh" : "Tai khoan thuong")
                    + "\n\nKhong doi san pham, quyen vao va che do danh tinh sau khi tao."
                    + "\nDu lieu/anh san pham bi khoa tu phien dau tien de bao toan lich su, ke ca phien huy."
                    + "\nServer kiem tra gia, thoi luong va quyen truoc khi tao. Xac nhan tao phong?";
            if (!confirm(review) || !current(request, false)) { finishCreatingAuction(); return; }
            auctions.createAuction(
                            data.productId, data.startPrice, data.increment, data.durationMinutes,
                            data.visibility, data.roomPassword, data.anonymousGuests)
                    .whenComplete((created, createError) -> Platform.runLater(() -> {
                        finishCreatingAuction();
                        if (!current(request, false)) { return; }
                        if (success(created, createError)) {
                            applySnapshot(created, false);
                            renderAuctions();
                            appendEvent("Đã tạo phòng #" + created.get("auctionId"));
                        }
                        else if (createError != null) { loadAuctionList(); showError("Chua xac nhan tao phong. Hay kiem tra sanh/phien cua toi truoc khi thu lai."); }
                    }));
        }));
    }

    private void finishCreatingAuction() { creatingAuction = false; if (createAuctionAction != null) { createAuctionAction.setDisable(false); } }

    private void logout() {
        autoReconnect = false;
        connectionGeneration++;
        if (reconnectTask != null) { reconnectTask.cancel(false); }
        invalidateRoom(true); roomChanging = true;
        RoomRequest request = roomRequest(0);
        refreshBidControls();
        accounts.logout().whenComplete((response, error) -> Platform.runLater(() -> {
            if (!current(request, false)) { return; }
            roomChanging = false;
            if (error == null && response != null && response.isSuccess()) {
                model.clearIdentity();
                socialRoom.clear();
                auctionTable.getItems().clear();
                stage.setScene(loginScene);
                appendEvent("Đã đăng xuất");
            } else {
                success(response, error);
                synchronizeRoom();
            }
        }));
    }

    private void onConnectionState(ConnectionState state, String detail) {
        model.setConnectionState(state, detail);
        Platform.runLater(() -> {
            if (closed.get() || transport.getState() != state) {
                return;
            }
            roomConnectionGeneration++;
            if (state == ConnectionState.CONNECTED) { soundConnected = true; }
            else if (state == ConnectionState.DISCONNECTED || state == ConnectionState.CLOSED) {
                if (soundConnected && model.hasSessionToken() && autoReconnect) { sounds.play(ClientSounds.Cue.WARNING); }
                soundConnected = false;
            }
            if (bidPending) {
                bidPending = false;
                try { uncertainBidAmount = Money.parseInput(bidField.getText()); }
                catch (RuntimeException ignored) { }
                feedback("L\u01b0\u1ee3t g\u1eedi tr\u01b0\u1edbc ch\u01b0a x\u00e1c nh\u1eadn. Kh\u00f4ng t\u1ef1 g\u1eedi l\u1ea1i; ch\u1edd k\u1ebft n\u1ed1i v\u00e0 \u0111\u1ed3ng b\u1ed9.", "warning");
            }
            invalidateRoom(false);
            roomChanging = false;
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
        String token = model.getSessionToken();
        long connection = roomConnectionGeneration;
        Platform.runLater(() -> {
            if (!closed.get() && connection == roomConnectionGeneration && token.equals(model.getSessionToken())
                    && (model.hasSessionToken() || message.getType() == MessageType.CONNECTION_WELCOME)) {
                handleServerEvent(message);
            }
        });
    }

    private void handleServerEvent(WireMessage message) {
        Map<String, String> data = message.getData();
        switch (message.getType()) {
            case ROOM_CHAT_MESSAGE -> socialRoom.onMessage(data);
            case ROOM_INTERACTION_CHANGED -> socialRoom.onInteraction(data);
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
                if (java.util.Objects.equals(model.getJoinedAuctionId(), ClientWireParser.longValue(data, "auctionId", 0L))) {
                    lastRoomSignal = System.nanoTime();
                }
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
                if (java.util.Objects.equals(model.getJoinedAuctionId(), auction.getAuctionId())) {
                    sounds.playOnce(ClientSounds.Cue.AUCTION_END, "ended:" + auction.getAuctionId());
                    lastRoomSignal = System.nanoTime(); roomWalletChanged();
                    showConfirmedReceipt(auction.getAuctionId());
                }
                renderAuctions();
                refreshWallet();
                appendEvent("Phòng #" + auction.getAuctionId() + " đã kết thúc");
            }
            case AUCTION_ARCHIVED -> {
                if (java.util.Objects.equals(model.getJoinedAuctionId(), ClientWireParser.longValue(data, "auctionId", 0L))) {
                    showConfirmedReceipt(ClientWireParser.longValue(data, "auctionId", 0L));
                    invalidateRoom(true);
                }
                model.removeAuction(ClientWireParser.longValue(data, "auctionId", 0L),
                        ClientWireParser.serverNow(data));
                renderAuctions();
                appendEvent("Phòng #" + data.getOrDefault("auctionId", "") + " đã được lưu trữ");
            }
            case AUCTION_KICKED -> {
                long auctionId = ClientWireParser.longValue(data, "auctionId", 0L);
                if (java.util.Objects.equals(model.getJoinedAuctionId(), auctionId)) { invalidateRoom(true); }
                model.markKicked(auctionId);
                renderAuctions();
                showError(data.getOrDefault("message", "Bạn đã bị kick khỏi phòng"));
            }
            case OUTBID_NOTIFICATION -> {
                ClientAuction joined = model.joinedAuction();
                if (transport.isConnected() && joined != null && joined.isOpen()
                        && joined.getAuctionId() == ClientWireParser.longValue(data, "auctionId", 0)
                        && ClientWireParser.decimal(data, "currentPrice").compareTo(joined.getCurrentPrice()) >= 0) {
                    sounds.playOnce(ClientSounds.Cue.OUTBID, "outbid:" + joined.getAuctionId() + ":" + data.get("currentPrice"));
                }
                appendEvent("BẠN ĐÃ BỊ VƯỢT GIÁ: " + data.getOrDefault("productName", ""));
            }
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
        ClientAuction confirmed = ClientWireParser.auction(data, "");
        if (!confirmed.isOpen() && java.util.Objects.equals(model.getJoinedAuctionId(), confirmed.getAuctionId())) {
            showConfirmedReceipt(confirmed.getAuctionId());
        }
    }

    private void showConfirmedReceipt(long room) {
        if (shownReceipts.add(model.getSessionToken() + ":" + room)) { showPersonalReceipt(room); }
    }

    private void showPersonalReceipt(long room) {
        long userId = model.getUserId(); String token = model.getSessionToken();
        java.util.function.BooleanSupplier sameSession = () -> model.getUserId() == userId && model.getSessionToken().equals(token);
        new AuctionReceiptsPane(stage, transport, sameSession,
                () -> new WalletPane(stage, new WalletApi(transport), userId, model.getUsername(), sameSession).show()).show(room);
    }

    private void applyBidUpdate(Map<String, String> data) {
        model.applyAuctionUpdate(
                ClientWireParser.auction(data, ""),
                ClientWireParser.bid(data, "bid."),
                ClientWireParser.serverNow(data));
        if (java.util.Objects.equals(model.getJoinedAuctionId(), ClientWireParser.longValue(data, "auctionId", 0L))) {
            lastRoomSignal = System.nanoTime();
            confirmUncertainBid();
            roomWalletChanged();
        }
    }

    private void renderAuctions() {
        var snapshot = model.auctionSnapshot().stream().filter(value -> value.isOpen()
                || java.util.Objects.equals(model.getJoinedAuctionId(), value.getAuctionId())).toList();
        roomCount.setText(Integer.toString(snapshot.size()));
        openCount.setText(Long.toString(snapshot.stream().filter(ClientAuction::isOpen).count()));
        joinedRoom.setText(model.getJoinedAuctionId() == null ? "—" : "#" + model.getJoinedAuctionId());
        ClientAuction selected = auctionTable.getSelectionModel().getSelectedItem();
        long selectedId = selected == null ? 0L : selected.getAuctionId();
        ClientAuction activeRoom = model.joinedAuction();
        long preferredId = activeRoom != null ? activeRoom.getAuctionId() : selectedId;
        renderingAuctions = true;
        try {
            var filtered = vn.ptit.btl16.client.model.LobbyQuery.filter(snapshot, model.getUserId(),
                    lobbyVisibility.getValue() == null ? "ALL" : lobbyVisibility.getValue(),
                    lobbyIdentity.getValue() == null ? "ALL" : lobbyIdentity.getValue(), lobbyHosted.isSelected(),
                    searchMode.getValue() == null ? "ALL" : searchMode.getValue(), searchField.getText(),
                    lobbyOrder.getValue() == null ? "ENDING" : lobbyOrder.getValue(), model.serverNow());
            int pages = Math.max(1, (filtered.size() + 29) / 30); lobbyPage = Math.min(lobbyPage, pages - 1);
            lobbyPageLabel.setText((lobbyPage + 1) + "/" + pages + " - " + filtered.size() + " phien");
            lobbyPrevious.setDisable(lobbyPage == 0); lobbyNext.setDisable(lobbyPage + 1 >= pages);
            auctionTable.getItems().setAll(activeRoom != null ? List.of(activeRoom)
                    : vn.ptit.btl16.client.model.LobbyQuery.page(filtered, lobbyPage, 30));
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
        detailVisibility.setText((!auction.isOpen() ? "ĐÃ KẾT THÚC" :
                auction.requiresPassword() ? "PHÒNG RIÊNG TƯ" : "PHÒNG CÔNG KHAI")
                + (auction.hasAnonymousGuests() ? " \u00b7 KH\u00c1CH \u1ea8N DANH" : ""));
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
        socialRoom.tick();
        ClientAuction joined = model.joinedAuction();
        if (transport.isConnected() && roomSnapshotReady && joined != null && joined.isOpen() && joined.getEndTime() != null) {
            long remaining = java.time.Duration.between(model.serverNow(), joined.getEndTime()).toMillis();
            if (remaining > 0 && remaining <= 10_000) {
                sounds.playOnce(ClientSounds.Cue.WARNING, "ending:" + joined.getAuctionId());
            }
        }
        long now = System.nanoTime();
        if (now - lastWalletRefresh > TimeUnit.SECONDS.toNanos(4)) {
            lastWalletRefresh = now; refreshWallet(); refreshRoomWallet();
        }
        if (model.getJoinedAuctionId() != null && roomSnapshotReady && !bidPending && !roomChanging
                && now - lastRoomSignal >= ROOM_FRESH_NANOS) { synchronizeRoom(); }
        refreshBidControls();
        auctionTable.refresh();
        ClientAuction selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (roomMode) { liveRoom.tick(model.serverNow(), transport.isConnected()); }
            if (!selected.isOpen() && selected.getEndedAt() != null
                    && !model.serverNow().isBefore(selected.getEndedAt().plusSeconds(60))) {
                invalidateRoom(true);
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
        boolean available = auction != null && auction.isOpen() && transport.isConnected()
                && auction.getEndTime() != null && model.serverNow().isBefore(auction.getEndTime())
                && auction.getStartTime() != null && !model.serverNow().isBefore(auction.getStartTime());
        boolean hosted = auction != null && auction.isHostedBy(model.getUserId());
        joinButton.setDisable(!available || joined || roomChanging);
        leaveButton.setDisable(model.getJoinedAuctionId() == null || roomChanging || !transport.isConnected());
        boolean leading = auction != null && java.util.Objects.equals(auction.getCurrentWinnerId(), auction.viewerId(model.getUserId()));
        boolean noNextPrice = auction != null && maxBidAmount != null
                && auction.getCurrentPrice().add(auction.getMinBidIncrement()).compareTo(maxBidAmount) > 0;
        bidButton.setText(bidPending ? "\u0110ang g\u1eedi..." : "\u0110\u1eb7t gi\u00e1");
        bidButton.setDisable(!available || !joined || hosted || leading || bidPending || !roomReady() || noNextPrice);
        bidField.setDisable(bidButton.isDisabled());
        syncRoomButton.setDisable(!joined || !transport.isConnected() || roomChanging || roomSyncing || bidPending);
        historyButton.setDisable(auction == null || roomChanging || !transport.isConnected());
        managementPane.setVisible(hosted); managementPane.setManaged(hosted);
        joinedRoom.setText(model.getJoinedAuctionId() == null ? "\u2014" : "#" + model.getJoinedAuctionId());
        detailVisibility.setVisible(auction != null);
        boolean disabled = !available || !hosted || !joined || !roomReady();
        boolean hasBids = auction != null && auction.getCurrentWinnerId() != null;
        boolean extensionLimit = auction != null && auction.getHardEndTime() != null
                && !auction.getEndTime().isBefore(auction.getHardEndTime());
        extendButton.setDisable(disabled || hasBids || extensionLimit);
        endButton.setDisable(disabled || hasBids);
        cancelButton.setDisable(disabled || hasBids);
        kickButton.setDisable(disabled);
        hostLockReason.setText(!joined ? "V\u00e0o ph\u00f2ng \u0111\u1ec3 \u0111\u1ed3ng b\u1ed9 v\u00e0 qu\u1ea3n l\u00fd."
                : !available ? "Phi\u00ean \u0111\u00e3 \u0111\u00f3ng/h\u1ebft gi\u1edd ho\u1eb7c m\u1ea5t k\u1ebft n\u1ed1i. Kh\u00f4ng th\u1ec3 qu\u1ea3n l\u00fd."
                : !roomReady() ? "\u0110ang ch\u1edd \u0111\u1ed3ng b\u1ed9 d\u1eef li\u1ec7u h\u1ee3p l\u1ec7; c\u00e1c n\u00fat qu\u1ea3n l\u00fd t\u1ea1m kh\u00f3a."
                : hasBids ? "\u0110\u00e3 c\u00f3 l\u01b0\u1ee3t h\u1ee3p l\u1ec7: kh\u00f4ng \u0111\u01b0\u1ee3c gia h\u1ea1n th\u1ee7 c\u00f4ng, k\u1ebft th\u00fac s\u1edbm ho\u1eb7c h\u1ee7y. Ch\u1ec9 m\u1eddi ng\u01b0\u1eddi ch\u01b0a t\u1eebng tr\u1ea3 gi\u00e1 ra kh\u1ecfi ph\u00f2ng."
                : extensionLimit ? "\u0110\u00e3 ch\u1ea1m h\u1ea1n cu\u1ed1i: kh\u00f4ng th\u1ec3 gia h\u1ea1n th\u00eam."
                : "Ch\u1ec9 \u0111\u01b0\u1ee3c gia h\u1ea1n, k\u1ebft th\u00fac s\u1edbm, h\u1ee7y khi ch\u01b0a c\u00f3 l\u01b0\u1ee3t h\u1ee3p l\u1ec7. Server ki\u1ec3m tra l\u1ea1i m\u1ecdi thao t\u00e1c.");
        updateRoomLayout(joined);
        renderRoomActivity(auction, joined, hosted, leading);
    }

    private void updateRoomLayout(boolean joined) {
        if (roomMode == joined) { return; }
        roomMode = joined;
        liveRoom.insights().setVisible(joined); liveRoom.insights().setManaged(joined);
        if (!joined) { liveRoom.clear(); socialRoom.clear(); }
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
            productDetails.getChildren().clear();
            roomProductIdentity.getChildren().setAll(productHeader, detailName, detailHost);
            HBox.setHgrow(roomProductIdentity, Priority.ALWAYS);
            roomProductIdentity.setMinWidth(0);
            roomProductSummary.setAlignment(Pos.CENTER_LEFT);
            roomProductSummary.getChildren().setAll(roomImage, roomProductIdentity);
            ClientAuction receiptAuction = model.joinedAuction();
            if (receiptAuction != null && !receiptAuction.isOpen()) {
                Button receipt = new Button("Xem bi\u00ean nh\u1eadn c\u1ee7a t\u00f4i");
                receipt.setId("room-personal-receipt"); receipt.setOnAction(event -> showPersonalReceipt(receiptAuction.getAuctionId()));
                roomProductSummary.getChildren().add(receipt);
            }
            socialRoom.setAuctionPanels(liveRoom.insights(), new VBox(12, productDescription, managementPane));
            roomMain.getChildren().setAll(roomProductSummary, socialRoom);
            priceBlock.getChildren().clear();
            GridPane compactPrice = new GridPane(); compactPrice.setHgap(8); compactPrice.setVgap(4);
            javafx.scene.layout.ColumnConstraints priceColumn = new javafx.scene.layout.ColumnConstraints(); priceColumn.setPercentWidth(52);
            javafx.scene.layout.ColumnConstraints timingColumn = new javafx.scene.layout.ColumnConstraints(); timingColumn.setPercentWidth(48);
            compactPrice.getColumnConstraints().setAll(priceColumn, timingColumn);
            detailTime.setWrapText(true);
            compactPrice.add(priceCaption, 0, 0); compactPrice.add(detailPrice, 0, 1);
            compactPrice.add(detailTime, 1, 0); compactPrice.add(detailLeader, 1, 1);
            priceBlock.getChildren().add(compactPrice);
            bidding.getChildren().add(0, priceBlock);
            bidding.setSpacing(5);
            bidding.setMinHeight(Region.USE_PREF_SIZE);
            bidding.getStyleClass().setAll("bid-card"); bidding.setPadding(new Insets(12));
            productDetails.getStyleClass().add("room-product");
            roomBidScroll.setContent(bidding);
            roomSide.getChildren().add(0, roomBidScroll);
            roomColumns.getChildren().setAll(roomMain, roomSide);
            detailPanel.setCenter(roomColumns);
            detailsScroll.setVvalue(0); roomControlsScroll.setVvalue(0);
        } else {
            roomMain.getChildren().clear(); socialRoom.setAuctionPanels(null, null);
            roomColumns.getChildren().clear(); roomSide.getChildren().remove(roomBidScroll);
            roomBidScroll.setContent(null);
            bidding.getChildren().remove(priceBlock);
            priceBlock.getChildren().setAll(priceCaption, detailPrice, detailTime, detailLeader);
            roomProductSummary.getChildren().clear();
            roomProductIdentity.getChildren().clear();
            productDetails.getChildren().setAll(productHeader, detailName, detailHost, roomImage,
                    priceBlock, liveRoom.insights(), productDescription, managementPane);
            bidding.setMinHeight(Region.USE_COMPUTED_SIZE);
            bidding.setSpacing(10);
            bidding.getStyleClass().setAll("card"); bidding.setPadding(new Insets(16));
            productDetails.getStyleClass().remove("room-product");
            detailPanel.setCenter(detailsScroll); detailPanel.setBottom(bidding);
            listingSplit.getItems().add(0, listingPanel); listingSplit.setDividerPositions(0.60);
            pageTitle.setText("Sàn đấu giá"); pageEyebrow.setText("KHÁM PHÁ & ĐẤU GIÁ");
        }
    }

    private void renderRoomActivity(ClientAuction auction, boolean joined, boolean hosted, boolean leading) {
        roomFunds.setVisible(joined); roomFunds.setManaged(joined);
        roomRules.setVisible(joined); roomRules.setManaged(joined);
        roomConnection.getParent().setVisible(joined); roomConnection.getParent().setManaged(joined);
        if (!joined || auction == null) { return; }
        pageTitle.setText("Ph\u00f2ng \u0111\u1ea5u gi\u00e1 #" + auction.getAuctionId());
        pageEyebrow.setText(hosted ? "KH\u00d4NG GIAN CH\u1ee6 PH\u00d2NG" : "PHI\u00caN \u0110\u1ea4U GI\u00c1 C\u1ee6A B\u1ea0N");
        BigDecimal minimum = auction.getCurrentPrice().add(auction.getMinBidIncrement());
        bidField.setPromptText("T\u1ed1i thi\u1ec3u " + money(minimum));
        String connection = !transport.isConnected()
                ? autoReconnect ? "\u0110ang k\u1ebft n\u1ed1i l\u1ea1i \u00b7 D\u1eef li\u1ec7u c\u0169" : "M\u1ea5t k\u1ebft n\u1ed1i \u00b7 D\u1eef li\u1ec7u c\u0169"
                : roomChanging || roomSyncing || roomWalletLoading && roomAvailable == null
                ? "\u0110\u00e3 k\u1ebft n\u1ed1i \u00b7 \u0110ang \u0111\u1ed3ng b\u1ed9"
                : !roomReady() ? "\u0110\u00e3 k\u1ebft n\u1ed1i \u00b7 D\u1eef li\u1ec7u c\u0169/ch\u01b0a \u0111\u1ee7"
                : "\u0110\u00e3 k\u1ebft n\u1ed1i \u00b7 \u0110\u00e3 \u0111\u1ed3ng b\u1ed9";
        roomConnection.setText(connection);
        liveRoom.setDataState(!transport.isConnected() ? autoReconnect ? "RECONNECTING" : "OFFLINE"
                : roomChanging || roomSyncing || roomWalletLoading && roomAvailable == null ? "SYNCING"
                : !roomReady() ? "STALE" : "READY");
        String lock = !auction.isOpen() ? "Phi\u00ean \u0111\u00e3 \u0111\u00f3ng."
                : !model.serverNow().isBefore(auction.getEndTime()) ? "H\u1ebft gi\u1edd nh\u1eadn gi\u00e1; ch\u1edd server ch\u1ed1t."
                : hosted ? "Ch\u1ee7 ph\u00f2ng kh\u00f4ng \u0111\u01b0\u1ee3c \u0111\u1eb7t gi\u00e1."
                : leading ? "B\u1ea1n \u0111ang d\u1eabn \u0111\u1ea7u, kh\u00f4ng th\u1ec3 t\u1ef1 n\u00e2ng gi\u00e1."
                : maxBidAmount != null && minimum.compareTo(maxBidAmount) > 0 ? "\u0110\u00e3 ch\u1ea1m gi\u1edbi h\u1ea1n gi\u00e1." : "";
        bidHint.setText("T\u1ed1i thi\u1ec3u " + money(minimum) + " \u00b7 B\u01b0\u1edbc " + money(auction.getMinBidIncrement())
                + (lock.isBlank() ? "" : "\n" + lock));
        roomFunds.setText("Kh\u1ea3 d\u1ee5ng: " + (roomAvailable == null ? "ch\u01b0a \u0111\u1ed3ng b\u1ed9" : money(roomAvailable))
                + " \u00b7 Gi\u1eef phi\u00ean n\u00e0y: " + (roomHeld == null ? "ch\u01b0a \u0111\u1ed3ng b\u1ed9" : money(roomHeld)));
        for (int index = 0; index < quickBidButtons.size(); index++) {
            Button suggestion = quickBidButtons.get(index);
            BigDecimal delta = auction.getMinBidIncrement().multiply(BigDecimal.valueOf(new int[]{1, 3, 5}[index]));
            suggestion.setText("+" + money(delta));
            suggestion.setDisable(bidButton.isDisabled() || maxBidAmount == null
                    || auction.getCurrentPrice().add(delta).compareTo(maxBidAmount) > 0);
        }
        socialRoom.setRoom(auction, model.getUserId());
        liveRoom.update(auction, model.currentBidSnapshot(), auction.viewerId(model.getUserId()),
                transport.isConnected(), model.serverNow());
    }

    private void updateRoomImageSize() {
        double height = roomMode ? 105 : 160;
        roomImage.setMinHeight(roomMode ? 105 : 150);
        roomImage.setMinWidth(roomMode ? 115 : 0);
        roomImage.setPrefWidth(roomMode ? 135 : 300);
        roomImage.setMaxWidth(roomMode ? 145 : Double.MAX_VALUE);
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
        sounds.play(ClientSounds.Cue.ERROR);
        Alert alert = new Alert(Alert.AlertType.ERROR, message == null ? "Lỗi không xác định" : message);
        alert.setHeaderText("Auction");
        showDialog(alert);
    }

    private void showInfo(String message) {
        sounds.play(ClientSounds.Cue.SUCCESS);
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
        invalidateRoom(true);
        clock.stop();
        if (soundUi != null) { soundUi.close(); }
        sounds.close();
        socialRoom.clear();
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
        private final boolean anonymousGuests;

        private AuctionFormData(
                long productId,
                BigDecimal startPrice,
                BigDecimal increment,
                int durationMinutes,
                String visibility,
                String roomPassword, boolean anonymousGuests) {
            this.productId = productId;
            this.startPrice = startPrice;
            this.increment = increment;
            this.durationMinutes = durationMinutes;
            this.visibility = visibility;
            this.roomPassword = roomPassword;
            this.anonymousGuests = anonymousGuests;
        }
    }
}
