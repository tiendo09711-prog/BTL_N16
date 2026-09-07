package vn.ptit.btl16.client.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
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
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
    private final Map<String, Image> imageCache = new LinkedHashMap<>();

    private final TextField endpointField = new TextField();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label loginStateLabel = new Label("● Disconnected");
    private final Label loginRttLabel = new Label("RTT: --");
    private final Button loginButton = new Button("Đăng nhập");
    private final Button registerButton = new Button("Đăng ký");
    private final Button connectButton = new Button("Kết nối lại");

    private final Label userLabel = new Label("User: --");
    private final Label connectionLabel = new Label("● Disconnected");
    private final Label rttLabel = new Label("RTT: --");
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
    private final Button kickButton = new Button("Kick user");
    private final Timeline clock = new Timeline();

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
        clock.getKeyFrames().add(new KeyFrame(Duration.millis(500), event -> refreshClock()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        stage.setTitle("BTL16 - Sàn đấu giá realtime");
        stage.setMinWidth(1050);
        stage.setMinHeight(720);
        stage.setScene(loginScene);
        stage.show();
        scheduler.scheduleAtFixedRate(this::heartbeat, 2,
                Math.max(1, config.getHeartbeatIntervalMillis() / 1000), TimeUnit.SECONDS);
        if (!Boolean.getBoolean("btl16.client.manualConnect")) {
            connect(false);
        }
    }

    private void buildScenes() {
        loginScene = new Scene(buildLoginRoot(), 560, 420);
        mainScene = new Scene(buildMainRoot(), 1250, 820);
    }

    private Region buildLoginRoot() {
        endpointField.setText(Boolean.getBoolean("btl16.client.manualConnect") ? "" : transport.getEndpoint());
        endpointField.setPromptText("Dán địa chỉ từ server: ws://192.168.1.10:8890/ws");
        connectButton.setText("Kết nối");
        usernameField.setPromptText("Username");
        passwordField.setPromptText("Password");
        Label title = new Label("BTL16 AUCTION CLIENT");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label endpointLabel = new Label("Địa chỉ server (sao chép từ ứng dụng Server)");
        HBox actions = new HBox(10, loginButton, registerButton, connectButton);
        actions.setAlignment(Pos.CENTER);
        HBox state = new HBox(18, loginStateLabel, loginRttLabel);
        state.setAlignment(Pos.CENTER);
        VBox root = new VBox(12, title, endpointLabel, endpointField,
                new Label("Username"), usernameField,
                new Label("Password"), passwordField,
                actions, new Separator(), state);
        root.setPadding(new Insets(28));
        root.setAlignment(Pos.CENTER_LEFT);
        loginButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(loginButton, Priority.ALWAYS);
        HBox.setHgrow(registerButton, Priority.ALWAYS);
        loginButton.setOnAction(event -> login());
        registerButton.setOnAction(event -> register());
        connectButton.setOnAction(event -> connect(true));
        endpointField.setOnAction(event -> connect(true));
        passwordField.setOnAction(event -> login());
        return root;
    }

    private Region buildMainRoot() {
        Button profileButton = new Button("Profile");
        Button productsButton = new Button("Sản phẩm của tôi");
        Button createAuctionButton = new Button("Tạo phòng");
        Button refreshButton = new Button("Làm mới");
        Button logoutButton = new Button("Logout");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(10, userLabel, connectionLabel, rttLabel, spacer,
                productsButton, createAuctionButton, profileButton, refreshButton, logoutButton);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));

        searchMode.getItems().setAll("ALL", "PRODUCT_NAME", "ROOM_ID");
        searchMode.setValue("ALL");
        searchField.setPromptText("Tên sản phẩm hoặc mã phòng");
        Button searchButton = new Button("Tìm");
        Button clearButton = new Button("Xóa lọc");
        HBox search = new HBox(8, new Label("Tìm kiếm:"), searchField, searchMode,
                searchButton, clearButton);
        search.setPadding(new Insets(0, 10, 10, 10));
        search.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        configureAuctionTable();
        VBox details = buildDetails();
        SplitPane split = new SplitPane(auctionTable, new ScrollPane(details));
        split.setDividerPositions(0.67);

        HBox roomActions = new HBox(8, joinButton, leaveButton, historyButton,
                new Label("Giá bid:"), bidField, bidButton);
        roomActions.setAlignment(Pos.CENTER_LEFT);
        HBox hostActions = new HBox(8, new Label("Host:"), extendButton,
                endButton, cancelButton, kickButton);
        hostActions.setAlignment(Pos.CENTER_LEFT);
        eventLog.setEditable(false);
        eventLog.setWrapText(true);
        eventLog.setPrefRowCount(7);
        VBox bottom = new VBox(8, roomActions, hostActions,
                new Label("Realtime notifications / event log"), eventLog);
        bottom.setPadding(new Insets(10));

        VBox topArea = new VBox(top, search);
        BorderPane root = new BorderPane(split, topArea, null, bottom, null);
        profileButton.setOnAction(event -> showProfile());
        productsButton.setOnAction(event -> showProducts());
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
        extendButton.setOnAction(event -> extendAuction());
        endButton.setOnAction(event -> endAuction());
        cancelButton.setOnAction(event -> cancelAuction());
        kickButton.setOnAction(event -> kickUser());
        auctionTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, value) -> renderDetails(value));
        return root;
    }

    private void configureAuctionTable() {
        addColumn("Mã phòng", value -> "#" + value.getAuctionId(), 85);
        addColumn("Tên sản phẩm", ClientAuction::getProductName, 170);
        addColumn("Chủ phòng", ClientAuction::getHostUsername, 110);
        addColumn("Giá hiện tại", value -> value.getCurrentPrice().toPlainString(), 110);
        addColumn("Người dẫn đầu", ClientAuction::getCurrentWinnerUsername, 115);
        addColumn("Còn lại", this::remainingText, 95);
        addColumn("Trạng thái", ClientAuction::getStatus, 90);
        addColumn("Người xem", value -> Integer.toString(value.getWatcherCount()), 80);
        addColumn("Loại phòng", value -> value.requiresPassword() ? "PRIVATE 🔒" : "PUBLIC", 105);
        auctionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        auctionTable.setPlaceholder(new Label("Chưa có phòng đấu giá"));
    }

    private void addColumn(
            String title,
            java.util.function.Function<ClientAuction, String> mapper,
            double width) {
        TableColumn<ClientAuction, String> column = new TableColumn<>(title);
        column.setCellValueFactory(value -> new SimpleStringProperty(mapper.apply(value.getValue())));
        column.setPrefWidth(width);
        auctionTable.getColumns().add(column);
    }

    private VBox buildDetails() {
        productImage.setFitWidth(300);
        productImage.setFitHeight(220);
        productImage.setPreserveRatio(true);
        detailName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        detailDescription.setEditable(false);
        detailDescription.setWrapText(true);
        detailDescription.setPrefRowCount(7);
        VBox details = new VBox(9, productImage, detailName, detailHost,
                detailPrice, detailLeader, detailTime, detailVisibility,
                new Label("Mô tả"), detailDescription);
        details.setPadding(new Insets(14));
        return details;
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
            autoReconnect = model.hasSessionToken();
            transport.disconnect();
        }
        try {
            transport.setEndpoint(endpointField.getText().trim());
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
            return;
        }
        setLoginBusy(true);
        transport.connect().whenComplete((value, error) -> Platform.runLater(() -> {
            setLoginBusy(false);
            if (error != null) {
                appendEvent("CONNECT FAILED: " + rootMessage(error));
                scheduleReconnect();
                return;
            }
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
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Đăng ký tài khoản");
        TextField username = new TextField(usernameField.getText());
        PasswordField password = new PasswordField();
        PasswordField confirm = new PasswordField();
        TextField displayName = new TextField();
        TextField email = new TextField();
        TextField phone = new TextField();
        GridPane grid = formGrid();
        addRow(grid, 0, "Username", username);
        addRow(grid, 1, "Password", password);
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
        dialog.showAndWait().ifPresent(data -> {
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
        userLabel.setText("User: " + model.getUsername());
        stage.setScene(mainScene);
        renderAuctions();
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
            Optional<String> result = dialog.showAndWait();
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
                renderDetails(auctionTable.getSelectionModel().getSelectedItem());
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
            BigDecimal amount = new BigDecimal(bidField.getText().trim());
            auctions.bid(auctionId, amount).whenComplete((response, error) ->
                    Platform.runLater(() -> {
                        if (success(response, error)) {
                            applyBidUpdate(response.getWireMessage().getData());
                            bidField.clear();
                            renderAuctions();
                        }
                    }));
        } catch (NumberFormatException exception) {
            showError("Giá bid không hợp lệ");
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
            alert.showAndWait();
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
        dialog.showAndWait().ifPresent(value -> {
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
        dialog.showAndWait().ifPresent(username -> auctions
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
            addRow(grid, 0, "Username", new Label(model.getUsername()));
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
            dialog.showAndWait().ifPresent(data -> accounts.updateProfile(
                            data.get("displayName"), data.get("email"), data.get("phone"))
                    .whenComplete((updated, updateError) -> Platform.runLater(() -> {
                        if (success(updated, updateError)) {
                            model.applyIdentity(updated);
                            userLabel.setText("User: " + model.getUsername());
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
        dialog.showAndWait().ifPresent(data -> accounts.changePassword(
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
        productColumn(table, "Mã", ClientProduct::getCode, 110);
        productColumn(table, "Tên", ClientProduct::getName, 220);
        productColumn(table, "Ảnh", value -> value.hasImage() ? "Có" : "Chưa có", 80);
        productColumn(table, "Trạng thái", value -> value.isActive() ? "ACTIVE" : "INACTIVE", 90);
        productColumn(table, "Cập nhật", value -> value.getUpdatedAt() == null
                ? "" : DATE_TIME.format(value.getUpdatedAt()), 160);
        Button add = new Button("Thêm sản phẩm");
        Button edit = new Button("Sửa");
        Button deactivate = new Button("Ngừng sử dụng");
        Button close = new Button("Đóng");
        HBox actions = new HBox(8, add, edit, deactivate, close);
        VBox root = new VBox(10, table, actions);
        root.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        window.setScene(new Scene(root, 760, 470));
        Runnable reload = () -> auctions.myProducts().whenComplete((response, error) ->
                Platform.runLater(() -> {
                    if (success(response, error)) {
                        table.getItems().setAll(
                                ClientWireParser.products(response.getWireMessage().getData()));
                    }
                }));
        add.setOnAction(event -> showProductForm(null, reload));
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
                auctions.deactivateProduct(selected.getProductId()).whenComplete((response, error) ->
                        Platform.runLater(() -> {
                            if (success(response, error)) {
                                reload.run();
                            }
                        }));
            }
        });
        close.setOnAction(event -> window.close());
        window.show();
        reload.run();
    }

    private void showProductForm(ClientProduct product, Runnable onSaved) {
        Dialog<ProductFormData> dialog = new Dialog<>();
        dialog.setTitle(product == null ? "Thêm sản phẩm" : "Sửa sản phẩm");
        TextField code = new TextField(product == null ? "" : product.getCode());
        TextField name = new TextField(product == null ? "" : product.getName());
        TextArea description = new TextArea(product == null ? "" : product.getDescription());
        description.setPrefRowCount(4);
        ImageView preview = new ImageView();
        preview.setFitWidth(220);
        preview.setFitHeight(150);
        preview.setPreserveRatio(true);
        Label fileLabel = new Label(product != null && product.hasImage()
                ? "Giữ ảnh hiện tại nếu không chọn ảnh mới" : "Chưa chọn ảnh");
        Button choose = new Button("Chọn ảnh");
        final File[] selectedFile = new File[1];
        choose.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "PNG/JPEG", "*.png", "*.jpg", "*.jpeg"));
            File file = chooser.showOpenDialog(dialog.getOwner());
            if (file != null) {
                selectedFile[0] = file;
                fileLabel.setText(file.getName());
                preview.setImage(new Image(file.toURI().toString(), 220, 150, true, true));
            }
        });
        GridPane grid = formGrid();
        addRow(grid, 0, "Mã sản phẩm", code);
        addRow(grid, 1, "Tên sản phẩm", name);
        addRow(grid, 2, "Mô tả", description);
        addRow(grid, 3, "Ảnh", new VBox(6, choose, fileLabel, preview));
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        dialog.setResultConverter(button -> {
            if (button.getButtonData() != ButtonBar.ButtonData.OK_DONE) {
                return null;
            }
            if (product == null && selectedFile[0] == null) {
                showError("Sản phẩm mới cần chọn ảnh");
                return null;
            }
            try {
                byte[] bytes = selectedFile[0] == null ? null : Files.readAllBytes(selectedFile[0].toPath());
                String mime = selectedFile[0] == null ? "" : mimeFor(selectedFile[0]);
                String imageName = selectedFile[0] == null ? "" : selectedFile[0].getName();
                return new ProductFormData(code.getText(), name.getText(), description.getText(),
                        bytes, mime, imageName);
            } catch (IOException exception) {
                showError("Không đọc được ảnh: " + exception.getMessage());
                return null;
            }
        });
        dialog.showAndWait().ifPresent(data -> {
            CompletableFuture<ApiResponse> future;
            if (product == null) {
                future = auctions.createProduct(data.code, data.name, data.description,
                        data.imageData, data.imageMime, data.imageName);
            } else if (data.imageData == null) {
                future = auctions.updateProduct(product.getProductId(), data.code,
                        data.name, data.description);
            } else {
                future = auctions.updateProduct(product.getProductId(), data.code,
                        data.name, data.description, data.imageData, data.imageMime, data.imageName);
            }
            future.whenComplete((response, error) -> Platform.runLater(() -> {
                if (success(response, error)) {
                    imageCache.keySet().removeIf(key -> key.startsWith(
                            (product == null ? response.get("productId") : product.getProductId()) + ":"));
                    onSaved.run();
                }
            }));
        });
    }

    private void showCreateAuction() {
        auctions.myProducts().whenComplete((response, error) -> Platform.runLater(() -> {
            if (!success(response, error)) {
                return;
            }
            List<ClientProduct> products = ClientWireParser.products(response.getWireMessage().getData())
                    .stream().filter(ClientProduct::isActive).toList();
            if (products.isEmpty()) {
                showError("Bạn cần có sản phẩm ACTIVE trước khi tạo phòng");
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
                            new BigDecimal(startPrice.getText().trim()),
                            new BigDecimal(increment.getText().trim()),
                            Integer.parseInt(duration.getText().trim()),
                            privateRoom.isSelected() ? "PRIVATE" : "PUBLIC",
                            privateRoom.isSelected() ? roomPassword.getText() : "");
                } catch (NumberFormatException exception) {
                    showError("Giá hoặc thời lượng không hợp lệ");
                    return null;
                }
            });
            dialog.showAndWait().ifPresent(data -> auctions.createAuction(
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
            String text = switch (state) {
                case CONNECTED -> "● Connected";
                case CONNECTING -> "● Connecting";
                case DISCONNECTED -> reconnecting.get() ? "● Reconnecting" : "● Disconnected";
                case CLOSED -> "● Closed";
            };
            loginStateLabel.setText(text);
            connectionLabel.setText(text);
            if (state == ConnectionState.DISCONNECTED && (autoReconnect || stage.isShowing())) {
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (closed.get() || transport.isConnected() || !reconnecting.compareAndSet(false, true)) {
            return;
        }
        scheduleReconnectAttempt(1, config.getReconnectInitialDelayMillis());
    }

    private void scheduleReconnectAttempt(int attempt, long delayMillis) {
        if (closed.get() || transport.isConnected()) {
            reconnecting.set(false);
            return;
        }
        Platform.runLater(() -> {
            loginStateLabel.setText("● Reconnecting " + attempt + '/' + config.getReconnectMaxAttempts());
            connectionLabel.setText(loginStateLabel.getText());
        });
        scheduler.schedule(() -> transport.connect().whenComplete((value, error) -> {
            if (error == null) {
                reconnecting.set(false);
                if (model.hasSessionToken()) {
                    Platform.runLater(this::resumeSession);
                }
                return;
            }
            if (attempt >= config.getReconnectMaxAttempts()) {
                reconnecting.set(false);
                Platform.runLater(() -> showError("Không thể kết nối lại: " + rootMessage(error)));
                return;
            }
            long nextDelay = Math.min(config.getReconnectMaxDelayMillis(), delayMillis * 2L);
            scheduleReconnectAttempt(attempt + 1, nextDelay);
        }), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void heartbeat() {
        if (!transport.isConnected()) {
            return;
        }
        accounts.ping().whenComplete((response, error) -> Platform.runLater(() -> {
            if (error == null && response != null && response.isSuccess()) {
                String rtt = response.get("roundTripMillis") + " ms";
                loginRttLabel.setText("RTT: " + rtt);
                rttLabel.setText("RTT: " + rtt);
            }
        }));
    }

    private void onServerEvent(WireMessage message) {
        Platform.runLater(() -> handleServerEvent(message));
    }

    private void handleServerEvent(WireMessage message) {
        Map<String, String> data = message.getData();
        switch (message.getType()) {
            case CONNECTION_WELCOME -> appendEvent("CONNECTION_WELCOME via "
                    + data.getOrDefault("transport", "TCP"));
            case AUCTION_CREATED -> {
                ClientAuction auction = ClientWireParser.auction(data, "");
                model.applySnapshot(auction, List.of(), ClientWireParser.serverNow(data), false);
                renderAuctions();
                appendEvent("AUCTION_CREATED #" + auction.getAuctionId());
            }
            case BID_UPDATE -> {
                applyBidUpdate(data);
                renderAuctions();
                appendEvent("BID_UPDATE " + data.getOrDefault("currentPrice", ""));
            }
            case AUCTION_TICK -> {
                model.applyTick(
                        ClientWireParser.longValue(data, "auctionId", 0L),
                        ClientWireParser.instant(data, "endTime"),
                        data.getOrDefault("status", ""),
                        ClientWireParser.integer(data, "watcherCount", -1),
                        ClientWireParser.serverNow(data));
                auctionTable.refresh();
                renderDetails(auctionTable.getSelectionModel().getSelectedItem());
            }
            case AUCTION_EXTENDED -> {
                loadAuctionList();
                appendEvent("AUCTION_EXTENDED #" + data.getOrDefault("auctionId", ""));
            }
            case AUCTION_ENDED, AUCTION_CANCELLED -> {
                ClientAuction auction = ClientWireParser.auction(data, "");
                model.applySnapshot(auction, model.currentBidSnapshot(),
                        ClientWireParser.serverNow(data), false);
                renderAuctions();
                appendEvent(message.getType() + " #" + auction.getAuctionId());
            }
            case AUCTION_ARCHIVED -> {
                model.removeAuction(ClientWireParser.longValue(data, "auctionId", 0L),
                        ClientWireParser.serverNow(data));
                renderAuctions();
                appendEvent(data.getOrDefault("message", "AUCTION_ARCHIVED"));
            }
            case AUCTION_KICKED -> {
                long auctionId = ClientWireParser.longValue(data, "auctionId", 0L);
                if (model.getJoinedAuctionId() != null && model.getJoinedAuctionId() == auctionId) {
                    model.leaveJoinedAuction();
                }
                showError(data.getOrDefault("message", "Bạn đã bị kick khỏi phòng"));
            }
            case OUTBID_NOTIFICATION -> appendEvent("BẠN ĐÃ BỊ VƯỢT GIÁ: "
                    + data.getOrDefault("productName", ""));
            case ERROR -> appendEvent("SERVER ERROR: " + data.getOrDefault("message", ""));
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
        ClientAuction selected = auctionTable.getSelectionModel().getSelectedItem();
        long selectedId = selected == null ? 0L : selected.getAuctionId();
        auctionTable.getItems().setAll(model.auctionSnapshot());
        if (selectedId != 0L) {
            auctionTable.getItems().stream()
                    .filter(value -> value.getAuctionId() == selectedId)
                    .findFirst().ifPresent(value -> auctionTable.getSelectionModel().select(value));
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
            updateHostButtons(null);
            return;
        }
        detailName.setText("#" + auction.getAuctionId() + " - " + auction.getProductName());
        detailHost.setText("Chủ phòng: " + auction.getHostUsername());
        detailPrice.setText("Giá hiện tại: " + auction.getCurrentPrice()
                + " | Bước giá: " + auction.getMinBidIncrement());
        detailLeader.setText("Người dẫn đầu: " + (auction.getCurrentWinnerUsername().isBlank()
                ? "Chưa có" : auction.getCurrentWinnerUsername()));
        detailTime.setText("Còn lại: " + remainingText(auction));
        detailVisibility.setText("Loại phòng: "
                + (auction.requiresPassword() ? "PRIVATE 🔒" : "PUBLIC"));
        detailDescription.setText(auction.getDescription());
        updateHostButtons(auction);
        loadProductImage(auction);
    }

    private void loadProductImage(ClientAuction auction) {
        if (!auction.hasImage()) {
            productImage.setImage(null);
            return;
        }
        String key = auction.getProductId() + ":" + auction.getImageVersion();
        Image cached = imageCache.get(key);
        if (cached != null) {
            productImage.setImage(cached);
            return;
        }
        auctions.getProductImage(auction.getProductId()).whenComplete((response, error) ->
                Platform.runLater(() -> {
                    if (!success(response, error)) {
                        return;
                    }
                    try {
                        byte[] bytes = Base64.getDecoder().decode(response.get("imageBase64"));
                        Image image = new Image(new ByteArrayInputStream(bytes));
                        imageCache.put(key, image);
                        ClientAuction current = auctionTable.getSelectionModel().getSelectedItem();
                        if (current != null && current.getProductId() == auction.getProductId()) {
                            productImage.setImage(image);
                        }
                    } catch (RuntimeException exception) {
                        appendEvent("Không hiển thị được ảnh: " + exception.getMessage());
                    }
                }));
    }

    private void refreshClock() {
        auctionTable.refresh();
        ClientAuction selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            detailTime.setText("Còn lại: " + remainingText(selected));
        }
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
        boolean disabled = auction == null || !auction.isOpen()
                || !auction.isHostedBy(model.getUserId());
        extendButton.setDisable(disabled);
        endButton.setDisable(disabled);
        cancelButton.setDisable(disabled);
        kickButton.setDisable(disabled);
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
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message == null ? "Lỗi không xác định" : message);
        alert.setHeaderText("BTL16 Auction");
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText("BTL16 Auction");
        alert.showAndWait();
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Xác nhận");
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
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

    private String mimeFor(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") ? "image/png" : "image/jpeg";
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

    private static final class ProductFormData {
        private final String code;
        private final String name;
        private final String description;
        private final byte[] imageData;
        private final String imageMime;
        private final String imageName;

        private ProductFormData(
                String code,
                String name,
                String description,
                byte[] imageData,
                String imageMime,
                String imageName) {
            this.code = code;
            this.name = name;
            this.description = description;
            this.imageData = imageData;
            this.imageMime = imageMime;
            this.imageName = imageName;
        }
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
