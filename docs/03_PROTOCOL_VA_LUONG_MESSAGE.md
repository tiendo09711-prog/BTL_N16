# 03 - PROTOCOL VA LUONG MESSAGE

## Framing

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
| EVENT | Server | BID_UPDATE, AUCTION_ENDED | Khong can |

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
message=Bid must be greater than current price
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
AuctionPanel
-> ClientController.placeBid()
-> AuctionApi.bid()
-> NetworkClient.sendRequest()
-> requestId + frame
-> ClientConnection.read loop
-> MessageRouter
-> AuctionController.handlePlaceBid()
-> BidService.placeBid()
-> require session
-> require room membership
-> lock AuctionRuntime
-> validate OPEN/end/currentPrice
-> AuctionRepository.commitAcceptedBid()
-> DB transaction / FOR UPDATE
-> update AuctionRuntime
-> unlock
-> BID_UPDATE room broadcast
-> OUTBID notification
-> optional AUCTION_EXTENDED
-> BID_ACCEPTED response
```
