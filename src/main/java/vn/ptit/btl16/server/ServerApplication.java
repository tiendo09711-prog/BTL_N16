package vn.ptit.btl16.server;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.media.MediaConfig;
import vn.ptit.btl16.server.media.MediaHttpServer;
import vn.ptit.btl16.server.media.MediaService;
import vn.ptit.btl16.common.protocol.LengthPrefixedMessageCodec;
import vn.ptit.btl16.server.account.controller.AccountController;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.account.repository.JdbcUserRepository;
import vn.ptit.btl16.server.account.repository.UserRepository;
import vn.ptit.btl16.server.account.security.PasswordHasher;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.account.service.AccountService;
import vn.ptit.btl16.server.auction.controller.AuctionController;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.auction.repository.JdbcAuctionRepository;
import vn.ptit.btl16.server.auction.service.AuctionBroadcastService;
import vn.ptit.btl16.server.auction.service.AuctionManager;
import vn.ptit.btl16.server.auction.service.AuctionManagementService;
import vn.ptit.btl16.server.auction.service.AuctionQueryService;
import vn.ptit.btl16.server.auction.service.AuctionTimerService;
import vn.ptit.btl16.server.auction.service.BidService;
import vn.ptit.btl16.server.auction.service.RoomManager;
import vn.ptit.btl16.server.db.DatabaseSchema;
import vn.ptit.btl16.server.module.AuctionModule;
import vn.ptit.btl16.server.module.CoreAccountModule;
import vn.ptit.btl16.server.network.ConnectionRegistry;
import vn.ptit.btl16.server.network.ConnectionLifecycleListener;
import vn.ptit.btl16.server.network.ServerMessagingService;
import vn.ptit.btl16.server.network.TcpServer;
import vn.ptit.btl16.server.network.WebSocketServerTransport;
import vn.ptit.btl16.server.routing.MessageRouter;
import vn.ptit.btl16.server.session.SessionCleanupService;
import vn.ptit.btl16.server.session.SessionManager;
import vn.ptit.btl16.server.wallet.WalletRepository;
import vn.ptit.btl16.server.wallet.WalletController;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Composition root for the complete single-server application. */
public final class ServerApplication implements AutoCloseable {
    private final ServerConfig config;
    private final SessionManager sessions;
    private final ConnectionRegistry connections;
    private final RoomManager rooms;
    private final AuctionManager auctions;
    private final ServerSequence sequence;
    private final TcpServer tcpServer;
    private final WebSocketServerTransport webSocketServer;
    private final SessionCleanupService sessionCleanup;
    private final AuctionTimerService auctionTimer;
    private final MediaHttpServer mediaServer;
    private final MediaService media;
    private final WalletRepository wallets;
    private final AuctionRepository auctionRepository;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private ServerApplication(
            ServerConfig config,
            SessionManager sessions,
            ConnectionRegistry connections,
            RoomManager rooms,
            AuctionManager auctions,
            ServerSequence sequence,
            TcpServer tcpServer,
            WebSocketServerTransport webSocketServer,
            SessionCleanupService sessionCleanup,
            AuctionTimerService auctionTimer,
            MediaHttpServer mediaServer,
            MediaService media,
            WalletRepository wallets, AuctionRepository auctionRepository) {
        this.config = config;
        this.sessions = sessions;
        this.connections = connections;
        this.rooms = rooms;
        this.auctions = auctions;
        this.sequence = sequence;
        this.tcpServer = tcpServer;
        this.webSocketServer = webSocketServer;
        this.sessionCleanup = sessionCleanup;
        this.auctionTimer = auctionTimer;
        this.mediaServer = mediaServer;
        this.media = media;
        this.wallets = wallets;
        this.auctionRepository = auctionRepository;
    }

    public static ServerApplication create(ServerConfig config) {
        PasswordHasher hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        JdbcConnectionFactory factory = new JdbcConnectionFactory(config);
        if (config.isDbAutoInitialize()) {
            DatabaseSchema.initialize(factory);
        }
        UserRepository users = new JdbcUserRepository(factory);
        AuctionRepository auctionRepository = new JdbcAuctionRepository(factory);
        System.out.println("[REPOSITORY] JDBC " + config.databaseJdbcUrl());
        return compose(config, users, auctionRepository, hasher, false, new WalletRepository(factory), new vn.ptit.btl16.server.social.RoomSocialStore(factory));
    }

    public static ServerApplication createForTests(
            ServerConfig config,
            UserRepository users,
            AuctionRepository auctionRepository,
            PasswordHasher hasher) {
        return compose(config, users, auctionRepository, hasher, true, null, new vn.ptit.btl16.server.social.RoomSocialStore(null));
    }

    private static ServerApplication compose(
            ServerConfig config,
            UserRepository users,
            AuctionRepository auctionRepository,
            PasswordHasher hasher,
            boolean isolated,
            WalletRepository wallets, vn.ptit.btl16.server.social.RoomSocialStore socialStore) {
        ServerSequence sequence = new ServerSequence(auctionRepository.findMaxServerSequence());
        SessionManager sessions = new SessionManager(
                Duration.ofSeconds(config.getResumeGraceSeconds()));
        ConnectionRegistry connections = new ConnectionRegistry();
        RoomManager rooms = new RoomManager();
        rooms.setPresenceListener(auctionRepository::appendPresence);
        AuctionManager auctions = new AuctionManager(
                auctionRepository,
                config.getClosedVisibilitySeconds());
        MessageRouter router = new MessageRouter(sessions, sequence);
        ServerMessagingService messaging = new ServerMessagingService(
                connections,
                sessions,
                sequence);
        AuctionBroadcastService broadcasts = new AuctionBroadcastService(rooms, messaging);

        AccountService accountService = new AccountService(users, hasher, sessions);
        AccountController accountController = new AccountController(accountService, rooms);
        AuctionQueryService queryService = new AuctionQueryService(
                auctions,
                auctionRepository,
                config.getBidHistoryLimit());
        BidService bidService = new BidService(
                auctions,
                auctionRepository,
                rooms,
                broadcasts,
                sequence,
                config);
        AuctionManagementService managementService = new AuctionManagementService(
                auctions,
                auctionRepository,
                rooms,
                broadcasts,
                hasher,
                sessions);
        MediaService media = new MediaService(MediaConfig.load(isolated), auctionRepository, sessions, managementService);
        managementService.setMediaService(media);
        MediaHttpServer mediaServer = new MediaHttpServer(media, config.getBindAddress());
        AuctionController auctionController = new AuctionController(
                queryService,
                bidService,
                rooms,
                managementService);
        var social = new vn.ptit.btl16.server.social.RoomSocialService(
                auctions, managementService, rooms, users, sessions, socialStore, messaging);
        auctionController.setSocialService(social);
        social.setAuctionRepository(auctionRepository);
        social.register(router);
        router.setOutboundPolicy(social::project);
        messaging.setOutboundPolicy(social::project);

        new CoreAccountModule(
                accountController,
                sessions,
                connections,
                rooms,
                auctions,
                sequence).register(router);
        new AuctionModule(auctionController).register(router);
        var progress = new vn.ptit.btl16.server.auction.service.RoomProgressService(
                auctions, managementService, auctionRepository, rooms, users);
        progress.register(router);
        new vn.ptit.btl16.server.auction.service.AuctionReceiptService(auctionRepository, sessions).register(router);
        new vn.ptit.btl16.server.notification.PersonalNotificationService(auctionRepository, sessions).register(router);
        auctionController.setProgressService(progress);
        if (wallets != null) { new WalletController(wallets, managementService, sessions).register(router); }

        SessionCleanupService cleanup = new SessionCleanupService(
                sessions,
                config.getCleanupIntervalSeconds());
        AuctionTimerService timer = new AuctionTimerService(
                auctions,
                auctionRepository,
                rooms,
                broadcasts,
                config);

        ConnectionLifecycleListener lifecycle = connection -> {
            String connectionId = connection.getConnectionId();
            rooms.removeConnection(connectionId);
            sessions.detachByConnection(connectionId);
        };
        TcpServer tcpServer = new TcpServer(
                config,
                new LengthPrefixedMessageCodec(config.getMaxFrameBytes()),
                router,
                connections,
                lifecycle,
                sequence);
        WebSocketServerTransport webSocketServer = new WebSocketServerTransport(
                config.getWebSocketBindAddress(),
                config.getWebSocketPort(),
                config.getWebSocketPath(),
                config.getMaxFrameBytes(),
                router,
                connections,
                lifecycle,
                sequence);

        return new ServerApplication(
                config,
                sessions,
                connections,
                rooms,
                auctions,
                sequence,
                tcpServer,
                webSocketServer,
                cleanup,
                timer,
                mediaServer,
                media,
                wallets, auctionRepository);
    }

    public void start() throws IOException {
        if (disposed.get()) { throw new IllegalStateException("Server application is closed"); }
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Server application is already started");
        }
        sessionCleanup.start();
        auctionTimer.start();
        try {
            if (!config.isTcpEnabled() && !config.isWebSocketEnabled()) {
                throw new IOException("At least one server transport must be enabled");
            }
            for (String warning : media.migrateLegacy()) { System.err.println("[MEDIA MIGRATION] " + warning + "; original retained"); }
            mediaServer.start();
            if (config.isTcpEnabled()) {
                tcpServer.start();
            }
            if (config.isWebSocketEnabled()) {
                webSocketServer.startAndAwait();
            }
        } catch (IOException exception) {
            close();
            throw exception;
        }
    }

    public void awaitTermination() throws InterruptedException {
        if (config.isTcpEnabled()) {
            tcpServer.awaitTermination();
        } else if (config.isWebSocketEnabled()) {
            webSocketServer.awaitTermination();
        }
    }

    public int getBoundPort() {
        return config.isTcpEnabled() ? tcpServer.getBoundPort() : 0;
    }

    public int getWebSocketBoundPort() {
        return config.isWebSocketEnabled() ? webSocketServer.getBoundPort() : 0;
    }

    public ServerConfig getConfig() {
        return config;
    }

    public WalletRepository wallets() {
        if (wallets == null) { throw new IllegalStateException("Wallet requires a persistent JDBC server"); }
        return wallets;
    }

    public List<AuctionSnapshot> auctionSnapshots() {
        return auctions.snapshots();
    }
    public List<java.util.Map<String, String>> operationEvents(Long room, String type, java.time.Instant from, java.time.Instant to, long before) {
        return auctionRepository.operationEvents(room, type, from, to, before, 51);
    }

    public ServerStats stats() {
        return new ServerStats(
                config.isTcpEnabled() ? tcpServer.getBoundPort() : 0,
                config.isWebSocketEnabled() ? webSocketServer.getBoundPort() : 0,
                connections.size(),
                connections.countTransport("TCP"),
                connections.countTransport("WEBSOCKET"),
                sessions.activeCount(),
                sessions.detachedCount(),
                rooms.roomCount(),
                rooms.totalSubscriptions(),
                auctions.openCount(),
                auctions.endedCount(),
                sequence.current(),
                config.isEmbeddedDatabase() ? "Local / H2" : "MySQL / JDBC");
    }

    @Override
    public void close() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        started.set(false);
        webSocketServer.close();
        mediaServer.close();
        tcpServer.close();
        auctionTimer.close();
        sessionCleanup.close();
    }
}
