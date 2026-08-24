# 04 - DATABASE BTL_16

## Vai tro

Database chi ho tro luu tru. Logic realtime va quyet dinh bid van nam o Java server.

## Bang

```text
users
login_history
products
auctions
bids
auction_results
auction_blocked_users
```

## Quan he

```text
users 1 -------- n bids n -------- 1 auctions n -------- 1 products
  |                                      |
  |                                      1
  n                                      |
login_history                       auction_results
```

## users

| Cot | Y nghia |
|---|---|
| user_id | Khoa chinh |
| username | Ten dang nhap unique |
| display_name | Ten hien thi |
| email, phone | Ho so |
| password_hash | PBKDF2 hash |
| password_salt | Salt rieng |
| password_iterations | So vong PBKDF2 |
| active | Cho phep login |
| last_login_at | Login thanh cong gan nhat |

## auctions

| Cot | Y nghia |
|---|---|
| auction_id | Khoa phien |
| host_user_id | Chu tri phien |
| product_id | San pham |
| start_price | Gia khoi diem |
| min_bid_increment | Buoc gia toi thieu |
| current_price | Gia chinh thuc |
| current_winner_id | User dang dan dau |
| start_time, end_time | Moc server |
| status | OPEN/ENDED/CANCELLED |
| ended_at | Thoi diem dong |
| version | Tang moi khi state thay doi |

## products

| Cot | Y nghia |
|---|---|
| product_id | Khoa san pham |
| created_by | Chu so huu san pham |
| code | Ma unique |
| name, description | Noi dung hien thi |
| active | Soft delete; false thi khong tao phien moi |
| created_at, updated_at | Audit thoi gian |

## auction_blocked_users

| Cot | Y nghia |
|---|---|
| auction_id, user_id | Khoa ghep user bi chan tai phien |
| blocked_by | Host thuc hien kick |
| blocked_at | Thoi diem server |

## bids

| Cot | Y nghia |
|---|---|
| bid_id | Khoa lich su |
| auction_id | Phien |
| user_id | Nguoi bid |
| amount | Gia dat |
| server_sequence | Thu tu chinh thuc |
| created_at | Thoi diem server nhan |

## Transaction bid

Trong `JdbcAuctionRepository.commitAcceptedBid()`:

```text
setAutoCommit(false)
-> SELECT auction FOR UPDATE
-> check status/current price/end time
-> INSERT bids
-> UPDATE auctions
-> COMMIT
```

Bat ky loi nao:

```text
ROLLBACK
```

## Hai tang dong bo

```text
Java per-auction ReentrantLock
+
MySQL SELECT ... FOR UPDATE
```

Lock Java bao ve state trong mot server. Row lock MySQL bao ve transaction va lam ro kien thuc JDBC/concurrency.

## Transaction ket thuc

```text
SELECT auction FOR UPDATE
-> check OPEN va endedAt >= endTime
-> UPDATE status=ENDED
-> INSERT/UPDATE auction_results
-> COMMIT
```

Manual end dung cung transaction tren nhung bo dieu kien `endedAt >= endTime`.

Sau khi dong, server giu phong tren danh sach trong 120 giay roi archive khoi runtime hien thi. Archive khong chay `DELETE`; lich su van nam trong `auctions`, `bids` va `auction_results` de truy vet.

## Transaction host control

```text
EXTEND: UPDATE end_time WHERE status=OPEN AND end_time=expected
CANCEL: UPDATE status=CANCELLED WHERE OPEN AND NOT EXISTS bid
KICK: INSERT/UPDATE auction_blocked_users
```

Product dang co auction OPEN khong duoc update/deactivate.

## Tao database

Khuyen nghi:

```bat
scripts\setup-db.cmd
```

Hoac chay main:

```text
vn.ptit.btl16.server.db.DatabaseSetupMain
```

Hoac execute:

```text
sql/00_schema.sql
sql/01_demo_data.sql
```

## Reset demo

```bat
scripts\reset-db.cmd
```

Lenh nay xoa toan bo `btl_16` va tao lai.

## Cau SQL kiem tra

```sql
USE btl_16;
SELECT * FROM users;
SELECT * FROM auctions ORDER BY auction_id DESC;
SELECT * FROM bids ORDER BY server_sequence DESC;
SELECT * FROM auction_results;
SELECT * FROM products ORDER BY product_id DESC;
SELECT * FROM auction_blocked_users;
SELECT * FROM login_history ORDER BY login_id DESC;
```
