# TRAN VAN PHUOC - DANH SACH PHIEN, CHI TIET SAN PHAM VA DU LIEU AUCTION

## Nhiem vu chinh

```text
Product data
+ auction list
+ auction detail/snapshot
+ state/repository
+ room join/leave
+ bid history query
+ client list/detail UI
```

## So do module

```text
AUCTION_LIST
-> AuctionController.handleList
-> AuctionQueryService.listAuctions
-> AuctionManager.snapshots
-> AuctionWireData.auctionList
-> AUCTION_LIST_RESULT
-> AuctionTableModel

JOIN_AUCTION
-> require auction
-> RoomManager.join
-> get snapshot + recent bids
-> AUCTION_SNAPSHOT
-> AuctionPanel detail/history
```

## Thu tu hoc file

1. `Product.java`
2. `AuctionStatus.java`
3. `AuctionSnapshot.java`
4. `AuctionRuntime.java`
5. `AuctionRepository.java`
6. `JdbcAuctionRepository.findAllAuctions/findAuctionById/findRecentBids`
7. `TestAuctionRepository.java` trong `src/test`
8. `AuctionManager.java`
9. `AuctionQueryService.java`
10. `RoomManager.java`
11. `AuctionWireData.java`
12. `AuctionController.handleList/handleJoin/handleLeave/handleHistory`
13. Client `ClientAuction`, `ClientWireParser`
14. `AuctionTableModel`, `AuctionPanel`

## Bien phai thuoc

| Bien | Y nghia |
|---|---|
| `productId` | Khoa san pham |
| `code` | Ma san pham unique |
| `auctionId` | Khoa phien |
| `startPrice` | Gia ban dau |
| `currentPrice` | Gia authoritative |
| `currentWinnerId` | Leader ID |
| `currentWinnerUsername` | Ten hien thi leader |
| `startTime`, `endTime` | Thoi gian server |
| `status` | OPEN/ENDED/CANCELLED |
| `version` | Thu tu state cua auction |
| `runtimes` | Map auctionId -> AuctionRuntime |
| `connectionsByAuction` | Room subscribers |
| `auctionsByConnection` | Reverse index de cleanup |
| `watcherCount` | So connection trong room |
| `historyLimit` | So bid gan nhat gui client |

## Snapshot va Runtime

### AuctionSnapshot

- Bat bien.
- An toan de gui qua cac tang.
- Client va dashboard nhan snapshot.

### AuctionRuntime

- Mutable state tren server.
- Co ReentrantLock rieng.
- Chi service server duoc cap nhat.

## Room

```text
connectionsByAuction:
  Auction 1 -> conn A, conn B, conn C

auctionsByConnection:
  conn A -> Auction 1
```

Reverse map giup xoa nhanh moi subscription khi socket mat.

## Du lieu list va detail

List cung cap du thong tin demo:

```text
product name
description
start/current price
leader
end time
status
watcher count
```

Detail them recent bid history.

## Bai tap

1. Them san pham moi trong DB.
2. Them auction moi va tai list lai.
3. JOIN/LEAVE va quan sat watcherCount.
4. Tat client khong LEAVE, quan sat server cleanup room.
5. Kiem tra list sap OPEN truoc ENDED/CANCELLED va tu an sau retention.
6. Thay `bidHistoryLimit` va xem payload.
7. Them cot startPrice vao JTable.

## Cau hoi rieng

### 1. Product va Auction tai sao tach bang?

Mot san pham co the duoc dua vao nhieu phien; quan he 1-n, tranh lap mo ta san pham.

### 2. Snapshot de lam gi?

Dong goi state nhat quan de gui client va tranh cho tang khac sua runtime truc tiep.

### 3. Runtime tai sao khong lay moi lan tu DB?

Realtime can state nhanh trong RAM; DB la persistence va transaction support.

### 4. Khi server start, runtime den tu dau?

AuctionManager load `repository.findAllAuctions()` va tao AuctionRuntime.

### 5. List co lay tu client cache khong?

Khong; request den server va server tao snapshot tu AuctionManager.

### 6. Tai sao room luu connectionId?

Broadcast can gui den socket cu the; mot reconnect tao connectionId moi va phai JOIN/RESYNC lai.

### 7. Tai sao co reverse map?

Khi socket mat, khong can quet moi room de tim connection.

### 8. Client JOIN phien ENDED duoc khong?

Trong 120 giay visibility window thi duoc xem snapshot/history, nhung server tu choi bid. Sau khi `AUCTION_ARCHIVED`, phong bi an va JOIN/RESYNC tra `AUCTION_NOT_FOUND`.

### 9. watcherCount co phai so user unique?

Hien tai la so active connection subscriber; vi mot account chi mot session nen gan bang user count.

### 10. History lay bao nhieu?

Theo `auction.bidHistoryLimit`, mac dinh 50.

### 11. Sap xep history?

Server sequence moi nhat truoc.

### 12. Version de lam gi?

Moi bid/end tang version; client co the bo qua snapshot cu hon.

### 13. Tai sao khong broadcast list moi lan tick?

Lang phi; tick chi gui room. Tao phong dung `AUCTION_CREATED`, con phong het retention dung `AUCTION_ARCHIVED` de cap nhat danh sach realtime.

### 14. RESYNC tra gi?

Neu co auctionId: snapshot + bids va join room moi. Neu khong: auction list.

### 15. DB schema co quan he nao?

Product 1-n Auction, Auction 1-n Bid, User 1-n Bid, Auction 1-1 Result.

### 16. UI co phai phan chinh?

Khong; JTable va detail chi de quan sat luong network va state.

## Nhiem vu nang cap SV01-SV08

- Chu tri Product ownership, update va soft delete.
- Them host/min increment vao snapshot/runtime.
- Dong bo `AuctionRepository`, JDBC/test double, create product/create room/my lists.
- Them runtime moi bang `AuctionManager.addRuntime`.
- Phu trach `ClientProduct`, host column va product/auction data tren UI.

Can demo product tao tu client duoc luu repository va room moi xuat hien khong can restart server.

## Lo trinh nang cap ca nhan

Muc tieu: hoan thien product/create room va persistence, dong thoi hoc cach du lieu duoc authorization, bid, lifecycle va client su dung.

| Ngay | Noi dung hoc va thuc hanh | Dau ra ban giao |
|---|---|---|
| 1 | Doc ma tinh nang moi, ve user -> product -> auction -> blocked user | So do model/ownership |
| 2 | Hoan thien model, repository, JDBC/test double va runtime fields | Contract du lieu SV01-SV08 |
| 3 | Hoan thien CRUD, my products, create/my auctions | OP01/OP02 va tao phong |
| 4 | Pair voi Tien review owner/host tu session va migration | Checklist auth/persistence |
| 5 | Pair voi Dung hoan thien blocked user va kick/rejoin | Repository behavior OP07 |
| 6 | Pair voi Duc doi chieu endTime/status/result | Commit phuc vu lifecycle |
| 7 | Pair voi Thuan dong bo client model, bang va dialog | Field server-client nhat quan |
| 8 | Chay test CRUD/create/list va migrate JDBC | Test double/JDBC cung contract |
| 9 | Tao product/phong khi server chay, reconnect va tai lai | Runtime moi khong can restart |
| 10 | Demo product -> room -> join -> kick -> reload | Luong du lieu end-to-end |

### Dau vao phu thuoc

- Session/protocol/migration cua Tien; bid/block rule cua Dung.
- Lifecycle commit cua Duc; parser/controller/dialog cua Thuan.

### Dau ra ban giao

- Product ownership, update, soft delete va create room dong bo JDBC/test double.
- Runtime moi, my lists va blocked user persistence.

### Nguoi review

- Tien review schema/auth; Dung review runtime/block data.
- Duc review lifecycle commit; Thuan review wire data/UI.

### Tieu chi hoan thanh

- Chi owner sua/an product; soft delete khong mat lich su.
- Chi product active cua owner duoc dung tao phong.
- Phong moi xuat hien khong can restart server.
- Test double va JDBC tra cung snapshot, my lists va block behavior.

## Bo sung moi - Visibility, archive va room cleanup

### Nhiem vu

- Chu tri `AuctionManager.archivedAuctionIds` va `archiveClosedAuctions`.
- An phong archive khoi `snapshots`, `snapshotsByHost`, dashboard stats va `requireRuntime`.
- Them `RoomManager.removeAuction` de don hai chieu auction/connection.
- Bao dam repository/JDBC van giu auction, bid va result; archive khong them lenh `DELETE`.

### Ngay 11 trong lo trinh

| Noi dung hoc va thuc hanh | Dau ra ban giao |
|---|---|
| Ve quan he runtime visible va du lieu persistent | So do RAM/MySQL |
| Hoan thien filter list/my-list/dashboard | Contract visibility |
| Pair voi Duc noi timer vao manager va room cleanup | Luong archive server |
| Pair voi Thuan kiem tra client list va RESYNC | Test end-to-end |

### Tieu chi bo sung

- Phong archive khong con trong `AUCTION_LIST`, `MY_AUCTIONS` hay dashboard.
- `requireRuntime` tra `AUCTION_NOT_FOUND` cho phong da archive.
- Room/subscription duoc don hai chieu, du lieu MySQL van truy van duoc.
