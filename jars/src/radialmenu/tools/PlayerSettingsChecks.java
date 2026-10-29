package radialmenu.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import radialmenu.config.Setting;

public final class PlayerSettingsChecks {

    private static final String CSV = "data/config/LunaSettings.csv";
    private static final List<String> COLUMNS = List.of("fieldID", "fieldName", "fieldType", "defaultValue",
            "secondaryValue", "fieldDescription", "minValue", "maxValue", "tab");

    private static int checks;

    private PlayerSettingsChecks() {
    }

    public static void main(String[] args) throws Exception {
        List<List<String>> rows = parse(Files.readString(Path.of(CSV)));
        require(rows.get(0).equals(COLUMNS), "luna columns");
        Map<String, List<String>> byId = new HashMap<>();
        for (List<String> row : rows.subList(1, rows.size())) {
            require(row.size() == COLUMNS.size(), "nine columns in " + row);
            String id = row.get(0);
            require(id.startsWith("radial_menu_"), "mod prefix on " + id);
            require(byId.put(id, row) == null, "unique id " + id);
            if (row.get(2).equals("Header")) continue;
            require(find(id) != null, "csv row has a setting: " + id);
            // luna formats name and description with addPara, so a literal percent sign is %%
            require(!row.get(1).replace("%%", "").contains("%"), "escaped % in " + id + " fieldName");
            require(!row.get(5).replace("%%", "").contains("%"), "escaped % in " + id + " fieldDescription");
        }
        for (Setting setting : Setting.values()) {
            List<String> row = byId.get(setting.id);
            require(row != null, "csv row for " + setting);
            String type = switch (setting.type) {
                case KEYCODE -> "Keycode";
                case INT -> "Int";
                case BOOLEAN -> "Boolean";
                case CHOICE -> "Radio";
            };
            require(row.get(2).equals(type), "type for " + setting);
            String fallback = switch (setting.type) {
                case BOOLEAN -> String.valueOf(setting.fallback != 0);
                case CHOICE -> setting.choices.get(setting.fallback);
                default -> String.valueOf(setting.fallback);
            };
            require(row.get(3).equals(fallback), "default for " + setting);
            require(!row.get(1).isBlank() && !row.get(5).isBlank(), "name and description for " + setting);
            require(setting.sanitize(null) == setting.fallback, "missing value for " + setting);
            switch (setting.type) {
                case KEYCODE -> {
                    require(setting.sanitize(-1) == setting.fallback, "negative keycode for " + setting);
                    require(setting.sanitize(256) == setting.fallback, "out of range keycode for " + setting);
                    require(setting.sanitize(0) == 0, "cleared keybind kept for " + setting);
                }
                case INT -> {
                    require(Integer.parseInt(row.get(6)) == setting.min, "minimum for " + setting);
                    require(Integer.parseInt(row.get(7)) == setting.max, "maximum for " + setting);
                    require(setting.sanitize(-1000) == setting.min, "clamp minimum for " + setting);
                    require(setting.sanitize(1000) == setting.max, "clamp maximum for " + setting);
                }
                case BOOLEAN -> require(setting.sanitize(0) == 0 && setting.sanitize(1) == 1, "boolean for " + setting);
                case CHOICE -> {
                    // luna splits secondaryValue on commas and trims each choice
                    List<String> labels = new ArrayList<>();
                    for (String label : row.get(4).split(",")) labels.add(label.trim());
                    require(labels.equals(setting.choices), "choices for " + setting);
                    require(setting.sanitize(-1) == setting.fallback, "unknown choice for " + setting);
                    require(setting.sanitize(setting.choices.size()) == setting.fallback, "out of range choice for " + setting);
                    for (int i = 0; i < setting.choices.size(); i++) {
                        require(setting.sanitize(i) == i, "choice " + i + " kept for " + setting);
                    }
                }
            }
        }
        System.out.println("Player settings: " + checks + " checks passed");
    }

    private static Setting find(String id) {
        for (Setting setting : Setting.values()) if (setting.id.equals(id)) return setting;
        return null;
    }

    private static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c != '"') cell.append(c);
                else if (i + 1 < text.length() && text.charAt(i + 1) == '"') cell.append(text.charAt(++i));
                else quoted = false;
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (c != '\r') {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
