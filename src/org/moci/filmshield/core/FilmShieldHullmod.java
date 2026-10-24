package org.moci.filmshield.core;
import com.fs.starfarer.api.combat.*;
import org.moci.filmshield.api.*;
/** Built-in-only generic hullmod. Content mods can subclass getProfile or use FilmShield.attach directly. */
public class FilmShieldHullmod extends BaseHullMod implements FilmShieldProvider {
    private static final ShieldProfile DEFAULT=ShieldProfile.builder("filmshield_native_v1").build();
    protected ShieldProfile getProfile(ShipAPI ship) { return DEFAULT; }
    @Override public void applyEffectsAfterShipCreation(ShipAPI ship,String id) { FilmShield.attach(ship,getProfile(ship),id); }
    @Override public boolean isApplicableToShip(ShipAPI ship) { return ship!=null&&ship.getShield()!=null; }
}
