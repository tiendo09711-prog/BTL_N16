package vn.ptit.btl16.client.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.common.protocol.MessageType;
import java.util.*;
import java.util.function.*;

public final class PersonalNotificationsPane extends BorderPane {
    private final Stage stage = new Stage();
    private final ClientTransport transport;
    private final BooleanSupplier sameSession;
    private final BiConsumer<String, Long> open;
    private final TableView<Map<String, String>> table = new TableView<>();
    private final Label status = new Label();
    private final CheckBox unread = new CheckBox("Chi chua doc");
    private final Button next = new Button("Trang tiep");
    private long generation, cursor = Long.MAX_VALUE, nextCursor;
    private final Timeline poll = new Timeline(new KeyFrame(Duration.seconds(5), event -> { if (active() && cursor == Long.MAX_VALUE) { load(cursor); } }));

    public PersonalNotificationsPane(Window owner, ClientTransport transport, BooleanSupplier sameSession, BiConsumer<String, Long> open) {
        this.transport = transport; this.sameSession = sameSession; this.open = open;
        stage.initOwner(owner); stage.setTitle("Thong bao ca nhan"); setPadding(new Insets(18));
        WalletUi.column(table, "TRANG THAI", row -> Boolean.parseBoolean(row.get("read")) ? "Da doc" : "Chua doc", 90);
        WalletUi.column(table, "NOI DUNG", row -> title(row.get("kind")), 250);
        WalletUi.column(table, "PHIEN", row -> row.get("auctionId"), 80);
        WalletUi.column(table, "THOI GIAN", row -> WalletUi.time(java.time.Instant.parse(row.get("createdAt"))), 160);
        table.setPlaceholder(new Label("Chua co thong bao phu hop"));
        Button refresh = new Button("Trang dau / Thu lai"); refresh.setOnAction(event -> load(Long.MAX_VALUE));
        unread.setOnAction(event -> load(Long.MAX_VALUE)); next.setDisable(true); next.setOnAction(event -> load(nextCursor));
        Button read = new Button("Danh dau doc / chua doc"); read.setOnAction(event -> mark());
        Button related = new Button("Mo noi dung lien quan"); related.setOnAction(event -> {
            var row = table.getSelectionModel().getSelectedItem(); if (row == null || !active()) { return; }
            String room = row.get("auctionId"); open.accept(row.get("kind"), room.isEmpty() ? 0L : Long.parseLong(room));
        });
        setTop(new VBox(8, DesktopTheme.label("Thong bao cua toi", "page-title"),
                new Label("Thong bao khong cap quyen vao lai phong rieng/chat. Lien ket van kiem tra quyen hien tai."),
                new FlowPane(8, 8, unread, refresh, next, read, related), status)); setCenter(table);
        Scene scene = new Scene(this, 820, 500); DesktopTheme.apply(scene); stage.setScene(scene);
        stage.setOnHidden(event -> { generation++; poll.stop(); table.getItems().clear(); status.setText(""); }); poll.setCycleCount(Timeline.INDEFINITE);
    }
    public void show() { DesktopTheme.fitOnScreen(stage, 840, 540); stage.show(); load(Long.MAX_VALUE); poll.play(); }
    private boolean active() { if (!sameSession.getAsBoolean()) { stage.close(); return false; } return stage.isShowing(); }
    private static String title(String kind) {
        return switch (kind) {
            case "OUTBID" -> "Ban da bi vuot gia"; case "AUCTION_RESULT" -> "Server da chot ket qua phien";
            case "TOPUP_APPROVED" -> "Yeu cau nap mo phong da duoc duyet"; case "TOPUP_REJECTED" -> "Yeu cau nap bi tu choi - xem ly do trong vi";
            default -> "Thong bao";
        };
    }
    private void load(long before) {
        if (!active()) { return; } cursor = before; long token = ++generation; next.setDisable(true); status.setText("Dang tai...");
        transport.sendRequest(MessageType.GET_MY_NOTIFICATIONS, Map.of("beforeId", Long.toString(before), "unreadOnly", Boolean.toString(unread.isSelected())))
                .whenComplete((wire, error) -> Platform.runLater(() -> {
                    if (!active() || token != generation) { return; }
                    var response = wire == null ? null : new ApiResponse(wire);
                    if (error != null || response == null || !response.isSuccess()) { status.setText("Khong tai duoc. Bam Trang dau / Thu lai."); return; }
                    List<Map<String, String>> rows = new ArrayList<>();
                    for (int index = 0; index < Integer.parseInt(response.get("count")); index++) {
                        Map<String, String> row = new LinkedHashMap<>(); String prefix = "notification." + index + ".";
                        for (String key : List.of("id", "kind", "auctionId", "reference", "createdAt", "read")) { row.put(key, response.get(prefix + key)); }
                        rows.add(row);
                    }
                    table.getItems().setAll(rows); nextCursor = Long.parseLong(response.get("nextBeforeId")); next.setDisable(!Boolean.parseBoolean(response.get("hasMore")));
                    status.setText(response.get("unreadCount") + " chua doc" + (rows.isEmpty() ? " - Khong co thong bao phu hop" : ""));
                }));
    }
    private void mark() {
        var row = table.getSelectionModel().getSelectedItem(); if (row == null || !active()) { return; }
        long token = ++generation;
        transport.sendRequest(MessageType.READ_MY_NOTIFICATION, Map.of("notificationId", row.get("id"), "read", Boolean.toString(!Boolean.parseBoolean(row.get("read")))))
                .whenComplete((wire, error) -> Platform.runLater(() -> {
                    if (!active() || token != generation) { return; }
                    if (error != null || wire == null || !new ApiResponse(wire).isSuccess()) { status.setText("Khong cap nhat duoc. Bam Thu lai."); }
                    else { load(cursor); }
                }));
    }
}
