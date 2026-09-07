# 14 – Kịch bản diễn tập 5 người

Bắt đầu bằng DB trống. Nhóm tự nhập tài khoản/sản phẩm/phòng trong buổi diễn tập, không có mẫu cài sẵn.

| Người | Trình bày và thao tác |
|---|---|
| Đỗ Tiến | Start MySQL XAMPP, chỉ db.port/JDBC, mở Server EXE, copy URL; giải thích router/account/session |
| Trần Văn Phước | Tự tạo account người bán, thêm sản phẩm có ảnh, tạo PUBLIC/PRIVATE; chỉ BLOB/ownership/search |
| Phạm Anh Dũng | Account người mua A, join/bid; chỉ membership, min increment, khóa và transaction |
| Mai Trung Đức | Theo dõi anti-sniping, cùng người bán extend/end/cancel; giải thích result/retention/archive |
| Vũ Trí Thuận | Account người mua B, bid cạnh tranh, xem event, mất mạng/reconnect/resync; chỉ Platform.runLater |

1. Tiến mở server; Dũng/Thuận nhận URL và mở client, đăng ký account riêng.
2. Phước tạo sản phẩm/phòng; người mua tìm tên/ID và vào phòng.
3. Dũng/Thuận bid gần đồng thời, quan sát outbid; Phước thử tự bid và bị từ chối.
4. Phòng PRIVATE: nhập sai rồi đúng password; kick một người và thử vào lại.
5. Phòng khác: bid sát cuối để Đức giải thích endTime tăng; Thuận thử reconnect trong grace.
6. Kết thúc phòng: chờ 120 giây để tự archive; query DB thấy lịch sử còn nguyên.

Cần đúng cổng XAMPP trên máy server, WS/firewall cho client và nguyên folder EXE app/runtime. Máy client không cài DB. Dùng console/legacy chỉ khi trình bày TCP framing riêng. Không giả định auction ID cố định. Reset cuối buổi chỉ khi thực sự muốn xóa toàn bộ DB và đã dừng server.
