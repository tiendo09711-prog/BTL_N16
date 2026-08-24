# 07 - KICH BAN DEMO VA KIEM THU

## Demo 1 - Nhieu client cung xem mot room

1. Mo server.
2. Mo ba client: demo, alice, bob.
3. Ca ba chon cung auction va bam `Vao phong`.
4. Demo dat gia.
5. Quan sat ca ba nhan `BID_UPDATE`.

Chung minh:

- multiple connections;
- room/subscriber;
- server push event;
- output stream cua moi connection duoc dong bo.

## Demo 2 - Bid dong thoi

Chay:

```bat
scripts\run-load-test.cmd 10
```

Hoac hai nguoi bam Dat gia gan cung luc.

Chung minh:

- worker threads;
- per-auction lock;
- kiem tra lai currentPrice sau khi lock;
- DB transaction;
- chi mot authoritative final state.

## Demo 3 - Anti-sniping

1. Chon auction ngan.
2. Cho countdown con duoi 10 giay.
3. Gui bid hop le.
4. Tat ca client nhan `AUCTION_EXTENDED`.
5. End time tang 10 giay.

Chung minh timer nam o server, client chi hien thi.

## Demo 4 - Outbid notification

1. Demo dang dan dau.
2. Alice bid cao hon.
3. Tat ca room nhan `BID_UPDATE`.
4. Rieng Demo nhan `OUTBID_NOTIFICATION`.

Chung minh broadcast room va unicast user khac nhau.

## Demo 5 - Disconnect/reconnect

1. Client dang o trong room.
2. Tat Wi-Fi hoac dong server socket/client process.
3. Server xoa connection khoi room va detach session.
4. Client reconnect theo backoff.
5. Client gui `RESUME_SESSION`.
6. Client gui `RESYNC`.
7. Snapshot moi nhat duoc hien thi.

## Demo 6 - Ket thuc phien

1. Cho timer het.
2. Server lock auction.
3. Transaction cap nhat ENDED va result.
4. Room nhan `AUCTION_ENDED`.
5. Bid moi bi `AUCTION_NOT_OPEN` hoac `BID_AFTER_END`.
6. Cho du 120 giay, room nhan `AUCTION_ARCHIVED` va bien mat khoi client/dashboard.
7. Mo MySQL de xac nhan auction, bid va result van con.

## Self-test

```bat
scripts\run-self-tests-jdk-only.cmd
```

Bao gom:

- codec framing va UTF-8;
- password PBKDF2;
- session detach/resume/expire;
- TCP server that;
- hai client login/join/bid;
- outbid event;
- concurrent bids;
- authoritative final price;
- disconnect/resume/resync;
- timer end.
- archive phong sau visibility window, an list/dashboard va don room;
- product create/update/deactivate;
- host create/list room;
- minimum bid increment va chan host tu bid;
- host extend/end/cancel;
- kick, event va block rejoin.

## Demo 7 - Product va tao phong

1. Login bang `demo`.
2. Bam `Them san pham`, sau do `Sua san pham`.
3. Bam `Tao phong`, chon gia khoi diem, buoc gia va thoi luong.
4. Client khac nhan `AUCTION_CREATED` va thay phong moi.

## Demo 8 - Quyen chu tri

1. Host join phong cua minh va thu bid: server tu choi `AUCTION_FORBIDDEN`.
2. Alice bid thap hon minimum increment: `BID_TOO_LOW`.
3. Host bam `Gia han`, quan sat `extensionSource=HOST`.
4. Thu `Ket thuc` de chot som.

## Demo 9 - Huy va kick

1. Tao phong moi chua co bid, bam `Huy phong`, status thanh `CANCELLED`.
2. Tao phong khac, cho Alice join.
3. Host bam `Moi user`, nhap `alice`.
4. Alice nhan `AUCTION_KICKED`, roi phong va khong join lai duoc.

## Demo 10 - An phong sau 2 phut

1. Dat tam `auction.closedVisibilitySeconds=10` neu can demo nhanh, sau demo tra lai `120`.
2. Cho mot phong `ENDED` hoac `CANCELLED`.
3. Trong visibility window, ca client va dashboard van hien ket qua.
4. Het window, server phat `AUCTION_ARCHIVED`; client tu xoa phong va dashboard khong con dong do.
5. Query MySQL de chung minh he thong chi an khoi san, khong xoa lich su.

## Checklist truoc demo

- [ ] JDK 17 dung.
- [ ] MySQL dang chay neu dung JDBC.
- [ ] Database `btl_16` co du lieu moi.
- [ ] Port 8888 khong bi chiem.
- [ ] Ba tai khoan demo login duoc.
- [ ] Tat ca client JOIN cung auction.
- [ ] Countdown du de demo.
- [ ] Script load test chay.
- [ ] Firewall duoc mo neu demo LAN.
- [ ] Moi thanh vien thuoc file va luong cua minh.
- [ ] Da test them/sua/an san pham va tao phong.
- [ ] Da test host extend/end/cancel/kick.
- [ ] Da test phong tu an sau 120 giay va du lieu MySQL van con.
