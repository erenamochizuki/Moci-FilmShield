package org.moci.filmshield.core;
import com.fs.starfarer.api.BaseModPlugin;
import org.dark.shaders.util.ShaderLib;
/** Initializes resources without requiring any content mod. OpenGL allocation stays in render callbacks. */
public final class FilmShieldModPlugin extends BaseModPlugin {
    @Override public void onApplicationLoad() throws Exception {
        FilmShieldSettings.load();
        ShaderLib.init();
    }
}
