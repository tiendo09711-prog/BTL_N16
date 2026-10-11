# VERIFICATION – Kiểm tra ngày 07/09/2026

## Nâng cấp ảnh/video và hai EXE JavaFX - 11/10/2026

Phần này ghi nhận lượt nâng cấp media hiện tại. Các mục bên dưới được giữ nguyên như bằng chứng lịch sử; những lần setup/chạy production được mô tả trong các mục cũ không phải thao tác của lượt nâng cấp này. Không chạy ứng dụng production, không mở EXE để smoke-test, không setup/reset hoặc ghi database `btl_16`.

### Chức năng và dữ liệu

- Hoàn chỉnh luồng JavaFX người bán → HTTP streaming → xử lý native → tệp trên server + metadata JDBC → thư viện JavaFX người xem. Dùng phiên đăng nhập hiện có và lấy cổng media qua giao thức điều khiển; không gửi video dưới dạng thông điệp Base64.
- Server bắt buộc 1-5 ảnh, tối đa một video, mô tả 50-2000 ký tự Unicode sau trim khi lưu và khi mở phiên; video không thay ảnh. Không tạo nội dung mẫu cho dữ liệu cũ.
- Biên tập có chọn nhiều ảnh, thumbnail/preview, đại diện/thứ tự/thay/xóa, tiến độ và lỗi từng tệp, zoom/kéo/vừa khung, xoay, chọn và xác nhận cắt, đặt lại, hủy. Tải/xử lý không chạy trên FX thread. Thư viện có tải thumbnail trước, ảnh chi tiết khi chọn, trước/sau, toàn màn hình, thử lại, video phát/dừng/tua/âm lượng, không tự phát và mặc định tắt tiếng.
- Quyền sở hữu, đóng băng sau mọi phiên đã từng tạo, phòng riêng và lệnh cấm được kiểm tra ở server, kể cả GET/HEAD/Range ảnh gốc/thumbnail/chi tiết/video. Vé media gắn với phiên đăng nhập và ngữ cảnh; không dùng URL làm đường tắt vượt quyền. Lưu media/tạo phiên/ngừng/khôi phục cùng dùng khóa mặt hàng; giữ nguyên một phiên OPEN, luật người thắng, thời gian, trả giá và lịch sử.
- Tệp mới lưu mặc định tại `%LOCALAPPDATA%/BTL16/AuctionServer/media/`, tên UUID do server tạo, độc lập CWD/runtime/JAR. Database chỉ lưu metadata media mới; giữ nguyên các cột/BLOB cũ. Migration kiểm tra hash, file chuẩn hóa và metadata trước khi coi đã chuyển; không xóa nguồn cũ hoặc sửa mô tả/lịch sử. Tải dở được dọn, chỉ dọn thư mục UUID cũ hơn 24 giờ khi đã xác minh không được tham chiếu.
- Giữ riêng source, thumbnail JPEG tối đa 360 px và detail JPEG tối đa 4096 px; video chuyển MP4 H.264/AAC cho JavaFX. Có khóa kho, quota, giới hạn tệp/điểm ảnh/thời gian/dung lượng và worker. Đã sửa lỗi native log bị Windows giữ handle bằng đọc stdout/stderr song song qua pipe có giới hạn; áp dụng chặn điểm ảnh ngay trong bước probe/giải mã.

### Định dạng và giới hạn

- Đã kiểm chứng ảnh JPEG, PNG, BMP, GIF (khung đầu), TIFF (trang đầu), WebP bằng tệp thật; JPEG EXIF xoay và PNG trong suốt. Chưa hỗ trợ SVG/HEIC/HEIF/AVIF/RAW, không cam kết mọi biến thể ảnh động, HDR hoặc màu chuyên nghiệp.
- Đã kiểm chứng MP4/MOV với MPEG-4 + AAC, WebM VP9 + Opus, MKV/AVI MPEG-4 + AAC; đầu ra H.264/AAC thực sự phát/tua/đổi âm lượng bằng JavaFX. Không cam kết mọi codec của mọi container.
- Mặc định cấu hình: ảnh 32 MiB/24 triệu điểm ảnh; video 200 MiB/8,5 triệu điểm ảnh mỗi khung/180 giây; 180 giây mỗi bước tải/xử lý; 400 MiB mỗi sản phẩm/bản nháp tính cả nguồn và biến thể; toàn kho 5 GiB; hai worker. Chi tiết khóa cấu hình và cách đổi nằm trong `docs/23_ANH_VIDEO_SAN_PHAM.md`.
- Sao lưu database và toàn bộ thư mục media cùng thời điểm khi server đã dừng; bản H2 mặc định nằm cùng thư mục dữ liệu server. Không chỉ sao chép EXE hoặc database rồi bỏ kho media. Khi đổi máy giữ nguyên cây UUID và cấu hình lại gốc dữ liệu nếu cần.

### Kiểm thử đã chạy

Môi trường Windows x64, JDK 17, JavaFX 21.0.4, FFmpeg/ffprobe Windows x64 được ghim phiên bản và SHA-256; H2 cách ly và MySQL/MariaDB database thử UUID riêng.

    . .\scripts\activate-env.ps1
    mvn -q test-compile dependency:copy-dependencies -DincludeScope=runtime
    mvn -q -DskipTests package
    java -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.AllSelfTests
    java -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.JdbcProductRestorationSelfTest --mysql
    java --module-path 'target/dependency/javafx-base-21.0.4-win.jar;target/dependency/javafx-graphics-21.0.4-win.jar;target/dependency/javafx-controls-21.0.4-win.jar;target/dependency/javafx-media-21.0.4-win.jar' --add-modules javafx.controls,javafx.media -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.MediaUiSelfTest
    java --module-path 'target/dependency/javafx-base-21.0.4-win.jar;target/dependency/javafx-graphics-21.0.4-win.jar;target/dependency/javafx-controls-21.0.4-win.jar' --add-modules javafx.controls -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.ProductRestorationUiSelfTest
    java -cp 'dist/BTL16-Auction-Server/app/*;target/test-classes' vn.ptit.btl16.selftest.MediaUiSelfTest
    python tools/export-auction-rules.py
    python tools/export-auction-rules.py --check
    npm.cmd run test:packaging
    npm.cmd run dist
    powershell -NoProfile -ExecutionPolicy Bypass -File tools/verify-media-bundles.ps1
    git diff --check

- PASS toàn bộ 16 nhóm `AllSelfTests` sau bản sửa Java cuối; gồm hồi quy khôi phục, phiên đấu giá, TCP/WebSocket và desktop LAN loopback. Log: `target/media-all-final.log`.
- PASS `MediaSystemSelfTest`: thiếu/quá số lượng media và mô tả, định dạng thật/EXIF/ảnh lớn, ảnh giả/video lỗi, quota/thời gian tải/hủy/tải dở, quyền owner/phòng riêng/block/thu hồi session/vé/GET/HEAD/Range, metadata không BLOB cho dữ liệu mới, migration giữ BLOB, restart còn tệp, dọn mồ côi không xóa tệp tham chiếu, cạnh tranh sửa/tạo phiên. Database H2 và kho nằm trong thư mục tạm cách ly.
- PASS JavaFX media với module-path; PASS thêm ba lượt JVM liên tiếp với JAR/thư viện/native đóng gói sau bản sửa pipe cuối. Thao tác thật: multi-select/upload, cover/order, heartbeat FX khi xử lý ảnh lớn, zoom/fit, kéo vùng cắt/xoay/reset, video decode/play/seek/volume/mute, gallery/fullscreen/save/cancel. Log: `target/media-packaged-ui-1.log` đến `target/media-packaged-ui-3.log`; ảnh chụp: `target/media-ui-preview/`.
- PASS thêm một lượt `MediaUiSelfTest` trực tiếp với JAR/thư viện trong app-image sau build EXE cuối; không gọi entry point ứng dụng production. Log: `target/media-final-dist-ui.log`.
- PASS giao diện khôi phục với stub server, không dùng production. Log: `target/media-restore-ui-final.log`. PASS JDBC MySQL/MariaDB cách ly: schema/metadata media, quyền, đóng băng, lịch sử và cạnh tranh khôi phục/tạo phiên qua nhiều kết nối; database thử tự dọn. Log: `target/media-mysql-isolated.log`. Chưa phải kiểm thử đầy đủ media migration/streaming trên MySQL.
- Chế độ classpath của JavaFX in cảnh báo `Unsupported JavaFX configuration: classes were loaded from unnamed module`; các kiểm thử vẫn PASS, không xem cảnh báo này là bằng chứng đã chạy launcher EXE. Kiểm thử module-path không có cảnh báo đó.
- PASS bảy kiểm thử Node về layout/bootstrap đóng gói; PASS xuất và kiểm tra DOCX (ZIP/XML, UTF-8, fingerprint nguồn, checksum); PASS `git diff --check` sau dọn khoảng trắng ở các fixture vừa sửa.
- PASS build hai EXE mới và kiểm tra tĩnh payload: JAR nhúng khớp JAR nguồn build/app-image, có JavaFX Media/native tools/giấy phép/module HTTP, không kèm database hoặc media người dùng, không còn validator header-only cũ. SHA-256 EXE và Word/hướng dẫn phân phối khớp. Chỉ đọc metadata/resource EXE, không chạy launcher. Log: `target/media-dist-build.log`, `target/media-bundle-verification.log`.

### Bàn giao và giới hạn

- Bộ luật Markdown/Word được cập nhật bằng ngôn ngữ tự nhiên, giữ Điều 13 về khôi phục không mở lại phiên cũ/không xóa lịch sử/không đấu giá lại hàng đã có người thắng; bổ sung ảnh, video, mô tả, đóng băng và quyền xem. Word không có mã nguồn hoặc phụ lục kỹ thuật.
- Hai EXE gốc bàn giao: `dist/BTL16-Auction-Server.exe` và `dist/BTL16-Auction-Client.exe`; có runtime Java/JavaFX, server kèm FFmpeg/ffprobe và thông tin giấy phép. Checksum nằm trong `dist/SHA256SUMS.txt`; hướng dẫn và bộ luật Word nằm cùng thư mục. Mã nguồn công cụ build/verify và hướng dẫn media được cập nhật, không thay đổi các phần không liên quan có sẵn trong worktree.
- Kích thước bản build cuối: server 128.550.912 byte, client 54.356.992 byte. Đây là hai EXE tự bung có thể sao chép riêng; nếu dùng EXE bên trong thư mục app-image thì phải giữ nguyên cả thư mục app/runtime đi kèm.
- Chưa xác minh mở launcher EXE thực tế, Wi-Fi/firewall trên nhiều máy vật lý, ngắt điện, phục hồi backup thực tế, toàn bộ migration/streaming trên MySQL hoặc mọi codec/thiết bị. Chưa mở/render Word bằng Microsoft Word. HTTP/WebSocket chỉ dành LAN tin cậy, chưa có TLS; mở TCP 8890/8891 profile Private/LocalSubnet, không mở MySQL cho client hoặc công khai dịch vụ ra Internet.
- Native tools có LICENSE/provenance/build config; cần rà soát nghĩa vụ cung cấp mã nguồn bên thứ ba trước khi phân phối bundle rộng rãi ngoài nhóm học tập.

## Khôi phục mặt hàng đã ngừng - 11/10/2026

Môi trường: Windows x64, Temurin JDK 17.0.20.1+1, Maven, JavaFX 21.0.4, H2 2.3.232 và MySQL/MariaDB đang có sẵn theo cấu hình phát triển. Các kết quả sau là lượt chạy cuối của phần nâng cấp này. Giữ nguyên các thay đổi không liên quan đã có trong worktree.

### Phạm vi triển khai

- Bổ sung `RESTORE_PRODUCT` / `RESTORE_PRODUCT_RESULT` qua router, controller, service, JDBC repository và client API. Chỉ người đã đăng nhập, đang có phiên đăng nhập hoạt động và sở hữu mặt hàng được yêu cầu khôi phục.
- Dùng lại `products.active`, không thay schema, nhân bản mặt hàng hoặc đổi mã. Mặt hàng đang hoạt động là no-op sau khi kiểm tra chủ sở hữu, không cập nhật cả `updated_at`.
- Mặt hàng ngừng chỉ được khôi phục khi không có phiên OPEN và không có phiên ENDED với người thắng. Không phụ thuộc xác nhận thanh toán. Phiên CANCELLED hoặc ENDED không có người thắng vẫn cho phép khôi phục nếu đủ điều kiện.
- JDBC dùng chung `productTransaction` / khóa hàng mặt hàng với tạo phiên, ngừng và sửa mặt hàng; kiểm tra lại quyền và điều kiện sau khi lấy khóa. Không cập nhật phiên, lượt trả giá, kết quả, giới hạn thời gian hoặc lệnh cấm cũ; không mở/tạo phiên và không gỡ đóng băng nội dung/ảnh.
- JavaFX có nút khôi phục, cột điều kiện, giải thích lý do, xác nhận, khóa thao tác đang chờ và làm mới cả khi server từ chối dữ liệu đã cũ. Client Swing legacy cũng có thao tác khôi phục.
- Điều 13 được cập nhật bằng ngôn ngữ tự nhiên trong Markdown và xuất lại Word cùng manifest. Bản Word không có mã nguồn, cấu trúc chương trình hoặc phụ lục kỹ thuật.

### Các kiểm thử đã chạy

Các lệnh chính, từ PowerShell đã kích hoạt môi trường:

    . .\scripts\activate-env.ps1
    mvn -q test-compile dependency:copy-dependencies -DincludeScope=runtime
    java -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.AllSelfTests
    java -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.JdbcProductRestorationSelfTest --mysql
    java --module-path 'target/dependency/javafx-base-21.0.4-win.jar;target/dependency/javafx-graphics-21.0.4-win.jar;target/dependency/javafx-controls-21.0.4-win.jar' --add-modules javafx.controls -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.ProductRestorationUiSelfTest
    npm.cmd run test:packaging
    npm.cmd run dist
    python tools/export-auction-rules.py
    python tools/export-auction-rules.py --check
    git diff --check

- Build/test-compile Java 17: PASS. Toàn bộ 14 nhóm trong `AllSelfTests`: PASS, gồm cả các kiểm thử hồi quy TCP/WebSocket và khởi động lại với H2 cách ly đã có.
- `ProductRestorationSelfTest`: PASS chủ sở hữu khôi phục, từ chối người khác/chưa đăng nhập/phiên đăng nhập không hoạt động, lặp lại an toàn, từ chối hàng đã có người thắng hoặc ngừng nhưng còn phiên mở; hàng hủy/chưa bán mở được phiên mới; nội dung/ảnh vẫn đóng băng; giữ lịch sử, mật khẩu phòng, thời gian, trần gia hạn và lệnh cấm cũ.
- `JdbcProductRestorationSelfTest`: PASS trên cả H2 và MySQL/MariaDB cách ly. Đối chiếu mọi cột trong các bảng phiên, lượt trả giá, kết quả và lệnh cấm trước/sau khôi phục; đối chiếu toàn bộ hàng dữ liệu mặt hàng sau yêu cầu lặp lại/bị từ chối, gồm ảnh và timestamps. Trạng thái khôi phục vẫn có khi đọc qua repository/kết nối mới.
- Tranh chấp: PASS khôi phục đồng thời chỉ có một lần đổi trạng thái; 12 vòng restore/create/deactivate, mỗi vòng 6 yêu cầu và hai instance repository trên JDBC, không tạo hai phiên mở và không ngừng hàng có phiên mở. Kiểm thử có điều phối giữ lại một yêu cầu sau bước kiểm tra service, bán mặt hàng trong lúc chờ rồi xác nhận transaction cuối từ chối khôi phục.
- `ProductRestorationNetworkSelfTest`: PASS yêu cầu/response, đăng nhập, chủ sở hữu, logout, trạng thái đủ điều kiện, khôi phục và tạo phiên mới qua cả TCP lẫn WebSocket loopback.
- `ProductRestorationUiSelfTest`: PASS hộp thoại JavaFX với API giả, không mạng/database: nút và giải thích các trạng thái, xác nhận, chống nhấn lặp trong khi chờ, làm mới khi thành công và khi bị từ chối do đã có người thắng. Ảnh trước/sau trong `target/restore-ui-preview` đã được xem để kiểm tra bố cục.
- Packaging: PASS 7 kiểm thử cấu trúc bundle; build lại hai EXE độc lập trong `dist` và hai app-image. Kiểm tra tĩnh xác nhận payload ZIP nằm đúng trong EXE, JAR trong cả payload/app-image khớp JAR vừa build, có mã khôi phục mới, không đóng gói file dữ liệu database; `SHA256SUMS.txt` khớp hai EXE. Không chạy EXE hoặc smoke-test production.
- Word: PASS xuất lại, checksum/fingerprint nguồn, cấu trúc ZIP/XML, UTF-8 và kiểm tra không có nội dung kỹ thuật. `git diff --check`: PASS.

### An toàn dữ liệu và giới hạn

- Không chạy ứng dụng production hoặc chương trình setup/reset; không setup/reset/ghi database `btl_16`. Các server loopback trong self-test chỉ dùng repository giả hoặc H2 trong thư mục tạm riêng.
- JDBC mới chỉ tạo H2 trong thư mục tạm hoặc MySQL/MariaDB có tên ngẫu nhiên `btl16_verify_restore_...`, kiểm tra tiền tố trước khi tạo/kiểm thử/dọn. Database thử được dọn trong `finally`; cấu hình phát triển và dữ liệu ứng dụng của người dùng không thay đổi.
- Chưa chạy hai EXE production sau đóng gói theo yêu cầu; kiểm tra payload/checksum không thay thế kiểm thử khởi chạy EXE thật. Chưa xác minh nhiều máy Wi-Fi thật, Windows Firewall, hoặc thao tác GUI toàn hệ thống từ EXE.
- Đã kiểm tra cấu trúc/nội dung Word tự động, chưa mở Microsoft Word để xác minh phân trang hoặc bố cục in thực tế.

## Bổ sung kiểm thử luật ngày 11/10/2026

Môi trường lượt bổ sung: Windows x64, Temurin JDK 17.0.20.1+1 (kích hoạt bằng scripts/activate-env.ps1), Maven 3.9.16, Node.js v24.13.0, Python 3.13.0. MySQL/MariaDB có sẵn tại 127.0.0.1:3306. Không khởi chạy ứng dụng production/GUI và không setup/reset/ghi database btl_16 trong lượt sửa luật này.

Đã kiểm tra phần thay đổi có sẵn trước khi tiếp tục: interface repository bổ sung chưa được các implementation hoàn thiện nên compile lỗi; đã hoàn thiện JDBC/fixture. Đã sửa việc reset giới hạn gia hạn khi cập nhật/tải lại và kiểm tra lại toàn bộ luật liên quan.

Các lệnh đã chạy thành công sau thay đổi source cuối:

    npm.cmd run build
    npm.cmd test
    scripts\run-jdbc-tests.cmd
    npm.cmd run test:packaging
    python tools/export-auction-rules.py
    python tools/export-auction-rules.py --check
    git diff --check

- Build target Java 17: PASS. npm test: PASS cả 10 nhóm gọi bởi AllSelfTests; có kiểm thử TCP/WebSocket loopback, không phải chạy production.
- AuctionRulesSelfTest: PASS các luật host/bid/quyền, người đã bị vượt giá vẫn được bảo vệ và bid lại, đóng băng/đã bán, giá không thể bid, trần gia hạn bền vững, JOIN/KICK race có điều phối, client stale-state/tick/kick, định dạng tiền và hiển thị phần lẻ.
- WebSocketUpgradeSelfTest: PASS các endpoint HISTORY/RESYNC/ảnh private trước/sau grant, sau kick và HISTORY/RESYNC sau đóng phòng, cùng luồng TCP → WebSocket resume.
- JDBC thực tế: PASS luật trên JdbcAuctionRepository, tạo hai phòng cùng sản phẩm chỉ nhận một, hai bid đồng giá chỉ nhận một, rollback bid sai, đóng một lần, hard_end_time được giữ khi initialize lại và migration cột cũ. Database thử cuối: btl16_verify_b77ae3e2a5684fbfb6078e5e84eca5b4; test tự dọn trong finally.
- Packaging: PASS 5 test Node. DOCX: PASS cấu trúc ZIP/XML, UTF-8, fingerprint source và checksum; không khẳng định đã mở/render bằng Microsoft Word hoặc click GUI/LAN bằng tay.

Bộ luật hiện hành: [DOCX](docs/LUAT_DAU_GIA_BTL16.docx), [Markdown](docs/LUAT_DAU_GIA_BTL16.md). Các phần tiếp theo giữ nguyên bằng chứng lịch sử ngày 07/09/2026, không phải bằng chứng đã chạy lại production trong lượt này.

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
