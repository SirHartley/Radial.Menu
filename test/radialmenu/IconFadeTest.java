package radialmenu;

public final class IconFadeTest {
    public static void main(String[] args) {
        float[] mesh = IconFade.MESH;
        check(mesh.length == 9 * 4 * 3, "Nine quads cover the icon");
        for (int i = 0; i < mesh.length; i += 3) {
            float x = mesh[i], y = mesh[i + 1], alpha = mesh[i + 2];
            check(x >= 0f && x <= 1f && y >= 0f && y <= 1f, "Vertices stay inside the icon");
            if (x == 0f || x == 1f || y == 0f || y == 1f) {
                check(alpha == 0f, "Image border is transparent");
            } else {
                check(alpha == 1f, "Central 75% remains opaque");
            }
        }
        System.out.println("IconFadeTest: transparent border and opaque center passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
