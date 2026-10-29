package radialmenu;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import radialmenu.config.Setting;

final class RadialLayout {

    static final int MAIN_SLOTS = Setting.WHEEL_SLOTS.max;
    static final int SUBMENU_SLOTS = Setting.SUBMENU_SLOTS.max;
    static final String MOD_ICON = "graphics/radialmenu/icon/small_icon_radialmenu.png";
    private static final String FILE = "radial_menu_layout.json";

    private final List<Slot> main;

    private RadialLayout(List<Slot> main) {
        this.main = new ArrayList<>(main);
    }

    // shared across saves in the common folder; a missing or unreadable file falls back to radial_menu.json
    static RadialLayout load(List<String> defaults) {
        try {
            if (Global.getSettings().fileExistsInCommon(FILE)) {
                return fromJSON(Global.getSettings().readJSONFromCommon(FILE, true));
            }
        } catch (IOException | JSONException | RuntimeException e) {
            Global.getLogger(RadialLayout.class).warn("Could not read " + FILE + "; using the default layout", e);
        }
        return defaults(defaults);
    }

    static RadialLayout defaults(List<String> abilityIds) {
        List<Slot> slots = new ArrayList<>(Collections.nCopies(MAIN_SLOTS, Slot.EMPTY));
        for (int i = 0; i < abilityIds.size() && i < MAIN_SLOTS; i++) slots.set(i, Slot.ability(abilityIds.get(i)));
        return new RadialLayout(slots);
    }

    static RadialLayout fromJSON(JSONObject json) throws JSONException {
        return new RadialLayout(slots(json.getJSONArray("main"), MAIN_SLOTS, true));
    }

    Slot get(int slot, int child) {
        return child < 0 ? main.get(slot) : main.get(slot).children().get(child);
    }

    void set(int slot, int child, Slot value) {
        main.set(slot, child < 0 ? value : main.get(slot).withChild(child, value));
        try {
            Global.getSettings().writeJSONToCommon(FILE, toJSON(), true);
        } catch (IOException | JSONException e) {
            Global.getLogger(RadialLayout.class).warn("Could not save " + FILE, e);
        }
    }

    JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("main", toJSON(main));
        return json;
    }

    private static JSONArray toJSON(List<Slot> slots) throws JSONException {
        JSONArray array = new JSONArray();
        for (Slot slot : slots) {
            JSONObject entry = new JSONObject();
            if (slot.abilityId() != null) entry.put("ability", slot.abilityId());
            if (slot.isSubmenu()) {
                entry.put("icon", slot.icon());
                if (slot.hue() != null) entry.put("hue", slot.hue().doubleValue());
                entry.put("slots", toJSON(slot.children()));
            }
            array.put(entry);
        }
        return array;
    }

    private static List<Slot> slots(JSONArray array, int size, boolean submenus) throws JSONException {
        List<Slot> slots = new ArrayList<>(Collections.nCopies(size, Slot.EMPTY));
        for (int i = 0; i < array.length() && i < size; i++) {
            JSONObject entry = array.getJSONObject(i);
            if (entry.has("ability")) {
                slots.set(i, Slot.ability(entry.getString("ability")));
            } else if (submenus && entry.has("slots")) {
                Float hue = entry.has("hue") ? (float) entry.getDouble("hue") : null;
                List<Slot> children = slots(entry.getJSONArray("slots"), SUBMENU_SLOTS, false);
                slots.set(i, new Slot(null, entry.getString("icon"), hue, List.copyOf(children)));
            }
        }
        return slots;
    }
}
