# Admin: không gian quản trị theo tab

## Phạm vi đã rà soát

Ứng dụng Server JavaFX (`ServerFxMain` / `ServerFxApp`) dùng một cửa sổ chính. Menu bên trái và thanh tab phía trên cùng chọn một trang; bấm lại không tạo bản sao hay mở cửa sổ con.

Dashboard Swing cũ (`ServerDashboardMain` / `ServerDashboardFrame`) cũng đã được kiểm tra: chỉ có giám sát, sao chép/làm mới địa chỉ và dừng server, không có các cửa sổ quản trị con. Màn hình tương thích cũ này được giữ nguyên; bản phân phối Server dùng `ServerFxMain`.

| Menu / chức năng cũ | Vị trí hiện tại | Thành phần giữ lại |
| --- | --- | --- |
| Tổng quan sàn | Tab Tổng quan | Kết nối, người trực tuyến, phiên mở/kết thúc, danh sách phiên, nhịp kết nối, hoạt động gần đây |
| Tài khoản & ví | Tab Tài khoản & ví → Tài khoản & giao dịch | Danh sách tài khoản, hồ sơ, số dư, giữ chỗ, khả dụng |
| Lịch sử tài khoản | Các tab con trong chi tiết tài khoản | Lịch sử nạp, lịch sử rút, biến động ví / thanh toán |
| Duyệt nạp demo | Tab Tài khoản & ví → Duyệt nạp demo | Hàng chờ, chấp nhận, từ chối, lý do từ chối |
| Duyệt rút tiền | Tab Tài khoản & ví → Duyệt rút tiền | Hàng chờ, duyệt trừ tiền, từ chối, lý do từ chối |
| Quản lý dòng tiền | Tab Dòng tiền sàn | Bộ lọc kỳ, phí toàn bộ/trong kỳ, giá chốt, tiền người bán nhận, đối soát, xem thêm |
| Nhật ký vận hành | Tab Nhật ký vận hành | Lọc mã phiên, loại sự kiện, ngày bắt đầu/kết thúc, thử lại, trang tiếp |
| Sao chép địa chỉ | Thao tác nhanh trên sidebar và Tổng quan | Sao chép địa chỉ mạng; Tổng quan vẫn có chọn địa chỉ và sao chép mã kết nối |
| Tạm dừng / khởi động lại | Thao tác trên sidebar | Xác nhận trước khi dừng; tiếp tục dùng các tab đã mở sau khi khởi động lại |
| Đóng ứng dụng | Nút đóng cửa sổ chính | Xác nhận ngắt kết nối khi server đang chạy |

## Bố cục và vòng đời

- Thanh điều hướng, trạng thái server và thời gian hoạt động luôn nằm trong cùng cửa sổ.
- Tab quản trị không có nút đóng để tránh làm mất một mục chức năng.
- Trang chỉ được khởi tạo lần đầu khi cần và được giữ lại khi đổi tab. Bộ lọc, tab con, tài khoản đang chọn và lý do từ chối đang nhập không bị dựng lại.
- Ba bảng lịch sử tài khoản dùng tab con, thay cho việc xếp chồng nhiều bảng dài. Có thể kéo vạch phân chia giữa danh sách tài khoản và chi tiết.
- Chỉ trang đang xem mới chạy bộ hẹn giờ tải lại. Rời tab hoặc dừng server sẽ dừng polling và vô hiệu hóa kết quả tải cũ.
- Khi server tạm dừng, thông báo xuất hiện ngay trong trang; dữ liệu cũ được đánh dấu chưa cập nhật và thao tác quản trị bị khóa. Khởi động lại kích hoạt trang đang chọn với instance server mới.
- Xác nhận duyệt tiền, dừng và đóng server vẫn dùng hộp thoại có chủ sở hữu là cửa sổ đang thao tác. Đây là xác nhận hành động, không phải cửa sổ điều hướng.
- CSS riêng `src/main/resources/ui/server-admin.css` giữ tông màu hiện có và không thay đổi giao diện Client hay CSS dùng chung.
- Các phương thức `show()` của trang được giữ tương thích với việc kiểm thử/truy cập độc lập; menu của ứng dụng chính không gọi các phương thức này và không khởi tạo `Stage` phụ.

## Kiểm thử

`ServerAdminTabsUiSelfTest` được đăng ký trong `npm run test:full` và kiểm tra:

- Đủ bốn tab chính, ba tab tài khoản và ba tab lịch sử.
- Chuyển bằng sidebar hoặc thanh tab không tạo cửa sổ mới; trang chỉ được tạo một lần.
- Dữ liệu tài khoản, hàng chờ nạp/rút, bộ lọc dòng tiền và nhật ký.
- Giữ bản nháp/bộ lọc, dừng polling khi ẩn, xử lý kết quả tải đến muộn.
- Xác nhận thuộc cửa sổ chính, hủy không trừ tiền, duyệt nạp/rút cộng/trừ đúng một lần.
- Ảnh từng mục và bố cục thu gọn trong `target/admin-tabs/`.

`ServerButtonsUiSelfTest` kiểm tra thêm việc tạm dừng/khởi động lại, khóa/mở trang quản trị và vẫn giữ đúng một cửa sổ. Các kiểm thử ví và dòng tiền độc lập vẫn dùng lại các trang này.
