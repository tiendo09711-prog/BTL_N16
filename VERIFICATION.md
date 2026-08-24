# VERIFICATION - TRẠNG THÁI BUILD VÀ KIỂM THỬ

Ngày kiểm tra: 2026-08-24

## 1. Môi trường

```text
OpenJDK 26.0.1
Compile target: --release 17
Node.js v24.13.0
npm 11.6.2
MySQL 127.0.0.1:3306
jpackage: C:\Apache NetBeans\jdk\bin\jpackage.exe
```

Project dùng JavaFX 21.0.4, Java-WebSocket 1.6.0, Jackson 2.17.2, MySQL Connector/J 8.4.0 và SLF4J NOP runtime binding.

## 2. Maven clean test

Lệnh:

```bash
mvn clean test
```

Kết quả thật:

```text
Compiling 120 source files with release 17
Compiling 11 test source files with release 17
BUILD SUCCESS
```

Hai repository fixture có tên theo convention JUnit 3 nhưng không chứa method `test*`, vì vậy Surefire báo `Tests run: 0`. Main-based self-test bắt buộc được chạy riêng bằng `npm test`.

## 3. Self-test

Lệnh:

```bash
npm test
```

Kết quả:

```text
[PASS] ProtocolCodecSelfTest
[PASS] JsonWireMessageCodecSelfTest
[PASS] PasswordHasherSelfTest
[PASS] SessionManagerSelfTest
[PASS] FullNetworkAuctionSelfTest
[PASS] AuctionManagementSelfTest
[PASS] WebSocketUpgradeSelfTest
ALL SELF-TESTS PASSED
```

`FullNetworkAuctionSelfTest` xác minh TCP login/list/join/bid, concurrent bid, outbid, resume/resync, timer, ended và archive.

`WebSocketUpgradeSelfTest` xác minh:

```text
WebSocket connect + CONNECTION_WELCOME
register/login/ping/logout
WebSocket tạo product có ảnh và private auction
TCP nhận AUCTION_CREATED từ WebSocket
join private thiếu/sai/đúng password
TCP bid và WebSocket nhận BID_UPDATE
search product name case-insensitive
search exact Auction ID
GET_PRODUCT_IMAGE roundtrip
AUCTION_LIST không chứa imageBase64
disconnect TCP -> resume session qua WebSocket
RESYNC private room không hỏi lại password
kick revoke grant và block không bị bypass
```

## 4. npm build

Lệnh:

```bash
npm run build
```

Kết quả: exit code 0; Maven package và copy runtime dependencies thành công.

## 5. MySQL setup/migration

Lệnh:

```bat
scripts\setup-db.cmd
```

Kết quả:

```text
DATABASE SETUP COMPLETE
Database: btl_16
MySQL: 127.0.0.1:3306
```

Đã query trực tiếp `information_schema.COLUMNS`; các cột migration tồn tại:

```text
auctions.room_password_hash
auctions.room_password_iterations
auctions.room_password_salt
auctions.visibility
products.image_data
products.image_mime
products.image_name
products.image_size
products.image_version
```

## 6. Development runner

Lệnh server-only đã chạy:

```bash
npm run dev:server -- --skip-setup
```

Xác nhận:

```text
MySQL ready
TCP listening 0.0.0.0:8888
WebSocket listening 0.0.0.0:8890/ws
Runner in local và LAN endpoint
Ctrl+C dừng WebSocket và TCP sạch
```

Lệnh đầy đủ đã chạy:

```bash
npm run dev -- --skip-setup
```

Xác nhận JavaFX client local được mở và server ghi nhận một WebSocket connection tại `/ws`. Sau smoke test, runner dừng client/server sạch. JDK 26 in cảnh báo JavaFX classpath/native-access, nhưng ứng dụng vẫn khởi động và kết nối; đây không phải compilation/runtime failure.

## 7. jpackage

Lệnh:

```bash
npm run dist:client
```

Kết quả:

```text
dist/BTL16-Auction-Client/
BTL16-Auction-Client.exe
app/
runtime/
```

App-image có 329 file, tổng kích thước 147.883.628 byte (xấp xỉ 141,03 MiB hoặc 148 MB hệ thập phân). Packaged executable được start hidden trong 6 giây, process vẫn chạy (`PACKAGED_CLIENT_RUNNING=True`) rồi được dừng bằng process tree cleanup. Điều này xác minh launcher/runtime image không thoát lỗi ngay khi khởi động.

## 8. Phần chưa runtime verify

- Chưa demo bằng ba máy vật lý trong cùng LAN.
- Chưa kiểm tra Windows Firewall thực tế trên máy khác.
- Chưa kiểm tra kết nối từ Internet, `wss://`, reverse proxy hoặc VPN mesh.
- Chưa tạo MSI/installer; đã tạo app-image self-contained không cần WiX trên máy client.
- GUI đã smoke-test khởi động/kết nối, nhưng chưa thực hiện toàn bộ thao tác thủ công bằng chuột trên packaged app.

Các phần trên không được đánh dấu pass trong checklist bàn giao; mã nguồn, automated self-test, MySQL migration, runner và packaging đã được thực thi thật.
