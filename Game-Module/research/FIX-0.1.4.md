# 0.1.4 — admission limited to GameCube supported games

## Report

User installed 0.1.4's predecessor (0.1.3), enabled the module in LSPosed, rebooted: almost every app crashed on launch, repeatedly. Removing the APK and rebooting restored normal operation. Effect of commit 35cd57a (Direct Boot config read) is that the configured policy reaches `system_server` early at boot instead of being unavailable, so the default policy is applied to the whole device.

## Cause

0.1.3 admission was fail-open: `all_apps` defaulted to true, so `allows(pkg)` admitted every user app. The framework hooks then answer the OEM whitelist queries for every package:

- `MEMCController.isMEMCGles/VulkanWhitelistApp`
- `VSRController.isVSRGles/VulkanWhitelistApp`
- `*ConfigParser.support*ForCurrentModel`

Native `com.vivo.game.GameManager` (from `vivo-framework.jar`) calls `isWhitelistAppImpl(100/101/200/201)` in its constructor; an admitted package then `dlopen`s `libVSR.so` / `libVkLayer_VSR.so` from `/system/app/GameLib/GameLib.apk` and installs a Vulkan layer into the app's renderer.

Passive logs captured on device support this:

- `fps144-failure.txt`: `VSR1HookDFManagerVK init success` in kernelsu, Telegram, TikTok.
- `interaction-log.txt`: Maps, Facebook reaching `BACKEND_ALLOWED`.

Those are not game renderers, and the layer is written for a small set of game engines. A crash inside an injected layer crashes the host app. No `AndroidRuntime` tombstone for the failing apps was captured, so the exact faulting instruction is unconfirmed; the failure surface is established, not the crash site.

## Change

Packages are no longer read from module configuration. The only source is the vivo list the user already manages:

`content://com.vivo.gamecube.provider/support_game`, column `package_name`, served by `DaemonProvider` (process `:daemon`, `directBootAware`, signature permission `com.vivo.gamecube.permission.GAME_PROVIDER`; the scope runs as uid system).

- `RuntimeState.scanGames` queries it and keeps a package only when `ApplicationInfo` resolves, is enabled, has uid >= 10000 and no `FLAG_SYSTEM`. The module's own package is dropped.
- A `ContentObserver` on the URI resets the 10 s scan window and reapplies, so toggling a game in GameCube takes effect without reboot.
- Any query failure keeps the previous list; a missing or empty provider admits nothing. Empty policy is the fail-closed default.
- `ACTION_USER_UNLOCKED` also forces a rescan (Direct Boot ordering).
- `Policy` loses `allApps`, `vaa`, `selected`, `excluded`; `allows()` is `enabled && games.contains(pkg)`.
- `ConfigStore` drops `all_apps`/`vaa`/`selected`/`excluded` keys; protocol stays 1.
- UI drops the "all apps" switch and both package text fields. Only the enable switch, MEMC, VSR and the target-FPS profile remain, published on change (no Save button).

Cache write is skipped in package `android`: `system_server` has no data directory, and `getSharedPreferences` there throws `No data directory found for package android`.

## Scope

Hook points and target versions are unchanged. `GameManagerService.isWhitelistApp` itself is still not hooked; only the controller/parser gates are, and they now return true only for games in the vivo list whose native support flag is already on. No `.so`, driver or HAL is patched.

## Verification

- `tests/PolicyTest.java`: 29 checks (admission set, module package excluded, disabled policy admits nothing, thermal hysteresis).
- `tests/ConfigContractTest.java`: 10 checks.
- `tests/AutoConfigTest.py`: asserts the provider URI present, and that `getInstalledApplications`, `all_apps`/`allApps` are gone and the `"android"` cache guard exists.
- `tools/verify_targets.py`: 100/100 DEX declarations.

Offline checks pass. Runtime verification is pending; the first flash of this build needs a module-disable path (KernelSU / recovery) in case a new crash appears, and logcat filtered on `VivoGameUnlock` plus `AndroidRuntime` if it does.

## Limit

A game absent from the vivo supported list is not admitted at all, even if it worked in an earlier build through manual configuration. A game present in the list still depends on the OEM engine accepting its render path; admission is not proof of interpolation or super-resolution output.
