package radialmenu;

import java.util.function.ToDoubleFunction;

/** Measures the horizontal room available at a label's position in a sector. */
final class SectorText {
    private SectorText() { }

    static float width(double angle, double sectorWidth, float radius, float yOffset, float lineHeight) {
        double x = Math.cos(angle) * radius;
        double y = Math.sin(angle) * radius + yOffset;
        float low = 0f, high = 140f;
        for (int i = 0; i < 16; i++) {
            float mid = (low + high) / 2f;
            if (inside(x - mid / 2, y, angle, sectorWidth)
                    && inside(x + mid / 2, y, angle, sectorWidth)
                    && inside(x - mid / 2, y + lineHeight, angle, sectorWidth)
                    && inside(x + mid / 2, y + lineHeight, angle, sectorWidth)) low = mid;
            else high = mid;
        }
        // Extremely narrow sectors cannot contain even a glyph; keep the text readable.
        return Math.max(20f, low - 8f);
    }

    private static boolean inside(double x, double y, double angle, double sectorWidth) {
        double distance = Math.hypot(x, y);
        if (distance < RadialSelection.INNER_RADIUS + 4 || distance > RadialSelection.OUTER_RADIUS - 4) return false;
        double difference = Math.atan2(Math.sin(Math.atan2(y, x) - angle), Math.cos(Math.atan2(y, x) - angle));
        return Math.abs(difference) <= sectorWidth / 2;
    }

    static String wrap(String text, float maxWidth, ToDoubleFunction<String> measure) {
        StringBuilder result = new StringBuilder();
        String line = "";
        for (String word : text.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && measure.applyAsDouble(candidate) > maxWidth) {
                if (result.length() > 0) result.append('\n');
                result.append(line);
                line = word;
            } else {
                line = candidate;
            }
        }
        if (result.length() > 0) result.append('\n');
        return result.append(line).toString();
    }
}
