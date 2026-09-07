# 16 – Checklist chức năng và bàn giao

Cập nhật **07/09/2026**. Phân biệt chức năng có trong source, automated test đã chạy và thao tác GUI/LAN chưa kiểm tra. Bằng chứng tại [VERIFICATION](../VERIFICATION.md).

## Code và automated self-test

- [x] Hai transport TCP/WS dùng chung ServerConnection/router/session/room/business core.
- [x] Account/register/login/profile/password, PBKDF2 và session detach/resume.
- [x] Product CRUD/ownership, ảnh PNG/JPEG giới hạn 700 KiB, metadata/BLOB/cache version.
- [x] Create/list/my/search, PUBLIC/PRIVATE và password/grant theo session.
- [x] Bid rule/min increment/no self-bid, khóa từng auction, outbid và cross-transport event.
- [x] Host extend/end/cancel/kick, block không bypass bằng private grant.
- [x] Timer/anti-sniping/result, retention/archive và dọn client/runtime/room.
- [x] JavaFX là client chính; callback chuyển Platform.runLater, reconnect/resync và UI manual-connect cho EXE.
- [x] ServerAddresses regression cho IPv4 LAN, bind/port/path/WS disabled.
- [x] npm test: 9 nhóm self-test pass, fixture tách biệt không ghi XAMPP.

Các tick UI ở phần này chỉ xác nhận code đã có và protocol được test, không khẳng định đã click toàn bộ giao diện bằng tay.

## XAMPP/JDBC và DB sạch

- [x] Tải lại MySQL Connector/J 8.4.0 bằng Maven; JDBC kết nối XAMPP MariaDB 10.4.32 tại 127.0.0.1:3306.
- [x] Config/source và config EXE hướng dẫn db.port theo từng máy; runner kiểm tra qua Java ServerConfig, không hardcode cổng DB.
- [x] Bỏ production DemoDataSeeder, SQL demo và tài khoản điền sẵn/hướng dẫn demo ở UI/banner.
- [x] btl_16 mới có 7 bảng, tất cả 0 dòng; không nhập dữ liệu cũ.
- [x] XamppDatabaseSelfTest chạy DB thử riêng: setup/startup không seed, setup giữ dữ liệu đã có, reset tạo lại trống; dọn DB thử.
- [x] Script reset yêu cầu RESET và cảnh báo database được chọn trong cấu hình; startup thông thường không reset.

## Đóng gói và tài liệu

- [x] npm run dist tạo cả Server EXE và Client EXE với app/runtime; config server cũ được giữ khi build lại.
- [x] README đúng 5 mục; setup chính VS Code + XAMPP, tài liệu chi tiết không dồn vào README.
- [x] docs/members của 5 người nêu thứ tự file, flow, biến, test, reviewer và tiêu chí bàn giao.
- [x] docs/09 phủ toàn bộ Java source và công cụ; docs/13 nêu ownership file dùng chung.
- [x] Liên kết tài liệu nội bộ đã kiểm tra; PROJECT_TREE và START_HERE theo source mới.

## Nhóm cần thực hiện trực tiếp

- [ ] Ít nhất hai máy vật lý cùng LAN: đúng IP/card mạng, firewall WS, copy địa chỉ từ dashboard.
- [ ] Thử đầy đủ GUI hai EXE: đăng ký → ảnh/product → public/private → bid/host → result/archive.
- [ ] Ngắt Wi-Fi máy client thật, nối lại và kiểm tra resume/resync; thử cả trường hợp session hết hạn.
- [ ] Thử trên máy thành viên có XAMPP dùng cổng khác và mật khẩu DB riêng.
- [ ] Thử Windows khác/kiến trúc runtime tương thích; copy toàn folder không cài Java.
- [ ] Nếu cần Internet: đánh giá riêng TLS/wss/VPN/firewall; bản LAN không phải triển khai Internet đã bảo mật.

Load-test tạo account/bid thật, chỉ chạy trên DB thử. Không đánh dấu pass transaction bid/ảnh đầy đủ trên XAMPP chỉ dựa vào in-memory self-test; JDBC test hiện tập trung schema/setup/reset.
