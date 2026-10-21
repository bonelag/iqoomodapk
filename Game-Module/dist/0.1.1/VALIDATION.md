# Kiểm tra bản 0.1.1

Ngày hoàn thành: 2026-10-21 (Asia/Saigon).

| Hạng mục | Kết quả |
|---|---|
| Compile Java + D8 + đóng gói Android | Đạt |
| PolicyTest | 30/30: chọn package, loại trừ, từng tính năng, mode FPS, hysteresis nhiệt 47/44°C |
| ConfigContractTest | 10/10: đích nhận cố định, từ chối đích tùy ý/prefix, giới hạn payload |
| Đối chiếu khai báo DEX gốc | 69/69: method, prototype, field, static flag và enum parent |
| Kiểm tra APK | 12/12: gồm loader/process bất biến giữa callback package |
| Chữ ký | apksigner verify đạt, APK Signature Scheme v3 |
| Alignment | zipalign check đạt |
| Cài lên điện thoại | Đã cập nhật 0.1.1 qua ADB với --dont-kill --no-incremental; PackageManager xác nhận code 2 |
| LSPosed nạp hook thực tế | Chưa kiểm thử |
| Sidebar trong game | Chưa kiểm thử |
| Engine native chấp nhận / tạo khung hình | Chưa kiểm thử |
| So sánh chất lượng VSR / đo frame nội suy đầu ra | Chưa kiểm thử |

APK được ký bằng khóa phát triển của dự án. SHA-256 hiện tại nằm trong SHA256SUMS, đồng thời được ghi ở build-report.json và apk-verification.json. Hai JSON xác minh APK phải có cùng hash với APK.

Source bundle nằm cạnh APK: VivoGameUnlock-0.1.1-source.zip. Không chứa keystore riêng, firmware hay thư mục giải biên dịch lớn. Có source ứng dụng/hook, công cụ build và test, tài liệu, dependency compile-only có hash.

Không khởi động lại hay force-stop. Bản 0.1.0 được cài bằng ADB theo yêu cầu người dùng, sau đó đọc log xác định lỗi. Chưa xác nhận runtime bản sửa 0.1.1. Không sửa Settings/property/database/phân vùng để khắc phục. Source không chứa script triển khai tự động sang điện thoại.

Cập nhật 0.1.1: system_server PID 5657, GameWatch 6908, GameCube daemon 7162, GameCube 8481 và assistantui 18941 giữ nguyên sau cài. Process của riêng ứng dụng module đổi từ 11160 sang 32334 trong quá trình cập nhật; --dont-kill không ngăn được thay process riêng của APK khi cài đè base APK này. Không chạy lệnh force-stop. Chưa thấy LOAD version=0.1.1 trong các scope còn chạy, nên chưa coi hook mới đã được nạp.

Giới hạn ảnh hưởng đến kết luận: xác minh DEX chứng minh tên/chữ ký hook khớp artifact, không chứng minh hook được LSPosed thực thi. Kết quả kiểm tra gói APK không chứng minh tương thích mọi render path. Không tuyên bố đã unlock native hoạt động trên mọi game.
