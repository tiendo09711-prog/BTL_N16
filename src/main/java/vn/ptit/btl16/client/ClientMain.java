package vn.ptit.btl16.client;

import javafx.application.Application;
import vn.ptit.btl16.client.fx.JavaFxClientApp;

/** JavaFX desktop client entry point. */
public final class ClientMain {
    private ClientMain() {
    }

    public static void main(String[] args) {
        Application.launch(JavaFxClientApp.class, args);
    }
}
