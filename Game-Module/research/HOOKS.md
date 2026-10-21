# Hook map and artifact evidence

These hooks target artifacts acquired from the user's device in this session, not code from a different GameCube release.

| Artifact | SHA-256 | Origin |
|---|---|---|
| GameCube base.apk | 4ca9d9d8401b6cff44635e194150f2410a5de7a5bc584e8bbcef681b5498943f | pull_adb_package, ev_00a420d2a2b2e618c579d900232ed613318b2bee2aab9e870e6150bf7629427b |
| GameWatch.apk | 496d3312bf2e52dde56051deeab503306c76d413ea3d50593285dda50b7282c9 | pull_adb_package, ev_e1fc41563e5c01283322e10731194f9f77057383c736ea9772d5b7c3e00fe3fd |
| vivo-services.jar | See ../dist/target-verification.json | Read-only adb pull before the user's no-device-mutation constraint; local research/vivo-services.jar |

JADX CLI 1.5.6 was used because REA's Android decompiler adapter lacked its caller-supplied JAR. Decompiled sources have recovery errors in some methods. `tools/verify_targets.py` independently parses original DEX class_data method/field declarations, verifies exact parameter/return types and static flags where needed, and does not use decompiler aliases.

## GameCube

- `utils.f0.d0`, `F0`: selected ConfiguredFunction package gates only. Unrelated features unchanged.
- `utils.f0.G0`: sidebar availability for GAME_SUPER_RESOLUTION and FRAME_INTERPOLATION only.
- `utils.f0.k0`: package admission to frame controller.
- `utils.f0.S0`: add missing SR profile before original hardware admission is evaluated.
- `frameinterpolation.h.a1`, `.f0`: add missing frame profile before controller values/FPS lookup.
- `m9.j.s`: missing profile lookup fallback. Existing list profiles take priority.
- `frameinterpolation.k.k`, `.l`: fallback q8.b dimensions (route 3, supportSDK=false), only when original lookup is null.
- `m9.d.l`: generic legacy request with selected source/target and real PID. Existing OEM frame profiles unchanged.
- `superresolution.i.K0`: add missing high-mode SR profile before foreground/background processing.
- `superresolution.m.f`: VSR whitelist query.
- `common.utils.f0.j`: VAA whitelist query; backend hardware admission still applies.
- `Settings.Global.getString` in GameCube processes: virtual union for selected whitelist keys, without persistent Settings writes. SDK tips, post-processing lists and feature/hardware flags are untouched.
- `common.utils.j0.b`: logging only.

Profile objects are constructed using the exact original constructors for `com.vivo.common.frameintersupportlist.entity.a`, `s7.b`, `n8.e`, `q8.b`. Static fields in AssistantUIService are originally named **h** and **i** in DEX; JADX displays collision aliases f17703h and f17704i. Runtime lists remain CopyOnWriteArrayList.

## GameWatch

- `e0.a.o(int,int[])`: audit VGPU commands. It forwards into AbsVivoDisplayEnhanceManager.setDisplayEnhanceConfigureVGpu in the stock implementation.
- `j0.n0.B(int)` accepts indices 3 and 102. Its E method passes request state, index, real PID, requested source/target FPS and the SR selector into the VGPU API. The native framework's MEMCController additionally checks index == 3 when creating policy. This grounds generic route 3.
- The private `j0.n0.z(String)` recognizes Genshin SDK data. It is deliberately **not** widened: interpreting other games as Genshin SDK events would not provide valid game data.

## System Framework

- GameManagerService.systemReady initializes asynchronous configuration reads. Provider/PackageManager work never runs synchronously in frame/whitelist hook callbacks.
- MEMCController / VSRController / VAAController: is{Feature}{Gles,Vulkan}WhitelistApp adds selected packages only if the corresponding SUPPORT_* flag is already true.
- MEMCConfigParser / VSRConfigParser / VAAConfigParser: support{Feature}{Gles,Vulkan}ForCurrentModel extends package membership only where the original global API support function reports support.
- GameManagerService.isWhitelistApp remains original; it calls the above controller gates for client-side capability queries, including callbacks used by native libraries.
- MEMCController.getSdkGameTargetFps fallback uses the active generic sidebar request's declared source rate when no OEM native profile or SDK value exists. It does not manufacture measured FPS or report native success.
- MEMCConfigParser.getMEMCFpsForPackage supplies source/refresh pair only for absent OEM package profiles, ordinary policies 1/2, and eligible packages. Additional profiles use 47/44°C hysteresis; unknown temperature is stopped. Original pipeline error, screen record, security status and supported refresh rate decisions remain in OEM code.
- GameManagerService.dispatchMEMCEnabled / dispatchVSREnabled / dispatchVAAEnabled are logged only; these callbacks are not proof of actual image output.

## Remaining unknowns

Actual engine compatibility, native package-specific checks, successful frame output and visual quality have not been measured. No .so or HAL is patched. VSRConfigParser default mapping and OEM thermal behavior are retained; an original native value of 0 is not forced positive. Post-processing and extreme SR modes are not fabricated.

No runtime claim is made from static DEX checks. No reboot, force-stop, app installation, LSPosed activation, configuration write or root mutation was executed on the phone while producing this module.
