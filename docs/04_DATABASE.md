# 04 – Database và JDBC trên XAMPP

## Kết nối

Chỉ server dùng JDBC. ServerConfig đọc db.host/db.port/db.name/db.user/db.password từ config/server.properties; JdbcConnectionFactory load com.mysql.cj.jdbc.Driver và mở DriverManager Connection. EXE dùng app/config/server.properties riêng.

Maven cung cấp MySQL Connector/J 8.4.0. Đã kết nối local XAMPP MariaDB 10.4.32 tại 127.0.0.1:3306. **Mỗi máy chỉnh db.port theo XAMPP**, không mặc định tất cả là 3306. URL jdbc:mysql được ghép trong ServerConfig; không cần driver riêng mang tên XAMPP.

## Bảy bảng

| Bảng | Trách nhiệm |
|---|---|
| users | Username duy nhất, profile, password hash/salt/iterations, active, last_login |
| login_history | Audit login thành công, liên kết user |
| products | created_by, mã/tên/mô tả, active, image_data/mime/name/size/version |
| auctions | Host/product, start price/min increment/current price/winner, start_time/end_time/hard_end_time/status/version, visibility và room password hash/salt/iterations |
| bids | Auction, user, amount, server_sequence và thời điểm |
| auction_results | Một kết quả theo auction_id; winner NULL nếu không có bid |
| auction_blocked_users | Khóa auction_id/user_id, người chặn và thời điểm; bền vững sau reconnect/restart |

InnoDB + utf8mb4; schema đầy đủ trong sql/00_schema.sql và server/db/DatabaseSchema.java. Ảnh lưu BLOB, không dùng đường dẫn máy người bán. Password account/phòng dùng PBKDF2; session/connection/membership ở RAM.

## Setup/reset không seed

- DatabaseCheckMain kết nối cấp server để kiểm tra host/port/user/driver; không cần DB có sẵn, không ghi dữ liệu.
- DatabaseSetupMain tạo DB/bảng nếu thiếu và upgrade cột; giữ dữ liệu có sẵn, không seed.
- ServerApplication.create tự initialize nếu db.autoInitialize=true; không seed.
- DatabaseResetMain DROP database đã cấu hình rồi tạo schema trống. scripts/reset-db.cmd yêu cầu gõ RESET; dừng server và kiểm tra đúng host/port/name trước khi chạy.
- sql/99_reset_database.sql xóa riêng btl_16 rồi SOURCE 00_schema.sql từ thư mục sql; không import mẫu.
- Đã bỏ DemoDataSeeder và SQL seed. Fixture src/test chỉ dùng regression, không nạp vào XAMPP.

## Transaction cần giải thích

**Login:** last_login và login_history được ghi trong cùng transaction.

**Bid:** lấy khóa auction → kiểm tra membership đúng connection/user, block, không phải host/người dẫn đầu, OPEN/đúng thời gian, amount ≥ currentPrice + minBidIncrement → SELECT ... FOR UPDATE → đối chiếu giá/trạng thái/quyền/giới hạn → INSERT bid + UPDATE price/winner/endTime/version → COMMIT → cập nhật RAM → broadcast. Lỗi SQL hoặc runtime ROLLBACK; conflict yêu cầu snapshot mới. Bid đầu cũng cần startPrice + minBidIncrement.

**End:** cùng khóa runtime và khóa row, kiểm tra OPEN và đối chiếu winner/price/endTime, UPDATE ENDED + ghi auction_results → commit → event. Host chỉ được chốt sớm khi chưa có bid; timer chốt đúng hạn. Đóng một lần; winner NULL không phải bán thành công.

**Extend/cancel/kick:** extend thủ công và cancel chỉ khi chưa có bid. Extend đối chiếu endTime kỳ vọng và hard_end_time; anti-sniping dùng chung ngân sách gia hạn tổng 600 giây. Kick chỉ người chưa từng bid, ghi block rồi bỏ membership/revoke grant. JOIN recheck quyền và thêm membership dưới cùng khóa với BID/KICK.

**Product:** tạo phiên/sửa/vô hiệu hóa khóa dòng sản phẩm trong transaction. Cấm hai phiên OPEN cho cùng productId; đã có bất kỳ phiên nào thì đóng băng nội dung/ảnh; đã có người thắng thì không được mở phiên mới. Migration hard_end_time chỉ điền giá trị NULL một lần bằng end_time hiện có + 600 giây, không reset hạn đã lưu.

Chi tiết nghiệp vụ và giới hạn vận hành một server/database: [bộ luật hiện hành](LUAT_DAU_GIA_BTL16.md).

**Archive:** sau retention mặc định 120 giây, ẩn phòng khỏi runtime/list/dashboard và dọn RoomManager; không DELETE lịch sử auctions/bids/results.

## Kiểm tra DB mới

Chạy SHOW TABLES trong btl_16; SELECT COUNT(*) lần lượt trên cả 7 bảng phải bằng 0 ngay sau setup mới/reset. Sau khi tự đăng ký/tạo phòng thì có dữ liệu là đúng; setup lần nữa không được xóa chúng. Xem lệnh và JDBC test cách ly ở [07](07_DEMO_KIEM_THU.md).
