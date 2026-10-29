package radialmenu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import radialmenu.config.PlayerSettings;
import radialmenu.config.RadialMenuConfig;
import radialmenu.layout.RadialLayout;
import radialmenu.wheel.CampaignRadialMenu;

public final class RadialMenuModPlugin extends BaseModPlugin {
    public static final String MOD_ID = "radial_menu";

    private RadialLayout layout;

    @Override
    public void onApplicationLoad() throws Exception {
        RadialMenuConfig config;
        try {
            config = RadialMenuConfig.fromJSON(
                    Global.getSettings().loadJSON(RadialMenuConfig.PATH, MOD_ID));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load " + RadialMenuConfig.PATH, failure);
        }
        layout = RadialLayout.load(config.abilityIds());
        // submenus can use the mod icon, which no vanilla loader preloads
        Global.getSettings().loadTexture(RadialLayout.MOD_ICON);
        PlayerSettings.install();
    }
    @Override
    public void onGameLoad(boolean newGame) {
        // luna skips settings listeners during new-game setup
        PlayerSettings.reload();
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        listeners.removeListenerOfClass(CampaignRadialMenu.class);
        listeners.addListener(new CampaignRadialMenu(layout), true);
    }
}
