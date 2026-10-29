package vn.ptit.btl16.client.fx;

import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/** Metallic vector medals and trophy, scalable at Windows display resolutions. */
final class RankingArtwork extends Pane {
    RankingArtwork(int rank, double size) {
        setMinSize(size, size); setPrefSize(size, size); setMaxSize(size, size); setMouseTransparent(true);
        String dark = rank == 2 ? "#676866" : rank == 3 ? "#843b16" : "#a36308";
        String mid = rank == 2 ? "#aaaead" : rank == 3 ? "#ce8549" : "#e8af31";
        String light = rank == 2 ? "#fafbf8" : rank == 3 ? "#ffe3b4" : "#fff4bd";
        Paint metal = new LinearGradient(0, 0, 1, .7, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(light)), new Stop(.28, Color.web(mid)), new Stop(.48, Color.web(light)),
                new Stop(.65, Color.web(mid)), new Stop(1, Color.web(dark)));
        Group art = new Group();
        for (int side : new int[]{-1, 1}) {
            SVGPath stem = path(side < 0 ? "M44 91 Q3 75 16 25" : "M56 91 Q97 75 84 25", Color.TRANSPARENT);
            stem.setStroke(Color.web(mid)); stem.setStrokeWidth(1.6); art.getChildren().add(stem);
            for (int index = 0; index < 8; index++) {
                double degrees = 65 - index * 15;
                double angle = Math.toRadians(degrees);
                double x = 50 + side * 37 * Math.cos(angle), y = 50 + 38 * Math.sin(angle);
                Ellipse leaf = new Ellipse(x, y, 2.7, 6.4); leaf.setRotate(side * (35 - degrees)); leaf.setFill(metal);
                art.getChildren().add(leaf);
            }
        }
        if (rank == 0) {
            SVGPath handles = path("M29 27H16v10c0 16 10 20 24 20 M71 27h13v10c0 16-10 20-24 20", Color.TRANSPARENT);
            handles.setStroke(metal); handles.setStrokeWidth(4);
            SVGPath cup = path("M27 20h46l-4 23Q65 60 54 62v17h9v6H37v-6h9V62Q35 60 31 43z", metal);
            SVGPath base = path("M33 86h34v7H33z M28 93h44v6H28z", metal);
            cup.setStroke(Color.web(dark)); cup.setStrokeWidth(.7); art.getChildren().addAll(handles, cup, base);
        } else {
            art.getChildren().add(path("M29 61L24 91l16-8l10 12l9-12l17 8l-6-30z", metal));
            Circle rim = new Circle(50, 51, 29, metal); rim.setStroke(Color.web(light)); rim.setStrokeWidth(2);
            Circle center = new Circle(50, 51, 23, new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web(mid)), new Stop(1, Color.web(dark)))); center.setStroke(Color.web(light));
            Text number = new Text(Integer.toString(rank)); number.setFont(Font.font("Be Vietnam Pro", FontWeight.BOLD, 34)); number.setFill(Color.web(light));
            number.setX(50 - number.getLayoutBounds().getWidth() / 2); number.setY(63);
            SVGPath crown = path("M32 22L28 10l13 6l9-14l9 14l13-6l-4 12z", metal); crown.setStroke(Color.web(light));
            art.getChildren().addAll(rim, center, number, crown);
        }
        Group scaled = new Group(art); art.setScaleX(size / 100); art.setScaleY(size / 100);
        art.setTranslateX((size - 100) / 2); art.setTranslateY((size - 100) / 2); getChildren().add(scaled);
    }
    private static SVGPath path(String data, Paint fill) { SVGPath shape = new SVGPath(); shape.setContent(data); shape.setFill(fill); return shape; }
}
