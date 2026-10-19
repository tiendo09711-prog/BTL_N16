# Lưu dữ liệu duy nhất trong MySQL/XAMPP

## Cách chạy

1. Trên máy Server, bật **MySQL** trong XAMPP. Mặc định dùng cổng **3306**, database **btl_16**, user **root**, mật khẩu trống. Không cần Apache, trừ khi muốn mở phpMyAdmin.
2. Mở `dist/BTL16-Auction-Server.exe`. Nếu MySQL chưa chạy, Server báo lỗi; không tự chuyển sang H2 hoặc file database khác.
3. Mở `dist/BTL16-Auction-Client.exe`, kết nối bằng mã/địa chỉ từ Server. Client không cần MySQL/XAMPP và không có thông tin đăng nhập DB.
4. Đăng ký tài khoản mới. Không có dữ liệu mẫu hoặc tiền tự cấp. Nạp QR demo vẫn cần Server duyệt.

## Dữ liệu nằm ở đâu?

- Tất cả tài khoản, hồ sơ, lịch sử đăng nhập, ví, yêu cầu nạp, giữ chỗ, giao dịch, sản phẩm, ảnh/video, phòng, giá và kết quả đều lưu trong `btl_16` trên **máy Server**.
- `product_media` chứa metadata, `media_content` chứa nhị phân nguồn/thumbnail/ảnh chi tiết/video. Mỗi đoạn tối đa 256 KiB để không đòi tăng `max_allowed_packet` toàn XAMPP. Lưu sản phẩm và media là một giao dịch.
- XAMPP trên máy hiện tại lưu DB tại `C:\xampp\mysql\data`; không tự chép/xóa file InnoDB khi MySQL đang chạy. Xem bảng qua phpMyAdmin hoặc MySQL client.
- `%TEMP%/btl16-mysql-media-*` chỉ là tệp tạm cho FFmpeg, tải dở và cache HTTP. Server mới dùng cache mới, media được đọc lại từ DB. Dừng Server bình thường sẽ dọn cache. Không sao lưu cache thay cho DB.
- Client có trạng thái giao diện/phiên trong RAM và có thể chọn tệp nguồn để gửi, nhưng không lưu database, số dư hoặc dữ liệu chung trên máy client.

## Chuyển đổi lần này

Dữ liệu cũ trong `%LOCALAPPDATA%/BTL16/AuctionServer` và database `btl_16` đã được xóa theo yêu cầu ngày 19/10/2026. `btl_16` được tạo lại trống, không nhập lại tài khoản/media H2. Các database khác của XAMPP không bị xóa.

Khởi động ứng dụng những lần sau **không xóa dữ liệu**. H2 chỉ còn phục vụ kiểm thử cách ly và không có trong gói EXE; bản chạy thật chỉ chấp nhận MySQL.

## Sao lưu và kiểm thử

Sao lưu toàn bộ `btl_16`, gồm cả `media_content` và các bảng ví. Có thể xuất SQL bằng phpMyAdmin hoặc `mysqldump` với `--single-transaction --hex-blob --skip-extended-insert`. Không chỉ xuất tài khoản/metadata rồi bỏ dữ liệu nhị phân.

`npm run test:mysql` tạo database ngẫu nhiên `btl16_verify_*`, kiểm tra MySQL thật rồi xóa database test. Không sử dụng database production cho test. `npm run test:full` vẫn dùng H2 cách ly cho các fixture nhanh; muốn kiểm tra bản đóng gói cùng XAMPP, chạy `node tools/run-tests.mjs --ui --mysql --packaged` sau `npm run dist`.
