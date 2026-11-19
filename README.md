# Đúng Góc

Ứng dụng Android giúp người mới căn bố cục cảnh và chụp ảnh một người bằng camera sau. Giao diện và chỉ dẫn bằng tiếng Việt; ảnh được phân tích trên thiết bị, không gửi lên mạng.

## Chế độ và đường nét gợi ý

- **Tự động / Chụp người / Chụp cảnh**: mặc định tự động, có thể tự chọn bất cứ lúc nào. Tự động chuyển sang người sau 0,5 giây nhận diện ổn định; chuyển sang cảnh sau 1,8 giây không thấy người (0,9 giây khi mới mở), tránh nhảy chế độ vì mất dấu tạm thời.
- **Chụp người**: một dáng người mờ nằm cố định ở vị trí nên đứng. Đổi dáng, bán thân/toàn thân hoặc chọn chính giữa/một phần ba trái/phải; hướng dẫn căn máy dùng cùng vị trí và tỷ lệ với mẫu. Có nút Đổi dáng ngay trên camera.
- **Chụp cảnh**: vòng mờ đánh dấu điểm đặt chi tiết chính; đường nét đứt gợi ý vị trí của đường ngang nổi bật. Có thể đổi bố cục, bật/tắt đường nét. Nút chụp luôn ở ngoài vùng điều khiển cuộn.
- Gợi ý cảnh dựa trên tương phản, màu sắc và cạnh ngang của ảnh thu nhỏ; không phân loại ngữ nghĩa biển/núi/tòa nhà, không hiểu sở thích thẩm mỹ. Tự động phân biệt có người/không thấy người, vì vậy cảnh không có người cũng có thể là đồ vật hoặc nội thất. Chọn chế độ thủ công nếu nhận diện không đúng ý định.
- Chỉ báo khớp gợi ý khi có điểm nhấn/đường ngang đủ rõ và giữ ổn định. Cảnh ít chi tiết vẫn hiện bố cục tham khảo, không tự báo đã đẹp. Chế độ cảnh thủ công không cần nạp mô hình người.

## Chụp và lưu ảnh

- Camera thật với hướng dẫn căn khung toàn thân/bán thân, tiến/lùi, hướng máy trái/phải và giữ máy thẳng.
- 5 dáng có hình minh họa: tự nhiên, chống hông, tay ngang eo, chạm tóc, thả tay nhẹ. Có chế độ chỉ căn bố cục.
- Mỗi lần chỉ hiện một chỉ dẫn, ghi rõ dành cho người chụp hay người tạo dáng.
- Khung xanh sau khoảng một giây ổn định; mất dấu, thêm người hoặc cắt cơ thể sẽ hủy trạng thái sẵn sàng. Người dùng vẫn có thể bấm chụp khi chưa có khung xanh.
- Xem ảnh trước khi lưu, chụp lại hoặc lưu ảnh gốc có EXIF. Bản xem trước đã được xoay theo EXIF.
- Android 10+: lưu vào `Pictures/DungGoc`. Android 8–9: chọn nơi lưu bằng hộp thoại hệ thống. Không xin quyền đọc toàn bộ ảnh hoặc ghi toàn bộ bộ nhớ.
- Camera dừng khi ứng dụng về nền và khi xem ảnh. Chỉ dẫn bắt đầu lại sau khi mở camera trở lại hoặc đổi kiểu ảnh/dáng.

## Chạy và tạo APK

Cần **JDK 17**, **Android SDK Platform 35**, **Build Tools 35.0.0**, và kết nối mạng cho lần build đầu. Cách thuận tiện nhất là cài Android Studio, mở thư mục này, chọn Gradle JDK 17 và để Studio tạo `local.properties` với `sdk.dir`. Không đưa file này vào Git.

Trên Windows PowerShell, nếu chưa cấu hình các biến môi trường:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :guidance:test :app:lintDebug :app:assembleDebug
```

Trên macOS/Linux:

```sh
sh gradlew :guidance:test :app:lintDebug :app:assembleDebug
```

Hai launcher là bootstrap dạng văn bản: tải Gradle 8.11.1 vào `.tools/` và kiểm tra SHA-256 từ [Gradle](https://gradle.org/release-checksums/). Không cài công cụ vào hệ thống. Có thể dùng Gradle 8.11.1 đã cài sẵn với cùng các tác vụ, hoặc chạy tác vụ `wrapper` để tạo bộ Gradle Wrapper tiêu chuẩn.

APK debug nằm tại `app/build/outputs/apk/debug/app-debug.apk`. Khi đã bật USB debugging trên điện thoại:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Workflow `.github/workflows/android.yml` cũng chạy kiểm thử, lint và tạo APK khi push/PR hoặc kích hoạt thủ công. Workflow được cung cấp trong mã nguồn; chưa được chạy từ môi trường hiện tại. Không có thao tác phát hành hoặc upload mã nguồn nào được thực hiện.

## Mô hình ngoại tuyến

Tác vụ `preparePoseModel` tải **Pose Landmarker Lite float16 revision 1** từ Google vào thư mục assets được sinh khi build. Mô hình được đóng gói trong APK; điện thoại không cần mạng kể cả lần mở đầu. Manifest loại quyền Internet ngay cả khi thư viện phụ thuộc khai báo quyền này.

Nguồn mô hình cố định:

```text
https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task
```

Nếu đã có mô hình trên máy hoặc môi trường build không truy cập được Google Storage:

```powershell
.\gradlew.bat :app:assembleDebug '-PposeModelPath=C:/models/pose_landmarker_lite.task'
```

Tác vụ kiểm tra kích thước và các tệp detector trong archive, chỉ hoàn tất asset sau khi kiểm tra thành công. Đây không phải kiểm tra checksum mật mã cho mô hình. Xem [tài liệu và điều khoản MediaPipe](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker) trước khi phân phối sản phẩm.

## Kiến trúc

- `guidance`: Kotlin/JVM thuần, không phụ thuộc Android. Nhận các điểm cơ thể chuẩn hóa theo khung đang hiển thị, độ tin cậy, độ sáng, độ nghiêng và lựa chọn người dùng; trả về chỉ dẫn, đối tượng cần hành động, mũi tên và trạng thái sẵn sàng.
- `app`: Kotlin/Compose, CameraX, MediaPipe và lưu ảnh. Preview, phân tích và chụp dùng chung một CameraX ViewPort. Khung phân tích được crop rồi xoay trước khi suy luận, tránh đánh giá theo phần ảnh không xuất hiện trên màn hình.
- Phân tích đồng bộ ở `VIDEO` mode trên một executor riêng để dùng tracking, bỏ khung cũ và giới hạn khoảng 2–10 lần/giây tùy tốc độ xử lý; không chặn luồng giao diện. Camera vẫn hiển thị theo nhịp riêng.
- Bộ quy tắc ưu tiên ánh sáng/khả năng nhận diện, số người, phần cơ thể trong khung, bố cục, độ nghiêng rồi tư thế. Có làm mượt, ngưỡng vào/ra khác nhau và thời gian giữ chỉ dẫn. Điều kiện sẵn sàng cũng kiểm tra điểm thô để không che khuất mất dấu bởi làm mượt.
- Ảnh chưa lưu nằm trong cache riêng; chụp lại sẽ xóa bản tạm. Dọn các bản tạm quá 24 giờ, trừ ảnh đang xem. Cache có thể bị Android thu hồi; đây không phải kho lưu ảnh lâu dài.
- `CompositionCoach` chọn người/cảnh, giữ ổn định bố cục đích và chuyển chỉ dẫn phù hợp; `PoseTemplate` cung cấp các tọa độ chuẩn hóa để vẽ dáng mờ. `SceneComposition` chỉ xử lý ảnh thu nhỏ trên CPU, không có mô hình tải thêm hoặc API mạng.

Không có backend, tài khoản, nhận diện danh tính, điểm chấm ngoại hình, tự động chụp, âm thanh, selfie hoặc chế độ nhóm. App không đo khoảng cách theo mét hay suy ra góc máy tối ưu trong không gian 3D. Các dáng được đối chiếu bằng quy tắc hình học tương đối, cần hiệu chỉnh qua thử nghiệm thực tế.

## Kiểm thử và tình trạng xác minh

`guidance/src/test` kiểm thử mất người, nhiều người, thiếu sáng, điểm bị che, cắt chân, bán thân, hướng căn máy, từng dáng, thời gian ổn định, chọn người/cảnh, chế độ thủ công, ảnh phẳng, đường ngang, điểm nhấn và độ khớp giữa mẫu mờ với quy tắc cho mọi dáng/kiểu ảnh/bố cục. Chạy riêng bằng `:guidance:test --configure-on-demand` để chỉ cấu hình module JVM, không cấu hình app Android; vẫn cần JDK/Gradle và các dependency Kotlin/JUnit.

Trong môi trường triển khai ban đầu chưa có Android SDK, Gradle hoặc JDK 17 đầy đủ được cấu hình và tải dependency bị chặn bởi proxy. Vì vậy **chưa biên dịch APK, chưa chạy JUnit/lint và chưa xác minh trên điện thoại**. Có thể chạy `python scripts/check_project.py` để kiểm tra cấu trúc XML, cấu hình, tài nguyên và các ràng buộc đóng gói; kiểm tra này không thay thế việc biên dịch hoặc kiểm thử Android.

Danh sách thử nghiệm máy thật và tiêu chí nghiệm thu ở [docs/acceptance.md](docs/acceptance.md).

