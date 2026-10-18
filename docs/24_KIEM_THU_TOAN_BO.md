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
- `test:full`: them nut bam JavaFX, dashboard/login, editor/gallery/video va khoi phuc san pham.
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
  va thanh toan/giao hang khong nam trong bang chung tu test loopback nay.
