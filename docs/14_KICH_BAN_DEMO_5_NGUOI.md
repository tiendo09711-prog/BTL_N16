# 14 - KICH BAN DEMO CHIA CHO 5 NGUOI

## Phan 1 - Do Tien, 2-3 phut

- Gioi thieu mot server nhieu client.
- Mo server dashboard.
- Giai thich framing, worker threads, router, session.
- Login ba client.
- Chi ra connection/session tren dashboard.

## Phan 2 - Tran Van Phuoc, 2 phut

- Tai danh sach auction.
- Chon san pham.
- JOIN room.
- Giai thich snapshot, watcher count va DB relation.

## Phan 3 - Pham Anh Dung, 3 phut

- Hai client dat gia.
- Cho thay accepted/rejected.
- Chay load test 5-10 client.
- Giai thich per-auction lock va transaction.

## Phan 4 - Mai Trung Duc, 2-3 phut

- Cho countdown.
- Bid trong 10 giay cuoi.
- Demo +10 giay.
- Cho phien ket thuc va result.
- Giai thich 120 giay visibility truoc khi archive.

## Phan 5 - Vu Tri Thuan, 2-3 phut

- Chi ra BID_UPDATE va OUTBID notification.
- Tat mot client/network.
- Reconnect, resume, resync.
- Trinh bay self-test va load test.
- Cho client nhan `AUCTION_ARCHIVED` va tu xoa phong.

## Phan nang cap - moi nguoi 1 tinh nang

- Do Tien: giai thich request/event, auth route, config retention va composition.
- Tran Van Phuoc: them/sua/an san pham, tao phong, host ownership va an list/archive.
- Pham Anh Dung: minimum increment, chan host bid va race kick/bid/archive.
- Mai Trung Duc: gia han, ket thuc/huy va timer archive sau 120 giay.
- Vu Tri Thuan: dialog client, event created/cancelled/kicked/archived va self-test.

Kich ban lien mach:

```text
demo tao product/phong
-> alice join va bid
-> demo gia han
-> demo kick alice
-> alice join lai bi chan
-> demo ket thuc
-> ket qua con hien trong visibility window
-> server gui AUCTION_ARCHIVED, client/dashboard xoa phong
-> tao phong khac va huy khi chua co bid
```

## Tong ket - Do Tien

```text
Client chi gui request va hien thi.
Server quyet dinh moi state chinh thuc.
Do wow nam o TCP, realtime, concurrency, timer va reconnect.
```

## Kich ban nang cap ba may

- May A chay `npm run dev:server`, in TCP va WebSocket endpoint.
- May B dung JavaFX tao product co anh va private room.
- May C search, join bang password va bid realtime.
- Mot client TCP tao bid de chung minh cross-transport.
- Ngat mang C, resume/resync khong hoi password; host kick va end room.
- Nhan manh server authoritative, PBKDF2, lock/transaction va MySQL ownership.
