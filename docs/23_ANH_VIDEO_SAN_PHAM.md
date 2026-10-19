# Ảnh và video sản phẩm

## Sử dụng JavaFX

- Nếu nút **Lưu sản phẩm** bị mờ, đọc thông báo ngay cạnh nút: ví dụ mô tả `Oke nhé` mới có **7/50 ký tự**, còn thiếu 43 ký tự. Đây là điều kiện dữ liệu, không phải lỗi XAMPP. Bộ đếm bỏ khoảng trắng đầu/cuối và đếm ký tự Unicode; mã, tên, số lượng ảnh và trạng thái tải cũng phải hợp lệ.
- **Sẵn sàng** ở từng ảnh chỉ có nghĩa ảnh đã tải lên **bản nháp trên server**, chưa có nghĩa sản phẩm đã lưu. Phải bấm **Lưu sản phẩm** để ghi metadata vào DB và giữ tệp chính thức. Hủy/đóng cửa sổ trước khi lưu sẽ hủy bản nháp. Thanh thông báo và nút lưu luôn nằm ngoài vùng cuộn; cửa sổ tự giới hạn theo vùng màn hình khả dụng.
- Khi **Vào phòng**, giao diện chuyển sang không gian riêng: ảnh lớn và mô tả bên trái; giá hiện tại, thời gian, ô đặt giá, bước giá gợi ý và lịch sử mới nhất bên phải. Các nút gợi ý chỉ điền giá, không tự gửi lệnh. **Rời** đưa bạn về danh sách phòng; quyền đặt giá/quản lý vẫn do server kiểm tra.

## Server, database và XAMPP

- Máy chạy **Server EXE** là nơi lưu dữ liệu chung, duy nhất trong **MySQL của XAMPP**, mặc định `127.0.0.1:3306/btl_16`. Phải bật MySQL trước khi chạy Server; Apache không bắt buộc. Client không cần cài hoặc kết nối DB. Không còn H2 hay kho ảnh/video lâu dài trong AppData.
- Ảnh/video được lưu thành tệp trên server; DB lưu thông tin sản phẩm và tham chiếu đến tệp, không nhét toàn bộ video vào DB. Muốn sao lưu/chuyển máy, dừng server rồi sao lưu **cả database và media**. Không tự xóa DB hay đổi sang MySQL để chữa nút lưu bị mờ: hai backend là hai kho dữ liệu riêng, không tự đồng bộ.
- Chế độ chạy bằng `03_CHAY_SERVER_MYSQL.cmd` dùng `config/server.properties` và cần MySQL hoạt động. Nếu XAMPP báo `Bind on TCP/IP port`, `10048` hoặc `another mysqld server running on port: 3306`, kiểm tra Laragon/MySQL khác đang chiếm cổng; chỉ dùng một dịch vụ trên cổng đó, hoặc cấu hình cổng riêng rồi sửa `db.port` tương ứng. Không xóa thư mục dữ liệu MySQL để xử lý lỗi trùng cổng.

## Thao tác media

- Trong **Sản phẩm của tôi**, chọn **Thêm sản phẩm** hoặc **Sửa**. Mô tả bắt buộc 50-2000 ký tự sau khi bỏ khoảng trắng ở đầu/cuối; tự ghi đặc điểm, tình trạng và khiếm khuyết nếu có.
- **Chọn nhiều ảnh** nhận 1-5 ảnh; **Video** nhận tối đa một video. Video không thay thế ảnh. Tệp được tải và chuẩn hóa từng tệp, không đọc toàn bộ danh sách vào RAM. Thanh tiến độ chuyển sang trạng thái xử lý khi tải xong; lỗi nằm ngay ở tệp tương ứng. Có thể xóa tệp lỗi hoặc dùng **Thay tệp** để thử lại.
- Chọn ảnh trong danh sách, dùng **Làm đại diện**, các nút lên/xuống, **Thay tệp**, **Xóa**. Ảnh đầu tiên là đại diện. Xem trước ảnh/video trước khi lưu; chỉ lưu khi toàn bộ tệp đã sẵn sàng và đủ thông tin.
- Cuộn chuột hoặc dùng +/- để zoom, kéo để xem chi tiết, **Vừa khung** để đưa ảnh về ban đầu. **Xoay 90°**, **Chọn vùng cắt**, kéo vùng và **Xác nhận cắt** để chỉnh ảnh. Không tự cắt ảnh. **Đặt lại ảnh gốc** bỏ các chỉnh sửa trong lần biên tập hiện tại; nguồn gốc vẫn được giữ trên server. Xoay lại ảnh sẽ bắt đầu vùng cắt toàn ảnh theo hướng mới.
- **Hủy** hoặc đóng cửa sổ hủy bản nháp và tải đang thực hiện; không tự tạo mặt hàng. Yêu cầu lưu đã hoàn tất ở server không phải một thao tác có thể hoàn tác bằng việc đóng cửa sổ.
- Trong phiên, chọn **Ảnh / video** để mở thư viện; người bán cũng có nút này trong danh sách mặt hàng. Có thumbnail, trước/sau, zoom/kéo/vừa khung, toàn màn hình và thử lại. Ảnh chi tiết chỉ tải khi chọn ảnh, không tải tất cả ảnh gốc ngay khi mở danh sách phiên.
- Video được chuẩn hóa cho JavaFX, có phát/tạm dừng, thanh tua, âm lượng, toàn màn hình; không tự phát và ban đầu tắt tiếng. Nếu quyền xem hết hiệu lực hoặc đường truyền gián đoạn, chọn **Thử lại**; không cần mở URL thủ công.

## Định dạng đã kiểm chứng

- Ảnh: JPEG/JFIF/JPE, PNG/APNG, BMP, GIF, TIFF, WebP, HEIC/HEIF, AVIF, JPEG 2000, ICO và PNM. Có test tệp HEIC thật, HEIC điện thoại 4032x3024 ghép từ 48 ô, AVIF/JP2/ICO/PPM sinh bằng encoder thật; không chỉ kiểm tra header hay đuôi tệp. GIF/APNG dùng khung đầu; TIFF dùng trang đầu; chưa cam kết phát ảnh động hoặc xem nhiều trang TIFF.
- JPEG có EXIF hướng xoay đã được thử; hướng được chuẩn hóa trước xoay/cắt. PNG trong suốt được đặt trên nền trắng, không thành nền đen. Ảnh lớn được thu vừa, giữ tỷ lệ; bản chi tiết tối đa cạnh 4096 px, thumbnail tối đa cạnh 360 px, đều là JPEG riêng biệt.
- Video: MP4/MOV với MPEG-4 + AAC, WebM với VP9 + Opus, MKV/AVI với MPEG-4 + AAC đã được thử. Server nhận diện container/luồng thật bằng ffprobe và giải mã lại; đổi đuôi hoặc MIME không làm tệp lỗi thành hợp lệ. Không cam kết mọi codec trong mọi container.
- Video được chuyển sang MP4 H.264/AAC, yuv420p, tối đa 1920x1080, 30 fps, fast-start; không tự cắt ngắn video để lách giới hạn thời lượng. Đã kiểm tra phát, tua và điều chỉnh âm lượng bằng JavaFX MediaPlayer thật trên máy Windows hiện tại.
- HEIC dạng lưới được ghép đủ ảnh trước khi thu nhỏ, không lấy nhầm một ô 512 px làm cả ảnh; kiểm tra tổng kích thước lưới để không lách quota. Tên viết hoa, tên không có đuôi hoặc đuôi sai không quyết định bộ giải mã. Tệp được chuẩn hóa thành JPEG để client Windows và Mac không phải tự giải mã HEIC/AVIF bằng JavaFX.
- Không thể cam kết nhận mọi tệp mang tên ảnh: SVG/RAW, dữ liệu hỏng, codec không có trong FFmpeg hoặc tệp vượt quota vẫn bị từ chối. Các biến thể HDR, ảnh chiều sâu/không gian, màu in chuyên nghiệp, mọi hướng xoay của HEIC và mọi thiết bị ghi hình thực tế chưa được kiểm chứng đầy đủ. Giữ giới hạn tài nguyên để tránh làm treo server.

## Giới hạn và cấu hình

Cấu hình trong file server đang dùng (bản desktop: `app/config/server.properties` trong app-image). Nếu EXE tự bung, sửa cấu hình của phiên bản tương ứng ở thư mục cache được launcher tạo, không sửa dữ liệu trong runtime. Có thể dùng thuộc tính Java `btl16.server.config` để chỉ định file cấu hình riêng khi chạy từ mã nguồn.

| Khóa | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `media.port` | 8891 | HTTP media, tách khỏi WebSocket 8890; 0 dùng cổng động trong test |
| `media.imageMaxBytes` | 33554432 | 32 MiB mỗi ảnh nguồn |
| `media.videoMaxBytes` | 209715200 | 200 MiB mỗi video nguồn |
| `media.imageMaxPixels` | 64000000 | 64 triệu điểm ảnh nguồn/lưới; có test PNG điện thoại 48 MP |
| `media.videoMaxPixels` | 8500000 | 8,5 triệu điểm ảnh mỗi khung nguồn |
| `media.videoMaxSeconds` | 180 | Tối đa ba phút; kiểm tra cả đầu vào và đầu ra |
| `media.processingTimeoutSeconds` | 180 | Hạn tải một tệp và mỗi bước xử lý native |
| `media.productMaxBytes` | 419430400 | 400 MiB tổng nguồn và biến thể của mặt hàng/bản nháp |
| `media.storageMaxBytes` | 5368709120 | 5 GiB toàn kho, gồm tệp đang tải và tệp chưa dọn |
| `media.detailMaxEdge` | 4096 | Cạnh tối đa bản ảnh chi tiết |
| `media.workers` | 2 | Số tệp tải/xử lý đồng thời, cấu hình 1-8 |

Kích thước tối thiểu của đầu vào là 2x2. Mỗi phiên đăng nhập có tối đa ba bản nháp, toàn server 64; bản nháp hết hạn sau một giờ. Một bản nháp được giữ tối đa 12 tệp trung gian để thay/chỉnh, nhưng khi lưu vẫn kiểm tra 1-5 ảnh và tối đa một video, không trùng tham chiếu. Hãy xóa tệp không dùng nếu vượt ngân sách bản nháp.

Server Windows kèm FFmpeg/ffprobe trong gói. Server Mac cần cài FFmpeg 8+ và ffprobe một lần (`brew install ffmpeg`); client Mac không cần. Nếu không đặt `media.ffmpeg`/`media.ffprobe`, server tự tìm công cụ đóng gói, `/opt/homebrew/bin`, `/usr/local/bin`, rồi `PATH`. Nếu bản cũ có đường dẫn sai, xóa hai dòng này để tự dò hoặc sửa thành đường dẫn đúng. Không dùng tệp `.exe` Windows trên Mac. Bản cài cũ giữ cấu hình riêng; nếu vẫn có ngưỡng 24 MP, đổi `media.imageMaxPixels=64000000` trong file cấu hình server đang dùng rồi khởi động lại.

Kiểm thử đầy đủ và giới hạn kiểm chứng: [24_KIEM_THU_TOAN_BO.md](24_KIEM_THU_TOAN_BO.md).

Trước xử lý, server dự trữ ngân sách bằng `media.productMaxBytes` cho mỗi worker và kiểm tra dung lượng trống ổ đĩa (chừa thêm 64 MiB). Đây là dự trữ bảo thủ: có thể từ chối tệp nhỏ khi phần kho còn lại không đủ cả ngân sách, thay vì làm đầy đĩa giữa chuyển đổi. Nên cấu hình giới hạn toàn kho lớn hơn ngân sách mỗi worker. Giới hạn điểm ảnh được truyền cả vào ffprobe/FFmpeg trước giải mã. Đầu ra native bị giám sát dung lượng và thời gian; stdout/stderr được đọc đồng thời qua pipe có giới hạn, tránh log tăng vô hạn và lỗi Windows giữ khóa tệp log.

## Lưu trữ và sao lưu

- Lưu lâu dài duy nhất trong MySQL/XAMPP. `media_content` lưu nguồn, thumbnail, ảnh chi tiết và video đã xử lý dưới dạng BLOB chia đoạn tối đa 256 KiB, phù hợp cả XAMPP mặc định có `max_allowed_packet=1 MiB`.
- Mỗi media có UUID do server tạo: `products/<UUID>/source`, `thumbnail.jpg`, `detail.jpg` hoặc `video.mp4`. `staging` chỉ chứa tải dở. Không dùng tên/đường dẫn client làm đường dẫn đĩa và không trả đường dẫn tuyệt đối server trong manifest.
- Bảng `product_media` lưu ID, chủ thể mặt hàng, loại, tên, thông số và thứ tự; `media_content` lưu nhị phân. Lưu metadata và toàn bộ BLOB trong cùng giao dịch; lỗi sẽ rollback. Xóa/thay metadata xóa các đoạn cũ bằng khóa ngoại, những media giữ lại được ghi lại trong giao dịch đó.
- File `.media.lock` ngăn hai server cùng quản lý một kho. Khi khởi động lại, tải dở được dọn. Hủy/lỗi tải được dọn có thử lại khi Windows còn giữ handle. Tệp `products` không có tham chiếu, cũ hơn 24 giờ, được xét dọn sau 60 giây đầu và mỗi giờ; kiểm tra lại DB ngay trước xóa, đồng bộ với bước đưa tệp vào kho và lưu metadata. Tệp có tham chiếu không bị dọn dù cũ. Nếu DB lỗi, bỏ lượt dọn, không đoán rằng tệp đã mồ côi.
- Sao lưu **toàn bộ database MySQL**, bao gồm `media_content`. Các file `products/staging` hiện chỉ nằm trong `%TEMP%/btl16-mysql-media-*`; không phải dữ liệu lâu dài, không cần chuyển sang máy mới. Server khởi động lại có thể khôi phục ảnh/video chỉ từ DB. `btl16.dataDir`/`media.root` không thay thế nơi lưu lâu dài của bản MySQL.
- Không xóa thư mục `products`, đổi UUID hoặc chỉ dọn theo tên tệp để lấy dung lượng; dùng cơ chế đối chiếu metadata. Không đưa kho lên thư mục public của web server.

## Quyền và dữ liệu cũ

- Bắt đầu/chỉnh/lưu bản nháp kiểm tra đăng nhập và chủ sở hữu; lưu kiểm tra lại trong giao dịch khóa hàng sản phẩm, dùng chung với tạo phiên, ngừng và khôi phục. Đã từng có phiên thì khóa cả nội dung, media, đại diện, thứ tự, dù phiên đã hủy/không có người thắng. Khôi phục không gỡ khóa và không sửa lịch sử.
- Mỗi GET/HEAD/range ảnh gốc, thumbnail, chi tiết và video dùng vé ngẫu nhiên gắn với phiên đăng nhập, media và ngữ cảnh phiên đấu giá. Vé 30 phút không thay thế quyền; server kiểm tra lại đăng nhập, mật khẩu/quyền phòng riêng và lệnh cấm trên từng yêu cầu, đồng thời kiểm tra lại trong lúc stream. Đăng xuất, bị kick hoặc đóng bản nháp làm vé không còn sử dụng được. Thư viện có thể lấy vé mới qua **Thử lại**.
- Đầu vào/đầu ra HTTP dùng buffer 64 KiB, có Range 206/416 và HEAD, không Base64 toàn video. Legacy `GET_PRODUCT_IMAGE` chỉ trả thumbnail JPEG nhỏ để giữ tương thích; legacy tạo/sửa một ảnh cũng đi qua bộ xử lý tệp và điều kiện mới, không có đường tắt ghi BLOB.
- Đợt chuyển sang MySQL-only đã xóa dữ liệu cũ theo yêu cầu, không nhập lại H2 hoặc kho file cũ. Bộ chuyển ảnh BLOB đời cũ chỉ còn phục vụ fixture/test cách ly, không phải đường lưu trữ production.
- Mặt hàng cũ chưa từng có phiên có thể bổ sung cho đủ điều kiện. Mặt hàng đóng băng mà thiếu mô tả/ảnh hợp lệ không được mở phiên mới. Phiên đã tồn tại vẫn giữ kết quả và các quy định cũ, không bị đóng lại hay reset vì nâng cấp media.

## Build và kiểm thử cách ly

```powershell
. ./scripts/activate-env.ps1
powershell -ExecutionPolicy Bypass -File tools/prepare-media.ps1
mvn -q test-compile dependency:copy-dependencies '-DincludeScope=runtime'
java -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.AllSelfTests
java --module-path 'target/dependency/javafx-base-21.0.4-win.jar;target/dependency/javafx-graphics-21.0.4-win.jar;target/dependency/javafx-controls-21.0.4-win.jar;target/dependency/javafx-media-21.0.4-win.jar' --add-modules javafx.controls,javafx.media -cp 'target/classes;target/test-classes;target/dependency/*' vn.ptit.btl16.selftest.MediaUiSelfTest
python tools/export-auction-rules.py
python tools/export-auction-rules.py --check
npm run test:packaging
npm run dist
powershell -NoProfile -ExecutionPolicy Bypass -File tools/verify-media-bundles.ps1
```

FFmpeg/ffprobe được tải theo phiên bản và SHA-256 cố định; server bundle kèm công cụ, LICENSE, README build và nguồn gốc trong `tools/media`. Client chỉ cần runtime JavaFX Media kèm sẵn, không cần cài FFmpeg. Thông tin giấy phép nguồn mở nằm ngoài bộ luật Word; cần rà soát điều kiện phân phối nguồn của công cụ bên thứ ba trước khi công bố lại bundle rộng rãi.

`MediaSystemSelfTest` chỉ tạo H2 với tên `btl16_verify_media_<UUID>` trong thư mục tạm đã kiểm tra; không dùng `btl_16`. Test có tệp thật, điều kiện thiếu/quá số lượng, EXIF, ảnh lớn, lỗi nội dung/video, quota, hạn tải, tải dở/hủy, quyền/vé/range, chuyển BLOB, restart, dọn mồ côi và cạnh tranh sửa/tạo phiên. `MediaUiSelfTest` dùng repository bộ nhớ và HTTP loopback riêng, thao tác JavaFX thật, phát/tua video thật và đếm nhịp FX khi xử lý ảnh lớn; ảnh màn hình ở `target/media-ui-preview`.

`JdbcProductRestorationSelfTest --mysql` đã chạy thành công trên database MySQL/MariaDB thử mang tên UUID riêng: có schema/metadata media mới, đóng băng, lịch sử và cạnh tranh khôi phục/tạo phiên qua nhiều kết nối; database thử được tự dọn. Kiểm thử này không thay thế kiểm tra toàn bộ chuyển BLOB, upload/stream và phục hồi media trên MySQL. Ba lượt JavaFX liên tiếp với JAR/thư viện đóng gói cũng đã đạt sau khi sửa lỗi khóa log native trên Windows.

Không chạy smoke-test EXE hoặc production để thực hiện lượt bàn giao này. Chưa xác minh LAN nhiều máy vật lý, firewall/Wi-Fi thật, ngắt điện đột ngột, toàn bộ luồng migration/streaming media trên MySQL, hoặc mọi biến thể codec. HTTP/WebSocket hiện không mã hóa TLS: chỉ dùng LAN tin cậy, mở TCP 8890/8891 với profile Private/LocalSubnet; không mở cổng MySQL cho client, không đưa dịch vụ thẳng ra Internet. Kết quả và phạm vi kiểm chứng cuối nằm trong `VERIFICATION.md`.
