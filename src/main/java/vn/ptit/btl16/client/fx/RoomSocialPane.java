package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.fx.DesktopTheme;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class RoomSocialPane extends VBox {
    private record Person(long id, String name, boolean host, long version, boolean self, boolean leading, boolean canKick, String kickReason, Instant mutedUntil, String muteReason) {
        @Override public String toString() { return name + (host ? " (Ch\u1ee7 ph\u00f2ng)" : "")
                + (self ? " (B\u1ea1n)" : "") + (leading ? " (\u0110ang d\u1eabn \u0111\u1ea7u)" : ""); }
    }
    private record Chat(long id, Person person, String body, Instant time, String replyName, String replyBody) { }
    private record Reaction(int count, boolean mine) { }
    private static final Map<String, String> EMOJI = new LinkedHashMap<>();
    static { EMOJI.put("LIKE", "\ud83d\udc4d"); EMOJI.put("HEART", "\u2764"); EMOJI.put("LAUGH", "\ud83d\ude04"); EMOJI.put("CLAP", "\ud83d\udc4f"); }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private final ClientTransport transport;
    private final RoomSocialApi api;
    private final Label privacy = DesktopTheme.label("", "social-privacy");
    private final VBox chatRows = new VBox(12);
    private final VBox memberRows = new VBox(10);
    private final Label memberStatus = new Label();
    private final RoomProgressPane progress;
    private final ScrollPane scroll = new ScrollPane(chatRows);
    private final TextField input = new TextField();
    private final Button send = new Button("G\u1eedi");
    private final Button unread = new Button();
    private final Label feedback = DesktopTheme.label("", "muted");
    private final Label counter = DesktopTheme.label("0/500", "muted");
    private final Label announcement = DesktopTheme.label("", "room-announcement-text");
    private final Button editAnnouncement = new Button("Ghim / S\u1eeda th\u00f4ng b\u00e1o");
    private final VBox announcementBox = new VBox(5, announcement, editAnnouncement);
    private final Label muteStatus = DesktopTheme.label("", "muted");
    private final Label replyPreview = DesktopTheme.label("", "muted");
    private final Button cancelReply = new Button("H\u1ee7y tr\u1ea3 l\u1eddi");
    private final HBox replyBox = new HBox(8, replyPreview, cancelReply);
    private final Label interactionFeedback = DesktopTheme.label("", "muted");
    private final Button retryInteraction = new Button("Th\u1eed l\u1ea1i");
    private final Label chatTitle = DesktopTheme.label("Trò chuyện (0)", "section-title");
    private final TitledPane memberPane = new TitledPane();
    private final VBox auctionContent = new VBox(16);
    private int memberTotal;
    private final TreeMap<Long, Chat> messages = new TreeMap<>();
    private final Map<Long, Map<String, Reaction>> reactions = new HashMap<>();
    private final Map<Long, HBox> reactionBars = new HashMap<>();
    private final List<Button> chatActions = new ArrayList<>();
    private final List<Button> hostActions = new ArrayList<>();
    private final Set<Long> observedAfterCursor = new HashSet<>();
    private final Map<Long, ApiResponse> profiles = new HashMap<>();
    private final Set<Long> pendingProfiles = new HashSet<>();
    private final Set<Long> unavailableProfiles = new HashSet<>();
    private List<Person> members = List.of();
    private ClientAuction auction;
    private long actualUserId;
    private long generation;
    private long lastRefresh;
    private boolean loading, sending, serverCanChat;
    private boolean initialized, serverCanInteract, mutating, refreshAgain, suppressRead;
    private long chatCursor, interactionRevision = -1, replyToId, retryReplyId;
    private Instant mutedUntil;
    private String muteReason = "", announcementBody = "";
    private java.util.function.Supplier<java.util.concurrent.CompletableFuture<ApiResponse>> failedInteraction;
    private String retryBody = "", retryNonce = "";
    private java.util.function.BiConsumer<ClientSounds.Cue, String> sound = (cue, key) -> { };
    private java.util.function.BiConsumer<Long, String> friendAction;
    public void onFriend(java.util.function.BiConsumer<Long, String> action) { friendAction = action; }
    private int unreadCount;
    private javafx.stage.Window owner;

    public void setOwner(javafx.stage.Window owner) { this.owner = owner; }

    public RoomSocialPane(ClientTransport transport) {
        this.transport = transport; this.api = new RoomSocialApi(transport);
        this.progress = new RoomProgressPane(transport);
        progress.setFlowLayout(true);
        getStyleClass().addAll("card", "room-social"); setId("room-social");
        setMinHeight(Region.USE_PREF_SIZE);
        chatTitle.setId("room-chat-title");
        privacy.setWrapText(true); feedback.setWrapText(true);
        scroll.setFitToWidth(true); scroll.setPrefHeight(145); scroll.setMinHeight(100); scroll.setMaxHeight(145);
        scroll.getStyleClass().add("social-scroll"); chatRows.setFillWidth(true);
        input.setPromptText("Tr\u00f2 chuy\u1ec7n c\u00f9ng m\u1ecdi ng\u01b0\u1eddi..."); input.setId("room-chat-input");
        send.setId("room-chat-send"); send.getStyleClass().add("primary");
        HBox composer = new HBox(8, input, send); HBox.setHgrow(input, Priority.ALWAYS);
        unread.getStyleClass().add("ghost"); unread.setVisible(false); unread.setManaged(false);
        unread.setOnAction(event -> { memberPane.setExpanded(false); scroll.setVvalue(1); resetUnread(); });
        scroll.vvalueProperty().addListener((observable, previous, current) -> { if (!suppressRead && viewingBottom()) { resetUnread(); } });
        input.textProperty().addListener((observable, previous, current) -> { counter.setText(current.codePointCount(0, current.length()) + "/500"); updateInput(); });
        input.setOnAction(event -> send()); send.setOnAction(event -> send());
        replyPreview.setWrapText(true); HBox.setHgrow(replyPreview, Priority.ALWAYS);
        replyBox.setId("room-chat-reply-preview"); cancelReply.setId("room-chat-cancel-reply"); cancelReply.setOnAction(event -> cancelReply()); show(replyBox, false);
        VBox chat = new VBox(8, scroll, unread, replyBox, composer, new HBox(10, counter, feedback));
        chat.setMinHeight(0); VBox.setVgrow(scroll, Priority.ALWAYS);
        memberStatus.setId("room-members-status"); memberStatus.setWrapText(true);
        Button retryMembers = new Button("T\u1ea3i l\u1ea1i"); retryMembers.setOnAction(event -> refresh());
        VBox roster = new VBox(8, memberStatus, retryMembers, memberRows);
        memberPane.setContent(roster); memberPane.setText("Người trong phòng"); memberPane.setExpanded(false);
        memberPane.setId("room-chat-members");
        memberPane.expandedProperty().addListener((observable, before, after) -> { if (!after && viewingBottom()) { resetUnread(); } });
        announcement.setWrapText(true); announcement.setId("room-announcement"); announcementBox.getStyleClass().add("room-announcement");
        editAnnouncement.setId("room-announcement-edit"); editAnnouncement.getStyleClass().add("ghost"); editAnnouncement.setOnAction(event -> editAnnouncement());
        muteStatus.setWrapText(true); muteStatus.setId("room-chat-mute-status"); interactionFeedback.setWrapText(true); interactionFeedback.setId("room-interaction-status");
        retryInteraction.setId("room-interaction-retry"); retryInteraction.setOnAction(event -> { if (failedInteraction != null) { mutate(failedInteraction); } }); show(retryInteraction, false);
        getChildren().setAll(chatTitle, privacy, announcementBox, muteStatus,
                new HBox(8, interactionFeedback, retryInteraction), chat, memberPane);
        setAuctionPanels(null, null);
        updateInput();
    }

    public VBox auctionContent() { return auctionContent; }
    public void focusBid(vn.ptit.btl16.client.model.ClientBid bid) { progress.focusBid(bid); }
    public void onEventFocus(java.util.function.Consumer<Node> action) { progress.onReveal(action); }

    public void setAuctionPanels(Node insights, Node details) {
        auctionContent.setMinWidth(0);
        auctionContent.getChildren().clear();
        if (insights != null) { auctionContent.getChildren().add(insights); }
        addPanel("Nhật ký phiên", progress);
        if (details != null) { addPanel("Chi tiết sản phẩm", details); }
    }

    private void addPanel(String title, Node content) {
        VBox panel = new VBox(12, DesktopTheme.label(title, "section-title"), content);
        panel.getStyleClass().add("card"); panel.setMinHeight(Region.USE_PREF_SIZE);
        panel.setMinWidth(0);
        auctionContent.getChildren().add(panel);
    }

    public void setRoom(ClientAuction value, long userId) {
        if (value == null) { clear(); return; }
        boolean changed = auction == null || auction.getAuctionId() != value.getAuctionId();
        if (changed) { clear(); }
        auction = value; actualUserId = userId;
        privacy.setText(value.hasAnonymousGuests()
                ? "Kh\u00e1ch \u1ea9n danh \u00b7 Avatar tr\u1ed1ng, ch\u1ec9 hi\u1ec7n s\u1ed1 th\u1ee9 t\u1ef1. Kh\u00f4ng chia s\u1ebb th\u00f4ng tin c\u00e1 nh\u00e2n trong chat."
                : "Kh\u00e1ch th\u01b0\u1eddng \u00b7 B\u1ea5m avatar \u0111\u1ec3 xem h\u1ed3 s\u01a1 c\u00f4ng khai.");
        progress.setRoom(value.getAuctionId());
        updateInput(); if (changed) { refresh(); }
    }

    public void onStatistics(java.util.function.Consumer<Map<String, String>> consumer) { progress.onStatistics(consumer); }
    public void invalidateRequests() {
        generation++; loading = false; sending = false; mutating = false; serverCanChat = false; serverCanInteract = false; lastRefresh = 0;
        pendingProfiles.clear(); progress.invalidateRequests();
        if (failedInteraction != null) { interactionFeedback.setText("M\u1ea5t k\u1ebft n\u1ed1i; ch\u01b0a x\u00e1c nh\u1eadn. K\u1ebft n\u1ed1i l\u1ea1i \u0111\u1ec3 th\u1eed l\u1ea1i."); show(retryInteraction, true); }
        updateInput();
    }

    public void clear() {
        progress.clear(); memberStatus.setText("");
        generation++; auction = null; messages.clear(); members = List.of(); profiles.clear();
        pendingProfiles.clear(); unavailableProfiles.clear(); loading = false; sending = false;
        serverCanChat = false; lastRefresh = 0; retryBody = ""; retryNonce = "";
        initialized = false; serverCanInteract = false; mutating = false; refreshAgain = false; chatCursor = 0; interactionRevision = -1;
        reactions.clear(); reactionBars.clear(); chatActions.clear(); hostActions.clear(); observedAfterCursor.clear(); suppressRead = false; mutedUntil = null; muteReason = ""; announcementBody = ""; announcement.setText("");
        failedInteraction = null; interactionFeedback.setText(""); show(retryInteraction, false); cancelReply();
        input.clear(); chatRows.getChildren().clear(); memberRows.getChildren().clear(); feedback.setText(""); resetUnread(); updateInput();
        memberTotal = 0; updateUnread(); memberPane.setExpanded(false);
    }

    public void tick() {
        progress.tick();
        if (unreadCount > 0 && viewingBottom()) { resetUnread(); }
        memberRows.setDisable(!transport.isConnected() || loading);
        if (auction != null && !transport.isConnected()) { memberStatus.setText("M\u1ea5t k\u1ebft n\u1ed1i \u00b7 Danh s\u00e1ch c\u00f3 th\u1ec3 \u0111\u00e3 c\u0169"); }
        updateInput();
        if (auction != null && transport.isConnected() && System.nanoTime() - lastRefresh > 3_000_000_000L) { refresh(); }
    }

    private void refresh() {
        if (auction == null || loading || !transport.isConnected()) { return; }
        loading = true; lastRefresh = System.nanoTime(); long token = generation;
        memberStatus.setText("\u0110ang t\u1ea3i ng\u01b0\u1eddi tham gia...");
        api.social(auction.getAuctionId(), initialized ? chatCursor : -1, messages.keySet()).whenComplete((response, error) -> Platform.runLater(() -> {
            if (generation != token || auction == null) { return; }
            loading = false;
            if (!transport.isConnected() || !ok(response, error)) { serverCanChat = false; serverCanInteract = false; memberStatus.setText("Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c danh s\u00e1ch. B\u1ea5m T\u1ea3i l\u1ea1i; d\u1eef li\u1ec7u hi\u1ec7n c\u00f3 c\u00f3 th\u1ec3 \u0111\u00e3 c\u0169."); updateInput(); return; }
            var data = response.getWireMessage().getData();
            int totalMembers = ClientWireParser.integer(data, "memberTotal", ClientWireParser.integer(data, "memberCount", 0));
            memberTotal = totalMembers; updateUnread();
            memberStatus.setText(totalMembers + " t\u00e0i kho\u1ea3n \u0111ang trong ph\u00f2ng" + (totalMembers > 200 ? " \u00b7 Ch\u1ec9 hi\u1ec3n th\u1ecb 200 t\u00e0i kho\u1ea3n \u0111\u1ea7u" : ""));
            applyInteraction(data);
            List<Person> next = new ArrayList<>();
            for (int index = 0; index < ClientWireParser.integer(data, "memberCount", 0); index++) { next.add(person(data, "member." + index + ".")); }
            boolean profileChanged = false;
            for (Person person : next) {
                ApiResponse cached = profiles.get(person.id());
                if (cached != null && ClientWireParser.longValue(cached.getWireMessage().getData(), "profileVersion", 0) != person.version()) { profiles.remove(person.id()); profileChanged = true; }
                unavailableProfiles.remove(person.id());
            }
            boolean changed = profileChanged || !members.equals(next); members = List.copyOf(next);
            for (int index = 0; index < ClientWireParser.integer(data, "chatCount", 0); index++) { changed |= addChat(data, "chat." + index + ".", initialized); }
            initialized = true; chatCursor = Math.max(chatCursor, ClientWireParser.longValue(data, "nextChatId", chatCursor));
            observedAfterCursor.removeIf(id -> id <= chatCursor); reactions.keySet().retainAll(messages.keySet());
            Set<Long> retainedProfiles = new HashSet<>(); members.forEach(person -> retainedProfiles.add(person.id()));
            messages.values().forEach(message -> retainedProfiles.add(message.person().id()));
            profiles.keySet().retainAll(retainedProfiles); unavailableProfiles.retainAll(retainedProfiles);
            if (changed || chatRows.getChildren().isEmpty()) { render(); }
            updateInput();
            if (Boolean.parseBoolean(data.get("hasMoreChat")) || refreshAgain) { refreshAgain = false; refresh(); }
        }));
    }

    public void onSound(java.util.function.BiConsumer<ClientSounds.Cue, String> listener) { sound = Objects.requireNonNull(listener); }

    public void onMessage(Map<String, String> data) {
        if (auction == null || ClientWireParser.longValue(data, "auctionId", 0) != auction.getAuctionId()) { return; }
        if (addChat(data, "chat.", true)) {
            render();
        }
    }

    private boolean addChat(Map<String, String> data, String prefix, boolean notify) {
        long id = ClientWireParser.longValue(data, prefix + "id", 0);
        if (id == 0 || messages.containsKey(id)) { return false; }
        Person sender = person(data, prefix);
        boolean unseen = id > chatCursor && observedAfterCursor.add(id);
        if (notify && unseen && auction != null && transport.isConnected() && auction.isOpen()) {
            boolean own = sender.id() == auction.viewerId(actualUserId);
            if (own || !viewingBottom()) {
                sound.accept(own ? ClientSounds.Cue.CHAT_SENT : ClientSounds.Cue.CHAT_RECEIVED, "chat:" + auction.getAuctionId() + ":" + id);
            }
        }
        if (notify && unseen && !viewingBottom() && auction != null && sender.id() != auction.viewerId(actualUserId)) { unreadCount++; updateUnread(); }
        messages.put(id, new Chat(id, sender, data.getOrDefault(prefix + "body", ""), ClientWireParser.instant(data, prefix + "sentAt"), data.getOrDefault(prefix + "reply.name", ""), data.getOrDefault(prefix + "reply.body", "")));
        while (messages.size() > 200) { messages.pollFirstEntry(); }
        return true;
    }

    private Person person(Map<String, String> data, String prefix) {
        return new Person(ClientWireParser.longValue(data, prefix + "participantId", 0), data.getOrDefault(prefix + "name", ""),
                Boolean.parseBoolean(data.get(prefix + "host")), ClientWireParser.longValue(data, prefix + "profileVersion", 0),
                Boolean.parseBoolean(data.get(prefix + "self")), Boolean.parseBoolean(data.get(prefix + "leading")),
                Boolean.parseBoolean(data.get(prefix + "canKick")), data.getOrDefault(prefix + "kickReason", ""), ClientWireParser.instant(data, prefix + "mute.until"), data.getOrDefault(prefix + "mute.reason", ""));
    }

    private void render() {
        boolean bottom = scroll.getVvalue() > .95 || chatRows.getChildren().isEmpty();
        double previousScroll = scroll.getVvalue();
        suppressRead = true;
        chatRows.getChildren().clear(); memberRows.getChildren().clear(); reactionBars.clear(); chatActions.clear(); hostActions.clear();
        if (messages.isEmpty()) { chatRows.getChildren().add(DesktopTheme.label("Ch\u01b0a c\u00f3 tin nh\u1eafn. H\u00e3y b\u1eaft \u0111\u1ea7u cu\u1ed9c tr\u00f2 chuy\u1ec7n!", "muted")); }
        for (Chat message : messages.values()) {
            Label name = DesktopTheme.label(message.person().toString(), "social-name"); name.setWrapText(true);
            Label time = DesktopTheme.label(message.time() == null ? "" : TIME.format(message.time()), "muted");
            Label body = new Label(message.body()); body.setWrapText(true); body.setMinWidth(0); body.setMaxWidth(Double.MAX_VALUE);
            VBox content = new VBox(4, new HBox(8, name, time), body); content.setMinWidth(0); HBox.setHgrow(content, Priority.ALWAYS);
            if (!message.replyBody().isEmpty()) {
                Label quote = DesktopTheme.label(message.replyName() + ": " + message.replyBody(), "chat-quote"); quote.setWrapText(true); content.getChildren().add(1, quote);
            }
            Button reply = new Button("Tr\u1ea3 l\u1eddi"); reply.setId("room-reply-" + message.id()); reply.getStyleClass().add("ghost");
            reply.setOnAction(event -> beginReply(message)); chatActions.add(reply);
            HBox bar = new HBox(5); reactionBars.put(message.id(), bar); renderReactions(message.id());
            FlowPane actions = new FlowPane(5, 5, reply, bar); content.getChildren().add(actions);
            HBox row = new HBox(9, avatar(message.person()), content); row.setAlignment(Pos.TOP_LEFT); row.getStyleClass().add("social-message");
            if (auction != null && message.person().id() == auction.viewerId(actualUserId)) { row.getStyleClass().add("own"); }
            chatRows.getChildren().add(row);
        }
        memberPane.setText("Người trong phòng (" + memberTotal + ")");
        for (Person person : members) {
            Label name = new Label(person.toString()); name.setWrapText(true); HBox.setHgrow(name, Priority.ALWAYS);
            HBox row = new HBox(10, avatar(person), name); row.setAlignment(Pos.CENTER_LEFT);
            if (auction != null && auction.isHostedBy(actualUserId)) {
                Button kick = new Button("M\u1eddi ra"); kick.getStyleClass().add("ghost"); kick.setOnAction(event -> kick(person));
                kick.setDisable(!person.canKick() || !transport.isConnected());
                Label reason = new Label(kickReason(person)); reason.setWrapText(true); reason.getStyleClass().add("muted");
                row.getChildren().add(new VBox(3, kick, reason));
                if (!person.host()) {
                    Button mute = new Button(person.mutedUntil() != null && Instant.now().isBefore(person.mutedUntil()) ? "B\u1ecf t\u1eaft ti\u1ebfng" : "T\u1eaft ti\u1ebfng");
                    mute.setId("room-mute-" + person.id()); mute.getStyleClass().add("ghost"); mute.setOnAction(event -> editMute(person));
                    mute.setDisable(!canInteract() || mutating);
                    hostActions.add(mute);
                    Label note = new Label(person.mutedUntil() == null ? "" : "\u0110\u1ebfn " + TIME.format(person.mutedUntil()) + ": " + person.muteReason()); note.setWrapText(true);
                    row.getChildren().add(new VBox(3, mute, note));
                }
            }
            memberRows.getChildren().add(row);
        }
        updateInput();
        long token = generation; Platform.runLater(() -> { if (token == generation) { scroll.setVvalue(bottom ? 1 : previousScroll); suppressRead = false; if (bottom && viewingBottom()) { resetUnread(); } } });
    }

    private Node avatar(Person person) {
        Button avatar = new Button(); avatar.getStyleClass().add("social-avatar"); avatar.setAccessibleText(person.name());
        boolean anonymous = auction == null || auction.hasAnonymousGuests();
        if (!anonymous) {
            ApiResponse profile = profiles.get(person.id());
            Image image = profile == null ? null : image(profile.get("avatarBase64"));
            if (image != null) {
                ImageView view = new ImageView(image); view.setFitWidth(34); view.setFitHeight(34); view.setClip(new Circle(17, 17, 17)); avatar.setGraphic(view);
            } else { avatar.setText(person.name().isBlank() ? "?" : person.name().substring(0, person.name().offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT)); }
            avatar.setOnAction(event -> openProfile(person.id())); fetchAvatar(person.id());
        } else { avatar.setDisable(true); avatar.getStyleClass().add("anonymous"); }
        return avatar;
    }

    private void fetchAvatar(long id) {
        if (auction == null || auction.hasAnonymousGuests() || profiles.containsKey(id) || unavailableProfiles.contains(id) || !pendingProfiles.add(id)) { return; }
        long token = generation;
        api.profile(auction.getAuctionId(), id).whenComplete((response, error) -> Platform.runLater(() -> {
            if (token != generation || auction == null || auction.hasAnonymousGuests()) { return; }
            pendingProfiles.remove(id);
            if (error == null && response != null && response.isSuccess()) { profiles.put(id, response); render(); }
            else { unavailableProfiles.add(id); }
        }));
    }

    public void openProfile(long id) {
        if (auction == null || auction.hasAnonymousGuests()) { return; }
        long token = generation;
        api.profile(auction.getAuctionId(), id).whenComplete((response, error) -> Platform.runLater(() -> {
            if (token != generation || !ok(response, error)) { return; }
            Dialog<Void> dialog = dialog("H\u1ed3 s\u01a1 c\u00f4ng khai");
            Image avatar = image(response.get("avatarBase64")); ImageView view = new ImageView(avatar); view.setFitWidth(80); view.setFitHeight(80);
            Label name = DesktopTheme.label(response.get("displayName") + (response.get("friendCode").isBlank() ? "" : " #" + response.get("friendCode")), "detail-title"); name.setWrapText(true);
            Label bio = new Label(response.get("biography").isBlank() ? "Ch\u01b0a c\u00f3 gi\u1edbi thi\u1ec7u." : response.get("biography")); bio.setWrapText(true);
            Label joined = new Label("Tham gia: " + response.get("joinedAt").split("T")[0]);
            Label note = DesktopTheme.label("Email, s\u1ed1 \u0111i\u1ec7n tho\u1ea1i v\u00e0 v\u00ed kh\u00f4ng \u0111\u01b0\u1ee3c chia s\u1ebb.", "muted"); note.setWrapText(true);
            VBox content = new VBox(12, view, name, bio, joined, note);
            if (friendAction != null && id != actualUserId) {
                Button friend = new Button("Kết bạn / Nhắn tin"); friend.setId("room-profile-friend"); friend.getStyleClass().add("primary");
                friend.setOnAction(event -> { dialog.close(); if (generation == token) { friendAction.accept(id, response.get("displayName")); } });
                content.getChildren().add(friend);
            }
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); dialog.show();
        }));
    }

    private void send() {
        if (send.isDisabled() || auction == null) { return; }
        String body = input.getText().strip();
        if (!body.equals(retryBody) || retryNonce.isEmpty() || retryReplyId != replyToId) { retryBody = body; retryReplyId = replyToId; retryNonce = UUID.randomUUID().toString(); }
        sending = true; feedback.setText("\u0110ang g\u1eedi..."); updateInput(); long token = generation;
        api.send(auction.getAuctionId(), body, retryNonce, replyToId).whenComplete((response, error) -> Platform.runLater(() -> {
            if (token != generation) { return; }
            sending = false;
            if (transport.isConnected() && ok(response, error)) { onMessage(response.getWireMessage().getData()); input.clear(); cancelReply(); retryBody = ""; retryNonce = ""; feedback.setText(""); }
            else {
                sound.accept(error == null && response != null && !response.isSuccess() ? ClientSounds.Cue.ERROR : ClientSounds.Cue.WARNING, "chat-error:" + retryNonce);
                feedback.setText((error == null && response != null && !response.isSuccess() ? response.getMessageText() : "Ch\u01b0a x\u00e1c nh\u1eadn tin \u0111\u00e3 g\u1eedi.") + " B\u1ea5m Th\u1eed l\u1ea1i \u0111\u1ec3 g\u1eedi l\u1ea1i an to\u00e0n.");
            }
            updateInput();
        }));
    }

    private void updateInput() {
        boolean muted = mutedUntil != null && Instant.now().isBefore(mutedUntil);
        boolean writable = canInteract() && serverCanChat && !muted;
        input.setDisable(!writable || sending);
        String body = input.getText().strip();
        send.setDisable(!writable || sending || body.isBlank() || body.codePointCount(0, body.length()) > 500);
        send.setText(sending ? "\u0110ang g\u1eedi..." : !retryNonce.isEmpty() && body.equals(retryBody) && replyToId == retryReplyId ? "Th\u1eed l\u1ea1i" : "G\u1eedi");
        chatActions.forEach(button -> button.setDisable(!writable || mutating || sending));
        cancelReply.setDisable(sending);
        hostActions.forEach(button -> button.setDisable(!canInteract() || mutating));
        reactionBars.values().forEach(bar -> bar.setDisable(!writable || mutating));
        editAnnouncement.setDisable(!canInteract() || mutating);
        show(editAnnouncement, auction != null && auction.isHostedBy(actualUserId));
        show(announcementBox, !announcementBody.isBlank() || (auction != null && auction.isHostedBy(actualUserId)));
        retryInteraction.setDisable(!canInteract() || mutating);
        muteStatus.setText(muted ? "Chat b\u1ecb t\u1eaft ti\u1ebfng \u0111\u1ebfn " + DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault()).format(mutedUntil) + ": " + muteReason + " \u00b7 V\u1eabn \u0111\u01b0\u1ee3c \u0111\u1eb7t gi\u00e1." : "");
        show(muteStatus, muted);
        if (auction != null && !transport.isConnected()) { feedback.setText("M\u1ea5t k\u1ebft n\u1ed1i \u00b7 Ch\u01b0a x\u00e1c nh\u1eadn thao t\u00e1c m\u1edbi"); }
        if (auction != null && (!auction.isOpen() || (auction.getEndTime() != null && !Instant.now().isBefore(auction.getEndTime())))) { feedback.setText("Phi\u00ean \u0111\u00e3 k\u1ebft th\u00fac \u00b7 Ch\u1ec9 \u0111\u1ecdc"); }
    }

    private boolean canInteract() { return auction != null && auction.isOpen() && auction.getEndTime() != null && Instant.now().isBefore(auction.getEndTime()) && serverCanInteract && transport.isConnected(); }

    private void beginReply(Chat message) {
        if (!canInteract()) { return; }
        replyToId = message.id(); replyPreview.setText(message.person().name() + ": " + shorten(message.body(), 100)); show(replyBox, true); input.requestFocus();
    }
    private void cancelReply() { replyToId = 0; replyPreview.setText(""); show(replyBox, false); }

    public void onInteraction(Map<String, String> data) {
        if (auction == null || ClientWireParser.longValue(data, "auctionId", 0) != auction.getAuctionId()) { return; }
        if (loading) { refreshAgain = true; } else { refresh(); }
    }

    private void applyInteraction(Map<String, String> data) {
        long revision = ClientWireParser.longValue(data, "interactionRevision", 0);
        if (revision < interactionRevision) { return; }
        interactionRevision = revision;
        serverCanInteract = Boolean.parseBoolean(data.get("canInteract")); serverCanChat = serverCanInteract;
        mutedUntil = ClientWireParser.instant(data, "selfMute.until"); muteReason = data.getOrDefault("selfMute.reason", "");
        announcementBody = data.getOrDefault("announcement.body", "");
        announcement.setText(announcementBody.isBlank() ? "" : "\ud83d\udccc " + shorten(announcementBody, 180));
        announcement.setTooltip(announcementBody.isBlank() ? null : new Tooltip(announcementBody));
        for (int index = 0; index < ClientWireParser.integer(data, "reactionCount", 0); index++) {
            String prefix = "reaction." + index + "."; long id = ClientWireParser.longValue(data, prefix + "chatId", 0);
            Map<String, Reaction> next = new HashMap<>();
            for (String emoji : EMOJI.keySet()) { next.put(emoji, new Reaction(ClientWireParser.integer(data, prefix + emoji + ".count", 0), Boolean.parseBoolean(data.get(prefix + emoji + ".mine")))); }
            if (!next.equals(reactions.put(id, next))) { renderReactions(id); }
        }
        updateInput();
    }

    private void renderReactions(long id) {
        HBox bar = reactionBars.get(id); if (bar == null) { return; } bar.getChildren().clear();
        for (String emoji : EMOJI.keySet()) {
            Reaction state = reactions.getOrDefault(id, Map.of()).getOrDefault(emoji, new Reaction(0, false));
            Button button = new Button(EMOJI.get(emoji) + " " + state.count()); button.setId("room-reaction-" + id + "-" + emoji);
            button.getStyleClass().add(state.mine() ? "reaction-selected" : "ghost"); button.setAccessibleText(emoji + " " + state.count() + (state.mine() ? " selected" : ""));
            button.setOnAction(event -> { if (auction != null) { long room = auction.getAuctionId(); String nonce = UUID.randomUUID().toString(); mutate(() -> api.reaction(room, id, emoji, !state.mine(), nonce)); } });
            bar.getChildren().add(button);
        }
    }

    private void mutate(java.util.function.Supplier<java.util.concurrent.CompletableFuture<ApiResponse>> operation) {
        if (!canInteract() || mutating) { return; }
        mutating = true; failedInteraction = operation; interactionFeedback.setText("\u0110ang g\u1eedi thao t\u00e1c..."); show(retryInteraction, false); updateInput(); long token = generation;
        operation.get().whenComplete((response, error) -> Platform.runLater(() -> {
            if (token != generation || auction == null) { return; }
            mutating = false;
            if (transport.isConnected() && error == null && response != null && response.isSuccess()) {
                applyInteraction(response.getWireMessage().getData()); failedInteraction = null; interactionFeedback.setText("\u0110\u00e3 x\u00e1c nh\u1eadn"); show(retryInteraction, false); onInteraction(response.getWireMessage().getData());
                sound.accept(ClientSounds.Cue.SUCCESS, "interaction:" + auction.getAuctionId() + ":" + interactionRevision);
            } else {
                interactionFeedback.setText(error == null && response != null && !response.isSuccess() ? response.getMessageText() : "Ch\u01b0a x\u00e1c nh\u1eadn thao t\u00e1c. Th\u1eed l\u1ea1i an to\u00e0n v\u1edbi c\u00f9ng m\u00e3 g\u1eedi."); show(retryInteraction, true);
                sound.accept(ClientSounds.Cue.ERROR, "interaction-error:" + generation + ":" + interactionRevision);
            }
            updateInput();
        }));
    }

    private void editAnnouncement() {
        if (auction == null || !auction.isHostedBy(actualUserId) || !canInteract()) { return; }
        long room = auction.getAuctionId(), token = generation;
        Dialog<String> dialog = new Dialog<>(); dialog.setTitle("Th\u00f4ng b\u00e1o ph\u00f2ng"); DesktopTheme.dialog(dialog, getScene() == null ? owner : getScene().getWindow());
        TextArea text = new TextArea(announcementBody); text.setId("room-announcement-body"); text.setWrapText(true); text.setPrefRowCount(3);
        dialog.getDialogPane().setContent(new VBox(8, new Label("T\u1ed1i \u0111a 500 k\u00fd t\u1ef1; \u0111\u1ec3 tr\u1ed1ng \u0111\u1ec3 b\u1ecf ghim."), text));
        ButtonType save = new ButtonType("L\u01b0u", ButtonBar.ButtonData.OK_DONE), remove = new ButtonType("B\u1ecf ghim", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(save, remove, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == save ? text.getText().strip() : button == remove ? "" : null);
        dialog.showAndWait().ifPresent(body -> { if (token == generation) { String nonce = UUID.randomUUID().toString(); mutate(() -> api.announcement(room, body, nonce)); } });
    }

    private void editMute(Person person) {
        if (auction == null || !auction.isHostedBy(actualUserId) || !canInteract()) { return; }
        long room = auction.getAuctionId(), token = generation;
        if (person.mutedUntil() != null && Instant.now().isBefore(person.mutedUntil())) {
            String nonce = UUID.randomUUID().toString(); mutate(() -> api.mute(room, person.id(), 0, "", nonce)); return;
        }
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("T\u1eaft ti\u1ebfng " + person.name()); DesktopTheme.dialog(dialog, getScene() == null ? owner : getScene().getWindow());
        TextField reason = new TextField(); reason.setPromptText("L\u00fd do (1-250 k\u00fd t\u1ef1)"); reason.setId("room-mute-reason");
        Spinner<Integer> minutes = new Spinner<>(1, 1440, 5); minutes.setId("room-mute-minutes");
        dialog.getDialogPane().setContent(new VBox(8, new Label("S\u1ed1 ph\u00fat t\u1eaft ti\u1ebfng; kh\u00f4ng ch\u1eb7n \u0111\u1eb7t gi\u00e1."), minutes, reason));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> {
            if (token == generation) { String nonce = UUID.randomUUID().toString(), body = reason.getText().strip(); int seconds = minutes.getValue() * 60; mutate(() -> api.mute(room, person.id(), seconds, body, nonce)); }
        });
    }

    private static void show(Node node, boolean visible) { node.setVisible(visible); node.setManaged(visible); }
    private static String shorten(String text, int limit) { return text.codePointCount(0, text.length()) <= limit ? text : text.substring(0, text.offsetByCodePoints(0, limit)) + "..."; }

    public void chooseKick() {
        if (auction == null || !auction.isHostedBy(actualUserId)) { return; }
        List<Person> guests = members.stream().filter(Person::canKick).toList();
        if (guests.isEmpty()) { feedback.setText("Ch\u01b0a c\u00f3 kh\u00e1ch \u0111\u1ec3 m\u1eddi ra."); return; }
        ChoiceDialog<Person> dialog = new ChoiceDialog<>(guests.get(0), guests); dialog.setTitle("M\u1eddi kh\u00e1ch ra kh\u1ecfi ph\u00f2ng");
        dialog.setHeaderText("Ng\u01b0\u1eddi \u0111\u00e3 \u0111\u1eb7t gi\u00e1 kh\u00f4ng th\u1ec3 b\u1ecb m\u1eddi ra.");
        if (getScene() != null) { dialog.initOwner(getScene().getWindow()); }
        dialog.showAndWait().ifPresent(this::kick);
    }

    private void kick(Person person) {
        if (auction == null || !person.canKick() || !transport.isConnected()) { return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "M\u1eddi " + person.name() + " ra kh\u1ecfi ph\u00f2ng?", ButtonType.OK, ButtonType.CANCEL);
        if (getScene() != null) { confirm.initOwner(getScene().getWindow()); }
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK || auction == null) { return; }
        long token = generation;
        api.kick(auction.getAuctionId(), person.id()).whenComplete((response, error) -> Platform.runLater(() -> {
            if (token != generation) { return; } if (ok(response, error)) { lastRefresh = 0; tick(); }
        }));
    }

    private static String kickReason(Person person) {
        return switch (person.kickReason()) {
            case "HAS_VALID_BID" -> "\u0110\u00e3 c\u00f3 bid h\u1ee3p l\u1ec7, kh\u00f4ng th\u1ec3 m\u1eddi ra";
            case "HOST" -> "Kh\u00f4ng th\u1ec3 m\u1eddi ch\u1ee7 ph\u00f2ng ra";
            case "CLOSED" -> "Phi\u00ean \u0111\u00e3 \u0111\u00f3ng";
            case "NOT_HOST" -> "Ch\u1ec9 ch\u1ee7 ph\u00f2ng \u0111\u01b0\u1ee3c m\u1eddi ra";
            default -> "";
        };
    }

    public void editOwnProfile() {
        long token = generation;
        api.ownProfile().whenComplete((response, error) -> Platform.runLater(() -> {
            if (token != generation || !ok(response, error)) { return; }
            Dialog<Void> dialog = dialog("Avatar v\u00e0 gi\u1edbi thi\u1ec7u c\u00f4ng khai");
            TextArea biography = new TextArea(response.get("biography")); biography.setWrapText(true); biography.setPrefRowCount(4);
            ImageView preview = new ImageView(image(response.get("avatarBase64"))); preview.setFitWidth(80); preview.setFitHeight(80);
            String[] selected = { null }; Label errorLabel = new Label(); errorLabel.setWrapText(true);
            Button upload = new Button("Ch\u1ecdn \u1ea3nh PNG/JPEG"); Button remove = new Button("X\u00f3a avatar");
            upload.setOnAction(event -> {
                FileChooser chooser = new FileChooser(); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG / JPEG", "*.png", "*.jpg", "*.jpeg"));
                var file = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow()); if (file == null) { return; }
                try {
                    if (Files.size(file.toPath()) > 750 * 1024) { throw new IllegalArgumentException("Ch\u1ecdn \u1ea3nh kh\u00f4ng qu\u00e1 750 KB."); }
                    selected[0] = Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath())); preview.setImage(image(selected[0])); errorLabel.setText("");
                } catch (Exception exception) { errorLabel.setText(exception.getMessage()); }
            });
            remove.setOnAction(event -> { selected[0] = ""; preview.setImage(null); });
            Label note = new Label("T\u1ed1i \u0111a 500 k\u00fd t\u1ef1. \u1ea2nh \u0111\u01b0\u1ee3c thu nh\u1ecf v\u00e0 lo\u1ea1i b\u1ecf metadata. Kh\u00f4ng hi\u1ec7n trong ph\u00f2ng \u1ea9n danh."); note.setWrapText(true);
            Button publicPreview = new Button("Xem trước hồ sơ người khác thấy");
            publicPreview.setOnAction(event -> {
                Dialog<Void> view = dialog("Hồ sơ công khai - xem trước");
                ImageView avatar = new ImageView(preview.getImage()); avatar.setFitWidth(80); avatar.setFitHeight(80);
                Label bio = new Label(biography.getText()); bio.setWrapText(true);
                Label privacy = new Label("Chỉ có tên hiển thị, ngày tham gia, ảnh đại diện và giới thiệu. Khách ẩn danh chỉ hiện bí danh; không xem hồ sơ."); privacy.setWrapText(true);
                view.getDialogPane().setContent(new VBox(10, avatar, new Label(response.get("displayName")), new Label(response.get("joinedAt")), bio, privacy));
                view.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); view.show();
            });
            dialog.getDialogPane().setContent(new VBox(12, preview, new HBox(8, upload, remove), biography, publicPreview, note, errorLabel));
            ButtonType save = new ButtonType("L\u01b0u", ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
            Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
            saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                event.consume(); saveButton.setDisable(true);
                api.saveProfile(biography.getText(), selected[0]).whenComplete((saved, saveError) -> Platform.runLater(() -> {
                    if (token != generation || !dialog.isShowing()) { return; }
                    saveButton.setDisable(false);
                    if (saveError == null && saved != null && saved.isSuccess()) { profiles.clear(); lastRefresh = 0; dialog.close(); tick(); }
                    else { errorLabel.setText(saveError == null && saved != null ? saved.getMessageText() : "Kh\u00f4ng th\u1ec3 l\u01b0u. H\u00e3y th\u1eed l\u1ea1i."); }
                }));
            }); dialog.show();
        }));
    }

    private Dialog<Void> dialog(String title) {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle(title); dialog.getDialogPane().setPrefWidth(440);
        DesktopTheme.dialog(dialog, getScene() != null ? getScene().getWindow() : owner);
        return dialog;
    }
    private boolean ok(ApiResponse response, Throwable error) {
        if (error == null && response != null && response.isSuccess()) { return true; }
        feedback.setText(error == null && response != null ? response.getMessageText() : "Kh\u00f4ng th\u1ec3 k\u1ebft n\u1ed1i. H\u00e3y th\u1eed l\u1ea1i."); return false;
    }
    private boolean viewingBottom() {
        for (Node ancestor = this; ancestor != null; ancestor = ancestor.getParent()) {
            if (!ancestor.isVisible()) { return false; }
        }
        return !memberPane.isExpanded() && (scroll.getVvalue() > .95 || messages.isEmpty());
    }
    private void updateUnread() { chatTitle.setText("Trò chuyện (" + memberTotal + ")"); unread.setText(unreadCount + " tin nh\u1eafn m\u1edbi \u2193"); show(unread, unreadCount > 0); }
    private void resetUnread() { unreadCount = 0; updateUnread(); }
    private static Image image(String base64) {
        if (base64 == null || base64.isBlank()) { return null; }
        try { Image image = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(base64)), 128, 128, true, true); return image.isError() ? null : image; }
        catch (IllegalArgumentException exception) { return null; }
    }
}
