# Hai ứng dụng Auction cho Windows

## Mở ứng dụng

- Máy chủ mở `dist/BTL16-Auction-Server.exe`.
- Những máy còn lại mở `dist/BTL16-Auction-Client.exe`.
- Có thể chỉ chép một EXE tương ứng sang mỗi máy. Không cần cài Java, Maven, Node.js hoặc XAMPP để dùng bản mặc định này.
- Bản đóng gói dành cho Windows 10/11 x64. Lần mở đầu tiên cần một chút thời gian để giải nén runtime vào `%LOCALAPPDATA%/BTL16/DesktopApps`.
- Bộ ứng dụng chưa ký số. Chỉ mở EXE do chính nhóm build hoặc nhận từ nguồn tin cậy; không tắt phần mềm bảo vệ máy.

## Đấu giá qua cùng Wi-Fi

1. Kết nối các máy vào cùng một mạng LAN/Wi-Fi tin cậy, không dùng mạng khách có cô lập thiết bị.
2. Mở Server trên một máy; đợi trạng thái **Đang hoạt động**.
3. Chọn địa chỉ Wi-Fi/LAN phù hợp nếu máy có nhiều card mạng. Bấm **Sao chép mã** và gửi mã cho các máy khác. Cũng có thể dùng **Sao chép địa chỉ**.
4. Mở Client, dán mã vào **Mã kết nối**, bấm **Kết nối**. Mã `AUC-...` chỉ mã hóa IP và cổng hiện tại, không phải mật khẩu truy cập.
5. Mỗi người đăng ký một tài khoản riêng rồi đăng nhập.
6. Người bán vào **Sản phẩm của tôi**, thêm sản phẩm và ảnh, rồi **Tạo phòng đấu giá**.
7. Người mua chọn phòng, bấm **Vào phòng**, nhập giá và bấm **Đặt giá**. Phòng riêng tư yêu cầu mật khẩu phòng.
8. Giữ Server và mạng hoạt động trong suốt phiên. Một người có thể mở Client ngay trên máy Server để tham gia.

Ứng dụng bắt đầu với dữ liệu trống, không cài sẵn tài khoản hoặc phiên đấu giá mẫu.

## Khôi phục mặt hàng đã ngừng

Đăng nhập tài khoản người bán, vào **Sản phẩm của tôi**, chọn mặt hàng đã ngừng. Cột **Khôi phục** cho biết mặt hàng có đủ điều kiện hay không; phần mô tả giải thích trường hợp bị từ chối. Với mặt hàng đủ điều kiện, bấm **Khôi phục mặt hàng**, xác nhận rồi tạo phòng mới khi cần. Bấm **Làm mới** nếu trạng thái vừa thay đổi.

Chỉ chủ sở hữu được khôi phục. Mặt hàng không được có phiên đang mở hoặc phiên đã kết thúc với người thắng, kể cả chưa thanh toán. Phiên bị hủy hoặc kết thúc không có người thắng vẫn cho phép khôi phục nếu đủ điều kiện. Khôi phục không tự tạo phòng, không mở lại phòng cũ, không xóa lịch sử và không cho sửa nội dung/ảnh đã đóng băng.

## Windows Firewall

Server mặc định lắng nghe TCP `8890` cho WebSocket và `8891` cho ảnh/video. Khi Windows hỏi quyền truy cập mạng, chỉ cho phép trên mạng **Private** tin cậy. Không cần mở cổng MySQL, không tắt firewall.

Nếu Client chưa kết nối được, từ máy Client chạy PowerShell:

```powershell
Test-NetConnection IP_MAY_SERVER -Port 8890
Test-NetConnection IP_MAY_SERVER -Port 8891
```

Nếu kết quả là `False`, kiểm tra IP, profile mạng, firewall và chế độ cô lập thiết bị của Wi-Fi. Với mạng tin cậy đã đặt Private, có thể mở PowerShell bằng quyền Administrator trên máy Server và chạy:

```powershell
New-NetFirewallRule -DisplayName "BTL16 Auction LAN" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8890,8891 -Profile Private -RemoteAddress LocalSubnet
```

Không đổi mạng công cộng sang Private nếu không tin cậy mạng đó. Nếu DHCP đổi IP sau khi tắt máy hoặc đổi Wi-Fi, dùng lại mã mới hiện trên Server; không tái sử dụng mã cũ.

## Dữ liệu

- Bản desktop mặc định dùng cơ sở dữ liệu H2 lưu trên ổ đĩa, ở `%LOCALAPPDATA%/BTL16/AuctionServer/auction.mv.db` trên máy Server.
- Tài khoản, sản phẩm, ảnh/video, lịch sử giá và kết quả được giữ lại sau khi đóng/mở ứng dụng. Ảnh/video mới nằm trong thư mục `media` riêng ở thư mục dữ liệu server, database chỉ giữ metadata/tham chiếu. Không nằm trong EXE, runtime hay mã nguồn. Thời gian kết thúc phiên vẫn là thời gian thực; tắt Server không đóng băng đồng hồ đấu giá.
- Sao lưu: đóng Server, sau đó chép nguyên thư mục `%LOCALAPPDATA%/BTL16/AuctionServer` sang nơi an toàn. Không chép riêng file database khi Server đang chạy.
- Database MySQL có sẵn của dự án không bị xóa hoặc thay thế. Dữ liệu MySQL cũ không tự chuyển sang H2.
- Mã nguồn và cấu hình `config/server.properties` vẫn giữ luồng MySQL để học JDBC/XAMPP. Bản đóng gói dùng `config/server-desktop.properties` làm cấu hình mặc định.
- Các thư mục `dist/BTL16-Auction-Server` và `dist/BTL16-Auction-Client` là app-image để kiểm tra hoặc chạy trực tiếp. Nếu dùng EXE bên trong các thư mục này, phải giữ nguyên cả thư mục. EXE trực tiếp ở gốc `dist` đã chứa toàn bộ app-image.

## Đóng gói lại

Trên Windows có JDK 17+ với `jpackage`, Maven, Node.js và .NET Framework compiler:

```powershell
. ./scripts/activate-env.ps1
powershell -ExecutionPolicy Bypass -File tools/prepare-media.ps1
npm run dist
```

Lệnh tạo hai giao diện JavaFX, runtime riêng, hai EXE tự bung ở gốc `dist` và bản app-image tương ứng. Nếu build trên máy khác không có bộ JDK trong script, đặt `JAVA_HOME` và `PATH` tới JDK phù hợp trước khi chạy `npm run dist`.

## Kiểm thử

```powershell
scripts/run-self-tests.cmd
npm run test:packaging
java -cp "target/classes;target/test-classes;target/dependency/*" vn.ptit.btl16.selftest.DesktopUiSelfTest
powershell -ExecutionPolicy Bypass -File tools/smoke-windows-bundles.ps1
```

Self-test desktop dùng database tạm riêng và ba client WebSocket thật để kiểm tra mã kết nối, tài khoản, phòng riêng tư, ảnh, cạnh tranh đặt giá, tự kết thúc và dữ liệu sau restart. UI self-test tạo ảnh trong `target/desktop-preview`, không thêm dữ liệu mẫu vào database sử dụng thật. Smoke-test EXE cần cổng 8890 trống, mở bản đóng gói từ thư mục ngoài dự án với `JAVA_HOME`/Java trên `PATH` bị loại bỏ, dùng database riêng trong `target/bundle-smoke-*`, rồi kiểm tra đặt giá và dữ liệu sau khi dừng/khởi động lại process.

Chi tiết chức năng ảnh/video, định dạng đã thử, giới hạn, chuyển ảnh cũ và kiểm thử cách ly: [Ảnh và video sản phẩm](23_ANH_VIDEO_SAN_PHAM.md). Lượt nâng cấp media không chạy smoke-test EXE, production hay setup/reset `btl_16`; các lệnh smoke-test trước đây không thuộc lượt xác minh này.

Đây là bộ ứng dụng học tập trong mạng LAN tin cậy, chưa phải dịch vụ đấu giá thương mại: WebSocket/HTTP LAN không mã hóa TLS, chưa có thanh toán hoặc xác thực danh tính. Không mở trực tiếp cổng ứng dụng ra Internet và không dùng mật khẩu quan trọng trên các hệ thống khác.
