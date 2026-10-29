package vn.ptit.btl16.client.fx;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.common.fx.DesktopTheme;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

final class AccountProfileForm extends VBox {
    private final TextField displayName, email, phone;
    private final Button avatar = new Button();

    AccountProfileForm(ClientAppModel model, Runnable publicProfile) {
        getStyleClass().addAll("account-page", "profile-form"); setId("account-profile-form"); setSpacing(10);
        TextField username = new TextField(model.getUsername()); username.setEditable(false); username.getStyleClass().add("profile-readonly"); username.setId("profile-username");
        displayName = new TextField(model.getDisplayName()); email = new TextField(model.getEmail()); phone = new TextField(model.getPhone());
        displayName.setId("profile-display-name"); email.setId("profile-email"); phone.setId("profile-phone");
        TextField friendCode = new TextField(model.getFriendCode().isBlank() ? "—" : "#" + model.getFriendCode());
        friendCode.setId("profile-friend-code"); friendCode.setEditable(false); friendCode.getStyleClass().add("profile-readonly");
        friendCode.setTooltip(new Tooltip("Dùng tên hiển thị và mã này để người khác tìm và gửi lời mời kết bạn."));
        TextField created = new TextField(date(model.getCreatedAt())); created.setEditable(false); created.getStyleClass().add("profile-readonly"); created.setId("profile-created-at");
        VBox basic = AccountUi.card(AccountUi.sectionHeading("Thông tin cơ bản", "Thông tin định danh và liên hệ của bạn trong hệ thống", "profile"),
                field("profile", "Tên đăng nhập", username), field("edit", "Tên hiển thị", displayName), field("users", "Mã kết bạn", friendCode), field("mail", "Email", email), field("phone", "Điện thoại", phone), field("calendar", "Ngày tạo", created));
        String name = model.getDisplayName().isBlank() ? model.getUsername() : model.getDisplayName();
        avatar.setText(name.isBlank() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT)); avatar.getStyleClass().add("profile-avatar"); avatar.setId("profile-avatar"); avatar.setAccessibleText("Đổi ảnh đại diện"); avatar.setOnAction(event -> publicProfile.run());
        Button editPublic = AccountUi.button("Ảnh đại diện và giới thiệu công khai", "image"); editPublic.setId("profile-edit-public"); editPublic.setWrapText(true); editPublic.setMaxWidth(Double.MAX_VALUE); editPublic.getStyleClass().add("account-gold-soft"); editPublic.setOnAction(event -> publicProfile.run());
        VBox publicText = new VBox(7, editPublic, DesktopTheme.label("Tải ảnh đại diện và viết giới thiệu ngắn về bản thân với cộng đồng.", "account-note")); publicText.setMinWidth(0); HBox.setHgrow(publicText, Priority.ALWAYS);
        HBox publicRow = new HBox(15, avatar, publicText); publicRow.setAlignment(Pos.CENTER_LEFT);
        VBox publicCard = AccountUi.card(AccountUi.sectionHeading("Hồ sơ công khai", "Ảnh đại diện và giới thiệu hiển thị với người dùng khác", "users"), publicRow);
        VBox privacy = AccountUi.card(AccountUi.sectionHeading("Quyền riêng tư", "Thông tin hiển thị và bảo mật tài khoản", "lock"), AccountUi.hint("Email, điện thoại, ví và phiên đăng nhập không xuất hiện trong hồ sơ công khai. Chỉ bạn và người vận hành có thể xem thông tin liên hệ riêng tư. Hồ sơ công khai chỉ hiện ảnh đại diện và phần giới thiệu của bạn."));
        getChildren().setAll(basic, publicCard, privacy);
    }
    Map<String, String> values() { return Map.of("displayName", displayName.getText(), "email", email.getText(), "phone", phone.getText()); }
    void setAvatar(String encoded) {
        if (encoded == null || encoded.isBlank()) { return; }
        try {
            Image image = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
            if (!image.isError()) { ImageView view = new ImageView(image); view.setFitWidth(60); view.setFitHeight(60); view.setPreserveRatio(false); view.setClip(new Circle(30, 30, 30)); avatar.setText(""); avatar.setGraphic(view); }
        } catch (IllegalArgumentException ignored) { }
    }
    private static HBox field(String icon, String caption, TextField value) {
        HBox label = new HBox(9, AccountUi.emblem(icon, 28), DesktopTheme.label(caption, "profile-field-label")); label.setAlignment(Pos.CENTER_LEFT); label.setMinWidth(143); label.setPrefWidth(143);
        value.setMinWidth(0); value.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(value, Priority.ALWAYS);
        HBox row = new HBox(10, label, value); row.setAlignment(Pos.CENTER_LEFT); return row;
    }
    private static String date(String raw) { try { return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.parse(raw)); } catch (RuntimeException error) { return raw == null || raw.isBlank() ? "—" : raw; } }
}
