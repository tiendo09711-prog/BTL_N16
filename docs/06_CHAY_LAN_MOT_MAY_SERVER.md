# 06 - CHAY LAN: MOT MAY SERVER, CAC MAY CON LAI LA CLIENT

## Kien truc

```text
May Do Tien
  Java Server + Laragon MySQL
          ^
          | TCP 8888
          |
May Thuan / Dung / Duc / Phuoc
  Java Client
```

Client khong can Laragon neu chi chay client.

## Tren may server

1. Mo terminal tai thu muc goc.
2. Chay `npm run dev:server`, hoac `npm run dev` neu may server cung can mot client local.
3. Giu:

```properties
server.bindAddress=0.0.0.0
server.port=8888
```

4. Runner se in IPv4; co the kiem tra lai bang:

```bat
scripts\show-server-ip.cmd
```

Hoac:

```bat
ipconfig
```

5. Cho phep inbound TCP 8888 trong Windows Defender Firewall.
6. Giu terminal npm dang chay; `Ctrl+C` se dung Java server/client.

## Tren moi may client

Neu may client co bo source, chay:

```bash
npm run client -- --host=192.168.x.x
```

`192.168.x.x` la IPv4 cua may server. Client cung co the sua `config/client.properties` va chay `scripts\run-client.cmd`.

## Gioi han cua link chia se

- Server hien tai dung raw TCP, khong phai HTTP/WebSocket.
- Dia chi `192.168.x.x:8888` la dia chi ket noi, khong phai URL cho trinh duyet.
- Nguoi choi can Java Swing client va JDK phu hop.
- Muon bam link va choi ngay tren web can bo sung web frontend va WebSocket/HTTP gateway.
- TCP hien tai khong co TLS; chi nen demo trong LAN/VPN tin cay, khong public truc tiep ra Internet.

## Kiem tra loi

| Hien tuong | Nguyen nhan thuong gap |
|---|---|
| Connection refused | Server chua chay hoac sai port |
| Timeout | Firewall, khac LAN, sai IP |
| Ping duoc nhung client khong vao | TCP 8888 chua mo |
| JDBC error tren server | MySQL Laragon chua bat/sai password |
| Client bi AUTH_REQUIRED | Session chua login/resume |
| Resume that bai | Qua 120 giay hoac server restart |

## Bao mat va thiet ke

- Khong mo port 3306 cho client.
- Khong gui password DB sang client.
- Server xu ly moi SQL.
- TCP demo chua dung TLS, chi nen dung tai khoan demo trong LAN lab.

## Demo LAN goi y

1. Tat ca 5 may login.
2. Tat ca JOIN cung auction.
3. Hai may bid gan dong thoi.
4. Quan sat dashboard server va event tat ca client.
5. Mot may tat Wi-Fi, bat lai.
6. Client reconnect, resume va resync.
7. Bid trong 10 giay cuoi de demo extension.
8. Cho phong dong, quan sat ket qua con hien 120 giay.
9. Het retention, tat ca client nhan `AUCTION_ARCHIVED` va dashboard tu an phong.

Du lieu lich su van nam tren MySQL cua may server; cac may client khong duoc truy cap port 3306.
