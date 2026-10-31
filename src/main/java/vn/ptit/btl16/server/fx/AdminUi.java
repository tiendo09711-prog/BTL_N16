package vn.ptit.btl16.server.fx;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

final class AdminUi {
    private AdminUi() { }

    static void table(TableView<?> table, int... numericColumns) {
        table.getStyleClass().add("admin-data-table");
        table.setMinSize(0, 80);
        for (var column : table.getColumns()) { column.setMinWidth(Math.min(100,Math.max(60,column.getPrefWidth()))); }
        for (int index : numericColumns) {
            var column = table.getColumns().get(index);
            column.getStyleClass().add("admin-number-column"); column.setMinWidth(115);
        }
    }

    static StackPane tableFrame(TableView<?> table) {
        StackPane frame = new StackPane(table);
        frame.getStyleClass().add("admin-table-frame"); frame.setMinSize(0, 80);
        Rectangle clip = new Rectangle(); clip.widthProperty().bind(frame.widthProperty());
        clip.heightProperty().bind(frame.heightProperty()); clip.setArcWidth(24); clip.setArcHeight(24);
        frame.setClip(clip); VBox.setVgrow(frame, Priority.ALWAYS); return frame;
    }

    static HBox navigation(TabPane tabs, String id, String... icons) {
        tabs.getStyleClass().add("admin-segmented-content");
        HBox bar = new HBox(5); bar.setId(id); bar.getStyleClass().add("admin-segmented-nav");
        ToggleGroup group = new ToggleGroup();
        for (int index = 0; index < tabs.getTabs().size(); index++) {
            int selected = index;
            ToggleButton button = new ToggleButton(tabs.getTabs().get(index).getText(), ServerIcons.icon(icons[index]));
            button.setId(id + "-" + index); button.setToggleGroup(group); button.setWrapText(true);
            button.setMinWidth(0); button.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(button, Priority.ALWAYS);
            button.setOnAction(event -> { tabs.getSelectionModel().select(selected); button.setSelected(true); });
            bar.getChildren().add(button);
        }
        ((ToggleButton) bar.getChildren().get(tabs.getSelectionModel().getSelectedIndex())).setSelected(true);
        tabs.getSelectionModel().selectedIndexProperty().addListener((observable, before, after) -> {
            if (after.intValue() >= 0) { ((ToggleButton) bar.getChildren().get(after.intValue())).setSelected(true); }
        });
        return bar;
    }

    static HBox toolbar(javafx.scene.Node... nodes) {
        HBox row = new HBox(12, nodes); row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("admin-card-toolbar"); return row;
    }
}
