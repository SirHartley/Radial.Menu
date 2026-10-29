package radialmenu;

import com.fs.starfarer.api.characters.AbilityPlugin;

// one assigned ability, named from the game's specification; the fleet may not own it
final class RadialAbility {

    final String id;
    final String title;

    RadialAbility(String id, String title) {
        this.id = id;
        this.title = title;
    }

    boolean isAvailable(AbilityPlugin ability) {
        // Match the stock ability button, including when an active toggle may be turned off.
        return ability != null && ability.isUsable();
    }

    boolean isActive(AbilityPlugin ability) {
        return ability != null && ability.isActiveOrInProgress();
    }
    boolean press(AbilityPlugin ability) {
        if (!isAvailable(ability)) return false;
        // Preserve native toggle logic, sounds, and transponder identity confirmation.
        ability.pressButton();
        return true;
    }
}
