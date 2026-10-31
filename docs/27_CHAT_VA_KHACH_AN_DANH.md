# Chat nhóm và chế độ danh tính trong phòng đấu giá

## Bo sung Cum 4-6

- Ket qua va bien nhan dung bi danh trong phong an danh, khong them API tra
  tai khoan nguoi thang. Inbox ca nhan khong luu ten/danh tinh nguoi vuot gia.
- Co bien nhan/thong bao KHONG dong nghia co quyen mo lai phong rieng, chat,
  roster hay ho so. Session moi van phai qua quyen vao phong hien hanh.
- Ho so rieng tach public; nut xem truoc chi hien ten hien thi, ngay tham gia,
  avatar va biography. Khong cong khai email, dien thoai, vi hay session.
- Mute chi chan chat/tra loi/reaction, khong chan bid; kick va session het han
  duoc server kiem tra. Luu tru chat khong doi. Demo: `32_DEMO_VA_NGHIEM_THU.md`.

## Dùng thử

1. Đăng nhập chủ phòng, tạo sản phẩm và chọn **Tạo phòng đấu giá**.
2. Chọn **Khách thường** hoặc **Khách ẩn danh**. Lựa chọn này độc lập với phòng công khai/riêng tư có mật khẩu và không thể đổi sau khi tạo.
3. Chủ phòng và khách bấm **Vào phòng**. Khung **Không gian chung** có các tab **Trò chuyện**, **Người tham gia**, **Diễn biến giá** và **Chi tiết**. Chat mở mặc định; ô nhập nằm ngay trong màn hình phòng, không bị đẩy xuống dưới biểu đồ.
4. Với phòng thường, bấm avatar trong chat/danh sách khách để xem tên hiển thị, avatar, giới thiệu và ngày tham gia. Avatar ở lịch sử giá cũng mở hồ sơ nếu người đó còn trong phòng.
5. Trong **Hồ sơ**, chọn **Avatar và giới thiệu công khai** để tải ảnh PNG/JPEG hoặc xóa avatar; giới thiệu tối đa 500 ký tự. Client và server nhận ảnh tối đa 750 KB, giới hạn 8 triệu pixel và chuẩn hóa ảnh thành JPEG 128x128, bỏ metadata gốc.
6. Với phòng ẩn danh, khách chỉ xuất hiện dưới tên **Khách #1**, **Khách #2**... cùng avatar trống. Không có hành động xem hồ sơ. Danh sách người tham gia cho chủ phòng có nút **Mời ra** theo số khách, không cần biết tài khoản. Người đã đặt giá hợp lệ vẫn không thể bị mời ra.

## Quyền riêng tư

- Server không trả email, số điện thoại, ví, mật khẩu, địa chỉ mạng, phiên đăng nhập hoặc lần đăng nhập cuối qua API hồ sơ công khai.
- Phòng ẩn danh che tài khoản khách ngay trong gói tin gửi tới client, áp dụng cả chủ phòng: danh sách phòng, snapshot/resync, lịch sử giá, lượt trả giá, người dẫn trước, thông báo vượt giá, kết quả và kick.
- Khách có mã công khai âm theo thứ tự riêng trong từng phòng, không phải ID tài khoản. Mã được lưu trong MySQL, không đổi khi rời/vào lại hay khởi động lại server. Chủ phòng vẫn là chủ sở hữu công khai của sản phẩm; trong chat phòng ẩn danh chỉ hiện nhãn **Chủ phòng** và avatar trống.
- API xem hồ sơ từ chối mọi yêu cầu trong phòng ẩn danh, kể cả yêu cầu tự viết hoặc do chủ phòng gửi. API kick bằng username bị vô hiệu hóa trong phòng ẩn danh để tránh dò tài khoản.
- Số trong nhãn **Khách #1**, **Khách #2**... chỉ tính khách, không tính chủ phòng và không lấy trực tiếp từ mã công khai âm. Số được giữ nguyên khi khách rời/vào lại, không đánh lại theo số người đang trực tuyến.
- Đây là ẩn danh giữa người dùng, không phải ẩn danh đối với quản trị cơ sở dữ liệu: server vẫn giữ ID thật cho xác thực, ví và xử lý đấu giá.
- Nội dung chat do người dùng tự nhập: hệ thống không thể bảo đảm ẩn danh nếu người dùng tự viết tên, số điện thoại hoặc thông tin nhận diện. Giao diện nhắc không chia sẻ thông tin cá nhân.

## Chat và lưu trữ

- Chỉ phiên đăng nhập đang hoạt động và đang tham gia phòng được đọc/gửi chat hoặc xem hồ sơ. Kiểm tra lại quyền phòng riêng và trạng thái bị chặn ở server.
- Tin nhắn từ 1 đến 500 ký tự Unicode, tối thiểu 750 ms giữa hai tin của cùng một khách trong một phòng. Người gửi lấy từ phiên đăng nhập, không lấy từ dữ liệu client.
- Tin nhắn có nonce để gửi lại khi lỗi mạng mà không tạo bản sao; tái sử dụng nonce với nội dung khác bị từ chối.
- Chat được lưu trong MySQL, trả 50 tin gần nhất khi vào phòng. Client giữ tối đa 200 tin trong bộ nhớ, chống trùng theo ID và thông báo tin mới khi đang cuộn lên.
- Chat chuyển sang chỉ đọc khi phiên kết thúc hoặc bị hủy; không gửi khi mất kết nối. Danh sách khách được đồng bộ khoảng 3 giây, tin nhắn mới nhận realtime qua socket.
- Avatar tải riêng qua API hồ sơ và được cache theo phòng; không lặp lại dữ liệu ảnh trong từng tin nhắn. Cache xóa khi rời phòng/đăng xuất; phòng ẩn danh không gọi API avatar.

## Nâng cấp và kiểm thử

Giữ `db.autoInitialize=true` để server tự thêm cột `auctions.anonymous_guests` và các bảng `room_participants`, `room_chat`, `user_public_profiles`, không xóa dữ liệu cũ. Phòng cũ mặc định khách thường. `sql/00_schema.sql` chứa cấu trúc cho cơ sở dữ liệu mới; không chạy script reset trên dữ liệu đang dùng.

Các nhóm kiểm thử: `RoomSocialSelfTest` (socket thật và kiểm tra gói tin/quyền), `RoomSocialStorageSelfTest` (H2 biệt lập, migration, persistence, cấp số đồng thời, avatar, che kết quả), `RoomSocialUiSelfTest` (giao diện JavaFX, chat, avatar/hồ sơ, chuyển phòng và chế độ ẩn danh). Hai nhóm đầu được chạy trong `AllSelfTests`; nhóm UI chạy qua `tools/run-tests.mjs --ui`.

Thay đổi có trong mã nguồn; cần build lại bản phân phối nếu demo bằng EXE cũ.
