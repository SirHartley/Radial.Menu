package radialmenu;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import java.util.List;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/** Session-only campaign overlay; delegates ability behavior to the vanilla buttons. */
public final class CampaignRadialMenu implements CampaignInputListener, CampaignUIRenderingListener {
    private final RadialMenuConfig config;
    private final RadialSelection selection = new RadialSelection(0);
    private RadialAbility[] actions = new RadialAbility[0];
    private LabelAPI[] actionLabels = new LabelAPI[0];
    private SpriteAPI[] actionIcons = new SpriteAPI[0];
    private final Map<String, Float> iconHues = new HashMap<String, Float>();
    private final Map<String, BufferedImage> iconBackdrops = new HashMap<String, BufferedImage>();
    private final Map<String, Integer> backdropTextures = new HashMap<String, Integer>();
    private String[] actionIconNames = new String[0];
    private Float[] actionHues = new Float[0];
    private FeatheredSector[] geometry = new FeatheredSector[0];
    private FeatheredSector[] liftedGeometry = new FeatheredSector[0];
    private float geometryScale;
    private LabelAPI cancelLabel;

    CampaignRadialMenu(RadialMenuConfig config) {
        this.config = java.util.Objects.requireNonNull(config, "Radial menu configuration was not loaded");
    }

    private void prepareWheel() {
        // Membership is captured on press so additions/removals cannot shift the release target.
        actions = config.ownedAbilities(Global.getSector().getPlayerFleet());
        selection.setSectors(actions.length);
        actionLabels = new LabelAPI[actions.length];
        actionIcons = new SpriteAPI[actions.length];
        actionIconNames = new String[actions.length];
        actionHues = new Float[actions.length];
        float radius = (RadialSelection.INNER_RADIUS + RadialSelection.OUTER_RADIUS) / 2;
        for (int i = 0; i < actions.length; i++) {
            String iconName = ability(actions[i]).getSpec().getIconName();
            actionIconNames[i] = iconName;
            actionIcons[i] = Global.getSettings().getSprite(iconName);
            actionHues[i] = iconHue(iconName);
            actionIcons[i].setSize(36f, 36f);
            actionIcons[i].setNormalBlend();
            LabelAPI label = Global.getSettings().createLabel("", Fonts.ORBITRON_12);
            label.setAlignment(Alignment.MID);
            float width = SectorText.width(selection.sectorAngle(i), selection.sectorWidth(), radius,
                    12f, label.computeTextHeight("Ag"));
            while (true) {
                String wrapped = SectorText.wrap(actions[i].title, width, label::computeTextWidth);
                label.setText(wrapped);
                float fitted = SectorText.width(selection.sectorAngle(i), selection.sectorWidth(), radius,
                        12f, label.computeTextHeight(wrapped));
                if (fitted >= width - 0.5f) break;
                width = fitted;
            }
            actionLabels[i] = label;
        }
        geometry = new FeatheredSector[actions.length];
        liftedGeometry = new FeatheredSector[actions.length];
        geometryScale = 0f;
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

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
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
        boolean openingKeyHeld = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
                || Keyboard.isKeyDown(Keyboard.KEY_B);
        boolean usableUI = allowed();
        if (usableUI && openingKeyHeld && !selection.isHeld()) prepareWheel();
        int selected = selection.update(openingKeyHeld, usableUI, cancel,
                Global.getSettings().getMouseX(), Global.getSettings().getMouseY(),
                Global.getSettings().getScreenWidth(), Global.getSettings().getScreenHeight());
        if (wasOpen || selection.isOpen()) {
            for (InputEventAPI event : events) {
                if (event.isConsumed()) continue;
                if (event.isMouseEvent() || event.isKeyboardEvent()) event.consume();
            }
            if (usableUI) Global.getSector().getCampaignUI().setDisallowPlayerInteractionsForOneFrame();
        }
        // Recheck on release: cooldowns, toggles, and salvage context may have changed.
        if (selected >= 0 && allowed()) {
            RadialAbility action = actions[selected];
            action.press(ability(action));
        }
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
        int highlightedSector = hovered >= 0 && actions[hovered].isAvailable(ability(actions[hovered]))
                ? hovered : -1;
        Color[] outlines = new Color[actions.length];
        Color[] activeColors = new Color[actions.length];
        // Keep the background readable against the campaign map without making it opaque.
        int fillAlpha = Math.round(Misc.getDarkPlayerColor().getAlpha() * 0.6f);
        updateGeometry();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            // Draw backgrounds first so a neighboring sector cannot cover its label or icon.
            for (int i = 0; i < actions.length; i++) {
                boolean available = actions[i].isAvailable(ability(actions[i]));
                boolean highlighted = highlightedSector == i;
                Color rim = !available ? Misc.getGrayColor() : Misc.getBasePlayerColor();
                Color fill = actionHues[i] == null ? Misc.getDarkPlayerColor()
                        : Color.getHSBColor(actionHues[i], 0.65f, 0.35f);
                float[] hsb = Color.RGBtoHSB(fill.getRed(), fill.getGreen(), fill.getBlue(), null);
                activeColors[i] = Color.getHSBColor(hsb[0], hsb[1], Math.min(1f, hsb[2] + 0.4f));
                drawMesh((highlighted ? liftedGeometry[i] : geometry[i]).fill, Misc.setAlpha(fill, fillAlpha));
                outlines[i] = Misc.scaleAlpha(rim, 0.75f);
            }
            // Active abilities pulse inward from every edge, even when temporarily unavailable.
            double pulse = 0.5 + 0.5 * Math.sin(Math.floorMod(System.nanoTime(), 3_000_000_000L)
                    * (2 * Math.PI / 3_000_000_000.0));
            for (int i = 0; i < actions.length; i++) {
                if (!actions[i].isActive(ability(actions[i]))) continue;
                Color glow = Misc.setAlpha(activeColors[i], (int) (75 + 90 * pulse));
                FeatheredSector sector = i == highlightedSector ? liftedGeometry[i] : geometry[i];
                drawMesh(sector.innerGlow, glow);
                drawMesh(sector.outerGlow, glow);
                if (actions.length > 1) {
                    drawMesh(sector.startGlow, glow);
                    drawMesh(sector.endGlow, glow);
                }
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            for (int i = 0; i < actions.length; i++) {
                boolean available = actions[i].isAvailable(ability(actions[i]));
                LabelAPI label = actionLabels[i];
                label.setColor(!available ? Misc.getGrayColor() : Misc.getTextColor());
                double angle = selection.sectorAngle(i);
                float radius = (RadialSelection.INNER_RADIUS + RadialSelection.OUTER_RADIUS) / 2;
                float x = selection.getX() + (float) Math.cos(angle) * radius;
                float y = selection.getY() + (float) Math.sin(angle) * radius;
                SpriteAPI icon = actionIcons[i];
                drawIconBackdrop(actionIconNames[i], icon, x, y - 10f, available ? 1f : 0.4f);
                drawFadedIcon(icon, x, y - 10f, available ? 1f : 0.4f);
                float labelHeight = label.computeTextHeight(label.getText());
                drawLabel(label, x, y + 12f + labelHeight / 2f);
            }
            // Draw every outline and shared divider once, after all backgrounds.
            for (int i = 0; i < actions.length; i++) {
                FeatheredSector sector = i == highlightedSector ? liftedGeometry[i] : geometry[i];
                Color core = Misc.scaleAlpha(Misc.getTextColor(), 0.75f);
                drawMesh(sector.innerBorder, outlines[i]);
                drawMesh(sector.outerBorder, outlines[i]);
                drawMesh(sector.innerOutline, core);
                drawMesh(sector.outerOutline, core);
                if (actions.length > 1) {
                    // Shared divider stops at the ordinary radius; the selected slice adds its own sides.
                    drawMesh(geometry[i].dividerBorder, outlines[i]);
                    drawMesh(geometry[i].divider, core);
                    if (i == highlightedSector) {
                        drawMesh(sector.extensionStartBorder, outlines[i]);
                        drawMesh(sector.extensionEndBorder, outlines[i]);
                        drawMesh(sector.extensionStart, core);
                        drawMesh(sector.extensionEnd, core);
                    }
                }
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

    private void drawLabel(LabelAPI label, float x, float y) {
        float width = label.computeTextWidth(label.getText());
        float height = label.computeTextHeight(label.getText());
        label.getPosition().setSize(width, height);
        label.getPosition().setLocation(x - width / 2f, y - height / 2f);
        label.render(1f);
    }

}
