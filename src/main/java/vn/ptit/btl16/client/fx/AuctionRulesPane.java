package vn.ptit.btl16.client.fx;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import vn.ptit.btl16.common.fx.DesktopTheme;

/** Rules document and safety checklists embedded in the account shell. */
final class AuctionRulesPane extends BorderPane implements AutoCloseable {
    AuctionRulesPane() {
        setId("auction-rules-page"); getStyleClass().addAll("account-page", "rules-page"); setMinSize(0, 0); setTop(banner());
        VBox steps = new VBox(8,
                section(1, "Tham gia phiên", "users", "Đăng nhập và vào đúng phòng trước khi trả giá. Phòng riêng yêu cầu mật khẩu hoặc quyền tham gia còn hiệu lực. Chủ phòng, người đang dẫn đầu và người bị cấm tham gia không được đặt giá.", null),
                section(2, "Giá khởi điểm và bước giá", "coins", "Giá đầu tiên phải ≥ giá khởi điểm + bước giá. Các lượt sau phải ≥ giá hiện tại + bước giá. Bạn có thể trả cao hơn mức tối thiểu; không bắt buộc là bội số của bước giá.",
                        "Giá khởi điểm 100.000 đồng, bước 10.000 đồng → giá đầu từ 110.000 đồng.\nSau mức 115.000 đồng, lượt tiếp theo từ 125.000 đồng."),
                section(3, "Cách nhập và hiệu lực giá", "gavel", "Tiền có tối đa 2 chữ số thập phân. Dùng dấu phẩy cho phần lẻ, ví dụ 1.234,56. Lượt chỉ có hiệu lực khi được Server chấp nhận; lượt hợp lệ không thể rút, sửa hoặc hủy. Rời phòng hay mất kết nối không làm mất hiệu lực lượt đã được chấp nhận.", null),
                section(4, "Ví, thanh toán và thuế người bán", "wallet", "Số dư khả dụng phải đủ toàn bộ mức ra giá. Tiền của người dẫn đầu được giữ chỗ; khi bị vượt giá hợp lệ, khoản giữ chỗ được giải phóng. Khi kết thúc, người mua trả đúng giá chốt.\n\nThuế người bán tính trên toàn bộ giá chốt: đến đúng 1.000.000.000 đồng là 10%, trên 1.000.000.000 đồng là 15%. Top doanh thu được giảm trực tiếp vào thuế suất: Top 1 giảm 5 điểm %, Top 2 giảm 4, Top 3 giảm 3, Top 4–5 giảm 2, Top 6–10 giảm 1.",
                        "Giá chốt 100.000.000 đồng, người bán Top 1 → thuế 10% − 5 điểm % = 5%, thực nhận 95.000.000 đồng.\nGiá chốt đúng 1 tỷ đồng, chưa thuộc Top 10 → thuế 10%, thực nhận 900.000.000 đồng."),
                section(5, "Thời gian và gia hạn", "clock", "Server quyết định thời điểm nhận giá, giá hiện tại và thời hạn của phiên. Lượt hợp lệ sát giờ có thể được gia hạn tự động theo cấu hình. Đồng hồ về 0 chưa phải kết quả cuối cùng: chờ thông báo kết thúc từ Server. Khi giá đã thay đổi, hãy kiểm tra lại mức tối thiểu trước khi gửi.", null),
                section(6, "Quyền của chủ phòng", "gavel", "Chỉ được gia hạn thủ công, kết thúc sớm hoặc hủy khi chưa có lượt trả giá hợp lệ. Chỉ được mời người chưa từng trả giá trong phiên ra khỏi phòng. Chủ phòng không được tự đặt giá cho sản phẩm của mình.", null),
                section(7, "Kết quả và lịch sử", "receipt", "Người dẫn đầu hợp lệ cuối cùng là người thắng và thanh toán đúng giá thắng. Không có lượt hợp lệ thì không có người thắng. Xem kết quả ở mục “Kết quả phiên của tôi”; rời phòng không xóa lịch sử hoặc thay đổi kết quả.", null));
        VBox fourth = (VBox) ((HBox) steps.getChildren().get(3)).getChildren().get(1);
        TitledPane taxDetails = new TitledPane("Chi tiết doanh thu, xếp hạng và thanh toán", copy("Doanh thu là tổng giá chốt của mọi phiên bán thành công, trước thuế; thứ hạng lấy trước khi chốt phiên hiện tại. Bằng doanh thu thì phiên bán thành công đầu tiên sớm hơn xếp trước, sau đó theo mã tài khoản. Thuế làm tròn đến 2 chữ số thập phân và trừ vào tiền người bán. Không có người thắng thì không thu thuế. Biên nhận lưu mức thuế, mức giảm, thứ hạng và tiền thực nhận tại lúc chốt; không tính lại phiên cũ. Ví, nạp tiền và thanh toán hiện là mô phỏng học tập."));
        taxDetails.setExpanded(false); taxDetails.setId("auction-rules-tax-details"); taxDetails.getStyleClass().add("rules-tax-details"); fourth.getChildren().add(taxDetails);
        steps.getStyleClass().add("rules-timeline");
        Line connector = new Line(11, 16, 11, 16); connector.endYProperty().bind(steps.heightProperty().subtract(24)); connector.setStroke(Color.web("#e7d2af")); connector.setStrokeWidth(1.5); connector.setMouseTransparent(true);
        Pane lineLayer = new Pane(connector); lineLayer.setManaged(false); lineLayer.setMouseTransparent(true); StackPane timeline = new StackPane(lineLayer, steps);
        VBox workflowCopy = new VBox(4, DesktopTheme.label("Quy trình tham gia đấu giá", "rules-workflow-title"), copy("Vui lòng đọc kỹ các bước dưới đây trước khi tham gia đấu giá.")); workflowCopy.setMinWidth(0); HBox.setHgrow(workflowCopy, Priority.ALWAYS);
        HBox workflowHeading = new HBox(11, AccountUi.emblem("rules", 36), workflowCopy); workflowHeading.setAlignment(Pos.CENTER_LEFT);
        VBox workflow = AccountUi.card(workflowHeading, timeline); workflow.setId("auction-rules-workflow"); workflow.getStyleClass().add("rules-workflow");
        VBox side = new VBox(12,
                checklist("Nguyên tắc chung", "target", false,
                        "Đấu giá công khai, minh bạch, công bằng.", "Mỗi lượt giá đều được ghi nhận và xác nhận bởi Server.", "Không sử dụng phần mềm gian lận hoặc hành vi gây rối.", "Tuân thủ quy định của chủ phòng và quản trị viên."),
                checklist("Các hành vi bị cấm", "warning", true,
                        "Sử dụng nhiều tài khoản để thao túng giá.", "Đặt giá ảo và cố tình không thanh toán.", "Phá hoại, gây rối hoặc làm gián đoạn phiên đấu giá.", "Sử dụng công cụ, phần mềm can thiệp trái phép."),
                checklist("Lưu ý quan trọng", "info", false,
                        "Hãy kiểm tra kỹ thông tin sản phẩm và điều kiện tham gia.", "Đảm bảo số dư ví đủ trước khi đặt giá.", "Mọi mốc thời gian và kết quả cuối cùng do Server xác nhận.", "Trong trường hợp có tranh chấp, quyết định của quản trị viên là quyết định cuối cùng."));
        side.setId("auction-rules-checklists"); side.setMinWidth(Region.USE_PREF_SIZE); side.setMaxWidth(Region.USE_PREF_SIZE);
        HBox columns = new HBox(13, workflow, side); columns.setAlignment(Pos.TOP_LEFT); HBox.setHgrow(workflow, Priority.ALWAYS); columns.getStyleClass().add("rules-columns"); columns.setId("auction-rules-content");
        side.prefWidthProperty().bind(columns.widthProperty().multiply(.36));
        ScrollPane scroll = new ScrollPane(columns); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.setMinHeight(0); scroll.setId("auction-rules-scroll"); setCenter(scroll);
    }
    private static StackPane banner() {
        StackPane banner = new StackPane(); banner.getStyleClass().add("rules-hero"); banner.setMinHeight(88); banner.setMaxHeight(88);
        Pane artwork = new Pane(); artwork.setMouseTransparent(true);
        Ellipse wave = new Ellipse(300, 110); wave.setRotate(-23); wave.setFill(Color.web("#fffaf0", .4)); wave.centerXProperty().bind(banner.widthProperty().subtract(140)); wave.setCenterY(-25);
        Node gavel = MarketplaceIcons.icon("gavel", 95, "#bd934f"); gavel.setOpacity(.35); gavel.setRotate(-16); gavel.layoutXProperty().bind(banner.widthProperty().multiply(.62)); gavel.setLayoutY(18); artwork.getChildren().addAll(wave, gavel);
        VBox titles = new VBox(5, DesktopTheme.label("Luật đấu giá", "rules-hero-title"), DesktopTheme.label("HƯỚNG DẪN THAM GIA ĐỂ CÓ TRẢI NGHIỆM TỐT NHẤT", "rules-hero-subtitle")); titles.setMinWidth(0); HBox.setHgrow(titles, Priority.ALWAYS);
        Label slogan = DesktopTheme.label("Đấu giá minh bạch\nCông bằng - An toàn", "rules-hero-slogan"); slogan.setMinWidth(185);
        HBox content = new HBox(16, AccountUi.emblem("rules", 58), titles, slogan); content.setAlignment(Pos.CENTER_LEFT); content.getStyleClass().add("rules-hero-content"); banner.getChildren().addAll(artwork, content);
        Rectangle clip = new Rectangle(); clip.widthProperty().bind(banner.widthProperty()); clip.heightProperty().bind(banner.heightProperty()); clip.setArcWidth(18); clip.setArcHeight(18); banner.setClip(clip);
        banner.widthProperty().addListener((o, old, width) -> { boolean show = width.doubleValue() >= 850; slogan.setVisible(show); slogan.setManaged(show); }); return banner;
    }
    private static HBox section(int number, String title, String icon, String text, String example) {
        Label ordinal = DesktopTheme.label(Integer.toString(number), "rules-number");
        VBox copy = new VBox(6, DesktopTheme.label(title, "rules-section-title"), copy(text)); copy.setMinWidth(0); HBox.setHgrow(copy, Priority.ALWAYS);
        HBox identity = new HBox(13, AccountUi.emblem(icon, 46), copy); identity.setAlignment(Pos.TOP_LEFT);
        VBox card = new VBox(9, identity); card.setMinWidth(0); HBox.setHgrow(card, Priority.ALWAYS); card.getStyleClass().add("rules-step-card");
        if (example != null) {
            VBox exampleCopy = new VBox(4, DesktopTheme.label("Ví dụ minh họa", "account-small-title"), DesktopTheme.label(example, "rules-example-text")); exampleCopy.setMinWidth(0); HBox.setHgrow(exampleCopy, Priority.ALWAYS);
            HBox exampleRow = new HBox(10, MarketplaceIcons.icon("info", 17, "#ba8a3c"), exampleCopy); exampleRow.getStyleClass().add("account-hint"); exampleRow.setAlignment(Pos.TOP_LEFT); VBox.setMargin(exampleRow, new javafx.geometry.Insets(0, 0, 0, 59)); card.getChildren().add(exampleRow);
        }
        HBox row = new HBox(10, ordinal, card); row.getStyleClass().add("rules-section"); row.setAlignment(Pos.TOP_LEFT); return row;
    }
    private static VBox checklist(String title, String icon, boolean prohibited, String... items) {
        HBox heading = new HBox(11, AccountUi.emblem(icon, 35), DesktopTheme.label(title, "rules-section-title")); heading.setAlignment(Pos.CENTER_LEFT);
        VBox card = AccountUi.card(heading); card.getStyleClass().add("rules-checklist"); if (prohibited) { card.getStyleClass().add("rules-prohibited"); }
        for (String item : items) {
            StackPane symbol = new StackPane(MarketplaceIcons.icon(prohibited ? "close" : "check", 9, "#fffdf5")); symbol.setMinSize(14, 14); symbol.setMaxSize(14, 14); symbol.getStyleClass().add("rules-bullet");
            Label text = copy(item); text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS); HBox row = new HBox(9, symbol, text); row.setAlignment(Pos.TOP_LEFT); card.getChildren().add(row);
        } return card;
    }
    private static Label copy(String text) { return DesktopTheme.label(text, "rules-copy"); }
    @Override public void close() { }
}
