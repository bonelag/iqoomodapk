"""Source contract for automatic configuration; runtime delivery needs device testing."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/io/github/vivogameunlock'
ui = (java / 'MainActivity.java').read_text(encoding='utf-8')
store = (java / 'ConfigStore.java').read_text(encoding='utf-8')
runtime = (java / 'RuntimeState.java').read_text(encoding='utf-8')
policy = (java / 'Policy.java').read_text(encoding='utf-8')
assert 'Lưu cấu hình' not in ui
assert 'setOnItemSelectedListener' in ui
assert 'ConfigStore.preferences(this)' in ui
assert 'prefs.getString("language", "en")' in ui
assert 'new String[]{"English", "Tiếng Việt"}' in ui
assert 'putString("language", which == 1 ? "vi" : "en")' in ui
ET.parse(root / 'app/src/main/res/drawable/ic_language.xml')
assert 'SOFT_INPUT_ADJUST_RESIZE' in ui
assert 'Math.max(bars.bottom, keyboard)' in ui
assert 'focused.requestRectangleOnScreen(area, false)' in ui
assert 'createDeviceProtectedStorageContext()' in store
assert 'createDeviceProtectedStorageContext()' in runtime
assert runtime.index('ctx.registerReceiver(receiver') < runtime.index('cache = ctx.')
assert 'Intent.ACTION_USER_UNLOCKED' in runtime
# Admission comes only from GameCube's supported-games list, never from every installed app.
assert 'content://com.vivo.gamecube.provider/support_game' in runtime
assert 'getInstalledApplications' not in runtime
assert 'all_apps' not in ui + store + runtime
assert 'allApps' not in policy
# system_server (package "android") has no data dir for SharedPreferences.
assert '!"android".equals(ctx.getPackageName())' in runtime
assert 'isUserUnlocked()' in runtime
hooks = (java / 'GameUnlock.java').read_text(encoding='utf-8')
assert '"attach"' not in hooks
# Generic games need the BufferQueue route, not package-specific shader managers.
assert 'backend("sr", "VSR", "VSR", "vsr")' not in hooks
assert '"isVSRPostProcessingWhitelistApp"' in hooks
assert '"supportVSRPostProcessingForCurrentModel"' in hooks
assert '"SUPPORT_VSR_POST_PROCESSING"' in hooks
assert 'hook(controller, "isVSRFailedApp"' not in hooks
assert 'callMethod(controller, "w1"' not in hooks
assert 'callMethod(controller, "v1", ctx, pkg, mode == 1, false)' in hooks
assert '!Double.isFinite(temperature)' in hooks
android = '{http://schemas.android.com/apk/res/android}'
app = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot().find('application')
for name in ('.ConfigProvider', '.ConfigRequestReceiver'):
    component = next(c for c in app if c.get(android + 'name') == name)
    assert component.get(android + 'directBootAware') == 'true', name
print('AutoConfigTest: source contract passed (not runtime delivery)')
