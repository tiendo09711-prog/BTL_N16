package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.protocol.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Global friends directory and a collapsible private conversation at the bottom right. */
public final class FriendsPane extends VBox implements AutoCloseable {
    private record Person(long id, String username, String name, String code, String state, boolean online, int unread) {
        Person state(String next) { return new Person(id, username, name, code, next, online, unread); }
    }
    private record Message(long id, long sender, String body, Instant time) { }
    private record Draft(String body, String nonce) { }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
    private final ClientTransport transport;
    private final ClientAppModel model;
    private final FriendsApi api;
    private final Button icon = new Button(), back = new Button("‹"), minimize = new Button("−"), close = new Button("×");
    private final Label badge = new Label(), heading = new Label("Bạn bè"), status = new Label(), counter = new Label();
    private final VBox panel = new VBox(10), directory = new VBox(10), peopleRows = new VBox(8), conversation = new VBox(8), messageRows = new VBox(8);
    private final TextField search = new TextField(), searchCode = new TextField();
    private final TextArea input = new TextArea();
    private final ToggleButton friends = new ToggleButton("Bạn bè"), invitations = new ToggleButton("Lời mời");
    private final Button find = new Button("Tìm"), clearSearch = new Button("×"), send = new Button("Gửi"), older = new Button("Xem tin trước");
    private final ScrollPane peopleScroll = new ScrollPane(peopleRows), chatScroll = new ScrollPane(messageRows);
    private final List<Person> people = new ArrayList<>();
    private final TreeMap<Long, Message> messages = new TreeMap<>();
    private final Map<Long, String> drafts = new HashMap<>();
    private final Map<Long, Draft> retries = new HashMap<>();
    private final Set<Long> mutating = new HashSet<>(), sending = new HashSet<>();
    private Person lookup, current;
    private long userId, generation, listSequence, lookupSequence, chatGeneration, lastList, lastHistory, historyCursor;
    private String session = "";
    private boolean connected, disposed, loadingList, loadingHistory, chatMode, initialized, hasOlder;
    private int unread, pending;

    public FriendsPane(ClientTransport transport, ClientAppModel model) {
        this.transport = transport; this.model = model; this.api = new FriendsApi(transport);
        setId("friends-dock"); setSpacing(7); setAlignment(Pos.BOTTOM_RIGHT); setFillWidth(false);
        setMinWidth(0); setMaxWidth(370); setMaxHeight(Region.USE_PREF_SIZE); setPickOnBounds(false);
        icon.setId("marketplace-friends-button"); icon.getStyleClass().addAll("market-icon-button", "market-notification-button");
        icon.setTooltip(new Tooltip("Bạn bè và lời mời kết bạn")); icon.setAccessibleText("Bạn bè và lời mời kết bạn");
        badge.setId("friends-count"); badge.getStyleClass().add("market-notification-count"); badge.setMouseTransparent(true);
        var graphic = new StackPane(MarketplaceIcons.icon("users", 23, "#715c43"), badge);
        graphic.setPrefSize(28, 26); StackPane.setAlignment(badge, Pos.TOP_RIGHT); badge.setTranslateX(4); badge.setTranslateY(-3);
        icon.setGraphic(graphic); icon.setOnAction(event -> { if (available()) { showDirectory(); } });
        panel.setId("friends-panel"); panel.getStyleClass().add("friends-panel"); panel.setMinWidth(300); panel.setPrefWidth(370); panel.setMaxWidth(370);
        panel.setPadding(new Insets(12));
        heading.getStyleClass().add("section-title"); heading.setMinWidth(0); heading.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(heading, Priority.ALWAYS);
        back.setId("friends-back"); back.setTooltip(new Tooltip("Danh sách bạn bè")); back.setOnAction(event -> showDirectory());
        minimize.setId("friends-minimize"); minimize.setTooltip(new Tooltip("Thu gọn")); minimize.setOnAction(event -> collapse());
        close.setId("friends-close"); close.setTooltip(new Tooltip("Đóng khung trò chuyện")); close.setOnAction(event -> collapse());
        for (var action : List.of(back, minimize, close)) { action.getStyleClass().add("friends-window-action"); }
        HBox title = new HBox(6, back, heading, minimize, close); title.setAlignment(Pos.CENTER_LEFT);
        search.setId("friends-search"); search.setPromptText("Tên hiển thị"); search.setMinWidth(0); HBox.setHgrow(search, Priority.ALWAYS);
        searchCode.setId("friends-search-code"); searchCode.setPromptText("#0123"); searchCode.setPrefWidth(76); searchCode.setMaxWidth(76); searchCode.setMinWidth(64);
        searchCode.setAccessibleText("Mã kết bạn gồm 4 số"); searchCode.setOnAction(event -> search());
        searchCode.setTextFormatter(new TextFormatter<String>(change -> change.getControlNewText().matches("#?[0-9]{0,4}") ? change : null));
        find.setId("friends-search-button"); find.setMinWidth(Region.USE_PREF_SIZE); find.setOnAction(event -> search()); search.setOnAction(event -> search());
        clearSearch.setOnAction(event -> { search.clear(); searchCode.clear(); lookup = null; lookupSequence++; find.setDisable(!available()); renderPeople(); });
        ToggleGroup filters = new ToggleGroup(); friends.setToggleGroup(filters); invitations.setToggleGroup(filters); friends.setSelected(true);
        friends.setId("friends-list-tab"); invitations.setId("friends-invitations-tab");
        filters.selectedToggleProperty().addListener((observable, before, now) -> { if (now == null) { friends.setSelected(true); } renderPeople(); });
        for (var scroll : List.of(peopleScroll, chatScroll)) {
            scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setMinHeight(120); scroll.setPrefHeight(265); scroll.setMaxHeight(310);
            VBox.setVgrow(scroll, Priority.ALWAYS); scroll.getStyleClass().add("friends-scroll");
        }
        directory.getChildren().addAll(new HBox(6, search, searchCode, find, clearSearch), new HBox(7, friends, invitations), peopleScroll);
        older.setId("private-chat-older"); older.setOnAction(event -> history(true));
        input.setId("private-chat-input"); input.setPromptText("Nhập tin nhắn…"); input.setWrapText(true); input.setPrefRowCount(2); input.setMaxHeight(76);
        input.textProperty().addListener((observable, before, now) -> updateControls());
        input.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) { event.consume(); send(); } });
        send.setId("private-chat-send"); send.getStyleClass().add("primary"); send.setOnAction(event -> send());
        HBox footer = new HBox(8, counter, new Region(), send); HBox.setHgrow(footer.getChildren().get(1), Priority.ALWAYS);
        conversation.getChildren().addAll(older, chatScroll, input, footer);
        status.setId("friends-status"); status.setWrapText(true); status.setMaxWidth(Double.MAX_VALUE); status.getStyleClass().add("friends-status");
        panel.getChildren().addAll(title, directory, conversation, status); getChildren().add(panel);
        sceneProperty().addListener((observable, before, scene) -> { if (scene != null) { panel.maxHeightProperty().bind(scene.heightProperty().subtract(100)); } });
        show(panel, false); show(conversation, false); show(badge, false); show(this, false); updateControls();
    }

    public Button button() { return icon; }
    public void tick() {
        if (disposed) { return; }
        if (!model.hasSessionToken()) { if (!session.isEmpty()) { reset(); } return; }
        if (!session.equals(model.getSessionToken()) || userId != model.getUserId()) {
            reset(); session = model.getSessionToken(); userId = model.getUserId();
        }
        if (connected != transport.isConnected()) {
            connected = transport.isConnected(); generation++; listSequence++; lookupSequence++; chatGeneration++;
            loadingList = false; loadingHistory = false; sending.clear(); mutating.clear(); lastList = 0; lastHistory = 0;
            status.setText(connected ? "" : "Mất kết nối. Tin nhắn sẽ đồng bộ khi kết nối lại."); updateControls();
        }
        if (!available()) { return; }
        if (System.nanoTime() - lastList > 5_000_000_000L) { refresh(); }
        if (chatVisible() && System.nanoTime() - lastHistory > 3_000_000_000L) { history(false); }
    }

    public void onEvent(WireMessage event) {
        if (!available()) { return; }
        var data = event.getData();
        if (event.getType() == MessageType.FRIENDS_CHANGED && ClientWireParser.longValue(data, "recipientUserId", 0) == userId) { lastList = 0; refresh(); }
        if (event.getType() == MessageType.PRIVATE_MESSAGE && ClientWireParser.longValue(data, "recipientId", 0) == userId) {
            lastList = 0; refresh();
            if (current != null && current.id == ClientWireParser.longValue(data, "senderId", 0)) { lastHistory = 0; if (chatVisible()) { history(false); } }
        }
    }

    public void showPerson(long id, String name) {
        tick(); if (!available() || id <= 0 || id == userId) { return; }
        showDirectory(); status.setText("Đang tải " + name + "…");
        long token = generation, sequence = ++lookupSequence;
        api.lookup(id).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!valid(token) || sequence != lookupSequence) { return; }
            if (!success(response, error)) { return; }
            lookup = person(response.getWireMessage().getData(), "target."); status.setText("");
            renderPeople();
        }));
    }

    private void showDirectory() {
        if (!available()) { return; } saveDraft(); chatMode = false; heading.setText("Bạn bè");
        show(this, true); show(panel, true); show(directory, true); show(conversation, false); show(back, false); status.setText("");
        renderPeople(); lastList = 0; refresh(); updateControls();
    }
    private void collapse() { saveDraft(); show(panel, false); show(this, false); updateBadge(); }
    private void saveDraft() { if (current != null && chatMode) { drafts.put(current.id, input.getText()); } }

    private void refresh() {
        if (!available() || loadingList) { return; }
        loadingList = true; lastList = System.nanoTime(); long token = generation, sequence = ++listSequence;
        api.list().whenComplete((response, error) -> Platform.runLater(() -> {
            if (!valid(token) || sequence != listSequence) { return; } loadingList = false;
            if (!success(response, error)) { return; }
            var data = response.getWireMessage().getData(); people.clear();
            for (int index = 0; index < ClientWireParser.integer(data, "count", 0); index++) { people.add(person(data, "person." + index + ".")); }
            unread = ClientWireParser.integer(data, "unreadCount", 0); pending = ClientWireParser.integer(data, "requestCount", 0);
            friends.setText("Bạn bè (" + ClientWireParser.integer(data, "friendCount", 0) + ")"); invitations.setText("Lời mời (" + pending + ")");
            if (lookup != null) { lookup = people.stream().filter(value -> value.id == lookup.id).findFirst().orElse(lookup.state("NONE")); }
            if (current != null) { current = people.stream().filter(value -> value.id == current.id).findFirst().orElse(current); }
            renderPeople(); updateBadge();
        }));
    }

    private void search() {
        if (!available()) { return; }
        String name = search.getText().strip(), code = searchCode.getText().strip();
        long token = generation, sequence = ++lookupSequence; lookup = null; renderPeople();
        if (!code.matches("#?[0-9]{4}") || name.isBlank()) { find.setDisable(false); status.setText("Nhập tên hiển thị và mã kết bạn gồm đúng 4 số (ví dụ #0123)."); return; }
        find.setDisable(true); status.setText("Đang tìm tài khoản…");
        api.lookup(name, code).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!valid(token) || sequence != lookupSequence) { return; } find.setDisable(false);
            if (!success(response, error)) { renderPeople(); return; }
            lookup = person(response.getWireMessage().getData(), "target."); status.setText(""); renderPeople();
        }));
    }

    private void renderPeople() {
        peopleRows.getChildren().clear();
        List<Person> visible = lookup != null ? List.of(lookup) : people.stream().filter(person -> friends.isSelected() == person.state.equals("FRIEND"))
                .sorted(Comparator.comparing((Person person) -> !person.online).thenComparing(person -> person.name, String.CASE_INSENSITIVE_ORDER)).toList();
        for (var person : visible) {
            Label avatar = new Label(person.name.isBlank() ? "?" : person.name.substring(0, person.name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT));
            avatar.getStyleClass().add("friends-avatar"); avatar.setMinSize(38, 38); avatar.setAlignment(Pos.CENTER);
            Label name = new Label(person.name + (person.code.isBlank() ? "" : " #" + person.code)); name.getStyleClass().add("friends-person-name"); name.setWrapText(true);
            Label detail = new Label(person.online ? "Trực tuyến" : "Ngoại tuyến"); detail.getStyleClass().add("muted"); detail.setWrapText(true);
            HBox actions = new HBox(6); actions.setAlignment(Pos.CENTER_LEFT);
            switch (person.state) {
                case "FRIEND" -> actions.getChildren().add(action("Nhắn tin" + (person.unread > 0 ? " (" + person.unread + ")" : ""), "friend-message-" + person.id, () -> openChat(person)));
                case "INCOMING" -> actions.getChildren().addAll(action("Chấp nhận", "friend-accept-" + person.id, () -> mutate(person, "accept")), action("Từ chối", "friend-decline-" + person.id, () -> mutate(person, "decline")));
                case "OUTGOING" -> actions.getChildren().addAll(new Label("Đã gửi lời mời"), action("Hủy", "friend-cancel-" + person.id, () -> mutate(person, "cancel")));
                default -> actions.getChildren().add(action("Kết bạn", "friend-add-" + person.id, () -> mutate(person, "add")));
            }
            actions.setDisable(!available() || mutating.contains(person.id));
            VBox text = new VBox(4, name, detail, actions); HBox.setHgrow(text, Priority.ALWAYS); text.setMinWidth(0);
            HBox row = new HBox(10, avatar, text); row.setId("friend-person-" + person.id); row.getStyleClass().add("friends-person"); row.setPadding(new Insets(10)); peopleRows.getChildren().add(row);
        }
        if (visible.isEmpty()) { Label empty = new Label(friends.isSelected() ? "Chưa có bạn bè. Tìm bằng tên hiển thị và mã #4 số hoặc kết bạn từ trò chuyện trong phòng. Có thể gửi lời mời và nhắn tin cho bạn bè ngoại tuyến." : "Chưa có lời mời kết bạn."); empty.setWrapText(true); empty.setPadding(new Insets(14)); peopleRows.getChildren().add(empty); }
    }

    private void mutate(Person person, String action) {
        if (!available() || !mutating.add(person.id)) { return; } renderPeople(); long token = generation;
        CompletableFuture<ApiResponse> request = action.equals("add") ? api.add(person.id) : api.respond(person.id, action);
        request.whenComplete((response, error) -> Platform.runLater(() -> {
            if (!valid(token)) { return; } mutating.remove(person.id);
            if (success(response, error)) {
                String state = action.equals("add") ? response.get("target.state") : action.equals("accept") ? "FRIEND" : "NONE";
                if (lookup != null && lookup.id == person.id) { lookup = person.state(state); }
                status.setText(action.equals("accept") ? "Đã kết bạn. Hai bạn có thể nhắn tin." : action.equals("add") ? "Đã gửi lời mời kết bạn." : "Đã cập nhật lời mời.");
                lastList = 0; refresh();
            }
            renderPeople();
        }));
    }

    private void openChat(Person person) {
        if (!available() || !person.state.equals("FRIEND")) { return; }
        saveDraft(); boolean changed = current == null || current.id != person.id; current = person; chatMode = true;
        if (changed) { chatGeneration++; loadingHistory = false; messages.clear(); initialized = false; historyCursor = 0; hasOlder = false; input.setText(drafts.getOrDefault(person.id, "")); renderMessages(false, false); }
        show(this, true); show(panel, true); show(directory, false); show(conversation, true); show(back, true); heading.setText(person.name + (person.code.isBlank() ? "" : " #" + person.code));
        heading.setTooltip(new Tooltip("@" + person.username)); status.setText(""); lastHistory = 0; history(false); updateControls(); input.requestFocus(); updateBadge();
    }

    private void history(boolean previous) {
        if (!available() || !chatVisible() || loadingHistory || (previous && messages.isEmpty())) { return; }
        long token = generation, chat = chatGeneration, target = current.id;
        boolean first = !initialized, atBottom = chatScroll.getVvalue() >= .95, read = !previous && (first || atBottom);
        long before = previous ? messages.firstKey() : 0, after = !previous && initialized ? historyCursor : 0;
        loadingHistory = true; lastHistory = System.nanoTime(); older.setDisable(true);
        api.history(target, before, after, read).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!valid(token) || chat != chatGeneration || current == null || target != current.id) { return; }
            loadingHistory = false; older.setDisable(false);
            if (!success(response, error)) { updateControls(); return; }
            var data = response.getWireMessage().getData(); int count = ClientWireParser.integer(data, "count", 0);
            for (int index = 0; index < count; index++) {
                Message message = message(data, "message." + index + "."); messages.put(message.id, message);
                if (!previous) { historyCursor = Math.max(historyCursor, message.id); }
            }
            if (previous || first) { hasOlder = Boolean.parseBoolean(data.get("hasMore")); }
            initialized = true; renderMessages(!previous && (first || atBottom), previous); updateControls();
            if (read) { lastList = 0; refresh(); }
            if (after > 0 && count == 50) { lastHistory = 0; }
        }));
    }

    private void send() {
        if (send.isDisabled() || current == null || !available()) { return; }
        long token = generation, target = current.id; String body = input.getText().strip();
        Draft retry = retries.get(target); if (retry == null || !retry.body.equals(body)) { retry = new Draft(body, UUID.randomUUID().toString()); retries.put(target, retry); }
        sending.add(target); updateControls(); status.setText("Đang gửi…");
        api.send(target, body, retry.nonce).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!valid(token)) { return; } sending.remove(target);
            if (response != null && response.isSuccess() && error == null) {
                retries.remove(target); drafts.remove(target);
                if (current != null && current.id == target) {
                    var message = message(response.getWireMessage().getData(), ""); messages.put(message.id, message);
                    if (input.getText().strip().equals(body)) { input.clear(); }
                    status.setText(""); renderMessages(true, false); lastHistory = 0;
                }
            } else if (current != null && current.id == target) {
                status.setText(error == null && response != null ? response.getUserMessage() : "Chưa xác nhận tin đã gửi. Bấm Gửi để thử lại an toàn.");
            }
            updateControls();
        }));
    }

    private void renderMessages(boolean bottom, boolean prepended) {
        double oldHeight = messageRows.getHeight(), viewport = chatScroll.getViewportBounds().getHeight();
        double oldOffset = chatScroll.getVvalue() * Math.max(0, oldHeight - viewport);
        messageRows.getChildren().clear(); messageRows.setPadding(new Insets(8));
        for (var message : messages.values()) {
            boolean mine = message.sender == userId;
            Label text = new Label(message.body); text.setWrapText(true); text.setMaxWidth(263); text.setMinHeight(Region.USE_PREF_SIZE);
            Label time = new Label(TIME.format(message.time)); time.getStyleClass().add("private-message-time");
            VBox bubble = new VBox(4, text, time); bubble.getStyleClass().addAll("private-message", mine ? "mine" : "theirs"); bubble.setMaxWidth(280);
            HBox row = new HBox(bubble); row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT); row.setId("private-message-" + message.id); messageRows.getChildren().add(row);
        }
        if (messages.isEmpty()) { Label empty = new Label("Bắt đầu cuộc trò chuyện với " + (current == null ? "bạn bè" : current.name) + "."); empty.setWrapText(true); messageRows.getChildren().add(empty); }
        show(older, hasOlder); long chat = chatGeneration;
        Platform.runLater(() -> {
            if (chat != chatGeneration) { return; } messageRows.applyCss(); messageRows.layout();
            if (bottom) { chatScroll.setVvalue(1); }
            else if (prepended) { double range = messageRows.getHeight() - viewport; if (range > 0) { chatScroll.setVvalue((oldOffset + messageRows.getHeight() - oldHeight) / range); } }
        });
    }

    private void updateControls() {
        boolean enabled = available(); icon.setDisable(!enabled); search.setDisable(!enabled); searchCode.setDisable(!enabled);
        input.setDisable(!enabled || current == null || !initialized || sending.contains(current.id));
        int length = input.getText().strip().codePointCount(0, input.getText().strip().length()); counter.setText(length + "/2000");
        send.setDisable(input.isDisabled() || length == 0 || length > 2000); send.setText(current != null && sending.contains(current.id) ? "Đang gửi…" : "Gửi");
        older.setDisable(!enabled || loadingHistory); peopleRows.setDisable(!enabled); find.setDisable(!enabled);
        updateBadge();
    }
    private void updateBadge() {
        int total = unread + pending; badge.setText(total > 99 ? "99+" : Integer.toString(total)); show(badge, total > 0);
    }
    private boolean available() { return !disposed && connected && model.hasSessionToken() && model.getUserId() == userId && session.equals(model.getSessionToken()) && transport.isConnected(); }
    private boolean valid(long token) { return token == generation && available(); }
    private boolean chatVisible() { return panel.isVisible() && chatMode && current != null; }
    private boolean success(ApiResponse response, Throwable error) {
        if (error == null && response != null && response.isSuccess()) { return true; }
        status.setText(error == null && response != null ? response.getUserMessage() : "Không thể tải dữ liệu. Hệ thống sẽ thử lại khi có kết nối."); return false;
    }
    public void reset() {
        generation++; chatGeneration++; listSequence++; lookupSequence++; loadingList = false; loadingHistory = false;
        session = ""; userId = 0; connected = false; people.clear(); messages.clear(); drafts.clear(); retries.clear(); mutating.clear(); sending.clear();
        current = null; lookup = null; chatMode = false; initialized = false; hasOlder = false; unread = 0; pending = 0; historyCursor = 0; lastList = 0; lastHistory = 0;
        input.clear(); search.clear(); searchCode.clear(); peopleRows.getChildren().clear(); messageRows.getChildren().clear(); status.setText("");
        show(panel, false); show(this, false); updateControls();
    }
    @Override public void close() { reset(); disposed = true; }
    private static void show(javafx.scene.Node node, boolean visible) { node.setVisible(visible); node.setManaged(visible); }
    private static Button action(String title, String id, Runnable run) { var button = new Button(title); button.setId(id); button.getStyleClass().add("friends-row-action"); button.setOnAction(event -> run.run()); return button; }
    private static Person person(Map<String, String> data, String prefix) { return new Person(ClientWireParser.longValue(data, prefix + "userId", 0), data.getOrDefault(prefix + "username", ""), data.getOrDefault(prefix + "displayName", ""), data.getOrDefault(prefix + "friendCode", ""), data.getOrDefault(prefix + "state", "NONE"), Boolean.parseBoolean(data.get(prefix + "online")), ClientWireParser.integer(data, prefix + "unread", 0)); }
    private static Message message(Map<String, String> data, String prefix) { return new Message(ClientWireParser.longValue(data, prefix + "id", 0), ClientWireParser.longValue(data, prefix + "senderId", 0), data.getOrDefault(prefix + "body", ""), Instant.parse(data.get(prefix + "sentAt"))); }
}
