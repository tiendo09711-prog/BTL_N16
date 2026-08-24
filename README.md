# BTL 16 - SÀN ĐẤU GIÁ REALTIME JAVAFX + WEBSOCKET

Đồ án Lập trình mạng gồm JavaFX desktop client, Java central server, MySQL và hai transport dùng chung một business core:

- WebSocket là transport mặc định của JavaFX client.
- TCP length-prefixed được giữ cho legacy client, console client, load-test và so sánh khi thuyết trình.
- Server quyết định session, quyền phòng, giá bid, winner, timer, anti-sniping và lifecycle.
- Client không kết nối JDBC/MySQL trực tiếp.

## Kiến trúc

```text
                +----------------------+
                |       MySQL          |
                +----------^-----------+
                           |
                          JDBC
                           |
                +----------+-----------+
                |      Java Server     |
                | MessageRouter        |
                | Business Services    |
                | SessionManager       |
                | RoomManager          |
                +----^------------^----+
                     |            |
               TCP :8888      WS :8890/ws
                     |            |
              Legacy/Test      JavaFX Client
```

```text
JavaFX UI
  -> FxClientController
  -> AccountApi / AuctionApi
  -> ClientTransport
       |- WebSocketClientTransport (mặc định)
       `- TcpClientTransport (legacy/test)
  -> ServerConnection
       |- ClientConnection (TCP)
       `- WebSocketConnectionAdapter
  -> MessageRouter -> Services -> Repositories -> MySQL
```

TCP và WebSocket không có business logic riêng. Mọi request đều đi qua cùng `MessageRouter`, session, room, auction lock, transaction và broadcast service. Vì `ConnectionRegistry` lưu `ServerConnection`, event tạo từ client TCP vẫn đến client WebSocket và ngược lại.

## Chức năng

- Register, login, logout, profile, update profile, change password.
- Session resume, reconnect/backoff, RESYNC và rejoin room.
- Product CRUD, ảnh PNG/JPEG, preview, BLOB MySQL, image cache theo `productId:imageVersion`.
- Auction list, My Auctions, search theo tên sản phẩm hoặc Auction ID.
- Public room và private room dùng PBKDF2; quyền đã xác minh thuộc session.
- Join/leave, bid, bid history, realtime update, outbid notification.
- Host extend/end/cancel/kick; block không thể bypass bằng private-room grant.
- Per-auction `ReentrantLock`, transaction và `SELECT ... FOR UPDATE`.
- Timer authoritative trên server, anti-sniping, result/winner và archive.
- JavaFX UI dùng `Platform.runLater(...)` khi nhận callback từ network thread.

## Yêu cầu

- JDK 17 trở lên; môi trường đã xác minh với JDK 26.0.1.
- Maven 3.x.
- Node.js 20 trở lên cho development runner.
- MySQL 8.x; cấu hình mặc định dùng Laragon MySQL tại `127.0.0.1:3306`.
- `jpackage` để tạo app-image phân phối cho máy client.

## Cấu hình mặc định

`config/server.properties`:

```properties
server.tcp.enabled=true
server.tcp.bindAddress=0.0.0.0
server.tcp.port=8888

server.websocket.enabled=true
server.websocket.bindAddress=0.0.0.0
server.websocket.port=8890
server.websocket.path=/ws
```

`config/client.properties`:

```properties
client.transport=websocket
client.websocketUrl=ws://127.0.0.1:8890/ws
```

Có thể override bằng properties/system properties/CLI runner. Không hardcode IP LAN cá nhân.

## Chạy nhanh

Trên máy server:

```bash
npm run dev
```

Lệnh thực hiện:

1. Kiểm tra/start MySQL local.
2. Maven build và copy runtime dependencies.
3. Setup/migrate/seed database `btl_16`.
4. Start Java server với TCP `8888` và WebSocket `8890/ws`.
5. Chờ hai listener ready.
6. In endpoint local/LAN.
7. Mở một JavaFX client local.

Chỉ chạy server:

```bash
npm run dev:server
```

Không setup/seed lại:

```bash
npm run dev:server -- --skip-setup
```

Mở JavaFX client local:

```bash
npm run client
```

Kết nối server LAN:

```bash
npm run client -- --url=ws://192.168.1.10:8890/ws
```

Chạy JavaFX qua TCP legacy:

```bash
npm run client -- --transport=tcp --host=192.168.1.10 --port=8888
```

`ws://.../ws` là WebSocket endpoint, không phải trang web.

## Build và test

```bash
mvn clean test
npm run build
npm test
```

Self-test gồm:

- Binary TCP codec và JSON WebSocket codec.
- Password hashing và session lifecycle.
- Full TCP auction flow, reconnect, timer, archive.
- Product/host management và bid rules.
- WebSocket register/login/ping/logout.
- TCP ↔ WebSocket cross-broadcast.
- Product image roundtrip và payload list không chứa Base64 ảnh.
- Private room password, session grant, reconnect/resync và kick safety.
- Search case-insensitive theo product name và exact Auction ID.

## Đóng gói JavaFX client

```bash
npm run dist:client
```

Kết quả:

```text
dist/
`-- BTL16-Auction-Client/
```

Đây là app-image self-contained do `jpackage` tạo. Máy client khác chỉ cần:

1. Nhận/copy toàn bộ thư mục `BTL16-Auction-Client`.
2. Mở executable trong thư mục đó.
3. Nhập `ws://IP_MAY_SERVER:8890/ws`.
4. Login và sử dụng.

Máy client không cần source code, IDE, Maven, Node.js hay JDK riêng.

## Chạy LAN ba máy

Máy A - server:

```bash
npm run dev:server
```

Mở Windows Firewall inbound TCP:

```text
8888 - TCP legacy/test
8890 - WebSocket JavaFX
3306 - KHÔNG mở cho client
```

Máy B và C:

```text
Mở packaged JavaFX client
Server URL: ws://IP_MAY_A:8890/ws
```

Kịch bản demo:

1. B login, tạo product có ảnh và private room.
2. C search theo tên/mã phòng, thử thiếu/sai/đúng password.
3. Một client TCP và một client WebSocket cùng vào phòng.
4. TCP bid, WebSocket nhận `BID_UPDATE` và outbid event.
5. Ngắt mạng C, reconnect, resume, RESYNC và rejoin không nhập lại password.
6. Host extend/kick/end; server gửi winner và archive đúng lifecycle.

## Kết nối Internet

IP `192.168.x.x` và `10.x.x.x` chỉ dùng trong LAN. Để truy cập từ Internet cần một giải pháp độc lập như:

- Port forwarding + public IP.
- VPN mesh như Tailscale.
- Tunnel/reverse proxy.
- VPS/reverse proxy và `wss://` khi triển khai TLS.

Project không tuyên bố LAN IP là public URL và không bắt buộc tunnel để chạy local/LAN.

## MySQL migration

Database vẫn là `btl_16`. `DatabaseSchema` tự thêm cột còn thiếu, không yêu cầu drop database:

```text
products.image_data
products.image_mime
products.image_name
products.image_size
products.image_version

auctions.visibility
auctions.room_password_hash
auctions.room_password_salt
auctions.room_password_iterations
```

Legacy product không có ảnh dùng placeholder. Legacy auction mặc định `PUBLIC`.

## Main classes

| Mục đích | Main class |
|---|---|
| Server console | `vn.ptit.btl16.server.ServerMain` |
| Server dashboard | `vn.ptit.btl16.server.dashboard.ServerDashboardMain` |
| JavaFX client | `vn.ptit.btl16.client.ClientMain` |
| Swing legacy client | `vn.ptit.btl16.client.LegacySwingClientMain` |
| Console TCP client | `vn.ptit.btl16.client.ConsoleClientMain` |
| Database setup | `vn.ptit.btl16.server.db.DatabaseSetupMain` |
| Database reset | `vn.ptit.btl16.server.db.DatabaseResetMain` |
| Concurrent load test | `vn.ptit.btl16.tools.ConcurrentBidLoadTestMain` |
| Self-tests | `vn.ptit.btl16.selftest.AllSelfTests` |

## Tài liệu

- `docs/17_JAVAFX_WEBSOCKET_UPGRADE.md`
- `docs/18_PRODUCT_IMAGE_PRIVATE_ROOM_SEARCH.md`
- `docs/19_MULTI_MACHINE_AND_PACKAGING.md`
- `docs/members/*` giữ trách nhiệm cũ và có thêm phần nâng cấp JavaFX/WebSocket.

Tài khoản demo:

```text
demo  / demo123
alice / alice123
bob   / bob123
```
