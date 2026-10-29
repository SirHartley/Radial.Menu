package radialmenu.config;

import java.util.List;

import org.lwjgl.input.Keyboard;

public enum Setting {

    HOTKEY("radial_menu_hotkey", Keyboard.KEY_B),
    SECOND_HOTKEY("radial_menu_second_hotkey", Keyboard.KEY_LCONTROL),
    THIRD_HOTKEY("radial_menu_third_hotkey", Keyboard.KEY_RCONTROL),
    WHEEL_SLOTS("radial_menu_wheel_slots", 7, 1, 10),
    SUBMENU_SLOTS("radial_menu_submenu_slots", 5, 1, 10),
    ICONS_ONLY("radial_menu_icons_only", true),
    TINTED_TILES("radial_menu_tinted_tiles", false),
    TOOLTIPS("radial_menu_tooltips", true),
    SELECT_BY_DIRECTION("radial_menu_select_by_direction", false),
    LIFT("radial_menu_lift", true),
    NO_ANIMATION("radial_menu_no_animation", false);

    public enum Type { KEYCODE, INT, BOOLEAN }

    public static final List<Setting> HOTKEYS = List.of(HOTKEY, SECOND_HOTKEY, THIRD_HOTKEY);

    public final String id;
    public final Type type;
    // booleans are stored as 0 and 1
    public final int fallback;
    public final int min;
    public final int max;

    Setting(String id, int key) {
        this(id, Type.KEYCODE, key, Keyboard.KEY_NONE, Keyboard.KEYBOARD_SIZE - 1);
    }

    Setting(String id, int fallback, int min, int max) {
        this(id, Type.INT, fallback, min, max);
    }

    Setting(String id, boolean fallback) {
        this(id, Type.BOOLEAN, fallback ? 1 : 0, 0, 1);
    }

    Setting(String id, Type type, int fallback, int min, int max) {
        this.id = id;
        this.type = type;
        this.fallback = fallback;
        this.min = min;
        this.max = max;
    }

    // luna stores a cleared keybind as 0; a bad keycode falls back, a count is clamped
    public int sanitize(Integer value) {
        if (value == null) return fallback;
        return switch (type) {
            case KEYCODE -> value >= min && value <= max ? value : fallback;
            case INT -> Math.max(min, Math.min(max, value));
            case BOOLEAN -> value != 0 ? 1 : 0;
        };
    }
}
