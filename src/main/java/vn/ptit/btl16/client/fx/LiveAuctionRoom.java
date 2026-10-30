package vn.ptit.btl16.client.fx;

import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.model.ClientBid;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class LiveAuctionRoom {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final Comparator<ClientBid> BID_ORDER = Comparator.comparingLong(ClientBid::getServerSequence)
            .thenComparingLong(ClientBid::getBidId);
    private final HBox heading = new HBox(12);
    private final VBox insights = new VBox(10);
    private final VBox history = new VBox(10);
    private final Label live = DesktopTheme.label("LIVE", "room-live-tag");
    private final Label status = DesktopTheme.label("", "room-status-title");
    private final Label statusDetail = DesktopTheme.label("", "muted");
    private final Label viewers = DesktopTheme.label("", "room-presence");
    private final Label recentCount = DesktopTheme.label("0", "room-metric-value");
    private final Label bidders = DesktopTheme.label("0", "room-metric-value");
    private final Label growth = DesktopTheme.label("0%", "room-metric-value");
    private final Label extensions = DesktopTheme.label("...", "room-metric-value");
    private final Label statisticsNote = DesktopTheme.label("\u0110ang t\u1ea3i th\u1ed1ng k\u00ea to\u00e0n phi\u00ean...", "muted");
    private final Label chartNote = DesktopTheme.label("", "muted");
    private final Label notice = DesktopTheme.label("", "room-live-notice");
    private final Label deadline = DesktopTheme.label("", "muted");
    private final ProgressBar timeProgress = new ProgressBar();
    private final AreaChart<Number, Number> chart;
    private final ToggleButton mine = new ToggleButton("L\u01b0\u1ee3t c\u1ee7a t\u00f4i");
    private final VBox bidRows = new VBox(8);
    private final Label historyNote = DesktopTheme.label("", "muted");
    private ClientAuction auction;
    private List<ClientBid> bids = List.of();
    private List<PricePoint> plotted = List.of();
    private long userId;
    private String dataState = "READY";

    public void setDataState(String state) { dataState = state; }
    private String historyKey = "";
    private Runnable onHistory = () -> { };
    private java.util.function.LongConsumer onProfile = id -> { };

    public void onProfile(java.util.function.LongConsumer action) { onProfile = action; }

    public LiveAuctionRoom() {
        insights.setMinWidth(0);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.setMaxWidth(Double.MAX_VALUE);
        VBox identity = new VBox(4, status, statusDetail);
        HBox.setHgrow(identity, Priority.ALWAYS);
        statusDetail.setWrapText(true);
        status.setWrapText(true);
        viewers.setMinWidth(130);
        viewers.setAlignment(Pos.CENTER_RIGHT);
        heading.getChildren().setAll(live, identity, viewers);
        status.setId("room-session-status");
        viewers.setId("room-viewers");
        recentCount.setId("room-recent-bids");
        bidders.setId("room-recent-bidders");
        growth.setId("room-price-growth");
        timeProgress.setId("room-time-progress");
        notice.setId("room-live-notice");
        chartNote.setId("room-chart-note");
        notice.setWrapText(true);
        chartNote.setWrapText(true);
        deadline.setWrapText(true);
        timeProgress.setMaxWidth(Double.MAX_VALUE);
        timeProgress.getStyleClass().add("room-time-progress");
        NumberAxis horizontal = new NumberAxis();
        horizontal.setTickLabelsVisible(false);
        horizontal.setTickMarkVisible(false);
        horizontal.setMinorTickVisible(false);
        NumberAxis vertical = new NumberAxis();
        vertical.setForceZeroInRange(false);
        vertical.setMinorTickVisible(false);
        vertical.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number value) { return compact(value); }
            @Override public Number fromString(String value) { return 0; }
        });
        chart = new AreaChart<>(horizontal, vertical);
        chart.setId("room-price-chart");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setVerticalGridLinesVisible(false);
        chart.setMinHeight(145);
        chart.setMinWidth(0);
        chart.setPrefHeight(165);
        chart.setMaxHeight(185);
        chart.getStyleClass().add("room-price-chart");
        FlowPane metrics = new FlowPane(18, 8,
                metric("T\u1ed4NG BID H\u1ee2P L\u1ec6", recentCount),
                metric("T\u00c0I KHO\u1ea2N \u0110\u00c3 BID", bidders),
                metric("S\u1ed0 L\u1ea6N GIA H\u1ea0N", extensions),
                metric("T\u0102NG T\u1eea KH\u1edeI \u0110I\u1ec2M", growth));
        HBox chartHeading = new HBox(8, DesktopTheme.label("Di\u1ec5n bi\u1ebfn gi\u00e1", "section-title"),
                DesktopTheme.spacer(), DesktopTheme.label("VND", "eyebrow"));
        chartHeading.setAlignment(Pos.CENTER_LEFT);
        insights.getStyleClass().add("room-insights");
        statisticsNote.setWrapText(true); statisticsNote.setId("room-statistics-status"); extensions.setId("room-extensions");
        insights.getChildren().setAll(metrics, statisticsNote, chartHeading, chart, chartNote, timeProgress, deadline, notice);
        mine.setId("room-history-mine");
        mine.getStyleClass().add("ghost");
        mine.setOnAction(event -> renderHistory(true));
        Button all = new Button("M\u1edf l\u1ecbch s\u1eed");
        all.getStyleClass().add("ghost");
        all.setOnAction(event -> onHistory.run());
        HBox historyHeading = new HBox(8, DesktopTheme.label("D\u00f2ng \u0111\u1eb7t gi\u00e1", "section-title"),
                DesktopTheme.spacer(), mine);
        historyHeading.setAlignment(Pos.CENTER_LEFT);
        history.getStyleClass().addAll("card", "room-history");
        history.getChildren().setAll(historyHeading, historyNote, bidRows, all);
        historyNote.setWrapText(true);
        historyNote.setId("room-history-note");
        bidRows.setId("room-bid-rows");
    }

    public HBox heading() { return heading; }
    public VBox insights() { return insights; }
    public VBox history() { return history; }
    public void onHistory(Runnable action) { onHistory = action; }

    public void update(ClientAuction value, List<ClientBid> values, long currentUserId,
            boolean connected, Instant now) {
        ClientAuction previous = auction;
        boolean changedRoom = previous == null || previous.getAuctionId() != value.getAuctionId()
                || userId != currentUserId;
        auction = value;
        userId = currentUserId;
        bids = values.stream().filter(bid -> bid.getAuctionId() == value.getAuctionId()).sorted(BID_ORDER).toList();
        if (changedRoom) {
            mine.setSelected(false);
            plotted = List.of();
            historyKey = "";
            setStatistics(java.util.Map.of("state", "LOADING"));
            notice.setText("Ch\u1ec9 l\u01b0\u1ee3t \u0111\u01b0\u1ee3c server ch\u1ea5p nh\u1eadn m\u1edbi xu\u1ea5t hi\u1ec7n t\u1ea1i \u0111\u00e2y.");
        }
        viewers.setText(Math.max(0, value.getWatcherCount()) + " k\u1ebft n\u1ed1i theo d\u00f5i");
        renderChart();
        renderHistory(changedRoom);
        tick(now, connected);
    }

    public void tick(Instant now, boolean connected) {
        if (auction == null) { return; }
        boolean expired = auction.isOpen() && auction.getEndTime() != null
                && !now.isBefore(auction.getEndTime());
        String title;
        String detail;
        String tone;
        if (!connected) {
            title = "M\u1ea5t k\u1ebft n\u1ed1i v\u1edbi server";
            detail = "\u0110\u00e2y l\u00e0 d\u1eef li\u1ec7u g\u1ea7n nh\u1ea5t. Ch\u1edd server x\u00e1c nh\u1eadn tr\u01b0\u1edbc khi \u0111\u1eb7t gi\u00e1 ti\u1ebfp.";
            tone = "offline";
        } else if (!"READY".equals(dataState)) {
            title = "SYNCING".equals(dataState) ? "\u0110ang \u0111\u1ed3ng b\u1ed9 ph\u00f2ng" : "D\u1eef li\u1ec7u ph\u00f2ng \u0111\u00e3 c\u0169";
            detail = "\u0110\u00e3 k\u1ebft n\u1ed1i nh\u01b0ng ch\u01b0a \u0111\u1ee7 d\u1eef li\u1ec7u h\u1ee3p l\u1ec7. T\u1ea1m kh\u00f3a \u0111\u1eb7t gi\u00e1 v\u00e0 qu\u1ea3n l\u00fd; ch\u1edd x\u00e1c nh\u1eadn t\u1eeb server.";
            tone = "offline";
        } else if (!auction.isOpen()) {
            boolean cancelled = "CANCELLED".equals(auction.getStatus());
            boolean won = Objects.equals(auction.getCurrentWinnerId(), userId);
            title = cancelled ? "Phi\u00ean \u0111\u00e3 h\u1ee7y" : auction.getCurrentWinnerId() == null
                    ? "K\u1ebft th\u00fac \u00b7 Ch\u01b0a c\u00f3 ng\u01b0\u1eddi th\u1eafng" : won
                    ? "B\u1ea1n \u0111\u00e3 th\u1eafng phi\u00ean \u0111\u1ea5u gi\u00e1!" : "Phi\u00ean \u0111\u00e3 c\u00f3 k\u1ebft qu\u1ea3";
            detail = cancelled || auction.getCurrentWinnerId() == null
                    ? "Kh\u00f4ng ph\u00e1t sinh giao d\u1ecbch mua b\u00e1n t\u1eeb phi\u00ean n\u00e0y."
                    : auction.getCurrentWinnerUsername() + " th\u1eafng v\u1edbi gi\u00e1 "
                            + WalletUi.money(auction.getCurrentPrice()) + ". Xem bi\u1ebfn \u0111\u1ed9ng trong v\u00ed.";
            tone = won && !cancelled ? "winning" : "finished";
        } else if (expired) {
            title = "\u0110ang ch\u1edd server ch\u1ed1t phi\u00ean";
            detail = "\u0110\u1ed3ng h\u1ed3 v\u1ec1 0 ch\u01b0a x\u00e1c nh\u1eadn ng\u01b0\u1eddi th\u1eafng. K\u1ebft qu\u1ea3 s\u1ebd \u0111\u01b0\u1ee3c server c\u1eadp nh\u1eadt.";
            tone = "urgent";
        } else if (auction.isHostedBy(userId)) {
            title = "B\u1ea1n \u0111ang \u0111i\u1ec1u h\u00e0nh phi\u00ean";
            detail = "Theo d\u00f5i c\u1ea1nh tranh gi\u00e1 tr\u1ef1c ti\u1ebfp. Ch\u1ee7 ph\u00f2ng kh\u00f4ng \u0111\u01b0\u1ee3c t\u1ef1 \u0111\u1eb7t gi\u00e1.";
            tone = "hosting";
        } else if (Objects.equals(auction.getCurrentWinnerId(), userId)) {
            title = "B\u1ea1n \u0111ang d\u1eabn \u0111\u1ea7u";
            detail = "M\u1ee9c " + WalletUi.money(auction.getCurrentPrice())
                    + " \u0111\u00e3 \u0111\u01b0\u1ee3c ch\u1ea5p nh\u1eadn. Ch\u01b0a ph\u1ea3i k\u1ebft qu\u1ea3 cu\u1ed1i c\u00f9ng.";
            tone = "winning";
        } else if (bids.stream().anyMatch(bid -> bid.getUserId() == userId)) {
            title = "B\u1ea1n \u0111\u00e3 b\u1ecb v\u01b0\u1ee3t gi\u00e1";
            detail = "C\u00f3 th\u1ec3 tr\u1edf l\u1ea1i v\u1edbi m\u1ee9c t\u1ed1i thi\u1ec3u "
                    + WalletUi.money(auction.getCurrentPrice().add(auction.getMinBidIncrement())) + ".";
            tone = "outbid";
        } else {
            title = auction.getCurrentWinnerId() == null ? "Ch\u1edd l\u01b0\u1ee3t m\u1edf gi\u00e1" : "S\u1eb5n s\u00e0ng tham gia";
            detail = "Gi\u00e1 t\u1ed1i thi\u1ec3u "
                    + WalletUi.money(auction.getCurrentPrice().add(auction.getMinBidIncrement()))
                    + ". L\u01b0\u1ee3t h\u1ee3p l\u1ec7 \u0111\u00e3 g\u1eedi kh\u00f4ng th\u1ec3 r\u00fat l\u1ea1i.";
            tone = "watching";
        }
        if (!status.getText().equals(title)) { status.setText(title); DesktopTheme.reveal(status); }
        statusDetail.setText(detail);
        heading.getStyleClass().setAll("room-signal", "room-signal-" + tone);
        live.setText(!connected ? "RECONNECTING".equals(dataState) ? "KẾT NỐI LẠI" : "OFFLINE"
                : !"READY".equals(dataState) ? "SYNCING".equals(dataState) ? "ĐỒNG BỘ" : "DỮ LIỆU CŨ" : !auction.isOpen() ? "K\u1ebeT QU\u1ea2" : expired ? "CH\u1edc CH\u1ed0T" : "LIVE");
        long remaining = auction.getEndTime() == null ? 0 : Math.max(0, Duration.between(now, auction.getEndTime()).toMillis());
        long total = auction.getStartTime() == null || auction.getEndTime() == null ? 0
                : Duration.between(auction.getStartTime(), auction.getEndTime()).toMillis();
        timeProgress.setProgress(!auction.isOpen() || total <= 0 ? 0 : Math.min(1, (double) remaining / total));
        timeProgress.getStyleClass().remove("urgent");
        AuctionDeadlineStyle.update(deadline, auction, now);
        if (auction.isOpen() && remaining <= 30000) { timeProgress.getStyleClass().add("urgent"); }
        deadline.setText(auction.getEndTime() == null ? "Ch\u01b0a c\u00f3 m\u1ed1c th\u1eddi gian"
                : (!auction.isOpen() ? "Phi\u00ean \u0111\u00e3 \u0111\u00f3ng" : expired ? "\u0110ang ch\u1edd k\u1ebft qu\u1ea3" : remaining <= 30000
                ? "30 gi\u00e2y cu\u1ed1i \u00b7 Theo d\u00f5i gia h\u1ea1n t\u1eeb server" : "\u0110ang nh\u1eadn gi\u00e1")
                + " \u00b7 H\u1ea1n " + TIME.format(auction.getEndTime()));
    }

    public void setStatistics(java.util.Map<String, String> data) {
        if (data.containsKey("state")) {
            if ("LOADING".equals(data.get("state"))) {
                for (Label metric : List.of(recentCount, bidders, extensions, growth)) { metric.setText("..."); }
            }
            statisticsNote.setText("ERROR".equals(data.get("state")) ? "L\u1ed7i t\u1ea3i th\u1ed1ng k\u00ea; s\u1ed1 li\u1ec7u hi\u1ec7n c\u00f3 c\u00f3 th\u1ec3 \u0111\u00e3 c\u0169. \u0110ang th\u1eed l\u1ea1i..."
                    : "\u0110ang t\u1ea3i th\u1ed1ng k\u00ea to\u00e0n phi\u00ean..."); return;
        }
        recentCount.setText(data.getOrDefault("totalBids", "...")); bidders.setText(data.getOrDefault("uniqueBidders", "..."));
        boolean complete = Boolean.parseBoolean(data.get("extensionsComplete"));
        extensions.setText(data.getOrDefault("extensionCount", "...") + (complete ? "" : "+"));
        growth.setText("+" + WalletUi.money(vn.ptit.btl16.client.model.ClientWireParser.decimal(data, "priceIncrease")));
        statisticsNote.setText(complete ? "Th\u1ed1ng k\u00ea to\u00e0n phi\u00ean t\u1eeb server; kh\u00f4ng ph\u1ea3i s\u1ed1 d\u00f2ng \u0111\u00e3 t\u1ea3i."
                : "Phi\u00ean c\u0169: s\u1ed1 gia h\u1ea1n ch\u1ec9 ghi nh\u1eadn t\u1eeb khi n\u00e2ng c\u1ea5p, kh\u00f4ng bi\u1ebft t\u1ed5ng tr\u01b0\u1edbc \u0111\u00f3.");
    }

    public void clear() {
        auction = null;
        AuctionDeadlineStyle.update(deadline, null, Instant.now());
        dataState = "READY";
        bids = List.of();
        plotted = List.of();
        historyKey = "";
        mine.setSelected(false);
        chart.getData().clear();
        bidRows.getChildren().clear();
    }

    private void renderChart() {
        List<PricePoint> points = new ArrayList<>();
        points.add(new PricePoint(auction.getStartPrice(), "Gi\u00e1 kh\u1edfi \u0111i\u1ec3m"));
        for (ClientBid bid : bids) {
            points.add(new PricePoint(bid.getAmount(), bid.getUsername() + " \u00b7 " + TIME.format(bid.getCreatedAt())));
        }
        if (points.get(points.size() - 1).amount().compareTo(auction.getCurrentPrice()) != 0) {
            points.add(new PricePoint(auction.getCurrentPrice(), "Gi\u00e1 hi\u1ec7n t\u1ea1i do server x\u00e1c nh\u1eadn"));
        }
        chartNote.setText("Gi\u00e1 kh\u1edfi \u0111i\u1ec3m + " + bids.size()
                + " l\u01b0\u1ee3t g\u1ea7n nh\u1ea5t \u0111\u00e3 t\u1ea3i; kh\u00f4ng ph\u1ea3i to\u00e0n b\u1ed9 l\u1ecbch s\u1eed.");
        if (points.equals(plotted)) { return; }
        plotted = List.copyOf(points);
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for (int index = 0; index < points.size(); index++) {
            PricePoint point = points.get(index);
            XYChart.Data<Number, Number> sample = new XYChart.Data<>(index, point.amount());
            Label marker = new Label();
            marker.getStyleClass().add("room-chart-dot");
            marker.setMinSize(7, 7);
            marker.setPrefSize(7, 7);
            marker.setMaxSize(7, 7);
            String description = point.label() + "\n" + WalletUi.money(point.amount());
            marker.setAccessibleText(description);
            Tooltip.install(marker, new Tooltip(description));
            sample.setNode(marker);
            series.getData().add(sample);
        }
        chart.getData().setAll(List.of(series));
        chart.setAccessibleText("Di\u1ec5n bi\u1ebfn gi\u00e1. Kh\u1edfi \u0111i\u1ec3m " + WalletUi.money(auction.getStartPrice())
                + ", hi\u1ec7n t\u1ea1i " + WalletUi.money(auction.getCurrentPrice()) + ", " + bids.size() + " l\u01b0\u1ee3t \u0111\u00e3 t\u1ea3i.");
    }

    private void renderHistory(boolean force) {
        if (auction == null) { return; }
        String key = auction.getAuctionId() + ":" + auction.getVersion() + ":" + auction.getStatus()
                + ":" + auction.getCurrentPrice() + ":" + auction.getCurrentWinnerId() + ":" + userId + ":" + mine.isSelected()
                + ":" + bids.stream().map(bid -> Long.toString(bid.getBidId())).collect(Collectors.joining(","));
        if (!force && historyKey.equals(key)) { return; }
        boolean newBid = !historyKey.isEmpty() && !historyKey.equals(key);
        historyKey = key;
        bidRows.getChildren().clear();
        List<ClientBid> selected = bids.stream().filter(bid -> !mine.isSelected() || bid.getUserId() == userId)
                .sorted(BID_ORDER.reversed()).limit(6).toList();
        historyNote.setText(selected.size() + " / " + bids.size() + " l\u01b0\u1ee3t \u0111\u00e3 t\u1ea3i"
                + (mine.isSelected() ? " \u00b7 Ch\u1ec9 hi\u1ec3n th\u1ecb l\u01b0\u1ee3t c\u1ee7a b\u1ea1n" : " \u00b7 M\u1edbi nh\u1ea5t tr\u01b0\u1edbc"));
        if (selected.isEmpty()) {
            Label empty = DesktopTheme.label(mine.isSelected()
                    ? "Kh\u00f4ng c\u00f3 l\u01b0\u1ee3t c\u1ee7a b\u1ea1n trong l\u1ecbch s\u1eed g\u1ea7n \u0111\u00e2y \u0111\u00e3 t\u1ea3i."
                    : "Ch\u01b0a c\u00f3 l\u01b0\u1ee3t \u0111\u1eb7t gi\u00e1. L\u01b0\u1ee3t \u0111\u01b0\u1ee3c server ch\u1ea5p nh\u1eadn s\u1ebd hi\u1ec7n \u1edf \u0111\u00e2y.", "muted");
            empty.setWrapText(true);
            bidRows.getChildren().add(empty);
            return;
        }
        for (ClientBid bid : selected) {
            boolean own = bid.getUserId() == userId;
            boolean leader = Objects.equals(auction.getCurrentWinnerId(), bid.getUserId())
                    && bid.getAmount().compareTo(auction.getCurrentPrice()) == 0;
            Label avatar = DesktopTheme.label(auction.hasAnonymousGuests() ? "" : bid.getUsername().isBlank() ? "?" : bid.getUsername().substring(0, 1).toUpperCase(Locale.ROOT), "room-avatar");
            if (!auction.hasAnonymousGuests()) { avatar.setOnMouseClicked(event -> onProfile.accept(bid.getUserId())); avatar.setStyle("-fx-cursor: hand;"); }
            Label name = new Label(bid.getUsername() + (own ? " (b\u1ea1n)" : ""));
            name.setWrapText(true);
            Label time = DesktopTheme.label(TIME.format(bid.getCreatedAt()) + (leader
                    ? auction.isOpen() ? " \u00b7 \u0110ang d\u1eabn \u0111\u1ea7u" : " \u00b7 Gi\u00e1 th\u1eafng" : " \u00b7 \u0110\u00e3 ch\u1ea5p nh\u1eadn"), "muted");
            time.setWrapText(true);
            VBox identity = new VBox(4, name, time);
            HBox.setHgrow(identity, Priority.ALWAYS);
            Label amount = DesktopTheme.label(WalletUi.money(bid.getAmount()), "bid-amount");
            HBox row = new HBox(10, avatar, identity, amount);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("room-live-bid");
            if (own) { row.getStyleClass().add("own"); }
            if (leader) { row.getStyleClass().add("leading"); }
            bidRows.getChildren().add(row);
        }
        if (newBid) { DesktopTheme.reveal(bidRows.getChildren().get(0)); }
    }

    private static VBox metric(String title, Label value) {
        Label caption = DesktopTheme.label(title, "eyebrow");
        caption.setWrapText(true);
        VBox box = new VBox(4, value, caption);
        box.setMinWidth(85);
        box.setMaxWidth(150);
        return box;
    }

    private static String compact(Number number) {
        BigDecimal value = new BigDecimal(number.toString());
        for (int exponent : new int[]{9, 6, 3}) {
            BigDecimal divisor = BigDecimal.TEN.pow(exponent);
            if (value.abs().compareTo(divisor) >= 0) {
                String suffix = exponent == 9 ? " t\u1ef7" : exponent == 6 ? " tr" : "k";
                return value.divide(divisor, 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + suffix;
            }
        }
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private record PricePoint(BigDecimal amount, String label) { }
}
