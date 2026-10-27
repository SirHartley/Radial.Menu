package radialmenu;

public final class RadialSelectionTest {
    public static void main(String[] args) {
        RadialSelection wheel = new RadialSelection(5);
        check(step(wheel, true, true, false, 400, 300) == -1, "Press must not activate");
        check(wheel.isOpen(), "Opening key shows wheel");
        check(step(wheel, false, true, false, 400, 300) == -1, "Center cancels");
        check(!wheel.isOpen(), "Release closes wheel");

        // Independent screen-coordinate examples: up, upper-right, lower-right, lower-left, upper-left.
        int[][] targets = {{400, 420}, {514, 337}, {471, 203}, {329, 203}, {286, 337}};
        for (int i = 0; i < targets.length; i++) {
            step(wheel, true, true, false, 400, 300);
            check(wheel.hoveredSector(targets[i][0], targets[i][1]) == i,
                    "Hover identifies sector " + i);
            check(step(wheel, false, true, false, targets[i][0], targets[i][1]) == i,
                    "Release selects sector " + i);
            check(step(wheel, false, true, false, targets[i][0], targets[i][1]) == -1,
                    "Only one selection per release");
        }
        // Test both sides of every sector boundary, including the wrap past 360 degrees.
        int[][] angles = {{35, 0}, {37, 1}, {107, 1}, {109, 2}, {179, 2},
                {181, 3}, {251, 3}, {253, 4}, {323, 4}, {325, 0}, {359, 0}, {1, 0}};
        step(wheel, true, true, false, 400, 300);
        for (int[] test : angles) {
            double angle = Math.toRadians(test[0]);
            float x = 400 + (float) Math.sin(angle) * 120;
            float y = 300 + (float) Math.cos(angle) * 120;
            check(wheel.hoveredSector(x, y) == test[1], "Clockwise boundary at " + test[0]);
        }
        check(step(wheel, false, true, false, 650, 300) == -1, "Outside wheel cancels");
        step(wheel, true, true, false, 400, 300);
        check(wheel.hoveredSector(400, 490) == 0, "Lifted outer edge remains selectable");
        check(step(wheel, false, true, false, 400, 490) == 0, "Release on lifted edge selects");
        step(wheel, true, true, false, 400, 300);
        step(wheel, true, true, true, 400, 420);
        check(!wheel.isOpen(), "Escape closes");
        step(wheel, true, true, false, 400, 420);
        check(!wheel.isOpen(), "Still-held opening key must not reopen after Escape");
        check(step(wheel, false, true, false, 400, 420) == -1, "Cancelled release cannot select");

        step(wheel, true, true, false, 400, 300);
        step(wheel, true, false, false, 400, 420);
        check(!wheel.isOpen(), "Dialog or focus loss closes");
        check(step(wheel, false, true, false, 400, 420) == -1, "Returning from dialog cannot select");
        step(wheel, true, false, false, 400, 300);
        step(wheel, true, true, false, 400, 300);
        check(!wheel.isOpen(), "Key pressed outside campaign requires a fresh press");
        step(wheel, false, true, false, 400, 300);

        step(wheel, true, true, false, 0, 0);
        check(wheel.getX() - RadialSelection.OUTER_RADIUS - RadialSelection.SELECTION_LIFT >= 0
                && wheel.getY() - RadialSelection.OUTER_RADIUS - RadialSelection.SELECTION_LIFT >= 0, "Lower edge stays on screen");
        step(wheel, false, true, false, 0, 0);
        step(wheel, true, true, false, 800, 600);
        check(wheel.getX() + RadialSelection.OUTER_RADIUS + RadialSelection.SELECTION_LIFT <= 800
                && wheel.getY() + RadialSelection.OUTER_RADIUS + RadialSelection.SELECTION_LIFT <= 600, "Upper edge stays on screen");
        wheel.cancel();
        check(step(wheel, false, true, false, 600, 470) == -1, "Render-side cancellation cannot select");
        step(wheel, true, true, false, 0, 300);
        check(step(wheel, false, true, false, 0, 300) == -1, "Clamping cannot select without movement");

        step(wheel, true, true, false, 400, 300);
        step(wheel, true, true, false, 514, 337); // B released while Ctrl remains held, or vice versa.
        check(wheel.isOpen(), "Another held opening key keeps the wheel open");
        check(step(wheel, false, true, false, 514, 337) == 1, "Last opening key release selects once");
        for (int count : new int[] {1, 2, 3, 8}) {
            RadialSelection dynamic = new RadialSelection(count);
            for (int index = 0; index < count; index++) {
                step(dynamic, true, true, false, 400, 300);
                double angle = Math.PI / 2 - index * Math.PI * 2 / count;
                float x = 400 + (float) Math.cos(angle) * 120;
                float y = 300 + (float) Math.sin(angle) * 120;
                check(step(dynamic, false, true, false, x, y) == index,
                        "Dynamic wheel selects sector " + index + " of " + count);
            }
        }
        RadialSelection empty = new RadialSelection(0);
        check(step(empty, true, true, false, 400, 300) == -1 && !empty.isOpen(),
                "No owned abilities must not open a wheel");
        empty.setSectors(2);
        step(empty, true, true, false, 400, 300);
        check(!empty.isOpen(), "An empty gesture must not reopen while its key remains held");
        step(empty, false, true, false, 400, 300);
        step(empty, true, true, false, 400, 300);
        check(empty.isOpen(), "Next fresh press can use newly owned abilities");
        try {
            empty.setSectors(3);
            throw new AssertionError("An open wheel must reject layout changes");
        } catch (IllegalStateException expected) {
            check(step(empty, false, true, false, 400, 180) == 1,
                    "Rejected layout change leaves the original release target intact");
        }
        System.out.println("RadialSelectionTest: fixed and dynamic gesture checks passed");
    }

    private static int step(RadialSelection wheel, boolean held, boolean allowed,
                            boolean cancel, float x, float y) {
        return wheel.update(held, allowed, cancel, x, y, 800, 600);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
