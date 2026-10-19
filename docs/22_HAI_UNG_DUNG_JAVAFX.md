# Hai ứng dụng Auction cho Windows

## Mở ứng dụng

- Máy chủ mở `dist/BTL16-Auction-Server.exe`.
- Những máy còn lại mở `dist/BTL16-Auction-Client.exe`.
- Có thể chỉ chép một EXE tương ứng sang mỗi máy. Java và công cụ media đã được đóng gói. **Máy Server phải cài XAMPP và bật MySQL**; Client không cần XAMPP, Java, Maven hoặc Node.js.
- Bản đóng gói dành cho Windows 10/11 x64. Lần mở đầu tiên cần một chút thời gian để giải nén runtime vào `%LOCALAPPDATA%/BTL16/DesktopApps`.
- Bộ ứng dụng chưa ký số. Chỉ mở EXE do chính nhóm build hoặc nhận từ nguồn tin cậy; không tắt phần mềm bảo vệ máy.

## Đấu giá qua cùng Wi-Fi

1. Kết nối các máy vào cùng một mạng LAN/Wi-Fi tin cậy, không dùng mạng khách có cô lập thiết bị.
2. Bật **MySQL** trong XAMPP trên máy chủ (mặc định cổng 3306), rồi mở Server và đợi trạng thái **Đang hoạt động**. Apache không bắt buộc.
3. Chọn địa chỉ Wi-Fi/LAN phù hợp nếu máy có nhiều card mạng. Bấm **Sao chép mã** và gửi mã cho các máy khác. Cũng có thể dùng **Sao chép địa chỉ**.
4. Mở Client, dán mã vào **Mã kết nối**, bấm **Kết nối**. Mã `AUC-...` chỉ mã hóa IP và cổng hiện tại, không phải mật khẩu truy cập.
5. Mỗi người đăng ký một tài khoản riêng rồi đăng nhập.
6. Người bán vào **Sản phẩm của tôi**, thêm sản phẩm và ảnh, rồi **Tạo phòng đấu giá**.
7. Người mua nạp ví demo và chờ server duyệt như hướng dẫn tiếp theo, rồi chọn phòng, bấm **Vào phòng**, nhập giá và bấm **Đặt giá**. Phòng riêng tư yêu cầu mật khẩu phòng.
8. Giữ Server và mạng hoạt động trong suốt phiên. Một người có thể mở Client ngay trên máy Server để tham gia.

Ứng dụng bắt đầu với dữ liệu trống, không cài sẵn tài khoản hoặc phiên đấu giá mẫu.

## Ví, QR demo và duyệt nạp

- Mọi tài khoản có số dư ban đầu **0 đồng**. Trên Client, mở **Ví & nạp tiền** để xem số dư, tiền đang giữ chỗ, số dư khả dụng, yêu cầu nạp và sổ giao dịch.
- Nhập từ **1.000 đến 1.000.000.000 đồng**, tối đa hai chữ số thập phân, bấm **Hiện QR demo**. Mã QR có thể quét, nhưng chỉ chứa mã yêu cầu và số tiền mô phỏng, không có thông tin chuyển khoản thật. Bấm **Đã nhập** để gửi yêu cầu.
- Client chỉ tạo yêu cầu **Chờ duyệt**, không tự cộng tiền. Mỗi tài khoản chỉ có một yêu cầu đang chờ; gửi lại cùng mã không tạo lần nạp thứ hai.
- Trên Server, mở **Tài khoản & ví → Duyệt nạp demo**, chọn yêu cầu, bấm **Chấp nhận & cộng tiền** hoặc **Từ chối** rồi xác nhận. Chấp nhận cộng đúng số tiền một lần; từ chối không thay đổi số dư. Client tự cập nhật sau vài giây.
- Tab **Tài khoản & giao dịch** trên Server cho phép xem tên đăng nhập, họ tên, email, điện thoại, ngày tạo/đăng nhập, số dư, giữ chỗ, lịch sử nạp và giao dịch của từng tài khoản. Không hiển thị mật khẩu.
- Khi đặt giá hợp lệ, server giữ chỗ **toàn bộ mức giá** của người dẫn đầu. Số dư khả dụng bằng số dư trừ tổng giữ chỗ ở mọi phòng; không thể dùng cùng tiền dẫn đầu nhiều phòng. Bị vượt giá sẽ được giải phóng giữ chỗ; rời phòng, đăng xuất hoặc mất mạng không hủy cam kết mua.
- Khi hết phiên có người thắng, server trừ giá thắng khỏi ví người mua, giải phóng giữ chỗ và cộng đúng số tiền đó cho người bán trong cùng giao dịch. Đóng lại phiên không thanh toán lần hai. Không có người thắng thì không chuyển tiền; bản demo không tính phí, không hỗ trợ rút tiền hay giao hàng.

## Phòng hết giờ và dữ liệu cũ

- Phòng kết thúc bị ẩn ngay khỏi danh sách tìm kiếm/sàn. Người đang ở phòng vẫn xem được kết quả, không thể đặt giá thêm; sau đếm ngược **60 giây** sẽ tự trở về sàn. Lưu trữ phòng không xóa lịch sử, kết quả hoặc giao dịch.
- Lượt chuyển sang chỉ dùng XAMPP đã xóa dữ liệu cũ theo yêu cầu và tạo lại `btl_16` trống. Không nhập lại H2 hoặc kho media cũ; cần đăng ký tài khoản mới. Các lần khởi động sau chỉ tạo/cập nhật cấu trúc, **không tự xóa dữ liệu**.
- Phiên cũ còn mở, đã có người dẫn đầu từ trước khi có ví và không có khoản giữ chỗ: khi kết thúc sẽ ghi **Phiên cũ: cần kiểm tra thanh toán**, không tạo tiền cho người bán hoặc tự trừ âm ví người mua. Phiên đã đóng trước nâng cấp giữ nguyên lịch sử, không tự thanh toán bù. Nếu có lượt giá mới sau nâng cấp, lượt đó phải được giữ chỗ và thanh toán theo cơ chế ví. Bản demo chưa có nút đối soát các phiên cũ; nên tạo phiên mới để trình diễn nạp và thanh toán.
- Số dư, yêu cầu chờ duyệt, giữ chỗ và sổ giao dịch đều nằm trên máy Server và tồn tại sau restart. Sao lưu toàn bộ dữ liệu như hướng dẫn bên dưới.

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

- Bản source và EXE đều chỉ dùng **MySQL trong XAMPP trên máy Server**, mặc định `btl_16` ở `127.0.0.1:3306`. Nếu MySQL chưa bật hoặc cấu hình sai, Server báo lỗi, không tạo H2 thay thế.
- Tài khoản, ví, nạp tiền, sản phẩm, ảnh/video, lịch sử và kết quả đều nằm trong DB. `product_media` lưu metadata; `media_content` lưu dữ liệu nhị phân thành các đoạn tối đa 256 KiB. Metadata và ảnh/video được lưu trong cùng giao dịch, lỗi thì rollback toàn bộ.
- Thư mục `%TEMP%/btl16-mysql-media-*` chỉ chứa xử lý tải dở/cache phục vụ HTTP, được dọn khi dừng Server bình thường. Server mới dùng thư mục tạm mới và tải media từ MySQL; đây không phải nơi lưu trữ lâu dài. Client không có bản sao DB.
- Sao lưu **toàn bộ database MySQL**, bao gồm `media_content` và các bảng ví. Không cần sao lưu cache. Xem [Lưu trữ MySQL/XAMPP](25_MYSQL_XAMPP_ONLY.md).
- Bản đóng gói lấy cấu hình từ `config/server-desktop.properties`; cấu hình MySQL hợp lệ đã có được giữ khi đóng gói lại, cấu hình H2 cũ được thay thế. Thời gian kết thúc phiên vẫn là thời gian thực; tắt Server không đóng băng đồng hồ đấu giá.
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

Self-test desktop dùng database tạm riêng và ba client WebSocket thật để kiểm tra mã kết nối, tài khoản, phòng riêng tư, ảnh, cạnh tranh đặt giá, tự kết thúc và dữ liệu sau restart. UI self-test tạo ảnh trong `target/desktop-preview`, không thêm dữ liệu mẫu vào database sử dụng thật. Với bản MySQL-only, helper `smoke-windows-bundles.ps1` chuyển sang kiểm thử JAR/runtime đóng gói và các database MySQL `btl16_verify_*`. Không dùng `btl16.dataDir` để cách ly MySQL, không mở EXE production để tạo dữ liệu test trong `btl_16`; kiểm tra payload EXE bằng `tools/verify-media-bundles.ps1`.

Chi tiết chức năng ảnh/video, định dạng đã thử, giới hạn, chuyển ảnh cũ và kiểm thử cách ly: [Ảnh và video sản phẩm](23_ANH_VIDEO_SAN_PHAM.md). Lượt nâng cấp media không chạy smoke-test EXE, production hay setup/reset `btl_16`; các lệnh smoke-test trước đây không thuộc lượt xác minh này.

Đây là bộ ứng dụng học tập trong mạng LAN tin cậy, chưa phải dịch vụ đấu giá thương mại: WebSocket/HTTP LAN không mã hóa TLS, tiền và QR hoàn toàn mô phỏng, chưa tích hợp thanh toán thật hoặc xác thực danh tính. Không mở trực tiếp cổng ứng dụng ra Internet và không dùng mật khẩu quan trọng trên các hệ thống khác.
