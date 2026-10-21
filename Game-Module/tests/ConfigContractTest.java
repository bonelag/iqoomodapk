import io.github.vivogameunlock.ConfigContract;

public final class ConfigContractTest {
    private static int checks;
    private static void check(boolean b, String message) { checks++; if (!b) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(ConfigContract.targetAllowed("android"), "system context target");
        check(ConfigContract.targetAllowed("com.vivo.gamecube"), "GameCube target");
        check(ConfigContract.targetAllowed("com.vivo.gamewatch"), "GameWatch target");
        check(!ConfigContract.targetAllowed("com.vivo.gamecube.evil"), "no prefix-based destination trust");
        check(!ConfigContract.targetAllowed("com.other.app"), "no arbitrary broadcast destination");
        check(!ConfigContract.targetAllowed(null), "no null destination");
        check(!ConfigContract.targetAllowed(""), "no empty destination");
        check(ConfigContract.limited(null).isEmpty(), "null payload normalized");
        check(ConfigContract.limited("com.garena.game.kgvn").equals("com.garena.game.kgvn"), "ordinary package preserved");
        check(ConfigContract.limited(new String(new char[20000]).replace('\0','x')).length()==16384, "bounded payload");
        System.out.println("ConfigContractTest: "+checks+" checks passed");
    }
}
