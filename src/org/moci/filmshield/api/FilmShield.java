package org.moci.filmshield.api;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import java.util.Collection;
import java.util.Collections;
import org.moci.filmshield.core.FilmShieldController;
import org.moci.filmshield.render.FilmShieldRenderer;
import org.moci.filmshield.ui.FilmShieldUI;
/** Stable entry point. All methods are combat-thread only. No static ship/engine registry. */
public final class FilmShield {
    public static final int API_VERSION=1;
    public static final String MOD_ID="moci_filmshield";
    private static final String CONTROLLER_KEY="moci_filmshield.controller.v1";
    private static final String VISUAL_KEY="moci_filmshield.visual.v1";
    private FilmShield() {}
    /** Null means no local participant. Built-ins are included in the variant's hullmod collection. */
    public static String getLocalSelectedHullmodId(ShipAPI ship) {
        if(ship==null) return null;
        ShipVariantAPI variant=ship.getVariant();
        Collection<String> ids=variant!=null ? variant.getHullMods()
                : ship.getHullSpec()==null ? Collections.<String>emptyList() : ship.getHullSpec().getBuiltInMods();
        if(ids==null||Global.getSettings()==null) return null;
        for(String id:ids) {
            if(variant!=null&&variant.getSuppressedMods()!=null&&variant.getSuppressedMods().contains(id)) continue;
            HullModSpecAPI spec=Global.getSettings().getHullModSpec(id);
            if(spec!=null&&spec.getEffect() instanceof FilmShieldProvider) return id;
        }
        return null;
    }
    /** A module without local participants inherits the parent's selection, not its callback order. */
    public static String getSelectedHullmodId(ShipAPI ship) {
        if(ship==null) return null;
        String local=getLocalSelectedHullmodId(ship);
        if(local!=null) return local;
        ShipAPI parent=ship.isStationModule()?ship.getParentStation():null;
        return parent==null||parent==ship ? null : getSelectedHullmodId(parent);
    }
    public static boolean isSelected(ShipAPI ship,String hullmodId) {
        return hullmodId!=null&&hullmodId.equals(getSelectedHullmodId(ship));
    }
    /** Returns null for a nonselected bound profile. Unbound calls retain strict legacy ownership. */
    public static FilmShieldController attach(ShipAPI ship, ShieldProfile profile) {
        if(ship==null||profile==null) throw new IllegalArgumentException("Ship and profile are required");
        return attach(ship,profile,profile.hullmodId);
    }
    /** Hullmod integrations should use this overload (or bind profile.hullmodId). Losers do nothing. */
    public static FilmShieldController attach(ShipAPI ship, ShieldProfile profile,String hullmodId) {
        if(ship==null||profile==null) throw new IllegalArgumentException("Ship and profile are required");
        if(hullmodId!=null&&!isSelected(ship,hullmodId)) return null;
        FilmShieldController existing=find(ship);
        if(existing!=null) {
            if(!existing.getProfile().id.equals(profile.id)) throw new IllegalStateException("A different film shield already owns this ship");
            return existing;
        }
        FilmShieldController controller=new FilmShieldController(ship,profile,hullmodId);
        ship.addListener(controller);
        return controller;
    }
    public static FilmShieldController find(ShipAPI ship) {
        return ship==null ? null : (FilmShieldController)ship.getCustomData().get(CONTROLLER_KEY);
    }
    public static ShieldVisual visual(ShipAPI ship) {
        return ship==null ? null : (ShieldVisual)ship.getCustomData().get(VISUAL_KEY);
    }
    /** Used by compatibility subclasses; call once from their constructor, never each frame. */
    public static void claim(FilmShieldController controller) {
        ShipAPI ship=controller.getShip();
        if(find(ship)!=null) throw new IllegalStateException("Duplicate film shield controller");
        registerVisual(ship,controller);
        ship.setCustomData(CONTROLLER_KEY,controller);
    }
    /** Visual-only extension for an independent shield implementation. Existing owners are never replaced. */
    public static void registerVisual(ShipAPI ship,ShieldVisual visual) {
        if(ship==null||visual==null) throw new IllegalArgumentException("Ship and visual are required");
        ShieldVisual existing=visual(ship);
        if(existing!=null && existing!=visual) throw new IllegalStateException("A film shield visual already owns this ship");
        ship.setCustomData(VISUAL_KEY,visual);
    }
    /** Check before constructing any independent damage listener; false means this hullmod loses. */
    public static boolean registerVisual(ShipAPI ship,ShieldVisual visual,String hullmodId) {
        if(!isSelected(ship,hullmodId)) return false;
        registerVisual(ship,visual);
        return true;
    }
    public static void unregisterVisual(ShipAPI ship,ShieldVisual visual) {
        if(visual(ship)==visual) ship.removeCustomData(VISUAL_KEY);
    }
    public static void detach(ShipAPI ship) {
        FilmShieldController controller=find(ship);
        if(controller!=null) controller.dispose();
    }
    public static void release(FilmShieldController controller) {
        ShipAPI ship=controller.getShip();
        if(find(ship)==controller) ship.removeCustomData(CONTROLLER_KEY);
        unregisterVisual(ship,controller);
    }
    /** Safe in hull creation/refit: GL work is deferred to the render callback. */
    public static void ensureCombatServices() {
        if(Global.getCombatEngine()==null) return;
        FilmShieldRenderer.getInstance();
        FilmShieldUI.register();
    }
    /** Register a mod-owned GraphicsLib texture CSV for normal relief fallback. Call at application load. */
    public static void registerNormalTextureDataCSV(String path) { FilmShieldRenderer.registerNormalTextureDataCSV(path); }
    public static void attachModules(ShipAPI ship,ShieldProfile profile) {
        if(!profile.followParent || !ship.isShipWithModules()) return;
        FilmShieldController parent=find(ship);
        for(ShipAPI module:ship.getChildModulesCopy()) {
            if(module.isAlive()&&!module.isHulk() && find(module)==null && visual(module)==null
                    &&getLocalSelectedHullmodId(module)==null)
                attach(module,profile,parent==null?profile.hullmodId:parent.getHullmodId());
        }
    }
}
