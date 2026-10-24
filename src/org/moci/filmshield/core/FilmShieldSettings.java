package org.moci.filmshield.core;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONObject;
/** Library defaults; optional providers may further restrict effects. No defense depends on these switches. */
public final class FilmShieldSettings {
    public static boolean skipFighterEffects=true, enableFighterLimit=false, enableNormalRelief=true, enableShaders=true;
    public static int fighterLimit=150;
    private FilmShieldSettings() {}
    public static void load() throws Exception {
        JSONObject json=Global.getSettings().getMergedJSONForMod("data/config/filmshield.json","moci_filmshield");
        skipFighterEffects=json.optBoolean("skipFighterEffects",true);
        enableFighterLimit=json.optBoolean("enableFighterLimit",false);
        enableNormalRelief=json.optBoolean("enableNormalRelief",true);
        enableShaders=json.optBoolean("enableShaders",true);
        fighterLimit=Math.max(1,json.optInt("fighterLimit",150));
    }
    public static boolean shouldRender(ShipAPI ship,int fighters) {
        return !ship.isFighter()||(!skipFighterEffects&&(!enableFighterLimit||fighters<=fighterLimit));
    }
}
