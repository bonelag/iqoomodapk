#!/system/bin/sh
# Collect the on-device artifacts the Vivo Game Unlock hooks are written against.
#
#   adb push Game-Module/run.sh /data/local/tmp/
#   adb shell su -c 'sh /data/local/tmp/run.sh'
#
# Read-only on the device. Writes one archive to /sdcard/Download and removes its
# staging folder. Root is required: /system/framework, /system/res and /data/app
# are not readable as the shell user.

set -u
PATH=/system/bin:/system/xbin:/vendor/bin:/data/adb/ksu/bin:/data/adb/magisk:$PATH
export PATH

DEST=/sdcard/Download
STAMP=$(date +%Y%m%d-%H%M%S)
NAME=vgu-resources-$STAMP
WORK=$DEST/$NAME
PACKAGES="com.vivo.gamecube com.vivo.gamewatch com.vivo.gamelib"
MISSING=

say()  { echo "[vgu] $*"; }
warn() { echo "[vgu] WARN: $*" >&2; }
miss() { MISSING="$MISSING $1"; warn "missing: $1"; }

copy() {
  [ -f "$1" ] || { miss "$1"; return 1; }
  cp -f "$1" "$2" 2>/dev/null || cat "$1" > "$2" 2>/dev/null || { rm -f "$2"; miss "$1"; return 1; }
  return 0
}

# put <archive-subdir> <file-name>: prefer known ROM locations, then a shallow search.
put() {
  _src=
  for _d in /system/framework /system_ext/framework /product/framework /odm/framework \
            /system/res/game /system/etc/gamecube /system_ext/etc/gamecube /product/etc/gamecube; do
    [ -f "$_d/$2" ] && { _src="$_d/$2"; break; }
  done
  [ -n "$_src" ] || _src=$(find /system /system_ext /product /odm -maxdepth 4 -name "$2" -type f 2>/dev/null | head -n 1)
  [ -n "$_src" ] || { miss "$2"; return 1; }
  copy "$_src" "$WORK/$1/$2" && say "ok $_src"
}

[ "$(id -u)" = 0 ] || { echo "[vgu] run as root: su -c 'sh $0'" >&2; exit 1; }
[ -d "$DEST" ] || { echo "[vgu] $DEST not available" >&2; exit 1; }

mkdir -p "$WORK/device" "$WORK/apk" "$WORK/framework" "$WORK/config/gamecube" "$WORK/native" || exit 1

_root=unknown
[ -d /data/adb/ksu ] && _root=KernelSU
[ -d /data/adb/magisk ] && _root=Magisk

FP=$(getprop ro.build.fingerprint)
DEV=$(getprop ro.product.device)
MODEL=$(getprop ro.product.model)
REL=$(getprop ro.build.version.release)
SDK=$(getprop ro.build.version.sdk)

getprop > "$WORK/device/getprop.txt" 2>/dev/null
uname -a > "$WORK/device/uname.txt" 2>/dev/null
service list > "$WORK/device/services.txt" 2>/dev/null
settings list global 2>/dev/null | grep -Ei 'memc|vsr|vaa|gamecube|game_mode' > "$WORK/device/settings-game.txt"

{
  echo "collected=$(date)"
  echo "device=$DEV"
  echo "model=$MODEL"
  echo "fingerprint=$FP"
  echo "release=$REL"
  echo "sdk=$SDK"
  echo "build_id=$(getprop ro.build.id)"
  echo "root=$_root"
  echo "display=$(wm size 2>/dev/null | tr -d '\r' | tr '\n' ' ')"
  echo "density=$(wm density 2>/dev/null | tr -d '\r' | tr '\n' ' ')"
} > "$WORK/device/summary.txt"

: > "$WORK/device/packages.txt"
for _p in $PACKAGES; do
  echo "== $_p" >> "$WORK/device/packages.txt"
  dumpsys package "$_p" 2>/dev/null | grep -E 'versionName=|versionCode=|codePath=|lastUpdateTime=' \
    >> "$WORK/device/packages.txt"
done

for _p in $PACKAGES; do
  mkdir -p "$WORK/apk/$_p"
  _paths=$(pm path "$_p" 2>/dev/null | tr -d '\r' | sed 's/^package://')
  [ -n "$_paths" ] || { miss "$_p (pm path)"; continue; }
  _i=0
  for _path in $_paths; do
    case $_path in
      *base.apk) _out="$WORK/apk/$_p/base.apk" ;;
      *) _i=$((_i + 1)); _out="$WORK/apk/$_p/split-$_i.apk" ;;
    esac
    copy "$_path" "$_out" && say "ok $_path"
  done
done

put framework vivo-framework.jar
put framework vivo-services.jar
put framework framework.jar
put framework services.jar

put config vsr_config.xml
put config vaa_config.xml

for _d in /system/etc/gamecube /system_ext/etc/gamecube /product/etc/gamecube; do
  [ -d "$_d" ] || continue
  for _f in "$_d"/*.xml; do
    [ -f "$_f" ] && copy "$_f" "$WORK/config/gamecube/$(basename "$_f")" && say "ok $_f"
  done
done

for _so in libVSR.so libVkLayer_VSR.so libvivogamecore.so; do
  _src=$(find /data/app -maxdepth 6 -path '*com.vivo.gamelib*' -name "$_so" 2>/dev/null | head -n 1)
  [ -n "$_src" ] && copy "$_src" "$WORK/native/$_so" && say "ok $_src"
done
if [ -z "$(ls -A "$WORK/native" 2>/dev/null)" ] && command -v unzip >/dev/null 2>&1; then
  unzip -o -j "$WORK/apk/com.vivo.gamelib/base.apk" 'lib/arm64-v8a/*.so' -d "$WORK/native" >/dev/null 2>&1 \
    && say "ok GameLib native libs via unzip"
fi
for _so in libgamemanager_aidl.so libaimemc.so; do
  _src=$(find /system/lib64 /system_ext/lib64 /vendor/lib64 -maxdepth 1 -name "$_so" 2>/dev/null | head -n 1)
  [ -n "$_src" ] && copy "$_src" "$WORK/native/$_so" && say "ok $_src"
done

cat > "$WORK/README.md" <<EOF
# Vivo Game Unlock - tài nguyên thu từ thiết bị

- Thu lúc: $STAMP
- Thiết bị: $MODEL ($DEV), Android $REL (SDK $SDK)
- Fingerprint: $FP
- Root: $_root
- Nguồn: chỉ đọc từ máy; không sửa file hệ thống nào.

Đây là bộ đầu vào để đọc/viết các hook trong Game-Module. Mọi file đều là bản sao
nguyên trạng từ thiết bị, có SHA256SUMS đi kèm để đối chiếu.

## Nội dung

    apk/com.vivo.gamecube/        GameCube: sidebar, cổng bật/tắt, profile game, phát lệnh
    apk/com.vivo.gamewatch/       GameWatch: nhận dữ liệu SDK, API VGPU, gọi backend
    apk/com.vivo.gamelib/         Thư viện native: libVSR.so, libVkLayer_VSR.so, libvivogamecore.so
    framework/vivo-framework.jar  Phần mở rộng framework của vivo
    framework/vivo-services.jar   Backend trong system_server: MEMC/VSR/VAA, whitelist, nhiệt độ
    framework/framework.jar       framework.jar của AOSP (chỉ cần khi hook internals của Android)
    framework/services.jar        services.jar của AOSP (chỉ cần khi hook internals của Android)
    config/vsr_config.xml         Bảng tham số VSR theo model (ví dụ Genshin 0/646/450)
    config/vaa_config.xml         Khai báo VAA native theo package/model
    config/gamecube/*.xml         Danh sách profile OEM: FPS, siêu phân giải, frameInter
    native/*.so                   Thư viện native tương ứng
    device/getprop.txt            Toàn bộ property, gồm sys.game.* quyết định khả năng VAA/VSR
    device/packages.txt           versionName/versionCode/codePath của 3 package
    device/services.txt           Binder service đang chạy
    device/settings-game.txt      Settings toàn cục có khoá memc/vsr/vaa/game
    device/summary.txt            Tóm tắt máy

## File nào trả lời câu hỏi nào

- Package/model nào được chấp nhận: config/vsr_config.xml, config/vaa_config.xml
- Profile FPS/SR gốc của từng game: config/gamecube/*.xml
- Hàm Java nào quyết định admission: apk/com.vivo.gamecube + framework/vivo-services.jar
- Giá trị số mà lớp native mong đợi: config/vsr_config.xml + native/libVSR.so
- ROM có hỗ trợ VAA trên đường render hiện tại: device/getprop.txt (sys.game.vaa.*)

## Dùng lại trên máy tính

    framework/*.jar   -> Game-Module/research/vivo-services.jar, vivo-framework.jar
    apk/<package>/    -> investigation_game_20261021/apks/<package>/
    config/*.xml      -> Game-Module/research/graphics/

Sau đó chạy Game-Module/build.ps1 và python Game-Module/tools/verify_targets.py.

## Giới hạn

Bộ file này đủ để đọc đúng cấu trúc và viết hook khớp version, nhưng không tự bảo
đảm module chạy đúng 100%: hook chỉ nạp khi device/fingerprint/versionCode khớp,
và khử răng cưa native còn phụ thuộc ROM. Máy này báo sys.game.vaa.vulkan.enable=0
nên VAA không có đường render trên Vulkan. Xác nhận cuối cùng vẫn là log LSPosed
và phép đo khung hình thật.
EOF

if command -v sha256sum >/dev/null 2>&1; then
  ( cd "$WORK" && find . -type f ! -name SHA256SUMS -exec sha256sum {} + > SHA256SUMS )
elif command -v md5sum >/dev/null 2>&1; then
  ( cd "$WORK" && find . -type f ! -name MD5SUMS -exec md5sum {} + > MD5SUMS )
  warn "sha256sum unavailable, wrote MD5SUMS"
else
  warn "no checksum tool available"
fi

for _need in apk/com.vivo.gamecube/base.apk apk/com.vivo.gamewatch/base.apk \
             apk/com.vivo.gamelib/base.apk framework/vivo-services.jar \
             framework/vivo-framework.jar config/vsr_config.xml; do
  [ -f "$WORK/$_need" ] || MISSING="$MISSING $_need"
done

cd "$DEST" || exit 1
if command -v zip >/dev/null 2>&1; then
  zip -qr "$NAME.zip" "$NAME" && OUT=$DEST/$NAME.zip
elif command -v tar >/dev/null 2>&1; then
  tar -czf "$NAME.tar.gz" "$NAME" && OUT=$DEST/$NAME.tar.gz
  warn "zip not available on this ROM; wrote tar.gz instead"
else
  OUT=$WORK
  warn "neither zip nor tar available; folder left in place"
fi

[ "$OUT" = "$WORK" ] || rm -rf "$WORK"

say "archive: $OUT ($(du -h "$OUT" 2>/dev/null | cut -f1))"
[ -z "$MISSING" ] || say "MISSING:$MISSING"
say "pull to PC: adb pull $OUT"
