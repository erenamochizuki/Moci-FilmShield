import java.awt.Color;
import java.lang.reflect.*;
import java.util.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
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
        final List<String> hullmods=new ArrayList<>();
        final Set<String> suppressed=new HashSet<>();
        final ShipVariantAPI variant=proxy(ShipVariantAPI.class,(m,a)->{
            if(m.getName().equals("getHullMods")) return hullmods;
            if(m.getName().equals("getSuppressedMods")) return suppressed;
            return zero(m.getReturnType());
        });
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
                    case "getVariant":return variant;
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
    static void priorityTests() {
        Map<String,HullModEffect> effects=new HashMap<>();
        for(String id:Arrays.asList("moci","vector","red")) effects.put(id,new FilmShieldHullmod());
        effects.put("ordinary",new BaseHullMod());
        Global.setSettings(proxy(SettingsAPI.class,(m,a)->{
            if(m.getName().equals("getHullModSpec")) return proxy(HullModSpecAPI.class,(sm,sa)->
                    sm.getName().equals("getEffect")?effects.get((String)a[0]):zero(sm.getReturnType()));
            return zero(m.getReturnType());
        }));
        String[][] orders={{"moci","vector","red"},{"moci","red","vector"},{"vector","moci","red"},
                {"vector","red","moci"},{"red","moci","vector"},{"red","vector","moci"}};
        for(String[] order:orders) {
            Hull h=new Hull(true); h.hullmods.add("ordinary"); h.hullmods.addAll(Arrays.asList(order));
            // Call in reverse order: priority is independent of callback order and mod load order.
            for(int i=order.length-1;i>=0;i--) {
                ShieldProfile p=ShieldProfile.builder(order[i]).fluxPerDamage(i+1).build();
                FilmShieldController c=FilmShield.attach(h.ship,p,order[i]);
                check((c!=null)==(i==0),"only list-first provider attaches: "+Arrays.toString(order));
            }
            FilmShieldController winner=FilmShield.find(h.ship);
            check(winner.getHullmodId().equals(order[0])&&h.listeners.size()==1,"one damage listener for three mods");
            check(FilmShield.visual(h.ship)==winner,"one visual owner");
            winner.modifyDamageTaken(null,h.ship,new Hit().api,null,false);
            near(80,h.flux,"only winner's efficiency applies");
            h.shieldOn=false;check(!winner.isActive(),"inactive winner never enables a runner-up");
            FilmShield.detach(h.ship);near(120,h.arc,"selection preserves native shield restoration");
        }
        Hull callbacks=new Hull(true);callbacks.hullmods.addAll(Arrays.asList("vector","red","moci"));
        effects.get("moci").applyEffectsAfterShipCreation(callbacks.ship,"moci");
        effects.get("red").applyEffectsAfterShipCreation(callbacks.ship,"red");
        check(callbacks.listeners.isEmpty()&&!callbacks.skip,"losing hullmod callbacks leave shield untouched");
        effects.get("vector").applyEffectsAfterShipCreation(callbacks.ship,"vector");
        effects.get("vector").applyEffectsAfterShipCreation(callbacks.ship,"vector");
        check(callbacks.listeners.size()==1&&"vector".equals(FilmShield.find(callbacks.ship).getHullmodId()),"standard hullmod callbacks arbitrate and remain idempotent");
        FilmShield.detach(callbacks.ship);
        Hull h=new Hull(true);h.hullmods.addAll(Arrays.asList("red","vector","moci"));h.suppressed.add("red");
        check("vector".equals(FilmShield.getSelectedHullmodId(h.ship)),"suppressed provider skipped");
        check(FilmShield.attach(h.ship,external("red"),"red")==null&&h.listeners.isEmpty(),"suppressed loser has no side effects");
        ShieldProfile bound=ShieldProfile.builder("moci").hullmodId("moci").build();
        check(FilmShield.attach(h.ship,bound)==null,"legacy attach honors profile binding");
        FilmShieldController inactive=new FilmShieldController(h.ship,bound);
        check(!inactive.isActive()&&FilmShield.visual(h.ship)==null,"direct losing constructor does not claim");
        near(120,h.arc,"direct losing constructor does not hide native shield");
        Hit shared=new Hit();shared.modifier.modifyMult(bound.modifierId,.5f);
        check(inactive.modifyDamageTaken(null,h.ship,shared.api,null,false)==null,"direct losing listener stays inactive");
        near(.5f,shared.modifier.getModifiedValue(),"losing compatibility listener cannot erase winner's damage modifier");
        check(!FilmShield.registerVisual(h.ship,()->true,"moci"),"visual-only loser rejected before registration");
        ShieldVisual visual=()->true;
        check(FilmShield.registerVisual(h.ship,visual,"vector"),"visual-only winner can register");
        check(FilmShield.attach(h.ship,bound)==null&&FilmShield.visual(h.ship)==visual,"losing damage provider cannot replace visual winner");
        Hull unknown=new Hull(true);unknown.hullmods.add("ordinary");
        check(FilmShield.attach(unknown.ship,bound)==null,"absent hullmod cannot claim ownership");
        Hull parent=new Hull(false),child=new Hull(false);parent.hullmods.add("moci");child.parent=parent.ship;parent.modules.add(child.ship);
        ShieldProfile pp=ShieldProfile.builder("parent").hullmodId("moci").control(ShieldProfile.Control.EXTERNAL).build();
        FilmShieldController pc=FilmShield.attach(parent.ship,pp);pc.setEnabled(true);
        FilmShield.attachModules(parent.ship,pp);
        check(FilmShield.find(child.ship)!=null&&FilmShield.find(child.ship).isActive(),"unconfigured module inherits selected parent");
        FilmShield.detach(parent.ship);
        Hull local=new Hull(false);local.parent=parent.ship;local.hullmods.add("red");parent.modules.clear();parent.modules.add(local.ship);
        pc=FilmShield.attach(parent.ship,pp);pc.setEnabled(true);FilmShield.attachModules(parent.ship,pp);
        check(FilmShield.find(local.ship)==null,"parent cannot preempt a module's own pending hullmod callback");
        FilmShieldController own=FilmShield.attach(local.ship,external("local"),"red");own.setEnabled(true);
        own.modifyDamageTaken(null,local.ship,new Hit().api,null,false);
        near(100,local.flux,"independent module charges own flux");near(0,parent.flux,"independent module does not charge another mod's parent shield");
        FilmShield.detach(parent.ship);check(FilmShield.find(local.ship)==own,"parent cleanup preserves module's independent provider");
        Global.setSettings(null);
    }
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
        priorityTests();
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
