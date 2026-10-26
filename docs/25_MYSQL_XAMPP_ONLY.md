# Lưu dữ liệu duy nhất trong MySQL/XAMPP

## Cách chạy

1. Trên máy Server, bật **MySQL** trong XAMPP. Mặc định dùng cổng **3306**, database **btl_16**, user **root**, mật khẩu trống. Không cần Apache, trừ khi muốn mở phpMyAdmin. Nếu MySQL đã chạy dưới dạng dịch vụ thì không cần giữ cửa sổ XAMPP mở.
2. Mở `dist/BTL16-Auction-Server.exe`. Nếu MySQL chưa chạy, Server báo lỗi; không tự chuyển sang H2 hoặc file database khác.
3. Mở `dist/BTL16-Auction-Client.exe`, kết nối bằng mã/địa chỉ từ Server. Client không cần MySQL/XAMPP và không có thông tin đăng nhập DB.
4. Đăng ký tài khoản mới. Không có dữ liệu mẫu hoặc tiền tự cấp. Nạp QR demo vẫn cần Server duyệt.

## Dữ liệu nằm ở đâu?

- Tất cả dữ liệu nghiệp vụ đã lưu thành công: tài khoản, hồ sơ, lịch sử đăng nhập, ví, yêu cầu nạp, giữ chỗ, giao dịch, sản phẩm, ảnh/video, phòng, giá và kết quả đều lưu trong `btl_16` trên **máy Server**.
- `product_media` chứa metadata; `media_content` chứa nhị phân nguồn/thumbnail/ảnh chi tiết/video. Mỗi đoạn tối đa 256 KiB. Lưu sản phẩm và media là một giao dịch; lỗi sẽ rollback. Media giữ lại khi sửa sản phẩm không bị xóa rồi ghi lại.
- XAMPP trên máy hiện tại lưu DB tại `C:\xampp\mysql\data`; không tự chép/xóa file InnoDB khi MySQL đang chạy. Xem bảng qua phpMyAdmin hoặc MySQL client.
- **Không tạo kho media, staging hoặc cache dữ liệu trên đĩa**, kể cả `%TEMP%/btl16-mysql-media-*` hay `%LOCALAPPDATA%/BTL16/AuctionServer`. `media.root` và `btl16.media.root` không còn được dùng.
- Upload/bản nháp và việc chuẩn hóa ảnh/video chỉ dùng **RAM**. FFmpeg/ffprobe đọc dữ liệu qua HTTP loopback có URL ngẫu nhiên và hỗ trợ seek; đầu ra đi qua pipe vào RAM. Chỉ khi bấm **Lưu sản phẩm** thành công mới ghi BLOB vào MySQL. Hủy, hết hạn hoặc dừng Server giải phóng bản nháp; bản nháp chưa lưu không tồn tại qua restart.
- Khi xem ảnh/video, Server stream các đoạn trực tiếp từ MySQL, hỗ trợ HEAD và Range, không khôi phục BLOB ra file cache. Bộ đệm đọc giới hạn theo đoạn, không nạp cả video cho mỗi người xem.
- Client giữ trạng thái giao diện/phiên trong RAM, không lưu database nghiệp vụ. Tệp nguồn do người dùng chọn upload vẫn nằm ở vị trí gốc trên máy họ.

## File chương trình không phải dữ liệu nghiệp vụ

EXE một file cần giải nén chương trình, Java runtime, công cụ và cấu hình vào `%LOCALAPPDATA%/BTL16/DesktopApps/<tên ứng dụng>-<phiên bản>`. Đây không phải cache ảnh/video hay bản sao database. Nếu không muốn dùng thư mục giải nén này, chạy EXE trong thư mục app-image `dist/BTL16-Auction-Server` hoặc `dist/BTL16-Auction-Client` và giữ nguyên thư mục chương trình đi kèm. Không thể yêu cầu ứng dụng chạy mà không có file chương trình/cấu hình hoặc RAM.

## Dung lượng và sao lưu

- Ngân sách MySQL vẫn theo `media.storageMaxBytes`; mỗi upload dự trữ bảo thủ `media.productMaxBytes` cho nguồn và các biến thể. Đồng thời giới hạn tổng payload RAM và dự trữ không vượt một phần ba Java heap tối đa để chừa chỗ cho các bản sao xử lý. Thiếu RAM/quota trả lỗi rõ ràng, không tự tràn ra file tạm.
- Chạy lại Server **không xóa database** và không tự xuất bản sao SQL ra nơi khác. H2 chỉ phục vụ kiểm thử cách ly, không có trong gói EXE production.
- Nếu cần sao lưu thủ công, xuất **toàn bộ `btl_16`**, gồm `media_content` và các bảng ví, bằng phpMyAdmin hoặc `mysqldump`. File sao lưu là một bản dữ liệu bổ sung do người quản trị chủ động tạo; ứng dụng không cần nó để chạy.

## Kiểm thử

`npm run test:mysql` tạo database ngẫu nhiên `btl16_verify_*`, kiểm tra MySQL thật rồi xóa database test. Không sử dụng database production cho test. Kiểm thử xác nhận upload ảnh/video, sửa sản phẩm, restart, HEAD/Range và không tạo thư mục media tạm. Các fixture/cấu hình test vẫn là file phát triển cách ly, không phải nơi ứng dụng production lưu dữ liệu người dùng. Chạy `npm run test:full` cho toàn bộ fixture nhanh; kiểm tra bản đóng gói sau `npm run dist` bằng `node tools/run-tests.mjs --ui --mysql --packaged`.
