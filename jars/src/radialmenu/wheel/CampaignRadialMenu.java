package radialmenu.wheel;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import radialmenu.config.PlayerSettings;
import radialmenu.config.Setting;
import radialmenu.layout.RadialLayout;
import radialmenu.layout.SlotPicker;
import radialmenu.render.AbilityTooltip;
import radialmenu.render.FeatheredSector;
import radialmenu.render.IconBackdrop;
import radialmenu.render.IconFade;
import radialmenu.render.IconHue;
import radialmenu.render.IconScale;
import radialmenu.render.SectorText;
import radialmenu.wheel.RadialSelection.Target;
import radialmenu.wheel.WheelEntry.Kind;

/** Session-only campaign overlay; delegates ability behavior to the vanilla buttons. */
public final class CampaignRadialMenu implements CampaignInputListener, CampaignUIRenderingListener {

    private enum Mesh {
        FILL, EDGE, INNER_EDGE, INNER_GLOW, OUTER_GLOW, START_GLOW, END_GLOW;

        float[] of(FeatheredSector sector) {
            return switch (this) {
                case FILL -> sector.fill;
                case EDGE -> sector.edge;
                case INNER_EDGE -> sector.innerEdge;
                case INNER_GLOW -> sector.innerGlow;
                case OUTER_GLOW -> sector.outerGlow;
                case START_GLOW -> sector.startGlow;
                case END_GLOW -> sector.endGlow;
            };
        }
    }

    private static final float EDIT_HOLD = 0.5f;
    private static final float LIFT_IN = 0.08f;
    private static final float LIFT_OUT = 0.15f;
    private static final float EXTEND_IN = 0.1f;
    private static final float EXTEND_OUT = 0.08f;
    // vanilla button hover timing
    private static final float GLOW_IN = 0.05f;
    private static final float GLOW_OUT = 0.25f;
    // black under the dark ui colour, so the map shows through a little, like vanilla's dimmed dialogs
    private static final float BACKGROUND_SHADE = 0.3f;
    private static final float BACKGROUND_TINT = 0.65f;
    // share of the way from the dark ui colour to its twin in the tile's hue, for tinted tiles at rest
    private static final float REST_TINT = 0.7f;
    // measured from a vanilla ability slot: the outer line shows the base ui colour at 70%, the inner one at 35%
    private static final float EDGE_ALPHA = 0.7f;
    private static final float INNER_EDGE_ALPHA = 0.35f;
    // the two 1-unit border lines cover the outer 2 units of a tile
    private static final float BORDER = 2f;

    private final RadialLayout layout;
    private final RadialSelection selection = new RadialSelection(0);
    private final Map<String, Float> iconHues = new HashMap<String, Float>();
    private final Map<String, BufferedImage> iconBackdrops = new HashMap<String, BufferedImage>();
    private final Map<String, Integer> backdropTextures = new HashMap<String, Integer>();
    // icons that are not IconScale.SIZE square, resized once when read
    private final Map<String, BufferedImage> scaledIcons = new HashMap<>();
    private final Map<String, Integer> scaledTextures = new HashMap<>();
    private final AbilityTooltip tooltip = new AbilityTooltip();
    private LabelAPI cancelLabel;

    private WheelRing main = new WheelRing(new WheelEntry[0], new double[0], 0, 0f, 0f, false, 0f, 0f, 0f, 0f);
    private WheelRing submenu;
    private final FaderUtil extension = new FaderUtil(0f, EXTEND_IN, EXTEND_OUT);

    private boolean animate = true;
    private boolean lift = true;
    private boolean names;
    private boolean tinted;
    private boolean tooltips;
    private boolean pauseWhileOpen;

    private Target pointer = Target.NONE;
    private Target tooltipTarget = Target.NONE;
    private float tooltipTime;
    private boolean tooltipExpanded;
    private boolean returnMousePending;
    private boolean holdingPause;
    private boolean pausedBefore;
    // lwjgl window pixels, the space setCursorPosition takes
    private int pressMouseX;
    private int pressMouseY;
    private float hold;
    private long lastInputNanos;
    private long lastRenderNanos;

    public CampaignRadialMenu(RadialLayout layout) {
        this.layout = Objects.requireNonNull(layout, "Radial menu layout was not loaded");
    }

    private void openWheel() {
        animate = !PlayerSettings.enabled(Setting.NO_ANIMATION);
        lift = PlayerSettings.enabled(Setting.LIFT);
        names = !PlayerSettings.enabled(Setting.ICONS_ONLY);
        tinted = PlayerSettings.enabled(Setting.TINTED_TILES);
        tooltips = PlayerSettings.enabled(Setting.TOOLTIPS);
        returnMousePending = PlayerSettings.enabled(Setting.RETURN_MOUSE);
        pauseWhileOpen = PlayerSettings.enabled(Setting.PAUSE);
        pressMouseX = Mouse.getX();
        pressMouseY = Mouse.getY();
        selection.setDirectional(PlayerSettings.enabled(Setting.SELECT_BY_DIRECTION));
        extension.setDuration(animate ? EXTEND_IN : 0f, animate ? EXTEND_OUT : 0f);
        int count = PlayerSettings.get(Setting.WHEEL_SLOTS);
        WheelEntry[] entries = new WheelEntry[count];
        boolean submenus = false;
        for (int i = 0; i < count; i++) {
            entries[i] = WheelEntry.of(layout.get(i, -1), i);
            submenus |= entries[i].kind == Kind.SUBMENU;
        }
        selection.setSectors(count, submenus ? RadialSelection.CHILD_OUTER_RADIUS + RadialSelection.SELECTION_LIFT
                : RadialSelection.OUTER_RADIUS + RadialSelection.SELECTION_LIFT);
        double[] centers = new double[count];
        for (int i = 0; i < count; i++) centers[i] = selection.sectorAngle(i);
        main = prepare(new WheelRing(entries, centers, selection.sectorWidth(),
                RadialSelection.INNER_RADIUS, RadialSelection.OUTER_RADIUS, false, liftIn(), liftOut(),
                glowIn(), glowOut()));
        submenu = null;
        extension.forceOut();
        pointer = Target.NONE;
        hold = 0f;
        tooltipTarget = Target.NONE;
        tooltipTime = 0f;
    }

    private void extend(int parent) {
        WheelEntry entry = main.entries[parent];
        if (entry.kind != Kind.SUBMENU) return;
        int count = PlayerSettings.get(Setting.SUBMENU_SLOTS);
        selection.extend(parent, count);
        WheelEntry[] entries = new WheelEntry[count];
        double[] centers = new double[count];
        for (int i = 0; i < count; i++) {
            entries[i] = WheelEntry.of(layout.get(entry.slot, i), i);
            centers[i] = selection.childAngle(i);
        }
        submenu = prepare(new WheelRing(entries, centers, RadialSelection.CHILD_WIDTH,
                RadialSelection.CHILD_INNER_RADIUS, RadialSelection.CHILD_OUTER_RADIUS, true, liftIn(), liftOut(),
                glowIn(), glowOut()));
        extension.forceOut();
        extension.fadeIn();
    }

    // a fader with no duration jumps straight to its target
    private float liftIn() {
        return animate ? LIFT_IN : 0f;
    }

    private float liftOut() {
        return animate ? LIFT_OUT : 0f;
    }

    private float glowIn() {
        return animate ? GLOW_IN : 0f;
    }

    private float glowOut() {
        return animate ? GLOW_OUT : 0f;
    }

    private void retract() {
        selection.retract();
        extension.fadeOut();
    }

    private WheelRing prepare(WheelRing ring) {
        for (int i = 0; i < ring.entries.length; i++) {
            WheelEntry entry = ring.entries[i];
            // a saved submenu icon can name a sprite from a mod that is no longer loaded
            if (entry.iconName != null) entry.icon = Global.getSettings().getSprite(entry.iconName);
            if (entry.icon != null) entry.icon.setNormalBlend();
            // every icon gets its backdrop, even when a submenu's own colour replaces the icon's hue
            Float iconHue = entry.iconName == null ? null : readIcon(entry.iconName);
            entry.fillHue = entry.hue != null ? entry.hue : iconHue;
            entry.tint = entry.fillHue == null ? null : restTint(entry.fillHue);
            entry.label = names ? label(entry.title, ring, i, entry.icon != null) : null;
        }
        return ring;
    }

    private static LabelAPI label(String title, WheelRing ring, int index, boolean belowIcon) {
        LabelAPI label = Global.getSettings().createLabel("", Fonts.ORBITRON_12);
        label.setAlignment(Alignment.MID);
        float lineHeight = label.computeTextHeight("Ag");
        float yOffset = belowIcon ? 12f : -lineHeight / 2f;
        float width = SectorText.width(ring.center(index), ring.width, ring.inner, ring.outer, yOffset, lineHeight);
        while (true) {
            String wrapped = SectorText.wrap(title, width, label::computeTextWidth);
            label.setText(wrapped);
            float fitted = SectorText.width(ring.center(index), ring.width, ring.inner, ring.outer,
                    yOffset, label.computeTextHeight(wrapped));
            if (fitted >= width - 0.5f) break;
            width = fitted;
        }
        return label;
    }

    // reads an icon once, caching its hue and the backdrop drawn behind it
    private Float readIcon(String iconName) {
        if (!iconHues.containsKey(iconName)) {
            Float hue = null;
            try (InputStream stream = Global.getSettings().openStream(iconName)) {
                BufferedImage image = ImageIO.read(stream);
                if (image == null) throw new IOException("Unsupported image format: " + iconName);
                hue = IconHue.average(image);
                iconBackdrops.put(iconName, IconBackdrop.create(image));
                BufferedImage scaled = IconScale.normalize(image);
                if (scaled != null) scaledIcons.put(iconName, scaled);
            } catch (IOException e) {
                Global.getLogger(CampaignRadialMenu.class).warn("Could not read ability icon " + iconName, e);
            }
            iconHues.put(iconName, hue);
        }
        return iconHues.get(iconName);
    }

    // the dark ui colour turned toward the same saturation and brightness in the tile's hue
    private static Color restTint(float hue) {
        Color dark = Misc.getDarkPlayerColor();
        float[] hsb = Color.RGBtoHSB(dark.getRed(), dark.getGreen(), dark.getBlue(), null);
        Color twin = Misc.setAlpha(Color.getHSBColor(hue, hsb[1], hsb[2]), dark.getAlpha());
        return Misc.interpolateColor(dark, twin, REST_TINT);
    }

    @Override
    public int getListenerInputPriority() { return 100; }

    private boolean allowed() {
        if (Global.getCurrentState() != GameState.CAMPAIGN || !Display.isActive()
                || Global.getSector().getPlayerFleet() == null) return false;
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        return !ui.isShowingDialog() && !ui.isShowingMenu()
                && ui.getCurrentCoreTab() == null && !ui.isHideUI();
    }

    private AbilityPlugin ability(RadialAbility action) {
        return Global.getSector().getPlayerFleet().getAbility(action.id);
    }

    private boolean available(WheelEntry entry) {
        return switch (entry.kind) {
            case ABILITY -> entry.action.isAvailable(ability(entry.action));
            case SUBMENU -> true;
            case EMPTY -> false;
        };
    }

    private WheelEntry entryAt(Target target) {
        if (target.sector() < 0) return null;
        if (target.child() < 0) return main.entries[target.sector()];
        return submenu == null ? null : submenu.entries[target.child()];
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        float amount = frameTime();
        boolean wasOpen = selection.isOpen();
        boolean cancel = false;
        // Read only unconsumed events. Escape cancels until all opening keys are released.
        // F1 is vanilla's default expand-tooltip key; the player's binding is not in the api
        for (InputEventAPI event : events) {
            if (event.isConsumed() || !event.isKeyDownEvent() || !wasOpen) continue;
            if (event.getEventValue() == Keyboard.KEY_ESCAPE) {
                cancel = true;
                event.consume();
            } else if (event.getEventValue() == Keyboard.KEY_F1) {
                tooltipExpanded = !tooltipExpanded;
                event.consume();
            }
        }
        boolean openingKeyHeld = openingKeyHeld();
        boolean usableUI = allowed();
        float mouseX = Global.getSettings().getMouseX();
        float mouseY = Global.getSettings().getMouseY();
        if (usableUI && openingKeyHeld && !selection.isHeld()) openWheel();
        boolean released = selection.update(openingKeyHeld, usableUI, cancel, mouseX, mouseY,
                Global.getSettings().getScreenWidth(), Global.getSettings().getScreenHeight());
        if (!wasOpen && selection.isOpen() && pauseWhileOpen) {
            pausedBefore = Global.getSector().isPaused();
            Global.getSector().setPaused(true);
            holdingPause = true;
        }
        if (wasOpen || selection.isOpen()) {
            for (InputEventAPI event : events) {
                if (event.isConsumed()) continue;
                if (event.isMouseEvent() || event.isKeyboardEvent()) event.consume();
            }
            if (usableUI) Global.getSector().getCampaignUI().setDisallowPlayerInteractionsForOneFrame();
        }
        if (selection.isOpen()) trackPointer(amount, mouseX, mouseY);
        // Recheck on release: cooldowns, toggles, and salvage context may have changed.
        if (released && allowed()) {
            WheelEntry entry = entryAt(selection.target(mouseX, mouseY));
            if (entry != null) activate(entry);
        }
        // the render pass and an edit hold can close the wheel too, so this runs on the first input frame after
        if (!selection.isOpen()) afterClose();
    }

    private void afterClose() {
        if (returnMousePending) {
            returnMousePending = false;
            Mouse.setCursorPosition(pressMouseX, pressMouseY);
        }
        if (holdingPause) {
            holdingPause = false;
            // a dialog opened from the wheel, such as SlotPicker, now holds the pause and lifts it when dismissed
            if (!Global.getSector().getCampaignUI().isShowingDialog()) Global.getSector().setPaused(pausedBefore);
        }
    }

    // like vanilla's ability bar: the ability's own on-sound replaces the pressed sound when it has one
    private void activate(WheelEntry entry) {
        if (entry.kind != Kind.ABILITY) return;
        AbilityPlugin ability = ability(entry.action);
        if (ability == null || !entry.action.press(ability)) {
            Global.getSoundPlayer().playUISound("ui_button_disabled_pressed", 1f, 1f);
        } else if (!ability.hasCustomButtonPressSounds()) {
            Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
        }
    }

    // input callbacks get no frame time; clamp so a hitch cannot complete an edit hold at once
    private float frameTime() {
        long now = System.nanoTime();
        float amount = lastInputNanos == 0L ? 0f : Math.min(0.1f, (now - lastInputNanos) / 1e9f);
        lastInputNanos = now;
        return amount;
    }

    private static boolean openingKeyHeld() {
        for (Setting hotkey : Setting.HOTKEYS) {
            int key = PlayerSettings.get(hotkey);
            if (key != Keyboard.KEY_NONE && Keyboard.isKeyDown(key)) return true;
        }
        // choices after None are lwjgl buttons 2 (middle), 3 and 4; isButtonDown is false for one the mouse lacks
        int choice = PlayerSettings.get(Setting.MOUSE_BUTTON);
        return choice > 0 && Mouse.isButtonDown(choice + 1);
    }

    // mouse events are consumed while the wheel is open, so the button is polled like the hotkeys
    private void trackPointer(float amount, float mouseX, float mouseY) {
        updateExtension(mouseX, mouseY);
        Target target = selection.target(mouseX, mouseY);
        WheelEntry entry = entryAt(target);
        if (!target.equals(pointer)) {
            pointer = target;
            hold = 0f;
            if (entry != null && entry.kind != Kind.EMPTY) {
                Global.getSoundPlayer().playUISound("ui_button_mouseover", 1f, 1f);
            }
        }
        if (entry == null || !Mouse.isButtonDown(0)) {
            hold = 0f;
            return;
        }
        hold += amount;
        if (hold >= EDIT_HOLD) edit(target, entry);
    }

    // a submenu opens as soon as its tile is hovered; the main ring or the centre closes it again
    private void updateExtension(float mouseX, float mouseY) {
        int parent = selection.extended();
        if (parent < 0) {
            Target target = selection.target(mouseX, mouseY);
            if (target.sector() >= 0 && target.child() < 0) extend(target.sector());
            return;
        }
        float distance = selection.distance(mouseX, mouseY);
        if (distance < RadialSelection.INNER_RADIUS) {
            retract();
        } else if (distance < RadialSelection.OUTER_RADIUS) {
            int sector = selection.sectorAt(mouseX, mouseY);
            if (sector == parent) return;
            retract();
            extend(sector);
        } else {
            // only a directional wheel targets a main tile past the ring, outside the submenu's span
            Target target = selection.target(mouseX, mouseY);
            if (target.child() >= 0 || target.sector() < 0 || target.sector() == parent) return;
            retract();
            extend(target.sector());
        }
    }

    private void edit(Target target, WheelEntry entry) {
        selection.cancel();
        hold = 0f;
        Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
        if (target.child() >= 0) SlotPicker.open(layout, main.entries[target.sector()].slot, entry.slot);
        else SlotPicker.open(layout, entry.slot, -1);
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
        if (selection.isOpen()) {
            Global.getSector().getCampaignUI().setDisallowPlayerInteractionsForOneFrame();
            for (InputEventAPI event : events) {
                if (!event.isConsumed()) event.consume();
            }
        }
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) { }
    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) { }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        if (!allowed()) {
            selection.cancel();
            return;
        }
        if (!selection.isOpen()) return;
        float amount = renderTime();
        Target target = selection.target(Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
        WheelEntry hovered = entryAt(target);
        if (target.equals(tooltipTarget)) {
            tooltipTime += amount;
        } else {
            tooltipTarget = target;
            tooltipTime = 0f;
            tooltipExpanded = false;
        }
        boolean lit = hovered != null && available(hovered);
        int parent = selection.extended();
        int mainLit = parent >= 0 ? parent : lit && target.child() < 0 ? target.sector() : -1;
        int childLit = lit && target.child() >= 0 ? target.child() : -1;
        extension.advance(amount);
        if (submenu != null && parent < 0 && extension.getBrightness() == 0f) submenu = null;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            drawRing(main, mainLit, 1f, amount, target.child() < 0 ? target.sector() : -1);
            if (submenu != null) {
                drawRing(submenu, childLit, ease(extension.getBrightness()), amount, target.child());
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            if (cancelLabel == null) {
                cancelLabel = Global.getSettings().createLabel("Cancel", Fonts.ORBITRON_12);
            }
            cancelLabel.setColor(Misc.getTextColor());
            drawLabel(cancelLabel, selection.getX(), selection.getY(), 1f);
        } finally {
            GL11.glPopAttrib();
        }
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        if (!tooltips || !selection.isOpen() || !allowed() || tooltipTime < AbilityTooltip.DELAY) return;
        WheelEntry entry = entryAt(tooltipTarget);
        if (entry == null || entry.kind != Kind.ABILITY) return;
        AbilityPlugin ability = ability(entry.action);
        if (ability == null || !ability.hasTooltip()) return;
        boolean child = tooltipTarget.child() >= 0;
        WheelRing ring = child ? submenu : main;
        double angle = ring.center(child ? tooltipTarget.child() : tooltipTarget.sector());
        float extent = (submenu != null ? RadialSelection.CHILD_OUTER_RADIUS : RadialSelection.OUTER_RADIUS)
                + RadialSelection.SELECTION_LIFT;
        float y = selection.getY() + (float) Math.sin(angle) * ring.middle();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            tooltip.render(ability, tooltipExpanded, selection.getX() - extent, selection.getX() + extent, y,
                    Math.cos(angle) >= 0);
        } finally {
            GL11.glPopAttrib();
        }
    }

    // the render callback gets no frame time and runs while paused, so animations keep their own clock
    private float renderTime() {
        long now = System.nanoTime();
        float amount = lastRenderNanos == 0L ? 0f : Math.min(0.1f, (now - lastRenderNanos) / 1e9f);
        lastRenderNanos = now;
        return amount;
    }

    private static float ease(float brightness) {
        return brightness * brightness * (3f - 2f * brightness);
    }

    // grow is the submenu's extension, 1 for the main ring; it scales both its radius and its alpha
    private void drawRing(WheelRing ring, int lit, float grow, float amount, int held) {
        int count = ring.entries.length;
        ring.updateGeometry();
        float[] eased = new float[count];
        float[] glow = new float[count];
        for (int i = 0; i < count; i++) {
            if (i == lit && lift) ring.lift[i].fadeIn();
            else ring.lift[i].fadeOut();
            ring.lift[i].advance(amount);
            eased[i] = ease(ring.lift[i].getBrightness());
            if (i == lit) ring.glow[i].fadeIn();
            else ring.glow[i].fadeOut();
            ring.glow[i].advance(amount);
            glow[i] = ring.glow[i].getBrightness();
        }
        Color[] activeColors = new Color[count];
        boolean[] greyed = new boolean[count];
        Color shade = Misc.setAlpha(Color.black, Math.round(255 * BACKGROUND_SHADE * grow));
        Color background = Misc.scaleAlpha(Misc.getDarkPlayerColor(), BACKGROUND_TINT * grow);
        // Draw backgrounds first so a neighboring sector cannot cover its label or icon.
        for (int i = 0; i < count; i++) {
            WheelEntry entry = ring.entries[i];
            // an owned ability that cannot be used right now, cooling down included, loses its colour
            greyed[i] = entry.kind == Kind.ABILITY && !available(entry);
            Color color = entry.fillHue == null ? Misc.getBasePlayerColor() : IconHue.tileColor(entry.fillHue);
            activeColors[i] = greyed[i] ? grey(color) : color;
            drawMesh(ring, i, Mesh.FILL, grow, eased[i], shade, false);
            Color tile = greyed[i] ? grey(background)
                    : tinted && entry.tint != null ? Misc.scaleAlpha(entry.tint, BACKGROUND_TINT * grow) : background;
            drawMesh(ring, i, Mesh.FILL, grow, eased[i], tile, false);
            if (glow[i] > 0f) {
                // vanilla's button hover: an additive wash that whitens as it brightens
                Color light = Misc.interpolateColor(activeColors[i], Color.white, glow[i] * 0.5f);
                float strength = glow[i] * 0.5f * Math.min(1f, (Misc.getDarkPlayerColor().getAlpha() + 50f) / 255f);
                drawMesh(ring, i, Mesh.FILL, grow, eased[i], Misc.setAlpha(light, Math.round(255 * strength * grow)), true);
            }
        }
        // Active abilities pulse inward from every edge, even when temporarily unavailable.
        double pulse = !animate ? 0.5 : 0.5 + 0.5 * Math.sin(Math.floorMod(System.nanoTime(), 3_000_000_000L)
                * (2 * Math.PI / 3_000_000_000.0));
        for (int i = 0; i < count; i++) {
            WheelEntry entry = ring.entries[i];
            if (entry.kind != Kind.ABILITY || !entry.action.isActive(ability(entry.action))) continue;
            Color pulseColor = Misc.setAlpha(activeColors[i], (int) ((75 + 90 * pulse) * grow));
            drawMesh(ring, i, Mesh.INNER_GLOW, grow, eased[i], pulseColor);
            drawMesh(ring, i, Mesh.OUTER_GLOW, grow, eased[i], pulseColor);
            drawMesh(ring, i, Mesh.START_GLOW, grow, eased[i], pulseColor);
            drawMesh(ring, i, Mesh.END_GLOW, grow, eased[i], pulseColor);
        }
        for (int i = 0; i < count; i++) {
            WheelEntry entry = ring.entries[i];
            if (entry.kind != Kind.ABILITY) continue;
            AbilityPlugin ability = ability(entry.action);
            if (ability == null || !ability.showCooldownIndicator()) continue;
            double start = ring.center(i) - ring.width / 2;
            drawCooldown(start, start + ring.width, ring.inner, outerRadius(ring, grow, eased[i]),
                    ability.getCooldownFraction(), Misc.setAlpha(activeColors[i], (int) (110 * grow)));
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        for (int i = 0; i < count; i++) {
            WheelEntry entry = ring.entries[i];
            boolean available = available(entry);
            LabelAPI label = entry.label;
            if (label != null) label.setColor(!available ? Misc.getGrayColor() : Misc.getTextColor());
            double angle = ring.center(i);
            // contents ride half the lift, so they stay centred in the taller slice
            float radius = ring.inner + (ring.middle() - ring.inner) * grow
                    + eased[i] * RadialSelection.SELECTION_LIFT / 2f;
            float x = selection.getX() + (float) Math.cos(angle) * radius;
            float y = selection.getY() + (float) Math.sin(angle) * radius;
            if (entry.icon == null) {
                if (label != null) drawLabel(label, x, y, grow);
                continue;
            }
            float alpha = (available ? 1f : 0.4f) * grow;
            // without a name the icon takes the tile's centre
            float iconY = label != null ? y - 12f : y;
            Color tint = RadialLayout.isModIcon(entry.iconName) ? IconHue.modIconColor(entry.fillHue) : Color.white;
            drawIconBackdrop(entry.iconName, x, iconY, alpha, tint);
            drawFadedIcon(entry.iconName, entry.icon, x, iconY, alpha, tint, false);
            if (glow[i] > 0f) drawFadedIcon(entry.iconName, entry.icon, x, iconY, alpha * glow[i] * 0.33f, tint, true);
            if (label != null) drawLabel(label, x, y + 12f + label.computeTextHeight(label.getText()) / 2f, grow);
        }
        // Draw every border after all backgrounds; each tile is closed, so nothing is shared.
        // vanilla keeps a disabled slot's border in the base ui colour
        Color edge = Misc.scaleAlpha(Misc.getBasePlayerColor(), EDGE_ALPHA * grow);
        Color innerEdge = Misc.scaleAlpha(Misc.getBasePlayerColor(), INNER_EDGE_ALPHA * grow);
        for (int i = 0; i < count; i++) {
            drawMesh(ring, i, Mesh.EDGE, grow, eased[i], edge);
            drawMesh(ring, i, Mesh.INNER_EDGE, grow, eased[i], innerEdge);
        }
        if (held >= 0 && hold > 0f && held < count) {
            // the edit hold runs clockwise along the outer edge
            double start = ring.center(held) - ring.width / 2;
            float outer = outerRadius(ring, grow, eased[held]);
            drawBand(start, start + ring.width, RadialSelection.TILE_GAP / 2f, Math.min(1f, hold / EDIT_HOLD),
                    outer - 4f, outer, 1f, 1f, Misc.getBrightPlayerColor());
        }
    }

    // same luminance, no hue
    private static Color grey(Color color) {
        int luminance = Math.round(0.299f * color.getRed() + 0.587f * color.getGreen() + 0.114f * color.getBlue());
        return new Color(luminance, luminance, luminance, color.getAlpha());
    }

    private static float outerRadius(WheelRing ring, float grow, float eased) {
        return ring.inner + (ring.outer - ring.inner) * grow + eased * RadialSelection.SELECTION_LIFT;
    }

    // collapsed, base and lifted meshes share their angles, so vertices pair up and a blend is the in-between radius
    private void drawMesh(WheelRing ring, int index, Mesh mesh, float grow, float lift, Color color) {
        drawMesh(ring, index, mesh, grow, lift, color, false);
    }

    private void drawMesh(WheelRing ring, int index, Mesh mesh, float grow, float lift, Color color, boolean additive) {
        float[] base = mesh.of(ring.base[index]);
        float[] lifted = mesh.of(ring.lifted[index]);
        float[] collapsed = ring.collapsed == null ? base : mesh.of(ring.collapsed[index]);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        // Stock labels may change GL state between meshes; reassert the coverage blend each time.
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < base.length; i += 3) {
            GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                    color.getAlpha() / 255f * base[i + 2]);
            GL11.glVertex2f(selection.getX() + collapsed[i] + (base[i] - collapsed[i]) * grow + (lifted[i] - base[i]) * lift,
                    selection.getY() + collapsed[i + 1] + (base[i + 1] - collapsed[i + 1]) * grow
                            + (lifted[i + 1] - base[i + 1]) * lift);
        }
        GL11.glEnd();
    }

    // textures are made on first draw, on the render thread
    private static Integer texture(String name, Map<String, BufferedImage> images, Map<String, Integer> textures) {
        Integer texture = textures.get(name);
        if (texture == null) {
            BufferedImage image = images.remove(name);
            if (image == null) return null;
            texture = IconBackdrop.upload(image);
            textures.put(name, texture);
        }
        return texture;
    }

    private void drawIconBackdrop(String name, float x, float y, float alpha, Color tint) {
        Integer texture = texture(name, iconBackdrops, backdropTextures);
        if (texture == null) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glColor4f(tint.getRed() / 255f, tint.getGreen() / 255f, tint.getBlue() / 255f, alpha);
        // twice the icon's size, so the backdrop's fade reaches zero at the icon's corners; a readable icon,
        // the only kind with a backdrop, always draws at IconScale.SIZE
        float w = IconScale.SIZE, h = IconScale.SIZE;
        // the upload starts at the image's top row and ui y points up, so texture v 0 belongs at the top
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(x - w, y - h);
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(x + w, y - h);
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(x - w, y + h);
        GL11.glEnd();
    }

    private void drawFadedIcon(String name, SpriteAPI icon, float x, float y, float alpha, Color tint,
                               boolean additive) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        Integer scaled = texture(name, scaledIcons, scaledTextures);
        float tx, ty, tw, th, width, height;
        if (scaled != null) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, scaled);
            // the upload starts at the image's top row, so v runs from 1 at the bottom to 0 at the top
            tx = 0f;
            ty = 1f;
            tw = 1f;
            th = -1f;
            width = IconScale.SIZE;
            height = IconScale.SIZE;
        } else {
            icon.bindTexture();
            tx = icon.getTexX();
            ty = icon.getTexY();
            tw = icon.getTexWidth();
            th = icon.getTexHeight();
            width = icon.getWidth();
            height = icon.getHeight();
        }
        // one texel per unit on whole pixels, so linear filtering does not blur the icon
        float left = snap(x - width / 2f), bottom = snap(y - height / 2f);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < IconFade.MESH.length; i += 3) {
            float u = IconFade.MESH[i], v = IconFade.MESH[i + 1];
            GL11.glColor4f(tint.getRed() / 255f, tint.getGreen() / 255f, tint.getBlue() / 255f,
                    alpha * IconFade.MESH[i + 2]);
            GL11.glTexCoord2f(tx + u * tw, ty + v * th);
            GL11.glVertex2f(left + u * width, bottom + v * height);
        }
        GL11.glEnd();
    }

    private static float snap(float value) {
        float scale = Global.getSettings().getScreenScaleMult();
        return Math.round(value * scale) / scale;
    }

    // fills from the outer edge inward inside the borders; vanilla's fraction is 0 at the start of the cooldown
    // and 1 at its end
    private void drawCooldown(double start, double end, float ringInner, float outer, float fraction, Color color) {
        float feather = 1f / Global.getSettings().getScreenScaleMult();
        float side = RadialSelection.TILE_GAP / 2f + BORDER;
        float inner = ringInner + BORDER;
        float top = outer - BORDER;
        if (top <= inner) return;
        float front = top - (top - inner) * Math.max(0f, Math.min(1f, fraction));
        drawBand(start, end, side, 1f, Math.max(inner, front - feather), front, 0f, 1f, color);
        drawBand(start, end, side, 1f, front, top, 1f, 1f, color);
    }

    // an arc band between straight sides side units inside the boundary rays, like the tile's own sides;
    // portion keeps that share of it, measured clockwise from end
    private void drawBand(double start, double end, float side, float portion, float inner, float outer,
                          float innerCoverage, float outerCoverage, Color color) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        int steps = FeatheredSector.steps(start, end);
        GL11.glBegin(GL11.GL_QUADS);
        for (int step = 0; step < steps; step++) {
            bandVertex(bandAngle(start, end, side, portion, inner, step, steps), inner, innerCoverage, color);
            bandVertex(bandAngle(start, end, side, portion, outer, step, steps), outer, outerCoverage, color);
            bandVertex(bandAngle(start, end, side, portion, outer, step + 1, steps), outer, outerCoverage, color);
            bandVertex(bandAngle(start, end, side, portion, inner, step + 1, steps), inner, innerCoverage, color);
        }
        GL11.glEnd();
    }

    // the full band's corners at different radii lie on one straight side, so the quads keep it straight
    private static double bandAngle(double start, double end, float side, float portion, float radius,
                                    int step, int steps) {
        double inset = Math.asin(Math.min(1f, side / Math.max(radius, side)));
        double high = end - inset;
        double low = high - Math.max(0, end - start - 2 * inset) * portion;
        return low + (high - low) * step / steps;
    }

    private void bandVertex(double angle, float radius, float coverage, Color color) {
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                color.getAlpha() / 255f * coverage);
        GL11.glVertex2f(selection.getX() + (float) Math.cos(angle) * radius,
                selection.getY() + (float) Math.sin(angle) * radius);
    }

    private void drawLabel(LabelAPI label, float x, float y, float alpha) {
        float width = label.computeTextWidth(label.getText());
        float height = label.computeTextHeight(label.getText());
        label.getPosition().setSize(width, height);
        label.getPosition().setLocation(x - width / 2f, y - height / 2f);
        label.render(alpha);
    }
}
