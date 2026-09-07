# 11 - LO TRINH HOC VA TICH HOP BAN NANG CAP

Các phase/ngày bên dưới là lộ trình học lại code đã triển khai, không phải backlog chưa làm. Luồng hiện tại: JavaFX/WebSocket; các đoạn TCP/Swing là nền tảng legacy. Setup dùng VS Code + XAMPP và DB trống; xem docs/05_CACH_CHAY_VSCODE_XAMPP.md. Nhiệm vụ bảo trì hiện tại theo docs/13 và docs/members.

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

## Ngay 11 - Retention va archive phong

- Tien: chot `auction.closedVisibilitySeconds`, `AUCTION_ARCHIVED` va composition.
- Duc: timer xac dinh moc archive sau `endedAt`.
- Phuoc: `AuctionManager` an list/my-list/dashboard va `RoomManager.removeAuction`.
- Thuan: client nhan event, xoa model/joined room va them regression test.
- Dung: review bid/resync sau archive va race tai ranh gioi retention.
- Ca nhom demo: phong dong con hien 120 giay, sau do bien mat nhung MySQL van con.

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
- Phong dong tu an sau 120 giay tren client/dashboard, room duoc don va lich su DB khong bi xoa.

## Phase nang cap ngay 12-20

- Ngay 12: transport abstraction va map lai kien truc.
- Ngay 13: JavaFX skeleton, login va application thread.
- Ngay 14: WebSocket server + JSON `WireMessage`.
- Ngay 15: WebSocket client, reconnect/resume/resync.
- Ngay 16: product image end-to-end.
- Ngay 17: public/private room, PBKDF2 va session grant.
- Ngay 18: search product name/Auction ID.
- Ngay 19: TCP-WS cross test, concurrency va lifecycle.
- Ngay 20: jpackage, LAN, docs va demo ba may.
