# Kiem thu anh, giao dien va hanh trinh dau gia

## Chay lai

Can JDK 17+, Maven, Node.js va FFmpeg/ffprobe 8+. Windows dung tools/media;
tren Mac cai FFmpeg bang Homebrew. Test giao dien can phien desktop that,
khong chi terminal headless. Moi test UI chay trong JVM rieng.

```text
npm test
npm run test:full
npm run test:mysql
npm run dist
npm run dist:mac:portable
npm run test:bundles
npm run test:dist
```

- `npm test`: compile, test Node dong goi va AllSelfTests, gom WebSocket/HTTP that.
- `test:full`: them nut bam JavaFX, dashboard/login, editor/gallery/video, khoi phuc san pham, vi/QR/duyet nap va dong man hinh ket qua.
- `test:mysql`: them JDBC MySQL/MariaDB, chi tao/xoa database ten btl16_verify_*; can MySQL local dang chay.
- `test:bundles`: kiem JAR/SHA-256, JavaFX/native/dong goi sach cua 4 archive Mac va 2 EXE Windows; doi chieu TOAN BO file trong cac thu muc giai nen voi archive/payload EXE, khong chi rieng JAR.
- `test:dist`: Windows, lap lai core va UI voi JAR, thu vien va runtime TU GOI SERVER, khong dung classes production trong target.

Log tung nhom va ket qua JSON: target/qa/; kiem thu runtime dong goi:
target/qa-packaged/. Lenh tra exit code khac 0 neu co nhom fail; khong bo qua UI loi.
Test hanh trinh doi moc het han 1 phut that, khong sua DB de gia ket qua.
Test dung thu muc tam, DB rieng va cong ngau nhien, khong dung DB/media production.

## Pham vi tu dong

| Nhom | Tinh huong |
| --- | --- |
| MysqlStorageSelfTest | XAMPP that voi DB btl16_verify_* rieng; anh >1 MiB chia doan, video, vi/nap/giu cho/thanh toan dung mot lan; khong tao cache/tep media tam; restart doc truc tiep tu MySQL; HEAD, Range qua ranh gioi chunk va suffix |
| SeekableMp4SelfTest | Chi muc MP4 hoan chinh trong RAM, frame khong doi, offset 32/64-bit, idempotence va tu choi du lieu cat cut/phan manh |
| WalletSelfTest | So du dau 0; auth/rieng tu; cho duyet/tu choi/chap nhan/khong trung; giu cho, vuot gia, roi phong; thieu tien; race nhieu phong; rollback; thanh toan dung mot lan va restart |
| WalletUiSelfTest | QR doc duoc bang bo giai ma; Da nhap chi gui cho duyet; nut xac nhan hien ngay; nut duyet/tu choi server; client tu dong cap nhat so du; chi tiet ho so/so giao dich |
| DesktopUiSelfTest | Phong rieng sau khi vao; anh lon/lich su/buoc gia; quay lai san; refresh khong lam mat ket qua; khoa bid khi dong; dem nguoc 60 giay va tu dong an phong |
| ImageCompatibilitySelfTest | HEIC that, HEIC 48 o ghep du 4032x3024, AVIF/JP2/ICO/PPM, PNG 48 MP, ten viet hoa/sai duoi, JPEG preview, video gia anh va SVG bi tu choi |
| MediaConfigurationSelfTest | Uu tien cong cu dong goi, config, JVM property, duong dan co dau cach, bo loc duoi viet hoa cho Mac |
| MediaSystemSelfTest | JPEG/PNG/BMP/GIF/TIFF/WebP; EXIF/alpha/crop/rotate; upload/HTTP/Range/HEAD; quota/huy/tai do; quyen rieng/chan/session; JDBC/migration/race/restart |
| AuctionJourneySelfTest | Dang ky/dang nhap -> HTTP HEIC -> luu san pham -> phong rieng/password/search -> bid/outbid/kick/rejoin/reconnect -> bid dong thoi -> het han that/nguoi thang -> restart |
| AuctionJourneySelfTest | Ma loi chinh xac: sai owner, trung ma, buoc gia sai, phong trung, dong bang, host bid, bid thap, tu nang, chot/huy/gia han sau bid, kick nguoi da bid, chan viewer, bid sau dong, dau gia lai hang da ban |
| AuctionRulesSelfTest | Tong gia han thu cong + tu dong toi da 600 giay, anti-sniping, quyen host/buyer, race join/kick, thu tu ban tin cu/moi |
| AuctionButtonsUiSelfTest | Bam ket noi/dang ky/dang nhap/tao phong/gia han/kick/refresh/search/xoa loc/join/bid/lich su/roi/profile/doi password/logout/huy/ket thuc; khoa nut tu nang va nut host sau bid |
| MediaUiSelfTest | Upload nhieu anh, HEIC luoi + AVIF trong editor/save, cover/order, zoom/fit, keo crop/rotate/reset, video play/seek/volume/mute, fullscreen/gallery/cancel |
| ServerButtonsUiSelfTest | Sao chep dia chi/ma vao clipboard that, xac nhan tam dung, dung/khoi dong lai server va tai khoan van con |
| ProductRestoration* | Owner/auth/idempotence, unsold/sold/open, dong bang/lich su, restore/reauction, JDBC nhieu ket noi va UI voi stub co ghi ro |
| Protocol/Network/DesktopLan* | TCP/WebSocket, session/reconnect, ban tin/JSON/limit, private room, dong thoi va luu ben vung |

Chu phong can vao phong de nhan realtime; tao phong khong tu dong vao phong.
Test UI vao phong o ca seller va buyer. Server van tu choi thao tac trai luat
ke ca khi client cu chua khoa nut. Kick nguoi da bid luon bi chan o server,
ke ca nguoi do khong con dan dau.

## Gioi han phai noi ro

## Nghiem thu nang cap Cum 1-6

- Them CompletionSelfTest/CompletionUiSelfTest: loc/sort toan sanh truoc paging,
  xem lai tao phong + gui lap, inbox owner-only/read/restart/dedup, vi loc truoc
  LIMIT va ly do tu choi, projection log van hanh khong payload nhay cam.
- AuctionJourneySelfTest chay ca PRIVATE NAMED va PRIVATE ANONYMOUS: HEIC/product,
  chat/profile/mute nhung van bid, cung gia gan dong thoi, reconnect, cho het han
  that, receipt + PURCHASE/Sale khop ledger, server restart va kiem DB durable.
- DesktopUiSelfTest phai vao phong qua handler join that de cho snapshot/vi ready;
  khong bo qua gate moi bang cach goi rieng applySnapshot trong fixture dan dau.
- H2 2.3.232 chi dung test: quan sat du lieu bi lui sau nhieu open/close (runtime
  da ENDED nhung file DB quay ve OPEN/140, mat cac phien/bid da commit). Ap dung
  AUTO_COMPACT_FILL_RATE=0 vao URL test, tuong ung workaround upstream
  h2database/h2database issue #4247. Khong sua URL MySQL, khong doi quy trinh settle,
  khong dong goi H2. Log fail cu duoc giu; ket qua sau workaround ghi rieng.
- Full runner: `node tools/run-tests.mjs --ui --mysql`; --mysql chi dung cac DB
  btl16_verify_* rieng va cleanup, khong btl_16. Chi them --mysql khi may co MySQL.
- Build hai vai tro bang `npm run dist`. Kiem payload bang
  `powershell -NoProfile -ExecutionPolicy Bypass -File tools/verify-media-bundles.ps1`.
  Smoke JAR/runtime dong goi: `node tools/run-tests.mjs --skip-build --ui --mysql --packaged`.
- Kich ban/demo/LAN: `32_DEMO_VA_NGHIEM_THU.md`. Lenh va ket qua THUC TE moi nhat:
  `33_KET_QUA_NGHIEM_THU.md`, `DEMO_UPGRADE_STATE.md`; khong lay dong lenh huong dan
  trong tai lieu nay lam bang chung da chay.

## Gioi han

- Windows: test that tren may hien tai; log moi quyet dinh PASS/FAIL.
- Dang nhap duoc khong co nghia kenh anh da thong: may server can mo ca TCP 8890
  (WebSocket) va 8891 (anh/video) tren LAN tin cay, hoac cac cong da cau hinh.
- Server Mac portable can JDK 17+ dung kien truc, MySQL va FFmpeg/ffprobe 8+;
  client Mac khong can tu cai FFmpeg. Thieu codec tren server van co the loi upload.
- Mac Intel/Apple Silicon: kiem cau truc, JavaFX/native, JAR va archive tren Windows
  KHONG thay the chay GUI/codec tren Mac. De xac nhan runtime, chay `npm run test:full`
  tu source tren tung may Mac voi JDK/Node/FFmpeg dung kien truc.
- Chua khang dinh moi phien ban macOS/Windows, moi codec hay moi thiet bi deu duoc ho tro.
  SVG/RAW, tep hong, qua quota va codec khong co van bi tu choi co chu dich.
- Nut native FileChooser duoc kiem danh sach bo loc; test UI truyen danh sach file vao
  handler chung, khong tu dong thao tac hop thoai chon file cua he dieu hanh.
- LAN/firewall giua nhieu may vat ly, TLS/Internet, mat dien, dung luong dia thuc te,
  va thanh toan that/giao hang khong nam trong bang chung tu test loopback nay.
- Vi va QR chi la demo. H2 chi la dependency test, khong duoc dong goi trong EXE; MySQL/XAMPP la noi luu duy nhat cua ban chay that. Test MySQL dung DB btl16_verify_* va tu xoa sau test, khong ghi du lieu mau vao btl_16.
  UI countdown tua dong ho model de kiem moc 60 giay; AuctionJourneySelfTest van cho het han dau gia that.
