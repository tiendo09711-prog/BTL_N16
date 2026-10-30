package vn.ptit.btl16.client.fx;

import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** Converts the user's local wall-clock selection into an absolute server timestamp. */
final class AuctionSchedulePane extends VBox {
    private final ZoneId zone = ZoneId.systemDefault();
    private final ComboBox<String> mode = new ComboBox<>();
    private final DatePicker date = new DatePicker();
    private final ComboBox<String> hour = new ComboBox<>(), minute = new ComboBox<>();

    AuctionSchedulePane(Instant now) {
        super(7);
        mode.setId("auction-start-mode"); mode.getItems().setAll("Bắt đầu ngay", "Hẹn giờ bắt đầu"); mode.getSelectionModel().selectFirst();
        date.setId("auction-start-date"); hour.setId("auction-start-hour"); minute.setId("auction-start-minute");
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
        date.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate value) { return value == null ? "" : format.format(value); }
            @Override public LocalDate fromString(String text) { return text == null || text.isBlank() ? null : LocalDate.parse(text.strip(), format); }
        });
        date.setPromptText("dd/MM/yyyy"); date.setMinWidth(0); date.setPrefWidth(140); date.setMaxWidth(Double.MAX_VALUE);
        date.setDayCellFactory(picker -> new DateCell() {
            @Override public void updateItem(LocalDate day, boolean empty) {
                super.updateItem(day, empty); setDisable(empty || day == null || day.isBefore(LocalDate.ofInstant(now, zone)));
            }
        });
        for (int index = 0; index < 60; index++) {
            String value = String.format("%02d", index); minute.getItems().add(value);
            if (index < 24) { hour.getItems().add(value); }
        }
        var initial = now.atZone(zone).plusMinutes(5);
        date.setValue(initial.toLocalDate()); hour.setValue(String.format("%02d", initial.getHour())); minute.setValue(String.format("%02d", initial.getMinute()));
        hour.setMinWidth(60); minute.setMinWidth(60); hour.setPrefWidth(64); minute.setPrefWidth(64);
        hour.setAccessibleText("Giờ bắt đầu"); minute.setAccessibleText("Phút bắt đầu");
        HBox clock = new HBox(5, date, hour, new Label(":"), minute); clock.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(date, javafx.scene.layout.Priority.ALWAYS);
        clock.disableProperty().bind(mode.getSelectionModel().selectedIndexProperty().isEqualTo(0));
        Label help = new Label("Giờ trên máy: " + zone + ". Thời lượng tính từ lúc bắt đầu.");
        help.getStyleClass().add("muted"); help.setWrapText(true); help.setMaxWidth(355);
        getChildren().setAll(mode, clock, help);
    }

    javafx.beans.binding.BooleanBinding scheduledProperty() { return mode.getSelectionModel().selectedIndexProperty().isEqualTo(1); }

    Instant value(Instant now) {
        if (mode.getSelectionModel().getSelectedIndex() == 0) { return null; }
        try {
            date.commitValue();
            if (date.getValue() == null || hour.getValue() == null || minute.getValue() == null) { throw new IllegalArgumentException(); }
            LocalDateTime local = date.getValue().atTime(Integer.parseInt(hour.getValue()), Integer.parseInt(minute.getValue()));
            var offsets = zone.getRules().getValidOffsets(local);
            if (offsets.isEmpty()) { throw new IllegalArgumentException(); }
            Instant start = local.toInstant(offsets.get(0));
            if (!start.isAfter(now) || start.isAfter(Instant.parse("9999-12-30T00:00:00Z"))) { throw new IllegalArgumentException(); }
            return start;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Chọn ngày, giờ bắt đầu hợp lệ trong tương lai (ngày/tháng/năm và giờ:phút).");
        }
    }
}
