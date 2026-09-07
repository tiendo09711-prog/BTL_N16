# 17 - NÂNG CẤP JAVAFX VÀ WEBSOCKET

## Kiến trúc trước và sau

Trước nâng cấp, Swing client phụ thuộc trực tiếp vào `NetworkClient` TCP và server router nhận `ClientConnection` concrete.

Sau nâng cấp:

```text
JavaFX -> AccountApi/AuctionApi -> ClientTransport
                                  |- WebSocketClientTransport
                                  `- TcpClientTransport

TCP ClientConnection -----------+
                                 +-> ServerConnection -> MessageRouter -> business core
WebSocketConnectionAdapter -----+
```

Business rule không bị copy sang WebSocket. TCP và WebSocket dùng chung session, room, lock, transaction, repository và broadcast.

## Swing sang JavaFX

Entrypoint mặc định là `ClientMain -> JavaFxClientApp`. Swing cũ được giữ tại `LegacySwingClientMain` cho mục đích đối chiếu.

JavaFX client có:

- Login/connection URL, register, trạng thái và RTT.
- Auction `TableView`, detail, image, countdown và realtime log.
- Profile/update/change password.
- My Products, create/update/deactivate và preview ảnh.
- Create public/private room.
- Search, join/leave, bid/history và host controls.

Callback transport chạy ngoài JavaFX Application Thread. Mọi cập nhật node UI đều được chuyển qua `Platform.runLater(...)`.

## TCP và WebSocket khác nhau

TCP là byte stream nên project dùng frame:

```text
[4-byte length][binary WireMessage payload]
```

WebSocket đã có message boundary nên mỗi `WireMessage` là một TEXT JSON frame, không bọc thêm TCP length.

```json
{
  "version": 1,
  "kind": "REQUEST",
  "type": "LOGIN",
  "requestId": "uuid",
  "serverSequence": 0,
  "sentAt": 0,
  "data": {"username": "tai_khoan_da_dang_ky", "password": "..."}
}
```

`JsonWireMessageCodec` dùng Jackson, validate kích thước, field, enum và malformed JSON. Không nối JSON thủ công.

## Request, response và event

- Request do client gửi, có `requestId`.
- Response trả đúng `requestId`, hoàn thành pending `CompletableFuture`.
- Event do server chủ động phát, có `serverSequence`, không phụ thuộc request đang chờ.

Một reader/listener nhận cả response và event. Response được correlation trước; event được dispatch cho listener.

## Reconnect, resume và resync

```text
disconnect
-> ConnectionState.DISCONNECTED
-> retry/backoff
-> connect WebSocket mới
-> CONNECTION_WELCOME
-> RESUME_SESSION(sessionToken)
-> RESYNC(auctionId nếu đang ở room)
-> refresh AUCTION_LIST
```

Resume khôi phục identity/session. RESYNC lấy authoritative snapshot mới nhất và rejoin room. Client không replay cache cũ như nguồn sự thật.

## Cross transport

`ConnectionRegistry` chứa `ServerConnection`. `ServerMessagingService` và `AuctionBroadcastService` gửi event theo connection ID, không theo loại transport. Vì vậy:

- TCP tạo auction thì WebSocket nhận `AUCTION_CREATED`.
- TCP bid thì WebSocket nhận `BID_UPDATE`.
- WebSocket bid thì TCP nhận event tương tự.

## File chính

```text
client/network/ClientTransport.java
client/network/TcpClientTransport.java
client/network/WebSocketClientTransport.java
client/fx/JavaFxClientApp.java
client/fx/FxClientController.java
server/network/ServerConnection.java
server/network/WebSocketConnectionAdapter.java
server/network/WebSocketServerTransport.java
common/protocol/JsonWireMessageCodec.java
```

## Trạng thái bàn giao hiện tại

Client EXE dùng system property btl16.client.manualConnect=true để chờ nhập WS URL rồi bấm Kết nối. Server EXE chạy dashboard và copy URL LAN, không phải website. Setup dùng VS Code + XAMPP, db.port theo từng máy; không còn auto-seed. Đăng ký account mới rồi tự tạo sản phẩm/phòng. Xem [05](05_CACH_CHAY_VSCODE_XAMPP.md), [19](19_MULTI_MACHINE_AND_PACKAGING.md) và docs/members cho trách nhiệm hiện tại.
