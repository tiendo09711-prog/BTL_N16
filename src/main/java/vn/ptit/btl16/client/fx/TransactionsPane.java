package vn.ptit.btl16.client.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import vn.ptit.btl16.client.service.WalletApi;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.common.wallet.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Personal ledger, with filters applied by the server before pagination. */
public final class TransactionsPane extends BorderPane implements AutoCloseable {
    private final WalletApi api;
    private final long user;
    private final BooleanSupplier sameSession;
    private final TextField room = new TextField();
    private final ComboBox<String> type = new ComboBox<>();
    private final DatePicker from = new DatePicker(), to = new DatePicker();
    private final TableView<WalletEntry> entries = WalletUi.ledger(false);
    private final Button previous = AccountUi.button("Trang trước", "left"), next = AccountUi.button("Trang tiếp", "right");
    private final Label status = DesktopTheme.label("", "account-note");
    private final Label balances = DesktopTheme.label("", "account-note");
    private final Timeline poll = new Timeline(new KeyFrame(Duration.seconds(5), event -> { if (!this.loading) { load(); } }));
    private final List<Long> cursors = new ArrayList<>(List.of(Long.MAX_VALUE));
    private Map<String, String> filters = Map.of();
    private int page;
    private long generation, nextCursor;
    private boolean active, loading, hasMore;

    public TransactionsPane(WalletApi api, long user, String username, BooleanSupplier sameSession) {
        this.api = api; this.user = user; this.sameSession = sameSession;
        setId("account-transactions"); getStyleClass().addAll("account-page", "transactions-page");
        VBox heading = new VBox(10, AccountUi.heading("Giao dịch", "Lịch sử cộng, trừ tiền của " + username + " từ nạp, rút và mua bán", "exchange", null), balances);
        heading.setPadding(new javafx.geometry.Insets(16)); setTop(heading);
        room.setId("transactions-room"); room.setPromptText("Mã phiên"); room.setPrefWidth(155); room.setPrefColumnCount(8);
        type.setId("transactions-type"); type.getItems().setAll("", "TOPUP", "WITHDRAWAL", "PURCHASE", "SALE", "ROOM_DEPOSIT", "DEPOSIT_RELEASE"); type.setValue(""); type.setPrefWidth(230);
        type.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String value) { return value == null || value.isEmpty() ? "Tất cả loại giao dịch" : WalletUi.type(value); }
            @Override public String fromString(String value) { return value; }
        });
        from.setId("transactions-from"); to.setId("transactions-to"); from.setPromptText("Từ ngày"); to.setPromptText("Đến ngày (gồm ngày này)");
        Button filter = AccountUi.button("Lọc / trang đầu", "search"); filter.setId("transactions-filter"); filter.getStyleClass().add("account-gold"); filter.setOnAction(event -> applyFilters());
        Button refresh = AccountUi.button("Làm mới", "refresh"); refresh.setId("transactions-refresh"); refresh.setOnAction(event -> load());
        FlowPane filterRow = new FlowPane(10, 10, room, type, from, to, filter, refresh);
        entries.setId("transactions-entries"); entries.getStyleClass().add("account-table"); entries.setFixedCellSize(46); entries.setMinHeight(220); entries.setPrefHeight(520);
        configureAmountColumn();
        entries.setPlaceholder(DesktopTheme.label("Chưa có giao dịch phù hợp. Các khoản nạp/rút xuất hiện khi đã được duyệt.", "account-note"));
        previous.setId("transactions-previous"); next.setId("transactions-next");
        previous.setOnAction(event -> { if (page > 0 && !loading) { page--; load(); } });
        next.setOnAction(event -> { if (!loading && hasMore) { if (cursors.size() <= page + 1) { cursors.add(nextCursor); } else { cursors.set(page + 1, nextCursor); } page++; load(); } });
        HBox footer = new HBox(12, status, DesktopTheme.spacer(), previous, next); footer.setAlignment(Pos.CENTER_LEFT);
        VBox card = AccountUi.card(filterRow, entries, footer); VBox.setVgrow(entries, Priority.ALWAYS);
        BorderPane.setMargin(card, new javafx.geometry.Insets(0, 16, 16, 16)); setCenter(card);
        poll.setCycleCount(Timeline.INDEFINITE); controls();
        type.setOnAction(event -> applyFilters());
        room.setOnAction(event -> applyFilters());
    }
    @SuppressWarnings("unchecked")
    private void configureAmountColumn() {
        ((TableColumn<WalletEntry, String>) entries.getColumns().get(1)).setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty); setText(null); getStyleClass().removeAll("account-status-green", "account-status-red");
                if (!empty && getTableRow() != null && getTableRow().getItem() != null) {
                    BigDecimal amount = getTableRow().getItem().amount();
                    setText((amount.signum() > 0 ? "+" : "") + WalletUi.money(amount));
                    if (amount.signum() != 0) { getStyleClass().add(amount.signum() > 0 ? "account-status-green" : "account-status-red"); }
                }
            }
        });
    }
    public void start() { active = true; poll.play(); applyFilters(); }
    private boolean active() { return active && sameSession.getAsBoolean() && getScene() != null; }
    @Override public void close() { active = false; generation++; poll.stop(); entries.getItems().clear(); balances.setText(""); }
    private void applyFilters() {
        if (!active()) { return; }
        try {
            var values = new LinkedHashMap<String, String>(); String roomValue = room.getText().strip();
            if (!roomValue.isEmpty() && Long.parseLong(roomValue) <= 0) { throw new IllegalArgumentException(); }
            values.put("auctionId", roomValue); values.put("type", type.getValue()); var zone = java.time.ZoneId.systemDefault();
            if (from.getValue() != null) { values.put("from", from.getValue().atStartOfDay(zone).toInstant().toString()); }
            if (to.getValue() != null) { values.put("to", to.getValue().plusDays(1).atStartOfDay(zone).toInstant().toString()); }
            if (from.getValue() != null && to.getValue() != null && from.getValue().isAfter(to.getValue())) { throw new IllegalArgumentException(); }
            filters = Map.copyOf(values); page = 0; cursors.clear(); cursors.add(Long.MAX_VALUE);
            entries.getItems().clear(); status.setText("Đang tải giao dịch…"); load();
        } catch (RuntimeException error) { status.setText("Mã phiên hoặc khoảng ngày không hợp lệ"); }
    }
    private void load() {
        if (!active()) { return; }
        long token = ++generation; loading = true; controls();
        var values = new LinkedHashMap<>(filters); values.put("beforeId", Long.toString(cursors.get(page)));
        values.put("hideReservations", "true");
        values.put("hideLegacyReview", "true");
        api.entries(values).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!active() || token != generation) { return; }
            loading = false;
            if (error != null || response == null || !response.isSuccess() || !Long.toString(user).equals(response.get("userId"))) {
                hasMore = false; status.setText("Không tải được giao dịch. Bấm Làm mới để thử lại."); controls(); return;
            }
            entries.getItems().setAll(WalletWire.entries(response.getWireMessage().getData()));
            hasMore = Boolean.parseBoolean(response.get("hasMore")); nextCursor = Long.parseLong(response.get("nextBeforeId"));
            balances.setText("Số dư ví: " + WalletUi.money(new BigDecimal(response.get("balance"))) + "    •    Khả dụng: " + WalletUi.money(new BigDecimal(response.get("available"))));
            status.setText("Trang " + (page + 1) + " • " + entries.getItems().size() + " giao dịch • Mới nhất trước"); controls();
        }));
    }
    private void controls() { previous.setDisable(loading || page == 0); next.setDisable(loading || !hasMore); }
}
