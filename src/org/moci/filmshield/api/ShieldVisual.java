package org.moci.filmshield.api;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
import com.fs.starfarer.api.combat.ShipAPI;
import org.lwjgl.util.vector.Vector2f;
/** Visual-only SPI: supplying this interface does not install damage interception. */
public interface ShieldVisual {
    boolean isActive();
    default List<? extends ShieldHit> getHits() { return Collections.emptyList(); }
    default Color getLowColor() { return new Color(55,145,255); }
    default Color getHighColor() { return new Color(255,20,45); }
    default float getLevel(ShipAPI ship) { return ship.getFluxLevel(); }
    default float getRadius(ShipAPI ship) { return ship.getShield() == null ? ship.getCollisionRadius() : ship.getShield().getRadius(); }
    default Vector2f getCenter(ShipAPI ship) { return ship.getShield() == null ? ship.getLocation() : ship.getShield().getLocation(); }
    default boolean showStatus() { return false; }
    default String getStatusText() { return isActive() ? "FIELD: ON" : "FIELD: OFF"; }
    /** Return false to disable only this provider's effects, never its defense. */
    default boolean shouldRender(ShipAPI ship, int fighters) { return org.moci.filmshield.core.FilmShieldSettings.shouldRender(ship,fighters); }
}
