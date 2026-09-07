# 05 – Setup VS Code + XAMPP

## Môi trường

- VS Code với Extension Pack for Java (bao gồm hỗ trợ Maven).
- JDK 17 trở lên; lệnh `java`, `javac` và `mvn` có trên PATH. Đóng gói cần `jpackage` trong JDK, không chỉ JRE.
- Node.js 20+ để chạy các lệnh npm của dự án. Không có bước `npm install` bắt buộc: runner dùng module Node tích hợp.
- XAMPP trên máy server. Môi trường kiểm tra ngày 07/09/2026: dịch vụ MySQL của XAMPP thực tế là MariaDB 10.4.32, tại `C:/xampp/mysql/data/`, cổng 3306; JDBC Connector/J 8.4.0 đã kết nối thành công.

## 1. Bật DB và chọn đúng cổng

Trong XAMPP Control Panel, Start dòng MySQL; xem cột Port(s). Nếu cần đổi cổng, dùng Config → my.ini, kiểm tra port ở cả phần client và mysqld, khởi động lại MySQL. Không đổi cấu hình XAMPP của bạn khác một cách máy móc.

Trong `config/server.properties`:

```properties
db.driver=com.mysql.cj.jdbc.Driver
db.host=127.0.0.1
db.port=3306
db.name=btl_16
db.user=root
db.password=
db.autoInitialize=true
```

Đổi `db.port` theo cổng thực tế, ví dụ 3307. Điền đúng mật khẩu nếu DB không dùng mật khẩu rỗng. Root/mật khẩu rỗng chỉ là cấu hình local của bài tập, không dùng cho triển khai công khai.

Không cần Apache để chạy Java/JDBC. Chỉ bật Apache khi muốn xem DB bằng phpMyAdmin. Client ở máy khác không dùng cổng DB, mà dùng WebSocket 8890 của Java server.

## 2. Mở code, tải JDBC, tạo schema trống

Mở thư mục gốc bằng VS Code; đợi Java/Maven nhận `pom.xml`. Trong terminal PowerShell:

```powershell
java -version
javac -version
mvn -version
node --version
npm run build
java -cp 'target/classes;target/dependency/*' vn.ptit.btl16.server.db.DatabaseCheckMain
./scripts/setup-db.cmd
```

JDBC khai báo trong `pom.xml`: `com.mysql:mysql-connector-j:8.4.0`. Maven tải driver và copy vào `target/dependency/`; `JdbcConnectionFactory` dùng URL do `ServerConfig` ghép từ host/port/name. Không có driver riêng theo Laragon hay XAMPP; kết nối phụ thuộc dịch vụ DB đang nghe và cấu hình JDBC.

Nếu dependency JDBC hỏng, tải lại rồi clean build:

```powershell
mvn dependency:purge-local-repository '-DmanualInclude=com.mysql:mysql-connector-j' '-DreResolve=true'
mvn clean package dependency:copy-dependencies '-DincludeScope=runtime' '-DskipTests'
```

Nếu VS Code chưa nhận dependency: Command Palette → Java: Clean Java Language Server Workspace; mở lại project và chờ import Maven.

## 3. Chạy và debug

```powershell
npm run dev
npm run dev:server
npm run client
npm test
```

Chọn một trong hai lệnh đầu, không chạy hai server cùng cổng. `dev` build → kiểm tra JDBC thật bằng cấu hình Java → setup schema → khởi động server → chờ TCP/WS → in LAN → mở một client. `dev:server` bỏ bước mở client. Runner không tự bật XAMPP; nếu DB chưa chạy, thông báo yêu cầu bật MySQL và kiểm tra cấu hình.

`--skip-setup` chỉ bỏ bước setup của runner; server vẫn có thể tự tạo schema theo `db.autoInitialize=true`. Không lệnh khởi động nào tự reset/seed dữ liệu.

Run and Debug có Server Dashboard, Server Console, Auction Client, Self Tests, Check XAMPP JDBC và Setup Empty Database. Debug entry point JavaFX là `ClientMain` → `JavaFxClientApp` → `FxClientController`. `LegacySwingClientMain` chỉ dùng khi học client TCP/Swing cũ.

Các cổng TCP 8888/WS 8890 là mặc định mà runner chờ; nếu tùy biến listener/path hoặc tắt TCP, chạy trực tiếp Server Dashboard/Server Console trong VS Code để dùng cấu hình server. Cổng DB không có hạn chế này: runner đọc qua `ServerConfig`, không hardcode 3306.

## 4. Bắt đầu lại hoàn toàn

Dừng mọi server đang dùng DB, xác nhận `db.host`, `db.port`, `db.name` trỏ đúng DB dự án rồi chạy `./scripts/reset-db.cmd` và gõ `RESET`. Lệnh xóa database đã cấu hình, tạo lại 7 bảng trống; không phục hồi dữ liệu cũ, không seed.

Setup thông thường không phá dữ liệu. Có thể tự import `sql/00_schema.sql`; script SQL dùng cố định tên `btl_16`, còn Java setup/reset dùng `db.name`. `sql/99_reset_database.sql` dành cho mysql CLI khi thư mục hiện tại là `sql/` vì có lệnh SOURCE.

## 5. EXE và lỗi thường gặp

`npm run dist` tạo lại cả hai bộ. Config của EXE là `dist/BTL16-Auction-Server/app/config/server.properties`, không phải file trong source. Đóng gói giữ config EXE cũ nếu đã có, nên kiểm tra lại cổng/password sau build.

| Lỗi | Kiểm tra |
|---|---|
| Connection refused / Communications link failure | MySQL đã Start? Host/port có đúng XAMPP? |
| Access denied | db.user/db.password và quyền tạo DB/bảng |
| Driver not found | Maven build và runtime classpath chứa Connector/J |
| Address already in use | Tắt server cũ hoặc đổi listener; không đổi db.port để sửa lỗi WS |
| Client khác máy không vào | Cùng LAN, IP server, firewall WS; không nhập localhost |
| Danh sách phòng trống | Đúng với DB mới: tự đăng ký, thêm sản phẩm và tạo phòng |
