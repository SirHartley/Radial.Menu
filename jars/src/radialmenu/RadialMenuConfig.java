package radialmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// default layout: ability ids for the first main wheel slots, used until the player edits the wheel
final class RadialMenuConfig {
    static final String PATH = "data/config/radial_menu.json";
    private final List<String> abilityIds;

    private RadialMenuConfig(List<String> abilityIds) {
        this.abilityIds = Collections.unmodifiableList(abilityIds);
    }

    static RadialMenuConfig fromJSON(JSONObject json) throws JSONException {
        JSONArray entries = json.getJSONArray("abilities");
        if (entries.length() > RadialLayout.MAIN_SLOTS) {
            throw new IllegalArgumentException(PATH + ": at most " + RadialLayout.MAIN_SLOTS + " abilities");
        }
        List<String> ids = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < entries.length(); i++) {
            Object entry = entries.get(i);
            if (!(entry instanceof String) || ((String) entry).trim().isEmpty()) {
                throw new IllegalArgumentException(PATH + ": abilities[" + i + "] must be a non-empty ability ID");
            }
            String id = ((String) entry).trim();
            if (!seen.add(id)) {
                throw new IllegalArgumentException(PATH + ": duplicate ability ID: " + id);
            }
            ids.add(id);
        }
        return new RadialMenuConfig(ids);
    }

    List<String> abilityIds() {
        return abilityIds;
    }
}
