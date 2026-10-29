package radialmenu.render;

import java.awt.Color;
import java.awt.image.BufferedImage;

import com.fs.starfarer.api.util.Misc;

/** Circular mean of the visible, moderately saturated pixels in an ability icon. */
public final class IconHue {
    private IconHue() { }

    public static Float average(BufferedImage image) {
        double x = 0, y = 0;
        int count = 0;
        float[] hsb = new float[3];
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = 0; column < image.getWidth(); column++) {
                int pixel = image.getRGB(column, row);
                if ((pixel >>> 24) == 0) continue;
                Color.RGBtoHSB((pixel >> 16) & 255, (pixel >> 8) & 255, pixel & 255, hsb);
                if (hsb[1] <= 0.3f || hsb[1] >= 0.9f) continue;
                double angle = hsb[0] * Math.PI * 2;
                x += Math.cos(angle);
                y += Math.sin(angle);
                count++;
            }
        }
        // Opposing hues have no defined mean direction.
        if (count == 0 || Math.hypot(x, y) < count * 1e-6) return null;
        double hue = Math.atan2(y, x) / (Math.PI * 2);
        return (float) (hue < 0 ? hue + 1 : hue);
    }

    // the colour a tile with this hue lights up in
    public static Color tileColor(float hue) {
        return Color.getHSBColor(hue, 0.55f, 1f);
    }

    // a greyscale mod icon takes its tile's colour, lightened so the symbol stays brighter than the tile
    public static Color modIconColor(Float hue) {
        return Misc.interpolateColor(hue == null ? Misc.getBasePlayerColor() : tileColor(hue), Color.white, 0.3f);
    }
}
