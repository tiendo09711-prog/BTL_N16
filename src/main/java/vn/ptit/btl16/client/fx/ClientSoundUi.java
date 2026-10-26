package vn.ptit.btl16.client.fx;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ClientSoundUi implements AutoCloseable {
    private final Stage owner;
    private final ClientSounds sounds;
    private final Set<Scene> scenes = new HashSet<>();
    private final Map<Window, ChangeListener<Scene>> windows = new HashMap<>();
    private final EventHandler<ActionEvent> actions = this::onAction;
    private final EventHandler<MouseEvent> tabs = this::onTab;
    private final ListChangeListener<Window> windowListener = change -> {
        while (change.next()) {
            for (Window window : change.getAddedSubList()) { if (belongsToClient(window)) { attach(window); } }
            for (Window window : change.getRemoved()) { detach(window); }
        }
    };

    public ClientSoundUi(Stage owner, ClientSounds sounds) {
        this.owner = owner;
        this.sounds = sounds;
        attach(owner);
        Window.getWindows().addListener(windowListener);
        for (Window window : Window.getWindows()) { if (belongsToClient(window)) { attach(window); } }
    }

    public static MenuButton controls(ClientSounds sounds) {
        MenuButton menu = new MenuButton();
        menu.setId("client-sound-settings");
        menu.getProperties().put("client.sound.silent", true);
        menu.getStyleClass().add("ghost");
        menu.textProperty().bind(Bindings.createStringBinding(() -> !sounds.availableProperty().get()
                ? "\u00c2m thanh: kh\u00f4ng kh\u1ea3 d\u1ee5ng" : sounds.isEnabled() && sounds.getVolume() > 0
                ? "\u00c2m thanh: b\u1eadt" : "\u00c2m thanh: t\u1eaft", sounds.enabledProperty(), sounds.volumeProperty(), sounds.availableProperty()));
        CheckMenuItem enabled = new CheckMenuItem("B\u1eadt \u00e2m thanh");
        enabled.setId("client-sound-enabled");
        enabled.setSelected(sounds.isEnabled());
        enabled.setOnAction(event -> sounds.setEnabled(enabled.isSelected()));
        sounds.enabledProperty().addListener((observable, before, after) -> enabled.setSelected(after));
        Slider volume = new Slider(0, 100, sounds.getVolume() * 100);
        volume.setId("client-sound-volume");
        volume.setPrefWidth(180);
        volume.setAccessibleText("\u00c2m l\u01b0\u1ee3ng hi\u1ec7u \u1ee9ng Client");
        volume.valueProperty().addListener((observable, before, after) -> sounds.setVolume(after.doubleValue() / 100));
        sounds.volumeProperty().addListener((observable, before, after) -> {
            if (Math.abs(volume.getValue() - after.doubleValue() * 100) > .01) { volume.setValue(after.doubleValue() * 100); }
        });
        Label amount = new Label();
        amount.textProperty().bind(Bindings.format("\u00c2m l\u01b0\u1ee3ng: %.0f%%", volume.valueProperty()));
        Button preview = new Button("Nghe th\u1eed");
        preview.setId("client-sound-preview");
        preview.setOnAction(event -> sounds.play(ClientSounds.Cue.SUCCESS));
        preview.disableProperty().bind(sounds.enabledProperty().not().or(sounds.volumeProperty().isEqualTo(0)).or(sounds.availableProperty().not()));
        VBox panel = new VBox(8, amount, volume, preview, new Label("Ch\u1ec9 hi\u1ec7u \u1ee9ng; kh\u00f4ng \u0111\u1ed5i \u00e2m l\u01b0\u1ee3ng video."));
        panel.getProperties().put("client.sound.silent", true);
        menu.getItems().setAll(enabled, new CustomMenuItem(panel, false));
        return menu;
    }

    private boolean belongsToClient(Window window) {
        while (window != null) {
            if (window == owner) { return true; }
            window = window instanceof Stage stage ? stage.getOwner() : window instanceof PopupWindow popup ? popup.getOwnerWindow() : null;
        }
        return false;
    }

    private void attach(Window window) {
        if (windows.containsKey(window)) { return; }
        ChangeListener<Scene> listener = (observable, previous, next) -> { uninstall(previous); install(next); };
        windows.put(window, listener);
        window.sceneProperty().addListener(listener);
        install(window.getScene());
    }

    private void detach(Window window) {
        ChangeListener<Scene> listener = windows.remove(window);
        if (listener != null) { window.sceneProperty().removeListener(listener); uninstall(window.getScene()); }
    }

    private void install(Scene scene) {
        if (scene == null || !scenes.add(scene)) { return; }
        scene.addEventFilter(ActionEvent.ACTION, actions);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, tabs);
        if (scene.getRoot() instanceof DialogPane dialog) {
            if (dialog.getStyleClass().contains("error")) { sounds.play(ClientSounds.Cue.ERROR); }
            else if (dialog.getStyleClass().contains("warning")) { sounds.play(ClientSounds.Cue.WARNING); }
        }
    }

    private void uninstall(Scene scene) {
        if (scene != null && scenes.remove(scene)) {
            scene.removeEventFilter(ActionEvent.ACTION, actions);
            scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, tabs);
        }
    }

    private boolean silent(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (Boolean.TRUE.equals(current.getProperties().get("client.sound.silent"))) { return true; }
        }
        return false;
    }

    private void onAction(ActionEvent event) {
        if (event.getTarget() instanceof Node node && (node instanceof ButtonBase || node instanceof TextField)
                && !node.isDisabled() && !silent(node) && !(node instanceof MenuButton)) {
            Object cue = node.getProperties().get("client.sound.cue");
            sounds.play(cue instanceof ClientSounds.Cue sound ? sound : ClientSounds.Cue.TAP);
        }
    }

    private void onTab(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || !(event.getTarget() instanceof Node node) || silent(node)) { return; }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current.getStyleClass().contains("tab") && !current.isDisabled()) { sounds.play(ClientSounds.Cue.NAVIGATE); return; }
        }
    }

    @Override
    public void close() {
        Window.getWindows().removeListener(windowListener);
        for (Window window : Set.copyOf(windows.keySet())) { detach(window); }
    }
}
