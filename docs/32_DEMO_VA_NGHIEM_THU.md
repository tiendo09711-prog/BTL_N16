# Demo va nghiem thu Cum 1-6

Ngay: 2026-10-25. Pham vi demo: dau gia va vi MO PHONG; khong giao hang,
hoan tien that, KYC, danh gia uy tin hay quy trinh tranh chap.
Trang thai kiem thu/build chinh xac: xem docs/DEMO_UPGRADE_STATE.md va
docs/33_KET_QUA_NGHIEM_THU.md. Khong dung cac ban macOS cu de demo ban nang cap nay.

## Tong hop 1-5
1. Dat gia: guard pending, muc gia nhanh, trang thai dan dau/vuot gia, hold vi,
   dong bo lai sau reconnect va bo callback cu khi doi phong/session.
2. Phong truc tiep: bieu do/lich su, thong ke server, nhat ky/gia han va hien dien;
   chat/avatar/ho so an toan, bi danh an danh theo phong.
3. Tuong tac: tra loi theo tin that cua server, reaction desired-state chong lap,
   ghim thong bao, mute chat co thoi han NHUNG van duoc bid.
4. Ket qua server chot, bien nhan rieng luu ben, tra cuu tham gia/thang/to chuc,
   link ledger cua minh. Dan dau truoc chot KHONG co nghia da thang/da thanh toan.
5. Sanh loc/sort/paging, xem lai tao phong, mau mo ta va giai thich dong bang,
   preview public profile, loc vi/ly do tu choi nap, inbox ca nhan, local ops log.

## Chuan bi (khong tu seed/reset production)
- Mot may Windows server chay MySQL/XAMPP, hai cua so/may khach A va B; host H
  co the chay client tren may server. Tao/dang nhap ba tai khoan thu cong trong
  DB demo RIENG da duoc nguoi phu trach chon; khong dung tai khoan that.
- Sao luu MySQL va server.properties; xac nhan db.name/host/port dung. Neu DB cu,
  cho initializer nang schema mot lan (docs/30,31), khong chay DROP/reset scripts.
- Chuan bi it nhat 4 mat hang khac nhau: anh PNG/JPEG hop le va mo ta 50-2000 ky tu
  ve dac diem, tinh trang, phu kien, khiem khuyet. Khong tai su dung mat hang da ban.
- A/B nap demo 1.000.000, operator duyet tren server; tao them mot yeu cau nap
  de tu choi voi ly do "Sai noi dung demo". QR khong lien quan ngan hang that.
- Gia khoi diem 100.000, buoc 10.000, thoi luong 1 phut. Gia han tu dong mac dinh
  khi bid trong 10 giay cuoi: them 10 giay, co hard deadline server.

## Kich ban 8-12 phut
1. H tao san pham: dung mau mo ta, them anh, luu. Mo public profile preview:
   chi ten hien thi/ngay tham gia/avatar/gioi thieu, khong email/dien thoai/vi.
2. H tao phong PUBLIC thuong: xem lai san pham/gia/buoc/thoi luong/quyen/danh tinh;
   huy xac nhan mot lan (khong tao), sau do xac nhan. Bam lap khong tao trung.
3. A/B tim phong o sanh (loc/gia/thoi gian), vao, gui chat/tra loi/reaction;
   H ghim thong bao, mute A. A chat bi chan. Khi CHUA CO BID, A va B cung gui
   110.000 gan dong thoi: toi da mot bid thanh cong. Neu B thang luot nay, A
   dat 120.000 de chung minh mute khong chan bid; neu A thang, bid 110.000 da chung minh.
4. Nguoi dang dan dau bi tu choi neu dat tiep; 105.000/so am/sai dinh dang bi
   tu choi. Khach khong dan dau thu 2.000.000 khi chi co 1.000.000: thieu tien,
   khong ghi bid. Bid hop le tiep theo tao hold/release va thong bao vuot gia.
5. Bid hop le trong 10 giay cuoi: thoi gian/gia han doi theo server, chart/tong
   bid/unique bidders/nhat ky cap nhat. Ngat ket noi A roi noi lai; khong gui lap.
6. Cho server chot tu nhien. H/A/B xem role rieng va ket qua; winner/gia chot,
   tong bid/nguoi bid/gia han. Mo receipt -> giao dich vi CUA MINH; PAID chi khi
   ledger/settlement xac nhan. Dong popup/roi phong; tra cuu lai tu sidebar.
7. Tao mat hang moi + PRIVATE ANONYMOUS: B nhap sai mat khau bi chan; dung mat khau
   vao duoc, chi thay Khach #n/avatar trong, profile bi an. Lap bid/chat/reaction;
   kiem tra payload theo cac selftest, khong chi dua vao giao dien.
8. Cho chot; restart app/server dung quy trinh, dang nhap lai va mo bien nhan.
   Van la bi danh; receipt KHONG mo khoa chat/phong rieng trong session moi.
9. Hai mat hang con lai: huy phong chua bid, ket thuc phong khong bid. Receipt
   phan biet CANCELLED/NO_BIDS va khong hien gia khoi diem nhu gia da ban.
10. Vi: loc phien/loai/ngay, xem ly do tu choi nap; inbox doc/chua doc, mo receipt/vi.
    Operator: loc log theo ma phien/BID_ACCEPTED/time, khong payload nhay cam.

## Checklist truoc buoi trinh bay
- [ ] Chon DUNG Windows client/server moi, kiem SHA256SUMS.txt, khong dung EXE cu.
- [ ] MySQL/XAMPP san sang; backup va config duoc giu; migration schema da xong.
- [ ] H/A/B dang nhap dung tai khoan, tien kha dung du va san pham chua ban.
- [ ] Cung LAN tin cay, tat AP/client isolation; copy dia chi hien tai tu server,
      khong hardcode dia chi cu. Firewall Private cho 8890 WebSocket va 8891 media;
      8888 TCP chi neu bat. Khong mo MySQL ra may khach/Internet.
- [ ] Tu hai may khach mo room, chat, anh/video va bid; kiem payload an danh.
- [ ] Rut Wi-Fi/cap LAN THAT khi request bid cho: UI khoa gui, khong tu gui lai;
      reconnect resync server/vi, chi mot bid/hold. Doi phong trong luc cho khong
      de response cu doi gia/chat/vi cua phong moi.
- [ ] Mat mang dung luc ket thuc: UI khong tu phan winner/payment; sau reconnect
      receipt khop ledger va restart van tra duoc.
- [ ] Kick khach chua bid: khong vao lai/lay chat/roster; receipt rieng neu da tham
      gia chi cho summary. Session logout/het han bi chan o server.
- [ ] Laptop va cua so nho: composer, nut dat gia va ket qua quan trong truy cap
      duoc; scroll media/ledger khong che nut. Kiem DPI man hinh trinh chieu.
- [ ] Dong app dung cach, khong reset DB. Ghi rieng buoc chua test/loi don test.

Checklist GUI/LAN nay can nguoi trinh bay danh dau sau khi thao tac thuc te;
anh/test JavaFX tu dong khong thay the nghiem thu thu cong qua LAN.
