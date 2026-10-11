# 1. Tên dự án

**BTL 16 – Sàn đấu giá trực tuyến thời gian thực**

## 2. Dự án làm gì?

Cho phép đăng ký tài khoản, quản lý sản phẩm có ảnh, tạo phòng đấu giá công khai/riêng tư, đặt giá và theo dõi kết quả realtime. Chủ phòng chỉ được gia hạn thủ công/kết thúc sớm/hủy khi chưa có bid, và chỉ được kick người chưa từng bid trong phiên. Tổng gia hạn thủ công + tự động không quá 600 giây so với hạn ban đầu.

**Luật hiện hành:** [Bản Word đầy đủ](docs/LUAT_DAU_GIA_BTL16.docx), [bản Markdown để bảo trì](docs/LUAT_DAU_GIA_BTL16.md). Bộ quy tắc trình bày bằng ngôn ngữ tự nhiên để trao đổi với giảng viên: điều kiện tham gia, cách trả giá, thời hạn/gia hạn, quyền người bán, xác định người thắng, đấu giá lại và các ví dụ. Không đưa cấu trúc chương trình hoặc phụ lục kỹ thuật vào bản Word. Xuất lại bằng `python tools/export-auction-rules.py`; kiểm tra khớp source bằng `python tools/export-auction-rules.py --check`.

## 3. Server và client hoạt động thế nào? (EXE)

    Client EXE (JavaFX) → WebSocket → Server EXE (JavaFX) → JDBC → H2 / MySQL

- **Server** xử lý tài khoản, quyền vào phòng, giá bid, thời gian và người thắng; lưu dữ liệu trong DB.
- **Client** chỉ gửi yêu cầu và nhận cập nhật; không kết nối DB trực tiếp.
- Bản Windows mới: mở `dist/BTL16-Auction-Server.exe` trên máy chủ và `dist/BTL16-Auction-Client.exe` trên máy khách. Hai EXE ở **gốc dist** tự chứa ứng dụng và runtime, có thể chép riêng từng file.
- Không cần cài Java/Maven/Node/XAMPP để dùng bản Windows mặc định. Server dùng database H2 lưu trên máy; cấu hình MySQL cũ vẫn giữ riêng để học JDBC/XAMPP.
- Chia sẻ mã kết nối từ Server cho các máy cùng LAN/Wi-Fi. Chi tiết: [Hai ứng dụng JavaFX](docs/22_HAI_UNG_DUNG_JAVAFX.md). Các bản app-image trong thư mục con vẫn cần giữ nguyên cả thư mục.

### Bản macOS (client và server)

- Tạo portable cho **Mac Apple Silicon và Intel** ngay trên Windows: `npm run dist:mac:portable`. Bốn gói `.tar.gz` nằm trong `dist/`; máy nhận cần **JDK 17+ đúng kiến trúc**, mở file `.command` sau khi giải nén.
- Tạo `.app` **kèm runtime Java**: chạy `npm run dist` **trên Mac**, kết quả ở `dist/macos-arm64/` hoặc `dist/macos-x64/`. Server dùng cấu hình database của bản đóng gói, client không cần DB.
- Hướng dẫn chi tiết: [docs/20_MACOS_PACKAGING.md](docs/20_MACOS_PACKAGING.md). Bản Windows hiện tại được giữ riêng, không chuyển EXE thành ứng dụng Mac.

## 4. Setup để đọc và sửa code: VS Code + XAMPP

1. Cài **VS Code + Extension Pack for Java**, **JDK 17+**, **Maven 3.x**, **Node.js 20+** và **XAMPP**. Mở thư mục chứa `pom.xml` bằng VS Code.
2. Trong XAMPP Control Panel, bấm **Start** ở dòng **MySQL**. Không cần bật Apache, trừ khi muốn dùng phpMyAdmin.
3. Sửa `config/server.properties` cho đúng máy mình:

```properties
db.driver=com.mysql.cj.jdbc.Driver
db.host=127.0.0.1
db.port=3306
db.name=btl_16
db.user=root
db.password=
```

**Mỗi bạn có thể dùng cổng MySQL khác nhau:** đọc cột **Port(s)** trong XAMPP rồi sửa `db.port` (ví dụ 3306 hoặc 3307). Đây là cổng DB, **không phải** cổng client kết nối server (8890). Nếu tài khoản DB có mật khẩu, điền `db.password`.

4. Tại terminal gốc dự án, chạy:

```powershell
npm run build
./scripts/setup-db.cmd
npm run dev
```

Maven tự tải JDBC **MySQL Connector/J 8.4.0** từ `pom.xml`; không cần chép JAR thủ công. Setup chỉ tạo/cập nhật cấu trúc DB, **không tạo tài khoản, sản phẩm hay phiên mẫu** và không xóa dữ liệu đã nhập. Muốn xóa toàn bộ DB dự án và bắt đầu lại: dừng server, chạy `./scripts/reset-db.cmd` rồi gõ `RESET`.

Chạy riêng server: `npm run dev:server`; mở thêm client: `npm run client`; kiểm thử: `npm test`. Tạo lại hai EXE sau khi sửa code: `npm run dist` (JDK cần có `jpackage`).

Đọc code theo [docs/00_BAT_DAU.md](docs/00_BAT_DAU.md), setup chi tiết tại [docs/05_CACH_CHAY_VSCODE_XAMPP.md](docs/05_CACH_CHAY_VSCODE_XAMPP.md); nhiệm vụ từng người nằm trong [docs/members](docs/members).

## 5. Hướng dẫn sử dụng server, client

**Máy server**
1. Bật MySQL trong XAMPP. Kiểm tra `dist/BTL16-Auction-Server/app/config/server.properties`, nhất là `db.port` và mật khẩu; cấu hình này độc lập với file trong source và được giữ khi đóng gói lại.
2. Mở `BTL16-Auction-Server.exe`, chọn IP LAN cùng mạng với client, bấm **Sao chep dia chi** và gửi địa chỉ cho các bạn.
3. Cho phép inbound TCP **8890** (hoặc cổng WebSocket đã cấu hình) trên mạng Private của Windows Firewall. Không mở cổng MySQL cho client. Giữ cửa sổ server mở khi sử dụng.

**Máy client**
1. Mở `BTL16-Auction-Client.exe`, dán địa chỉ `ws://IP_SERVER:8890/ws`, bấm **Kết nối**. Nếu chạy cùng máy server, dùng `ws://127.0.0.1:8890/ws`.
2. **Đăng ký tài khoản mới**, rồi đăng nhập. Hệ thống ban đầu trống, không có tài khoản demo.
3. Người bán thêm sản phẩm với mô tả 50-2000 ký tự, 1-5 ảnh và tối đa một video, rồi tạo phòng, đặt giá khởi điểm/bước giá/thời gian và mật khẩu nếu là phòng riêng tư. Xem [Ảnh và video sản phẩm](docs/23_ANH_VIDEO_SAN_PHAM.md) để biết định dạng, giới hạn, biên tập và sao lưu.
4. Người mua dùng tài khoản khác, tìm phòng → vào phòng → đặt giá; theo dõi giá hiện tại, thời gian và người thắng. Chủ phòng không được tự bid; người đang dẫn đầu không được tự nâng giá. Bid đầu tối thiểu bằng giá khởi điểm + bước giá. Sản phẩm đã có phiên bị khóa nội dung/ảnh; sản phẩm đã có người thắng không được đấu giá lại.

Chi tiết phân phối EXE và kết nối LAN: [docs/19_MULTI_MACHINE_AND_PACKAGING.md](docs/19_MULTI_MACHINE_AND_PACKAGING.md).
