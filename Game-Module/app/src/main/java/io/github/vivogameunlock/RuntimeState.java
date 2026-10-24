package io.github.vivogameunlock;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
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
    /** GameCube "Supported games" list: the only source of packages this module may admit. */
    static final Uri SUPPORT_GAMES = Uri.parse("content://com.vivo.gamecube.provider/support_game");
    private volatile Policy policy = closed();
    private volatile String packageList = "";
    private volatile java.util.Map<String, Policy> profiles = Collections.emptyMap();
    private volatile boolean started;
    private Set<String> games = Collections.emptySet();
    private long lastGameScan;
    private boolean unavailableLogged;
    private Bundle received;
    private SharedPreferences cache;

    static Policy closed() {
        return new Policy(false, false, false, 120, Collections.<String>emptySet());
    }
    Policy policy() { return policy; }
    Policy policy(String pkg) {
        Policy selected = pkg == null ? null : profiles.get(pkg);
        return selected == null ? policy : selected;
    }
    String packages() { return packageList; }
    String packages(String feature) {
        java.util.List<String> selected = new java.util.ArrayList<>();
        for (String pkg : policy.packages()) {
            Policy p = policy(pkg);
            if ("memc".equals(feature) ? p.memc : p.vsr) selected.add(pkg);
        }
        return android.text.TextUtils.join(";", selected);
    }

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
            if ((android.app.Application.getProcessName() + "").startsWith("com.vivo.gamecube"))
                registerGameCommands(ctx, handler);
            request(ctx);
            // system_server runs as package "android", which has no data directory for preferences.
            if (!"android".equals(ctx.getPackageName())) {
                try {
                    cache = ctx.createDeviceProtectedStorageContext().getSharedPreferences(ConfigContract.CACHE, 0);
                    if (cache.getBoolean("received", false)) {
                        received = ConfigStore.read(cache);
                        apply(ctx, received);
                    }
                } catch (Throwable t) { GameUnlock.log("CONFIG_CACHE_ERROR: " + t); }
            }
            try {
                ctx.getContentResolver().registerContentObserver(SUPPORT_GAMES, true, new ContentObserver(handler) {
                    @Override public void onChange(boolean selfChange) {
                        lastGameScan = 0;
                        if (received != null) apply(ctx, received);
                    }
                });
            } catch (Throwable t) { GameUnlock.log("GAMES_OBSERVER_ERROR: " + t); }
            try {
                IntentFilter events = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
                ctx.registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent i) {
                        lastGameScan = 0;
                        request(ctx);
                        if (received != null) apply(ctx, received);
                    }
                }, events, null, handler);
            } catch (Throwable t) { GameUnlock.log("UNLOCK_RECEIVER_ERROR: " + t); }
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
                    if (received == null) { policy = closed(); profiles = Collections.emptyMap(); packageList = ""; }
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
            Intent request = new Intent().setComponent(new ComponentName(Policy.MODULE,
                    Policy.MODULE + ".ConfigRequestReceiver")).putExtra("target", ctx.getPackageName())
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            if (lastGameScan > 0) request.putExtra("game_count", games.size())
                    .putExtra("games", android.text.TextUtils.join(";", games));
            ctx.sendBroadcast(request);
        } catch (Throwable t) { GameUnlock.log("CONFIG_REQUEST_ERROR: " + t); }
    }

    private void registerGameCommands(Context ctx, Handler handler) {
        ctx.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getIntExtra("protocol", 0) != 1) return;
                String id = intent.getStringExtra("request_id");
                if (id == null || id.length() > 64) return;
                String error = "";
                try {
                    String operation = intent.getStringExtra("operation");
                    String pkg = intent.getStringExtra("package");
                    if (!"list".equals(operation)) {
                        if (pkg == null || pkg.length() > 256 || !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
                                || Policy.MODULE.equals(pkg)) throw new IllegalArgumentException("Invalid package");
                        if ("add".equals(operation)) {
                            ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(pkg, 0);
                            if (!ai.enabled || ai.uid % 100000 < 10000 || (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                                throw new IllegalArgumentException("Only enabled user apps can be added");
                            if (!hasGame(ctx, pkg)) {
                                android.content.ContentValues values = new android.content.ContentValues();
                                values.put("package_name", pkg);
                                try { ctx.getContentResolver().insert(SUPPORT_GAMES, values); }
                                catch (RuntimeException insertError) { if (!hasGame(ctx, pkg)) throw insertError; }
                            }
                        } else if ("remove".equals(operation)) {
                            ctx.getContentResolver().delete(SUPPORT_GAMES, "package_name=?", new String[]{pkg});
                        } else throw new IllegalArgumentException("Invalid operation");
                        ctx.getContentResolver().notifyChange(SUPPORT_GAMES, null);
                        if (hasGame(ctx, pkg) != "add".equals(operation)) throw new IllegalStateException("GameCube did not save the change");
                    }
                    lastGameScan = 0;
                    scanGames(ctx);
                    if (received != null) apply(ctx, received);
                } catch (Exception failure) { error = failure.getClass().getSimpleName() + ": " + failure.getMessage(); }
                Intent reply = new Intent().setComponent(new ComponentName(Policy.MODULE,
                        Policy.MODULE + ".ConfigRequestReceiver"))
                        .putExtra("target", ctx.getPackageName()).putExtra("game_result_id", id)
                        .putExtra("game_error", error);
                if (lastGameScan > 0) reply.putExtra("game_count", games.size())
                        .putExtra("games", android.text.TextUtils.join(";", games));
                ctx.sendBroadcast(reply);
            }
        }, new IntentFilter(ConfigContract.GAME_ACTION), ConfigContract.PERMISSION, handler, Context.RECEIVER_EXPORTED);
    }

    private boolean hasGame(Context ctx, String pkg) {
        try (Cursor cursor = ctx.getContentResolver().query(SUPPORT_GAMES, new String[]{"package_name"},
                "package_name=?", new String[]{pkg}, null)) {
            if (cursor == null) throw new IllegalStateException("GameCube provider unavailable");
            return cursor.moveToFirst();
        }
    }

    /** Reads enabled games from GameCube; on any failure keeps the previous list. */
    private void scanGames(Context ctx) {
        android.os.UserManager um = ctx.getSystemService(android.os.UserManager.class);
        if (um != null && !um.isUserUnlocked()) return;
        try (Cursor c = ctx.getContentResolver().query(SUPPORT_GAMES, null, null, null, null)) {
            if (c == null) return;
            int col = c.getColumnIndex("package_name");
            if (col < 0) return;
            Set<String> next = new HashSet<>();
            while (c.moveToNext()) {
                String pkg = c.getString(col);
                if (pkg == null || Policy.MODULE.equals(pkg)) continue;
                try {
                    ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(pkg, 0);
                    if (ai.enabled && ai.uid % 100000 >= 10000 && (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
                        next.add(pkg);
                } catch (Throwable notInstalled) { }
            }
            games = next;
            lastGameScan = SystemClock.elapsedRealtime();
            pushGames(ctx);
        } catch (Throwable t) { GameUnlock.log("GAMES_SCAN_ERROR host=" + ctx.getPackageName() + ": " + t); }
    }

    /** Hands the freshly scanned list to the module app so its UI updates without a request. */
    private void pushGames(Context ctx) {
        if (!ctx.getPackageName().startsWith("com.vivo.gamecube")) return;
        try {
            ctx.sendBroadcast(new Intent().setComponent(new ComponentName(Policy.MODULE,
                    Policy.MODULE + ".ConfigRequestReceiver")).putExtra("target", ctx.getPackageName())
                    .putExtra("game_count", games.size())
                    .putExtra("games", android.text.TextUtils.join(";", games))
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        } catch (Throwable t) { GameUnlock.log("GAMES_PUSH_ERROR: " + t); }
    }

    private void apply(Context ctx, Bundle b) {
        if (lastGameScan == 0 || SystemClock.elapsedRealtime() - lastGameScan > 10000) scanGames(ctx);
        Policy next = new Policy(b.getBoolean("enabled", true), b.getBoolean("memc", true),
                b.getBoolean("vsr", true), b.getInt("target_fps", 120), games);
        java.util.List<String> names = new java.util.ArrayList<>(next.packages());
        Collections.sort(names);
        String joined = android.text.TextUtils.join(";", names);
        boolean changed = !joined.equals(packageList) || policy.enabled != next.enabled;
        java.util.Map<String, Policy> overrides = new java.util.HashMap<>();
        try {
            org.json.JSONObject json = new org.json.JSONObject(b.getString("profiles", "{}"));
            for (String pkg : games) {
                org.json.JSONObject entry = json.optJSONObject(pkg);
                if (entry != null) overrides.put(pkg, new Policy(next.enabled,
                        entry.optBoolean("memc", next.memc), entry.optBoolean("vsr", next.vsr),
                        entry.optInt("target_fps", next.targetFps), games));
            }
        } catch (org.json.JSONException invalid) { GameUnlock.log("PROFILE_PARSE_ERROR " + invalid); }
        profiles = Collections.unmodifiableMap(overrides);
        packageList = joined;
        policy = next;
        if (changed) GameUnlock.log("CONFIG_READY host=" + ctx.getPackageName() + " games=" + names + " target=" + next.targetFps);
    }
}
