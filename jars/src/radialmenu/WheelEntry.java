package radialmenu;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;

// one tile as shown: captured when its ring is built, so edits cannot shift a release target
final class WheelEntry {

    enum Kind { ABILITY, SUBMENU, EMPTY }

    final Kind kind;
    final int slot;
    final String title;
    final RadialAbility action;
    final String iconName;
    final Float hue;
    SpriteAPI icon;
    Float fillHue;
    LabelAPI label;

    private WheelEntry(Kind kind, int slot, String title, RadialAbility action, String iconName, Float hue) {
        this.kind = kind;
        this.slot = slot;
        this.title = title;
        this.action = action;
        this.iconName = iconName;
        this.hue = hue;
    }

    static WheelEntry of(Slot slot, int index) {
        if (slot.isSubmenu()) {
            String icon = slot.icon() != null ? slot.icon() : RadialLayout.MOD_ICON;
            return new WheelEntry(Kind.SUBMENU, index, "Submenu", null, icon, slot.hue());
        }
        AbilitySpecAPI spec = slot.abilityId() == null ? null : Global.getSettings().getAbilitySpec(slot.abilityId());
        if (spec == null) return new WheelEntry(Kind.EMPTY, index, "Empty", null, null, null);
        return new WheelEntry(Kind.ABILITY, index, spec.getName(), new RadialAbility(spec.getId(), spec.getName()),
                spec.getIconName(), null);
    }
}
