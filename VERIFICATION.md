# VERIFICATION - TRANG THAI BIEN DICH VA KIEM THU

Ngay kiem tra: 2026-08-24

## 1. Moi truong da dung

```text
OpenJDK 21.0.11
javac 21.0.11
Compile target: --release 17
```

Project chi dung API Java chuan o compile-time. MySQL Connector/J la runtime dependency khi bat `repository.mode=jdbc`.

## 2. Bien dich toan bo source

Da bien dich:

```text
109 file Java trong src/main/java
7 file Java trong src/test/java
Tong cong 116 file Java
```

Lenh:

```bash
./scripts/compile-jdk-only.sh
```

Ket qua: thanh cong.

Kiem tra them voi `javac -Xlint:all` khong co loi; chi co canh bao serialization thong thuong cua cac lop Swing/JTable model, khong anh huong chay chuong trinh.

## 3. Self-test end-to-end

Lenh:

```bash
./scripts/run-self-tests-jdk-only.sh
```

Ket qua cuoi:

```text
[PASS] ProtocolCodecSelfTest
[PASS] PasswordHasherSelfTest
[PASS] SessionManagerSelfTest
[PASS] FullNetworkAuctionSelfTest
[PASS] AuctionManagementSelfTest
ALL SELF-TESTS PASSED
```

`FullNetworkAuctionSelfTest` mo TCP server that tren mot port ngau nhien va kiem tra:

```text
2 client TCP that
-> login
-> list auction
-> JOIN cung room
-> bid va BID_UPDATE realtime
-> OUTBID_NOTIFICATION
-> gui bid gan dong thoi
-> state cuoi authoritative
-> disconnect
-> RESUME_SESSION tren socket moi
-> RESYNC snapshot moi nhat
-> timer dong phien
-> AUCTION_ENDED
-> bid sau khi het gio bi tu choi
```

`AuctionManagementSelfTest` kiem tra:

```text
create/list/update/deactivate product
-> create/my auctions
-> owner tro thanh host
-> host khong duoc bid
-> minimum bid increment
-> host extend
-> non-host bi tu choi host control
-> kick + AUCTION_KICKED + block rejoin
-> cancel phien co bid bi tu choi
-> manual end
-> cancel phien chua co bid
```

## 4. Kiem thu cross-process

Da chay `ServerMain`, `ConsoleClientMain` va load-test trong cac process Java tach biet.

Console client da hoan thanh:

```text
CONNECTION_WELCOME
LOGIN
AUCTION_LIST co 3 phien
JOIN_AUCTION
PLACE_BID
BID_UPDATE event
PING/PONG
LOGOUT
```

Load test 5 socket dong thoi da hoan thanh; moi request di qua TCP server, per-auction lock va final RESYNC. Lan kiem tra gan nhat:

```text
Clients: 5
Accepted: 5, rejected: 0
Authoritative final price: 1,150,000 VND
Winner: client co bid cao nhat
```

So accepted co the thay doi theo thu tu thread va muc gia; dieu can bao dam la moi bid duoc kiem tra lai trong critical section va snapshot cuoi dung voi bid cao nhat da duoc chap nhan.

## 5. Kiem thu giao dien

Da khoi dong bang virtual display:

```text
ServerDashboardMain o memory mode
ClientMain Swing
```

Hai cua so khoi dong, client tao TCP connection toi server, khong co Java exception trong smoke test.

## 6. Phan JDBC/Laragon

Da xac nhan:

- `DatabaseSchema`, `DemoDataSeeder`, `JdbcUserRepository`, `JdbcAuctionRepository` bien dich thanh cong.
- Schema va cau SQL phu hop cac bang `users`, `login_history`, `products`, `auctions`, `bids`, `auction_results`, `auction_blocked_users`.
- `DatabaseSchema` co migration bo sung owner/host/min increment/active cho database cu.
- Password seed demo/alice/bob da duoc doi chieu dung PBKDF2-HMAC-SHA256 120000 vong.

Chua chay ket noi MySQL/Laragon thuc trong lan nang cap nay. Buoc xac nhan bat buoc tren may nhom:

```bat
03_TAO_DATABASE_LARAGON.cmd
04_CHAY_SERVER_LARAGON.cmd
05_CHAY_CLIENT.cmd
```

Sau do kiem tra:

```sql
USE btl_16;
SELECT * FROM users;
SELECT * FROM auctions;
SELECT * FROM bids ORDER BY server_sequence DESC;
SELECT * FROM auction_results;
SELECT * FROM auction_blocked_users;
```

## 7. Hai buoc con phu thuoc may nhom

1. Test JDBC tren Laragon cua may server, do password/root config co the khac nhau.
2. Test LAN giua it nhat hai may vat ly sau khi mo Windows Firewall TCP 8888.

Ngoai hai buoc moi truong nay, full luong memory/TCP/concurrency/reconnect/timer da duoc chay va xac nhan.
