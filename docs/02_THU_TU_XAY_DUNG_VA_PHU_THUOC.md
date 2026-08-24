# 02 - THU TU XAY DUNG VA PHU THUOC

## Thu tu merge bat buoc

### Giai doan 1 - Do Tien dung nen

```text
Protocol
-> TCP framing
-> Server accept/client connect
-> Router
-> Account/session
-> Connection registry
-> Messaging service
```

Tat ca thanh vien khac phu thuoc vao giai doan nay.

### Giai doan 2 - Tran Van Phuoc dung du lieu auction

```text
Product
-> AuctionSnapshot/AuctionRuntime
-> AuctionRepository
-> AuctionManager
-> AUCTION_LIST
-> JOIN_AUCTION/AUCTION_SNAPSHOT
```

Dung va Duc can state nay de dat gia va timer.

### Giai doan 3 - Pham Anh Dung dung Bid Engine

```text
PLACE_BID
-> validate room/user/status/price
-> per-auction lock
-> DB transaction
-> update runtime
-> BID_ACCEPTED/BID_REJECTED
-> BID_UPDATE
```

Thuan phu thuoc event nay de realtime update.

### Giai doan 4 - Mai Trung Duc dung Timer

```text
auction-timer
-> check end time
-> anti-sniping end time extension
-> close once
-> save AuctionResult
-> AUCTION_EXTENDED/AUCTION_TICK/AUCTION_ENDED
```

Timer va Bid Engine dung chung auction lock.

### Giai doan 5 - Vu Tri Thuan dung realtime/reconnect/test

```text
Event listener
-> update client model
-> heartbeat
-> disconnect detection
-> reconnect backoff
-> RESUME_SESSION
-> RESYNC
-> load/concurrency test
```

### Giai doan 6 - Tich hop

```text
Isolated self-tests
-> MySQL schema/setup
-> local multi-client test tung cua so
-> LAN demo
-> stress test
-> question rehearsal
```

### Giai doan 7 - Nang cap product va host

```text
Protocol/ErrorCode
-> schema owner/host/min increment/blocked user
-> Product CRUD + soft delete
-> CREATE_AUCTION + AuctionManager.addRuntime
-> host authorization
```

### Giai doan 8 - Host control va client

```text
min bid increment + block self-bid
-> manual extend/end/cancel
-> kick + block rejoin
-> client API/model/dialog/event
-> AuctionManagementSelfTest
```

### Giai doan 9 - Dong va an phong sau 2 phut

```text
auction.closedVisibilitySeconds=120
-> timer close ENDED/CANCELLED nhu cu
-> AuctionManager archive sau retention
-> RoomManager.removeAuction
-> AUCTION_ARCHIVED broadcastAll
-> ClientAppModel.removeAuction
-> FullNetworkAuctionSelfTest kiem tra list/dashboard/resync
```

## Ma tran phu thuoc

| Nguoi | Dau vao can tu nguoi khac | Dau ra cung cap |
|---|---|---|
| Do Tien | Khong | TCP, protocol, session, router, messaging |
| Tran Van Phuoc | Protocol/router | Auction state, list, snapshot, repository |
| Pham Anh Dung | Session, room, auction state | Bid result, bid update, history |
| Mai Trung Duc | Auction state, bid end-time update | Tick, extension, close, result |
| Vu Tri Thuan | Tat ca event tren | Client realtime, reconnect, tests, integration |

## Quy tac khi sua code

- Sua `MessageType` phai thong bao ca nhom.
- Sua field wire phai sua server parser, client parser va document protocol.
- Sua schema phai sua repository va SQL file.
- Sua auction rule phai them self-test.
- Sua retention/archive phai doi chieu server list, dashboard, room va client model.
- Khong merge UI neu request/response chua chay bang console.

## Giai doan 10 - JavaFX va transport abstraction

1. `ServerConnection`/`ClientTransport`, giu TCP test pass.
2. JSON codec va WebSocket server/client.
3. JavaFX feature parity, callback qua `Platform.runLater`.
4. Image, private grant va search.
5. Cross-transport test, packaging, LAN va docs.

Protocol/schema abstraction phai merge truoc UI feature phu thuoc.
