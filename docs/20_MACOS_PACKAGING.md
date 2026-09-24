# Chạy client và server trên macOS

Windows và macOS dùng chung giao thức WebSocket. Có thể chạy server Windows với client Mac hoặc ngược lại. Client luôn nhập địa chỉ hiện tại từ dashboard server, không có IP cố định.

## 1. Bản portable tạo được ngay trên Windows

Tại thư mục source, cài JDK 17+, Maven, Node.js 20+ và công cụ `tar`, rồi chạy:

```text
npm run dist:mac:portable
```

Lệnh build Java và tải riêng thư viện JavaFX gốc cho từng kiến trúc Mac; không sao chép thư viện JavaFX Windows sang Mac. Kết quả trong `dist/`:

| Gói `.tar.gz` | Máy nhận |
| --- | --- |
| `BTL16-Auction-Client-macOS-arm64-portable.tar.gz` | Client Mac Apple Silicon (M-series) |
| `BTL16-Auction-Server-macOS-arm64-portable.tar.gz` | Server Mac Apple Silicon (M-series) |
| `BTL16-Auction-Client-macOS-x64-portable.tar.gz` | Client Mac Intel |
| `BTL16-Auction-Server-macOS-x64-portable.tar.gz` | Server Mac Intel |

**Portable không chứa Java.** Máy nhận cần cài JDK macOS 17 trở lên đúng kiến trúc (`arm64` hoặc `x64`). Không cần Maven, Node.js hay source trên máy nhận. Launcher kiểm tra phiên bản/kiến trúc Java và báo lỗi nếu không phù hợp. Xem loại chip trong **About This Mac**; dùng Java ARM cho gói ARM, không dùng Java Intel chạy qua Rosetta cho gói ARM.

### Máy client

1. Nhận đúng gói client, giải nén **nguyên thư mục**.
2. Mở `BTL16-Auction-Client.command`.
3. Nếu file mất quyền thực thi, mở Terminal trong thư mục vừa giải nén và chạy:

```bash
bash ./BTL16-Auction-Client.command
```

4. Dán địa chỉ WebSocket do server cung cấp, bấm Kết nối. Không cài MySQL trên máy client.

### Máy server

1. Nhận đúng gói server, giải nén nguyên thư mục.
2. Cài/chạy MySQL hoặc MariaDB cục bộ, có thể dùng XAMPP nếu bản cài tương thích máy Mac. Không bắt buộc XAMPP; ứng dụng cần dịch vụ DB, không cần Apache.
3. Sửa `config/server.properties` trong gói: `db.host`, `db.port`, `db.user`, `db.password` theo DB thực tế. DB mặc định là `btl_16`; ứng dụng có thể tạo schema nếu tài khoản có quyền.
4. Mở `BTL16-Auction-Server.command`, hoặc chạy từ Terminal trong thư mục gói:

```bash
bash ./BTL16-Auction-Server.command
```

5. Cho phép kết nối đến Java/server trong firewall macOS nếu được hỏi, chỉ trên mạng tin cậy. Không cần mở cổng MySQL cho client; cổng ứng dụng mặc định là TCP 8890.
6. Sao chép địa chỉ LAN trên dashboard cho client. Đổi mạng thì làm mới địa chỉ và gửi lại IP mới.

Các lệnh `New-NetFirewallRule`/`Test-NetConnection` là dành cho Windows, không chạy trên Mac. Có thể kiểm tra cổng từ Terminal Mac bằng `nc -vz IP_MAY_SERVER 8890`.

Script đóng gói giữ lại cấu hình server trong thư mục output nếu đã tồn tại. Kiểm tra/xóa thông tin DB riêng tư trước khi chia sẻ gói server; chỉ gửi gói client cho người mua/bán.

## 2. Bản `.app` có Java đi kèm: build trên Mac

`jpackage` không cross-build `.app` macOS trên Windows. Trên Mac cần source, JDK 17+ có `jpackage`, Maven và Node.js 20+. Node.js và JDK phải cùng kiến trúc; thư viện JavaFX cũng phải tương ứng.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v '17+')
export PATH="$JAVA_HOME/bin:$PATH"
npm run dist
```

Kết quả trên Apple Silicon:

```text
dist/macos-arm64/BTL16-Auction-Client.app
dist/macos-arm64/BTL16-Auction-Server.app
```

Trên Intel: thư mục `dist/macos-x64/`. Có thể chỉ build một phía bằng `npm run dist:client` hoặc `npm run dist:server`. Các lệnh này trên Windows vẫn tạo EXE ở đường dẫn cũ, không ghi đè các gói Mac.

`.app` chứa runtime Java, nên máy nhận không cần cài Java riêng. Server vẫn cần MySQL. Cấu hình DB nằm tại `BTL16-Auction-Server.app/Contents/app/config/server.properties`; trong Finder, dùng **Show Package Contents** để mở. File hướng dẫn nằm cạnh `.app`, ngoài app bundle.

Để gửi ứng dụng và giữ cấu trúc bundle, chạy trên máy Mac build:

```bash
ditto -c -k --sequesterRsrc --keepParent dist/macos-arm64/BTL16-Auction-Client.app dist/BTL16-Auction-Client-macOS-arm64.zip
ditto -c -k --sequesterRsrc --keepParent dist/macos-arm64/BTL16-Auction-Server.app dist/BTL16-Auction-Server-macOS-arm64.zip
```

Đổi `arm64` thành `x64` trên Intel. Đây là bản build nội bộ **chưa ký/notarize**; macOS có thể cảnh báo khi tải từ máy khác. Chỉ mở bản bạn biết rõ nguồn gốc, theo cơ chế cho phép ứng dụng của macOS; không tắt Gatekeeper toàn hệ thống.

## 3. Kiểm tra và giới hạn xác minh

```text
npm run test:packaging
```

Test kiểm tra layout Windows/Mac, đường dẫn cấu hình trong `.app`, launcher portable và loại bỏ thư viện JavaFX sai nền tảng/kiến trúc. Build portable thành công trên Windows không thay thế việc chạy thử GUI trên Mac.

Trước khi bàn giao, thử trên Mac thật: mở cả hai giao diện, kết nối server, đăng ký/đăng nhập bằng tài khoản thử, tạo phòng/bid từ hai máy, ngắt/kết nối lại. Nếu có cả Intel và ARM, thử riêng từng gói. Các test Java trong dự án vẫn chạy bằng `sh scripts/run-self-tests.sh` trên Mac.

`dist/` đã được ignore: gửi archive riêng, không commit thư viện hay runtime vào Git. Các script và tài liệu build mới là phần cần đưa vào repository.
