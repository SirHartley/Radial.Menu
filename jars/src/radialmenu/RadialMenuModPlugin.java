package radialmenu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import radialmenu.config.PlayerSettings;

public final class RadialMenuModPlugin extends BaseModPlugin {
    public static final String MOD_ID = "radial_menu";

    private RadialMenuConfig config;

    @Override
    public void onApplicationLoad() throws Exception {
        try {
            config = RadialMenuConfig.fromJSON(
                    Global.getSettings().loadJSON(RadialMenuConfig.PATH, MOD_ID));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load " + RadialMenuConfig.PATH, failure);
        }
        PlayerSettings.install();
    }
    @Override
    public void onGameLoad(boolean newGame) {
        // luna skips settings listeners during new-game setup
        PlayerSettings.reload();
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        listeners.removeListenerOfClass(CampaignRadialMenu.class);
        listeners.addListener(new CampaignRadialMenu(config), true);
    }
}
