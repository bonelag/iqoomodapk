package io.github.vivogameunlock;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

final class ConfigStore {
    static final String[] FLAGS = {"enabled", "all_apps", "memc", "vsr", "vaa"};
    private ConfigStore() { }
    static synchronized SharedPreferences preferences(Context ctx) {
        Context storage = ctx.createDeviceProtectedStorageContext();
        SharedPreferences p = storage.getSharedPreferences("config", 0);
        if (!p.getBoolean("direct_boot_ready", false)) {
            if (!ctx.getSystemService(android.os.UserManager.class).isUserUnlocked())
                throw new IllegalStateException("Configuration migration requires first unlock");
            SharedPreferences old = ctx.getSharedPreferences("config", 0);
            Bundle previous = read(old);
            write(p, previous);
            p.edit().putBoolean("direct_boot_ready", true).apply();
        }
        return p;
    }
    static Bundle read(Context ctx) { return read(preferences(ctx)); }
    static Bundle read(SharedPreferences p) {
        Bundle b = new Bundle();
        b.putInt("protocol", 1);
        for (String key : FLAGS) b.putBoolean(key, !"vaa".equals(key) && p.getBoolean(key, true));
        b.putInt("target_fps", p.getInt("target_fps", 120));
        b.putString("selected", ConfigContract.limited(p.getString("selected", "")));
        b.putString("excluded", ConfigContract.limited(p.getString("excluded", "")));
        b.putLong("revision", p.getLong("revision", 0));
        return b;
    }
    static void write(SharedPreferences p, Bundle b) {
        SharedPreferences.Editor e = p.edit().putBoolean("received", true);
        for (String key : FLAGS) e.putBoolean(key, b.getBoolean(key, true));
        e.putInt("target_fps", b.getInt("target_fps", 120))
                .putString("selected", ConfigContract.limited(b.getString("selected", "")))
                .putString("excluded", ConfigContract.limited(b.getString("excluded", "")))
                .putLong("revision", b.getLong("revision", 0)).apply();
    }
    static void publish(Context ctx) {
        // A user UI change only writes this module's own preferences and sends configuration.
        SharedPreferences p = preferences(ctx);
        p.edit().putLong("revision", Math.max(System.currentTimeMillis(), p.getLong("revision", 0) + 1)).apply();
        for (String pkg : ConfigContract.TARGETS) send(ctx, pkg);
    }
    static void send(Context ctx, String pkg) {
        if (!ConfigContract.targetAllowed(pkg)) return;
        ctx.sendBroadcast(new Intent(ConfigContract.ACTION).setPackage(pkg).putExtras(read(ctx))
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
    }
}
