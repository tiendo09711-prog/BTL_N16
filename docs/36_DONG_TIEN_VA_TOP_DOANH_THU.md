# Dòng tiền, phí sàn và Top doanh thu

Ngày kiểm tra: 30/10/2026. Phạm vi: source Server/Client, dữ liệu thanh toán và giao diện JavaFX.

## Các thay đổi

1. Server có mục **Quản lý dòng tiền**: tổng phí sàn đã thu toàn bộ, phí thu trong kỳ, tổng giá chốt, tiền người bán nhận, số phiên cần đối soát và danh sách giao dịch. Có lọc kỳ, làm mới, tự cập nhật và phân trang 50 dòng.
2. Phí sàn được ghi vào `platform_fee_entries` trong cùng giao dịch với thanh toán, kết quả và biên nhận. Ghi sổ lỗi thì toàn bộ giao dịch rollback. Mỗi phiên chỉ thu một lần; xem trang, làm mới hoặc khởi động lại không phát sinh thu thêm.
3. Top doanh thu chỉ tính phiên thắng đã thanh toán `PAID`, giá chốt khớp và không ẩn danh. Phiên chưa thanh toán hoặc `REVIEW_REQUIRED` không được cộng doanh thu hay dùng để tạo ưu đãi.
4. Các tên chức năng, hướng dẫn, biên nhận và thông báo dùng **Phí sàn**. Đây là doanh thu phí dịch vụ, không phải lãi ròng; hệ thống không có nghiệp vụ thuế nhà nước.
5. **Top doanh thu** có 5 kỳ: **1 năm, 6 tháng, 3 tháng, 1 tháng, 7 ngày**, mặc định **1 tháng**, hiển thị rõ khoảng ngày. Tháng/năm là mốc lịch tính lùi từ hiện tại theo GMT+7, không phải tháng cố định 30 ngày. Đổi bộ lọc nhanh không để phản hồi cũ ghi đè kết quả mới.
6. Chọn **Phòng ẩn danh** phải xác nhận đúng nội dung: “Nếu mở phòng ẩn danh thì mọi thông tin người bán, người mua đều ẩn, giá trị phòng sẽ không được tính vào top doanh thu”. Chọn Không hoặc đóng hộp thoại thì trở về phòng thường và giữ nguyên bản nháp.
7. Phòng ẩn danh không tạo doanh thu xếp hạng trong bất kỳ kỳ nào, kể cả người bán có cả phiên công khai lẫn phiên ẩn danh. Vẫn thanh toán và thu phí bình thường. Bí danh áp dụng cho cả chủ phòng; quyền quản lý của chủ phòng được giữ nguyên. Biên nhận người mua không lộ hạng/mức giảm phí của người bán; sản phẩm đã mua không lộ tài khoản người bán gốc.

## Quy ước tính tiền

- Người mua trả đúng giá chốt. Người bán nhận giá chốt trừ phí sàn. Tổng hai khoản người bán nhận và sàn thu luôn bằng số tiền người mua trả.
- Giữ biểu phí hiện có: đến đúng 1 tỷ đồng là 10%, trên 1 tỷ đồng là 15%; mức giảm theo thứ hạng vẫn giữ nguyên.
- **Ưu đãi phí luôn lấy thứ hạng 1 tháng ngay trước khi chốt**, độc lập với bộ lọc thống kê đang xem. Phiên chốt đồng thời được tuần tự hóa khi lấy hạng và thanh toán.
- Khi nâng cấp, chỉ chép phí hợp lệ đã lưu của phiên `PAID` vào sổ sàn; không tính lại phí lịch sử, không trừ ví lần nữa.
- Mã tài khoản nội bộ được giữ trong DB phục vụ thanh toán/đối soát; giao diện và dữ liệu trả về cho người khác che danh tính phòng ẩn danh.

## Tích hợp giao diện tab Server

- Trang dòng tiền sử dụng `ServerAdminPage`, hiển thị trong tab **Dòng tiền sàn** của cửa sổ Server chính; chọn **Quản lý dòng tiền** không mở thêm cửa sổ.
- Trang chỉ tự cập nhật khi đang được chọn; chuyển tab dừng bộ đếm và bỏ phản hồi cũ. Kỳ đã chọn và các trang đã tải được giữ khi quay lại.
- Nếu đổi tab trong lúc đang tải, trạng thái tải được vô hiệu hóa và trang tải lại khi mở lại, tránh kẹt nút phân trang. Đã tái hiện lỗi bằng test bất đồng bộ trước khi xác minh bản sửa.
- Tạm dừng Server khóa trang và hiện thông báo ngay trong cửa sổ; khởi động lại kích hoạt và nạp lại dữ liệu từ Server mới. Không sửa công thức phí, điều kiện xếp hạng hoặc transaction thanh toán để thích nghi giao diện.
- Đã chạy lại `CashFlowAndAnonymousUiSelfTest`, `ServerButtonsUiSelfTest`, `RevenueRankingUiSelfTest` sau khi tích hợp: đạt. Test kiểm tra tab thật, chỉ một cửa sổ, đủ 58 giao dịch qua hai trang, giữ kỳ/phân trang, bỏ phản hồi khi tab ẩn, phục hồi sau hủy tải, dừng/khởi động lại Server và xác nhận phòng ẩn danh.
- Kiểm tra lại nghiệp vụ sau tích hợp: `CashFlowRulesSelfTest` trên H2 và MySQL đều đạt; `SellerRevenueSelfTest` trên JDK 25 của dự án đạt, bao gồm đóng phiên đồng thời và lưu phí lịch sử.
- `mvn -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime`: đạt; tạo JAR và dependency hiện tại, chưa đóng gói lại EXE.

## Kiểm thử đã chạy

| Nhóm | Kết quả | Nội dung |
| --- | --- | --- |
| Biên dịch Java 25 | Đạt | Toàn bộ source chính và self-test, output riêng để tránh ảnh hưởng phiên build khác |
| `CashFlowRulesSelfTest` trên H2 | Đạt | 5 kỳ, ranh giới thời gian, tháng nhuận, PAID-only, loại phòng ẩn danh, ưu đãi tháng, bảo toàn tiền |
| `CashFlowRulesSelfTest --mysql` | Đạt | Cùng nghiệp vụ trên MySQL/XAMPP; kiểm tra rollback bằng trigger lỗi, thu đúng một lần, phí lịch sử 3%, làm tròn và phân trang |
| `RevenueRankingUiSelfTest` | Đạt | Tên mới, mặc định tháng, đủ 5 bộ lọc, khoảng ngày và bỏ phản hồi cũ |
| `CashFlowAndAnonymousUiSelfTest` | Đạt | Số liệu sàn, lọc/làm mới, che danh tính, xác nhận Có/Không/đóng, giữ bản nháp |
| UI kết quả/thông báo/tài khoản/kết thúc phiên | Đạt | `AuctionReceiptUiSelfTest`, `PersonalNotificationsUiSelfTest`, `AccountScreensUiSelfTest`, `AuctionEndingUiSelfTest` |
| UI phòng/quản trị/ví/sản phẩm | Đạt | `RoomSocialUiSelfTest`, `ServerButtonsUiSelfTest`, `RoomInteractionUiSelfTest`, `RoomProgressUiSelfTest`, `WalletUiSelfTest`, `CompletionUiSelfTest`, `ProductDeletionUiSelfTest`, `MarketplaceUiSelfTest` |
| Tài liệu luật | Đạt | Xuất lại DOCX và kiểm tra cấu trúc, UTF-8, fingerprint source, checksum |

Test MySQL dùng database tạm có tiền tố `btl16_fee_test_`, tách biệt database ứng dụng `btl_16`; đã dọn database test. Đã xem ảnh chụp giao diện dòng tiền và Top doanh thu ở kích thước thường/thu nhỏ.

## Lưu ý ở bộ kiểm thử rộng

- Đợt chạy `AllSelfTests` có 42 dấu PASS, sau đó `AuctionJourneySelfTest` gặp timeout yêu cầu mạng khi chạy đồng thời với các nhóm UI. Chạy riêng lại trong 149 giây đã đạt cả hai hành trình NAMED và ANONYMOUS: tạo sản phẩm/phòng riêng, chat/hồ sơ/quyền, đặt giá, kết nối lại, hết hạn thật, biên nhận/thanh toán và khởi động lại.
- `RoomSocialUiSelfTest` cũng gặp timeout tạo sản phẩm ở lượt chạy tải cao; chạy riêng lại đã đạt, không sửa timeout để che lỗi.
- `LiveAuctionRoomUiSelfTest` còn lỗi assertion bố cục cột ở chiều rộng 850px. Đã chạy lại bằng `LiveAuctionRoom`, `ClientAuction` và CSS lấy từ HEAD trong thư mục kiểm tra riêng, vẫn lỗi đúng assertion đó. Đây không phải giao diện Top doanh thu hoặc dòng tiền.
- `AuctionButtonsUiSelfTest` giả định nội dung hộp tạo phòng là `GridPane`, trong khi ngay ở bản HEAD đã là `BorderPane`; test dừng do `ClassCastException`. Luồng tạo phòng và xác nhận ẩn danh được kiểm tra bằng test mới.
- Tổng cộng 14/16 nhóm UI đã chạy đạt sau lượt kiểm tra lại; hai lỗi còn lại là hai mục vừa nêu. Không sửa hai vấn đề kiểm thử giao diện ngoài phạm vi này, không khẳng định toàn bộ `npm run test:full` đạt.
- Khi kiểm tra lại tích hợp tab, `SellerRevenueSelfTest` có hai lượt lỗi parser H2 ở `ON DUPLICATE KEY UPDATE` trong đoạn đóng phiên đồng thời trên JDK 26 đang có sẵn trong PATH. Chạy đúng JDK 25 được cấu hình trong dự án đã đạt, không thay đổi nghiệp vụ hoặc bỏ assertion. Chưa khẳng định tương thích bộ self-test H2 với JDK 26. Dùng `. ./scripts/activate-env.ps1` trước các lệnh Maven/Java trong PowerShell.

## Chạy lại và sử dụng

- `npm test`: các self-test logic.
- `npm run test:full`: thêm các self-test JavaFX, bao gồm các lưu ý ở trên.
- `npm run test:mysql`: thêm kiểm thử MySQL; cần bật MySQL trong XAMPP.
- `python -X utf8 tools/export-auction-rules.py --check`: kiểm tra tài liệu Word khớp source.
- Log đợt kiểm tra nằm trong thư mục được ghi ở `$env:TEMP\btl16-fees-validation-current.txt`, thư mục con `logs`.
- Chưa đóng gói lại EXE trong đợt này. Cần build/chạy lại Server và Client để dùng source mới; Server tự nâng cấp sổ thu phí khi khởi động.
