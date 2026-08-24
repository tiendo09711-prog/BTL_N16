# PHAM ANH DUNG - DAT GIA, CONCURRENCY VA LICH SU BID

## Nhiem vu chinh

```text
PLACE_BID
+ validate bid
+ xu ly nhieu bid dong thoi
+ cap nhat gia/winner
+ transaction luu bid
+ BID_ACCEPTED/BID_REJECTED
+ bid history
```

## So do module

```text
AuctionController.handlePlaceBid
  -> BidService.placeBid
       -> require authenticated UserSession
       -> require RoomManager membership
       -> AuctionManager.requireRuntime
       -> runtime.lock
       -> check OPEN/end/currentPrice
       -> AuctionRepository.commitAcceptedBid
            -> SELECT ... FOR UPDATE
            -> INSERT bids
            -> UPDATE auctions
            -> COMMIT
       -> AuctionRuntime.applyAcceptedBid
       -> unlock
       -> BID_UPDATE room
       -> OUTBID user
       -> AUCTION_EXTENDED neu can
```

## Thu tu hoc file

1. `BidRecord.java`
2. `BidCommit.java`
3. `BidOutcome.java`
4. `AuctionRuntime.java`
5. `BidService.java`
6. `AuctionRepository.java`
7. `InMemoryAuctionRepository.commitAcceptedBid()`
8. `JdbcAuctionRepository.commitAcceptedBid()`
9. `AuctionController.handlePlaceBid()`
10. `AuctionBroadcastService.java`
11. `ConcurrentBidLoadTestMain.java`
12. `FullNetworkAuctionSelfTest.java`

## Bien phai thuoc

| Bien | Y nghia |
|---|---|
| `amount` | Muc gia sau validate |
| `expectedPrice` | Gia runtime luc bat dau transaction |
| `previousWinnerId` | Leader cu de gui outbid |
| `remainingMillis` | So ms con lai khi bid |
| `extended` | Co kich hoat anti-sniping |
| `newEndTime` | End time commit vao DB |
| `bidSequence` | Thu tu bid chinh thuc |
| `runtime.lock` | Critical section theo auction |
| `server_sequence` | Sequence luu trong bids |
| `MAX_BID` | Gioi han de validate |
| `DATABASE_CONFLICT` | DB va runtime khong con khop |

## Race condition can giai thich

Gia ban dau 1,000,000:

```text
Thread A doc 1,000,000 -> bid 1,100,000 hop le
Thread B doc 1,000,000 -> bid 1,200,000 hop le
```

Neu khong lock, ca hai co the ghi de. Voi lock:

```text
A lock -> check -> update 1,100,000 -> unlock
B lock -> doc lai 1,100,000 -> check -> update 1,200,000 -> unlock
```

Hoac B vao truoc, A se bi BID_TOO_LOW. Ca hai truong hop deu co final state dung.

## Tai sao hai lop dong bo

### ReentrantLock Java

- Bao ve `AuctionRuntime` trong RAM.
- Chi khoa Auction X, Auction Y van chay song song.

### `SELECT ... FOR UPDATE` MySQL

- Khoa row trong transaction.
- Dam bao insert bid va update auction cung commit/rollback.
- Phat hien state conflict neu sau nay co nhieu server/process.

## Luong publish sau lock

```text
lock
-> validate
-> transaction
-> update runtime
-> tao BidOutcome
unlock
-> broadcast BID_UPDATE
-> notify previous leader
-> broadcast extension
```

Khong giu lock khi gui socket.

## Bai tap

1. Xoa lock va chay load test, quan sat nguy co.
2. Dat breakpoint hai client cung vao `runtime.getLock().lock()`.
3. Test bid bang gia hien tai.
4. Test bid sau end time.
5. Test bid khi chua JOIN room.
6. Xem bang `bids` va `auctions` sau bid.
7. Co tinh gay SQL exception va kiem tra rollback.

## Cau hoi rieng

### 1. Bid hop le can dieu kien gi?

User da login, da vao room, auction OPEN, server time chua qua endTime, amount lon hon currentPrice va trong gioi han.

### 2. Tai sao khong tin currentPrice tren client?

Client co the cu hoac bi sua. Server doc state chinh thuc.

### 3. Tai sao khong tin userId trong request?

Client co the gia mao. User lay tu authenticated session.

### 4. Critical section gom phan nao?

Doc current state, validate, transaction, update runtime va tao outcome.

### 5. Tai sao per-auction lock?

Bid vao Auction A khong can chan Auction B.

### 6. ReentrantLock cong bang `true` de lam gi?

Giam kha nang thread doi lau bi bo doi; khong dam bao thu tu mang tuyet doi nhung queue lock cong bang hon.

### 7. Bid nao den truoc?

Bid nao vao critical section va duoc server chap nhan truoc co sequence truoc. Thoi gian click client khong phai nguon chinh thuc.

### 8. serverSequence sinh luc nao?

Sau validate state trong lock, truoc khi commit bid.

### 9. Neu DB conflict?

Repository rollback va BidService tra DATABASE_CONFLICT, client can RESYNC va thu lai.

### 10. Transaction gom may cau lenh?

SELECT FOR UPDATE, INSERT bids, UPDATE auctions; sau do commit.

### 11. Neu INSERT thanh cong nhung UPDATE loi?

Rollback, bid khong ton tai va gia khong doi.

### 12. BID_ACCEPTED va BID_UPDATE khac gi?

BID_ACCEPTED la response cho nguoi gui; BID_UPDATE la event cho ca room.

### 13. Tai sao co the nguoi gui nhan ca hai?

Nguoi gui cung la subscriber room, nen nhan response va event; client dedup theo bidId.

### 14. Outbid gui cho ai?

Previous winner neu khac bidder moi va dang co active connection.

### 15. Bid history sap xep the nao?

Theo serverSequence giam dan, gioi han `bidHistoryLimit`.

### 16. Bid sat gio lien quan ai?

BidService phat hien window va cap nhat newEndTime; TimerService tiep tuc dung endTime moi.

### 17. Kiem thu dong thoi the nao?

Dung CountDownLatch cho nhieu client gui cung luc, sau do RESYNC final state.

### 18. Tai sao response tu choi la BID_REJECTED thay vi ERROR?

De UI phan biet loi nghiep vu bid va loi he thong chung.

## Nhiem vu nang cap SV01-SV08

- Them `minBidIncrement` vao quy tac bid.
- Chan host tu bid san pham cua minh.
- Kiem tra membership ben trong per-auction lock.
- Review `RoomManager.kickUser`, block rejoin va race kick/bid/end.

Can giai thich tai sao check room truoc lock la chua du khi host kick dong thoi.

## Lo trinh nang cap ca nhan

Muc tieu: hoan thien OP03 va phan an toan cua OP07, dong thoi hoc cach bid lien ket voi room, lifecycle, repository va client.

| Ngay | Noi dung hoc va thuc hanh | Dau ra ban giao |
|---|---|---|
| 1 | Doc ma tinh nang moi, xac dinh invariant bid va kick | Invariant truoc/sau transaction |
| 2 | Pair voi Phuoc hoc runtime, host va min increment | So do state server khi dat gia |
| 3 | Review client cung Thuan ve minimum bid va error | Contract `BID_REJECTED` cho OP03 |
| 4 | Hoan thien min increment, chan host bid, re-check room trong lock | Bid rule authoritative |
| 5 | Hoan thien/review kick, block rejoin va race kick/bid/end | Checklist OP07 dong thoi |
| 6 | Pair voi Duc review bid sat gio, manual end va timer | Ma tran race bid/extend/end/cancel |
| 7 | Them assertion min increment, self-bid va kick race | Test am va concurrency |
| 8 | Doi chieu memory/JDBC, transaction va rollback | Bao cao hai repository |
| 9 | Chay nhieu client dat gia, kick bidder va resync | Final price/winner authoritative |
| 10 | Demo race condition va giai thich state | Demo OP03 + OP07 |

### Dau vao phu thuoc

- Session/error code cua Tien; runtime/repository cua Phuoc.
- Client handler cua Thuan; close/extend/cancel contract cua Duc.

### Dau ra ban giao

- Bid rule co min increment, self-bid prevention va membership re-check.
- Kick/bid/end khong tao state mau thuan; co test va kich ban demo.

### Nguoi review

- Phuoc review runtime/repository; Duc review race voi timer.
- Thuan review error/event client nhan.

### Tieu chi hoan thanh

- Bid duoi `currentPrice + minBidIncrement` bi server tu choi.
- Host khong the bid ke ca khi goi API ngoai UI.
- User bi kick khong the chen bid hoac join lai.
- Snapshot va bid history co cung winner/current price sau test dong thoi.
