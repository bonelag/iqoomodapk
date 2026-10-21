# Sửa lỗi 0.1.1 — 2026-10-21

Người dùng xác nhận sau khi tự reload game/GameCube: có nút Siêu tốc độ khung hình nhưng bấm không mở bảng điều khiển; VSR chưa hiện.

## Bằng chứng đọc từ thiết bị

- Snapshot LSPosed: module đã enabled, scope gồm system, com.vivo.gamecube, com.vivo.gamewatch. Không sửa database LSPosed. Snapshot DB/WAL chỉ để đọc, không bảo đảm ảnh chụp nguyên tử.
- Game đang được nhận diện: com.garena.game.kgvn.
- Log hiện tại ở GameCube assistantui: `HOOK_ERROR m9.j.s ... ClassNotFoundException: s7$b`.
- Log GameCube/GameWatch/system: `Failed to find provider info for io.github.vivogameunlock.config`, `CONFIG_UNAVAILABLE: IllegalArgumentException`.
- Provider có khai báo trong PackageManager và có ContentProviderRecord nhưng các caller được thử vẫn không resolve qua content resolver. Chưa xác định lớp lọc cụ thể; không kết luận Hide My Applist là thủ phạm.
- XML cấu hình ứng dụng có target_fps/selected/excluded; không có dữ liệu chứng minh native engine đã hoạt động.

Log lọc chỉ liên quan module nằm trong runtime/0.1.0-module-log.txt (local, không đưa vào source bundle).

## Thay đổi

1. Entry object Xposed có thể nhận nhiều callback package. 0.1.0 gán lại loader/process trên cùng object trước khi lọc package, nên closure hook có thể dùng loader khác. 0.1.1 tạo session riêng, dùng field final và Context.getClassLoader() sau attach. Không đổi tên class s7.b thành s7$b: tên DEX s7.b vốn đúng; dấu `$` là fallback lookup của XposedHelpers khi không tìm thấy tên ban đầu.
2. Bổ sung broadcast cấu hình: receiver trong host yêu cầu sender có permission signature do module khai báo; receiver request trong module yêu cầu DUMP và chỉ trả lời về ba đích cố định. Không mở provider cho mọi UID, không chạy shell/root từ ứng dụng.
3. Có cache cấu hình riêng trong private SharedPreferences của scope để nhận lại sau lifecycle tự nhiên. Không ghi Settings hệ thống, không sửa database tính năng OEM. Chưa có cache/broadcast/provider hợp lệ thì vẫn giữ policy đóng.
4. Tăng version code lên 2; log LOAD có version và thêm CONFIG_RECEIVER_READY/CONFIG_RECEIVED.

## Kiểm tra và giới hạn

- 30 PolicyTest + 10 ConfigContractTest.
- 69 khai báo target DEX khớp.
- 12 kiểm tra APK, có kiểm tra field loader/process bất biến.
- Chữ ký v3 và zipalign đạt.
- Đã xem manifest đóng gói: permission CONFIG protectionLevel=signature; request receiver yêu cầu DUMP.
- Chưa xác nhận bản 0.1.1 nhận broadcast hay mở bảng điều khiển trên thiết bị. Những kiểm tra offline không chứng minh runtime thành công.
- Cập nhật APK không thay được hook đang tồn tại trong process. Không khởi động lại hay force-stop để ép nạp; người dùng giữ quyền quyết định lifecycle.

## Trạng thái sau cài bản sửa

ADB install -r --dont-kill --no-incremental thành công; versionName=0.1.1/versionCode=2; permission CONFIG được khai báo signature và cấp cho chính module. PID của system_server, GameWatch và các process GameCube giữ nguyên. PID ứng dụng module đổi khi APK được cập nhật. Không gọi force-stop hay reboot. Chưa quan sát LOAD version=0.1.1 trong các scope hiện còn sống; chưa xác nhận bản sửa UI/backend có hiệu lực.
