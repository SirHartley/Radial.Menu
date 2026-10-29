package radialmenu.render;

/** Normalized icon mesh: the central 75% stays opaque and the border fades out. */
public final class IconFade {
    public static final float[] MESH = createMesh();

    private IconFade() { }

    private static float[] createMesh() {
        float[] positions = {0f, 0.125f, 0.875f, 1f};
        float[] opacity = {0f, 1f, 1f, 0f};
        float[] mesh = new float[3 * 3 * 4 * 3];
        int offset = 0;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                offset = vertex(mesh, offset, positions[x], positions[y], opacity[x] * opacity[y]);
                offset = vertex(mesh, offset, positions[x + 1], positions[y], opacity[x + 1] * opacity[y]);
                offset = vertex(mesh, offset, positions[x + 1], positions[y + 1], opacity[x + 1] * opacity[y + 1]);
                offset = vertex(mesh, offset, positions[x], positions[y + 1], opacity[x] * opacity[y + 1]);
            }
        }
        return mesh;
    }

    private static int vertex(float[] mesh, int offset, float x, float y, float opacity) {
        mesh[offset++] = x;
        mesh[offset++] = y;
        mesh[offset++] = opacity;
        return offset;
    }
}
