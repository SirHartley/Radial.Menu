package radialmenu;

/** Cached, non-overlapping quads. Each vertex contains local x, local y, and alpha coverage. */
final class FeatheredSector {
    private static final int SEGMENTS_PER_CIRCLE = 160;
    final float[] fill;
    final float[] outline;
    final float[] border;
    final float[] outerGlow;
    final float[] innerGlow;
    final float[] startGlow;
    final float[] endGlow;

    // vertex count and order depend on the angles only, so CampaignRadialMenu blends two outer radii.
    // sides run parallel to the boundary rays, gap / 2 inside them, so the gap keeps one width
    FeatheredSector(double start, double end, float inner, float outer, float gap, float screenScale) {
        float feather = 1f / screenScale; // One physical screen pixel, independent of UI scale.
        float side = gap / 2f;
        float[] radii = {inner, inner + feather, outer - feather, outer};
        float[] coverage = {0f, 0.65f, 1f, 0f};
        fill = join(arcs(start, end, radii, coverage, side + feather),
                side(start, 1f, radii, coverage, new float[] {side, side + feather}, new float[] {0f, 1f}),
                side(end, -1f, radii, coverage, new float[] {side, side + feather}, new float[] {0f, 1f}));
        outline = loop(start, end, inner, outer, side, new float[] {-feather, 0f, feather}, new float[] {0f, 1f, 0f});
        border = loop(start, end, inner, outer, side, new float[] {-1.5f * feather, -0.5f * feather,
                0.5f * feather, 1.5f * feather}, new float[] {0f, 1f, 1f, 0f});
        float glowWidth = Math.min(16f, (outer - inner) / 2f);
        outerGlow = arcs(start, end, new float[] {outer - glowWidth, outer}, new float[] {0f, 1f}, side);
        innerGlow = arcs(start, end, new float[] {inner, inner + glowWidth}, new float[] {1f, 0f}, side);
        float sideWidth = Math.min(glowWidth,
                (float) (inner * Math.sin(Math.min((end - start) / 2, Math.PI / 2)) * 0.5));
        float[] span = {inner, outer};
        float[] full = {1f, 1f};
        startGlow = side(start, 1f, span, full, new float[] {side, side + sideWidth}, new float[] {1f, 0f});
        endGlow = side(end, -1f, span, full, new float[] {side, side + sideWidth}, new float[] {1f, 0f});
    }

    static int steps(double start, double end) {
        return Math.max(1, (int) Math.ceil((end - start) * SEGMENTS_PER_CIRCLE / (Math.PI * 2)));
    }

    // a closed band around the tile; offsets run across the edge, outward on the arcs and into the tile on the sides
    private static float[] loop(double start, double end, float inner, float outer, float side,
                                float[] offsets, float[] coverage) {
        float[] outerRadii = new float[offsets.length];
        float[] innerRadii = new float[offsets.length];
        float[] sideOffsets = new float[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            outerRadii[i] = outer + offsets[i];
            innerRadii[i] = inner - offsets[i];
            sideOffsets[i] = side + offsets[i];
        }
        float[] span = {inner, outer};
        float[] full = {1f, 1f};
        return join(arcs(start, end, outerRadii, coverage, side), arcs(start, end, innerRadii, coverage, side),
                side(start, 1f, span, full, sideOffsets, coverage), side(end, -1f, span, full, sideOffsets, coverage));
    }

    // an arc band whose ends stop where it meets the straight sides, inset from the boundary rays by side
    private static float[] arcs(double start, double end, float[] radii, float[] coverage, float side) {
        int steps = steps(start, end);
        float[] vertices = new float[(radii.length - 1) * steps * 4 * 3];
        int offset = 0;
        for (int band = 0; band < radii.length - 1; band++) {
            for (int step = 0; step < steps; step++) {
                offset = arc(vertices, offset, start, end, radii[band], side, step, steps, coverage[band]);
                offset = arc(vertices, offset, start, end, radii[band + 1], side, step, steps, coverage[band + 1]);
                offset = arc(vertices, offset, start, end, radii[band + 1], side, step + 1, steps, coverage[band + 1]);
                offset = arc(vertices, offset, start, end, radii[band], side, step + 1, steps, coverage[band]);
            }
        }
        return vertices;
    }

    private static int arc(float[] vertices, int offset, double start, double end, float radius, float side,
                           int step, int steps, float coverage) {
        double inset = Math.asin(Math.min(1f, side / Math.max(radius, side)));
        double span = Math.max(0, end - start - 2 * inset);
        return line(vertices, offset, start + inset + span * step / steps, radius, 0f, coverage);
    }

    // a band along the straight side next to the boundary ray; direction 1 means the tile lies counterclockwise
    private static float[] side(double angle, float direction, float[] radii, float[] radialCoverage,
                                float[] offsets, float[] offsetCoverage) {
        float[] vertices = new float[(radii.length - 1) * (offsets.length - 1) * 4 * 3];
        int offset = 0;
        for (int band = 0; band < radii.length - 1; band++) {
            for (int across = 0; across < offsets.length - 1; across++) {
                offset = along(vertices, offset, angle, direction, radii[band], offsets[across],
                        radialCoverage[band] * offsetCoverage[across]);
                offset = along(vertices, offset, angle, direction, radii[band + 1], offsets[across],
                        radialCoverage[band + 1] * offsetCoverage[across]);
                offset = along(vertices, offset, angle, direction, radii[band + 1], offsets[across + 1],
                        radialCoverage[band + 1] * offsetCoverage[across + 1]);
                offset = along(vertices, offset, angle, direction, radii[band], offsets[across + 1],
                        radialCoverage[band] * offsetCoverage[across + 1]);
            }
        }
        return vertices;
    }

    // the point on the circle of this radius, at a fixed distance from the boundary ray
    private static int along(float[] vertices, int offset, double angle, float direction, float radius,
                             float normal, float coverage) {
        float t = (float) Math.sqrt(Math.max(0f, radius * radius - normal * normal));
        return line(vertices, offset, angle, t, direction * normal, coverage);
    }

    private static int line(float[] vertices, int offset, double angle, float radius,
                            float normalOffset, float coverage) {
        vertices[offset++] = (float) (Math.cos(angle) * radius - Math.sin(angle) * normalOffset);
        vertices[offset++] = (float) (Math.sin(angle) * radius + Math.cos(angle) * normalOffset);
        vertices[offset++] = coverage;
        return offset;
    }

    private static float[] join(float[]... parts) {
        int length = 0;
        for (float[] part : parts) length += part.length;
        float[] joined = new float[length];
        int offset = 0;
        for (float[] part : parts) {
            System.arraycopy(part, 0, joined, offset, part.length);
            offset += part.length;
        }
        return joined;
    }
}
