# 01 - KIEN TRUC TONG THE

## So do deployment

```text
MAY CLIENT A       MAY CLIENT B       MAY CLIENT C
Swing Client       Swing Client       Swing Client
     \                  |                  /
      \____________ TCP 8888 ____________/
                         |
                         v
               JAVA CENTRAL SERVER
        +--------------------------------+
        | TcpServer / ClientConnection   |
        | MessageRouter                  |
        | SessionManager                 |
        | RoomManager                    |
        | AuctionManager                 |
        | AuctionManagementService       |
        | BidService                     |
        | AuctionTimerService            |
        | ServerMessagingService         |
        +---------------+----------------+
                        |
                   JDBC 3306
                        |
                  LARAGON MYSQL
                    database btl_16
```

Client khong duoc ket noi truc tiep vao MySQL.

## So do package

```text
vn.ptit.btl16
|-- common
|   |-- config
|   |-- protocol
|   |-- util
|   `-- validation
|-- server
|   |-- network
|   |-- routing
|   |-- session
|   |-- account
|   |-- auction
|   |-- db
|   |-- module
|   `-- dashboard
|-- client
|   |-- network
|   |-- service
|   |-- model
|   |-- controller
|   `-- view
`-- tools
```

## So do module server

```text
TcpServer
  -> ClientConnection
  -> MessageRouter
       |-- CoreAccountModule
       |    |-- AccountController
       |    |-- AccountService
       |    `-- UserRepository / SessionManager
       |
       `-- AuctionModule
            |-- AuctionController
            |-- AuctionQueryService
            |-- AuctionManagementService
            |-- BidService
            |-- AuctionTimerService
            |-- AuctionManager
            |-- RoomManager
            `-- AuctionRepository
```

## Mo hinh thread

```text
SERVER
main thread
  -> khoi tao va cho server dung

tcp-acceptor
  -> ServerSocket.accept()

client-worker-1..N
  -> moi worker chay read loop cua mot connection

auction-timer
  -> check end time va broadcast tick

session-cleanup
  -> xoa session detached het grace period

CLIENT
Swing EDT
  -> click va render

server-reader
  -> doc response va event

client-request-timeouts
  -> timeout CompletableFuture

client-heartbeat
  -> PING/PONG

client-reconnect
  -> backoff connect lai
```

## Nguon su that

- `AuctionRuntime`: state realtime trong RAM cua server.
- `AuctionRepository`: persistence va transaction.
- `SessionManager`: user dang online va resumable session.
- `RoomManager`: connection dang theo doi auction nao.
- `AuctionManagementService`: ownership, product CRUD, create/extend/end/cancel/kick.
- Client model chi la cache hien thi.

## Luong full

```text
LOGIN
-> AUCTION_LIST
-> JOIN_AUCTION
-> AUCTION_SNAPSHOT
-> PLACE_BID
-> lock auction
-> validate
-> transaction DB
-> update runtime
-> unlock
-> BID_ACCEPTED cho nguoi gui
-> BID_UPDATE cho room
-> OUTBID_NOTIFICATION cho leader cu
-> AUCTION_EXTENDED neu sat gio
-> AUCTION_ENDED khi timer het
```

## Luong quan tri moi

```text
LOGIN
-> CREATE_PRODUCT / UPDATE_PRODUCT / DEACTIVATE_PRODUCT
-> CREATE_AUCTION
-> server lay hostUserId tu session
-> repository insert
-> AuctionManager.addRuntime
-> AUCTION_CREATED broadcast
-> host EXTEND_AUCTION / END_AUCTION / CANCEL_AUCTION
-> cung per-auction lock voi bid va timer
-> KICK_AUCTION_USER
-> luu auction_blocked_users
-> remove connection khoi RoomManager
-> AUCTION_KICKED den client bi moi
```

Client chi hien nut chu tri khi `auction.hostUserId == model.userId`; server van kiem tra lai quyen.
