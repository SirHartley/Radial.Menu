package radialmenu.layout;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;
import radialmenu.render.IconHue;

// one object is the empty dialog host, the custom dialog delegate and the panel plugin
public final class SlotPicker extends BaseCustomUIPanelPlugin implements InteractionDialogPlugin, CustomDialogDelegate {

    private enum Choice { EMPTY, SUBMENU, ICON_COLOUR, ESCAPE }

    private static final float WIDTH = 440f;
    private static final float HEADING = 20f;
    private static final float ROW = 32f;
    private static final float CELL = 48f;
    private static final float SWATCH = 24f;
    private static final float GAP = 3f;
    private static final float PAD = 10f;
    private static final float[] HUES = {0f, 0.08f, 0.17f, 0.33f, 0.5f, 0.67f, 0.83f};

    private final RadialLayout layout;
    private final int slot;
    private final int child;
    private final Slot current;
    private final String title;
    private final List<AbilityPlugin> abilities;
    private final float height;

    private InteractionDialogAPI dialog;
    private CustomPanelAPI panel;
    private CustomDialogCallback callback;
    private CustomPanelAPI view;
    private final Map<Object, ButtonAPI> buttons = new HashMap<>();
    private Object pending;
    private boolean iconView;
    private String icon;
    private Float hue;

    // plugin render runs after the panel's children, so the sprite or swatch sits over its checkbox
    private static final class IconCell extends BaseCustomUIPanelPlugin {

        private static final float INSET = 4f;

        private final SpriteAPI sprite;
        private final Color swatch;
        private final SlotPicker owner;
        private PositionAPI position;

        IconCell(String sprite, Color swatch, SlotPicker owner) {
            this.sprite = sprite == null ? null : Global.getSettings().getSprite(sprite);
            this.swatch = swatch;
            this.owner = owner;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            this.position = position;
        }

        @Override
        public void buttonPressed(Object data) {
            owner.buttonPressed(data);
        }

        @Override
        public void render(float alphaMult) {
            if (position == null) return;
            if (swatch != null) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                Misc.renderQuad(position.getX() + INSET, position.getY() + INSET, position.getWidth() - 2f * INSET,
                        position.getHeight() - 2f * INSET, swatch, alphaMult);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
            if (sprite == null) return;
            sprite.setSize(position.getWidth() - 2f * INSET, position.getHeight() - 2f * INSET);
            sprite.setAlphaMult(alphaMult);
            sprite.setNormalBlend();
            sprite.renderAtCenter(position.getCenterX(), position.getCenterY());
        }
    }

    private SlotPicker(RadialLayout layout, int slot, int child) {
        this.layout = layout;
        this.slot = slot;
        this.child = child;
        current = layout.get(slot, child);
        title = child < 0 ? "Wheel slot " + (slot + 1) : "Submenu slot " + (child + 1);
        abilities = new ArrayList<>(Global.getSector().getPlayerFleet().getAbilities().values());
        abilities.sort(Comparator.comparingInt(ability -> ability.getSpec().getSortOrder()));
        height = Math.min(460f, Global.getSettings().getScreenHeight() - 160f);
    }

    public static void open(RadialLayout layout, int slot, int child) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui.isShowingDialog()) return;
        ui.showInteractionDialog(new SlotPicker(layout, slot, child), null);
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.setPromptText("");
        dialog.hideTextPanel();
        dialog.hideVisualPanel();
        dialog.getOptionPanel().clearOptions();
        // only reachable if the custom dialog is gone; it handles escape itself
        dialog.setOptionOnEscape("", Choice.ESCAPE);
        dialog.showCustomDialog(WIDTH, height, this);
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.panel = panel;
        this.callback = callback;
        showList();
    }

    // presses change the panel tree, so they wait for advance instead of running inside input dispatch
    @Override
    public void advance(float amount) {
        if (pending == null) return;
        Object press = pending;
        pending = null;
        if (press instanceof AbilityPlugin ability) {
            layout.set(slot, child, Slot.ability(ability.getId()));
            callback.dismissCustomDialog(0);
        } else if (press == Choice.EMPTY) {
            layout.set(slot, child, Slot.EMPTY);
            callback.dismissCustomDialog(0);
        } else if (press == Choice.SUBMENU) {
            icon = current.isSubmenu() && current.icon() != null ? current.icon() : RadialLayout.MOD_ICON;
            hue = current.isSubmenu() ? current.hue() : null;
            showLook();
        } else if (press == Choice.ICON_COLOUR) {
            hue = null;
            mark();
        } else if (press instanceof Float pressed) {
            hue = pressed;
            mark();
        } else if (press instanceof String sprite) {
            icon = sprite;
            mark();
        }
    }

    @Override
    public void buttonPressed(Object data) {
        pending = data;
    }

    @Override
    public void customDialogConfirm() {
        if (iconView) layout.set(slot, child, current.isSubmenu() ? current.withLook(icon, hue) : Slot.submenu(icon, hue));
        dialog.dismiss();
    }

    @Override
    public void customDialogCancel() {
        dialog.dismiss();
    }

    @Override
    public boolean hasCancelButton() {
        return true;
    }

    @Override
    public String getConfirmText() {
        return null;
    }

    @Override
    public String getCancelText() {
        return null;
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return this;
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == Choice.ESCAPE) dialog.dismiss();
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }

    private void showList() {
        iconView = false;
        TooltipMakerAPI heading = newView(title);
        TooltipMakerAPI list = view.createUIElement(WIDTH, height - HEADING - PAD, true);
        if (current.isSubmenu()) {
            list.addPara("Choosing Empty or an ability removes this submenu and its abilities.", Misc.getGrayColor(), 0f);
        }
        row(list, Global.getSettings().getSpriteName("abilities", "clear_slot"), "Empty", Choice.EMPTY);
        if (child < 0) row(list, RadialLayout.MOD_ICON, "Submenu", Choice.SUBMENU);
        for (AbilityPlugin ability : abilities) {
            row(list, ability.getSpec().getIconName(), ability.getSpec().getName(), ability);
            if (ability.getId().equals(current.abilityId())) buttons.get(ability).setChecked(true);
        }
        if (current.isSubmenu() && buttons.containsKey(Choice.SUBMENU)) buttons.get(Choice.SUBMENU).setChecked(true);
        if (current.abilityId() == null && !current.isSubmenu()) buttons.get(Choice.EMPTY).setChecked(true);
        view.addUIElement(list).belowLeft(heading, PAD);
    }

    private void row(TooltipMakerAPI list, String sprite, String name, Object data) {
        float width = WIDTH - 5f;
        TooltipMakerAPI text = list.beginImageWithText(sprite, ROW, width, true);
        buttons.put(data, text.addAreaCheckbox(name, data, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Misc.getBrightPlayerColor(), width - ROW - 10f, ROW, 0f));
        list.addImageWithText(GAP);
    }

    private void showLook() {
        iconView = true;
        TooltipMakerAPI heading = newView("Submenu icon and colour");
        TooltipMakerAPI body = view.createUIElement(WIDTH, height - HEADING - PAD, true);
        List<String> sprites = iconChoices();
        int columns = (int) ((WIDTH - 5f - GAP) / (CELL + GAP));
        int rows = (sprites.size() + columns - 1) / columns;
        CustomPanelAPI grid = view.createCustomPanel(WIDTH - 5f, rows * (CELL + GAP) + GAP, null);
        for (int i = 0; i < sprites.size(); i++) {
            cell(grid, sprites.get(i), sprites.get(i), GAP + i % columns * (CELL + GAP),
                    GAP + i / columns * (CELL + GAP), CELL);
        }
        body.addCustom(grid, 0f);
        body.addPara("Colour", Misc.getTextColor(), PAD);
        CustomPanelAPI swatches = view.createCustomPanel(WIDTH - 5f, SWATCH, null);
        for (int i = 0; i < HUES.length; i++) cell(swatches, HUES[i], null, i * (SWATCH + 4f), 0f, SWATCH);
        body.addCustom(swatches, GAP);
        buttons.put(Choice.ICON_COLOUR, body.addAreaCheckbox("Use the icon's colour", Choice.ICON_COLOUR,
                Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), 220f, SWATCH, GAP));
        view.addUIElement(body).belowLeft(heading, PAD);
        mark();
    }

    // every ability spec queues its icon at startup; the mod icon is loaded by the mod plugin
    private static List<String> iconChoices() {
        Set<String> sprites = new LinkedHashSet<>();
        sprites.add(RadialLayout.MOD_ICON);
        for (String id : Global.getSettings().getSortedAbilityIds()) {
            AbilitySpecAPI spec = Global.getSettings().getAbilitySpec(id);
            if (spec != null && spec.getIconName() != null && !spec.getIconName().isEmpty()) sprites.add(spec.getIconName());
        }
        return new ArrayList<>(sprites);
    }

    // vanilla keeps per-panel element bookkeeping, so each view is a fresh child panel
    private TooltipMakerAPI newView(String text) {
        if (view != null) panel.removeComponent(view);
        buttons.clear();
        view = panel.createCustomPanel(WIDTH, height, this);
        panel.addComponent(view).inTL(0f, 0f);
        TooltipMakerAPI heading = view.createUIElement(WIDTH, HEADING, false);
        heading.addSectionHeading(text, Alignment.MID, 0f);
        view.addUIElement(heading).inTL(0f, 0f);
        return heading;
    }

    private void cell(CustomPanelAPI host, Object data, String sprite, float x, float y, float size) {
        Color swatch = data instanceof Float hue ? IconHue.tileColor(hue) : null;
        CustomPanelAPI cell = host.createCustomPanel(size, size, new IconCell(sprite, swatch, this));
        TooltipMakerAPI element = cell.createUIElement(size, size, false);
        ButtonAPI box = element.addAreaCheckbox("", data, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Misc.getBrightPlayerColor(), size, size, 0f);
        // a tooltip shifts its first element 5 units right; undo it so the box sits under the sprite
        box.getPosition().setXAlignOffset(0f);
        buttons.put(data, box);
        cell.addUIElement(element).inTL(0f, 0f);
        host.addComponent(cell).inTL(x, y);
    }

    // an area checkbox toggles itself on click, so the model sets every one again
    private void mark() {
        for (Map.Entry<Object, ButtonAPI> button : buttons.entrySet()) {
            Object data = button.getKey();
            button.getValue().setChecked(data instanceof String ? data.equals(icon)
                    : data instanceof Float ? data.equals(hue) : data == Choice.ICON_COLOUR && hue == null);
        }
    }
}
