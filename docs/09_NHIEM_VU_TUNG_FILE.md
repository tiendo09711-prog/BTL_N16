# 09 - NHIEM VU TUNG FILE

## Common

| File | Giai quyet van de gi |
|---|---|
| `AppProperties` | Doc file `.properties` UTF-8 va validate kieu |
| `ServerConfig` | Gom TCP, DB, session, timer rule |
| `ClientConfig` | Gom host, timeout, heartbeat, reconnect |
| `MessageKind` | Phan biet REQUEST/RESPONSE/EVENT |
| `MessageType` | Tu dien protocol toan nhom |
| `ErrorCode` | Ma loi on dinh cho client |
| `WireMessage` | Envelope bat bien di tren TCP |
| `LengthPrefixedMessageCodec` | Encode/decode `[length][payload]` |
| `WireValues` | Parse long/decimal/field bat buoc |
| `Money` | Chuan hoa va hien thi VND |
| `Times` | Instant, countdown, format |
| `InputValidation` | Validate account input |

## Network core server

| File | Giai quyet van de gi |
|---|---|
| `TcpServer` | Bind, accept, worker pool |
| `ClientConnection` | Mot socket, read loop, output lock, close |
| `ConnectionRegistry` | Danh ba connection dang song |
| `ServerMessagingService` | Gui event user/room/toan bo |
| `ConnectionLifecycleListener` | Hook khi socket mat |
| `MessageRouter` | Kiem tra protocol/auth va route |
| `RequestContext` | Request + session + ham reply |
| `MessageHandler` | Functional interface handler |
| `ServerSequence` | Atomic sequence toan server |
| `ServerModule` | Hop dong module dang ky route |
| `CoreAccountModule` | Route account va PING |
| `AuctionModule` | Route auction va bid |

## Account/session

| File | Giai quyet van de gi |
|---|---|
| `UserAccount` | Entity user server-side |
| `PasswordHash` | Salt/hash/iterations |
| `Pbkdf2PasswordHasher` | Hash va verify mat khau |
| `UserRepository` | Hop dong persistence user |
| `JdbcUserRepository` | SQL, PreparedStatement, login transaction |
| `TestUserRepository` (`src/test`) | Test double user, khong duoc production khoi tao |
| `AccountService` | Register/login/profile/password rule |
| `AccountController` | Protocol adapter account |
| `UserSession` | Snapshot session |
| `SessionManager` | Create/find/detach/resume/logout/expire |
| `SessionCleanupService` | Xoa detached session het han |

## Auction data - Tran Van Phuoc

| File | Giai quyet van de gi |
|---|---|
| `Product` | Du lieu san pham |
| `AuctionStatus` | OPEN/ENDED |
| `AuctionSnapshot` | State bat bien gui ra ngoai |
| `AuctionRuntime` | State mutable tren server + lock |
| `AuctionRepository` | Hop dong list/detail/bid/result |
| `JdbcAuctionRepository` | SQL auction, bid history, transaction |
| `TestAuctionRepository` (`src/test`) | Test double auction cho self-test |
| `AuctionManager` | Cache runtime theo auctionId |
| `AuctionQueryService` | List, snapshot, history |
| `AuctionWireData` | Chuyen state thanh map protocol |
| `RoomManager` | Subscriber theo auction |
| `AuctionController` | List/join/leave/history/resync/bid route |
| `CreateProductCommit`, `UpdateProductCommit` | Du lieu ghi product |
| `CreateAuctionCommit` | Du lieu tao room moi |
| `BlockAuctionUserCommit` | Du lieu luu user bi kick |
| `AuctionManagementService` | Product CRUD, host/create/join/kick |
| `RoomMember`, `KickOutcome` | Thanh vien room va ket qua kick |

## Bid - Pham Anh Dung

| File | Giai quyet van de gi |
|---|---|
| `BidRecord` | Entity mot bid da chap nhan |
| `BidCommit` | Du lieu transaction bid |
| `BidOutcome` | Ket qua de response va broadcast |
| `BidService` | Validate, lock, commit, update, publish |
| `AuctionConflictException` | DB state conflict |
| `AuctionException` | Business error kem ErrorCode |

## Timer/result - Mai Trung Duc

| File | Giai quyet van de gi |
|---|---|
| `AuctionTimerService` | Tick, detect expiry, close once |
| `ExtendAuctionCommit` | Compare-and-set end time |
| `CancelAuctionCommit` | Chuyen OPEN sang CANCELLED |
| `CloseAuctionCommit` | Du lieu transaction close |
| `AuctionResult` | Winner/final price/endedAt |
| `AuctionBroadcastService` | Phat extension/tick/end vao room |

## Client realtime/integration - Vu Tri Thuan

| File | Giai quyet van de gi |
|---|---|
| `NetworkClient` | Connect, requestId, reader thread, event |
| `ConnectionState` | State TCP client |
| `AccountApi` | Facade account protocol |
| `AuctionApi` | Facade auction protocol |
| `ClientAuction` | Auction cache tren client |
| `ClientProduct` | Product owner/active tren client |
| `ClientBid` | Bid cache tren client |
| `ClientWireParser` | Map protocol -> model |
| `ClientAppModel` | State chung thread-safe |
| `HeartbeatService` | PING/PONG va detect connection loi |
| `ReconnectCoordinator` | Exponential backoff |
| `ClientController` | MVC + realtime + resume/resync |
| `FullNetworkAuctionSelfTest` | End-to-end multi-client test |
| `AuctionManagementSelfTest` | End-to-end product/host/kick test |
| `ConcurrentBidLoadTestMain` | Stress bid bang socket that |

## View MVC

| File | Giai quyet van de gi |
|---|---|
| `LoginPanel` | Login/register/reconnect UI |
| `AuctionPanel` | List/detail/room/bid/history/notification UI |
| `MainFrame` | Card layout, status bar, dialogs |
| `AuctionTableModel` | Bang danh sach phien |
| `BidTableModel` | Bang lich su bid |
| `ClientMain` | Composition root client Swing |
| `ConsoleClientMain` | Luong nho de debug |

## DB/bootstrap/dashboard

| File | Giai quyet van de gi |
|---|---|
| `DatabaseSchema` | Tao/drop DB va tables |
| `DemoDataSeeder` | Seed user, product, auction |
| `DatabaseSetupMain` | Setup mot lan |
| `DatabaseResetMain` | Reset demo |
| `ServerApplication` | Noi tat ca dependency |
| `ServerMain` | Server console entry |
| `ServerDashboardMain` | Server GUI entry |
| `ServerDashboardFrame` | Hien stats va auction state |
| `ServerStats` | Snapshot dashboard |
