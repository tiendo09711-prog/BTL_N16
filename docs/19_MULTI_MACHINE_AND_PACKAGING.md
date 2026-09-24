# 19 - MULTI MACHINE VÀ PACKAGING

## Development commands

```bash
npm run dev
npm run dev:server
npm run client
npm run client -- --url=ws://192.168.1.10:8890/ws
npm run client -- --transport=tcp --host=192.168.1.10 --port=8888
npm run dist:client
npm run dist:server
npm run dist
npm run dist:mac:portable
```

`npm run dev` build, kiểm tra JDBC theo cấu hình Java, setup schema không seed, start TCP + WebSocket, chờ ready, in IPv4 LAN và mở JavaFX client local. `dev:server` không mở client.

**macOS:** `npm run dist:mac:portable` tạo gói client/server cho Intel và Apple Silicon ngay trên Windows (máy nhận cần JDK 17+). `npm run dist` trên Mac tạo `.app` có runtime Java đi kèm. Xem `docs/20_MACOS_PACKAGING.md` để chọn đúng gói và cấu hình MySQL/firewall trên Mac.

## Hai ứng dụng EXE riêng biệt

Trên máy build Windows có JDK (`jpackage`), Maven và Node, chạy `06_TAO_HAI_UNG_DUNG_EXE.cmd` hoặc `npm run dist` để tạo cả hai ứng dụng. Có thể build riêng bằng `npm run dist:server` và `npm run dist:client`.

```text
dist/
  BTL16-Auction-Server/
    BTL16-Auction-Server.exe
    app/config/server.properties
    app/...
    runtime/...
    START_HERE.txt
  BTL16-Auction-Client/
    BTL16-Auction-Client.exe
    app/...
    runtime/...
    START_HERE.txt
```

Đây là hai launcher EXE có giao diện, mỗi bộ kèm dependency và Java runtime. Máy sử dụng không cần source, IDE, Maven, Node hay JDK riêng. **Phải giữ/copy nguyên thư mục của từng bộ, không chỉ copy file EXE.** Đây không phải hai file EXE đơn lẻ chứa mọi thứ, cũng không phải installer.

### Máy server

1. Cài XAMPP và Start dòng MySQL trên máy server. Máy local đã kiểm tra với XAMPP MariaDB 10.4.32 qua MySQL Connector/J 8.4.0; DB không được đóng gói trong EXE.
2. Lần đầu, kiểm tra `app/config/server.properties`: mặc định `db.host=127.0.0.1`, `db.port=3306`, `db.name=btl_16`, `db.user=root`, mật khẩu rỗng. Mỗi bạn có thể dùng cổng MySQL khác nhau: sửa db.port theo cột Port(s) của XAMPP, không nhầm với WebSocket 8890. Sửa cho đúng tài khoản MySQL. MySQL không được đóng gói trong EXE.
3. Mở `BTL16-Auction-Server.exe`. Server dùng cấu hình nằm cạnh ứng dụng, không phụ thuộc thư mục đang làm việc của shortcut. Cấu hình trong bộ server cũ được giữ lại khi build lại.
4. Khi server sẵn sàng, chọn địa chỉ LAN cùng mạng với client và bấm **Sao chep dia chi**, ví dụ `ws://192.168.1.18:8890/ws`. Địa chỉ được tạo từ IPv4 thực tế, cổng và path đã cấu hình, không phải IP cố định.
5. Gửi địa chỉ cho các máy client. Giữ server mở trong suốt phiên đấu giá. Đóng cửa sổ hoặc bấm **Dung server** sẽ ngắt các client.

Nếu MySQL chưa bật, sai tài khoản hoặc cổng server bị chiếm, EXE hiện thông báo lỗi và đường dẫn cấu hình để sửa. Nó không hiển thị địa chỉ “sẵn sàng” khi khởi động thất bại.

Nếu đổi Wi-Fi/IP, bấm **Lam moi dia chi** và gửi địa chỉ mới. Nút sao chép bị vô hiệu hóa nếu không tìm được IPv4 LAN hoặc WebSocket không được bật. Khi có nhiều card mạng/VPN, chọn IP cùng mạng với client.

### Máy client

1. Nhận nguyên thư mục `BTL16-Auction-Client` (có thể nén ZIP để gửi rồi giải nén).
2. Mở `BTL16-Auction-Client.exe`.
3. Dán nguyên địa chỉ được server cung cấp vào ô **Địa chỉ server**, bấm **Kết nối** hoặc Enter.
4. Đăng ký tài khoản mới rồi đăng nhập. DB mới trống hoàn toàn: người bán thêm sản phẩm/tạo phòng trước, người mua dùng account khác để vào bid. Không có tài khoản demo.

Bản EXE client chờ nhập địa chỉ, không tự kết nối nhầm về `127.0.0.1`. Máy client không cần MySQL hoặc source code. Địa chỉ cần được nhập lại khi mở một phiên client mới.

## LAN

Server bind:

```text
TCP       0.0.0.0:8888
WebSocket 0.0.0.0:8890/ws
```

Windows Firewall inbound:

```text
TCP 8888 - legacy/test
TCP 8890 - JavaFX WebSocket
```

Không mở MySQL 3306 cho máy client. Client chỉ kết nối Java server.

Máy client nhập:

```text
ws://IP_MAY_SERVER:8890/ws
```

Đây là WebSocket endpoint, không phải website.

## Demo ba máy

Máy A: Start MySQL trong XAMPP, mở `BTL16-Auction-Server.exe`, sao chép địa chỉ LAN cho B và C.

Máy B:

```text
Mở packaged JavaFX client
Tạo product có ảnh và private room
```

Máy C:

```text
Search room
Thử thiếu/sai/đúng password
Bid và nhận realtime event
Ngắt mạng rồi reconnect/resume/resync
```

Có thể mở một TCP legacy client trên A/B để demo cross-transport.

## Internet

`192.168.x.x` và `10.x.x.x` là private LAN. Remote Internet cần port forwarding/public IP, VPN mesh, tunnel hoặc reverse proxy/VPS. Khi triển khai TLS/reverse proxy có thể dùng `wss://`.

Tunnel không phải dependency bắt buộc. LAN phải chạy độc lập.

## Giới hạn packaging

Runtime Java được đóng gói sẵn; MySQL vẫn là dịch vụ riêng trên server. Bộ Windows phải được build trên Windows và dùng trên máy tương thích với kiến trúc runtime được đóng gói. Không gửi bộ server chứa cấu hình database cho máy client.

Firewall cần cho phép cổng WebSocket trên máy server; ứng dụng không tự thay đổi firewall hay yêu cầu quyền Administrator. Sau khi mở server, kiểm tra từ một máy client bằng PowerShell:

```powershell
Test-NetConnection IP_MAY_SERVER -Port 8890
```

Cần `TcpTestSucceeded : True`, rồi thử hai client cùng vào một phòng, bid và kiểm tra cập nhật realtime. Cùng Wi-Fi nhưng mạng guest/AP isolation vẫn có thể chặn kết nối giữa các máy.

## Khi client kết nối lâu hoặc không kết nối được

- Kết nối lần đầu thất bại sẽ báo lỗi để sửa IP/cổng và thử lại, không tự lặp 30 lần. Timeout mặc định cho một lần kết nối là 5 giây; không phải thời gian chờ bắt buộc khi mạng hoạt động tốt.
- Tự kết nối lại chỉ bật sau khi đã kết nối thành công. Khi mất mạng, client vẫn thử lại để khôi phục phiên. Bấm **Kết nối** thủ công sẽ hủy lịch thử lại và lần bắt tay cũ trước khi dùng địa chỉ mới.
- XAMPP chỉ cần chạy MySQL trên máy server. Client kết nối WebSocket tới Java server, không kết nối Apache/MySQL trực tiếp.
- Trên máy client, chạy `Test-NetConnection IP_MAY_SERVER -Port 8890`. Nếu `TcpTestSucceeded` là `False`, kiểm tra IP được server hiển thị, cổng cấu hình, firewall và mạng trước; tăng timeout không giải quyết được việc cổng bị chặn.
- Trên máy server, dùng `Get-NetConnectionProfile` để kiểm tra profile mạng. Rule chỉ áp dụng cho **Private** sẽ không áp dụng nếu Wi-Fi đang là **Public**. Chỉ chuyển sang Private nếu đây là mạng LAN đáng tin cậy; không tắt toàn bộ firewall.
- Với mạng LAN tin cậy đã đặt Private, có thể mở PowerShell **Run as administrator** và tạo rule giới hạn mạng con nội bộ (đổi cổng nếu cấu hình khác):

```powershell
New-NetFirewallRule -DisplayName "BTL16 WebSocket LAN" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8890 -Profile Private -RemoteAddress LocalSubnet
```

Sau khi sửa source, cần đóng gói lại bằng `npm run dist:client` rồi gửi lại **nguyên thư mục** `dist/BTL16-Auction-Client`. EXE đã gửi trước đó không tự nhận bản sửa. Không cần đóng gói lại server nếu chỉ cập nhật luồng kết nối client.
