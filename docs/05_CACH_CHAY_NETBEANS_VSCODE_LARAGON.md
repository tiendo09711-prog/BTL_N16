# 05 - CACH CHAY NETBEANS, VSCODE VA LARAGON

## A. NetBeans

1. Cai JDK 17.
2. Mo NetBeans.
3. `File -> Open Project`.
4. Chon thu muc co `pom.xml`.
5. Cho Maven tai dependency.
6. Chuot phai project -> Properties -> Java Platform -> JDK 17.
7. Chay main class tuy muc dich:

```text
DatabaseSetupMain
ServerDashboardMain
ClientMain
```

### Mo nhieu client trong NetBeans

- Chay `ClientMain`.
- Khi cua so da mo, chay lai main class them lan nua.
- Hoac chay `scripts\run-client.cmd` ngoai terminal.

## B. VSCode

Cai extension:

```text
Extension Pack for Java
Maven for Java
```

Mo thu muc project. VSCode se doc `pom.xml`.

Trong Run and Debug da co:

```text
Run Server Dashboard
Run Server Console
Run Auction Client
Run Self Tests
```

Neu classpath chua cap nhat:

```text
Ctrl+Shift+P
Java: Clean Java Language Server Workspace
Maven: Reload Projects
```

## C. Laragon

Chi can bat MySQL. Apache va Mailpit khong can cho Java TCP.

Cau hinh mac dinh:

```properties
db.host=127.0.0.1
db.port=3306
db.name=btl_16
db.user=root
db.password=
```

Neu root co password, sua `db.password`.

Chay:

```bat
scripts\setup-db.cmd
```

## D. Chay local mot may

### Chay nhanh

Tai thu muc goc:

```bash
npm run dev
```

Runner build, setup MySQL, chay server console va mo mot client local. Dung bang `Ctrl+C`.

### Chay tung thanh phan

Project chi chay runtime voi MySQL/JDBC va khong con script tu dong mo nhieu cua so.

Terminal 1:

```bat
scripts\run-server-dashboard.cmd
```

Terminal 2, 3, 4:

```bat
scripts\run-client.cmd
```

Hoac chay them client bang npm:

```bash
npm run client -- --host=127.0.0.1
```

## E. Debug theo module

### Do Tien

Breakpoint:

```text
NetworkClient.sendRequest
LengthPrefixedMessageCodec.write/read
ClientConnection.run
MessageRouter.route
AccountService.login
SessionManager.createSession
```

### Tran Van Phuoc

```text
AuctionController.handleList/handleJoin
AuctionQueryService
AuctionManager
JdbcAuctionRepository.findAllAuctions
```

### Pham Anh Dung

```text
BidService.placeBid
JdbcAuctionRepository.commitAcceptedBid
AuctionRuntime.applyAcceptedBid
```

### Mai Trung Duc

```text
AuctionTimerService.safeRun
closeIfExpired
JdbcAuctionRepository.closeAuction
```

### Vu Tri Thuan

```text
NetworkClient.readLoop
ClientController.handleEvent
ReconnectCoordinator
ClientController.resumeAfterReconnect
```
