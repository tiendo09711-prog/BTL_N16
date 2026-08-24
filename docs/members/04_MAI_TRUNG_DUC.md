# MAI TRUNG DUC - TIMER, ANTI-SNIPING, KET THUC VA KET QUA

## Nhiem vu chinh

```text
Timer chinh thuc tren server
+ countdown event
+ anti-sniping
+ dong phien dung mot lan
+ xac dinh winner
+ luu auction_results
+ AUCTION_ENDED
```

## So do module

```text
AuctionTimerService.auction-timer
  -> lap qua AuctionRuntime
  -> closeIfExpired
       -> lock auction
       -> check status OPEN va now >= endTime
       -> AuctionRepository.closeAuction transaction
       -> runtime.markEnded
       -> unlock
       -> broadcast AUCTION_ENDED

Moi tickMillis:
  -> snapshot OPEN auctions co subscriber
  -> AUCTION_TICK
```

Anti-sniping duoc tinh trong BidService:

```text
valid bid trong antiSnipingWindowSeconds
-> newEndTime = oldEndTime + extensionSeconds
-> commit DB + runtime
-> AUCTION_EXTENDED
```

Timer luon doc endTime moi.

## Thu tu hoc file

1. `AuctionStatus.java`
2. `AuctionSnapshot.java`
3. `AuctionRuntime.java`
4. `AuctionResult.java`
5. `CloseAuctionCommit.java`
6. `AuctionTimerService.java`
7. `BidService` phan anti-sniping
8. `JdbcAuctionRepository.closeAuction()`
9. `AuctionWireData.tick/extended/ended()`
10. `AuctionBroadcastService.java`
11. Client `handleEvent` cho TICK/EXTENDED/ENDED

## Bien phai thuoc

| Bien | Y nghia |
|---|---|
| `checkMillis` | Chu ky quet phien het han |
| `tickMillis` | Chu ky phat countdown event |
| `lastTickAt` | Moc tick da phat gan nhat |
| `endTime` | Thoi gian dong chinh thuc |
| `status` | OPEN/ENDED |
| `endedAt` | Thoi diem server dong |
| `antiSnipingWindowSeconds` | Khoang sat gio de gia han |
| `extensionSeconds` | So giay cong them |
| `winnerId` | Current winner khi close |
| `finalPrice` | Current price khi close |
| `version` | State version tang khi bid/end |

## Dong phien mot lan

Hai lop bao ve:

```text
AuctionRuntime lock
+
DB row status check trong transaction
```

Neu hai lan timer/chuc nang cung thu close:

- Thread dau chuyen OPEN -> ENDED.
- Thread sau thay status ENDED va bo qua.
- `auction_results.auction_id` la primary key.

## Ranh gioi bid va end

BidService va TimerService dung cung lock.

```text
Neu bid lay lock truoc:
  bid van check now < endTime
  neu hop le co the gia han
  timer doc endTime moi

Neu timer lay lock truoc va close:
  bid sau thay status ENDED va bi tu choi
```

## Countdown client

Server gui:

```text
serverNow
endTime
remainingMillis
status
```

Client co the noi suy countdown giua hai tick, nhung khong duoc tu dong quyet dinh winner.

## Bai tap

1. Giam demo short auction con 20 giay.
2. Bid khi con 9 giay, xem +10.
3. Bid khi con 11 giay, xem khong gia han.
4. Thu bid sau AUCTION_ENDED.
5. Kiem tra `auction_results` va `auctions.status`.
6. Dat breakpoint timer va bid cung lock.
7. Chay hai trigger close gan dong thoi va chung minh mot result.

## Cau hoi rieng

### 1. Tai sao timer nam o server?

Client clock khac nhau va co the bi sua; server moi quyet dinh end time.

### 2. ScheduledExecutorService lam gi?

Chay task check timer dinh ky tren mot thread co ten `auction-timer`.

### 3. Tai sao check 200 ms nhung tick 1000 ms?

Can close kha chinh xac nhung khong can broadcast UI qua day.

### 4. Anti-sniping duoc kich hoat luc nao?

Bid hop le va remaining time nho hon hoac bang window 10 giay.

### 5. End time duoc cong tu now hay oldEndTime?

Cong vao oldEndTime, giu quy tac moi bid sat gio them dung 10 giay.

### 6. Neu nhieu bid lien tiep trong 10 giay cuoi?

Moi bid hop le co the tiep tuc cong 10 giay, tuy rule hien tai.

### 7. Tai sao BidService cap nhat end time chu khong TimerService?

Gia han la mot phan cua transaction bid va phai cung atomic voi accepted bid.

### 8. Lam sao timer thay endTime moi?

BidService cap nhat AuctionRuntime trong cung lock; timer snapshot/runtime doc gia tri moi.

### 9. Close transaction gom gi?

Lock row, kiem tra OPEN/end, update auctions ENDED, insert/upsert result, commit.

### 10. Auction khong co bid thi winner la ai?

winnerId null, finalPrice bang start/current price, UI hien khong co winner.

### 11. Tai sao broadcast sau unlock?

Socket cham khong duoc giu auction lock.

### 12. AUCTION_TICK co luu DB khong?

Khong; tick chi la event hien thi. EndTime da nam trong state/DB.

### 13. Client tat countdown ve 0 co dong phien khong?

Khong. Chi server timer thay doi status.

### 14. Neu server tre 300 ms moi close?

Status chinh thuc van dua tren endTime; bid sau endTime bi tu choi du timer chua broadcast end.

### 15. Lam sao dam bao AUCTION_ENDED chi phat mot lan?

Chi broadcast khi repository `closeAuction` tra ve result present va runtime vua markEnded.

### 16. endedAt va endTime khac gi?

endTime la han du kien/chinh thuc; endedAt la luc task server thuc su commit close.

## Nhiem vu nang cap SV01-SV08

- Chu tri host extend va `extensionSource=HOST`.
- Manual end dung chung lock/transaction voi timer close.
- Cancel chi khi chua co bid va chuyen `CANCELLED`.
- Review `ExtendAuctionCommit`, `CancelAuctionCommit`, `CloseAuctionCommit.requireExpired`.

Can demo timer close va host close khong the tao hai ket qua.

## Lo trinh nang cap ca nhan

Muc tieu: hoan thien quyen dieu khien thoi gian cua host va hoc quan he giua create room, bid, timer, transaction va UI.

| Ngay | Noi dung hoc va thuc hanh | Dau ra ban giao |
|---|---|---|
| 1 | Doc ma tinh nang moi, ve `OPEN -> ENDED/CANCELLED` | So do lifecycle |
| 2 | Pair voi Tien hoc host authorization va error contract | Checklist extend/end/cancel |
| 3 | Pair voi Phuoc hoc endTime, host, result va repository | Bang field runtime/DB |
| 4 | Pair voi Dung hoc anti-sniping va race bid/close | Ma tran thu tu bid/extend/end |
| 5 | Hoan thien host extend, manual end, cancel no-bid, close-once | Service/commit JDBC va test double |
| 6 | Pair voi Thuan bind nut host, countdown va status | UI dung quyen/trang thai |
| 7 | Them assertion extend/end/cancel va timer/manual race | Test lifecycle |
| 8 | Chay JDBC, kiem tra result duy nhat va rollback | Bien ban transaction |
| 9 | Thu bid, gia han, ket thuc som va huy bang nhieu client | Kich ban lifecycle |
| 10 | Demo timer close va host close cung luc | Bang chung mot ket qua |

### Dau vao phu thuoc

- Protocol/host authorization cua Tien; runtime/repository cua Phuoc.
- Bid/anti-sniping rule cua Dung; host controls cua Thuan.

### Dau ra ban giao

- Host extend, manual end va cancel no-bid dong bo JDBC/test double.
- Test close-once va kich ban demo lifecycle.

### Nguoi review

- Tien review authorization; Dung review lock/race.
- Phuoc review commit/runtime; Thuan review event/status.

### Tieu chi hoan thanh

- User khong phai host khong the extend, end hoac cancel.
- Timer va host cung close van chi co mot `auction_result`.
- Phong co bid khong the cancel nhung co the ket thuc som.
- Client hien dung `ENDED` hoac `CANCELLED`.
