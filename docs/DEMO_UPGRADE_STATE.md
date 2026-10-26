# Demo upgrade - trang thai moi nhat

## Distribution refresh - checkpoint 2026-10-26
- Da xac nhan nang cap chat co trong source nhung dist cu: Mac 2026-10-18,
  Windows Client 2026-10-19, Windows Server 2026-10-24. Compile/test khong tu
  cap nhat EXE/archive hay process dang mo.
- Da rebuild hai Windows EXE va app-image, bon Mac portable arm64/x64 archive
  va thu muc giai nen tu source hien tai. Lenh chung: npm run dist:all.
  Giu nguyen cac nang cap CUM 1-6, cau hinh MySQL va du lieu nguoi dung.
- Them BUILD_INFO.json tung goi va dist/BUILD_INFO.json: source/JAR SHA-256,
  build time, role/platform/architecture va checksum sau tep ban giao.
  Verification tu choi source/JAR/architecture khong khop; dong bo guide va
  SQL migration (khong kem script reset) cho ban phan phoi.
- Packaging tests PASS 11/11. Payload verification PASS: 240 production
  class/resource, Server 232 / Client 222 tep app-image khop EXE; ca bon
  archive Mac khop thu muc giai nen, JAR, JavaFX natives va checksum.
- Chay test bang JAR/runtime Windows vua dong goi, PASS 4/4:
  RoomInteractionSelfTest, RoomSocialStorageSelfTest,
  RoomInteractionUiSelfTest, RoomSocialUiSelfTest.
  Giu kiem tra quyen host, gia mao/cross-room, nonce/reaction/rate limit,
  mute het han/unmute nhung van bid, unread, disconnect/retry, dong phien
  va khong lo danh tinh khach an danh.
- Bang chung: target/distribution-rebuild-2026-10-26.log,
  target/distribution-packaging-unit.log,
  target/distribution-tests-2026-10-26.json,
  target/*-distribution-2026-10-26.log, dist/VERIFICATION.md.
- Khong tuyen bo full regression moi hoac GUI Mac da chay. Mac portable can
  JDK 17+ dung kien truc; khong phai native .app. Khong launch Server that,
  reset/migrate production DB hay tat cac process dang mo trong luot nay.
- Can dong an toan CA Server/Client cu roi mo hai EXE ngay goc dist;
  process/AppData cache dang chay va shortcut cu khong tu nang cap.
  Nen backup DB truoc khi khoi dong Server moi. Huong dan:
  docs/34_CAP_NHAT_BAN_PHAN_PHOI.md.

## Lich su checkpoint CUM 5 (giu nguyen de doi chieu)

## CUM 5 - checkpoint 2026-10-25
- Hoan thanh sanh loc/sort toan snapshot truoc paging 30 dong, nhan rieng/an danh,
  nhan manh <=60 giay; giu loi vao receipt Cum 4.
- Tao phong co xem lai/xac nhan va pending guard; mau mo ta san pham khong doi
  validation; giai thich khoa metadata/media sau phien dau tien.
- Ho so rieng tach public, xem truoc whitelist public; vi co loc server
  phien/loai/ngay/keyset, ly do tu choi nap va chu thich QR/vi mo phong.
- Thong bao owner-only luu cung transaction OUTBID/result/topup review, doc/chua doc,
  source key unique chong lap qua reconnect/restart, khong chua danh tinh khach.
  Lien ket van qua quyen hien tai, khong grant phong rieng/chat.
- Local server co loc room_events theo phien/loai/thoi gian, projection an toan
  khong actor/detail/password/token/payload. Khong them RPC admin/dashboard.
- Migration: personal_notifications va wallet_topups.rejection_reason; initializer
  idempotent, khong backfill notification/ghi lai ledger. Giu cung vong doi
  account/auction; khong TTL moi va khong doi luu tru/quyen chat/receipt.
  Huong dan: docs/31_HOAN_THIEN_CHUNG.md, sql/05_personal_notifications.sql.
- Compile PASS. Muc tieu PASS 8/8: CompletionSelfTest, WalletSelfTest,
  AuctionReceiptSelfTest, RoomInteractionSelfTest, RoomProgressStorageSelfTest,
  CompletionUiSelfTest, WalletUiSelfTest, RoomSocialUiSelfTest.
  Log target/*-cluster5-final.log; target/cluster5-final-tests.json.
  Anh target/completion-wallet.png, target/completion-lobby-small.png.
- Loi trong test moi (so sanh enum voi String) da sua va chay lai PASS.
  Chua chay full suite/dong goi trong Cum 5. Chua test MySQL/LAN thu cong.
- Bat dau CUM 6: xu ly hai diem fail ghi nhan o Cum 4, kiem thu tich hop,
  cap nhat huong dan/demo va dong goi bang scripts hien co. Chua danh dau nghiem thu.

## Lich su checkpoint CUM 4 (giu nguyen de doi chieu)

Ngay: 2026-10-25. Moi nhat: CUM 4 - ket qua phien, bien nhan va tra cuu ca nhan.
Trang thai: CUM 4 HOAN THANH; compile va 7/7 nhom muc tieu PASS tren Java 17.
Full regression: 12/14 nhom PASS; DesktopUiSelfTest va AuctionJourneySelfTest
con FAIL (chi tiet duoi). KHONG tuyen bo full suite xanh.
Da doc checkpoint CUM 1/2/3, tai su dung stats/journal va transaction bid/vi;
giu nguyen cac thay doi san co trong worktree, khong lam lai luat dau gia.

## CUM 4 - Chuc nang / du lieu
- Sidebar Ket qua phien cua toi: Phien toi da tham gia / da thang / to chuc,
  loc trang thai, ten/ma san pham/ma phien va keyset pagination 30 hang/trang.
  Mo lai receipt khi da roi phong, reconnect, session moi hay app/server restart.
- Server xac nhan WON / NO_BIDS / CANCELLED; hien san pham, ma phien, thoi
  gian chot, winner display, gia chot, total bids, unique bidders, extensions
  va canh bao extensionsComplete=false cho nhat ky cu. Khong co bid/huy:
  khong lay gia khoi diem lam gia ban. Personal role: WINNER/NOT_WON/WATCHED/HOST.
- Khong co receipt cho nguoi chi dang dan dau hoac dong ho client ve 0.
  Mat ket noi/timeout/loi chi hien chua xac nhan + thu lai. Generation va
  session guard chan callback cu; logout dong va xoa cua so receipt.
- closeAuction khoa auctions, settle vi, ghi result + event ENDED + summary
  + personal receipts cung transaction. Cancel cung ghi event + receipt.
  Fault receipt rollback ca hold/payment/result; repeated/concurrent close
  khong sinh PURCHASE/SALE lan hai. Cua so receipt doc lap timer quay ve san.
- Tai su dung RoomProgressStore.stats(Connection, room) CUM 2 trong chinh
  transaction chot, khong dem bid/event tren client. Summary dong bang de
  khong can runtime, membership hay journal tinh lai sau archive/restart.
- Wallet doc ledger/settlement/hold cua chinh user theo session. PAID can
  settlement + PURCHASE/SALE dung amount/delta; thieu/sai, LEGACY_REVIEW,
  con hold hoac net reserved_delta khong ve 0 => REVIEW_REQUIRED.
  RELEASED phai co RELEASE that; watcher khong thay giao dich nguoi khac.
  Link receipt -> ledger dung phien (100 gan nhat + hasMore) -> Vi cua toi.

## CUM 4 - Chinh sach quyen / luu tru / migration
- HAI bang moi: auction_receipt_summaries chi snapshot toi thieu; personal
  receipts chi (auction_id,user_id,role). Receipt cho host, bidder va account
  co JOINED da luu truoc chot, ke ca da roi/disconnect/kick. Chi xem san hay
  co alias khong duoc cap receipt. Khong sao chep chat/roster/profile/password.
- API require login va session hien tai, owner tu session, khong tin userId
  client. Khong co quyen/khong ton tai/chua chot cung AUCTION_FORBIDDEN.
  Receipt khong grant private-room, chat, roster, profile hay bid/event history.
  Cac API phong van giu blocked check + private grant theo session hien tai.
  Kicked account chi giu quyen doc BAN TOM TAT CA NHAN, khong mo lai phong.
- An danh: winnerDisplay dong bang alias room_participants; ca detail/list
  khong tra winner account ID, username hay profile URL. Personal role tu
  server so khop noi bo. Whitelist receipt khong can runtime projector.
- Giu luu tru hien hanh: archive chi an san/xoa membership, khong xoa DB.
  Khong doi TTL hay mo rong quyen luu/doc chat. Chi them snapshot receipt
  cung vong doi phien, ON DELETE CASCADE theo auction/user; khong cache local.
- DatabaseSchema.initialize -> AuctionReceiptStore.initialize idempotent.
  Backfill chi phien co result xac nhan hoac CANCELLED, khong settle lai.
  WATCHED cu chi khoi phuc khi co JOINED; khong suy doan du lieu truoc CUM 2.
- DB moi: sql/00_schema.sql. db.autoInitialize=false: chay RIENG
  sql/04_auction_receipts.sql truoc ban moi. SQL chi tao bang; backfill phien
  cu bang mot lan DatabaseSchema.initialize (autoInitialize=true mot lan,
  roi tra false). Sao luu truoc migration; khong DROP/reset/seed production.
- Chi tiet payload, scope quyen, migration va entry points:
  docs/30_KET_QUA_PHIEN.md. Khong them giao hang, refund that hay tranh chap.

## CUM 4 - Kiem thu da chay / gioi han
- JAVA_HOME=C:/Program Files/Java/jdk-17; mvn -q test-compile
  dependency:copy-dependencies -DincludeScope=test: PASS tren code cuoi.
- PASS 7/7 nhom muc tieu tren code cuoi: AuctionReceiptSelfTest,
  WalletSelfTest, RoomProgressStorageSelfTest, RoomSocialStorageSelfTest,
  AuctionReceiptUiSelfTest, LiveAuctionRoomUiSelfTest, BidExperienceUiSelfTest.
  Ket qua: target/cluster4-final-tests.json; log target/<Test>-cluster4-final.log.
- Receipt test JDBC H2 file tam: migration lap/backfill/restart/archive,
  winner chua chot bi chan, 4 role, immutable stats, aliases, no bid/cancel,
  concurrent close/retry, receipt FK fault rollback, exact own ledger,
  mismatched purchase/release REVIEW_REQUIRED, filter/keyset, kicked receipt.
- TCP server that: no login/outsider/userId spoof, raw anon detail/list,
  receipt khong mo private history/chat, kicked van bi chan history;
  disconnect dung luc timer chot, resume, result+payment tu DB; server restart
  va fresh login. UI: cho xac nhan/offline/denial/retry, own-ledger links,
  filters/keyset/stale callbacks/logout/session guard va reopen.
  Anh target/auction-receipt.png da kiem tra truc quan.
- node tools/run-tests.mjs --skip-build --ui: 12/14 nhom PASS.
  Luot full truoc tinh chinh kiem tra net reserved_delta va bo sung test cuoi;
  7 nhom muc tieu tren da chay lai sau tinh chinh.
  target/qa/results.json va target/cluster4-regression.log ghi ket qua day du.
- DesktopUiSelfTest.java:128 con FAIL banner dan dau nhu checkpoint CUM 3;
  fixture applySnapshot/render truc tiep khong qua join/sync gate CUM 1.
- AllSelfTests chay qua cac nhom CUM 4/vi/CUM 2/3 roi FAIL tai
  AuctionJourneySelfTest.java:114: sau deadline 1 phut, expected
  PRODUCT_ALREADY_SOLD nhung actual INTERNAL_ERROR, log "Auction runtime
  already exists: 1". Chay RIENG AuctionJourneySelfTest lap lai cung loi:
  target/AuctionJourneySelfTest-cluster4-isolated.log. Khong sua luong
  tao lai san pham/runtime ngoai pham vi CUM 4; khong khang dinh loi nay da
  ton tai truoc nang cap. Cac nhom UI khac trong luot full deu PASS.
- git diff --check: PASS. Khong chay MySQL production/migration that, khong
  reset du lieu, khong build EXE; chua demo LAN thu cong cho CUM 4.

## Lich su CUM 3
Ngay: 2026-10-25. CUM 3 hoan thanh voi compile + 5/5 nhom muc tieu PASS;
full regression khi do 12/13 nhom PASS. Cac ghi chep CUM 3/2/1 giu de doi chieu.

## CUM 3 - Chuc nang / bao mat
- Mot thong bao/phong: host ghim/sua/bo, server luu, dong bo nguoi moi vao.
  The vang gon NGOAI tab chat; tooltip cho day du; host editor 500 ky tu.
- Unread ngay tren tab, dung khi o tab khac/cuon len. Cursor + message id
  chong trung giua event/ack/poll, khong dem snapshot dau hay tin cua minh.
  Chi clear khi xem chat o cuoi hoac bam tin moi; resync giu cursor.
- Reply co preview/huy; server lay goc tu DB dung phong, tu suy ra ten/trich
  dan/alias. Nonce chat rang buoc ca body va replyToId, khong tin author client.
- Bon emoji LIKE/HEART/LAUGH/CLAP, desired-state active true/false, dem/mine
  theo viewer. PK moi (chat,user,emoji), receipt nonce persistent, >=400 ms
  giua lenh moi theo (room,actor). Retry cung nonce khong toggle lan nua.
- Host mute/unmute theo participantId, ly do va han server (1-86400 giay).
  UI chon 1-1440 phut. Chan chat/reply/reaction, KHONG chan bid hay huy bid;
  khong doi kick protection. Ly do/han hien ngoai chat cho nguoi bi mute,
  trong roster cho host. Het han tinh luc doc/ghi, khong can client bo mute.
- Phong an danh dung ma khach am room-local. Host khong can username that;
  server tu choi user id duong/ma phong khac khi mute. Reply dung putIdentity;
  pin khong chua author; reaction chi counts/mine; invalidation event chi
  room/revision. Khong them avatar, account id, username hay profile URL khach.
- Moi mutation require session hien tai, read access va membership dung
  connection; khoa runtime, OPEN/endTime va host check server. Outbound event
  kiem tra lai session/membership/quyen doc, khong gui cho khach da roi phong.
- Dong phien chi doc. Mat ket noi/timeout hien chua xac nhan; khong optimistic
  bubble/count. Pending, loi, Thu lai giu nonce + payload; generation chan
  callback cu, revision chan snapshot interaction cu ghi de ack moi.
- Snapshot dau 50 tin, sau do keyset afterChatId toi da 50 tin/lan; hasMore
  bat kip reconnect. Client giu toi da 200, reaction refs toi da 200, verify
  theo batch SQL. Mutation/event khong tai lai toan bo chat/avatar; reaction
  cap nhat thanh nho. Giu avatar cache/ho so whitelist/chat anti-spam 750 ms.
- KHONG them typing indicator, file, video call hay thay doi luat dau gia.

## CUM 3 - API / migration / file
- Moi MessageType: SET_ROOM_ANNOUNCEMENT/RESULT, SET_ROOM_REACTION/RESULT,
  SET_ROOM_MUTE/RESULT, ROOM_INTERACTION_CHANGED (chi invalidation).
- GET_ROOM_SOCIAL them afterChatId, chatIds, nextChatId, hasMoreChat,
  announcement.*, interactionRevision, canInteract, selfMute.*, reaction.N.*;
  member mute chi host/ban than duoc doc. SEND_ROOM_CHAT them replyToId.
- RoomSocialSchema.initialize (duoc DatabaseSchema goi) migration idempotent:
  room_chat.reply_to_id default 0 + room_interaction_state, room_chat_mutes,
  room_chat_reactions, room_interaction_requests. Giu chat cu, khong seed/reset.
- JDBC mutation + receipt SHA-256 + revision cung transaction, khoa hang
  auctions de chong trung qua nhieu connection/store. Loi rollback toan bo,
  khong emit event truoc commit. Retry khong gia han mute hay sua timestamp pin.
- DB moi: sql/00_schema.sql. DB cu autoInitialize=false: sql/03_chat_interactions.sql,
  CHI chay ALTER khi reply_to_id chua ton tai. Khong chay reset/seed production.
- Moi: server/social/RoomInteractionStore.java, RoomInteractionSelfTest.java,
  RoomInteractionUiSelfTest.java, docs/29_TUONG_TAC_CHAT.md va migration SQL.
- Mo rong RoomSocialStore/Schema/Service, RoomSocialApi/Pane, MessageType,
  FxClientController (event), CSS, RoomSocialStorageSelfTest, AllSelfTests va
  tools/run-tests.mjs --ui. Giu cac thay doi cu cua nguoi dung/CUM 1/2.

## CUM 3 - Kiem thu da chay / gioi han
- JAVA_HOME=C:/Program Files/Java/jdk-17; mvn -q test-compile
  dependency:copy-dependencies -DincludeScope=test: PASS.
- Luot cuoi SAU tinh chinh: PASS 5/5 nhom muc tieu RoomInteractionSelfTest,
  RoomSocialStorageSelfTest, RoomSocialSelfTest, RoomInteractionUiSelfTest,
  RoomSocialUiSelfTest. target/cluster3-final-tests.json ghi ca 5 pass va
  luot DesktopUiSelfTest rieng van fail; log target/<TenTest>-cluster3.log.
- Socket/server: host/nonmember/role, gia author/quote, reply/reaction khac
  phong, nonce lap/dong thoi/conflict, counts/mine, rate, mute het han/unmute,
  mute van bid hop le, closed va raw anonymous packet khong lo danh tinh.
- JDBC H2 tam: migration lap, reply/pin/reaction/mute/receipt qua restart,
  expiry boundary, nonce dong thoi qua 2 store, fault injection rollback,
  timestamp pin khong doi boi reaction/mute, delta history co gioi han.
- UI JavaFX + TCP: host editor pin/mute va unmute bang ma khach, pin khi
  dong chat tab, unread doi tab/cuon len/reload, reply/huy, reaction pending/
  error/retry cung nonce, mute expiry, offline/closed, an danh va layout 520 px.
- Chay node tools/run-tests.mjs --ui --skip-build: 12/13 groups PASS.
  AllSelfTests (toan bo server/network/storage/media) PASS; packaging-unit
  va 10/11 UI groups PASS. target/qa/results.json, target/cluster3-full-suite.log.
  Luot full nay truoc tinh chinh batch refs/timestamp pin; 5 nhom muc tieu
  da chay lai tren code cuoi nhu tren.
- Con FAIL DesktopUiSelfTest.java:128: "accepted buyer bid updates personal
  leading banner", lap lai khi chay rieng. Fixture goi applySnapshot/render
  truc tiep, khong qua join/sync gate CUM 1; khong sua test/banner ngoai cum chat.
  Cac BidExperienceUiSelfTest, LiveAuctionRoomUiSelfTest, AuctionButtonsUiSelfTest
  va WalletUiSelfTest trong luot full deu PASS. Khong tuyen bo full suite xanh.
- git diff --check (cr-at-eol): PASS. Khong chay MySQL production/migration that,
  khong reset du lieu, khong build EXE, chua demo LAN thu cong cho CUM 3.

## Lich su checkpoint
Thong tin CUM 2/1 duoi day duoc giu de doi chieu, khong phai trang thai moi nhat.

## CUM 2 - Nguon du lieu / nghiep vu
- Tab Nhat ky phien rieng voi chat; ListView cuon rieng, toi da 200 ban ghi,
  chong trung theo event_id, trang cu thay cua so thay vi noi vo han.
- Su kien server luu CREATED, JOINED, LEFT, BID_ACCEPTED, LEADER_CHANGED,
  EXTENDED_MANUAL, EXTENDED_AUTO, ENDED, CANCELLED; server cap thoi gian.
- JDBC ghi bid + vi + auction + log bid/leader/auto-extension cung transaction.
  Manual extension, close/result/settlement va cancel cung transaction voi log.
  Test ep insert event that bai xac nhan rollback ca bid, gia, hold va journal.
- JOINED/LEFT dua vao ket noi dau/cuoi cua tai khoan, khong dua vao polling.
  Co log cho leave, disconnect, kick va archive; join/resync lap khong tao log.
  event_id ben DB on dinh qua restart; keyset before/after phuc hoi reconnect.
- Tong bid = COUNT(bids), so tai khoan bid = COUNT(DISTINCT user_id),
  so gia han = COUNT(events manual/auto), tang gia = current_price - start_price.
  Khong lay so dong lich su da tai lam thong ke toan phien.
- UI ghi ro ket noi theo doi, tach voi tai khoan hien dien va tai khoan da bid.
  Role Chu phong/Ban/Dang dan dau va canKick/kickReason lay tu server.
  Moi nguoi tung co bid hop le bi khoa kick, ke ca khong con dan dau.
  Danh sach co loading/error/retry, gioi han 200 dong va ghi ro khi bi gioi han;
  cache avatar don theo danh sach/chat dang giu.
- Hop lich su thay TextArea bang TableView thoi gian server, nguoi dat, tien,
  muc tang; 50 dong/trang, trang cu, Moi nhat, bo loc Luot cua toi o server.
  Muc tang tinh so voi bid truoc trong TOAN PHIEN (khong phai trong trang/filter).
  throughSequence giu snapshot khi co bid moi dong thoi; tien dung WalletUi.money.
- Giu cac tab cu va gate/generation cua CUM 1. Bo suy dien thong bao bien dong
  gia/gia han tu render snapshot; nhat ky chi hien event server da luu.
- Dung lai outbound projection/ordinal cua RoomSocialService de mask event va
  moi trang bid, ke ca host/mine/archived; stats chi co so tong, khong ID khach that.
- Nhat ky/stats doc khoang 3 giay; truoc khi gui van kiem tra session/quyen.
  Disconnect/logout/doi phong vo hieu callback generation; reconnect tiep cursor.
- Sua WebSocketServerTransport de khoi phuc interrupt khi onMessage/onClose
  ket thuc sau shutdown; tranh worker con song khi JDBC I/O nuot interrupt.
  AuctionButtonsUiSelfTest da chay lai va process thoat thanh cong (exit 0).

## CUM 2 - API / migration
- GET_ROOM_EVENTS -> ROOM_EVENTS_RESULT: auctionId, beforeId HOAC afterId,
  limit 50 (toi da 100); eventCount/event.N.*, hasMore, nextBeforeId/nextAfterId.
- GET_AUCTION_STATS -> AUCTION_STATS_RESULT: totalBids, uniqueBidders,
  extensionCount, extensionsComplete, priceIncrease, watcherConnectionCount,
  presentAccountCount. Cac so tong doc tu DB, khong bi gioi han cua lich su client.
- GET_BID_HISTORY mo rong: limit, beforeSequence, throughSequence, mine;
  them bid.N.increase, nextBeforeSequence, throughSequence, hasMore.
  Tai khoan cho mine lay tu session, bo qua userId client gia mao.
- GET_ROOM_SOCIAL them self/leading/hasValidBid/canKick/kickReason,
  memberTotal (tong tai khoan hien dien) tach memberCount (so dong tra ve).
- requireHistoryReadAccess cho phep doc theo ID sau archive, van kiem tra
  blocked/private-room grant. KHONG mo rong quyen live/bid/vi cua CUM 1.
- Migration DUY NHAT: them bang room_events, index (auction_id,event_id), qua
  RoomProgressStore.initialize + DatabaseSchema.initialize; DDL cho DB moi o
  sql/00_schema.sql. Khong doi schema bids/auctions, khong reset/seed DB.
- Phien moi co CREATED => extensionsComplete=true. Phien cu KHONG the khoi
  phuc so lan gia han truoc nang cap: false + UI ghi ro so ghi nhan tu nang cap,
  khong trinh bay so nay la tong day du. Tong bid/unique bidders/tang gia van day du.
- Neu db.autoInitialize=false: can them bang theo DDL truoc khi chay ban moi.
  KHONG chay script reset/toan bo schema tren du lieu production.
- Chi tiet payload, migration va UI: docs/28_DIEN_BIEN_PHIEN.md.

## CUM 2 - File / diem vao
- Moi: client/fx/RoomProgressPane.java, BidHistoryPane.java;
  server/auction/model/RoomProgress.java, repository/RoomProgressStore.java,
  service/RoomProgressService.java.
- Noi vao RoomSocialPane, LiveAuctionRoom, FxClientController, AuctionApi,
  MessageType, AuctionController, ServerApplication, AuctionRepository/Jdbc,
  RoomManager, AuctionManagementService, DatabaseSchema, RoomSocialService.
- Test moi: RoomProgressSelfTest, RoomProgressStorageSelfTest,
  RoomProgressUiSelfTest. Cap nhat LiveAuctionRoomUiSelfTest va
  AuctionButtonsUiSelfTest theo metric server va bang lich su thay TextArea.
- TestAuctionRepository cung cap data/event tuong ung cho integration test;
  AllSelfTests va tools/run-tests.mjs dang ky test moi.

## CUM 2 - Kiem thu da chay
- JAVA_HOME=C:/Program Files/Java/jdk-17; mvn -q test-compile
  dependency:copy-dependencies -DincludeScope=test: PASS.
- PASS 13/13 nhom: RoomProgressSelfTest, RoomProgressStorageSelfTest,
  RoomSocialSelfTest, RoomSocialStorageSelfTest, AuctionRulesSelfTest,
  WalletSelfTest, FullNetworkAuctionSelfTest, RoomProgressUiSelfTest,
  LiveAuctionRoomUiSelfTest, BidExperienceUiSelfTest, AuctionButtonsUiSelfTest,
  RoomSocialUiSelfTest, WalletUiSelfTest.
- Bao phu 125/135 bid vuot trang 50, bid dong thoi TCP/JDBC, mine qua nhieu trang,
  delta qua ranh gioi trang/filter, snapshot khi co bid moi, server clamp limit,
  id on dinh/persistence, poll/resync khong tao log, reconnect khong trung,
  log cap 200, callback cu sau doi phong/dong hop, private/blocked, an danh cho
  host va guest, kick ca nguoi tung bid nhung khong con dan dau bi tu choi.
- Tu dong gia han qua BidService that, thu cong, ket thuc, huy, archive va
  rollback neu ghi journal that bai da duoc kiem tra. Test DB dung H2 tam duoi
  Temp; transport TCP/WebSocket localhost; KHONG MySQL production.
- FullNetworkAuctionSelfTest khong co main: goi run() qua Java source runner
  tam trong target, da xoa runner sau khi test. Ket qua cuoi exit 0.
- Tong hop: target/cluster2-tests.json; log: target/<TenSelfTest>-cluster2.log;
  compile: target/cluster2-compile.log. git diff --check: PASS (CRLF warnings san co).
- Con canh bao CSS JavaFX lookup -ink/-teal san co; assertions va UI tests PASS.
- Khong chay full suite, MySQL tests/migration, LAN thu cong hay build EXE.
  Chua mo rong sang cum ket qua/chat hay thay doi luat bid/vi.

---

## CUM 1 - Checkpoint truoc (giu lai)
Ngay: 2026-10-25. CUM 1 HOAN THANH; compile va 7/7 nhom test muc tieu PASS.

## Pham vi va quyet dinh
- Da kiem tra AGENTS.md va git status, doc luat/checkpoint (truoc do chua co).
- Giu nguyen cac thay doi san co; khong reset/checkout/commit/doi branch.
- Server van quyet dinh gia, thoi gian, quyen, ket qua va vi; khong doi luat.
- Nut/Enter/nut tang nhanh khoa dong bo ngay khi gui; phan hoi nam canh o gia.
- Loi nghiep vu hien inline, khong dung modal cua success() cho bid.
- Timeout watchdog theo client.requestTimeoutMillis: ket qua CHUA XAC NHAN,
  chi doc RESYNC + GET_WALLET; KHONG tu gui lai bid hay ket luan that bai/thang.
- Chi xac nhan luot chua ro khi co bid/history server khop phong, self-id va so tien;
  khong tim thay trong cua so lich su khong dong nghia da bi tu choi.
- Callback rang buoc generation phong, generation ket noi, session va sequence bid/vi;
  vo hieu ngay khi chuyen/roi phong, logout, disconnect, kick, archive hoac close.
- Chi mo bid khi snapshot va vi rieng hop le; tin hieu phong/vi qua 10 giay la cu.
  Vi refresh dinh ky 4 giay; reconnect phai resume session roi resync truoc khi thao tac.
- Xoa so du sidebar khi doi vong doi/session; response vi cu khong cap nhat tai khoan moi.
- Chu phong vao phong va dong bo truoc khi quan ly; UI ghi ro ly do khoa theo luat
  (da co bid, het gio, het quyen, mat mang, cham han cuoi), server van kiem tra lai.
- Money.parseInput/input va WalletUi.money dung BigDecimal, khong lam tron tien.
  Ho tro 1.234,56 / 1234,56, plain 1234.56 va nhom nghin 1.234; so le dung dau phay.
  Gioi han gia lay tu server; chart giu du lieu BigDecimal, chi truc do thi la NumberAxis.
- Giu hai cot; gia/dong ho gon, lich su co viewport rieng >=160px; khoi bid cuon rieng
  khi loi/luat dai, khong day toan bo lich su ra ngoai man hinh laptop.
- Khong sua chat, nhat ky phien, man hinh ket qua hoac chinh sach an danh.

## API / migration
- GET_WALLET them auctionId tuy chon; WalletApi.get(long auctionId).
- Khi co auctionId: lay user tu session, requireReadAccess (phong rieng/chan deu kiem tra),
  doc mot SELECT nhat quan users + wallet_holds WHERE user_id cua session.
- Tra auctionId, available, roomHeld, maxBidAmount; khong them userId/username vao
  response vi theo phong, tranh lo danh tinh trong phong an danh.
- Payload/luong broadcast bid khong chua so du/hold. GET_WALLET cu khong doi.
- KHONG migration/schema/seed/reset DB; KHONG ghi hay chay test tren MySQL production.

## File va diem vao da doi
- client/fx/FxClientController.java: placeBid, synchronizeRoom, refreshRoomWallet,
  roomReady/current/invalidateRoom, callback join/leave/resume/host/history, room layout.
- client/fx/LiveAuctionRoom.java: setDataState, tick, bieu dien chart BigDecimal.
- client/service/WalletApi.java; common/util/Money.java; common/fx/WalletUi.java.
- server/wallet/WalletController.java, WalletRepository.roomSummary; ServerApplication wiring.
- src/main/resources/ui/auction.css: inline feedback, compact bid scroll/rules.
- selftest/BidExperienceUiSelfTest.java (moi); WalletSelfTest.java;
  AuctionButtonsUiSelfTest.java (doi cho gate dong bo/vao phong truoc khi quan ly).
- tools/run-tests.mjs: dang ky UI selftest moi, khong chay full runner trong cum nay.

## Kiem thu
- Compile: JAVA_HOME=C:/Program Files/Java/jdk-17; mvn -q test-compile
  dependency:copy-dependencies -DincludeScope=test: PASS.
- Luot kiem tra cuoi sau tat ca sua doi: PASS 7/7 nhom muc tieu:
  BidExperienceUiSelfTest, AuctionButtonsUiSelfTest, LiveAuctionRoomUiSelfTest,
  WalletUiSelfTest, WalletSelfTest, RoomSocialSelfTest, AuctionRulesSelfTest.
- Bao phu: gui lap/Enter/tang nhanh; gia doi khi dang gui; loi nghiep vu;
  watchdog that va callback timeout; disconnect/connected-chua-sync; response bid,
  resync, vi cu sau doi phong/roi/logout; alias an danh; tien le/gioi han;
  quyen vi rieng/bi chan, userId gia mao, hold tung phong va giao dich dong thoi.
- Backend/UI integration dung H2 tam duoi Temp va TCP/WebSocket localhost, KHONG MySQL.
- Log: target/<TenSelfTest>-cluster1.log; anh: target/bid-experience-preview/laptop.png.
- git diff --check tren cac file da sua: PASS (bo qua CRLF bang cr-at-eol).

## Ton dong / tiep theo
- Log UI co canh bao CSS JavaFX lookup -ink/-teal; assertions va anh UI van dat.
  Chua xu ly canh bao CSS ngoai pham vi cum dat gia.
- Chua demo thu cong qua LAN/production; timeout/delay duoc tai hien bang transport
  co dieu khien, integration bid/vi/quyen dung server va WebSocket that.
- Khong chay full suite, MySQL test hay build EXE (dung yeu cau).
- Tiep theo: cho giao CUM 2; khong tu mo rong sang chat/nhat ky/ket qua.
