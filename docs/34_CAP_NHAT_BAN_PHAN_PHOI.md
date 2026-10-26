# Cap nhat EXE va macOS tu source - 2026-10-26

## Vi sao source co chat moi nhung EXE van cu?

`mvn test-compile`, chay source va chay self-test KHONG thay the goi da phat hanh.
JAR trong target va cac goi trong dist can duoc dong goi lai. Windows va Mac
truoc day co hai lenh rieng, nen de bo sot mot nen tang. Khong chep rieng file
EXE ben trong app-image: giao dien/nghiep vu Java nam trong JAR di kem.

## Mot lenh build tat ca tren Windows

Can JDK 17+ co jpackage, Maven, Node.js, .NET Framework compiler va FFmpeg/ffprobe
da chuan bi theo docs/23. Dat JAVA_HOME/PATH den JDK, sau do:

```powershell
npm run test:packaging
npm run dist:all
```

Lenh build source hien tai, tao lai ca Client VA Server:

- dist/BTL16-Auction-Client.exe va dist/BTL16-Auction-Server.exe (single-file).
- Hai thu muc app-image Windows cung ten, co Java runtime.
- Bon thu muc/archives macOS: Client/Server x arm64 (Apple Silicon)/x64 (Intel).
- Manifest SHA256SUMS cho tung nen tang va BUILD_INFO.json nhan dien build.
- Huong dan phong/chat/ket qua/thong bao, cac SQL migration 03/04/05 trong
  goi Server; KHONG dong goi script reset database.

Sau build se tu chay verify-distributions: so sanh JAR dong nhat, payload EXE
voi toan bo app-image, archive Mac voi thu muc giai nen, JavaFX native dung
kien truc, checksum, compiled chat/reactions/mute/receipt/inbox va khong user data.
BUILD_INFO.json luu thoi gian UTC, SHA-256 JAR va fingerprint src/main + pom.xml.
`npm run test:bundles` tu choi goi cu khi source da thay doi ke ca target JAR
van cu. Phai build lai, khong sua manifest de vuot kiem tra.

`npm run dist` van chi build native Windows (hoac .app native neu chay tren Mac).
`npm run dist:mac:portable` van chi build bon goi Mac portable. Dung dist:all
tren Windows neu can phat hanh dong bo cho ca hai nen tang.

## Chay ban moi ma khong mat du lieu

1. Cho phien dang dien ra ket thuc, thoat Client va Server cu binh thuong.
   Build KHONG tu tat ung dung/server dang chay, khong can thiep phien dau gia.
2. Chay lai EXE o GOC dist (hoac giai nen TOAN BO goi Mac moi). Cap nhat
   ca Server va Client, khong tron Server cu voi Client moi.
3. Server giu MySQL config hop le san co khi dong goi, KHONG reset/seed hay
   sao chep database vao goi. Sao luu MySQL truoc nang cap. autoInitialize=true
   them schema thieu khi Server moi khoi dong; false thi lam migration theo
   docs/29, docs/30, docs/31. Khong chay 00_schema.sql de nang cap DB da co.
4. EXE single-file giai nen vao AppData/Local/BTL16/DesktopApps/<app>-<hash>.
   Build moi co hash moi, khong dung lai cache cu. Process cu dang mo van la
   code cu; build khong tu bien no thanh code moi. Khong can xoa cache cu.
5. Neu dung shortcut, kiem tra Target tro den EXE moi o dist, KHONG tro
   thang vao thu muc cache AppData cua build cu.

## Gioi han macOS

Cross-build tren Windows tao portable .tar.gz voi JavaFX Mac dung kien truc,
KHONG tao .app native/co chu ky hay macOS runtime. Can macOS JDK 17+ dung
kien truc de chay .command; Server can MySQL va FFmpeg/ffprobe cho media.
Chua the khang dinh chay GUI tren Mac that tu kiem tra payload o Windows.
De tao .app kem Java runtime, chay `npm run dist` tren Mac dung kien truc.

Ket qua kiem tra dot phat hanh: dist/VERIFICATION.md va checkpoint.
