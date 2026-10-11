package vn.ptit.btl16.selftest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.client.service.ProductMediaApi;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.model.RoomVisibility;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.db.DatabaseSchema;
import vn.ptit.btl16.server.media.*;
import vn.ptit.btl16.server.session.UserSession;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.Socket;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MediaSystemSelfTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
    private static Path fixtures;

    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String previous = System.getProperty("btl16.server.config"), previousData = System.getProperty("btl16.dataDir");
        Path directory = Files.createTempDirectory("btl16-media-jdbc-").toRealPath();
        String database = "btl16_verify_media_" + UUID.randomUUID().toString().replace("-", "");
        Path settings = directory.resolve("server.properties");
        Files.writeString(settings, "db.driver=org.h2.Driver\ndb.name=" + database + "\ndb.user=sa\ndb.password=\ndb.autoInitialize=false\nmedia.port=0\n");
        System.setProperty("btl16.server.config", settings.toString()); System.setProperty("btl16.dataDir", directory.toString());
        try {
            ServerConfig config = ServerConfig.loadDefault();
            TestSupport.check(config.getDbName().equals(database) && config.databaseJdbcUrl().startsWith("jdbc:h2:file:" + directory.toString().replace('\\', '/') + "/"), "refuse non-isolated JDBC");
            JdbcConnectionFactory factory = new JdbcConnectionFactory(config); DatabaseSchema.initialize(factory);
            try (Connection connection = factory.openDatabase(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users(user_id, username, display_name, password_hash, password_salt, password_iterations, active) VALUES "
                        + "(1, 'host', 'Host', 'test', 'test', 30000, TRUE), (2, 'buyer', 'Buyer', 'test', 'test', 30000, TRUE), (3, 'viewer', 'Viewer', 'test', 'test', 30000, TRUE)");
            }
            fixtures = directory.resolve("fixtures"); Files.createDirectory(fixtures);
            MediaConfig mediaConfig = MediaConfig.load(false);
            TestSupport.check(mediaConfig.root().startsWith(directory), "refuse a media root outside isolated test data");
            generateFixtures(mediaConfig, fixtures);
            JdbcAuctionRepository repository = new JdbcAuctionRepository(factory);
            AuctionRulesSelfTest.Fixture fixture = new AuctionRulesSelfTest.Fixture(repository);
            long persistedId; List<ProductMedia> persisted;
            try (MediaService media = new MediaService(mediaConfig, repository, fixture.sessions, fixture.management);
                 MediaHttpServer server = new MediaHttpServer(media, "127.0.0.1")) {
                fixture.management.setMediaService(media); server.start();
                validate(media, fixture, repository);
                formats(media, fixture.host);
                cancellation(media, fixture.host);
                limits(mediaConfig, repository);
                migration(media, fixture, repository, factory);
                ProductMediaApi api = new ProductMediaApi(URI.create("http://127.0.0.1:" + media.port()), fixture.host::getSessionToken);
                String draft = api.begin(0).get(10, TimeUnit.SECONDS);
                JsonNode first = api.upload(draft, fixtures.resolve("fixture.png"), "IMAGE", progress -> { }).get(30, TimeUnit.SECONDS);
                JsonNode second = api.upload(draft, fixtures.resolve("fixture.webp"), "IMAGE", progress -> { }).get(30, TimeUnit.SECONDS);
                JsonNode video = api.upload(draft, fixtures.resolve("fixture.mp4"), "VIDEO", progress -> { }).get(30, TimeUnit.SECONDS);
                JsonNode transformed = api.transform(draft, first.path("id").asText(), 1, 0, 0, 0.5, 1).get(30, TimeUnit.SECONDS);
                TestSupport.equals(400, transformed.path("width").asInt(), "explicit crop after rotation width");
                TestSupport.equals(1200, transformed.path("height").asInt(), "rotation height");
                persistedId = api.commit(draft, "MEDIA_HTTP", "Media product", TestMedia.DESCRIPTION,
                        List.of(second.path("id").asText(), transformed.path("id").asText(), video.path("id").asText())).get(30, TimeUnit.SECONDS).path("productId").asLong();
                persisted = repository.findProductMedia(persistedId);
                TestSupport.equals(second.path("id").asText(), persisted.get(0).id(), "selected cover and order persisted");
                TestSupport.equals(3, persisted.size(), "all selected metadata persisted");
                try (Connection connection = factory.openDatabase(); PreparedStatement statement = connection.prepareStatement("SELECT image_data FROM products WHERE product_id = ?")) {
                    statement.setLong(1, persistedId); try (ResultSet rows = statement.executeQuery()) { rows.next(); TestSupport.check(rows.getBytes(1) == null, "new image/video never stored as BLOB"); }
                }
                access(media, fixture, repository, api, persistedId);
                concurrency(media, fixture, repository);
                interruptedHttp(media, fixture.host);
                Path orphan = mediaConfig.root().resolve("products").resolve(UUID.randomUUID().toString()); Files.createDirectory(orphan); Files.writeString(orphan.resolve("source"), "orphan");
                java.nio.file.attribute.FileTime old = java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(90000));
                Files.setLastModifiedTime(orphan, old);
                for (ProductMedia item : persisted) { Files.setLastModifiedTime(mediaConfig.root().resolve("products").resolve(item.id()), old); }
                media.cleanupUnused();
                TestSupport.check(!Files.exists(orphan), "old proven orphan cleaned"); media.requireReady(persistedId);
            }
            AuctionRulesSelfTest.Fixture restarted = new AuctionRulesSelfTest.Fixture(new JdbcAuctionRepository(new JdbcConnectionFactory(config)));
            try (MediaService media = new MediaService(mediaConfig, repository, restarted.sessions, restarted.management);
                 MediaHttpServer server = new MediaHttpServer(media, "127.0.0.1")) {
                restarted.management.setMediaService(media); server.start();
                TestSupport.equals(persisted, repository.findProductMedia(persistedId), "metadata survives reopen"); media.requireReady(persistedId);
                Map<String, Object> manifest = media.manifest(restarted.host, persistedId, 0);
                JsonNode body = JSON.valueToTree(manifest);
                for (JsonNode item : body.path("media")) { TestSupport.equals(200, get(media, item.path("thumbnail").asText(), null).statusCode(), "thumbnail available after restart"); }
                TestSupport.equals(206, get(media, body.path("media").get(2).path("video").asText(), "bytes=0-1023").statusCode(), "video range after restart");
            }
            System.out.println("[PASS] MediaSystemSelfTest: real formats/orientation/crop, requirements, quotas, cancel/interrupted HTTP, private/block/tickets/ranges, JDBC BLOB migration, file persistence and races");
        } finally { property("btl16.server.config", previous); property("btl16.dataDir", previousData); }
    }

    static void generateFixtures(MediaConfig config, Path directory) throws Exception {
        for (String extension : List.of("jpg", "png", "bmp", "gif", "tiff", "webp")) {
            tool(config.ffmpeg(), "-f", "lavfi", "-i", "testsrc2=size=1200x800:rate=1", "-frames:v", "1", directory.resolve("fixture." + extension).toString());
        }
        tool(config.ffmpeg(), "-f", "lavfi", "-i", "color=c=red:size=5200x4400:rate=1", "-frames:v", "1", directory.resolve("large.png").toString());
        tool(config.ffmpeg(), "-f", "lavfi", "-i", "color=c=red:size=6000x4100:rate=1", "-frames:v", "1", directory.resolve("over-pixels.png").toString());
        for (String extension : List.of("mp4", "mov", "webm", "mkv", "avi")) {
            boolean webm = extension.equals("webm");
            tool(config.ffmpeg(), "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=15", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100", "-t", "2",
                    "-c:v", webm ? "libvpx-vp9" : "mpeg4", "-c:a", webm ? "libopus" : "aac", directory.resolve("fixture." + extension).toString());
        }
        BufferedImage rgb = new BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB); var graphics = rgb.createGraphics(); graphics.setColor(java.awt.Color.RED); graphics.fillRect(0, 0, 160, 100); graphics.dispose();
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream(); ImageIO.write(rgb, "jpg", jpeg);
        byte[] exif = { 'E','x','i','f',0,0,'I','I',42,0,8,0,0,0,1,0,0x12,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0 };
        try (OutputStream output = Files.newOutputStream(directory.resolve("oriented.jpg"))) {
            byte[] original = jpeg.toByteArray(); output.write(original, 0, 2); output.write(new byte[]{(byte)255,(byte)225,0,(byte)(exif.length+2)}); output.write(exif); output.write(original, 2, original.length-2);
        }
        BufferedImage transparent = new BufferedImage(160,100,BufferedImage.TYPE_INT_ARGB); ImageIO.write(transparent,"png",directory.resolve("transparent.png").toFile());
    }
    static void tool(Path tool, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of(tool.toString(), "-v", "error", "-y", "-threads", "2")); command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start(); String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        TestSupport.check(process.waitFor(90, TimeUnit.SECONDS) && process.exitValue() == 0, "valid fixture generation: " + output);
    }
    private static ProductMedia upload(MediaService media, UserSession session, String draft, Path file, String kind) throws Exception {
        try (InputStream input = Files.newInputStream(file)) { return media.upload(session, draft, kind, "../../" + file.getFileName(), input, Files.size(file), 0, 0, 0, 1, 1); }
    }
    private static void validate(MediaService media, AuctionRulesSelfTest.Fixture fixture, AuctionRepository repository) throws Exception {
        ProductRestorationSelfTest.expect(ErrorCode.PRODUCT_INCOMPLETE, () -> fixture.management.createProduct(fixture.host, "NO_IMAGE", "Missing", TestMedia.DESCRIPTION));
        ProductRestorationSelfTest.expect(ErrorCode.AUTH_REQUIRED, () -> media.begin(null, 0));
        String draft = media.begin(fixture.host, 0); List<String> images = new ArrayList<>();
        for (int index = 0; index < 6; index++) { images.add(upload(media, fixture.host, draft, fixtures.resolve("fixture.png"), "IMAGE").id()); }
        expect(ErrorCode.PRODUCT_INCOMPLETE, () -> media.commit(fixture.host, draft, "TOO_MANY", "Too many", TestMedia.DESCRIPTION, images));
        expect(ErrorCode.PRODUCT_INCOMPLETE, () -> media.commit(fixture.host, draft, "SHORT_DESC", "Short", "  " + "x".repeat(49) + "  ", List.of(images.get(0))));
        expect(ErrorCode.PRODUCT_INCOMPLETE, () -> media.commit(fixture.host, draft, "EMPTY_DESC", "Empty", " ", List.of(images.get(0))));
        expect(ErrorCode.PRODUCT_INCOMPLETE, () -> media.commit(fixture.host, draft, "LONG_DESC", "Long", "x".repeat(2001), List.of(images.get(0))));
        String video1 = upload(media, fixture.host, draft, fixtures.resolve("fixture.mp4"), "VIDEO").id();
        String video2 = upload(media, fixture.host, draft, fixtures.resolve("fixture.mp4"), "VIDEO").id();
        expect(ErrorCode.PRODUCT_INCOMPLETE, () -> media.commit(fixture.host, draft, "NO_PHOTO", "Video only", TestMedia.DESCRIPTION, List.of(video1)));
        expect(ErrorCode.PRODUCT_INCOMPLETE, () -> media.commit(fixture.host, draft, "TWO_VIDEO", "Two videos", TestMedia.DESCRIPTION, List.of(images.get(0), video1, video2)));
        expect(ErrorCode.AUCTION_FORBIDDEN, () -> media.commit(fixture.buyer, draft, "STOLEN", "Stolen", TestMedia.DESCRIPTION, List.of(images.get(0))));
        media.cancel(fixture.host, draft);
        TestSupport.equals(0, repository.findProductsByOwner(1).size(), "invalid drafts never create products");
    }
    private static void formats(MediaService media, UserSession owner) throws Exception {
        for (String extension : List.of("jpg", "png", "bmp", "gif", "tiff", "webp", "mp4", "mov", "webm", "mkv", "avi")) {
            String draft = media.begin(owner, 0); boolean video = List.of("mp4", "mov", "webm", "mkv", "avi").contains(extension);
            ProductMedia item = upload(media, owner, draft, fixtures.resolve("fixture." + extension), video ? "VIDEO" : "IMAGE");
            TestSupport.check(item.width() > 0 && !item.name().contains("/"), "content decoded and filename sanitized: " + extension);
            JsonNode view = JSON.valueToTree(media.uploaded(owner, draft, item));
            Path thumbnail = media.readable(item.id(), "thumbnail", ticket(view.path("thumbnail").asText()));
            BufferedImage preview = ImageIO.read(thumbnail.toFile()); TestSupport.check(preview.getWidth() <= 360 && preview.getHeight() <= 360, "separate small thumbnail");
            if (!video) { TestSupport.equals(1200, item.width(), "detail remains useful for zoom"); }
            media.cancel(owner, draft);
        }
        for (String filename : List.of("large.png", "oriented.jpg", "transparent.png")) {
            String draft = media.begin(owner, 0); ProductMedia item = upload(media, owner, draft, fixtures.resolve(filename), "IMAGE");
            if (filename.equals("large.png")) { TestSupport.equals(4096, Math.max(item.width(), item.height()), "large image resized without automatic crop"); }
            if (filename.equals("oriented.jpg")) { TestSupport.equals(100, item.width(), "EXIF direction corrected"); TestSupport.equals(160, item.height(), "EXIF height corrected"); }
            if (filename.equals("transparent.png")) {
                JsonNode view = JSON.valueToTree(media.uploaded(owner, draft, item)); BufferedImage detail = ImageIO.read(media.readable(item.id(), "detail", ticket(view.path("detail").asText())).toFile());
                TestSupport.check((detail.getRGB(10,10) & 0xffffff) > 0xf0f0f0, "transparent image flattened on white");
            }
            media.cancel(owner, draft);
        }
        String draft = media.begin(owner, 0);
        expect(ErrorCode.MEDIA_LIMIT, () -> upload(media, owner, draft, fixtures.resolve("over-pixels.png"), "IMAGE"));
        expect(ErrorCode.MEDIA_INVALID, () -> media.upload(owner, draft, "IMAGE", "fake.png", new ByteArrayInputStream(new byte[]{(byte)137,80,78,71,13,10,26,10,0,0,0,0}), 12, 0,0,0,1,1));
        expect(ErrorCode.MEDIA_INVALID, () -> media.upload(owner, draft, "VIDEO", "fake.mp4", new ByteArrayInputStream("broken video".getBytes()), 12, 0,0,0,1,1));
        expect(ErrorCode.MEDIA_INVALID, () -> upload(media, owner, draft, fixtures.resolve("fixture.mp4"), "IMAGE"));
        expect(ErrorCode.MEDIA_LIMIT, () -> media.upload(owner, draft, "IMAGE", "large.png", InputStream.nullInputStream(), media.config().imageBytes()+1, 0,0,0,1,1));
        media.cancel(owner, draft);
    }
    private static void cancellation(MediaService media, UserSession owner) throws Exception {
        String draft = media.begin(owner, 0); BlockingInput input = new BlockingInput();
        CompletableFuture<?> upload = CompletableFuture.runAsync(() -> { try { media.upload(owner, draft, "IMAGE", "cancel.png", input, -1,0,0,0,1,1); throw new AssertionError("cancel accepted"); } catch (IOException | AuctionException expected) { } });
        TestSupport.check(input.reading.await(5, TimeUnit.SECONDS), "streaming upload started"); media.cancel(owner, draft); upload.get(5, TimeUnit.SECONDS);
        try (var files = Files.list(media.config().root().resolve("staging"))) { TestSupport.equals(0L, files.count(), "cancel removes all unfinished files"); }
    }
    private static void limits(MediaConfig config, AuctionRepository repository) throws Exception {
        AuctionRulesSelfTest.Fixture fixture = new AuctionRulesSelfTest.Fixture(repository);
        MediaConfig limited = new MediaConfig(Files.createTempDirectory("media-limits-"),config.ffmpeg(),config.ffprobe(),0,1024,1024,24000000,8500000,1<<20,1<<16,1,1,4096,1);
        try (MediaService media = new MediaService(limited, repository, fixture.sessions, fixture.management)) {
            String draft = media.begin(fixture.host,0);
            expect(ErrorCode.MEDIA_LIMIT, () -> upload(media,fixture.host,draft,fixtures.resolve("fixture.png"),"IMAGE"));
            BlockingInput input = new BlockingInput(); long start = System.nanoTime();
            expect(ErrorCode.MEDIA_LIMIT, () -> media.upload(fixture.host,draft,"IMAGE","slow",input,-1,0,0,0,1,1));
            TestSupport.check(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-start)<4,"stream upload time bounded"); media.cancel(fixture.host,draft);
        }
        MediaConfig full = new MediaConfig(Files.createTempDirectory("media-full-"),config.ffmpeg(),config.ffprobe(),0,config.imageBytes(),config.videoBytes(),24000000,8500000,1024,2048,180,180,4096,1);
        try (MediaService media = new MediaService(full,repository,fixture.sessions,fixture.management)) {
            String draft=media.begin(fixture.host,0); expect(ErrorCode.MEDIA_STORAGE_FULL,()->upload(media,fixture.host,draft,fixtures.resolve("transparent.png"),"IMAGE")); media.cancel(fixture.host,draft);
        }
    }
    private static void migration(MediaService media, AuctionRulesSelfTest.Fixture fixture, AuctionRepository repository, JdbcConnectionFactory factory) throws Exception {
        byte[] source=Files.readAllBytes(fixtures.resolve("fixture.png"));
        Product inserted=repository.createProduct(new CreateProductCommit(1,"host","LEGACY_MEDIA","Old item","",Instant.now(),source,"image/png","old.png"));
        Product legacy=repository.findProductById(inserted.getProductId()).orElseThrow();
        TestSupport.equals(List.of(),media.migrateLegacy(),"legacy migration successful");
        Product unchanged=repository.findProductById(legacy.getProductId()).orElseThrow();
        TestSupport.equals(legacy.getDescription(),unchanged.getDescription(),"never invent a legacy description"); TestSupport.equals(legacy.getUpdatedAt(),unchanged.getUpdatedAt(),"migration preserves product timestamp");
        TestSupport.check(Arrays.equals(source,repository.findProductImage(legacy.getProductId()).orElseThrow().getData()),"legacy original BLOB retained");
        TestSupport.equals(1,repository.findProductMedia(legacy.getProductId()).size(),"verified file metadata created");
        expect(ErrorCode.PRODUCT_INCOMPLETE,()->fixture.management.createAuction(fixture.host,legacy.getProductId(),new BigDecimal("100"),new BigDecimal("10"),1));
        fixture.management.updateProduct(fixture.host,legacy.getProductId(),"LEGACY_MEDIA","Old item",TestMedia.DESCRIPTION);
        var auction=fixture.management.createAuction(fixture.host,legacy.getProductId(),new BigDecimal("100"),new BigDecimal("10"),1);
        fixture.management.cancelAuction(fixture.host,auction.getAuctionId());
        try (Connection connection=factory.openDatabase();PreparedStatement statement=connection.prepareStatement("UPDATE products SET description = '' WHERE product_id = ?")) { statement.setLong(1,legacy.getProductId());statement.executeUpdate(); }
        expect(ErrorCode.PRODUCT_FROZEN,()->fixture.management.updateProduct(fixture.host,legacy.getProductId(),"LEGACY_MEDIA","Old",TestMedia.DESCRIPTION));
        expect(ErrorCode.PRODUCT_INCOMPLETE,()->fixture.management.createAuction(fixture.host,legacy.getProductId(),new BigDecimal("100"),new BigDecimal("10"),1));
    }
    private static void access(MediaService media,AuctionRulesSelfTest.Fixture fixture,AuctionRepository repository,ProductMediaApi owner,long productId)throws Exception {
        expect(ErrorCode.AUCTION_FORBIDDEN,()->media.begin(fixture.buyer,productId));
        var auction=fixture.management.createAuction(fixture.host,productId,new BigDecimal("100"),new BigDecimal("10"),1,RoomVisibility.PRIVATE,"room1234");
        expect(ErrorCode.PRODUCT_FROZEN,()->media.begin(fixture.host,productId));
        expect(ErrorCode.ROOM_ACCESS_DENIED,()->media.manifest(fixture.buyer,productId,auction.getAuctionId()));
        fixture.management.joinAuction(fixture.buyer,"buyer",auction.getAuctionId(),"room1234");
        JsonNode view=JSON.valueToTree(media.manifest(fixture.buyer,productId,auction.getAuctionId()));
        for(JsonNode item:view.path("media")) { for(String variant:List.of("thumbnail","original",item.path("kind").asText().equals("IMAGE")?"detail":"video")) { TestSupport.equals(200,get(media,item.path(variant).asText(),null).statusCode(),"authorized "+variant); } }
        String video=view.path("media").get(2).path("video").asText();
        HttpResponse<byte[]> partial=get(media,video,"bytes=0-1023");TestSupport.equals(206,partial.statusCode(),"range response");TestSupport.equals(1024,partial.body().length,"bounded range");
        TestSupport.equals(206,get(media,video,"bytes=-100").statusCode(),"suffix range"); TestSupport.equals(416,get(media,video,"bytes=999999999-").statusCode(),"invalid range");
        TestSupport.equals(400,get(media,video.substring(0,video.indexOf('?')),null).statusCode(),"direct access without ticket denied");
        fixture.management.kickUser(fixture.host,auction.getAuctionId(),"buyer");
        for(JsonNode item:view.path("media")) { for(String variant:List.of("thumbnail","original",item.path("kind").asText().equals("IMAGE")?"detail":"video")) { TestSupport.equals(400,get(media,item.path(variant).asText(),null).statusCode(),"issued ticket revoked on kick: "+variant); } }
        expect(ErrorCode.ROOM_ACCESS_DENIED,()->media.manifest(fixture.buyer,productId,auction.getAuctionId()));
        JsonNode viewer=JSON.valueToTree(media.manifest(fixture.host,productId,auction.getAuctionId()));
        fixture.sessions.detachByConnection("host");TestSupport.equals(401,get(media,viewer.path("media").get(0).path("original").asText(),null).statusCode(),"detached session ticket denied");
        fixture.sessions.resumeSession(fixture.host.getSessionToken(),"host","local");
        TestSupport.check(repository.isAuctionUserBlocked(auction.getAuctionId(),2),"ban persisted");
    }
    private static void concurrency(MediaService media,AuctionRulesSelfTest.Fixture fixture,AuctionRepository repository)throws Exception {
        Product product=TestMedia.create(fixture.management,fixture.host,"MEDIA_RACE","Race",TestMedia.DESCRIPTION);
        String draft=media.begin(fixture.host,product.getProductId());String id=upload(media,fixture.host,draft,fixtures.resolve("fixture.png"),"IMAGE").id();
        CountDownLatch gate=new CountDownLatch(1);
        CompletableFuture<Boolean> edit=CompletableFuture.supplyAsync(()-> { try { gate.await();media.commit(fixture.host,draft,"MEDIA_RACE","Changed",TestMedia.DESCRIPTION,List.of(id));return true; } catch(AuctionException exception) { TestSupport.equals(ErrorCode.PRODUCT_FROZEN,exception.getErrorCode(),"racing edit frozen");return false; } catch(Exception exception) { throw new CompletionException(exception); } });
        CompletableFuture<?> create=CompletableFuture.runAsync(()-> { try {gate.await();fixture.management.createAuction(fixture.host,product.getProductId(),new BigDecimal("100"),new BigDecimal("10"),1);}catch(Exception exception){throw new CompletionException(exception);} });
        gate.countDown();boolean edited=edit.get(20,TimeUnit.SECONDS);create.get(20,TimeUnit.SECONDS);
        var auction=repository.findAllAuctions().stream().filter(item->item.getProduct().getProductId()==product.getProductId()).findFirst().orElseThrow();
        TestSupport.equals(edited?"Changed":"Race",auction.getProduct().getName(),"published content matches serialized save/create");
        expect(ErrorCode.PRODUCT_FROZEN,()->fixture.management.updateProduct(fixture.host,product.getProductId(),"MEDIA_RACE","Forbidden",TestMedia.DESCRIPTION)); media.cancel(fixture.host,draft);
    }
    private static void interruptedHttp(MediaService media,UserSession owner)throws Exception {
        String draft=media.begin(owner,0);
        try(Socket socket=new Socket("127.0.0.1",media.port())){
            String request="POST /v1/drafts/"+draft+"/files?kind=IMAGE&name=partial.png HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer "+owner.getSessionToken()+"\r\nContent-Length: 80000\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(TestMedia.png());socket.getOutputStream().flush();socket.shutdownOutput();
        }
        Thread.sleep(300); media.cancel(owner,draft);
        try(var files=Files.list(media.config().root().resolve("staging"))){TestSupport.equals(0L,files.count(),"interrupted HTTP leaves no staged file");}
    }
    private static HttpResponse<byte[]> get(MediaService media,String relative,String range)throws Exception {
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+media.port()+relative)).timeout(java.time.Duration.ofSeconds(10));if(range!=null){request.header("Range",range);}return HTTP.send(request.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private static String ticket(String relative){return relative.substring(relative.indexOf("ticket=")+7);}
    private static void property(String name,String value){if(value==null){System.clearProperty(name);}else{System.setProperty(name,value);}}
    private static void expect(ErrorCode code,Checked action)throws Exception {try{action.run();throw new AssertionError("Expected "+code);}catch(AuctionException exception){TestSupport.equals(code,exception.getErrorCode(),"media error code");}}
    private interface Checked {void run()throws Exception;}
    private static final class BlockingInput extends InputStream {
        final CountDownLatch reading=new CountDownLatch(1); boolean closed;
        public synchronized int read()throws IOException {reading.countDown();while(!closed){try{wait();}catch(InterruptedException exception){Thread.currentThread().interrupt();throw new IOException(exception);}}throw new IOException("closed");}
        public synchronized void close(){closed=true;notifyAll();}
    }
}
