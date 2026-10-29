package radialmenu;

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
import com.fs.starfarer.api.loading.AbilitySpecAPI;
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

/** Session-only campaign overlay; delegates ability behavior to the vanilla buttons. */
public final class CampaignRadialMenu implements CampaignInputListener, CampaignUIRenderingListener {

    private enum Kind { ABILITY, SUBMENU, EMPTY, BACK }

    private static final float LAYER_DWELL = 0.25f;
    private static final float EDIT_HOLD = 0.5f;
    private static final float LIFT_IN = 0.1f;
    private static final float LIFT_OUT = 0.25f;

    private final RadialLayout layout;
    private final RadialSelection selection = new RadialSelection(0);
    private final Map<String, Float> iconHues = new HashMap<String, Float>();
    private final Map<String, BufferedImage> iconBackdrops = new HashMap<String, BufferedImage>();
    private final Map<String, Integer> backdropTextures = new HashMap<String, Integer>();
    private Entry[] entries = new Entry[0];
    private int submenuSlot = -1;
    private FeatheredSector[] geometry = new FeatheredSector[0];
    private FeatheredSector[] liftedGeometry = new FeatheredSector[0];
    private float geometryScale;
    private FaderUtil[] lift = new FaderUtil[0];
    private long lastRenderNanos;
    private LabelAPI cancelLabel;

    private int pointerSector = -1;
    private float dwell;
    private float hold;
    private long lastInputNanos;

    private static final class Entry {

        final Kind kind;
        final int slot;
        final String title;
        final RadialAbility action;
        final String iconName;
        final Float hue;
        SpriteAPI icon;
        Float fillHue;
        LabelAPI label;

        Entry(Kind kind, int slot, String title, RadialAbility action, String iconName, Float hue) {
            this.kind = kind;
            this.slot = slot;
            this.title = title;
            this.action = action;
            this.iconName = iconName;
            this.hue = hue;
        }
    }

    CampaignRadialMenu(RadialLayout layout) {
        this.layout = Objects.requireNonNull(layout, "Radial menu layout was not loaded");
    }

    // a layer is captured when it opens, so edits and learned abilities cannot shift the release target
    private void buildLayer(float mouseX, float mouseY) {
        boolean submenu = submenuSlot >= 0;
        int count = submenu ? PlayerSettings.get(Setting.SUBMENU_SLOTS) + 1 : PlayerSettings.get(Setting.WHEEL_SLOTS);
        int back = submenu ? count / 2 : -1;
        // back sits at the bottom; an odd count turns the wheel half a sector so it can
        double offset = submenu && count % 2 == 1 ? -Math.PI / count : 0;
        if (selection.isOpen()) selection.relayer(count, offset, mouseX, mouseY);
        else selection.setSectors(count, offset);
        entries = new Entry[count];
        for (int i = 0; i < count; i++) {
            int slot = back >= 0 && i > back ? i - 1 : i;
            Entry entry = i == back ? new Entry(Kind.BACK, -1, "Back", null, null, null)
                    : entry(submenu ? layout.get(submenuSlot, slot) : layout.get(slot, -1), slot);
            // a saved submenu icon can name a sprite from a mod that is no longer loaded
            if (entry.iconName != null) entry.icon = Global.getSettings().getSprite(entry.iconName);
            if (entry.icon != null) {
                entry.icon.setSize(36f, 36f);
                entry.icon.setNormalBlend();
            }
            entry.fillHue = entry.hue != null ? entry.hue : entry.iconName == null ? null : iconHue(entry.iconName);
            entry.label = label(entry.title, i, entry.icon != null);
            entries[i] = entry;
        }
        geometry = new FeatheredSector[count];
        liftedGeometry = new FeatheredSector[count];
        geometryScale = 0f;
        lift = new FaderUtil[count];
        for (int i = 0; i < count; i++) lift[i] = new FaderUtil(0f, LIFT_IN, LIFT_OUT);
        pointerSector = -1;
        dwell = 0f;
        hold = 0f;
    }

    private static Entry entry(Slot slot, int index) {
        if (slot.isSubmenu()) {
            String icon = slot.icon() != null ? slot.icon() : RadialLayout.MOD_ICON;
            return new Entry(Kind.SUBMENU, index, "Submenu", null, icon, slot.hue());
        }
        AbilitySpecAPI spec = slot.abilityId() == null ? null : Global.getSettings().getAbilitySpec(slot.abilityId());
        if (spec == null) return new Entry(Kind.EMPTY, index, "Empty", null, null, null);
        return new Entry(Kind.ABILITY, index, spec.getName(), new RadialAbility(spec.getId(), spec.getName()),
                spec.getIconName(), null);
    }

    private LabelAPI label(String title, int index, boolean belowIcon) {
        LabelAPI label = Global.getSettings().createLabel("", Fonts.ORBITRON_12);
        label.setAlignment(Alignment.MID);
        float radius = (RadialSelection.INNER_RADIUS + RadialSelection.OUTER_RADIUS) / 2;
        float lineHeight = label.computeTextHeight("Ag");
        float yOffset = belowIcon ? 12f : -lineHeight / 2f;
        float width = SectorText.width(selection.sectorAngle(index), selection.sectorWidth(), radius,
                yOffset, lineHeight);
        while (true) {
            String wrapped = SectorText.wrap(title, width, label::computeTextWidth);
            label.setText(wrapped);
            float fitted = SectorText.width(selection.sectorAngle(index), selection.sectorWidth(), radius,
                    yOffset, label.computeTextHeight(wrapped));
            if (fitted >= width - 0.5f) break;
            width = fitted;
        }
        return label;
    }

    private Float iconHue(String iconName) {
        if (!iconHues.containsKey(iconName)) {
            Float hue = null;
            try (InputStream stream = Global.getSettings().openStream(iconName)) {
                BufferedImage image = ImageIO.read(stream);
                if (image == null) throw new IOException("Unsupported image format: " + iconName);
                hue = IconHue.average(image);
                iconBackdrops.put(iconName, IconBackdrop.create(image));
            } catch (IOException e) {
                Global.getLogger(CampaignRadialMenu.class).warn("Could not read ability icon " + iconName, e);
            }
            iconHues.put(iconName, hue);
        }
        return iconHues.get(iconName);
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

    private boolean available(Entry entry) {
        return switch (entry.kind) {
            case ABILITY -> entry.action.isAvailable(ability(entry.action));
            case SUBMENU, BACK -> true;
            case EMPTY -> false;
        };
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        float amount = frameTime();
        boolean wasOpen = selection.isOpen();
        boolean cancel = false;
        // Read only unconsumed events. Escape cancels until all opening keys are released.
        for (InputEventAPI event : events) {
            if (!event.isConsumed() && event.isKeyDownEvent()
                    && event.getEventValue() == Keyboard.KEY_ESCAPE && wasOpen) {
                cancel = true;
                event.consume();
            }
        }
        boolean openingKeyHeld = openingKeyHeld();
        boolean usableUI = allowed();
        float mouseX = Global.getSettings().getMouseX();
        float mouseY = Global.getSettings().getMouseY();
        if (usableUI && openingKeyHeld && !selection.isHeld()) {
            submenuSlot = -1;
            buildLayer(mouseX, mouseY);
        }
        int selected = selection.update(openingKeyHeld, usableUI, cancel, mouseX, mouseY,
                Global.getSettings().getScreenWidth(), Global.getSettings().getScreenHeight());
        if (wasOpen || selection.isOpen()) {
            for (InputEventAPI event : events) {
                if (event.isConsumed()) continue;
                if (event.isMouseEvent() || event.isKeyboardEvent()) event.consume();
            }
            if (usableUI) Global.getSector().getCampaignUI().setDisallowPlayerInteractionsForOneFrame();
        }
        if (selection.isOpen()) trackPointer(amount, mouseX, mouseY);
        // Recheck on release: cooldowns, toggles, and salvage context may have changed.
        if (selected >= 0 && allowed() && entries[selected].kind == Kind.ABILITY) {
            RadialAbility action = entries[selected].action;
            action.press(ability(action));
        }
    }

    // input callbacks get no frame time; clamp so a hitch cannot complete a dwell or hold at once
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
        return false;
    }

    // mouse events are consumed while the wheel is open, so the button is polled like the hotkeys
    private void trackPointer(float amount, float mouseX, float mouseY) {
        int hovered = selection.hoveredSector(mouseX, mouseY);
        if (hovered != pointerSector) {
            pointerSector = hovered;
            dwell = 0f;
            hold = 0f;
        }
        if (hovered < 0) return;
        Entry entry = entries[hovered];
        if (Mouse.isButtonDown(0)) {
            dwell = 0f;
            if (entry.kind == Kind.BACK) return;
            hold += amount;
            if (hold >= EDIT_HOLD) edit(entry);
            return;
        }
        hold = 0f;
        dwell += amount;
        if (dwell < LAYER_DWELL) return;
        if (entry.kind == Kind.SUBMENU) {
            submenuSlot = entry.slot;
            buildLayer(mouseX, mouseY);
        } else if (entry.kind == Kind.BACK) {
            submenuSlot = -1;
            buildLayer(mouseX, mouseY);
        }
    }

    private void edit(Entry entry) {
        selection.cancel();
        hold = 0f;
        if (submenuSlot >= 0) SlotPicker.open(layout, submenuSlot, entry.slot);
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
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) { }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        if (!allowed()) {
            selection.cancel();
            return;
        }
        if (!selection.isOpen()) return;
        int hovered = selection.hoveredSector(
                Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
        int highlightedSector = hovered >= 0 && available(entries[hovered]) ? hovered : -1;
        float[] eased = lift(highlightedSector);
        Color[] outlines = new Color[entries.length];
        Color[] activeColors = new Color[entries.length];
        // Keep the background readable against the campaign map without making it opaque.
        int fillAlpha = Math.round(Misc.getDarkPlayerColor().getAlpha() * 0.6f);
        updateGeometry();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            // Draw backgrounds first so a neighboring sector cannot cover its label or icon.
            for (int i = 0; i < entries.length; i++) {
                boolean available = available(entries[i]);
                Color rim = !available ? Misc.getGrayColor() : Misc.getBasePlayerColor();
                Color fill = entries[i].fillHue == null ? Misc.getDarkPlayerColor()
                        : Color.getHSBColor(entries[i].fillHue, 0.65f, 0.35f);
                float[] hsb = Color.RGBtoHSB(fill.getRed(), fill.getGreen(), fill.getBlue(), null);
                activeColors[i] = Color.getHSBColor(hsb[0], hsb[1], Math.min(1f, hsb[2] + 0.4f));
                drawMesh(geometry[i].fill, liftedGeometry[i].fill, eased[i], Misc.setAlpha(fill, fillAlpha));
                outlines[i] = Misc.scaleAlpha(rim, 0.75f);
            }
            // Active abilities pulse inward from every edge, even when temporarily unavailable.
            double pulse = 0.5 + 0.5 * Math.sin(Math.floorMod(System.nanoTime(), 3_000_000_000L)
                    * (2 * Math.PI / 3_000_000_000.0));
            for (int i = 0; i < entries.length; i++) {
                if (entries[i].kind != Kind.ABILITY || !entries[i].action.isActive(ability(entries[i].action))) continue;
                Color glow = Misc.setAlpha(activeColors[i], (int) (75 + 90 * pulse));
                drawMesh(geometry[i].innerGlow, liftedGeometry[i].innerGlow, eased[i], glow);
                drawMesh(geometry[i].outerGlow, liftedGeometry[i].outerGlow, eased[i], glow);
                if (entries.length > 1) {
                    drawMesh(geometry[i].startGlow, liftedGeometry[i].startGlow, eased[i], glow);
                    drawMesh(geometry[i].endGlow, liftedGeometry[i].endGlow, eased[i], glow);
                }
            }
            for (int i = 0; i < entries.length; i++) {
                if (entries[i].kind != Kind.ABILITY) continue;
                AbilityPlugin ability = ability(entries[i].action);
                if (ability == null || !ability.showCooldownIndicator()) continue;
                double start = selection.sectorAngle(i) - selection.sectorWidth() / 2;
                drawCooldown(start, start + selection.sectorWidth(), outerRadius(eased[i]),
                        ability.getCooldownFraction(), Misc.setAlpha(activeColors[i], 110));
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            for (int i = 0; i < entries.length; i++) {
                Entry entry = entries[i];
                boolean available = available(entry);
                LabelAPI label = entry.label;
                label.setColor(!available ? Misc.getGrayColor() : Misc.getTextColor());
                double angle = selection.sectorAngle(i);
                // contents ride half the lift, so they stay centred in the taller slice
                float radius = (RadialSelection.INNER_RADIUS + RadialSelection.OUTER_RADIUS) / 2
                        + eased[i] * RadialSelection.SELECTION_LIFT / 2f;
                float x = selection.getX() + (float) Math.cos(angle) * radius;
                float y = selection.getY() + (float) Math.sin(angle) * radius;
                float labelHeight = label.computeTextHeight(label.getText());
                if (entry.icon == null) {
                    drawLabel(label, x, y);
                    continue;
                }
                drawIconBackdrop(entry.iconName, entry.icon, x, y - 10f, available ? 1f : 0.4f);
                drawFadedIcon(entry.icon, x, y - 10f, available ? 1f : 0.4f);
                drawLabel(label, x, y + 12f + labelHeight / 2f);
            }
            // Draw every outline and shared divider once, after all backgrounds.
            for (int i = 0; i < entries.length; i++) {
                FeatheredSector base = geometry[i];
                FeatheredSector lifted = liftedGeometry[i];
                Color core = Misc.scaleAlpha(Misc.getTextColor(), 0.75f);
                drawMesh(base.innerBorder, lifted.innerBorder, eased[i], outlines[i]);
                drawMesh(base.outerBorder, lifted.outerBorder, eased[i], outlines[i]);
                drawMesh(base.innerOutline, lifted.innerOutline, eased[i], core);
                drawMesh(base.outerOutline, lifted.outerOutline, eased[i], core);
                if (entries.length > 1) {
                    // Shared divider stops at the ordinary radius; a lifted slice adds its own sides.
                    drawMesh(base.dividerBorder, outlines[i]);
                    drawMesh(base.divider, core);
                    if (eased[i] > 0f) {
                        drawMesh(base.extensionStartBorder, lifted.extensionStartBorder, eased[i], outlines[i]);
                        drawMesh(base.extensionEndBorder, lifted.extensionEndBorder, eased[i], outlines[i]);
                        drawMesh(base.extensionStart, lifted.extensionStart, eased[i], core);
                        drawMesh(base.extensionEnd, lifted.extensionEnd, eased[i], core);
                    }
                }
            }
            if (pointerSector >= 0 && hold > 0f) {
                // the edit hold runs clockwise along the outer edge
                double end = selection.sectorAngle(pointerSector) + selection.sectorWidth() / 2;
                float outer = outerRadius(eased[pointerSector]);
                drawBand(end - selection.sectorWidth() * Math.min(1f, hold / EDIT_HOLD), end,
                        outer - 4f, outer, 1f, 1f, Misc.getBrightPlayerColor());
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            if (cancelLabel == null) {
                cancelLabel = Global.getSettings().createLabel("Cancel", Fonts.ORBITRON_12);
            }
            cancelLabel.setColor(Misc.getTextColor());
            drawLabel(cancelLabel, selection.getX(), selection.getY());
        } finally {
            GL11.glPopAttrib();
        }
    }

    // the render callback gets no frame time and runs while paused, so the lift keeps its own clock
    private float[] lift(int highlightedSector) {
        long now = System.nanoTime();
        float amount = lastRenderNanos == 0L ? 0f : Math.min(0.1f, (now - lastRenderNanos) / 1e9f);
        lastRenderNanos = now;
        float[] eased = new float[entries.length];
        for (int i = 0; i < entries.length; i++) {
            if (i == highlightedSector) lift[i].fadeIn();
            else lift[i].fadeOut();
            lift[i].advance(amount);
            float brightness = lift[i].getBrightness();
            eased[i] = brightness * brightness * (3f - 2f * brightness);
        }
        return eased;
    }

    private static float outerRadius(float eased) {
        return RadialSelection.OUTER_RADIUS + eased * RadialSelection.SELECTION_LIFT;
    }

    private void updateGeometry() {
        float scale = Global.getSettings().getScreenScaleMult();
        if (scale == geometryScale) return;
        for (int i = 0; i < geometry.length; i++) {
            double start = selection.sectorAngle(i) - selection.sectorWidth() / 2;
            geometry[i] = new FeatheredSector(start, start + selection.sectorWidth(),
                    RadialSelection.INNER_RADIUS, RadialSelection.OUTER_RADIUS, scale);
            liftedGeometry[i] = new FeatheredSector(start, start + selection.sectorWidth(),
                    RadialSelection.INNER_RADIUS, RadialSelection.OUTER_RADIUS + RadialSelection.SELECTION_LIFT, scale);
        }
        geometryScale = scale;
    }


    private void drawIconBackdrop(String name, SpriteAPI icon, float x, float y, float alpha) {
        Integer texture = backdropTextures.get(name);
        if (texture == null) {
            BufferedImage image = iconBackdrops.remove(name);
            if (image == null) return;
            texture = IconBackdrop.upload(image);
            backdropTextures.put(name, texture);
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glColor4f(1f, 1f, 1f, alpha);
        float w = icon.getWidth() * 1.25f, h = icon.getHeight() * 1.25f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(x - w, y - h);
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(x + w, y - h);
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(x - w, y + h);
        GL11.glEnd();
    }
    private void drawFadedIcon(SpriteAPI icon, float x, float y, float alpha) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        icon.bindTexture();
        float tx = icon.getTexX(), ty = icon.getTexY();
        float tw = icon.getTexWidth(), th = icon.getTexHeight();
        float width = icon.getWidth() * 1.25f, height = icon.getHeight() * 1.25f;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < IconFade.MESH.length; i += 3) {
            float u = IconFade.MESH[i], v = IconFade.MESH[i + 1];
            GL11.glColor4f(1f, 1f, 1f, alpha * IconFade.MESH[i + 2]);
            GL11.glTexCoord2f(tx + u * tw, ty + v * th);
            GL11.glVertex2f(x + (u - 0.5f) * width, y + (v - 0.5f) * height);
        }
        GL11.glEnd();
    }

    // base and lifted meshes share their angles, so vertices pair up and a blend is the in-between radius
    private void drawMesh(float[] base, float[] lifted, float t, Color color) {
        if (t <= 0f) {
            drawMesh(base, color);
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < base.length; i += 3) {
            GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                    color.getAlpha() / 255f * base[i + 2]);
            GL11.glVertex2f(selection.getX() + base[i] + (lifted[i] - base[i]) * t,
                    selection.getY() + base[i + 1] + (lifted[i + 1] - base[i + 1]) * t);
        }
        GL11.glEnd();
    }

    private void drawMesh(float[] vertices, Color color) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        // Stock labels may change GL state between meshes; reassert the coverage blend each time.
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < vertices.length; i += 3) {
            GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                    color.getAlpha() / 255f * vertices[i + 2]);
            GL11.glVertex2f(selection.getX() + vertices[i], selection.getY() + vertices[i + 1]);
        }
        GL11.glEnd();
    }

    // fills from the outer edge inward; vanilla's fraction is 0 at the start of the cooldown and 1 at its end
    private void drawCooldown(double start, double end, float outer, float fraction, Color color) {
        float feather = 1f / Global.getSettings().getScreenScaleMult();
        float inner = RadialSelection.INNER_RADIUS + feather;
        float top = outer - feather;
        float front = top - (top - inner) * Math.max(0f, Math.min(1f, fraction));
        drawBand(start, end, front - feather, front, 0f, 1f, color);
        drawBand(start, end, front, top, 1f, 1f, color);
    }

    private void drawBand(double start, double end, float inner, float outer,
                          float innerCoverage, float outerCoverage, Color color) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        int steps = FeatheredSector.steps(start, end);
        GL11.glBegin(GL11.GL_QUADS);
        for (int step = 0; step < steps; step++) {
            double a = start + (end - start) * step / steps;
            double b = start + (end - start) * (step + 1) / steps;
            bandVertex(a, inner, innerCoverage, color);
            bandVertex(a, outer, outerCoverage, color);
            bandVertex(b, outer, outerCoverage, color);
            bandVertex(b, inner, innerCoverage, color);
        }
        GL11.glEnd();
    }

    private void bandVertex(double angle, float radius, float coverage, Color color) {
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                color.getAlpha() / 255f * coverage);
        GL11.glVertex2f(selection.getX() + (float) Math.cos(angle) * radius,
                selection.getY() + (float) Math.sin(angle) * radius);
    }

    private void drawLabel(LabelAPI label, float x, float y) {
        float width = label.computeTextWidth(label.getText());
        float height = label.computeTextHeight(label.getText());
        label.getPosition().setSize(width, height);
        label.getPosition().setLocation(x - width / 2f, y - height / 2f);
        label.render(1f);
    }

}
