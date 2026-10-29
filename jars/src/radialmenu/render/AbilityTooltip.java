package radialmenu.render;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseAbilityPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;

// vanilla's ability bar tooltip, rebuilt off the ui tree every frame as vanilla does; an api tooltip maker has
// no border or background, so the frame is drawn here from vanilla's sprites and numbers
public final class AbilityTooltip {

    // vanilla's default hover delay; the player's own setting is not in the api
    public static final float DELAY = 0.3f;
    private static final float PAD = 7f;
    private static final float BACKGROUND_ALPHA = 0.85f;
    private static final float GAP = 10f;
    private static final String MORE = "Press F1 for more info";
    private static final String LESS = "Press F1 to hide";

    private final SpriteAPI west = edge("w");
    private final SpriteAPI westTop = edge("w_top");
    private final SpriteAPI westBottom = edge("w_bot");
    private final SpriteAPI east = edge("e");
    private final SpriteAPI eastTop = edge("e_top");
    private final SpriteAPI eastBottom = edge("e_bot");
    private LabelAPI footer;

    // left and right are the wheel's outer edges; the tooltip sits beside them, centred on y where it fits
    public void render(AbilityPlugin ability, boolean expanded, float left, float right, float y, boolean rightSide) {
        // createUIElement takes 10 off its width, vanilla's own tooltip uses the ability's width as is
        float width = ability.getTooltipWidth() + 10f;
        CustomPanelAPI panel = Global.getSettings().createCustom(width, 0f, null);
        TooltipMakerAPI tooltip = panel.createUIElement(width, 0f, false);
        ability.createTooltip(tooltip, expanded);
        float pad = ability instanceof BaseAbilityPlugin base && !base.getInterruptedList().isEmpty() ? 3f : 10f;
        tooltip.addPara("Hold left-click to assign another ability", Misc.getGrayColor(), pad);
        panel.addUIElement(tooltip).inTL(0f, 0f);
        float contentWidth = tooltip.getPosition().getWidth();
        float contentHeight = tooltip.getPosition().getHeight();
        panel.getPosition().setSize(contentWidth, contentHeight);

        float frameWidth = contentWidth + 2f * PAD;
        float frameHeight = contentHeight + 2f * PAD;
        LabelAPI label = ability.isTooltipExpandable() ? footer(expanded) : null;
        float lineHeight = label == null ? 0f : label.computeTextHeight(label.getText());
        float footerHeight = label == null ? 0f : 8f + lineHeight;
        float screenWidth = Global.getSettings().getScreenWidth();
        float screenHeight = Global.getSettings().getScreenHeight();
        float near = rightSide ? right + GAP : left - GAP - frameWidth;
        float far = rightSide ? left - GAP - frameWidth : right + GAP;
        float x = fits(near, frameWidth, screenWidth) || !fits(far, frameWidth, screenWidth) ? near : far;
        x = Math.max(0f, Math.min(screenWidth - frameWidth, x));
        float bottom = Math.max(footerHeight, Math.min(screenHeight - frameHeight, y - frameHeight / 2f));

        frame(x, bottom, frameWidth, frameHeight);
        panel.getPosition().setLocation(x + PAD, bottom + PAD);
        panel.render(1f);
        if (label == null) return;
        float labelWidth = label.computeTextWidth(label.getText());
        frame(x, bottom - footerHeight, Math.max(200f, labelWidth + 30f), footerHeight);
        label.getPosition().setSize(labelWidth, lineHeight);
        // vanilla hangs the label 10 below the content, which sits PAD inside the frame, and indents it 7
        label.getPosition().setLocation(x + 2f * PAD, bottom + PAD - 10f - lineHeight);
        label.render(1f);
    }

    private LabelAPI footer(boolean expanded) {
        String text = expanded ? LESS : MORE;
        if (footer == null || !footer.getText().equals(text)) {
            footer = Global.getSettings().createLabel(text, Fonts.ORBITRON_12);
            footer.setColor(Misc.getGrayColor());
            footer.setHighlight("F1");
            footer.setHighlightColor(Global.getSettings().getColor("buttonShortcut"));
        }
        return footer;
    }

    private static boolean fits(float x, float width, float screenWidth) {
        return x >= 0f && x + width <= screenWidth;
    }

    private void frame(float x, float y, float width, float height) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Misc.renderQuad(x, y, width, height, Color.black, BACKGROUND_ALPHA);
        side(west, westTop, westBottom, x, y, height);
        side(east, eastTop, eastBottom, x + width - east.getWidth(), y, height);
    }

    // the middle piece tiles up the side: its texture repeats past 1, as in vanilla's frame
    private static void side(SpriteAPI middle, SpriteAPI top, SpriteAPI bottom, float x, float y, float height) {
        float piece = middle.getHeight();
        middle.renderRegion(x, y + piece, 0f, 0f, 1f, height / piece - 2f);
        top.renderRegion(x, y + height - piece, 0f, 0f, 1f, 1f);
        bottom.renderRegion(x, y, 0f, 0f, 1f, 1f);
    }

    private static SpriteAPI edge(String part) {
        return Global.getSettings().getSprite("ui", "ui_border1b_" + part);
    }
}
