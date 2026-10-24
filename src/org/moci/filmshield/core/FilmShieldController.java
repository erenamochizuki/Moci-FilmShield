package org.moci.filmshield.core;
import java.awt.Color;
import java.util.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.*;
import com.fs.starfarer.api.loading.ProjectileSpawnType;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import org.moci.filmshield.api.*;

/** Surface interception engine. A native ShieldAPI is a control source, not a collision surface. */
public class FilmShieldController implements DamageTakenModifier, AdvanceableListener, ShieldVisual {
    public static final String KEY="filmshield_absorb";
    public static final Color TEXT_COLOR=new Color(255,155,255,255);
    public static final Color JITTER_COLOR=new Color(155,155,255,75);
    public static final Color JITTER_UNDER_COLOR=new Color(155,155,255,155);
    public enum ParamType { ARC, SHIP, PROJ, BEAM }
    protected final ShipAPI ship;
    protected final ShieldProfile profile;
    private final List<Hit> hits=new ArrayList<Hit>();
    private final Set<DamageAPI> modifiedDamage=Collections.newSetFromMap(new IdentityHashMap<DamageAPI,Boolean>());
    private final ShieldAPI originalShield;
    private final float originalArc,originalRadius;
    private final Color originalInner,originalRing;
    private final boolean originalSkip;
    private boolean enabled,disposed,pendingArmorRestore,servicesStarted;
    private float[][] armorGridCopy;
    public FilmShieldController(ShipAPI ship,ShieldProfile profile) {
        if(ship==null||profile==null) throw new IllegalArgumentException("Ship and profile are required");
        if(profile.control==ShieldProfile.Control.NATIVE_SHIELD && ship.getShield()==null
                && !(profile.followParent&&ship.isStationModule()&&ship.getParentStation()!=null))
            throw new IllegalArgumentException("Native control requires a shield; use EXTERNAL for shieldless ships");
        this.ship=ship; this.profile=profile;
        originalShield=ship.getShield();
        originalArc=originalShield==null?0:originalShield.getArc();
        originalRadius=originalShield==null?0:originalShield.getRadius();
        originalInner=originalShield==null?null:originalShield.getInnerColor();
        originalRing=originalShield==null?null:originalShield.getRingColor();
        originalSkip=originalShield!=null&&originalShield.isSkipRendering();
        FilmShield.claim(this);
        init(ship);
    }
    public void init(ShipAPI ignored) {
        if(originalShield!=null) {
            originalShield.setArc(0f);
            originalShield.setRadius(Math.max(10f,ship.getCollisionRadius()-ship.getHullSize().ordinal()*10f));
            originalShield.setSkipRendering(true);
            originalShield.setInnerColor(new Color(0,0,0,0));
            originalShield.setRingColor(new Color(0,0,0,0));
        }
    }
    public ShipAPI getShip() { return ship; }
    public ShieldProfile getProfile() { return profile; }
    /** External mode starts disabled. Ship systems/AI decide when it is enabled. */
    public void setEnabled(boolean value) { enabled=value; }
    public boolean isEnabled() { return enabled; }
    private ShipAPI fluxOwner() {
        if(profile.followParent&&ship.isStationModule()&&ship.getParentStation()!=null) return ship.getParentStation();
        return ship;
    }
    @Override public boolean isActive() {
        if(disposed||!ship.isAlive()||ship.isHulk()||ship.isPhased()) return false;
        ShipAPI owner=fluxOwner();
        if(owner!=ship) {
            FilmShieldController parent=FilmShield.find(owner);
            return parent!=null&&parent.isActive();
        }
        if(owner.getFluxTracker().isOverloadedOrVenting()) return false;
        return profile.control==ShieldProfile.Control.NATIVE_SHIELD
                ? owner.getShield()!=null&&owner.getShield().isOn() : enabled;
    }
    @Override public boolean showStatus() { return profile.showStatus; }
    @Override public String getStatusText() { return isActive()?profile.hudActiveText:profile.hudInactiveText; }
    @Override public Color getLowColor() { return profile.lowColor; }
    @Override public Color getHighColor() { return profile.highColor; }
    @Override public float getLevel(ShipAPI ignored) { return fluxOwner().getFluxLevel(); }
    @Override public List<? extends ShieldHit> getHits() { return Collections.unmodifiableList(hits); }
    /** Legacy access; prefer the read-only getHits(). */
    public List<Hit> getHitDatas() { return hits; }
    public static String getKey() { return KEY; }
    protected boolean checkSurvival() { return !ship.isAlive(); }
    protected ParamType getType(Object param,DamageAPI damage) {
        if(param instanceof DamagingProjectileAPI) return ParamType.PROJ;
        if(param instanceof BeamAPI) return ParamType.BEAM;
        if(param instanceof EmpArcEntityAPI) return ParamType.ARC;
        return ParamType.SHIP;
    }
    @Override public String modifyDamageTaken(Object param,CombatEntityAPI target,DamageAPI damage,Vector2f point,boolean shieldHit) {
        damage.getModifier().unmodify(profile.modifierId);
        // Finish the preceding absorbed hit before any following bypass hit can alter armor.
        finishArmorRestore();
        if(target!=ship||shieldHit||!isActive()||!profile.hooks.shouldAbsorb(ship,param,damage,point)) return null;
        ShipAPI owner=fluxOwner();
        ShipAPI source=param instanceof BeamAPI ? ((BeamAPI)param).getSource()
                : param instanceof DamagingProjectileAPI ? ((DamagingProjectileAPI)param).getSource() : null;
        boolean beam=param instanceof BeamAPI||damage.isDps();
        float factor=profile.fluxPerDamage;
        if(profile.control==ShieldProfile.Control.NATIVE_SHIELD) {
            if(owner.getHullSpec().getShieldSpec()==null) return null;
            factor*=owner.getHullSpec().getShieldSpec().getFluxPerDamageAbsorbed();
        }
        factor*=owner.getMutableStats().getShieldAbsorptionMult().getModifiedValue()
                *owner.getMutableStats().getShieldDamageTakenMult().getModifiedValue();
        if(owner!=ship&&profile.suppressSpecLessModuleProjectiles&&param instanceof DamagingProjectileAPI
                &&shouldSuppressFluxForProjectile((DamagingProjectileAPI)param)) factor=0;
        float flux=profile.hooks.modifyFlux(ship,param,damage,computeDamageToShield(damage,source,beam)*factor);
        if(!Float.isFinite(flux)||flux<0) return null;
        // Preserve instantaneous melee beam hits: duration applies only to DPS, and they generate hard flux.
        boolean hard=damage.isForceHardFlux()||(!damage.isDps()&&!damage.isSoftFlux())||(!beam&&!damage.isSoftFlux());
        saveArmor();
        pendingArmorRestore=true;
        owner.getFluxTracker().increaseFlux(flux,hard);
        damage.getModifier().modifyMult(profile.modifierId,0.0001f); // Engine requires a nonzero damage multiplier.
        modifiedDamage.add(damage);
        CombatEngineAPI engine=Global.getCombatEngine();
        if(point!=null&&engine!=null) {
            if(param instanceof DamagingProjectileAPI) createHitRipple(point,owner.getVelocity(),flux,damage.getType(),
                    VectorUtils.getFacing(VectorUtils.getDirectionalVector(owner.getLocation(),point)),Math.max(1f,owner.getCollisionRadius()));
            if(param instanceof DamagingProjectileAPI||beam) {
                if(hits.size()>=128) hits.remove(0);
                hits.add(createHit(point,beam?0.8f:1f,MathUtils.clamp(flux/(beam?2f:4f),10f,40f)));
            }
            engine.addFloatingDamageText(point,flux,TEXT_COLOR,ship,damage.getStats()==null?null:damage.getStats().getEntity());
        }
        profile.hooks.onAbsorbed(ship,param,flux,hard);
        return profile.modifierId;
    }
    @Override public void advance(float amount) {
        if(disposed) return;
        if(!ship.isAlive()||ship.isHulk()||ship.wasRemoved()) { dispose(); return; }
        CombatEngineAPI engine=Global.getCombatEngine();
        if(engine==null||engine.isPaused()) return;
        if(!servicesStarted) { FilmShield.ensureCombatServices(); servicesStarted=true; }
        finishArmorRestore();
        for(DamageAPI damage:modifiedDamage) damage.getModifier().unmodify(profile.modifierId);
        modifiedDamage.clear();
        for(Iterator<Hit> it=hits.iterator();it.hasNext();) { Hit hit=it.next(); hit.advance(amount); if(hit.shouldExpire()) it.remove(); }
        attachModules();
        if(isActive()) ship.getMutableStats().getEmpDamageTakenMult().modifyMult(profile.modifierId,0f);
        else ship.getMutableStats().getEmpDamageTakenMult().unmodify(profile.modifierId);
        if(profile.showStatus&&engine.getPlayerShip()==ship) engine.maintainStatusForPlayerShip(profile.modifierId,
                "graphics/icons/hullsys/fortress_shield.png",profile.title,isActive()?profile.activeText:profile.inactiveText,!isActive());
    }
    protected Hit createHit(Vector2f point,float time,float size) { return new Hit(ship,point,time,size); }
    protected void attachModules() { FilmShield.attachModules(ship,profile); }
    public void dispose() {
        if(disposed) return;
        finishArmorRestore();
        disposed=true;
        for(DamageAPI damage:modifiedDamage) damage.getModifier().unmodify(profile.modifierId);
        modifiedDamage.clear(); hits.clear();
        ship.getMutableStats().getEmpDamageTakenMult().unmodify(profile.modifierId);
        if(originalShield!=null&&ship.getShield()==originalShield) {
            originalShield.setArc(originalArc); originalShield.setRadius(originalRadius);
            originalShield.setSkipRendering(originalSkip); originalShield.setInnerColor(originalInner); originalShield.setRingColor(originalRing);
        }
        if(profile.followParent&&ship.isShipWithModules()) for(ShipAPI module:ship.getChildModulesCopy()) {
            FilmShieldController child=FilmShield.find(module);
            if(child!=null&&child!=this&&child.profile.followParent&&child.profile.id.equals(profile.id)) child.dispose();
        }
        FilmShield.release(this);
        ship.removeListener(this);
    }
    private void finishArmorRestore() { if(pendingArmorRestore) { retainArmor(); pendingArmorRestore=false; } }
    public void saveArmor() {
        ArmorGridAPI armor=ship.getArmorGrid();
        if(armor==null) return;
        float[][] grid=armor.getGrid();
        if(armorGridCopy==null||armorGridCopy.length!=grid.length) armorGridCopy=new float[grid.length][];
        for(int x=0;x<grid.length;x++) {
            if(armorGridCopy[x]==null||armorGridCopy[x].length!=grid[x].length) armorGridCopy[x]=new float[grid[x].length];
            System.arraycopy(grid[x],0,armorGridCopy[x],0,grid[x].length);
        }
    }
    public void retainArmor() {
        ArmorGridAPI armor=ship.getArmorGrid();
        if(armor==null||armorGridCopy==null) return;
        float[][] grid=armor.getGrid();
        for(int x=0;x<Math.min(grid.length,armorGridCopy.length);x++)
            for(int y=0;y<Math.min(grid[x].length,armorGridCopy[x].length);y++)
                if(grid[x][y]<armorGridCopy[x][y]) armor.setArmorValue(x,y,armorGridCopy[x][y]);
    }
    public void immuneEMPEffects(float level) {
        ship.getMutableStats().getEmpDamageTakenMult().modifyMult(profile.modifierId,level<0.25f?(0.25f-level)*4f:0f);
    }
    private float computeDamageToShield(DamageAPI damage, ShipAPI source, boolean isBeam) {
        float base = damage.getDamage();
        // A beam may also be the source of an instantaneous melee hit.
        // Only DPS damage is multiplied by a time slice.
        if (damage.isDps()) {
            if (damage.getDamage() > 0) {
                base = damage.getDpsDuration() * damage.getDamage();
            }
        }
        base *= damage.getType().getShieldMult();
        if (base == 0) {
            return 0;
        }
        float mult = 1;
        if (source != null) {
            switch (ship.getHullSize()) {
                case DEFAULT:
                    break;
                case FIGHTER:
                    mult *= source.getMutableStats().getDamageToFighters().getModifiedValue();
                    break;
                case FRIGATE:
                    mult *= source.getMutableStats().getDamageToFrigates().getModifiedValue();
                    break;
                case DESTROYER:
                    mult *= source.getMutableStats().getDamageToDestroyers().getModifiedValue();
                    break;
                case CRUISER:
                    mult *= source.getMutableStats().getDamageToCruisers().getModifiedValue();
                    break;
                case CAPITAL_SHIP:
                    mult *= source.getMutableStats().getDamageToCapital().getModifiedValue();
                    break;
            }
        }
        switch (damage.getType()) {
            case KINETIC:
                mult *= ship.getMutableStats().getKineticDamageTakenMult().getModifiedValue();
                break;
            case HIGH_EXPLOSIVE:
                mult *= ship.getMutableStats().getHighExplosiveDamageTakenMult().getModifiedValue();
                break;
            case FRAGMENTATION:
                mult *= ship.getMutableStats().getFragmentationDamageTakenMult().getModifiedValue();
                mult *= profile.fragmentationMultiplier;
                break;
            case ENERGY:
                mult *= ship.getMutableStats().getEnergyDamageTakenMult().getModifiedValue();
                break;
            case OTHER:
                break;
        }
        if (isBeam) {
            mult *= ship.getMutableStats().getBeamDamageTakenMult().getModifiedValue();
        } else {
            mult *= ship.getMutableStats().getProjectileDamageTakenMult().getModifiedValue();
        }
        return base * mult;
    }

    private boolean shouldSuppressFluxForProjectile(DamagingProjectileAPI projectile) {
        if (projectile == null) {
            return false;
        }
        if (projectile instanceof MissileAPI || projectile.getSpawnType() == ProjectileSpawnType.MISSILE) {
            return false;
        }
        if (projectile.getExplosionSpecIfExplosion() != null) {
            return false;
        }
        return projectile.getProjectileSpec() == null;
    }

    private void createHitRipple(Vector2f location, Vector2f velocity, float damage, DamageType type, float direction,
                                 float shieldRadius) {
        float dmg = damage;
        if (type == DamageType.FRAGMENTATION) {
            dmg *= 0.25f;
        }
        if (type == DamageType.HIGH_EXPLOSIVE) {
            dmg *= 0.5f;
        }
        if (type == DamageType.KINETIC) {
            dmg *= 2f;
        }

        if (dmg < 75f) {
            return;
        }

        float fadeTime = (float) Math.pow(dmg, 0.25) * 0.1f;
        float size = (float) Math.pow(dmg, 0.3333333) * 8f;

        float ratio = Math.min(size / shieldRadius, 1f);
        float arc = 90f - ratio * 14.54136f; // Don't question the magic number

        float start1 = direction - arc;
        if (start1 < 0f) {
            start1 += 360f;
        }
        float end1 = direction + arc;
        if (end1 >= 360f) {
            end1 -= 360f;
        }

        float start2 = direction + arc;
        if (start2 < 0f) {
            start2 += 360f;
        }
        float end2 = direction - arc;
        if (end2 >= 360f) {
            end2 -= 360f;
        }

        RippleDistortion ripple = new RippleDistortion(location, velocity);
        ripple.setSize(size);
        ripple.setIntensity(size * 0.2f);
        ripple.setFrameRate(60f / fadeTime);
        ripple.fadeInSize(fadeTime * 1.2f);
        ripple.fadeOutIntensity(fadeTime);
        ripple.setSize(size * 0.2f);
        ripple.setArc(start1, end1);
        DistortionShader.addDistortion(ripple);

        ripple = new RippleDistortion(location, velocity);
        ripple.setSize(size);
        ripple.setIntensity(size * 0.05f);
        ripple.setFrameRate(60f / fadeTime);
        ripple.fadeInSize(fadeTime * 1.2f);
        ripple.fadeOutIntensity(fadeTime);
        ripple.setSize(size * 0.2f);
        ripple.setArc(start2, end2);
        DistortionShader.addDistortion(ripple);
    }

    public static class Hit implements ShieldHit {
        private Vector2f rel;
        private float time;
        private final float size;
        private final float orgT;

        public Hit(ShipAPI ship, Vector2f location, float time, float size){
            this.rel = Vector2f.sub(location, ship.getLocation(), null);
            VectorUtils.rotate(rel, -ship.getFacing());
            this.orgT = time;
            this.time = time;
            this.size = size;
        }

        public void advance(float amount){
            time -= amount;
        }

        public boolean shouldExpire(){
            return time <= 0;
        }

        public float getSize() {
            return size;
        }

        public Vector2f getRel() {
            return new Vector2f(rel);
        }

        public float getLevel(){
            return (orgT == 0) ? 0 : time / orgT;
        }
    }

}
