# 07 – Kiểm thử và diễn tập từ DB trống

## Self-test không cần XAMPP

Chạy npm test tại gốc. Script Maven test-compile + copy dependency rồi gọi AllSelfTests.main; mvn test riêng không chạy đầy đủ suite main-based này.

| Test | Kiểm tra |
|---|---|
| ProtocolCodecSelfTest | TCP framing/codec |
| JsonWireMessageCodecSelfTest | JSON WireMessage |
| PasswordHasherSelfTest | PBKDF2 password đúng/sai |
| SessionManagerSelfTest | Login/detach/resume/expiry |
| ServerAddressesSelfTest | IPv4 LAN, bind/port/path, WS tắt/không có LAN |
| DatabaseConfigSelfTest | Port tùy chỉnh, URL JDBC/config riêng và port sai |
| AuctionRulesSelfTest | JOIN/KICK race có điều phối, quyền đọc, luật host/bid, đóng băng/đã bán, trần gia hạn, numeric input và dữ liệu client đến muộn |
| ProductRestorationSelfTest | Quyền đăng nhập/chủ sở hữu, khôi phục lặp lại, hàng chưa bán/đã bán/còn phiên mở, đóng băng, giữ lịch sử và tranh chấp restore/create/deactivate |
| JdbcProductRestorationSelfTest | Tự tạo H2 cách ly trong thư mục tạm; transaction, lưu trạng thái, so sánh toàn bộ dữ liệu phiên cũ và tranh chấp qua nhiều kết nối |
| ProductRestorationNetworkSelfTest | RESTORE_PRODUCT qua TCP/WebSocket, xác thực, quyền sở hữu, trạng thái đủ điều kiện và tạo phiên mới sau khôi phục |
| FullNetworkAuctionSelfTest | TCP thật, bid đồng thời, outbid, resume/resync, timer/ended/archive |
| AuctionManagementSelfTest | Product/ownership, tạo phòng, host controls/kick |
| WebSocketUpgradeSelfTest | WS account/image/private/search; TCP↔WS event/bid/resume/grant/block |

Fixture src/test không ghi DB dự án. Không dùng test fixture để khẳng định transaction MariaDB đã pass.

### Kiểm thử JDBC riêng cho khôi phục

Sau khi build test, chạy H2 cách ly (cũng được gọi bởi AllSelfTests):

    java -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.JdbcProductRestorationSelfTest

Để kiểm tra cùng bộ luật và tranh chấp trên MySQL/MariaDB tại host/port cấu hình, dùng:

    java -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.JdbcProductRestorationSelfTest --mysql

Lệnh MySQL sao chép thông tin kết nối vào cấu hình tạm, bắt buộc thay tên DB thành `btl16_verify_restore_<mã ngẫu nhiên>` trước khi mở kết nối kiểm thử. Chỉ tạo và dọn database thử này; không chạy ứng dụng production, không gọi chương trình setup/reset và không ghi `btl_16`. Cần quyền CREATE/DROP cho database thử. Bộ test có cả yêu cầu bị trì hoãn sau bước kiểm tra nghiệp vụ: mặt hàng được bán trong lúc chờ vẫn phải bị từ chối ở transaction cuối cùng.

## JDBC/XAMPP cách ly

Start MySQL, build test rồi chạy từ PowerShell:

    java -cp 'target/classes;target/dependency/*' vn.ptit.btl16.server.db.DatabaseCheckMain
    java -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.XamppDatabaseSelfTest

Hoặc chạy `scripts\run-jdbc-tests.cmd` để build test và thực hiện bộ kiểm thử cách ly. Test JDBC tạo DB riêng tiền tố btl16_verify_, kiểm tra schema trống/setup giữ dữ liệu/reset trống, luật trên JDBC thật, tạo phòng/bid đồng thời và migration/persistence hard_end_time rồi tự dọn trong finally. Cần quyền CREATE/DROP database. Không reset btl_16 của người dùng.

## Diễn tập GUI

Kiểm tra tự động riêng hộp thoại khôi phục (cần môi trường desktop JavaFX):

    java --module-path 'target/dependency/javafx-base-21.0.4-win.jar;target/dependency/javafx-graphics-21.0.4-win.jar;target/dependency/javafx-controls-21.0.4-win.jar' --add-modules javafx.controls -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.ProductRestorationUiSelfTest

Test này chỉ dựng hộp thoại với API giả, không khởi chạy ứng dụng production, không kết nối mạng hoặc database. Kiểm tra nút và lý do cho hàng đang hoạt động/đủ điều kiện/đã bán/còn phiên mở, xác nhận, chống nhấn lặp khi đang chờ, làm mới sau thành công và khi server từ chối dữ liệu đã cũ. Ảnh kiểm tra nằm trong `target/restore-ui-preview`. Test GUI chạy riêng, không bắt buộc desktop cho AllSelfTests.

1. Bật MySQL XAMPP, Server EXE và ít nhất hai Client EXE.
2. Tự đăng ký một người bán và hai người mua, không dùng demo/alice/bob cài sẵn.
3. Người bán thêm ảnh PNG/JPEG, tạo PUBLIC và PRIVATE; thử password thiếu/sai/đúng, tìm tên/ID.
4. Hai người mua cùng bid; so sánh giá/winner/event và DB. Thử host tự bid, bid thấp, sau hết giờ.
5. Bid sát cuối để thấy anti-sniping; tổng gia hạn không vượt 600 giây. Host extend/end/cancel chỉ trước bid đầu; thử sau bid và phải bị từ chối. Người dẫn đầu tự nâng giá cũng phải bị từ chối.
6. Kick người chỉ xem và chưa từng bid, rồi thử rejoin/reconnect/history/resync; block không được bypass bằng grant private. Kick người đã bid, kể cả bị vượt giá, phải bị từ chối.
7. Mất mạng rồi nối lại trong grace: resume/resync, không dùng tiếp snapshot cũ.
8. Chờ kết quả và retention 120 giây: phòng biến mất nhưng DB giữ lịch sử.

Đối chiếu trường hợp chi tiết với [bộ luật hiện hành](LUAT_DAU_GIA_BTL16.md), không suy ra quyền host chỉ từ việc nút đang hiển thị.

## Load-test: dữ liệu thật, chỉ dùng DB thử

05_TEST_BID_DONG_THOI.cmd / scripts/run-load-test.cmd gọi ConcurrentBidLoadTestMain qua TCP. Tool tự tạo account load_* và bid thật; cần phòng PUBLIC đang mở, không tự tạo phòng. Không chạy trên DB muốn giữ sạch. Đây là tool chủ động kiểm tra tải, không phải auto-seed.

## Báo cáo

Ghi ngày, môi trường, lệnh và kết quả thật vào VERIFICATION.md. Build/self-test local không chứng minh LAN nhiều máy, firewall hay toàn bộ thao tác GUI; nhóm chỉ tick sau khi chạy trực tiếp.
