package vn.ptit.btl16.server.fx;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import vn.ptit.btl16.common.fx.*;
import vn.ptit.btl16.server.ServerApplication;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class OperationLogWindow extends ServerAdminPage {
    private Stage stage;
    private final Supplier<ServerApplication> server;
    private final Executor worker;
    private final TableView<Map<String, String>> table = new TableView<>();
    private final TextField room = new TextField(), type = new TextField();
    private final DatePicker from = new DatePicker(), to = new DatePicker();
    private final Label status = new Label();
    private final Button next = new Button("Trang tiếp");
    private final Button filter = new Button("Lọc");
    private long generation, cursor = Long.MAX_VALUE;
    private Long selectedRoom;
    private String selectedType = "";
    private Instant selectedFrom, selectedTo;
    private ServerApplication loadedServer;
    private boolean loading;
    public OperationLogWindow(Window owner, Supplier<ServerApplication> server, Executor worker) {
        super(owner);
        this.server = server; this.worker = worker; setId("server-operations-page"); setPadding(new Insets(20,28,20,28));
        room.setPromptText("Mã phiên"); room.setPrefColumnCount(7); type.setPromptText("Loại sự kiện");
        type.setTooltip(new Tooltip("BID_ACCEPTED, ENDED, CANCELLED"));
        room.setId("server-log-room"); type.setId("server-log-type");
        from.setId("server-log-from"); to.setId("server-log-to");
        from.setPrefWidth(170); to.setPrefWidth(170);
        from.setPromptText("Từ ngày"); to.setPromptText("Đến ngày");
        filter.setOnAction(event -> load(false)); next.setDisable(true); next.setOnAction(event -> load(true));
        room.setOnAction(event -> load(false)); type.setOnAction(event -> load(false));
        filter.setId("server-log-filter"); next.setId("server-log-next"); status.setId("server-log-status");
        FlowPane filters = new FlowPane(8, 8, room, type, from, to, filter); filters.getStyleClass().add("admin-log-filters");
        VBox heading = new VBox(filters);
        heading.setPadding(new Insets(0,0,18,0)); setTop(heading);
        Map<String,String> titles = Map.of("id","Mã","auctionId","Phiên","type","Sự kiện","time","Thời điểm","amount","Số tiền");
        for (String key : List.of("id", "auctionId", "type", "time", "amount")) { WalletUi.column(table, titles.get(key), row -> row.get(key), key.equals("time") ? 230 : 150); }
        table.setId("server-operation-events"); table.setMinHeight(100);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().getLast().getStyleClass().add("money-column");
        table.setPlaceholder(new Label("Không có sự kiện phù hợp")); setCenter(table);
        status.setWrapText(true); status.getStyleClass().add("muted"); status.setMinWidth(0);
        HBox footer = new HBox(12,status,DesktopTheme.spacer(),next); footer.setPadding(new Insets(12,0,0,0)); setBottom(footer);
    }
    public void show() {
        if (stage == null) { stage = createWindow("Nhật ký vận hành", 970, 620); }
        stage.show(); activate();
    }
    @Override protected void onActivate() { if (loadedServer != server.get()) { load(false); } }
    @Override protected void onDeactivate() {
        generation++;
        if (loading) { loadedServer = null; }
        loading = false;
        filter.setDisable(false);
    }
    private void load(boolean following) {
        if (!isActive()) { return; }
        var running = server.get(); if (running == null) { status.setText("Server đang dừng"); return; }
        try {
            if (!following) {
                selectedRoom = room.getText().isBlank() ? null : Long.parseLong(room.getText().strip()); selectedType = type.getText().strip();
                selectedFrom = from.getValue() == null ? null : from.getValue().atStartOfDay(ZoneId.systemDefault()).toInstant();
                selectedTo = to.getValue() == null ? null : to.getValue().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(); cursor = Long.MAX_VALUE;
            }
        } catch (RuntimeException error) { status.setText("Mã phiên/ngày không hợp lệ"); return; }
        Long filterRoom = selectedRoom; String filterType = selectedType; Instant filterFrom = selectedFrom, filterTo = selectedTo; long before = cursor;
        boolean nextWasDisabled = next.isDisabled();
        long token = ++generation; loading = true; next.setDisable(true); filter.setDisable(true); status.setText("Đang tải...");
        CompletableFuture.supplyAsync(() -> running.operationEvents(filterRoom, filterType, filterFrom, filterTo, before), worker)
                .whenComplete((rows, error) -> Platform.runLater(() -> {
                    if (!isActive() || token != generation || running != server.get()) { return; }
                    loading = false; filter.setDisable(false);
                    if (error != null) { next.setDisable(nextWasDisabled); status.setText("Chưa tải được dữ liệu. Thử lọc lại."); return; }
                    loadedServer = running;
                    var page = rows.stream().limit(50).toList(); table.getItems().setAll(page);
                    cursor = page.isEmpty() ? 0 : Long.parseLong(page.get(page.size() - 1).get("id")); next.setDisable(rows.size() <= 50);
                    status.setText(page.isEmpty() ? "Không có sự kiện phù hợp" : page.size() + " sự kiện");
                }));
    }
}
