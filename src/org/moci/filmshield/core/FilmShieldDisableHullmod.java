package org.moci.filmshield.core;
import com.fs.starfarer.api.combat.*;

/** Since 1.3.0. Developer compatibility switch, hidden everywhere (hidden and hiddenEverywhere in hull_mods.csv):
 * players never see or install it. Other mods add it through builtInMods, a variant or addPermaMod; the ship then
 * skips every film shield participant and keeps its native shield untouched. On a station it also covers the modules.
 * Detection is by ID in {@link org.moci.filmshield.api.FilmShield#isDisabled}. */
public class FilmShieldDisableHullmod extends BaseHullMod {
    /** Never blocks installation, so other mods can add the switch as a built-in on any hull. */
    @Override public boolean isApplicableToShip(ShipAPI ship) { return true; }
    /** Not for players: never offered in the refit picker, in addition to the hidden flags in hull_mods.csv. */
    @Override public boolean showInRefitScreenModPickerFor(ShipAPI ship) { return false; }
}
