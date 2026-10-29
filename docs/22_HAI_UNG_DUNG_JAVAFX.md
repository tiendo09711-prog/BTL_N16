# Hai ứng dụng Auction cho Windows

## Mở ứng dụng

Khi thêm sản phẩm, hệ thống tự cấp mã **MH1, MH2, MH3…** khi lưu thành công. Mã dùng chung cho mọi người bán, không nhập hoặc sửa thủ công. Bộ đếm được lưu trong MySQL và không đặt lại khi khởi động Server hay xóa sản phẩm. Mã cũ được giữ nguyên; hệ thống tự bỏ qua mã MH đã tồn tại.

- Máy chủ mở `dist/BTL16-Auction-Server.exe`.
- Những máy còn lại mở `dist/BTL16-Auction-Client.exe`.
- Có thể chỉ chép một EXE tương ứng sang mỗi máy. Java và công cụ media đã được đóng gói. **Máy Server phải cài XAMPP và bật MySQL**; Client không cần XAMPP, Java, Maven hoặc Node.js.
- Bản đóng gói dành cho Windows 10/11 x64. Lần mở đầu tiên cần một chút thời gian để giải nén runtime vào `%LOCALAPPDATA%/BTL16/DesktopApps`.
- Bộ ứng dụng chưa ký số. Chỉ mở EXE do chính nhóm build hoặc nhận từ nguồn tin cậy; không tắt phần mềm bảo vệ máy.

## Bạn bè và nhắn tin riêng

- Mỗi tài khoản có mã ngẫu nhiên cố định dạng **#0123**, xem tại **Tài khoản → Mã kết bạn**. Server tự cấp mã cho cả tài khoản mới và tài khoản cũ. Hai người trùng tên hiển thị có mã khác nhau; mã giữ nguyên khi đăng nhập lại, khởi động lại Server hoặc sửa hồ sơ. Nếu đổi tên tạo ra cặp tên + mã đã có người dùng, hãy chọn tên hiển thị khác.
- Bấm biểu tượng hình người cạnh chuông thông báo để mở **Bạn bè**. Nhập **tên hiển thị** và **mã 4 số** của người nhận ở hai ô riêng (ví dụ `Nguyễn Minh Anh` và `#0123`), bấm **Tìm**, rồi **Kết bạn**. Không dùng tên đăng nhập để phân biệt các người trùng tên hiển thị. Mục **Lời mời** có **Chấp nhận**, **Từ chối** và **Hủy** lời mời đã gửi. Có thể tìm và gửi lời mời khi người nhận ngoại tuyến; họ sẽ thấy lời mời khi đăng nhập lại. Chỉ sau khi được chấp nhận, hai bên mới nhắn riêng được.
- Trong trò chuyện của phòng, bấm avatar người đang tham gia để mở hồ sơ rồi chọn **Kết bạn / Nhắn tin**. Phòng ẩn danh vẫn che danh tính và không mở hồ sơ/kết bạn từ avatar ẩn danh.
- Chọn **Nhắn tin** để mở khung chat ở góc dưới bên phải. Enter gửi tin, Shift+Enter xuống dòng; tối đa 2000 ký tự/tin. **Xem tin trước** tải thêm lịch sử. Có thể nhắn với bạn bè ở ngoài phòng hoặc gửi khi họ ngoại tuyến; tin nhắn được lưu và người nhận thấy khi đăng nhập lại.
- Khung chat chỉ mở khi chọn **Nhắn tin** từ mục **Bạn bè** trong biểu tượng người. Nút **−** hoặc **×** ẩn toàn bộ khung; mở lại bằng biểu tượng người → chọn bạn → **Nhắn tin**. Không có nút Trò chuyện thường trực ở góc dưới; vị trí này hiển thị **Đăng xuất**. Tin mới không tự bật khung; biểu tượng người hiển thị tổng số lời mời đến và tin chưa đọc. Đọc tin trong khung chat sẽ cập nhật số chưa đọc.
- Lời mời, danh sách bạn bè và tin nhắn lưu trong MySQL trên Server. Gửi lại sau lỗi mạng dùng cùng mã tin để tránh trùng. Mất kết nối sẽ khóa thao tác gửi; đổi tài khoản/đăng xuất xóa dữ liệu riêng và bản nháp trên Client. Server kiểm tra người gửi và quan hệ bạn bè cho mọi yêu cầu; tin riêng không phát vào phòng hoặc tới người dùng khác.

## Đấu giá qua cùng Wi-Fi

1. Kết nối các máy vào cùng một mạng LAN/Wi-Fi tin cậy, không dùng mạng khách có cô lập thiết bị.
2. Bật **MySQL** trong XAMPP trên máy chủ (mặc định cổng 3306), rồi mở Server và đợi trạng thái **Đang hoạt động**. Apache không bắt buộc.
3. Chọn địa chỉ Wi-Fi/LAN phù hợp nếu máy có nhiều card mạng. Bấm **Sao chép mã** và gửi mã cho các máy khác. Cũng có thể dùng **Sao chép địa chỉ**.
4. Mở Client, dán mã vào **Mã kết nối**, bấm **Kết nối**. Mã `AUC-...` chỉ mã hóa IP và cổng hiện tại, không phải mật khẩu truy cập.
5. Mỗi người đăng ký một tài khoản riêng rồi đăng nhập.
6. Người bán vào **Sản phẩm bán**, thêm sản phẩm và ảnh, rồi **Tạo phòng đấu giá**. Chọn **Bắt đầu ngay** hoặc **Hẹn giờ bắt đầu** với ngày, giờ và phút trên máy. Phòng hẹn giờ nằm ở **Sắp diễn ra** đến giờ bắt đầu, sau đó tự sang **Đang đấu giá**; thời lượng tính từ lúc bắt đầu đã chọn.
7. Người mua nạp ví demo và chờ server duyệt như hướng dẫn tiếp theo, rồi chọn phòng, bấm **Vào phòng**, nhập giá và bấm **Đặt giá**. Phòng riêng tư yêu cầu mật khẩu phòng.
8. Giữ Server và mạng hoạt động trong suốt phiên. Một người có thể mở Client ngay trên máy Server để tham gia.

Ứng dụng bắt đầu với dữ liệu trống, không cài sẵn tài khoản hoặc phiên đấu giá mẫu.

Trên sảnh, **Phiên nổi bật** xếp theo điểm tổng hợp: số tài khoản đang trong phòng + số lượt ra giá hợp lệ của cả phiên + số lượt vào đã ghi nhận. Mỗi người đang ở trong phòng hoặc mỗi lượt vào/ra giá tính 1 điểm; rời phòng không cộng điểm lượt vào, đồng thời giảm số người hiện có. Phòng trống không có điểm từ số người. Nhiều cửa sổ đồng thời của cùng tài khoản không làm tăng số người hay sinh thêm lượt vào. Chỉ lấy tối đa 5 phòng công khai đang diễn ra và không chặn người xem, cập nhật mỗi 5 giây. Bằng điểm thì ưu tiên đông người hơn, kết thúc sớm hơn rồi mã phòng nhỏ hơn. Thẻ dùng ảnh đầu tiên và giữ chức năng vào phòng.

**Sản phẩm bán** nằm ngay trong cửa sổ chính, giữ thanh điều hướng và hiển thị danh sách dạng thẻ bo góc, khung chi tiết bên phải. Chỉ hiển thị sản phẩm do tài khoản đăng bán; bỏ sản phẩm đã mua và bộ lọc **Đã sở hữu**. Trạng thái gồm **Đã bán** màu đỏ khi phiên đã chốt với người thắng, **Đang đấu giá** màu xanh khi còn phòng mở (kể cả hẹn giờ), hoặc **Chưa giao dịch** màu đen. Phiên hủy/kết thúc không có người thắng không tính là đã bán. Bộ lọc, danh sách, lưới và chi tiết dùng chung trạng thái từ Server; trang tự cập nhật mỗi 5 giây và dừng khi chuyển mục. Thanh dưới chỉ còn **Thêm sản phẩm** và **Làm mới**; **Sửa**, **Ảnh / video**, **Xóa sản phẩm** nằm trong dấu ba chấm của từng sản phẩm ở cả danh sách và lưới.

Bấm **chuông thông báo** phía trên để mở **Thông báo của tôi**; mục thông báo đã được bỏ khỏi thanh bên. Chuông lớn hơn và có huy hiệu đỏ hiển thị số chưa đọc (trên 99 hiện **99+**), tự kiểm tra mỗi 5 giây và cập nhật ngay khi tải lại danh sách sau thao tác đọc/xóa. Huy hiệu ẩn khi không còn tin chưa đọc. Chọn **⋮ → Xóa** để xóa thông báo của mình. Số chưa đọc và trang đầu được tải lại; biên nhận, kết quả đấu giá và dữ liệu ví vẫn giữ nguyên. Server xác định chủ thông báo từ phiên đăng nhập.

Nếu chỉ xem phòng rồi rời đi mà chưa có lượt đặt giá được chấp nhận, bạn không nhận thông báo cá nhân từ phòng đó khi giá thay đổi hoặc phiên kết thúc. Nếu đã đặt giá, lần giá thay đổi đầu tiên sau khi rời phòng sẽ tạo đúng một tin **Đã có giá thay đổi**, kèm mã phòng và nút **Quay lại phòng** để vào trực tiếp. Đọc/xóa tin hoặc khởi động lại Server không làm gửi lại trong cùng lần rời phòng; quay lại rồi rời phòng lần nữa thì có thể nhận một tin mới. Phòng riêng vẫn kiểm tra quyền/mật khẩu, phòng đã đóng hoặc tài khoản bị chặn không thể vào lại bằng liên kết. Thông báo kết quả cuối phiên của người đã đặt giá vẫn được giữ riêng. Cần mở lại cả Server và Client sau cập nhật này.

**Kết quả phiên của tôi** mở ngay trong giao diện chính, có banner, ảnh sản phẩm, kết quả, vai trò và thời gian kết thúc. Lọc theo phiên đã tham gia/đã thắng/đã tổ chức, trạng thái, tên hoặc mã phiên; dùng phân trang để xem thêm. Nút xem và dấu **⋮** mở chi tiết phiên ở bên phải, dấu **×** đóng bảng chi tiết. Chi tiết hiển thị kết quả, sản phẩm, thuế và tiền thực nhận; lịch sử ví xem riêng tại mục **Giao dịch**.

## Ví, QR demo và duyệt nạp/rút

- Mọi tài khoản có số dư ban đầu **0 đồng**. Trên Client, mở **Ví & nạp/rút tiền** để xem số dư, số dư khả dụng, tiền trong két và các tab **Nạp tiền**, **Rút tiền**, **Két sắt**.
- Mở **Giao dịch** trên thanh bên để xem sổ cộng/trừ tiền riêng: nạp/rút đã duyệt, mua/bán và chuyển ví/két. Số tiền tăng màu xanh, số tiền giảm màu đỏ; mỗi dòng có số dư ví và tiền trong két sau giao dịch. Chọn loại giao dịch sẽ lọc ngay và trở về trang đầu; có thêm bộ lọc mã phiên, khoảng ngày và phân trang. Các dòng giữ/hoàn tiền giữ chỗ và phiên cũ cần kiểm tra thanh toán không hiển thị trong mục này; Server vẫn lưu để đối soát. Yêu cầu nạp/rút đang chờ hoặc bị từ chối vẫn nằm trong tab tương ứng của Ví.
- **Két sắt:** lần chuyển đầu tiên nhập số tiền từ khả dụng, tạo mã PIN **6 chữ số** và nhập lại PIN; tạo PIN và chuyển tiền cùng thành công hoặc cùng hủy. Những lần sau nhập PIN một lần để mở két và chuyển vào; mỗi lần **Rút từ két về ví** cần nhập PIN xác nhận thêm một lần. Tiền trong két không được dùng trực tiếp để đặt giá hoặc duyệt rút tiền từ ví. Chuyển ví/két xử lý ngay, không cần duyệt, không thu phí; nhập số tiền lớn hơn 0 với tối đa hai chữ số thập phân. Rời tab/trang hoặc đăng xuất sẽ khóa két; quyền mở két cũng hết hạn sau 15 phút không hoạt động. Sai PIN 5 lần thì chờ 60 giây. PIN không lưu dạng rõ và không hiển thị trên Server.
- Nhập từ **1.000 đến 1.000.000.000 đồng**, tối đa hai chữ số thập phân, bấm **Hiện QR demo**. Mã QR có thể quét, nhưng chỉ chứa mã yêu cầu và số tiền mô phỏng, không có thông tin chuyển khoản thật. Bấm **Đã nhập** để gửi yêu cầu.
- Client chỉ tạo yêu cầu **Chờ duyệt**, không tự cộng tiền. Mỗi tài khoản chỉ có một yêu cầu đang chờ; gửi lại cùng mã không tạo lần nạp thứ hai.
- Trên Server, mở **Tài khoản & ví → Duyệt nạp demo**, chọn yêu cầu, bấm **Chấp nhận & cộng tiền** hoặc **Từ chối** rồi xác nhận. Chấp nhận cộng đúng số tiền một lần; từ chối không thay đổi số dư. Client tự cập nhật sau vài giây.
- Để rút tiền mô phỏng, trên Client chọn **Rút tiền**, nhập từ **1.000 đến 1.000.000.000 đồng**, tối đa hai chữ số thập phân, không vượt khả dụng, rồi bấm **Gửi yêu cầu rút**. Chỉ có một yêu cầu rút đang chờ; gửi yêu cầu chưa trừ tiền. Trên Server chọn **Tài khoản & ví → Duyệt rút tiền**, chọn yêu cầu và bấm **Duyệt & trừ tiền** hoặc **Từ chối**. Khi duyệt phải còn đủ khả dụng; chỉ trừ đúng số tiền một lần, không lấy tiền đang giữ chỗ. Từ chối không trừ tiền và hiển thị lý do. Client tự cập nhật trạng thái, số dư, thông báo và giao dịch **Rút tiền đã duyệt**. Không có chuyển khoản ngân hàng thật.
- Tab **Tài khoản & giao dịch** trên Server cho phép xem tên đăng nhập, họ tên, email, điện thoại, ngày tạo/đăng nhập, số dư, giữ chỗ, lịch sử nạp và giao dịch của từng tài khoản. Không hiển thị mật khẩu.
- Khi đặt giá hợp lệ, server giữ chỗ **toàn bộ mức giá** của người dẫn đầu. Số dư khả dụng bằng số dư trừ tổng giữ chỗ ở mọi phòng; không thể dùng cùng tiền dẫn đầu nhiều phòng. Bị vượt giá sẽ được giải phóng giữ chỗ; rời phòng, đăng xuất hoặc mất mạng không hủy cam kết mua.
- Khi hết phiên có người thắng, Server trừ đúng giá thắng khỏi ví người mua, giải phóng giữ chỗ và cộng tiền thực nhận cho người bán trong cùng giao dịch. Thuế trừ vào người bán: đến đúng 1 tỷ đồng là 10%, trên 1 tỷ đồng là 15%; theo thứ hạng tổng doanh thu trước khi chốt phiên, Top 1 giảm 5 điểm %, Top 2 giảm 4, Top 3 giảm 3, Top 4–5 giảm 2, Top 6–10 giảm 1. Thuế làm tròn đến hai chữ số thập phân. Giá chốt, thứ hạng, thuế suất, mức giảm và số thuế được lưu trong `wallet_settlements`; giao dịch `SALE` ghi tiền bán thực nhận. Đóng lại phiên không thanh toán hay thu thuế lần hai; phiên đã thanh toán giữ nguyên số tiền lịch sử. Không có người thắng thì không chuyển tiền hoặc thu thuế; bản demo có rút tiền mô phỏng qua Server duyệt và chưa có giao hàng.

## Phòng hết giờ và dữ liệu cũ

- Phòng kết thúc bị ẩn ngay khỏi danh sách tìm kiếm/sàn. Người đang ở phòng vẫn xem được kết quả, không thể đặt giá thêm; sau đếm ngược **60 giây** sẽ tự trở về sàn. Lưu trữ phòng không xóa lịch sử, kết quả hoặc giao dịch.
- Lượt chuyển sang chỉ dùng XAMPP đã xóa dữ liệu cũ theo yêu cầu và tạo lại `btl_16` trống. Không nhập lại H2 hoặc kho media cũ; cần đăng ký tài khoản mới. Các lần khởi động sau chỉ tạo/cập nhật cấu trúc, **không tự xóa dữ liệu**.
- Phiên cũ còn mở, đã có người dẫn đầu từ trước khi có ví và không có khoản giữ chỗ: khi kết thúc sẽ ghi **Phiên cũ: cần kiểm tra thanh toán**, không tạo tiền cho người bán hoặc tự trừ âm ví người mua. Phiên đã đóng trước nâng cấp giữ nguyên lịch sử, không tự thanh toán bù. Nếu có lượt giá mới sau nâng cấp, lượt đó phải được giữ chỗ và thanh toán theo cơ chế ví. Bản demo chưa có nút đối soát các phiên cũ; nên tạo phiên mới để trình diễn nạp và thanh toán.
- Số dư, yêu cầu chờ duyệt, giữ chỗ và sổ giao dịch đều nằm trên máy Server và tồn tại sau restart. Sao lưu toàn bộ dữ liệu như hướng dẫn bên dưới.

## Luật đấu giá và thông báo tạo phòng

**Luật đấu giá** mở ngay trong cửa sổ chính: banner vàng nhạt, cột quy trình gồm đủ bảy bước và ví dụ minh họa, cột bên phải gồm **Nguyên tắc chung**, **Các hành vi bị cấm**, **Lưu ý quan trọng**. Cuộn để đọc tiếp; phần chi tiết doanh thu, xếp hạng và thanh toán mở rộng tại bước 4. Chuyển mục ở thanh bên để quay lại phiên đang tham gia.

Nếu bấm **Tạo phòng đấu giá** khi chưa có sản phẩm đủ điều kiện, thông báo minh họa màu kem/vàng xuất hiện giữa màn hình, nền phía sau tối và mờ. Bấm **Thử lại ngay** để tải lại danh sách sản phẩm và mở biểu mẫu tạo phòng nếu có sản phẩm hợp lệ; bấm **×** để đóng. Các quy tắc về sản phẩm đã bán, phiên đang mở và số lần gửi yêu cầu vẫn giữ nguyên.

## Xóa sản phẩm

Đăng nhập tài khoản người bán, vào **Sản phẩm bán**, mở menu ba chấm của sản phẩm rồi bấm **Xóa sản phẩm**. Hộp xác nhận nêu rõ thao tác không thể khôi phục. Có thể xóa sản phẩm đang sử dụng hoặc đã ngừng; danh sách tự cập nhật sau khi Server xử lý. Cột, bộ lọc, nút và khung thông tin khôi phục đã được bỏ. Dấu **×** phía trên bên phải đưa về **Sàn đấu giá**, không còn nút Đóng phía dưới.

Chỉ chủ sở hữu đã đăng nhập được xóa. Sản phẩm còn phiên đang mở hoặc đã hẹn giờ phải kết thúc hoặc hủy phiên hợp lệ trước. Sản phẩm chưa từng có phiên được xóa cùng toàn bộ metadata và dữ liệu ảnh/video trong một giao dịch. Sản phẩm đã có lịch sử được xóa khỏi danh sách quản lý, không thể khôi phục, sửa hay mở phiên mới; bản ghi và ảnh/video cần cho lịch sử, biên nhận, giao dịch và doanh thu vẫn được giữ trong DB. Dữ liệu đánh dấu xóa nằm trong `deleted_products`; khởi động lại không đưa sản phẩm trở về danh sách. Xóa và tạo phiên dùng cùng khóa sản phẩm để không có phiên mở trên sản phẩm vừa xóa.

## Windows Firewall

Server mặc định lắng nghe TCP `8890` cho WebSocket và `8891` cho ảnh/video. Khi Windows hỏi quyền truy cập mạng, chỉ cho phép trên mạng **Private** tin cậy. Không cần mở cổng MySQL, không tắt firewall.

Nếu Client chưa kết nối được, từ máy Client chạy PowerShell:

```powershell
Test-NetConnection IP_MAY_SERVER -Port 8890
Test-NetConnection IP_MAY_SERVER -Port 8891
```

Nếu kết quả là `False`, kiểm tra IP, profile mạng, firewall và chế độ cô lập thiết bị của Wi-Fi. Với mạng tin cậy đã đặt Private, có thể mở PowerShell bằng quyền Administrator trên máy Server và chạy:

```powershell
New-NetFirewallRule -DisplayName "BTL16 Auction LAN" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8890,8891 -Profile Private -RemoteAddress LocalSubnet
```

Không đổi mạng công cộng sang Private nếu không tin cậy mạng đó. Nếu DHCP đổi IP sau khi tắt máy hoặc đổi Wi-Fi, dùng lại mã mới hiện trên Server; không tái sử dụng mã cũ.

## Dữ liệu

- Bản source và EXE đều chỉ dùng **MySQL trong XAMPP trên máy Server**, mặc định `btl_16` ở `127.0.0.1:3306`. Nếu MySQL chưa bật hoặc cấu hình sai, Server báo lỗi, không tạo H2 thay thế.
- Tài khoản, ví, nạp tiền, sản phẩm, ảnh/video, lịch sử và kết quả đều nằm trong DB. `product_media` lưu metadata; `media_content` lưu dữ liệu nhị phân thành các đoạn tối đa 256 KiB. Metadata và ảnh/video được lưu trong cùng giao dịch, lỗi thì rollback toàn bộ.
- Không tạo thư mục media tạm/cache trên đĩa. Upload và xử lý dùng RAM; khi lưu thành công toàn bộ nội dung vào MySQL. Xem ảnh/video stream trực tiếp từ các đoạn BLOB, không khôi phục ra file. Client không có bản sao database.
- Sao lưu **toàn bộ database MySQL**, bao gồm `media_content` và các bảng ví. Không cần sao lưu cache. Xem [Lưu trữ MySQL/XAMPP](25_MYSQL_XAMPP_ONLY.md).
- Bản đóng gói lấy cấu hình từ `config/server-desktop.properties`; cấu hình MySQL hợp lệ đã có được giữ khi đóng gói lại, cấu hình H2 cũ được thay thế. Thời gian kết thúc phiên vẫn là thời gian thực; tắt Server không đóng băng đồng hồ đấu giá.
- Các thư mục `dist/BTL16-Auction-Server` và `dist/BTL16-Auction-Client` là app-image để kiểm tra hoặc chạy trực tiếp. Nếu dùng EXE bên trong các thư mục này, phải giữ nguyên cả thư mục. EXE trực tiếp ở gốc `dist` đã chứa toàn bộ app-image.

## Đóng gói lại

Trên Windows có JDK 17+ với `jpackage`, Maven, Node.js và .NET Framework compiler:

```powershell
. ./scripts/activate-env.ps1
powershell -ExecutionPolicy Bypass -File tools/prepare-media.ps1
npm run dist
```

Lệnh tạo hai giao diện JavaFX, runtime riêng, hai EXE tự bung ở gốc `dist` và bản app-image tương ứng. Nếu build trên máy khác không có bộ JDK trong script, đặt `JAVA_HOME` và `PATH` tới JDK phù hợp trước khi chạy `npm run dist`.

## Kiểm thử

De cap nhat DONG THOI Windows EXE va macOS portable sau khi sua source, dung
`npm run dist:all` tren Windows, roi `npm run test:bundles` de kiem tra lai.
Chi chay source/test-compile khong cap nhat EXE. Xem `34_CAP_NHAT_BAN_PHAN_PHOI.md`
ve BUILD_INFO.json, kien truc Mac, shortcut/cache cu va cach khoi dong lai an toan.

Huong dan nang cap phong/chat/gia/ket qua/bien nhan/vi va thong bao:
`26_LIVE_AUCTION_ROOM.md` den `31_HOAN_THIEN_CHUNG.md`. Kich ban demo va checklist
LAN/mat mang that: `32_DEMO_VA_NGHIEM_THU.md`. Ket qua test/build thuc te:
`33_KET_QUA_NGHIEM_THU.md` va `DEMO_UPGRADE_STATE.md`. Dot cap nhat 2026-10-26
gom hai EXE Windows va bon goi macOS portable arm64/x64 trong dist, cung source/JAR.
Ket qua build va kiem tra goi: `dist/VERIFICATION.md`, `dist/BUILD_INFO.json`.

Client co hieu ung am thanh nhe cho nut bam, chat, bid duoc chap nhan, vuot gia
va dong phien. Menu Am thanh o man dang nhap/sidebar cho tat/bat, am luong va
nghe thu; tuy chon duoc luu tren may. Chi phat xac nhan khi Server da tra loi,
khong beep theo tung giay va khong them am thanh cho Server. Xem `35_AM_THANH_CLIENT.md`.

```powershell
scripts/run-self-tests.cmd
npm run test:packaging
java -cp "target/classes;target/test-classes;target/dependency/*" vn.ptit.btl16.selftest.DesktopUiSelfTest
powershell -ExecutionPolicy Bypass -File tools/smoke-windows-bundles.ps1
```

Self-test desktop dùng database tạm riêng và ba client WebSocket thật để kiểm tra mã kết nối, tài khoản, phòng riêng tư, ảnh, cạnh tranh đặt giá, tự kết thúc và dữ liệu sau restart. UI self-test tạo ảnh trong `target/desktop-preview`, không thêm dữ liệu mẫu vào database sử dụng thật. Với bản MySQL-only, helper `smoke-windows-bundles.ps1` chuyển sang kiểm thử JAR/runtime đóng gói và các database MySQL `btl16_verify_*`. Không dùng `btl16.dataDir` để cách ly MySQL, không mở EXE production để tạo dữ liệu test trong `btl_16`; kiểm tra payload EXE bằng `tools/verify-media-bundles.ps1`.

Chi tiết chức năng ảnh/video, định dạng đã thử, giới hạn, chuyển ảnh cũ và kiểm thử cách ly: [Ảnh và video sản phẩm](23_ANH_VIDEO_SAN_PHAM.md). Lượt nâng cấp media không chạy smoke-test EXE, production hay setup/reset `btl_16`; các lệnh smoke-test trước đây không thuộc lượt xác minh này.

Đây là bộ ứng dụng học tập trong mạng LAN tin cậy, chưa phải dịch vụ đấu giá thương mại: WebSocket/HTTP LAN không mã hóa TLS, tiền và QR hoàn toàn mô phỏng, chưa tích hợp thanh toán thật hoặc xác thực danh tính. Không mở trực tiếp cổng ứng dụng ra Internet và không dùng mật khẩu quan trọng trên các hệ thống khác.
