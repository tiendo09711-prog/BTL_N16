# Dat coc phong sap dien ra va gioi han so cho

## Tao phong

- Moi phong moi, cong khai/rieng va thuong/an danh, bat buoc nhap
  `maxParticipants`: so nguyen tu 1 den 10000. Form khong dien san gia tri.
- Phong sap dien ra bat buoc nhap `depositAmount` duong, toi da hai chu so
  thap phan. Phong mo ngay khong thu coc. Khong thay doi hai gia tri sau tao.
- Phong hen gio chua cho vao truoc `startTime`. Nguoi mua chon phong trong
  sanh de dat coc; phong rieng van can mat khau hop le.
- Client kiem tra capability `roomAdmission=true` va `auctionScheduling=true`
  truoc khi gui tao phong hen gio; server cu khong duoc am tham bo qua luat.

## Dat coc va tien dau gia

- Chi nhan du so tien coc truoc gio bat dau; khong ho tro coc tung phan.
  Server tu lay muc coc trong DB, khong tin so tien client gui.
- Tien coc duoc giu trong vi, khong phai phi chuyen ngay cho nguoi ban.
  Vi thieu kha dung thi khong ghi nhan coc va khong giu tien mot phan.
- Gui lai yeu cau coc khong tru lan hai, ke ca sau khi server khoi dong lai.
- Chu phong khong can coc. Nguoi khac chua coc du truoc gio bat dau thi
  khong vao duoc, ke ca khi phong van con cho. Nguoi bi chan khong vao duoc.
- Coc khong giu cho: nguoi da coc van phai cho neu phong day.
- Khi dat gia, coc duoc tinh vao so tien dang giu, khong giu trung hai lan.
  Khi bi vuot gia, phan coc van duoc giu de bao dam quyen tham gia.
- Ket thuc/huy: hoan coc nguoi khong thang. Coc nguoi thang bu vao gia mua;
  phan coc vuot gia mua duoc hoan. Phong khong co bid cung hoan coc.
- Roi phong/mat ket noi chi giai phong cho, khong tu huy coc hoac cam ket bid.
  Nguoi da coc co the vao lai neu con cho va phien van mo.
- Chu phong chi kick nguoi chua tung bid theo luat hien co; coc cua nguoi bi
  kick duoc hoan, nhung nguoi do van bi chan khoi phien.

## So cho va cap nhat realtime

- Mot tai khoan dang co mat tinh mot cho, ke ca chu phong va nguoi chi xem.
  Nhieu ket noi cua cung tai khoan khong chiem nhieu cho.
- Khong tinh theo lich su dat gia hay danh sach nguoi da coc. Tai khoan chi
  het chiem cho khi ket noi cuoi cung cua tai khoan roi phong.
- Kiem tra va cap cho la thao tac nguyen tu: hai nguoi tranh cho cuoi cung
  khong the cung vao thanh cong. Chu phong khong vuot gioi han.
- Roi phong, dang xuat, mat ket noi hoac kick giai phong cho. Sanh va phong
  nhan `ROOM_OCCUPANCY_CHANGED` voi `auctionId`, `watcherCount`,
  `occupancyVersion`; khong gui danh tinh nguoi tham gia ra sanh.
- Client giu ban cap nhat co version moi nhat; snapshot/danh sach cu den
  muon khong ghi de so nguoi moi. Khong can bam tai lai sanh de thay cho trong.
- `watcherCount` tren wire la so tai khoan dang co mat. So lieu ky thuat
  `watcherConnectionCount` van dem ket noi theo nghia cu.

## Database va nang cap

- `DatabaseSchema.initialize` them `auctions.max_participants`,
  `auctions.deposit_amount` va bang `auction_deposits` theo cach idempotent.
  Khong reset DB, khong sua ledger cu. Nen sao luu MySQL truoc khi nang cap.
- Phong cu mac dinh 100 cho, coc 0 de giu tuong thich; moi phong hen gio
  tao moi qua API bat buoc nhap coc duong.
- `sql/00_schema.sql` chua cot cho DB moi; `sql/06_room_admission.sql` tao
  bang coc. Rieng file 06 khong nang cap cot cua bang auctions cu: can chay
  migration Java khi nang cap, ke ca neu quan tri tat `db.autoInitialize`.
- Moi cap `(auction_id, user_id)` chi co mot dong coc. Ledger tham chieu
  `DEPOSIT:<auctionId>:<userId>` va `DEPOSIT_RELEASE:<auctionId>:<userId>`.
- Can build/cap nhat va khoi dong lai ca Server va Client; EXE dang chay
  khong tu nhan thay doi ma nguon. MySQL san xuat khong duoc thay bang H2.

## Kiem thu

- `RoomAdmissionSelfTest`: validation, tranh cho cuoi, vao lap, thoat/dang
  xuat/mat ket noi, realtime, coc thieu/nhap sai mat khau/thu lap, tai khoi
  dong, giu tien bid, hoan coc va snapshot den sai thu tu.
- `CompletionUiSelfTest`: bat buoc nhap so cho/coc va kiem tra payload tao.
- Chay compile va bo hoi quy (PowerShell, H2 chi duoc dung trong test):

```powershell
mvn -q -DskipTests test-compile
$ffmpeg = (Resolve-Path tools/media/ffmpeg.exe).Path
$ffprobe = (Resolve-Path tools/media/ffprobe.exe).Path
java '-Dbtl16.test.allowEmbedded=true' "-Dbtl16.ffmpeg=$ffmpeg" "-Dbtl16.ffprobe=$ffprobe" -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.AllSelfTests
```
