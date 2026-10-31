package vn.ptit.btl16.client.fx;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.util.Money;
import vn.ptit.btl16.common.util.Times;

final class RoomDepositDialog extends Dialog<ButtonType> {
    RoomDepositDialog(Window owner, ClientAuction room) {
        setTitle("Xác nhận đặt cọc");
        var pane = getDialogPane();
        pane.setId("room-deposit-dialog");
        pane.getStyleClass().addAll("standard-dialog", "room-deposit-dialog");
        pane.setPrefWidth(560);
        Label product = text(room.getProductName(), "deposit-product");
        Label amount = text(Money.input(room.getDepositAmount()) + " đ", "deposit-amount"); amount.setId("deposit-amount");
        VBox summary = new VBox(7, text("Số tiền đặt cọc", "deposit-caption"), amount, product,
                text("Phòng #" + room.getAuctionId(), "muted")); summary.getStyleClass().add("deposit-summary");
        Label deadline = text(Times.display(room.getStartTime()), "deposit-deadline"); deadline.setId("deposit-deadline");
        VBox timing = new VBox(5, text("Hoàn tất trước giờ bắt đầu", "deposit-caption"), deadline);
        timing.getStyleClass().add("deposit-deadline-card");
        VBox policy = new VBox(13,
                policy("01", "Giữ trong ví", "Tiền cọc được giữ trong ví và tính vào tiền thanh toán nếu bạn thắng đấu giá."),
                policy("02", "Hoàn lại khi không thắng", "Tiền cọc được hoàn khi phiên đấu giá kết thúc hoặc bị hủy."));
        policy.setId("deposit-policy"); policy.getStyleClass().add("deposit-policy");
        Label note = text("Lưu ý: đặt cọc không giữ chỗ. Bạn cần vào phòng khi phiên bắt đầu và vẫn còn chỗ trống.", "deposit-notice");
        VBox body = new VBox(16, summary, timing, policy, note); body.getStyleClass().add("deposit-body");
        pane.setContent(body); pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        setResultConverter(button -> button); DesktopTheme.dialog(this, owner);
        Button confirm = (Button) pane.lookupButton(ButtonType.OK); confirm.setText("Đặt cọc ngay"); confirm.setId("deposit-confirm");
        Button cancel = (Button) pane.lookupButton(ButtonType.CANCEL); cancel.setText("Để sau"); cancel.setId("deposit-cancel");
    }

    private static HBox policy(String number, String title, String explanation) {
        Label marker = text(number, "deposit-step");
        marker.setMinWidth(Region.USE_PREF_SIZE); marker.setMaxWidth(Region.USE_PREF_SIZE);
        VBox description = new VBox(4, text(title, "deposit-policy-title"), text(explanation, "deposit-policy-text"));
        description.setMinWidth(0); HBox.setHgrow(description, Priority.ALWAYS);
        HBox row = new HBox(12, marker, description); row.setAlignment(Pos.TOP_LEFT); return row;
    }

    private static Label text(String value, String style) {
        Label label = DesktopTheme.label(value, style); label.setMinWidth(0); label.setMinHeight(Region.USE_PREF_SIZE); return label;
    }
}
