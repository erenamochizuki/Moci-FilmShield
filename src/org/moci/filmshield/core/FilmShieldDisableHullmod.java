package org.moci.filmshield.core;
import com.fs.starfarer.api.combat.*;
import org.moci.filmshield.api.FilmShield;

/** Since 1.3.0. Per-ship opt-out for compatibility: the ship skips every film shield participant and keeps its
 * native shield untouched. On a station it also covers the modules. Detection is by ID in {@link FilmShield#isDisabled}. */
public class FilmShieldDisableHullmod extends BaseHullMod {
    /** Never blocks installation, so other mods can add the switch as a built-in on any hull. */
    @Override public boolean isApplicableToShip(ShipAPI ship) { return true; }
    /** Offered in the refit picker only where a film shield participant exists, locally or on a parent station. */
    @Override public boolean showInRefitScreenModPickerFor(ShipAPI ship) { return hasParticipant(ship); }
    private static boolean hasParticipant(ShipAPI ship) {
        for(int depth=0;ship!=null&&depth<16;depth++) {
            if(FilmShield.getLocalProviderHullmodId(ship)!=null) return true;
            ShipAPI parent=ship.isStationModule()?ship.getParentStation():null;
            if(parent==ship) break;
            ship=parent;
        }
        return false;
    }
}
