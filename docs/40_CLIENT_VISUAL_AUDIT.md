# Client visual audit

## Pham vi va cach kiem tra

Client dang chay la JavaFX (`ClientMain` -> `JavaFxClientApp`), khong phai
trang web. Kiem thu desktop dung scene, control, layout va luong xu ly that
cua JavaFX. Playwright mo bao cao anh, kiem tra tai anh/font, tim kiem,
phong to, xem thanh phan va bo cuc bao cao hep; khong duoc xem cac buoc nay
la thao tac truc tiep len cua so desktop.

`tools/client-visual-audit.mjs` kiem ke moi file trong `client/fx`, cac vi tri
tao Stage/Dialog/Alert, so lan tao nut/nhan va test UI tham chieu truc tiep.
Ket qua luu tai `target/client-visual-audit/inventory.json`. Tham chieu test
la ban do ma nguon, khong phai bang chung moi trang thai deu duoc bao phu.

## Sua o lop dung chung

- `DesktopTheme` tai font Be Vietnam Pro cho ca dialog mo truoc scene chinh;
  ap dung theme lap lai khong nhan ban stylesheet.
- Dialog mac dinh co header voi bieu tuong vector, nhan phan loai, tieu de
  xuong dong; khong con icon dau hoi/xanh va bo cuc header Modena cu.
- Ho tro Alert, TextInputDialog, ChoiceDialog va Dialog thuong. Cac hop
  thoai da thiet ke rieng (tao phien, ho so, quan ly phong...) giu header rieng.
- Noi dung co the cuon doc lap. Thanh nut khong cuon theo form; van vua
  vung lam viec man hinh va cua so thap. Khong boc long mot viewport san co.
- Viet hoa nut mac dinh nhung giu nguyen ButtonType, result converter,
  Enter/Escape va nut do caller tu dat ten. Thong bao dung "Da hieu";
  xac nhan dung "Xac nhan". Chu hien thi thuc te co dau tieng Viet.
- `desktop-surfaces.css` bo sung be mat dong nhat cho tab, danh sach,
  popup/menu, date picker, spinner, checkbox/radio, titled pane, slider,
  progress va dialog. Co nen, vien mem, do bong nhe va trang thai focus.
- Tang do doc cua chu phu tren sidebar, the san pham, bang thong bao va
  trang tai khoan. Giu bang mau nga/vang dong hien co; khong doi nghiep vu.
- Chu dung font vector va grayscale antialias, tranh vien mau subpixel
  tren be mat trong suot va anh snapshot. Do net thuc te van phu thuoc DPI,
  man hinh va cau hinh render cua may; khong sua ti le hien thi cua Windows.

## Hop thoai dat coc

`RoomDepositDialog` thay doan van dai bang cac khoi rieng:

1. Tien dat coc noi bat, ten san pham va so phong.
2. Han hoan tat truoc gio bat dau.
3. Chinh sach giu tien va hoan tien, danh so tung muc.
4. Luu y dat coc khong giu cho va hai nut "Dat coc ngay" / "De sau".

Dialog chi thu thap xac nhan. Request dat coc van nam trong controller,
chi gui sau ket qua `ButtonType.OK`; huy khong gui lenh dat coc.

Anh mau: `target/client-visual-audit/after/room-deposit.png`.
Anh cua so hep/thap va noi dung cuon den cuoi nam cung thu muc.

## Trinh quan ly media

`ProductMediaEditor` dung cung heading co icon voi cac trang tai khoan.
Form thong tin va vung xem truoc tach thanh hai card sang, vien mem va bong
nhe. Thanh cong cu cat anh co trang thai chon ro rang. Anh khong bi ep thanh
mot dai mong khi toolbar xuong dong: gallery giu chieu cao toi thieu va cuon
doc lap, trong khi nut Quay lai/Luu san pham van co dinh o chan cua so.

`MediaUiSelfTest` kiem tra chieu cao anh trong cua so thap, upload that,
cat/xoay/hoan nguyen, video va huy. Anh viewer cho doi anh duoc chon tai xong
sau khi thoat fullscreen, tranh chup trang thai dang tai trong bao cao.

## Ban do kiem thu

| Nhom | Kiem thu / anh |
| --- | --- |
| Dang nhap, loi, dang ky, doi mat khau, mat khau phong, nhat ky | LoginFeedbackUiSelfTest, DialogVisualUiSelfTest, DesktopUiSelfTest |
| San dau gia, the/grid/list, tim kiem, bo loc, phien noi bat | MarketplaceUiSelfTest, CompletionUiSelfTest, DesktopUiSelfTest |
| Tao phong, xem lai, hen gio, an danh, dieu kien san pham | CompletionUiSelfTest, ClientViewportUiSelfTest, CashFlowAndAnonymousUiSelfTest, AccountScreensUiSelfTest |
| Phong truc tiep, san pham, dien bien, bid, lich su, chat | RoomCenterTabsUiSelfTest, LiveAuctionRoomUiSelfTest, BidExperienceUiSelfTest, RoomProgressUiSelfTest, RoomSocialUiSelfTest, RoomInteractionUiSelfTest |
| Ma phong, khach tham gia, ghim thong bao | RoomCodeUiSelfTest, RoomManagementUiSelfTest |
| Vi, QR, nap/rut, giao dich | WalletUiSelfTest, AccountScreensUiSelfTest, CompletionUiSelfTest |
| San pham, media, xoa, ma san pham | MediaUiSelfTest, ProductDeletionUiSelfTest, ProductCodesUiSelfTest, AccountScreensUiSelfTest |
| Ket qua, bien nhan, chi tiet | AuctionReceiptUiSelfTest, AccountScreensUiSelfTest |
| Thong bao, phan trang, chon/xoa nhieu | PersonalNotificationsUiSelfTest, CompletionUiSelfTest |
| Ban be, loi moi, tin nhan rieng, dang xuat | FriendsUiSelfTest, FriendsSessionUiSelfTest |
| Top doanh thu, luat, ho so, am thanh | RevenueRankingUiSelfTest, CashFlowAndAnonymousUiSelfTest, AccountScreensUiSelfTest, ClientSoundsUiSelfTest |
| Ket thuc, bieu ngu dan dau, chuc mung | AuctionEndingUiSelfTest, BidLeadingBannerUiSelfTest |
| Thanh phan dung chung va cua so thap | DialogVisualUiSelfTest, ClientViewportUiSelfTest |
| Regression theme dung chung phia server | ServerAdminTabsUiSelfTest, ServerButtonsUiSelfTest |

`DialogVisualUiSelfTest` chup rieng header, body, action bar, luu text/font
va toa do control trong `after/screens.json`. Bao gom form thuc te duoc mo
tu FxClientController, khong phai ban sao HTML. Kiem tra nut khong ra ngoai
cua so, ket qua nhap/chon, huy, va viec huy form khong gui mutation tai khoan.

Cac fixture tao phong cung nhap so nguoi toi da theo quy tac hien tai;
kiem thu doi phong xac nhan leave truoc join; bo loc vi bao gom dat/hoan coc.
Day la cap nhat du lieu/ky vong test, khong noi long validation san pham.

## Chay lai

```powershell
npm run test:full
node tools/client-visual-audit.mjs
```

Chi chay lai cac test UI (khong lap cac han dau gia that cua backend):

```powershell
node tools/run-tests.mjs --ui-only
```

Co the dung `--only=DialogVisualUiSelfTest,RoomCodeUiSelfTest` de chon nhom
kiem thu; log rieng nam o `target/qa-focused/`. Ten nhom khong hop le bi bao loi.
Runner UI mac dinh dung `prism.order=sw` de anh lap lai duoc, khong phu thuoc
driver GPU cua may chay test; khong ep client san pham sang software renderer.
Bien `BTL16_TEST_PRISM` cho phep chon renderer khi chan doan. Lan kiem thu
GPU tren may nay in loi `Illegal texture dimensions (-1x-1)` trong renderer
D3D o test ban be/bid; cac assertion van qua. Cac nhom nay duoc chay lai
bang software renderer de tach ket qua layout/tuong tac khoi loi driver.

Log bo day du o `target/qa/`, log lan chay UI rieng o `target/qa-ui/`.
Bao cao hop ket qua theo ten test, uu tien ket qua UI/focused moi hon; anh cu truoc
lan QA duoc loai khoi gallery, ngoai tru bo anh baseline `before/`.

Chay Playwright khi da co Chromium tuong ung:

```powershell
npm exec --yes --package=playwright -- node tools/client-visual-audit.mjs --playwright
```

Neu can cai browser truoc: `npm exec --yes --package=playwright -- playwright install chromium`.
Co the dung Playwright da cai san bang bien `PLAYWRIGHT_MODULE` tro den
`index.mjs` cua package. Khong can them browser vao runtime JavaFX.

Mo `target/client-visual-audit/index.html` de tim theo ten man hinh, loc
before/after/client, xem anh kich thuoc goc va cac crop thanh phan.
Ket qua browser o `playwright-results.json`; anh bao cao o `report-*.png`.

## Ket qua ngay 30/10/2026

- Kiem ke 44 file JavaFX client; gallery co 144 anh man hinh (gom baseline)
  va 103 crop thanh phan. Bo dialog rieng bao gom 26 trang thai desktop/compact.
- Ket qua moi nhat theo tung nhom: 34/34 qua, gom 31 nhom UI, compile,
  backend `AllSelfTests` va packaging-unit. Chi tiet o `inventory.json`.
- Lan UI tong chay 31/32 nhom: ca `ClientSoundsUiSelfTest` bi race giua
  loi doc bid response va kich ban broadcast tiep theo. Fixture nay da doi
  loi doc truoc khi sang kich ban moi, khong sua engine am thanh; chay lai
  rieng hai lan deu qua. Luu nguyen log lan tong o `target/qa-ui/`.
- Luot focused cuoi qua ca viewport, sound va media (4/4 tinh ca compile).
  Maven package thanh cong; Node syntax va `git diff --check` khong co loi.
- Playwright qua 3 nhom kiem tra bao cao: tai du 247 anh, tim/phong to anh,
  bo cuc hep. Chromium 153.0.8010.12; khong dieu khien JavaFX bang browser.

## Gioi han

- Anh va test dung tai khoan/du lieu fixture; khong thay doi du lieu phien
  dang mo cua nguoi dung, khong dong hai client hay server dang chay san.
- Khong the bao phu moi to hop du lieu, quyen va thoi diem bat dong bo chi
  bang anh. Doc log test va inventory cung gallery de danh gia pham vi.
- Cua so chon file, thanh tieu de va vien cua so do Windows ve khong phai
  Modena; khong thay chung bang HTML hay sua cau hinh he dieu han.
- Sau khi build can khoi dong lai client dang mo de nap class/CSS moi.
- Maven package khong cap nhat EXE phan phoi cu; neu dung EXE thi can dong
  goi lai bang `npm run dist:client` truoc khi khoi dong ban phan phoi.
