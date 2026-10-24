package org.moci.filmshield.api;
import com.fs.starfarer.api.combat.*;
import org.lwjgl.util.vector.Vector2f;
/** Optional integration hooks. Called on the combat thread; implementations must not throw. */
public interface ShieldHooks {
    ShieldHooks NONE = new ShieldHooks() {};
    default boolean shouldAbsorb(ShipAPI ship, Object source, DamageAPI damage, Vector2f point) { return true; }
    default float modifyFlux(ShipAPI ship, Object source, DamageAPI damage, float flux) { return flux; }
    default void onAbsorbed(ShipAPI ship, Object source, float flux, boolean hardFlux) {}
}
