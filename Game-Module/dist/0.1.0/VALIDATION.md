# Kiểm tra bản 0.1.0

Ngày hoàn thành: 2026-10-21 (Asia/Saigon).

| Hạng mục | Kết quả |
|---|---|
| Compile Java + D8 + đóng gói Android | Đạt |
| PolicyTest | 30/30: chọn package, loại trừ, từng tính năng, mode FPS, hysteresis nhiệt 47/44°C |
| Đối chiếu khai báo DEX gốc | 69/69: method, prototype, field, static flag và enum parent |
| Kiểm tra APK | 10/10: entry point, class, tài nguyên, API chỉ compile, không có shell/native/key payload |
| Chữ ký | apksigner verify đạt, APK Signature Scheme v3 |
| Alignment | zipalign check đạt |
| Cài lên điện thoại | Chưa thực hiện |
| LSPosed nạp hook thực tế | Chưa kiểm thử |
| Sidebar trong game | Chưa kiểm thử |
| Engine native chấp nhận / tạo khung hình | Chưa kiểm thử |
| So sánh chất lượng VSR / đo frame nội suy đầu ra | Chưa kiểm thử |

APK được ký bằng khóa phát triển của dự án. SHA-256 hiện tại nằm trong SHA256SUMS, đồng thời được ghi ở build-report.json và apk-verification.json. Hai JSON xác minh APK phải có cùng hash với APK.

Source bundle nằm cạnh APK: VivoGameUnlock-0.1.0-source.zip. Không chứa keystore riêng, firmware hay thư mục giải biên dịch lớn. Có source ứng dụng/hook, công cụ build và test, tài liệu, dependency compile-only có hash.

Không khởi động lại, không force-stop, không cài hay kích hoạt module, không sửa Settings/property/database hoặc phân vùng trên thiết bị. Không có script triển khai tự động sang điện thoại. Những thao tác thu thập ADB ban đầu chỉ đọc; sau yêu cầu hạn chế thao tác trên điện thoại, chỉ làm việc offline.

Giới hạn ảnh hưởng đến kết luận: xác minh DEX chứng minh tên/chữ ký hook khớp artifact, không chứng minh hook được LSPosed thực thi. Kết quả kiểm tra gói APK không chứng minh tương thích mọi render path. Không tuyên bố đã unlock native hoạt động trên mọi game.
