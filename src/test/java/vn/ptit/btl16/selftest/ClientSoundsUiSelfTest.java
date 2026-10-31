package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.fx.ClientSoundUi;
import vn.ptit.btl16.client.fx.ClientBidVoice;
import vn.ptit.btl16.client.fx.ClientSounds;
import vn.ptit.btl16.client.fx.ClientSounds.Cue;
import vn.ptit.btl16.client.fx.FxClientController;
import vn.ptit.btl16.client.fx.RoomSocialPane;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.network.ConnectionState;
import vn.ptit.btl16.client.network.ConnectionStateListener;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ClientSoundsUiSelfTest {
    private static final AtomicLong CLOCK = new AtomicLong(1_000_000_000L);
    private static final List<Cue> PLAYED = new ArrayList<>();
    private static final List<String> SPOKEN = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.concurrent.atomic.AtomicInteger VOICE_STOPS = new java.util.concurrent.atomic.AtomicInteger();
    private record View(Stage stage, FxClientController controller, ClientAppModel model, Network network, ClientSounds sounds) { }

    public static void main(String[] args) throws Exception {
        System.setProperty("btl16.client.manualConnect", "true");
        Platform.startup(() -> Platform.setImplicitExit(false));
        View view = null;
        try {
            view = fx(() -> {
                ClientAppModel model = new ClientAppModel();
                model.applyIdentity(new ApiResponse(message(MessageType.LOGIN_RESULT,
                        Map.of("success", "true", "userId", "2", "username", "buyer", "sessionToken", "sound-session"))));
                Network network = new Network();
                ClientSounds sounds = new ClientSounds(null, (cue, volume) -> PLAYED.add(cue), CLOCK::get);
                ClientBidVoice voice = new ClientBidVoice(sounds, new ClientBidVoice.Speech() {
                    public boolean available() { return true; }
                    public void speak(String text, java.util.function.DoubleSupplier volume, java.util.function.BooleanSupplier cancelled) { if (!cancelled.getAsBoolean()) { SPOKEN.add(text); } }
                    public void stop() { VOICE_STOPS.incrementAndGet(); }
                }, Platform::runLater);
                Stage stage = new Stage();
                FxClientController controller = new FxClientController(stage, ClientConfig.loadDefault(), model, network,
                        new AccountApi(network), new AuctionApi(network), sounds, voice);
                invoke(controller, "buildScenes"); stage.setScene((Scene) field(controller, "mainScene")); stage.show();
                return new View(stage, controller, model, network, sounds);
            });
            controlsAndOwnership(view);
            presentation(view);
            bids(view);
            narration(view);
            chat(view);
            staleReply(view);
            closingNarration(view);
            countdownAndEvents(view);
            winnerNarration(view);
            fx(() -> {
                for (Cue cue : Cue.values()) {
                    AudioClip clip = new AudioClip(Objects.requireNonNull(ClientSounds.class.getResource(cue.resource())).toExternalForm());
                    clip.play(0); clip.stop();
                }
                return null;
            });
            System.out.println("[PASS] ClientSoundsUiSelfTest: controls/volume/mute, confirmed bid/chat sounds, room-only 30s voice, winner-only congratulations in room/lobby, dedup/resync, countdown/server end, native WAV decode");
        } finally {
            View closing = view;
            fx(() -> {
                if (closing != null) { closing.controller().close(); }
                for (Window window : List.copyOf(Window.getWindows())) { window.hide(); }
                return null;
            });
            Platform.exit();
        }
    }

    private static void presentation(View view) throws Exception {
        fx(() -> { ((MenuButton) view.stage().getScene().lookup("#client-sound-settings")).show(); return null; });
        drain();
        fx(() -> {
            MenuButton menu = (MenuButton) view.stage().getScene().lookup("#client-sound-settings");
            VBox panel = (VBox) ((CustomMenuItem) menu.getItems().get(1)).getContent();
            panel.applyCss(); panel.layout();
            checkboxPresentation(panel, "client-music-enabled");
            checkboxPresentation(panel, "client-bid-voice-enabled");
            Label amount = (Label) panel.lookup(".sound-settings-label");
            javafx.scene.paint.Color foreground = (javafx.scene.paint.Color) amount.getTextFill();
            javafx.scene.paint.Color background = (javafx.scene.paint.Color) panel.getBackground().getFills().getFirst().getFill();
            TestSupport.check((luminance(background) + .05) / (luminance(foreground) + .05) >= 4.5, "sound menu text has readable contrast on its actual popup background");
            snapshot(panel.getScene(), "sound-settings.png");
            menu.hide();
            Scene original = view.stage().getScene();
            ((Button) original.lookup("#auction-rules-navigation")).fire();
            original.getRoot().applyCss(); original.getRoot().layout();
            TestSupport.check(original.lookup("#auction-rules-content") != null && view.stage().getScene() == original, "reading rules stays in the account shell");
            snapshot(original, "auction-rules.png");
            ((Button) original.lookup("#account-marketplace-navigation")).fire();
            return null;
        });
    }

    private static void checkboxPresentation(VBox panel, String id) {
        CheckBox checkbox = (CheckBox) panel.lookup("#" + id);
        boolean original = checkbox.isSelected();
        try {
            if (!checkbox.isSelected()) { checkbox.fire(); }
            panel.applyCss(); panel.layout();
            TestSupport.check(checkbox.isSelected(), id + " selects when clicked");
            var box = (javafx.scene.layout.Region) checkbox.lookup(".box");
            var mark = (javafx.scene.layout.Region) checkbox.lookup(".mark");
            var background = (javafx.scene.paint.Color) box.getBackground().getFills().getLast().getFill();
            var foreground = (javafx.scene.paint.Color) mark.getBackground().getFills().getLast().getFill();
            double contrast = (Math.max(luminance(background), luminance(foreground)) + .05)
                    / (Math.min(luminance(background), luminance(foreground)) + .05);
            TestSupport.check(foreground.getOpacity() > .9 && contrast >= 4.5,
                    id + " selected checkmark contrasts with its box: " + foreground + " on " + background);
            TestSupport.check(mark.getShape() != null && mark.getWidth() > 0 && mark.getHeight() > 0,
                    id + " has a visible checkmark shape");
            checkbox.fire();
            panel.applyCss(); panel.layout();
            TestSupport.check(!checkbox.isSelected(), id + " deselects when clicked again");
            TestSupport.check(mark.getBackground() == null || mark.getBackground().getFills().isEmpty()
                            || ((javafx.scene.paint.Color) mark.getBackground().getFills().getLast().getFill()).getOpacity() == 0,
                    id + " hides its checkmark when deselected");
        } finally {
            if (checkbox.isSelected() != original) { checkbox.fire(); }
            panel.applyCss(); panel.layout();
        }
    }

    private static double luminance(javafx.scene.paint.Color color) {
        double[] channels = { color.getRed(), color.getGreen(), color.getBlue() };
        for (int i = 0; i < channels.length; i++) { channels[i] = channels[i] <= .04045 ? channels[i] / 12.92 : Math.pow((channels[i] + .055) / 1.055, 2.4); }
        return .2126 * channels[0] + .7152 * channels[1] + .0722 * channels[2];
    }

    private static void snapshot(Scene scene, String name) throws Exception {
        scene.getRoot().applyCss(); scene.getRoot().layout();
        var image = scene.snapshot(null);
        var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bitmap.getHeight(); y++) { for (int x = 0; x < bitmap.getWidth(); x++) { bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y)); } }
        var output = java.nio.file.Path.of("target", "desktop-preview"); java.nio.file.Files.createDirectories(output);
        javax.imageio.ImageIO.write(bitmap, "png", output.resolve(name).toFile());
    }

    private static void narration(View view) throws Exception {
        ready(view, "100", 0);
        int before = SPOKEN.size();
        Map<String, String> update = data("1500000", -3);
        update.putAll(Map.of("bid.bidId", "42", "bid.auctionId", "1", "bid.userId", "-3", "bid.username", "Khách #3",
                "bid.amount", "1500000", "bid.serverSequence", "42", "bid.createdAt", Instant.now().toString()));
        fx(() -> {
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.BID_UPDATE, update));
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.BID_UPDATE, update));
            return null;
        });
        TestSupport.waitUntil(() -> SPOKEN.size() == before + 1, 3000, "one narration for a real accepted bid event");
        ClientBidVoiceSelfTest.checkAnnouncement(SPOKEN.get(before), "Khách số 3",
                "wire event uses an uploaded MC sentence with the displayed anonymous alias");
    }

    private static void controlsAndOwnership(View view) throws Exception {
        fx(() -> {
            MenuButton menu = (MenuButton) view.stage().getScene().lookup("#client-sound-settings");
            TestSupport.check(menu != null, "sound menu visible on Client");
            var panel = ((CustomMenuItem) menu.getItems().get(1)).getContent();
            var music = (CheckBox) panel.lookup("#client-music-enabled");
            TestSupport.check(music.isSelected(), "background music enabled in settings");
            music.fire(); TestSupport.check(!view.sounds().isMusicEnabled(), "music can be muted independently");
            TestSupport.check(view.sounds().isEnabled(), "music toggle preserves effects and narration");
            music.fire();
            ((Slider) panel.lookup("#client-music-volume")).setValue(45);
            TestSupport.equals(.45, view.sounds().getMusicVolume(), "music slider updates its separate volume");
            ((Slider) panel.lookup("#client-sound-volume")).setValue(20);
            TestSupport.equals(.2, view.sounds().getVolume(), "volume control updates engine");
            CheckMenuItem enabled = (CheckMenuItem) menu.getItems().get(0);
            enabled.setSelected(false); enabled.fire();
            TestSupport.check(!view.sounds().play(Cue.ERROR), "mute immediately suppresses effects");
            enabled.setSelected(true); enabled.fire();
            ((Button) panel.lookup("#client-sound-preview")).fire();
            TestSupport.check(PLAYED.contains(Cue.SUCCESS), "preview plays only when enabled");
            return null;
        });
        fx(() -> {
            Stage owned = new Stage(); owned.initOwner(view.stage()); Button action = new Button("Action");
            owned.setScene(new Scene(new VBox(action))); owned.show(); action.fire();
            TestSupport.equals(Cue.TAP, PLAYED.get(PLAYED.size() - 1), "owned client window gets neutral feedback"); owned.close();
            int before = PLAYED.size();
            Stage unrelated = new Stage(); Button serverAction = new Button("Unrelated server window");
            unrelated.setScene(new Scene(new VBox(serverAction))); unrelated.show(); serverAction.fire();
            TestSupport.equals(before, PLAYED.size(), "unowned/server windows remain silent"); unrelated.close(); return null;
        });
    }

    private static void ready(View view, String price, long winner) throws Exception {
        fx(() -> {
            view.network().state = ConnectionState.CONNECTED;
            view.network().snapshot = data(price, winner);
            view.model().applySnapshot(ClientWireParser.auction(view.network().snapshot, ""), List.of(), Instant.now(), true);
            invoke(view.controller(), "renderAuctions");
            set(view.controller(), "roomSnapshotReady", true); set(view.controller(), "roomChanging", false); set(view.controller(), "roomSyncing", false);
            set(view.controller(), "roomAvailable", new BigDecimal("10000")); set(view.controller(), "roomHeld", BigDecimal.ZERO);
            set(view.controller(), "maxBidAmount", new BigDecimal("999999999999999"));
            set(view.controller(), "lastRoomSignal", System.nanoTime()); set(view.controller(), "lastRoomWallet", System.nanoTime());
            invoke(view.controller(), "refreshBidControls"); return null;
        });
        drain();
    }

    private static void bids(View view) throws Exception {
        ready(view, "100", 0);
        int accepted = count(Cue.BID_ACCEPTED);
        int spokenBefore = SPOKEN.size();
        fx(() -> { ((TextField) field(view.controller(), "bidField")).setText("110"); ((Button) field(view.controller(), "bidButton")).fire(); return null; });
        TestSupport.equals(accepted, count(Cue.BID_ACCEPTED), "pending request must not sound accepted");
        view.network().take(MessageType.PLACE_BID).complete(message(MessageType.BID_REJECTED, Map.of("success", "false", "errorCode", "BID_TOO_LOW")));
        drain(); TestSupport.equals(accepted, count(Cue.BID_ACCEPTED), "rejected request must not sound accepted");
        TestSupport.check(count(Cue.ERROR) > 0, "rejected bid has a quiet error cue");
        ready(view, "100", 0);
        fx(() -> { ((TextField) field(view.controller(), "bidField")).setText("110"); ((Button) field(view.controller(), "bidButton")).fire(); return null; });
        Map<String, String> response = data("110", -2);
        response.putAll(Map.of("bid.bidId", "2", "bid.auctionId", "1", "bid.userId", "-2", "bid.amount", "110", "bid.createdAt", Instant.now().toString()));
        view.network().take(MessageType.PLACE_BID).complete(message(MessageType.BID_ACCEPTED, response)); drain();
        TestSupport.equals(accepted + 1, count(Cue.BID_ACCEPTED), "canonical accepted response plays once");
        TestSupport.waitUntil(() -> SPOKEN.size() == spokenBefore + 1, 3000, "accepted-response narration finishes before the next scenario");
        fx(() -> { invoke(view.controller(), "bidConfirmedSound", new Class<?>[]{long.class, BigDecimal.class}, 1L, new BigDecimal("110.00")); return null; });
        TestSupport.equals(accepted + 1, count(Cue.BID_ACCEPTED), "replayed confirmation does not sound twice");
    }

    private static void chat(View view) throws Exception {
        int received = count(Cue.CHAT_RECEIVED), sent = count(Cue.CHAT_SENT);
        TestSupport.equals(0, received, "initial chat history is silent");
        fx(() -> {
            RoomSocialPane pane = (RoomSocialPane) field(view.controller(), "socialRoom");
            ((TitledPane) pane.lookup("#room-chat-members")).setExpanded(true);
            pane.onMessage(chatMessage(8, -3)); pane.onMessage(chatMessage(8, -3));
            pane.onMessage(new HashMap<>(Map.of("auctionId", "99", "chat.id", "9")));
            return null;
        });
        TestSupport.equals(received + 1, count(Cue.CHAT_RECEIVED), "new other-guest message sounds once; duplicate/cross-room silent");
        fx(() -> {
            RoomSocialPane pane = (RoomSocialPane) field(view.controller(), "socialRoom");
            ((TextField) pane.lookup("#room-chat-input")).setText("Hello");
            ((Button) pane.lookup("#room-chat-send")).fire(); return null;
        });
        TestSupport.equals(sent, count(Cue.CHAT_SENT), "pending chat is not acknowledged by sound");
        view.network().take(MessageType.SEND_ROOM_CHAT).complete(message(MessageType.ROOM_CHAT_MESSAGE, chatMessage(10, -2))); drain();
        TestSupport.equals(sent + 1, count(Cue.CHAT_SENT), "anonymous self alias identifies canonical sent message");
        fx(() -> { ((RoomSocialPane) field(view.controller(), "socialRoom")).onMessage(chatMessage(10, -2)); return null; });
        TestSupport.equals(sent + 1, count(Cue.CHAT_SENT), "broadcast plus reply cannot double-play sent message");
        TestSupport.equals(0, view.network().profiles, "sound never requests guest identities or avatars");
    }

    private static void closingNarration(View view) throws Exception {
        ready(view, "140", -3);
        int before = SPOKEN.size();
        fx(() -> {
            view.model().leaveJoinedAuction();
            invoke(view.controller(), "renderAuctions");
            Map<String, String> ending = data("140", -3);
            ending.put("endTime", Instant.now().plusSeconds(29).toString());
            view.model().applySnapshot(ClientWireParser.auction(ending, ""), List.of(), Instant.now(), false);
            invoke(view.controller(), "refreshClock");
            return null;
        });
        TestSupport.equals(before, SPOKEN.size(), "viewing auction from lobby cannot play its reminder");
        ready(view, "140", -3);
        fx(() -> {
            Map<String, String> ending = data("140", -3);
            ending.put("endTime", Instant.now().plusSeconds(31).toString());
            view.model().applySnapshot(ClientWireParser.auction(ending, ""), List.of(), Instant.now(), true);
            invoke(view.controller(), "refreshClock");
            TestSupport.equals(before, SPOKEN.size(), "no early reminder");
            ending = data("140", -3);
            ending.put("endTime", Instant.now().plusSeconds(29).toString());
            view.model().applySnapshot(ClientWireParser.auction(ending, ""), List.of(), Instant.now(), true);
            view.network().state = ConnectionState.DISCONNECTED;
            invoke(view.controller(), "refreshClock");
            view.network().state = ConnectionState.CONNECTED;
            set(view.controller(), "roomSnapshotReady", false);
            invoke(view.controller(), "refreshClock");
            set(view.controller(), "roomSnapshotReady", true); set(view.controller(), "roomSyncing", true);
            invoke(view.controller(), "refreshClock");
            set(view.controller(), "roomSyncing", false); set(view.controller(), "roomChanging", true);
            invoke(view.controller(), "refreshClock");
            set(view.controller(), "roomChanging", false); set(view.controller(), "bidPending", true);
            set(view.controller(), "lastRoomSignal", System.nanoTime() - TimeUnit.SECONDS.toNanos(11));
            invoke(view.controller(), "refreshClock");
            TestSupport.equals(before, SPOKEN.size(), "disconnected, unready, syncing, changing or stale room cannot speak");
            set(view.controller(), "bidPending", false); set(view.controller(), "lastRoomSignal", System.nanoTime());
            invoke(view.controller(), "refreshClock");
            invoke(view.controller(), "refreshClock");
            return null;
        });
        TestSupport.waitUntil(() -> SPOKEN.size() == before + 1, 3000, "fresh joined room gets one closing reminder");
        TestSupport.equals("Có ai muốn đưa ra mức giá cao hơn không ạ?", SPOKEN.get(before), "controller speaks requested thirty-second invitation");
        fx(() -> {
            Map<String, String> ending = data("140", -3); ending.put("endTime", Instant.now().plusSeconds(28).toString());
            invoke(view.controller(), "applySnapshot", new Class<?>[]{ApiResponse.class, boolean.class},
                    new ApiResponse(message(MessageType.RESYNC_RESULT, ending)), true);
            invoke(view.controller(), "refreshClock"); return null;
        });
        TestSupport.equals(before + 1, SPOKEN.size(), "resync and repeated ticks cannot replay reminder");
    }

    private static void countdownAndEvents(View view) throws Exception {
        ready(view, "140", -3);
        int outbid = count(Cue.OUTBID);
        fx(() -> {
            Map<String, String> event = Map.of("auctionId", "1", "currentPrice", "150");
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.OUTBID_NOTIFICATION, event));
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.OUTBID_NOTIFICATION, event));
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.OUTBID_NOTIFICATION, Map.of("auctionId", "99", "currentPrice", "200")));
            return null;
        });
        TestSupport.equals(outbid + 1, count(Cue.OUTBID), "only one active-room outbid cue, not duplicates or other rooms");
        int previousWarnings = count(Cue.WARNING);
        fx(() -> {
            Map<String, String> ending = data("120", -3); ending.put("endTime", Instant.now().plusSeconds(8).toString());
            view.model().applySnapshot(ClientWireParser.auction(ending, ""), List.of(), Instant.now(), true);
            invoke(view.controller(), "refreshClock"); return null;
        });
        int warnings = count(Cue.WARNING);
        TestSupport.equals(previousWarnings + 1, warnings, "one quiet ten-second warning");
        fx(() -> { invoke(view.controller(), "refreshClock"); return null; });
        TestSupport.equals(warnings, count(Cue.WARNING), "countdown warning is not repeated every tick");
        int end = count(Cue.AUCTION_END);
        fx(() -> {
            Map<String, String> expired = data("120", -3); expired.put("endTime", Instant.now().minusSeconds(1).toString());
            view.model().applySnapshot(ClientWireParser.auction(expired, ""), List.of(), Instant.now(), true);
            invoke(view.controller(), "refreshClock"); return null;
        });
        TestSupport.equals(end, count(Cue.AUCTION_END), "local timer zero is not confirmed auction end");
        fx(() -> {
            Map<String, String> ended = data("120", -3); ended.put("status", "ENDED"); ended.put("endedAt", Instant.now().toString());
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.AUCTION_ENDED, ended));
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.AUCTION_ENDED, ended)); return null;
        }); drain();
        TestSupport.equals(end + 1, count(Cue.AUCTION_END), "confirmed server close sounds once");
    }

    private static void winnerNarration(View view) throws Exception {
        int before = SPOKEN.size();
        Map<String, String> lobbyWin = winningResult(20);
        fx(() -> {
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.AUCTION_ENDED, lobbyWin));
            return null;
        });
        TestSupport.waitUntil(() -> SPOKEN.size() == before + 1, 3000, "personal win outside auction room speaks");
        TestSupport.equals("Chúc mừng bạn đã đấu giá thành công Bình gốm cổ!", SPOKEN.get(before), "congratulations use actual product name");
        fx(() -> {
            Map<String, String> opening = data("120", 0); opening.put("auctionId", "21");
            view.model().applySnapshot(ClientWireParser.auction(opening, ""), List.of(), Instant.now(), true);
            invoke(view.controller(), "renderAuctions");
            Map<String, String> expired = data("120", -2);
            expired.putAll(Map.of("auctionId", "21", "endTime", Instant.now().minusSeconds(1).toString()));
            view.model().applySnapshot(ClientWireParser.auction(expired, ""), List.of(), Instant.now(), true);
            invoke(view.controller(), "refreshClock"); return null;
        });
        TestSupport.equals(before + 1, SPOKEN.size(), "local expiry cannot announce a win before server confirmation");
        Map<String, String> roomWin = winningResult(21);
        fx(() -> {
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.AUCTION_ENDED, roomWin));
            return null;
        });
        TestSupport.waitUntil(() -> SPOKEN.size() == before + 2, 3000, "personal win inside room speaks");
        int stops = VOICE_STOPS.get();
        fx(() -> {
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.AUCTION_ENDED, roomWin));
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.AUCTION_ENDED, lobbyWin));
            invoke(view.controller(), "applySnapshot", new Class<?>[]{ApiResponse.class, boolean.class},
                    new ApiResponse(message(MessageType.RESYNC_RESULT, roomWin)), true);
            Map<String, String> loser = winningResult(22); loser.put("winnerIsSelf", "false");
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.AUCTION_ENDED, loser));
            Map<String, String> empty = winningResult(23); empty.put("winnerId", "");
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.AUCTION_ENDED, empty));
            Map<String, String> cancelled = winningResult(24); cancelled.put("status", "CANCELLED");
            invoke(view.controller(), "handleServerEvent", new Class<?>[]{WireMessage.class}, message(MessageType.AUCTION_CANCELLED, cancelled));
            return null;
        }); drain();
        TestSupport.equals(before + 2, SPOKEN.size(), "duplicates, losing, no bids and cancellation never congratulate");
        TestSupport.equals(stops, VOICE_STOPS.get(), "duplicate end and closed snapshot do not cut off congratulations");
    }

    private static Map<String, String> winningResult(long room) {
        Map<String, String> result = data("120", -2);
        result.putAll(Map.of("auctionId", Long.toString(room), "status", "ENDED", "productName", "Bình gốm cổ",
                "winnerId", "-2", "winnerUsername", "Khách #2", "winnerIsSelf", "true", "finalPrice", "120",
                "endedAt", Instant.now().toString(), "endTime", Instant.now().minusSeconds(1).toString()));
        return result;
    }

    private static void staleReply(View view) throws Exception {
        ready(view, "130", -3);
        int before = count(Cue.BID_ACCEPTED);
        fx(() -> { ((TextField) field(view.controller(), "bidField")).setText("140"); ((Button) field(view.controller(), "bidButton")).fire(); return null; });
        CompletableFuture<WireMessage> pending = view.network().take(MessageType.PLACE_BID);
        fx(() -> { view.network().state = ConnectionState.DISCONNECTED; return null; });
        pending.completeExceptionally(new IllegalStateException("connection lost")); drain();
        TestSupport.equals(before, count(Cue.BID_ACCEPTED), "disconnect never sounds accepted");
        ready(view, "130", -3);
        fx(() -> { ((TextField) field(view.controller(), "bidField")).setText("140"); ((Button) field(view.controller(), "bidButton")).fire(); return null; });
        pending = view.network().take(MessageType.PLACE_BID);
        fx(() -> { view.model().clearIdentity(); return null; });
        pending.complete(message(MessageType.BID_ACCEPTED, data("140", -2))); drain();
        TestSupport.equals(before, count(Cue.BID_ACCEPTED), "previous-session callback cannot play confirmation");
        fx(() -> { view.model().applyIdentity(new ApiResponse(message(MessageType.LOGIN_RESULT,
                Map.of("success", "true", "userId", "2", "username", "buyer", "sessionToken", "restored-sound-session")))); return null; });
    }

    private static long version;
    private static Map<String, String> data(String price, long winner) {
        Map<String, String> data = new HashMap<>(Map.of("success", "true", "auctionId", "1", "productId", "1", "productName", "Sound demo", "hostUserId", "1", "startPrice", "100", "minBidIncrement", "10", "currentPrice", price, "status", "OPEN"));
        data.put("version", Long.toString(++version)); data.put("currentWinnerId", winner == 0 ? "" : Long.toString(winner));
        data.put("startTime", Instant.now().minusSeconds(10).toString()); data.put("endTime", Instant.now().plusSeconds(300).toString());
        data.put("serverNow", Instant.now().toString()); data.put("anonymousGuests", "true"); data.put("selfParticipantId", "-2"); return data;
    }

    private static Map<String, String> chatMessage(long id, long person) {
        return Map.of("success", "true", "auctionId", "1", "chat.id", Long.toString(id), "chat.participantId", Long.toString(person), "chat.name", "Guest", "chat.body", "Hello", "chat.sentAt", Instant.now().toString());
    }
    private static WireMessage message(MessageType type, Map<String, String> data) { return new WireMessage(1, MessageKind.RESPONSE, type, "sound-test", 1, System.currentTimeMillis(), data); }
    private static int count(Cue cue) throws Exception { return fx(() -> (int) PLAYED.stream().filter(value -> value == cue).count()); }
    private static Object field(Object target, String name) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void set(Object target, String name, Object value) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
    private static void invoke(Object target, String name) throws Exception { invoke(target, name, new Class<?>[0]); }
    private static void invoke(Object target, String name, Class<?>[] types, Object... values) throws Exception { Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(target, values); }
    private static <Result> Result fx(Callable<Result> action) throws Exception {
        CompletableFuture<Result> result = new CompletableFuture<>();
        Platform.runLater(() -> { CLOCK.addAndGet(3_000_000_000L); try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result.get(15, TimeUnit.SECONDS);
    }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }

    private static final class Network implements ClientTransport {
        private ConnectionState state = ConnectionState.CONNECTED;
        private Map<String, String> snapshot = data("100", 0);
        private final Map<MessageType, CompletableFuture<WireMessage>> pending = new EnumMap<>(MessageType.class);
        private int profiles;
        synchronized CompletableFuture<WireMessage> take(MessageType type) { return Objects.requireNonNull(pending.remove(type), "Missing request: " + type); }
        @Override public synchronized CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> request) {
            if (type == MessageType.GET_ROOM_PROFILE) { profiles++; }
            if (type == MessageType.PLACE_BID || type == MessageType.SEND_ROOM_CHAT) { var future = new CompletableFuture<WireMessage>(); pending.put(type, future); return future; }
            if (type == MessageType.RESYNC) { return CompletableFuture.completedFuture(message(MessageType.RESYNC_RESULT, snapshot)); }
            Map<String, String> response = new HashMap<>(Map.of("success", "true", "auctionId", "1", "available", "10000", "roomHeld", "0", "maxBidAmount", "999999999999999", "memberCount", "0", "chatCount", "0", "canInteract", "true"));
            if (type == MessageType.GET_ROOM_SOCIAL) {
                response.putAll(Map.of("chatCount", "1", "chat.0.id", "1", "chat.0.participantId", "-3", "chat.0.name", "Guest", "chat.0.body", "Old history", "chat.0.sentAt", Instant.now().minusSeconds(20).toString()));
            }
            return CompletableFuture.completedFuture(message(type, response));
        }
        @Override public CompletableFuture<Void> connect() { state = ConnectionState.CONNECTED; return CompletableFuture.completedFuture(null); }
        @Override public void disconnect() { state = ConnectionState.DISCONNECTED; }
        @Override public boolean isConnected() { return state == ConnectionState.CONNECTED; }
        @Override public ConnectionState getState() { return state; }
        @Override public String getEndpoint() { return "ws://test"; }
        @Override public void setEndpoint(String endpoint) { }
        @Override public CompletableFuture<WireMessage> ping() { return sendRequest(MessageType.PING, Map.of()); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { }
        @Override public void addStateListener(ConnectionStateListener listener) { }
        @Override public void close() { state = ConnectionState.CLOSED; }
    }
}
