# 03 - PROTOCOL VA LUONG MESSAGE

## Hai transport dùng chung WireMessage

JavaFX EXE mặc định dùng WebSocket + JsonWireMessageCodec tại /ws, không bọc JSON bằng length-prefix TCP. WS callback qua WebSocketConnectionAdapter về cùng MessageRouter. pendingRequests có cả ở WebSocketClientTransport và NetworkClient; response dùng requestId, event được dispatch về UI.

## Framing TCP (legacy/test)

TCP chi cung cap byte stream. Project dung:

```text
+----------------------+----------------------------+
| frameLength: 4 bytes | payload: frameLength byte |
+----------------------+----------------------------+
```

`LengthPrefixedMessageCodec.read()` goi `readFully()` de dam bao doc du payload.

## Payload

```text
magic:int
version:int
kind:string
messageType:string
requestId:string
serverSequence:long
sentAtEpochMillis:long
dataCount:int
repeat dataCount:
  key:string
  value:string
```

Moi string co:

```text
stringLength:int
UTF-8 bytes
```

## Ba loai message

| Kind | Nguon | Vi du | requestId |
|---|---|---|---|
| REQUEST | Client | LOGIN, PLACE_BID | Co |
| RESPONSE | Server | LOGIN_RESULT, BID_ACCEPTED | Giu requestId cua request |
| EVENT | Server | BID_UPDATE, AUCTION_ENDED, AUCTION_ARCHIVED | Khong can |

## requestId

- Do client tao.
- `NetworkClient.pendingRequests` luu `requestId -> CompletableFuture`.
- Khi response ve, reader thread complete dung future.
- Cho phep client gui nhieu request ma khong nham response.

## serverSequence

- Do server tao bang `AtomicLong`.
- Bid accepted co sequence rieng va duoc luu DB.
- Event `BID_UPDATE` dung sequence cua bid.
- Client co the bo qua update cu neu mo rong them logic.

## Message account

| Request | Response |
|---|---|
| REGISTER | REGISTER_RESULT |
| LOGIN | LOGIN_RESULT |
| RESUME_SESSION | RESUME_SESSION_RESULT |
| LOGOUT | LOGOUT_RESULT |
| GET_PROFILE | PROFILE_RESULT |
| UPDATE_PROFILE | UPDATE_PROFILE_RESULT |
| CHANGE_PASSWORD | CHANGE_PASSWORD_RESULT |
| PING | PONG |

## Message auction

| Request | Response/Event |
|---|---|
| CREATE_PRODUCT | CREATE_PRODUCT_RESULT |
| MY_PRODUCTS | MY_PRODUCTS_RESULT |
| UPDATE_PRODUCT | UPDATE_PRODUCT_RESULT |
| DEACTIVATE_PRODUCT | DEACTIVATE_PRODUCT_RESULT |
| RESTORE_PRODUCT | RESTORE_PRODUCT_RESULT |
| AUCTION_LIST | AUCTION_LIST_RESULT |
| CREATE_AUCTION | CREATE_AUCTION_RESULT |
| MY_AUCTIONS | MY_AUCTIONS_RESULT |
| JOIN_AUCTION | AUCTION_SNAPSHOT |
| LEAVE_AUCTION | LEAVE_AUCTION_RESULT |
| GET_BID_HISTORY | BID_HISTORY_RESULT |
| PLACE_BID | BID_ACCEPTED hoac BID_REJECTED |
| EXTEND_AUCTION | EXTEND_AUCTION_RESULT |
| END_AUCTION | END_AUCTION_RESULT |
| CANCEL_AUCTION | CANCEL_AUCTION_RESULT |
| KICK_AUCTION_USER | KICK_AUCTION_USER_RESULT |
| RESYNC | RESYNC_RESULT |
| Server push | BID_UPDATE |
| Server push | OUTBID_NOTIFICATION |
| Server push | AUCTION_EXTENDED |
| Server push | AUCTION_CREATED |
| Server push | AUCTION_CANCELLED |
| Server push | AUCTION_KICKED |
| Server push | AUCTION_TICK |
| Server push | AUCTION_ENDED |
| Server push | AUCTION_ARCHIVED |

## Khôi phục mặt hàng đã ngừng

`RESTORE_PRODUCT` gửi `productId`, yêu cầu đăng nhập và quyền sở hữu; thành công trả `RESTORE_PRODUCT_RESULT` với dữ liệu mặt hàng và `active=true`. Yêu cầu trên mặt hàng đã hoạt động là no-op, kể cả `updatedAt`, nhưng vẫn kiểm tra chủ sở hữu. Lỗi được trả qua `ERROR` với `AUTH_REQUIRED`, `AUCTION_FORBIDDEN`, `PRODUCT_NOT_FOUND`, `PRODUCT_IN_USE` hoặc `PRODUCT_ALREADY_SOLD` tùy trường hợp.

`MY_PRODUCTS_RESULT` bổ sung `product.{index}.restorationStatus`: `ACTIVE`, `ELIGIBLE`, `OPEN_AUCTION` hoặc `SOLD`. Client cũ vẫn đọc các trường cũ bình thường; client mới dùng `UNKNOWN` nếu thiếu hoặc chưa hiểu trạng thái. Đây là thông tin gợi ý cho giao diện, có thể cũ ngay khi hiển thị; server luôn kiểm tra lại trong transaction.

Khôi phục, tạo phiên và ngừng mặt hàng cùng khóa hàng dữ liệu mặt hàng trước khi kiểm tra/ghi. Khôi phục chỉ cập nhật trạng thái hoạt động của mặt hàng đủ điều kiện, không tạo/phát sự kiện mở phiên, không sửa phiên cũ, lượt trả giá, kết quả hoặc lệnh cấm. Quy tắc đóng băng thông tin/ảnh và tối đa một phiên mở cho mỗi mặt hàng không thay đổi.

## Field quan trong

### PLACE_BID

```text
auctionId
amount
```

Server khong nhan `userId` tu client. User duoc lay tu session.

### CREATE_PRODUCT / UPDATE_PRODUCT

```text
productId        chi UPDATE
code
name
description
```

Owner luon lay tu session. `DEACTIVATE_PRODUCT` la soft delete bang `active=false`.

### CREATE_AUCTION

```text
productId
startPrice
minBidIncrement
durationMinutes
```

Server tu gan `hostUserId`, `startTime`, `endTime`, `currentPrice`, `status` va `version`.

### Host control

```text
EXTEND_AUCTION: auctionId, extensionSeconds
END_AUCTION: auctionId
CANCEL_AUCTION: auctionId
KICK_AUCTION_USER: auctionId, username
```

Tat ca deu kiem tra host theo session tren server.

Quyen host chi ap dung cho auction cu the. Khong co role seller/buyer toan cuc: mot user co the tao phong cua minh va van bid trong phong cua user khac. Client khong duoc coi la network server cua phong.

### AUCTION_ARCHIVED

```text
auctionId
productName
status
message
serverNow
```

Server gui event nay cho tat ca connection sau khi phong da `ENDED` hoac `CANCELLED` du 120 giay. Client xoa auction khoi list va roi joined room neu dang giu phong do. Day khong phai lenh xoa du lieu MySQL.

### Auction snapshot

```text
auctionId
productId
productCode
productName
description
hostUserId
hostUsername
startPrice
minBidIncrement
currentPrice
currentWinnerId
currentWinnerUsername
startTime
endTime
status
endedAt
version
watcherCount
bidCount
bid.0.*
bid.1.*
serverNow
```

### Bid record

```text
bidId
auctionId
userId
username
amount
serverSequence
createdAt
```

## Loi

Generic loi:

```text
type=ERROR
success=false
errorCode=AUTH_REQUIRED
message=Login is required
```

Bid loi duoc tra bang:

```text
type=BID_REJECTED
success=false
errorCode=BID_TOO_LOW
message=Bid must be at least currentPrice + minBidIncrement
```

Loi quan tri thuong gap:

```text
AUCTION_FORBIDDEN
PRODUCT_NOT_FOUND
PRODUCT_INACTIVE
PRODUCT_IN_USE
AUCTION_ALREADY_EXISTS
AUCTION_HAS_BIDS
USER_BLOCKED_FROM_AUCTION
USER_NOT_IN_AUCTION_ROOM
```

## Luong PLACE_BID chi tiet

```text
FxClientController.placeBid()
-> AuctionApi.bid()
-> ClientTransport.sendRequest()
-> WebSocketClientTransport: requestId + JSON WireMessage
-> WebSocketServerTransport / WebSocketConnectionAdapter
-> MessageRouter
-> AuctionController.handlePlaceBid()
-> BidService.placeBid()
-> require session
-> lock AuctionRuntime
-> recheck room membership / no host self-bid
-> validate OPEN / endTime / currentPrice + minBidIncrement
-> AuctionRepository.commitAcceptedBid()
-> DB transaction / FOR UPDATE
-> update AuctionRuntime
-> unlock
-> BID_UPDATE room broadcast
-> OUTBID notification
-> optional AUCTION_EXTENDED
-> BID_ACCEPTED response
```

## WebSocket JSON va protocol moi

- TCP van dung length-prefixed binary codec.
- WebSocket dung mot TEXT JSON frame cho mot `WireMessage` qua `JsonWireMessageCodec`.
- Message moi: `GET_PRODUCT_IMAGE`, `GET_PRODUCT_IMAGE_RESULT`, `SEARCH_AUCTIONS`, `SEARCH_AUCTIONS_RESULT`.
- Field mo rong: image metadata, `visibility`, `requiresPassword`, `roomPassword` chi trong request join/create.
- Error moi: room password, image validation va invalid search query.
- `AUCTION_LIST_RESULT` khong duoc chua `imageBase64`.

## Kenh media tep (11/10/2026)

- `GET_MEDIA_ENDPOINT` / `GET_MEDIA_ENDPOINT_RESULT`: route da dang nhap, tra `mediaPort`; client dung host cua ket noi WebSocket/TCP hien tai, khong hard-code IP.
- HTTP `POST /v1/drafts?productId=...`, `POST /v1/drafts/{id}/files?kind=IMAGE|VIDEO&name=...`, `POST /v1/drafts/{id}/transform`, `POST /v1/drafts/{id}/commit`, `DELETE /v1/drafts/{id}`, `DELETE /v1/drafts/{id}/files/{mediaId}`. Dieu khien JSON co gioi han 64 KiB; upload la raw stream, Bearer session hien tai, khong Base64 video.
- `GET /v1/products/{productId}?auctionId=...` tra metadata va URL tuong doi co ve ngan han; `GET /v1/limits` tra gioi han cho form. Commit chi nhan danh sach ID do server da tiep nhan/duoc phep giu lai, khong nhan duong dan dia phuong.
- `GET|HEAD /v1/files/{mediaId}/{thumbnail|detail|original|video}?ticket=...` kiem tra lai session/owner/private grant/block va media moi lan; ho tro Range 206/416. Ve rang buoc session, media, ban nhap/ngu canh phong va het han sau 30 phut. Khong co static file server cho kho media.
- Loi nghiep vu co `PRODUCT_INCOMPLETE`, `MEDIA_INVALID`, `MEDIA_LIMIT`, `MEDIA_STORAGE_FULL`, `MEDIA_PROCESSING_FAILED`, `UPLOAD_CANCELLED`. Luong CREATE/UPDATE_PRODUCT legacy mot anh van di qua pipeline tep; GET_PRODUCT_IMAGE legacy chi tra thumbnail JPEG nho.
- Chi tiet luong, gioi han, schema metadata va sao luu: `docs/23_ANH_VIDEO_SAN_PHAM.md`. Khong doi framing/bid/result/restore cua luong dau gia hien co.
