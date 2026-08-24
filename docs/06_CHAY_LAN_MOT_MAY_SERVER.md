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

1. Bat Laragon MySQL.
2. Chay setup DB.
3. Giu:

```properties
server.bindAddress=0.0.0.0
server.port=8888
```

4. Tim IPv4:

```bat
scripts\show-server-ip.cmd
```

Hoac:

```bat
ipconfig
```

5. Cho phep inbound TCP 8888 trong Windows Defender Firewall.
6. Chay:

```bat
scripts\run-server-dashboard.cmd
```

## Tren moi may client

Sua `config/client.properties`:

```properties
client.serverHost=192.168.x.x
client.serverPort=8888
```

`192.168.x.x` la IPv4 cua may server.

Chay:

```bat
scripts\run-client.cmd
```

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
