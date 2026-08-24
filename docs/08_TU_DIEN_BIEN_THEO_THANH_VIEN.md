# 08 - TU DIEN BIEN THEO THANH VIEN

## 1. Do Tien - Network core, account, session

| Bien | File | Y nghia |
|---|---|---|
| `bindAddress` | ServerConfig | Dia chi server bind; 0.0.0.0 nhan LAN |
| `port` | ServerConfig | TCP app port, mac dinh 8888 |
| `workerThreads` | ServerConfig | So worker doc client dong thoi |
| `maxFrameBytes` | ServerConfig | Chan frame qua lon |
| `connectionId` | ClientConnection | ID cua mot socket hien tai |
| `socket` | ClientConnection/NetworkClient | Kenh TCP |
| `input`, `output` | ClientConnection/NetworkClient | Data stream doc/ghi |
| `outputLock` | ClientConnection/NetworkClient | Khong cho byte cua hai frame xen nhau |
| `closed` | ClientConnection | Bao dam close mot lan |
| `requestId` | WireMessage | Noi request voi response |
| `serverSequence` | WireMessage | Thu tu event/response server |
| `pendingRequests` | NetworkClient | requestId -> CompletableFuture |
| `routes` | MessageRouter | MessageType -> handler |
| `sessionToken` | UserSession | Token resume session |
| `byToken` | SessionManager | token -> session record |
| `tokenByConnection` | SessionManager | connection -> token |
| `tokenByUser` | SessionManager | user -> token, chan login trung |
| `resumeDeadline` | UserSession | Han phuc hoi sau disconnect |
| `mutationLock` | SessionManager | Cap nhat ba map session nguyen tu |
| `passwordHash` | UserAccount | Hash, khong phai password ro |
| `passwordSalt` | UserAccount | Salt rieng cua mat khau |

## 2. Vu Tri Thuan - Realtime, reconnect, test, integration

| Bien | File | Y nghia |
|---|---|---|
| `eventListeners` | NetworkClient | Cac noi nhan event server push |
| `stateListeners` | NetworkClient | Cac noi nhan state connection |
| `state` | NetworkClient | DISCONNECTED/CONNECTING/CONNECTED/CLOSED |
| `readerThread` | NetworkClient | Doc response va event lien tuc |
| `heartbeatIntervalMillis` | ClientConfig | Chu ky PING |
| `consecutiveFailures` | HeartbeatService | So heartbeat loi lien tiep |
| `reconnectInitialDelayMillis` | ClientConfig | Delay reconnect dau |
| `reconnectMaxDelayMillis` | ClientConfig | Tran backoff |
| `reconnectMaxAttempts` | ClientConfig | So lan thu toi da |
| `running` | ReconnectCoordinator | Dang co chuoi reconnect hay khong |
| `autoReconnectEnabled` | ClientController | Tat khi logout chu dong |
| `serverClockOffsetMillis` | ClientAppModel | Lech gio server-client |
| `joinedAuctionId` | ClientAppModel | Room can resync sau reconnect |
| `bidUpdateEvents` | FullNetworkAuctionSelfTest | Dem event realtime |
| `endedEvent` | FullNetworkAuctionSelfTest | Cho AUCTION_ENDED |

## 3. Pham Anh Dung - Bid va concurrency

| Bien | File | Y nghia |
|---|---|---|
| `rawAmount`, `amount` | BidService | Gia client gui va gia da normalize |
| `MAX_BID` | BidService | Gioi han gia |
| `runtime` | BidService | State cua auction can cap nhat |
| `lock` | AuctionRuntime | ReentrantLock rieng cua auction |
| `expectedPrice` | BidService/BidCommit | Gia ma thread da kiem tra |
| `previousWinnerId` | BidService | Leader truoc bid |
| `oldEndTime` | BidService | End time truoc anti-sniping |
| `remainingMillis` | BidService | Thoi gian con lai khi bid |
| `extended` | BidService/BidOutcome | Co gia han hay khong |
| `bidSequence` | BidService | Thu tu chinh thuc cua bid |
| `newEndTime` | BidCommit | End time sau khi xu ly bid |
| `bidIds` | InMemoryAuctionRepository | Sinh bid ID memory |
| `server_sequence` | bids table | Thu tu bid luu DB |
| `DATABASE_CONFLICT` | ErrorCode | State DB da thay doi |

## 4. Mai Trung Duc - Timer, extension, ending

| Bien | File | Y nghia |
|---|---|---|
| `checkMillis` | AuctionTimerService | Chu ky kiem tra het han |
| `tickMillis` | AuctionTimerService | Chu ky broadcast countdown |
| `scheduler` | AuctionTimerService | Scheduled executor server |
| `lastTickAt` | AuctionTimerService | Tranh broadcast tick qua day |
| `antiSnipingWindowSeconds` | ServerConfig/BidService | Cua so sat gio |
| `extensionSeconds` | ServerConfig/BidService | So giay gia han |
| `endTime` | AuctionRuntime | Thoi diem ket thuc chinh thuc |
| `status` | AuctionRuntime | OPEN/ENDED |
| `endedAt` | AuctionRuntime/AuctionResult | Thoi diem dong thuc te |
| `winnerId` | AuctionResult | Nguoi thang co the null |
| `finalPrice` | AuctionResult | Gia cuoi |
| `version` | AuctionRuntime | Tang khi state thay doi |
| `lastTickAt` | AuctionTimerService | Moc phat tick gan nhat |

## 5. Tran Van Phuoc - Product, list, detail, auction state

| Bien | File | Y nghia |
|---|---|---|
| `productId` | Product | Khoa san pham |
| `code` | Product | Ma ngan unique |
| `name` | Product | Ten san pham |
| `description` | Product | Mo ta |
| `auctionId` | AuctionSnapshot | Khoa phien |
| `startPrice` | AuctionSnapshot | Gia khoi diem |
| `currentPrice` | AuctionSnapshot | Gia server hien tai |
| `currentWinnerId` | AuctionSnapshot | ID leader |
| `currentWinnerUsername` | AuctionSnapshot | Ten leader hien thi |
| `startTime` | AuctionSnapshot | Moc mo |
| `endTime` | AuctionSnapshot | Moc dong |
| `status` | AuctionSnapshot | OPEN/ENDED |
| `watcherCount` | ClientAuction | So subscriber room |
| `runtimes` | AuctionManager | auctionId -> AuctionRuntime |
| `connectionsByAuction` | RoomManager | auction -> connection set |
| `auctionsByConnection` | RoomManager | connection -> auction set |
