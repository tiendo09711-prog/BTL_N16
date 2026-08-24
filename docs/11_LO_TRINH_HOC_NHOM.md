# 11 - LO TRINH HOC VA TICH HOP BAN NANG CAP

## Nguyen tac hoc

Moi file can tra loi duoc:

1. Input la gi?
2. Output la gi?
3. Ai goi no?
4. No goi ai?
5. Chay tren thread nao?
6. State nao bi thay doi?
7. Quyen nao duoc kiem tra?
8. Loi duoc tra ve bang gi?

## Ngay 1 - Protocol va schema chung

- Ca nhom doc `MessageType`, `ErrorCode`, `AuctionRepository`.
- Chot field `ownerId`, `hostUserId`, `minBidIncrement`, `active`.
- Ve vong doi `OPEN -> ENDED` va `OPEN -> CANCELLED`.
- Bai tap: ve duong di `CREATE_AUCTION` va `KICK_AUCTION_USER`.

## Ngay 2 - Do Tien va Tran Van Phuoc

- Do Tien: protocol, route, composition, migration schema.
- Phuoc: Product/Auction model, repository contract/JDBC, owner/host mapping.
- Pair review `DatabaseSchema` va `JdbcAuctionRepository`.

## Ngay 3 - Product va tao phong

- Phuoc: create/update/deactivate product, my products, create/my auctions.
- Thuan: `ClientProduct`, parser va `AuctionApi` tuong ung.
- Do Tien review request/response va loi.

## Ngay 4 - Bid rule va kick

- Dung: min bid increment, chan host tu bid, re-check room trong auction lock.
- Dung + Phuoc: `RoomMember`, kick va block tai tham gia.
- Thuan: event `AUCTION_KICKED` va client roi phong.

## Ngay 5 - Host control

- Duc: host extend, manual end, cancel no-bid.
- Dung review race bid/end/kick.
- Do Tien review authorization lay user tu session.

## Ngay 6 - Client management UI

- Thuan: bind controller va xu ly async response/event.
- Phuoc: bang auction, product dialog, tao phong dialog.
- Duc: nut gia han/ket thuc/huy va status display.
- Dung: o bid hien minimum increment va error.

## Ngay 7 - Self-test

- Thuan chu tri `AuctionManagementSelfTest`.
- Moi nguoi them it nhat mot assertion cho module minh:
  - Tien: auth/route.
  - Phuoc: CRUD/create/list.
  - Dung: min increment/self-bid/kick race.
  - Duc: extend/end/cancel.

## Ngay 8 - JDBC va migration

- Reset database va chay `DatabaseSetupMain`.
- Kiem tra products owner, auctions host/min increment, blocked users.
- Chay lai mot lan tren database cu de xac nhan migration cot.

## Ngay 9 - Multi-client va LAN

- Bob tao san pham/phong.
- Demo va Alice join/bid.
- Host gia han, kick mot user, ket thuc mot phien va huy mot phien khac.
- Thu reconnect, resync va firewall TCP 8888.

## Ngay 10 - Van dap va ban giao

- Moi nguoi demo mot request moi end-to-end.
- Reviewer giai thich file cua tac gia.
- Chay self-test, load test va ghi lai ket qua JDBC/LAN.

## Pair review moi

```text
Do Tien <-> Vu Tri Thuan: protocol/client integration
Tran Van Phuoc <-> Pham Anh Dung: repository/room/bid rule
Mai Trung Duc <-> Do Tien: lifecycle/manual close/timer
```

## Dieu kien hoan thanh

- Moi nguoi giai thich duoc phan cu va phan nang cap.
- Khong co logic authorization chi nam tren client.
- Test repository va JDBC tuan theo cung `AuctionRepository` contract.
- `FullNetworkAuctionSelfTest` va `AuctionManagementSelfTest` deu pass.
