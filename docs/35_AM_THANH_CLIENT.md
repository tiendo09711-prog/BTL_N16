# Hieu ung am thanh Client - 2026-10-26

## Thiet ke

Chi Client phat hieu ung; Server va cua so khong thuoc Client khong phat.
Muc tieu la phan hoi nhe, ro nghia, khong bien moi thay doi gia thanh tieng on.
Khong co nhac nen, doc ten/tai khoan, am thanh dang nhap phim hay hover.

| Thao tac / su kien | Hieu ung |
| --- | --- |
| Bam nut, Enter gui lenh, chon nhanh muc gia | Tap ngan 55 ms; CHUA phai xac nhan thanh cong |
| Nut dieu huong, doi tab, vao/roi phong da xac nhan | Hai not nhe 110 ms |
| Dang nhap/dang ky, thong bao hoan tat, ghim/mute/reaction da duoc xac nhan | Hai not tang 240 ms |
| Server chap nhan bid hoac dong bo tim thay bid cua minh | Tin hieu rieng 200 ms, khong phai tieng thang cuoc |
| Bi vuot gia trong phong dang tham gia | Hai not ha 320 ms; gioi han tan suat |
| Tin chat cua minh duoc server xac nhan | Pop tang 100 ms |
| Tin chat moi cua nguoi khac khi dang xem tab khac/cuon len | Hai not 160 ms; khong phat lai lich su |
| Con <=10 giay, mat ket noi/chua xac nhan | Canh bao nhe 260 ms; countdown chi mot lan/phong |
| Du lieu sai hoac server tu choi | Hai not tram 230 ms |
| Server xac nhan dong/huy phien dang tham gia | Ba not trung tinh 420 ms; khong khang dinh ai thang |

Khong phat tieng thanh cong luc dang gui, mat mang hay timeout. Dong ho Client
ve 0 KHONG phat tieng ket thuc/thang. Khong beep theo tung giay/tung gia live.
Khong thong bao am thanh cho moi phong trong sanh. Phan hoi cu sau doi session
van qua cac guard hien co, khong duoc tao tieng xac nhan muon.

## Dieu khien

Menu **Am thanh: bat/tat** co o man dang nhap va sidebar Client:

- Bat/tat hieu ung, thanh am luong 0-100%, nut Nghe thu.
- Mac dinh bat, am luong 32%; tat hoac keo ve 0 dung tieng ngay.
- Luu tuy chon tren may bang Java Preferences; khoi dong lai van giu lua chon.
  Khong gui tuy chon len Server va khong gan vao ho so cong khai/tai khoan.
- Chi thay doi hieu ung, khong sua am luong he thong hay video san pham.
- Khong co thiet bi/khong nap duoc audio thi tu chuyen im lang, khong chan
  dang nhap, chat, bid hay hien hop thoai loi lap lai.

## Tai nguyen / dong goi

Muoi WAV PCM mono 22.05 kHz / 16 bit, moi tep 55-420 ms, tong duoi 120 KB.
Nam trong src/main/resources/audio/client va duoc nap/cache cuc bo, khong tai
qua mang, khong dung FFmpeg, khong ghi tep tam hay cho media san pham.
Tat ca la tone goc tao boi tools/generate-client-sounds.mjs, khong lay nhac/SFX
thuong mai. Tai tao (khong can khi chay app): node tools/generate-client-sounds.mjs.

Policy uu tien canh bao/bid hon tieng bam; dung clip truoc khi phat clip moi,
cooldown theo loai va cache toi da 256 event key de chong lap ack/broadcast.
Phong an danh dung participant alias hien co; audio khong yeu cau avatar,
profile hay username. Khong doi protocol, nonce, mute, luat bid hay database.

Sau khi sua source/audio, dung npm run dist:all de tao lai Windows va ca hai
kien truc Mac. Dong Client cu roi mo EXE o goc dist; process cu khong tu cap nhat.
Mac portable van can JDK 17+ dung kien truc; kiem tra payload tren Windows
khong thay the chay/nghe thu tren macOS that.

## Kiem thu

ClientSoundsSelfTest kiem tra volume/mute/luu tuy chon, cooldown, uu tien,
dedup co gioi han, fallback khi audio hong va header/kich thuoc muoi WAV.
ClientSoundsUiSelfTest kiem tra dieu khien, gioi han Client/owned windows,
pending/accepted/rejected bid, chat canonical va alias an danh, lich su/replay,
countdown/close tu Server, disconnect/callback session cu va giai ma WAV native.
Runner tu tat loa khi test tu dong bang -Dbtl16.client.sound.disabled=true;
test UI dung backend ghi nhan rieng, khong thay doi tuy chon am thanh that.
