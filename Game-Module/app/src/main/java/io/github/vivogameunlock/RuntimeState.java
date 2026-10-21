package io.github.vivogameunlock;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Provider and package-manager work happens only on a dedicated background thread. */
final class RuntimeState {
    private volatile Policy policy = closed();
    private volatile String packageList = "";
    private volatile boolean started;
    private Set<String> installed = Collections.emptySet();
    private long lastPackageScan;
    private boolean unavailableLogged;
    private Bundle received;
    private SharedPreferences cache;

    static Policy closed() {
        return new Policy(false, false, false, false, false, 120, "", "", Collections.emptySet());
    }
    Policy policy() { return policy; }
    String packages() { return packageList; }

    synchronized void start(Context context) {
        if (started || context == null) return;
        started = true;
        Context app = context.getApplicationContext();
        final Context ctx = app == null ? context : app;
        HandlerThread thread = new HandlerThread("VivoGameUnlockConfig");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        // The permission is signature-only and granted to the module APK. Other apps cannot
        // supply configuration. Provider visibility filters no longer disable a received config.
        handler.post(() -> {
            try {
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent intent) {
                        try {
                            Bundle b = intent.getExtras();
                            if (b == null || b.getInt("protocol", 0) != 1) return;
                            if (received != null && b.getLong("revision", 0) < received.getLong("revision", 0)) return;
                            received = new Bundle(b);
                            apply(ctx, received);
                            try { if (cache != null) ConfigStore.write(cache, received); }
                            catch (Throwable t) { GameUnlock.log("CONFIG_CACHE_ERROR: " + t); }
                            GameUnlock.log("CONFIG_RECEIVED host=" + ctx.getPackageName() + " revision=" + b.getLong("revision", 0));
                        } catch (Throwable t) { GameUnlock.log("CONFIG_RECEIVE_ERROR: " + t); }
                    }
                };
                IntentFilter filter = new IntentFilter(ConfigContract.ACTION);
                if (Build.VERSION.SDK_INT >= 33)
                    ctx.registerReceiver(receiver, filter, ConfigContract.PERMISSION, handler, Context.RECEIVER_EXPORTED);
                else ctx.registerReceiver(receiver, filter, ConfigContract.PERMISSION, handler);
                GameUnlock.log("CONFIG_RECEIVER_READY host=" + ctx.getPackageName());
            } catch (Throwable t) { GameUnlock.log("CONFIG_RECEIVER_ERROR: " + t); }
            request(ctx);
            try {
                cache = ctx.createDeviceProtectedStorageContext().getSharedPreferences(ConfigContract.CACHE, 0);
                if (cache.getBoolean("received", false)) {
                    received = ConfigStore.read(cache);
                    apply(ctx, received);
                }
            } catch (Throwable t) { GameUnlock.log("CONFIG_CACHE_ERROR: " + t); }
            IntentFilter events = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
            ctx.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    lastPackageScan = 0;
                    request(ctx);
                    if (received != null) apply(ctx, received);
                }
            }, events, null, handler);
        });
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    Bundle b = received;
                    // Keep the original channel as a fallback on ROMs where it resolves.
                    if (b == null) {
                        b = ctx.getContentResolver().call(ConfigProvider.URI, "config", null, null);
                        if (b == null) throw new IllegalStateException("Empty module config");
                    }
                    apply(ctx, b);
                    unavailableLogged = false;
                } catch (Throwable t) {
                    if (received == null) { policy = closed(); packageList = ""; }
                    if (!unavailableLogged) {
                        GameUnlock.log("CONFIG_UNAVAILABLE host=" + ctx.getPackageName() + ": " + t);
                        unavailableLogged = true;
                    }
                } finally {
                    // Explicit manifest receiver is short-lived, no activity/service start or root.
                    request(ctx);
                    handler.postDelayed(this, 15000);
                }
            }
        });
    }

    private void request(Context ctx) {
        try {
            ctx.sendBroadcast(new Intent().setComponent(new ComponentName(Policy.MODULE,
                    Policy.MODULE + ".ConfigRequestReceiver")).putExtra("target", ctx.getPackageName())
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        } catch (Throwable t) { GameUnlock.log("CONFIG_REQUEST_ERROR: " + t); }
    }

    private void apply(Context ctx, Bundle b) {
        if (lastPackageScan == 0 || SystemClock.elapsedRealtime() - lastPackageScan > 60000) {
            try {
                Set<String> next = new HashSet<>();
                for (ApplicationInfo ai : ctx.getPackageManager().getInstalledApplications(0)) {
                    if (ai.enabled && ai.uid % 100000 >= 10000 && (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
                        next.add(ai.packageName);
                }
                installed = next;
                lastPackageScan = SystemClock.elapsedRealtime();
            } catch (Throwable t) { GameUnlock.log("CONFIG_PACKAGES_ERROR: " + t); }
        }
        Policy next = new Policy(b.getBoolean("enabled", true), b.getBoolean("all_apps", true),
                b.getBoolean("memc", true), b.getBoolean("vsr", true), b.getBoolean("vaa", true),
                b.getInt("target_fps", 120), b.getString("selected", ""), b.getString("excluded", ""), installed);
        java.util.List<String> names = new java.util.ArrayList<>(next.packages());
        Collections.sort(names);
        packageList = android.text.TextUtils.join(";", names);
        boolean first = !policy.enabled && next.enabled;
        policy = next;
        if (first) GameUnlock.log("CONFIG_READY host=" + ctx.getPackageName() + " candidates=" + names.size() + " target=" + next.targetFps);
    }
}
