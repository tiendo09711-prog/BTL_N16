package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.stage.*;
import vn.ptit.btl16.client.fx.FxClientController;
import vn.ptit.btl16.client.model.*;
import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.service.AuctionWireData;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Catalog removal replaces restoration throughout the view; stub mutations never touch a real server. */
public final class ProductDeletionUiSelfTest {
    public static void main(String[] args) throws Exception {
        Platform.startup(()->Platform.setImplicitExit(false)); Network network=new Network();
        var model=new ClientAppModel(); model.applyIdentity(new ApiResponse(WireMessage.response(MessageType.LOGIN_RESULT,null,1,Map.of("userId","1","username","demo","sessionToken","delete-ui"))));
        Stage owner=fx(()->{Stage stage=new Stage();Scene scene=new Scene(new StackPane(),1100,800);DesktopTheme.apply(scene);stage.setScene(scene);stage.show();return stage;});
        var controller=fx(()->new FxClientController(owner,ClientConfig.loadDefault(),model,network,new AccountApi(network),new AuctionApi(network)));
        try {
            fx(()->{var build=FxClientController.class.getDeclaredMethod("buildScenes");build.setAccessible(true);build.invoke(controller);owner.setScene((Scene)field(controller,"mainScene"));return null;});
            fx(()->{var method=FxClientController.class.getDeclaredMethod("showProducts");method.setAccessible(true);method.invoke(controller);return null;}); drain();
            Stage window=owner;
            Node page=fx(()->window.getScene().lookup("#seller-products-page"));
            @SuppressWarnings("unchecked") TableView<ClientProduct> table=fx(()->(TableView<ClientProduct>)window.getScene().lookup("#seller-products"));
            Button delete=fx(()->(Button)field(page,"delete"));
            verifyActionStates(page, table);
            fx(()->{
                TestSupport.equals(3,table.getItems().size(),"all undeleted items load");
                TestSupport.check(table.getColumns().stream().noneMatch(c->c.getText().contains("Khôi phục")),"restoration column removed");
                TestSupport.check(window.getScene().lookup("#restore-product")==null && window.getScene().lookup("#product-restoration-status")==null,"restoration controls removed");
                String copy=labels(window.getScene().getRoot());TestSupport.check(!copy.contains("khôi phục") && !copy.contains("Khôi phục"),"restoration details removed");
                TestSupport.check(window.getScene().getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast).noneMatch(b->b.getText().equals("Đóng")||b.getText().equals("Ngừng sử dụng")),"bottom close and deactivate removed");
                TestSupport.check(page.lookup("#delete-product")==null && page.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast).noneMatch(b->b.getText().equals("Sửa")||b.getText().equals("Ảnh / video")),"per-item actions are available only in three-dot menus");
                @SuppressWarnings("unchecked") var filter=(ComboBox<String>)window.getScene().lookup("#seller-product-filter");TestSupport.check(!filter.getItems().contains("RESTORABLE"),"restoration filter removed");
                table.getSelectionModel().select(1);TestSupport.check(!delete.isDisabled(),"inactive product can be deleted");
                window.getScene().getRoot().applyCss();window.getScene().getRoot().layout();screenshot(window.getScene(),"target/product-deletion.png");return null;
            });
            answer(delete,ButtonType.NO,"không thể khôi phục"); TestSupport.equals(0,network.deletes,"cancel sends no delete");
            answer(delete,ButtonType.YES,"không thể khôi phục");
            fx(()->{TestSupport.equals(1,network.deletes,"confirmation sends one delete");TestSupport.equals(2L,network.requested,"deletes selected inactive product");TestSupport.check(delete.isDisabled(),"pending delete disabled");delete.fire();TestSupport.equals(1,network.deletes,"no duplicate pending delete");network.complete(true);return null;});drain();
            fx(()->{TestSupport.equals(2,table.getItems().size(),"successful delete refreshes list");TestSupport.check(table.getItems().stream().noneMatch(p->p.getProductId()==2),"deleted row absent");table.getSelectionModel().select(1);return null;});
            // The three-dot menu invokes exactly the same deletion handler.
            MenuButton menu=fx(()->{
                table.applyCss();table.layout();
                return table.lookupAll(".account-more").stream().filter(MenuButton.class::isInstance).map(MenuButton.class::cast).filter(m->{for(Node n=m;n!=null;n=n.getParent()){if(n instanceof TableRow<?> row){return row.getIndex()==1;}}return false;}).findFirst().orElseThrow();
            });
            fx(()->{menu.show();TestSupport.equals(List.of("Sửa","Xóa sản phẩm"),menu.getItems().stream().map(MenuItem::getText).toList(),"untraded menu only has edit and delete");menu.hide();return null;});
            CompletableFuture<Void> firing=launch(()->menu.getItems().get(1).fire());waitDialog(ButtonType.YES);fx(()->{((Button)dialog(ButtonType.YES).lookupButton(ButtonType.YES)).fire();return null;});firing.get(10,TimeUnit.SECONDS);
            fx(()->{TestSupport.equals(3L,network.requested,"context menu deletes its own row");network.complete(false);return null;});waitDialog(ButtonType.OK);
            fx(()->{DialogPane message=dialog(ButtonType.OK);TestSupport.check(message.getContentText().contains("phiên đang mở"),"server refusal explained");((Button)message.lookupButton(ButtonType.OK)).fire();return null;});drain();
            fx(()->{TestSupport.equals(2,table.getItems().size(),"refused deletion keeps item");TestSupport.check(!delete.isDisabled(),"failed deletion re-enables action");table.getSelectionModel().selectFirst();return null;});
            answer(delete,ButtonType.YES,"không thể khôi phục");int loads=network.loads;
            fx(()->{((Button)window.getScene().lookup("#seller-products-close")).fire();network.complete(true);return null;});drain();
            TestSupport.equals(loads,network.loads,"late mutation reply after close does not reload view");
            TestSupport.check(fx(()->window.isShowing()&&window.getScene().lookup("#seller-products-page")==null),"top-right X returns to marketplace without closing the app");
            TestSupport.equals(0,fx(()->table.getItems().size()),"close releases product view");
            System.out.println("[PASS] ProductDeletionUiSelfTest: status-based list/grid actions, read-only details/gallery, inactive delete, cancel/confirm, single pending request, context-menu target, refusal/reload, X and stale closed reply");
        } finally {fx(()->{controller.close();for(Window window:List.copyOf(Window.getWindows())){window.hide();}return null;});Platform.exit();}
    }
    private static void verifyActionStates(Node page, TableView<ClientProduct> table) throws Exception {
        fx(() -> {
            List<ClientProduct> original = List.copyOf(table.getItems());
            Button edit = (Button) field(page, "edit"), media = (Button) field(page, "media"), delete = (Button) field(page, "delete");
            var editHandler = edit.getOnAction(); var mediaHandler = media.getOnAction(); var deleteHandler = delete.getOnAction();
            List<Long> edits = new ArrayList<>(), deletes = new ArrayList<>(), views = new ArrayList<>();
            edit.setOnAction(event -> edits.add(table.getSelectionModel().getSelectedItem().getProductId()));
            delete.setOnAction(event -> deletes.add(table.getSelectionModel().getSelectedItem().getProductId()));
            media.setOnAction(event -> views.add(table.getSelectionModel().getSelectedItem().getProductId()));
            var setter = page.getClass().getDeclaredMethod("setProducts", List.class, Long.class); setter.setAccessible(true);
            List<ProductTransactionStatus> statuses = List.of(ProductTransactionStatus.UNTRADED, ProductTransactionStatus.AUCTIONING, ProductTransactionStatus.SOLD);
            try {
                for (int round = 0; round < 2; round++) {
                    List<ClientProduct> products = new ArrayList<>();
                    for (int index = 0; index < original.size(); index++) {
                        ClientProduct product = original.get(index);
                        products.add(new ClientProduct(product.getProductId(), product.getOwnerId(), product.getOwnerUsername(), product.getCode(),
                                product.getName(), product.getDescription(), product.isActive(), product.getCreatedAt(), product.getUpdatedAt(),
                                product.hasImage(), product.getImageMime(), product.getImageName(), product.getImageSize(), product.getImageVersion(),
                                product.getRestorationStatus(), statuses.get((index + round) % statuses.size())));
                    }
                    setter.invoke(page, products, null);
                    for (ClientProduct product : products) {
                        table.getSelectionModel().select(product); table.applyCss(); table.layout();
                        boolean untraded = product.getTransactionStatus() == ProductTransactionStatus.UNTRADED;
                        boolean auctioning = product.getTransactionStatus() == ProductTransactionStatus.AUCTIONING;
                        TestSupport.equals(!untraded, edit.isDisabled(), "only untraded products enable editing");
                        TestSupport.equals(auctioning, delete.isDisabled(), "auctioning products disable deletion");
                        TestSupport.check(!media.isDisabled(), "all statuses retain read-only gallery access");
                        TableRow<?> row = table.lookupAll(".table-row-cell").stream().filter(TableRow.class::isInstance).map(TableRow.class::cast)
                                .filter(candidate -> candidate.getItem() == product).findFirst().orElseThrow();
                        MenuButton menu = (MenuButton) row.lookup(".account-more");
                        if (auctioning) {
                            TestSupport.check(menu == null, "auctioning table rows hide all actions");
                            int previousEdits = edits.size(), previousDeletes = deletes.size(); edit.fire(); delete.fire();
                            TestSupport.equals(previousEdits, edits.size(), "auctioning edit cannot fire");
                            TestSupport.equals(previousDeletes, deletes.size(), "auctioning delete cannot fire");
                        } else {
                            table.getSelectionModel().select(products.stream().filter(other -> other != product).findFirst().orElseThrow());
                            menu.show();
                            TestSupport.equals(product, table.getSelectionModel().getSelectedItem(), "opening menu selects its own product");
                            TestSupport.equals(untraded ? List.of("Sửa", "Xóa sản phẩm") : List.of("Xóa sản phẩm"), menu.getItems().stream().map(MenuItem::getText).toList(), "table menu follows transaction status");
                            TestSupport.check(menu.getItems().stream().noneMatch(MenuItem::isDisable), "menu permissions reflect its own product");
                            if (untraded) { menu.getItems().getFirst().fire(); TestSupport.equals(product.getProductId(), edits.getLast(), "edit targets untraded menu product"); }
                            else { int previousEdits = edits.size(); edit.fire(); TestSupport.equals(previousEdits, edits.size(), "sold product cannot invoke edit"); }
                            menu.getItems().getLast().fire(); menu.hide();
                            TestSupport.equals(product.getProductId(), deletes.getLast(), "delete targets menu product");
                        }
                        String details = labels(page.lookup("#seller-product-detail"));
                        TestSupport.check(details.contains(product.getCode()) && details.contains(product.getName()) && details.contains(product.getDescription()), "selection retains right-side product information");
                        int previousViews = views.size();
                        page.lookup("#seller-detail-image").getOnMouseClicked().handle(click(1));
                        page.lookup("#seller-media-count").getOnMouseClicked().handle(click(1));
                        TestSupport.equals(previousViews + 2, views.size(), "image and media count retain gallery entry points for every status");
                        TestSupport.equals(product.getProductId(), views.getLast(), "detail gallery targets selected product");
                    }
                    ((ToggleButton) page.lookup("#seller-grid-view")).fire();
                    TilePane cards = (TilePane) field(page, "cards");
                    for (int index = 0; index < products.size(); index++) {
                        ClientProduct product = products.get(index); Node card = cards.getChildren().get(index);
                        card.getOnMouseClicked().handle(click(1));
                        TestSupport.equals(product, table.getSelectionModel().getSelectedItem(), "single grid click selects product details");
                        MenuButton menu = (MenuButton) card.lookup(".account-more");
                        if (product.getTransactionStatus() == ProductTransactionStatus.AUCTIONING) { TestSupport.check(menu == null, "auctioning grid cards hide all actions"); }
                        else { TestSupport.equals(product.getTransactionStatus() == ProductTransactionStatus.UNTRADED ? List.of("Sửa", "Xóa sản phẩm") : List.of("Xóa sản phẩm"), menu.getItems().stream().map(MenuItem::getText).toList(), "grid menu follows transaction status"); }
                        int previousViews = views.size(); card.getOnMouseClicked().handle(click(2));
                        TestSupport.equals(previousViews + 1, views.size(), "double grid click opens gallery regardless of status");
                        TestSupport.equals(product.getProductId(), views.getLast(), "grid gallery targets clicked product");
                    }
                    ((ToggleButton) page.lookup("#seller-list-view")).fire();
                }
                table.getSelectionModel().clearSelection();
                TestSupport.check(edit.isDisabled() && delete.isDisabled() && media.isDisabled(), "empty selection disables all handlers");
            } finally {
                edit.setOnAction(editHandler); media.setOnAction(mediaHandler); delete.setOnAction(deleteHandler);
                setter.invoke(page, original, null);
                ((ToggleButton) page.lookup("#seller-list-view")).fire();
            }
            return null;
        });
    }
    private static MouseEvent click(int count) { return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, count, false, false, false, false, false, false, false, false, false, true, null); }
    private static void answer(Button button,ButtonType answer,String text)throws Exception{var action=launch(button::fire);waitDialog(ButtonType.YES);fx(()->{DialogPane pane=dialog(ButtonType.YES);TestSupport.check(pane.getContentText().contains(text),"confirmation explains permanent removal");((Button)pane.lookupButton(answer)).fire();return null;});action.get(10,TimeUnit.SECONDS);drain();}
    private static CompletableFuture<Void> launch(Runnable action){var result=new CompletableFuture<Void>();Platform.runLater(()->{try{action.run();result.complete(null);}catch(Throwable error){result.completeExceptionally(error);}});return result;}
    private static DialogPane dialog(ButtonType button){return Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane pane&&pane.getButtonTypes().contains(button)).map(w->(DialogPane)w.getScene().getRoot()).findFirst().orElseThrow();}
    private static void waitDialog(ButtonType button)throws Exception{TestSupport.waitUntil(()->{try{return fx(()->{try{dialog(button);return true;}catch(NoSuchElementException ignored){return false;}});}catch(Exception error){throw new CompletionException(error);}},10000,"dialog appears");}
    private static <T>T fx(Callable<T> action)throws Exception{var task=new FutureTask<T>(action);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);}
    private static Object field(Object target,String name)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static void drain()throws Exception{fx(()->null);Thread.sleep(100);fx(()->null);}
    private static String labels(Node node){StringBuilder out=new StringBuilder();if(node instanceof Label label){out.append(label.getText());}if(node instanceof Parent parent){for(Node child:parent.getChildrenUnmodifiable()){out.append('\n').append(labels(child));}}return out.toString();}
    private static void screenshot(Scene scene,String path)throws Exception{var image=scene.snapshot(null);var out=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<out.getHeight();y++){for(int x=0;x<out.getWidth();x++){out.setRGB(x,y,image.getPixelReader().getArgb(x,y));}}javax.imageio.ImageIO.write(out,"png",new java.io.File(path));}
    private static final class Network extends NetworkClient {
        int deletes,loads;long requested;final Set<Long> removed=new HashSet<>();CompletableFuture<WireMessage> pending;
        Network(){super(2097152,1000,10000);}
        @Override public CompletableFuture<WireMessage> sendRequest(MessageType type,Map<String,String> data){
            if(type==MessageType.DELETE_PRODUCT){deletes++;requested=Long.parseLong(data.get("productId"));pending=new CompletableFuture<>();return pending;}
            TestSupport.equals(MessageType.MY_PRODUCTS,type,"stub only handles catalog calls");loads++;
            List<Product> products=new ArrayList<>();for(int index=1;index<=3;index++){if(!removed.contains((long)index)){products.add(new Product(index,1,"demo","SP_"+index,"Sản phẩm "+index,TestMedia.DESCRIPTION,index!=2,Instant.now(),Instant.now()));}}
            products.add(new Product(991,2,"seller","BOUGHT991","Sản phẩm đã mua",TestMedia.DESCRIPTION,true,Instant.now(),Instant.now()));
            Map<String,String> response=AuctionWireData.productList(products,Instant.now());response.put("success","true");return CompletableFuture.completedFuture(WireMessage.response(MessageType.MY_PRODUCTS_RESULT,null,1,response));
        }
        void complete(boolean success){if(success){removed.add(requested);}pending.complete(WireMessage.response(success?MessageType.DELETE_PRODUCT_RESULT:MessageType.ERROR,null,2,success?Map.of("success","true"):Map.of("success","false","errorCode","PRODUCT_IN_USE","message","Sản phẩm còn phiên đang mở hoặc sắp diễn ra.")));}
    }
}
