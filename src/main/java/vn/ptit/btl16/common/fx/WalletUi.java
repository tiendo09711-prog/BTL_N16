package vn.ptit.btl16.common.fx;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import vn.ptit.btl16.common.wallet.WalletEntry;
import vn.ptit.btl16.common.wallet.TopUpRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

public final class WalletUi {
    private WalletUi() { }
    public static String money(BigDecimal amount) { return vn.ptit.btl16.common.util.Money.input(amount) + " \u20ab"; }
    public static String time(Instant time) { return time == null ? "-" : DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault()).format(time); }
    public static String status(String status) {
        return switch (status) { case "PENDING" -> "Ch\u1edd duy\u1ec7t"; case "APPROVED" -> "\u0110\u00e3 c\u1ed9ng ti\u1ec1n"; default -> "T\u1eeb ch\u1ed1i"; };
    }
    public static String type(String type) {
        return switch (type) {
            case "TOPUP" -> "N\u1ea1p demo \u0111\u00e3 duy\u1ec7t"; case "RESERVE" -> "Gi\u1eef ti\u1ec1n \u0111\u1eb7t gi\u00e1";
            case "RELEASE" -> "Ho\u00e0n ti\u1ec1n gi\u1eef ch\u1ed7"; case "PURCHASE" -> "Thanh to\u00e1n mua h\u00e0ng";
            case "SALE" -> "Thu ti\u1ec1n b\u00e1n h\u00e0ng"; case "LEGACY_REVIEW" -> "Phi\u00ean c\u0169: c\u1ea7n ki\u1ec3m tra thanh to\u00e1n"; default -> type;
        };
    }
    public static <Row> void column(TableView<Row> table, String title, Function<Row, String> value, double width) {
        TableColumn<Row, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width); table.getColumns().add(column);
    }
    public static TableView<WalletEntry> ledger() {
        TableView<WalletEntry> table = new TableView<>();
        column(table, "GIAO D\u1ecaCH", entry -> type(entry.type()), 185);
        column(table, "BI\u1ebeN \u0110\u1ed8NG TI\u1ec0N", entry -> money(entry.amount()), 140);
        column(table, "GI\u1eee CH\u1ed6 +/-", entry -> money(entry.reservedDelta()), 140);
        column(table, "S\u1ed0 D\u01af SAU", entry -> money(entry.balanceAfter()), 140);
        column(table, "TH\u1edcI GIAN", entry -> time(entry.createdAt()), 140);
        column(table, "THAM CHI\u1ebeU", WalletEntry::reference, 180);
        table.setPrefHeight(210); return table;
    }
    public static TableView<TopUpRequest> requests(boolean admin) {
        TableView<TopUpRequest> table = new TableView<>();
        if (admin) { column(table, "T\u00c0I KHO\u1ea2N", TopUpRequest::username, 150); }
        column(table, "S\u1ed0 TI\u1ec0N", request -> money(request.amount()), 160);
        column(table, "TR\u1ea0NG TH\u00c1I", request -> status(request.status()), 160);
        column(table, "T\u1ea0O L\u00daC", request -> time(request.createdAt()), 150);
        column(table, "M\u00c3 Y\u00caU C\u1ea6U", TopUpRequest::id, 290);
        column(table, "LY DO TU CHOI", TopUpRequest::rejectionReason, 300);
        table.setPrefHeight(180); return table;
    }
}
