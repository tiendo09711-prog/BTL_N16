package vn.ptit.btl16.selftest;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;
import vn.ptit.btl16.client.fx.*;
import vn.ptit.btl16.client.service.ProductMediaApi;
import vn.ptit.btl16.server.auction.repository.TestAuctionRepository;
import vn.ptit.btl16.server.media.*;
import java.io.File;
import java.net.URI;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public final class MediaUiSelfTest {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        TestAuctionRepository repository = new TestAuctionRepository();
        AuctionRulesSelfTest.Fixture fixture = new AuctionRulesSelfTest.Fixture(repository);
        MediaService media = fixture.management.media();
        Path directory = Files.createTempDirectory("media-ui-fixtures-");
        MediaSystemSelfTest.generateFixtures(media.config(), directory);
        AtomicInteger ticks = new AtomicInteger();
        Timeline heartbeat = fx(() -> { Timeline timeline = new Timeline(new KeyFrame(Duration.millis(25), event -> ticks.incrementAndGet())); timeline.setCycleCount(Timeline.INDEFINITE); timeline.play(); return timeline; });
        try (MediaHttpServer server = new MediaHttpServer(media,"127.0.0.1")) {
            server.start(); ProductMediaApi api = new ProductMediaApi(URI.create("http://127.0.0.1:"+media.port()), fixture.host::getSessionToken);
            ProductMediaEditor editor = fx(() -> { ProductMediaEditor value = new ProductMediaEditor(null,api,null,()->{}); value.show(); return value; });
            waitFor(() -> field(editor,"draft") != null,"editor gets an authenticated draft");
            fx(() -> { ((TextField)field(editor,"code")).setText("UI_MEDIA"); ((TextField)field(editor,"name")).setText("Study camera"); ((TextArea)field(editor,"description")).setText(TestMedia.DESCRIPTION); return null; });
            int before = ticks.get();
            fx(() -> { invoke(editor,"addFiles",new Class<?>[]{List.class,String.class,boolean.class},List.of(directory.resolve("large.png").toFile(),directory.resolve("fixture.webp").toFile()),"IMAGE",false); return null; });
            waitFor(() -> ready(editor,2),"multi-select images finish processing without blocking JavaFX");
            TestSupport.check(ticks.get()-before>=4,"FX heartbeat continues during large image upload/conversion");
            fx(() -> { invoke(editor,"addFiles",new Class<?>[]{List.class,String.class,boolean.class},List.of(directory.resolve("fixture.mp4").toFile()),"VIDEO",false); return null; });
            waitFor(() -> ready(editor,3),"video preview uploaded before product save");
            @SuppressWarnings("unchecked") ListView<Object> list = fx(() -> (ListView<Object>)field(editor,"list"));
            MediaGalleryPane preview = fx(() -> (MediaGalleryPane)field(editor,"preview"));
            fx(() -> { list.getSelectionModel().select(1); invoke(editor,"move",new Class<?>[]{boolean.class,int.class},true,-1); return null; });
            String cover = fx(() -> ((JsonNode)field(entries(editor).get(0),"media")).path("id").asText());
            waitFor(() -> preview.imagePane().getImage()!=null && preview.imagePane().getImage().getProgress()==1,"detail loads lazily");
            fx(() -> { preview.imagePane().zoom(2); TestSupport.equals(2.0,preview.imagePane().zoomLevel(),"zoom tool"); preview.imagePane().fit(); TestSupport.equals(1.0,preview.imagePane().zoomLevel(),"fit/reset tool"); return null; });
            fx(() -> { invoke(editor,"rotate",new Class<?>[]{}); return null; });
            waitFor(() -> !((boolean)field(editor,"busy")),"rotation completes asynchronously");
            TestSupport.equals(800,fx(() -> ((JsonNode)field(entries(editor).get(0),"media")).path("width").asInt()),"rotate preview dimensions");
            waitFor(() -> preview.imagePane().getImage()!=null && preview.imagePane().getImage().getProgress()==1,"rotated detail loaded");
            fx(() -> {
                preview.applyCss(); preview.layout(); MediaImagePane pane=preview.imagePane(); pane.setCropping(true);
                var bounds=((javafx.scene.image.ImageView)field(pane,"image")).getBoundsInParent();
                double left=bounds.getMinX()+bounds.getWidth()*0.3,top=bounds.getMinY()+bounds.getHeight()*0.3,
                        right=bounds.getMinX()+bounds.getWidth()*0.7,bottom=bounds.getMinY()+bounds.getHeight()*0.7;
                pane.fireEvent(mouse(pane,MouseEvent.MOUSE_PRESSED,left,top)); pane.fireEvent(mouse(pane,MouseEvent.MOUSE_DRAGGED,right,bottom));
                TestSupport.check(pane.crop()!=null,"user-dragged crop region maps to image"); invoke(editor,"crop",new Class<?>[]{}); return null;
            });
            waitFor(() -> !((boolean)field(editor,"busy")),"confirmed crop completes");
            TestSupport.check(fx(() -> ((JsonNode)field(entries(editor).get(0),"media")).path("width").asInt())<800,"confirmed crop changes preview");
            fx(() -> { invoke(editor,"reset",new Class<?>[]{}); return null; });
            waitFor(() -> !((boolean)field(editor,"busy")),"original reset completes");
            TestSupport.equals(1200,fx(() -> ((JsonNode)field(entries(editor).get(0),"media")).path("width").asInt()),"original source preserved across rotate/crop");
            waitFor(() -> preview.imagePane().getImage()!=null && preview.imagePane().getImage().getProgress()==1 && !preview.imagePane().getImage().isError(),"reset detail renders before screenshot");
            snapshot(fx(()->(Stage)field(editor,"stage")),"seller-media-editor.png");
            fx(() -> { preview.select(2); return null; });
            waitFor(() -> field(preview,"player")!=null && ((MediaPlayer)field(preview,"player")).getStatus()==MediaPlayer.Status.READY,"remote MP4 video actually decodes");
            fx(() -> { MediaPlayer player=(MediaPlayer)field(preview,"player"); TestSupport.check(player.isMute() && !player.isAutoPlay(),"video never autoplays sound"); player.play(); return null; });
            waitFor(() -> ((MediaPlayer)field(preview,"player")).getCurrentTime().toMillis()>300,"video playback advances");
            fx(() -> { MediaPlayer player=(MediaPlayer)field(preview,"player"); player.pause(); player.seek(Duration.millis(1000)); ((Slider)field(preview,"volume")).setValue(0.35); TestSupport.check(!player.isMute() && player.getVolume()==0.35,"explicit volume control"); return null; });
            waitFor(() -> ((MediaPlayer)field(preview,"player")).getCurrentTime().toMillis()>=900,"video seek works");
            snapshot(fx(()->(Stage)field(editor,"stage")),"seller-video-preview.png");
            fx(() -> { invoke(editor,"save",new Class<?>[]{}); return null; });
            waitFor(() -> !((Stage)field(editor,"stage")).isShowing(),"editor commits real product and closes");
            long productId=repository.findProductByCode("UI_MEDIA").orElseThrow().getProductId();
            List<ProductMedia> stored=repository.findProductMedia(productId);TestSupport.equals(3,stored.size(),"UI saves all image/video references");
            TestSupport.equals("fixture.webp",stored.get(0).name(),"UI cover/order persisted");
            TestSupport.check(!stored.get(0).id().equals(cover),"editing creates a safe new variant reference, not overwrites published files");
            Stage gallery=fx(() -> { MediaGalleryPane.show(null,api,productId,0,"Gallery"); return (Stage)javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing).findFirst().orElseThrow(); });
            MediaGalleryPane pane=fx(()->(MediaGalleryPane)gallery.getScene().getRoot());
            waitFor(()->pane.imagePane().getImage()!=null && pane.imagePane().getImage().getProgress()==1,"viewer gallery loads first image");
            fx(()->{pane.select(1);pane.imagePane().zoom(2);gallery.setFullScreenExitHint("");gallery.setFullScreen(true);return null;});
            waitFor(gallery::isFullScreen,"fullscreen gallery");fx(()->{gallery.setFullScreen(false);return null;});snapshot(gallery,"viewer-media-gallery.png");
            fx(()->{gallery.close();return null;});
            ProductMediaEditor cancelled=fx(()->{ProductMediaEditor value=new ProductMediaEditor(null,api,null,()->{});value.show();return value;});
            waitFor(()->field(cancelled,"draft")!=null,"cancel editor ready");
            fx(()->{invoke(cancelled,"addFiles",new Class<?>[]{List.class,String.class,boolean.class},List.of(directory.resolve("large.png").toFile()),"IMAGE",false);((Stage)field(cancelled,"stage")).close();return null;});
            Thread.sleep(1200);TestSupport.equals(1,repository.findProductsByOwner(1).size(),"cancelled editor never saves a partial product");
            System.out.println("[PASS] MediaUiSelfTest: multi-select/cover/order, real upload + FX heartbeat, lazy preview, zoom/fit, drag crop/rotation/reset, muted video playback/seek/volume, gallery/fullscreen/save/cancel");
        } finally {
            fx(()->{heartbeat.stop();new ArrayList<>(javafx.stage.Window.getWindows()).forEach(window->window.hide());return null;}); media.close();Platform.exit();
        }
    }
    private static MouseEvent mouse(MediaImagePane pane,javafx.event.EventType<MouseEvent> type,double horizontal,double vertical){return new MouseEvent(pane,pane,type,horizontal,vertical,horizontal,vertical,MouseButton.PRIMARY,1,false,false,false,false,true,false,false,false,false,false,new PickResult(pane,new javafx.geometry.Point3D(horizontal,vertical,0),1));}
    private static List<?> entries(ProductMediaEditor editor)throws Exception{return(List<?>)field(editor,"entries");}
    private static boolean ready(ProductMediaEditor editor,int count)throws Exception{
        List<?> entries=entries(editor);if(entries.size()!=count || ((boolean)field(editor,"busy"))){return false;}
        for(Object entry:entries){if(field(entry,"media")==null){throw new AssertionError("File upload failed: "+field(entry,"name")+" | "+field(entry,"state"));}}return true;
    }
    private static Object field(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static Object invoke(Object object,String name,Class<?>[] types,Object...arguments)throws Exception{Method method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object,arguments);}
    private static <Result>Result fx(Callable<Result> action)throws Exception{CompletableFuture<Result> result=new CompletableFuture<>();Platform.runLater(()->{try{result.complete(action.call());}catch(Throwable error){result.completeExceptionally(error);}});return result.get(15,TimeUnit.SECONDS);}
    private static void waitFor(Callable<Boolean> check,String description)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);while(System.nanoTime()<end){if(fx(check)){return;}Thread.sleep(50);}throw new AssertionError("Timeout: "+description);}
    private static void snapshot(Stage stage,String filename)throws Exception{
        BufferedImage bitmap=fx(()->{var image=stage.getScene().snapshot(null);int width=(int)image.getWidth(),height=(int)image.getHeight();BufferedImage result=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);for(int row=0;row<height;row++){for(int column=0;column<width;column++){result.setRGB(column,row,image.getPixelReader().getArgb(column,row));}}return result;});
        Path directory=Path.of("target/media-ui-preview");Files.createDirectories(directory);ImageIO.write(bitmap,"png",directory.resolve(filename).toFile());
    }
}
