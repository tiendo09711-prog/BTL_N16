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
| FullNetworkAuctionSelfTest | TCP thật, bid đồng thời, outbid, resume/resync, timer/ended/archive |
| AuctionManagementSelfTest | Product/ownership, tạo phòng, host controls/kick |
| WebSocketUpgradeSelfTest | WS account/image/private/search; TCP↔WS event/bid/resume/grant/block |

Fixture src/test không ghi DB dự án. Không dùng test fixture để khẳng định transaction MariaDB đã pass.

## JDBC/XAMPP cách ly

Start MySQL, build test rồi chạy từ PowerShell:

    java -cp 'target/classes;target/dependency/*' vn.ptit.btl16.server.db.DatabaseCheckMain
    java -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.XamppDatabaseSelfTest

Test JDBC tạo DB riêng tiền tố btl16_verify_, kiểm tra schema trống/setup giữ dữ liệu/reset trống rồi tự dọn trong finally. Cần quyền CREATE/DROP database. Không reset btl_16 của người dùng.

## Diễn tập GUI

1. Bật MySQL XAMPP, Server EXE và ít nhất hai Client EXE.
2. Tự đăng ký một người bán và hai người mua, không dùng demo/alice/bob cài sẵn.
3. Người bán thêm ảnh PNG/JPEG, tạo PUBLIC và PRIVATE; thử password thiếu/sai/đúng, tìm tên/ID.
4. Hai người mua cùng bid; so sánh giá/winner/event và DB. Thử host tự bid, bid thấp, sau hết giờ.
5. Bid sát cuối để thấy anti-sniping. Thử host extend/end/cancel (cancel chỉ trước bid).
6. Kick người mua rồi thử rejoin/reconnect; block không được bypass bằng grant private.
7. Mất mạng rồi nối lại trong grace: resume/resync, không dùng tiếp snapshot cũ.
8. Chờ kết quả và retention 120 giây: phòng biến mất nhưng DB giữ lịch sử.

## Load-test: dữ liệu thật, chỉ dùng DB thử

05_TEST_BID_DONG_THOI.cmd / scripts/run-load-test.cmd gọi ConcurrentBidLoadTestMain qua TCP. Tool tự tạo account load_* và bid thật; cần phòng PUBLIC đang mở, không tự tạo phòng. Không chạy trên DB muốn giữ sạch. Đây là tool chủ động kiểm tra tải, không phải auto-seed.

## Báo cáo

Ghi ngày, môi trường, lệnh và kết quả thật vào VERIFICATION.md. Build/self-test local không chứng minh LAN nhiều máy, firewall hay toàn bộ thao tác GUI; nhóm chỉ tick sau khi chạy trực tiếp.
