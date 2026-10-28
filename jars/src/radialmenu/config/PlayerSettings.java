package radialmenu.config;

import java.util.EnumMap;
import java.util.Map;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import radialmenu.RadialMenuModPlugin;

public final class PlayerSettings implements LunaSettingsListener {

    private static Map<Setting, Integer> current = defaults();

    private PlayerSettings() {
    }

    public static void install() {
        if (!LunaSettings.hasSettingsListenerOfClass(PlayerSettings.class)) {
            LunaSettings.addSettingsListener(new PlayerSettings());
        }
        reload();
    }

    public static void reload() {
        Map<Setting, Integer> values = new EnumMap<>(Setting.class);
        for (Setting setting : Setting.values()) {
            values.put(setting, setting.sanitize(LunaSettings.getInt(RadialMenuModPlugin.MOD_ID, setting.id)));
        }
        current = values;
    }

    @Override
    public void settingsChanged(String modId) {
        if (RadialMenuModPlugin.MOD_ID.equals(modId)) reload();
    }

    public static int get(Setting setting) {
        return current.get(setting);
    }

    public static Map<Setting, Integer> defaults() {
        Map<Setting, Integer> result = new EnumMap<>(Setting.class);
        for (Setting setting : Setting.values()) result.put(setting, setting.fallback);
        return result;
    }
}
