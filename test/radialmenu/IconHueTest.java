package radialmenu;

import java.awt.Color;
import java.awt.image.BufferedImage;

public final class IconHueTest {
    public static void main(String[] args) {
        BufferedImage image = new BufferedImage(5, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.HSBtoRGB(0.98f, 0.6f, 0.8f));
        image.setRGB(1, 0, Color.HSBtoRGB(0.02f, 0.6f, 0.8f));
        image.setRGB(2, 0, Color.HSBtoRGB(0.5f, 0.1f, 0.8f));
        image.setRGB(3, 0, Color.HSBtoRGB(0.5f, 1f, 0.8f));
        image.setRGB(4, 0, Color.HSBtoRGB(0.5f, 0.6f, 0.8f) & 0x00ffffff);
        Float hue = IconHue.average(image);
        check(hue != null && (hue < 0.03f || hue > 0.97f),
                "Average hues across the red wrap, excluding transparent and out-of-range pixels");
        image.setRGB(0, 0, Color.GRAY.getRGB());
        image.setRGB(1, 0, Color.WHITE.getRGB());
        check(IconHue.average(image) == null, "No eligible pixels has no mean hue");
        image.setRGB(0, 0, Color.HSBtoRGB(0f, 0.6f, 1f));
        image.setRGB(1, 0, Color.HSBtoRGB(0.5f, 0.6f, 1f));
        check(IconHue.average(image) == null, "Opposite hues have no mean direction");
        System.out.println("IconHueTest: filtering and circular mean passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
