import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.lang.reflect.*;
import java.util.*;
import org.apache.log4j.*;
import org.apache.log4j.spi.LoggingEvent;
import org.moci.filmshield.core.FilmShieldSettings;
import org.moci.filmshield.render.FilmShieldRenderer;

/** Log-only coverage; no GL context is created or claimed to be tested. */
public class FilmShieldRenderDiagnosticsTest {
    static int checks;
    static final List<String> lines=new ArrayList<>();
    static void check(boolean ok,String label) { checks++; if(!ok) throw new AssertionError(label+lines); }
    static long count(String text) { return lines.stream().filter(s->s.contains(text)).count(); }
    static Object delegate(Method method,Object target,Object[] args) {
        try { return method.invoke(target,args); }
        catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    static void set(Object target,String name,Object value) throws Exception {
        Field f=FilmShieldRenderer.class.getDeclaredField(name); f.setAccessible(true); f.set(target,value);
    }
    static void call(Object target,String method,Class<?>[] types,Object... args) throws Exception {
        Method m=FilmShieldRenderer.class.getDeclaredMethod(method,types);m.setAccessible(true);m.invoke(target,args);
    }
    public static void main(String[] args) throws Exception {
        CombatEngineAPI old=Global.getCombatEngine();
        boolean shaders=FilmShieldSettings.enableShaders;
        Logger logger=Logger.getLogger(FilmShieldRenderer.class); Level level=logger.getLevel();
        AppenderSkeleton capture=new AppenderSkeleton() {
            protected void append(LoggingEvent event) { lines.add(event.getRenderedMessage()); }
            public void close() {} public boolean requiresLayout() { return false; }
        };
        logger.addAppender(capture);logger.setLevel(Level.INFO);
        try {
            Map<String,Object> data=new HashMap<>();
            Global.setCombatEngine(FilmShieldContractTest.proxy(CombatEngineAPI.class,(m,a)->{
                if(m.getName().equals("getCustomData"))return data;
                return FilmShieldContractTest.zero(m.getReturnType());
            }));
            FilmShieldRenderer r=FilmShieldRenderer.getInstance();
            check(FilmShieldRenderer.getInstance()==r&&count("renderer_registered ")==1,"renderer registration logs once");
            FilmShieldContractTest.Hull hull=new FilmShieldContractTest.Hull(true);
            SpriteAPI sprite=FilmShieldContractTest.proxy(SpriteAPI.class,(m,a)->{
                if(m.getName().equals("getTextureId"))return 12;
                if(m.getName().equals("getWidth")||m.getName().equals("getHeight"))return 200f;
                return FilmShieldContractTest.zero(m.getReturnType());
            });
            ShipHullSpecAPI spec=FilmShieldContractTest.proxy(ShipHullSpecAPI.class,(m,a)->{
                if(m.getName().equals("getHullId"))return "Moci_Yamato";
                return delegate(m,hull.hullSpec,a);
            });
            ShipAPI ship=FilmShieldContractTest.proxy(ShipAPI.class,(m,a)->{
                if(m.getName().equals("getHullSpec"))return spec;
                if(m.getName().equals("getSpriteAPI"))return sprite;
                if(m.getName().equals("getId"))return "yamato-test";
                return delegate(m,hull.ship,a);
            });
            Class<?>[] renderTypes={ShipAPI.class,SpriteAPI.class,SpriteAPI.class,boolean.class};
            FilmShieldSettings.enableShaders=false;
            call(r,"reportRenderPath",renderTypes,ship,sprite,sprite,false);
            check(count("path=fixed_pipeline")==1&&count("reason=config_disabled")==1,"fallback reason is explicit");
            check(count("hull=Moci_Yamato")==1&&count("ship=yamato-test")==1,"fallback identifies ship");
            for(int i=0;i<100;i++)call(r,"reportRenderPath",renderTypes,ship,sprite,sprite,false);
            check(count("ship_render ")==1,"stable fallback does not flood log");
            FilmShieldSettings.enableShaders=true;
            set(r,"shaderInitialized",true);set(r,"shaderFailure","none");
            set(r,"lastProgramProbe",Float.MAX_VALUE); // GL probing is covered only in game.
            call(r,"reportRenderPath",renderTypes,ship,sprite,sprite,true);
            check(count("path=shader")==1,"recovery to shader logs once");
            for(int i=0;i<100;i++)call(r,"reportRenderPath",renderTypes,ship,sprite,sprite,true);
            check(count("ship_render ")==2,"stable shader path does not repeat");
            set(r,"shaderInitialized",false);set(r,"shaderFailure","required_uniform_missing");
            call(r,"reportRenderPath",renderTypes,ship,sprite,sprite,false);
            check(count("reason=required_uniform_missing")==1,"later fallback carries failure reason");
            Class<?>[] warningTypes={String.class,String.class,Exception.class};
            for(int i=0;i<100;i++)call(r,"diagnosticWarning",warningTypes,"test_error","program mismatch",null);
            check(count("test_error ")==1,"same warning logs once per renderer");
            FilmShieldRenderer second=new FilmShieldRenderer();
            call(second,"diagnosticWarning",warningTypes,"test_error","new combat",null);
            check(count("test_error ")==2,"new combat can report same warning again");
            System.out.println("FilmShield render diagnostic checks passed: "+checks);
        } finally {
            logger.removeAppender(capture);logger.setLevel(level);
            FilmShieldSettings.enableShaders=shaders;Global.setCombatEngine(old);
        }
    }
}
