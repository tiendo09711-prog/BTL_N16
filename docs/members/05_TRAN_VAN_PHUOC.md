# TRẦN VĂN PHƯỚC – Product, ảnh, dữ liệu phòng và repository

Đối chiếu code ngày 07/09/2026. Đây là nhiệm vụ học, giải thích, kiểm thử và bảo trì phần đã triển khai.

## Phạm vi

Chịu trách nhiệm mô hình sản phẩm/phòng, ownership, ảnh BLOB, create/list/my/search, runtime/repository và dữ liệu archive. Chia sẻ AuctionManagementService với Dũng/Đức, không tự nhận toàn bộ logic của file này.

## Thứ tự đọc file

Đường dẫn Java tương đối với src/main/java/vn/ptit/btl16/, trừ src/test và công cụ gốc. Tra toàn bộ file/ownership ở [09](../09_NHIEM_VU_TUNG_FILE.md) và [13](../13_BANG_PHAN_CONG_FILE_THEO_NGUOI.md).

1. server/auction/model/Product, ProductImage, RoomVisibility, AuctionSnapshot, AuctionRuntime, AuctionStatus.
2. repository/AuctionRepository, JdbcAuctionRepository; CreateProductCommit, UpdateProductCommit, CreateAuctionCommit, BlockAuctionUserCommit.
3. service/AuctionManagementService: product CRUD, image, create/join/kick data; ProductImageValidator.
4. service/AuctionQueryService, AuctionManager, AuctionWireData; controller/AuctionController và module/AuctionModule.
5. server/db/DatabaseSchema, sql/00_schema.sql; phối hợp Tiến về JDBC XAMPP/port và không seed.
6. client/model/ClientProduct, ClientAuction, ClientWireParser; client/service/AuctionApi.
7. client/fx/FxClientController.showProducts/showProductForm/showCreateAuction/search/loadProductImage.
8. src/test/java/.../TestAuctionRepository, AuctionManagementSelfTest, WebSocketUpgradeSelfTest, XamppDatabaseSelfTest.

## Luồng phải tự giải thích

**Product:** session → validate owner/input/image → repository INSERT/UPDATE → trả metadata. Deactivate là soft delete; product đang có auction OPEN không được sửa/ngừng sử dụng theo rule.

**Ảnh:** client chọn PNG/JPEG và Base64 → ProductImageValidator giới hạn 700 * 1024 byte, kiểm tra signature/MIME → BLOB + metadata/version. GET_PRODUCT_IMAGE riêng; list/search không chứa imageBase64. Validator kiểm tra chữ ký, không khẳng định giải mã đầy đủ mọi ảnh.

**Tạo phòng:** chủ sản phẩm → giá/bước giá/thời gian → PUBLIC hoặc PRIVATE + PBKDF2 password → repository create → addRuntime → AUCTION_CREATED. Không lưu password plaintext.

**List/my/search:** AuctionQueryService lấy snapshot/runtime; my lọc host, search tên sản phẩm không phân biệt hoa thường hoặc ID chính xác. Phòng archive không quay lại list; kiểm tra tombstone khi response cũ về client.

**JDBC/runtime:** dữ liệu commit thành công trước khi cập nhật RAM; record/commit object phân tách input transaction khỏi mutable runtime. Archive không DELETE lịch sử, block user vẫn lưu DB sau restart.

## Biến và bất biến cần nhớ

productId/code/createdBy, active, imageData/imageMime/imageVersion, hostUserId, visibility, roomPasswordHash/salt/iterations, startPrice/minBidIncrement, version, runtimes/archivedAuctionIds. Public/private là quyền join, không phải ẩn hoàn toàn metadata phòng khỏi list/search.

## Kiểm thử và bài thực hành

- AuctionManagementSelfTest: ownership, CRUD/deactivate, create/my rooms, host controls.
- WebSocketUpgradeSelfTest: BLOB protocol roundtrip, list không có Base64, private join/search, kick/grant.
- XamppDatabaseSelfTest: schema mới trống/giữ dữ liệu khi setup/reset không seed, chạy DB thử riêng.
- Diễn tập hai account tự đăng ký, ảnh PNG/JPEG, ảnh sai/qua giới hạn, không sửa sản phẩm người khác.
- Query products/auctions/bids/results/block trên XAMPP sau thao tác; không coi fixture in-memory là bằng chứng JDBC transaction.

## Câu hỏi bảo vệ

Tại sao BLOB thay đường dẫn file? Các máy client không chung filesystem. imageVersion để làm gì? Khóa cache thay khi ảnh đổi. Soft delete khác drop DB? Soft delete giữ lịch sử resource; reset xóa toàn database được chọn. Tại sao schema Java và SQL đều phải cập nhật? Người dùng có thể setup từ Java hoặc import SQL.

## Phối hợp và tiêu chí bàn giao

Tiến review schema/JDBC/protocol, Dũng review bid/private/block, Đức review lifecycle/archive, Thuận review model/UI/ảnh/search.

- Tự chỉ được entry point, request, service/repository và event tương ứng, không chỉ nhớ tên lớp.
- Chạy lại test liên quan, ghi đúng kết quả/chỗ chưa thử; sửa protocol/schema thì cập nhật docs chung.
- Môi trường VS Code + XAMPP; xem [05](../05_CACH_CHAY_VSCODE_XAMPP.md). db.port theo mỗi máy; client chỉ cần URL WS.
- DB thật không có seed/account mẫu. Fixture test giữ riêng trong src/test; diễn tập tự đăng ký và tạo dữ liệu.
