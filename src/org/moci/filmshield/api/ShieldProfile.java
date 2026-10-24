package org.moci.filmshield.api;
import java.awt.Color;
/** Immutable per-owner configuration. Reuse a profile; do not create one each frame. */
public final class ShieldProfile {
    public enum Control { NATIVE_SHIELD, EXTERNAL }
    public final String id, title, activeText, inactiveText, hudActiveText, hudInactiveText, modifierId;
    public final Control control;
    public final float fluxPerDamage, fragmentationMultiplier;
    public final boolean followParent, showStatus, suppressSpecLessModuleProjectiles;
    public final Color lowColor, highColor;
    public final ShieldHooks hooks;
    private ShieldProfile(Builder b) {
        if (b.id == null || b.id.trim().isEmpty()) throw new IllegalArgumentException("Profile id is required");
        if (b.control == null || b.low == null || b.high == null || b.hooks == null) throw new IllegalArgumentException("Null profile option");
        if (!Float.isFinite(b.flux) || b.flux < 0 || !Float.isFinite(b.frag) || b.frag < 0) throw new IllegalArgumentException("Flux and fragmentation multipliers must be finite and nonnegative");
        id=b.id; control=b.control; title=b.title; activeText=b.on; inactiveText=b.off;
        hudActiveText=b.hudOn; hudInactiveText=b.hudOff; modifierId=b.modifierId;
        fluxPerDamage=b.flux; fragmentationMultiplier=b.frag; followParent=b.parent;
        showStatus=b.status; suppressSpecLessModuleProjectiles=b.suppress;
        lowColor=b.low; highColor=b.high; hooks=b.hooks;
    }
    public static Builder builder(String id) { return new Builder(id); }
    public static final class Builder {
        private final String id;
        private Control control=Control.NATIVE_SHIELD;
        private float flux=1f, frag=1f;
        private boolean parent=true, status=true, suppress=false;
        private String title="贴膜盾", on="激活", off="关闭", hudOn="FIELD: ON", hudOff="FIELD: OFF", modifierId="filmshield_absorb";
        private Color low=new Color(55,145,255), high=new Color(255,20,45);
        private ShieldHooks hooks=ShieldHooks.NONE;
        private Builder(String id) { this.id=id; }
        public Builder control(Control v) { control=v; return this; }
        /** Native mode uses the hull's shield efficiency, scaled by this value. External mode uses it directly. */
        public Builder fluxPerDamage(float v) { flux=v; return this; }
        public Builder fragmentationMultiplier(float v) { frag=v; return this; }
        public Builder followParent(boolean v) { parent=v; return this; }
        public Builder showStatus(boolean v) { status=v; return this; }
        public Builder suppressSpecLessModuleProjectiles(boolean v) { suppress=v; return this; }
        public Builder colors(Color low, Color high) { this.low=low; this.high=high; return this; }
        public Builder labels(String title,String on,String off,String hudOn,String hudOff) {
            if (title==null||on==null||off==null||hudOn==null||hudOff==null) throw new IllegalArgumentException("Null label");
            this.title=title; this.on=on; this.off=off; this.hudOn=hudOn; this.hudOff=hudOff; return this;
        }
        public Builder modifierId(String value) { if(value==null||value.isEmpty()) throw new IllegalArgumentException("Empty modifier id"); modifierId=value; return this; }
        public Builder hooks(ShieldHooks v) { hooks=v; return this; }
        public ShieldProfile build() { return new ShieldProfile(this); }
    }
}
