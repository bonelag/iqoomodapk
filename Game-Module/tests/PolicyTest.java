import io.github.vivogameunlock.Policy;
import java.util.*;

public final class PolicyTest {
    private static int checks;
    private static void check(boolean value, String name) {
        checks++; if (!value) throw new AssertionError(name);
    }
    private static Policy config(boolean enabled, boolean all, boolean memc, boolean vsr,
                                 boolean vaa, int fps, String selected, String excluded) {
        return new Policy(enabled, all, memc, vsr, vaa, fps, selected, excluded,
                new HashSet<>(Arrays.asList("com.garena.game.kgvn", "com.miHoYo.GenshinImpact", Policy.MODULE)));
    }
    public static void main(String[] args) {
        Policy p = config(true, true, true, true, true, 120, "", "");
        check(!p.vaa, "legacy VAA enable is ignored");
        check(p.allows("com.garena.game.kgvn"), "non-Chinese package admitted");
        check(p.allows("com.miHoYo.GenshinImpact"), "international package admitted");
        check(!p.allows("android"), "system package rejected");
        check(!p.allows(Policy.MODULE), "module excluded");
        check(!p.allows(null), "null rejected");
        check(!p.allows(""), "empty rejected");
        check(!p.allows("com.malicious.unknown"), "uninstalled rejected");
        check(config(false,true,true,true,true,120,"","").packages().isEmpty(), "master off");
        check(!config(true,true,true,true,true,120,"","com.garena.game.kgvn").allows("com.garena.game.kgvn"), "exclude priority");
        Policy selected = config(true,false,true,true,true,60,"com.garena.game.kgvn","");
        check(selected.allows("com.garena.game.kgvn"), "selected package");
        check(!selected.allows("com.miHoYo.GenshinImpact"), "unselected package");
        check(Policy.parse("com.one.game;com.two.game\ncom.three.game,com.four.game bad ; *").size()==4, "exact package parsing");
        check(!Policy.parse("com.one.gameevil").contains("com.one.game"), "no substring match");
        check(p.feature("BOOST_FRAME_V3_60_120"), "120 FPS mode");
        check(!p.feature("BOOST_FRAME_V3_30_120"), "do not expose incompatible pair");
        check(selected.feature("BOOST_FRAME_V3_30_60"), "60 FPS mode");
        check(config(true,true,true,true,true,90,"","").feature("BOOST_FRAME_V3_60_45_90"), "90 FPS mode");
        check(!config(true,true,false,true,true,120,"","").feature("FRAME_INTERPOLATION"), "MEMC disabled");
        check(!config(true,true,true,false,true,120,"","").feature("SUPER_RESOLUTION"), "VSR disabled");
        check(!p.feature("COMPETITION_MODE"), "unrelated OEM feature unchanged");
        check(!p.feature(null), "null feature");
        check(config(true,true,true,true,true,999,"","").targetFps==120, "invalid FPS normalized");
        check(Policy.GPU_ROUTE==3, "ROM MEMC GPU route");
        check(Policy.thermalStopped(false,47), "stop at 47");
        check(Policy.thermalStopped(true,45), "stay stopped in hysteresis band");
        check(!Policy.thermalStopped(true,44), "recover at 44");
        check(!Policy.thermalStopped(false,46), "remain on below stop threshold");
        check(Policy.thermalStopped(false,0), "unknown temperature does not enable generic native profile");
        check(Policy.thermalStopped(false,Double.NaN), "NaN temperature rejected");
        check(Policy.thermalStopped(false,Double.POSITIVE_INFINITY), "infinite temperature rejected");
        System.out.println("PolicyTest: "+checks+" checks passed");
    }
}
