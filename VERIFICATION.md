# VERIFICATION – Kiểm tra ngày 07/09/2026

## Môi trường thực tế

- Windows x64, OpenJDK/Temurin 26.0.1; compile target Java 17.
- Maven 3.9.16, Node.js v24.13.0.
- XAMPP MySQL: MariaDB 10.4.32, 127.0.0.1:3306, datadir C:/xampp/mysql/data/.
- MySQL Connector/J 8.4.0; JavaFX/WS/Jackson theo pom.xml.
- JDK hiện có trên PATH dùng được cho VS Code; không cần chạy IDE cũ. Không thay đổi cài đặt toàn hệ thống ngoài tải lại dependency JDBC của Maven.

## JDBC đã tải lại và kết nối

Các lệnh đã chạy thành công:

    mvn -q dependency:purge-local-repository '-DmanualInclude=com.mysql:mysql-connector-j' '-DreResolve=true'
    mvn -q clean test-compile dependency:copy-dependencies '-DincludeScope=runtime'
    java -cp 'target/classes;target/dependency/*' vn.ptit.btl16.server.db.DatabaseCheckMain
    java -cp 'target/classes;target/dependency/*' vn.ptit.btl16.server.db.DatabaseSetupMain

Kết quả:

    JDBC connected to 127.0.0.1:3306
    Database server: 5.5.5-10.4.32-MariaDB
    Driver: MySQL Connector/J mysql-connector-j-8.4.0
    DATABASE SETUP COMPLETE
    Schema ready. No demo accounts, products or auctions are created.

Trước khi setup, XAMPP chưa có btl_16. Đã tạo schema mới tại đúng datadir XAMPP, không nhập dữ liệu cũ, không chạm database khác. Không cần chuyển sang driver khác khi Connector/J hiện tại đã kết nối được DB này.

## Self-test

npm test đã chạy lại sau thay đổi code cuối và pass cả 9 nhóm:

    [PASS] ProtocolCodecSelfTest
    [PASS] JsonWireMessageCodecSelfTest
    [PASS] PasswordHasherSelfTest
    [PASS] SessionManagerSelfTest
    [PASS] ServerAddressesSelfTest
    [PASS] DatabaseConfigSelfTest
    [PASS] FullNetworkAuctionSelfTest
    [PASS] AuctionManagementSelfTest
    [PASS] WebSocketUpgradeSelfTest
    ALL SELF-TESTS PASSED

DatabaseConfigSelfTest kiểm tra host/port 3307/custom name trên config tạm, URL DB/server dùng cùng endpoint, port 0/65536/không phải số bị từ chối. Đây là kiểm tra cấu hình; chưa thay XAMPP thật sang cổng khác trên máy thành viên.

Các network tests dùng repository fixture in-memory cách ly, không seed vào DB XAMPP. mvn test riêng không thay thế npm test vì suite chạy bằng main.

## JDBC test cách ly

Đã chạy:

    java -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.XamppDatabaseSelfTest

Kết quả PASS: tạo DB btl16_verify_<UUID>, xác minh 7 bảng trống sau setup và ServerApplication.create; chèn một dòng thử, chạy setup lại vẫn giữ đúng một dòng, reset rồi tất cả bảng trống. Finally dọn DB thử và config tạm; query xác nhận không còn DB tiền tố btl16_verify_.

Test này xác minh schema/setup/reset bằng JDBC thật; không khẳng định đã chạy mọi transaction bid/ảnh trên MariaDB. Các luồng nghiệp vụ đầy đủ được kiểm tra bằng fixture/network suite, GUI/DB thật vẫn cần nhóm diễn tập.

## Development runner

Smoke test tools/dev-runner.mjs --server-only, cùng entry point npm run dev:server, đã chạy build → JDBC check → setup không seed → server ready:

    TCP listening 0.0.0.0:8888
    WebSocket listening 0.0.0.0:8890/ws
    [DEV] Server is ready.
    DEV_RUNNER_SMOKE_PASS

Runner in URL local/LAN và hướng dẫn client; không tự mở Laragon/XAMPP. Process của bài smoke test được dừng riêng sau kiểm tra, không tắt MySQL. Không dùng lần này để khẳng định đã kiểm tra Ctrl+C thủ công hoặc full npm run dev mở GUI.

## Hai EXE

npm run dist đã build lại thành công sau chỉnh sửa banner/UI cuối:

    dist/BTL16-Auction-Server/BTL16-Auction-Server.exe
    dist/BTL16-Auction-Client/BTL16-Auction-Client.exe

Cả hai có app/runtime. Bộ server có mysql-connector-j-8.4.0.jar; app/config/server.properties đã bỏ tùy chọn demo, dùng DB XAMPP 3306. Cơ chế đóng gói vẫn giữ cấu hình EXE cũ khi build lại.

Smoke test mở EXE bằng Start-Process -WindowStyle Hidden từ thư mục TEMP, không phải thư mục source:

    SERVER_EXE_SMOKE_PASS: alive; TCP 8888 and WS 8890 ready from a different working directory.
    PACKAGED_WS_WELCOME_PASS
    CLIENT_EXE_SMOKE_PASS: alive with bundled runtime.

WS probe thực sự nhận CONNECTION_WELCOME từ packaged server. Client EXE sống sau 5 giây; chưa tự động click nhập URL/login/bid. Đã dừng riêng các process thử, không để server giữ cổng sau kiểm tra.

## DB bàn giao vẫn sạch

Query lại sau test runner và cả hai EXE:

| Bảng | Số dòng |
|---|---:|
| users | 0 |
| login_history | 0 |
| products | 0 |
| auctions | 0 |
| bids | 0 |
| auction_results | 0 |
| auction_blocked_users | 0 |

Không có tài khoản demo hoặc dữ liệu mới do bài kiểm tra để lại trong btl_16. Người dùng bắt đầu bằng đăng ký, thêm sản phẩm, tạo phòng.

## Tài liệu và giới hạn

- README giữ 5 mục, hướng dẫn setup VS Code/XAMPP và cổng mỗi máy.
- docs/members của 5 người đối chiếu source; docs/09 phủ toàn bộ file Java, docs/13 chia reviewer cả file dùng chung/công cụ/docs.
- Kiểm tra link nội bộ và ký tự điều khiển: 26 file Markdown README/docs, không lỗi.
- git diff --check không báo lỗi whitespace; cảnh báo LF/CRLF là cấu hình Git Windows, không phải test failure.
- Chưa thử LAN nhiều máy vật lý/firewall thật, toàn bộ thao tác GUI thủ công, máy thành viên cổng DB khác hoặc Internet/TLS. Các mục này vẫn để chưa tick trong docs/16.
