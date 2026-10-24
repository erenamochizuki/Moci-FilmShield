import java.awt.Color;
import java.lang.reflect.*;
import java.util.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.*;
import org.lwjgl.util.vector.Vector2f;
import org.moci.filmshield.api.*;
import org.moci.filmshield.core.*;

/** Engine-interface contract tests. No Moci, BoxUtil, LunaLib, game window or GL context on the classpath. */
public final class FilmShieldContractTest {
    static int checks;
    static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    static void near(float expected,float actual,String message) { check(Math.abs(expected-actual)<0.001f,message+": "+actual); }
    static Object zero(Class<?> type) {
        if(type==boolean.class) return false; if(type==float.class) return 0f; if(type==int.class) return 0;
        if(type==double.class) return 0d; if(type==long.class) return 0L; return null;
    }
    interface Call { Object call(Method m,Object[] a); }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type,Call call) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->{
            if(m.getName().equals("hashCode")) return System.identityHashCode(p);
            if(m.getName().equals("equals")) return p==a[0];
            if(m.getName().equals("toString")) return type.getSimpleName()+"-test";
            return call.call(m,a==null?new Object[0]:a);
        });
    }
    static class Hull {
        boolean alive=true,hulk,removed,phased,overloaded,shieldOn=true,skip;
        boolean lastHard; float flux,arc=120,radius=90;
        Color inner=Color.BLUE,ring=Color.CYAN;
        float[][] armor={{100,100},{100,100}};
        final Map<String,Object> custom=new HashMap<>();
        final Map<String,MutableStat> stats=new HashMap<>();
        final List<Object> listeners=new ArrayList<>();
        final List<ShipAPI> modules=new ArrayList<>();
        ShipAPI parent;
        final Vector2f location=new Vector2f();
        final FluxTrackerAPI fluxTracker=proxy(FluxTrackerAPI.class,(m,a)->{
            switch(m.getName()) {
                case "isOverloadedOrVenting": return overloaded;
                case "increaseFlux": flux+=(Float)a[0]; lastHard=(Boolean)a[1]; return false;
                case "getCurrFlux": return flux;
                case "getMaxFlux": return 10000f;
            } return zero(m.getReturnType());
        });
        final MutableShipStatsAPI mutable=proxy(MutableShipStatsAPI.class,(m,a)->{
            if(m.getReturnType()==MutableStat.class) return stats.computeIfAbsent(m.getName(),k->new MutableStat(1f));
            return zero(m.getReturnType());
        });
        final ArmorGridAPI armorAPI=proxy(ArmorGridAPI.class,(m,a)->{
            if(m.getName().equals("getGrid")) return armor;
            if(m.getName().equals("setArmorValue")) { armor[(Integer)a[0]][(Integer)a[1]]=(Float)a[2]; return null; }
            return zero(m.getReturnType());
        });
        final ShieldAPI shield=proxy(ShieldAPI.class,(m,a)->{
            switch(m.getName()) {
                case "getArc":return arc; case "setArc":arc=(Float)a[0];return null;
                case "getRadius":return radius; case "setRadius":radius=(Float)a[0];return null;
                case "getInnerColor":return inner; case "setInnerColor":inner=(Color)a[0];return null;
                case "getRingColor":return ring; case "setRingColor":ring=(Color)a[0];return null;
                case "isSkipRendering":return skip; case "setSkipRendering":skip=(Boolean)a[0];return null;
                case "isOn":return shieldOn; case "getLocation":return location;
            } return zero(m.getReturnType());
        });
        final ShipHullSpecAPI hullSpec=proxy(ShipHullSpecAPI.class,(m,a)->{
            if(m.getName().equals("getShieldSpec")) return proxy(ShipHullSpecAPI.ShieldSpecAPI.class,(sm,sa)-> sm.getName().equals("getFluxPerDamageAbsorbed")?0.8f:zero(sm.getReturnType()));
            return zero(m.getReturnType());
        });
        final ShipAPI ship;
        Hull(boolean nativeShield) {
            ship=proxy(ShipAPI.class,(m,a)->{
                switch(m.getName()) {
                    case "getShield":return nativeShield?shield:null;
                    case "getHullSpec":return hullSpec;
                    case "getCustomData":return custom;
                    case "setCustomData":custom.put((String)a[0],a[1]);return null;
                    case "removeCustomData":custom.remove(a[0]);return null;
                    case "addListener":listeners.add(a[0]);return null;
                    case "removeListener":listeners.remove(a[0]);return null;
                    case "getFluxTracker":return fluxTracker;
                    case "getMutableStats":return mutable;
                    case "getArmorGrid":return armorAPI;
                    case "getHullSize":return ShipAPI.HullSize.FRIGATE;
                    case "getCollisionRadius":return 100f;
                    case "isAlive":return alive; case "isHulk":return hulk; case "wasRemoved":return removed;
                    case "isPhased":return phased;
                    case "getParentStation":return parent; case "isStationModule":return parent!=null;
                    case "isShipWithModules":return !modules.isEmpty(); case "getChildModulesCopy":return new ArrayList<>(modules);
                    case "getLocation":case "getVelocity":return location;
                    case "getFluxLevel":return flux/10000f;
                }
                return zero(m.getReturnType());
            });
        }
    }
    static class Hit {
        final MutableStat modifier=new MutableStat(1f);
        float amount=100,duration=.1f; boolean dps,soft,force; DamageType type=DamageType.ENERGY;
        final DamageAPI api=proxy(DamageAPI.class,(m,a)->{
            switch(m.getName()) {
                case "getModifier":return modifier;
                case "getDamage":case "getBaseDamage":return amount;
                case "isDps":return dps; case "getDpsDuration":return duration;
                case "isSoftFlux":return soft; case "isForceHardFlux":return force;
                case "getType":return type;
            } return zero(m.getReturnType());
        });
    }
    static ShieldProfile external(String id) { return ShieldProfile.builder(id).control(ShieldProfile.Control.EXTERNAL).build(); }
    static void reject(Runnable action,String label) { boolean failed=false;try{action.run();}catch(IllegalArgumentException|IllegalStateException e){failed=true;}check(failed,label); }
    public static void main(String[] args) {
        Global.setCombatEngine(null);
        reject(()->ShieldProfile.builder("bad").fluxPerDamage(Float.NaN).build(),"reject NaN");
        reject(()->ShieldProfile.builder("bad").fragmentationMultiplier(-1).build(),"reject negative multiplier");
        Hull h=new Hull(false); ShieldProfile profile=external("test");
        FilmShieldController c=FilmShield.attach(h.ship,profile);
        check(!c.isActive(),"external starts disabled");
        check(c==FilmShield.attach(h.ship,profile)&&h.listeners.size()==1,"idempotent attach");
        reject(()->FilmShield.attach(h.ship,external("other")),"different profile cannot double stack");
        reject(()->FilmShield.registerVisual(h.ship,()->true),"visual ownership cannot be silently replaced");
        c.setEnabled(true); check(c.isActive(),"external shieldless activation");
        BeamAPI beam=proxy(BeamAPI.class,(m,a)->zero(m.getReturnType()));
        Hit melee=new Hit(); c.modifyDamageTaken(beam,h.ship,melee.api,null,false);
        near(100,h.flux,"instantaneous beam-origin melee is not multiplied by duration");check(h.lastHard,"melee hard flux");
        near(.0001f,melee.modifier.getModifiedValue(),"shield intercepted hull damage");
        h.armor[0][0]=99; Hit beamHit=new Hit();beamHit.dps=true;
        c.modifyDamageTaken(beam,h.ship,beamHit.api,null,false);
        near(100,h.armor[0][0],"absorbed damage restores only saved armor");
        near(110,h.flux,"DPS uses duration");check(!h.lastHard,"DPS soft flux");
        Hit forced=new Hit();forced.dps=true;forced.force=true;c.modifyDamageTaken(beam,h.ship,forced.api,null,false);check(h.lastHard,"forced hard DPS");
        Hit soft=new Hit();soft.soft=true;c.modifyDamageTaken(null,h.ship,soft.api,null,false);check(!h.lastHard,"explicit soft projectile/custom hit");
        float before=h.flux; h.overloaded=true;
        check(c.modifyDamageTaken(null,h.ship,new Hit().api,null,false)==null,"overloaded bypass");near(before,h.flux,"no overloaded flux");h.overloaded=false;
        h.phased=true;check(!c.isActive(),"phasing bypass");h.phased=false;
        c.setEnabled(false);Hit bypass=new Hit();check(c.modifyDamageTaken(null,h.ship,bypass.api,null,false)==null,"disabled bypass");h.armor[0][0]=75;
        FilmShield.detach(h.ship);near(75,h.armor[0][0],"bypass armor damage not healed at detach");
        check(h.custom.isEmpty()&&h.listeners.isEmpty(),"detach removes ownership/listener");near(1,melee.modifier.getModifiedValue(),"detach cleans shared damage modifier");
        Hull nativeHull=new Hull(true);FilmShieldController nativeController=FilmShield.attach(nativeHull.ship,ShieldProfile.builder("native").build());
        near(0,nativeHull.arc,"native collision shield hidden");check(nativeHull.skip,"native render hidden");
        nativeController.modifyDamageTaken(null,nativeHull.ship,new Hit().api,null,false);near(80,nativeHull.flux,"native hull efficiency retained");
        nativeHull.shieldOn=false;check(!nativeController.isActive(),"native toggle respected");FilmShield.detach(nativeHull.ship);
        near(120,nativeHull.arc,"original arc restored");near(90,nativeHull.radius,"original radius restored");check(nativeHull.inner.equals(Color.BLUE)&&!nativeHull.skip,"original colors/render flag restored");
        Hull parent=new Hull(false),module=new Hull(false);module.parent=parent.ship;parent.modules.add(module.ship);
        FilmShieldController pc=FilmShield.attach(parent.ship,profile);pc.setEnabled(true);FilmShield.attachModules(parent.ship,profile);
        FilmShieldController mc=FilmShield.find(module.ship);check(mc!=null&&mc.isActive(),"module inherits activation");
        mc.modifyDamageTaken(null,module.ship,new Hit().api,null,false);near(100,parent.flux,"module charges parent");near(0,module.flux,"module does not charge self");
        pc.setEnabled(false);check(!mc.isActive(),"parent off disables module");
        FilmShield.detach(parent.ship);check(FilmShield.find(module.ship)==null,"parent detach cleans inherited modules");
        Hull filtered=new Hull(false);
        ShieldProfile filterProfile=ShieldProfile.builder("filter").control(ShieldProfile.Control.EXTERNAL)
            .hooks(new ShieldHooks() { public boolean shouldAbsorb(ShipAPI ship,Object source,DamageAPI damage,Vector2f point) { return source!=null; } }).build();
        FilmShieldController fc=FilmShield.attach(filtered.ship,filterProfile);fc.setEnabled(true);
        check(fc.modifyDamageTaken(null,filtered.ship,new Hit().api,null,false)==null,"integration hook can bypass interception");
        Hull free=new Hull(false);FilmShieldController freeShield=FilmShield.attach(free.ship,ShieldProfile.builder("free").control(ShieldProfile.Control.EXTERNAL).fluxPerDamage(0).build());freeShield.setEnabled(true);
        check(freeShield.modifyDamageTaken(null,free.ship,new Hit().api,null,false)!=null,"zero-cost shields still absorb");near(0,free.flux,"zero-cost shield generates no flux");
        Hull frag=new Hull(false);FilmShieldController fragShield=FilmShield.attach(frag.ship,external("frag"));fragShield.setEnabled(true);
        Hit fragHit=new Hit();fragHit.type=DamageType.FRAGMENTATION;fragShield.modifyDamageTaken(null,frag.ship,fragHit.api,null,false);near(25,frag.flux,"generic fragmentation uses type multiplier once");
        Hull legacyFrag=new Hull(false);FilmShieldController legacyShield=FilmShield.attach(legacyFrag.ship,ShieldProfile.builder("legacy").control(ShieldProfile.Control.EXTERNAL).fragmentationMultiplier(.25f).build());legacyShield.setEnabled(true);
        legacyShield.modifyDamageTaken(null,legacyFrag.ship,fragHit.api,null,false);near(6.25f,legacyFrag.flux,"Moci extra fragmentation multiplier retained");
        Map<String,Object> combatData=new HashMap<>();boolean[] paused={true};
        Global.setCombatEngine(proxy(CombatEngineAPI.class,(m,a)->{
            if(m.getName().equals("isPaused")) return paused[0];
            if(m.getName().equals("getCustomData")) return combatData;
            return zero(m.getReturnType());
        }));
        Hull life=new Hull(false);FilmShieldController lc=FilmShield.attach(life.ship,external("life"));lc.setEnabled(true);
        lc.advance(.1f);check(combatData.isEmpty(),"paused advance allocates no services");paused[0]=false;
        lc.advance(.1f);check(combatData.size()==2,"services register without a GL context");lc.advance(.1f);check(combatData.size()==2,"services do not duplicate");
        near(0,life.mutable.getEmpDamageTakenMult().getModifiedValue(),"active EMP protection");
        life.removed=true;lc.advance(.1f);check(FilmShield.find(life.ship)==null,"removed entity cleaned");near(1,life.mutable.getEmpDamageTakenMult().getModifiedValue(),"EMP modifier cleaned");
        Global.setCombatEngine(null);
        System.out.println("FilmShield contract checks passed: "+checks);
    }
}
