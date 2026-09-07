# ĐỖ TIẾN – Hạ tầng, tài khoản, session và JDBC

Đối chiếu code ngày 07/09/2026. Đây là nhiệm vụ học, giải thích, kiểm thử và bảo trì phần đã triển khai.

## Phạm vi

Chịu trách nhiệm ghép hệ thống, protocol TCP/JSON, server transport, router, account/session, XAMPP/JDBC/schema và dashboard EXE. Không phải viết lại những phần đã có.

## Thứ tự đọc file

Đường dẫn Java tương đối với src/main/java/vn/ptit/btl16/, trừ src/test và công cụ gốc. Tra toàn bộ file/ownership ở [09](../09_NHIEM_VU_TUNG_FILE.md) và [13](../13_BANG_PHAN_CONG_FILE_THEO_NGUOI.md).

1. common/config/AppProperties, ServerConfig, ClientConfig: đọc file source hoặc config EXE; cổng DB độc lập WS/TCP.
2. common/protocol/WireMessage, MessageKind, MessageType, ErrorCode, ProtocolLimits, LengthPrefixedMessageCodec, JsonWireMessageCodec.
3. server/network/ServerConnection, ClientConnection, TcpServer, WebSocketConnectionAdapter, WebSocketServerTransport, ConnectionRegistry.
4. server/routing/MessageRouter, RequestContext; server/module/ServerModule, CoreAccountModule, AuctionModule.
5. server/account/controller/AccountController → service/AccountService → repository/UserRepository, JdbcUserRepository, JdbcConnectionFactory.
6. server/account/security/PasswordHasher, Pbkdf2PasswordHasher, PasswordHash; server/session/UserSession, SessionManager, SessionCleanupService.
7. server/db/DatabaseCheckMain, DatabaseSetupMain, DatabaseResetMain, DatabaseSchema; sql/00_schema.sql, sql/99_reset_database.sql, pom.xml.
8. server/ServerApplication, ServerMain, ServerStats, ServerSequence; server/dashboard/ServerDashboardMain, ServerDashboardFrame, ServerAddresses.
9. tools/dev-utils.mjs, dev-runner.mjs và scripts setup/reset/run-server; phối hợp Thuận về package-apps.mjs.

## Luồng phải tự giải thích

**Request:** WebSocketClientTransport → WebSocketServerTransport → WebSocketConnectionAdapter → MessageRouter.route → AccountController → AccountService. TCP dùng ClientConnection nhưng cùng router/service.

**Login:** kiểm tra input → tìm user → verify PBKDF2 → create session → ghi last_login/login_history → LOGIN_RESULT user/token. Truy vết lỗi rollback và duplicate login.

**Disconnect/resume:** lifecycle bỏ membership và detach session; kết nối mới RESUME_SESSION bằng token trong grace, sau đó RESYNC. Token mất khi server restart.

**JDBC mới:** build tải Connector/J → DatabaseCheckMain đọc đúng host/port → setup 7 bảng trống → server nạp JDBC repositories. Setup giữ dữ liệu, reset xóa toàn DB đã cấu hình; không seeder.

**EXE:** ServerDashboardMain start ứng dụng trước khi hiển thị dashboard; ServerAddresses lọc LAN và tạo WS URL theo bind/port/path. Config EXE được truyền bằng btl16.server.config, không phụ thuộc shortcut cwd.

## Biến và bất biến cần nhớ

connectionId khác userId và sessionToken; requestId ghép future, serverSequence đánh thứ tự; pendingRequests/outputLock thuộc network; byToken/tokenByConnection/tokenByUser và mutationLock thuộc session; db.port là cổng XAMPP, không phải 8890.

## Kiểm thử và bài thực hành

- ProtocolCodecSelfTest, JsonWireMessageCodecSelfTest, PasswordHasherSelfTest, SessionManagerSelfTest, ServerAddressesSelfTest, DatabaseConfigSelfTest.
- XamppDatabaseSelfTest chạy riêng: schema trống, startup không seed, setup giữ dữ liệu, reset sạch.
- Tự đổi db.port trong config thử và kiểm tra JDBC báo đúng endpoint; không đổi cấu hình XAMPP của người khác.
- Start EXE khi DB tắt/sai mật khẩu để đọc lỗi; bật DB rồi thử dashboard copy URL (GUI cần nhóm kiểm tra trực tiếp).

## Câu hỏi bảo vệ

Tại sao vừa TCP vừa WS? Hai cách đóng gói/vận chuyển message để so sánh nhưng cùng business core. Tại sao client không có DB password? Client chỉ gọi API. Tại sao ConcurrentHashMap vẫn cần mutationLock? Một thao tác session cập nhật nhiều map phải nguyên tử. XAMPP có làm Java server không? Không, XAMPP chỉ cung cấp DB trong hệ thống này.

## Phối hợp và tiêu chí bàn giao

Phước review schema/repository, Dũng review session/grant/block, Đức review shutdown/lifecycle, Thuận review transport/config/packaging.

- Tự chỉ được entry point, request, service/repository và event tương ứng, không chỉ nhớ tên lớp.
- Chạy lại test liên quan, ghi đúng kết quả/chỗ chưa thử; sửa protocol/schema thì cập nhật docs chung.
- Môi trường VS Code + XAMPP; xem [05](../05_CACH_CHAY_VSCODE_XAMPP.md). db.port theo mỗi máy; client chỉ cần URL WS.
- DB thật không có seed/account mẫu. Fixture test giữ riêng trong src/test; diễn tập tự đăng ký và tạo dữ liệu.
