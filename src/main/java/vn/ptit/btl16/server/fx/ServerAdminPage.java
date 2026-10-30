package vn.ptit.btl16.server.fx;

import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.common.fx.DesktopTheme;

public abstract class ServerAdminPage extends BorderPane {
    private final Window owner;
    private boolean active;

    protected ServerAdminPage(Window owner) {
        this.owner = owner;
        getStyleClass().add("admin-page");
        setMinSize(0, 0);
    }

    public final void activate() {
        if (!active) {
            active = true;
            onActivate();
        }
    }

    public final void deactivate() {
        if (active) {
            active = false;
            onDeactivate();
        }
    }

    protected final boolean isActive() {
        return active;
    }

    protected final Window dialogOwner() {
        return getScene() == null ? owner : getScene().getWindow();
    }

    protected final Stage createWindow(String title, double width, double height) {
        Stage window = new Stage();
        window.initOwner(owner);
        window.initModality(Modality.WINDOW_MODAL);
        window.setTitle(title);
        window.getIcons().add(DesktopTheme.icon());
        Scene scene = new Scene(this, width, height);
        DesktopTheme.apply(scene);
        scene.getStylesheets().add(ServerAdminPage.class.getResource("/ui/server-admin.css").toExternalForm());
        window.setScene(scene);
        window.setMinWidth(860);
        window.setMinHeight(540);
        window.setOnHidden(event -> deactivate());
        DesktopTheme.fitOnScreen(window, width, height);
        return window;
    }

    protected abstract void onActivate();

    protected abstract void onDeactivate();
}
