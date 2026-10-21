package io.github.vivogameunlock;

/** Shared transport contract. Target package values are never accepted as arbitrary destinations. */
public final class ConfigContract {
    public static final String ACTION = "io.github.vivogameunlock.CONFIG_V1";
    public static final String PERMISSION = "io.github.vivogameunlock.permission.CONFIG";
    public static final String CACHE = "io.github.vivogameunlock.runtime_config";
    public static final String[] TARGETS = {"android", "com.vivo.gamecube", "com.vivo.gamewatch"};
    private ConfigContract() { }
    public static boolean targetAllowed(String pkg) {
        return "android".equals(pkg) || "com.vivo.gamecube".equals(pkg) || "com.vivo.gamewatch".equals(pkg);
    }
    public static String limited(String text) {
        return text == null ? "" : text.substring(0, Math.min(text.length(), 16384));
    }
}
