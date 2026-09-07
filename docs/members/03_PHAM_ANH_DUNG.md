# PHẠM ANH DŨNG – Bid, concurrency, private-room và kick safety

Đối chiếu code ngày 07/09/2026. Đây là nhiệm vụ học, giải thích, kiểm thử và bảo trì phần đã triển khai.

## Phạm vi

Chịu trách nhiệm tính đúng đắn khi nhiều client bid, quyền vào phòng và race giữa bid/kick/end. Dùng chung rule cho TCP/WS; không chỉ chặn nút trên UI.

## Thứ tự đọc file

Đường dẫn Java tương đối với src/main/java/vn/ptit/btl16/, trừ src/test và công cụ gốc. Tra toàn bộ file/ownership ở [09](../09_NHIEM_VU_TUNG_FILE.md) và [13](../13_BANG_PHAN_CONG_FILE_THEO_NGUOI.md).

1. server/auction/controller/AuctionController: route join/bid và lấy session từ RequestContext.
2. server/auction/service/BidService, BidOutcome; model/BidRecord, AuctionRuntime.
3. server/auction/repository/BidCommit; JdbcAuctionRepository.commitAcceptedBid và AuctionConflictException.
4. service/RoomManager, RoomMember, KickOutcome, AuctionBroadcastService.
5. AuctionManagementService.joinAuction/kickUser và các kiểm tra block/private password/ownership; session/UserSession, SessionManager.
6. common/util/Money; common/protocol/ErrorCode, MessageType; AuctionWireData.bidUpdate.
7. client/fx/FxClientController.placeBid/joinSelected/kickUser; AuctionApi; ClientAppModel.
8. FullNetworkAuctionSelfTest, AuctionManagementSelfTest, WebSocketUpgradeSelfTest; tools/ConcurrentBidLoadTestMain.

## Luồng phải tự giải thích

**Bid:** lấy session từ server → parse amount → require runtime → lock → recheck membership → chặn host tự bid → OPEN/chưa hết giờ → amount ≥ currentPrice + minBidIncrement → tính anti-sniping → commitAcceptedBid → applyAcceptedBid → unlock → BID_UPDATE/outbid/extension. Exception không được làm kẹt khóa.

**Transaction:** SELECT FOR UPDATE đối chiếu trạng thái/giá kỳ vọng, INSERT bid, UPDATE winner/price/endTime/version, COMMIT; lỗi ROLLBACK. Java lock bảo vệ RAM trong process; DB lock bảo vệ row/transaction.

**Private join:** kiểm tra bị block trước khi cho dùng grant; password đúng thì session được cấp quyền. Membership thuộc connection, grant thuộc session, block lưu DB.

**Kick:** host kiểm tra quyền, ghi block, revoke grant và bỏ membership. Bid phải kiểm tra membership bên trong cùng khóa, không chỉ kiểm tra trước khi chờ khóa.

**Outbid:** thông báo riêng người dẫn đầu cũ qua messaging/session; update room đến cả TCP lẫn WS.

## Biến và bất biến cần nhớ

rawAmount/amount, minimumBid, expectedPrice, previousWinnerId, remainingMillis, oldEndTime/newEndTime, bidSequence, runtime.getLock(), membersByAuction/auctionsByConnection. Không dùng double cho tiền; server tự lấy userId từ session thay vì tin userId client gửi.

## Kiểm thử và bài thực hành

- FullNetworkAuctionSelfTest: bid đồng thời/TCP/outbid/RESYNC.
- AuctionManagementSelfTest: min increment, host self-bid, cancel/end/kick.
- WebSocketUpgradeSelfTest: private password thiếu/sai/đúng, grant resume, kick/block không bypass, TCP bid → WS event.
- Diễn tập bid thấp, chưa join, đúng lúc hết giờ, người bị kick thử bid/rejoin.
- ConcurrentBidLoadTestMain tạo dữ liệu thật, cần PUBLIC room; chỉ chạy DB thử, không gọi load-test là auto-seed.

## Câu hỏi bảo vệ

Tại sao khóa từng auction? Phòng khác vẫn xử lý song song. Tại sao kiểm tra membership trong khóa? Tránh đã kiểm tra xong nhưng bị kick trước commit. Tại sao broadcast ngoài khóa? Không để client chậm giữ khóa nghiệp vụ. Grant có vượt block không? Không; block phải được kiểm tra trước.

## Phối hợp và tiêu chí bàn giao

Đức review bid/end/timer/anti-sniping; Phước review transaction/block persistence; Tiến review session/security; Thuận review UI và regression.

- Tự chỉ được entry point, request, service/repository và event tương ứng, không chỉ nhớ tên lớp.
- Chạy lại test liên quan, ghi đúng kết quả/chỗ chưa thử; sửa protocol/schema thì cập nhật docs chung.
- Môi trường VS Code + XAMPP; xem [05](../05_CACH_CHAY_VSCODE_XAMPP.md). db.port theo mỗi máy; client chỉ cần URL WS.
- DB thật không có seed/account mẫu. Fixture test giữ riêng trong src/test; diễn tập tự đăng ký và tạo dữ liệu.
