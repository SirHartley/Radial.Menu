package radialmenu.config;

import java.util.EnumMap;
import java.util.Map;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import radialmenu.RadialMenuModPlugin;

public final class PlayerSettings implements LunaSettingsListener {

    private static Map<Setting, Integer> current = defaults();
    private static Runnable resetLayout;

    private PlayerSettings() {
    }

    // the value read at startup is the baseline, so a reset left on does not repeat every launch
    public static void install(Runnable onResetLayout) {
        if (!LunaSettings.hasSettingsListenerOfClass(PlayerSettings.class)) {
            LunaSettings.addSettingsListener(new PlayerSettings());
        }
        current = readAll();
        resetLayout = onResetLayout;
    }

    // luna has no button field and no setter, so turning the reset toggle on is the trigger
    public static void reload() {
        Map<Setting, Integer> values = readAll();
        boolean reset = values.get(Setting.RESET_LAYOUT) != 0 && current.get(Setting.RESET_LAYOUT) == 0;
        current = values;
        if (reset) resetLayout.run();
    }

    private static Map<Setting, Integer> readAll() {
        Map<Setting, Integer> values = new EnumMap<>(Setting.class);
        for (Setting setting : Setting.values()) {
            values.put(setting, setting.sanitize(read(setting)));
        }
        return values;
    }

    @Override
    public void settingsChanged(String modId) {
        if (RadialMenuModPlugin.MOD_ID.equals(modId)) reload();
    }

    private static Integer read(Setting setting) {
        return switch (setting.type) {
            case BOOLEAN -> {
                Boolean value = LunaSettings.getBoolean(RadialMenuModPlugin.MOD_ID, setting.id);
                yield value == null ? null : value ? 1 : 0;
            }
            case CHOICE -> {
                String value = LunaSettings.getString(RadialMenuModPlugin.MOD_ID, setting.id);
                yield value == null ? null : setting.choices.indexOf(value.trim());
            }
            default -> LunaSettings.getInt(RadialMenuModPlugin.MOD_ID, setting.id);
        };
    }

    public static int get(Setting setting) {
        return current.get(setting);
    }

    public static boolean enabled(Setting setting) {
        return get(setting) != 0;
    }

    public static Map<Setting, Integer> defaults() {
        Map<Setting, Integer> result = new EnumMap<>(Setting.class);
        for (Setting setting : Setting.values()) result.put(setting, setting.fallback);
        return result;
    }
}
