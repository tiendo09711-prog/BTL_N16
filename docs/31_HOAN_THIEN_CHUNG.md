# Cum 5 - Hoan thien cac man hinh hien co

## Sanh va tao phong
- Loc cong khai/rieng, khach thuong/an danh, phien toi to chuc va tim theo ten/ma.
  Sap xep thoi gian ket thuc/gia tang/giam tren TOAN BO snapshot server, sau do
  moi chia 30 dong/trang. Hien tai LIST_AUCTIONS khong co paging server.
  Khong sap xep rieng tung trang bang header cua TableView.
- Phien con <=60 giay duoc to mau; nhan quyen vao va danh tinh la hai thuoc tinh
  doc lap. Ket qua phien cua toi la loi vao tra cuu/bien nhan cua Cum 4.
- Tao phong: nhap -> Xem lai -> xac nhan -> gui. Khoa thao tac trong luc tai form,
  xem lai va cho server; callback cu sau doi session bi bo. Timeout khong tu gui lai.
- Khong doi san pham/quyen vao/danh tinh sau khi tao. Metadata/media bi dong bang
  sau phien dau tien (ke ca huy), server van la noi quyet dinh.
- Mau mo ta gom dac diem/tinh trang/phu kien/khiem khuyet; xac nhan truoc khi
  ghi de noi dung. Khong doi kiem tra mo ta 50-2000 ky tu va media hien hanh.

## Ho so, vi va thong bao
- Ho so rieng tach khoi avatar/gioi thieu cong khai. Xem truoc chi dung whitelist
  ten hien thi, ngay tham gia, avatar, biography cua API public. Khong them email,
  dien thoai, wallet hay session. Phong an danh khong mo ho so khach.
- Vi/QR CHI MO PHONG. Kha dung = so du - dang giu; roi phong khong xoa hold.
  Thanh toan dua tren ledger/settlement, khong dua tren nguoi dang dan dau.
- GET_WALLET_ENTRIES lay owner tu session; loc ma phien, loai, tu ngay (bao gom),
  den ngay (bao gom o UI, chuyen thanh moc tren loai tru) truoc LIMIT 50, keyset ID.
  Tra cuu ledger cua minh khong cap quyen doc vi/hold/chat cua nguoi khac.
  GET_WALLET theo room van giu nguyen gate phong rieng/kick.
- Tu choi nap co ly do 1-300 ky tu; review lap giu nguyen ket qua/ly do da chot.
- Thong bao: OUTBID, AUCTION_RESULT, TOPUP_APPROVED/REJECTED duoc luu cung transaction
  voi bid/receipt/review. Unique source_key chong lap; khong luu ten nguoi dat gia,
  winner account, chat hay noi dung giao dich cua nguoi khac. Doc/chua doc owner-only.
  Lam moi dinh ky 5 giay o trang dau; khong append lap sau reconnect.
- Lien ket ket qua mo receipt rieng; nap mo vi rieng; vuot gia tim phong trong sanh
  de vao bang gate hien co. Thong bao KHONG la giay phep vao phong rieng/chat/ho so.

## Luu tru / migration / van hanh
- Them personal_notifications (sql/05) va wallet_topups.rejection_reason. Khoi tao
  schema tu dong idempotent, khong reset, khong sua ledger/receipt cu, khong backfill
  thong bao cu. Backup MySQL truoc khi nang cap; cai db.autoInitialize=false can
  duoc nang schema co kiem soat bang DatabaseSchema.initialize.
- Thong bao giu cung vong doi account/auction, khong co TTL moi. FK cascade khi
  account/auction bi xoa theo chinh sach hien hanh; dong/luu tru runtime KHONG xoa.
  Bien nhan va chat van theo chinh sach Cum 2-4, khong mo rong quyen giu/xem chat.
- Nhat ky van hanh chi co cua so local server, tai su dung room_events Cum 2.
  Loc phien/loai/thoi gian truoc keyset 50 dong. Chi projection ID/phien/loai/time/amount;
  khong actor/detail/payload/password/token. Khong them RPC admin hay dashboard.
- Cac phan moi co loading/empty/error/thu lai va bo callback cu theo generation/session.

Neu da tat auto-initialize, dung cong cu SETUP hien co (KHONG ResetMain) mot lan
voi dung config va MySQL da backup:

```powershell
java -Dbtl16.server.config=config/server.properties -cp "target/classes;target/dependency/*" vn.ptit.btl16.server.db.DatabaseSetupMain
```

Lenh setup khong tao tai khoan/san pham demo; chi nang schema idempotent va backfill
receipt da xac nhan theo Cum 4. Khong chay lenh nay vao DB khac voi config da duyet.
