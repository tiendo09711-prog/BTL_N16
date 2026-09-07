# 12 - MAPPING 4 SLIDE CUA THAY VAO PROJECT

## Slide 1 - Vao ra voi Java

Tinh hoa duoc dung:

```text
InputStream / OutputStream
BufferedInputStream / BufferedOutputStream
DataInputStream / DataOutputStream
try-with-resources
```

File ap dung:

```text
ClientConnection
NetworkClient
LengthPrefixedMessageCodec
ConsoleClientMain
```

Kien thuc bo sung can thiet:

```text
TCP byte stream
message framing
readFully
frame size limit
UTF-8 string length
```

Khong hoc lan man cac bien the file I/O khong lien quan.

## Slide 2 - Thread trong Java

Tinh hoa duoc dung:

```text
Runnable/run/start
nhieu thread cung hoat dong
chia se du lieu
sleep/scheduled work
thread lifecycle
```

File ap dung:

```text
TcpServer - tcp-acceptor va worker pool
ClientConnection - Runnable read loop
AuctionTimerService - scheduled thread
SessionCleanupService - scheduled thread
NetworkClient - server-reader
HeartbeatService - heartbeat thread
ReconnectCoordinator - reconnect thread
```

Kien thuc bo sung:

```text
ExecutorService
ScheduledExecutorService
ConcurrentHashMap
ReentrantLock
AtomicLong/AtomicBoolean
CompletableFuture
```

`AuctionTimerService` cung minh hoa scheduled cleanup: dong phien dung han, giu ket qua 120 giay, sau do archive khoi san va phat event cho client.

## Slide 3 - MVC

Tinh hoa duoc dung:

```text
Model dong goi du lieu
View thu input va hien thi
Controller nhan event va dieu phoi
```

Client:

```text
Model: ClientAppModel, ClientAuction, ClientBid
JavaFX UI/controller: JavaFxClientApp, FxClientController
Service/Network: AccountApi, AuctionApi, ClientTransport, WebSocketClientTransport
Legacy Swing: MainFrame, LoginPanel, AuctionPanel, ClientController, NetworkClient
```

Server duoc tach mo rong:

```text
Controller -> Service -> Repository
```

Vi du:

```text
AuctionController -> BidService -> JdbcAuctionRepository
```

## Slide 4 - JDBC

Tinh hoa duoc dung:

```text
Connection
PreparedStatement
ResultSet
INSERT/UPDATE/SELECT
transaction
commit/rollback
```

File ap dung:

```text
JdbcConnectionFactory
JdbcUserRepository
JdbcAuctionRepository
DatabaseSchema
DatabaseCheckMain / DatabaseSetupMain / DatabaseResetMain (khong seed)
```

Hai transaction chinh:

```text
Login audit:
UPDATE last_login + INSERT login_history

Accepted bid:
SELECT FOR UPDATE + INSERT bid + UPDATE auction
```

## Bang tong hop

| Slide | Kien thuc | Demo thay thay duoc |
|---|---|---|
| I/O | Stream, buffer, data stream | Frame TCP gui/nhan dung |
| Thread | Multi-thread va shared state | Nhieu client, timer, race condition |
| MVC | Tach Model/View/Controller | JavaFX client, API/model va presentation controller |
| JDBC | SQL, ResultSet, transaction | Bid va result luu MySQL |

WebSocket bo sung vi du message boundary/JSON, reconnect va cross-transport; TCP van la vi du framing byte stream.
