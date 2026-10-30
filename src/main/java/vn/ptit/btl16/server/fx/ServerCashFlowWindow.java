package vn.ptit.btl16.server.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.common.util.RevenuePeriod;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.wallet.PlatformCashFlowRepository;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class ServerCashFlowWindow extends ServerAdminPage {
    private Stage stage;
    private final Supplier<ServerApplication> server;
    private final Executor worker;
    private final ComboBox<RevenuePeriod> period = new ComboBox<>();
    private final TableView<PlatformCashFlowRepository.Entry> table = new TableView<>();
    private final Label balance = value("cash-flow-balance"), fees = value("cash-flow-fees"),
            gross = value("cash-flow-gross"), net = value("cash-flow-net");
    private final Label status = new Label(), range = new Label(), review = new Label();
    private final Button next = new Button("Xem thêm"), refresh = new Button("Làm mới");
    private boolean loading, paged;
    private final Timeline poll = new Timeline(new KeyFrame(Duration.seconds(5), event -> { if (!paged && !loading) { load(false); } }));
    private final DateTimeFormatter time = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(RevenuePeriod.ZONE);
    private long generation, cursor;
    private ServerApplication loadedServer;

    public ServerCashFlowWindow(Window owner, Supplier<ServerApplication> server, Executor worker) {
        super(owner);
        this.server=server; this.worker=worker;
        setId("server-cash-flow"); setPadding(new Insets(20,28,20,28));
        period.setId("cash-flow-period"); period.getItems().setAll(RevenuePeriod.values()); period.setValue(RevenuePeriod.DEFAULT);
        refresh.setId("cash-flow-refresh"); next.setId("cash-flow-next"); next.setDisable(true);
        refresh.setOnAction(event -> load(false)); next.setOnAction(event -> load(true));
        period.setOnAction(event -> load(false));
        Label explanation = DesktopTheme.label("Phí sàn tự động được thu khi phiên thanh toán thành công. Người mua trả đúng giá chốt; người bán nhận giá chốt trừ phí sàn. Hệ thống không xử lý thuế nhà nước.", "muted");
        explanation.setWrapText(true);
        TilePane cards = new TilePane(12,12,card("PHÍ SÀN ĐÃ THU / TOÀN BỘ",balance),card("PHÍ THU TRONG KỲ",fees),
                card("TỔNG GIÁ CHỐT TRONG KỲ",gross),card("NGƯỜI BÁN NHẬN TRONG KỲ",net));
        cards.setPrefColumns(4); cards.prefTileWidthProperty().bind(widthProperty().subtract(92).divide(4));
        range.setId("cash-flow-range"); review.setId("cash-flow-review"); status.setId("cash-flow-status");
        range.setWrapText(true); review.setWrapText(true);
        status.setWrapText(true); status.setMinWidth(0); HBox.setHgrow(status,Priority.ALWAYS);
        VBox heading = new VBox(10,DesktopTheme.label("Thu phí & đối soát", "section-title"),explanation,cards,
                new FlowPane(10,8,new Label("Kỳ thống kê"),period,refresh),range,review);
        heading.setPadding(new Insets(0,0,15,0)); setTop(heading);
        table.setId("cash-flow-table"); table.setPlaceholder(new Label("Chưa có khoản phí sàn đã thu trong kỳ này"));
        WalletUi.column(table,"PHIÊN",entry -> "#"+entry.auctionId(),75);
        WalletUi.column(table,"THỜI ĐIỂM THU",entry -> time.format(entry.collectedAt()),170);
        WalletUi.column(table,"NGƯỜI BÁN",PlatformCashFlowRepository.Entry::seller,120);
        WalletUi.column(table,"NGƯỜI MUA",PlatformCashFlowRepository.Entry::buyer,120);
        WalletUi.column(table,"GIÁ CHỐT",entry -> WalletUi.money(entry.saleAmount()),160);
        WalletUi.column(table,"TỶ LỆ PHÍ",entry -> entry.feeRate().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()+"%",85);
        WalletUi.column(table,"SÀN ĐÃ THU",entry -> WalletUi.money(entry.feeAmount()),150);
        WalletUi.column(table,"NGƯỜI BÁN NHẬN",entry -> WalletUi.money(entry.sellerNet()),160);
        setCenter(table);
        HBox footer = new HBox(12,status,DesktopTheme.spacer(),next); footer.setPadding(new Insets(12,0,0,0)); setBottom(footer);
        table.setMinHeight(100); poll.setCycleCount(Timeline.INDEFINITE);
    }

    public void show() {
        if (stage==null) { stage=createWindow("Quản lý dòng tiền sàn",1200,810); }
        stage.show(); activate();
    }

    @Override protected void onActivate() {
        if (!paged || loadedServer!=server.get()) { load(false); }
        poll.play();
    }

    @Override protected void onDeactivate() {
        poll.stop(); generation++;
        if (loading) { loadedServer=null; }
        loading=false;
    }

    private void load(boolean following) {
        if (!isActive()) { return; }
        var running=server.get();
        if (running==null) { status.setText("Server đang dừng; chưa thể tải dòng tiền."); return; }
        if (following && loading) { return; }
        long before=following ? cursor : 0, token=++generation;
        var selected=period.getValue(); loading=true; next.setDisable(true); status.setText("Đang tải...");
        CompletableFuture.supplyAsync(() -> running.wallets().cashFlow(selected,before),worker)
                .whenComplete((snapshot,error) -> Platform.runLater(() -> {
                    if (!isActive() || token!=generation || running!=server.get()) { return; }
                    loading=false;
                    if (error!=null) { status.setText("Chưa tải được dữ liệu. Nhấn Làm mới để thử lại."); return; }
                    loadedServer=running;
                    balance.setText(WalletUi.money(snapshot.balance())); fees.setText(WalletUi.money(snapshot.fees()));
                    gross.setText(WalletUi.money(snapshot.gross())); net.setText(WalletUi.money(snapshot.sellerNet()));
                    range.setText("Khoảng thống kê: "+time.format(snapshot.from())+" → "+time.format(snapshot.to())+" (GMT+7) · "+snapshot.paidCount()+" phiên đã thanh toán");
                    review.setText("Cần đối soát: "+snapshot.reviewCount()+" phiên. Phiên chưa thanh toán không ghi nhận phí sàn đã thu.");
                    if (following) { table.getItems().addAll(snapshot.entries()); } else { table.getItems().setAll(snapshot.entries()); }
                    paged=following; cursor=snapshot.entries().isEmpty() ? 0 : snapshot.entries().getLast().auctionId();
                    next.setDisable(!snapshot.hasMore());
                    status.setText("Đã ghi nhận "+table.getItems().size()+" giao dịch · Mỗi phiên thu đúng một lần · Danh tính phòng ẩn danh được che");
                }));
    }

    private static Label value(String id) { Label label=DesktopTheme.label("—","section-title"); label.setId(id); return label; }
    private static VBox card(String caption, Label amount) {
        Label title=DesktopTheme.label(caption,"eyebrow"); title.setWrapText(true); title.setMinHeight(28);
        VBox box=new VBox(8,title,amount); box.getStyleClass().add("card");
        box.setMinWidth(0); box.setPadding(new Insets(12)); return box;
    }
}
