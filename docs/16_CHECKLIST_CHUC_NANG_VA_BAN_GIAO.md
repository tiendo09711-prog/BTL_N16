# 16 - CHECKLIST CHUC NANG VA BAN GIAO

Tai lieu nay cho biet tinh nang da nam o dau, ai hoc chinh va cach kiem tra.

## 1. Network core va account - Do Tien

| Tinh nang | Request/Event | Server code chinh | Client/UI | Kiem tra |
|---|---|---|---|---|
| TCP connect | `CONNECTION_WELCOME` | `TcpServer`, `ClientConnection` | `NetworkClient`, status bar | mo nhieu client |
| Message framing | tat ca message | `LengthPrefixedMessageCodec` | cung codec | `ProtocolCodecSelfTest` |
| Register | `REGISTER` | `AccountController`, `AccountService` | dialog dang ky | dang ky user moi |
| Login | `LOGIN` | account service + repository | `LoginPanel` | dung/sai password |
| Session | `LOGIN_RESULT` | `SessionManager` | `ClientAppModel` | login trung account |
| Resume | `RESUME_SESSION` | `SessionManager.resumeSession` | reconnect coordinator | tat/bat server hoac mat socket |
| Profile/password | `GET_PROFILE`, `UPDATE_PROFILE`, `CHANGE_PASSWORD` | account module | dialog profile | sua va login lai |

## 2. Auction data va room - Tran Van Phuoc

| Tinh nang | Request/Event | Server code chinh | Client/UI | Kiem tra |
|---|---|---|---|---|
| Danh sach phien | `AUCTION_LIST` | `AuctionQueryService` | bang auction | refresh list |
| Chi tiet/snapshot | `JOIN_AUCTION`, `AUCTION_SNAPSHOT` | `AuctionController`, `AuctionWireData` | panel chi tiet | chon va join |
| Room subscriber | `JOIN_AUCTION`, `LEAVE_AUCTION` | `RoomManager` | Join/Leave | watcherCount thay doi |
| Bid history | `GET_BID_HISTORY` | repository/query service | bang lich su | dat gia roi tai history |
| Du lieu DB | products/auctions | `JdbcAuctionRepository` | khong truy cap DB truc tiep | xem Laragon |
| Product CRUD | `CREATE_PRODUCT`, `UPDATE_PRODUCT`, `DEACTIVATE_PRODUCT` | `AuctionManagementService` + repository | dialog product | owner moi sua/an duoc |
| Tao phong | `CREATE_AUCTION`, `AUCTION_CREATED` | management + `AuctionManager.addRuntime` | nut Tao phong | phong xuat hien realtime |
| Phong cua toi | `MY_AUCTIONS` | manager filter host | nut Phong cua toi | chi hien phong host |
| An phong dong | `AUCTION_ARCHIVED` | `AuctionManager`, `RoomManager` | xoa khoi bang | sau 120 giay khong con list/dashboard |

## 3. Bid va concurrency - Pham Anh Dung

| Tinh nang | Request/Event | Server code chinh | Client/UI | Kiem tra |
|---|---|---|---|---|
| Place bid | `PLACE_BID` | `BidService` | o gia + nut Dat gia | bid hop le |
| Tu choi | `BID_REJECTED` | `AuctionException`/controller | thong bao ly do | gia bang/thap hon |
| Accepted | `BID_ACCEPTED` | controller | thong bao response | gia cao hon |
| Race condition | nhieu `PLACE_BID` | `AuctionRuntime` lock | nhieu client | `05_TEST_BID_DONG_THOI.cmd` |
| Transaction | insert bid + update auction | `JdbcAuctionRepository.commitAcceptedBid` | khong co SQL client | xem bids/auctions |
| Outbid | `OUTBID_NOTIFICATION` | `AuctionBroadcastService` | notification panel | alice bid, bob vuot |
| Buoc gia | `minBidIncrement` | `BidService` | detail/bid error | bid thap hon minimum |
| Chan self-bid | `AUCTION_FORBIDDEN` | `BidService` | nut bid host bi khoa | host goi API truc tiep |
| Kick safety | `KICK_AUCTION_USER` | runtime lock + `RoomManager` | nut Moi user | kick cung luc bid |

## 4. Timer va ending - Mai Trung Duc

| Tinh nang | Request/Event | Server code chinh | Client/UI | Kiem tra |
|---|---|---|---|---|
| Countdown | `AUCTION_TICK` | `AuctionTimerService` | countdown label | quan sat moi giay |
| Anti-sniping | `AUCTION_EXTENDED` | `BidService` | end time/countdown | bid trong 10 giay cuoi |
| Close once | `AUCTION_ENDED` | timer + per-auction lock | winner/status | cho phien het gio |
| Late bid | `BID_AFTER_END` | `BidService` | thong bao tu choi | bid sau END |
| Luu ket qua | auction_results | `closeAuction` transaction | ket qua hien thi | xem MySQL |
| Host extend | `EXTEND_AUCTION` | `AuctionManagementService` | nut Gia han | source HOST |
| Manual end | `END_AUCTION` | close transaction | nut Ket thuc | chot winner som |
| Cancel no-bid | `CANCEL_AUCTION` | repository cancel | nut Huy phong | status CANCELLED |
| Retention 2 phut | `AUCTION_ARCHIVED` | `AuctionTimerService` | notification | ENDED/CANCELLED con hien 120 giay |

## 5. Realtime, reconnect va integration - Vu Tri Thuan

| Tinh nang | Request/Event | Server code chinh | Client/UI | Kiem tra |
|---|---|---|---|---|
| Server push | `BID_UPDATE`, tick, ended | messaging/broadcast | event listeners | 3 client cung room |
| Heartbeat | `PING/PONG` | core module | `HeartbeatService` | xem latency |
| Disconnect cleanup | socket close | lifecycle listener + room/session | connection status | tat client dot ngot |
| Auto reconnect | transport connect | session core | `ReconnectCoordinator` | dung/chay lai server |
| Resync | `RESYNC` | auction controller | snapshot moi | reconnect khi auction da doi |
| End-to-end test | nhieu message | toan he thong | test clients | `FullNetworkAuctionSelfTest` |
| Management test | product/host/kick | toan he thong | API clients | `AuctionManagementSelfTest` |
| Kick event | `AUCTION_KICKED` | broadcast to removed connection | client roi room | join lai bi chan |
| Archive event | `AUCTION_ARCHIVED` | broadcast toan bo connection | remove + tombstone model | client tu xoa, response cu khong them lai |

## 6. Dieu kien coi la san sang demo

- [x] Bien dich Java 17.
- [x] Runtime server chi khoi tao MySQL/JDBC.
- [x] Hai client TCP thuc dat gia va nhan event.
- [x] Test race condition va final state authoritative.
- [x] Test disconnect, resume va RESYNC.
- [x] Test timer dong auction va broadcast winner.
- [x] Test archive sau visibility window, an client/dashboard va don room.
- [x] Co client Swing day du cho luong demo.
- [x] Co server dashboard.
- [x] Co schema MySQL `btl_16` va JDBC repository.
- [x] Co product ownership, update va soft delete.
- [x] Co host create/list/extend/end/cancel room.
- [x] Co minimum bid increment va chan host tu bid.
- [x] Moi tai khoan co the vua ban o phong minh, vua mua o phong nguoi khac.
- [x] Khong can role admin/seller/buyer toan cuc; quyen theo ownership/host tung resource.
- [x] Co kick user va block rejoin.
- [x] Client Swing da dong bo cac thao tac quan tri.
- [x] `AuctionManagementSelfTest` pass voi repository test tach biet.
- [x] `DatabaseSetupMain` da chay tren Laragon/MySQL va schema JDBC da duoc xac nhan.
- [x] `npm run dev` chay MySQL, server va mot client local; `Ctrl+C` don process.
- [x] Lich su auction/bid/result van luu MySQL sau khi phong bi an.
- [ ] Nhom thu LAN voi it nhat hai may vat ly va mo firewall TCP 8888.

Muc LAN cuoi cung can hai may vat ly va cau hinh Windows Firewall cua nhom.

## Checklist nang cap JavaFX + WebSocket

- [x] JavaFX la client entrypoint mac dinh; Swing duoc giu legacy.
- [x] TCP va WebSocket cung router/business core.
- [x] JSON codec, request correlation, timeout va realtime event.
- [x] Product image BLOB + message tai rieng + cache version.
- [x] Public/private room + PBKDF2 + session grant reconnect.
- [x] Search product name va Auction ID qua server.
- [x] Self-test WebSocket basic/cross transport/private/image/search.
- [x] `npm run build`, `npm run client`, `npm run dev:server`, `npm run dist:client` da co script.
- [ ] Nhom runtime demo JavaFX tren ba may vat ly va mo firewall 8890.
