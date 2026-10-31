# Server: Phiên đấu giá

Menu **Phiên đấu giá** thay thế **Nhật ký vận hành**. Nhật ký kỹ thuật vẫn được lưu; không còn hiển thị thành một mục điều hướng riêng.

- Mặc định hiển thị thẻ ảnh theo phong cách marketplace của Client; có thể chuyển sang bảng danh sách có thumbnail. Ảnh bìa tải cục bộ bằng thumbnail, có cache và không tải lại ở mỗi nhịp cập nhật.
- Bấm một phòng hoặc nhấn Enter trên dòng được chọn để xem chi tiết trong cùng cửa sổ.
- Lọc theo mã phòng (hoặc ID phiên), từ ngày, đến ngày và hai lựa chọn trạng thái: Đang diễn ra / Đã kết thúc. Mặc định không giới hạn trạng thái; Bỏ lọc trở về mặc định.
- Ngày lọc là **ngày bắt đầu phiên**, theo múi giờ máy server; ngày kết thúc được tính trọn ngày. Phiên đã hủy thuộc nhóm đã kết thúc nhưng có nhãn Đã hủy riêng.
- Không loại bỏ phiên cũ đã được ẩn khỏi marketplace. Bộ lọc và chế độ xem được giữ khi quay lại danh sách hoặc chuyển mục quản trị.

## Ba tab chi tiết

Thanh chuyển mục dùng nút bo tròn cùng phong cách với tab trong phòng Client, thay cho tab JavaFX mặc định. Chuyển tab luôn về đầu nội dung để tránh cắt mất ảnh hoặc đầu hồ sơ. Nội dung có lề riêng, khung ngà bo góc, viền và bóng nhẹ; không tràn sát sidebar/cạnh cửa sổ.

1. **Chi tiết sản phẩm:** ảnh và tên ở cột trái; giá, người dẫn đầu/người thắng và thông tin phiên ở cột phải. Khung giá nâu trầm, chữ vàng giống Client. Mô tả nằm trong thẻ riêng bên dưới. Cửa sổ hẹp tự xếp thành một cột, ảnh giữ đầy đủ tỷ lệ và không đẩy vỡ bố cục.
2. **Thông tin người bán:** thẻ avatar/tên/username bên trái, thông tin liên hệ chia cột bên phải; giới thiệu và mốc thời gian nằm trong các khung riêng. DTO riêng không chứa mật khẩu, hash, salt hay số vòng băm.
3. **Lịch sử phiên:** tái sử dụng nguyên `LiveAuctionRoom` (biểu đồ) và `RoomProgressPane` (nhật ký) của Client qua adapter đọc dữ liệu cục bộ, không đăng nhập giả, không tham gia phòng và không tạo thêm sự kiện. Nhật ký có các nút Nhật ký cũ hơn / Theo dõi mới nhất, còn biểu đồ tải toàn bộ lượt giá bằng cursor.

Danh sách người từng tham gia nằm dưới nhật ký, gồm avatar, username, tên hiển thị và thời điểm vào phòng đầu tiên. Người đã rời phòng vẫn còn trong danh sách. Nếu quay lại, hiển thị thêm lần vào gần nhất và số lần vào; từng lượt vào đều có trong nhật ký phiên. Không giới hạn danh sách theo trang nhật ký đang xem. Dữ liệu cũ chỉ hiển thị các lượt tham gia thực sự đã được ghi nhận, không suy đoán thời gian.

Phòng ẩn danh dùng đúng bí danh Khách # của phòng, avatar mặc định, không lộ tên/username thật trong danh sách người tham gia, biểu đồ, nhật ký hay người thắng. Hồ sơ người bán vẫn là tab quản trị riêng theo yêu cầu.

## Cập nhật và kiểm thử

Danh sách/chi tiết tự làm mới mỗi 5 giây. Nhật ký dùng nhịp cập nhật sẵn có của Client và không tự nhảy khỏi trang lịch sử người dùng đang đọc. Rời mục quản trị dừng polling và bỏ kết quả tải cũ; khởi động lại server không giữ tham chiếu đến instance cũ.

Các kiểm thử liên quan: `AuctionSessionsSelfTest`, `AuctionSessionsUiSelfTest`, `RoomProgressStorageSelfTest`, `ServerAdminTabsUiSelfTest`.

Kiểm thử giao diện còn xác nhận ba thẻ ảnh cùng hàng ở cửa sổ rộng, các khung tự xếp cột và giữ đủ chiều cao khi thu nhỏ, không chồng lên mô tả/giới thiệu hoặc tràn ngang. Ảnh chụp từng tab và bố cục nhỏ được lưu tại `target/auction-sessions/`.
