# 15 - QUY TAC GIT VA TICH HOP

## Branch

```text
main
feature/do-tien-network
feature/thuan-realtime
feature/dung-bid
feature/duc-timer
feature/phuoc-auction-data
```

## Truoc khi push

```bat
scripts\run-self-tests-jdk-only.cmd
```

Neu sua JDBC, chay them local Laragon demo.

## Khong commit

```text
target/
out/
.idea/
*.iml
config/server-local.properties
config/client-local.properties
```

## Quy tac merge

1. Rebase/pull main.
2. Chay self-test.
3. Reviewer doc file dung chung.
4. Neu sua protocol, cap nhat `03_PROTOCOL_VA_LUONG_MESSAGE.md`.
5. Neu sua schema, cap nhat SQL va repository.
6. Neu sua rule, them test.

## Conflict thuong gap

- `MessageType.java`: Do Tien merge.
- `ServerApplication.java`: Do Tien merge.
- `ClientController.java`: Thuan va Do Tien pair merge.
- `JdbcAuctionRepository.java`: Phuoc/Dung/Duc review chung.

## File hotspot sau nang cap

- `MessageType.java`, `JsonWireMessageCodec.java`, `ServerConnection.java`: Do Tien merge.
- `ClientTransport.java`, `WebSocketClientTransport.java`, `FxClientController.java`: Thuan merge, Do Tien review.
- `AuctionManagementService.java`, `SessionManager.java`: Dung review private grant/block.
- `AuctionWireData.java`, timer/lifecycle event: Duc review.
- Product/image/search/repository/schema: Phuoc implementation, Do Tien review migration.
- Moi merge transport phai chay ca TCP self-test va `WebSocketUpgradeSelfTest`.
