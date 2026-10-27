package radialmenu;

import java.lang.reflect.Proxy;
import com.fs.starfarer.api.characters.AbilityPlugin;

public final class RadialAbilityTest {
    public static void main(String[] args) {
        for (RadialAbility action : new RadialAbility[] {
                new RadialAbility("mod_duration", "Modded Ability"),
                new RadialAbility("mod_toggle", "Modded Toggle")}) {
            check(!action.press(null), "Missing ability must not activate: " + action);
            check(!action.isActive(null), "Missing ability has no active indicator");
            NativeButton button = new NativeButton();
            AbilityPlugin ability = button.api();
            button.usable = false;
            check(!action.isAvailable(ability), "Unusable action must be disabled: " + action);
            check(!action.press(ability) && button.presses == 0, "Unusable action must not be pressed");

            button.usable = true;
            check(action.isAvailable(ability), "Ready action must be available: " + action);
            check(action.press(ability) && button.presses == 1, "Ready action uses native button once");
            button.usable = false; // Eligibility changed after the wheel opened.
            check(!action.press(ability) && button.presses == 1, "Recheck availability before dispatch");
            check(action.title.equals(action.id.equals("mod_duration") ? "Modded Ability" : "Modded Toggle"),
                    "Display only the ability name");
        }

        {
            RadialAbility toggle = new RadialAbility("mod_toggle", "Modded Toggle");
            NativeButton button = new NativeButton();
            button.toggle = true;
            AbilityPlugin ability = button.api();
            check(!toggle.isActive(ability), "Inactive ability has no indicator");
            check(toggle.press(ability) && button.active, "Native button turns toggle on");
            check(toggle.isActive(ability), "Active toggle has an indicator");
            check(toggle.isAvailable(ability), "Active usable toggle must remain selectable");
            check(toggle.press(ability) && !button.active, "Native button turns toggle off");
            button.inProgress = true;
            check(toggle.isActive(ability), "An in-progress duration ability has an indicator");
            button.inProgress = false;
            button.usable = false;
            button.cooldown = true;
            button.active = true;
            check(toggle.isActive(ability), "Unusable active ability keeps its indicator");
            check(!toggle.press(ability), "Active toggle respects native disabled state");
        }

        NativeButton transponder = new NativeButton();
        transponder.toggle = true;
        transponder.confirmationRequired = true;
        AbilityPlugin ability = transponder.api();
        check(new RadialAbility("transponder", "Transponder").press(ability),
                "Transponder dispatches native request");
        check(transponder.confirmationPending && !transponder.active,
                "Native confirmation must be able to defer identity-changing activation");
        transponder.confirm();
        check(transponder.active, "Only native confirmation activates guarded transponder");
        System.out.println("RadialAbilityTest: availability, native dispatch, and toggle checks passed");
    }

    /** Fake only the external game's button boundary, not our radial logic. */
    private static final class NativeButton {
        boolean usable = true;
        boolean active;
        boolean inProgress;
        boolean cooldown;
        boolean toggle;
        boolean confirmationRequired;
        boolean confirmationPending;
        int presses;

        AbilityPlugin api() {
            return (AbilityPlugin) Proxy.newProxyInstance(AbilityPlugin.class.getClassLoader(),
                    new Class<?>[] {AbilityPlugin.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "isUsable": return usable;
                            case "isActive": return active;
                            case "isActiveOrInProgress": return active || inProgress;
                            case "isOnCooldown": return cooldown;
                            case "pressButton":
                                presses++;
                                if (confirmationRequired) confirmationPending = true;
                                else if (toggle) active = !active;
                                return null;
                            default:
                                throw new AssertionError("Must use native pressButton, not " + method.getName());
                        }
                    });
        }

        void confirm() {
            if (!confirmationPending) throw new AssertionError("No pending native confirmation");
            active = true;
            confirmationPending = false;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
