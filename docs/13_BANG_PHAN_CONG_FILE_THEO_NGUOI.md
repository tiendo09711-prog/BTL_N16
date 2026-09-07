# 13 – Phân công học code, bảo trì và review

Đối chiếu source ngày 07/09/2026. Các chức năng đã có; bảng này quy định người phải hiểu và chịu trách nhiệm thay đổi, không khẳng định ai đã viết từng dòng. Danh sách **toàn bộ file Java** có người phụ trách ở [09](09_NHIEM_VU_TUNG_FILE.md).

| Thành viên | Phạm vi chính | Tài liệu cá nhân |
|---|---|---|
| Đỗ Tiến | common protocol/config, network server, account/security/session, module/router/composition, XAMPP/JDBC/schema, dashboard/LAN | [Tiến](members/01_DO_TIEN.md) |
| Vũ Trí Thuận | JavaFX/UI, client transport/API/model, realtime/reconnect, legacy UI, test runner/fixtures, dev/client runner và hai EXE | [Thuận](members/02_VU_TRI_THUAN.md) |
| Phạm Anh Dũng | BidService/BidCommit/BidOutcome/BidRecord, khóa/transaction, RoomManager/membership, private grant/block, kick race và load-test | [Dũng](members/03_PHAM_ANH_DUNG.md) |
| Mai Trung Đức | Timer/anti-sniping, close-once, extend/end/cancel, result/retention/archive và lifecycle event | [Đức](members/04_MAI_TRUNG_DUC.md) |
| Trần Văn Phước | Product/ảnh BLOB/validator, auction model/repository/runtime, CRUD/create/list/my/search, ownership và persistence block/archive | [Phước](members/05_TRAN_VAN_PHUOC.md) |

## File dùng chung: chia theo thao tác

| File/nhóm | Người chính theo phần | Reviewer |
|---|---|---|
| AuctionManagementService | Phước: product/image/create/my; Dũng: join/private/kick; Đức: extend/end/cancel | Tiến: auth; Thuận: API/UI |
| JdbcAuctionRepository | Phước: dữ liệu chung/product/ảnh; Dũng: commit bid/block; Đức: close/extend/cancel/result | Tiến: JDBC/schema |
| AuctionRuntime, AuctionManager | Phước: model/runtime/list; Dũng: khóa bid; Đức: trạng thái/archive | Cả nhóm |
| RoomManager, SessionManager | Dũng: membership/grant/block; Tiến: session lifetime; Phước: dữ liệu phòng | Thuận, Đức |
| AuctionController, AuctionModule, MessageType/ErrorCode | Tiến: contract/router; chủ nghiệp vụ: handler/field | Thuận: client parity |
| AuctionWireData, AuctionBroadcastService | Phước: snapshot/ảnh/list; Dũng: bid/outbid; Đức: lifecycle | Tiến, Thuận |
| FxClientController, AuctionApi, ClientWireParser, ClientAppModel | Thuận: UI/event/model; mỗi người review thao tác nghiệp vụ của mình | Cả nhóm |
| DatabaseSchema, sql/*, ServerConfig, JdbcConnectionFactory | Tiến: XAMPP/JDBC/port/setup/reset; Phước: bảng/cột/ảnh | Dũng, Đức: transaction |
| ServerDashboardMain/Frame, ServerAddresses | Tiến: startup/config/URL; Thuận: phân phối EXE | Đức: lifecycle/shutdown |
| tools/package-*.mjs, package.json, 06_TAO_HAI_UNG_DUNG_EXE.cmd | Thuận: hai app-image; Tiến: server config/JDBC | Cả nhóm thử bản phân phối |

## Không để lọt phần test/công cụ/docs

- Thuận chạy AllSelfTests; mỗi chủ module chịu assertion: Tiến protocol/account/config/LAN, Dũng bid/private/block, Đức timer/result/archive, Phước data/image/search.
- XamppDatabaseSelfTest: Tiến/Phước kiểm tra JDBC thật trong DB thử riêng; Thuận giữ regression. Fixture in-memory không được seed vào btl_16.
- Dũng vận hành ConcurrentBidLoadTestMain với DB thử đã có PUBLIC room; tool ghi dữ liệu thật.
- Tiến/Thuận phụ trách build/run scripts, .vscode, package.json, .github và file hướng dẫn gốc. target/dist/out là output, không phải source ownership.
- Tiến: docs/00–06, 09–10, 12–13, 15 và README/setup. Thuận: 07, 11, 16–17, 19 và VERIFICATION. Phước: 04, 18; Dũng/Đức review 03–04, 08, 10 theo rule. Cả nhóm: 08, 14 và docs/members của mình.

## Bàn giao tối thiểu từng người

1. Đọc được luồng UI → request → service → transaction → event và chỉ đúng file/method.
2. Tự chạy phần mình bằng account mới, không dựa dữ liệu mẫu/ID cố định.
3. Sửa rule/protocol/schema thì cập nhật test, SQL/Java schema và docs liên quan cùng lúc.
4. Setup VS Code + XAMPP; db.port theo máy, client chỉ dùng WS URL. Schema mới trống; reset chỉ khi chủ động xóa dữ liệu.
5. Ghi bằng chứng đã chạy, để LAN/GUI chưa thử ở trạng thái chưa xác minh.
