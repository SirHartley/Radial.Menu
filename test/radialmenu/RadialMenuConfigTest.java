package radialmenu;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility;
import com.fs.starfarer.api.loading.AbilitySpecAPI;

public final class RadialMenuConfigTest {
    public static void main(String[] args) throws Exception {
        RadialMenuConfig config = RadialMenuConfig.fromJSON(new JSONObject(
                "{\"abilities\":[\"missing_mod\",\"mod_custom\",\"scavenge\",\"unknown\"]}"));
        Map<String, AbilityPlugin> abilities = new HashMap<String, AbilityPlugin>();
        abilities.put("mod_custom", duration("Modded Ability", true));
        abilities.put("scavenge", duration("Scavenge", false));
        CampaignFleetAPI fleet = fleet(abilities);
        RadialAbility[] owned = config.ownedAbilities(fleet);
        check(owned.length == 2, "Only player-owned abilities get sectors");
        check(owned[0].id.equals("mod_custom") && owned[1].id.equals("scavenge"), "Keep configuration order");
        check(owned[0].title.equals("Modded Ability"),
                "Use the native ability name, including abilities not built into this mod");
        check(!owned[1].isAvailable(abilities.get("scavenge")), "Owned but unusable ability still gets a sector");

        abilities.remove("scavenge");
        check(config.ownedAbilities(fleet).length == 1, "Recheck player ownership for the next gesture");
        check(owned.length == 2 && owned[1].id.equals("scavenge"), "Previous gesture keeps its target positions");
        check(!owned[1].press(fleet.getAbility(owned[1].id)), "An ability removed mid-gesture cannot activate");
        abilities.clear();
        check(config.ownedAbilities(fleet).length == 0, "No owned candidates produces an empty menu");
        check(RadialMenuConfig.fromJSON(new JSONObject("{\"abilities\":[]}")).ownedAbilities(fleet).length == 0,
                "An empty configuration is allowed");

        AbilitySpecAPI toggleSpec = spec("Custom Toggle");
        AbilityPlugin toggle = new BaseToggleAbility() {
            @Override public AbilitySpecAPI getSpec() { return toggleSpec; }
            @Override public boolean isUsable() { return true; }
            @Override protected void activateImpl() { }
            @Override protected void applyEffect(float amount, float level) { }
            @Override protected void deactivateImpl() { }
            @Override protected void cleanupImpl() { }
        };
        abilities.put("custom_toggle", toggle);
        RadialAbility[] toggles = RadialMenuConfig.fromJSON(new JSONObject(
                "{\"abilities\":[\" custom_toggle \"]}")).ownedAbilities(fleet);
        check(toggles.length == 1 && toggles[0].id.equals("custom_toggle"), "Trim IDs before lookup");
        check(toggles[0].title.equals("Custom Toggle"), "Display the native toggle name");

        reject("{}");
        reject("{\"abilities\":\"scavenge\"}");
        reject("{\"abilities\":[42]}");
        reject("{\"abilities\":[null]}");
        reject("{\"abilities\":[{}]}");
        reject("{\"abilities\":[\"   \"]}");
        reject("{\"abilities\":[\"scavenge\",\" scavenge \"]}");
        System.out.println("RadialMenuConfigTest: parsing, ownership filtering, ordering, and custom abilities passed");
    }

    private static CampaignFleetAPI fleet(Map<String, AbilityPlugin> abilities) {
        return (CampaignFleetAPI) Proxy.newProxyInstance(CampaignFleetAPI.class.getClassLoader(),
                new Class<?>[] {CampaignFleetAPI.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAbility")) return abilities.get(args[0]);
                    throw new AssertionError("Unexpected fleet API call: " + method.getName());
                });
    }

    private static AbilityPlugin duration(String name, boolean usable) {
        AbilitySpecAPI spec = spec(name);
        return (AbilityPlugin) Proxy.newProxyInstance(AbilityPlugin.class.getClassLoader(),
                new Class<?>[] {AbilityPlugin.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getSpec": return spec;
                        case "isUsable": return usable;
                        case "isOnCooldown": return false;
                        case "isActiveOrInProgress": return false;
                        default: throw new AssertionError("Unexpected ability API call: " + method.getName());
                    }
                });
    }

    private static AbilitySpecAPI spec(String name) {
        return (AbilitySpecAPI) Proxy.newProxyInstance(AbilitySpecAPI.class.getClassLoader(),
                new Class<?>[] {AbilitySpecAPI.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    throw new AssertionError("Unexpected spec API call: " + method.getName());
                });
    }

    private static void reject(String json) throws Exception {
        try {
            RadialMenuConfig.fromJSON(new JSONObject(json));
        } catch (JSONException | IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid ability configuration must fail visibly: " + json);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
