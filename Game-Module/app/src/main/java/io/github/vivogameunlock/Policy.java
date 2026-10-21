package io.github.vivogameunlock;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Android-independent policy, exercised by tests/PolicyTest.java. */
public final class Policy {
    public static final String MODULE = "io.github.vivogameunlock";
    public static final int GPU_ROUTE = 3;
    public final boolean enabled, allApps, memc, vsr, vaa;
    public final int targetFps;
    private final Set<String> selected, excluded, installed;

    public Policy(boolean enabled, boolean allApps, boolean memc, boolean vsr, boolean vaa,
                  int targetFps, String selected, String excluded, Set<String> installed) {
        this.enabled = enabled; this.allApps = allApps;
        this.memc = memc; this.vsr = vsr; this.vaa = vaa;
        this.targetFps = targetFps == 60 || targetFps == 90 || targetFps == 120 ? targetFps : 120;
        this.selected = parse(selected); this.excluded = parse(excluded);
        this.installed = Collections.unmodifiableSet(new HashSet<>(installed));
    }

    public static Set<String> parse(String text) {
        Set<String> result = new HashSet<>();
        if (text != null) for (String s : text.split("[\\s,;]+"))
            if (s.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) result.add(s);
        return result;
    }

    public boolean allows(String pkg) {
        return enabled && pkg != null && !MODULE.equals(pkg) && installed.contains(pkg)
                && !excluded.contains(pkg) && (allApps || selected.contains(pkg));
    }

    public Set<String> packages() {
        Set<String> result = new HashSet<>();
        for (String pkg : installed) if (allows(pkg)) result.add(pkg);
        return result;
    }

    public static boolean thermalStopped(boolean previous, double temperature) {
        if (Double.isNaN(temperature) || Double.isInfinite(temperature) || temperature <= 0 || temperature >= 47) return true;
        if (temperature <= 44) return false;
        return previous;
    }

    public boolean feature(String name) {
        if (name == null) return false;
        if (name.equals("FRAME_INTERPOLATION") || name.equals("BOOST_FRAME")
                || name.equals("OPTIMIZE_POWER") || name.equals("BOOST_FRAME_V3")
                || name.equals("OPTIMIZE_POWER_V3")) return memc;
        if (name.equals("SUPER_RESOLUTION") || name.equals("SUPER_HD_ENGINE")) return vsr;
        // Expose only a coherent pair for generic V3 profiles, not every OEM mode.
        if (name.equals("BOOST_FRAME_V3_30_60")) return memc && targetFps == 60;
        if (name.equals("BOOST_FRAME_V3_60_45_90")) return memc && targetFps == 90;
        if (name.equals("BOOST_FRAME_V3_60_120")) return memc && targetFps == 120;
        if (name.equals("OPTIMIZE_POWER_V3_60_30_60")) return memc;
        return false;
    }
}
