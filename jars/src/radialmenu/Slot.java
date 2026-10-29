package radialmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

record Slot(String abilityId, String icon, Float hue, List<Slot> children) {

    static final Slot EMPTY = new Slot(null, null, null, null);

    static Slot ability(String abilityId) {
        return new Slot(abilityId, null, null, null);
    }

    static Slot submenu(String icon, Float hue) {
        return new Slot(null, icon, hue, Collections.nCopies(RadialLayout.SUBMENU_SLOTS, EMPTY));
    }

    boolean isSubmenu() {
        return children != null;
    }

    Slot withChild(int index, Slot child) {
        List<Slot> copy = new ArrayList<>(children);
        copy.set(index, child);
        return new Slot(null, icon, hue, List.copyOf(copy));
    }

    Slot withLook(String icon, Float hue) {
        return new Slot(null, icon, hue, children);
    }
}
