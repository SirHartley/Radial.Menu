package radialmenu.render;

import java.awt.image.BufferedImage;

// fits an icon to vanilla's ability icon size, on premultiplied channels so transparent pixels add no fringe
public final class IconScale {

    public static final int SIZE = 48;
    private static final float SHARPEN = 0.6f;
    private static final float[] BLUR = gaussian(1f, 2);

    private IconScale() { }

    // null when the icon already is SIZE square; otherwise a SIZE square copy fit by its longer side and centred.
    // a box filter shrinks it, a bicubic stretch and an unsharp mask enlarge it
    public static BufferedImage normalize(BufferedImage source) {
        int width = source.getWidth(), height = source.getHeight();
        if (width == SIZE && height == SIZE) return null;
        float scale = (float) SIZE / Math.max(width, height);
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        float[][] pixels = premultiplied(source);
        float[][] scaled = scale < 1f ? shrink(pixels, width, height, targetWidth, targetHeight)
                : sharpen(stretch(pixels, width, height, targetWidth, targetHeight), targetWidth, targetHeight);
        BufferedImage result = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int left = (SIZE - targetWidth) / 2, top = (SIZE - targetHeight) / 2;
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                result.setRGB(left + x, top + y, argb(scaled, y * targetWidth + x));
            }
        }
        return result;
    }

    // channels a, r, g, b in 0..1, colour multiplied by alpha
    private static float[][] premultiplied(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        float[][] pixels = new float[4][width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                int i = y * width + x;
                float alpha = (pixel >>> 24) / 255f;
                pixels[0][i] = alpha;
                pixels[1][i] = ((pixel >> 16) & 255) / 255f * alpha;
                pixels[2][i] = ((pixel >> 8) & 255) / 255f * alpha;
                pixels[3][i] = (pixel & 255) / 255f * alpha;
            }
        }
        return pixels;
    }

    private static int argb(float[][] pixels, int i) {
        float alpha = clamp(pixels[0][i]);
        if (alpha <= 0f) return 0;
        int a = Math.round(alpha * 255f);
        int r = Math.round(clamp(pixels[1][i] / alpha) * 255f);
        int g = Math.round(clamp(pixels[2][i] / alpha) * 255f);
        int b = Math.round(clamp(pixels[3][i] / alpha) * 255f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // each target pixel averages the source area it covers, weighted by overlap
    private static float[][] shrink(float[][] source, int width, int height, int targetWidth, int targetHeight) {
        float[][] result = new float[4][targetWidth * targetHeight];
        double stepX = (double) width / targetWidth, stepY = (double) height / targetHeight;
        for (int y = 0; y < targetHeight; y++) {
            double top = y * stepY, bottom = top + stepY;
            for (int x = 0; x < targetWidth; x++) {
                double left = x * stepX, right = left + stepX;
                double[] sum = new double[4];
                for (int sy = (int) top; sy < Math.min(height, Math.ceil(bottom)); sy++) {
                    double coverY = Math.min(bottom, sy + 1) - Math.max(top, sy);
                    for (int sx = (int) left; sx < Math.min(width, Math.ceil(right)); sx++) {
                        double weight = coverY * (Math.min(right, sx + 1) - Math.max(left, sx));
                        for (int c = 0; c < 4; c++) sum[c] += source[c][sy * width + sx] * weight;
                    }
                }
                for (int c = 0; c < 4; c++) result[c][y * targetWidth + x] = (float) (sum[c] / (stepX * stepY));
            }
        }
        return result;
    }

    // keys bicubic (a = -0.5) with edge pixels repeated
    private static float[][] stretch(float[][] source, int width, int height, int targetWidth, int targetHeight) {
        float[][] result = new float[4][targetWidth * targetHeight];
        for (int y = 0; y < targetHeight; y++) {
            double sourceY = (y + 0.5) * height / targetHeight - 0.5;
            int baseY = (int) Math.floor(sourceY);
            for (int x = 0; x < targetWidth; x++) {
                double sourceX = (x + 0.5) * width / targetWidth - 0.5;
                int baseX = (int) Math.floor(sourceX);
                for (int c = 0; c < 4; c++) {
                    double sum = 0;
                    for (int j = -1; j <= 2; j++) {
                        int row = Math.max(0, Math.min(height - 1, baseY + j));
                        double weightY = cubic(sourceY - (baseY + j));
                        for (int i = -1; i <= 2; i++) {
                            int column = Math.max(0, Math.min(width - 1, baseX + i));
                            sum += source[c][row * width + column] * weightY * cubic(sourceX - (baseX + i));
                        }
                    }
                    result[c][y * targetWidth + x] = (float) sum;
                }
            }
        }
        return result;
    }

    private static double cubic(double distance) {
        double t = Math.abs(distance);
        if (t <= 1) return 1.5 * t * t * t - 2.5 * t * t + 1;
        if (t < 2) return -0.5 * t * t * t + 2.5 * t * t - 4 * t + 2;
        return 0;
    }

    // unsharp mask: add back SHARPEN of the difference to a gaussian blur; colour never exceeds alpha
    private static float[][] sharpen(float[][] pixels, int width, int height) {
        float[][] result = new float[4][width * height];
        for (int c = 0; c < 4; c++) {
            float[] blurred = blur(blur(pixels[c], width, height, 1, 0), width, height, 0, 1);
            for (int i = 0; i < blurred.length; i++) {
                result[c][i] = clamp(pixels[c][i] + SHARPEN * (pixels[c][i] - blurred[i]));
            }
        }
        for (int i = 0; i < width * height; i++) {
            for (int c = 1; c < 4; c++) result[c][i] = Math.min(result[c][i], result[0][i]);
        }
        return result;
    }

    private static float[] blur(float[] channel, int width, int height, int dx, int dy) {
        float[] result = new float[channel.length];
        int radius = BLUR.length / 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float sum = 0;
                for (int k = -radius; k <= radius; k++) {
                    int column = Math.max(0, Math.min(width - 1, x + k * dx));
                    int row = Math.max(0, Math.min(height - 1, y + k * dy));
                    sum += channel[row * width + column] * BLUR[k + radius];
                }
                result[y * width + x] = sum;
            }
        }
        return result;
    }

    private static float[] gaussian(float sigma, int radius) {
        float[] kernel = new float[radius * 2 + 1];
        float total = 0;
        for (int i = -radius; i <= radius; i++) {
            kernel[i + radius] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
            total += kernel[i + radius];
        }
        for (int i = 0; i < kernel.length; i++) kernel[i] /= total;
        return kernel;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
