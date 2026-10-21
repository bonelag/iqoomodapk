"""Source contract for automatic configuration; runtime delivery needs device testing."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/io/github/vivogameunlock'
ui = (java / 'MainActivity.java').read_text(encoding='utf-8')
store = (java / 'ConfigStore.java').read_text(encoding='utf-8')
runtime = (java / 'RuntimeState.java').read_text(encoding='utf-8')
assert 'Lưu cấu hình' not in ui
assert 'setOnItemSelectedListener' in ui
assert 'watch(selected, "selected")' in ui and 'watch(excluded, "excluded")' in ui
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
android = '{http://schemas.android.com/apk/res/android}'
app = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot().find('application')
for name in ('.ConfigProvider', '.ConfigRequestReceiver'):
    component = next(c for c in app if c.get(android + 'name') == name)
    assert component.get(android + 'directBootAware') == 'true', name
print('AutoConfigTest: source contract passed (not runtime delivery)')
