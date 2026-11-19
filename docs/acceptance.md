# Kiểm thử trên thiết bị

Các mục bên dưới là việc cần thực hiện trên máy thật; chưa đánh dấu đạt khi chưa có bằng chứng.

## Kiểm thử tự động trên Android

Kết nối điện thoại đã bật USB debugging hoặc mở emulator, rồi chạy:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

`PoseAnalyzerTest` chạy mô hình thật với hai khung hình trắng liên tiếp. Kiểm thử xác nhận bitmap được giải phóng, mỗi khung đều trả về hướng dẫn “không có người”, và bộ phân tích không bị tắt sau khung đầu tiên. Trường hợp này ngăn lỗi đọc độ sáng sau khi `MPImage.close()` đã recycle bitmap. Không cần cấp quyền camera vì kiểm thử dùng ảnh tạo sẵn trên máy.

Workflow GitHub biên dịch APK kiểm thử bằng `assembleDebugAndroidTest`; chưa tự chạy kiểm thử thiết bị này.

## Ma trận thiết bị

Chọn ít nhất ba máy: Android 8/9, một máy tầm trung Android 10–13, một máy Android 14 trở lên. Ghi model, phiên bản hệ điều hành, RAM, phiên bản APK và kết quả cho từng trường hợp. Ưu tiên máy có ít nhất một thiết bị hiệu năng thấp.

## Luồng chính

- Cài APK, bật chế độ máy bay trước lần mở đầu; cấp quyền camera và thấy preview cùng hướng dẫn.
- Toàn thân: đứng đủ trong khung, chọn lần lượt 5 dáng; hướng dẫn phản ánh đúng dáng và chuyển xanh sau khoảng một giây giữ ổn định.
- Bán thân: lấy khung từ đầu đến hông; không yêu cầu đưa chân vào ảnh. Kiểm tra hình minh họa tương ứng.
- Tắt hướng dẫn dáng; chỉ còn chỉ dẫn bố cục. Có thể chụp ở mọi trạng thái khi camera đã mở.
- Đưa người về bên trái/phải, đứng quá gần/quá xa, nghiêng máy: hành động theo chỉ dẫn phải đưa khung về trạng thái đạt.
- Chụp, xem, chụp lại: bản tạm bị xóa và thư viện chưa xuất hiện ảnh. Chụp lần nữa, lưu: chỉ có một ảnh lưu đúng chiều.
- Android 8/9: hủy hộp thoại lưu không mất ảnh tạm; chọn lưu và kiểm tra file JPEG ở vị trí đã chọn. Android 10+: xác nhận ảnh ở Pictures/DungGoc.

## Độ tin cậy và vòng đời

- Cho thêm người vào ảnh, che vai/tay/chân, đưa người ra khỏi khung hoặc đi vào nơi tối: khung xanh tắt ở kết quả phân tích tiếp theo và lời nhắc phù hợp.
- Thay đổi dáng/kiểu ảnh lúc đang xanh: không tái sử dụng trạng thái sẵn sàng cũ.
- Chuyển nền, khóa/mở màn hình, quay lại từ cài đặt quyền: camera giải phóng khi nghỉ, hoạt động lại khi quay lại, bắt đầu lại thời gian ổn định.
- Xoay ngang/dọc trong camera và trong màn hình xem ảnh: preview không méo, crop của ảnh chụp khớp khung đang thấy, ảnh xem đúng EXIF, thao tác vẫn truy cập được.
- Mở camera bằng ứng dụng khác hoặc gây lỗi nhận diện: có nút thử lại; nếu chỉ lỗi AI, vẫn chụp thủ công được.
- Từ chối quyền một lần và vĩnh viễn; cấp lại từ Settings; thu hồi quyền khi app ở nền. Không crash, có đường phục hồi.
- Mô phỏng hết dung lượng/lỗi ghi: không báo lưu thành công, không công bố ảnh MediaStore chưa ghi xong, cho phép thử lại.
- Về nền/đổi cấu hình trong lúc chụp hoặc lưu: không crash, không có phiên camera cũ giữ tài nguyên; kiểm tra không tạo nhiều ảnh thư viện từ một lần bấm lưu.
- Cỡ chữ hệ thống lớn và TalkBack: lựa chọn dáng, nút chụp, nút lưu có thể thao tác; nội dung hướng dẫn vẫn đọc được.

## Hiệu năng và trải nghiệm

- Trên máy tầm trung, đo thời gian từ khung đầu vào đến cập nhật chỉ dẫn: mục tiêu p95 dưới 500 ms sau khi mô hình khởi tạo. Ghi cả FPS phân tích, FPS preview, thời gian mở camera và thời gian khởi tạo mô hình.
- Dùng liên tục 10 phút; kiểm tra preview không bị hàng đợi suy luận kéo chậm, theo dõi nhiệt và RAM. Giảm nhịp phân tích hoặc độ phân giải nếu mục tiêu không đạt.
- Thử trong nhà, ngoài trời, ngược sáng, quần áo rộng, các màu da/chiều cao khác nhau; ghi các hướng dẫn sai và hiệu chỉnh ngưỡng.
- Cho 5 người ít kinh nghiệm tự dùng. Ít nhất 4 người tự chọn dáng, làm theo hướng dẫn và lưu ảnh mà không được chỉ thao tác. Hỏi chỉ dẫn nào khó hiểu và so sánh ảnh trước/sau.
- Không coi khung xanh là bằng chứng ảnh đẹp; đánh giá khả năng dùng và chất lượng ảnh riêng.
