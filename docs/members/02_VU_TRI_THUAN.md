# VŨ TRÍ THUẬN – JavaFX, realtime, reconnect, kiểm thử và đóng gói

Đối chiếu code ngày 07/09/2026. Đây là nhiệm vụ học, giải thích, kiểm thử và bảo trì phần đã triển khai.

## Phạm vi

Chịu trách nhiệm client chính JavaFX/WebSocket, API/model, event/reconnect và bộ công cụ chạy/đóng gói. ClientController/MainFrame/AuctionPanel chỉ là client Swing legacy, không phải luồng Client EXE hiện tại.

## Thứ tự đọc file

Đường dẫn Java tương đối với src/main/java/vn/ptit/btl16/, trừ src/test và công cụ gốc. Tra toàn bộ file/ownership ở [09](../09_NHIEM_VU_TUNG_FILE.md) và [13](../13_BANG_PHAN_CONG_FILE_THEO_NGUOI.md).

1. client/ClientMain → client/fx/JavaFxClientApp → FxClientController.
2. client/network/ClientTransport, WebSocketClientTransport, TcpClientTransport, NetworkClient, ConnectionState/Listener.
3. client/service/AccountApi, AuctionApi, ApiResponse; client/model/ClientWireParser, ClientAppModel, ClientAuction, ClientBid, ClientProduct.
4. FxClientController: kết nối/login, loadAuctionList/search/joinSelected/placeBid, showProducts/showCreateAuction, host controls.
5. FxClientController.onServerEvent/handleServerEvent, scheduleReconnect/scheduleReconnectAttempt, heartbeat, applyResync.
6. tools/client-runner.mjs, dev-runner.mjs, dev-utils.mjs; package-client.mjs, package-server.mjs, package-desktop.mjs, package-apps.mjs.
7. package.json, 06_TAO_HAI_UNG_DUNG_EXE.cmd, .vscode/launch.json, scripts/run-self-tests.cmd; toàn bộ src/test và test fixtures.
8. client/LegacySwingClientMain, controller/ClientController, HeartbeatService, ReconnectCoordinator, view/* để giải thích TCP/Swing cũ.

## Luồng phải tự giải thích

**Kết nối EXE:** manualConnect chờ URL server → connect → bật đăng ký/login. Không tự mặc định kết nối localhost trên máy khác.

**Request:** UI → AccountApi/AuctionApi → ClientTransport pendingRequests → response future → Platform.runLater → parser/model/render. Không block UI bằng chờ network đồng bộ.

**Event:** onServerEvent chuyển về FX thread → handleServerEvent → cập nhật snapshot/bid/tick; AUCTION_ARCHIVED gọi model.removeAuction, AUCTION_KICKED bỏ trạng thái joined. Event có thể đến trước response; model/version/bidId tránh state cũ/trùng.

**Reconnect:** retry/backoff → kết nối lại → resume token → resync/joined snapshot. Session hết hạn hoặc server restart thì yêu cầu login, không tự xem token RAM là hợp lệ mãi.

**Ảnh:** yêu cầu GET_PRODUCT_IMAGE riêng, cache productId:imageVersion; list không mang Base64 ảnh.

**Packaging:** package-desktop gọi packageApplications cho server/client; package-apps stage jar/dependency/config rồi jpackage app-image. Giữ config server cũ khi build lại, copy cả app/runtime.

## Biến và bất biến cần nhớ

transport, model, pendingRequests, requestId, joinedAuctionId, reconnecting, imageCache, archivedAuctionIds, serverNow/endTime. Phân biệt FX Application Thread với Swing EDT; HeartbeatService/ReconnectCoordinator riêng thuộc legacy, FX dùng scheduler trong controller.

## Kiểm thử và bài thực hành

- npm test phải chạy cả TCP và WebSocketUpgradeSelfTest; mvn test riêng không đủ main-based suite.
- Phối hợp chạy DatabaseConfigSelfTest/XamppDatabaseSelfTest, không seed vào DB người dùng.
- Kiểm tra hai Client EXE trên LAN, event bid/tick/kick/archive; thử đổi URL server, sai URL, reconnect/session expiry.
- npm run dist tạo cả hai folder; kiểm tra config EXE cũ được giữ, runtime/JDBC được đóng gói, client không cần XAMPP.
- Load-test tạo account/bid thật: chỉ chạy DB thử đã có PUBLIC room; đừng chạy nếu cần giữ btl_16 trống.

## Câu hỏi bảo vệ

Tại sao Platform.runLater? Network thread không được tùy ý sửa JavaFX UI. requestId khác event thế nào? Response ghép request, event push không đợi request. Copy mỗi EXE được không? Không, launcher cần app/runtime. Test fixture có chứng minh JDBC không? Không, phải chạy integration riêng.

## Phối hợp và tiêu chí bàn giao

Tiến review transport/config/EXE server; Phước review product/model/ảnh/search; Dũng review bid/kick; Đức review timer/result/archive.

- Tự chỉ được entry point, request, service/repository và event tương ứng, không chỉ nhớ tên lớp.
- Chạy lại test liên quan, ghi đúng kết quả/chỗ chưa thử; sửa protocol/schema thì cập nhật docs chung.
- Môi trường VS Code + XAMPP; xem [05](../05_CACH_CHAY_VSCODE_XAMPP.md). db.port theo mỗi máy; client chỉ cần URL WS.
- DB thật không có seed/account mẫu. Fixture test giữ riêng trong src/test; diễn tập tự đăng ký và tạo dữ liệu.
