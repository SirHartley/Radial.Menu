package radialmenu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

public final class RadialMenuModPlugin extends BaseModPlugin {
    private RadialMenuConfig config;

    @Override
    public void onApplicationLoad() throws Exception {
        try {
            config = RadialMenuConfig.fromJSON(
                    Global.getSettings().loadJSON(RadialMenuConfig.PATH, "radial_menu"));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load " + RadialMenuConfig.PATH, failure);
        }
    }
    @Override
    public void onGameLoad(boolean newGame) {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        listeners.removeListenerOfClass(CampaignRadialMenu.class);
        listeners.addListener(new CampaignRadialMenu(config), true);
    }
}
