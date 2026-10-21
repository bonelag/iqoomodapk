# Kiểm tra 0.1.2 — 2026-10-21

- Build Java/D8/Android, ký v3 và zipalign: đạt.
- PolicyTest 30/30; ConfigContractTest 10/10.
- Khai báo DEX gốc: 96/96; kiểm tra APK: 12/12.
- SHA256: 95776800e2d675cc2c474d983de7173655c5409d00a4c7a55fbf95fbaf844588.
- ADB install -r --dont-kill --no-incremental: Success. PackageManager xác nhận versionName 0.1.2, versionCode 3 lúc 02:37:55.
- PIDs trước/sau cài giống nhau: 1988 3252 18953 20792 23153 23447 cho truy vấn system_server/GameCube/GameWatch/ba game thử. Không reboot, force-stop hoặc kill.
- Log sau cài chưa có VAA_UI_ADDED/VSR_SELECTION của 0.1.2. Process đang chạy giữ hook cũ; cần người dùng reload GameCube/game.

Người dùng đã xác nhận MEMC của 0.1.1 hoạt động trên Free Fire, Master Duel, Liên Quân. 0.1.2 giữ nguyên các hook MEMC đó. VAA/VSR mới chưa được xác nhận bằng thử nghiệm trong game.

## Kiểm thử tiếp

1. Reload GameCube/game như lần trước; không cần reboot điện thoại.
2. Mở panel Siêu FPS hoặc Siêu độ phân giải trong sidebar. Với game ngoài whitelist VAA gốc, tìm công tắc Khử răng cưa (VAA) bên trong panel; có thể cần cuộn xuống.
3. Thử bật/tắt VAA. Log cần có VAA_UI_ADDED, VAA_REQUEST, VAA_SELECTION; đối chiếu tiếp NATIVE_DISPATCH dispatchVAAEnabled và log OEM. ROM chỉ bật VAA GLES, không có VAA Vulkan.
4. Chọn VSR Cực cao (mode 1), đóng/mở lại panel. Xem VSR_SELECTION: accepted, stored, reason, setting. Thermal/update/hardware gate bị chặn sẽ báo lỗi, không giả thành công.
5. Lựa chọn lưu và dispatch chỉ chứng minh yêu cầu đã đi qua một đoạn pipeline. Kiểm tra hiệu ứng thực tế trong game để xác nhận GPU/engine áp dụng.

Không gán kết quả kiểm thử offline cho runtime. Chi tiết bằng chứng và giới hạn: research/FIX-0.1.2.md. Báo cáo bản trước được lưu ở dist/0.1.1.

Cập nhật quan sát sau cài: system_server sau đó đổi sang PID 32689 và GameWatch sang 2253, GameCube daemon sang 3271; log xác nhận LOAD version=0.1.2. Chưa xác định nguyên nhân đổi process. Agent không chạy reboot/kill/force-stop. Chưa có log thao tác VAA/VSR ở thời điểm lưu; không coi đây là xác nhận hiệu ứng đồ họa.
