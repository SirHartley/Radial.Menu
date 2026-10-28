# Campaign Radial Menu


## Controls

1. Hold a hotkey on the campaign map to open the wheel at the mouse. The hotkeys are **B**, **left Ctrl** and **right Ctrl** by default; change or clear them under **Hotkeys** in LunaLib's mod settings.
2. Move the mouse out of the center and onto the desired sector.
3. Release the opening key to activate the highlighted action.

Release in the center, outside the ring, or without moving to cancel. **Escape** also cancels; release all opening keys before opening again. If several hotkeys are held, release all of them to select.

The wheel is kept inside the screen and uses the game's virtual UI coordinates, fonts, and player UI colors. Campaign mouse and keyboard input is captured while the wheel is open. The wheel does not pause campaign time.

## Abilities

By default, the configuration requests these abilities, clockwise from the top. Only abilities the player's fleet actually has are included:

1. **Scavenge** (salvage): use vanilla Scavenge inside an unscavenged debris field.
2. **Sustained Burn**: toggle on or off.
3. **Emergency Burn**: activate the normal duration ability.
4. **Transponder**: toggle on or off, with the normal identity-reveal warning when required.
5. **Go Dark**: toggle on or off.

Each sector shows the ability name above its icon. Owned abilities that are cooling down or otherwise unusable are greyed out, not removed. Membership and positions are captured when the opening key is pressed, so learning or losing an ability cannot move a different action under the mouse during that gesture. Ownership and usability are checked again through the current fleet's ability lookup on release. The next fresh press rebuilds the wheel from the player's current abilities. If no configured abilities are owned, the wheel does not open.

Selection uses the game's normal ability-button handler, preserving toggle behavior, sounds, incompatibilities, costs, and confirmation dialogs. The mod does not create loot or replace ability effects.

The wheel does not operate in combat, dialogs, core screens, menus, hidden-UI mode, or while the game window is unfocused. Opening another screen or losing focus cancels an open wheel.

## Configuration

Edit `data/config/radial_menu.json` to choose the campaign **ability IDs**, not character skill IDs:

```json
{
  "abilities": [
    "scavenge",
    "sustained_burn",
    "emergency_burn",
    "transponder",
    "go_dark"
  ]
}
```

The array order determines the clockwise order. Names come from each owned ability's game specification. Other mods' ability IDs can be listed; they are omitted unless the player's fleet has that ability. An empty list disables the wheel. Blank IDs, non-string entries, and duplicate IDs are configuration errors reported at startup.

Configuration is read when Starsector starts. Edit this source folder's file before building/installing, or edit the installed mod's file after installation, then restart the game. Installing again copies the source configuration over the installed copy. Large ability lists can crowd the fixed-size wheel's labels; no entries are silently capped.

## Installation

Download the latest `Radial.Menu-<version>.zip` from [Releases](https://github.com/SirHartley/Radial.Menu/releases), extract it into Starsector's `mods` directory and enable **Campaign Radial Menu** in the launcher. It requires [LunaLib](https://github.com/Lukas22041/LunaLib), which in turn requires LazyLib.

This folder was repurposed from Voidborne Ascendancy. Its skills, skill data, art, migration plugin, and PatchLib patch have been removed. This is a different mod ID; it does **not** replace Voidborne Ascendancy for saves that require that mod. Keep the original mod installed separately for those saves.

The radial menu's listener is transient and is registered on every new game or load. It does not write menu state or custom gameplay objects into saves.

## Verification

In-game smoke test:

- Hold a hotkey inside an unscavenged debris field. Select Scavenge and release. Confirm the normal salvage dialog opens once.
- Repeat outside a field and after scavenging it. Confirm Scavenge is greyed out while the other owned abilities retain their positions.
- Change the configured order and add an ability the player does not own. Restart and confirm the order is respected and the unowned entry is omitted. Try an empty list and a single owned ability.
- Learn or lose a configured ability between gestures. Confirm the next press updates the wheel, while an already-open wheel keeps its targets stable and cannot activate a removed ability.
- Toggle Sustained Burn, Transponder, and Go Dark on and off. Verify the ability names and icons, wind-up, and normal ability incompatibilities. When turning the transponder on would reveal a hostile identity, verify the native confirmation appears and Cancel leaves it off.
- Activate Emergency Burn once. Verify the normal fuel cost/effect and a disabled sector during its active/cooldown period. Try without enough fuel.
- Cancel with the center, outside the ring, and Escape. Test every hotkey, combinations of hotkeys, and a stationary key tap near a screen edge.
- Open Fleet/Intel, pause menus, or a dialog, and switch focus away. Confirm no wheel or unintended activation.
- In LunaLib's mod settings, change one hotkey and clear another, then save. Confirm the wheel follows the new keys without a restart, after loading a save, and in a new game. Clear all three and confirm the wheel cannot open.
- Test paused campaign, UI scaling, save/load, and a second campaign in the same process.
