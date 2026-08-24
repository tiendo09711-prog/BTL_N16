package vn.ptit.btl16.client.controller;

import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.model.ClientBid;
import vn.ptit.btl16.client.model.ClientProduct;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.ConnectionState;
import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.client.view.AuctionPanel;
import vn.ptit.btl16.client.view.LoginPanel;
import vn.ptit.btl16.client.view.MainFrame;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Main MVC controller for login, auction room, realtime events and reconnect/resync. */
public final class ClientController implements AutoCloseable {
    private final ClientConfig config;
    private final ClientAppModel model;
    private final MainFrame view;
    private final NetworkClient network;
    private final AccountApi accounts;
    private final AuctionApi auctions;
    private final HeartbeatService heartbeat;
    private final ReconnectCoordinator reconnect;
    private final Timer clockTimer;
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private volatile boolean autoReconnectEnabled;

    public ClientController(
            ClientConfig config,
            ClientAppModel model,
            MainFrame view,
            NetworkClient network,
            AccountApi accounts,
            AuctionApi auctions) {
        this.config = config;
        this.model = model;
        this.view = view;
        this.network = network;
        this.accounts = accounts;
        this.auctions = auctions;
        this.heartbeat = new HeartbeatService(
                network,
                accounts,
                config.getHeartbeatIntervalMillis(),
                value -> onEdt(() -> view.setLatency(value)));
        this.reconnect = new ReconnectCoordinator(
                network,
                config,
                new ReconnectCoordinator.Listener() {
                    @Override
                    public void onAttempt(int attempt, int maxAttempts, long delayMillis) {
                        onEdt(() -> view.getAuctionPanel().appendNotification(
                                "Reconnect " + attempt + '/' + maxAttempts
                                        + " sau " + delayMillis + " ms"));
                    }

                    @Override
                    public void onTransportConnected() {
                        resumeAfterReconnect(0);
                    }

                    @Override
                    public void onExhausted(String lastError) {
                        onEdt(() -> view.showError(
                                "Khong the ket noi lai server: " + lastError));
                    }
                });
        this.clockTimer = new Timer(250, event -> {
            Instant now = model.serverNow();
            view.getAuctionPanel().updateClock(now);
        });
    }

    public void start() {
        bindEvents();
        network.addStateListener(this::handleConnectionState);
        network.addEventListener(this::handleEvent);
        heartbeat.start();
        clockTimer.start();
        connectInitial();
    }

    private void bindEvents() {
        LoginPanel login = view.getLoginPanel();
        AuctionPanel auction = view.getAuctionPanel();
        login.onLogin(event -> login());
        login.onRegister(event -> register());
        login.onReconnect(event -> connectManually());
        auction.onRefreshList(event -> loadAuctionList(true));
        auction.onMyProducts(event -> showMyProducts());
        auction.onMyAuctions(event -> showMyAuctions());
        auction.onAddProduct(event -> createProduct());
        auction.onEditProduct(event -> editProduct());
        auction.onDeactivateProduct(event -> deactivateProduct());
        auction.onCreateAuction(event -> createAuction());
        auction.onJoin(event -> joinSelectedAuction());
        auction.onLeave(event -> leaveAuction());
        auction.onHistory(event -> loadHistory());
        auction.onBid(event -> placeBid());
        auction.onExtend(event -> extendSelectedAuction());
        auction.onEnd(event -> endSelectedAuction());
        auction.onCancel(event -> cancelSelectedAuction());
        auction.onKick(event -> kickUserFromSelectedAuction());
        auction.onProfile(event -> manageProfile());
        auction.onLogout(event -> logout());
        view.onWindow(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                close();
            }
        });
    }

    private void connectInitial() {
        setLoginBusy(true);
        CompletableFuture.runAsync(() -> connectTransport())
                .whenComplete((ignored, error) -> onEdt(() -> {
                    setLoginBusy(false);
                    if (error != null) {
                        view.showError("Khong ket noi duoc server: " + rootMessage(error));
                    }
                }));
    }

    private void connectManually() {
        if (network.isConnected()) {
            return;
        }
        setLoginBusy(true);
        CompletableFuture.runAsync(this::connectTransport)
                .thenRun(() -> {
                    if (model.hasSessionToken()) {
                        resumeAfterReconnect(0);
                    }
                })
                .whenComplete((ignored, error) -> onEdt(() -> {
                    setLoginBusy(false);
                    if (error != null) {
                        view.showError("Ket noi lai that bai: " + rootMessage(error));
                    }
                }));
    }

    private void connectTransport() {
        try {
            network.connect(config.getServerHost(), config.getServerPort());
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private void login() {
        LoginPanel panel = view.getLoginPanel();
        char[] password = panel.getPassword();
        String passwordText = new String(password);
        Arrays.fill(password, '\0');
        panel.clearPassword();
        setLoginBusy(true);
        accounts.login(panel.getUsername(), passwordText)
                .whenComplete((response, error) -> onEdt(() -> {
                    setLoginBusy(false);
                    if (error != null) {
                        view.showError("Dang nhap loi: " + rootMessage(error));
                        return;
                    }
                    if (!response.isSuccess()) {
                        view.showError(failure(response));
                        return;
                    }
                    model.applyIdentity(response);
                    autoReconnectEnabled = true;
                    view.showAuction(model);
                    view.getAuctionPanel().appendNotification("Dang nhap thanh cong");
                    loadAuctionList(false);
                }));
    }

    private void register() {
        Optional<MainFrame.RegistrationInput> optional = view.promptRegistration();
        if (optional.isEmpty()) {
            return;
        }
        try (MainFrame.RegistrationInput input = optional.get()) {
            char[] password = input.getPassword();
            char[] confirmation = input.getConfirmation();
            try {
                if (!Arrays.equals(password, confirmation)) {
                    view.showError("Mat khau nhap lai khong khop");
                    return;
                }
                setLoginBusy(true);
                accounts.register(
                                input.getUsername(),
                                new String(password),
                                input.getDisplayName(),
                                input.getEmail(),
                                input.getPhone())
                        .whenComplete((response, error) -> onEdt(() -> {
                            setLoginBusy(false);
                            if (error != null) {
                                view.showError("Dang ky loi: " + rootMessage(error));
                            } else if (!response.isSuccess()) {
                                view.showError(failure(response));
                            } else {
                                view.showInfo("Dang ky thanh cong. Hay dang nhap.");
                            }
                        }));
            } finally {
                Arrays.fill(password, '\0');
                Arrays.fill(confirmation, '\0');
            }
        }
    }

    private void loadAuctionList(boolean showBusy) {
        if (showBusy) {
            setAuctionBusy(true);
        }
        auctions.listAuctions().whenComplete((response, error) -> onEdt(() -> {
            if (showBusy) {
                setAuctionBusy(false);
            }
            if (error != null) {
                view.showError("Tai danh sach loi: " + rootMessage(error));
                return;
            }
            if (!response.isSuccess()) {
                view.showError(failure(response));
                return;
            }
            applyAuctionList(response);
            view.renderAuction(model);
        }));
    }

    private void showMyAuctions() {
        setAuctionBusy(true);
        auctions.myAuctions().whenComplete((response, error) -> onEdt(() -> {
            setAuctionBusy(false);
            if (error != null) {
                view.showError("Tai phong cua toi loi: " + rootMessage(error));
            } else if (!response.isSuccess()) {
                view.showError(failure(response));
            } else {
                applyAuctionList(response);
                view.renderAuction(model);
                view.getAuctionPanel().appendNotification("Dang hien thi cac phong ban chu tri");
            }
        }));
    }

    private void showMyProducts() {
        loadMyProducts(view::showProducts);
    }

    private void createProduct() {
        Optional<MainFrame.ProductInput> optional = view.promptProduct(null);
        if (optional.isEmpty()) {
            return;
        }
        MainFrame.ProductInput input = optional.get();
        setAuctionBusy(true);
        auctions.createProduct(input.getCode(), input.getName(), input.getDescription())
                .whenComplete((response, error) -> onEdt(() -> {
                    setAuctionBusy(false);
                    if (error != null) {
                        view.showError("Them san pham loi: " + rootMessage(error));
                    } else if (!response.isSuccess()) {
                        view.showError(failure(response));
                    } else {
                        view.showInfo("Da them san pham " + response.get("name"));
                    }
                }));
    }

    private void editProduct() {
        loadMyProducts(products -> view.chooseProduct(products, "Chon san pham can sua", false)
                .ifPresent(product -> view.promptProduct(product)
                        .ifPresent(input -> updateProduct(input))));
    }

    private void updateProduct(MainFrame.ProductInput input) {
        setAuctionBusy(true);
        auctions.updateProduct(
                        input.getProductId(),
                        input.getCode(),
                        input.getName(),
                        input.getDescription())
                .whenComplete((response, error) -> onEdt(() -> {
                    setAuctionBusy(false);
                    if (error != null) {
                        view.showError("Sua san pham loi: " + rootMessage(error));
                    } else if (!response.isSuccess()) {
                        view.showError(failure(response));
                    } else {
                        view.showInfo("Da cap nhat san pham");
                        loadAuctionList(false);
                    }
                }));
    }

    private void deactivateProduct() {
        loadMyProducts(products -> view.chooseProduct(products, "Chon san pham can an", true)
                .ifPresent(product -> {
                    if (!view.confirm("Vo hieu hoa san pham " + product.getName() + "?")) {
                        return;
                    }
                    setAuctionBusy(true);
                    auctions.deactivateProduct(product.getProductId())
                            .whenComplete((response, error) -> onEdt(() -> {
                                setAuctionBusy(false);
                                if (error != null) {
                                    view.showError("An san pham loi: " + rootMessage(error));
                                } else if (!response.isSuccess()) {
                                    view.showError(failure(response));
                                } else {
                                    view.showInfo("Da vo hieu hoa san pham");
                                }
                            }));
                }));
    }

    private void createAuction() {
        loadMyProducts(products -> view.promptAuction(products).ifPresent(input -> {
            try {
                BigDecimal startPrice = parseMoneyInput(input.getStartPrice(), "Gia khoi diem");
                BigDecimal minIncrement = parseMoneyInput(
                        input.getMinBidIncrement(), "Buoc gia toi thieu");
                int durationMinutes = Integer.parseInt(input.getDurationMinutes().trim());
                setAuctionBusy(true);
                auctions.createAuction(
                                input.getProduct().getProductId(),
                                startPrice,
                                minIncrement,
                                durationMinutes)
                        .whenComplete((response, error) -> onEdt(() -> {
                            setAuctionBusy(false);
                            if (error != null) {
                                view.showError("Tao phong loi: " + rootMessage(error));
                            } else if (!response.isSuccess()) {
                                view.showError(failure(response));
                            } else {
                                applySnapshot(response, false);
                                view.renderAuction(model);
                                view.showInfo("Da tao phong dau gia #" + response.get("auctionId"));
                            }
                        }));
            } catch (NumberFormatException exception) {
                view.showError("Thoi luong phai la so nguyen");
            } catch (IllegalArgumentException exception) {
                view.showError(exception.getMessage());
            }
        }));
    }

    private void loadMyProducts(Consumer<List<ClientProduct>> consumer) {
        setAuctionBusy(true);
        auctions.myProducts().whenComplete((response, error) -> onEdt(() -> {
            setAuctionBusy(false);
            if (error != null) {
                view.showError("Tai san pham loi: " + rootMessage(error));
            } else if (!response.isSuccess()) {
                view.showError(failure(response));
            } else {
                consumer.accept(ClientWireParser.products(response.getWireMessage().getData()));
            }
        }));
    }

    private void extendSelectedAuction() {
        Long auctionId = view.getAuctionPanel().getSelectedAuctionId();
        if (auctionId == null) {
            return;
        }
        view.promptText("Gia han", "So giay gia han (10-600)", "60").ifPresent(raw -> {
            try {
                int seconds = Integer.parseInt(raw);
                setAuctionBusy(true);
                auctions.extendAuction(auctionId, seconds)
                        .whenComplete((response, error) -> onEdt(() -> {
                            setAuctionBusy(false);
                            if (error != null) {
                                view.showError("Gia han loi: " + rootMessage(error));
                            } else if (!response.isSuccess()) {
                                view.showError(failure(response));
                            } else {
                                applySnapshot(response, false);
                                view.renderAuction(model);
                                view.showInfo("Da gia han " + seconds + " giay");
                            }
                        }));
            } catch (NumberFormatException exception) {
                view.showError("So giay gia han khong hop le");
            }
        });
    }

    private void endSelectedAuction() {
        Long auctionId = view.getAuctionPanel().getSelectedAuctionId();
        if (auctionId == null || !view.confirm("Ket thuc som phien #" + auctionId + "?")) {
            return;
        }
        setAuctionBusy(true);
        auctions.endAuction(auctionId).whenComplete((response, error) -> onEdt(() -> {
            setAuctionBusy(false);
            if (error != null) {
                view.showError("Ket thuc phien loi: " + rootMessage(error));
            } else if (!response.isSuccess()) {
                view.showError(failure(response));
            } else {
                loadAuctionList(false);
                view.showInfo("Da ket thuc phien #" + auctionId);
            }
        }));
    }

    private void cancelSelectedAuction() {
        Long auctionId = view.getAuctionPanel().getSelectedAuctionId();
        if (auctionId == null || !view.confirm(
                "Huy phien #" + auctionId + "? Chi huy duoc khi chua co bid.")) {
            return;
        }
        setAuctionBusy(true);
        auctions.cancelAuction(auctionId).whenComplete((response, error) -> onEdt(() -> {
            setAuctionBusy(false);
            if (error != null) {
                view.showError("Huy phien loi: " + rootMessage(error));
            } else if (!response.isSuccess()) {
                view.showError(failure(response));
            } else {
                loadAuctionList(false);
                view.showInfo("Da huy phien #" + auctionId);
            }
        }));
    }

    private void kickUserFromSelectedAuction() {
        Long auctionId = view.getAuctionPanel().getSelectedAuctionId();
        if (auctionId == null) {
            return;
        }
        view.promptText("Moi nguoi dung", "Nhap username can moi", "")
                .filter(value -> !value.isBlank())
                .ifPresent(username -> {
                    setAuctionBusy(true);
                    auctions.kickUser(auctionId, username)
                            .whenComplete((response, error) -> onEdt(() -> {
                                setAuctionBusy(false);
                                if (error != null) {
                                    view.showError("Moi user loi: " + rootMessage(error));
                                } else if (!response.isSuccess()) {
                                    view.showError(failure(response));
                                } else {
                                    view.showInfo("Da moi " + username + " khoi phong");
                                }
                            }));
                });
    }

    private void joinSelectedAuction() {
        Long selected = view.getAuctionPanel().getSelectedAuctionId();
        if (selected == null) {
            view.showError("Hay chon mot phien dau gia");
            return;
        }
        Long current = model.getJoinedAuctionId();
        setAuctionBusy(true);
        CompletableFuture<ApiResponse> operation;
        if (current != null && current.longValue() != selected.longValue()) {
            operation = auctions.leave(current).thenCompose(ignored -> auctions.join(selected));
        } else {
            operation = auctions.join(selected);
        }
        operation.whenComplete((response, error) -> onEdt(() -> {
            setAuctionBusy(false);
            if (error != null) {
                view.showError("Vao phong loi: " + rootMessage(error));
                return;
            }
            if (!response.isSuccess()) {
                view.showError(failure(response));
                return;
            }
            applySnapshot(response, true);
            view.renderAuction(model);
            view.getAuctionPanel().appendNotification(
                    "Da vao phong auction " + selected);
        }));
    }

    private void leaveAuction() {
        Long auctionId = model.getJoinedAuctionId();
        if (auctionId == null) {
            return;
        }
        setAuctionBusy(true);
        auctions.leave(auctionId).whenComplete((response, error) -> onEdt(() -> {
            setAuctionBusy(false);
            if (error != null) {
                view.showError("Roi phong loi: " + rootMessage(error));
                return;
            }
            if (!response.isSuccess()) {
                view.showError(failure(response));
                return;
            }
            model.leaveJoinedAuction();
            view.renderAuction(model);
            view.getAuctionPanel().appendNotification("Da roi phong auction " + auctionId);
        }));
    }

    private void loadHistory() {
        Long auctionId = model.getJoinedAuctionId();
        if (auctionId == null) {
            return;
        }
        setAuctionBusy(true);
        auctions.history(auctionId).whenComplete((response, error) -> onEdt(() -> {
            setAuctionBusy(false);
            if (error != null) {
                view.showError("Tai lich su loi: " + rootMessage(error));
            } else if (!response.isSuccess()) {
                view.showError(failure(response));
            } else {
                model.applyHistory(
                        ClientWireParser.bids(response.getWireMessage().getData()),
                        ClientWireParser.serverNow(response.getWireMessage().getData()));
                view.renderAuction(model);
            }
        }));
    }

    private void placeBid() {
        Long auctionId = model.getJoinedAuctionId();
        if (auctionId == null) {
            view.showError("Ban chua vao phong dau gia");
            return;
        }
        BigDecimal amount;
        try {
            amount = parseBidAmount(view.getAuctionPanel().getAmountText());
        } catch (IllegalArgumentException exception) {
            view.showError(exception.getMessage());
            return;
        }
        setAuctionBusy(true);
        auctions.bid(auctionId, amount).whenComplete((response, error) -> onEdt(() -> {
            setAuctionBusy(false);
            if (error != null) {
                view.showError("Dat gia loi: " + rootMessage(error));
                return;
            }
            if (!response.isSuccess()) {
                view.showError(failure(response));
                return;
            }
            applyBidUpdate(response.getWireMessage().getData());
            view.getAuctionPanel().clearAmount();
            view.renderAuction(model);
            view.getAuctionPanel().appendNotification(
                    "Server chap nhan bid " + amount.toPlainString() + " VND");
        }));
    }

    private BigDecimal parseBidAmount(String raw) {
        String normalized = raw == null ? "" : raw.trim()
                .replace(" ", "")
                .replace(",", "")
                .replace(".", "");
        if (normalized.isBlank() || !normalized.matches("[0-9]+")) {
            throw new IllegalArgumentException("Muc gia phai la so VND duong");
        }
        return new BigDecimal(normalized).setScale(2);
    }

    private BigDecimal parseMoneyInput(String raw, String label) {
        String normalized = raw == null ? "" : raw.trim()
                .replace(" ", "")
                .replace(",", "")
                .replace(".", "");
        if (normalized.isBlank() || !normalized.matches("[0-9]+")) {
            throw new IllegalArgumentException(label + " phai la so VND duong");
        }
        return new BigDecimal(normalized).setScale(2);
    }

    private void manageProfile() {
        int action = view.promptProfileAction(model);
        if (action == 0) {
            updateProfile();
        } else if (action == 1) {
            changePassword();
        }
    }

    private void updateProfile() {
        Optional<MainFrame.ProfileInput> optional = view.promptProfile(model);
        if (optional.isEmpty()) {
            return;
        }
        MainFrame.ProfileInput input = optional.get();
        setAuctionBusy(true);
        accounts.updateProfile(input.getDisplayName(), input.getEmail(), input.getPhone())
                .whenComplete((response, error) -> onEdt(() -> {
                    setAuctionBusy(false);
                    if (error != null) {
                        view.showError("Cap nhat ho so loi: " + rootMessage(error));
                    } else if (!response.isSuccess()) {
                        view.showError(failure(response));
                    } else {
                        model.applyIdentity(response);
                        view.renderAuction(model);
                        view.showInfo("Da cap nhat ho so");
                    }
                }));
    }

    private void changePassword() {
        Optional<MainFrame.PasswordChangeInput> optional = view.promptPasswordChange();
        if (optional.isEmpty()) {
            return;
        }
        try (MainFrame.PasswordChangeInput input = optional.get()) {
            char[] current = input.getCurrent();
            char[] next = input.getNext();
            char[] confirmation = input.getConfirmation();
            try {
                if (!Arrays.equals(next, confirmation)) {
                    view.showError("Mat khau moi nhap lai khong khop");
                    return;
                }
                setAuctionBusy(true);
                accounts.changePassword(new String(current), new String(next))
                        .whenComplete((response, error) -> onEdt(() -> {
                            setAuctionBusy(false);
                            if (error != null) {
                                view.showError("Doi mat khau loi: " + rootMessage(error));
                            } else if (!response.isSuccess()) {
                                view.showError(failure(response));
                            } else {
                                view.showInfo("Doi mat khau thanh cong");
                            }
                        }));
            } finally {
                Arrays.fill(current, '\0');
                Arrays.fill(next, '\0');
                Arrays.fill(confirmation, '\0');
            }
        }
    }

    private void logout() {
        if (!view.confirm("Dang xuat tai khoan hien tai?")) {
            return;
        }
        autoReconnectEnabled = false;
        reconnect.cancel();
        setAuctionBusy(true);
        accounts.logout().whenComplete((response, error) -> onEdt(() -> {
            setAuctionBusy(false);
            model.clearIdentity();
            view.showLogin();
            if (error != null) {
                view.showError("Session local da xoa, server tra loi loi: " + rootMessage(error));
            }
        }));
    }

    private void handleConnectionState(ConnectionState state, String detail) {
        model.setConnectionState(state, detail);
        onEdt(() -> {
            view.renderConnection(state, detail);
            if (state == ConnectionState.DISCONNECTED && model.hasSessionToken()) {
                view.getAuctionPanel().appendNotification("Mat ket noi: " + detail);
            }
        });
        if (state == ConnectionState.DISCONNECTED
                && model.hasSessionToken()
                && autoReconnectEnabled
                && !closing.get()) {
            reconnect.start();
        }
    }

    private void resumeAfterReconnect(int retry) {
        if (closing.get() || !network.isConnected() || !model.hasSessionToken()) {
            return;
        }
        Long joinedBeforeDisconnect = model.getJoinedAuctionId();
        accounts.resumeSession(model.getSessionToken())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        onEdt(() -> view.showError("Resume session loi: " + rootMessage(error)));
                        return;
                    }
                    if (!response.isSuccess()) {
                        if ("ACCOUNT_ALREADY_ONLINE".equals(response.getErrorCode()) && retry < 5) {
                            CompletableFuture.delayedExecutor(600, TimeUnit.MILLISECONDS)
                                    .execute(() -> resumeAfterReconnect(retry + 1));
                            return;
                        }
                        onEdt(() -> {
                            autoReconnectEnabled = false;
                            model.clearIdentity();
                            view.showLogin();
                            view.showError("Khong phuc hoi duoc session: " + failure(response));
                        });
                        return;
                    }
                    model.applyIdentity(response);
                    auctions.resync(joinedBeforeDisconnect)
                            .whenComplete((sync, syncError) -> onEdt(() -> {
                                if (syncError != null) {
                                    view.showError("Resync loi: " + rootMessage(syncError));
                                    return;
                                }
                                if (!sync.isSuccess()) {
                                    view.showError(failure(sync));
                                    return;
                                }
                                if (joinedBeforeDisconnect == null) {
                                    applyAuctionList(sync);
                                } else {
                                    applySnapshot(sync, true);
                                }
                                view.showAuction(model);
                                view.getAuctionPanel().appendNotification(
                                        "Reconnect + resume + resync thanh cong");
                            }));
                });
    }

    private void handleEvent(WireMessage message) {
        Map<String, String> data = message.getData();
        switch (message.getType()) {
            case CONNECTION_WELCOME -> onEdt(() -> view.getAuctionPanel().appendNotification(
                    "TCP connected, connectionId=" + data.getOrDefault("connectionId", "")));
            case BID_UPDATE -> {
                applyBidUpdate(data);
                onEdt(() -> {
                    view.renderAuction(model);
                    view.getAuctionPanel().appendNotification(
                            "BID_UPDATE: " + data.getOrDefault("bid.username", "")
                                    + " -> " + data.getOrDefault("bid.amount", "") + " VND");
                });
            }
            case AUCTION_EXTENDED -> {
                long id = ClientWireParser.longValue(data, "auctionId", 0L);
                model.applyTick(
                        id,
                        ClientWireParser.instant(data, "endTime"),
                        "OPEN",
                        -1,
                        ClientWireParser.serverNow(data));
                onEdt(() -> {
                    view.renderAuction(model);
                    String source = data.getOrDefault("extensionSource", "ANTI_SNIPING");
                    view.getAuctionPanel().appendNotification(
                            source + ": auction " + id + " duoc gia han "
                                    + data.getOrDefault("extensionSeconds", "") + " giay");
                });
            }
            case AUCTION_CREATED -> {
                ClientAuction auction = ClientWireParser.auction(data, "");
                model.applySnapshot(
                        auction,
                        List.of(),
                        ClientWireParser.serverNow(data),
                        false);
                onEdt(() -> {
                    view.renderAuction(model);
                    view.getAuctionPanel().appendNotification(
                            "AUCTION_CREATED: " + auction.getProductName()
                                    + " boi " + auction.getHostUsername());
                });
            }
            case AUCTION_TICK -> {
                long id = ClientWireParser.longValue(data, "auctionId", 0L);
                model.applyTick(
                        id,
                        ClientWireParser.instant(data, "endTime"),
                        data.getOrDefault("status", ""),
                        ClientWireParser.integer(data, "watcherCount", -1),
                        ClientWireParser.serverNow(data));
                onEdt(() -> view.renderAuction(model));
            }
            case AUCTION_ENDED -> {
                ClientAuction auction = ClientWireParser.auction(data, "");
                model.applySnapshot(
                        auction,
                        model.currentBidSnapshot(),
                        ClientWireParser.serverNow(data),
                        false);
                onEdt(() -> {
                    view.renderAuction(model);
                    String winner = data.getOrDefault("winnerUsername", "");
                    view.getAuctionPanel().appendNotification(
                            "AUCTION_ENDED: " + auction.getProductName()
                                    + " | winner=" + (winner.isBlank() ? "khong co" : winner)
                                    + " | final=" + data.getOrDefault("finalPrice", ""));
                });
            }
            case AUCTION_CANCELLED -> {
                ClientAuction auction = ClientWireParser.auction(data, "");
                model.applySnapshot(
                        auction,
                        model.currentBidSnapshot(),
                        ClientWireParser.serverNow(data),
                        false);
                onEdt(() -> {
                    view.renderAuction(model);
                    view.getAuctionPanel().appendNotification(
                            "AUCTION_CANCELLED: " + auction.getProductName());
                });
            }
            case AUCTION_KICKED -> {
                long auctionId = ClientWireParser.longValue(data, "auctionId", 0L);
                Long joinedId = model.getJoinedAuctionId();
                if (joinedId != null && joinedId.longValue() == auctionId) {
                    model.leaveJoinedAuction();
                }
                onEdt(() -> {
                    view.renderAuction(model);
                    view.showError(data.getOrDefault(
                            "message", "Ban da bi moi khoi phong dau gia"));
                });
            }
            case OUTBID_NOTIFICATION -> onEdt(() -> view.getAuctionPanel().appendNotification(
                    "BAN DA BI VUOT GIA tai " + data.getOrDefault("productName", "")
                            + ", nguoi moi: " + data.getOrDefault("newLeader", "")));
            default -> {
                // Responses are completed through requestId; other events are intentionally ignored.
            }
        }
    }

    private void applyAuctionList(ApiResponse response) {
        Map<String, String> data = response.getWireMessage().getData();
        model.replaceAuctions(
                ClientWireParser.auctions(data),
                ClientWireParser.serverNow(data));
    }

    private void applySnapshot(ApiResponse response, boolean joined) {
        Map<String, String> data = response.getWireMessage().getData();
        model.applySnapshot(
                ClientWireParser.auction(data, ""),
                ClientWireParser.bids(data),
                ClientWireParser.serverNow(data),
                joined);
    }

    private void applyBidUpdate(Map<String, String> data) {
        ClientAuction auction = ClientWireParser.auction(data, "");
        ClientBid bid = ClientWireParser.bid(data, "bid.");
        model.applyAuctionUpdate(auction, bid, ClientWireParser.serverNow(data));
    }

    private void setLoginBusy(boolean busy) {
        onEdt(() -> view.getLoginPanel().setBusy(busy));
    }

    private void setAuctionBusy(boolean busy) {
        onEdt(() -> view.getAuctionPanel().setBusy(busy));
    }

    private String failure(ApiResponse response) {
        String code = response.getErrorCode();
        String message = response.getMessageText();
        return (code.isBlank() ? response.getType().name() : code)
                + (message.isBlank() ? "" : ": " + message);
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

    private void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        autoReconnectEnabled = false;
        clockTimer.stop();
        heartbeat.close();
        reconnect.close();
        network.close();
    }
}
