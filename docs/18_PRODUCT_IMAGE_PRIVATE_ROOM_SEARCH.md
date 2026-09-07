# 18 - PRODUCT IMAGE, PRIVATE ROOM VÀ SEARCH

## Product image end-to-end

```text
JavaFX FileChooser/ImageView
-> AuctionApi imageBase64 request
-> AuctionController
-> AuctionManagementService
-> ProductImageValidator
-> AuctionRepository/JDBC
-> MySQL MEDIUMBLOB
```

Server giới hạn 700 * 1024 byte (700 KiB), chỉ nhận PNG/JPEG, kiểm tra MIME và magic bytes. Client validation chỉ phục vụ UX. Validator kiểm tra chữ ký/MIME, không giải mã đầy đủ nội dung mọi ảnh.

Schema:

```text
products.image_data MEDIUMBLOB
products.image_mime VARCHAR(50)
products.image_name VARCHAR(255)
products.image_size INT
products.image_version BIGINT
```

Auction list/snapshot chỉ mang metadata:

```text
hasImage
imageMime
imageVersion
```

Ảnh đầy đủ chỉ trả qua `GET_PRODUCT_IMAGE_RESULT`. JavaFX cache theo `productId:imageVersion`. Product legacy không ảnh không làm client crash.

## Public/private room

Schema auction:

```text
visibility VARCHAR(16) DEFAULT 'PUBLIC'
room_password_hash VARCHAR(255)
room_password_salt VARCHAR(255)
room_password_iterations INT
```

`RoomVisibility` có `PUBLIC` và `PRIVATE`. Password phòng dùng lại PBKDF2-HMAC-SHA256 của account password. Plaintext password không được lưu DB, log hoặc gửi xuống client.

## Join private room

```text
authenticated
-> auction tồn tại
-> user không bị block
-> auction còn OPEN theo thời gian server
-> host: cho vào
-> PUBLIC: cho vào
-> PRIVATE + session grant: cho vào
-> thiếu password: ROOM_PASSWORD_REQUIRED
-> sai password: ROOM_PASSWORD_INVALID
-> đúng password: tạo session-scoped grant rồi RoomManager.join
```

Grant được lưu theo session token trong `SessionManager`:

- Disconnect: grant còn trong resume window.
- Resume cùng token: grant còn.
- Logout/session expired: grant bị xóa.
- Kick/block: grant của user cho auction bị revoke.
- Grant không chứa plaintext password.

## Reconnect private room

Sau khi password đúng:

```text
disconnect -> resume token -> RESYNC auctionId -> join bằng session grant
```

User không phải nhập lại password trong cùng session. Session mới phải nhập lại. Block luôn được kiểm tra trước grant nên không thể bypass kick.

## Search qua server

Protocol:

```text
SEARCH_AUCTIONS
SEARCH_AUCTIONS_RESULT
```

Mode:

- `PRODUCT_NAME`: trim, case-insensitive, contains.
- `ROOM_ID`: numeric, exact `auctionId`.
- `ALL`: kết hợp hai cách trên.

Query phải có 1-100 ký tự. Search chạy trên authoritative runtime snapshot, không phải JavaFX local filter. Private room vẫn xuất hiện với `visibility=PRIVATE` và `requiresPassword=true`, không có password/hash.

## Error code mới

```text
ROOM_PASSWORD_REQUIRED
ROOM_PASSWORD_INVALID
ROOM_ACCESS_DENIED
PRODUCT_IMAGE_TOO_LARGE
PRODUCT_IMAGE_UNSUPPORTED_TYPE
PRODUCT_IMAGE_INVALID
INVALID_SEARCH_QUERY
```

## Chuẩn bị dữ liệu trên XAMPP

Schema mới trống, không có ảnh/product/phòng/account mẫu. Tự đăng ký, thêm sản phẩm rồi tạo phòng để kiểm tra luồng trên; client không JDBC trực tiếp. DatabaseSchema và sql/00_schema.sql phải được cập nhật cùng repository khi đổi cột. Test ảnh/private/search dùng fixture trong WebSocketUpgradeSelfTest; kiểm tra GUI/DB thật cần diễn tập riêng.
