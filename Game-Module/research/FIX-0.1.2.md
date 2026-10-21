# 0.1.2 — VAA control and generic VSR selection

Target identity and original APK digests remain those in HOOKS.md. No OEM APK patch.

## Evidence and diagnosis

- User reports MEMC working in Free Fire, Master Duel and Liên Quân. The module keeps the 0.1.1 MEMC hooks unchanged.
- Original GameCube `CommonSuperResolutionFrameView.c(Context)` builds different panels. Type 1 contains QSuperResolutionView, type 4 a legacy frame view; neither has a VAA row. Whitelist hooks alone cannot add a missing view.
- Original QSuperFrameView and SuperResolutionView VAA handlers require PUBG's in-game AA SDK setting. That is not a usable generic control for unrelated games.
- Read-only device properties: `sys.game.vaa.gles.enable=1`, `sys.game.vaa.vivo.enable=1`, `sys.game.vaa.vulkan.enable=0`. No Vulkan VAA capability is forced.
- Original `superresolution.i.l1(1)` uses L0 -> asynchronous AISR query -> conflict/update dialogue. Preference/command changes occur later. This provides several ways for a radio selection to return to 0; runtime evidence has not established which branch affected the user's games.

## Changes

- Append a VAA switch to existing graphics panels for eligible non-stock VAA games. Hide the duplicated SDK-specific row if present. Read original admission with virtual whitelist extension suspended on the current thread, so the module does not mistake its own whitelist result for stock support.
- The switch calls OEM `i.t1(value,false)` to send the real native request, then OEM preference writer `f0.m(context,realPackage,value)`. Attach/initial refresh never sends a command. Captured package must still be foreground at click time. Preserve the algorithm update gate. OEM manager v2 continues restoring saved VAA values across foreground/background events where its hardware gate permits.
- A saved VAA switch is requested state, not proof of native engine success. Current firmware supports the GLES route only.
- For module-created VSR profiles only, bind an `o` strategy to that profile; restore the previous strategy when returning to stock games. This avoids retaining another game's profile/thresholds.
- Generic VSR selection calls OEM `w1(context,realPackage,enabled,true)` directly rather than the AISR/SDK conflict dialogue. Preserve hardware, foreground, algorithm update and thermal gates; reject unavailable temperatures when enabling. Support modes 0 and 1 only. Set controller activity state and start/stop OEM thermal monitoring through J1. Persist through the OEM writer, and log saved state separately from command acceptance.
- Add VAA_SELECTION, VAA_REQUEST, VSR_SELECTION, VSR_COMMAND and VAA_UI_ADDED logs. Existing system-side NATIVE_DISPATCH hooks remain unchanged so the already-running 0.1.1 system_server does not need a restart.

## Validation and limits

Build checks compile Android code, run existing 40 policy/config assertions, verify APK signature/alignment and 12 package checks, and validate 96 method/field declarations against original DEX. This is not a live UI or GPU test.

After installation, GameCube processes already running retain old hook code. User must reload GameCube/game as previously done, then confirm `VAA_UI_ADDED` / `VSR_SELECTION` in logs. Agent must not reboot or force-stop anything. Only changing a setting or seeing NATIVE_DISPATCH does not prove visual processing succeeded; correlate OEM/native logs and in-game output.

Read-only captures and configuration XML under research/graphics are local diagnostic evidence, excluded from source bundles. Original VSR default native mapping is retained (`0->0`, `1->7`, `2->322`); no game identity, rendering-resolution SDK event, native success, blacklist or Vulkan capability is fabricated.
