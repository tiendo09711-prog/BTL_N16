package vn.ptit.btl16.client.fx;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import vn.ptit.btl16.client.model.ClientProduct;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.common.util.Money;
import vn.ptit.btl16.common.util.Times;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/** A single modal keeps the seller's draft intact between editing and confirmation. */
final class AuctionCreationDialog extends Dialog<AuctionCreationDialog.Draft> {
    record Draft(ClientProduct product, BigDecimal startPrice, BigDecimal increment, int durationMinutes,
                 String visibility, String roomPassword, boolean anonymousGuests, Instant startTime,
                 int maxParticipants, BigDecimal depositAmount) { }

    private final Supplier<Instant> now;
    private final ComboBox<ClientProduct> product = new ComboBox<>();
    private final TextField price = input("auction-create-price", "100000");
    private final TextField increment = input("auction-create-increment", "10000");
    private final TextField duration = input("auction-create-duration", "5");
    private final TextField maxParticipants = input("auction-create-max-participants", "");
    private final TextField depositAmount = input("auction-create-deposit", "");
    private final PasswordField password = new PasswordField(), repeatedPassword = new PasswordField();
    private final RadioButton publicRoom = new RadioButton("Công khai"), privateRoom = new RadioButton("Riêng tư");
    private final ComboBox<String> identity = new ComboBox<>();
    private final AuctionSchedulePane schedule;
    private final Label error = label("", "auction-create-error");
    private final BorderPane content = new BorderPane();
    private final VBox editor = new VBox(9);
    private final ScrollPane scroll = new ScrollPane();
    private final ButtonType next = new ButtonType("Tạo phòng", ButtonBar.ButtonData.OK_DONE);
    private final ButtonType cancel = new ButtonType("Quay lại", ButtonBar.ButtonData.CANCEL_CLOSE);
    private final ButtonType confirm = new ButtonType("Xác nhận", ButtonBar.ButtonData.OK_DONE);
    private final ButtonType back = new ButtonType("Quay lại", ButtonBar.ButtonData.CANCEL_CLOSE);
    private Draft draft;
    private boolean reviewing;
    private Node background;
    private Effect previousEffect;
    private GaussianBlur blur;
    private Region shade;

    AuctionCreationDialog(Stage owner, List<ClientProduct> products, Supplier<Instant> now) {
        this.now = now;
        initOwner(owner); initModality(Modality.WINDOW_MODAL); initStyle(StageStyle.TRANSPARENT);
        setTitle("Tạo phòng đấu giá");
        schedule = new AuctionSchedulePane(now.get()); schedule.getStyleClass().add("auction-create-schedule");
        product.setId("auction-create-product"); product.getItems().setAll(products); product.getSelectionModel().selectFirst();
        product.setMaxWidth(Double.MAX_VALUE);
        identity.setId("auction-create-identity"); identity.getItems().setAll("Phòng thường", "Phòng ẩn danh"); identity.getSelectionModel().selectFirst();
        identity.valueProperty().addListener((observable,before,after) -> {
            if (identity.getSelectionModel().getSelectedIndex()!=1) { return; }
            ButtonType yes=new ButtonType("Có",ButtonBar.ButtonData.YES), no=new ButtonType("Không",ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert confirmation=new Alert(Alert.AlertType.CONFIRMATION,
                    "Nếu mở phòng ẩn danh thì mọi thông tin người bán, người mua đều ẩn, giá trị phòng sẽ không được tính vào top doanh thu",yes,no);
            confirmation.setTitle("Xác nhận mở phòng ẩn danh"); confirmation.setHeaderText("Mở phòng ẩn danh?");
            confirmation.getDialogPane().setId("auction-anonymous-confirmation");
            DesktopTheme.dialog(confirmation,getDialogPane().getScene()==null ? owner : getDialogPane().getScene().getWindow());
            ((Button)confirmation.getDialogPane().lookupButton(yes)).setDefaultButton(false);
            ((Button)confirmation.getDialogPane().lookupButton(no)).setDefaultButton(true);
            if (confirmation.showAndWait().orElse(no)!=yes) { identity.getSelectionModel().selectFirst(); }
        });
        ToggleGroup visibility = new ToggleGroup(); publicRoom.setToggleGroup(visibility); privateRoom.setToggleGroup(visibility); publicRoom.setSelected(true);
        privateRoom.setId("auction-create-private"); publicRoom.setId("auction-create-public");
        password.setId("auction-create-password"); repeatedPassword.setId("auction-create-password-confirm");
        password.setPromptText("Chỉ nhập khi chọn phòng riêng tư"); repeatedPassword.setPromptText("Nhập lại mật khẩu");
        password.disableProperty().bind(privateRoom.selectedProperty().not());
        repeatedPassword.disableProperty().bind(privateRoom.selectedProperty().not());
        HBox roomTypes = new HBox(20, publicRoom, privateRoom); roomTypes.setAlignment(Pos.CENTER_LEFT);
        maxParticipants.setPromptText("Bắt buộc: số nguyên từ 1 đến 10000");
        depositAmount.setPromptText("Bắt buộc với phòng hẹn giờ");
        HBox depositRow = field("coins", "Phí đặt cọc", unit(depositAmount, "VND"));
        HBox depositNote = note("Phải đặt cọc đủ trước giờ bắt đầu mới được vào phòng. Cọc không giữ chỗ; được tính vào tiền đấu giá và hoàn khi phiên kết thúc/hủy nếu không thắng.");
        depositRow.visibleProperty().bind(schedule.scheduledProperty()); depositRow.managedProperty().bind(depositRow.visibleProperty());
        depositNote.visibleProperty().bind(schedule.scheduledProperty()); depositNote.managedProperty().bind(depositNote.visibleProperty());
        VBox settings = section("Thiết lập đấu giá", "Cài đặt giá khởi điểm, bước giá và thời gian đấu giá", "gavel",
                field("coins", "Giá khởi điểm", unit(price, "VND")), field("chart", "Bước giá", unit(increment, "VND")),
                field("clock", "Thời lượng (phút)", unit(duration, "phút")), field("calendar", "Thời gian bắt đầu", schedule),
                field("users", "Số lượng tối đa", unit(maxParticipants, "người")),
                label("Tính cả chủ phòng và người xem. Rời phòng sẽ nhường chỗ, kể cả đã đặt giá.", "auction-create-note"),
                depositRow, depositNote);
        VBox privacy = section("Quyền riêng tư phòng", "Chọn chế độ hiển thị và thiết lập mật khẩu cho phòng riêng tư", "shield",
                field("users", "Loại phòng", roomTypes), field("lock", "Mật khẩu", password), field("lock", "Xác nhận", repeatedPassword));
        HBox privacyNote = note("Phòng ẩn danh che danh tính người bán và người mua; không xem hồ sơ và không tính vào Top doanh thu. Vẫn thanh toán và thu phí sàn bình thường. Không thể đổi chế độ sau khi tạo.");
        privacyNote.getStyleClass().add("auction-create-privacy-note");
        editor.getChildren().setAll(section("Thông tin sản phẩm", "Chọn sản phẩm sẽ được đấu giá trong phòng", "box", field("box", "Sản phẩm", product)),
                settings, privacy, section("Cài đặt khác", "Thiết lập danh tính người bán và người mua", "users", field("profile", "Danh tính phòng", identity), privacyNote));
        editor.getStyleClass().add("auction-create-body");
        editor.setMinHeight(Region.USE_PREF_SIZE);
        error.setId("auction-create-error"); error.setVisible(false); error.setManaged(false);
        error.setMinHeight(Region.USE_PREF_SIZE); error.setMaxWidth(Double.MAX_VALUE);
        content.setBottom(error); BorderPane.setMargin(error, new Insets(0, 13, 3, 13));
        scroll.setId("auction-create-scroll"); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.setMinSize(0, 0);
        scroll.getStyleClass().add("auction-create-scroll");
        content.setCenter(scroll); content.setMinHeight(0);
        var pane = getDialogPane(); pane.setId("auction-creation-dialog"); pane.getStyleClass().add("auction-creation-dialog");
        Rectangle outline = new Rectangle(); outline.widthProperty().bind(pane.widthProperty()); outline.heightProperty().bind(pane.heightProperty()); outline.setArcWidth(28); outline.setArcHeight(28); pane.setClip(outline);
        pane.setContent(content); pane.setMinWidth(480); pane.setPrefWidth(580);
        DesktopTheme.dialog(this, owner);
        setResultConverter(button -> button == confirm ? draft : null);
        setOnShown(event -> {
            var window = (Stage) pane.getScene().getWindow(); pane.getScene().setFill(Color.TRANSPARENT);
            dimBackground(); resize(); AccountUi.draggable(content.getTop(), window);
            pane.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, key -> {
                if (key.getCode() == javafx.scene.input.KeyCode.ESCAPE) { close(); key.consume(); }
            });
        });
        setOnHidden(event -> restoreBackground());
        edit();
    }

    private void edit() {
        reviewing = false; setTitle("Tạo phòng đấu giá");
        content.setTop(header(false)); scroll.setContent(editor); scroll.setVvalue(0);
        getDialogPane().getButtonTypes().setAll(next, cancel);
        styleButton(next, "auction-create-next", "plus", true);
        styleButton(cancel, "auction-create-cancel", "left", false);
        getDialogPane().lookupButton(next).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            try { draft = values(); error.setVisible(false); error.setManaged(false); review(); }
            catch (IllegalArgumentException invalid) { showError(invalid.getMessage()); }
        });
        resize();
    }

    private void review() {
        reviewing = true; setTitle("Xác nhận tạo phòng");
        VBox rows = new VBox(); rows.setId("auction-create-review"); rows.getStyleClass().add("auction-review-card");
        rows.getChildren().addAll(
                summary("box", "Sản phẩm", draft.product().getName() + " (#" + draft.product().getProductId() + ")"),
                summary("coins", "Giá khởi điểm", WalletUi.money(draft.startPrice())),
                summary("chart", "Bước giá", WalletUi.money(draft.increment())),
                summary("play", "Bắt đầu", draft.startTime() == null ? "Ngay sau khi tạo" : Times.display(draft.startTime())),
                summary("clock", "Thời lượng", draft.durationMinutes() + " phút"),
                summary("users", "Số lượng tối đa", draft.maxParticipants() + " người (tính cả chủ phòng)"),
                summary("coins", "Phí đặt cọc", draft.startTime() == null ? "Không áp dụng" : WalletUi.money(draft.depositAmount())),
                summary("users", "Loại phòng", draft.visibility().equals("PRIVATE") ? "Riêng tư" : "Công khai"),
                summary("shield", "Danh tính", draft.anonymousGuests() ? "Người bán và người mua ẩn danh" : "Tài khoản thường"));
        VBox body = new VBox(11, rows, note("Không thể đổi sản phẩm, loại phòng và danh tính sau khi tạo."));
        if (draft.anonymousGuests()) { body.getChildren().add(note("Đã xác nhận phòng ẩn danh: che danh tính hai bên và không cộng doanh thu xếp hạng.")); }
        if (draft.startTime() != null) {
            body.getChildren().add(label("Phòng nằm trong mục Sắp diễn ra đến giờ bắt đầu. Dự kiến kết thúc: "
                    + Times.display(draft.startTime().plusSeconds(draft.durationMinutes() * 60L)), "auction-create-note"));
        }
        Label frozen = label("Thông tin và ảnh sản phẩm được giữ nguyên từ phiên đầu tiên, kể cả phiên hủy.", "auction-create-note");
        body.getChildren().add(frozen); body.getStyleClass().add("auction-review-body");
        content.setTop(header(true)); scroll.setContent(body); scroll.setVvalue(0);
        getDialogPane().getButtonTypes().setAll(confirm, back);
        styleButton(confirm, "auction-create-confirm", "check", true);
        styleButton(back, "auction-create-back", "left", false);
        getDialogPane().lookupButton(back).addEventFilter(ActionEvent.ACTION, event -> { event.consume(); edit(); });
        getDialogPane().lookupButton(confirm).addEventFilter(ActionEvent.ACTION, event -> {
            try { draft = values(); }
            catch (IllegalArgumentException invalid) { event.consume(); edit(); showError(invalid.getMessage()); }
        });
        resize();
    }

    private Draft values() {
        if (product.getValue() == null) { throw new IllegalArgumentException("Chọn sản phẩm để tạo phòng."); }
        BigDecimal start = money(price, "Giá khởi điểm"), step = money(increment, "Bước giá");
        int minutes;
        try { minutes = Integer.parseInt(duration.getText().strip()); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Thời lượng phải từ 1 đến 180 phút."); }
        if (minutes < 1 || minutes > 180) { throw new IllegalArgumentException("Thời lượng phải từ 1 đến 180 phút."); }
        if (privateRoom.isSelected()) {
            if (password.getText().length() < 4 || password.getText().length() > 100) { throw new IllegalArgumentException("Mật khẩu phòng riêng phải có 4–100 ký tự."); }
            if (!password.getText().equals(repeatedPassword.getText())) { throw new IllegalArgumentException("Mật khẩu phòng xác nhận không khớp."); }
        }
        int capacity;
        try { capacity = Integer.parseInt(maxParticipants.getText().strip()); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Bắt buộc nhập số lượng tối đa: số nguyên từ 1 đến 10000."); }
        if (capacity < 1 || capacity > 10000) { throw new IllegalArgumentException("Số lượng tối đa phải từ 1 đến 10000 người."); }
        Instant startTime = schedule.value(now.get());
        BigDecimal deposit = startTime == null ? BigDecimal.ZERO : money(depositAmount, "Phí đặt cọc");
        return new Draft(product.getValue(), start, step, minutes, privateRoom.isSelected() ? "PRIVATE" : "PUBLIC",
                privateRoom.isSelected() ? password.getText() : "", identity.getSelectionModel().getSelectedIndex() == 1,
                startTime, capacity, deposit);
    }

    private static BigDecimal money(TextField field, String caption) {
        try { return Money.parseInput(field.getText()); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(caption + " phải là số tiền hợp lệ lớn hơn 0."); }
    }

    private void showError(String message) {
        error.setText(message); error.setVisible(true); error.setManaged(true);
    }

    private StackPane header(boolean review) {
        StackPane header = new StackPane(); header.getStyleClass().add(review ? "auction-review-header" : "auction-create-header");
        Rectangle clip = new Rectangle(); clip.widthProperty().bind(header.widthProperty()); clip.heightProperty().bind(header.heightProperty()); header.setClip(clip);
        Pane art = new Pane(); art.setMouseTransparent(true);
        for (int i = 0; i < 3; i++) {
            Ellipse wave = new Ellipse(230 + i * 25, 70 + i * 13); wave.setFill(Color.TRANSPARENT); wave.setStroke(Color.web("#fffdf6", .8)); wave.setRotate(-17);
            wave.centerXProperty().bind(header.widthProperty().multiply(.7)); wave.setCenterY(75 + i * 12); art.getChildren().add(wave);
        }
        Group hammer = goldGavel(); double scale = review ? 1 : .75;
        hammer.getTransforms().add(new javafx.scene.transform.Scale(scale, scale, 0, 0));
        hammer.layoutXProperty().bind(header.widthProperty().subtract(review ? 158 : 145)); hammer.setLayoutY(review ? 66 : 4);
        art.getChildren().add(hammer);
        StackPane emblem = new StackPane(MarketplaceIcons.icon(review ? "check" : "gavel", review ? 31 : 22, "#fffaf1"));
        emblem.getStyleClass().add(review ? "auction-review-emblem" : "auction-create-emblem");
        Label title = label(review ? "Xác nhận tạo phòng" : "Tạo phòng đấu giá", review ? "auction-review-title" : "auction-create-title");
        Label subtitle = label(review ? "Kiểm tra nhanh thông tin trước khi tạo phòng đấu giá" : "Thiết lập thông tin để tạo phòng đấu giá mới", "auction-create-subtitle");
        Node heading;
        if (review) { VBox centered = new VBox(8, emblem, title, subtitle); centered.setAlignment(Pos.CENTER); heading = centered; }
        else { HBox row = new HBox(13, emblem, new VBox(4, title, subtitle)); row.setAlignment(Pos.CENTER_LEFT); heading = row; }
        Button close = new Button("", MarketplaceIcons.icon("close", 16, "#805525")); close.setId("auction-create-close"); close.setAccessibleText("Đóng tạo phòng đấu giá"); close.getStyleClass().add("auction-create-close"); close.setOnAction(event -> close());
        StackPane.setAlignment(close, Pos.TOP_RIGHT); StackPane.setMargin(close, new Insets(10));
        header.getChildren().setAll(art, heading, close);
        if (getDialogPane().getScene() != null && getDialogPane().getScene().getWindow() instanceof Stage stage) { AccountUi.draggable(heading, stage); }
        return header;
    }

    static Group goldGavel() {
        var metal = new LinearGradient(0, 0, 1, .4, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#91612a")), new Stop(.24, Color.web("#f6dca1")), new Stop(.44, Color.web("#b9853b")),
                new Stop(.68, Color.web("#f8e4b6")), new Stop(1, Color.web("#ab7734")));
        Rectangle base = new Rectangle(14, 86, 84, 10); base.setArcWidth(5); base.setArcHeight(5); base.setFill(metal);
        Ellipse rim = new Ellipse(56, 86, 42, 7); rim.setFill(metal); rim.setStroke(Color.web("#f9e5bd"));
        SVGPath handle = new SVGPath(); handle.setContent("M53 32L113 70Q119 74 113 81Q110 85 105 81L48 40Z"); handle.setFill(metal);
        Rectangle barrel = new Rectangle(21, 10, 49, 28); barrel.setArcWidth(6); barrel.setArcHeight(6); barrel.setFill(metal);
        Ellipse left = new Ellipse(22, 24, 6, 17); left.setFill(metal); left.setStroke(Color.web("#e0b46d"));
        Ellipse right = new Ellipse(70, 24, 6, 17); right.setFill(metal); right.setStroke(Color.web("#fff0cc"));
        Group head = new Group(barrel, left, right);
        for (int x : new int[]{29, 33, 59, 63}) { Rectangle band = new Rectangle(x, 9, 2, 30); band.setFill(Color.web("#fff0cc", .65)); head.getChildren().add(band); }
        head.getTransforms().add(new javafx.scene.transform.Rotate(-32, 46, 24));
        Group art = new Group(base, rim, handle, head); art.setOpacity(.8); return art;
    }

    private void styleButton(ButtonType type, String id, String icon, boolean gold) {
        Button button = (Button) getDialogPane().lookupButton(type); button.setId(id);
        button.getStyleClass().add(gold ? "auction-create-gold" : "auction-create-secondary");
        if (icon != null) { button.setGraphic(MarketplaceIcons.icon(icon, 16, gold ? "#fffaf1" : "#805525")); }
    }

    private void resize() {
        content.setPrefHeight(reviewing ? 635 : 790);
        if (isShowing()) { DesktopTheme.fitOnScreen((Stage) getDialogPane().getScene().getWindow(), reviewing ? 640 : 580, reviewing ? 705 : 870); }
    }

    private void dimBackground() {
        if (getOwner() == null || getOwner().getScene() == null) { return; }
        var root = getOwner().getScene().getRoot();
        background = root instanceof StackPane shell && !shell.getChildren().isEmpty() ? shell.getChildren().getFirst() : root;
        previousEffect = background.getEffect(); blur = new GaussianBlur(5); blur.setInput(previousEffect); background.setEffect(blur);
        if (root instanceof StackPane shell) {
            shade = new Region(); shade.setId("auction-create-backdrop"); shade.setStyle("-fx-background-color: rgba(30, 23, 15, .34);"); shade.setMouseTransparent(true); shell.getChildren().add(shade);
        }
    }

    private void restoreBackground() {
        if (background != null && background.getEffect() == blur) { background.setEffect(previousEffect); }
        if (shade != null && shade.getParent() instanceof Pane parent) { parent.getChildren().remove(shade); }
        background = null; previousEffect = null; blur = null; shade = null;
    }

    private static VBox section(String title, String description, String icon, Node... fields) {
        HBox heading = new HBox(10, AccountUi.emblem(icon, 35), new VBox(3, label(title, "auction-create-section-title"), label(description, "auction-create-note")));
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox section = new VBox(9, heading); section.getChildren().addAll(fields); section.getStyleClass().add("auction-create-section"); return section;
    }

    private static HBox field(String icon, String caption, Node control) {
        HBox name = new HBox(9, MarketplaceIcons.icon(icon, 15, "#b98237"), label(caption, "auction-create-caption")); name.setAlignment(Pos.CENTER_LEFT); name.setMinWidth(155); name.setPrefWidth(155);
        if (control instanceof Region region) { region.setMinWidth(0); region.setMaxWidth(Double.MAX_VALUE); }
        HBox.setHgrow(control, Priority.ALWAYS); HBox row = new HBox(9, name, control); row.setAlignment(Pos.CENTER_LEFT); return row;
    }

    private static HBox unit(TextField field, String unit) {
        HBox box = new HBox(field, label(unit, "auction-create-unit")); box.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(field, Priority.ALWAYS); field.setMinWidth(0); box.getStyleClass().add("auction-create-input-unit"); return box;
    }

    private static HBox summary(String icon, String caption, String value) {
        Label name = label(caption, "auction-review-caption"); name.setMinWidth(163); name.setPrefWidth(163);
        Label text = label(value, "auction-review-value"); text.setMinWidth(0); text.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(15, AccountUi.emblem(icon, 33), name, text); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("auction-review-row"); return row;
    }

    private static HBox note(String text) {
        Label message = label("Lưu ý: " + text, "auction-create-note");
        message.setWrapText(true); message.setMinWidth(0); HBox.setHgrow(message, Priority.ALWAYS);
        HBox note = new HBox(9, MarketplaceIcons.icon("info", 17, "#c58b2f"), message); note.setAlignment(Pos.CENTER_LEFT); note.getStyleClass().add("auction-create-hint"); return note;
    }

    private static TextField input(String id, String text) { TextField field = new TextField(text); field.setId(id); return field; }
    private static Label label(String text, String style) { return DesktopTheme.label(text, style); }
}
