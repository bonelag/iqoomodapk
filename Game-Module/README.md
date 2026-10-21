# Vivo Game Unlock — LSPosed module

**Bản hiện tại: 0.1.3 (version code 4).** Hàng khử răng cưa dùng giao diện OEM: chữ trắng, switch theo theme, trợ giúp qua icon. Bổ sung đường chọn VSR riêng cho Genshin quốc tế có profile gốc. Chi tiết: [research/FIX-0.1.3.md](research/FIX-0.1.3.md). VAA chưa được xác nhận có hiệu ứng: Free Fire lỗi manager GLES; Genshin đang dùng Vulkan trong khi ROM không hỗ trợ VAA Vulkan.

**Lịch sử 0.1.2:** Thêm công tắc **Khử răng cưa (VAA)** trong panel Siêu FPS/Siêu độ phân giải cho game mới; sửa đường chọn VSR qua lệnh OEM và giữ kiểm tra nhiệt độ. ROM hiện chỉ hỗ trợ VAA qua OpenGL ES. Chi tiết: [research/FIX-0.1.2.md](research/FIX-0.1.2.md). MEMC giữ nguyên từ 0.1.1, đã được người dùng xác nhận hoạt động ở Free Fire, Master Duel, Liên Quân.

**Lịch sử 0.1.1:** Sửa hai lỗi đã thấy trong log 0.1.0: class loader bị thay khi có callback package khác (`ClassNotFoundException: s7$b`) và đường đọc cấu hình qua ContentProvider không resolve được. APK mới: [dist/VivoGameUnlock-0.1.3.apk](dist/VivoGameUnlock-0.1.3.apk).

Bản 0.1.1 cố định class loader theo từng hook session, lấy loader từ Context sau Application.attach. Cấu hình có thêm kênh broadcast giới hạn bởi permission `signature`; request cấu hình chỉ dành cho caller có quyền `DUMP`. Bản nhận được lưu ở file cache riêng của module trong dữ liệu private của scope, không sửa các Settings hay database OEM. ContentProvider vẫn là đường dự phòng. Nút **Lưu cấu hình** gửi bản cập nhật đến các scope đã nạp mã 0.1.1.

Sau cập nhật, process còn giữ hook 0.1.0 vẫn chạy mã cũ. Kiểm tra log `LOAD version=0.1.1`, `CONFIG_RECEIVER_READY`, `CONFIG_RECEIVED` và `CONFIG_READY` để xác nhận từng scope đã nạp/nhận bản mới. Không coi cài APK thành công là các hook trong RAM đã được thay. Dự án không tự khởi động lại hay dừng process.

Module thử nghiệm dành riêng cho **vivo PD2507 / V2507A, Android 15 (SDK 35)**:

- GameCube `14.0.10.00_ex`, version code `1401000`.
- GameWatch `25.0.0.03`, version code `250003`.
- Backend từ `vivo-services.jar` đã lấy trên chính thiết bị.

Hook còn kiểm tra chính xác fingerprint ROM `AP3A.240905.015.A2_V000L1 / compiler251024223111`. ROM khác sẽ có log `UNSUPPORTED_ROM` và bỏ qua hook để tránh dùng nhầm cấu trúc framework.

**APK:** [dist/VivoGameUnlock-0.1.3.apk](dist/VivoGameUnlock-0.1.3.apk).

Đã build và kiểm tra offline. Bản 0.1.0 đã được cài qua ADB theo yêu cầu người dùng; log thực tế xác nhận hook nạp được nhưng còn lỗi loader/cấu hình kể trên. Người dùng đã xác nhận MEMC hoạt động trên 0.1.1. VAA/VSR của 0.1.2 cần kiểm thử runtime sau khi người dùng reload GameCube. Không có thao tác khởi động lại hay force-stop thiết bị/app do agent thực hiện.

## Module thực hiện

1. Mở điều kiện theo package của MEMC, VSR và VAA trong GameCube để tính năng có thể xuất hiện trên sidebar.
2. Thêm profile trong bộ nhớ cho game chưa có cấu hình. Giữ package và PID thật. Không giả game thành Genshin.
3. Mở whitelist GLES/Vulkan tại `MEMCController`, `VSRController`, `VAAController` và điều kiện package theo model trong framework. Chỉ mở API mà cờ hỗ trợ phần cứng gốc đang cho phép.
4. Bổ sung bảng FPS cho game chưa có profile MEMC native và chưa phát sự kiện SDK vivo: dùng FPS nguồn được yêu cầu từ sidebar. Đây **không phải FPS đo được**. Giữ kiểm tra lỗi pipeline gốc; profile bổ sung dừng ở 47°C, khôi phục ở 44°C và dừng khi nhiệt độ chưa xác định.
5. Ghi log phân biệt profile, yêu cầu sidebar, backend chấp nhận và dispatch tới ứng dụng. Không giả kết quả engine thành công.

Profile bổ sung mặc định: MEMC `60 → 120`; có thể chọn `30 → 60` hoặc `45 → 90`. VSR chỉ mở chế độ cao thông thường cho game mới; không giả cấu hình cho chế độ siêu cao, post-processing riêng hoặc các kết hợp OEM chưa có bằng chứng.

Route `3` được dùng vì mã gốc GameWatch nhận route này và backend MEMC của ROM kiểm tra trực tiếp `mValues[1] == 3`. Đây là lựa chọn đường GPU của ROM đang phân tích; không phải đổi package hay báo game khác cho hệ thống.

## Cách dùng khi bạn tự quyết định cài

1. Cài APK trong `dist/` bằng trình cài ứng dụng.
2. Trong LSPosed, bật **Vivo Game Unlock**, chọn scope:
   - **System Framework** (bắt buộc cho backend).
   - **GameCube** — `com.vivo.gamecube`.
   - **GameWatch** — `com.vivo.gamewatch`.
3. Mở ứng dụng Vivo Game Unlock để chọn tính năng và profile. Mặc định mở cho mọi ứng dụng người dùng; bạn vẫn cần thêm game vào **Game Mode** của vivo để có sidebar. Có chế độ danh sách cho phép và danh sách loại trừ.
4. Nhấn **Lưu cấu hình**. Khi hook 0.1.1 đã được nạp, scope nhận broadcast hoặc yêu cầu cấu hình mỗi 15 giây; danh sách ứng dụng được đọc mỗi 60 giây. Mở lại sidebar để phần giao diện đọc cấu hình mới.

**Scope System Framework không gắn hook vào một `system_server` đang chạy sẵn chỉ bằng cách cài APK.** Nó cần process framework được nạp cùng module. Theo yêu cầu không khởi động lại/dừng process của bạn, dự án không tự thực hiện bước nạp lại và không chứa script làm việc đó. Cài hoặc bật scope không đủ để coi backend đã hoạt động.

Ứng dụng cấu hình không có quyền/lệnh root, không có nút khởi động lại hay dừng app. Không cần cài đè APK vivo, không cần Core Patch hoặc ZIP KernelSU riêng cho bản này. KernelSU/LSPosed hiện có là hạ tầng; thay đổi được thực hiện bằng hook trong bộ nhớ khi bạn chủ động cho module chạy.

## Giới hạn thực tế

- Mở nút trên sidebar, gửi yêu cầu hợp lệ và backend dispatch **không chứng minh mọi game đã có nội suy hoặc siêu độ phân giải đầu ra**. Engine native vẫn có thể không tương thích API/render path của game.
- Module không thay thuật toán trong GameLib, không patch `.so`, driver hoặc HAL. Nếu có chặn trong native hoặc cần model đặc thù thì vẫn cần phân tích tiếp.
- Whitelist post-processing VSR được giữ nguyên. Giá trị bật VSR vẫn do `VSRConfigParser.getEnabledValue` gốc quyết định; không ép giá trị dương nếu backend trả 0.
- Tất cả profile OEM hiện có được ưu tiên. Không mở mọi chế độ FPS OEM hoặc tắt giới hạn nhiệt/phần cứng/ghi màn hình/lỗi engine.
- Chế độ mọi ứng dụng coi các app người dùng là ứng viên hỗ trợ, vì nhiều game không khai báo Android category GAME. Nó có thể mở khả năng khởi tạo thư viện cho app ngoài game; chọn danh sách cho phép nếu muốn giới hạn chính xác. Xử lý khung hình vẫn do yêu cầu từ sidebar và điều kiện gốc quyết định.
- Tắt module trong ứng dụng khôi phục các quyết định theo whitelist gốc sau lần đọc cấu hình tiếp theo. Nó không cưỡng bức tắt pipeline đang chạy, không xóa lựa chọn đã lưu trong GameCube và không đóng game. Tắt tính năng trên sidebar trước khi đổi profile để yêu cầu bật/tắt đi qua đường gốc.
- Profile trong bộ nhớ có thể bị cấu hình cloud của OEM cập nhật lại. Hook thêm lại profile thiếu khi controller/sidebar kiểm tra lần tiếp theo; không ghi đè database.
- Nếu chưa có cấu hình hợp lệ từ provider, broadcast hoặc cache riêng của module, policy mặc định đóng. Nếu provider lỗi sau khi đã nhận broadcast hợp lệ, scope giữ cấu hình đã nhận; không lặp lại lỗi làm tắt policy.
- Bản khác model/SDK/fingerprint sẽ không cài hook. GameCube/GameWatch sai version code sẽ bị bỏ qua và có log `UNSUPPORTED_VERSION`. Framework cần đối chiếu lại khi cập nhật ROM, kể cả vẫn giữ Android 15.

## Kiểm tra log khi bạn tự thử

Trong log LSPosed, tìm `[VivoGameUnlock]`:

| Dấu log | Ý nghĩa |
|---|---|
| `HOOK_OK` | Đăng ký được hook; chưa khẳng định hook đã được gọi |
| `HOOK_MISSING`, `HOOK_ERROR` | Điểm hook thiếu hoặc callback có lỗi; giữ kết quả gốc của điểm đó |
| `CONFIG_READY` | Đã đọc cấu hình và danh sách ứng viên |
| `LOAD version=0.1.1` | Scope đã nạp bản code mới, không phải hook 0.1.0 trong RAM |
| `CONFIG_RECEIVER_READY` | Đã đăng ký kênh cấu hình có permission signature |
| `CONFIG_RECEIVED` | Scope đã nhận được cấu hình qua kênh dự phòng |
| `MEMC_PROFILE`, `VSR_PROFILE` | Đã tạo profile trong bộ nhớ |
| `SIDEBAR_MEMC_REQUEST` | Đường gốc GameCube đã phát yêu cầu |
| `WATCH_VGPU_REQUEST` | GameWatch đã gọi API VGPU; log có mã trả về API |
| `BACKEND_ALLOWED` | Backend đã chấp nhận package qua whitelist được mở |
| `MEMC_BACKEND_PROFILE` | Đã cấp bảng source/target cho game thiếu profile |
| `NATIVE_DISPATCH` | Framework đã đi qua hàm dispatch; chưa xác nhận engine xử lý thành công |

Xác nhận nội suy thật cần thêm phản hồi engine và phép đo đầu ra. FPS hiển thị của game có thể chỉ đếm frame game render, không gồm frame nội suy.

## Build offline trên máy tính

Máy hiện tại đã có Android SDK và JDK. Từ workspace:

```powershell
./Game-Module/build.ps1
python Game-Module/tools/verify_targets.py
```

Có thể truyền `-Sdk` và `-JavaHome` nếu đường dẫn khác. Build dùng SDK 35, build-tools 35.0.1 và JDK 17; không tải dependency hoặc gọi ADB. Xposed API 82 chỉ dùng lúc compile và không được đóng gói vào APK. Keystore phát triển nằm trong `.local/`, không đưa vào Git; giữ file này nếu muốn ký các bản cập nhật bằng cùng khóa.

- `dist/SHA256SUMS`: hash APK.
- `dist/build-report.json`: kết quả build/kiểm tra ký.
- `dist/target-verification.json`: đối chiếu trực tiếp 69 khai báo DEX, gồm chữ ký method, tên field thật và enum.
- `tests/PolicyTest.java`: 30 kiểm tra chính sách chọn package, chọn profile, loại trừ và ngưỡng nhiệt.
- `tests/ConfigContractTest.java`: 10 kiểm tra giới hạn đích gửi và kích thước payload cấu hình.
- `research/HOOKS.md`: nguồn bằng chứng và phạm vi hook.

Kết quả kiểm tra offline không thay cho thử runtime. Toàn bộ trạng thái kiểm thử được ghi rõ trong `dist/VALIDATION.md`.


