# 09 – Nhiệm vụ từng file và coverage ownership

Đối chiếu toàn bộ Java source ngày 07/09/2026. Đường dẫn cột File tương đối với src/main/java/vn/ptit/btl16/; các hàng test ghi rõ src/test. Người chính là trách nhiệm học/bảo trì, không khẳng định lịch sử tác giả. File dùng chung theo bảng reviewer trong [13](13_BANG_PHAN_CONG_FILE_THEO_NGUOI.md).

| File | Người chính | Nhiệm vụ |
|---|---|---|
| `client/ClientMain.java` | Thuận (Tiến review network) | Entry point JavaFX |
| `client/ConsoleClientMain.java` | Thuận (Tiến review network) | Console TCP, nhập account đã đăng ký |
| `client/LegacySwingClientMain.java` | Thuận (Tiến review network) | Entry point Swing/TCP legacy |
| `client/controller/ClientController.java` | Thuận (Tiến review network) | Controller Swing legacy |
| `client/controller/HeartbeatService.java` | Thuận (Tiến review network) | Heartbeat cho Swing legacy |
| `client/controller/ReconnectCoordinator.java` | Thuận (Tiến review network) | Backoff cho Swing legacy |
| `client/fx/FxClientController.java` | Thuận (Tiến review network) | UI/input, API, model/event, reconnect, ảnh |
| `client/fx/JavaFxClientApp.java` | Thuận (Tiến review network) | Vòng đời Application JavaFX |
| `client/model/ClientAppModel.java` | Thuận (Tiến review network) | Cache snapshot/bid/join/archive/server clock |
| `client/model/ClientAuction.java` | Thuận (Tiến review network) | Client DTO/state Auction |
| `client/model/ClientBid.java` | Thuận (Tiến review network) | Client DTO/state Bid |
| `client/model/ClientProduct.java` | Thuận (Tiến review network) | Client DTO/state Product |
| `client/model/ClientWireParser.java` | Thuận (Tiến review network) | Wire map → client DTO |
| `client/network/ClientTransport.java` | Thuận (Tiến review network) | Contract request/event/connect |
| `client/network/ConnectionState.java` | Thuận (Tiến review network) | Trạng thái/callback ConnectionState |
| `client/network/ConnectionStateListener.java` | Thuận (Tiến review network) | Trạng thái/callback ConnectionStateListener |
| `client/network/NetworkClient.java` | Thuận (Tiến review network) | TCP framing/read loop/output lock |
| `client/network/TcpClientTransport.java` | Thuận (Tiến review network) | Adapter NetworkClient |
| `client/network/WebSocketClientTransport.java` | Thuận (Tiến review network) | WS JSON, pending future/timeout |
| `client/service/AccountApi.java` | Thuận (Tiến review network) | Client request account/session/profile |
| `client/service/ApiResponse.java` | Thuận (Tiến review network) | Đọc success/error/payload |
| `client/service/AuctionApi.java` | Thuận (Tiến review network) | Client request product/room/bid/host/search/image |
| `client/view/AuctionPanel.java` | Thuận (Tiến review network) | View phòng/bid/host Swing legacy |
| `client/view/AuctionTableModel.java` | Thuận (Tiến review network) | Table auctions Swing |
| `client/view/BidTableModel.java` | Thuận (Tiến review network) | Table bids Swing |
| `client/view/LoginPanel.java` | Thuận (Tiến review network) | Form Swing legacy không điền account mẫu |
| `client/view/MainFrame.java` | Thuận (Tiến review network) | View chính Swing legacy |
| `common/config/AppProperties.java` | Tiến | Đọc Properties UTF-8 và validate kiểu |
| `common/config/ClientConfig.java` | Tiến | Transport/URL/manualConnect/timeout/reconnect |
| `common/config/ServerConfig.java` | Tiến | Config DB/listener/session/rule |
| `common/protocol/ErrorCode.java` | Tiến | Mã lỗi contract |
| `common/protocol/JsonWireMessageCodec.java` | Tiến | JSON WS codec/validation |
| `common/protocol/LengthPrefixedMessageCodec.java` | Tiến | Binary TCP framing/readFully |
| `common/protocol/MessageCodec.java` | Tiến | Contract encode/decode |
| `common/protocol/MessageKind.java` | Tiến | REQUEST/RESPONSE/EVENT |
| `common/protocol/MessageType.java` | Tiến | Enum request/response/event |
| `common/protocol/ProtocolException.java` | Tiến | Phân loại lỗi Protocol |
| `common/protocol/ProtocolLimits.java` | Tiến | Giới hạn frame/field |
| `common/protocol/WireMessage.java` | Tiến | Envelope kind/type/requestId/sequence/data |
| `common/protocol/WireValues.java` | Tiến | Chuyển kiểu field |
| `common/util/Money.java` | Tiến | BigDecimal chuẩn hóa tiền |
| `common/util/Times.java` | Tiến | Chuyển đổi/hiển thị thời gian |
| `common/validation/InputValidation.java` | Tiến | Validate input dùng chung |
| `common/validation/ValidationException.java` | Tiến | Phân loại lỗi Validation |
| `server/ServerApplication.java` | Tiến | Ghép JDBC/module/transport/scheduler, start/close |
| `server/ServerMain.java` | Tiến | Server console |
| `server/ServerSequence.java` | Tiến | Sequence server/bid |
| `server/ServerStats.java` | Tiến | Snapshot thống kê dashboard |
| `server/account/controller/AccountController.java` | Tiến | Parse account request → service |
| `server/account/model/UserAccount.java` | Tiến | Account model |
| `server/account/repository/JdbcConnectionFactory.java` | Tiến | Load JDBC và mở DB/server connection |
| `server/account/repository/JdbcUserRepository.java` | Tiến | SQL user và transaction audit login |
| `server/account/repository/RepositoryException.java` | Tiến | Phân loại lỗi Repository |
| `server/account/repository/UserRepository.java` | Tiến | Contract persistence user |
| `server/account/security/PasswordHash.java` | Tiến | Hash/salt/iterations |
| `server/account/security/PasswordHasher.java` | Tiến | Contract password hashing |
| `server/account/security/Pbkdf2PasswordHasher.java` | Tiến | PBKDF2 hash/verify |
| `server/account/service/AccountException.java` | Tiến | Phân loại lỗi Account |
| `server/account/service/AccountService.java` | Tiến | Register/login/profile/password/session |
| `server/account/service/LoginResult.java` | Tiến | User và session sau login |
| `server/auction/controller/AuctionController.java` | Phước (Dũng/Đức review rule) | Parse auction request và gọi service |
| `server/auction/model/AuctionResult.java` | Đức | Winner/final price/end time |
| `server/auction/model/AuctionRuntime.java` | Phước (Dũng/Đức review rule) | State mutable + khóa từng phòng |
| `server/auction/model/AuctionSnapshot.java` | Phước (Dũng/Đức review rule) | Snapshot immutable của phòng |
| `server/auction/model/AuctionStatus.java` | Phước (Dũng/Đức review rule) | OPEN/ENDED/CANCELLED |
| `server/auction/model/BidRecord.java` | Dũng | Bid ID/user/amount/sequence/time |
| `server/auction/model/Product.java` | Phước (Dũng/Đức review rule) | Metadata/ownership sản phẩm |
| `server/auction/model/ProductImage.java` | Phước (Dũng/Đức review rule) | Nội dung ảnh và metadata |
| `server/auction/model/RoomVisibility.java` | Phước (Dũng/Đức review rule) | PUBLIC/PRIVATE |
| `server/auction/repository/AuctionConflictException.java` | Phước (Dũng/Đức review rule) | Phân loại lỗi AuctionConflict |
| `server/auction/repository/AuctionRepository.java` | Phước (Dũng/Đức review rule) | Contract persistence auction/product |
| `server/auction/repository/AuctionRepositoryException.java` | Phước (Dũng/Đức review rule) | Phân loại lỗi AuctionRepository |
| `server/auction/repository/BidCommit.java` | Dũng | Input immutable cho transaction Bid |
| `server/auction/repository/BlockAuctionUserCommit.java` | Phước (Dũng/Đức review rule) | Input immutable cho transaction BlockAuctionUser |
| `server/auction/repository/CancelAuctionCommit.java` | Đức | Input immutable cho transaction CancelAuction |
| `server/auction/repository/CloseAuctionCommit.java` | Đức | Input immutable cho transaction CloseAuction |
| `server/auction/repository/CreateAuctionCommit.java` | Phước (Dũng/Đức review rule) | Input immutable cho transaction CreateAuction |
| `server/auction/repository/CreateProductCommit.java` | Phước (Dũng/Đức review rule) | Input immutable cho transaction CreateProduct |
| `server/auction/repository/ExtendAuctionCommit.java` | Đức | Input immutable cho transaction ExtendAuction |
| `server/auction/repository/JdbcAuctionRepository.java` | Phước (Dũng/Đức review rule) | SQL product/ảnh/phòng/bid/host/result/block |
| `server/auction/repository/UpdateProductCommit.java` | Phước (Dũng/Đức review rule) | Input immutable cho transaction UpdateProduct |
| `server/auction/service/AuctionBroadcastService.java` | Phước (Dũng/Đức review rule) | Broadcast event room/toàn bộ/user |
| `server/auction/service/AuctionException.java` | Phước (Dũng/Đức review rule) | Phân loại lỗi Auction |
| `server/auction/service/AuctionManagementService.java` | Phước (Dũng/Đức review rule) | CRUD/image/create/join/host/kick, file dùng chung |
| `server/auction/service/AuctionManager.java` | Phước (Dũng/Đức review rule) | Runtime/list/my/archive tombstone |
| `server/auction/service/AuctionQueryService.java` | Phước (Dũng/Đức review rule) | List/detail/history/search |
| `server/auction/service/AuctionTimerService.java` | Đức | Tick/close/retention/archive |
| `server/auction/service/AuctionWireData.java` | Phước (Dũng/Đức review rule) | Snapshot/event → wire fields |
| `server/auction/service/BidOutcome.java` | Dũng | Kết quả service bid/event |
| `server/auction/service/BidService.java` | Dũng | Rule/lock/transaction bid và anti-sniping |
| `server/auction/service/KickOutcome.java` | Dũng | Thông tin sau kick |
| `server/auction/service/ProductImageValidator.java` | Phước (Dũng/Đức review rule) | Giới hạn 700 KiB, signature/MIME PNG/JPEG |
| `server/auction/service/RoomManager.java` | Dũng | Membership theo auction/connection |
| `server/auction/service/RoomMember.java` | Dũng | Danh tính thành viên room |
| `server/dashboard/ServerAddresses.java` | Tiến | IPv4 LAN và URL WS theo cấu hình |
| `server/dashboard/ServerDashboardFrame.java` | Tiến | Swing dashboard, stop/copy LAN |
| `server/dashboard/ServerDashboardMain.java` | Tiến | EXE server startup/error/config |
| `server/db/DatabaseCheckMain.java` | Tiến | Kiểm tra endpoint/driver JDBC, không ghi DB |
| `server/db/DatabaseResetMain.java` | Tiến | DROP DB cấu hình và tạo schema trống |
| `server/db/DatabaseSchema.java` | Tiến | DDL/migration 7 bảng |
| `server/db/DatabaseSetupMain.java` | Tiến | Tạo/upgrade schema, không seed |
| `server/module/AuctionModule.java` | Tiến | Auction/product routes |
| `server/module/CoreAccountModule.java` | Tiến | Account/session/ping routes |
| `server/module/ServerModule.java` | Tiến | Contract đăng ký route |
| `server/network/ClientConnection.java` | Tiến | TCP read/router/send/close |
| `server/network/ConnectionLifecycleListener.java` | Tiến | Callback dọn khi disconnect |
| `server/network/ConnectionRegistry.java` | Tiến | Registry connection TCP/WS |
| `server/network/ServerConnection.java` | Tiến | Contract connection độc lập transport |
| `server/network/ServerMessagingService.java` | Tiến | Gửi connection/user/toàn bộ |
| `server/network/TcpServer.java` | Tiến | TCP acceptor/worker |
| `server/network/WebSocketConnectionAdapter.java` | Tiến | WS → ServerConnection/router |
| `server/network/WebSocketServerTransport.java` | Tiến | WS listener/path và callback |
| `server/routing/MessageHandler.java` | Tiến | Contract route handler |
| `server/routing/MessageRouter.java` | Tiến | Tra route, auth/context và lỗi |
| `server/routing/RequestContext.java` | Tiến | Session/request/connection của handler |
| `server/session/SessionCleanupService.java` | Tiến | Scheduler expiry |
| `server/session/SessionException.java` | Tiến | Phân loại lỗi Session |
| `server/session/SessionManager.java` | Tiến | Map session/login/detach/resume/grant |
| `server/session/SessionStatus.java` | Tiến | Trạng thái session |
| `server/session/UserSession.java` | Tiến | User/token/status/private grants |
| `tools/ConcurrentBidLoadTestMain.java` | Dũng | TCP load-test tạo account/bid thật, chỉ DB thử |
| `src/test/java/vn/ptit/btl16/selftest/AllSelfTests.java` | Thuận (phối hợp chủ module) | Runner suite không cần DB |
| `src/test/java/vn/ptit/btl16/selftest/AuctionManagementSelfTest.java` | Thuận (phối hợp chủ module) | Regression AuctionManagement |
| `src/test/java/vn/ptit/btl16/selftest/DatabaseConfigSelfTest.java` | Thuận (phối hợp chủ module) | Config port/URL hợp lệ và sai |
| `src/test/java/vn/ptit/btl16/selftest/FullNetworkAuctionSelfTest.java` | Thuận (phối hợp chủ module) | Regression FullNetworkAuction |
| `src/test/java/vn/ptit/btl16/selftest/JsonWireMessageCodecSelfTest.java` | Thuận (phối hợp chủ module) | Regression JsonWireMessageCodec |
| `src/test/java/vn/ptit/btl16/selftest/PasswordHasherSelfTest.java` | Thuận (phối hợp chủ module) | Regression PasswordHasher |
| `src/test/java/vn/ptit/btl16/selftest/ProtocolCodecSelfTest.java` | Thuận (phối hợp chủ module) | Regression ProtocolCodec |
| `src/test/java/vn/ptit/btl16/selftest/ServerAddressesSelfTest.java` | Thuận (phối hợp chủ module) | Regression URL LAN |
| `src/test/java/vn/ptit/btl16/selftest/SessionManagerSelfTest.java` | Thuận (phối hợp chủ module) | Regression SessionManager |
| `src/test/java/vn/ptit/btl16/selftest/TestSupport.java` | Thuận (phối hợp chủ module) | Assertion hỗ trợ self-test |
| `src/test/java/vn/ptit/btl16/selftest/WebSocketUpgradeSelfTest.java` | Thuận (phối hợp chủ module) | Regression WebSocketUpgrade |
| `src/test/java/vn/ptit/btl16/selftest/XamppDatabaseSelfTest.java` | Thuận (phối hợp chủ module) | DB thử cách ly: setup/start/reset không seed |
| `src/test/java/vn/ptit/btl16/server/account/repository/TestUserRepository.java` | Thuận (phối hợp chủ module) | Fixture user in-memory, không XAMPP |
| `src/test/java/vn/ptit/btl16/server/auction/repository/TestAuctionRepository.java` | Thuận (phối hợp chủ module) | Fixture auction in-memory, không XAMPP |

## Công cụ/cấu hình và tài liệu

| File/nhóm | Người chính | Nhiệm vụ |
|---|---|---|
| pom.xml, lib/README.txt | Tiến | Dependency JDBC/JavaFX/WS/Jackson và build Java |
| config/server.properties | Tiến | XAMPP db.port/password, TCP/WS, session/rule |
| config/client*.properties | Thuận | Transport/URL/reconnect |
| sql/00_schema.sql, sql/99_reset_database.sql | Tiến + Phước | Schema và reset không seed |
| tools/dev-utils.mjs, dev-runner.mjs | Thuận + Tiến | Build, JDBC check theo config, schema, start/stop |
| tools/client-runner.mjs | Thuận | URL/transport CLI cho JavaFX |
| tools/package-apps.mjs | Thuận + Tiến | Stage dependency/config và jpackage hai EXE |
| tools/package-client.mjs, package-server.mjs, package-desktop.mjs | Thuận | Entry point đóng gói một/cả hai role |
| package.json, 01..06_*.cmd, scripts/* | Thuận + Tiến | Lệnh build/setup/reset/server/client/test/load/EXE |
| .vscode/* | Tiến + Thuận | Java editor và Run/Debug kể cả JDBC check/setup |
| README.md, START_HERE.txt, PROJECT_TREE.txt | Tiến | Điểm vào sử dụng và cây source |
| VERIFICATION.md, docs/07, docs/16 | Thuận + cả nhóm | Ghi kết quả thực, không tick LAN chưa thử |
| docs/00..19 và docs/members | Theo docs/13 | Hướng dẫn kiến trúc/nghiệp vụ, phạm vi học từng người |
| .github/*, .gitignore | Tiến + Thuận | Tích hợp và loại output/local config khỏi Git |

Không còn production seeder/SQL demo. target/dist/out là output sinh tự động, không phải module source cần chia cho người viết. Fixture src/test phải giữ riêng, không nạp vào DB XAMPP.
