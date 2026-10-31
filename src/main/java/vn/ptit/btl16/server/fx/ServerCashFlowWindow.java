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
    private final Timeline poll = new Timeline(new KeyFrame(Duration.seconds(5), event -> { if (!paged && !loading) { load(false, true); } }));
    private final DateTimeFormatter time = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm").withZone(RevenuePeriod.ZONE);
    private final DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(RevenuePeriod.ZONE);
    private long generation, cursor;
    private ServerApplication loadedServer;

    public ServerCashFlowWindow(Window owner, Supplier<ServerApplication> server, Executor worker) {
        super(owner);
        this.server=server; this.worker=worker;
        setId("server-cash-flow"); setPadding(new Insets(6,24,20,24));
        getStyleClass().add("admin-workspace");
        period.setId("cash-flow-period"); period.getItems().setAll(RevenuePeriod.values()); period.setValue(RevenuePeriod.DEFAULT);
        refresh.setId("cash-flow-refresh"); next.setId("cash-flow-next"); next.setDisable(true);
        refresh.setOnAction(event -> load(false)); next.setOnAction(event -> load(true));
        period.setOnAction(event -> load(false));
        refresh.setGraphic(ServerIcons.icon("refresh"));
        refresh.getStyleClass().add("admin-secondary-button"); next.getStyleClass().add("admin-secondary-button");
        TilePane cards = new TilePane(12,12,card("Phí sàn tích lũy",balance),card("Phí thu trong kỳ",fees),
                card("Tổng giá chốt",gross),card("Người bán nhận",net));
        cards.setId("cash-flow-cards");
        cards.prefColumnsProperty().bind(javafx.beans.binding.Bindings.when(widthProperty().lessThan(1000)).then(2).otherwise(4));
        cards.prefTileWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(() ->
                Math.floor((getWidth()-49-(cards.getPrefColumns()-1)*12)/cards.getPrefColumns()), widthProperty(), cards.prefColumnsProperty()));
        cards.setMinHeight(Region.USE_PREF_SIZE);
        range.setId("cash-flow-range"); review.setId("cash-flow-review"); status.setId("cash-flow-status");
        range.setWrapText(true); review.setWrapText(true);
        range.getStyleClass().add("admin-range"); review.getStyleClass().add("admin-review-notice");
        review.managedProperty().bind(review.visibleProperty()); review.setVisible(false);
        status.getStyleClass().add("muted");
        status.setWrapText(true); status.setMinWidth(0); HBox.setHgrow(status,Priority.ALWAYS);
        period.setPrefWidth(150); range.setMinWidth(0); HBox.setHgrow(range,Priority.ALWAYS);
        HBox toolbar = AdminUi.toolbar(ServerIcons.icon("clock"),period,range,DesktopTheme.spacer(),refresh);
        VBox heading = new VBox(16,cards,toolbar,review);
        heading.setPadding(new Insets(0,0,15,0)); setTop(heading);
        table.setId("cash-flow-table"); table.setPlaceholder(new Label("Chưa có giao dịch trong kỳ"));
        WalletUi.column(table,"Phiên",entry -> "#"+entry.auctionId(),60);
        WalletUi.column(table,"Thanh toán",entry -> time.format(entry.collectedAt()),145);
        WalletUi.column(table,"Người bán",PlatformCashFlowRepository.Entry::seller,115);
        WalletUi.column(table,"Người mua",PlatformCashFlowRepository.Entry::buyer,115);
        WalletUi.column(table,"Giá chốt",entry -> WalletUi.money(entry.saleAmount()),145);
        WalletUi.column(table,"Phí (%)",entry -> entry.feeRate().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()+"%",70);
        WalletUi.column(table,"Phí sàn",entry -> WalletUi.money(entry.feeAmount()),120);
        WalletUi.column(table,"Thực nhận",entry -> WalletUi.money(entry.sellerNet()),145);
        table.columnResizePolicyProperty().bind(javafx.beans.binding.Bindings.when(widthProperty().lessThan(950))
                .then(TableView.UNCONSTRAINED_RESIZE_POLICY).otherwise(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN));
        AdminUi.table(table,4,5,6,7);
        for (int index=0; index<table.getColumns().size(); index++) {
            table.getColumns().get(index).setMinWidth(index==0 ? 60 : index==5 ? 75 : 110);
        }
        HBox footer = new HBox(12,status,DesktopTheme.spacer(),next); footer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox sectionTitle = SessionUi.heading("Giao dịch trong kỳ","cash");
        sectionTitle.visibleProperty().bind(widthProperty().greaterThanOrEqualTo(1000));
        sectionTitle.managedProperty().bind(sectionTitle.visibleProperty());
        VBox transactions = SessionUi.card(sectionTitle,AdminUi.tableFrame(table),footer);
        transactions.getStyleClass().add("admin-transactions-card"); transactions.setMinHeight(0);
        setCenter(transactions);
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
        refresh.setDisable(false);
    }

    private void load(boolean following) {
        load(following, false);
    }

    private void load(boolean following, boolean automatic) {
        if (!isActive()) { return; }
        var running=server.get();
        if (running==null) { status.setText("Server đang dừng; chưa thể tải dòng tiền."); return; }
        if (following && loading) { return; }
        long before=following ? cursor : 0, token=++generation;
        boolean nextWasDisabled=next.isDisabled();
        var selected=period.getValue(); loading=true; next.setDisable(true); refresh.setDisable(true);
        if (!automatic) { status.setText("Đang tải..."); }
        CompletableFuture.supplyAsync(() -> running.wallets().cashFlow(selected,before),worker)
                .whenComplete((snapshot,error) -> Platform.runLater(() -> {
                    if (!isActive() || token!=generation || running!=server.get()) { return; }
                    loading=false; refresh.setDisable(false);
                    if (error!=null) { next.setDisable(nextWasDisabled); status.setText("Chưa tải được dữ liệu. Thử làm mới."); return; }
                    loadedServer=running;
                    balance.setText(WalletUi.money(snapshot.balance())); fees.setText(WalletUi.money(snapshot.fees()));
                    gross.setText(WalletUi.money(snapshot.gross())); net.setText(WalletUi.money(snapshot.sellerNet()));
                    range.setText(date.format(snapshot.from())+" – "+date.format(snapshot.to()));
                    review.setText(snapshot.reviewCount()+" phiên cần đối soát"); review.setVisible(snapshot.reviewCount()>0);
                    if (following) { table.getItems().addAll(snapshot.entries()); }
                    else if (!table.getItems().equals(snapshot.entries())) {
                        var chosen=table.getSelectionModel().getSelectedItem();
                        table.getItems().setAll(snapshot.entries());
                        if (chosen!=null) { table.getItems().stream().filter(entry -> entry.auctionId()==chosen.auctionId())
                                .findFirst().ifPresent(entry -> table.getSelectionModel().select(entry)); }
                    }
                    paged=following; cursor=snapshot.entries().isEmpty() ? 0 : snapshot.entries().getLast().auctionId();
                    next.setDisable(!snapshot.hasMore());
                    status.setText(table.getItems().size()+" giao dịch · "+snapshot.paidCount()+" phiên đã thanh toán");
                }));
    }

    private static Label value(String id) { Label label=DesktopTheme.label("—","stat-value"); label.setId(id); return label; }
    private static VBox card(String caption, Label amount) {
        Label title=DesktopTheme.label(caption,"muted"); title.setWrapText(true);
        HBox captionRow = new HBox(8,ServerIcons.icon("cash"),title); captionRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        amount.setMinWidth(0); amount.setWrapText(true); amount.setMaxWidth(Double.MAX_VALUE);
        VBox box=new VBox(12,captionRow,amount); box.getStyleClass().addAll("card","admin-cash-card");
        if (amount.getId().equals("cash-flow-balance")) { box.getStyleClass().add("admin-metric-featured"); }
        box.setMinWidth(0); box.setMaxWidth(Double.MAX_VALUE); return box;
    }
}
