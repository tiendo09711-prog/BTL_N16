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

1. Cai Node.js, JDK 17, Maven va Laragon.
2. Kiem tra `config/server.properties`.
3. Tai thu muc goc chay `npm run dev` tren may server.
4. Lay dia chi LAN ma runner in ra.
5. May khac chay `npm run client -- --host=IP_MAY_SERVER` neu co bo source.
6. Dang nhap demo, alice, bob va cho vao cung mot auction.
7. Dat gia tu hai client, quan sat broadcast/outbid/countdown.
8. Nhan `Ctrl+C` tai may server de dung server/client local.

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

### MySQL/JDBC

- Dung Laragon MySQL.
- La repository runtime duy nhat cua server.
- Luu user, product, auction, bid, blocked user va result.
- Self-test dung repository test rieng trong `src/test`, khong tao them che do chay server.

## Quy tac lam viec nhom

- Khong ai tao TCP server rieng.
- Khong ai tu dat protocol field ma khong thong nhat.
- Khong ai cho View goi SQL.
- Khong ai tin `userId` do client gui.
- Moi module phai co request, response va event ro rang.
- Merge theo thu tu trong `02_THU_TU_XAY_DUNG_VA_PHU_THUOC.md`.
