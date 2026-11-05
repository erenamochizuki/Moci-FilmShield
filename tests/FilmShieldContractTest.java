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
        int arcWrites;
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
                case "getArc":return arc; case "setArc":arcWrites++;arc=(Float)a[0];return null;
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
    static void nativeArcTests() {
        for(float invalid:new float[]{-1f,360.1f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.MAX_VALUE})
            reject(()->ShieldProfile.builder("invalid_arc").nativeShieldArc(invalid),"reject invalid native arc: "+invalid);
        ShieldProfile defaults=ShieldProfile.builder("arc_default").build();
        check(defaults.overrideNativeShieldArc&&defaults.nativeShieldArc==0f,"legacy profiles still override arc to zero");
        check(!ShieldProfile.builder("preserve_last").nativeShieldArc(60f).preserveNativeShieldArc().build().overrideNativeShieldArc,
                "preserve after override wins");
        check(ShieldProfile.builder("override_last").preserveNativeShieldArc().nativeShieldArc(60f).build().overrideNativeShieldArc,
                "override after preserve wins");
        for(ShieldProfile.Control control:ShieldProfile.Control.values()) {
            Hull preserved=new Hull(true);
            ShieldProfile keep=ShieldProfile.builder("arc_preserve").control(control).preserveNativeShieldArc().build();
            FilmShieldController c=FilmShield.attach(preserved.ship,keep);c.setEnabled(true);
            near(120,preserved.arc,"preserve mode retains native arc: "+control);
            check(preserved.arcWrites==0,"preserve attach does not write arc");
            check(preserved.skip&&preserved.radius==80f,"arc option leaves existing rendering/radius behavior intact");
            preserved.shield.setArc(75f); // A different mod changes the arc after attachment.
            c.advance(.1f);
            check(preserved.arcWrites==1,"advance does not overwrite third-party arc");
            Hit nativeHit=new Hit();
            check(c.modifyDamageTaken(null,preserved.ship,nativeHit.api,null,true)==null,"native shield hits bypass film settlement");
            near(0,preserved.flux,"native shield hit adds no film flux");
            near(1,nativeHit.modifier.getModifiedValue(),"native shield hit receives no film damage modifier");
            Hit hullHit=new Hit();c.modifyDamageTaken(null,preserved.ship,hullHit.api,null,false);
            near(control==ShieldProfile.Control.NATIVE_SHIELD?80f:100f,preserved.flux,"film still absorbs hull hits with native arc retained");
            FilmShield.detach(preserved.ship);
            near(75,preserved.arc,"preserve detach retains third-party arc");
            check(preserved.arcWrites==1,"preserve detach never writes arc");
            near(90,preserved.radius,"preserve mode still restores radius");
            check(!preserved.skip&&preserved.inner.equals(Color.BLUE),"preserve mode still restores rendering");
            for(float degrees:new float[]{0f,0.1f,90f,360f}) {
                Hull custom=new Hull(true);
                ShieldProfile p=ShieldProfile.builder("arc_custom").control(control).nativeShieldArc(degrees).build();
                FilmShieldController customController=FilmShield.attach(custom.ship,p);
                near(degrees,custom.arc,"custom native arc applied: "+control);
                check(FilmShield.attach(custom.ship,p)==customController&&custom.arcWrites==1,"repeated attach does not reapply arc");
                FilmShield.detach(custom.ship);near(120,custom.arc,"custom native arc restores original on detach");
                check(custom.arcWrites==2,"custom arc written once on attach and once on detach");
            }
        }
        Hull parent=new Hull(true),module=new Hull(true);
        module.parent=parent.ship;parent.modules.add(module.ship);module.arc=45f;
        ShieldProfile inherited=ShieldProfile.builder("arc_modules").nativeShieldArc(30f).build();
        FilmShield.attach(parent.ship,inherited);FilmShield.attachModules(parent.ship,inherited);
        near(30,parent.arc,"parent custom arc");near(30,module.arc,"inherited module custom arc");
        FilmShield.detach(parent.ship);
        near(120,parent.arc,"parent restores its original arc");near(45,module.arc,"module restores its own original arc");
        ShieldProfile keep=ShieldProfile.builder("preserve_modules").preserveNativeShieldArc().build();
        FilmShield.attach(parent.ship,keep);FilmShield.attachModules(parent.ship,keep);
        module.arc=55f;FilmShield.detach(parent.ship);
        near(55,module.arc,"inherited preserve mode retains module changes");
        Hull shieldless=new Hull(false);
        FilmShieldController external=FilmShield.attach(shieldless.ship,ShieldProfile.builder("arc_shieldless")
                .control(ShieldProfile.Control.EXTERNAL).nativeShieldArc(360f).build());
        external.setEnabled(true);check(external.isActive(),"arc override is harmless without ShieldAPI");FilmShield.detach(shieldless.ship);
    }
    /** 1.3.0: a native-shield profile on a ship whose ShieldAPI is missing (e.g. removed by another hullmod) must not crash ship creation. */
    static void shieldlessNativeTests() {
        List<String> warnings=new ArrayList<>();
        org.apache.log4j.AppenderSkeleton capture=new org.apache.log4j.AppenderSkeleton() {
            @Override protected void append(org.apache.log4j.spi.LoggingEvent event) {
                if(event.getLevel()==org.apache.log4j.Level.WARN) warnings.add(event.getRenderedMessage());
            }
            @Override public boolean requiresLayout() { return false; }
            @Override public void close() {}
        };
        org.apache.log4j.Logger root=org.apache.log4j.Logger.getRootLogger();
        root.addAppender(capture);
        try {
            Hull bare=new Hull(false);
            ShieldProfile nativeProfile=ShieldProfile.builder("native_shieldless").build();
            FilmShieldController inert=FilmShield.attach(bare.ship,nativeProfile);
            check(inert!=null,"native profile without ShieldAPI returns a controller instead of throwing");
            check(FilmShield.find(bare.ship)==null&&FilmShield.visual(bare.ship)==null,"inert native controller does not own the ship");
            check(bare.listeners.isEmpty()&&bare.custom.isEmpty(),"inert native controller adds no listener or custom data");
            check(warnings.size()==1&&warnings.get(0).contains("native_shieldless"),"missing native shield is logged once with the profile id");
            inert.setEnabled(true);inert.advance(.1f);inert.immuneEMPEffects(0f);
            check(!inert.isActive(),"inert native controller never activates");
            Hit hit=new Hit();
            check(inert.modifyDamageTaken(null,bare.ship,hit.api,null,false)==null,"inert native controller ignores hits");
            near(1,hit.modifier.getModifiedValue(),"inert native controller applies no damage modifier");
            near(0,bare.flux,"inert native controller adds no flux");
            check(bare.stats.isEmpty(),"inert native controller touches no ship stats");
            FilmShieldController direct=new FilmShieldController(bare.ship,nativeProfile,null);
            check(!direct.isActive()&&FilmShield.find(bare.ship)==null&&warnings.size()==2,"direct native construction without ShieldAPI is inert too");
            FilmShieldController owner=FilmShield.attach(bare.ship,external("external_after_native"));
            check(FilmShield.find(bare.ship)==owner&&bare.listeners.size()==1,"an EXTERNAL owner can still take the shieldless ship");
            FilmShield.detach(bare.ship);
            check(FilmShield.find(bare.ship)==null&&bare.listeners.isEmpty(),"EXTERNAL owner detaches cleanly");
            Hull disabled=new Hull(false);disabled.hullmods.add(FilmShield.DISABLE_HULLMOD_ID);
            FilmShield.attach(disabled.ship,nativeProfile);
            check(warnings.size()==2,"a disabled ship skips silently without the missing-shield warning");
        } finally {
            root.removeAppender(capture);
        }
    }
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
    static void disableTests() {
        Map<String,HullModEffect> effects=new HashMap<>();
        effects.put("moci",new FilmShieldHullmod());effects.put("red",new FilmShieldHullmod());
        effects.put("ordinary",new BaseHullMod());
        HullModEffect toggle=new FilmShieldDisableHullmod();effects.put(FilmShield.DISABLE_HULLMOD_ID,toggle);
        Global.setSettings(proxy(SettingsAPI.class,(m,a)->{
            if(m.getName().equals("getHullModSpec")) return proxy(HullModSpecAPI.class,(sm,sa)->
                    sm.getName().equals("getEffect")?effects.get((String)a[0]):zero(sm.getReturnType()));
            return zero(m.getReturnType());
        }));
        check(!(toggle instanceof FilmShieldProvider),"disable switch is not a provider");
        for(boolean switchFirst:new boolean[]{true,false}) {
            Hull o=new Hull(true);
            o.hullmods.addAll(switchFirst?Arrays.asList(FilmShield.DISABLE_HULLMOD_ID,"moci"):Arrays.asList("moci",FilmShield.DISABLE_HULLMOD_ID));
            check(FilmShield.isDisabled(o.ship)&&FilmShield.getSelectedHullmodId(o.ship)==null,"switch wins regardless of order: "+switchFirst);
        }
        Hull d=new Hull(true);d.hullmods.addAll(Arrays.asList("moci",FilmShield.DISABLE_HULLMOD_ID));
        check("moci".equals(FilmShield.getLocalProviderHullmodId(d.ship)),"raw provider lookup ignores the switch");
        check(FilmShield.getLocalSelectedHullmodId(d.ship)==null&&!FilmShield.isSelected(d.ship,"moci"),"disabled ship selects nothing");
        effects.get("moci").applyEffectsAfterShipCreation(d.ship,"moci");
        check(d.listeners.isEmpty()&&d.custom.isEmpty(),"standard hullmod callback skipped");
        near(120,d.arc,"disabled ship keeps native arc");near(90,d.radius,"disabled ship keeps native radius");
        check(!d.skip&&d.inner.equals(Color.BLUE)&&d.ring.equals(Color.CYAN),"disabled ship keeps native render and colors");
        check(FilmShield.attach(d.ship,ShieldProfile.builder("moci").hullmodId("moci").build())==null,"bound attach returns null");
        check(FilmShield.attach(d.ship,external("red"),"red")==null,"explicit bound attach returns null");
        FilmShieldController inert=FilmShield.attach(d.ship,ShieldProfile.builder("unbound").build());
        check(inert!=null&&!inert.isActive(),"unbound attach gets an inert controller");
        check(d.listeners.isEmpty()&&FilmShield.find(d.ship)==null&&FilmShield.visual(d.ship)==null,"inert controller has no listener or ownership");
        inert.setEnabled(true);check(!inert.isActive(),"inert controller cannot be enabled");
        Hit hit=new Hit();
        check(inert.modifyDamageTaken(null,d.ship,hit.api,null,false)==null,"inert controller intercepts nothing");
        near(0,d.flux,"inert controller charges no flux");near(1,hit.modifier.getModifiedValue(),"inert controller leaves damage alone");
        inert.immuneEMPEffects(.5f);near(1,d.mutable.getEmpDamageTakenMult().getModifiedValue(),"inert controller adds no EMP modifier");
        d.armor[0][0]=50;inert.saveArmor();d.armor[0][0]=10;inert.retainArmor();near(10,d.armor[0][0],"inert controller never restores armor");
        FilmShieldController direct=new FilmShieldController(d.ship,ShieldProfile.builder("direct").build());
        check(!direct.isActive()&&FilmShield.find(d.ship)==null,"direct constructor does not claim");near(120,d.arc,"direct constructor keeps native arc");
        FilmShield.claim(direct);check(FilmShield.find(d.ship)==null&&FilmShield.visual(d.ship)==null,"claim is a no-op");
        check(!FilmShield.registerVisual(d.ship,()->true,"moci"),"bound visual rejected");
        FilmShield.registerVisual(d.ship,()->true);check(FilmShield.visual(d.ship)==null,"unbound visual ignored");
        Hull bare=new Hull(false);bare.hullmods.add(FilmShield.DISABLE_HULLMOD_ID);
        FilmShieldController bareController=FilmShield.attach(bare.ship,ShieldProfile.builder("bare").build());
        check(bareController!=null&&!bareController.isActive(),"native profile on a disabled shieldless ship does not throw");
        Hull station=new Hull(false),child=new Hull(false),ownModule=new Hull(false);
        station.hullmods.addAll(Arrays.asList("moci",FilmShield.DISABLE_HULLMOD_ID));
        child.parent=station.ship;ownModule.parent=station.ship;ownModule.hullmods.add("red");
        station.modules.add(child.ship);station.modules.add(ownModule.ship);
        ShieldProfile sp=ShieldProfile.builder("station").hullmodId("moci").control(ShieldProfile.Control.EXTERNAL).build();
        check(FilmShield.isDisabled(child.ship)&&FilmShield.getSelectedHullmodId(child.ship)==null,"station switch covers modules");
        check(FilmShield.attach(station.ship,sp)==null,"disabled station attaches nothing");
        check(FilmShield.attach(ownModule.ship,external("own"),"red")==null&&ownModule.listeners.isEmpty(),"module's own provider skipped under a disabled station");
        FilmShield.attachModules(station.ship,sp);FilmShield.attachModules(station.ship,external("unbound_station"));
        check(FilmShield.find(child.ship)==null&&child.listeners.isEmpty(),"attachModules skips disabled modules");
        Hull mother=new Hull(false),on=new Hull(false),off=new Hull(false);mother.hullmods.add("moci");
        on.parent=mother.ship;off.parent=mother.ship;off.hullmods.add(FilmShield.DISABLE_HULLMOD_ID);
        mother.modules.add(on.ship);mother.modules.add(off.ship);
        FilmShieldController mc=FilmShield.attach(mother.ship,sp);mc.setEnabled(true);FilmShield.attachModules(mother.ship,sp);
        check(FilmShield.find(off.ship)==null&&off.listeners.isEmpty()&&!FilmShield.isSelected(off.ship,"moci"),"module switch skips only that module");
        check(FilmShield.find(on.ship)!=null&&FilmShield.find(on.ship).isActive(),"sibling module still inherits the station shield");
        FilmShield.detach(mother.ship);check(FilmShield.find(on.ship)==null,"station cleanup unchanged");
        Hull suppressedSwitch=new Hull(true);suppressedSwitch.hullmods.addAll(Arrays.asList(FilmShield.DISABLE_HULLMOD_ID,"moci"));
        suppressedSwitch.suppressed.add(FilmShield.DISABLE_HULLMOD_ID);
        check(!FilmShield.isDisabled(suppressedSwitch.ship)&&"moci".equals(FilmShield.getSelectedHullmodId(suppressedSwitch.ship)),"suppressed switch is inactive");
        Hull plain=new Hull(true);plain.hullmods.add("ordinary");
        Hull provider=new Hull(true);provider.hullmods.add("moci");
        check(toggle.isApplicableToShip(plain.ship)&&toggle.isApplicableToShip(null),"switch never blocks a built-in");
        check(!toggle.showInRefitScreenModPickerFor(plain.ship)&&!toggle.showInRefitScreenModPickerFor(provider.ship)
                &&!toggle.showInRefitScreenModPickerFor(d.ship)&&!toggle.showInRefitScreenModPickerFor(null),"switch is never offered to players");
        Hull loop=new Hull(false);loop.parent=loop.ship;
        check(!FilmShield.isDisabled(loop.ship)&&FilmShield.getSelectedHullmodId(loop.ship)==null,"self-parent terminates");
        Global.setSettings(null);
        Hull noSettings=new Hull(true);noSettings.hullmods.add(FilmShield.DISABLE_HULLMOD_ID);
        FilmShieldController noSettingsController=FilmShield.attach(noSettings.ship,ShieldProfile.builder("no_settings").build());
        check(FilmShield.isDisabled(noSettings.ship)&&noSettings.listeners.isEmpty()&&!noSettings.skip,"switch detection needs no settings");
        near(120,noSettings.arc,"no-settings disabled ship keeps native arc");check(!noSettingsController.isActive(),"no-settings controller inert");
        check(!FilmShield.isDisabled(null),"null ship is not disabled");
    }
    static List<String> csvRow(String line) {
        List<String> out=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;
        for(int i=0;i<line.length();i++) {
            char ch=line.charAt(i);
            if(quoted) { if(ch!='"') cell.append(ch); else if(i+1<line.length()&&line.charAt(i+1)=='"') { cell.append('"');i++; } else quoted=false; }
            else if(ch=='"') quoted=true;
            else if(ch==',') { out.add(cell.toString());cell.setLength(0); }
            else cell.append(ch);
        }
        out.add(cell.toString());return out;
    }
    /** hull_mods.csv must name loadable effect classes and existing sprites; a typo here only fails in game. */
    static void dataTests() throws Exception {
        java.nio.file.Path root=java.nio.file.Paths.get(FilmShieldContractTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent();
        List<String> lines=java.nio.file.Files.readAllLines(root.resolve("data/hullmods/hull_mods.csv"),java.nio.charset.StandardCharsets.UTF_8);
        List<String> header=csvRow(lines.get(0));
        Map<String,List<String>> rows=new HashMap<>();
        for(String line:lines.subList(1,lines.size())) if(!line.trim().isEmpty()) {
            List<String> row=csvRow(line);check(row.size()==header.size(),"hull_mods.csv column count: "+line);rows.put(row.get(1),row);
        }
        for(String id:Arrays.asList("filmshield_surface",FilmShield.DISABLE_HULLMOD_ID)) {
            List<String> row=rows.get(id);check(row!=null,"hull_mods.csv row: "+id);
            check(HullModEffect.class.isAssignableFrom(Class.forName(row.get(header.indexOf("script")))),"script is a hullmod effect: "+id);
            check(java.nio.file.Files.isRegularFile(root.resolve(row.get(header.indexOf("sprite")))),"sprite exists: "+id);
        }
        List<String> toggle=rows.get(FilmShield.DISABLE_HULLMOD_ID);
        check(FilmShieldDisableHullmod.class.getName().equals(toggle.get(header.indexOf("script"))),"disable row uses the switch class");
        check("true".equalsIgnoreCase(toggle.get(header.indexOf("hidden")))&&"true".equalsIgnoreCase(toggle.get(header.indexOf("hiddenEverywhere")))
                &&"false".equalsIgnoreCase(toggle.get(header.indexOf("unlocked"))),"disable switch is hidden everywhere and locked");
        check(toggle.get(header.indexOf("tags")).contains("no_build_in"),"disable switch cannot be built in as an s-mod");
    }
    public static void main(String[] args) throws Exception {
        Global.setCombatEngine(null);
        nativeArcTests();
        shieldlessNativeTests();
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
        disableTests();
        dataTests();
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
