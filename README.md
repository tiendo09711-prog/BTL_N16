# BTL 16 - HE THONG DAU GIA TRUC TUYEN THOI GIAN THUC

Day la ban ma nguon day du cua project Java desktop cho mon Lap trinh mang:

- Mot TCP server trung tam.
- Nhieu Swing client ket noi dong thoi.
- Dang ky, dang nhap, session, logout, resume session.
- Danh sach phien, chi tiet san pham, room/subscriber.
- Chu so huu them/sua/an san pham va tao phong truc tiep tu client.
- Chu tri gia han, ket thuc som, huy phong chua co bid va moi user.
- Buoc gia toi thieu, chan chu tri tu bid va block user join lai sau kick.
- Dat gia, xu ly nhieu bid dong thoi, khoa theo tung auction.
- Broadcast realtime, thong bao bi vuot gia.
- Timer chinh thuc tren server.
- Anti-sniping: bid hop le trong 10 giay cuoi se gia han 10 giay.
- Ket thuc phien dung mot lan, luu winner va ket qua.
- Phien `ENDED`/`CANCELLED` duoc giu 120 giay de xem ket qua, sau do tu an khoi san va dashboard.
- Disconnect, reconnect, resume va RESYNC snapshot moi nhat.
- JDBC + Laragon MySQL database `btl_16`.
- Server dashboard, client Swing va cong cu test concurrency.

## Mo hinh san tu phuc vu

He thong duoc coi la mot san dau gia trung tam, khong tach role `ADMIN`, `SELLER` hay `BUYER`:

- Moi tai khoan da dang nhap co the tao san pham va mo phien dau gia cua minh.
- Cung tai khoan do co the tham gia dat gia trong phien cua nguoi khac.
- Quyen so huu duoc xet theo tung product; quyen chu tri duoc xet theo tung auction.
- Chu san pham chi sua/an duoc san pham cua minh.
- Chu tri chi gia han, ket thuc, huy hoac kick trong phong cua minh.
- Chu tri khong duoc tu dat gia trong chinh phong do, nhung van duoc bid o phong nguoi khac.

Client chi dong vai tro nguoi dung/nguoi ban/chu tri theo tung phong. Client **khong tro thanh TCP server**; tat ca van qua Java central server de kiem tra quyen, timer, bid va MySQL.

May mo TCP server la may van hanh san. Khi phong dong, du lieu lich su van con trong MySQL; server chi ngung hien thi phong sau `auction.closedVisibilitySeconds=120` va gui `AUCTION_ARCHIVED` de cac client tu xoa khoi danh sach.

## Tai khoan demo

```text
demo  / demo123
alice / alice123
bob   / bob123
```

## Yeu cau

- JDK 17 tro len.
- Node.js 20 tro len de dung lenh `npm run dev`.
- NetBeans hoac VSCode Java Extension Pack.
- Maven de build va tai MySQL Connector/J.
- Laragon MySQL 8.x port 3306. Server chi chay voi MySQL/JDBC.

## Chay nhanh bang npm

Tai thu muc goc, tren may server:

```bash
npm run dev
```

Lenh nay se:

1. Kiem tra MySQL port 3306, thu mo Laragon neu MySQL dang tat.
2. Build Maven va tai MySQL Connector/J.
3. Setup/migrate/seed database `btl_16`.
4. Chay mot `ServerMain` MySQL/JDBC trong terminal hien tai.
5. Mo mot Swing client local ket noi `127.0.0.1:8888`.
6. In cac dia chi TCP LAN de may khac ket noi.

Nhan `Ctrl+C` de dung server va client do runner tao. MySQL duoc giu chay de tranh dung dot ngot database.

Chi chay server, khong mo client local:

```bash
npm run dev:server
```

Bo qua setup/seed khi database da san sang:

```bash
npm run dev -- --skip-setup
```

Tren mot may client co bo source/JDK/Node:

```bash
npm run client -- --host=IP_MAY_SERVER
```

Vi client hien tai la Java Swing dung raw TCP, dia chi `IP:8888` **khong phai link web** va khong mo truc tiep trong trinh duyet. Muon nguoi choi bam mot URL va dau gia ngay can xay them web client HTTP/WebSocket.

## Chay thu cong

Mo `START_HERE.txt`, hoac double-click theo thu tu:

```text
01_BUILD_VA_TEST.cmd
02_TAO_DATABASE_MYSQL.cmd
03_CHAY_SERVER_MYSQL.cmd
04_CHAY_CLIENT.cmd
```

Tu terminal:

```bat
scripts\run-self-tests.cmd
scripts\setup-db.cmd
scripts\run-server-dashboard.cmd
scripts\run-client.cmd
```

Moi script thu cong chi chay mot thanh phan de de quan ly terminal.
Server khong tu seed du lieu khi khoi dong; chi `02_TAO_DATABASE_MYSQL.cmd` tao du lieu demo.

## Chay dung Laragon + MySQL

1. Bat MySQL trong Laragon.
2. Kiem tra `config/server.properties`:

```properties
db.host=127.0.0.1
db.port=3306
db.name=btl_16
db.user=root
db.password=
```

3. Chay:

```bat
scripts\setup-db.cmd
scripts\run-server-dashboard.cmd
```

4. Mo them terminal va chay nhieu lan:

```bat
scripts\run-client.cmd
```

## Main class

| Muc dich | Main class |
|---|---|
| Server console | `vn.ptit.btl16.server.ServerMain` |
| Server dashboard | `vn.ptit.btl16.server.dashboard.ServerDashboardMain` |
| Swing client | `vn.ptit.btl16.client.ClientMain` |
| Console client | `vn.ptit.btl16.client.ConsoleClientMain` |
| Tao database | `vn.ptit.btl16.server.db.DatabaseSetupMain` |
| Reset database | `vn.ptit.btl16.server.db.DatabaseResetMain` |
| Concurrent load test | `vn.ptit.btl16.tools.ConcurrentBidLoadTestMain` |
| Self tests | `vn.ptit.btl16.selftest.AllSelfTests` |

## Cau truc nhanh

```text
Client Swing
  -> ClientController
  -> AccountApi / AuctionApi
  -> NetworkClient
  -> [length][payload] TCP frame
  -> TcpServer / ClientConnection
  -> MessageRouter
  -> AccountController hoac AuctionController
  -> Service
  -> Repository / Session / Room / AuctionRuntime
  -> MySQL
  -> response hoac realtime event quay lai client
```

## Phan cong theo 5 thanh vien

| Thanh vien | Module chinh |
|---|---|
| Do Tien | TCP core, protocol, account, session, system architecture |
| Vu Tri Thuan | realtime/reconnect, management client UI, test va integration |
| Pham Anh Dung | bid, min increment, concurrency va kick/bid safety |
| Mai Trung Duc | timer, host extend/end/cancel va auction result |
| Tran Van Phuoc | product CRUD, host/create room, snapshot va repository data |

## Tai lieu hoc

Doc theo thu tu:

1. `docs/00_BAT_DAU.md`
2. `docs/01_KIEN_TRUC_TONG_THE.md`
3. `docs/02_THU_TU_XAY_DUNG_VA_PHU_THUOC.md`
4. `docs/03_PROTOCOL_VA_LUONG_MESSAGE.md`
5. `docs/08_TU_DIEN_BIEN_THEO_THANH_VIEN.md`
6. File rieng cua tung nguoi trong `docs/members/`
7. `docs/05_CACH_CHAY_NETBEANS_VSCODE_LARAGON.md`
8. `docs/06_CHAY_LAN_MOT_MAY_SERVER.md`
9. `docs/07_DEMO_KIEM_THU.md`
10. `docs/10_CAU_HOI_THAY_HOI_TONG_HOP.md`
11. `docs/16_CHECKLIST_CHUC_NANG_VA_BAN_GIAO.md`

## Luu y ky thuat

- TCP la byte stream; project dung length-prefixed framing.
- Client khong ket noi MySQL truc tiep.
- Server la nguon su that ve gia, winner, end time va status.
- Moi auction co `ReentrantLock` rieng.
- Khong broadcast trong luc giu auction lock.
- Client reconnect phai RESYNC, khong tin cache cu.
- UI client chi la man hinh demo, khong phai trong tam cua bai.

## Trang thai kiem thu

Ma nguon da duoc bien dich bang `javac --release 17` va chay thanh cong cac self-test end-to-end bang repository test tach biet. Xem `VERIFICATION.md`.

Runtime server chi khoi tao `JdbcUserRepository` va `JdbcAuctionRepository`. Can bat Laragon/MySQL truoc khi chay server.
