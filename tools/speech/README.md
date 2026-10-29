# Giọng đọc tiếng Việt trên Client

Chuẩn bị trên Windows x64 (chỉ tải các gói công khai, không gửi dữ liệu đấu giá):

```powershell
powershell -ExecutionPolicy Bypass -File tools/prepare-client-voice.ps1
```

Piper `2023.11.14-2` và mô hình `vi_VN-vais1000-medium` tạo giọng nói ngay trên máy.
Khi phát thông báo, Client không sử dụng dịch vụ đọc qua mạng.
Phần tên và giá đọc liền mạch với `length_scale=0.94`; câu mời trả giá dùng
`length_scale=1.03`, `noise_scale=0.75`, `noise_w=0.85` và khoảng nghỉ cuối câu
0,18 giây để có nhịp MC rõ ràng, câu hỏi chậm hơn. Đây là điều chỉnh cách đọc của
mô hình hiện có; mô hình vẫn là giọng đơn VAIS-1000.

Hai câu mời cố định nằm trong `src/main/resources/audio/client/auction-*.wav`,
được tạo sẵn bằng cùng mô hình để câu nhắc 30 giây không phải chờ tổng hợp.
Có thể tạo lại bằng `python tools/render-client-voice.py` sau khi chuẩn bị Piper.
Các file này được đóng gói cùng tài nguyên Client, dùng cùng giấy phép mô hình.

Client dùng giờ Server để nhắc **“Có ai muốn đưa ra mức giá cao hơn không ạ?”**
khi còn 30 giây. Chỉ phòng đang tham gia, có kết nối và dữ liệu còn mới được
phát; sảnh và các phòng khác im lặng. Mỗi phiên chỉ nhắc một lần, kể cả khi
gia hạn, đồng bộ hay vào lại phòng. Lời nhắc được ưu tiên trước các lượt đọc đang
chờ, không chồng lên câu đang phát, và bị hủy khi rời phòng hoặc hết giờ.
Menu **Âm thanh → Đọc thông báo đấu giá**, nút tắt âm thanh và âm lượng áp dụng
cho cả thông báo ra giá lẫn lời nhắc.

Khi nhận `AUCTION_ENDED`, riêng người thắng nghe **“Chúc mừng bạn đã đấu giá
thành công (tên sản phẩm)!”** theo tên sản phẩm thật trong kết quả Server. Lời
chúc phát một lần cùng lần hiện ảnh chúc mừng, kể cả người thắng đã ra sảnh;
dùng cờ `winnerIsSelf` của Server để nhận diện đúng người thắng trong phòng ẩn
danh. Đồng bộ hoặc nhận lại kết quả không phát lại hay cắt ngang lời chúc.
Hết giờ trên máy Client, phiên bị hủy hoặc không có người thắng không phát lời
chúc. Cài đặt âm lượng và bật/tắt giọng đọc cũng áp dụng cho lời chúc mừng.

Mô hình được ghim ở revision `5041a7a49ae5b3a0f30e6837769eb03fdbb231bd` của
`rhasspy/piper-voices`, SHA-256:
`ec7c89e2c85f4d1edc24b6120c18aaf1bda614f06b511567eb9c7c0de15e2dab`.

Giữ nguyên thư mục `piper/`, file `.onnx`, `.onnx.json`, MODEL_CARD và giấy phép khi
đóng gói. Dataset VAIS-1000 dùng CC BY 4.0; xem MODEL_CARD.md. Piper dùng MIT;
eSpeak NG đi kèm dùng GPLv3. Script tải giấy phép để phân phối cùng ứng dụng.

Client tự tìm `tools/speech` ở gốc dự án hoặc trong thư mục `app` của bản đóng gói.
Có thể chỉ định vị trí khác bằng `-Dbtl16.speech.dir=...`. Trên macOS/Linux, đặt
bản Piper phù hợp trong `piper/piper` cùng mô hình tiếng Việt ở thư mục này.
