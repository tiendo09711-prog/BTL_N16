# 00 – Bắt đầu đọc code

Đối chiếu source ngày **07/09/2026**. Luồng chính: **JavaFX → WebSocket → Java server → JDBC → XAMPP**. TCP/Swing còn để học và kiểm thử; không phải UI EXE client mặc định.

## Thứ tự đọc

1. [README](../README.md): mục đích, setup và sử dụng hai EXE.
2. [05 – VS Code + XAMPP](05_CACH_CHAY_VSCODE_XAMPP.md): cổng DB từng máy, JDBC và schema trống.
3. [01 – Kiến trúc](01_KIEN_TRUC_TONG_THE.md), [03 – Protocol](03_PROTOCOL_VA_LUONG_MESSAGE.md), [04 – Database](04_DATABASE.md).
4. [13 – Phân công](13_BANG_PHAN_CONG_FILE_THEO_NGUOI.md) → [members](members): phần mỗi người phải hiểu và bảo trì.
5. [09 – Nhiệm vụ từng file](09_NHIEM_VU_TUNG_FILE.md), [08 – Biến](08_TU_DIEN_BIEN_THEO_THANH_VIEN.md): tra khi đọc source.
6. [07 – Kiểm thử](07_DEMO_KIEM_THU.md), [14 – Diễn tập 5 người](14_KICH_BAN_DEMO_5_NGUOI.md), [16 – Checklist](16_CHECKLIST_CHUC_NANG_VA_BAN_GIAO.md).
7. [17 – JavaFX/WS](17_JAVAFX_WEBSOCKET_UPGRADE.md), [18 – Ảnh/private/search](18_PRODUCT_IMAGE_PRIVATE_ROOM_SEARCH.md), [19 – EXE/LAN](19_MULTI_MACHINE_AND_PACKAGING.md).

## Điểm vào source

Java main nằm trong src/main/java/vn/ptit/btl16/:

| Muốn hiểu | Đọc theo luồng |
|---|---|
| Client EXE | ClientMain → JavaFxClientApp → FxClientController |
| Server EXE | ServerDashboardMain → ServerApplication → ServerDashboardFrame/ServerAddresses |
| Request | AccountApi/AuctionApi → ClientTransport → server/network → MessageRouter |
| Database | ServerConfig → JdbcConnectionFactory → DatabaseSchema → JDBC repositories |
| Bid | AuctionController → BidService → JdbcAuctionRepository → AuctionBroadcastService |
| Phòng/ảnh/quyền chủ | AuctionManagementService → repository/runtime/session |
| Timer/kết quả/archive | AuctionTimerService → AuctionManager → RoomManager → event |

## Quy ước bàn giao

- Chức năng đã có trong source; docs/members là phạm vi **cần hiểu, giải thích, kiểm thử, bảo trì**, không phải kế hoạch viết lại.
- DB mới hoàn toàn trống: tự đăng ký, thêm sản phẩm, tạo phòng; không có tài khoản demo cài sẵn.
- Fixture src/test cách ly với XAMPP. Load-test chủ động tạo dữ liệu thật, chỉ chạy trên DB thử riêng.
- Mỗi người cần kể được UI → request → service → transaction → event → UI, rồi chỉ đúng đoạn mình phụ trách.
- [VERIFICATION](../VERIFICATION.md) ghi lệnh đã chạy thật. Chưa tick LAN/GUI đầy đủ nếu chỉ chạy test local.
