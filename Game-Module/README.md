# Vivo Game Unlock — LSPosed module

**Bản hiện tại: 0.1.4 (version code 5).** APK: `dist/VivoGameUnlock-0.1.4.apk`.

## Thay đổi 0.1.4

- **Sửa lỗi văng hàng loạt app sau khi bật module và khởi động lại.** Nguyên nhân (suy luận từ log và mã, chưa có logcat crash trực tiếp): chế độ "Mọi ứng dụng" khiến hook framework báo `true` cho `isVSR*/isMEMC*WhitelistApp` với mọi app người dùng, nên vivo nạp `libVSR.so` / `libVkLayer_VSR.so` vào renderer của cả app không phải game.
- **Chỉ mở cho game trong Chế độ siêu trò chơi → Các trò chơi được hỗ trợ.** Danh sách đọc từ `content://com.vivo.gamecube.provider/support_game` (cột `package_name`), có `ContentObserver` nên bật/tắt game có hiệu lực ngay. App khác không bao giờ vào whitelist. Provider lỗi hoặc rỗng thì không mở gì.
- Bỏ "Mọi ứng dụng", danh sách cho phép, danh sách loại trừ. Không cần cấu hình package trong app nữa.
- Sửa `No data directory found for package android`: `system_server` không dùng cache SharedPreferences.
- VAA đã bị tắt từ 0.1.3 và không còn trong cấu hình.

Lịch sử: [0.1.1](research/FIX-0.1.1.md), [0.1.2](research/FIX-0.1.2.md), [0.1.3](research/FIX-0.1.3.md), [0.1.4](research/FIX-0.1.4.md). MEMC đã được người dùng xác nhận hoạt động ở Free Fire, Master Duel, Liên Quân (bản 0.1.1). Các bản sau chưa kiểm thử runtime đầy đủ.

Module thử nghiệm dành riêng cho **vivo PD2507 / V2507A, Android 15 (SDK 35)**:

- GameCube `14.0.10.00_ex`, version code `1401000`.
- GameWatch `25.0.0.03`, version code `250003`.
- Backend từ `vivo-services.jar` lấy trên chính thiết bị.

Hook còn kiểm tra fingerprint ROM `AP3A.240905.015.A2_V000L1 / compiler251024223111`. ROM khác sẽ có log `UNSUPPORTED_ROM` và bỏ qua hook.

## Module thực hiện

1. Lấy danh sách game từ GameCube (**Các trò chơi được hỗ trợ**). Chỉ các package này, đã cài, là app người dùng, mới đủ điều kiện.
2. Mở điều kiện MEMC và VSR trong GameCube cho các game đó để tính năng xuất hiện trên sidebar.
3. Thêm profile trong bộ nhớ cho game chưa có cấu hình vivo. Giữ package và PID thật. Không giả game thành Genshin.
4. Mở whitelist GLES/Vulkan tại `MEMCController`, `VSRController` và điều kiện package theo model, chỉ khi cờ hỗ trợ phần cứng gốc đang bật.
5. Bảng FPS cho game chưa có profile MEMC native: dùng FPS nguồn mà sidebar yêu cầu. Đây **không phải FPS đo được**. Giữ kiểm tra lỗi pipeline gốc; profile bổ sung dừng ở 47°C, khôi phục ở 44°C, và dừng khi nhiệt độ chưa xác định.
6. Ghi log phân biệt profile, yêu cầu sidebar, backend chấp nhận và dispatch. Không giả kết quả engine thành công.

Profile bổ sung mặc định: MEMC `60 → 120`; có thể chọn `30 → 60` hoặc `45 → 90`. VSR chỉ mở chế độ cao thông thường cho game mới.

Route `3` được dùng vì mã gốc GameWatch nhận route này và backend MEMC của ROM kiểm tra `mValues[1] == 3`.

## Cách dùng

1. Cài APK trong `dist/`.
2. Trong LSPosed, bật **Vivo Game Unlock**, chọn scope:
   - **System Framework** (bắt buộc cho backend).
   - **GameCube** — `com.vivo.gamecube`.
   - **GameWatch** — `com.vivo.gamewatch`.
3. Thêm game vào **Chế độ siêu trò chơi → Các trò chơi được hỗ trợ** của vivo. Module tự áp dụng cho các game này.
4. Mở app Vivo Game Unlock để bật/tắt MEMC, VSR và chọn profile. Thay đổi được gửi tự động, không có nút Lưu. Scope nhận broadcast hoặc yêu cầu cấu hình mỗi 15 giây.

Scope System Framework không gắn hook vào `system_server` đang chạy chỉ bằng cách cài APK; cần process framework nạp lại cùng module. Dự án không tự khởi động lại hay dừng process.

**Lần đầu thử 0.1.4:** bản cũ gây văng hàng loạt, nên chuẩn bị cách vô hiệu module trước khi reboot (KernelSU/recovery). Nếu vẫn văng, lấy logcat lọc `VivoGameUnlock` và `AndroidRuntime`.

## Giới hạn thực tế

- Mở nút trên sidebar, gửi yêu cầu hợp lệ và backend dispatch **không chứng minh game đã có nội suy hoặc siêu độ phân giải đầu ra**. Engine native vẫn có thể không tương thích API/render path của game.
- Module không thay thuật toán trong GameLib, không patch `.so`, driver hay HAL.
- Whitelist post-processing VSR được giữ nguyên. Giá trị bật VSR do `VSRConfigParser.getEnabledValue` gốc quyết định; không ép giá trị dương nếu backend trả 0.
- Profile OEM hiện có được ưu tiên. Không tắt giới hạn nhiệt/phần cứng/ghi màn hình/lỗi engine.
- Game không nằm trong danh sách GameCube sẽ không được mở, kể cả khi từng cấu hình bằng tay ở bản cũ.
- Tắt module trong app khôi phục quyết định whitelist gốc sau lần đọc cấu hình tiếp theo. Không cưỡng bức tắt pipeline đang chạy.
- Profile trong bộ nhớ có thể bị cấu hình cloud của OEM cập nhật lại; hook thêm lại khi controller/sidebar kiểm tra lần tiếp theo.
- Chưa có cấu hình hợp lệ từ broadcast, provider hoặc cache thì policy mặc định đóng.
- Bản khác model/SDK/fingerprint sẽ không cài hook. Sai version code GameCube/GameWatch có log `UNSUPPORTED_VERSION`.

## Kiểm tra log

Trong log LSPosed, tìm `[VivoGameUnlock]`:

| Dấu log | Ý nghĩa |
|---|---|
| `LOAD version=0.1.4` | Scope đã nạp code mới, không phải hook cũ trong RAM |
| `HOOK_OK` | Đăng ký được hook; chưa khẳng định hook đã được gọi |
| `HOOK_MISSING`, `HOOK_ERROR` | Điểm hook thiếu hoặc callback lỗi; giữ kết quả gốc |
| `CONFIG_RECEIVER_READY` | Đã đăng ký kênh cấu hình có permission signature |
| `CONFIG_RECEIVED` | Scope nhận được cấu hình |
| `CONFIG_READY ... games=[...]` | Danh sách game được mở hiện tại. Rỗng nghĩa là không game nào được mở |
| `GAMES_SCAN_ERROR`, `GAMES_OBSERVER_ERROR` | Không đọc được danh sách GameCube; giữ danh sách trước đó |
| `MEMC_PROFILE`, `VSR_PROFILE` | Đã tạo profile trong bộ nhớ |
| `SIDEBAR_MEMC_REQUEST` | Đường gốc GameCube đã phát yêu cầu |
| `WATCH_VGPU_REQUEST` | GameWatch đã gọi API VGPU; log có mã trả về |
| `BACKEND_ALLOWED` | Backend chấp nhận package qua whitelist. **Chỉ được xuất hiện với game trong danh sách.** Thấy app khác là lỗi |
| `MEMC_BACKEND_PROFILE` | Đã cấp bảng source/target cho game thiếu profile |
| `NATIVE_DISPATCH` | Framework đã đi qua hàm dispatch; chưa xác nhận engine xử lý thành công |

Xác nhận nội suy thật cần thêm phản hồi engine và phép đo đầu ra.

## Build offline trên máy tính

```powershell
./Game-Module/build.ps1
python Game-Module/tools/verify_targets.py
```

Có thể truyền `-Sdk` và `-JavaHome`. Build dùng SDK 35, build-tools 35.0.1, JDK 17; không tải dependency hay gọi ADB. Xposed API 82 chỉ dùng lúc compile. Keystore phát triển nằm trong `.local/`, không đưa vào Git; giữ file này để ký bản cập nhật bằng cùng khóa.

- `dist/SHA256SUMS`, `dist/build-report.json`: hash và kết quả build.
- `dist/target-verification.json`: đối chiếu khai báo DEX của GameCube/GameWatch/framework.
- `tests/PolicyTest.java`: kiểm tra chính sách (chỉ game trong danh sách được admit, ngưỡng nhiệt).
- `tests/ConfigContractTest.java`: giới hạn đích gửi và kích thước payload.
- `tests/AutoConfigTest.py`: hợp đồng mã nguồn (nguồn danh sách game, Direct Boot). Không nằm trong `build.py`, chạy riêng.
- `research/HOOKS.md`: nguồn bằng chứng và phạm vi hook. Phần nói về danh sách package cấu hình tay và VAA đã lỗi thời.

Kết quả kiểm tra offline không thay cho thử runtime.
