package radialmenu;

public final class FeatheredSectorTest {
    public static void main(String[] args) {
        for (int count : new int[] {1, 2, 3, 5, 8}) {
            for (float scale : new float[] {0.75f, 1f, 1.5f, 2f}) verify(count, scale);
        }
        System.out.println("FeatheredSectorTest: radial gradient, edge coverage, and single dividers passed");
    }

    private static void verify(int count, float scale) {
        FeatheredSector[] sectors = new FeatheredSector[count];
        RadialSelection wheel = new RadialSelection(count);
        for (int i = 0; i < sectors.length; i++) {
            double start = wheel.sectorAngle(i) - wheel.sectorWidth() / 2;
            sectors[i] = new FeatheredSector(start, start + wheel.sectorWidth(), 42, 180, scale);
            for (float[] mesh : new float[][] {sectors[i].fill, sectors[i].innerOutline,
                    sectors[i].outerOutline, sectors[i].divider, sectors[i].innerBorder,
                    sectors[i].outerBorder, sectors[i].dividerBorder, sectors[i].outerGlow,
                    sectors[i].innerGlow, sectors[i].startGlow, sectors[i].endGlow}) {
                for (int v = 0; v < mesh.length; v += 3) {
                    check(Float.isFinite(mesh[v]) && Float.isFinite(mesh[v + 1]), "Finite mesh coordinates");
                    check(mesh[v + 2] >= 0 && mesh[v + 2] <= 1, "Coverage must stay in alpha range");
                }
            }
        }
        // Sample the rendered quads, not a separate ideal-circle opacity formula.
        double glowAngle = wheel.sectorAngle(0) + Math.toRadians(0.37);
        double gx = Math.cos(glowAngle), gy = Math.sin(glowAngle);
        check(coverage(sectors[0].outerGlow, gx * 178, gy * 178)
                        > coverage(sectors[0].outerGlow, gx * 170, gy * 170),
                "Outer glow fades towards the sector interior");
        check(coverage(sectors[0].innerGlow, gx * 44, gy * 44)
                        > coverage(sectors[0].innerGlow, gx * 52, gy * 52),
                "Inner edge glow fades towards the sector interior");
        double glowBoundary = wheel.sectorAngle(0) - wheel.sectorWidth() / 2;
        double bx = Math.cos(glowBoundary) * 100, by = Math.sin(glowBoundary) * 100;
        double nx = -Math.sin(glowBoundary), ny = Math.cos(glowBoundary);
        check(coverage(sectors[0].startGlow, bx + nx, by + ny) > 0,
                "Side glow is visible inside the sector");
        near(coverage(sectors[0].startGlow, bx - nx, by - ny), 0, 0.0001,
                "Side glow stays out of the neighboring sector");
        for (int degrees = 0; degrees < 360; degrees += 3) {
            double angle = Math.toRadians(degrees + 0.37);
            check(fill(sectors, angle, 60) < fill(sectors, angle, 100)
                            && fill(sectors, angle, 100) < fill(sectors, angle, 160),
                    "Fill grows brighter toward the outer edge");
            float feather = 1f / scale;
            near(fill(sectors, angle, 41.8f), 0, 0.0001, "Center remains empty");
            near(fill(sectors, angle, 180.2f), 0, 0.0001, "Outside remains empty");
            near(fill(sectors, angle, 42 + feather / 2), 0.325, 0.08, "Inner edge fades over one screen pixel");
            near(fill(sectors, angle, 180 - feather / 2), 0.5, 0.08, "Outer edge fades over one screen pixel");
        }
        for (int i = 0; i < sectors.length; i++) {
            double boundary = wheel.sectorAngle(i) - wheel.sectorWidth() / 2;
            for (float normal : new float[] {-0.1f / scale, 0.1f / scale}) {
                double x = Math.cos(boundary) * 100 - Math.sin(boundary) * normal;
                double y = Math.sin(boundary) * 100 + Math.cos(boundary) * normal;
                double coverage = 0;
                for (FeatheredSector sector : sectors) coverage += coverage(sector.divider, x, y);
                near(coverage, 0.9, 0.0002, "A shared divider is drawn once, not twice");
            }
        }
    }

    private static double fill(FeatheredSector[] sectors, double angle, float radius) {
        double coverage = 0;
        for (FeatheredSector sector : sectors) {
            coverage += coverage(sector.fill, Math.cos(angle) * radius, Math.sin(angle) * radius);
        }
        return coverage;
    }

    private static double coverage(float[] mesh, double x, double y) {
        double result = 0;
        for (int q = 0; q < mesh.length; q += 12) {
            result += Math.max(triangle(mesh, q, q + 3, q + 6, x, y),
                    triangle(mesh, q, q + 6, q + 9, x, y));
        }
        return result;
    }

    private static double triangle(float[] v, int a, int b, int c, double x, double y) {
        double denominator = (v[b + 1] - v[c + 1]) * (v[a] - v[c])
                + (v[c] - v[b]) * (v[a + 1] - v[c + 1]);
        double wa = ((v[b + 1] - v[c + 1]) * (x - v[c])
                + (v[c] - v[b]) * (y - v[c + 1])) / denominator;
        double wb = ((v[c + 1] - v[a + 1]) * (x - v[c])
                + (v[a] - v[c]) * (y - v[c + 1])) / denominator;
        double wc = 1 - wa - wb;
        if (wa < 0 || wb < 0 || wc < 0) return 0;
        return wa * v[a + 2] + wb * v[b + 2] + wc * v[c + 2];
    }

    private static void near(double actual, double expected, double tolerance, String message) {
        check(Math.abs(actual - expected) <= tolerance, message + ": " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
