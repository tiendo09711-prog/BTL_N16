# MAI TRUNG ĐỨC – Timer, host controls, kết quả và archive

Đối chiếu code ngày 07/09/2026. Đây là nhiệm vụ học, giải thích, kiểm thử và bảo trì phần đã triển khai.

## Phạm vi

Chịu trách nhiệm thời gian authoritative của server, anti-sniping phối hợp BidService, close-once, host extend/end/cancel và vòng đời sau đóng. Không dựa vào countdown client để quyết định kết quả.

## Thứ tự đọc file

Đường dẫn Java tương đối với src/main/java/vn/ptit/btl16/, trừ src/test và công cụ gốc. Tra toàn bộ file/ownership ở [09](../09_NHIEM_VU_TUNG_FILE.md) và [13](../13_BANG_PHAN_CONG_FILE_THEO_NGUOI.md).

1. server/auction/service/AuctionTimerService: start/safeRun/closeIfExpired/broadcastTicks/archiveClosedAuctions/close.
2. AuctionManager.archiveClosedAuctions; RoomManager.removeAuction; model/AuctionRuntime, AuctionSnapshot, AuctionStatus, AuctionResult.
3. AuctionManagementService.extendAuction/endAuction/cancelAuction; repository/ExtendAuctionCommit, CloseAuctionCommit, CancelAuctionCommit.
4. JdbcAuctionRepository: transaction extend/close/cancel; BidService: anti-sniping khi bid hợp lệ.
5. AuctionWireData, AuctionBroadcastService, ServerMessagingService: tick/extended/ended/cancelled/archived.
6. common/config/ServerConfig, common/util/Times; config/server.properties (auction.*).
7. client/fx/FxClientController: refreshClock/remainingText, host controls và handleServerEvent.
8. FullNetworkAuctionSelfTest, AuctionManagementSelfTest và cross-transport WebSocketUpgradeSelfTest.

## Luồng phải tự giải thích

**Tick:** scheduler chạy kiểm tra mặc định 200 ms; phát countdown khoảng 1000 ms với serverNow/endTime. Client chỉ hiển thị thời gian theo server, không tự chốt winner.

**Anti-sniping:** BidService đang giữ khóa, bid hợp lệ trong cửa sổ cuối mặc định 10 giây thì cộng 10 giây vào endTime cũ, lưu transaction cùng bid; event extension phản ánh kết quả thật.

**Đóng đến hạn:** closeIfExpired lấy khóa runtime, kiểm tra OPEN và thời gian → repository close transaction → runtime ENDED/result → unlock → event. Host END dùng cùng cơ chế và bỏ điều kiện phải đến hạn; không tạo hai kết quả khi timer/host tranh nhau.

**Extend/cancel:** host sở hữu phòng mới được gọi. Extend kiểm tra trạng thái/endTime kỳ vọng; cancel chỉ khi chưa có bid và chuyển CANCELLED, không giả làm ENDED.

**Archive:** giữ phòng đóng trong retention mặc định 120 giây → AuctionManager bỏ runtime/đánh dấu tombstone → RoomManager dọn membership → AUCTION_ARCHIVED toàn hệ thống. DB auctions/bids/results không bị xóa.

## Biến và bất biến cần nhớ

checkMillis, tickMillis, lastTickAt, endTime, serverNow, status, endedAt, closedVisibilitySeconds, archivedAuctionIds, extension reason HOST/ANTI_SNIPING. Lock và transaction phải cùng cơ chế với bid, không tạo timer độc lập ở từng client.

## Kiểm thử và bài thực hành

- FullNetworkAuctionSelfTest kiểm tra timer/end/archive và RESYNC sau archive.
- AuctionManagementSelfTest kiểm tra extend/manual end/cancel và quyền host.
- Diễn tập bid sát cuối, host end gần lúc timer hết hạn, không có winner khi không ai bid.
- Chờ retention: list/my-list/dashboard và joined room được dọn, lịch sử DB còn.
- Thay retention trong config thử khi cần test nhanh; trả lại cấu hình mặc định trước khi bàn giao.

## Câu hỏi bảo vệ

Countdown về 0 ở client đã là kết thúc chưa? Chưa, phải đợi trạng thái/event server. Archive có xóa lịch sử không? Không. Tại sao winner nullable? Có thể không có bid. Cancel và end khác gì? Cancel chỉ trước bid, end ghi kết quả phiên.

## Phối hợp và tiêu chí bàn giao

Dũng review race bid/end/kick; Phước review repository/runtime/archive; Tiến review scheduler shutdown/config; Thuận review event/clock/host UI.

- Tự chỉ được entry point, request, service/repository và event tương ứng, không chỉ nhớ tên lớp.
- Chạy lại test liên quan, ghi đúng kết quả/chỗ chưa thử; sửa protocol/schema thì cập nhật docs chung.
- Môi trường VS Code + XAMPP; xem [05](../05_CACH_CHAY_VSCODE_XAMPP.md). db.port theo mỗi máy; client chỉ cần URL WS.
- DB thật không có seed/account mẫu. Fixture test giữ riêng trong src/test; diễn tập tự đăng ký và tạo dữ liệu.
