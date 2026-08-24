# 00 - BAT DAU TAI DAY

## Muc tieu cua bo source

Bo source nay khong chi de copy. Moi thanh vien phai hoc duoc mot luong day du:

```text
Client UI
-> Network request
-> TCP server
-> Business logic
-> Shared state / database
-> Network response / event
-> Client update
```

## Viec can lam ngay

1. Cai JDK 17.
2. Chay `scripts\run-self-tests-jdk-only.cmd`.
3. Chay `scripts\run-local-demo-memory.cmd`.
4. Dang nhap ba client bang demo, alice, bob.
5. Cho ca ba vao cung mot auction.
6. Dat gia tu hai client.
7. Quan sat broadcast, outbid va countdown.
8. Tat mot client, mo lai va quan sat reconnect/resync.

## 12 bat bien kien truc can thuoc

1. TCP khong co ranh gioi message.
2. `[length][payload]` tao ranh gioi message.
3. Moi connection co mot read loop.
4. Moi socket chi duoc ghi tuan tu qua output lock.
5. Client tao requestId.
6. Server tao serverSequence.
7. Server la nguon su that.
8. Room chi broadcast cho subscriber cua auction do.
9. Moi auction co lock rieng.
10. Khong gui mang trong luc giu auction lock.
11. Timer chinh thuc nam o server.
12. Reconnect phai lay snapshot moi nhat.

## Cach doc code

Khong doc theo thu tu alphabet. Doc theo duong di cua message:

```text
MessageType
-> WireMessage
-> LengthPrefixedMessageCodec
-> NetworkClient
-> TcpServer
-> ClientConnection
-> MessageRouter
-> Controller
-> Service
-> Repository / Manager
-> Event quay lai client
```

## Che do chay

### Memory mode

- Khong can Laragon.
- Du lieu reset moi lan restart server.
- Phu hop hoc network, thread va concurrency.

### JDBC mode

- Dung Laragon MySQL.
- Luu user, bid, result.
- Phu hop demo cuoi va bao ve transaction.

## Quy tac lam viec nhom

- Khong ai tao TCP server rieng.
- Khong ai tu dat protocol field ma khong thong nhat.
- Khong ai cho View goi SQL.
- Khong ai tin `userId` do client gui.
- Moi module phai co request, response va event ro rang.
- Merge theo thu tu trong `02_THU_TU_XAY_DUNG_VA_PHU_THUOC.md`.
