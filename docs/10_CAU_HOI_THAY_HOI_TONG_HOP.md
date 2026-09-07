# 10 - CAU HOI THAY CO THE HOI TONG HOP

## Kien truc

### 1. Tai sao dung mot server trung tam?

Server phai la nguon su that ve gia, winner, end time va status. Mot server giup de giai thich, phu hop BTL va tranh distributed complexity khong can thiet.

### 2. Client co ket noi DB khong?

Khong. Client mac dinh WebSocket toi Java server; TCP la transport legacy/test. Server moi JDBC toi MySQL.

### 3. Tai sao khong microservice?

Khong giup hoc them trong tam TCP/concurrency cho quy mo BTL, lai tang deployment complexity.

## TCP

### 4. Tai sao `1 send != 1 recv`?

TCP la byte stream. He dieu hanh co the tach mot write thanh nhieu read hoac gom nhieu write vao mot read.

### 5. Project tao ranh gioi message the nao?

Voi TCP: bon byte dau la frame length, sau do readFully doc payload. Voi WebSocket: thu vien tach message, JsonWireMessageCodec decode JSON WireMessage.

### 6. Tai sao co maxFrameBytes?

Chan client khai bao frame qua lon lam server cap phat bo nho bat thuong.

### 7. Tai sao moi socket co output lock?

Bid thread, timer thread va controller co the cung gui. Neu ghi xen byte thi frame bi hong.

## Thread

### 8. Co nhung thread nao?

Acceptor, client workers, auction timer, session cleanup; client chinh co FX Application Thread, WebSocket callback, heartbeat/reconnect/request timeout; Swing EDT chi cho client legacy/dashboard.

### 9. Tai sao dung worker pool?

Gioi han va quan ly thread ro rang hon tao thread khong gioi han, van de hoc va demo.

### 10. ConcurrentHashMap da du chua?

Khong trong SessionManager, vi mot thao tac can cap nhat dong bo ba map. Can `mutationLock`.

## Protocol

### 11. requestId va serverSequence khac nhau?

requestId ghep response voi request. serverSequence xac dinh thu tu event/bid do server cap.

### 12. Response va event khac nhau?

Response tra cho mot request va co requestId. Event do server chu dong push, vi du BID_UPDATE.

### 13. Them module moi the nao?

Viet controller/service/repository, sau do `router.register` trong mot `ServerModule`; khong sua accept loop.

## Session

### 14. userId, connectionId, sessionToken khac gi?

userId la danh tinh DB; connectionId la mot socket; sessionToken dung de resume danh tinh tren socket moi.

### 15. Disconnect va logout khac gi?

Disconnect chuyen session thanh DETACHED trong grace period. Logout xoa han token.

### 16. Tai sao chan mot account login hai noi?

De session, room va thong bao user co mot dich den ro rang trong ban BTL.

## Room/realtime

### 17. Tai sao can RoomManager?

Auction 1 chi broadcast cho client dang xem Auction 1, khong gui du lieu khong lien quan.

### 18. Room luu userId hay connectionId?

Luu connectionId vi broadcast can socket cu the. Thong bao rieng user thi SessionManager tim connectionId active.

### 19. Socket mat thi room the nao?

Connection lifecycle listener goi `rooms.removeConnection`.

## Bid/concurrency

### 20. Race condition co the xay ra ra sao?

Hai worker cung doc gia 1,000,000 va cung cho bid hop le, dan den ghi de va hai winner.

### 21. Giai phap?

Lock rieng tung AuctionRuntime, check va update trong critical section. JDBC con SELECT FOR UPDATE.

### 22. Tai sao khong khoa toan server?

Auction A va B phai xu ly song song; global lock lam giam concurrency.

### 23. Tai sao khong broadcast trong lock?

Client cham co the block socket, keo dai lock va ngan bid tiep theo.

### 24. Client gui userId co tin khong?

Khong. BidService lay user tu authenticated session.

## Timer

### 25. Countdown client co chinh thuc khong?

Khong. Client chi hien thi dua tren serverNow/endTime. Server timer moi dong phien.

### 26. Anti-sniping o dau?

BidService tinh remaining time trong lock; neu trong window thi tang endTime va broadcast extension.

### 27. Lam sao dong phien mot lan?

Timer dung cung auction lock, kiem tra status OPEN; repository transaction cung kiem tra row status.

### 28. Bid den dung luc het gio thi sao?

Ben nao lay lock truoc van phai check `now < endTime`. Neu qua endTime, bid bi tu choi; timer se dong phien.

## JDBC

### 29. PreparedStatement de lam gi?

Bind tham so an toan, tach SQL khoi du lieu va xu ly kieu ro rang.

### 30. Transaction bid gom gi?

Lock row, insert bid, update current price/winner/end time, commit; loi thi rollback.

### 31. Tai sao vua Java lock vua DB lock?

Java lock bao ve runtime trong process; DB lock bao ve row transaction va tranh state conflict.

## Reconnect

### 32. Tai sao khong tiep tuc dung cache cu?

Trong luc mat ket noi, gia/timer/winner co the da thay doi.

### 33. Reconnect flow?

Ket noi lai transport WS/TCP -> RESUME_SESSION -> RESYNC auction -> snapshot moi -> tiep tuc nhan event.

### 34. Server restart thi token cu con dung khong?

Khong trong ban nay vi session luu RAM; client can login lai. Day la quyet dinh pham vi hop ly.

## MVC

### 35. View co lam network khong?

Khong. JavaFX FxClientController xu ly input, goi AccountApi/AuctionApi qua ClientTransport; ClientAppModel giu state. Swing legacy co MainFrame/ClientController/NetworkClient.

### 36. Tai sao phai cap nhat UI tren dung thread?

JavaFX dung Platform.runLater tren FX thread. Voi Swing legacy/dashboard, Swing component khong thread-safe; callback network phai `SwingUtilities.invokeLater`.

## Demo

### 37. Do wow cua bai nam o dau?

Nhieu client, realtime room broadcast, race condition, lock, anti-sniping, server timer, disconnect/resync va transaction; khong nam o UI dep.

### 38. Kiem thu concurrency the nao?

Self-test mo socket that va `ConcurrentBidLoadTestMain` tao nhieu client gui bid gan dong thoi.

## Mo hinh san tu phuc vu

### 39. Tai sao khong tach ADMIN, SELLER va BUYER?

Pham vi bai tap coi day la san tu phuc vu. Moi tai khoan co the mua va ban; quyen duoc gioi han bang ownership cua product va hostUserId cua tung auction, nen khong can role toan cuc.

### 40. Client tao phong co tro thanh server khong?

Khong. Client chi tro thanh host nghiep vu cua phong. Java central server van giu socket, timer, room state, authorization, transaction va MySQL.

### 41. Mot host co duoc mua hang khong?

Co, host chi bi chan bid trong auction do chinh minh chu tri. O auction cua nguoi khac, tai khoan do la bidder binh thuong.

### 42. Tai sao phong dong khong bien mat ngay?

He thong giu `ENDED`/`CANCELLED` trong 120 giay de nguoi choi xem winner, gia cuoi va trang thai. Sau do `AuctionTimerService` archive, `AuctionManager` an khoi list/dashboard, `RoomManager` don subscriber va server gui `AUCTION_ARCHIVED` cho client.

### 43. Archive co xoa lich su dau gia khong?

Khong. Archive chi an phong khoi san dang hoat dong. Du lieu trong MySQL van con de xem `auctions`, `bids` va `auction_results`.

## Cau hoi nang cap

### 44. TCP khac WebSocket the nao?

TCP la byte stream nen can framing. WebSocket co message boundary, handshake va text/binary frame; project dung TEXT JSON.

### 45. Tai sao `ws://` khong phai web UI?

Do la endpoint de JavaFX trao doi message, khong phai trang HTML cho nguoi dung.

### 46. Tai sao can `Platform.runLater`?

Network callback khong chay tren JavaFX Application Thread; update Node tu thread khac la khong an toan.

### 47. Tai sao khong nhat Base64 anh vao list?

List realtime se phinh payload. Anh duoc tai rieng va cache theo version.

### 48. Private room reconnect tai sao khong hoi lai password?

Quyen da xac minh thuoc session token, con trong resume window va bi xoa khi logout/expired/kick.
