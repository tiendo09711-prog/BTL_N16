package vn.ptit.btl16.client.fx;

import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.common.fx.DesktopTheme;

/** Standalone adapter for callers outside the main account shell. */
public final class AuctionRulesWindow {
    private final Stage stage = new Stage();
    public AuctionRulesWindow(Window owner) {
        stage.initOwner(owner); stage.setTitle("Luật đấu giá");
        Button close = AccountUi.close(stage::close); close.setId("auction-rules-close");
        BorderPane header = new BorderPane(); header.setRight(close);
        BorderPane root = new BorderPane(new AuctionRulesPane()); root.setTop(header);
        AccountUi.window(stage, root, 1100, 760);
    }
    public void show() { DesktopTheme.fitOnScreen(stage, 1100, 760); stage.show(); }
}
