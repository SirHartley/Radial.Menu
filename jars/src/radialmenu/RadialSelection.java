package radialmenu;

/** Hold/release gesture. Coordinates and dimensions are in virtual UI pixels. */
final class RadialSelection {
    // Fixed design dimensions, not physical screen pixels; the game handles UI scaling.
    static final float INNER_RADIUS = 56f;
    static final float OUTER_RADIUS = 180f;
    static final float SELECTION_LIFT = 20f;
    // constant width of the gap between neighbouring tiles
    static final float TILE_GAP = 6f;
    // the submenu ring starts past a lifted main tile, so the two never overlap
    static final float CHILD_INNER_RADIUS = OUTER_RADIUS + SELECTION_LIFT + 6f;
    static final float CHILD_OUTER_RADIUS = CHILD_INNER_RADIUS + 100f;
    static final double CHILD_WIDTH = Math.PI * 2 / 12;

    record Target(int sector, int child) {

        static final Target NONE = new Target(-1, -1);
    }

    private int sectors;
    private float extent = OUTER_RADIUS + SELECTION_LIFT;
    private int parent = -1;
    private int children;
    private boolean directional;
    private boolean held;
    private boolean open;
    private float x;
    private float y;
    private float startX;
    private float startY;

    RadialSelection(int sectors) {
        setSectors(sectors, OUTER_RADIUS + SELECTION_LIFT);
    }

    // extent is the wheel's largest radius, which keeps it on screen
    void setSectors(int sectors, float extent) {
        if (open) throw new IllegalStateException("Cannot change sectors during a gesture");
        if (sectors < 0) throw new IllegalArgumentException("Sector count cannot be negative");
        this.sectors = sectors;
        this.extent = extent;
        retract();
    }

    void extend(int parent, int children) {
        this.parent = parent;
        this.children = children;
    }

    void retract() {
        parent = -1;
        children = 0;
    }

    int extended() { return parent; }

    // the tile in the pointer's direction, at any distance past the centre, instead of the tile under it
    void setDirectional(boolean directional) {
        this.directional = directional;
    }

    // true when the opening keys were released over an open wheel
    boolean update(boolean openingKeyHeld, boolean allowed, boolean cancel,
                   float mouseX, float mouseY, float width, float height) {
        boolean pressed = openingKeyHeld && !held;
        boolean released = !openingKeyHeld && held;
        held = openingKeyHeld;
        if (!allowed || cancel) {
            open = false;
            return false;
        }
        if (pressed) {
            if (sectors == 0) return false;
            startX = mouseX;
            startY = mouseY;
            x = clamp(mouseX, width);
            y = clamp(mouseY, height);
            open = true;
        }
        if (released && open) {
            open = false;
            return true;
        }
        return false;
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
    double sectorAngle(int index) { return Math.PI / 2 - index * sectorWidth(); }

    // child tiles are centered on their parent sector and follow clockwise
    double childAngle(int index) {
        return sectorAngle(parent) + children * CHILD_WIDTH / 2 - (index + 0.5) * CHILD_WIDTH;
    }

    float distance(float mouseX, float mouseY) {
        return (float) Math.hypot(mouseX - x, mouseY - y);
    }

    Target target(float mouseX, float mouseY) {
        if (sectors == 0) return Target.NONE;
        // Edge clamping must not turn a stationary key tap into a selection.
        float movedX = mouseX - startX;
        float movedY = mouseY - startY;
        if (movedX * movedX + movedY * movedY < INNER_RADIUS * INNER_RADIUS) return Target.NONE;
        float distance = distance(mouseX, mouseY);
        if (distance < INNER_RADIUS) return Target.NONE;
        if (children > 0 && distance >= OUTER_RADIUS) {
            if (!directional && distance > CHILD_OUTER_RADIUS + SELECTION_LIFT) return Target.NONE;
            int child = childAt(mouseX, mouseY);
            if (child >= 0) return new Target(parent, child);
            return directional ? new Target(sectorAt(mouseX, mouseY), -1) : Target.NONE;
        }
        if (!directional && distance > OUTER_RADIUS + SELECTION_LIFT) return Target.NONE;
        return new Target(sectorAt(mouseX, mouseY), -1);
    }

    // sector 0 points up; the rest follow clockwise. half-open bounds avoid overlap
    int sectorAt(float mouseX, float mouseY) {
        double clockwise = clockwise(mouseX, mouseY) + sectorWidth() / 2;
        return (int) (wrap(clockwise) / sectorWidth()) % sectors;
    }

    private int childAt(float mouseX, float mouseY) {
        double first = parent * sectorWidth() - children * CHILD_WIDTH / 2;
        double along = wrap(clockwise(mouseX, mouseY) - first);
        return along < children * CHILD_WIDTH ? (int) (along / CHILD_WIDTH) : -1;
    }

    private double clockwise(float mouseX, float mouseY) {
        return Math.atan2(mouseX - x, mouseY - y);
    }

    private static double wrap(double angle) {
        return (angle % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
    }

    private float clamp(float value, float size) {
        float margin = Math.min(extent, size / 2f);
        return Math.max(margin, Math.min(size - margin, value));
    }
}
