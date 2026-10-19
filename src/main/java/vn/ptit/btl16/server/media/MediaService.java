package vn.ptit.btl16.server.media;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.model.ProductImage;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.auction.service.AuctionManagementService;
import vn.ptit.btl16.server.session.SessionManager;
import vn.ptit.btl16.server.session.UserSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MediaService implements AutoCloseable {
    private final MediaConfig config;
    private final AuctionRepository repository;
    private final SessionManager sessions;
    private final AuctionManagementService management;
    private final MediaProcessor processor;
    private final JdbcMediaStorage databaseStorage;
    private final Path root;
    private final Semaphore workers;
    private final Object storageMutation = new Object();
    private final AtomicBoolean closing = new AtomicBoolean();
    private java.nio.channels.FileChannel lockChannel;
    private java.nio.channels.FileLock fileLock;
    private final Map<String, Draft> drafts = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final java.util.Set<Path> pendingCleanup = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService cleanup = Executors.newScheduledThreadPool(2, task -> {
        Thread thread = new Thread(task, "media-cleanup"); thread.setDaemon(true); return thread;
    });
    private long reserved;
    private int port;

    public MediaService(MediaConfig config, AuctionRepository repository, SessionManager sessions,
                        AuctionManagementService management) {
        this(config, repository, sessions, management,
                repository instanceof vn.ptit.btl16.server.auction.repository.JdbcAuctionRepository jdbc ? jdbc.mediaStorage() : null);
    }

    public MediaService(MediaConfig config, AuctionRepository repository, SessionManager sessions,
                        AuctionManagementService management, JdbcMediaStorage databaseStorage) {
        this.config = config; this.repository = repository; this.sessions = sessions; this.management = management;
        this.databaseStorage = databaseStorage;
        this.processor = new MediaProcessor(config); this.workers = new Semaphore(config.workers());
        try {
            Files.createDirectories(config.root()); root = config.root().toRealPath();
            if (Files.isSymbolicLink(root.resolve(".media.lock"))) { throw new IOException("Unsafe lock file"); }
            lockChannel = java.nio.channels.FileChannel.open(root.resolve(".media.lock"), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
            fileLock = lockChannel.tryLock();
            if (fileLock == null) { throw new IOException("Media directory already in use"); }
            directory("products"); directory("staging");
            try (var children = Files.list(directory("staging"))) {
                for (Path child : children.toList()) { deleteTree(child); }
            }
        } catch (IOException | RuntimeException exception) {
            releaseFileLock();
            throw new IllegalStateException("Cannot prepare media data directory (may be in use by another server)", exception);
        }
        cleanup.scheduleWithFixedDelay(this::expire, 30, 30, TimeUnit.SECONDS);
        cleanup.scheduleWithFixedDelay(this::cleanupUnusedQuietly, 60, 3600, TimeUnit.SECONDS);
    }

    public MediaConfig config() { return config; }
    public int port() { return port; }
    public void setPort(int value) { port = value; }

    public UserSession authenticate(String token) {
        UserSession session = token == null ? null : sessions.findByToken(token).orElse(null);
        if (session == null || !session.isActive()) { fail(ErrorCode.AUTH_REQUIRED, "Can dang nhap lai de su dung media"); }
        return session;
    }

    public synchronized String begin(UserSession session, long productId) {
        if (closing.get()) { fail(ErrorCode.UPLOAD_CANCELLED, "May chu media da dung"); }
        management.requireMediaEdit(session, productId);
        if (drafts.size() >= 64 || drafts.values().stream().filter(draft -> draft.owner == session.getUserId()).count() >= 3) {
            fail(ErrorCode.MEDIA_LIMIT, "Hay hoan tat/huy lan dang tai truoc");
        }
        Draft draft = new Draft(session, productId); drafts.put(draft.id, draft); return draft.id;
    }

    public ProductMedia upload(UserSession session, String draftId, String kind, String name, InputStream input,
                               long declaredBytes, int turns, double left, double top, double width, double height) throws IOException {
        Draft draft = draft(session, draftId);
        long limit = kind.equals("IMAGE") ? config.imageBytes() : config.videoBytes();
        if (!List.of("IMAGE", "VIDEO").contains(kind)) { fail(ErrorCode.MEDIA_INVALID, "Loai tep khong hop le"); }
        if (declaredBytes > limit) { fail(ErrorCode.MEDIA_LIMIT, "Tep vuot " + limit + " byte"); }
        if (!workers.tryAcquire()) { fail(ErrorCode.MEDIA_LIMIT, "May chu dang xu ly media, vui long thu lai"); }
        boolean reservation = false;
        String id = UUID.randomUUID().toString(); Path folder = null;
        AtomicBoolean timedOut = new AtomicBoolean();
        var timeout = cleanup.schedule(() -> { timedOut.set(true); try { input.close(); } catch (IOException ignored) { } }, config.timeoutSeconds(), TimeUnit.SECONDS);
        try {
            reserve(); reservation = true;
            synchronized (draft) {
                if (draft.cancelled.get() || draft.files.size() + draft.busy >= 12) { fail(ErrorCode.MEDIA_LIMIT, "Qua nhieu tep cho mot ban nhap"); }
                draft.busy++; draft.inputs.add(input);
            }
            folder = directory("staging").resolve(id); Files.createDirectory(folder);
            long bytes = 0;
            try (OutputStream output = Files.newOutputStream(folder.resolve("source"))) {
                byte[] buffer = new byte[65536]; int read;
                while (true) {
                    try { read = input.read(buffer); }
                    catch (IOException exception) {
                        if (draft.cancelled.get()) { fail(ErrorCode.UPLOAD_CANCELLED, "Da huy tai len"); }
                        if (timedOut.get()) { fail(ErrorCode.MEDIA_LIMIT, "Qua thoi gian tai tep (" + config.timeoutSeconds() + " giay)"); }
                        fail(ErrorCode.MEDIA_INVALID, "Ket noi tai len bi ngat hoac tep chua du. Hay thu lai tep nay");
                        throw exception;
                    }
                    if (read == -1) { break; }
                    bytes += read;
                    if (draft.cancelled.get()) { fail(ErrorCode.UPLOAD_CANCELLED, "Da huy tai len"); }
                    if (bytes > limit) { fail(ErrorCode.MEDIA_LIMIT, "Tep vuot " + limit + " byte"); }
                    output.write(buffer, 0, read);
                }
            }
            if (bytes == 0 || (declaredBytes >= 0 && bytes != declaredBytes)) { fail(ErrorCode.MEDIA_INVALID, "Tep rong hoac tai chua du"); }
            ProductMedia media = processor.process(folder, id, kind, name, turns, left, top, width, height, draft.cancelled::get);
            synchronized (draft) {
                if (draft.cancelled.get()) { fail(ErrorCode.UPLOAD_CANCELLED, "Da huy tai len"); }
                management.requireMediaEdit(authenticate(draft.token), draft.productId);
                long total = draft.files.values().stream().mapToLong(ProductMedia::bytes).sum() + media.bytes();
                if (total > config.productBytes()) { fail(ErrorCode.MEDIA_LIMIT, "Ban nhap vuot " + config.productBytes() + " byte; hay xoa tep khong dung"); }
                draft.files.put(id, media); folder = null; return media;
            }
        } finally {
            timeout.cancel(false);
            synchronized (draft) { if (draft.inputs.remove(input)) { draft.busy--; } }
            if (folder != null) { deleteStaged(folder); }
            if (reservation) { release(); }
            workers.release();
            if (closing.get() && workers.availablePermits() == config.workers()) { releaseFileLock(); }
            if (draft.cancelled.get()) { discard(draft); }
        }
    }

    public void remove(UserSession session, String draftId, String mediaId) throws IOException {
        Draft draft = draft(session, draftId);
        synchronized (draft) {
            if (draft.files.remove(mediaId) != null) { deleteStaged(mediaFolder(mediaId, true)); }
        }
    }

    public ProductMedia transform(UserSession session, String draftId, String mediaId, int turns,
                                  double left, double top, double width, double height) throws IOException {
        Draft draft = draft(session, draftId); ProductMedia media; boolean staged;
        synchronized (draft) { media = draft.files.get(mediaId); staged = media != null; }
        if (media == null) {
            media = repository.findMedia(mediaId).orElseThrow(() -> new AuctionException(ErrorCode.MEDIA_INVALID, "Media khong ton tai"));
            if (draft.productId == 0 || media.productId() != draft.productId) { fail(ErrorCode.AUCTION_FORBIDDEN, "Media thuoc san pham khac"); }
        }
        if (!media.image()) { fail(ErrorCode.MEDIA_INVALID, "Chi bien tap anh"); }
        Path source = (staged ? draftMediaFolder(mediaId) : mediaFolder(mediaId, false)).resolve("source");
        try (InputStream input = Files.newInputStream(source)) {
            return upload(session, draftId, "IMAGE", media.name(), input, Files.size(source), turns, left, top, width, height);
        }
    }

    public Product commit(UserSession session, String draftId, String code, String name, String description, List<String> ids) throws IOException {
        Draft draft = draft(session, draftId);
        synchronized (draft) {
            if (draft.busy != 0) { fail(ErrorCode.MEDIA_INVALID, "Hay cho tep tai len/xu ly xong"); }
            management.requireMediaEdit(session, draft.productId);
            List<ProductMedia> selected = new ArrayList<>();
            for (String id : ids) {
                ProductMedia media = draft.files.get(id);
                if (media == null) {
                    media = repository.findMedia(id).orElseThrow(() -> new AuctionException(ErrorCode.MEDIA_INVALID, "Media khong ton tai"));
                    if (draft.productId == 0 || media.productId() != draft.productId) { fail(ErrorCode.AUCTION_FORBIDDEN, "Khong duoc su dung media cua san pham khac"); }
                }
                selected.add(media);
            }
            ProductRequirements.validate(description, selected);
            management.prepareMediaProduct(session, draft.productId, code, name, description, selected);
            if (selected.stream().mapToLong(ProductMedia::bytes).sum() > config.productBytes()) { fail(ErrorCode.MEDIA_LIMIT, "San pham vuot gioi han dung luong"); }
            synchronized (storageMutation) {
                for (ProductMedia media : selected) {
                    if (draft.files.containsKey(media.id())) {
                        Path staged = mediaFolder(media.id(), true);
                        if (Files.isDirectory(staged, LinkOption.NOFOLLOW_LINKS)) {
                            Files.move(staged, mediaFolder(media.id(), false), StandardCopyOption.ATOMIC_MOVE);
                        }
                    }
                    verifyFiles(media);
                }
                Map<String, Path> folders = new LinkedHashMap<>();
                if (databaseStorage != null) {
                    for (ProductMedia media : selected) { folders.put(media.id(), mediaFolder(media.id(), false)); }
                }
                Product product = management.saveMediaProduct(session, draft.productId, code, name, description, selected, folders);
                draft.cancelled.set(true); drafts.remove(draft.id);
                discard(draft);
                return product;
            }
        }
    }

    public Product legacySave(UserSession session, long productId, String code, String name, String description,
                              byte[] bytes, String filename) {
        String draftId = begin(session, productId);
        try {
            List<String> ids;
            if (bytes == null || bytes.length == 0) {
                ids = productId == 0 ? List.of() : repository.findProductMedia(productId).stream().map(ProductMedia::id).toList();
            } else {
                ProductMedia image = upload(session, draftId, "IMAGE", filename, new java.io.ByteArrayInputStream(bytes), bytes.length, 0, 0, 0, 1, 1);
                ids = List.of(image.id());
            }
            return commit(session, draftId, code, name, description, ids);
        } catch (IOException exception) { throw new AuctionException(ErrorCode.MEDIA_PROCESSING_FAILED, "Khong the luu tep media"); }
        finally { cancel(session, draftId); }
    }

    public Map<String, Object> manifest(UserSession session, long productId, long auctionId) throws IOException {
        management.requireMediaRead(session, productId, auctionId);
        List<Map<String, Object>> media = new ArrayList<>();
        for (ProductMedia item : repository.findProductMedia(productId)) { media.add(view(session, item, null, auctionId)); }
        return Map.of("productId", productId, "media", media, "limits", limits());
    }

    public Map<String, Object> uploaded(UserSession session, String draftId, ProductMedia media) throws IOException {
        draft(session, draftId); return view(session, media, draftId, 0);
    }

    public Map<String, Object> limits() {
        return Map.of("imageBytes", config.imageBytes(), "videoBytes", config.videoBytes(), "imagePixels", config.imagePixels(),
                "videoSeconds", config.videoSeconds(), "descriptionMin", 50, "descriptionMax", 2000, "maxImages", 5, "maxVideos", 1);
    }

    private Map<String, Object> view(UserSession session, ProductMedia media, String draftId, long auctionId) throws IOException {
        if (tickets.size() > 8192) { expire(); if (tickets.size() > 8192) { fail(ErrorCode.MEDIA_LIMIT, "Qua nhieu yeu cau xem media"); } }
        String ticket = UUID.randomUUID().toString() + UUID.randomUUID();
        tickets.put(ticket, new Ticket(session.getSessionToken(), media.id(), media.productId(), draftId, auctionId, Instant.now().plusSeconds(1800)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", media.id()); result.put("kind", media.kind()); result.put("name", media.name()); result.put("sourceType", media.sourceType());
        result.put("bytes", media.bytes()); result.put("width", media.width()); result.put("height", media.height());
        result.put("duration", media.duration()); result.put("position", media.position());
        for (String variant : List.of("thumbnail", "detail", "original", "video")) {
            if ((media.image() && !variant.equals("video")) || (!media.image() && !variant.equals("detail"))) {
                result.put(variant, "/v1/files/" + media.id() + "/" + variant + "?ticket=" + ticket);
            }
        }
        return result;
    }

    public Path readable(String id, String variant, String key) throws IOException {
        Ticket ticket = tickets.get(key == null ? "" : key);
        if (ticket == null || !ticket.mediaId.equals(id) || ticket.expires.isBefore(Instant.now())) { fail(ErrorCode.ROOM_ACCESS_DENIED, "Quyen xem media het han; hay tai lai"); }
        UserSession session = authenticate(ticket.token);
        ProductMedia media;
        boolean staged = ticket.draftId != null;
        if (staged) {
            Draft draft = draft(session, ticket.draftId);
            synchronized (draft) { media = draft.files.get(id); }
            if (media == null) { fail(ErrorCode.MEDIA_INVALID, "Tep da bi xoa"); }
        } else {
            management.requireMediaRead(session, ticket.productId, ticket.auctionId);
            media = repository.findMedia(id).orElseThrow(() -> new AuctionException(ErrorCode.MEDIA_INVALID, "Media da thay doi"));
            if (media.productId() != ticket.productId) { fail(ErrorCode.ROOM_ACCESS_DENIED, "Media khong thuoc san pham"); }
        }
        String filename = switch (variant) {
            case "thumbnail" -> "thumbnail.jpg";
            case "detail" -> media.image() ? "detail.jpg" : "invalid";
            case "video" -> media.image() ? "invalid" : "video.mp4";
            case "original" -> "source";
            default -> "invalid";
        };
        Path path = (staged ? draftMediaFolder(id) : mediaFolder(id, false)).resolve(filename);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { fail(ErrorCode.MEDIA_INVALID, "Tep media khong con san sang"); }
        return path;
    }

    public void requireReady(long productId) {
        try { for (ProductMedia media : repository.findProductMedia(productId)) { verifyFiles(media); } }
        catch (IOException exception) { throw new AuctionException(ErrorCode.MEDIA_INVALID, "Media bi thieu; can khoi phuc ban sao luu tren server"); }
    }

    public byte[] cover(long productId) {
        ProductMedia image = repository.findProductMedia(productId).stream().filter(ProductMedia::image).findFirst()
                .orElseThrow(() -> new AuctionException(ErrorCode.PRODUCT_IMAGE_INVALID, "San pham chua co anh hop le"));
        try { return Files.readAllBytes(mediaFolder(image.id(), false).resolve("thumbnail.jpg")); }
        catch (IOException exception) { throw new AuctionException(ErrorCode.MEDIA_INVALID, "Anh khong san sang"); }
    }

    public List<String> migrateLegacy() {
        List<String> failures = new ArrayList<>();
        if (databaseStorage != null) { return failures; }
        for (long productId : repository.findLegacyImageProductIds()) {
            Path folder = null; boolean reservation = false;
            try {
                reserve(); reservation = true;
                ProductImage old = repository.findProductImage(productId).orElseThrow();
                if (old.getSize() > config.imageBytes()) { fail(ErrorCode.MEDIA_LIMIT, "Anh cu vuot nguong"); }
                String id = UUID.randomUUID().toString(); folder = directory("staging").resolve(id); Files.createDirectory(folder);
                Files.write(folder.resolve("source"), old.getData());
                if (!java.util.Arrays.equals(java.security.MessageDigest.getInstance("SHA-256").digest(old.getData()),
                        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(folder.resolve("source"))))) { throw new IOException("Verification failed"); }
                ProductMedia media = processor.process(folder, id, "IMAGE", old.getName(), 0, 0, 0, 1, 1, () -> false);
                synchronized (storageMutation) {
                    Files.move(folder, mediaFolder(id, false), StandardCopyOption.ATOMIC_MOVE); folder = null; verifyFiles(media);
                    repository.migrateLegacyImage(productId, old.getVersion(), media);
                }
            } catch (Exception exception) { failures.add("Product " + productId + ": " + exception.getClass().getSimpleName()); }
            finally { if (reservation) { release(); } if (folder != null) { try { deleteTree(folder); } catch (IOException ignored) { } } }
        }
        return failures;
    }

    public void cancel(UserSession session, String id) {
        Draft draft = drafts.get(id);
        if (draft == null) { return; }
        if (session == null || !session.isActive() || draft.owner != session.getUserId() || !draft.token.equals(session.getSessionToken())) { fail(ErrorCode.AUCTION_FORBIDDEN, "Khong duoc huy ban nhap cua nguoi khac"); }
        draft.cancelled.set(true);
        synchronized (draft) {
            for (InputStream input : draft.inputs) { try { input.close(); } catch (IOException ignored) { } }
        }
        discard(draft);
    }

    private Draft draft(UserSession session, String id) {
        Draft draft = drafts.get(id);
        if (draft == null || draft.cancelled.get() || draft.expires.isBefore(Instant.now())) { fail(ErrorCode.UPLOAD_CANCELLED, "Ban nhap da huy/het han"); }
        if (session == null || !session.isActive() || draft.owner != session.getUserId() || !draft.token.equals(session.getSessionToken())) { fail(ErrorCode.AUCTION_FORBIDDEN, "Ban nhap thuoc nguoi khac"); }
        management.requireMediaEdit(session, draft.productId);
        return draft;
    }

    private synchronized void reserve() throws IOException {
        long used;
        try (var files = Files.walk(databaseStorage == null ? root : directory("staging"))) { used = files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).mapToLong(path -> { try { return Files.size(path); } catch (IOException exception) { throw new java.io.UncheckedIOException(exception); } }).sum(); }
        if (databaseStorage != null) { used += databaseStorage.usedBytes(); }
        if (used + reserved + config.productBytes() > config.storageBytes() || Files.getFileStore(root).getUsableSpace() < reserved + config.productBytes() + (64L << 20)) {
            fail(ErrorCode.MEDIA_STORAGE_FULL, "Kho media/odia khong du cho tep va bien the; can sao luu va don kho an toan");
        }
        reserved += config.productBytes();
    }
    private synchronized void release() { reserved -= config.productBytes(); }

    private void verifyFiles(ProductMedia media) throws IOException {
        Path folder = mediaFolder(media.id(), false);
        for (String filename : List.of("source", "thumbnail.jpg", media.image() ? "detail.jpg" : "video.mp4")) {
            Path file = folder.resolve(filename);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) == 0) { throw new IOException("Missing media variant"); }
        }
    }
    private Path directory(String name) throws IOException {
        Path path = root.resolve(name);
        if (Files.isSymbolicLink(path)) { throw new IOException("Unsafe media directory"); }
        Files.createDirectories(path); return path;
    }
    private Path draftMediaFolder(String id) throws IOException {
        Path staged = mediaFolder(id, true);
        return Files.isDirectory(staged, LinkOption.NOFOLLOW_LINKS) ? staged : mediaFolder(id, false);
    }
    private Path mediaFolder(String id, boolean staged) throws IOException {
        if (!id.matches("[a-f0-9-]{36}")) { throw new IOException("Invalid media id"); }
        Path path = directory(staged ? "staging" : "products").resolve(id);
        if (Files.isSymbolicLink(path)) { throw new IOException("Unsafe media folder"); }
        if (!staged && databaseStorage != null) {
            synchronized (storageMutation) { databaseStorage.materialize(id, path); }
        }
        return path;
    }
    private void deleteTree(Path folder) throws IOException {
        Path checked = folder.toAbsolutePath().normalize();
        boolean staged = checked.startsWith(root.resolve("staging")) && !checked.equals(root.resolve("staging"));
        boolean unused = checked.startsWith(root.resolve("products")) && checked.getParent().equals(root.resolve("products"))
                && checked.getFileName().toString().matches("[a-f0-9-]{36}") && repository.findMedia(checked.getFileName().toString()).isEmpty();
        if (!staged && !unused) { throw new IOException("Refusing unsafe/referenced cleanup"); }
        if (!Files.exists(checked, LinkOption.NOFOLLOW_LINKS)) { return; }
        try (var files = Files.walk(checked)) { for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.deleteIfExists(path); } }
    }
    private void discard(Draft draft) {
        synchronized (draft) {
            if (draft.busy != 0) { return; }
            for (String id : draft.files.keySet()) { try { deleteStaged(mediaFolder(id, true)); } catch (IOException ignored) { } }
            drafts.remove(draft.id);
        }
    }
    private void expire() {
        for (Path folder : pendingCleanup) { deleteStaged(folder); }
        tickets.entrySet().removeIf(entry -> entry.getValue().expires.isBefore(Instant.now()));
        for (Draft draft : drafts.values()) {
            if (draft.expires.isBefore(Instant.now()) || sessions.findByToken(draft.token).filter(UserSession::isActive).isEmpty()) {
                draft.cancelled.set(true);
                synchronized (draft) { for (InputStream input : draft.inputs) { try { input.close(); } catch (IOException ignored) { } } }
                discard(draft);
            }
        }
    }
    private void deleteStaged(Path folder) {
        for (int attempt = 0; attempt < 6; attempt++) {
            try { deleteTree(folder); pendingCleanup.remove(folder); return; }
            catch (IOException exception) {
                try { Thread.sleep(50); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
        pendingCleanup.add(folder);
    }
    public void cleanupUnused() throws IOException {
        synchronized (storageMutation) {
            try (var folders = Files.list(directory("products"))) {
                for (Path folder : folders.toList()) {
                    String id = folder.getFileName().toString();
                    if (!id.matches("[a-f0-9-]{36}") || Files.isSymbolicLink(folder)
                            || Files.getLastModifiedTime(folder).toInstant().isAfter(Instant.now().minusSeconds(86400))) { continue; }
                    if (repository.findMedia(id).isEmpty()) { deleteTree(folder); }
                }
            }
        }
    }
    private void cleanupUnusedQuietly() { try { cleanupUnused(); } catch (Exception ignored) { } }
    private synchronized void releaseFileLock() {
        try { if (fileLock != null && fileLock.isValid()) { fileLock.release(); } } catch (IOException ignored) { }
        try { if (lockChannel != null && lockChannel.isOpen()) { lockChannel.close(); } } catch (IOException ignored) { }
    }
    @Override public void close() {
        if (!closing.compareAndSet(false, true)) { return; }
        cleanup.shutdownNow();
        for (Draft draft : drafts.values()) {
            draft.cancelled.set(true);
            synchronized (draft) { for (InputStream input : draft.inputs) { try { input.close(); } catch (IOException ignored) { } } }
            discard(draft);
        }
        tickets.clear();
        try {
            if (workers.tryAcquire(config.workers(), 5, TimeUnit.SECONDS)) {
                releaseFileLock(); workers.release(config.workers());
                if (databaseStorage != null && root.getFileName().toString().startsWith("btl16-mysql-media-")
                        && root.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toRealPath())) {
                    try (var paths = Files.walk(root)) {
                        for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.deleteIfExists(path); }
                    }
                }
            }
            cleanup.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        catch (IOException exception) { System.err.println("[MEDIA] Temporary cache cleanup: " + exception.getMessage()); }
    }
    private static void fail(ErrorCode code, String message) { throw new AuctionException(code, message); }
    private record Ticket(String token, String mediaId, long productId, String draftId, long auctionId, Instant expires) { }
    private static final class Draft {
        final String id = UUID.randomUUID().toString(); final long owner; final long productId; final String token;
        final Instant expires = Instant.now().plusSeconds(3600); final AtomicBoolean cancelled = new AtomicBoolean();
        final Map<String, ProductMedia> files = new LinkedHashMap<>(); final List<InputStream> inputs = new ArrayList<>(); int busy;
        Draft(UserSession session, long productId) { this.owner = session.getUserId(); this.token = session.getSessionToken(); this.productId = productId; }
    }
}
