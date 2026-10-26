package vn.ptit.btl16.server.fx;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import vn.ptit.btl16.common.fx.*;
import vn.ptit.btl16.server.ServerApplication;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class OperationLogWindow extends BorderPane {
    private final Stage stage = new Stage();
    private final Supplier<ServerApplication> server;
    private final Executor worker;
    private final TableView<Map<String, String>> table = new TableView<>();
    private final TextField room = new TextField(), type = new TextField();
    private final DatePicker from = new DatePicker(), to = new DatePicker();
    private final Label status = new Label();
    private final Button next = new Button("Trang tiep");
    private long generation, cursor = Long.MAX_VALUE;
    private Long selectedRoom;
    private String selectedType = "";
    private Instant selectedFrom, selectedTo;
    public OperationLogWindow(Window owner, Supplier<ServerApplication> server, Executor worker) {
        this.server = server; this.worker = worker; stage.initOwner(owner); stage.setTitle("Nhat ky van hanh"); setPadding(new Insets(18));
        room.setPromptText("Ma phien"); room.setPrefColumnCount(7); type.setPromptText("Loai: BID_ACCEPTED, ENDED...");
        from.setPromptText("Tu ngay"); to.setPromptText("Den ngay (gom ngay nay)");
        Button filter = new Button("Loc / Thu lai"); filter.setOnAction(event -> load(false)); next.setDisable(true); next.setOnAction(event -> load(true));
        setTop(new VBox(10, DesktopTheme.label("Nhat ky van hanh", "page-title"),
                new Label("Chi tren server. Khong hien danh tinh, chat, mat khau, token hay payload. Moc tu bao gom, den ngay bao gom."),
                new FlowPane(8, 8, room, type, from, to, filter, next), status));
        for (String key : List.of("id", "auctionId", "type", "time", "amount")) { WalletUi.column(table, key, row -> row.get(key), key.equals("time") ? 230 : 150); }
        table.setPlaceholder(new Label("Khong co su kien phu hop")); setCenter(table);
        Scene scene = new Scene(this, 950, 580); DesktopTheme.apply(scene); stage.setScene(scene); stage.setOnHidden(event -> generation++);
    }
    public void show() { DesktopTheme.fitOnScreen(stage, 970, 620); stage.show(); load(false); }
    private void load(boolean following) {
        var running = server.get(); if (running == null) { status.setText("Server dang dung"); return; }
        try {
            if (!following) {
                selectedRoom = room.getText().isBlank() ? null : Long.parseLong(room.getText().strip()); selectedType = type.getText().strip();
                selectedFrom = from.getValue() == null ? null : from.getValue().atStartOfDay(ZoneId.systemDefault()).toInstant();
                selectedTo = to.getValue() == null ? null : to.getValue().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(); cursor = Long.MAX_VALUE;
            }
        } catch (RuntimeException error) { status.setText("Ma phien/ngay khong hop le"); return; }
        Long filterRoom = selectedRoom; String filterType = selectedType; Instant filterFrom = selectedFrom, filterTo = selectedTo; long before = cursor;
        long token = ++generation; next.setDisable(true); status.setText("Dang tai...");
        CompletableFuture.supplyAsync(() -> running.operationEvents(filterRoom, filterType, filterFrom, filterTo, before), worker)
                .whenComplete((rows, error) -> Platform.runLater(() -> {
                    if (!stage.isShowing() || token != generation || running != server.get()) { return; }
                    if (error != null) { status.setText("Khong tai duoc. Kiem tra bo loc/ket noi database va bam Thu lai."); return; }
                    var page = rows.stream().limit(50).toList(); table.getItems().setAll(page);
                    cursor = page.isEmpty() ? 0 : Long.parseLong(page.get(page.size() - 1).get("id")); next.setDisable(rows.size() <= 50);
                    status.setText(page.isEmpty() ? "Khong co su kien phu hop" : page.size() + " su kien - loc truoc phan trang");
                }));
    }
}
