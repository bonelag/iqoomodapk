# 0.1.3 — OEM VAA row and international Genshin VSR

## User verification of 0.1.2

- User confirms a visible VSR graphics change in Free Fire and Liên Quân.
- User reports international Genshin high selection returning to Original, and ineffective AA in all tested games.
- Screenshot shows a black title, purple Android Switch and oversized help text on the dark/yellow GameCube panel. Those were introduced by the module's stock Android controls.

## UI change

Reuse the host layout_qchip_super_frame XML: inflate unattached, detach only cl_vaa, discard the unattached template. This is XML inflation only, not construction of QSuperFrameView/presenter or video playback. Reuse tv_vaa, iv_vaa, VMoveBoolButton and the OEM d0 tint helper. Use the original 17dp inset, compact white OEM title, native help icon and themed switch. Remove the inline technical paragraph; compatibility information lives behind the help icon. Row click submits the command; attach/state refresh never submits commands. Preserve stock VAA handlers for originally supported packages.

## Genshin VSR change

0.1.2 only intercepts l1 for module-created profiles. A stock profile skips that interception. Original l1 has an early G_RenderResolution SDK requirement for y0(package), which includes international Genshin; the original presenter then refreshes to saved state 0 on failure.

0.1.3 explicitly includes com.miHoYo.GenshinImpact in the direct high-mode selection path even when its profile is stock. It calls OEM v1 for VSR only, then the real OEM preference writer and UI/thermal-monitor update on acceptance. It does not fabricate G_RenderResolution, call r1 to reconfigure MEMC, rename the game, change native numeric mapping, or skip hardware/algorithm/temperature checks. Other generic games retain the 0.1.2 w1 path. International Genshin supports selection 0/1 through this path; extreme SDK-dependent mode is not fabricated. This fix needs runtime verification after GameCube reload.

## Native AA evidence and remaining limit

REA opened libVSR.so (SHA256 87e78d9e4c2bcd413fe559ef0ca762e895faa5e16ce517bbcc98124e66db9392). Its deep providers were unavailable on this Windows session; no provider setup/config change was performed. Continued with the installed NDK 29 llvm-objdump and original ELF string bytes.

- Original Vsr_Init starts at 0xcb8b0. After general capability admission, 0xcbae0..0xcbc3c compares package names and selects a manager. Recognized names include international/Chinese Genshin, Chinese PUBG/Honor of Kings, PUBG international, and Star Rail. Free Fire and com.garena.game.kgvn do not match that list.
- If no manager was created, branch 0xcbca8 -> 0xcc708 emits the literal g_VSR1Manager init error!!! (string 0x1ba05), then returns 0 at 0xcc720.
- Passive Free Fire log (PID 12239) shows precisely this error after GameLib loads libVSR.so. This supports failure of that GLES manager initialization, not failure of every VSR backend. User-observed VSR changes can still come from another route.
- Genshin PID 13718 logs CreateSwapchainKHR, Vulkan surfaces and target upscale graphics pipeline discovery. That establishes a Vulkan path for the observed run. Device property sys.game.vaa.vulkan.enable=0; no Vulkan VAA hardware flag is changed.

Therefore, the VAA toggle/dispatch in 0.1.2 cannot establish anti-aliasing effectiveness. This update fixes its presentation and explains compatibility; it does not claim generic native VAA rendering has been implemented. Patching a package comparison to use another game's shader/texture manager would not supply compatible shader signatures. No such speculative native patch or renderer injection is installed.

Local disassembly/log files remain under research/graphics and are excluded from the source bundle. Actual AA quality remains unconfirmed/ineffective per the user's report. Native VAA needs compatible engine support or a separately implemented rendering algorithm; the current Java LSPosed admission hooks cannot create that algorithm.
