package vn.ptit.btl16.server.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.util.ConnectionCode;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.ServerStats;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.dashboard.ServerAddresses;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class ServerFxApp extends Application {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "auction-server-lifecycle");
        thread.setDaemon(true);
        return thread;
    });
    private final Label state = DesktopTheme.label("ĐANG KHỞI ĐỘNG", "pill offline");
    private final Label clients = DesktopTheme.label("0", "stat-value");
    private final Label users = DesktopTheme.label("0", "stat-value");
    private final Label rooms = DesktopTheme.label("0", "stat-value");
    private final Label ended = DesktopTheme.label("0", "stat-value");
    private final Label code = DesktopTheme.label("—", "connection-code");
    private final Label uptime = DesktopTheme.label("00:00:00", "section-title");
    private final Label storage = DesktopTheme.label("Dữ liệu cục bộ", "muted");
    private final Label clock = DesktopTheme.label("", "muted");
    private final ComboBox<String> addresses = new ComboBox<>();
    private final TableView<AuctionSnapshot> table = new TableView<>();
    private final Button toggle = new Button("Đang khởi động…");
    private final Button copyCode = new Button("Sao chép mã");
    private final Button copyAddress = new Button("Sao chép địa chỉ");
    private final VBox activity = new VBox(11);
    private final HBox graph = new HBox(5);
    private final List<Integer> samples = new ArrayList<>();
    private final Timeline timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> refresh()));
    private final TabPane adminTabs = new TabPane();
    private final Map<Tab, Button> navigation = new LinkedHashMap<>();
    private final Map<Tab, ServerAdminPage> pages = new LinkedHashMap<>();
    private final Label pageTitle = DesktopTheme.label("Tổng quan", "page-title");
    private final Label offlineBanner = DesktopTheme.label("Đang khởi động server…", "admin-offline");
    private volatile ServerApplication application;
    private volatile boolean closing;
    private Stage stage;
    private Instant startedAt;
    private long ticks;
    private int previousClients;
    private int previousRooms;
    private boolean busy;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Auction • Server");
        stage.getIcons().add(DesktopTheme.icon());
        Scene scene = new Scene(buildRoot(), 1280, 820);
        DesktopTheme.apply(scene);
        scene.getStylesheets().add(getClass().getResource("/ui/server-admin.css").toExternalForm());
        clock.visibleProperty().bind(scene.widthProperty().greaterThanOrEqualTo(1180));
        clock.managedProperty().bind(clock.visibleProperty());
        stage.setScene(scene);
        stage.setMinWidth(980);
        stage.setMinHeight(640);
        stage.setOnCloseRequest(event -> {
            if (application != null && !confirm("Đóng server và ngắt các kết nối hiện tại?")) {
                event.consume();
            }
        });
        DesktopTheme.fitOnScreen(stage, 1300, 860);
        stage.show();
        adminTabs.lookup(".tab-header-area").setVisible(false);
        DesktopTheme.reveal(scene.getRoot());
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
        startServer();
    }

    private BorderPane buildRoot() {
        HBox header = new HBox(16, pageTitle, DesktopTheme.spacer(), clock, state);
        header.getStyleClass().add("admin-header");
        HBox stats = new HBox(14, stat("Kết nối", clients, false), stat("Trực tuyến", users, true),
                stat("Phiên đang mở", rooms, false), stat("Đã kết thúc", ended, false));

        addresses.setMaxWidth(Double.MAX_VALUE);
        addresses.setPromptText("Chọn địa chỉ mạng");
        addresses.valueProperty().addListener((observable, before, value) ->
                code.setText(value == null ? "—" : ConnectionCode.encode(value)));
        copyCode.getStyleClass().add("teal");
        copyAddress.getStyleClass().add("ghost");
        copyCode.setOnAction(event -> copy(addresses.getValue() == null
                ? null : ConnectionCode.encode(addresses.getValue()), copyCode));
        copyAddress.setOnAction(event -> copy(addresses.getValue(), copyAddress));
        VBox invitation = new VBox(12,
                new HBox(10, DesktopTheme.label("KẾT NỐI VÀO SÀN", "eyebrow"), DesktopTheme.spacer(),
                        DesktopTheme.label("LAN", "pill")), code, addresses,
                new HBox(8, copyCode, copyAddress));
        invitation.getStyleClass().add("connection-card");
        invitation.setMinWidth(300);
        HBox.setHgrow(invitation, Priority.ALWAYS);

        graph.setAlignment(Pos.BOTTOM_CENTER);
        graph.setFillHeight(false);
        graph.setMinHeight(82);
        VBox traffic = new VBox(11, new HBox(10, DesktopTheme.label("Nhịp kết nối", "section-title"),
                DesktopTheme.spacer(), DesktopTheme.label("30 giây", "eyebrow")), graph,
                new HBox(10, DesktopTheme.label("Lưu trữ", "muted"), DesktopTheme.spacer(), storage));
        traffic.getStyleClass().add("card");
        HBox.setHgrow(traffic, Priority.ALWAYS);
        HBox networkRow = new HBox(16, invitation, traffic);

        column("Phiên", item -> "#" + item.getAuctionId(), 70);
        column("Sản phẩm", item -> item.getProduct().getName(), 200);
        column("Chủ phòng", item -> item.hasAnonymousGuests() ? "Ẩn danh" : item.getHostUsername(), 130);
        column("Giá hiện tại", item -> money(item.getCurrentPrice()), 160);
        column("Dẫn đầu", item -> item.getCurrentWinnerId() == null
                ? "—" : item.hasAnonymousGuests() ? "Ẩn danh" : item.getCurrentWinnerUsername(), 130);
        column("Trạng thái", item -> switch (item.getStatus().name()) {
            case "OPEN" -> "Đang mở";
            case "CANCELLED" -> "Đã hủy";
            default -> "Kết thúc";
        }, 105);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(DesktopTheme.label("Chưa có phiên đấu giá", "muted"));
        table.setPrefHeight(260);
        table.setMinHeight(200);
        VBox roomCard = new VBox(15, new HBox(10, DesktopTheme.label("Các phiên đấu giá", "section-title"),
                DesktopTheme.spacer()), table);
        roomCard.getStyleClass().add("card");
        VBox.setVgrow(roomCard, Priority.ALWAYS);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox activityCard = new VBox(12, DesktopTheme.label("Hoạt động gần đây", "section-title"), activity);
        activityCard.getStyleClass().add("card");
        VBox content = new VBox(20, stats, networkRow, roomCard, activityCard);
        content.getStyleClass().add("admin-overview");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        adminTabs.setId("server-admin-tabs");
        adminTabs.getStyleClass().add("admin-tabs");
        adminTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        adminTabs.setMinSize(0, 0);
        Tab overview = new Tab("Tổng quan", scroll);
        overview.setId("server-overview-tab");
        Tab wallets = new Tab("Tài khoản & ví");
        wallets.setId("server-wallet-tab");
        Tab cashFlow = new Tab("Dòng tiền sàn");
        cashFlow.setId("server-cash-flow-tab");
        Tab operations = new Tab("Nhật ký vận hành");
        operations.setId("server-operations-tab");
        adminTabs.getTabs().addAll(overview, wallets, cashFlow, operations);
        adminTabs.getSelectionModel().selectedItemProperty().addListener((observable, before, selected) -> selectPage(selected));
        VBox sidebar = buildSidebar(overview, wallets, cashFlow, operations);
        offlineBanner.setId("server-offline-banner");
        offlineBanner.setWrapText(true);
        offlineBanner.setMaxWidth(Double.MAX_VALUE);
        offlineBanner.managedProperty().bind(offlineBanner.visibleProperty());
        VBox workspace = new VBox(header, offlineBanner, adminTabs);
        workspace.setMinWidth(0);
        VBox.setVgrow(adminTabs, Priority.ALWAYS);
        BorderPane root = new BorderPane(workspace);
        root.setId("server-admin-shell");
        root.getStyleClass().add("admin-shell");
        root.setLeft(sidebar);
        selectPage(overview);
        return root;
    }

    private VBox buildSidebar(Tab overview, Tab wallets, Tab cashFlow, Tab operations) {
        VBox sidebar = new VBox(8, DesktopTheme.brand("SERVER"), new Separator(),
                navigationButton(overview, "Tổng quan", "server-overview-open", "overview"),
                navigationButton(wallets, "Tài khoản & ví", "server-wallet-open", "wallet"),
                navigationButton(cashFlow, "Dòng tiền", "server-cash-flow-open", "cash"),
                navigationButton(operations, "Nhật ký", "server-operations-open", "log"));
        sidebar.getStyleClass().addAll("sidebar", "admin-sidebar");
        Region filler = new Region();
        VBox.setVgrow(filler, Priority.ALWAYS);
        Button network = new Button("Sao chép địa chỉ", ServerIcons.icon("copy"));
        network.setId("server-copy-address");
        network.setMaxWidth(Double.MAX_VALUE);
        network.setOnAction(event -> copy(addresses.getValue(), network));
        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.setGraphic(ServerIcons.icon("power"));
        toggle.setDisable(true);
        toggle.setOnAction(event -> {
            if (application == null) {
                startServer();
            } else if (confirm("Tạm dừng sàn và ngắt các kết nối hiện tại?")) {
                stopServer();
            }
        });
        sidebar.getChildren().addAll(filler, network, new Separator(),
                DesktopTheme.label("Thời gian hoạt động", "admin-sidebar-caption"), uptime, toggle);
        return sidebar;
    }

    private Button navigationButton(Tab tab, String title, String id, String icon) {
        Button button = new Button(title, ServerIcons.icon(icon));
        button.setId(id);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> adminTabs.getSelectionModel().select(tab));
        navigation.put(tab, button);
        return button;
    }

    private void selectPage(Tab selected) {
        if (selected == null) {
            return;
        }
        if (selected.getContent() == null) {
            ServerAdminPage page = switch (selected.getId()) {
                case "server-wallet-tab" -> new ServerWalletWindow(stage, () -> application, worker);
                case "server-cash-flow-tab" -> new ServerCashFlowWindow(stage, () -> application, worker);
                case "server-operations-tab" -> new OperationLogWindow(stage, () -> application, worker);
                default -> throw new IllegalArgumentException("Unknown admin tab: " + selected.getId());
            };
            pages.put(selected, page);
            selected.setContent(page);
        }
        navigation.forEach((tab, button) -> {
            button.getStyleClass().remove("nav-active");
            if (tab == selected) {
                button.getStyleClass().add("nav-active");
            }
        });
        pageTitle.setText(selected.getId().equals("server-overview-tab") ? "Tổng quan" : selected.getText());
        updateAdminAvailability();
    }

    private void updateAdminAvailability() {
        boolean available = application != null && !busy && !closing;
        offlineBanner.setVisible(!available);
        offlineBanner.setText(busy ? "Đang chuyển trạng thái…" : "Server tạm dừng · Khởi động lại để cập nhật dữ liệu.");
        pages.forEach((tab, page) -> {
            page.setDisable(!available);
            if (available && adminTabs.getSelectionModel().getSelectedItem() == tab) {
                page.activate();
            } else {
                page.deactivate();
            }
        });
    }

    private VBox stat(String title, Label value, boolean accent) {
        VBox box = new VBox(7, DesktopTheme.label(title, "eyebrow"), value);
        box.getStyleClass().add("stat-card");
        if (accent) {
            box.getStyleClass().add("accent");
        }
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private void column(String title, Function<AuctionSnapshot, String> value, double width) {
        TableColumn<AuctionSnapshot, String> column = new TableColumn<>(title);
        column.setCellValueFactory(item -> new SimpleStringProperty(value.apply(item.getValue())));
        column.setPrefWidth(width);
        table.getColumns().add(column);
    }

    private void startServer() {
        if (busy || closing) {
            return;
        }
        busy = true;
        toggle.setDisable(true);
        state.setText("ĐANG KHỞI ĐỘNG");
        updateAdminAvailability();
        CompletableFuture.supplyAsync(() -> {
            ServerApplication created = ServerApplication.create(ServerConfig.loadDefault());
            try {
                created.start();
                application = created;
                if (closing) {
                    created.close();
                    application = null;
                    return null;
                }
                return created;
            } catch (Exception exception) {
                created.close();
                throw new java.util.concurrent.CompletionException(exception);
            }
        }, worker).whenComplete((created, error) -> Platform.runLater(() -> {
            busy = false;
            if (closing) {
                return;
            }
            toggle.setDisable(false);
            updateAdminAvailability();
            if (error != null) {
                state.setText("CHƯA KHỞI ĐỘNG");
                toggle.setText("Khởi động lại");
                Throwable cause = error;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "Không thể khởi động sàn.\n" + cause.getMessage(), ButtonType.OK);
                alert.setHeaderText("Khởi động không thành công");
                DesktopTheme.dialog(alert, stage);
                alert.showAndWait();
                return;
            }
            startedAt = Instant.now();
            toggle.setText("Tạm dừng sàn");
            state.setText("Đang hoạt động");
            state.getStyleClass().remove("offline");
            addActivity("Sàn đấu giá đã sẵn sàng");
            refreshAddresses();
            refresh();
        }));
    }

    private void stopServer() {
        busy = true;
        toggle.setDisable(true);
        ServerApplication running = application;
        application = null;
        updateAdminAvailability();
        addresses.getItems().clear();
        state.setText("ĐANG DỪNG");
        CompletableFuture.runAsync(running::close, worker).whenComplete((nothing, error) -> Platform.runLater(() -> {
            busy = false;
            toggle.setDisable(false);
            toggle.setText("Khởi động sàn");
            state.setText("Đã tạm dừng");
            state.getStyleClass().add("offline");
            clients.setText("0");
            users.setText("0");
            updateAdminAvailability();
            addActivity("Sàn đấu giá đã tạm dừng");
        }));
    }

    private void refresh() {
        clock.setText(DateTimeFormatter.ofPattern("HH:mm • dd/MM/yyyy")
                .withZone(ZoneId.systemDefault()).format(Instant.now()));
        ServerApplication running = application;
        if (running == null || busy) {
            return;
        }
        ServerStats stats = running.stats();
        clients.setText(Integer.toString(stats.getActiveConnections()));
        users.setText(Integer.toString(stats.getActiveSessions()));
        rooms.setText(Integer.toString(stats.getOpenAuctions()));
        ended.setText(Integer.toString(stats.getEndedAuctions()));
        storage.setText(running.getConfig().isEmbeddedDatabase() ? "Database test cách ly" : "MySQL / XAMPP · Máy server");
        long seconds = java.time.Duration.between(startedAt, Instant.now()).getSeconds();
        uptime.setText(String.format("%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60));
        var selected = table.getSelectionModel().getSelectedItem();
        table.getItems().setAll(running.auctionSnapshots());
        if (selected != null) {
            table.getItems().stream().filter(item -> item.getAuctionId() == selected.getAuctionId())
                    .findFirst().ifPresent(item -> table.getSelectionModel().select(item));
        }
        if (previousClients != stats.getActiveConnections()) {
            addActivity("Kết nối hiện tại: " + stats.getActiveConnections());
            previousClients = stats.getActiveConnections();
        }
        if (previousRooms != stats.getOpenAuctions()) {
            addActivity("Phiên đang mở: " + stats.getOpenAuctions());
            previousRooms = stats.getOpenAuctions();
        }
        samples.add(stats.getActiveConnections());
        if (samples.size() > 30) {
            samples.remove(0);
        }
        graph.getChildren().clear();
        int maximum = Math.max(5, samples.stream().mapToInt(Integer::intValue).max().orElse(0));
        for (int sample : samples) {
            Region bar = new Region();
            bar.setPrefSize(9, 4 + 74.0 * sample / maximum);
            bar.setMinWidth(3);
            bar.getStyleClass().add("connection-bar");
            HBox.setHgrow(bar, Priority.ALWAYS);
            graph.getChildren().add(bar);
        }
        if (++ticks % 10 == 0) {
            refreshAddresses();
        }
    }

    private void refreshAddresses() {
        ServerApplication running = application;
        if (running == null) {
            return;
        }
        try {
            List<String> urls = ServerAddresses.webSocketUrls(running.getConfig(), running.getWebSocketBoundPort());
            if (urls.isEmpty()) {
                urls = List.of("ws://127.0.0.1:" + running.getWebSocketBoundPort()
                        + running.getConfig().getWebSocketPath());
            }
            String selected = addresses.getValue();
            if (!addresses.getItems().equals(urls)) {
                addresses.getItems().setAll(urls);
                addresses.setValue(urls.contains(selected) ? selected : urls.get(0));
            }
        } catch (Exception exception) {
            addActivity("Không thể cập nhật địa chỉ mạng");
        }
    }

    private void copy(String value, Button button) {
        if (value == null || busy || application == null) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
        String original = button.getText();
        button.setText("Đã sao chép");
        button.setDisable(true);
        new Timeline(new KeyFrame(Duration.seconds(1.5), event -> {
            button.setText(original);
            button.setDisable(false);
        })).play();
    }

    private void addActivity(String message) {
        String time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now());
        HBox row = new HBox(16, DesktopTheme.label("●", "muted"), DesktopTheme.label(message, "muted"),
                DesktopTheme.spacer(), DesktopTheme.label(time, "eyebrow"));
        activity.getChildren().add(0, row);
        if (activity.getChildren().size() > 3) {
            activity.getChildren().remove(3);
        }
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Xác nhận");
        DesktopTheme.dialog(alert, stage);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private String money(java.math.BigDecimal value) {
        return NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value) + " ₫";
    }

    @Override
    public void stop() {
        closing = true;
        timer.stop();
        pages.values().forEach(ServerAdminPage::deactivate);
        ServerApplication running = application;
        if (running != null) {
            running.close();
        }
        worker.shutdownNow();
    }
}
