# DO TIEN - NETWORK CORE, ACCOUNT, SESSION, SYSTEM ARCHITECTURE

## Nhiem vu chinh

```text
TCP client-server
+ dang nhap
+ quan ly session
+ du lieu tai khoan
+ protocol chung
+ kien truc va tich hop
```

## So do phan cua Do Tien

```text
ClientController
  -> NetworkClient
  -> LengthPrefixedMessageCodec
  -> TCP
  -> TcpServer
  -> ClientConnection
  -> MessageRouter
  -> CoreAccountModule
  -> AccountController
  -> AccountService
       |-- PasswordHasher
       |-- UserRepository
       `-- SessionManager
```

Do Tien con cung cap cho cac module khac:

```text
RequestContext.requireSession()
ConnectionRegistry
ServerMessagingService
ServerSequence
MessageType/ErrorCode
```

## Thu tu hoc file

### Tang 1 - Protocol

1. `MessageKind.java`
2. `MessageType.java`
3. `WireMessage.java`
4. `LengthPrefixedMessageCodec.java`
5. `ProtocolLimits.java`

Can giai thich duoc TCP byte stream va framing.

### Tang 2 - Connection

6. `NetworkClient.java`
7. `TcpServer.java`
8. `ClientConnection.java`
9. `ConnectionRegistry.java`
10. `ServerMessagingService.java`

Can giai thich read loop va output lock.

### Tang 3 - Router

11. `MessageRouter.java`
12. `RequestContext.java`
13. `CoreAccountModule.java`

Can tu them mot route ECHO ma khong sua TcpServer.

### Tang 4 - Account/session

14. `UserAccount.java`
15. `Pbkdf2PasswordHasher.java`
16. `UserRepository.java`
17. `JdbcUserRepository.java`
18. `SessionManager.java`
19. `AccountService.java`
20. `AccountController.java`

### Tang 5 - Composition

21. `ServerApplication.java`
22. `ServerMain.java`
23. `ClientMain.java`

## Bien phai thuoc

| Bien | Y nghia |
|---|---|
| `connectionId` | ID cua socket hien tai |
| `userId` | Khoa user DB, khong doi khi reconnect |
| `sessionToken` | Token phuc hoi user tren socket moi |
| `requestId` | Noi response ve dung future |
| `serverSequence` | Thu tu server event/bid |
| `outputLock` | Tuan tu hoa write cung socket |
| `pendingRequests` | Request dang doi response |
| `routes` | MessageType -> handler |
| `byToken` | Token -> session |
| `tokenByConnection` | Connection -> token |
| `tokenByUser` | User -> token |
| `resumeDeadline` | Han session detached |
| `maxFrameBytes` | Bao ve bo nho khi decode |

## Luong TCP connect

```text
NetworkClient.connect(host, port)
-> new Socket
-> connect timeout
-> getInputStream/getOutputStream
-> boc Buffered + Data stream
-> start server-reader

TcpServer.acceptLoop
-> ServerSocket.accept
-> configure TCP_NODELAY/KEEPALIVE
-> new ClientConnection
-> registry.add
-> CONNECTION_WELCOME
-> workers.execute(connection)
```

## Luong login

```text
LOGIN request
-> MessageRouter tim handler
-> AccountController doc username/password
-> AccountService validate
-> UserRepository findByUsername
-> PasswordHasher.verify
-> SessionManager.createSession
-> recordSuccessfulLogin transaction
-> LOGIN_RESULT co user + sessionToken
```

## Luong disconnect/resume

```text
socket close
-> ClientConnection.close
-> lifecycle listener
-> RoomManager.removeConnection
-> SessionManager.detachByConnection

socket moi
-> RESUME_SESSION token
-> SessionManager.resumeSession
-> RESUME_SESSION_RESULT
-> client RESYNC auction
```

## Bai tap tu viet

1. Tu viet codec length-prefix nho.
2. Them ECHO/ECHO_RESULT.
3. Chay 5 client va xem worker name.
4. Login trung account.
5. Disconnect va resume trong 120 giay.
6. Resume qua han.
7. Them field moi vao PONG va doc tren client.

## Cau hoi rieng va goi y tra loi

### 1. Tai sao phan cua ban la nen mong?

Moi request cua cac module deu di qua protocol, socket, connection, router va session do phan nay cung cap.

### 2. Server nhan nhieu client nhu the nao?

Acceptor nhan socket, moi connection duoc dua vao fixed worker pool va co read loop rieng.

### 3. Tai sao khong dung `Scanner` doc line cho protocol?

Line protocol co the lam duoc nhung kho gioi han/encode payload; project dung DataInput/DataOutput va length-prefix ro rang.

### 4. Mot lan write co phai mot lan read?

Khong, TCP la stream. Codec doc length truoc va `readFully` payload.

### 5. Neu hai thread gui mot socket?

Tat ca qua synchronized outputLock de frame khong bi xen byte.

### 6. requestId dung de lam gi?

Client co the co nhieu request dang cho; requestId giup complete dung CompletableFuture.

### 7. serverSequence dung de lam gi?

Danh so event/bid chinh thuc, ho tro thu tu va phat hien update cu.

### 8. Session luu o dau?

RAM trong SessionManager; du lieu account luu DB.

### 9. Tai sao session co ba map?

Can tra theo token, connection va user voi do phuc tap O(1), dong thoi chan account trung.

### 10. Tai sao can mutationLock neu da ConcurrentHashMap?

Mot thao tac session thay doi nhieu map; can tinh nguyen tu giua chung.

### 11. Password luu the nao?

PBKDF2-HMAC-SHA256 voi salt rieng va iteration, khong luu plaintext.

### 12. Client co biet password DB khong?

Khong, JDBC chi ton tai phia server.

### 13. Them module bid co sua TcpServer khong?

Khong; dang ky handler vao MessageRouter qua AuctionModule.

### 14. Khi client mat mang, server biet the nao?

Read loop nhan EOF/SocketException, finally close va goi lifecycle listener.

### 15. Server restart co resume token cu khong?

Khong vi session RAM; client login lai. Day la pham vi da chon cho BTL.

### 16. Tai sao server bind 0.0.0.0?

De nhan connection tu loopback va card LAN. Client dung IPv4 cu the cua server.

### 17. Port 3306 co mo cho client khong?

Khong; chi 8888 can mo.

### 18. MVC nam o dau?

Client View -> ClientController -> API/Network; server dung Controller-Service-Repository de tach trach nhiem.

### 19. Neu frame length am/qua lon?

Codec nem ProtocolException va dong connection, tranh cap phat nguy hiem.

### 20. Ban la nhom truong can nam gi ngoai module?

Duong di message, session, room, bid lock, timer, broadcast, reconnect va diem cap nhat DB.

## Nhiem vu nang cap SV01-SV08

- Chu tri `MessageType`, `ErrorCode`, `AuctionModule`, `ServerApplication`.
- Them migration owner/host/min increment/blocked user trong `DatabaseSchema`.
- Review moi route quan tri lay user tu session va bat buoc authentication.
- Review protocol/client integration va chay ban giao cuoi.

Can demo duoc duong di `CREATE_AUCTION` tu client den repository va event `AUCTION_CREATED` quay lai.

## Lo trinh nang cap ca nhan

Muc tieu: vua hoan thanh network/core, vua hoc cach SV01-SV08 va OP01/02/03/07 di end-to-end qua server va client.

| Ngay | Noi dung hoc va thuc hanh | Dau ra ban giao |
|---|---|---|
| 1 | Doc ma tinh nang moi, ve request -> route -> service -> repository -> response/event | So do `CREATE_AUCTION` va `KICK_AUCTION_USER` |
| 2 | Kiem tra protocol, error code, route authentication va schema migration | Danh sach message, field va loi da thong nhat |
| 3 | Pair voi Phuoc review owner/host lay tu session | Checklist authorization cho product/create room |
| 4 | Pair voi Dung hoc min increment, self-bid va lock khi kick | Ghi chu authorization va concurrency |
| 5 | Pair voi Duc review extend/end/cancel dung auction lock | Checklist close-once va quyen host |
| 6 | Pair voi Thuan doi chieu request/response/event server-client | Bang protocol khong con field lech |
| 7 | Them assertion auth/route va review test ca nhom | Test request chua login va sai host |
| 8 | Chay migration JDBC cung Phuoc | Bien ban schema cu/moi |
| 9 | Tich hop multi-client/LAN, reconnect, kick va resync | Checklist tich hop |
| 10 | Demo mot luong moi end-to-end va dieu phoi van dap | Demo create room hoac kick user |

### Dau vao phu thuoc

- Contract repository/model cua Phuoc; quy tac bid/kick cua Dung.
- Quy tac lifecycle cua Duc; payload va event handler cua Thuan.

### Dau ra ban giao

- Protocol, error code, route authentication, composition va migration day du.
- Checklist tich hop server-client va ket qua review cheo.

### Nguoi review

- Thuan review protocol/client; Phuoc review schema/repository.
- Dung va Duc review authorization tai thao tac host.

### Tieu chi hoan thanh

- Khong route quan tri nao tin `ownerId` hoac `hostUserId` do client gui.
- Tat ca request moi co response/error ro rang va client parse duoc.
- Giai thich duoc mot luong product, mot luong host control va mot luong kick.
- Self-test va build van pass sau tich hop.

## Bo sung moi - Retention va archive phong

### Nhiem vu

- Chu tri them `AUCTION_ARCHIVED` trong `MessageType` va doi chieu protocol server-client.
- Quan ly `auction.closedVisibilitySeconds=120` trong `ServerConfig`/`server.properties`.
- Noi config vao `AuctionManager` tai `ServerApplication` va review khong co client nao ket noi MySQL.
- Review tai lieu kien truc: mot TCP server trung tam mo san cho nhieu client LAN.

### Ngay 11 trong lo trinh

| Noi dung hoc va thuc hanh | Dau ra ban giao |
|---|---|
| Ve luong `ENDED/CANCELLED -> 120s -> AUCTION_ARCHIVED` | So do protocol/composition |
| Pair voi Duc va Phuoc kiem tra timer, manager, room cleanup | Checklist server authoritative |
| Pair voi Thuan doi chieu event va client model | Payload khong lech field |
| Chay review test archive va ban giao docs | Bien ban integration |

### Tieu chi bo sung

- Config production mac dinh dung 120 giay va test co the rut ngan.
- Event archive duoc khai bao mot lan, client/server cung hieu.
- Archive chi an khoi san, khong xoa lich su MySQL.
