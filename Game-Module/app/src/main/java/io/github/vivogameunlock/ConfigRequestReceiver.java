package io.github.vivogameunlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Manifest permission DUMP restricts requests to privileged callers, including the system UID hosts. */
public final class ConfigRequestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String pkg = intent.getStringExtra("target");
        if (ConfigContract.targetAllowed(pkg)) {
            if ("android".equals(pkg)) {
                ctx.createDeviceProtectedStorageContext().getSharedPreferences("runtime_status", 0).edit()
                        .putLong("heartbeat", android.os.SystemClock.elapsedRealtime())
                        .putInt("boot", android.provider.Settings.Global.getInt(ctx.getContentResolver(), "boot_count", -1)).apply();
            }
            if ("com.vivo.gamecube".equals(pkg) && intent.hasExtra("game_count")) {
                ConfigStore.captureDefaults(ctx, ConfigContract.limited(intent.getStringExtra("games")));
                ctx.createDeviceProtectedStorageContext().getSharedPreferences("runtime_status", 0).edit()
                        .putString("games", ConfigContract.limited(intent.getStringExtra("games")))
                        .putInt("game_count", Math.max(0, intent.getIntExtra("game_count", 0)))
                        .putLong("games_heartbeat", android.os.SystemClock.elapsedRealtime()).apply();
            }
            if ("com.vivo.gamecube".equals(pkg) && intent.hasExtra("game_result_id")) {
                ctx.createDeviceProtectedStorageContext().getSharedPreferences("runtime_status", 0).edit()
                        .putString("game_result_id", ConfigContract.limited(intent.getStringExtra("game_result_id")))
                        .putString("game_error", ConfigContract.limited(intent.getStringExtra("game_error"))).apply();
            }
            try { ConfigStore.send(ctx, pkg); }
            catch (IllegalStateException e) { android.util.Log.w("VivoGameUnlock", "Config waits for first unlock", e); }
        }
    }
}
