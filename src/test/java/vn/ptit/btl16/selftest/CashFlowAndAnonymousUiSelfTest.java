package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.*;
import vn.ptit.btl16.client.model.ClientProduct;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.common.util.RevenuePeriod;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.fx.ServerCashFlowWindow;
import vn.ptit.btl16.server.wallet.PlatformCashFlowRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class CashFlowAndAnonymousUiSelfTest {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var worker=Executors.newSingleThreadExecutor();
        Stage owner=fx(() -> { var stage=new Stage(); var scene=new Scene(new StackPane(),1100,760); DesktopTheme.apply(scene); stage.setScene(scene); stage.show(); return stage; });
        try {
            try (var fixture=new CashFlowRulesSelfTest.Fixture(); var server=ServerApplication.create(ServerConfig.loadDefault())) {
                var page=fx(() -> new ServerCashFlowWindow(owner,() -> server,worker));
                var tabs=fx(() -> {
                    var overview=new Tab("Tổng quan",new StackPane()); var cashFlow=new Tab("Dòng tiền sàn",page);
                    var navigation=new TabPane(overview,cashFlow); navigation.getStyleClass().add("admin-tabs");
                    navigation.getSelectionModel().selectedItemProperty().addListener((observable,before,selected) -> {
                        if (selected==cashFlow) { page.activate(); } else { page.deactivate(); }
                    });
                    owner.getScene().getStylesheets().add(ServerCashFlowWindow.class.getResource("/ui/server-admin.css").toExternalForm());
                    owner.getScene().setRoot(navigation); navigation.getSelectionModel().select(cashFlow); return navigation;
                });
                waitFor(() -> owner.getScene().lookup("#cash-flow-table") instanceof TableView<?> rows && rows.getItems().size()==3,"cash-flow page loads paid monthly rows");
                Stage cash=owner;
                var expected=server.wallets().cashFlow(RevenuePeriod.DEFAULT,0);
                fx(() -> {
                    TestSupport.equals(1L,Window.getWindows().stream().filter(Window::isShowing).count(),"cash-flow tab never opens another window");
                    TestSupport.equals(RevenuePeriod.DEFAULT,period(cash).getValue(),"cash flow defaults to one month");
                    TestSupport.equals(WalletUi.money(expected.balance()),((Label)cash.getScene().lookup("#cash-flow-balance")).getText(),"platform balance shown exactly");
                    TestSupport.equals(WalletUi.money(expected.fees()),((Label)cash.getScene().lookup("#cash-flow-fees")).getText(),"monthly collected fees shown exactly");
                    TestSupport.check(((Label)cash.getScene().lookup("#cash-flow-review")).getText().contains("1 phiên"),"review-required counter visible");
                    TestSupport.check(table().getItems().stream().filter(PlatformCashFlowRepository.Entry::anonymous).allMatch(entry -> !entry.seller().contains("seller9") && !entry.buyer().contains("buyer")),"anonymous identities hidden in money table");
                    screenshot(cash.getScene(),"target/server-cash-flow.png");
                    period(cash).setValue(RevenuePeriod.ONE_YEAR); return null;
                });
                waitFor(() -> table().getItems().size()==6,"annual cash-flow filter changes records");
                fx(() -> { period(cash).setValue(RevenuePeriod.ONE_MONTH); period(cash).setValue(RevenuePeriod.SEVEN_DAYS); return null; });
                waitFor(() -> table().getItems().size()==2,"rapid filter switches keep only latest result");
                fx(() -> { ((Button)cash.getScene().lookup("#cash-flow-refresh")).fire(); return null; });
                waitFor(() -> ((Label)cash.getScene().lookup("#cash-flow-status")).getText().contains("Đã ghi nhận"),"refresh completes");
                fx(() -> {
                    TestSupport.equals(WalletUi.money(expected.balance()),((Label)cash.getScene().lookup("#cash-flow-balance")).getText(),"viewing money never collects twice");
                    period(cash).setValue(RevenuePeriod.ONE_YEAR); tabs.getSelectionModel().selectFirst(); return null;
                });
                fixture.addPages();
                fx(() -> { tabs.getSelectionModel().select(1); return null; });
                waitFor(() -> table().getItems().size()==50,"returning to the tab loads the selected period with pagination");
                fx(() -> { ((Button)cash.getScene().lookup("#cash-flow-next")).fire(); return null; });
                waitFor(() -> table().getItems().size()==58,"next page appends transactions without duplicates");
                var displayed=fx(CashFlowAndAnonymousUiSelfTest::table);
                fx(() -> {
                    tabs.getSelectionModel().selectFirst(); tabs.getSelectionModel().select(1);
                    TestSupport.equals(58,displayed.getItems().size(),"completed pagination survives switching tabs");
                    TestSupport.equals(RevenuePeriod.ONE_YEAR,period(cash).getValue(),"selected period survives switching tabs"); return null;
                });
                var blocked=new CountDownLatch(1); var release=new CountDownLatch(1);
                worker.submit(() -> {
                    blocked.countDown();
                    try { release.await(20,TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                });
                TestSupport.check(blocked.await(10,TimeUnit.SECONDS),"worker barrier is ready");
                fx(() -> {
                    ((Button)cash.getScene().lookup("#cash-flow-refresh")).fire();
                    tabs.getSelectionModel().selectFirst(); return null;
                });
                release.countDown(); worker.submit(() -> { }).get(20,TimeUnit.SECONDS);
                fx(() -> {
                    TestSupport.equals(58,displayed.getItems().size(),"inactive tab rejects a delayed refresh response");
                    tabs.getSelectionModel().select(1); return null;
                });
                waitFor(() -> displayed.getItems().size()==50,"cancelled refresh restarts on activation instead of leaving pagination stuck");
                var refreshed=server.wallets().cashFlow(RevenuePeriod.ONE_YEAR,0);
                fx(() -> {
                    TestSupport.equals(WalletUi.money(refreshed.balance()),((Label)cash.getScene().lookup("#cash-flow-balance")).getText(),"tab reactivation does not charge fees again");
                    TestSupport.check(!((Button)cash.getScene().lookup("#cash-flow-next")).isDisabled(),"pagination is usable after reactivation");
                    tabs.getSelectionModel().selectFirst(); owner.getScene().setRoot(new StackPane()); return null;
                });
            }
            var creation=fx(() -> creation(owner));
            fx(() -> { creation.show(); ((TextField)creation.getDialogPane().lookup("#auction-create-price")).setText("54321"); return null; });
            Platform.runLater(() -> identity(creation).getSelectionModel().select(1));
            waitFor(() -> anonymousPrompt()!=null,"selecting anonymous mode opens confirmation");
            fx(() -> {
                var prompt=anonymousPrompt();
                TestSupport.check(labels(prompt).contains("Nếu mở phòng ẩn danh thì mọi thông tin người bán, người mua đều ẩn, giá trị phòng sẽ không được tính vào top doanh thu"),"exact privacy/revenue warning shown");
                choice(prompt,ButtonBar.ButtonData.CANCEL_CLOSE).fire(); return null;
            });
            waitFor(() -> identity(creation).getSelectionModel().getSelectedIndex()==0,"No reverts to normal room");
            fx(() -> { TestSupport.equals("54321",((TextField)creation.getDialogPane().lookup("#auction-create-price")).getText(),"declining preserves seller draft"); return null; });
            Platform.runLater(() -> identity(creation).getSelectionModel().select(1));
            waitFor(() -> anonymousPrompt()!=null,"confirmation required on each enable");
            fx(() -> { ((Stage)anonymousPrompt().getScene().getWindow()).close(); return null; });
            waitFor(() -> identity(creation).getSelectionModel().getSelectedIndex()==0,"closing warning does not consent");
            Platform.runLater(() -> identity(creation).getSelectionModel().select(1));
            waitFor(() -> anonymousPrompt()!=null,"confirmation reopens after cancel");
            fx(() -> { choice(anonymousPrompt(),ButtonBar.ButtonData.YES).fire(); return null; });
            waitFor(() -> anonymousPrompt()==null && identity(creation).getSelectionModel().getSelectedIndex()==1,"Yes enables anonymous room");
            fx(() -> {
                ((Button)creation.getDialogPane().lookup("#auction-create-next")).fire();
                TestSupport.check(creation.getDialogPane().lookup("#auction-create-review")!=null,"creation advances to review");
                TestSupport.check(labels(creation.getDialogPane()).contains("không cộng doanh thu xếp hạng"),"review reiterates exclusion");
                ((Button)creation.getDialogPane().lookup("#auction-create-back")).fire();
                TestSupport.equals(1,identity(creation).getSelectionModel().getSelectedIndex(),"back preserves confirmed anonymity");
                ((Button)creation.getDialogPane().lookup("#auction-create-next")).fire();
                ((Button)creation.getDialogPane().lookup("#auction-create-confirm")).fire();
                Object draft=creation.getResult(); var method=draft.getClass().getDeclaredMethod("anonymousGuests"); method.setAccessible(true);
                TestSupport.equals(true,method.invoke(draft),"confirmed anonymous flag reaches creation draft");
                return null;
            });
            System.out.println("[PASS] CashFlowAndAnonymousUiSelfTest: embedded cash-flow tab/balance/fees/review counter, filters/refresh/stale requests/privacy, tab activation/pagination/cancelled refresh, anonymous warning No/close/Yes, draft preservation and creation confirmation");
        } finally {
            worker.shutdownNow();
            fx(() -> { for (var window:List.copyOf(Window.getWindows())) { window.hide(); } return null; }); Platform.exit();
        }
    }
    private static Dialog<?> creation(Stage owner) throws Exception {
        var constructor=Class.forName("vn.ptit.btl16.client.fx.AuctionCreationDialog").getDeclaredConstructor(Stage.class,List.class,Supplier.class); constructor.setAccessible(true);
        Instant now=Instant.now(); var product=new ClientProduct(1,1,"seller","ITEM","Camera",TestMedia.DESCRIPTION,true,now,now,true,"image/png","image.png",100,1);
        return (Dialog<?>)constructor.newInstance(owner,List.of(product),(Supplier<Instant>)Instant::now);
    }
    @SuppressWarnings("unchecked") private static ComboBox<String> identity(Dialog<?> creation) { return (ComboBox<String>)creation.getDialogPane().lookup("#auction-create-identity"); }
    @SuppressWarnings("unchecked") private static ComboBox<RevenuePeriod> period(Stage window) { return (ComboBox<RevenuePeriod>)window.getScene().lookup("#cash-flow-period"); }
    @SuppressWarnings("unchecked") private static TableView<PlatformCashFlowRepository.Entry> table() { return (TableView<PlatformCashFlowRepository.Entry>)Window.getWindows().stream().filter(Window::isShowing).map(window -> window.getScene().lookup("#cash-flow-table")).filter(TableView.class::isInstance).findFirst().orElseThrow(); }
    private static DialogPane anonymousPrompt() { return Window.getWindows().stream().filter(Window::isShowing).map(window -> window.getScene().lookup("#auction-anonymous-confirmation")).filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElse(null); }
    private static Button choice(DialogPane pane,ButtonBar.ButtonData data) { return (Button)pane.lookupButton(pane.getButtonTypes().stream().filter(type -> type.getButtonData()==data).findFirst().orElseThrow()); }
    private static String labels(Node root) { var text=new StringBuilder(); if (root instanceof Labeled labeled) { text.append(labeled.getText()); } if (root instanceof Parent parent) { for (var child:parent.getChildrenUnmodifiable()) { text.append(labels(child)); } } return text.toString(); }
    private static <Result> Result fx(Callable<Result> action) throws Exception { var future=new CompletableFuture<Result>(); Platform.runLater(() -> { try { future.complete(action.call()); } catch (Throwable error) { future.completeExceptionally(error); } }); return future.get(20,TimeUnit.SECONDS); }
    private static void waitFor(Callable<Boolean> condition,String message) throws Exception { long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20); while (System.nanoTime()<deadline) { if (fx(condition)) { return; } Thread.sleep(30); } throw new AssertionError(message); }
    private static void screenshot(Scene scene,String path) throws Exception {
        scene.getRoot().applyCss();scene.getRoot().layout();var pixels=scene.snapshot(null);
        var image=new java.awt.image.BufferedImage((int)pixels.getWidth(),(int)pixels.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int row=0;row<image.getHeight();row++) { for (int column=0;column<image.getWidth();column++) { image.setRGB(column,row,pixels.getPixelReader().getArgb(column,row)); } }
        javax.imageio.ImageIO.write(image,"png",new java.io.File(path));
    }
}
