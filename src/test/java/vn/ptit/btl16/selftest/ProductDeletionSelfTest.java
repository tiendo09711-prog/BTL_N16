package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.*;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.model.*;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.db.DatabaseSchema;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

/** Permanent catalog removal, relational history, ownership, races and media rollback in an isolated DB. */
public final class ProductDeletionSelfTest {
    private static long sequence = 70000;
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String oldConfig=System.getProperty("btl16.server.config"), oldData=System.getProperty("btl16.dataDir");
        Path data=Files.createTempDirectory("btl16-product-delete-"); Path config=data.resolve("server.properties");
        Files.writeString(config,"db.driver=org.h2.Driver\ndb.name=deletion\ndb.user=sa\ndb.password=\ndb.autoInitialize=false\n");
        System.setProperty("btl16.server.config",config.toString()); System.setProperty("btl16.dataDir",data.toString());
        try {
            var factory=new JdbcConnectionFactory(ServerConfig.loadDefault()); DatabaseSchema.initialize(factory);
            sql(factory,"INSERT INTO users(user_id,username,display_name,password_hash,password_salt,password_iterations,active,wallet_balance) VALUES(1,'demo','Host','x','x',30000,TRUE,1000000),(2,'alice','Alice','x','x',30000,TRUE,1000000),(3,'bob','Bob','x','x',30000,TRUE,1000000)");
            var repository=new JdbcAuctionRepository(factory);
            long unused=product(repository); var images=repository.findProductMedia(unused);
            TestSupport.check(!images.isEmpty(),"unused fixture has real media");
            var serverConfig=ServerConfig.forTransportTests(0,0); var hasher=new Pbkdf2PasswordHasher(serverConfig.getPasswordIterations());
            try (var server=ServerApplication.createForTests(serverConfig,TestUserRepository.withDemoUsers(hasher),repository,hasher)) {
                server.start();
                try (var host=client(server.getBoundPort()); var other=client(server.getBoundPort()); var anonymous=client(server.getBoundPort())) {
                    var api=new AuctionApi(host); var otherApi=new AuctionApi(other);
                    ok(new AccountApi(host).login("demo","demo123")); ok(new AccountApi(other).login("alice","alice123"));
                    fail(new AuctionApi(anonymous).deleteProduct(unused),"AUTH_REQUIRED"); fail(otherApi.deleteProduct(unused),"AUCTION_FORBIDDEN");
                    ok(api.deleteProduct(unused)); TestSupport.check(repository.findProductById(unused).isEmpty(),"unused product physically deleted");
                    for (var image:images) { TestSupport.check(repository.findMedia(image.id()).isEmpty(),"metadata deleted"); TestSupport.equals(0L,count(factory,"SELECT COUNT(*) FROM media_content WHERE media_id='"+image.id()+"'"),"payload cascades"); }
                    fail(api.deleteProduct(unused),"PRODUCT_NOT_FOUND");
                    for (boolean scheduled:List.of(false,true)) {
                        long id=product(repository); long room=room(repository,id,scheduled);
                        fail(api.deleteProduct(id),"PRODUCT_IN_USE"); TestSupport.check(!repository.isProductDeleted(id),"running and scheduled products unchanged");
                        repository.cancelAuction(new CancelAuctionCommit(room,Instant.now())); ok(api.deleteProduct(id));
                        TestSupport.check(repository.isProductDeleted(id),"cancelled history product permanently removed from catalog");
                        fail(api.restoreProduct(id),"PRODUCT_NOT_FOUND");
                    }
                    long sold=product(repository); long auction=room(repository,sold,false); var current=repository.findAuctionById(auction).orElseThrow();
                    repository.commitAcceptedBid(new BidCommit(auction,current.getCurrentPrice(),2,"alice",new BigDecimal("110"),++sequence,Instant.now(),current.getEndTime())); close(factory,repository,auction);
                    var receipt=repository.personalReceipt(2,auction); var revenue=repository.topSellers(); var media=repository.findProductMedia(sold);
                    repository.deactivateProduct(sold,1); ok(api.deleteProduct(sold));
                    TestSupport.check(repository.findProductsByOwner(1).stream().noneMatch(p->p.getProductId()==sold),"inactive sold item is deletable and disappears");
                    TestSupport.equals(receipt,repository.personalReceipt(2,auction),"winner receipt, price, thumbnail and ledger retained");
                    TestSupport.equals(revenue,repository.topSellers(),"deletion does not reduce earned revenue or ranking");
                    TestSupport.equals(media,repository.findProductMedia(sold),"historic media retained");
                    fail(api.restoreProduct(sold),"PRODUCT_NOT_FOUND"); fail(api.deleteProduct(sold),"PRODUCT_NOT_FOUND");
                    fail(api.updateProduct(sold,"DELETED_EDIT","Edited",TestMedia.DESCRIPTION),"PRODUCT_NOT_FOUND");
                    denied(()->repository.createAuction(commit(sold,false)),"PRODUCT_NOT_FOUND");
                    denied(()->repository.restoreProduct(sold,1),"PRODUCT_NOT_FOUND");
                    var response=ok(api.myProducts()); TestSupport.check(vn.ptit.btl16.client.model.ClientWireParser.products(response.getWireMessage().getData()).stream().noneMatch(p->p.getProductId()==sold),"network catalog excludes deleted products");
                    DatabaseSchema.initialize(factory); DatabaseSchema.initialize(factory);
                    TestSupport.check(new JdbcAuctionRepository(factory).findProductsByOwner(1).stream().noneMatch(p->p.getProductId()==sold),"deletion survives restart");
                    TestSupport.equals(receipt,repository.personalReceipt(2,auction),"initialization never alters historical settlement");
                }
            }
            // Inject a dependent row to prove failure rolls media and product deletion back together.
            long guarded=product(repository); var original=repository.findProductMedia(guarded);
            sql(factory,"CREATE TABLE deletion_guard(product_id BIGINT PRIMARY KEY,FOREIGN KEY(product_id) REFERENCES products(product_id))"); sql(factory,"INSERT INTO deletion_guard VALUES("+guarded+")");
            try { repository.deleteProduct(guarded,1); throw new AssertionError("expected FK deletion failure"); } catch (AuctionRepositoryException expected) { }
            TestSupport.equals(original,repository.findProductMedia(guarded),"failed deletion restores media metadata");
            for(var image:original) { TestSupport.check(count(factory,"SELECT COUNT(*) FROM media_content WHERE media_id='"+image.id()+"'")>0,"failed deletion restores image bytes"); }
            sql(factory,"DELETE FROM deletion_guard"); repository.deleteProduct(guarded,1);
            // A new room and deletion share the product lock: exactly one can succeed.
            for(int index=0;index<4;index++) {
                long id=product(repository); var commit=commit(id,false); CountDownLatch gate=new CountDownLatch(1);
                var delete=CompletableFuture.supplyAsync(()->race(gate,()->repository.deleteProduct(id,1)));
                var create=CompletableFuture.supplyAsync(()->race(gate,()->repository.createAuction(commit))); gate.countDown();
                boolean removed=delete.get(15,TimeUnit.SECONDS), opened=create.get(15,TimeUnit.SECONDS);
                TestSupport.check(removed!=opened,"create/delete cannot both commit or both fail");
                TestSupport.equals(opened,repository.hasOpenAuctionForProduct(id),"no deleted product has an open room");
            }
            System.out.println("[PASS] ProductDeletionSelfTest: permanent deletion/auth/ownership, media cascade+rollback, active/scheduled refusal, sold/cancelled history+revenue, stale edit/restore rejection, restart and room/delete races");
        } finally { restore("btl16.server.config",oldConfig); restore("btl16.dataDir",oldData); }
    }
    private static long product(JdbcAuctionRepository repository) { String code="DEL_"+ ++sequence; return TestMedia.create(repository,new CreateProductCommit(1,"demo",code,code,TestMedia.DESCRIPTION,Instant.now())).getProductId(); }
    private static CreateAuctionCommit commit(long product,boolean scheduled) { Instant now=Instant.now().truncatedTo(ChronoUnit.MILLIS); return new CreateAuctionCommit(1,"demo",product,new BigDecimal("100"),new BigDecimal("10"),now.plusSeconds(scheduled?600:-1),now.plusSeconds(scheduled?1200:600),RoomVisibility.PUBLIC,"","",null,false); }
    private static long room(JdbcAuctionRepository repository,long product,boolean scheduled) { return repository.createAuction(commit(product,scheduled)).getAuctionId(); }
    private static void close(JdbcConnectionFactory factory,JdbcAuctionRepository repository,long room) throws Exception { Instant end=Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS); try(var connection=factory.openDatabase();var q=connection.prepareStatement("UPDATE auctions SET end_time=? WHERE auction_id=?")){q.setTimestamp(1,Timestamp.from(end));q.setLong(2,room);q.executeUpdate();} var a=repository.findAuctionById(room).orElseThrow();repository.closeAuction(new CloseAuctionCommit(room,a.getCurrentWinnerId(),a.getCurrentWinnerUsername(),a.getCurrentPrice(),end,Instant.now())).orElseThrow(); }
    private static NetworkClient client(int port) throws Exception {var client=new NetworkClient(2097152,5000,10000);client.connect("127.0.0.1",port);return client;}
    private static ApiResponse ok(CompletableFuture<ApiResponse> request)throws Exception{var r=request.get(10,TimeUnit.SECONDS);TestSupport.check(r.isSuccess(),r.getErrorCode()+": "+r.getMessageText());return r;}
    private static void fail(CompletableFuture<ApiResponse> request,String code)throws Exception{TestSupport.equals(code,request.get(10,TimeUnit.SECONDS).getErrorCode(),"deletion API refusal");}
    private static void denied(Runnable action,String code){try{action.run();throw new AssertionError("expected "+code);}catch(AuctionException error){TestSupport.equals(code,error.getErrorCode().name(),"repository rejects stale mutation");}}
    private static boolean race(CountDownLatch gate,Runnable action){try{gate.await();action.run();return true;}catch(AuctionException rejected){return false;}catch(InterruptedException error){throw new CompletionException(error);}}
    private static void sql(JdbcConnectionFactory f,String sql)throws Exception{try(var c=f.openDatabase();var s=c.createStatement()){s.executeUpdate(sql);}}
    private static long count(JdbcConnectionFactory f,String sql)throws Exception{try(var c=f.openDatabase();var s=c.createStatement();var rows=s.executeQuery(sql)){rows.next();return rows.getLong(1);}}
    private static void restore(String key,String value){if(value==null){System.clearProperty(key);}else{System.setProperty(key,value);}}
}
