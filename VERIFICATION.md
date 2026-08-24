# VERIFICATION - TRANG THAI BIEN DICH VA KIEM THU

Ngay kiem tra: 2026-08-24

## 1. Moi truong da dung

```text
OpenJDK 21.0.11
javac 21.0.11
Compile target: --release 17
```

Project chi dung API Java chuan o compile-time. MySQL Connector/J la runtime dependency bat buoc cua server.

## 2. Bien dich toan bo source

Da bien dich:

```text
107 file Java trong src/main/java
9 file Java trong src/test/java
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
-> tai khoan vua bid phong nguoi khac, vua tao product/phong cua minh
-> host phong A co the sang phong B lam bidder
-> host khong duoc bid
-> minimum bid increment
-> host extend
-> non-host bi tu choi host control
-> kick + AUCTION_KICKED + block rejoin
-> cancel phien co bid bi tu choi
-> manual end
-> cancel phien chua co bid
```

Kich ban tren xac nhan he thong la san tu phuc vu: khong can role toan cuc
`ADMIN`, `SELLER` hay `BUYER`; server cap quyen theo `ownerId` cua product va
`hostUserId` cua tung auction.

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

Da khoi dong giao dien trong smoke test truoc khi chuyen sang MySQL-only:

```text
ServerDashboardMain
ClientMain Swing
```

Hai cua so khoi dong, client tao TCP connection toi server, khong co Java exception trong smoke test.

## 6. Phan JDBC/Laragon

Da xac nhan truc tiep tren MySQL ngay 2026-08-24:

- `DatabaseSchema`, `DemoDataSeeder`, `JdbcUserRepository`, `JdbcAuctionRepository` bien dich thanh cong.
- MySQL lang nghe tai `127.0.0.1:3306`; JDBC `SELECT 1` tra ve thanh cong.
- `scripts\setup-db.cmd` chay thanh cong, khong reset database.
- Schema co du `users`, `login_history`, `products`, `auctions`, `bids`, `auction_results`, `auction_blocked_users`.
- `DatabaseSchema` co migration bo sung owner/host/min increment/active cho database cu.
- Password seed demo/alice/bob da duoc doi chieu dung PBKDF2-HMAC-SHA256 120000 vong.
- `ServerMain` production khoi dong voi `MySQL/JDBC`, lang nghe `0.0.0.0:8888`, sau do da duoc dung va giai phong cong.
- Server khong con tu seed demo khi khoi dong; seed chi chay qua lenh setup/reset chu dong.

Quy trinh chay:

```bat
02_TAO_DATABASE_MYSQL.cmd
03_CHAY_SERVER_MYSQL.cmd
04_CHAY_CLIENT.cmd
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

## 7. Buoc con phu thuoc may nhom

1. Test LAN giua it nhat hai may vat ly sau khi mo Windows Firewall TCP 8888.

Ngoai buoc LAN, build, self-test, migration, JDBC read-only va production server smoke test da duoc chay. Repository production chi con MySQL/JDBC.

## 8. npm development runner

Da xac nhan tren Windows ngay 2026-08-24:

- Node.js `v24.13.0`, npm `11.6.2`.
- `npm run dev:server -- --skip-setup` build va chay MySQL/JDBC server thanh cong.
- Runner in local `127.0.0.1:8888` va LAN `172.11.65.246:8888`.
- `npm run dev -- --skip-setup` mo them mot Swing client local va client ket noi TCP thanh cong.
- `Ctrl+C` dung cac Java process do runner tao; khong con BTL16 process va cong 8888 duoc giai phong.
- `npm test` chay toan bo self-test va tra ve `ALL SELF-TESTS PASSED`.

Dia chi runner in ra la dia chi TCP, khong phai URL trinh duyet. Web client HTTP/WebSocket chua nam trong kien truc hien tai.
