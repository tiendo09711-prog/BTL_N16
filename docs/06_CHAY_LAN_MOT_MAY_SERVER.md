# 06 – LAN: một máy server, các máy còn lại là client

1. Máy A Start MySQL trong XAMPP, chỉnh db.port/password trong app/config/server.properties của Server EXE rồi mở ứng dụng.
2. Chọn IPv4 LAN trên dashboard cùng mạng với B/C, bấm Sao chep dia chi và gửi URL ws://IP_MAY_A:8890/ws.
3. B/C nhận nguyên folder Client EXE, mở ứng dụng, dán URL và bấm Kết nối. Máy client không cần DB, source, JDK, Maven hay Node.
4. Tự đăng ký account riêng, một người thêm sản phẩm/tạo phòng và người khác join/bid; không có dữ liệu mẫu cài sẵn.

## Cổng và cấu hình

- WebSocket 8890: JavaFX mặc định, mở inbound TCP trên mạng Private của Windows Firewall.
- TCP 8888: chỉ mở nếu dùng legacy/console/load-test.
- DB 3306 hoặc cổng XAMPP từng máy: không mở cho client. Chỉ server JDBC kết nối DB.
- Server bind mặc định 0.0.0.0; client dùng IPv4 thật, không nhập 0.0.0.0. 127.0.0.1 chỉ dùng khi chạy cùng máy server.
- Các khóa listener: server.tcp.bindAddress/port và server.websocket.bindAddress/port/path trong config.

Nếu chạy bằng source: npm run dev:server trên A, npm run client -- --url=ws://IP_MAY_A:8890/ws trên B/C. Runner dùng cổng listener mặc định; tùy biến listener thì chạy main trực tiếp theo docs/05.

## Chẩn đoán và an toàn

- Refused: server chưa bật hoặc sai port. Timeout: firewall, sai IP, khác LAN hoặc Wi-Fi chặn thiết bị liên lạc.
- JDBC lỗi: kiểm tra XAMPP trên A và cấu hình DB của server, không cài DB lên B/C để chữa.
- Đổi mạng/IP: Lam moi dia chi rồi gửi URL mới; nhiều card/VPN cần chọn đúng mạng client.
- AUTH_REQUIRED: login/resume; quá grace hoặc server restart phải login lại.
- ws:// là endpoint, không phải website. Không có web frontend chỉ cần bấm link là chơi.
- WS/TCP mặc định không TLS: dùng tài khoản thử trong LAN tin cậy, không đưa trực tiếp ra Internet. Không gửi mật khẩu DB cho client.

Giữ server mở trong buổi diễn tập; đóng cửa sổ hoặc Dung server sẽ ngắt client. LAN vật lý/firewall cần nhóm thử trực tiếp; chi tiết EXE/Internet tại [19](19_MULTI_MACHINE_AND_PACKAGING.md).
