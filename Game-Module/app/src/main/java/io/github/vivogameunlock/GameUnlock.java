package io.github.vivogameunlock;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import java.lang.reflect.Method;
import java.util.*;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Version-specific hooks for the artifacts documented in research/HOOKS.md. */
public final class GameUnlock implements IXposedHookLoadPackage {
    private static final String TARGET_FINGERPRINT = "vivo/PD2507/PD2507:15/AP3A.240905.015.A2_V000L1/compiler251024223111:user/release-keys";
    private final RuntimeState state = new RuntimeState();
    private final Set<String> logged = Collections.synchronizedSet(new HashSet<>());
    private final Set<Object> syntheticResolution = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<Object> syntheticFrames = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Map<String, Integer> frameTargets = new HashMap<>();
    private final Set<String> genericOverheat = Collections.synchronizedSet(new HashSet<>());
    private final Map<Object, Object> resolutionStrategies = Collections.synchronizedMap(new WeakHashMap<>());
    private final ClassLoader loader;
    private final String process;

    public GameUnlock() { this(null, "module-entry"); }
    private GameUnlock(ClassLoader loader, String process) {
        this.loader = loader;
        this.process = process;
    }

    interface Action { void run(XC_MethodHook.MethodHookParam p) throws Throwable; }

    static void log(String message) {
        try { XposedBridge.log("[VivoGameUnlock] " + message); } catch (Throwable ignored) { }
    }
    private void once(String key, String message) { if (logged.size() < 2048 && logged.add(key)) log(process + " " + message); }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        // Xposed reuses the entry object for unrelated package callbacks. Never replace an
        // installed hook's class loader with a loader from a later handleLoadPackage call.
        if (!"android".equals(lp.packageName) && !"system".equals(lp.packageName)
                && !"com.vivo.gamecube".equals(lp.packageName) && !"com.vivo.gamewatch".equals(lp.packageName)) return;
        new GameUnlock(lp.classLoader, lp.processName).install(lp);
    }

    private void install(XC_LoadPackage.LoadPackageParam lp) {
        if (!"PD2507".equals(Build.DEVICE) || Build.VERSION.SDK_INT != 35) return;
        if (!TARGET_FINGERPRINT.equals(Build.FINGERPRINT)) {
            once("rom", "UNSUPPORTED_ROM " + Build.FINGERPRINT); return;
        }
        log("LOAD version=0.1.6 package=" + lp.packageName + " process=" + process);
        if (("android".equals(lp.packageName) || "system".equals(lp.packageName))
                && ("android".equals(process) || "system".equals(process) || "system_server".equals(process))) {
            systemHooks();
        } else if ("com.vivo.gamecube".equals(lp.packageName) || "com.vivo.gamewatch".equals(lp.packageName)) {
            // Registered for every GameCube process including :daemon (the always-on provider host),
            // so the game bridge stays reachable when the UI process is killed.
            hook(Application.class, "onCreate", new Object[]{}, null, p -> {
                Context ctx = (Context)p.thisObject;
                if (!lp.packageName.equals(ctx.getPackageName())) return;
                long code = ctx.getPackageManager().getPackageInfo(lp.packageName, 0).getLongVersionCode();
                long expected = "com.vivo.gamecube".equals(lp.packageName) ? 1401000 : 250003;
                if (code != expected) { once("version", "UNSUPPORTED_VERSION " + code); return; }
                GameUnlock appSession = new GameUnlock(ctx.getClassLoader(), process);
                if ("com.vivo.gamecube:daemon".equals(process)) { appSession.state.start(ctx); return; }
                if ("com.vivo.gamecube".equals(lp.packageName)) appSession.cubeHooks(); else appSession.watchHooks();
                appSession.state.start(ctx);
            });
        }
    }

    private void hook(String type, String method, Object[] args, Action before, Action after) {
        try { hook(XposedHelpers.findClass(type, loader), method, args, before, after); }
        catch (Throwable t) { once(type + "." + method, "HOOK_MISSING " + type + "." + method + " " + t); }
    }
    private void hook(Class<?> type, String method, Object[] args, Action before, Action after) {
        try {
            Object[] signature = Arrays.copyOf(args, args.length + 1);
            signature[args.length] = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (before != null) guarded(type.getName() + "." + method, before, p);
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (after != null && !p.hasThrowable()) guarded(type.getName() + "." + method, after, p);
                }
            };
            XposedHelpers.findAndHookMethod(type, method, signature);
            log(process + " HOOK_OK " + type.getName() + "." + method);
        } catch (Throwable t) { once(type.getName() + "." + method, "HOOK_MISSING " + type.getName() + "." + method + " " + t); }
    }
    private void guarded(String name, Action action, XC_MethodHook.MethodHookParam p) {
        Object original = p.getResult();
        try { action.run(p); }
        catch (Throwable t) {
            // Callbacks never throw into OEM processes, especially system_server.
            if (!p.hasThrowable() && p.getResult() != original) p.setResult(original);
            once("error:" + name, "HOOK_ERROR " + name + " " + t);
        }
    }
    private boolean allowed(String pkg, String feature) {
        Policy cfg = state.policy(pkg);
        return cfg.allows(pkg) && ("memc".equals(feature) ? cfg.memc : "vsr".equals(feature) && cfg.vsr);
    }
    private String foreground() {
        try {
            Object controller = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("com.vivo.gameassistant.o", loader), "V");
            return (String)XposedHelpers.callMethod(controller, "O0");
        } catch (Throwable ignored) { return null; }
    }

    private void cubeHooks() {
        Class<?> function = XposedHelpers.findClass("com.vivo.common.supportlist.pojo.ConfiguredFunction", loader);
        String util = "com.vivo.gameassistant.utils.f0";
        for (String method : new String[]{"d0", "F0"}) {
            hook(util, method, new Object[]{function, String.class}, null, p -> {
                if (Boolean.TRUE.equals(p.getResult()) || p.args[0] == null) return;
                Policy cfg = state.policy((String)p.args[1]);
                if (cfg.allows((String)p.args[1]) && cfg.feature(((Enum<?>)p.args[0]).name())) p.setResult(true);
            });
        }
        // G0 is the quick-switch availability gate, with different names from ConfiguredFunction.
        hook(util, "G0", new Object[]{"com.vivo.gameassistant.homegui.sideslide.controlcenter.switches.model.QuickSwitchItemType", String.class}, null, p -> {
            if (p.args[0] == null) return;
            String name = ((Enum<?>)p.args[0]).name();
            if (("GAME_SUPER_RESOLUTION".equals(name) && allowed((String)p.args[1], "vsr"))
                    || ("FRAME_INTERPOLATION".equals(name) && allowed((String)p.args[1], "memc"))) p.setResult(true);
        });
        hook(util, "k0", new Object[]{String.class}, null, p -> {
            if (allowed((String)p.args[0], "memc")) p.setResult(true);
        });
        hook("com.vivo.gameassistant.frameinterpolation.h", "a1", new Object[]{}, p -> {
            ensureFrameProfile(foreground());
        }, null);
        hook("com.vivo.gameassistant.frameinterpolation.h", "f0", new Object[]{
                "com.vivo.gameassistant.frameinterpolation.FrameInterpolationState", String.class}, p -> {
            ensureFrameProfile((String)p.args[1]);
        }, p -> {
            if ((Integer)p.getResult() > 0 || p.args[0] == null || !allowed((String)p.args[1], "memc")) return;
            String mode = ((Enum<?>)p.args[0]).name();
            if ("BOOST_FRAME".equals(mode)) p.setResult(state.policy((String)p.args[1]).targetFps);
            else if ("OPTIMIZE_POWER_CONSUMPTION".equals(mode)) p.setResult(60);
        });
        hook("com.vivo.gameassistant.frameinterpolation.h", "J0", new Object[]{}, p -> {
            if (allowed(foreground(), "memc")) XposedHelpers.setIntField(p.thisObject, "F", 0);
        }, null);
        hook("l9.b", "g", new Object[]{}, null, p -> {
            if (Boolean.TRUE.equals(p.getResult()) || !allowed(foreground(), "memc")) return;
            XposedHelpers.callMethod(p.thisObject, "l", -1);
            if ((Integer)XposedHelpers.callMethod(p.thisObject, "c") > 0) p.setResult(true);
        });
        hook("l9.b", "f", new Object[]{boolean.class}, null, p -> {
            if (!allowed(foreground(), "memc")) return;
            XposedHelpers.callMethod(p.thisObject, "l", -1);
            int fps = (Integer)XposedHelpers.callMethod(p.thisObject, "c");
            if (fps > 0) {
                Float value = (float)fps;
                XposedHelpers.setObjectField(p.thisObject, "e", value);
                p.setResult(value);
            }
        });
        hook(util, "S0", new Object[]{Context.class, String.class}, p -> {
            ensureResolutionProfile((String)p.args[1]);
        }, null);
        hook("com.vivo.gameassistant.superresolution.i", "K0", new Object[]{String.class, boolean.class}, p -> {
            ensureResolutionProfile((String)p.args[0]);
            bindResolutionStrategy(p.thisObject, (String)p.args[0]);
        }, null);
        graphicsHooks();
        hook("com.vivo.gameassistant.superresolution.m", "f", new Object[]{String.class}, null, p -> {
            if (allowed((String)p.args[0], "vsr")) p.setResult(true);
        });
        hook("com.vivo.common.utils.f0", "j", new Object[]{Context.class, String.class}, null, p -> p.setResult(false));

        // Virtual read results only: never overwrite system whitelist Settings.
        hook(Settings.Global.class, "getString", new Object[]{ContentResolver.class, String.class}, null, p -> {
            String key = (String)p.args[1];
            if (!state.policy().enabled || key == null || state.packages().isEmpty()) return;
            String additions = VSR_KEYS.contains(key) ? state.packages("vsr")
                    : MEMC_KEYS.contains(key) ? state.packages("memc") : "";
            if (!additions.isEmpty()) {
                String original = (String)p.getResult();
                p.setResult(original == null || original.isEmpty() ? additions : original + ";" + additions);
            }
        });
        hook("m9.j", "s", new Object[]{String.class}, null, p -> {
            String pkg = (String)p.args[0]; if (pkg == null || pkg.isEmpty()) pkg = foreground();
            if (p.getResult() == null && allowed(pkg, "memc")) p.setResult(frameProfile(pkg));
        });
        // Both legacy and V3 dimensions get real package/PID; GPU route 3 is required by this ROM.
        for (String method : new String[]{"k", "l"}) {
            hook("com.vivo.gameassistant.frameinterpolation.k", method, new Object[]{String.class}, null, p -> {
                String pkg = (String)p.args[0];
                if (p.getResult() == null && allowed(pkg, "memc")) {
                    Object dimension = XposedHelpers.newInstance(XposedHelpers.findClass("q8.b", loader));
                    XposedHelpers.callMethod(dimension, "l", Policy.GPU_ROUTE);
                    XposedHelpers.callMethod(dimension, "m", false);
                    p.setResult(dimension);
                }
            });
        }
        // The legacy strategy otherwise hardcodes half/target=60/120 for every generic game.
        hook("m9.d", "l", new Object[]{int.class, int.class, int.class, int.class,
                "com.vivo.gameassistant.frameinterpolation.FrameInterpolationState"}, p -> {
            String pkg = foreground();
            if (!allowed(pkg, "memc") || hasStockFrameProfile(pkg)) return;
            String mode = ((Enum<?>)p.args[4]).name();
            if ((!"OPTIMIZE_POWER_CONSUMPTION".equals(mode) && !"BOOST_FRAME".equals(mode)) || (Integer)p.args[2] <= 0) return;
            int target = "OPTIMIZE_POWER_CONSUMPTION".equals(mode) ? 60 : state.policy(pkg).targetFps;
            Object helper = XposedHelpers.callStaticMethod(XposedHelpers.findClass(
                    "com.vivo.gameassistant.frameinterpolation.i", loader), "e");
            XposedHelpers.callMethod(helper, "h", Policy.GPU_ROUTE, (Integer)p.args[2], target / 2, target);
            once("generic:" + pkg, "MEMC_REQUEST pkg=" + pkg + " pid=" + p.args[2] + " " + target/2 + "->" + target);
            p.setResult(null);
        }, null);
        // Audit only; no request or success result is fabricated here.
        hook("com.vivo.common.utils.j0", "b", new Object[]{Context.class, boolean.class, int.class, int.class, int.class, int.class}, null, p -> {
            once("request:" + Arrays.toString(Arrays.copyOfRange(p.args, 1, p.args.length)),
                    "SIDEBAR_MEMC_REQUEST " + Arrays.toString(Arrays.copyOfRange(p.args, 1, p.args.length)));
        });
    }

    private Object preferences() {
        return XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.vivo.common.utils.f0", loader), "h");
    }

    @SuppressWarnings("unchecked")
    private Object genericResolution(String pkg) {
        if (!allowed(pkg, "vsr")) return null;
        List<Object> list = (List<Object>)XposedHelpers.getStaticObjectField(XposedHelpers.findClass(
                "com.vivo.gameassistant.AssistantUIService", loader), "i");
        if (list != null) for (Object entry : list)
            if (pkg.equals(XposedHelpers.callMethod(entry, "p")))
                return syntheticResolution.contains(entry) ? entry : null;
        return null;
    }

    private void bindResolutionStrategy(Object controller, String pkg) {
        Object record = genericResolution(pkg);
        Object saved = resolutionStrategies.remove(controller);
        if (saved != null) XposedHelpers.setObjectField(controller, "z", saved);
        if (record == null) return;
        Object original = XposedHelpers.getObjectField(controller, "z");
        Object strategy = XposedHelpers.newInstance(XposedHelpers.findClass(
                "com.vivo.gameassistant.superresolution.o", loader));
        XposedHelpers.callMethod(strategy, "p", record);
        resolutionStrategies.put(controller, original);
        XposedHelpers.setObjectField(controller, "z", strategy);
    }

    private boolean algorithmBlocked(Context ctx, String pkg, String feature) {
        return Boolean.TRUE.equals(XposedHelpers.callStaticMethod(XposedHelpers.findClass(
                "com.vivo.common.algorithem.b", loader), "f", ctx, pkg, feature));
    }

    private boolean directResolution(String pkg) {
        return genericResolution(pkg) != null || (allowed(pkg, "vsr") && "com.miHoYo.GenshinImpact".equals(pkg));
    }

    private void graphicsHooks() {
        String sr = "com.vivo.gameassistant.superresolution.i";
        hook(sr, "l1", new Object[]{int.class}, p -> {
            Object controller = p.thisObject;
            Context ctx = (Context)XposedHelpers.getObjectField(controller, "a");
            String pkg = foreground();
            if (pkg == null || !allowed(pkg, "vsr")) return;

            ensureResolutionProfile(pkg);
            if (!directResolution(pkg)) return;
            bindResolutionStrategy(controller, pkg);

            int mode = (Integer)p.args[0];
            int previous = (Integer)XposedHelpers.callMethod(preferences(), "e", ctx, pkg);

            String reason = null;
            if (!pkg.equals(XposedHelpers.getObjectField(controller, "k"))) reason = "foreground_mismatch";
            else if (mode != 0 && mode != 1) reason = "unsupported_mode";
            else if (algorithmBlocked(ctx, pkg, "vsr_controlled")) reason = "algorithm_pending";
            else if (!Boolean.TRUE.equals(XposedHelpers.callStaticMethod(XposedHelpers.findClass(
                    "com.vivo.gameassistant.utils.f0", loader), "S0", ctx, pkg))) reason = "hardware_gate";
            else {
                XposedHelpers.callMethod(controller, "P0");
                XposedHelpers.callMethod(controller, "O0");
                boolean hot = Boolean.TRUE.equals(XposedHelpers.callMethod(controller, "a1", mode));
                double temperature = XposedHelpers.getDoubleField(controller, "C");
                if (mode > 0 && (hot || !Double.isFinite(temperature) || temperature <= 0)) reason = "thermal_gate";
            }

            boolean success = false;
            if (reason == null) {
                // w1 also sends an interpolation request; keep VSR independent of MEMC.
                success = Boolean.TRUE.equals(XposedHelpers.callMethod(controller, "v1", ctx, pkg, mode == 1, false));
                if (success) {
                    XposedHelpers.callMethod(preferences(), "l", ctx, pkg, mode);
                    XposedHelpers.setBooleanField(controller, "b", mode > 0);
                    XposedHelpers.setBooleanField(controller, "l", false);
                    XposedHelpers.callMethod(controller, "J1", mode, pkg);
                    XposedHelpers.callMethod(controller, "n1", mode);
                } else {
                    reason = "command_rejected";
                }
            }

            int stored = (Integer)XposedHelpers.callMethod(preferences(), "e", ctx, pkg);
            log(process + " VSR_SELECTION pkg=" + pkg + " mode=" + mode + " previous=" + previous
                    + " accepted=" + success + " stored=" + stored + " reason=" + reason
                    + " setting=" + Settings.Global.getInt(ctx.getContentResolver(), "vsr_value_from_gamecube", -1));

            if (!success) {
                Object panel = XposedHelpers.getObjectField(controller, "q");
                if (panel != null) XposedHelpers.callMethod(panel, "g", stored);
                android.widget.Toast.makeText(ctx, "VSR: chưa gửi được lệnh (" + reason + ")", android.widget.Toast.LENGTH_LONG).show();
            }
            p.setResult(success);
        }, null);
        hook("com.vivo.gameassistant.homegui.sideslide.panels.qchip.CommonSuperResolutionFrameView", "c",
                new Object[]{Context.class}, null, p -> hideVaaControl((android.view.View)p.thisObject));
        hook(sr, "v1", new Object[]{Context.class, String.class, boolean.class, boolean.class}, null, p -> {
            if (directResolution((String)p.args[1]))
                log(process + " VSR_COMMAND pkg=" + p.args[1] + " enabled=" + p.args[2] + " accepted=" + p.getResult());
        });
    }

    private void hideVaaControl(android.view.View panel) throws Throwable {
        int rowId = panel.getResources().getIdentifier("cl_vaa", "id", panel.getContext().getPackageName());
        android.view.View row = rowId == 0 ? null : panel.findViewById(rowId);
        if (row != null) row.setVisibility(android.view.View.GONE);
    }

    private static final Set<String> MEMC_KEYS = new HashSet<>(Arrays.asList(
            "gpu_memc_whitelist_apps", "gpu_memc_gles_whitelist_apps", "gpu_memc_vulkan_whitelist_apps",
            "gpu_memc_dynamic_whitelist_apps", "gpu_memc_gles_dynamic_whitelist_apps", "gpu_memc_vulkan_dynamic_whitelist_apps"));
    private static final Set<String> VSR_KEYS = new HashSet<>(Arrays.asList(
            "vsr_whitelist_apps", "vsr_all_whitelist_apps", "vsr_gles_whitelist_apps", "vsr_vulkan_whitelist_apps"));
    private Object frameProfile(String pkg) {
        int target = state.policy(pkg).targetFps;
        List<Object> boost = Collections.singletonList(XposedHelpers.newInstance(
                XposedHelpers.findClass("s7.b", loader), target == 90 ? 60 : target/2, target/2, target));
        List<Object> power = Collections.singletonList(XposedHelpers.newInstance(
                XposedHelpers.findClass("s7.b", loader), 60, 30, 60));
        Map<Integer, Map<String, String>> tips = new HashMap<>();
        for (int fps : new int[]{30, 60, 90, 120}) {
            Map<String, String> tip = new HashMap<>(); tip.put("tipStringName", ""); tip.put("tipString", fps + " FPS");
            tips.put(fps, tip);
        }
        return XposedHelpers.newInstance(XposedHelpers.findClass(
                "com.vivo.common.frameintersupportlist.entity.a", loader),
                pkg, pkg, "#", Policy.GPU_ROUTE, false, boost, power, tips, 47.0d, 44.0d, 0, 0);
    }

    private boolean hasStockFrameProfile(String pkg) {
        // Ask the unhooked lookup; do not guess from gameIndex or spoof game identity.
        try {
            Class<?> type = XposedHelpers.findClass("m9.j", loader);
            Method method = XposedHelpers.findMethodExact(type, "s", String.class);
            Object result = XposedBridge.invokeOriginalMethod(method, XposedHelpers.newInstance(type), new Object[]{pkg});
            return result != null && !syntheticFrames.contains(result);
        } catch (Throwable ignored) { return true; } // Unknown => preserve stock behavior.
    }

    @SuppressWarnings("unchecked")
    private synchronized void ensureFrameProfile(String pkg) {
        Class<?> service = XposedHelpers.findClass("com.vivo.gameassistant.AssistantUIService", loader);
        List<Object> original = (List<Object>)XposedHelpers.getStaticObjectField(service, "h");
        List<Object> list = original == null ? new ArrayList<>() : new ArrayList<>(original);
        int target = state.policy(pkg).targetFps;
        list.removeIf(item -> {
            if (!syntheticFrames.contains(item)) return false;
            String name = (String)XposedHelpers.callMethod(item, "k");
            return !allowed(name, "memc") || !Integer.valueOf(state.policy(name).targetFps).equals(frameTargets.get(name));
        });
        if (allowed(pkg, "memc")) {
            boolean present = false;
            for (Object entry : list) if (pkg.equals(XposedHelpers.callMethod(entry, "k"))) { present = true; break; }
            if (!present) {
                Object entry = frameProfile(pkg);
                list.add(entry); syntheticFrames.add(entry); frameTargets.put(pkg, target);
                once("frameprofile:" + pkg, "MEMC_PROFILE pkg=" + pkg + " GPU route=3 source=" + target/2 + " target=" + target);
            }
        }
        if (original == null || !list.equals(original)) XposedHelpers.setStaticObjectField(service, "h",
                new java.util.concurrent.CopyOnWriteArrayList<>(list));
    }

    @SuppressWarnings("unchecked")
    private synchronized void ensureResolutionProfile(String pkg) {
        Class<?> service = XposedHelpers.findClass("com.vivo.gameassistant.AssistantUIService", loader);
        // 'i' is the original DEX field name; JADX displays it as f17704i.
        List<Object> original = (List<Object>)XposedHelpers.getStaticObjectField(service, "i");
        List<Object> list = original == null ? new ArrayList<>() : new ArrayList<>(original);
        list.removeIf(item -> syntheticResolution.contains(item)
                && !allowed((String)XposedHelpers.callMethod(item, "p"), "vsr"));
        if (allowed(pkg, "vsr")) {
            boolean present = false;
            for (Object entry : list) if (pkg.equals(XposedHelpers.callMethod(entry, "p"))) { present = true; break; }
            if (!present) {
                Object entry = XposedHelpers.newInstance(XposedHelpers.findClass("n8.e", loader),
                        pkg, pkg, "#", 1, 30, 60, 0, 0, 47.0d, 44.0d, 47.0d, 44.0d,
                        Policy.GPU_ROUTE, 0, 0, 0, "");
                list.add(entry); syntheticResolution.add(entry);
                once("vsrprofile:" + pkg, "VSR_PROFILE pkg=" + pkg + " high-mode only");
            }
        }
        if (original == null || !list.equals(original)) XposedHelpers.setStaticObjectField(service, "i",
                new java.util.concurrent.CopyOnWriteArrayList<>(list));
    }

    private void watchHooks() {
        // z(String) controls Genshin SDK interpretation, not general admission. Keep it stock.
        hook("e0.a", "o", new Object[]{int.class, int[].class}, null, p -> {
            int[] values = (int[])p.args[1];
            if ((Integer)p.args[0] == 258 && values != null)
                once("vgpu:" + Arrays.toString(values), "WATCH_VGPU_REQUEST " + Arrays.toString(values)
                        + " apiReturn=" + p.getResult());
        });
    }

    private void systemHooks() {
        String service = "com.vivo.services.game.GameManagerService";
        // systemReady runs after provider services are available; all polling is asynchronous.
        hook(service, "systemReady", new Object[]{}, null, p -> {
            state.start((Context)XposedHelpers.getObjectField(p.thisObject, "mContext"));
        });
        backend("memc", "MEMC", "MEMC", "memc");
        genericMemcBackend();
        genericVsrBackend();
        auditDispatch(service, "dispatchMEMCEnabled", "memc");
        auditDispatch(service, "dispatchVSREnabled", "vsr");
    }

    private void genericVsrBackend() {
        String controller = "com.vivo.services.game.sr.VSRController";
        hook(controller, "isVSRPostProcessingWhitelistApp", new Object[]{String.class}, null, p -> {
            if (allowed((String)p.args[0], "vsr")
                    && XposedHelpers.getStaticBooleanField(p.thisObject.getClass(), "SUPPORT_VSR_POST_PROCESSING"))
                p.setResult(true);
        });
        hook("com.vivo.services.game.sr.VSRConfigParser", "supportVSRPostProcessingForCurrentModel",
                new Object[]{String.class}, null, p -> {
            if (allowed((String)p.args[0], "vsr") && Boolean.TRUE.equals(XposedHelpers.callStaticMethod(
                    p.method.getDeclaringClass(), "supportVSRPostProcessing"))) p.setResult(true);
        });
    }

    private void genericMemcBackend() {
        String controller = "com.vivo.services.game.memc.MEMCController";
        // For unknown games no vendor SDK event arrives. Use only the real sidebar request's
        // declared source rate, not an invented measurement or a spoofed Genshin SDK event.
        hook(controller, "getSdkGameTargetFps", new Object[]{String.class, int.class}, null, p -> {
            String pkg = (String)p.args[0];
            if ((Integer)p.getResult() > 0 || !allowed(pkg, "memc")) return;
            String active = (String)XposedHelpers.getObjectField(p.thisObject, "mCurMEMCPackage");
            if (!pkg.equals(active)) return;
            int[] request = (int[])XposedHelpers.getObjectField(p.thisObject, "mValues");
            if (request != null && request.length >= 5 && request[0] == 1
                    && request[1] == Policy.GPU_ROUTE && (request[3] == 30 || request[3] == 45 || request[3] == 60))
                p.setResult(request[3]);
        });
        hook(controller, "updateNoErrorState", new Object[]{}, null, p -> {
            if (XposedHelpers.getBooleanField(p.thisObject, "mNoError")) return;
            if (XposedHelpers.getIntField(p.thisObject, "mMEMCGlesRunning") <= 0
                    && XposedHelpers.getIntField(p.thisObject, "mMEMCVulkanRunning") <= 0) return;
            String pkg = (String)XposedHelpers.getObjectField(p.thisObject, "mCurMEMCPackage");
            if (!allowed(pkg, "memc")) return;
            if (XposedHelpers.getIntField(p.thisObject, "mCurMEMCPid") <= 0) {
                int[] request = (int[])XposedHelpers.getObjectField(p.thisObject, "mValues");
                if (request != null && request.length >= 3 && request[0] == 1 && request[2] > 0)
                    XposedHelpers.setIntField(p.thisObject, "mCurMEMCPid", request[2]);
            }
            XposedHelpers.setBooleanField(p.thisObject, "mNoError", true);
        });
        hook("com.vivo.services.game.memc.MEMCConfigParser", "getMEMCFpsForPackage",
                new Object[]{String.class, int.class, int.class, double.class, boolean.class}, null, p -> {
            String pkg = (String)p.args[0];
            int mode = (Integer)p.args[2];
            if (p.getResult() != null || !allowed(pkg, "memc") || (mode != 1 && mode != 2)) return;
            double temp = (Double)p.args[3];
            int target = mode == 2 ? 60 : state.policy(pkg).targetFps;
            if (Policy.thermalStopped(genericOverheat.contains(pkg), temp)) genericOverheat.add(pkg);
            else genericOverheat.remove(pkg);
            boolean stopped = genericOverheat.contains(pkg);
            p.setResult(XposedHelpers.newInstance(XposedHelpers.findClass(
                    "com.vivo.services.game.memc.MEMCConfigParser$MEMCFps", loader),
                    stopped ? 0 : target, stopped ? 0 : target));
            once("genericbackend:" + pkg + stopped, "MEMC_BACKEND_PROFILE pkg=" + pkg
                    + " source=" + target/2 + " target=" + target + " thermalStopped=" + stopped);
        });
    }

    private void backend(String path, String stem, String flagStem, String feature) {
        String base = "com.vivo.services.game." + path + ".";
        String controller = base + stem + "Controller";
        String parser = base + stem + "ConfigParser";
        for (String api : new String[]{"Gles", "Vulkan"}) {
            String flag = "SUPPORT_" + flagStem + "_" + api.toUpperCase(Locale.ROOT);
            hook(controller, "is" + stem + api + "WhitelistApp", new Object[]{String.class}, null, p -> {
                String pkg = (String)p.args[0];
                if (allowed(pkg, feature) && XposedHelpers.getStaticBooleanField(p.thisObject.getClass(), flag)) {
                    p.setResult(true);
                    once("backend:" + feature + api + pkg, "BACKEND_ALLOWED " + feature + "/" + api + " pkg=" + pkg);
                }
            });
            hook(parser, "support" + stem + api + "ForCurrentModel", new Object[]{String.class}, null, p -> {
                if (allowed((String)p.args[0], feature)
                        && Boolean.TRUE.equals(XposedHelpers.callStaticMethod((Class<?>)p.method.getDeclaringClass(), "support" + stem + api)))
                    p.setResult(true);
            });
        }
        // Post-processing VSR stays on its OEM profile list; no forced GLES/Vulkan hardware flags.
    }

    private void auditDispatch(String service, String method, String feature) {
        hook(service, method, new Object[]{String.class, int.class, int.class, int.class, int.class}, null, p -> {
            if (allowed((String)p.args[0], feature))
                once("dispatch:" + method + Arrays.toString(p.args), "NATIVE_DISPATCH " + method + " " + Arrays.toString(p.args));
        });
    }
}
