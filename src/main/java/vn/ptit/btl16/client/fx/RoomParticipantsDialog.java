package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.service.RoomSocialApi;
import vn.ptit.btl16.common.fx.DesktopTheme;

import java.util.*;
import java.util.function.BooleanSupplier;

public final class RoomParticipantsDialog extends Dialog<Void> {
    private record Member(long id, String name, boolean eligible, String reason) { }
    private final ClientTransport transport;
    private final RoomSocialApi api;
    private final long room;
    private final BooleanSupplier current;
    private final Runnable changed;
    private final TextField search = new TextField();
    private final CheckBox selectAll = new CheckBox("Ch\u1ecdn t\u1ea5t c\u1ea3 kh\u00e1ch h\u1ee3p l\u1ec7");
    private final ListView<Member> list = new ListView<>();
    private final Label count = DesktopTheme.label("", "muted");
    private final Label status = DesktopTheme.label("", "room-dialog-status");
    private final Button reload = new Button("T\u1ea3i l\u1ea1i");
    private final Button submit;
    private final Set<Long> selected = new LinkedHashSet<>();
    private final Map<Long, String> failures = new HashMap<>();
    private List<Member> members = List.of();
    private boolean loading, busy, disposed;
    private int completed, failed;
    private Long initial;

    public RoomParticipantsDialog(Window owner, ClientTransport transport, long room,
            BooleanSupplier current, Runnable changed, Long initial) {
        this.transport = transport; this.api = new RoomSocialApi(transport); this.room = room;
        this.current = current; this.changed = changed; this.initial = initial;
        setTitle("Qu\u1ea3n l\u00fd ng\u01b0\u1eddi tham gia");
        getDialogPane().getStyleClass().addAll("room-dialog", "room-participants-dialog");
        getDialogPane().setPrefWidth(600);
        Label title = DesktopTheme.label("M\u1eddi kh\u00e1ch ra kh\u1ecfi ph\u00f2ng", "room-dialog-title");
        Label note = DesktopTheme.label("Ch\u1ecdn m\u1ed9t ho\u1eb7c nhi\u1ec1u ng\u01b0\u1eddi trong danh s\u00e1ch. Ng\u01b0\u1eddi \u0111\u00e3 tr\u1ea3 gi\u00e1 kh\u00f4ng th\u1ec3 b\u1ecb m\u1eddi ra; máy chủ ki\u1ec3m tra l\u1ea1i t\u1eebng ng\u01b0\u1eddi.", "muted");
        search.setId("room-kick-search"); search.setPromptText("T\u00ecm theo t\u00ean hi\u1ec3n th\u1ecb trong ph\u00f2ng...");
        search.textProperty().addListener((observable, before, after) -> filter());
        selectAll.setId("room-kick-select-all");
        selectAll.setOnAction(event -> {
            for (Member member : list.getItems()) {
                if (member.eligible()) { if (selectAll.isSelected()) { selected.add(member.id()); } else { selected.remove(member.id()); } }
            }
            updateSelection();
        });
        reload.setId("room-kick-reload"); reload.getStyleClass().add("ghost"); reload.setOnAction(event -> load());
        HBox tools = new HBox(10, selectAll, DesktopTheme.spacer(), reload); tools.setAlignment(Pos.CENTER_LEFT);
        list.setId("room-kick-list"); list.getStyleClass().add("room-participants-list");
        list.setPrefHeight(290); list.setMinHeight(110); list.setCellFactory(view -> new MemberCell());
        list.setPlaceholder(DesktopTheme.label("Kh\u00f4ng c\u00f3 ng\u01b0\u1eddi ph\u00f9 h\u1ee3p", "muted"));
        count.setId("room-kick-count"); status.setId("room-kick-status");
        status.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox content = new VBox(14, title, note, search, tools, list, count, status);
        content.setMinWidth(0); VBox.setVgrow(list, Priority.ALWAYS);
        getDialogPane().setContent(content);
        ButtonType remove = new ButtonType("M\u1eddi ra kh\u1ecfi ph\u00f2ng", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(remove, new ButtonType("\u0110\u00f3ng", ButtonBar.ButtonData.CANCEL_CLOSE));
        submit = (Button) getDialogPane().lookupButton(remove); submit.setId("room-kick-submit"); submit.getStyleClass().add("room-danger-button");
        submit.setDefaultButton(false);
        submit.addEventFilter(ActionEvent.ACTION, event -> { event.consume(); start(); });
        setOnCloseRequest(event -> { if (busy) { event.consume(); } });
        setOnHidden(event -> disposed = true);
        DesktopTheme.dialog(this, owner); load();
    }

    private boolean available() { return !disposed && current.getAsBoolean() && transport.isConnected(); }

    private void load() {
        if (loading || busy || disposed) { return; }
        if (!available()) { status.setText("M\u1ea5t k\u1ebft n\u1ed1i ho\u1eb7c phi\u00ean \u0111\u00e3 thay \u0111\u1ed5i. H\u00e3y m\u1edf l\u1ea1i danh s\u00e1ch."); updateSelection(); return; }
        loading = true; updateSelection(); count.setText("\u0110ang t\u1ea3i danh s\u00e1ch m\u1edbi nh\u1ea5t...");
        api.social(room, 0, List.of()).whenComplete((response, error) -> Platform.runLater(() -> {
            if (disposed) { return; }
            loading = false;
            if (!available() || error != null || response == null || !response.isSuccess()) {
                members = List.of(); selected.clear(); filter();
                status.setText(response != null && !response.isSuccess() ? response.getUserMessage() : "Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c danh s\u00e1ch. H\u00e3y th\u1eed l\u1ea1i."); return;
            }
            var data = response.getWireMessage().getData(); List<Member> next = new ArrayList<>();
            for (int index = 0; index < Math.min(200, ClientWireParser.integer(data, "memberCount", 0)); index++) {
                String prefix = "member." + index + ".";
                if (Boolean.parseBoolean(data.get(prefix + "host"))) { continue; }
                next.add(new Member(ClientWireParser.longValue(data, prefix + "participantId", 0),
                        data.getOrDefault(prefix + "name", ""), Boolean.parseBoolean(data.get(prefix + "canKick")), data.getOrDefault(prefix + "kickReason", "")));
            }
            members = List.copyOf(next);
            Set<Long> eligible = new HashSet<>(); members.stream().filter(Member::eligible).forEach(member -> eligible.add(member.id()));
            selected.retainAll(eligible);
            if (initial != null && eligible.contains(initial)) { selected.add(initial); } initial = null;
            filter();
            if (ClientWireParser.integer(data, "memberTotal", 0) > 200) { status.setText("Ph\u00f2ng \u0111\u00f4ng: danh s\u00e1ch hi\u1ec3n th\u1ecb t\u1ed1i \u0111a 200 ng\u01b0\u1eddi, bao g\u1ed3m ch\u1ee7 ph\u00f2ng."); }
        }));
    }

    private void filter() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        list.getItems().setAll(members.stream().filter(member -> member.name().toLowerCase(Locale.ROOT).contains(query)).toList());
        updateSelection();
    }

    private void updateSelection() {
        boolean disabled = loading || busy || !available();
        search.setDisable(loading || busy); list.setDisable(disabled); selectAll.setDisable(disabled); reload.setDisable(loading || busy);
        List<Member> eligible = list.getItems().stream().filter(Member::eligible).toList();
        long checked = eligible.stream().filter(member -> selected.contains(member.id())).count();
        selectAll.setSelected(!eligible.isEmpty() && checked == eligible.size()); selectAll.setIndeterminate(checked > 0 && checked < eligible.size());
        count.setText("\u0110\u00e3 ch\u1ecdn " + selected.size() + " ng\u01b0\u1eddi \u00b7 " + list.getItems().size() + " / " + members.size() + " kh\u00e1ch hi\u1ec3n th\u1ecb");
        submit.setDisable(disabled || selected.isEmpty());
        submit.setText(busy ? "\u0110ang m\u1eddi ra..." : "M\u1eddi " + selected.size() + " ng\u01b0\u1eddi ra"); list.refresh();
    }

    private void start() {
        if (loading || busy || selected.isEmpty() || !available()) { return; }
        List<Member> targets = members.stream().filter(member -> member.eligible() && selected.contains(member.id())).toList();
        busy = true; completed = 0; failed = 0; failures.clear(); updateSelection(); kickNext(targets, 0);
    }

    private void kickNext(List<Member> targets, int index) {
        if (index >= targets.size() || !available()) {
            busy = false;
            status.setText("\u0110\u00e3 x\u00e1c nh\u1eadn m\u1eddi " + completed + " ng\u01b0\u1eddi ra" + (failed > 0 ? " \u00b7 " + failed + " l\u01b0\u1ee3t b\u1ecb t\u1eeb ch\u1ed1i ho\u1eb7c ch\u01b0a x\u00e1c nh\u1eadn (xem l\u00fd do trong danh s\u00e1ch)." : ".")
                    + (index < targets.size() ? " \u0110\u00e3 d\u1eebng; ch\u01b0a x\u1eed l\u00fd " + (targets.size() - index) + " ng\u01b0\u1eddi c\u00f2n l\u1ea1i." : ""));
            updateSelection(); if (completed > 0) { changed.run(); } if (available()) { load(); } return;
        }
        Member target = targets.get(index);
        status.setText("\u0110ang x\u1eed l\u00fd " + (index + 1) + " / " + targets.size() + ": " + target.name());
        api.kick(room, target.id()).whenComplete((response, error) -> Platform.runLater(() -> {
            if (disposed) { return; }
            if (error == null && response != null && response.isSuccess()) {
                completed++; selected.remove(target.id()); members = members.stream().filter(member -> member.id() != target.id()).toList();
            } else {
                failed++; failures.put(target.id(), response != null ? response.getUserMessage() : "Ch\u01b0a x\u00e1c nh\u1eadn; h\u00e3y t\u1ea3i l\u1ea1i danh s\u00e1ch.");
            }
            filter(); kickNext(targets, index + 1);
        }));
    }

    private final class MemberCell extends ListCell<Member> {
        @Override protected void updateItem(Member member, boolean empty) {
            super.updateItem(member, empty); setText(null); setGraphic(null);
            if (empty || member == null) { return; }
            CheckBox check = new CheckBox(); check.setId("room-kick-check-" + member.id());
            check.setAccessibleText(member.name()); check.setSelected(selected.contains(member.id())); check.setDisable(!member.eligible() || busy);
            check.setOnAction(event -> { if (check.isSelected()) { selected.add(member.id()); } else { selected.remove(member.id()); } updateSelection(); });
            Label name = DesktopTheme.label(member.name(), "social-name");
            String reason = failures.getOrDefault(member.id(), member.eligible() ? "Ch\u01b0a tr\u1ea3 gi\u00e1 \u00b7 C\u00f3 th\u1ec3 m\u1eddi ra"
                    : "HAS_VALID_BID".equals(member.reason()) ? "\u0110\u00e3 tr\u1ea3 gi\u00e1 \u00b7 \u0110\u01b0\u1ee3c b\u1ea3o v\u1ec7" : "Kh\u00f4ng th\u1ec3 m\u1eddi ra trong tr\u1ea1ng th\u00e1i hi\u1ec7n t\u1ea1i");
            VBox identity = new VBox(4, name, DesktopTheme.label(reason, "muted")); identity.setMinWidth(0); HBox.setHgrow(identity, Priority.ALWAYS);
            HBox row = new HBox(12, check, identity); row.setAlignment(Pos.CENTER_LEFT);
            row.prefWidthProperty().bind(list.widthProperty().subtract(40)); setGraphic(row);
        }
    }
}
