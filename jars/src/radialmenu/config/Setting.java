package radialmenu.config;

import java.util.List;

import org.lwjgl.input.Keyboard;

public enum Setting {

    HOTKEY("radial_menu_hotkey", Keyboard.KEY_B),
    SECOND_HOTKEY("radial_menu_second_hotkey", Keyboard.KEY_LCONTROL),
    THIRD_HOTKEY("radial_menu_third_hotkey", Keyboard.KEY_RCONTROL),
    WHEEL_SLOTS("radial_menu_wheel_slots", 5, 1, 10),
    // plus back, so a submenu has at most 10 sections
    SUBMENU_SLOTS("radial_menu_submenu_slots", 5, 1, 9);

    public static final List<Setting> HOTKEYS = List.of(HOTKEY, SECOND_HOTKEY, THIRD_HOTKEY);

    public final String id;
    public final int fallback;
    public final int min;
    public final int max;
    public final boolean keycode;

    Setting(String id, int key) {
        this(id, key, Keyboard.KEY_NONE, Keyboard.KEYBOARD_SIZE - 1, true);
    }

    Setting(String id, int fallback, int min, int max) {
        this(id, fallback, min, max, false);
    }

    Setting(String id, int fallback, int min, int max, boolean keycode) {
        this.id = id;
        this.fallback = fallback;
        this.min = min;
        this.max = max;
        this.keycode = keycode;
    }

    // luna stores a cleared keybind as 0; a bad keycode falls back, a count is clamped
    public int sanitize(Integer value) {
        if (value == null) return fallback;
        if (keycode) return value >= min && value <= max ? value : fallback;
        return Math.max(min, Math.min(max, value));
    }
}
