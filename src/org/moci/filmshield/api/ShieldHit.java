package org.moci.filmshield.api;
import org.lwjgl.util.vector.Vector2f;
/** Read-only hit data. Coordinates are relative to the hull, rotated by -facing. */
public interface ShieldHit {
    Vector2f getRel();
    float getSize();
    float getLevel();
}
