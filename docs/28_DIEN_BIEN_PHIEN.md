# CUM 2 - Dien bien phien (2026-10-25)

## Nguon du lieu va quy tac

- Giu nguyen luong dat gia, vi, timeout va gate dong bo cua CUM 1.
- Nhat ky he thong la tab rieng, khong tron voi chat hay log ket noi chung.
  Client chi hien cac ban ghi GET_ROOM_EVENTS; render/resync/polling khong tao log.
- room_events luu CREATED, JOINED, LEFT, BID_ACCEPTED, LEADER_CHANGED,
  EXTENDED_MANUAL, EXTENDED_AUTO, ENDED, CANCELLED. Thoi gian do server cap.
- Bid + hold vi + doi gia + bid/leader/auto-extension events cung transaction JDBC.
  Gia han thu cong, ket thuc/chot ket qua va huy cung transaction voi event.
  Transaction that bai khong de lai bid, hold, thay doi gia hay event nua chung.
- JOINED khi tai khoan co ket noi dau tien vao phong; LEFT khi ket noi cuoi roi,
  disconnect, bi kick hoac phong duoc archive. Join/resync lap khong tao event.
  Nhieu ket noi cung tai khoan khong tao them JOINED; reconnect thuc su co LEFT/JOINED.
- event_id do DB cap, khoa theo auction khi ghi, la thu tu on dinh trong phong.
  Khong dung ServerSequence de lam ID event vi sequence socket tang sau restart.
- Thong ke SQL tren toan bo bids va room_events, khong dua vao cua so client:
  COUNT(*), COUNT(DISTINCT user_id), so event gia han, current_price - start_price.
- watcherConnectionCount (va watcherCount cu) la SO KET NOI dang theo doi,
  khong phai so nguoi duy nhat. presentAccountCount la so tai khoan hien dien;
  uniqueBidders la so tai khoan tung co bid hop le trong toan phien.
- Phien tao sau nang cap co extensionsComplete=true. Phien cu khong co du lieu
  lich su gia han day du: extensionsComplete=false, UI ghi ro chi la so gia han
  ghi nhan tu luc nang cap (hien dau +), KHONG gia mao/backfill lich su cu.
  Tong bid, so tai khoan bid va tang gia van tinh duoc tren toan phien cu.

## API

Moi API duoi day can session hoat dong, kiem tra blocked/private-room grant o server.
Co the doc lai bang auctionId sau khi phien roi danh sach live/bi archive;
quyen private-room van theo session hien tai. Khong mo rong quyen bid/vi cua CUM 1.

### GET_ROOM_EVENTS -> ROOM_EVENTS_RESULT

- Input: auctionId, limit (mac dinh 50, clamp 1..100).
- Trang cu: beforeId exclusive (mac dinh Long.MAX_VALUE), thu tu event_id DESC.
- Reconnect/live: afterId exclusive, thu tu ASC de khong bo qua backlog dai.
  Khong dung dong thoi beforeId tuy chinh va afterId > 0.
- Output: eventCount, event.N.{id,type,time,userId,username,amount,detail},
  hasMore, nextBeforeId, nextAfterId, auctionId, serverNow.
- hasMore duoc lay bang limit + 1; client doc tiep afterId neu backlog con.
  detail chi chua deadline server doi voi event gia han, khong chua danh tinh.

### GET_AUCTION_STATS -> AUCTION_STATS_RESULT

- Input: auctionId.
- Output: totalBids, uniqueBidders, extensionCount, extensionsComplete,
  priceIncrease, watcherConnectionCount, presentAccountCount, auctionId, serverNow.
- Doc duoi room lock; aggregate SQL doc mot snapshot nhat quan.

### GET_BID_HISTORY -> BID_HISTORY_RESULT (mo rong tu API cu)

- Input: auctionId, limit (50, toi da 100), beforeSequence exclusive,
  throughSequence inclusive (server chot MAX(server_sequence) o trang dau), mine.
- mine lay tai khoan tu session, khong tin userId do client gui.
- Giu cac field bid cu; them bid.N.increase, hasMore, throughSequence,
  nextBeforeSequence, mine. increase so voi bid truoc DO trong toan phien,
  khong phai bid truoc trong trang/bo loc; bid dau so voi start_price.
- Giu throughSequence khi doc trang cu de bid dong thoi khong chen/trung trang.
  Bam Moi nhat hoac doi bo loc se tao snapshot trang moi.

### GET_ROOM_SOCIAL (mo rong)

- Moi member them self, leading, hasValidBid, canKick, kickReason.
- kickReason: HOST, HAS_VALID_BID, CLOSED, NOT_HOST hoac rong neu duoc moi ra.
- memberCount la so dong da tra (toi da 200); memberTotal la tong tai khoan
  dang hien dien. UI ghi ro neu danh sach bi gioi han.
- Server van kiem tra lai khi kick va chan ca nguoi tung bid nhung khong con
  dan dau. Khong tin canKick da cache tren client.

## An danh

- Tai su dung RoomSocialService.project va room_participants cua cum truoc.
  Mask ca event.N.userId/username va moi trang bid truoc khi gui tren socket.
- Khach chi nhan ID am theo ordinal rieng cua phong, khong tra ID tai khoan that,
  ke ca response danh cho chu phong, mine-filter, reconnect hay lich su archive.
- Stats chi chua so tong; roster chi dung participantId cong khai va role boolean.
  Khong chen avatar/email/tai khoan/vi vao event hay thong ke.
- Bo loc mine thuc hien truoc khi an danh. Khong suy self-id tu ID that o client.

## UI va gioi han

- Giu cac tab Tro chuyen, Nguoi tham gia, Dien bien gia, Chi tiet; them Nhat ky phien.
- Nhat ky dung ListView ao hoa, cuon rieng; TreeMap + danh sach toi da 200 event,
  chong trung theo ID. Trang cu thay cua so hien tai, tam dung live; bam Theo doi
  moi nhat de quay lai. Khong tich luy vo han cac trang cu hoac cac node.
- Hop lich su la TableView thoi gian server, nguoi dat, so tien, muc tang.
  Moi trang toi da 50 dong, thay trang thay vi noi vo han; co Luot cua toi,
  Trang cu hon, Moi nhat, loading/error/empty va vo hieu callback khi dong hop.
- Nhat ky/stats dong bo khoang 3 giay, chi doc cac su kien server da luu.
  Chuyen phong/logout/disconnect huy generation callback; reconnect tiep cursor.
- Hien thi Chu phong/Ban/Dang dan dau; loading/error/retry cho danh sach khach;
  nut moi ra bi khoa va hien ly do cho nguoi da bid. Avatar cache duoc don theo
  members/chat dang giu. Moi so tien dung WalletUi.money/BigDecimal nhu CUM 1.
- Bo thong bao gia/gia han suy dien tu render snapshot; cac bien dong nghiep vu
  chi co trong nhat ky server. Chart van chi the hien cua so bid da tai.

## Migration va kiem thu

- DatabaseSchema.initialize goi RoomProgressStore.initialize, CREATE TABLE IF NOT
  EXISTS room_events + index (auction_id,event_id). sql/00_schema.sql cung cap DDL
  cho DB moi. Khong reset/seed/backfill DB cu, khong can doi cau truc bids/auctions.
- Neu db.autoInitialize=false, DBA can them bang theo DDL truoc khi chay ban moi;
  khong chay script reset hay toan bo schema tren DB dang dung.
- RoomProgressSelfTest: socket that, 125+ bid, tong lon hon trang 50, mine qua
  nhieu trang, delta, cursor dong bang khi co bid moi, bid dong thoi, server-only
  presence, reconnect, private/blocked, raw packet an danh va kick nguoi da bid.
- RoomProgressStorageSelfTest: H2 tam, migration lap, journal sau restart,
  SQL totals/pages/mine, auto/manual extension, close/cancel, concurrent JDBC;
  ep INSERT event loi de chung minh rollback ca bid, gia va hold vi.
- RoomProgressUiSelfTest: journal rieng, cap 200, dedupe/reconnect/old-generation,
  trang cu, table/mine/cursor/tien le, role va ly do khong duoc kick.
- AllSelfTests dang ky hai nhom backend; tools/run-tests.mjs --ui dang ky nhom UI.
  Ket qua lan chay thuc te nam trong DEMO_UPGRADE_STATE.md va target/*-cluster2.log.
- WebSocketServerTransport khoi phuc interrupt khi callback hoan tat sau shutdown,
  tranh JDBC I/O nuot interrupt cua worker trong luc dong server. Test nut UI
  da kiem tra ca assertions va process exit 0, khong chi dua vao dong PASS.

Khong chay test hay migration tren MySQL production; cac test DB dung H2 duoi Temp.
