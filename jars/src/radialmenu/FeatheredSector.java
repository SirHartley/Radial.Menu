package radialmenu;

/** Cached, non-overlapping quads. Each vertex contains local x, local y, and alpha coverage. */
final class FeatheredSector {
    private static final int SEGMENTS_PER_CIRCLE = 160;
    final float[] fill;
    final float[] innerOutline;
    final float[] outerOutline;
    final float[] divider;
    final float[] innerBorder;
    final float[] outerBorder;
    final float[] dividerBorder;
    final float[] endDivider;
    final float[] endDividerBorder;
    final float[] extensionStart;
    final float[] extensionEnd;
    final float[] extensionStartBorder;
    final float[] extensionEndBorder;
    final float[] outerGlow;
    final float[] innerGlow;
    final float[] startGlow;
    final float[] endGlow;

    // vertex count and order depend on the angles only, so CampaignRadialMenu blends two outer radii
    FeatheredSector(double start, double end, float inner, float outer, float screenScale) {
        float feather = 1f / screenScale; // One physical screen pixel, independent of UI scale.
        float[] radii = {inner, inner + feather, outer - feather, outer};
        float[] coverage = {0f, 0.65f, 1f, 0f};
        fill = arcs(start, end, radii, coverage);
        innerOutline = arcs(start, end, new float[] {inner - feather, inner, inner + feather},
                new float[] {0f, 1f, 0f});
        outerOutline = arcs(start, end, new float[] {outer - feather, outer, outer + feather},
                new float[] {0f, 1f, 0f});
        // Only this sector's start boundary is emitted. The next sector owns the other divider.
        float[] lineCoverage = {0f, 1f, 1f, 0f};
        divider = divider(start, radii, lineCoverage, feather);
        innerBorder = arcs(start, end, new float[] {inner - 1.5f * feather, inner - 0.5f * feather,
                inner + 0.5f * feather, inner + 1.5f * feather}, new float[] {0f, 1f, 1f, 0f});
        outerBorder = arcs(start, end, new float[] {outer - 1.5f * feather, outer - 0.5f * feather,
                outer + 0.5f * feather, outer + 1.5f * feather}, new float[] {0f, 1f, 1f, 0f});
        dividerBorder = divider(start, radii, lineCoverage, 1.5f * feather);
        // submenu tiles stand alone, so they also draw the side a neighbour would own
        endDivider = divider(end, radii, lineCoverage, feather);
        endDividerBorder = divider(end, radii, lineCoverage, 1.5f * feather);
        float[] extensionRadii = {RadialSelection.OUTER_RADIUS, outer};
        float[] extensionCoverage = {1f, 1f};
        extensionStart = divider(start, extensionRadii, extensionCoverage, feather);
        extensionEnd = divider(end, extensionRadii, extensionCoverage, feather);
        extensionStartBorder = divider(start, extensionRadii, extensionCoverage, 1.5f * feather);
        extensionEndBorder = divider(end, extensionRadii, extensionCoverage, 1.5f * feather);
        float glowWidth = Math.min(16f, (outer - inner) / 2f);
        outerGlow = arcs(start, end, new float[] {outer - glowWidth, outer}, new float[] {0f, 1f});
        innerGlow = arcs(start, end, new float[] {inner, inner + glowWidth}, new float[] {1f, 0f});
        float sideWidth = Math.min(glowWidth,
                (float) (inner * Math.sin(Math.min((end - start) / 2, Math.PI / 2)) * 0.5));
        startGlow = glowSide(start, inner, outer, sideWidth);
        endGlow = glowSide(end, inner, outer, -sideWidth);
    }

    private static float[] glowSide(double angle, float inner, float outer, float inward) {
        float[] vertices = new float[12];
        int offset = line(vertices, 0, angle, inner, 0, 1);
        offset = line(vertices, offset, angle, outer, 0, 1);
        // Keep the offset corner inside the outer circle.
        float insetOuter = (float) Math.sqrt(outer * outer - inward * inward);
        offset = line(vertices, offset, angle, insetOuter, inward, 0);
        line(vertices, offset, angle, inner, inward, 0);
        return vertices;
    }

    static int steps(double start, double end) {
        return Math.max(1, (int) Math.ceil((end - start) * SEGMENTS_PER_CIRCLE / (Math.PI * 2)));
    }

    private static float[] arcs(double start, double end, float[] radii, float[] coverage) {
        int steps = steps(start, end);
        float[] vertices = new float[(radii.length - 1) * steps * 4 * 3];
        int offset = 0;
        for (int band = 0; band < radii.length - 1; band++) {
            for (int step = 0; step < steps; step++) {
                double a = start + (end - start) * step / steps;
                double b = start + (end - start) * (step + 1) / steps;
                offset = polar(vertices, offset, a, radii[band], coverage[band]);
                offset = polar(vertices, offset, a, radii[band + 1], coverage[band + 1]);
                offset = polar(vertices, offset, b, radii[band + 1], coverage[band + 1]);
                offset = polar(vertices, offset, b, radii[band], coverage[band]);
            }
        }
        return vertices;
    }

    private static float[] divider(double angle, float[] radii, float[] coverage, float feather) {
        float[] vertices = new float[(radii.length - 1) * 2 * 4 * 3];
        int offset = 0;
        for (int band = 0; band < radii.length - 1; band++) {
            for (int side = 0; side < 2; side++) {
                float a = (side - 1) * feather;
                float b = side * feather;
                float ca = side == 0 ? 0f : 1f;
                float cb = 1f - ca;
                offset = line(vertices, offset, angle, radii[band], a, coverage[band] * ca);
                offset = line(vertices, offset, angle, radii[band + 1], a, coverage[band + 1] * ca);
                offset = line(vertices, offset, angle, radii[band + 1], b, coverage[band + 1] * cb);
                offset = line(vertices, offset, angle, radii[band], b, coverage[band] * cb);
            }
        }
        return vertices;
    }

    private static int polar(float[] vertices, int offset, double angle, float radius, float coverage) {
        return line(vertices, offset, angle, radius, 0f, coverage);
    }

    private static int line(float[] vertices, int offset, double angle, float radius,
                            float normalOffset, float coverage) {
        vertices[offset++] = (float) (Math.cos(angle) * radius - Math.sin(angle) * normalOffset);
        vertices[offset++] = (float) (Math.sin(angle) * radius + Math.cos(angle) * normalOffset);
        vertices[offset++] = coverage;
        return offset;
    }
}
