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
- Disconnect, reconnect, resume va RESYNC snapshot moi nhat.
- JDBC + Laragon MySQL database `btl_16`.
- Che do memory de chay ngay khong can database.
- Server dashboard, client Swing va cong cu test concurrency.

## Tai khoan demo

```text
demo  / demo123
alice / alice123
bob   / bob123
```

## Yeu cau

- JDK 17 tro len.
- NetBeans hoac VSCode Java Extension Pack.
- Maven khi chay che do JDBC de tai MySQL Connector/J.
- Laragon MySQL 8.x port 3306 cho che do JDBC.

## Cach nhanh nhat de xem project chay

Mo `START_HERE.txt`, hoac double-click theo thu tu:

```text
01_TEST_KHONG_CAN_MYSQL.cmd
02_DEMO_NGAY_KHONG_CAN_MYSQL.cmd
```

Tu terminal, cac lenh tuong ung la:

```bat
scripts\run-self-tests-jdk-only.cmd
scripts\run-local-demo-memory.cmd
```

Lenh demo mo mot server memory va ba cua so client.

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

Hoac mot lenh demo:

```bat
scripts\run-local-demo-jdbc.cmd
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

Ma nguon da duoc bien dich bang `javac --release 17` va chay thanh cong cac self-test memory end-to-end. Xem `VERIFICATION.md`.

Duong JDBC da duoc bien dich, nhung can chay `DatabaseSetupMain` tren may co Laragon/MySQL de xac nhan password va cau hinh MySQL cu the cua nhom.
