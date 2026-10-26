# Ket qua nghiem thu demo - 2026-10-25

Luu y phat hanh 2026-10-26: cac ket qua Cum 6 ben duoi la lich su nghiem thu
source. Quy trinh dong goi lai DONG THOI Windows/macOS nam trong docs/34;
trang thai payload thuc te nam trong dist/VERIFICATION.md va BUILD_INFO.json.
Kiem tra payload tren Windows khong thay the nghiem thu GUI tren Mac that.

Trang thai dang cap nhat: Cum 5 da qua kiem thu muc tieu; Cum 6 dang full regression.
KHONG coi tai lieu nay la xac nhan GUI/LAN thu cong. Ket qua cuoi cung se ghi ro
lenh, exit code, log va artifact sau khi runner/dong goi hoan tat.

## Pham vi hoan thanh
- Cum 1: pending gate, bid nhanh, so tien hop le, dan dau/vuot gia, wallet hold,
  reconnect va response cu sau doi phong/session.
- Cum 2: phong live, chart/lich su, stats va room_events server, chat/public profile
  va danh tinh an danh theo phong; reuse trong cac cum sau.
- Cum 3: reply canonical, reactions desired-state + nonce, pin, timed mute nhung bid
  van duoc; quyen host/member, read-only sau dong.
- Cum 4: ket qua xac nhan, snapshot receipt toi thieu cung transaction settle,
  roles rieng, tra cuu/restart va doi chieu ledger, khong mo rong quyen phong rieng.
- Cum 5: loc/sort/paging sanh, review/confirm tao phong, mau mo ta va ly do dong
  bang, preview public profile, loc vi/ly do tu choi nap, inbox ca nhan va local ops log.
- Cum 6 chi them test/tai lieu va sua loi tich hop, khong them luong kinh doanh.

## Ket qua muc tieu da co
- Cum 5: compile PASS; 8/8 nhom trong target/cluster5-final-tests.json.
- Cum 6: Journey NAMED va ANONYMOUS deu PASS sau workaround H2 test,
  target/AuctionJourneySelfTest-cluster6-final.log. Cho dong ho server 1 phut that
  moi case; receipt WINNER/PAID, PURCHASE chi 1, host SALE va restart duoc assert.
- 6/6 nhom muc tieu: CompletionSelfTest, MysqlStorageSelfTest, XamppDatabaseSelfTest,
  AuctionButtonsUiSelfTest, DesktopUiSelfTest, CompletionUiSelfTest;
  target/cluster6-targeted-tests.json va target/*-cluster6-final-targeted.log.
- MySQL test chi tao/xoa DB btl16_verify_* ngau nhien. Khong setup/reset/seed btl_16.

## Loi tim thay / xu ly
- DesktopUiSelfTest fixture dan dau bo qua gate join/snapshot/vi. Doi sang handler
  join that va doi roomReady; assert chat input/bid button trong scene compact.
- Journey loi khong on dinh: DB H2 2.3.232 bi lui du lieu (runtime ENDED, DB OPEN,
  mat room/bid da commit). Giu log fail cu, ap dung AUTO_COMPACT_FILL_RATE=0 chi
  URL embedded test theo workaround h2database/h2database#4247. Khong thay doi
  production MySQL hay quy tac thanh toan de ep test qua. Them assert JDBC durable.
- Test nut tao phong duoc cap nhat cho review/confirm; giu test truoc-xac-nhan
  khong co room va request pending khong tao trung.
- Bo sung recheck session truoc response vi ca nhan va test inbox idle o trang sau:
  logout/het session dong cua so va xoa rows, ke ca khong fetch trang dau.
- Pin phong dang tham gia doc lap bo loc/trang sanh: bid lam thay doi thu tu gia
  khong duoc lam bien mat san pham/gia/ket qua cua phong live. Test voi 75 phong,
  phong dang vao o trang sau va bo loc loai tru phong do.

## Luu tru / quyen / migration
- Docs/30 va docs/31 la chinh sach chuan. Receipt/thong bao khong grant quyen
  chat/roster/profile/lich su phong rieng; owner lay tu session, khong tin userId.
- Anonymous winner la alias dong bang, inbox khong luu ten nguoi vuot gia.
- Bang moi personal_notifications va column wallet_topups.rejection_reason;
  initializer idempotent, khong notification backfill, khong TTL/permission moi
  cho chat. Khong reset hay thay doi ledger cu khi nang cap.

## Chua the xac nhan thu cong
- LAN giua nhieu may that, rut Wi-Fi/cap khi request dang cho va luc ket thuc;
  firewall/AP isolation/DPI projector. Dung checklist docs/32 truoc trinh bay.
- Ban macOS cu khong duoc build lai trong dot Windows nay; khong dung de demo ban moi.
- Canh bao CSS JavaFX lookup -ink/-teal con ton tai tu truoc. Khong tinh canh bao
  thanh test fail khi assertions pass, nhung cung khong tuyen bo da sua CSS.
- Khong co thanh toan/giao hang/hoan tien/tranh chap that. EXE production khong duoc
  mo de tao testdata; payload/runtime tests khong tuong duong nghiem thu EXE GUI thu cong.

## Demo va bang chung
- Kich ban + du lieu chuan bi + checklist LAN: docs/32_DEMO_VA_NGHIEM_THU.md.
- Anh JavaFX: target/desktop-preview/client-auctions.png, client-compact.png,
  target/auction-receipt.png, target/completion-wallet.png,
  target/completion-lobby-small.png. Day la anh test, khong phai anh demo thu cong.
- Ban cu/config da backup trong duong dan target/demo-preservation.json;
  hash config duoc doi chieu lai sau dong goi, khong in password/token vao bao cao.
