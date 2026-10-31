package vn.ptit.btl16.client.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.util.ConnectionCode;
import vn.ptit.btl16.common.util.Money;
import vn.ptit.btl16.common.util.Times;
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
import vn.ptit.btl16.client.service.ClientMessages;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.client.service.ProductMediaApi;
import vn.ptit.btl16.client.service.WalletApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.ProductTransactionStatus;
import vn.ptit.btl16.common.protocol.WireMessage;

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
    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
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
    private final ClientBackgroundMusic backgroundMusic;
    private final ClientBidVoice bidVoice;
    private final FriendsPane friendsPane;
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
    private final Label loginErrorLabel = new Label();
    private final HBox loginErrorBanner = new HBox(10);
    private final Label loginStateLabel = new Label("● Chưa kết nối");
    private final Label loginRttLabel = new Label("— ms");
    private final Button loginButton = new Button("Đăng nhập");
    private final Button registerButton = new Button("Đăng ký");
    private final Button connectButton = new Button("Kết nối lại");

    private final Label userLabel = new Label("Tài khoản");
    private VBox accountPageHost, auctionWorkspace;
    private ScrollPane accountPageScroll;
    private Button pageBack;
    private boolean accountPageVisible;
    private Label accountBreadcrumb;
    private Button marketplaceNavigation, walletNavigation, transactionsNavigation, receiptsNavigation, productsNavigation, rulesNavigation;
    private WalletPane embeddedWallet;
    private TransactionsPane embeddedTransactions;
    private AuctionReceiptsPane embeddedReceipts;
    private SellerProductsPane embeddedProducts;
    private AuctionRulesPane embeddedRules;
    private final Label walletBalance = DesktopTheme.label("Ví: đang đồng bộ…", "muted");
    private long lastWalletRefresh;
    private boolean walletLoading;
    private long walletRequestSequence;
    private Button notificationBell;
    private final Label notificationCount = new Label();
    private long notificationRequestSequence, lastNotificationRefresh;
    private boolean notificationsLoading;
    private String notificationSession = "";
    private long notificationUser;
    private final Label connectionLabel = new Label("● Chưa kết nối");
    private final Label rttLabel = new Label("— ms");
    private final TextField searchField = new TextField();
    private final ComboBox<String> searchMode = new ComboBox<>();
    private final AuctionMarketplacePane auctionCards = new AuctionMarketplacePane();
    private long lastFeaturedRefresh;
    private boolean featuredLoading;
    private CompletableFuture<ProductMediaApi> coverApi;
    private String coverSession = "";
    private long coverConnection = -1;
    private final ImageView productImage = new ImageView();
    private final Label detailName = new Label("Chọn một phòng");
    private final RoomCodeBadge detailRoomCode = new RoomCodeBadge("room-code-badge");
    private final RoomCodeBadge createdRoomCode = new RoomCodeBadge("created-room-code", "Ph\u00f2ng v\u1eeba t\u1ea1o - m\u00e3 ph\u00f2ng", this::clearCreatedRoomCode);
    private long createdAuctionId;
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
    private final CheckBox lobbyHosted = new CheckBox("Tôi tổ chức");
    private final Label lobbyPageLabel = new Label(), lobbyStatus = new Label();
    private final Button lobbyPrevious = new Button("Trước"), lobbyNext = new Button("Tiếp");
    private int lobbyPage;
    private long lobbyGeneration;
    private boolean creatingAuction;
    private Button createAuctionAction;
    private final Label openCount = DesktopTheme.label("0", "stat-value");
    private final Label roomCount = DesktopTheme.label("0", "stat-value");
    private final Label joinedRoom = DesktopTheme.label("—", "stat-value");
    private final Label participantCount = DesktopTheme.label("0", "stat-value");
    private final List<ToggleButton> phaseTabs = List.of(new ToggleButton(), new ToggleButton(), new ToggleButton());
    private String lobbyPhase = "ALL";
    private String lobbyOrderBeforeUpcoming = "NEWEST";
    private Instant nextLobbyStart;
    private final java.util.Set<String> imageRequests = new java.util.HashSet<>();
    private final Label pageTitle = DesktopTheme.label("Sàn đấu giá", "page-title");
    private final Label pageEyebrow = DesktopTheme.label("KHÁM PHÁ & ĐẤU GIÁ", "eyebrow");
    private final Label bidHint = DesktopTheme.label("", "bid-hint");
    private final FlowPane bidSuggestions = new FlowPane(6, 6);
    private final List<Button> quickBidButtons = List.of(new Button(), new Button(), new Button());
    private final Label bidFeedback = DesktopTheme.label("", "bid-feedback");
    private final Label roomConnection = DesktopTheme.label("", "bid-hint");
    private final Label roomFunds = DesktopTheme.label("", "bid-hint");
    private final Label roomFeeHint = DesktopTheme.label("Phí sàn người bán: đến 1 tỷ 10%, trên 1 tỷ 15%; xếp hạng doanh thu tháng được giảm 1–5 điểm %.", "bid-hint");
    private final Label hostLockReason = DesktopTheme.label("", "muted");
    private final Button syncRoomButton = new Button("\u0110\u1ed3ng b\u1ed9 l\u1ea1i");
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
    private final AuctionCongratulations congratulations = new AuctionCongratulations();
    private final WinnerCelebration winnerCelebration = new WinnerCelebration();
    private final BidLeadingBanner bidLeadingBanner = new BidLeadingBanner();
    private final java.util.Set<String> shownReceipts = new java.util.HashSet<>();
    private final RoomSocialPane socialRoom;
    private final VBox roomMain = new VBox(12);
    private final ScrollPane roomMainScroll = new ScrollPane(roomMain);
    private final ToggleButton roomProductTab = new ToggleButton("S\u1ea3n ph\u1ea9m");
    private final ToggleButton roomActivityTab = new ToggleButton("Di\u1ec5n bi\u1ebfn");
    private final VBox roomProductContent = new VBox(14);
    private final RoomProductDetails roomProductInformation = new RoomProductDetails();
    private Timeline roomEventScroll;
    private final VBox roomHistory = liveRoom.history();
    private final ScrollPane roomHistoryScroll = new ScrollPane(roomHistory);
    private final ToggleButton roomBidTab = new ToggleButton("\u0110\u1ea5u gi\u00e1 ngay");
    private final VBox priceBlock = new VBox(6);
    private final Label priceCaption = DesktopTheme.label("GI\u00c1 HI\u1ec6N T\u1ea0I", "eyebrow");
    private final HBox roomColumns = new HBox(16);
    private final BorderPane detailPanel = new BorderPane();
    private final StackPane roomImage = new StackPane();
    private final VBox roomProductSummary = new VBox(10);
    private final VBox roomProductIdentity = new VBox(8);
    private final HBox productHeader = new HBox(8);
    private final SplitPane listingSplit = new SplitPane();
    private VBox listingPanel, bidding, roomSide, productDetails;
    private HBox statsPanel, roomHeading;
    private ScrollPane detailsScroll, roomControlsScroll, roomBidScroll;
    private TitledPane productDescription;
    private boolean roomMode, renderingAuctions;
    private String productImageKey = "";
    private final HBox lobbyNameRow = new HBox(8), lobbyTimeRow = new HBox(9), lobbyPriceRow = new HBox(8);
    private final VBox lobbyPriceAmount = new VBox(1);
    private final Label detailStep = new Label(), detailTag = new Label(), detailAuctionBadge = new Label();
    private final ToggleButton detailFavorite = new ToggleButton();
    private final HBox membership = new HBox(7), bidActions = new HBox(8), bidInput = new HBox(6);
    private final Label bidAmountCaption = new Label("Số tiền đặt giá (đ)");
    private final HBox lobbyHostRow = new HBox(7);
    private final StackPane previewOverlay = new StackPane();
    private final MarketplaceActivityPane recentActivity = new MarketplaceActivityPane(this::showActivityLog);
    private VBox lobbyPreview, lobbyDetailColumn;
    private Region marketplaceBanner;
    private HBox lobbyToolbar;
    private FlowPane lobbyFilters;

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
        this(stage, config, model, transport, accounts, auctions, sounds, new ClientBidVoice(sounds));
    }

    public FxClientController(Stage stage, ClientConfig config, ClientAppModel model,
            ClientTransport transport, AccountApi accounts, AuctionApi auctions, ClientSounds sounds, ClientBidVoice bidVoice) {
        this.stage = stage;
        this.config = config;
        this.model = model;
        this.transport = transport;
        this.accounts = accounts;
        this.auctions = auctions;
        this.sounds = sounds;
        this.backgroundMusic = new ClientBackgroundMusic(sounds);
        this.bidVoice = bidVoice;
        this.socialRoom = new RoomSocialPane(transport);
        this.friendsPane = new FriendsPane(transport, model);
        this.socialRoom.onFriend(friendsPane::showPerson);
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
        stage.setTitle("Đấu giá • Sàn đấu giá");
        stage.getIcons().add(DesktopTheme.icon());
        stage.setMinWidth(1100);
        stage.setMinHeight(480);
        stage.setScene(loginScene);
        DesktopTheme.fitOnScreen(stage, 1120, 780);
        stage.heightProperty().addListener((observable, before, now) -> updateRoomImageSize());
        stage.show();
        backgroundMusic.start();
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
        usernameField.setId("login-username");
        passwordField.setId("login-password");
        loginButton.setId("login-submit");
        Label errorIcon = DesktopTheme.label("!", "login-error-icon");
        errorIcon.setMinSize(24, 24);
        errorIcon.setMaxSize(24, 24);
        errorIcon.setAlignment(Pos.CENTER);
        loginErrorLabel.setId("login-error-message");
        loginErrorLabel.setWrapText(true);
        loginErrorLabel.setMinWidth(0);
        loginErrorLabel.setMaxWidth(Double.MAX_VALUE);
        VBox errorCopy = new VBox(3, DesktopTheme.label("Ch\u01b0a th\u1ec3 \u0111\u0103ng nh\u1eadp", "login-error-title"), loginErrorLabel);
        HBox.setHgrow(errorCopy, Priority.ALWAYS);
        loginErrorBanner.getChildren().setAll(errorIcon, errorCopy);
        loginErrorBanner.setId("login-error-banner");
        loginErrorBanner.getStyleClass().add("login-error-banner");
        loginErrorBanner.setAlignment(Pos.TOP_LEFT);
        loginErrorBanner.setMinHeight(Region.USE_PREF_SIZE);
        loginErrorBanner.managedProperty().bind(loginErrorBanner.visibleProperty());
        clearLoginError();
        usernameField.textProperty().addListener((observable, before, now) -> clearLoginError());
        passwordField.textProperty().addListener((observable, before, now) -> clearLoginError());
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
        form.getChildren().add(form.getChildren().indexOf(loginButton), loginErrorBanner);
        form.getChildren().add(ClientSoundUi.controls(sounds, bidVoice));
        form.setPadding(new Insets(24, 0, 24, 0));
        StackPane formPanel = new StackPane(form);
        formPanel.setPadding(new Insets(28, 42, 28, 42));
        HBox.setHgrow(formPanel, Priority.ALWAYS);
        Circle orbit = new Circle(105, Color.TRANSPARENT);
        orbit.setStroke(Color.web("#d6ab62", 0.35));
        Circle inner = new Circle(78, Color.web("#ffffff", 0.035));
        StackPane art = new StackPane(orbit, inner, DesktopTheme.gavel(88, "#edc986"));
        art.setPrefHeight(242);
        Label liveTag = DesktopTheme.label("●  Đấu giá trực tiếp", "hero-tag");
        liveTag.setMaxWidth(180);
        VBox hero = new VBox(22, DesktopTheme.brand("Đấu giá", "Trực tiếp"), new Region(),
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
        root.setMinHeight(Region.USE_PREF_SIZE);
        ScrollPane scroll = DesktopTheme.viewport(root); scroll.setId("client-login-scroll");
        return scroll;
    }

    private Region buildMainRoot() {
        Button productsButton = navButton("Sản phẩm bán", "box"); productsButton.setId("account-products-navigation"); productsNavigation = productsButton;
        Button walletButton = navButton("Ví & nạp/rút tiền", "wallet"); walletButton.setId("account-wallet-navigation"); walletNavigation = walletButton;
        Button transactionsButton = navButton("Giao dịch", "exchange"); transactionsButton.setId("account-transactions-navigation"); transactionsNavigation = transactionsButton;
        transactionsButton.setOnAction(event -> showTransactions());
        Button rulesButton = navButton("Luật đấu giá", "rules"); rulesNavigation = rulesButton;
        Button rankingButton = navButton("Xếp hạng doanh thu", "trophy");
        rankingButton.setId("product-ranking-navigation"); rankingButton.setMaxWidth(Double.MAX_VALUE);
        rankingButton.setOnAction(event -> {
            long user = model.getUserId(); String token = model.getSessionToken();
            new AuctionRankingWindow(stage, transport,
                    () -> model.getUserId() == user && token.equals(model.getSessionToken()) && model.hasSessionToken()).show();
        });
        rulesButton.setId("auction-rules-navigation");
        rulesButton.setOnAction(event -> showRules());
        Button createAuctionButton = new Button("Tạo phòng đấu giá", MarketplaceIcons.icon("plus", 15, "#fff8e8"));
        createAuctionAction = createAuctionButton;
        Button refreshButton = iconButton("refresh", "Làm mới danh sách sản phẩm"); refreshButton.setId("marketplace-refresh");
        Button logoutButton = navButton("Đăng xuất", "logout");
        logoutButton.setId("marketplace-logout");
        Button marketplace = navButton("Sàn đấu giá", "users"); marketplace.setId("account-marketplace-navigation"); marketplaceNavigation = marketplace;
        marketplace.getStyleClass().add("nav-active");
        createAuctionButton.getStyleClass().add("market-gold-button"); createAuctionButton.setMaxWidth(Double.MAX_VALUE);
        VBox sidebar = new VBox(14, DesktopTheme.brand("Đấu giá", "Trực tiếp"));
        sidebar.getStyleClass().addAll("sidebar", "market-sidebar");
        sidebar.setMinWidth(192); sidebar.setPrefWidth(192); sidebar.setMaxWidth(192);
        for (Button navigation : List.of(marketplace, productsButton, walletButton, transactionsButton, rulesButton)) {
            navigation.getProperties().put("client.sound.cue", ClientSounds.Cue.NAVIGATE);
        }
        Region gap = new Region();
        gap.setPrefHeight(4);
        Region filler = new Region();
        VBox.setVgrow(filler, Priority.ALWAYS);
        for (Button button : List.of(marketplace, productsButton, walletButton, transactionsButton, rulesButton, logoutButton)) {
            button.setMaxWidth(Double.MAX_VALUE);
        }
        userLabel.getStyleClass().add("market-account-name"); userLabel.setMinWidth(0);
        VBox navigation = new VBox(4, marketplace, rankingButton, productsButton, walletButton, transactionsButton, rulesButton);
        navigation.getStyleClass().add("sidebar-navigation");
        Button avatar = new Button("P"); avatar.getStyleClass().add("market-account-avatar");
        avatar.setId("account-profile-navigation"); avatar.setAccessibleText("Mở tài khoản");
        avatar.setTooltip(new Tooltip("Tài khoản"));
        avatar.getProperties().put("client.sound.cue", ClientSounds.Cue.NAVIGATE);
        avatar.setOnAction(event -> showProfile());
        userLabel.textProperty().addListener((observable, before, name) -> avatar.setText(name.isBlank() ? "P" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT)));
        walletBalance.getStyleClass().add("market-account-balance"); walletBalance.setMinWidth(0);
        VBox identity = new VBox(3, userLabel, walletBalance); identity.setMinWidth(0); HBox.setHgrow(identity, Priority.ALWAYS);
        HBox accountIdentity = new HBox(9, avatar, identity); accountIdentity.setAlignment(Pos.CENTER_LEFT);
        VBox account = new VBox(11, accountIdentity, ClientSoundUi.controls(sounds, bidVoice)); account.getStyleClass().add("market-account");
        sidebar.getChildren().addAll(gap, DesktopTheme.label("Không gian của bạn", "market-nav-caption"),
                navigation, filler, createAuctionButton, new Separator(), account, logoutButton);
        sidebar.setMinWidth(0); sidebar.setMinHeight(Region.USE_PREF_SIZE);
        ScrollPane navigationScroll = DesktopTheme.viewport(sidebar); navigationScroll.setId("client-navigation-scroll");
        navigationScroll.setMinWidth(192); navigationScroll.setPrefWidth(192); navigationScroll.setMaxWidth(192);
        marketplace.setOnAction(event -> { showAuctionPage(); if (!roomMode) { auctionCards.getSelectionModel().clearSelection(); } loadAuctionList(); });
        connectionLabel.getStyleClass().addAll("pill", "market-connection");
        rttLabel.getStyleClass().add("muted");
        Button menu = iconButton("menu", "Ẩn / hiện thanh điều hướng");
        menu.setOnAction(event -> { boolean show = !navigationScroll.isManaged(); navigationScroll.setVisible(show); navigationScroll.setManaged(show); });
        Button bell = iconButton("bell", "Thông báo"); notificationBell = bell;
        bell.setId("marketplace-notification-bell"); bell.getStyleClass().add("market-notification-button");
        notificationCount.setId("marketplace-notification-count"); notificationCount.getStyleClass().add("market-notification-count");
        notificationCount.setMouseTransparent(true); notificationCount.setVisible(false); notificationCount.setManaged(false);
        StackPane bellGraphic = new StackPane(MarketplaceIcons.icon("bell", 22, "#715c43"), notificationCount);
        bellGraphic.setMinSize(28, 26); bellGraphic.setPrefSize(28, 26);
        StackPane.setAlignment(notificationCount, Pos.TOP_RIGHT); notificationCount.setTranslateX(4); notificationCount.setTranslateY(-3);
        bell.setGraphic(bellGraphic);
        Button full = iconButton("expand", "Bật / tắt toàn màn hình"); full.setOnAction(event -> { stage.setFullScreenExitHint(""); stage.setFullScreen(!stage.isFullScreen()); });
        pageEyebrow.setText("Khám phá & Đấu giá");
        Label crumb = new Label("Sàn đấu giá"); accountBreadcrumb = crumb; crumb.getStyleClass().add("market-breadcrumb-current");
        pageBack = AccountUi.button("Quay lại", "left"); pageBack.setId("client-page-back");
        pageBack.setVisible(false); pageBack.managedProperty().bind(pageBack.visibleProperty());
        pageBack.setOnAction(event -> { if (accountPageVisible) { showAuctionPage(); } else { leaveJoined(); } });
        HBox header = new HBox(10, menu, pageBack, MarketplaceIcons.icon("right", 9, "#b7a28b"), pageEyebrow,
                MarketplaceIcons.icon("right", 9, "#b7a28b"), crumb, DesktopTheme.spacer(), friendsPane.button(), bell, connectionLabel, rttLabel, full);
        header.getStyleClass().add("market-topbar");
        header.setAlignment(Pos.CENTER_LEFT);
        marketplaceBanner = new MarketplaceBanner(pageTitle);
        statsPanel = new HBox(10, statCard("Phiên đang mở", openCount, "Đang diễn ra", "gavel", true),
                statCard("Phòng đấu giá", roomCount, "Tổng phòng", "users", false),
                statCard("Lượt tham gia", participantCount, "Đang theo dõi", "flame", false));
        statsPanel.setId("marketplace-statistics");
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
        searchField.setPromptText("Tìm sản phẩm, tên phòng, người bán…");
        searchField.setId("marketplace-search-input"); searchField.setMinWidth(100);
        Button searchButton = new Button("Tìm kiếm");
        Button enterCode = new Button("Nh\u1eadp m\u00e3", MarketplaceIcons.icon("grid", 15, "#78552a"));
        enterCode.setId("marketplace-enter-room-code");
        enterCode.setMinWidth(Region.USE_PREF_SIZE);
        enterCode.setOnAction(event -> showRoomCodeDialog());
        Button clearButton = new Button("Xóa lọc");
        clearButton.getStyleClass().add("ghost");
        Button filtersToggle = new Button("Bộ lọc", MarketplaceIcons.icon("filter", 16, "#57442d")); filtersToggle.setId("marketplace-filters-toggle");
        filtersToggle.getStyleClass().add("ghost");
        HBox search = new HBox(10, MarketplaceIcons.icon("search", 17, "#302820"), searchField);
        search.getStyleClass().add("marketplace-search");
        lobbyVisibility.getItems().setAll("ALL", "PUBLIC", "PRIVATE"); lobbyVisibility.setValue("ALL");
        lobbyIdentity.getItems().setAll("ALL", "NAMED", "ANONYMOUS"); lobbyIdentity.setValue("ALL");
        lobbyOrder.getItems().setAll("NEWEST", "STARTING", "ENDING", "PRICE_ASC", "PRICE_DESC"); lobbyOrder.setValue("NEWEST");
        lobbyVisibility.setConverter(lobbyLabels(Map.of("ALL", "Tất cả phòng", "PUBLIC", "Phòng công khai", "PRIVATE", "Phòng riêng tư")));
        lobbyIdentity.setConverter(lobbyLabels(Map.of("ALL", "Mọi danh tính", "NAMED", "Khách thường", "ANONYMOUS", "Khách ẩn danh")));
        lobbyOrder.setConverter(lobbyLabels(Map.of("NEWEST", "Sắp xếp: Mới nhất", "STARTING", "Bắt đầu gần nhất", "ENDING", "Sắp hết giờ", "PRICE_ASC", "Giá tăng dần", "PRICE_DESC", "Giá giảm dần")));
        lobbyVisibility.setPromptText("Quyền vào"); lobbyIdentity.setPromptText("Danh tính");
        lobbyVisibility.getStyleClass().add("market-toolbar-combo"); lobbyOrder.getStyleClass().add("market-toolbar-combo");
        lobbyVisibility.setPrefWidth(150); lobbyOrder.setPrefWidth(184);
        decorateCombo(lobbyVisibility, "grid"); decorateCombo(lobbyOrder, "sort");
        lobbyToolbar = new HBox(10, search, enterCode, lobbyVisibility, lobbyOrder, filtersToggle); lobbyToolbar.setAlignment(Pos.CENTER_LEFT);
        lobbyToolbar.getStyleClass().add("market-toolbar"); HBox.setHgrow(search, Priority.ALWAYS);
        FlowPane filters = new FlowPane(8, 8, searchMode, lobbyIdentity, lobbyHosted, clearButton); lobbyFilters = filters;
        filters.setId("marketplace-filters"); filters.setVisible(false); filters.setManaged(false);
        filtersToggle.setOnAction(event -> { boolean show = !filters.isManaged(); filters.setManaged(show); filters.setVisible(show); });
        for (ComboBox<String> filter : List.of(lobbyVisibility, lobbyIdentity, lobbyOrder)) {
            filter.setOnAction(event -> { lobbyPage = 0; renderAuctions(); });
        }
        lobbyHosted.setOnAction(event -> { lobbyPage = 0; renderAuctions(); });
        lobbyPrevious.setOnAction(event -> { lobbyPage = Math.max(0, lobbyPage - 1); renderAuctions(); });
        lobbyNext.setOnAction(event -> { lobbyPage++; renderAuctions(); });
        search.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((observable, before, value) -> { lobbyPage = 0; renderAuctions(); });
        auctionCards.configure(auction -> loadCoverImage(auction, "thumbnail"), model::serverNow);
        auctionCards.onEnter(auction -> { if (auction.isOpen() && auction.getStartTime() != null && !model.serverNow().isBefore(auction.getStartTime())) { joinSelected(); } });
        auctionCards.onFavorites(this::refreshFavorite);
        HBox paging = new HBox(8, lobbyPrevious, lobbyPageLabel, lobbyNext); paging.setAlignment(Pos.CENTER);
        paging.getStyleClass().add("market-paging");
        lobbyStatus.getStyleClass().add("muted"); lobbyStatus.setWrapText(true);
        HBox tabs = new HBox(2); tabs.setAlignment(Pos.CENTER_LEFT); tabs.getStyleClass().add("market-phase-tabs");
        ToggleGroup phases = new ToggleGroup(); String[] phaseIds = {"ALL", "LIVE", "UPCOMING"};
        for (int index = 0; index < phaseTabs.size(); index++) {
            ToggleButton tab = phaseTabs.get(index); String phase = phaseIds[index]; tab.setToggleGroup(phases);
            tab.setId("marketplace-phase-" + phase.toLowerCase(java.util.Locale.ROOT)); tab.setSelected(index == 0);
            tab.setOnAction(event -> { tab.setSelected(true); selectLobbyPhase(phase); }); tabs.getChildren().add(tab);
        }
        ToggleButton gridView = new ToggleButton(), listView = new ToggleButton(); ToggleGroup views = new ToggleGroup();
        gridView.setToggleGroup(views); listView.setToggleGroup(views); gridView.setSelected(true);
        gridView.setGraphic(MarketplaceIcons.icon("grid", 14, "#78552a")); listView.setGraphic(MarketplaceIcons.icon("list", 14, "#78552a"));
        gridView.setId("marketplace-grid-view"); listView.setId("marketplace-list-view");
        gridView.setAccessibleText("Hiển thị dạng lưới"); listView.setAccessibleText("Hiển thị dạng danh sách");
        gridView.setOnAction(event -> { gridView.setSelected(true); auctionCards.setListMode(false); });
        listView.setOnAction(event -> { listView.setSelected(true); auctionCards.setListMode(true); });
        HBox viewsBar = new HBox(gridView, listView); viewsBar.getStyleClass().add("market-view-switch"); viewsBar.setAlignment(Pos.CENTER);
        HBox listingHeader = new HBox(7, tabs, DesktopTheme.spacer(), viewsBar); listingHeader.setAlignment(Pos.CENTER_LEFT);
        listingHeader.getStyleClass().add("market-list-header");
        lobbyStatus.setMinWidth(0); lobbyStatus.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(lobbyStatus, Priority.ALWAYS);
        HBox listingFooter = new HBox(6, lobbyStatus, paging, refreshButton); listingFooter.setAlignment(Pos.CENTER_LEFT);
        listingFooter.getStyleClass().add("market-list-footer");
        listingPanel = new VBox(10, listingHeader, auctionCards, listingFooter);
        listingPanel.getStyleClass().addAll("marketplace-listing", "market-list-surface");
        listingPanel.setMinWidth(320); VBox.setVgrow(auctionCards, Priority.ALWAYS);
        productDetails = buildDetails();
        lobbyPreview = new VBox(0, productDetails); lobbyPreview.getStyleClass().add("market-detail-card");
        lobbyDetailColumn = new VBox(9, lobbyPreview, recentActivity, productDescription, managementPane); lobbyDetailColumn.setMinWidth(0);
        detailsScroll = new ScrollPane(lobbyDetailColumn);
        detailsScroll.setFitToWidth(true);
        detailsScroll.setMinSize(300, 0);
        detailsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        membership.getChildren().setAll(leaveButton, historyButton);
        Button minus = iconButton("minus", "Giảm một bước giá"), plus = iconButton("plus", "Tăng một bước giá");
        minus.getStyleClass().add("market-step-button"); plus.getStyleClass().add("market-step-button");
        minus.setId("marketplace-bid-minus"); plus.setId("marketplace-bid-plus");
        minus.disableProperty().bind(bidField.disabledProperty()); plus.disableProperty().bind(bidField.disabledProperty());
        minus.setOnAction(event -> adjustBid(-1)); plus.setOnAction(event -> adjustBid(1));
        bidAmountCaption.getStyleClass().add("market-bid-caption");
        bidInput.getChildren().setAll(bidAmountCaption, bidField, minus, plus); bidInput.setAlignment(Pos.CENTER_LEFT);
        bidActions.getChildren().setAll(joinButton, bidButton); bidActions.setAlignment(Pos.CENTER_LEFT);
        joinButton.setMaxWidth(Double.MAX_VALUE); bidButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(joinButton, Priority.ALWAYS); HBox.setHgrow(bidButton, Priority.ALWAYS);
        joinButton.setGraphic(MarketplaceIcons.icon("enter", 16, "#845b26")); bidButton.setGraphic(MarketplaceIcons.icon("gavel", 17, "#fff8e8"));
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
        VBox management = new VBox(14,
                DesktopTheme.label("Điều hành phiên", "section-title"),
                DesktopTheme.label("Quản lý thời gian, trạng thái phiên và người tham gia.", "muted"),
                (javafx.scene.Node) managementPane.getContent(), hostLockReason);
        management.getStyleClass().add("room-management-content");
        managementPane.setContent(management);
        syncRoomButton.setOnAction(event -> synchronizeRoom());
        membership.getChildren().add(syncRoomButton);
        HBox syncRow = new HBox(8, roomConnection);
        HBox.setHgrow(roomConnection, Priority.ALWAYS); roomConnection.setMinWidth(0);
        roomFeeHint.setWrapText(true);
        bidding = new VBox(6, membership, syncRow, bidInput, bidActions, bidFeedback, bidSuggestions, bidHint, roomFunds, roomFeeHint);
        roomBidScroll = new ScrollPane(); roomBidScroll.setFitToWidth(true);
        roomBidScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        roomBidScroll.setMinHeight(0);
        roomBidScroll.getStyleClass().add("room-bid-scroll");
        bidding.getStyleClass().add("market-lobby-bidding"); bidding.setPadding(new Insets(6, 9, 8, 9));
        roomFeeHint.setVisible(false); roomFeeHint.setManaged(false);
        membership.setVisible(false); membership.setManaged(false);
        roomFunds.setVisible(false); roomFunds.setManaged(false); syncRow.setVisible(false); syncRow.setManaged(false);
        bidSuggestions.setVisible(false); bidSuggestions.setManaged(false);
        bidHint.setVisible(false); bidHint.setManaged(false);
        lobbyPreview.getChildren().add(bidding);
        detailPanel.setCenter(detailsScroll); detailPanel.setMinWidth(330); detailPanel.setPrefWidth(350); detailPanel.setMaxWidth(350);
        SplitPane.setResizableWithParent(detailPanel, false);
        detailPanel.getStyleClass().add("market-detail-panel");
        liveRoom.onHistory(this::showHistory);
        liveRoom.onProfile(socialRoom::openProfile);
        liveRoom.onBidSelected(socialRoom::focusBid);
        socialRoom.onEventFocus(this::revealRoomEvent);
        roomControlsScroll = new ScrollPane(socialRoom); roomControlsScroll.setFitToWidth(true);
        roomControlsScroll.setMinHeight(0);
        roomHistoryScroll.setFitToWidth(true); roomHistoryScroll.setMinHeight(0);
        roomHistoryScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        roomHistoryScroll.getStyleClass().add("room-bid-scroll");
        roomControlsScroll.getStyleClass().add("room-bid-scroll");
        ToggleGroup roomTabs = new ToggleGroup();
        ToggleButton chatTab = new ToggleButton("Tr\u00f2 chuy\u1ec7n");
        ToggleButton historyTab = new ToggleButton("L\u1ecbch s\u1eed");
        configureRoomTab(roomTabs, roomBidTab, "room-tab-bid", roomBidScroll);
        configureRoomTab(roomTabs, chatTab, "room-tab-chat", roomControlsScroll);
        configureRoomTab(roomTabs, historyTab, "room-tab-history", roomHistoryScroll);
        roomTabs.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null && previous != null) { roomTabs.selectToggle(previous); }
        });
        roomBidTab.setSelected(true);
        HBox tabButtons = new HBox(4, roomBidTab, chatTab, historyTab);
        tabButtons.setId("room-side-tabs"); tabButtons.getStyleClass().add("room-side-tabs");
        tabButtons.setMinHeight(Region.USE_PREF_SIZE);
        StackPane tabContent = new StackPane(roomBidScroll, roomControlsScroll, roomHistoryScroll);
        tabContent.setMinSize(0, 0); VBox.setVgrow(tabContent, Priority.ALWAYS);
        roomSide = new VBox(8, tabButtons, tabContent);
        roomSide.setMinWidth(340); roomSide.setPrefWidth(400); roomSide.setMaxWidth(430);
        roomControlsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        roomControlsScroll.setMinWidth(0);
        roomMain.setMinWidth(300); roomMain.setMinHeight(Region.USE_PREF_SIZE);
        roomMainScroll.setId("room-main-scroll"); roomMainScroll.setFitToWidth(true);
        roomMainScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        roomMainScroll.setMinWidth(300); HBox.setHgrow(roomMainScroll, Priority.ALWAYS);
        ToggleGroup mainTabs = new ToggleGroup();
        configureRoomTab(mainTabs, roomProductTab, "room-tab-product", roomProductContent);
        configureRoomTab(mainTabs, roomActivityTab, "room-tab-activity", socialRoom.auctionContent());
        roomProductTab.setSelected(true);
        mainTabs.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null && previous != null) { mainTabs.selectToggle(previous); }
            else if (selected != null) {
                if (roomEventScroll != null) { roomEventScroll.stop(); }
                roomMainScroll.setVvalue(0);
            }
        });
        roomColumns.heightProperty().addListener((observable, before, after) -> updateRoomImageSize());
        roomProductSummary.getStyleClass().addAll("card", "room-product");
        HBox.setHgrow(detailsScroll, Priority.ALWAYS); HBox.setHgrow(roomSide, Priority.ALWAYS);
        listingSplit.getItems().setAll(listingPanel);
        VBox.setVgrow(listingSplit, Priority.ALWAYS);
        roomHeading = new HBox(10, DesktopTheme.label("ĐẤU GIÁ TRỰC TIẾP", "eyebrow"), DesktopTheme.spacer(),
                DesktopTheme.label("Giá và thời gian được đồng bộ từ máy chủ", "muted"));
        roomHeading.getChildren().setAll(liveRoom.heading());
        HBox.setHgrow(liveRoom.heading(), Priority.ALWAYS);
        roomHeading.getStyleClass().add("room-heading"); roomHeading.setAlignment(Pos.CENTER_LEFT);
        roomHeading.setVisible(false); roomHeading.setManaged(false);
        eventLog.setEditable(false);
        eventLog.setWrapText(true);
        eventLog.setPrefRowCount(4);
        VBox workspace = new VBox(9, marketplaceBanner, congratulations, createdRoomCode, statsPanel, lobbyToolbar, filters, roomHeading, listingSplit);
        workspace.setPadding(new Insets(4, 10, 8, 12));
        workspace.getStyleClass().add("market-workspace");
        auctionWorkspace = workspace;
        accountPageScroll = DesktopTheme.viewport(workspace); accountPageScroll.setId("client-page-scroll");
        accountPageHost = new VBox(header, accountPageScroll); accountPageHost.setMinSize(0, 0);
        VBox.setVgrow(accountPageScroll, Priority.ALWAYS); showAccountContent(workspace);
        BorderPane root = new BorderPane(accountPageHost);
        root.setLeft(navigationScroll); BorderPane.setMargin(navigationScroll, new Insets(5, 0, 5, 4));
        Button receiptsButton = navButton("Kết quả phiên của tôi", "receipt");
        receiptsButton.setId("my-auction-receipts"); receiptsButton.setMaxWidth(Double.MAX_VALUE); receiptsNavigation = receiptsButton;
        navigation.getChildren().add(2, receiptsButton);
        receiptsButton.setOnAction(event -> showPersonalReceipt(0));
        bell.setOnAction(event -> {
            if (!model.hasSessionToken() || !transport.isConnected()) { return; }
            String session = model.getSessionToken(); long user = model.getUserId();
            long connection = roomConnectionGeneration;
            java.util.function.BooleanSupplier same = () -> !closed.get() && transport.isConnected()
                    && connection == roomConnectionGeneration && model.getUserId() == user && session.equals(model.getSessionToken());
            new PersonalNotificationsPane(stage, transport, same, (kind, room) -> {
                if (!same.getAsBoolean()) { return; }
                if (kind.startsWith("TOPUP") || kind.startsWith("WITHDRAWAL")) { showWallet(); }
                else if (kind.equals("AUCTION_RESULT")) { showPersonalReceipt(room); }
                else if (kind.equals("PRICE_CHANGED") || kind.equals("OUTBID")) { showAuctionPage(); joinAuction(room, ""); }
                else {
                    showAuctionPage(); lobbyPage = 0; lobbyVisibility.setValue("ALL"); lobbyIdentity.setValue("ALL"); lobbyHosted.setSelected(false);
                    searchMode.setValue("ROOM_ID"); searchField.setText(Long.toString(room)); loadAuctionList();
                    showError("Tìm phòng #" + room + " trong sảnh và bấm Vào phòng. Phòng riêng vẫn cần quyền/mật khẩu; nếu đã đóng hãy mở Kết quả phiên của tôi.");
                }
            }, this::acceptNotificationCount).show();
        });
        productsButton.setOnAction(event -> showProducts());
        walletButton.setOnAction(event -> showWallet());
        createAuctionButton.setOnAction(event -> showCreateAuction());
        refreshButton.setOnAction(event -> loadAuctionList());
        logoutButton.setOnAction(event -> logout());
        searchButton.setOnAction(event -> search());
        clearButton.setOnAction(event -> {
            searchField.clear();
            lobbyVisibility.setValue("ALL"); lobbyIdentity.setValue("ALL"); lobbyHosted.setSelected(false); lobbyOrder.setValue("NEWEST"); lobbyPage = 0;
            selectLobbyPhase("ALL"); lobbyOrder.setValue("NEWEST"); phaseTabs.get(0).setSelected(true);
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
        auctionCards.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, value) -> { if (!renderingAuctions) { renderDetails(value); } });
        Region animationBackdrop = new Region();
        animationBackdrop.setId("auction-animation-backdrop");
        animationBackdrop.getStyleClass().add("auction-animation-backdrop");
        animationBackdrop.setMouseTransparent(true);
        animationBackdrop.visibleProperty().bind(bidLeadingBanner.visibleProperty().or(winnerCelebration.visibleProperty()));
        animationBackdrop.managedProperty().bind(animationBackdrop.visibleProperty());
        StackPane.setAlignment(friendsPane, Pos.BOTTOM_RIGHT); StackPane.setMargin(friendsPane, new Insets(0, 12, 12, 0));
        StackPane shell = new StackPane(root, friendsPane, animationBackdrop, bidLeadingBanner, winnerCelebration);
        shell.getStyleClass().add("client-shell"); return shell;
    }

    private void configureRoomTab(ToggleGroup group, ToggleButton tab, String id, javafx.scene.Node content) {
        tab.setToggleGroup(group); tab.setId(id);
        tab.setMinWidth(0); tab.setPrefWidth(0); tab.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tab, Priority.ALWAYS);
        content.visibleProperty().bind(tab.selectedProperty());
        content.managedProperty().bind(tab.selectedProperty());
    }

    private void revealRoomEvent(javafx.scene.Node row) {
        if (!roomMode) { return; }
        roomActivityTab.setSelected(true);
        long generation = roomGeneration;
        Platform.runLater(() -> {
            if (!roomMode || !roomActivityTab.isSelected() || generation != roomGeneration || row.getScene() == null) { return; }
            roomMain.applyCss(); roomMain.layout();
            var bounds = roomMain.sceneToLocal(row.localToScene(row.getLayoutBounds()));
            double overflow = roomMain.getHeight() - roomMainScroll.getViewportBounds().getHeight();
            double position = overflow <= 0 ? 0 : Math.max(0, Math.min(1, (bounds.getMinY() - 14) / overflow));
            if (roomEventScroll != null) { roomEventScroll.stop(); }
            roomEventScroll = new Timeline(new KeyFrame(Duration.millis(280),
                    new javafx.animation.KeyValue(roomMainScroll.vvalueProperty(), position, javafx.animation.Interpolator.EASE_BOTH)));
            roomEventScroll.play();
        });
    }

    private HBox statCard(String title, Label value, String subtitle, String icon, boolean accent) {
        StackPane symbol = new StackPane(MarketplaceIcons.icon(icon, 25, "#765026")); symbol.getStyleClass().add("market-stat-icon");
        VBox text = new VBox(2, DesktopTheme.label(title, "market-stat-title"), value, DesktopTheme.label(subtitle, "market-stat-subtitle"));
        HBox box = new HBox(13, symbol, text); box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("market-stat-card"); box.setMinWidth(0); box.setPrefWidth(0);
        if (accent) box.getStyleClass().add("accent");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private Button navButton(String text, String icon) {
        Button button = new Button(text, MarketplaceIcons.icon(icon, 16, "#d1c7b8"));
        button.setGraphicTextGap(11); return button;
    }

    private void decorateCombo(ComboBox<String> combo, String icon) {
        combo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? "" : combo.getConverter().toString(value));
                setGraphic(empty || value == null ? null : MarketplaceIcons.icon(icon, 13, "#4c402f")); setGraphicTextGap(7);
            }
        });
    }

    private Button iconButton(String icon, String description) {
        Button button = new Button("", MarketplaceIcons.icon(icon, 15, "#715c43"));
        button.getStyleClass().add("market-icon-button"); button.setAccessibleText(description); button.setTooltip(new Tooltip(description)); return button;
    }

    private void showActivityLog() {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Hoạt động gần đây");
        TextArea log = new TextArea(eventLog.getText()); log.setEditable(false); log.setWrapText(true); log.setPrefSize(660, 340);
        dialog.getDialogPane().setContent(log); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); showDialog(dialog);
    }

    private void adjustBid(int direction) {
        ClientAuction auction = auctionCards.getSelectionModel().getSelectedItem();
        if (auction == null || bidField.isDisabled()) { return; }
        BigDecimal minimum = auction.getCurrentPrice().add(auction.getMinBidIncrement()), current = minimum;
        try { if (!bidField.getText().isBlank()) { current = Money.parseInput(bidField.getText()); } } catch (RuntimeException ignored) { }
        BigDecimal next = current.add(auction.getMinBidIncrement().multiply(BigDecimal.valueOf(direction))).max(minimum);
        if (maxBidAmount != null) { next = next.min(maxBidAmount); }
        bidField.setText(Money.input(next));
    }

    private void refreshFavorite() {
        ClientAuction selected = auctionCards.getSelectionModel().getSelectedItem();
        detailFavorite.setSelected(selected != null && auctionCards.isFavorite(selected.getAuctionId()));
    }

    private static StringConverter<String> lobbyLabels(Map<String, String> labels) {
        return new StringConverter<>() {
            @Override public String toString(String value) { return labels.getOrDefault(value == null ? "" : value, ""); }
            @Override public String fromString(String value) { return value; }
        };
    }

    private VBox buildDetails() {
        productImage.fitWidthProperty().bind(roomImage.widthProperty());
        productImage.fitHeightProperty().bind(roomImage.heightProperty());
        productImage.setSmooth(true);
        productImage.setStyle("-fx-cursor: hand;");
        productImage.setOnMouseClicked(event -> {
            ClientAuction selected = auctionCards.getSelectionModel().getSelectedItem();
            if (selected != null) { showMediaGallery(selected.getProductId(), selected.getAuctionId(), selected.getProductName()); }
        });
        var placeholder = DesktopTheme.gavel(34, "#b19770");
        productImage.imageProperty().addListener((observable, before, image) -> {
            placeholder.setVisible(image == null); updateProductCrop();
            if (image != null) { image.progressProperty().addListener((value, oldProgress, progress) -> updateProductCrop()); }
        });
        roomImage.widthProperty().addListener((observable, before, width) -> updateProductCrop());
        roomImage.heightProperty().addListener((observable, before, height) -> updateProductCrop());
        detailAuctionBadge.getStyleClass().add("market-live-badge");
        detailAuctionBadge.setGraphic(MarketplaceIcons.icon("clock", 10, "#fffaf7"));
        detailFavorite.getStyleClass().addAll("market-round-button", "market-favorite");
        detailFavorite.setGraphic(MarketplaceIcons.icon("heart", 15, "#7a6851")); detailFavorite.setAccessibleText("Yêu thích sản phẩm");
        detailFavorite.setTooltip(new Tooltip("Đánh dấu yêu thích trong phiên đăng nhập này"));
        detailFavorite.setOnAction(event -> { ClientAuction selected = auctionCards.getSelectionModel().getSelectedItem(); if (selected != null) { auctionCards.toggleFavorite(selected.getAuctionId()); } });
        HBox topOverlay = new HBox(detailAuctionBadge, DesktopTheme.spacer(), detailFavorite); topOverlay.setAlignment(Pos.TOP_CENTER); topOverlay.setPickOnBounds(false);
        Button previous = iconButton("left", "Sản phẩm trước"), next = iconButton("right", "Sản phẩm tiếp theo");
        previous.getStyleClass().add("market-round-button"); next.getStyleClass().add("market-round-button");
        previous.setOnAction(event -> selectAdjacentProduct(-1)); next.setOnAction(event -> selectAdjacentProduct(1));
        HBox arrows = new HBox(previous, DesktopTheme.spacer(), next); arrows.setAlignment(Pos.CENTER); arrows.setPickOnBounds(false);
        previewOverlay.setPickOnBounds(false); previewOverlay.getChildren().setAll(arrows, topOverlay);
        StackPane.setAlignment(topOverlay, Pos.TOP_CENTER); StackPane.setMargin(topOverlay, new Insets(9)); StackPane.setMargin(arrows, new Insets(8));
        roomImage.getChildren().addAll(placeholder, productImage, previewOverlay);
        Button zoom = new Button("Ph\u00f3ng to", MarketplaceIcons.icon("expand", 15, "#fff3df"));
        zoom.setId("room-image-zoom"); zoom.getStyleClass().add("room-image-zoom");
        zoom.visibleProperty().bind(previewOverlay.visibleProperty().not().and(roomImage.hoverProperty().or(zoom.focusedProperty())));
        zoom.managedProperty().bind(zoom.visibleProperty());
        zoom.setOnAction(event -> {
            ClientAuction selected = model.joinedAuction();
            if (selected != null) { showMediaGallery(selected.getProductId(), selected.getAuctionId(), selected.getProductName()); }
        });
        StackPane.setAlignment(zoom, Pos.BOTTOM_RIGHT); StackPane.setMargin(zoom, new Insets(16));
        roomImage.getChildren().add(zoom);
        roomImage.getStyleClass().addAll("room-image", "market-preview-image");
        javafx.scene.shape.Rectangle crop = new javafx.scene.shape.Rectangle(); crop.widthProperty().bind(roomImage.widthProperty()); crop.heightProperty().bind(roomImage.heightProperty()); crop.setArcWidth(15); crop.setArcHeight(15); roomImage.setClip(crop);
        roomImage.setMinSize(0, 150); roomImage.setPrefHeight(152); roomImage.setMaxHeight(152);
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
            ClientAuction selected = auctionCards.getSelectionModel().getSelectedItem();
            if (selected != null) { showMediaGallery(selected.getProductId(), selected.getAuctionId(), selected.getProductName()); }
        });
        productHeader.getChildren().setAll(detailVisibility, DesktopTheme.spacer(), gallery);
        productHeader.setAlignment(Pos.CENTER_LEFT);
        FlowPane sessionActions = new FlowPane(8, 8, extendButton, endButton, cancelButton);
        kickButton.setId("room-manage-participants"); kickButton.setText("Chọn người cần mời ra");
        kickButton.getStyleClass().add("room-manage-members"); kickButton.setMaxWidth(Double.MAX_VALUE);
        Label participantNote = DesktopTheme.label("Chọn nhiều khách cùng lúc từ danh sách; không cần nhập tên.", "muted");
        VBox participantActions = new VBox(8, DesktopTheme.label("Người tham gia", "eyebrow"), participantNote, kickButton);
        participantActions.getStyleClass().add("room-management-members");
        VBox hostActions = new VBox(14, sessionActions, participantActions);
        managementPane.getStyleClass().add("room-management");
        managementPane.setText("Quản lý phòng");
        managementPane.setContent(hostActions);
        managementPane.setExpanded(false);
        productDescription = new TitledPane("Thông tin sản phẩm", detailDescription);
        productDescription.setExpanded(false);
        detailTag.getStyleClass().add("market-category-tag");
        Button lobbyHistory = new Button("Lịch sử  →"); lobbyHistory.getStyleClass().add("market-text-button"); lobbyHistory.setOnAction(event -> showHistory());
        lobbyHostRow.getChildren().setAll(MarketplaceIcons.icon("profile", 17, "#9b9b9b"), detailHost, DesktopTheme.spacer(), lobbyHistory); lobbyHostRow.setAlignment(Pos.CENTER_LEFT); detailHost.setMinWidth(0);
        lobbyTimeRow.getStyleClass().add("market-countdown-box"); lobbyTimeRow.setAlignment(Pos.CENTER_LEFT);
        lobbyTimeRow.getChildren().setAll(MarketplaceIcons.icon("clock", 24, "#ff3457"), new VBox(1, DesktopTheme.label("Thời gian còn lại", "market-countdown-caption"), detailTime));
        VBox stepText = new VBox(2, DesktopTheme.label("Bước giá", "market-card-caption"), detailStep); stepText.getStyleClass().add("market-increment");
        HBox increment = new HBox(7, MarketplaceIcons.icon("gavel", 25, "#875d28"), stepText); increment.setAlignment(Pos.CENTER_LEFT);
        lobbyPriceRow.getChildren().setAll(lobbyPriceAmount, DesktopTheme.spacer(), increment); lobbyPriceRow.setAlignment(Pos.CENTER_LEFT);
        liveRoom.insights().setVisible(false);
        liveRoom.insights().setManaged(false);
        VBox details = new VBox(6); details.getStyleClass().add("market-product-details"); details.setPadding(new Insets(6, 6, 0, 6));
        restoreLobbyProduct(details);
        return details;
    }

    private void restoreLobbyProduct(VBox details) {
        lobbyNameRow.getChildren().setAll(detailName, detailTag); lobbyNameRow.setAlignment(Pos.CENTER_LEFT); detailName.setMinWidth(0); HBox.setHgrow(detailName, Priority.ALWAYS);
        if (!lobbyHostRow.getChildren().contains(detailHost)) { lobbyHostRow.getChildren().add(1, detailHost); }
        ((VBox) lobbyTimeRow.getChildren().get(1)).getChildren().setAll(DesktopTheme.label("Thời gian còn lại", "market-countdown-caption"), detailTime);
        lobbyPriceAmount.getChildren().setAll(priceCaption, detailPrice);
        priceBlock.getChildren().setAll(lobbyTimeRow, lobbyPriceRow, detailLeader);
        details.getChildren().setAll(roomImage, lobbyNameRow, lobbyHostRow, detailRoomCode, priceBlock);
        previewOverlay.setVisible(true); previewOverlay.setManaged(true);
    }

    private void selectAdjacentProduct(int direction) {
        if (auctionCards.getItems().isEmpty()) { return; }
        int index = Math.floorMod(auctionCards.getSelectionModel().getSelectedIndex() + direction, auctionCards.getItems().size());
        auctionCards.getSelectionModel().select(index);
    }

    private void updateProductCrop() {
        productImage.setPreserveRatio(roomMode);
        Image image = productImage.getImage();
        if (roomMode || image == null || image.getWidth() <= 0 || image.getHeight() <= 0 || roomImage.getWidth() <= 0 || roomImage.getHeight() <= 0) { productImage.setViewport(null); return; }
        double ratio = roomImage.getWidth() / roomImage.getHeight(), width = image.getWidth(), height = image.getHeight();
        if (width / height > ratio) { double visible = height * ratio; productImage.setViewport(new javafx.geometry.Rectangle2D((width - visible) / 2, 0, visible, height)); }
        else { double visible = width / ratio; productImage.setViewport(new javafx.geometry.Rectangle2D(0, (height - visible) / 2, width, visible)); }
    }

    private String money(BigDecimal value) {
        return WalletUi.money(value);
    }

    private void connect(boolean manual) {
        if (closed.get()) {
            return;
        }
        if (endpointField.getText().isBlank()) {
            showError("Hãy nhập địa chỉ được sao chép từ ứng dụng máy chủ trước khi kết nối.");
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
                        + "\nKiểm tra địa chỉ IP/cổng trên máy chủ, mạng nội bộ và tường lửa của máy tính."
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
        if (loginButton.isDisabled()) { return; }
        clearLoginError();
        if (usernameField.getText().isBlank() || passwordField.getText().isEmpty()) {
            showLoginError("Vui l\u00f2ng nh\u1eadp t\u00ean \u0111\u0103ng nh\u1eadp v\u00e0 m\u1eadt kh\u1ea9u.", true);
            (usernameField.getText().isBlank() ? usernameField : passwordField).requestFocus();
            return;
        }
        if (!transport.isConnected()) {
            connect(true);
            return;
        }
        setLoginBusy(true);
        accounts.login(usernameField.getText(), passwordField.getText())
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    setLoginBusy(false);
                    if (error != null) {
                        showLoginError("Kh\u00f4ng th\u1ec3 g\u1eedi y\u00eau c\u1ea7u \u0111\u0103ng nh\u1eadp. H\u00e3y ki\u1ec3m tra k\u1ebft n\u1ed1i v\u00e0 th\u1eed l\u1ea1i.", false);
                        return;
                    }
                    if (response == null || !response.isSuccess()) {
                        boolean invalidCredentials = response != null && "INVALID_CREDENTIALS".equals(response.getErrorCode());
                        String message = invalidCredentials
                                ? "T\u00ean \u0111\u0103ng nh\u1eadp ho\u1eb7c m\u1eadt kh\u1ea9u ch\u01b0a \u0111\u00fang. Vui l\u00f2ng ki\u1ec3m tra v\u00e0 th\u1eed l\u1ea1i."
                                : response == null || response.getMessageText().isBlank()
                                        ? "Ch\u01b0a th\u1ec3 \u0111\u0103ng nh\u1eadp l\u00fac n\u00e0y. Vui l\u00f2ng th\u1eed l\u1ea1i sau."
                                        : response.getUserMessage();
                        showLoginError(message, invalidCredentials);
                        if (invalidCredentials) { passwordField.requestFocus(); passwordField.selectAll(); }
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
                showError("Hãy kết nối máy chủ trước khi đăng ký");
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
                        model.clearIdentity(); clearCreatedRoomCode(); friendsPane.reset(); clearNotificationCount(); bidVoice.resetSession(); auctionCards.clearFavorites(); recentActivity.clear(); eventLog.clear(); auctionCards.getItems().clear(); auctionCards.clearFeatured(); socialRoom.clear(); congratulations.clear(); winnerCelebration.clear(); showAuctionPage(); stage.setScene(loginScene);
                        return;
                    }
                    model.applyIdentity(response);
                    showMain();
                    synchronizeRoom();
                    loadAuctionList();
                }));
    }

    private void showMain() {
        showAuctionPage();
        walletLoading = false; walletRequestSequence++;
        walletBalance.setText("V\u00ed: \u0111ang \u0111\u1ed3ng b\u1ed9...");
        userLabel.setText(model.getDisplayName().isBlank() ? model.getUsername() : model.getDisplayName());
        stage.setScene(mainScene);
        DesktopTheme.fitOnScreen(stage, 1420, 880);
        DesktopTheme.reveal(mainScene.getRoot());
        renderAuctions();
        refreshWallet();
        clearNotificationCount(); refreshNotificationCount();
        friendsPane.tick();
    }

    private void clearNotificationCount() {
        notificationRequestSequence++; notificationsLoading = false; lastNotificationRefresh = 0;
        notificationSession = ""; notificationUser = 0; renderNotificationCount(0);
    }

    private void renderNotificationCount(long count) {
        count = Math.max(0, count);
        notificationCount.setText(count > 99 ? "99+" : Long.toString(count));
        notificationCount.setVisible(count > 0); notificationCount.setManaged(count > 0);
        if (notificationBell != null) {
            String description = count > 0 ? "Thông báo · " + count + " chưa đọc" : "Thông báo · Không có thông báo chưa đọc";
            notificationBell.setAccessibleText(description); notificationBell.getTooltip().setText(description);
        }
    }

    private void acceptNotificationCount(long count) {
        notificationRequestSequence++; notificationsLoading = false; lastNotificationRefresh = System.nanoTime();
        renderNotificationCount(count);
    }

    private void refreshNotificationCount() {
        if (closed.get() || !model.hasSessionToken() || !transport.isConnected()) { clearNotificationCount(); return; }
        String session = model.getSessionToken(); long user = model.getUserId();
        if (!session.equals(notificationSession) || user != notificationUser) {
            clearNotificationCount(); notificationSession = session; notificationUser = user;
        }
        if (notificationsLoading) { return; }
        notificationsLoading = true; lastNotificationRefresh = System.nanoTime();
        long sequence = ++notificationRequestSequence, connection = roomConnectionGeneration;
        transport.sendRequest(MessageType.GET_MY_NOTIFICATIONS, Map.of("limit", "1", "unreadOnly", "true"))
                .whenComplete((wire, error) -> Platform.runLater(() -> {
                    if (sequence != notificationRequestSequence) { return; }
                    notificationsLoading = false;
                    if (closed.get() || !transport.isConnected() || connection != roomConnectionGeneration
                            || user != model.getUserId() || !session.equals(model.getSessionToken())) { return; }
                    if (error == null && wire != null && new ApiResponse(wire).isSuccess()) {
                        try { renderNotificationCount(Long.parseLong(wire.getData().getOrDefault("unreadCount", "0"))); }
                        catch (NumberFormatException ignored) { /* Keep the last confirmed count until the next refresh. */ }
                    }
                }));
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
        loadFeaturedAuctions();
        long generation = ++lobbyGeneration; lobbyStatus.setText("Đang tải sản phẩm…");
        auctions.listAuctions().whenComplete((response, error) -> Platform.runLater(() -> {
            if (!current(request, false) || generation != lobbyGeneration) { return; }
            if (!success(response, error)) {
                lobbyStatus.setText("Không tải được sản phẩm. Bấm Làm mới để thử lại.");
                return;
            }
            applyAuctionList(response);
            lobbyStatus.setText("Bấm vào sản phẩm để xem chi tiết và vào phòng.");
            auctionCards.retryImages();
            renderAuctions();
        }));
    }

    private void loadFeaturedAuctions() {
        if (featuredLoading || roomMode || !transport.isConnected() || !model.hasSessionToken()) { return; }
        featuredLoading = true; lastFeaturedRefresh = System.nanoTime(); RoomRequest request = roomRequest(0);
        auctions.featuredAuctions().whenComplete((response, error) -> Platform.runLater(() -> {
            featuredLoading = false;
            if (!current(request, false) || roomMode) { return; }
            if (error != null || response == null || !response.isSuccess()) { auctionCards.featuredUnavailable(); return; }
            auctionCards.setFeatured(response.getWireMessage().getData());
        }));
    }

    private void search() {
        lobbyPage = 0; loadAuctionList();
    }

    private void showRoomCodeDialog() {
        if (!model.hasSessionToken() || !transport.isConnected()) {
            showError("Vui l\u00f2ng k\u1ebft n\u1ed1i v\u00e0 \u0111\u0103ng nh\u1eadp tr\u01b0\u1edbc khi nh\u1eadp m\u00e3 ph\u00f2ng.");
            return;
        }
        if (roomChanging) { return; }
        RoomRequest request = roomRequest(0);
        new RoomCodeDialog(stage, auctions, () -> current(request, false)).showAndWait()
                .filter(auctionId -> current(request, false)).ifPresent(auctionId -> joinAuction(auctionId, ""));
    }

    private void joinSelected() {
        ClientAuction selected = selectedAuction();
        if (selected == null) {
            return;
        }
        String password = "";
        if (selected.requiresPassword() && !selected.isHostedBy(model.getUserId())) {
            Optional<String> result = roomPassword();
            if (result.isEmpty()) {
                return;
            }
            password = result.get();
        }
        if (phaseMatches(selected, "UPCOMING") && selected.getDepositAmount().signum() > 0) { placeRoomDeposit(selected, password); }
        else if (phaseMatches(selected, "UPCOMING")) { showInfo("Phòng này sẽ mở lúc " + Times.display(selected.getStartTime()) + "."); }
        else { joinAuction(selected.getAuctionId(), password); }
    }

    private boolean placingDeposit;
    private String depositSession = "";
    private final java.util.Set<Long> depositedRooms = new java.util.HashSet<>();

    private void placeRoomDeposit(ClientAuction room, String password) {
        if (placingDeposit || room.isHostedBy(model.getUserId()) || room.getDepositAmount().signum() <= 0) { return; }
        RoomDepositDialog confirmation = new RoomDepositDialog(stage, room);
        if (showDialog(confirmation).orElse(ButtonType.CANCEL) != ButtonType.OK) { return; }
        placingDeposit = true; RoomRequest request = roomRequest(room.getAuctionId()); refreshBidControls();
        auctions.placeDeposit(room.getAuctionId(), password).whenComplete((response, error) -> Platform.runLater(() -> {
            placingDeposit = false;
            if (!current(request, false)) { return; }
            if (success(response, error)) {
                depositedRooms.add(room.getAuctionId());
                appendEvent("Đã đặt cọc " + money(room.getDepositAmount()) + " cho phòng #" + room.getAuctionId() + ". Chờ đến giờ bắt đầu và còn chỗ để vào.");
                refreshWallet();
            }
            refreshBidControls();
        }));
    }

    private Optional<String> roomPassword() {
        Dialog<String> dialog = new Dialog<>(); dialog.setTitle("Phòng riêng tư");
        PasswordField field = new PasswordField(); field.setPromptText("Mật khẩu phòng");
        dialog.getDialogPane().setContent(new VBox(8, new Label("Phòng này yêu cầu mật khẩu"), field));
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Vào phòng", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        dialog.setResultConverter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE ? field.getText() : null);
        return showDialog(dialog);
    }

    private void joinAuction(long auctionId, String password) {
        if (!model.hasSessionToken() || !transport.isConnected() || auctionId <= 0) { return; }
        if (roomChanging) { return; }
        Long previous = model.getJoinedAuctionId();
        if (previous != null && previous != auctionId) {
            invalidateRoom(true); roomChanging = true; RoomRequest leaving = roomRequest(previous); refreshBidControls();
            auctions.leave(previous).whenComplete((response, error) -> Platform.runLater(() -> {
                if (!current(leaving, true)) { return; }
                roomChanging = false;
                if (success(response, error)) {
                    model.leaveJoinedAuction(); renderAuctions(); joinAuction(auctionId, password);
                } else { synchronizeRoom(); refreshBidControls(); }
            }));
            return;
        }
        invalidateRoom(true); roomChanging = true;
        RoomRequest request = roomRequest(auctionId);
        refreshBidControls();
        auctions.join(auctionId, password)
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (!current(request, false)) { return; }
                    roomChanging = false;
                    if (error == null && response != null && "ROOM_PASSWORD_REQUIRED".equals(response.getErrorCode())) {
                        Optional<String> result = roomPassword();
                        if (current(request, false)) { result.ifPresent(value -> joinAuction(auctionId, value)); }
                        refreshBidControls(); return;
                    }
                    if (success(response, error)) {
                        applySnapshot(response, true);
                        sounds.play(ClientSounds.Cue.NAVIGATE);
                        roomSnapshotReady = true; lastRoomSignal = System.nanoTime();
                        refreshRoomWallet();
                        renderAuctions();
                        auctionCards.getSelectionModel().select(model.joinedAuction());
                        appendEvent("Đã vào phòng #" + auctionId);
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
        backgroundMusic.setInAuctionRoom(false);
        bidVoice.clear();
        bidLeadingBanner.clear();
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
        updateHostButtons(auctionCards.getSelectionModel().getSelectedItem());
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
            feedback("Máy chủ \u0111\u00e3 x\u00e1c nh\u1eadn l\u01b0\u1ee3t " + money(amount) + ". Ch\u01b0a ph\u1ea3i k\u1ebft qu\u1ea3 th\u1eafng.", "success");
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
            case "INSUFFICIENT_FUNDS" -> "S\u1ed1 d\u01b0 kh\u1ea3 d\u1ee5ng kh\u00f4ng \u0111\u1ee7. H\u00e3y n\u1ea1p ví thử nghiệm v\u00e0 ch\u1edd duy\u1ec7t.";
            case "BID_ALREADY_LEADING" -> "B\u1ea1n \u0111ang d\u1eabn \u0111\u1ea7u, kh\u00f4ng th\u1ec3 t\u1ef1 n\u00e2ng gi\u00e1.";
            case "BID_AFTER_END", "AUCTION_NOT_OPEN", "AUCTION_NOT_FOUND" -> "Phi\u00ean \u0111\u00e3 \u0111\u00f3ng ho\u1eb7c h\u1ebft th\u1eddi gian nh\u1eadn gi\u00e1. Ch\u1edd máy chủ c\u1eadp nh\u1eadt.";
            case "AUCTION_FORBIDDEN" -> "Ch\u1ee7 ph\u00f2ng kh\u00f4ng \u0111\u01b0\u1ee3c \u0111\u1eb7t gi\u00e1 s\u1ea3n ph\u1ea9m c\u1ee7a m\u00ecnh.";
            case "USER_BLOCKED_FROM_AUCTION", "NOT_IN_AUCTION_ROOM", "ROOM_ACCESS_DENIED" -> "B\u1ea1n ch\u01b0a c\u00f3 quy\u1ec1n \u0111\u1eb7t gi\u00e1 t\u1ea1i ph\u00f2ng n\u00e0y.";
            case "INVALID_AMOUNT" -> "Gi\u00e1 kh\u00f4ng h\u1ee3p l\u1ec7: s\u1ed1 d\u01b0\u01a1ng, t\u1ed1i \u0111a 2 s\u1ed1 th\u1eadp ph\u00e2n v\u00e0 trong gi\u1edbi h\u1ea1n.";
            default -> response.getUserMessage();
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
        feedback("\u0110ang g\u1eedi " + money(amount) + ". Ch\u1edd máy chủ x\u00e1c nh\u1eadn, kh\u00f4ng b\u1ea5m l\u1ea1i.", "pending");
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
        if (transport.isConnected() && model.hasSessionToken() && java.util.Objects.equals(model.getJoinedAuctionId(), room)) {
            sounds.playOnce(ClientSounds.Cue.BID_ACCEPTED, "bid:" + room + ":" + amount.stripTrailingZeros().toPlainString());
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
        RoomRequest request = roomRequest(id);
        BidHistoryPane history = new BidHistoryPane(transport, id, () -> current(request, false));
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Lịch sử đặt giá · Phòng #" + id);
        dialog.getDialogPane().getStyleClass().addAll("room-dialog", "room-history-dialog");
        dialog.getDialogPane().setPrefWidth(860);
        dialog.setResizable(true); dialog.getDialogPane().setContent(history);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Đóng");
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
        RoomRequest request = roomRequest(selected.getAuctionId());
        new RoomParticipantsDialog(stage, transport, selected.getAuctionId(),
                () -> {
                    ClientAuction active = model.joinedAuction();
                    return current(request, true) && roomReady() && active != null && active.isOpen()
                            && active.getEndTime() != null && Instant.now().isBefore(active.getEndTime());
                },
                () -> { if (current(request, true)) { socialRoom.refresh(); loadAuctionList(); } }, null).showAndWait();
    }

    private void showWallet() {
        long user = model.getUserId(); String token = model.getSessionToken();
        java.util.function.BooleanSupplier same = () -> model.getUserId() == user && token.equals(model.getSessionToken()) && !closed.get();
        if (accountPageHost == null) { new WalletPane(stage, new WalletApi(transport), user, model.getUsername(), same).show(); return; }
        if (embeddedWallet != null) { return; }
        closeAccountPages();
        embeddedWallet = new WalletPane(stage, new WalletApi(transport), user, model.getUsername(), same, true);
        VBox.setVgrow(embeddedWallet, Priority.ALWAYS);
        showAccountContent(embeddedWallet);
        accountBreadcrumb.setText("Ví & nạp/rút tiền"); marketplaceNavigation.getStyleClass().remove("nav-active"); walletNavigation.getStyleClass().add("nav-active");
        stage.setTitle("Đấu giá • Ví của " + model.getUsername()); embeddedWallet.startEmbedded();
    }
    private void showAuctionPage() {
        if (embeddedWallet == null && embeddedTransactions == null && embeddedReceipts == null && embeddedProducts == null && embeddedRules == null) { return; }
        closeAccountPages();
        showAccountContent(auctionWorkspace);
        accountBreadcrumb.setText("Sàn đấu giá"); walletNavigation.getStyleClass().remove("nav-active");
        if (!marketplaceNavigation.getStyleClass().contains("nav-active")) { marketplaceNavigation.getStyleClass().add("nav-active"); }
        stage.setTitle("Đấu giá • Sàn đấu giá");
    }
    private void showTransactions() {
        if (embeddedTransactions != null || accountPageHost == null) { return; }
        long user = model.getUserId(); String token = model.getSessionToken();
        closeAccountPages();
        embeddedTransactions = new TransactionsPane(new WalletApi(transport), user, model.getUsername(),
                () -> model.getUserId() == user && token.equals(model.getSessionToken()) && !closed.get());
        VBox.setVgrow(embeddedTransactions, Priority.ALWAYS);
        showAccountContent(embeddedTransactions);
        accountBreadcrumb.setText("Giao dịch"); marketplaceNavigation.getStyleClass().remove("nav-active");
        transactionsNavigation.getStyleClass().add("nav-active"); stage.setTitle("Đấu giá • Giao dịch của " + model.getUsername()); embeddedTransactions.start();
    }
    private void showAccountContent(Region content) {
        VBox body = new VBox(content); VBox.setVgrow(content, Priority.ALWAYS);
        accountPageScroll.setContent(body); accountPageScroll.setVvalue(0); accountPageScroll.setHvalue(0);
        accountPageVisible = content != auctionWorkspace;
        pageBack.setVisible(accountPageVisible || roomMode);
        pageBack.setDisable(!accountPageVisible && leaveButton.isDisabled());
    }

    private void closeAccountPages() {
        if (embeddedWallet != null) { embeddedWallet.close(); embeddedWallet = null; }
        if (embeddedTransactions != null) { embeddedTransactions.close(); embeddedTransactions = null; }
        if (embeddedReceipts != null) { embeddedReceipts.close(); embeddedReceipts = null; }
        if (embeddedProducts != null) { embeddedProducts.close(); embeddedProducts = null; }
        if (embeddedRules != null) { embeddedRules.close(); embeddedRules = null; }
        if (walletNavigation != null) { walletNavigation.getStyleClass().remove("nav-active"); }
        if (transactionsNavigation != null) { transactionsNavigation.getStyleClass().remove("nav-active"); }
        if (receiptsNavigation != null) { receiptsNavigation.getStyleClass().remove("nav-active"); }
        if (productsNavigation != null) { productsNavigation.getStyleClass().remove("nav-active"); }
        if (rulesNavigation != null) { rulesNavigation.getStyleClass().remove("nav-active"); }
    }
    private void showRules() {
        if (accountPageHost == null || embeddedRules != null) { return; }
        closeAccountPages(); embeddedRules = new AuctionRulesPane();
        VBox.setVgrow(embeddedRules, Priority.ALWAYS); VBox.setMargin(embeddedRules, new Insets(4, 12, 10, 12));
        showAccountContent(embeddedRules);
        accountBreadcrumb.setText("Luật đấu giá"); marketplaceNavigation.getStyleClass().remove("nav-active"); rulesNavigation.getStyleClass().add("nav-active");
        stage.setTitle("Đấu giá • Luật đấu giá");
    }
    private CompletableFuture<SellerProductsPane.Preview> loadSellerPreview(ClientProduct product) {
        return ProductMediaApi.connect(auctions, transport, model).thenCompose(api -> api.manifest(product.getProductId(), 0).thenApply(body -> {
            int images = 0, videos = 0;
            for (var item : body.path("media")) { if ("IMAGE".equals(item.path("kind").asText())) { images++; } else if ("VIDEO".equals(item.path("kind").asText())) { videos++; } }
            var first = ProductMediaApi.firstImage(body);
            Image image = first == null || first.path("thumbnail").asText().isBlank() ? null : new Image(api.url(first.path("thumbnail").asText()), true);
            return new SellerProductsPane.Preview(image, images, videos);
        }));
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
            dialog.initStyle(javafx.stage.StageStyle.UNDECORATED);
            AccountProfileForm form = new AccountProfileForm(model, () -> socialRoom.editOwnProfile());
            ScrollPane scroll = new ScrollPane(form); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("profile-scroll");
            Button close = AccountUi.close(() -> dialog.close());
            HBox heading = AccountUi.heading("Hồ sơ tài khoản", "Quản lý thông tin tài khoản và cài đặt hiển thị của bạn", "profile", close);
            VBox body = new VBox(13, heading, scroll); VBox.setVgrow(scroll, Priority.ALWAYS); body.setPrefHeight(590); body.setMinHeight(0);
            dialog.getDialogPane().setContent(body); dialog.getDialogPane().getStyleClass().addAll("account-page", "profile-dialog");
            dialog.getDialogPane().setPrefWidth(520); dialog.setResizable(true);
            dialog.setOnShown(event -> {
                var profileStage = (Stage) dialog.getDialogPane().getScene().getWindow(); DesktopTheme.fitOnScreen(profileStage, 520, 730);
                AccountUi.draggable(heading, profileStage); AccountUi.dimOwner(profileStage);
                new vn.ptit.btl16.client.service.RoomSocialApi(transport).ownProfile().whenComplete((profile, profileError) -> Platform.runLater(() -> {
                    if (dialog.isShowing() && current(request, false) && profileError == null && profile != null && profile.isSuccess()) { form.setAvatar(profile.get("avatarBase64")); }
                }));
            });
            ButtonType save = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
            ButtonType changePassword = new ButtonType("Đổi mật khẩu", ButtonBar.ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().addAll(changePassword, save, new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE));
            ((Button) dialog.getDialogPane().lookupButton(save)).setGraphic(MarketplaceIcons.icon("save", 14, "#fff8ed"));
            ((Button) dialog.getDialogPane().lookupButton(changePassword)).setGraphic(MarketplaceIcons.icon("lock", 14, "#875c2c"));
            dialog.setResultConverter(button -> {
                if (button == changePassword) {
                    showChangePassword();
                    return null;
                }
                return button == save ? form.values() : null;
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
        if (embeddedProducts != null || accountPageHost == null) { return; }
        closeAccountPages();
        long productOwner = model.getUserId(); String productSession = model.getSessionToken();
        java.util.function.BooleanSupplier sameOwner = () -> productOwner == model.getUserId() && productSession.equals(model.getSessionToken()) && !closed.get();
        Button add = new Button("Thêm sản phẩm");
        Button edit = new Button("Sửa");
        Button media = new Button("Ảnh / video");
        Button delete = new Button("Xóa sản phẩm"); delete.setId("delete-product");
        Button refresh = new Button("Làm mới");
        SellerProductsPane view = new SellerProductsPane(this::loadSellerPreview, add, edit, media, delete, refresh, this::showAuctionPage);
        embeddedProducts = view;
        java.util.function.BooleanSupplier active = () -> embeddedProducts == view && sameOwner.getAsBoolean();
        VBox.setVgrow(view, Priority.ALWAYS); VBox.setMargin(view, new Insets(4, 12, 14, 12));
        showAccountContent(view);
        accountBreadcrumb.setText("Sản phẩm bán"); marketplaceNavigation.getStyleClass().remove("nav-active"); productsNavigation.getStyleClass().add("nav-active");
        stage.setTitle("Đấu giá • Sản phẩm bán của " + model.getUsername());
        TableView<ClientProduct> table = view.table();
        media.setOnAction(event -> {
            ClientProduct selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) { showMediaGallery(selected.getProductId(), 0, selected.getName()); }
        });
        SimpleBooleanProperty busy = new SimpleBooleanProperty(true);
        Runnable updateActions = () -> {
            ClientProduct selected = table.getSelectionModel().getSelectedItem();
            add.setDisable(busy.get());
            ProductTransactionStatus transactionStatus = selected == null ? null : selected.getTransactionStatus();
            edit.setDisable(busy.get() || transactionStatus != ProductTransactionStatus.UNTRADED);
            media.setDisable(busy.get() || selected == null);
            delete.setDisable(busy.get() || (transactionStatus != ProductTransactionStatus.UNTRADED && transactionStatus != ProductTransactionStatus.SOLD));
            refresh.setDisable(busy.get());
        };
        busy.addListener((observable, previous, current) -> updateActions.run());
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> updateActions.run());
        Runnable reload = () -> {
            if (!active.getAsBoolean() || busy.get()) { return; }
            ClientProduct selected = table.getSelectionModel().getSelectedItem();
            busy.set(true);
            auctions.myProducts().whenComplete((response, error) -> Platform.runLater(() -> {
                    busy.set(false);
                    if (!active.getAsBoolean()) {
                        return;
                    }
                    if (success(response, error)) {
                        view.setProducts(ClientWireParser.products(response.getWireMessage().getData()).stream()
                                .filter(product -> product.getOwnerId() == productOwner).toList(), selected == null ? null : selected.getProductId());
                    }
                    updateActions.run();
                }));
        };
        add.setOnAction(event -> showProductForm(null, reload));
        table.setRowFactory(tableView -> {
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
        delete.setOnAction(event -> {
            ClientProduct selected = table.getSelectionModel().getSelectedItem();
            if (!active.getAsBoolean()) { return; }
            if (selected != null && confirm("Xóa sản phẩm “" + selected.getName() + "”? Sản phẩm sẽ bị xóa khỏi danh sách và không thể khôi phục. Lịch sử đấu giá và giao dịch vẫn được giữ nguyên.") && sameOwner.getAsBoolean()) {
                busy.set(true);
                auctions.deleteProduct(selected.getProductId()).whenComplete((response, error) ->
                        Platform.runLater(() -> {
                            if (!active.getAsBoolean()) { return; }
                            busy.set(false);
                            success(response, error);
                            reload.run();
                        }));
            }
        });
        refresh.setOnAction(event -> reload.run());
        busy.set(false);
        updateActions.run();
        reload.run();
        view.startPolling(reload);
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
                    .stream().filter(ClientProduct::canAuction).toList();
            if (products.isEmpty()) {
                sounds.play(ClientSounds.Cue.ERROR);
                boolean retry = new AuctionEligibilityDialog(stage).showAndWait().orElse(false);
                finishCreatingAuction();
                if (retry && current(request, false)) { showCreateAuction(); }
                return;
            }
            var dialog = new AuctionCreationDialog(stage, products, model::serverNow);
            var chosen = dialog.showAndWait();
            if (chosen.isEmpty() || !current(request, false)) { finishCreatingAuction(); return; }
            var data = chosen.get();
            auctions.createAuction(
                            data.product().getProductId(), data.startPrice(), data.increment(), data.durationMinutes(),
                            data.visibility(), data.roomPassword(), data.anonymousGuests(), data.startTime(),
                            data.maxParticipants(), data.depositAmount())
                    .whenComplete((created, createError) -> Platform.runLater(() -> {
                        finishCreatingAuction();
                        if (!current(request, false)) { return; }
                        if (success(created, createError)) {
                            applySnapshot(created, false);
                            createdAuctionId = ClientWireParser.longValue(created.getWireMessage().getData(), "auctionId", 0L);
                            createdRoomCode.setCode(created.get("roomCode"));
                            if (data.startTime() != null && model.serverNow().isBefore(data.startTime())) { phaseTabs.get(2).fire(); }
                            renderAuctions();
                            appendEvent("Đã tạo phòng #" + created.get("auctionId"));
                        }
                        else if (createError != null) { loadAuctionList(); showError("Chưa xác nhận tạo phòng. Hãy kiểm tra sảnh/phiên của tôi trước khi thử lại."); }
                    }));
        }));
    }

    private void finishCreatingAuction() { creatingAuction = false; if (createAuctionAction != null) { createAuctionAction.setDisable(false); } }

    private void clearCreatedRoomCode() {
        createdAuctionId = 0L;
        createdRoomCode.setCode("");
    }

    private void refreshCreatedRoomCode() {
        if (createdAuctionId != 0L && model.auctionSnapshot().stream()
                .noneMatch(auction -> auction.getAuctionId() == createdAuctionId && auction.isOpen())) {
            clearCreatedRoomCode();
        }
    }

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
                clearCreatedRoomCode();
                friendsPane.reset();
                clearNotificationCount();
                bidVoice.resetSession();
                auctionCards.clearFavorites(); recentActivity.clear(); eventLog.clear();
                socialRoom.clear();
                congratulations.clear();
                winnerCelebration.clear();
                auctionCards.getItems().clear(); auctionCards.clearFeatured();
                showAuctionPage(); stage.setScene(loginScene);
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
            clearNotificationCount();
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
            updateHostButtons(auctionCards.getSelectionModel().getSelectedItem());
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
        friendsPane.onEvent(message);
        Map<String, String> data = message.getData();
        switch (message.getType()) {
            case ROOM_CHAT_MESSAGE -> socialRoom.onMessage(data);
            case ROOM_INTERACTION_CHANGED -> socialRoom.onInteraction(data);
            case CONNECTION_WELCOME -> appendEvent("Đã kết nối vào sàn");
            case ROOM_OCCUPANCY_CHANGED -> {
                auctionCards.applyOccupancy(ClientWireParser.longValue(data, "auctionId", 0),
                        ClientWireParser.integer(data, "watcherCount", -1), ClientWireParser.longValue(data, "occupancyVersion", 0));
                model.applyOccupancy(ClientWireParser.longValue(data, "auctionId", 0),
                        ClientWireParser.integer(data, "watcherCount", -1),
                        ClientWireParser.longValue(data, "occupancyVersion", 0), ClientWireParser.serverNow(data));
                renderAuctions();
            }
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
                auctionCards.refresh();
                renderDetails(auctionCards.getSelectionModel().getSelectedItem());
            }
            case AUCTION_EXTENDED -> {
                loadAuctionList();
                appendEvent("Đã gia hạn phòng #" + data.getOrDefault("auctionId", ""));
            }
            case AUCTION_ENDED, AUCTION_CANCELLED -> {
                if (message.getType() == MessageType.AUCTION_ENDED) {
                    congratulations.announce(data);
                }
                ClientAuction auction = ClientWireParser.auction(data, "");
                model.applySnapshot(auction, model.currentBidSnapshot(),
                        ClientWireParser.serverNow(data), false);
                if (java.util.Objects.equals(model.getJoinedAuctionId(), auction.getAuctionId())) {
                    bidVoice.clearRoom(auction.getAuctionId());
                    bidLeadingBanner.clearRoom(auction.getAuctionId());
                    if (transport.isConnected()) { sounds.playOnce(ClientSounds.Cue.AUCTION_END, "ended:" + auction.getAuctionId()); }
                    lastRoomSignal = System.nanoTime(); roomWalletChanged();
                    showConfirmedReceipt(auction.getAuctionId());
                }
                renderAuctions();
                if (message.getType() == MessageType.AUCTION_ENDED && model.hasSessionToken()
                        && winnerCelebration.announce(data, model.getUserId()) && transport.isConnected()) {
                    bidVoice.congratulate(data.getOrDefault("productName", ""));
                }
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
                showError(data.getOrDefault("message", "Bạn đã được mời ra khỏi phòng"));
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
            case ERROR -> appendEvent("Thông báo: " + ClientMessages.error(data.getOrDefault("errorCode", ""), data.getOrDefault("message", "")));
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
        ClientAuction active = model.joinedAuction();
        if (active != null) {
            if (active.isOpen()) {
                bidVoice.setRoom(active.getAuctionId(), model.currentBidSnapshot());
                bidLeadingBanner.setRoom(active.getAuctionId(), model.currentBidSnapshot(), active.getCurrentPrice());
            } else {
                bidVoice.clearRoom(active.getAuctionId());
                bidLeadingBanner.clearRoom(active.getAuctionId());
            }
        }
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
        java.util.function.BooleanSupplier sameSession = () -> model.getUserId() == userId && model.getSessionToken().equals(token) && !closed.get();
        if (accountPageHost == null) { new AuctionReceiptsPane(stage, transport, sameSession).show(room); return; }
        if (embeddedReceipts != null) { if (room > 0) { embeddedReceipts.loadReceipt(room); } return; }
        closeAccountPages();
        embeddedReceipts = new AuctionReceiptsPane(stage, transport, sameSession, true);
        VBox.setVgrow(embeddedReceipts, Priority.ALWAYS);
        showAccountContent(embeddedReceipts);
        accountBreadcrumb.setText("Kết quả phiên của tôi"); marketplaceNavigation.getStyleClass().remove("nav-active"); receiptsNavigation.getStyleClass().add("nav-active");
        stage.setTitle("Đấu giá • Kết quả phiên của " + model.getUsername()); embeddedReceipts.startEmbedded(room);
    }

    private void applyBidUpdate(Map<String, String> data) {
        model.applyAuctionUpdate(
                ClientWireParser.auction(data, ""),
                ClientWireParser.bid(data, "bid."),
                ClientWireParser.serverNow(data));
        if (java.util.Objects.equals(model.getJoinedAuctionId(), ClientWireParser.longValue(data, "auctionId", 0L))) {
            ClientAuction joined = model.joinedAuction();
            if (transport.isConnected() && model.hasSessionToken() && joined != null && joined.isOpen()) {
                var bid = ClientWireParser.bid(data, "bid.");
                boolean shown = bidLeadingBanner.announce(bid, joined.getCurrentPrice());
                Runnable finished = shown ? bidLeadingBanner.holdForSpeech() : null;
                if (!bidVoice.announce(bid, joined.getCurrentPrice(), finished) && shown) {
                    bidLeadingBanner.useDefaultDuration();
                }
            }
            lastRoomSignal = System.nanoTime();
            confirmUncertainBid();
            roomWalletChanged();
        }
    }

    private void renderAuctions() {
        refreshCreatedRoomCode();
        var snapshot = model.auctionSnapshot().stream().filter(value -> value.isOpen()
                || value.hasRecentResult(model.serverNow())).toList();
        roomCount.setText(Integer.toString(snapshot.size()));
        nextLobbyStart = snapshot.stream().filter(value -> phaseMatches(value, "UPCOMING")).map(ClientAuction::getStartTime).min(Instant::compareTo).orElse(null);
        openCount.setText(Long.toString(snapshot.stream().filter(value -> phaseMatches(value, "LIVE")).count()));
        participantCount.setText(Integer.toString(snapshot.stream().mapToInt(ClientAuction::getWatcherCount).sum()));
        joinedRoom.setText(model.getJoinedAuctionId() == null ? "—" : "#" + model.getJoinedAuctionId());
        ClientAuction selected = auctionCards.getSelectionModel().getSelectedItem();
        long selectedId = selected == null ? 0L : selected.getAuctionId();
        ClientAuction activeRoom = model.joinedAuction();
        long preferredId = activeRoom != null ? activeRoom.getAuctionId() : selectedId;
        renderingAuctions = true;
        try {
            var matching = vn.ptit.btl16.client.model.LobbyQuery.filter(snapshot, model.getUserId(),
                    lobbyVisibility.getValue() == null ? "ALL" : lobbyVisibility.getValue(),
                    lobbyIdentity.getValue() == null ? "ALL" : lobbyIdentity.getValue(), lobbyHosted.isSelected(),
                    searchMode.getValue() == null ? "ALL" : searchMode.getValue(), searchField.getText(),
                    lobbyPhase.equals("UPCOMING") ? "STARTING" : lobbyOrder.getValue() == null ? "ENDING" : lobbyOrder.getValue(), model.serverNow());
            String[] phases = {"ALL", "LIVE", "UPCOMING"};
            String[] labels = {"Tất cả sản phẩm", "Đang đấu giá", "Sắp diễn ra"};
            for (int index = 0; index < phases.length; index++) {
                String phase = phases[index]; phaseTabs.get(index).setText(labels[index] + " (" + matching.stream().filter(value -> phaseMatches(value, phase)).count() + ")");
            }
            var filtered = matching.stream().filter(value -> phaseMatches(value, lobbyPhase)).toList();
            int pages = Math.max(1, (filtered.size() + 29) / 30); lobbyPage = Math.min(lobbyPage, pages - 1);
            lobbyPageLabel.setText((lobbyPage + 1) + "/" + pages + " · " + filtered.size() + " sản phẩm");
            lobbyPrevious.setDisable(lobbyPage == 0); lobbyNext.setDisable(lobbyPage + 1 >= pages);
            if (lobbyPageLabel.getParent() != null) { lobbyPageLabel.getParent().setVisible(pages > 1); lobbyPageLabel.getParent().setManaged(pages > 1); }
            auctionCards.getItems().setAll(activeRoom != null ? List.of(activeRoom)
                    : vn.ptit.btl16.client.model.LobbyQuery.page(filtered, lobbyPage, 30));
            auctionCards.getItems().stream().filter(value -> value.getAuctionId() == preferredId)
                    .findFirst().ifPresent(value -> auctionCards.getSelectionModel().select(value));
            if (auctionCards.getSelectionModel().getSelectedItem() == null && !auctionCards.getItems().isEmpty()) { auctionCards.getSelectionModel().select(0); }
        } finally {
            renderingAuctions = false;
        }
        renderDetails(auctionCards.getSelectionModel().getSelectedItem());
    }

    private void selectLobbyPhase(String phase) {
        boolean wasUpcoming = lobbyPhase.equals("UPCOMING"), upcoming = phase.equals("UPCOMING");
        if (upcoming && !wasUpcoming) { lobbyOrderBeforeUpcoming = lobbyOrder.getValue() == null ? "NEWEST" : lobbyOrder.getValue(); }
        lobbyPhase = phase; lobbyPage = 0;
        lobbyOrder.setDisable(upcoming);
        if (upcoming) { lobbyOrder.setValue("STARTING"); }
        else if (wasUpcoming) { lobbyOrder.setValue(lobbyOrderBeforeUpcoming); }
        renderAuctions();
    }

    private boolean phaseMatches(ClientAuction auction, String phase) {
        boolean upcoming = auction.isOpen() && auction.getStartTime() != null && model.serverNow().isBefore(auction.getStartTime());
        return switch (phase) { case "LIVE" -> auction.isOpen() && !upcoming; case "UPCOMING" -> upcoming; default -> true; };
    }

    private void renderDetails(ClientAuction auction) {
        if (!roomMode) {
            if (auction == null) { listingSplit.getItems().remove(detailPanel); }
            else if (!listingSplit.getItems().contains(detailPanel)) {
                listingSplit.getItems().add(detailPanel); listingSplit.setDividerPositions(.63);
            }
        }
        if (auction == null) {
            detailName.setText("Chọn một phòng");
            detailRoomCode.setCode("");
            detailHost.setText("");
            detailPrice.setText("");
            detailLeader.setText("");
            detailTime.setText("");
            AuctionDeadlineStyle.update(detailTime, null, model.serverNow());
            detailVisibility.setText("");
            detailDescription.clear();
            productImage.setImage(null);
            productImageKey = "";
            updateHostButtons(null);
            return;
        }
        boolean personalRoom = java.util.Objects.equals(model.getJoinedAuctionId(), auction.getAuctionId());
        detailRoomCode.setCode(auction.getRoomCode());
        detailName.setText((personalRoom ? "#" + auction.getAuctionId() + " - " : "") + auction.getProductName());
        detailHost.setText("Chủ phòng: " + auction.getHostUsername() + (personalRoom ? "  •  Bước giá " + money(auction.getMinBidIncrement()) : ""));
        detailStep.setText(money(auction.getMinBidIncrement())); detailTag.setText(auction.requiresPassword() ? "Riêng tư" : "Công khai");
        detailAuctionBadge.setText("#" + auction.getAuctionId() + " · " + (!auction.isOpen() ? "ĐÃ KẾT THÚC" : phaseMatches(auction, "UPCOMING") ? "SẮP DIỄN RA" : "Đang đấu giá"));
        detailAuctionBadge.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("ended"), !auction.isOpen());
        detailAuctionBadge.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("upcoming"), phaseMatches(auction, "UPCOMING"));
        String price = money(auction.getCurrentPrice());
        if (!price.equals(detailPrice.getText())) {
            detailPrice.setText(price);
            DesktopTheme.reveal(detailPrice);
        }
        detailLeader.setText("Người dẫn đầu: " + (auction.getCurrentWinnerUsername().isBlank()
                ? "Chưa có" : auction.getCurrentWinnerUsername()));
        detailTime.setText(countdownText(auction));
        AuctionDeadlineStyle.update(detailTime, auction, model.serverNow());
        detailVisibility.setText((!auction.isOpen() ? "ĐÃ KẾT THÚC" :
                auction.requiresPassword() ? "PHÒNG RIÊNG TƯ" : "Phòng công khai")
                + (auction.hasAnonymousGuests() ? " · khách ẩn danh" : ""));
        detailDescription.setText(auction.getWatcherCount() + "/" + auction.getMaxParticipants() + " người đang trong phòng"
                + (auction.isFull() ? " · Đã đầy" : " · Còn " + (auction.getMaxParticipants() - auction.getWatcherCount()) + " chỗ")
                + (auction.getDepositAmount().signum() > 0 ? "\nĐặt cọc trước giờ bắt đầu: " + money(auction.getDepositAmount()) : "")
                + "\n\n" + auction.getDescription());
        updateHostButtons(auction);
        if (!roomMode) {
            ((Label) ((VBox) lobbyTimeRow.getChildren().get(1)).getChildren().get(0)).setText(
                    phaseMatches(auction, "UPCOMING") ? "Thời gian bắt đầu" : "Thời gian còn lại");
        }
        detailTime.setText(roomMode || !auction.isOpen() ? countdownText(auction) : remainingText(auction));
        detailLeader.setVisible(roomMode || auction.getCurrentWinnerId() != null); detailLeader.setManaged(detailLeader.isVisible());
        if (!roomMode) { bidField.setPromptText("Tối thiểu " + money(auction.getCurrentPrice().add(auction.getMinBidIncrement()))); }
        refreshFavorite();
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
        loadCoverImage(auction, variant)
                .whenComplete((image, error) -> Platform.runLater(() -> {
                    imageRequests.remove(key);
                    ClientAuction current = auctionCards.getSelectionModel().getSelectedItem();
                    if (current != null && current.getAuctionId() == auction.getAuctionId() && productImageKey.equals(key)) {
                        productImage.setImage(error == null ? image : null);
                        if (error != null || image == null) { productImageKey = ""; }
                        else { image.errorProperty().addListener((observable, before, failed) -> {
                            if (failed && productImageKey.equals(key)) { productImageKey = ""; }
                        }); }
                    }
                }));
    }

    private CompletableFuture<Image> loadCoverImage(ClientAuction auction, String variant) {
        String token = model.getSessionToken();
        if (coverApi == null || coverApi.isCompletedExceptionally() || !coverSession.equals(token) || coverConnection != connectionGeneration) {
            coverSession = token; coverConnection = connectionGeneration;
            coverApi = ProductMediaApi.connect(auctions, transport, model);
        }
        return coverApi.thenCompose(api -> api.manifest(auction.getProductId(), auction.getAuctionId()).thenApply(body -> {
            var first = ProductMediaApi.firstImage(body);
            return first == null || first.path(variant).asText().isBlank() ? null
                    : new Image(api.url(first.path(variant).asText()), true);
        }));
    }

    private void refreshClock() {
        friendsPane.tick();
        if (System.nanoTime() - lastNotificationRefresh > TimeUnit.SECONDS.toNanos(5)) { refreshNotificationCount(); }
        if (System.nanoTime() - lastFeaturedRefresh > TimeUnit.SECONDS.toNanos(5)) { loadFeaturedAuctions(); }
        socialRoom.tick();
        recentActivity.tick();
        if (nextLobbyStart != null && !model.serverNow().isBefore(nextLobbyStart)) { renderAuctions(); }
        var expiredResults = model.auctionSnapshot().stream()
                .filter(value -> !value.isOpen() && !value.hasRecentResult(model.serverNow())).toList();
        if (!expiredResults.isEmpty()) {
            boolean leaving = expiredResults.stream().anyMatch(value -> java.util.Objects.equals(model.getJoinedAuctionId(), value.getAuctionId()));
            if (leaving) { invalidateRoom(true); }
            for (ClientAuction result : expiredResults) { model.removeAuction(result.getAuctionId(), model.serverNow()); }
            renderAuctions();
            if (leaving) { appendEvent("Đã đóng màn hình kết quả sau 60 giây. Lịch sử và thanh toán vẫn được lưu trên máy chủ."); }
        }
        ClientAuction joined = model.joinedAuction();
        if (transport.isConnected() && roomSnapshotReady && !roomSyncing && !roomChanging
                && System.nanoTime() - lastRoomSignal < ROOM_FRESH_NANOS
                && joined != null && joined.isOpen() && joined.getEndTime() != null) {
            bidVoice.remindClosing(joined, model.serverNow());
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
        auctionCards.refresh();
        ClientAuction selected = auctionCards.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (roomMode) { liveRoom.tick(model.serverNow(), transport.isConnected()); }
            detailTime.setText(roomMode || !selected.isOpen() ? countdownText(selected) : remainingText(selected));
            AuctionDeadlineStyle.update(detailTime, selected, model.serverNow());
        }
    }

    private String countdownText(ClientAuction auction) {
        if (auction.isOpen()) { return "Còn lại: " + remainingText(auction); }
        Instant closed = auction.getEndedAt() == null ? auction.getEndTime() : auction.getEndedAt();
        long seconds = closed == null ? 0 : Math.max(0, java.time.Duration.between(model.serverNow(), closed.plusSeconds(60)).getSeconds());
        return (roomMode ? "Tự rời phòng sau " : "Đã kết thúc · Ẩn sau ") + String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private String remainingText(ClientAuction auction) {
        if (auction.getEndTime() == null || !auction.isOpen()) {
            return "00:00";
        }
        if (auction.getStartTime() != null && model.serverNow().isBefore(auction.getStartTime())) {
            return Times.display(auction.getStartTime());
        }
        long seconds = Math.max(0L,
                java.time.Duration.between(model.serverNow(), auction.getEndTime()).getSeconds());
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private ClientAuction selectedAuction() {
        ClientAuction selected = auctionCards.getSelectionModel().getSelectedItem();
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
        if (!depositSession.equals(model.getSessionToken())) { depositedRooms.clear(); depositSession = model.getSessionToken(); }
        boolean upcoming = auction != null && auction.isOpen() && phaseMatches(auction, "UPCOMING");
        boolean paid = auction != null && depositedRooms.contains(auction.getAuctionId());
        joinButton.setText(placingDeposit ? "Đang đặt cọc..." : upcoming
                ? hosted || auction.getDepositAmount().signum() <= 0 ? "Chờ bắt đầu" : paid ? "Đã đặt cọc · Chờ bắt đầu" : "Đặt cọc " + money(auction.getDepositAmount())
                : auction != null && auction.isFull() && !joined && !hosted ? "Phòng đã đầy" : "Vào phòng");
        joinButton.setDisable(joined || roomChanging || placingDeposit || !transport.isConnected()
                || (upcoming ? hosted || paid || auction.getDepositAmount().signum() <= 0 : !available || (!hosted && auction.isFull())));
        leaveButton.setDisable(model.getJoinedAuctionId() == null || roomChanging || !transport.isConnected());
        if (pageBack != null) { pageBack.setDisable(!accountPageVisible && leaveButton.isDisabled()); }
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
                : "Ch\u1ec9 \u0111\u01b0\u1ee3c gia h\u1ea1n, k\u1ebft th\u00fac s\u1edbm, h\u1ee7y khi ch\u01b0a c\u00f3 l\u01b0\u1ee3t h\u1ee3p l\u1ec7. Máy chủ ki\u1ec3m tra l\u1ea1i m\u1ecdi thao t\u00e1c.");
        updateRoomLayout(joined);
        renderRoomActivity(auction, joined, hosted, leading);
    }

    private void updateRoomLayout(boolean joined) {
        backgroundMusic.setInAuctionRoom(joined && transport.isConnected());
        if (roomMode == joined) { return; }
        roomMode = joined;
        if (pageBack != null) { pageBack.setVisible(accountPageVisible || joined); }
        if (joined) {
            bidVoice.setRoom(model.getJoinedAuctionId(), model.currentBidSnapshot());
            bidLeadingBanner.setRoom(model.getJoinedAuctionId(), model.currentBidSnapshot(), model.joinedAuction().getCurrentPrice());
        } else { bidVoice.clear(); bidLeadingBanner.clear(); }
        liveRoom.insights().setVisible(joined); liveRoom.insights().setManaged(joined);
        if (!joined) { liveRoom.clear(); socialRoom.clear(); }
        statsPanel.setVisible(!joined); statsPanel.setManaged(!joined);
        marketplaceBanner.setVisible(!joined); marketplaceBanner.setManaged(!joined);
        auctionCards.showFeatured(!joined);
        lobbyToolbar.setVisible(!joined); lobbyToolbar.setManaged(!joined);
        if (joined) { lobbyFilters.setVisible(false); lobbyFilters.setManaged(false); }
        roomHeading.setVisible(joined); roomHeading.setManaged(joined);
        joinButton.setVisible(!joined); joinButton.setManaged(!joined);
        bidSuggestions.setVisible(joined); bidSuggestions.setManaged(joined);
        bidHint.setVisible(joined); bidHint.setManaged(joined);
        productDescription.setExpanded(joined);
        membership.setVisible(joined); membership.setManaged(joined);
        roomFeeHint.setVisible(joined); roomFeeHint.setManaged(joined);
        bidAmountCaption.setVisible(!joined); bidAmountCaption.setManaged(!joined);
        previewOverlay.setVisible(!joined); previewOverlay.setManaged(!joined);
        updateRoomImageSize();
        updateProductCrop();
        detailPanel.setCenter(null); detailPanel.setBottom(null);
        if (joined) {
            detailPanel.setMaxWidth(Double.MAX_VALUE); SplitPane.setResizableWithParent(detailPanel, true);
            lobbyPreview.getChildren().clear(); lobbyDetailColumn.getChildren().clear();
            listingSplit.getItems().remove(listingPanel);
            if (!listingSplit.getItems().contains(detailPanel)) { listingSplit.getItems().add(detailPanel); }
            productDetails.getChildren().clear();
            lobbyNameRow.getChildren().clear(); lobbyPriceAmount.getChildren().clear();
            ((VBox) lobbyTimeRow.getChildren().get(1)).getChildren().clear();
            lobbyHostRow.getChildren().remove(detailHost);
            roomProductIdentity.getChildren().setAll(detailName, detailHost);
            HBox.setHgrow(roomProductIdentity, Priority.ALWAYS);
            roomProductIdentity.setMinWidth(0);
            roomProductSummary.getChildren().setAll(roomProductIdentity, detailRoomCode, productHeader, roomImage);
            ClientAuction receiptAuction = model.joinedAuction();
            if (receiptAuction != null && !receiptAuction.isOpen()) {
                Button receipt = new Button("Xem bi\u00ean nh\u1eadn c\u1ee7a t\u00f4i");
                receipt.setId("room-personal-receipt"); receipt.setOnAction(event -> showPersonalReceipt(receiptAuction.getAuctionId()));
                roomProductSummary.getChildren().add(receipt);
            }
            socialRoom.setAuctionPanels(liveRoom.insights(), null);
            roomProductContent.getChildren().setAll(roomProductSummary, roomProductInformation, managementPane);
            roomMain.getChildren().setAll(roomProductContent, socialRoom.auctionContent());
            roomProductTab.setSelected(true);
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
            roomBidTab.setSelected(true); roomBidScroll.setVvalue(0); roomHistoryScroll.setVvalue(0);
            HBox mainTabButtons = new HBox(4, roomProductTab, roomActivityTab);
            mainTabButtons.getStyleClass().addAll("room-side-tabs", "room-main-tabs");
            mainTabButtons.setMinHeight(Region.USE_PREF_SIZE);
            VBox mainColumn = new VBox(10, mainTabButtons, roomMainScroll);
            mainColumn.setMinSize(300, 0); HBox.setHgrow(mainColumn, Priority.ALWAYS);
            VBox.setVgrow(roomMainScroll, Priority.ALWAYS);
            roomColumns.getChildren().setAll(mainColumn, roomSide);
            detailPanel.setCenter(roomColumns);
            detailsScroll.setVvalue(0); roomMainScroll.setVvalue(0); roomControlsScroll.setVvalue(0);
        } else {
            if (roomEventScroll != null) { roomEventScroll.stop(); }
            roomProductContent.getChildren().clear();
            if (roomMainScroll.getParent() instanceof VBox mainColumn) { mainColumn.getChildren().clear(); }
            roomMain.getChildren().clear(); socialRoom.setAuctionPanels(null, null);
            roomColumns.getChildren().clear();
            roomBidScroll.setContent(null);
            bidding.getChildren().remove(priceBlock);
            priceBlock.getChildren().clear();
            roomProductSummary.getChildren().clear();
            roomProductIdentity.getChildren().clear();
            restoreLobbyProduct(productDetails);
            bidding.setMinHeight(Region.USE_COMPUTED_SIZE);
            bidding.setSpacing(6);
            bidding.getStyleClass().setAll("market-lobby-bidding"); bidding.setPadding(new Insets(6, 9, 8, 9));
            productDetails.getStyleClass().remove("room-product");
            lobbyPreview.getChildren().setAll(productDetails, bidding);
            lobbyDetailColumn.getChildren().setAll(lobbyPreview, recentActivity, productDescription, managementPane);
            detailPanel.setCenter(detailsScroll);
            detailPanel.setMaxWidth(350); SplitPane.setResizableWithParent(detailPanel, false);
            listingSplit.getItems().add(0, listingPanel); listingSplit.setDividerPositions(0.63);
            pageTitle.setText("Sàn đấu giá"); pageEyebrow.setText("Khám phá & Đấu giá");
        }
    }

    private void renderRoomActivity(ClientAuction auction, boolean joined, boolean hosted, boolean leading) {
        roomFunds.setVisible(joined); roomFunds.setManaged(joined);
        roomConnection.getParent().setVisible(joined); roomConnection.getParent().setManaged(joined);
        if (!joined || auction == null) { return; }
        roomProductInformation.update(auction);
        pageTitle.setText("Ph\u00f2ng \u0111\u1ea5u gi\u00e1 #" + auction.getAuctionId());
        pageEyebrow.setText(hosted ? "Quản lý phòng" : "Phòng đang tham gia");
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
                : !model.serverNow().isBefore(auction.getEndTime()) ? "H\u1ebft gi\u1edd nh\u1eadn gi\u00e1; ch\u1edd máy chủ ch\u1ed1t."
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
        double height = roomMode ? Math.max(240, Math.min(420, roomColumns.getHeight() * .6)) : 152;
        roomImage.setMinHeight(roomMode ? height : 150);
        roomImage.setMinWidth(0);
        roomImage.setPrefWidth(300);
        roomImage.setMaxWidth(Double.MAX_VALUE);
        roomImage.setPrefHeight(height); roomImage.setMaxHeight(height);
    }

    private boolean success(ApiResponse response, Throwable error) {
        if (error != null) {
            showError(rootMessage(error));
            return false;
        }
        if (response == null || !response.isSuccess()) {
            String message = response == null ? "Máy chủ chưa phản hồi. Vui lòng thử lại." : response.getUserMessage();
            showError(message.isBlank() ? "Không thể thực hiện thao tác. Vui lòng thử lại." : message);
            return false;
        }
        return true;
    }

    private void setLoginBusy(boolean busy) {
        loginButton.setDisable(busy);
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
        registerButton.setDisable(busy);
        connectButton.setDisable(busy);
    }

    private void showLoginError(String message, boolean invalidCredentials) {
        loginErrorLabel.setText(message);
        loginErrorBanner.setVisible(true);
        usernameField.pseudoClassStateChanged(INVALID, invalidCredentials);
        passwordField.pseudoClassStateChanged(INVALID, invalidCredentials);
        sounds.play(ClientSounds.Cue.ERROR);
    }

    private void clearLoginError() {
        loginErrorBanner.setVisible(false);
        loginErrorLabel.setText("");
        usernameField.pseudoClassStateChanged(INVALID, false);
        passwordField.pseudoClassStateChanged(INVALID, false);
    }

    private void appendEvent(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String line = DATE_TIME.format(Instant.now()) + " | " + message + System.lineSeparator();
        eventLog.appendText(line);
        recentActivity.add(message);
        if (eventLog.getLength() > 24000) {
            eventLog.deleteText(0, eventLog.getLength() - 16000);
        }
    }

    private void showError(String message) {
        sounds.play(ClientSounds.Cue.ERROR);
        Alert alert = new Alert(Alert.AlertType.ERROR, ClientMessages.text(message));
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        showDialog(alert);
    }

    private void showInfo(String message) {
        sounds.play(ClientSounds.Cue.SUCCESS);
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        showDialog(alert);
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận");
        alert.setHeaderText(null);
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
        return ClientMessages.message(throwable);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        autoReconnect = false;
        closeAccountPages();
        invalidateRoom(true);
        clock.stop();
        if (roomEventScroll != null) { roomEventScroll.stop(); }
        clearNotificationCount();
        if (soundUi != null) { soundUi.close(); }
        congratulations.close();
        winnerCelebration.close();
        bidLeadingBanner.close();
        auctionCards.close();
        bidVoice.close();
        backgroundMusic.close();
        sounds.close();
        socialRoom.clear();
        scheduler.shutdownNow();
        friendsPane.close();
        transport.close();
    }

}
