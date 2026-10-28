package radialmenu.config;

import java.util.List;

import org.lwjgl.input.Keyboard;

public enum Setting {

    HOTKEY("radial_menu_hotkey", Keyboard.KEY_B),
    SECOND_HOTKEY("radial_menu_second_hotkey", Keyboard.KEY_LCONTROL),
    THIRD_HOTKEY("radial_menu_third_hotkey", Keyboard.KEY_RCONTROL);

    public static final List<Setting> HOTKEYS = List.of(HOTKEY, SECOND_HOTKEY, THIRD_HOTKEY);

    public final String id;
    public final int fallback;

    Setting(String id, int fallback) {
        this.id = id;
        this.fallback = fallback;
    }

    // luna stores a cleared keybind as 0
    public int sanitize(Integer value) {
        return value != null && value >= 0 && value < Keyboard.KEYBOARD_SIZE ? value : fallback;
    }
}
