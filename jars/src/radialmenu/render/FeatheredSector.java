package radialmenu.render;

/** Cached, non-overlapping quads. Each vertex contains local x, local y, and alpha coverage. */
public final class FeatheredSector {
    private static final int SEGMENTS_PER_CIRCLE = 160;
    public final float[] fill;
    public final float[] edge;
    public final float[] innerEdge;
    public final float[] outerGlow;
    public final float[] innerGlow;
    public final float[] startGlow;
    public final float[] endGlow;

    // vertex count and order depend on the angles only, so CampaignRadialMenu blends two outer radii.
    // sides run parallel to the boundary rays, gap / 2 inside them, so the gap keeps one width
    public FeatheredSector(double start, double end, float inner, float outer, float gap, float screenScale) {
        float feather = 1f / screenScale; // One physical screen pixel, independent of UI scale.
        float side = gap / 2f;
        float[] radii = {inner, inner + feather, outer - feather, outer};
        float[] coverage = {0f, 0.65f, 1f, 0f};
        fill = join(arcs(start, end, radii, coverage, side + feather),
                side(start, 1f, radii, coverage, new float[] {side, side + feather}, new float[] {0f, 1f}),
                side(end, -1f, radii, coverage, new float[] {side, side + feather}, new float[] {0f, 1f}));
        // vanilla's ability slot border: a 1-unit line with 3-unit chamfers, then one 1 unit inside it with
        // 2-unit chamfers that the caller draws at half alpha
        edge = stroke(corners(start, end, inner, outer, side, 0.5f, 3f), 1f, feather);
        innerEdge = stroke(corners(start, end, inner, outer, side, 1.5f, 2f), 1f, feather);
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

    public static int steps(double start, double end) {
        return Math.max(1, (int) Math.ceil((end - start) * SEGMENTS_PER_CIRCLE / (Math.PI * 2)));
    }

    // the tile's outline as a closed polyline, inset from every edge and with each corner cut
    private static float[] corners(double start, double end, float inner, float outer, float side,
                                   float inset, float cut) {
        float top = outer - inset;
        float bottom = inner + inset;
        float offset = side + inset;
        int steps = steps(start, end);
        float[] points = new float[(2 * (steps + 1) + 4) * 2];
        int index = 0;
        double outerStart = start + Math.asin(Math.min(1f, offset / top)) + cut / top;
        double outerEnd = end - Math.asin(Math.min(1f, offset / top)) - cut / top;
        for (int step = 0; step <= steps; step++) {
            index = point(points, index, outerStart + (outerEnd - outerStart) * step / steps, top, 0f);
        }
        float high = (float) Math.sqrt(Math.max(0f, top * top - offset * offset)) - cut;
        float low = (float) Math.sqrt(Math.max(0f, bottom * bottom - offset * offset)) + cut;
        index = point(points, index, end, high, -offset);
        index = point(points, index, end, low, -offset);
        double innerEnd = end - Math.asin(Math.min(1f, offset / bottom)) - cut / bottom;
        double innerStart = start + Math.asin(Math.min(1f, offset / bottom)) + cut / bottom;
        for (int step = 0; step <= steps; step++) {
            index = point(points, index, innerEnd + (innerStart - innerEnd) * step / steps, bottom, 0f);
        }
        index = point(points, index, start, low, offset);
        point(points, index, start, high, offset);
        return points;
    }

    private static int point(float[] points, int index, double angle, float radius, float normalOffset) {
        points[index++] = (float) (Math.cos(angle) * radius - Math.sin(angle) * normalOffset);
        points[index++] = (float) (Math.sin(angle) * radius + Math.cos(angle) * normalOffset);
        return index;
    }

    // a closed line whose coverage adds up to its width: the antialiasing ramps sit inside it, not around it,
    // so a 1-unit line stays one pixel heavy like vanilla's
    private static float[] stroke(float[] points, float width, float feather) {
        int count = points.length / 2;
        float ramp = Math.min(feather, width);
        float core = (width - ramp) / 2f;
        float[] offsets = {-core - ramp, -core, core, core + ramp};
        float[] coverage = {0f, 1f, 1f, 0f};
        float[] vertices = new float[count * 3 * 4 * 3];
        int offset = 0;
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            float ax = points[i * 2], ay = points[i * 2 + 1];
            float bx = points[next * 2], by = points[next * 2 + 1];
            float length = (float) Math.max(1e-4, Math.hypot(bx - ax, by - ay));
            float nx = -(by - ay) / length, ny = (bx - ax) / length;
            for (int band = 0; band < 3; band++) {
                offset = vertex(vertices, offset, ax + nx * offsets[band], ay + ny * offsets[band], coverage[band]);
                offset = vertex(vertices, offset, ax + nx * offsets[band + 1], ay + ny * offsets[band + 1], coverage[band + 1]);
                offset = vertex(vertices, offset, bx + nx * offsets[band + 1], by + ny * offsets[band + 1], coverage[band + 1]);
                offset = vertex(vertices, offset, bx + nx * offsets[band], by + ny * offsets[band], coverage[band]);
            }
        }
        return vertices;
    }

    private static int vertex(float[] vertices, int offset, float x, float y, float coverage) {
        vertices[offset++] = x;
        vertices[offset++] = y;
        vertices[offset++] = coverage;
        return offset;
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
