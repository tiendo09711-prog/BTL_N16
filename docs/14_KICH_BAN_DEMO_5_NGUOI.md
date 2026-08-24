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

## Phan 5 - Vu Tri Thuan, 2-3 phut

- Chi ra BID_UPDATE va OUTBID notification.
- Tat mot client/network.
- Reconnect, resume, resync.
- Trinh bay self-test va load test.

## Phan nang cap - moi nguoi 1 tinh nang

- Do Tien: giai thich request moi, auth route va schema migration.
- Tran Van Phuoc: them/sua/an san pham, tao phong va host ownership.
- Pham Anh Dung: minimum increment, chan host bid va race kick/bid.
- Mai Trung Duc: gia han thu cong, ket thuc som va huy phong.
- Vu Tri Thuan: dialog client, event created/cancelled/kicked va management self-test.

Kich ban lien mach:

```text
demo tao product/phong
-> alice join va bid
-> demo gia han
-> demo kick alice
-> alice join lai bi chan
-> demo ket thuc
-> tao phong khac va huy khi chua co bid
```

## Tong ket - Do Tien

```text
Client chi gui request va hien thi.
Server quyet dinh moi state chinh thuc.
Do wow nam o TCP, realtime, concurrency, timer va reconnect.
```
