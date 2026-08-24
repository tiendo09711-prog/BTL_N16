# 19 - MULTI MACHINE VÀ PACKAGING

## Development commands

```bash
npm run dev
npm run dev:server
npm run client
npm run client -- --url=ws://192.168.1.10:8890/ws
npm run client -- --transport=tcp --host=192.168.1.10 --port=8888
npm run dist:client
```

`npm run dev` build, migrate database, start TCP + WebSocket, chờ ready, in IPv4 LAN và mở JavaFX client local. `dev:server` không mở client.

## App-image

`npm run dist:client` dùng `jpackage --type app-image` và tạo:

```text
dist/BTL16-Auction-Client/
```

Thư mục chứa application, dependency và Java runtime. Máy client không cần source, IDE, Maven, Node hay JDK riêng.

## LAN

Server bind:

```text
TCP       0.0.0.0:8888
WebSocket 0.0.0.0:8890/ws
```

Windows Firewall inbound:

```text
TCP 8888 - legacy/test
TCP 8890 - JavaFX WebSocket
```

Không mở MySQL 3306 cho máy client. Client chỉ kết nối Java server.

Máy client nhập:

```text
ws://IP_MAY_SERVER:8890/ws
```

Đây là WebSocket endpoint, không phải website.

## Demo ba máy

Máy A:

```bash
npm run dev:server
```

Máy B:

```text
Mở packaged JavaFX client
Tạo product có ảnh và private room
```

Máy C:

```text
Search room
Thử thiếu/sai/đúng password
Bid và nhận realtime event
Ngắt mạng rồi reconnect/resume/resync
```

Có thể mở một TCP legacy client trên A/B để demo cross-transport.

## Internet

`192.168.x.x` và `10.x.x.x` là private LAN. Remote Internet cần port forwarding/public IP, VPN mesh, tunnel hoặc reverse proxy/VPS. Khi triển khai TLS/reverse proxy có thể dùng `wss://`.

Tunnel không phải dependency bắt buộc. LAN phải chạy độc lập.

## Giới hạn packaging

App-image đã self-contained nhưng không phải Windows MSI/EXE installer. Tạo installer có thể cần WiX tùy phiên bản JDK. App-image là mức bàn giao không yêu cầu tool ngoài trên máy client.
