# 1. Tên dự án

**BTL 16 – Sàn đấu giá trực tuyến thời gian thực**

Khi thêm sản phẩm, Server tự cấp mã **MH1, MH2, MH3…**, dùng chung cho tất cả người bán. Người dùng không cần nhập và không thể sửa mã. Bộ đếm lưu trong MySQL, tiếp tục sau khi khởi động lại và không tái sử dụng mã sản phẩm đã xóa; mã cũ được giữ nguyên.

Biểu tượng **Bạn bè** nằm cạnh chuông thông báo: tìm bằng **tên hiển thị + mã #4 số**, gửi lời mời, chấp nhận/từ chối hoặc hủy lời mời đã gửi. Mã ngẫu nhiên cố định nằm trong **Hồ sơ tài khoản → Mã kết bạn**, được cấp cho cả tài khoản cũ và mới; người trùng tên có mã khác nhau. Trong phòng không ẩn danh, bấm avatar người trò chuyện rồi chọn **Kết bạn / Nhắn tin**. Sau khi hai bên kết bạn, chọn **Nhắn tin** trong mục Bạn bè để mở khung nhỏ ở góc dưới bên phải. Đóng hoặc thu gọn sẽ ẩn toàn bộ khung; mở lại qua biểu tượng Bạn bè. Nút **Đăng xuất** hiển thị ở cuối thanh bên, số tin chưa đọc nằm trên biểu tượng Bạn bè. Có thể gửi lời mời và tin nhắn cho bạn bè ngoại tuyến; người nhận thấy khi đăng nhập lại. Lịch sử, lời mời và tin nhận khi ngoại tuyến được lưu trong MySQL; đăng xuất sẽ xóa dữ liệu riêng đang hiển thị trên Client.

## 2. Dự án làm gì?

Cho phép đăng ký tài khoản, quản lý sản phẩm có ảnh, tạo phòng đấu giá công khai/riêng tư, đặt giá và theo dõi kết quả realtime. Chủ phòng chỉ được gia hạn thủ công/kết thúc sớm/hủy khi chưa có bid, và chỉ được kick người chưa từng bid trong phiên. Tổng gia hạn thủ công + tự động không quá 600 giây so với hạn ban đầu.

**Luật hiện hành:** [Bản Word đầy đủ](docs/LUAT_DAU_GIA_BTL16.docx), [bản Markdown để bảo trì](docs/LUAT_DAU_GIA_BTL16.md). Bộ quy tắc trình bày bằng ngôn ngữ tự nhiên để trao đổi với giảng viên: điều kiện tham gia, cách trả giá, thời hạn/gia hạn, quyền người bán, xác định người thắng, đấu giá lại và các ví dụ. Không đưa cấu trúc chương trình hoặc phụ lục kỹ thuật vào bản Word. Xuất lại bằng `python tools/export-auction-rules.py`; kiểm tra khớp source bằng `python tools/export-auction-rules.py --check`.

## 3. Server và client hoạt động thế nào? (EXE)

    Client EXE (JavaFX) → WebSocket/HTTP → Server EXE (JavaFX) → JDBC → MySQL trong XAMPP

- **Server** xử lý tài khoản, quyền vào phòng, giá bid, thời gian và người thắng; lưu dữ liệu trong DB.
- **Client** chỉ gửi yêu cầu và nhận cập nhật; không kết nối DB trực tiếp.
- Bản Windows mới: mở `dist/BTL16-Auction-Server.exe` trên máy chủ và `dist/BTL16-Auction-Client.exe` trên máy khách. Hai EXE ở **gốc dist** tự chứa ứng dụng và runtime, có thể chép riêng từng file.
- EXE đã kèm Java và công cụ xử lý media; không cần Maven/Node trên máy sử dụng. **Máy Server bắt buộc bật MySQL trong XAMPP**, mặc định `127.0.0.1:3306`, database `btl_16`. Không cần Apache. Client không cần XAMPP và không kết nối DB trực tiếp.
- Toàn bộ tài khoản, ví, yêu cầu nạp, sản phẩm, ảnh/video, lượt giá và kết quả được lưu trong MySQL trên máy Server. Không có H2 hoặc cơ chế tự chuyển sang database local khi MySQL ngừng chạy. Ảnh/video ngoài DB chỉ là tệp tạm để xử lý/phục vụ HTTP.
- Chia sẻ mã kết nối từ Server cho các máy cùng LAN/Wi-Fi. Chi tiết: [Hai ứng dụng JavaFX](docs/22_HAI_UNG_DUNG_JAVAFX.md). Các bản app-image trong thư mục con vẫn cần giữ nguyên cả thư mục.

### Bản macOS (client và server)

- Tạo portable cho **Mac Apple Silicon và Intel** ngay trên Windows: `npm run dist:mac:portable`. Bốn gói `.tar.gz` nằm trong `dist/`; máy nhận cần **JDK 17+ đúng kiến trúc**, mở file `.command` sau khi giải nén.
- Tạo `.app` **kèm runtime Java**: chạy `npm run dist` **trên Mac**, kết quả ở `dist/macos-arm64/` hoặc `dist/macos-x64/`. Server dùng cấu hình database của bản đóng gói, client không cần DB.
- Hướng dẫn chi tiết: [docs/20_MACOS_PACKAGING.md](docs/20_MACOS_PACKAGING.md). Bản Windows hiện tại được giữ riêng, không chuyển EXE thành ứng dụng Mac.

## 4. Setup để đọc và sửa code: VS Code + XAMPP

1. Cài **VS Code + Extension Pack for Java**, **JDK 17+**, **Maven 3.x**, **Node.js 20+** và **XAMPP**. Mở thư mục chứa `pom.xml` bằng VS Code.
2. Trong XAMPP Control Panel, bấm **Start** ở dòng **MySQL**. Không cần bật Apache, trừ khi muốn dùng phpMyAdmin.
3. Sửa `config/server.properties` cho đúng máy mình:

```properties
db.driver=com.mysql.cj.jdbc.Driver
db.host=127.0.0.1
db.port=3306
db.name=btl_16
db.user=root
db.password=
```

**Mỗi bạn có thể dùng cổng MySQL khác nhau:** đọc cột **Port(s)** trong XAMPP rồi sửa `db.port` (ví dụ 3306 hoặc 3307). Đây là cổng DB, **không phải** cổng client kết nối server (8890). Nếu tài khoản DB có mật khẩu, điền `db.password`.

4. Tại terminal gốc dự án, chạy:

```powershell
npm run build
./scripts/setup-db.cmd
npm run dev
```

Maven tự tải JDBC **MySQL Connector/J 8.4.0** từ `pom.xml`; không cần chép JAR thủ công. Setup chỉ tạo/cập nhật cấu trúc DB, **không tạo tài khoản, sản phẩm hay phiên mẫu** và không xóa dữ liệu đã nhập. Muốn xóa toàn bộ DB dự án và bắt đầu lại: dừng server, chạy `./scripts/reset-db.cmd` rồi gõ `RESET`.

Chạy riêng server: `npm run dev:server`; mở thêm client: `npm run client`; kiểm thử: `npm test`. Tạo lại hai EXE sau khi sửa code: `npm run dist` (JDK cần có `jpackage`).

Kiểm thử cả nút JavaFX, tải HEIC/AVIF và hành trình đấu giá đến hết hạn thật: `npm run test:full`. Kiểm tra tất cả gói Mac/Windows: `npm run test:bundles`; kiểm thử lại bằng runtime/JAR đóng gói Windows: `npm run test:dist`. Log và JSON nằm trong `target/qa/` hoặc `target/qa-packaged/`. Chi tiết phạm vi và phần chưa kiểm chứng: [docs/24_KIEM_THU_TOAN_BO.md](docs/24_KIEM_THU_TOAN_BO.md).

Đọc code theo [docs/00_BAT_DAU.md](docs/00_BAT_DAU.md), setup chi tiết tại [docs/05_CACH_CHAY_VSCODE_XAMPP.md](docs/05_CACH_CHAY_VSCODE_XAMPP.md); nhiệm vụ từng người nằm trong [docs/members](docs/members).

## 5. Hướng dẫn sử dụng server, client

**Máy server**
1. Bật **MySQL** trong XAMPP trên máy server trước khi mở EXE. Cấu hình mặc định: `127.0.0.1:3306`, database `btl_16`, tài khoản `root`, mật khẩu trống; sửa `dist/BTL16-Auction-Server/app/config/server.properties` nếu máy dùng thông số khác. Không có database local dự phòng.
2. Mở `BTL16-Auction-Server.exe`, chọn IP LAN cùng mạng với client, bấm **Sao chep dia chi** và gửi địa chỉ cho các bạn.
3. Cho phép inbound TCP **8890 và 8891** (WebSocket và ảnh/video; đổi theo cấu hình nếu cần) trên mạng Private của Windows Firewall. Nếu chỉ mở 8890, client có thể đăng nhập nhưng không đăng sản phẩm có ảnh được. Không mở cổng MySQL cho client. Giữ cửa sổ server mở khi sử dụng.

**Máy client**
1. Mở `BTL16-Auction-Client.exe`, dán địa chỉ `ws://IP_SERVER:8890/ws`, bấm **Kết nối**. Nếu chạy cùng máy server, dùng `ws://127.0.0.1:8890/ws`.
2. **Đăng ký tài khoản mới**, rồi đăng nhập. Hệ thống ban đầu trống, không có tài khoản demo.
3. Người bán thêm sản phẩm với mô tả 50-2000 ký tự, 1-5 ảnh và tối đa một video, rồi tạo phòng, đặt giá khởi điểm/bước giá/thời gian và mật khẩu nếu là phòng riêng tư. Xem [Ảnh và video sản phẩm](docs/23_ANH_VIDEO_SAN_PHAM.md) để biết định dạng, giới hạn, biên tập và sao lưu.
4. Người mua mở **Ví & nạp tiền**, nhập số tiền, bấm **Hiện QR demo** rồi **Đã nhập**. Người vận hành Server mở **Tài khoản & ví → Duyệt nạp demo**, chọn yêu cầu và chấp nhận. Số dư ban đầu bằng 0; chỉ tăng khi server duyệt. QR không chuyển tiền thật.
5. Người mua tìm phòng → vào phòng → đặt giá với số dư khả dụng đủ trả toàn bộ giá. Tiền của người dẫn đầu được giữ chỗ; khi bị vượt giá sẽ được giải phóng. Hết phiên, người mua trả đúng giá chốt; thuế trừ vào tiền người bán: **đến đúng 1 tỷ đồng là 10%, trên 1 tỷ là 15%**. Theo thứ hạng doanh thu trước khi chốt phiên, thuế suất được giảm trực tiếp: Top 1 giảm 5 điểm %, Top 2 giảm 4, Top 3 giảm 3, Top 4–5 giảm 2, Top 6–10 giảm 1; ngoài Top 10 không giảm. Ví dụ: giá chốt 100 triệu, người bán Top 1 trả thuế 5 triệu và nhận 95 triệu. Thuế làm tròn đến 2 chữ số thập phân, lưu cùng thanh toán và không thu lần hai. Biên nhận lưu thuế suất ban đầu, mức giảm, thứ hạng trước phiên, thuế thực thu và tiền thực nhận. Phiên cũ giữ nguyên số tiền đã thanh toán. Phòng vừa đóng còn hiển thị trong 60 giây với thời gian nhấp nháy đỏ; người đang ở phòng xem kết quả rồi tự trở về sàn. Lịch sử không bị xóa.
6. Chủ phòng không được tự bid; người đang dẫn đầu không được tự nâng giá. Bid đầu tối thiểu bằng giá khởi điểm + bước giá. Sản phẩm đã có phiên bị khóa nội dung/ảnh; sản phẩm đã có người thắng không được đấu giá lại. Chi tiết ví và dữ liệu cũ: [Hai ứng dụng JavaFX](docs/22_HAI_UNG_DUNG_JAVAFX.md).

Sàn đấu giá dùng thanh điều hướng nâu, điểm nhấn vàng và banner nền kem. Bốn ô thống kê hiển thị dữ liệu hiện tại; sản phẩm có thể xem dạng lưới hoặc danh sách, lọc theo trạng thái và tìm theo tên sản phẩm, mã phòng hoặc người bán. Mỗi thẻ dùng ảnh đầu tiên của sản phẩm, kèm giá, bước giá, thời gian và nút **Vào phòng**. Khung bên phải tự xem trước sản phẩm đầu tiên; bấm thẻ khác để đổi chi tiết, bấm **Vào phòng** để tham gia. Nút tim đánh dấu yêu thích trong phiên đăng nhập hiện tại. Tìm kiếm, **Bộ lọc**, sắp xếp và phân trang áp dụng cho toàn bộ danh sách. Ảnh phòng riêng chỉ tải khi tài khoản có quyền xem như trước.

Mục **5 phiên nổi bật nhất** nằm trên danh sách sản phẩm. Điểm chung bằng số người hiện trong phòng + tổng lượt ra giá hợp lệ + tổng lượt vào/rời phòng; mỗi người/lượt tính 1 điểm. Một tài khoản mở nhiều kết nối chỉ tính một người và không tạo lượt vào/rời giả. Chỉ xếp phiên công khai đang diễn ra mà người xem không bị chặn; tối đa 5 phiên, cập nhật mỗi 5 giây. Khi bằng điểm, ưu tiên số người hiện tại, giờ kết thúc gần hơn rồi mã phòng nhỏ hơn. Bấm thẻ để vào phòng như nút Vào phòng cũ.

Trong **Sản phẩm bán**, chỉ hiển thị sản phẩm do tài khoản đăng bán, không hiển thị sản phẩm đã mua hoặc bộ lọc Đã sở hữu. Trạng thái lấy từ phiên đấu giá: **Đã bán** màu đỏ nếu có phiên chốt với người thắng; **Đang đấu giá** màu xanh nếu có phiên mở (gồm đã hẹn giờ); còn lại **Chưa giao dịch** màu đen. Phiên hủy hoặc hết giờ không có người thắng trở về Chưa giao dịch. Danh sách, lưới, chi tiết và bộ lọc dùng chung trạng thái; tự làm mới mỗi 5 giây khi trang mở. Trong **Thông báo của tôi**, menu **⋮ → Xóa** xóa thông báo của chính người dùng, tải lại số chưa đọc và trang đầu; không xóa biên nhận, kết quả hay sổ giao dịch.

Khi **Tạo phòng đấu giá**, mục **Thời gian bắt đầu** cho phép **Bắt đầu ngay** hoặc **Hẹn giờ bắt đầu** bằng ngày và giờ:phút trên máy. Phòng hẹn giờ xuất hiện ở **Sắp diễn ra**, hiển thị lịch bắt đầu và chưa cho vào phòng/đặt giá; đến giờ tự chuyển sang **Đang đấu giá**. Mục **Sắp diễn ra** luôn xếp phòng có giờ bắt đầu gần nhất lên trước, kể cả qua nhiều trang; tạo phòng hẹn giờ thành công sẽ tự mở mục này. Thời lượng 1–180 phút tính từ lúc bắt đầu đã chọn. Lịch được lưu trên Server và giữ nguyên sau khi khởi động lại; cần giữ Server hoạt động để các máy có thể tham gia. Cập nhật mã nguồn phải đóng và mở lại cả Server lẫn Client. Client chặn gửi yêu cầu hẹn giờ đến Server cũ để tránh Server bỏ qua lịch và cho phòng chạy ngay.

Khi hết giờ, thời gian nhấp nháy đỏ ở thẻ sản phẩm và trong phòng. Thẻ vừa kết thúc còn hiển thị trong 60 giây xem kết quả. Mục **Top 10 doanh thu** xếp theo tổng giá chốt trước thuế của mọi phiên bán thành công của từng người bán, không giới hạn theo tháng. Bảng nền kem có huy chương vàng–bạc–đồng, ảnh đại diện công khai, tên tài khoản, số phiên thành công, tổng doanh thu và mức giảm thuế; tự cập nhật mỗi 5 giây và lưu được sau khi khởi động lại Server. Bằng doanh thu thì người có phiên bán thành công đầu tiên sớm hơn xếp trước, sau đó theo mã tài khoản. Phiên chưa kết thúc, bị hủy hoặc không có người thắng không tạo doanh thu. Khi chốt đồng thời, Server khóa thứ tự chốt và lấy thứ hạng trước mỗi phiên trong cùng giao dịch thanh toán. Chỉ người thắng nhận GIF chúc mừng mới do người dùng cung cấp: huy hiệu nằm chính giữa, chiếm khoảng 1/3 diện tích cửa sổ; pháo hoa bung ra gần hết cửa sổ. Toàn bộ hiệu ứng tự tắt sau 3 giây hoặc đóng ngay bằng nút **×** ở góc trên bên phải huy hiệu. Hai lớp GIF có nền trong suốt và chạy lại từ đầu mỗi lần thắng; sự kiện kết thúc trùng không phát lại.

Sảnh có ba tab **Tất cả sản phẩm**, **Đang đấu giá**, **Sắp diễn ra**. Kết quả cá nhân xem tại **Kết quả phiên của tôi**: danh sách có ảnh đầu tiên của sản phẩm và nhãn kết quả; bấm nút **⋮** để mở khung chi tiết bên phải, đóng bằng **×**. Khung chi tiết hiển thị thông tin sản phẩm, kết quả, phí sàn, tiền thực nhận và giao dịch ví của chính tài khoản. Có bộ lọc, tìm kiếm và nút chuyển trang trước/sau. Trong phòng, ảnh sản phẩm hiển thị lớn và mục **Luật đấu giá** ở thanh bên mở trang hướng dẫn trong cửa sổ chính, giữ nguyên phòng đang tham gia. Cột trái cuộn liền mạch qua ảnh, nhật ký, biểu đồ và chi tiết sản phẩm; cột đặt giá bên phải giữ nguyên vị trí. **Trò chuyện (số người)** gộp tin nhắn và danh sách người tham gia, đặt dưới khung đặt giá. Khi có người thắng, dòng chúc mừng hiện trên các Client đang kết nối và chạy từ phải sang trái 3 lượt, ghi rõ người thắng, sản phẩm và giá chốt.

Bấm **chuông thông báo** phía trên để mở danh sách thông báo; mục thông báo đã được bỏ khỏi thanh bên. Chuông lớn hơn, có huy hiệu đỏ đếm tin chưa đọc (trên 99 hiện **99+**), tự kiểm tra mỗi 5 giây và cập nhật sau thao tác đọc/xóa; hết tin chưa đọc thì ẩn huy hiệu. Danh sách dùng giao diện kem/vàng, danh sách bên trái và khung chi tiết bên phải. Nút **mắt** hoặc **⋮ → Xem chi tiết** mở kết quả đã chốt, ảnh đầu tiên của sản phẩm, người thắng, giá chốt, phí sàn đã ghi nhận và trạng thái ví; **Xem sản phẩm** mở mô tả ngay trong khung. Có lọc chưa đọc, chọn từng dòng hoặc chọn cả trang để đánh dấu đã đọc/chưa đọc, cùng phân trang trước/sau với 6 thông báo mỗi trang. Thông báo nạp tiền mở ví, thông báo giá có nút quay lại phòng trực tiếp và vẫn kiểm tra quyền/mật khẩu. Khung chi tiết cuộn riêng và đóng bằng **×**; dữ liệu của phiên đăng nhập cũ và phản hồi đến muộn không được hiển thị.

Người chỉ xem phòng rồi rời đi mà chưa đặt giá không nhận tin cá nhân về giá/kết quả của phòng đó. Người đã đặt giá nhận một tin **Đã có giá thay đổi** ở lần giá mới đầu tiên trong mỗi lần rời phòng, kèm mã phòng và liên kết quay lại. Đọc/xóa tin hoặc khởi động lại Server không làm gửi lặp; quay lại rồi rời đi mới cho phép một tin tiếp theo. Thông báo kết quả cuối phiên cho người đã đặt giá vẫn giữ riêng. Mở lại cả Server và Client để áp dụng cập nhật.

**Ví & nạp tiền** hiển thị ngay trong cửa sổ chính, giữ thanh điều hướng: ba ô số dư/khả dụng/giữ chỗ, nạp QR bên trái, yêu cầu nạp và sổ giao dịch bên phải. Tên loại giao dịch đã dịch sang tiếng Việt; mã yêu cầu có nút sao chép. Đổi về **Sàn đấu giá** sẽ dừng tác vụ làm mới của ví. **Sản phẩm bán** nằm trong cửa sổ chính, có tìm theo mã/tên, lọc trạng thái, chuyển danh sách/lưới, ảnh đầu tiên và khung chi tiết bên phải. Thanh dưới chỉ còn **Thêm sản phẩm** và **Làm mới**; các thao tác **Sửa**, **Ảnh / video**, **Xóa sản phẩm** nằm trong menu ba chấm của từng sản phẩm. Bỏ toàn bộ phần khôi phục và nút Đóng phía dưới; dấu × phía trên bên phải đưa về Sàn đấu giá. Chỉ chủ sở hữu được xóa sau khi xác nhận; sản phẩm còn phiên đang mở hoặc sắp diễn ra phải xử lý phiên trước. Sản phẩm chưa từng có phiên được xóa cùng ảnh/video; sản phẩm đã có phiên được loại khỏi danh sách và không thể khôi phục, dữ liệu lịch sử/biên nhận/ảnh/giao dịch/doanh thu vẫn được giữ nguyên. **Hồ sơ tài khoản** chia nhóm thông tin cơ bản, hồ sơ công khai và quyền riêng tư; ngày tạo dùng định dạng giờ địa phương, có Lưu, Hủy, Đổi mật khẩu và sửa ảnh đại diện công khai. **Luật đấu giá** nằm trong cửa sổ chính, có banner vàng nhạt, bảy bước đánh số và ví dụ minh họa ở cột trái, các nguyên tắc/hành vi bị cấm/lưu ý ở cột phải. Chi tiết doanh thu và thuế có thể mở rộng ở bước 4; giữ đủ quy tắc hiện hành. Khi chưa có sản phẩm đủ điều kiện để tạo phòng, thông báo kem/vàng xuất hiện giữa màn hình với nền tối, hình minh họa đấu giá, nút Thử lại ngay để tải lại sản phẩm và dấu × để đóng.

Mỗi lượt ra giá mới được Server chấp nhận hiển thị banner động **Đang dẫn đầu** ở chính giữa cửa sổ trong phòng, dùng hình người dùng cung cấp với tên tài khoản người ra giá và số tiền thực tế. Tên dài tự thu nhỏ để vừa khung. Banner có nền trong suốt, giữ đến khi giọng đọc thông báo lượt ra giá đó kết thúc và cập nhật ngay khi có giá mới; thông báo cũ đọc xong không làm ẩn banner của lượt mới. Khi tắt giọng đọc hoặc chưa cài giọng, banner tự ẩn sau 5 giây. Banner không chặn thao tác đặt giá hoặc trò chuyện. Phòng ẩn danh hiển thị mã **Khách #…** của phòng. Vào phòng, đồng bộ lịch sử và sự kiện trùng không phát lại banner; dừng giọng đọc, rời phòng, mất kết nối hoặc kết thúc phiên sẽ ẩn ngay.

Client tự phát nhạc nền **Yume to Hazakura** từ lúc mở ứng dụng, liên tục qua đăng nhập, sảnh và các phòng. Dùng bản loop người dùng cung cấp `Yume_to_Hazakura_Auction_loop_74s_to_hon.mp3`, giải mã thành PCM 44,1 kHz stereo tại `src/main/resources/audio/client/background-music.wav`. Toàn bộ bản nhạc nằm trong bộ nhớ và dùng một luồng âm thanh duy nhất, nối khung cuối về khung đầu ngay trong cùng bộ đệm; không mở lại MP3, chèn thời gian chờ hay phát chồng nhiều bản. Menu **Âm thanh** có **Bật nhạc nền** và thanh **Âm lượng nhạc nền** riêng, lưu cho lần mở sau. Tắt âm thanh chung sẽ tắt cả nhạc, hiệu ứng và giọng đọc. Nhạc dừng khi đóng Client; không chạy ở Server. Kiểm thử dùng thiết bị giả hoặc `-Dbtl16.client.sound.disabled=true` để không phát âm thanh thật.

Menu **Âm thanh** cho phép chỉnh âm lượng, bật/tắt **Đọc thông báo đấu giá** và **Nghe thử giọng Việt**. Giọng đọc theo nhịp MC: báo giá nhanh, rõ, câu hỏi chậm hơn. Chỉ người đang trong phòng nghe các lượt ra giá mới và câu **“Có ai muốn đưa ra mức giá cao hơn không ạ?”** khi còn 30 giây, một lần mỗi phiên; không đọc lại lịch sử hoặc nhắc lại sau khi gia hạn, đồng bộ hay vào lại phòng. Khi Server xác nhận phiên kết thúc, riêng người thắng nghe **“Chúc mừng bạn đã đấu giá thành công (tên sản phẩm)!”** một lần, kể cả đang ở sảnh. Tắt âm thanh hoặc giọng đọc sẽ tắt cả lời nhắc và lời chúc mừng. Chuẩn bị giọng Việt trên Windows bằng `powershell -ExecutionPolicy Bypass -File tools/prepare-client-voice.ps1` trước khi chạy hoặc đóng gói Client; xem [hướng dẫn giọng đọc](tools/speech/README.md). Giọng được tạo trên máy, không gửi tên người đấu giá hay số tiền lên dịch vụ bên ngoài.

Chi tiết phân phối EXE và kết nối LAN: [docs/19_MULTI_MACHINE_AND_PACKAGING.md](docs/19_MULTI_MACHINE_AND_PACKAGING.md).
