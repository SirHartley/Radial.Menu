# Campaign Radial Menu


## Controls

1. Hold a hotkey on the campaign map to open the wheel at the mouse. The hotkeys are **B**, **left Ctrl** and **right Ctrl** by default; change or clear them under **Hotkeys** in LunaLib's mod settings.
2. Move the mouse out of the center and onto the desired sector.
3. Release the opening key to activate the highlighted action.

Rest the mouse on a **Submenu** sector for a quarter of a second to open it in place. Its **Back** sector at the bottom returns to the main wheel the same way. Submenus hold abilities only; a submenu cannot contain another submenu.

To change a slot, hold the left mouse button on it for half a second while the wheel is open. An arc along the slot's outer edge shows the hold. A list of your fleet's abilities opens: pick one for the slot, empty it, or, on the main wheel, make it a submenu with its own icon and colour. The game pauses while the list is open and resumes when it closes. Changes are saved for every campaign.

Release in the center, outside the ring, or without moving to cancel. **Escape** also cancels; release all opening keys before opening again. If several hotkeys are held, release all of them to select.

The wheel is kept inside the screen and uses the game's virtual UI coordinates, fonts, and player UI colors. Campaign mouse and keyboard input is captured while the wheel is open. The wheel does not pause campaign time.

## Abilities

By default the main wheel has 5 slots and each submenu 5 slots plus Back. Change both under **Wheel** in LunaLib's mod settings: 1 to 10 main wheel slots and 1 to 9 submenu slots. Slots past the count keep their abilities and return when you raise it. Until you change a slot, the main wheel holds these abilities, clockwise from the top:

1. **Scavenge** (salvage): use vanilla Scavenge inside an unscavenged debris field.
2. **Sustained Burn**: toggle on or off.
3. **Emergency Burn**: activate the normal duration ability.
4. **Transponder**: toggle on or off, with the normal identity-reveal warning when required.
5. **Go Dark**: toggle on or off.

Each sector shows the ability name above its icon. Empty slots show **Empty**. A slot whose ability your fleet does not have is greyed out and keeps its place, and so are owned abilities that are cooling down or otherwise unusable. While an ability cools down, its sector fills from the outer edge inward; the ability is ready when the fill reaches the inner ring. Slots are captured when the wheel opens and when it changes layer, so learning or losing an ability cannot move a different action under the mouse during that gesture. Ownership and usability are checked again through the current fleet's ability lookup on release.

Selection uses the game's normal ability-button handler, preserving toggle behavior, sounds, incompatibilities, costs, and confirmation dialogs. The mod does not create loot or replace ability effects.

The wheel does not operate in combat, dialogs, core screens, menus, hidden-UI mode, or while the game window is unfocused. Opening another screen or losing focus cancels an open wheel.

## Configuration

`data/config/radial_menu.json` sets the default main wheel: up to 10 campaign **ability IDs**, not character skill IDs, for the first slots:

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

The array order determines the clockwise order. Names come from each ability's game specification. Other mods' ability IDs can be listed; they are greyed out unless the player's fleet has that ability. More than 10 IDs, blank IDs, non-string entries, and duplicate IDs are configuration errors reported at startup.

The defaults apply until you change a slot in game. From then on the whole layout, including submenus, is saved in Starsector's common data folder (normally `saves/common`) as `radial_menu_layout.json.data`, is shared by every campaign, and `radial_menu.json` is no longer used. Delete that file while the game is closed to return to the defaults. An unreadable layout file is logged and replaced by the defaults.

## Installation

Download the latest `Radial.Menu-<version>.zip` from [Releases](https://github.com/SirHartley/Radial.Menu/releases), extract it into Starsector's `mods` directory and enable **Campaign Radial Menu** in the launcher. It requires [LunaLib](https://github.com/Lukas22041/LunaLib), which in turn requires LazyLib.

This folder was repurposed from Voidborne Ascendancy. Its skills, skill data, art, migration plugin, and PatchLib patch have been removed. This is a different mod ID; it does **not** replace Voidborne Ascendancy for saves that require that mod. Keep the original mod installed separately for those saves.

The radial menu's listener is transient and is registered on every new game or load. It does not write menu state or custom gameplay objects into saves; the layout lives only in the common data folder.

## Verification

In-game smoke test:

- Hold a hotkey inside an unscavenged debris field. Select Scavenge and release. Confirm the normal salvage dialog opens once.
- Repeat outside a field and after scavenging it. Confirm Scavenge is greyed out while the other owned abilities retain their positions.
- With no saved layout, change the configured order and add an ability the player does not own. Restart and confirm the order is respected and the unowned entry is greyed out in its slot. Try an empty list and a single ability.
- Learn or lose an assigned ability between gestures. Confirm the next press updates the slot, while an already-open wheel keeps its targets stable and cannot activate a removed ability.
- Hold the left mouse button on a slot for half a second. Confirm the arc fills and the ability list opens. Assign an ability, empty a slot, and cancel. Confirm the wheel shows each change and that a quick click does nothing.
- Make a main wheel slot a submenu, choose an ability icon, then the mod icon, then a colour. Rest on it to open it, assign abilities inside, confirm the edit list offers no Submenu there, and use Back. Confirm a release over Submenu, Back or Empty does nothing.
- Under Wheel in LunaLib's mod settings, set 10 main wheel slots and 9 submenu slots, then 1 of each. Confirm the next wheel follows without a restart, labels stay inside their sectors, Back stays at the bottom, and hidden slots keep their abilities.
- Load another save and restart the game. Confirm the layout is the same. Delete `radial_menu_layout.json.data` and confirm the defaults return.
- Toggle Sustained Burn, Transponder, and Go Dark on and off. Verify the ability names and icons, wind-up, and normal ability incompatibilities. When turning the transponder on would reveal a hostile identity, verify the native confirmation appears and Cancel leaves it off.
- Activate Emergency Burn once. Verify the normal fuel cost/effect and a disabled sector during its active/cooldown period, with the cooldown fill growing from the outer edge to the inner ring. Try without enough fuel.
- Cancel with the center, outside the ring, and Escape. Test every hotkey, combinations of hotkeys, and a stationary key tap near a screen edge.
- Open Fleet/Intel, pause menus, or a dialog, and switch focus away. Confirm no wheel or unintended activation.
- In LunaLib's mod settings, change one hotkey and clear another, then save. Confirm the wheel follows the new keys without a restart, after loading a save, and in a new game. Clear all three and confirm the wheel cannot open.
- Test paused campaign, UI scaling, save/load, and a second campaign in the same process.
