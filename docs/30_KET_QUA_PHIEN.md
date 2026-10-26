# CUM 4 - Ket qua phien va bien nhan ca nhan (2026-10-25)

## Nguon xac nhan va tinh nguyen tu

- Khong thay doi luat bid, gia han, huy hay co che thanh toan mo phong.
  Nguoi dang dan dau, dong ho ve 0 va timeout KHONG phai nguoi thang.
- ENDED chi tao bien nhan tu auction_results da duoc ghi boi server;
  CANCELLED duoc ghi cung transaction huy. outcome: WON, NO_BIDS, CANCELLED.
  Khong bid/huy: finalPrice rong, khong bien gia khoi diem thanh gia ban.
- closeAuction khoa hang auctions, settle vi, ghi auction_results, event ENDED,
  summary va cac personal receipt trong CUNG transaction. Huy cung luu event
  CANCELLED + receipt trong transaction. Loi ghi receipt rollback ca thanh toan,
  hold, result va event. Dong thoi/thu lai chi mot OPEN -> ENDED duoc commit;
  unique settlement/reference hien co tiep tuc chong thanh toan hai lan.
- Tai su dung RoomProgressStore.stats(Connection, room) cua CUM 2: COUNT bids,
  COUNT DISTINCT bidder va extension events, khong dung 50 bid client dang nap.
  extensionsComplete van phan biet nhat ky cu thieu CREATED; khong tu suy ra
  cac lan gia han truoc nang cap.

## Du lieu toi thieu va quyen

- auction_receipt_summaries dong bang: ma phien, ma/ten san pham, thoi diem
  chot, outcome, ten hien thi nguoi thang, gia chot nullable, tong bid, so nguoi
  bid, so lan gia han + coverage va co an danh. Khong sao chep chat, danh sach
  nguoi tham gia, avatar, ho so, mat khau phong hay account id nguoi thang.
- auction_personal_receipts: PK (auction_id, user_id), personal_role:
  HOST, WINNER, NOT_WON, WATCHED. Host + bidder + tai khoan co JOINED da luu
  truoc luc chot co bien nhan, ke ca da roi phong/disconnect/bi kick.
  Viec xem danh sach san hay duoc cap alias KHONG tao quyen bien nhan.
- GET_PERSONAL_RECEIPT va MY_AUCTION_RECEIPTS require login; user lay tu session
  hien tai, khong tin userId client. Kiem tra lai session truoc reply.
  Khong co receipt (ca phien chua chot, khong ton tai, hoac nguoi ngoai):
  AUCTION_FORBIDDEN cung thong diep, khong co API do tai khoan nguoi thang.
- Quyen receipt tach khoi runtime/room/history. Co receipt KHONG grant private
  room, khong join/resync, chat, roster, ho so hay lich su bid/event cua phong.
  Cac API nay van requireHistoryReadAccess/requireReadAccess, private grant
  trong session hien tai va blocked check nhu CUM 2/3. Tai khoan da bi kick
  van duoc xem BAN TOM TAT CA NHAN cua chinh ho, khong duoc mo lai phong.
- Phong an danh dong bang `Khach #ordinal` tu room_participants, cung alias
  da dung trong phong; khong tra winnerId/username/profile URL. Neu phien cu
  thieu alias, cap ordinal room-local duoi khoa auctions trong transaction.
  Response la whitelist rieng, khong di qua projector can runtime cua phong.

## Luu tru va migration

- Giu chinh sach hien hanh: archive/closedVisibilitySeconds chi an runtime
  khoi san va bo membership, KHONG xoa auctions/bids/results/room_events/chat.
  Khong doi TTL, khong keo dai hay mo rong quyen luu/doc chat/ho so.
- Pham vi mo rong chi HAI bang receipt toi thieu. Cung vong doi ban ghi phien:
  FK auction ON DELETE CASCADE; personal receipt cung cascade khi xoa user.
  Khong them bo suu tap client/local cache chua thong tin phong rieng.
- DatabaseSchema.initialize goi AuctionReceiptStore.initialize sau CUM 2/3:
  CREATE IF NOT EXISTS, roi backfill idempotent cac phien da dong con du lieu
  xac nhan (ENDED phai co auction_results; CANCELLED theo trang thai DB).
  Backfill chi summary/role/alias, KHONG settle hay sua ledger. WATCHED chi
  khoi phuc khi co JOINED da luu; khong the suy ra nguoi chi xem truoc CUM 2.
  Summary da co khong tinh lai khi runtime/event thay doi.
- DB moi: sql/00_schema.sql. DB cu db.autoInitialize=false: chay RIENG
  sql/04_auction_receipts.sql truoc khi chay ban moi (khong DROP/reset/seed).
  Script chi tao bang cho cac lan chot tiep theo. Neu can backfill phien cu,
  chay mot lan DatabaseSchema.initialize theo quy trinh nang cap hien co
  (db.autoInitialize=true mot lan, sau do tra lai false). Sao luu DB truoc
  migration. Khong chay toan bo script schema/reset tren production.

## Vi va ledger ca nhan

- Moi lan mo receipt doc lai wallet_settlements, wallet_holds va wallet_entries
  cua CHINH session user. Khong dung gia chot de tu gan trang thai da tra tien.
  PAID can settlement PAID dung amount VA PURCHASE/SALE dung amount/delta.
  LEGACY_REVIEW, thieu/sai ledger, settlement khong khop hoac con hold sau chot:
  REVIEW_REQUIRED. Tong reserved_delta tren TOAN BO ledger phien phai ve 0,
  khong chi 100 dong dang hien thi. Loser thay RELEASED chi khi co RELEASE
  that va da can bang; watcher NONE.
- Match ledger bang cac reference that: BUY/SELL/LEGACY va BID:sequence:
  RESERVE/RELEASE lien ket bids cua dung phien. RELEASE co the thuoc user
  truoc do, khong loc nham chi sequence cua cac bid do user tu dat.
  Khong tra buyer_id/seller_id, so du hay giao dich nguoi khac.
- Receipt lien ket den bang giao dich rieng cua phien (toi da 100 gan nhat,
  entriesHasMore ro rang) va nut mo Vi cua toi de tra cuu so giao dich rieng.
  Khong thay doi quyen GET_WALLET(auctionId) hay cap quyen doc vi theo receipt.
  Khong them giao hang, hoan tien that, tranh chap hay so du gia.

## API va UI

- GET_PERSONAL_RECEIPT {auctionId} -> PERSONAL_RECEIPT_RESULT:
  auctionId, productCode, productName, endedAt, outcome, winnerDisplay,
  finalPrice, totalBids, uniqueBidders, extensionCount, extensionsComplete,
  anonymousGuests, personalRole, walletStatus, heldAmount, entryCount,
  entriesHasMore, entry.N.{id,type,amount,reservedDelta,reference,time}.
- MY_AUCTION_RECEIPTS {scope: PARTICIPATED|WON|HOSTED, outcome:
  ALL|WON|NO_BIDS|CANCELLED, search, beforeId, limit:1..100} ->
  MY_AUCTION_RECEIPTS_RESULT: count, hasMore, nextBeforeId, receipt.N.summary.
  PARTICIPATED gom watcher/bidder, HOSTED rieng chu phong. Keyset auctionId
  giam dan, mac dinh UI 30 hang; tim theo ten/ma san pham/ma phien, search<=100.
- Sidebar: Ket qua phien cua toi; ba nhom phien, loc trang thai/tim kiem,
  trang dau/trang tiep va lam moi. Chon hang de mo lai receipt sau roi phong,
  reconnect, session moi hoac app/server restart. Khong can join lai phong.
- Dang o phong khi server ket thuc/huy/archive: mo receipt, nut xem lai tren
  phan san pham khi phien da dong. Cua so receipt doc lap nen timer ve san
  khong lam mat ket qua. Resync snapshot CLOSED cung mo receipt.
- Mat ket noi/loi/khong co quyen: khong show ket qua optimistic; cho server,
  thu lai hoac Lam moi khi ket noi lai. Generation chan callback phien/room
  cu; dong va xoa cua so khi session thay doi/logout.

## Kiem thu

- AuctionReceiptSelfTest: H2 file tam, migration lap/backfill/repository va
  server restart, ket qua sau archive, leader chua chot khong co receipt;
  4 role, day du thong ke CUM 2; no bid/cancel, legacy REVIEW_REQUIRED;
  concurrent close + retry khong duplicate PURCHASE/SALE, fault receipt FK
  rollback ca hold/payment/result; ledger sai amount khong hien PAID.
- TCP server that: session spoof, anonymous raw payload, nguoi ngoai/khong
  login, private history/chat khong duoc mo bang receipt; disconnect luc
  timer chot, reconnect/resume va khoi phuc result/ledger; fresh login sau
  server restart. User ID cua receipt luon lay tu session.
- AuctionReceiptUiSelfTest: pending khong nhan thang, 4 role/no bid/cancel,
  REVIEW_REQUIRED, link own ledger/wallet, offline/denial/retry, callback cu,
  logout/session guard, mo lai cua so; anh target/auction-receipt.png.
- Xem docs/DEMO_UPGRADE_STATE.md cho ket qua lenh da chay va gioi han con lai.
