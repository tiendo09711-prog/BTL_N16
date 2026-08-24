# 13 - BANG PHAN CONG FILE THEO NGUOI - BAN NANG CAP

Nguyen tac: giu module cu cua tung nguoi, chi giao tinh nang moi gan nhat voi phan da lam.

## Do Tien - Protocol, authorization core va composition

```text
common/protocol/MessageType.java
common/protocol/ErrorCode.java
server/module/AuctionModule.java
server/routing/*
server/ServerApplication.java
server/db/DatabaseSchema.java
sql/00_schema.sql
sql/01_demo_data.sql
README.md va tai lieu kien truc/protocol
```

Nhiem vu moi:

- Them request/response/event SV01-SV08, OP01/02/03/07.
- Bao dam moi route quan tri bat buoc dang nhap.
- Review quy tac user/host lay tu session, khong tin ID client gui len.
- Noi `AuctionManagementService` vao composition root.
- Quan ly migration database va review tich hop cuoi.

## Vu Tri Thuan - Client controller, realtime va test

```text
client/controller/ClientController.java
client/model/ClientAppModel.java
client/model/ClientWireParser.java
client/service/AuctionApi.java
client/view/MainFrame.java
src/test/java/*
```

Nhiem vu moi:

- Dong bo API product/host control.
- Xu ly `AUCTION_CREATED`, `AUCTION_CANCELLED`, `AUCTION_KICKED`.
- Dialog them/sua/an san pham va tao phong.
- Viet `AuctionManagementSelfTest` va regression test reconnect.

## Pham Anh Dung - Bid rule, concurrency va kick safety

```text
server/auction/service/BidService.java
server/auction/repository/BidCommit.java
JdbcAuctionRepository.commitAcceptedBid()
server/auction/service/RoomMember.java
server/auction/service/KickOutcome.java
RoomManager.kickUser/findByUsername()
AuctionPanel bid controls
```

Nhiem vu moi:

- Ap dung `minBidIncrement` thay cho chi `amount > currentPrice`.
- Chan host tu bid san pham cua minh.
- Re-check membership ben trong auction lock de tranh race kick/bid.
- Review kick/block va bid/end dong thoi.

## Mai Trung Duc - Lifecycle va host control

```text
server/auction/model/AuctionResult.java
server/auction/repository/CloseAuctionCommit.java
server/auction/repository/ExtendAuctionCommit.java
server/auction/repository/CancelAuctionCommit.java
server/auction/service/AuctionTimerService.java
AuctionManagementService.extendAuction/endAuction/cancelAuction
AuctionWireData.extended/ended/cancelled
AuctionPanel host control buttons
```

Nhiem vu moi:

- Gia han thu cong va phan biet `HOST`/`ANTI_SNIPING`.
- Ket thuc som dung cung lock/transaction voi timer.
- Huy phien chi khi chua co bid, status `CANCELLED`.
- Review close once khi timer va host cung tac dong.

## Tran Van Phuoc - Product, auction ownership va repository

```text
server/auction/model/Product.java
server/auction/model/AuctionStatus.java
server/auction/model/AuctionSnapshot.java
server/auction/model/AuctionRuntime.java
server/auction/repository/AuctionRepository.java
server/auction/repository/InMemoryAuctionRepository.java
server/auction/repository/JdbcAuctionRepository.java
server/auction/repository/CreateProductCommit.java
server/auction/repository/UpdateProductCommit.java
server/auction/repository/CreateAuctionCommit.java
server/auction/repository/BlockAuctionUserCommit.java
server/auction/service/AuctionManager.java
server/auction/service/AuctionManagementService.java (product/create/join/kick data)
client/model/ClientProduct.java
client/model/ClientAuction.java
client/view/AuctionTableModel.java
```

Nhiem vu moi:

- Product ownership, update va soft delete.
- Host ownership, create room va my auctions.
- Dong bo memory/JDBC va runtime moi sau khi create.
- Luu blocked user de kick con hieu luc sau reconnect/restart JDBC.

## File dung chung va reviewer

| File | Chu tri | Reviewer |
|---|---|---|
| `MessageType`, `ErrorCode` | Do Tien | Ca nhom |
| `AuctionManagementService` | Phuoc/Duc | Tien, Dung |
| `AuctionRuntime` | Phuoc | Dung, Duc |
| `BidService` | Dung | Tien, Duc |
| `RoomManager` | Dung/Phuoc | Thuan |
| `JdbcAuctionRepository` | Phuoc | Dung, Duc, Tien |
| `ClientController` | Thuan | Tien, Phuoc |
| `AuctionPanel` | Thuan/Phuoc/Duc/Dung | Tien |
| `AuctionManagementSelfTest` | Thuan | Ca nhom |
| `ServerApplication`, `DatabaseSchema` | Tien | Ca nhom |

## Can bang khoi luong

| Thanh vien | Phan cu | Phan moi chinh |
|---|---|---|
| Do Tien | network/account/composition | protocol, auth route, migration, integration |
| Vu Tri Thuan | realtime/reconnect/test | management client, event, E2E test |
| Pham Anh Dung | bid/concurrency | min increment, self-bid, kick/bid safety |
| Mai Trung Duc | timer/result | extend/end/cancel lifecycle |
| Tran Van Phuoc | data/list/room | product CRUD, host/create room, persistence |
