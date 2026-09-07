# 01 – Kiến trúc tổng thể

## Hai ứng dụng, một business core

    ClientMain → JavaFxClientApp → FxClientController
      → AccountApi / AuctionApi → ClientTransport
        → WebSocketClientTransport → WS :8890/ws (mặc định)
        → TcpClientTransport → NetworkClient → TCP :8888 (legacy/test)

    ServerDashboardMain (EXE) hoặc ServerMain (console)
      → ServerApplication
        → WebSocketServerTransport → WebSocketConnectionAdapter
        → TcpServer → ClientConnection
          → ServerConnection → MessageRouter → Controller → Service
            → JdbcUserRepository / JdbcAuctionRepository
              → JdbcConnectionFactory → XAMPP MySQL/MariaDB

WebSocket dùng JSON, TCP dùng binary length-prefix. Hai transport dùng chung session/room/runtime/transaction/broadcast. Client không trực tiếp JDBC hoặc tự quyết định giá, quyền và thời điểm kết thúc.

## Thành phần và trách nhiệm

| Thành phần | Vai trò |
|---|---|
| ServerApplication | Composition root: JDBC/schema, module, listener, scheduler, start/close |
| ServerConnection, ConnectionRegistry | Abstraction gửi/đóng kết nối và registry TCP/WS |
| MessageRouter, RequestContext, ServerModule | Tra route, kiểm tra session, đăng ký handler |
| AccountService, SessionManager | Account/password/profile, login, detach/resume/grace |
| AuctionManager, AuctionRuntime | Tập runtime, snapshot và khóa riêng từng auction |
| RoomManager | Thành viên theo connectionId; dọn khi leave/disconnect/kick/archive |
| AuctionManagementService | Product/ảnh, tạo public/private, ownership và host controls |
| BidService | Recheck rule trong khóa → commit bid → runtime → event |
| AuctionTimerService | Tick, đóng đến hạn, giữ kết quả rồi archive khỏi list |
| AuctionBroadcastService, ServerMessagingService | Push theo room/toàn bộ/user qua ServerConnection |
| JDBC repositories | PreparedStatement, transaction, commit/rollback |
| ServerDashboardFrame, ServerAddresses | Dashboard Swing và URL LAN cho client |

## Trạng thái và thread

- DB giữ account/audit, sản phẩm/ảnh BLOB, auction/private hash, bid/result và block user.
- RAM server giữ socket/session/grant/membership/runtime. Restart mất session, client phải login lại.
- RAM client giữ ClientAppModel, pendingRequests và cache ảnh productId:imageVersion; RESYNC phục hồi snapshot khi mất mạng.
- TCP có acceptor/worker/read loop và outputLock; WS dùng callback adapter, không nhân đôi business logic.
- Bid, timer, host controls dùng cùng khóa AuctionRuntime; JDBC thêm SELECT ... FOR UPDATE. Chỉ cập nhật runtime/broadcast khi commit thành công; broadcast ngoài khóa để không giữ khóa do client chậm.
- JavaFX xử lý network callback trên Platform.runLater. EDT chỉ dành dashboard và Swing legacy.

## Triển khai

Server EXE kèm Java/dependency/config nhưng không kèm MySQL. Start MySQL trong XAMPP và chỉnh db.port theo từng máy. Client EXE nhận URL WS từ dashboard; firewall chỉ cần mở WS, không mở DB ra LAN.

Chi tiết [19](19_MULTI_MACHINE_AND_PACKAGING.md); ownership và reviewer ở [13](13_BANG_PHAN_CONG_FILE_THEO_NGUOI.md).
