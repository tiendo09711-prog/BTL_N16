# Giao dien quan ly phong dau gia

## Moi khach ra khoi phong

- Nut quan ly duoi san pham va nut "Moi ra" trong danh sach thanh vien
  cung dung `RoomParticipantsDialog`; khong con yeu cau nhap username.
- Lay danh sach moi tu server moi khi mo. Co tim theo ten hien thi,
  checkbox chon nhieu nguoi, chon tat ca khach hop le dang duoc loc,
  so nguoi da chon va tai lai danh sach. Loc khong lam mat lua chon.
- Phong thuong hien ten do server tra ve; phong an danh chi hien bi danh
  va gui participantId cua phong, khong tra cuu ho so hay username that.
- Chu phong khong nam trong danh sach can moi ra. Nguoi da dat gia van
  hien trong danh sach nhung bi khoa lua chon va co ly do bao ve.
- Mot lan bam xu ly tuan tu tung nguoi da chon qua API hien co. Server
  kiem tra lai quyen, trang thai phien va lich su bid cho tung lenh.
  Day khong phai giao dich nguyen khoi: neu mot nguoi vua dat gia hoac
  da roi phong, cac lenh khac van duoc xu ly va ket qua duoc bao rieng.
- Nguoi da moi ra thanh cong khong duoc gui lai khi thu lai. Tai lai sau
  xu ly de cap nhat nguoi vua dat gia; dung khi mat ket noi hay doi phien.
- API hien tai tra toi da 200 thanh vien, gom chu phong. Neu phong dong
  hon, hop thoai hien ro gioi han nay; tim kiem chi loc danh sach da tai.

## Ghim va tro chuyen

- Khong hien the ghim rong. Nut tao/sua ghim nam tren tieu de chat;
  noi dung ghim, nguoi dang va thoi gian cap nhat nam trong the rieng.
- Thong bao dai co "Xem day du" / "Thu gon", khong phai dua vao tooltip.
- `RoomAnnouncementDialog` co dem 500 Unicode code points, khoa luu
  noi dung rong/qua dai va nut bo ghim rieng. Huy khong thay doi du lieu.
- Tang khong gian doc chat, gom cac nut quan ly thanh vien thanh hang
  rieng de khong ep ten va ly do thanh nhung cot hep.

## Lich su

- Tab ben phai hien cac luot gan day bang the, muc gia tren dong rieng,
  bo loc luot cua toi va nut xem lich su day du.
- Hop lich su day du mac dinh rong 860 px; co 4 cot thoi gian server,
  nguoi dat, so tien, muc tang. Khi noi dung hep hon 620 px, doi sang
  the doc de tranh cat tieu de cot va so tien.
- Giu nguyen cursor va moc snapshot tu server, bo loc ca nhan,
  trang cu hon, tai moi va chan callback cu sau khi dong hop thoai.
  So luot hien thi la so luot trong trang, khong phai tong ca phien.

## Kiem thu

- `RoomManagementUiSelfTest` dung TCP that: phong thuong/an danh,
  nhieu checkbox, tim kiem giu lua chon, khoa nguoi da bid, hai nguoi
  bi moi ra thanh cong va mot nguoi vua bid bi tu choi trong cung batch,
  chan lenh offline/phien cu, gioi han Unicode cua ghim, mo rong ghim,
  lich su tren cua so rong/hep. Da dang ky trong `npm run test:full`.
- Anh xem truoc: `target/room-management-preview/`.
- Log kiem thu muc tieu: `target/room-management-qa/`.
- Khong doi schema, protocol hay quy tac dau gia.
