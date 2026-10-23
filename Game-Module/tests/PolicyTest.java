import io.github.vivogameunlock.Policy;
import java.util.*;

public final class PolicyTest {
    private static int checks;
    private static void check(boolean value, String name) {
        checks++; if (!value) throw new AssertionError(name);
    }
    private static final Set<String> GAMES = new HashSet<>(Arrays.asList("com.garena.game.kgvn", "com.miHoYo.GenshinImpact"));
    private static Policy config(boolean enabled, boolean memc, boolean vsr, int fps) {
        return new Policy(enabled, memc, vsr, fps, GAMES);
    }
    public static void main(String[] args) {
        Policy p = config(true, true, true, 120);
        check(p.allows("com.garena.game.kgvn"), "listed game admitted");
        check(p.allows("com.miHoYo.GenshinImpact"), "listed international game admitted");
        check(!p.allows("com.facebook.katana"), "non-game app never admitted");
        check(!p.allows("com.google.android.apps.maps"), "unlisted app rejected");
        check(!p.allows("android"), "system package rejected");
        check(!new Policy(true, true, true, 120, new HashSet<>(Arrays.asList(Policy.MODULE))).allows(Policy.MODULE), "module excluded");
        check(!p.allows(null), "null rejected");
        check(!p.allows(""), "empty rejected");
        check(new Policy(true, true, true, 120, Collections.<String>emptySet()).packages().isEmpty(), "empty game list admits nothing");
        check(RuntimeStateClosed.admitsNothing(), "closed policy admits nothing");
        check(config(false, true, true, 120).packages().isEmpty(), "master off");
        check(!config(false, true, true, 120).allows("com.garena.game.kgvn"), "master off rejects");
        check(p.feature("BOOST_FRAME_V3_60_120"), "120 FPS mode");
        check(!p.feature("BOOST_FRAME_V3_30_120"), "do not expose incompatible pair");
        check(config(true, true, true, 60).feature("BOOST_FRAME_V3_30_60"), "60 FPS mode");
        check(config(true, true, true, 90).feature("BOOST_FRAME_V3_60_45_90"), "90 FPS mode");
        check(!config(true, false, true, 120).feature("FRAME_INTERPOLATION"), "MEMC disabled");
        check(!config(true, true, false, 120).feature("SUPER_RESOLUTION"), "VSR disabled");
        check(!p.feature("COMPETITION_MODE"), "unrelated OEM feature unchanged");
        check(!p.feature(null), "null feature");
        check(config(true, true, true, 999).targetFps == 120, "invalid FPS normalized");
        check(Policy.GPU_ROUTE == 3, "ROM MEMC GPU route");
        check(Policy.thermalStopped(false, 47), "stop at 47");
        check(Policy.thermalStopped(true, 45), "stay stopped in hysteresis band");
        check(!Policy.thermalStopped(true, 44), "recover at 44");
        check(!Policy.thermalStopped(false, 46), "remain on below stop threshold");
        check(Policy.thermalStopped(false, 0), "unknown temperature does not enable generic native profile");
        check(Policy.thermalStopped(false, Double.NaN), "NaN temperature rejected");
        check(Policy.thermalStopped(false, Double.POSITIVE_INFINITY), "infinite temperature rejected");
        System.out.println("PolicyTest: " + checks + " checks passed");
    }

    private static final class RuntimeStateClosed {
        static boolean admitsNothing() {
            Policy closed = new Policy(false, false, false, 120, Collections.<String>emptySet());
            return !closed.allows("com.garena.game.kgvn") && closed.packages().isEmpty();
        }
    }
}
