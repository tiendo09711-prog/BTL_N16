package vn.ptit.btl16.client.fx;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.model.ClientProduct;
import vn.ptit.btl16.client.service.ProductMediaApi;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.media.ImageFormats;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ProductMediaEditor {
    private final ProductMediaApi api;
    private final ClientProduct product;
    private final Runnable saved;
    private final Stage stage = new Stage();
    private final ObservableList<Entry> entries = FXCollections.observableArrayList();
    private final ListView<Entry> list = new ListView<>(entries);
    private final TextField code = new TextField();
    private final TextField name = new TextField();
    private final TextArea description = new TextArea();
    private final Label message = new Label();
    private final Label counts = new Label();
    private final Label characters = new Label();
    private final Label limits = new Label();
    private final Button save = new Button("L\u01b0u s\u1ea3n ph\u1ea9m");
    private final Button addImages = new Button("+ Ch\u1ecdn nhi\u1ec1u \u1ea3nh");
    private final Button addVideo = new Button("+ Video");
    private final ToggleButton crop = new ToggleButton("Ch\u1ecdn v\u00f9ng c\u1eaft");
    private final MediaGalleryPane preview;
    private final HBox editTools;
    private String draft;
    private CompletableFuture<JsonNode> operation;
    private boolean busy, committed, closed;

    public ProductMediaEditor(Window owner, ProductMediaApi api, ClientProduct product, Runnable saved) {
        this.api = api; this.product = product; this.saved = saved; preview = new MediaGalleryPane(api);
        save.getStyleClass().add("primary"); addImages.getStyleClass().add("teal");
        stage.initOwner(owner); stage.initModality(Modality.WINDOW_MODAL); stage.setTitle(product == null ? "\u0110\u0103ng s\u1ea3n ph\u1ea9m" : "Ch\u1ec9nh s\u1eeda s\u1ea3n ph\u1ea9m");
        code.setPromptText("M\u00e3 duy nh\u1ea5t, 2-30 k\u00fd t\u1ef1"); name.setPromptText("T\u00ean s\u1ea3n ph\u1ea9m");
        description.setPromptText("M\u00f4 t\u1ea3 \u0111\u1eb7c \u0111i\u1ec3m, t\u00ecnh tr\u1ea1ng v\u00e0 khi\u1ebfm khuy\u1ebft n\u1ebfu c\u00f3 (50-2000 k\u00fd t\u1ef1)"); description.setWrapText(true); description.setPrefRowCount(4);
        if (product != null) { code.setText(product.getCode()); name.setText(product.getName()); description.setText(product.getDescription()); }
        description.textProperty().addListener((observable, before, now) -> refresh());
        list.setPrefWidth(310); list.setMinHeight(180); list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(Entry entry, boolean empty) {
                super.updateItem(entry, empty); setText(null);
                if (empty || entry == null) { setGraphic(null); return; }
                ImageView thumbnail = new ImageView(entry.thumbnail); thumbnail.setFitWidth(58); thumbnail.setFitHeight(46); thumbnail.setPreserveRatio(true);
                Label title = new Label(entry.name); title.setMaxWidth(205);
                Label state = new Label(entry.state); state.setWrapText(true); state.setMaxWidth(220);
                ProgressBar progress = new ProgressBar(entry.progress); progress.setPrefWidth(210);
                VBox text = new VBox(4, title, state, progress); setGraphic(new HBox(8, thumbnail, text));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((observable, before, entry) -> {
            if (entry != null && entry.media != null) { List<Entry> ready = ready(); int index = ready.indexOf(entry); if (preview.selectedIndex() != index) { preview.select(index); } }
        });
        preview.onSelection(index -> { List<Entry> ready = ready(); if (index >= 0 && index < ready.size()) { list.getSelectionModel().select(ready.get(index)); } });
        preview.onRetry(this::refreshPreview);
        Button remove = button("X\u00f3a", this::remove); Button replace = button("Thay t\u1ec7p", this::replace);
        Button cover = button("L\u00e0m \u0111\u1ea1i di\u1ec7n", () -> move(true, -1)); Button left = button("\u2191", () -> move(false, -1)); Button right = button("\u2193", () -> move(false, 1));
        crop.setOnAction(event -> preview.imagePane().setCropping(crop.isSelected()));
        Button applyCrop = button("X\u00e1c nh\u1eadn c\u1eaft", this::crop);
        editTools = new HBox(8, button("Xoay 90\u00b0", this::rotate), crop, applyCrop, button("\u0110\u1eb7t l\u1ea1i \u1ea3nh g\u1ed1c", this::reset));
        VBox information = new VBox(9, new Label("TH\u00d4NG TIN S\u1ea2N PH\u1ea8M"), code, name, description, characters,
                new HBox(8, addImages, addVideo), counts, limits, list, new HBox(6, remove, replace), new HBox(6, cover, left, right));
        information.setPadding(new Insets(16)); information.setPrefWidth(340); VBox.setVgrow(list, Priority.ALWAYS); limits.setWrapText(true); limits.setMaxWidth(300); limits.setMinHeight(Region.USE_PREF_SIZE);
        VBox gallery = new VBox(10, preview, editTools); VBox.setVgrow(preview, Priority.ALWAYS); gallery.setPadding(new Insets(8));
        SplitPane split = new SplitPane(information, gallery); split.setDividerPositions(0.31);
        Button cancel = button("H\u1ee7y", stage::close); HBox actions = new HBox(12, message, cancel, save); actions.setPadding(new Insets(14)); HBox.setHgrow(message, Priority.ALWAYS); message.setMaxWidth(Double.MAX_VALUE); message.setWrapText(true);
        BorderPane root = new BorderPane(split); root.setBottom(actions); root.setStyle("-fx-background-color: #eef5f4;");
        Scene scene = new Scene(root, 1200, 760); DesktopTheme.apply(scene);
        stage.setScene(scene); stage.setMinWidth(950); stage.setMinHeight(650);
        addImages.setOnAction(event -> choose("IMAGE", false)); addVideo.setOnAction(event -> choose("VIDEO", false)); save.setOnAction(event -> save());
        stage.setOnHidden(event -> { closed = true; preview.close(); if (!committed && operation != null) { operation.cancel(true); } if (!committed && draft != null) { api.cancel(draft); } });
        refresh();
    }

    public void show() {
        stage.show(); DesktopTheme.reveal(stage.getScene().getRoot()); message.setText("\u0110ang chu\u1ea9n b\u1ecb kho media...");
        api.begin(product == null ? 0 : product.getProductId()).whenComplete((id, error) -> Platform.runLater(() -> {
            if (error != null) { message.setText(ProductMediaApi.message(error)); return; }
            draft = id; if (closed) { api.cancel(id); return; }
            api.limits().whenComplete((body, failure) -> Platform.runLater(() -> {
                if (failure == null && !closed) { limits.setText(ImageFormats.LABEL + " \u00b7 \u1ea3nh \u2264 " + (body.path("imageBytes").asLong() >> 20) + " MiB / " + body.path("imagePixels").asLong() / 1_000_000 + " MP; video \u2264 " + (body.path("videoBytes").asLong() >> 20) + " MiB / " + body.path("videoSeconds").asInt() + " gi\u00e2y"); }
            }));
            if (product == null) { message.setText(""); refresh(); }
            else {
                busy = true;
                api.manifest(product.getProductId(), 0).whenComplete((body, failure) -> Platform.runLater(() -> {
                    busy = false; if (closed) { return; }
                    if (failure != null) { message.setText(ProductMediaApi.message(failure)); }
                    else { body.path("media").forEach(media -> entries.add(new Entry(media))); message.setText(""); refreshPreview(); }
                    refresh();
                }));
            }
        }));
    }

    private FileChooser chooser(String kind) {
        FileChooser chooser = new FileChooser(); chooser.setTitle(kind.equals("IMAGE") ? "Ch\u1ecdn \u1ea3nh s\u1ea3n ph\u1ea9m" : "Ch\u1ecdn video");
        chooser.getExtensionFilters().add(kind.equals("IMAGE") ? new FileChooser.ExtensionFilter(ImageFormats.LABEL, ImageFormats.chooserPatterns())
                : new FileChooser.ExtensionFilter("MP4, MOV, WebM, MKV, AVI", "*.mp4", "*.mov", "*.webm", "*.mkv", "*.avi"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("T\u1ea5t c\u1ea3 t\u1ec7p", "*")); return chooser;
    }
    private void choose(String kind, boolean replace) {
        if (draft == null || busy) { return; }
        List<File> files;
        if (kind.equals("IMAGE") && !replace) { files = chooser(kind).showOpenMultipleDialog(stage); }
        else { File file = chooser(kind).showOpenDialog(stage); files = file == null ? null : List.of(file); }
        if (files == null) { return; }
        addFiles(files, kind, replace);
    }
    private void addFiles(List<File> files, String kind, boolean replace) {
        if (draft == null || busy || closed) { return; }
        long count = entries.stream().filter(entry -> entry.kind.equals(kind)).count();
        if (count + files.size() - (replace ? 1 : 0) > (kind.equals("IMAGE") ? 5 : 1)) { message.setText("Ch\u1ec9 ch\u1ecdn 1-5 \u1ea3nh v\u00e0 t\u1ed1i \u0111a 1 video."); return; }
        Entry replaced = replace ? list.getSelectionModel().getSelectedItem() : null;
        List<Entry> pending = new ArrayList<>(); for (File file : files) { Entry entry = new Entry(file, kind); entries.add(entry); pending.add(entry); }
        busy = true; refresh(); uploadNext(pending, 0, replaced);
    }
    private void uploadNext(List<Entry> pending, int index, Entry replaced) {
        if (closed) { return; }
        if (index >= pending.size()) { busy = false; refresh(); return; }
        Entry entry = pending.get(index); entry.state = "\u0110ang t\u1ea3i..."; list.refresh();
        operation = api.upload(draft, entry.file.toPath(), entry.kind, progress -> {
            if (progress == 1 || progress - entry.lastReported > 0.02) { entry.lastReported = progress; Platform.runLater(() -> { entry.progress = progress == 1 ? -1 : progress; entry.state = progress == 1 ? "\u0110ang ki\u1ec3m tra / chuy\u1ec3n \u0111\u1ed5i..." : "\u0110ang t\u1ea3i " + (int) (progress * 100) + "%"; list.refresh(); }); }
        });
        operation.whenComplete((media, error) -> Platform.runLater(() -> {
            if (closed) { return; }
            if (error != null) { entry.state = ProductMediaApi.message(error); entry.progress = 0; }
            else { entry.install(media); if (replaced != null) { entries.remove(replaced); if (replaced.media != null) { api.remove(draft, replaced.id()); } } refreshPreview(); }
            list.refresh(); uploadNext(pending, index + 1, null);
        }));
    }
    private void replace() { Entry entry = list.getSelectionModel().getSelectedItem(); if (entry != null) { choose(entry.kind, true); } }
    private void remove() {
        if (busy) { return; } Entry entry = list.getSelectionModel().getSelectedItem(); if (entry == null) { return; }
        entries.remove(entry); if (entry.media != null && draft != null) { api.remove(draft, entry.id()); } refreshPreview(); refresh();
    }
    private void move(boolean cover, int delta) {
        if (busy) { return; } Entry entry = list.getSelectionModel().getSelectedItem(); if (entry == null || !entry.kind.equals("IMAGE")) { return; }
        int index = entries.indexOf(entry); int next = cover ? 0 : index + delta;
        if (next >= 0 && next < entries.size()) { Collections.swap(entries, index, next); list.getSelectionModel().select(entry); refreshPreview(); }
    }
    private void rotate() { Entry entry = selectedImage(); if (entry != null) { transform(entry, (entry.turns + 1) % 4, new Rectangle2D(0, 0, 1, 1)); } }
    private void reset() { Entry entry = selectedImage(); if (entry != null) { transform(entry, 0, new Rectangle2D(0, 0, 1, 1)); } }
    private void crop() {
        Entry entry = selectedImage(); Rectangle2D selection = preview.imagePane().crop();
        if (entry == null || selection == null) { message.setText("B\u1eadt Ch\u1ecdn v\u00f9ng c\u1eaft v\u00e0 k\u00e9o m\u1ed9t v\u00f9ng tr\u00ean \u1ea3nh."); return; }
        Rectangle2D original = entry.crop;
        transform(entry, entry.turns, new Rectangle2D(original.getMinX() + selection.getMinX() * original.getWidth(), original.getMinY() + selection.getMinY() * original.getHeight(),
                selection.getWidth() * original.getWidth(), selection.getHeight() * original.getHeight()));
    }
    private Entry selectedImage() { Entry entry = list.getSelectionModel().getSelectedItem(); return !busy && entry != null && entry.media != null && entry.kind.equals("IMAGE") ? entry : null; }
    private void transform(Entry entry, int turns, Rectangle2D rectangle) {
        busy = true; entry.state = "\u0110ang x\u1eed l\u00fd \u1ea3nh..."; entry.progress = -1; refresh();
        String previous = entry.id(); operation = api.transform(draft, previous, turns, rectangle.getMinX(), rectangle.getMinY(), rectangle.getWidth(), rectangle.getHeight());
        operation.whenComplete((media, error) -> Platform.runLater(() -> {
            busy = false; if (closed) { return; } entry.progress = 1;
            if (error != null) { entry.state = "S\u1eb5n s\u00e0ng"; message.setText(ProductMediaApi.message(error)); }
            else { entry.install(media); entry.turns = turns; entry.crop = rectangle; api.remove(draft, previous); crop.setSelected(false); preview.imagePane().setCropping(false); refreshPreview(); }
            refresh();
        }));
    }
    private void save() {
        if (busy || draft == null) { return; } busy = true; refresh(); message.setText("\u0110ang l\u01b0u...");
        operation = api.commit(draft, code.getText(), name.getText(), description.getText(), ready().stream().map(Entry::id).toList());
        operation.whenComplete((body, error) -> Platform.runLater(() -> {
            busy = false; if (closed) { return; }
            if (error != null) { message.setText(ProductMediaApi.message(error)); refresh(); }
            else { committed = true; stage.close(); saved.run(); }
        }));
    }
    private List<Entry> ready() { return entries.stream().filter(entry -> entry.media != null).toList(); }
    private void refreshPreview() { preview.setItems(ready().stream().map(entry -> entry.media).toList()); }
    private void refresh() {
        int length = description.getText().strip().codePointCount(0, description.getText().strip().length()); characters.setText(length + "/2000 k\u00fd t\u1ef1 \u00b7 t\u1ed1i thi\u1ec3u 50");
        long images = entries.stream().filter(entry -> entry.kind.equals("IMAGE")).count(); long videos = entries.size() - images;
        counts.setText(images + "/5 \u1ea3nh \u00b7 " + videos + "/1 video \u00b7 \u1ea3nh \u0111\u1ea7u l\u00e0 \u0111\u1ea1i di\u1ec7n");
        save.setDisable(busy || draft == null || images < 1 || images > 5 || videos > 1 || ready().size() != entries.size() || length < 50 || length > 2000);
        addImages.setDisable(busy || draft == null); addVideo.setDisable(busy || draft == null); editTools.setDisable(busy || draft == null); list.refresh();
    }
    private static Button button(String title, Runnable action) { Button button = new Button(title); button.setOnAction(event -> action.run()); return button; }
    private final class Entry {
        final String name; final String kind; final File file; JsonNode media; Image thumbnail; String state = "\u0110ang ch\u1edd"; double progress, lastReported; int turns;
        Rectangle2D crop = new Rectangle2D(0, 0, 1, 1);
        Entry(File file, String kind) { this.file = file; this.name = file.getName(); this.kind = kind; }
        Entry(JsonNode media) { this.file = null; this.name = media.path("name").asText(); this.kind = media.path("kind").asText(); install(media); }
        String id() { return media.path("id").asText(); }
        void install(JsonNode media) { this.media = media; this.progress = 1; this.state = "S\u1eb5n s\u00e0ng"; this.thumbnail = new Image(api.url(media.path("thumbnail").asText()), 60, 48, true, true, true); }
    }
}
