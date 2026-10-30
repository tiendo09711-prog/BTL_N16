package vn.ptit.btl16.client.fx;

import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class RoomProductDetails extends VBox {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final Label opening = new Label();
    private final Label increment = new Label();
    private final Label starts = new Label();
    private final Label ends = new Label();
    private final Label description = new Label();
    private final Label deposit = new Label();

    RoomProductDetails() {
        super(18);
        setId("room-product-information"); getStyleClass().addAll("card", "room-product-information");
        FlowPane facts = new FlowPane(24, 18,
                fact("Gi\u00e1 kh\u1edfi \u0111i\u1ec3m", opening), fact("B\u01b0\u1edbc gi\u00e1", increment),
                fact("B\u1eaft \u0111\u1ea7u", starts), fact("K\u1ebft th\u00fac", ends));
        description.setId("room-product-description"); description.setWrapText(true);
        description.setMinWidth(0); description.setMaxWidth(Double.MAX_VALUE);
        description.getStyleClass().add("room-product-description");
        deposit.getStyleClass().add("muted"); deposit.setWrapText(true);
        getChildren().setAll(facts, new javafx.scene.control.Separator(),
                new VBox(10, DesktopTheme.label("V\u1ec1 s\u1ea3n ph\u1ea9m", "section-title"), description), deposit);
        setMinHeight(USE_PREF_SIZE);
    }

    void update(ClientAuction auction) {
        if (auction == null) {
            return;
        }
        opening.setText(WalletUi.money(auction.getStartPrice()));
        increment.setText(WalletUi.money(auction.getMinBidIncrement()));
        starts.setText(date(auction.getStartTime())); ends.setText(date(auction.getEndTime()));
        description.setText(auction.getDescription().isBlank() ? "Ch\u01b0a c\u00f3 m\u00f4 t\u1ea3 s\u1ea3n ph\u1ea9m." : auction.getDescription());
        deposit.setText("\u0110\u1eb7t c\u1ecdc: " + WalletUi.money(auction.getDepositAmount()));
        deposit.setVisible(auction.getDepositAmount().signum() > 0); deposit.setManaged(deposit.isVisible());
    }

    private static VBox fact(String title, Label value) {
        value.getStyleClass().add("room-product-fact-value"); value.setWrapText(true);
        VBox box = new VBox(5, DesktopTheme.label(title, "muted"), value);
        box.setPrefWidth(180); box.setMinWidth(0);
        return box;
    }

    private static String date(Instant value) { return value == null ? "Ch\u01b0a x\u00e1c \u0111\u1ecbnh" : DATE.format(value); }
}
