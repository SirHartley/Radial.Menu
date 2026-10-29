package radialmenu;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.FaderUtil;

// a ring of tiles: the main wheel, or one submenu's tiles on the outer ring
final class WheelRing {

    final WheelEntry[] entries;
    final double width;
    final float inner;
    final float outer;
    // submenu tiles stand apart from their neighbours and grow out of the inner edge when shown
    final boolean standalone;
    private final double[] centers;

    final FaderUtil[] lift;
    final FaderUtil[] glow;
    FeatheredSector[] base;
    FeatheredSector[] lifted;
    FeatheredSector[] collapsed;
    private float scale;

    WheelRing(WheelEntry[] entries, double[] centers, double width, float inner, float outer,
              boolean standalone, float liftIn, float liftOut, float glowIn, float glowOut) {
        this.entries = entries;
        this.centers = centers;
        this.width = width;
        this.inner = inner;
        this.outer = outer;
        this.standalone = standalone;
        lift = new FaderUtil[entries.length];
        glow = new FaderUtil[entries.length];
        for (int i = 0; i < entries.length; i++) {
            lift[i] = new FaderUtil(0f, liftIn, liftOut);
            glow[i] = new FaderUtil(0f, glowIn, glowOut);
        }
    }

    double center(int index) {
        return centers[index];
    }

    float middle() {
        return (inner + outer) / 2f;
    }

    // meshes are rebuilt only when the ui scale changes
    void updateGeometry() {
        float current = Global.getSettings().getScreenScaleMult();
        if (current == scale) return;
        base = new FeatheredSector[entries.length];
        lifted = new FeatheredSector[entries.length];
        collapsed = standalone ? new FeatheredSector[entries.length] : null;
        for (int i = 0; i < entries.length; i++) {
            double start = centers[i] - width / 2;
            base[i] = new FeatheredSector(start, start + width, inner, outer, current);
            lifted[i] = new FeatheredSector(start, start + width, inner, outer + RadialSelection.SELECTION_LIFT, current);
            if (standalone) collapsed[i] = new FeatheredSector(start, start + width, inner, inner + 1f, current);
        }
        scale = current;
    }
}
