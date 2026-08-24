package vn.ptit.btl16.server;

import vn.ptit.btl16.common.config.ServerConfig;
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
import vn.ptit.btl16.server.network.ServerMessagingService;
import vn.ptit.btl16.server.network.TcpServer;
import vn.ptit.btl16.server.routing.MessageRouter;
import vn.ptit.btl16.server.session.SessionCleanupService;
import vn.ptit.btl16.server.session.SessionManager;

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
    private final SessionCleanupService sessionCleanup;
    private final AuctionTimerService auctionTimer;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private ServerApplication(
            ServerConfig config,
            SessionManager sessions,
            ConnectionRegistry connections,
            RoomManager rooms,
            AuctionManager auctions,
            ServerSequence sequence,
            TcpServer tcpServer,
            SessionCleanupService sessionCleanup,
            AuctionTimerService auctionTimer) {
        this.config = config;
        this.sessions = sessions;
        this.connections = connections;
        this.rooms = rooms;
        this.auctions = auctions;
        this.sequence = sequence;
        this.tcpServer = tcpServer;
        this.sessionCleanup = sessionCleanup;
        this.auctionTimer = auctionTimer;
    }

    public static ServerApplication create(ServerConfig config) {
        PasswordHasher hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        JdbcConnectionFactory factory = new JdbcConnectionFactory(config);
        if (config.isDbAutoInitialize()) {
            DatabaseSchema.initialize(factory);
        }
        UserRepository users = new JdbcUserRepository(factory);
        AuctionRepository auctionRepository = new JdbcAuctionRepository(factory);
        System.out.println("[REPOSITORY] MySQL/JDBC " + config.databaseJdbcUrl());
        return compose(config, users, auctionRepository, hasher);
    }

    public static ServerApplication createForTests(
            ServerConfig config,
            UserRepository users,
            AuctionRepository auctionRepository,
            PasswordHasher hasher) {
        return compose(config, users, auctionRepository, hasher);
    }

    private static ServerApplication compose(
            ServerConfig config,
            UserRepository users,
            AuctionRepository auctionRepository,
            PasswordHasher hasher) {
        ServerSequence sequence = new ServerSequence(auctionRepository.findMaxServerSequence());
        SessionManager sessions = new SessionManager(
                Duration.ofSeconds(config.getResumeGraceSeconds()));
        ConnectionRegistry connections = new ConnectionRegistry();
        RoomManager rooms = new RoomManager();
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
                broadcasts);
        AuctionController auctionController = new AuctionController(
                queryService,
                bidService,
                rooms,
                managementService);

        new CoreAccountModule(
                accountController,
                sessions,
                connections,
                rooms,
                auctions,
                sequence).register(router);
        new AuctionModule(auctionController).register(router);

        SessionCleanupService cleanup = new SessionCleanupService(
                sessions,
                config.getCleanupIntervalSeconds());
        AuctionTimerService timer = new AuctionTimerService(
                auctions,
                auctionRepository,
                rooms,
                broadcasts,
                config);

        TcpServer tcpServer = new TcpServer(
                config,
                new LengthPrefixedMessageCodec(config.getMaxFrameBytes()),
                router,
                connections,
                connection -> {
                    String connectionId = connection.getConnectionId();
                    rooms.removeConnection(connectionId);
                    sessions.detachByConnection(connectionId);
                },
                sequence);

        return new ServerApplication(
                config,
                sessions,
                connections,
                rooms,
                auctions,
                sequence,
                tcpServer,
                cleanup,
                timer);
    }

    public void start() throws IOException {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Server application is already started");
        }
        sessionCleanup.start();
        auctionTimer.start();
        try {
            tcpServer.start();
        } catch (IOException exception) {
            close();
            throw exception;
        }
    }

    public void awaitTermination() throws InterruptedException {
        tcpServer.awaitTermination();
    }

    public int getBoundPort() {
        return tcpServer.getBoundPort();
    }

    public ServerConfig getConfig() {
        return config;
    }

    public List<AuctionSnapshot> auctionSnapshots() {
        return auctions.snapshots();
    }

    public ServerStats stats() {
        return new ServerStats(
                tcpServer.getBoundPort(),
                connections.size(),
                sessions.activeCount(),
                sessions.detachedCount(),
                rooms.roomCount(),
                rooms.totalSubscriptions(),
                auctions.openCount(),
                auctions.endedCount(),
                sequence.current(),
                "MySQL/JDBC");
    }

    @Override
    public void close() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        tcpServer.close();
        auctionTimer.close();
        sessionCleanup.close();
    }
}
