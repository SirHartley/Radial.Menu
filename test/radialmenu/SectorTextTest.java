package radialmenu;

public final class SectorTextTest {
    public static void main(String[] args) {
        float radius = (RadialSelection.INNER_RADIUS + RadialSelection.OUTER_RADIUS) / 2;
        float wide = SectorText.width(Math.PI / 2, Math.PI * 2, radius, 12, 12);
        float narrow = SectorText.width(Math.PI / 2, Math.PI / 4, radius, 12, 12);
        check(wide > narrow, "Narrow sectors provide less room for text");
        check(SectorText.wrap("Sustained Burn", wide, s -> s.length() * 8).equals("Sustained Burn"),
                "Keep words together when the sector has room");
        check(SectorText.wrap("Sustained Burn", 80, s -> s.length() * 8).equals("Sustained\nBurn"),
                "Wrap at word boundaries when the name exceeds available width");
        check(SectorText.wrap("Transponder", 20, s -> s.length() * 8).equals("Transponder"),
                "Keep an oversized word intact rather than losing characters");
        System.out.println("SectorTextTest: sector width and word wrapping passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
