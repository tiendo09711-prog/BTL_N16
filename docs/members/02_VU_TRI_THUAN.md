# VU TRI THUAN - REALTIME, DISCONNECT/RECONNECT, TEST VA INTEGRATION

## Nhiem vu chinh

```text
Cap nhat realtime
+ xu ly mat ket noi
+ resume/resync
+ heartbeat
+ kiem thu multi-client
+ tich hop he thong
```

## So do module

```text
NetworkClient.server-reader
  -> response co requestId -> pending future
  -> EVENT -> ClientController.handleEvent
                 |-- BID_UPDATE
                 |-- OUTBID_NOTIFICATION
                 |-- AUCTION_EXTENDED
                 |-- AUCTION_TICK
                 `-- AUCTION_ENDED
                      -> ClientAppModel
                      -> Swing View

DISCONNECTED
  -> ReconnectCoordinator
  -> TCP connect moi
  -> RESUME_SESSION
  -> RESYNC
  -> snapshot moi
```

## Thu tu hoc file

1. `NetworkClient.java`
2. `ConnectionState.java`
3. `ClientAppModel.java`
4. `ClientWireParser.java`
5. `ClientController.java` - doc `handleEvent` truoc
6. `HeartbeatService.java`
7. `ReconnectCoordinator.java`
8. `RoomManager.java`
9. `ServerMessagingService.java`
10. `FullNetworkAuctionSelfTest.java`
11. `ConcurrentBidLoadTestMain.java`

## Bien phai thuoc

| Bien | Y nghia |
|---|---|
| `eventListeners` | Noi nhan server push |
| `pendingRequests` | Response flow rieng voi event flow |
| `state` | Trang thai TCP |
| `readerThread` | Mot thread doc server |
| `consecutiveFailures` | Heartbeat loi lien tiep |
| `running` | Reconnect loop dang chay |
| `reconnectMaxAttempts` | Gioi han thu lai |
| `autoReconnectEnabled` | Khong reconnect sau logout |
| `sessionToken` | Resume danh tinh |
| `joinedAuctionId` | Auction can RESYNC |
| `serverClockOffsetMillis` | Dong bo countdown hien thi |
| `bidUpdateEvents` | Dem event trong test |

## Luong BID_UPDATE

```text
BidService commit thanh cong
-> AuctionBroadcastService
-> RoomManager lay subscribers
-> ServerMessagingService
-> ClientConnection.send
-> NetworkClient.readLoop
-> eventListeners
-> ClientController.handleEvent
-> parse ClientAuction/ClientBid
-> ClientAppModel.applyAuctionUpdate
-> Swing render
```

## Luong reconnect

```text
Heartbeat 3 lan loi hoac socket EOF
-> NetworkClient DISCONNECTED
-> ReconnectCoordinator exponential backoff
-> connect TCP moi
-> AccountApi.resumeSession(sessionToken)
-> AuctionApi.resync(joinedAuctionId)
-> client thay cache bang snapshot server
```

## Test phai nam

- Codec hai frame lien tiep.
- 2 client trong cung room.
- Event BID_UPDATE den ca hai.
- Outbid chi den leader cu.
- Concurrent bid co final state dung.
- Disconnect xoa room connection.
- Resume token tren socket moi.
- RESYNC cho gia moi nhat.
- Timer event ket thuc.

## Bai tap

1. In ten thread trong `handleEvent` va chung minh khong phai EDT.
2. Tat server 5 giay, bat lai, quan sat backoff.
3. Tat client khi dang dan dau, de client khac bid, sau do reconnect.
4. Chay load test 5, 10, 20 client.
5. Them bo dem so event nhan duoc.

## Cau hoi rieng

### 1. Realtime o day la gi?

Server chu dong push event qua socket dang mo, khong polling database.

### 2. Response va event duoc tach the nao?

Response co requestId va complete pending future; event duoc chuyen cho event listeners.

### 3. Tai sao chi mot reader thread?

Neu nhieu thread cung doc mot input stream, khong biet thread nao lay frame nao va de gay sai protocol.

### 4. Heartbeat co vai tro gi?

Kiem tra server song, do RTT va phat hien connection im lang bi hong.

### 5. Tai sao 3 lan PING loi moi disconnect?

Tranh mot timeout tam thoi lam reconnect khong can thiet.

### 6. Backoff de lam gi?

Khong spam connect khi server dang down; delay tang dan toi gioi han.

### 7. Resume va resync khac nhau?

Resume phuc hoi danh tinh/session. Resync phuc hoi auction state moi nhat.

### 8. Tai sao khong replay moi event da bo lo?

Pham vi co ban chi can snapshot chinh xac, don gian va de bao ve.

### 9. Client clock co chinh thuc khong?

Khong. Client tinh countdown hien thi dua tren serverNow/endTime.

### 10. Khi event den ngoai Swing EDT thi sao?

Controller dung `SwingUtilities.invokeLater` truoc khi cham component.

### 11. Room cleanup o dau?

Lifecycle listener tren server goi `RoomManager.removeConnection`.

### 12. Neu reconnect nhanh ma old session van ACTIVE?

Controller retry resume vai lan khi nhan ACCOUNT_ALREADY_ONLINE, cho server phat hien old socket.

### 13. Logout co auto reconnect khong?

Khong; `autoReconnectEnabled=false` va token local bi xoa.

### 14. Test concurrency dung socket that khong?

Co; FullNetworkAuctionSelfTest va load tool mo NetworkClient that vao TcpServer.

### 15. Lam sao biet final state dung?

Sau concurrent bids, gui RESYNC va so sanh authoritative currentPrice/winner tren server.

### 16. Event co the den truoc response khong?

Co. BidService broadcast sau commit, controller response sau khi service tra ve; client model dedup bid theo bidId.

### 17. Neu event trung?

Model khong them lai bid co cung bidId; auction version tranh update cu hon.

### 18. Dieu gi xay ra khi server restart?

TCP reconnect duoc nhung session token khong con; UI yeu cau login lai.

## Nhiem vu nang cap SV01-SV08

- Dong bo `AuctionApi`, `ClientWireParser`, `ClientController`, dialog trong `MainFrame`.
- Xu ly realtime `AUCTION_CREATED`, `AUCTION_CANCELLED`, `AUCTION_KICKED`.
- Bind UI them/sua/an product, tao/my room va host control.
- Chu tri `AuctionManagementSelfTest` va regression reconnect/resync.

Can demo mot client bi kick tu dong roi room va bi server chan join lai.

## Lo trinh nang cap ca nhan

Muc tieu: dong bo day du tinh nang server moi len client va hoc duoc protocol, state, nghiep vu thay vi chi lam giao dien.

| Ngay | Noi dung hoc va thuc hanh | Dau ra ban giao |
|---|---|---|
| 1 | Doc ma tinh nang moi va bang protocol cung ca nhom | Bang request/response/event client can ho tro |
| 2 | Pair voi Tien hoc authentication, requestId va error mapping | Checklist API can session |
| 3 | Dong bo product CRUD, my products, create room va my auctions | API/parser/controller cho SV01-SV08 |
| 4 | Pair voi Dung xu ly min increment, self-bid va `AUCTION_KICKED` | Minimum bid va luong roi room khi bi kick |
| 5 | Pair voi Duc xu ly extend/end/cancel va `CANCELLED` | Host controls va state UI |
| 6 | Hoan thien dialog, callback async va cap nhat tren EDT | UI khong block reader thread |
| 7 | Chu tri `AuctionManagementSelfTest`, gom assertion ca nhom | Test product, room, bid, host va kick |
| 8 | Kiem tra payload memory/JDBC co cung cach parse | Checklist field va fallback |
| 9 | Thu reconnect/resume/resync sau create, extend, cancel va kick | Bao cao regression realtime |
| 10 | Demo client bi kick, reconnect va bi chan join lai | Kich ban OP07 end-to-end |

### Dau vao phu thuoc

- Protocol/route cua Tien; product/auction fields cua Phuoc.
- Bid/kick outcome cua Dung; lifecycle event/status cua Duc.

### Dau ra ban giao

- `AuctionApi`, parser, controller va UI ho tro tat ca thao tac moi.
- Event realtime cap nhat dung model; self-test va regression reconnect/resync.

### Nguoi review

- Tien review protocol; Phuoc review du lieu UI.
- Dung va Duc review thong bao nghiep vu cua module minh.

### Tieu chi hoan thanh

- Moi chuc nang server moi deu goi va quan sat duoc tren client.
- Client khong tu quyet dinh quyen, gia toi thieu hay trang thai ket thuc.
- Event den truoc/sau response khong lam trung state hoac treo UI.
- Demo reconnect/resync va kick chay bang hai client that.
