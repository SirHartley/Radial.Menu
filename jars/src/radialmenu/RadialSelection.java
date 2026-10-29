package radialmenu;

/** Hold/release gesture. Coordinates and dimensions are in virtual UI pixels. */
final class RadialSelection {
    // Fixed design dimensions, not physical screen pixels; the game handles UI scaling.
    static final float INNER_RADIUS = 42f;
    static final float OUTER_RADIUS = 180f;
    static final float SELECTION_LIFT = 20f;
    private int sectors;
    private double offset;
    private boolean held;
    private boolean open;
    private float x;
    private float y;
    private float startX;
    private float startY;

    RadialSelection(int sectors) {
        setSectors(sectors, 0);
    }

    // offset turns the wheel counterclockwise, in radians
    void setSectors(int sectors, double offset) {
        if (open) throw new IllegalStateException("Cannot change sectors during a gesture");
        if (sectors < 0) throw new IllegalArgumentException("Sector count cannot be negative");
        this.sectors = sectors;
        this.offset = offset;
    }

    // swaps the layer of an open wheel; restarting the gesture here stops the new layer selecting at once
    void relayer(int sectors, double offset, float mouseX, float mouseY) {
        if (!open) throw new IllegalStateException("Only an open wheel changes layers");
        if (sectors < 1) throw new IllegalArgumentException("A layer needs at least one sector");
        this.sectors = sectors;
        this.offset = offset;
        startX = mouseX;
        startY = mouseY;
    }

    /** Returns the released sector, or -1 when no selection was made. */
    int update(boolean openingKeyHeld, boolean allowed, boolean cancel,
               float mouseX, float mouseY, float width, float height) {
        boolean pressed = openingKeyHeld && !held;
        boolean released = !openingKeyHeld && held;
        held = openingKeyHeld;
        if (!allowed || cancel) {
            open = false;
            return -1;
        }
        if (pressed) {
            if (sectors == 0) return -1;
            startX = mouseX;
            startY = mouseY;
            x = clamp(mouseX, width);
            y = clamp(mouseY, height);
            open = true;
        }
        if (released && open) {
            int selected = hoveredSector(mouseX, mouseY);
            open = false;
            return selected;
        }
        return -1;
    }

    void cancel() { open = false; }
    boolean isOpen() { return open; }
    boolean isHeld() { return held; }
    float getX() { return x; }
    float getY() { return y; }
    double sectorWidth() {
        if (sectors == 0) throw new IllegalStateException("An empty wheel has no sector angles");
        return Math.PI * 2 / sectors;
    }
    double sectorAngle(int index) { return Math.PI / 2 + offset - index * sectorWidth(); }

    int hoveredSector(float mouseX, float mouseY) {
        if (sectors == 0) return -1;
        // Edge clamping must not turn a stationary key tap into a selection.
        float movedX = mouseX - startX;
        float movedY = mouseY - startY;
        if (movedX * movedX + movedY * movedY < INNER_RADIUS * INNER_RADIUS) return -1;
        float dx = mouseX - x;
        float dy = mouseY - y;
        float distanceSquared = dx * dx + dy * dy;
        if (distanceSquared < INNER_RADIUS * INNER_RADIUS
                || distanceSquared > (OUTER_RADIUS + SELECTION_LIFT) * (OUTER_RADIUS + SELECTION_LIFT)) return -1;
        // sector 0 points up, turned by the offset; the rest follow clockwise. half-open bounds avoid overlap
        double clockwise = Math.atan2(dx, dy) + offset + sectorWidth() / 2;
        clockwise = (clockwise % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        return (int) (clockwise / sectorWidth()) % sectors;
    }

    private static float clamp(float value, float extent) {
        float margin = Math.min(OUTER_RADIUS + SELECTION_LIFT, extent / 2f);
        return Math.max(margin, Math.min(extent - margin, value));
    }
}
