# Architecture

Technical routing for the current implementation. Code paths below are relative to `jars/src/`; data paths are repository-relative. Search the named owner before adding another implementation.

| Reference | Scope |
|---|---|
| [CLAUDE.md](../CLAUDE.md) | Workflow, build gate and document upkeep |
| [RELEASE.md](RELEASE.md) | Version numbers, release jar, package contents, version file and publishing |
| [CHANGELOG.md](CHANGELOG.md) | Change-note format and upkeep for every implementation task |
| [README.md](../README.md) | Player-facing controls, default abilities, configuration, installation and the in-game smoke test |

## Start here

| Change / symptom | Route |
|---|---|
| Which abilities get a sector, and their order | `data/config/radial_menu.json -> RadialMenuConfig.fromJSON -> RadialMenuConfig.ownedAbilities -> CampaignRadialMenu.prepareWheel` |
| Hotkeys | `data/config/LunaSettings.csv -> config/PlayerSettings.reload -> CampaignRadialMenu.openingKeyHeld` (`Setting.HOTKEYS`); [Settings](#settings) |
| Opening keys, Escape, cancel, release target | `CampaignRadialMenu.processCampaignInputPreCore -> RadialSelection.update -> RadialSelection.hoveredSector` |
| What a selection does | `CampaignRadialMenu.processCampaignInputPreCore -> CampaignRadialMenu.allowed -> RadialAbility.press -> AbilityPlugin.pressButton` |
| Greyed-out or pulsing sector | `RadialAbility.isAvailable` (`AbilityPlugin.isUsable`), `RadialAbility.isActive` (`AbilityPlugin.isActiveOrInProgress`) |
| Wheel does not open or closes by itself | `CampaignRadialMenu.allowed`, called from input and from `renderInUICoordsAboveUIBelowTooltips` |
| Sector shapes, borders, dividers, glow | `CampaignRadialMenu.updateGeometry -> FeatheredSector` |
| Sector fill colour | `CampaignRadialMenu.iconHue -> IconHue.average` |
| Icon and icon backdrop | `CampaignRadialMenu.drawFadedIcon` (`IconFade.MESH`), `CampaignRadialMenu.drawIconBackdrop` (`IconBackdrop.create`, `IconBackdrop.upload`) |
| Label wrapping and fit | `CampaignRadialMenu.prepareWheel -> SectorText.width/SectorText.wrap` |

## Registration and lifecycle

| Registry / hook | Owners |
|---|---|
| `mod_info.json` | Mod id `radial_menu` (`RadialMenuModPlugin.MOD_ID`), LunaLib dependency, `modPlugin` `radialmenu.RadialMenuModPlugin`, jar `jars/radialmenu.jar` (packaged from `jars/release/radialmenu.jar`) |
| `data/config/version/version_files.csv` | Registers `radialmenu.version` for Version Checker; [RELEASE.md](RELEASE.md#version-checker) |
| `RadialMenuModPlugin.onApplicationLoad()` | Reads `RadialMenuConfig.PATH` once through `loadJSON(RadialMenuConfig.PATH, "radial_menu")`; any config error is rethrown as `IllegalStateException` and stops the game start. Then `PlayerSettings.install()` |
| `RadialMenuModPlugin.onGameLoad()` | `PlayerSettings.reload()`, `removeListenerOfClass(CampaignRadialMenu.class)`, then `addListener(new CampaignRadialMenu(config), true)` (transient) |
| `data/config/radial_menu.json` | Ordered `abilities` array of ability ids; `RadialMenuConfig.fromJSON` |
| `data/config/LunaSettings.csv` | LunaLib settings rows (one General tab, a Hotkeys header and three `Keycode` rows); ids, types and defaults mirror `config/Setting` |

Load order: `onApplicationLoad -> RadialMenuConfig.fromJSON -> PlayerSettings.install` -> `onGameLoad -> PlayerSettings.reload -> removeListenerOfClass -> addListener`.

One gesture: opening key down `-> CampaignRadialMenu.prepareWheel -> RadialSelection.update` (opens at the clamped mouse position) `-> renderInUICoordsAboveUIBelowTooltips` each frame `->` key up `-> RadialSelection.hoveredSector -> allowed() -> RadialAbility.press`.

### Settings

`config/Setting` owns ids and defaults. `data/config/LunaSettings.csv` supplies the rows and owns the setting names and help text (`fieldName`, `fieldDescription`); it must match `Setting` on ids, types and defaults. `config/PlayerSettings` installs a `LunaSettingsListener` on application start, reloads on every LunaLib save and on game load, and caches sanitized values. Runtime callers read `PlayerSettings.get`, never LunaLib each frame.

- A hotkey is a LunaLib `Keycode`: an LWJGL key code, `0` when the player clears it with Escape. `Setting.sanitize` maps a missing or out-of-range value to the default.
- `PlayerSettings.reload` reads through `LunaSettings.getInt`, which loads LunaLib's settings on first use.
- LunaLib does not call listeners for settings saved during new-game setup (`if (!newGame) LunaSettings.reportSettingsChanged(mod)`), so `onGameLoad` reloads.
- LunaLib passes `fieldName` and `fieldDescription` to `addPara` as format strings; write a literal percent sign as `%%`.

Sources (LunaLib 2.0.1, `libs/Lazylib_lunalib.zip`): `LunaSettings.kt` `getInt`, `LunaSettingsLoader.kt` (`Keycode` defaults), `LunaUIKeybindButton.kt` (Escape clears to `0`), `LunaSettingsUIModsPanel.kt` line 614, `LunaSettingsUISettingsPanel.kt` lines 330 and 341.

### Build and release tools

Tools live in `radialmenu/tools/`, run as standalone programs with JDK 17 from the repository root, and never ship in the release jar.

| Entry point | Use / owners |
|---|---|
| `tools/ReleaseBuild.java` | `build <starsector-core>`: compiles `jars/src` into `jars/release/radialmenu.jar`; `package [tag]`: writes the ZIP, notes and stamped version file to `out/release/`. Reads `libs/` and the game jars, never writes to the game. [RELEASE.md](RELEASE.md) |
| `tools/ReleaseBuildChecks.java` | Headless checks of changelog parsing, version stamping and the package file filter |
| `tools/PlayerSettingsChecks.java` | Checks `data/config/LunaSettings.csv` against `config/Setting`: columns, `radial_menu_` prefix, unique ids, a `Setting` for every row, `Keycode` type, defaults, help text, escaped `%` and keycode sanitizing. Run from the repository root after the compile gate: `java -cp <classes>:<compile jars> radialmenu.tools.PlayerSettingsChecks` |
| `.github/workflows/release.yml` | Runs the checks and a preview package on every push and PR; publishes on a version tag |

## Data identity

| Stable ID | Display name |
|---|---|
| `radial_menu` (mod id) | Campaign Radial Menu |
| `radialmenu` (Java package) | none |
| Ability ids in `radial_menu.json` (`scavenge`, `sustained_burn`, `emergency_burn`, `transponder`, `go_dark` by default) | `AbilityPlugin.getSpec().getName()` |

`RadialMenuModPlugin.MOD_ID` is the mod id for `loadJSON` and LunaLib; it must match `mod_info.json`. Setting ids carry the `radial_menu_` prefix.

| Setting id | LunaLib name |
|---|---|
| `radial_menu_hotkey` | Hotkey |
| `radial_menu_second_hotkey` | Second hotkey |
| `radial_menu_third_hotkey` | Third hotkey |

## Source owners

Runtime classes are in `radialmenu/` and `radialmenu/config/`. In `radialmenu/` all but `RadialMenuModPlugin` and `CampaignRadialMenu` are package-private; `config/` is public so tools can read it.

| File | Owner / connection |
|---|---|
| `radialmenu/RadialMenuModPlugin.java` | Config load and listener registration |
| `radialmenu/RadialMenuConfig.java` | Parses and validates `radial_menu.json`; filters the configured ids to the abilities the fleet owns |
| `radialmenu/CampaignRadialMenu.java` | The listener: input capture, gesture to action, all drawing, per-session icon caches |
| `radialmenu/RadialSelection.java` | Gesture state (held, open, centre, start point), sector angles and hit testing; fixed `INNER_RADIUS`, `OUTER_RADIUS`, `SELECTION_LIFT` in virtual UI pixels |
| `radialmenu/RadialAbility.java` | One owned ability (id, title); availability, active state and the single press path |
| `radialmenu/FeatheredSector.java` | Cached quad meshes (x, y, coverage per vertex) for fill, borders, dividers, the lifted selection and glow |
| `radialmenu/SectorText.java` | Usable label width inside a sector and word wrapping |
| `radialmenu/IconHue.java` | Circular mean hue of an icon's visible, saturated pixels |
| `radialmenu/IconFade.java` | Static mesh fading an icon's edges |
| `radialmenu/config/Setting.java` | Setting ids, defaults and sanitizing; `HOTKEYS` lists the keys that open the wheel |
| `radialmenu/config/PlayerSettings.java` | LunaLib listener and the cached values |
| `radialmenu/IconBackdrop.java` | 2x bicubic copy of an icon with radial opacity falloff, and its GL texture upload |

## Contracts

### Engine and input

- `CampaignRadialMenu` is the only campaign listener. It is registered transient on every game load, so it writes nothing into saves; `removeListenerOfClass` first keeps one instance per session. `addListener(Object, boolean isTransient)` in `ListenerManagerAPI`.
- Hotkeys (`Setting.HOTKEYS`, by default `KEY_B`, `KEY_LCONTROL`, `KEY_RCONTROL`) are read with `Keyboard.isKeyDown` in `openingKeyHeld`, not from input events. A cleared key (`KEY_NONE`) is skipped; with all three cleared the wheel never opens. Escape is read from unconsumed key-down events and consumed.
- While the wheel is open, `processCampaignInputPreCore` consumes every unconsumed mouse and keyboard event and calls `setDisallowPlayerInteractionsForOneFrame`; `processCampaignInputPreFleetControl` does the same. Listener input priority is 100.
- `allowed()` requires `GameState.CAMPAIGN`, an active game window, a player fleet, and no dialog, menu, core tab or hidden UI. The render callback cancels an open wheel when it stops being allowed.
- The wheel does not pause campaign time.

### Gesture

- Membership and sector positions are captured when an opening key goes down (`prepareWheel`) and cannot change during the gesture. Availability is checked again on release, through the current fleet's `getAbility`.
- `RadialSelection.update` opens the wheel only if at least one configured ability is owned. A release closer than `INNER_RADIUS` to the press point selects nothing, so edge clamping cannot turn a stationary tap into a selection.
- Sector 0 points up; the rest follow clockwise. Boundaries are half-open, so a point belongs to exactly one sector.

### Rendering

- Coordinates are virtual UI pixels. `FeatheredSector` meshes are rebuilt only when `getScreenScaleMult()` changes.
- `renderInUICoordsAboveUIBelowTooltips` pushes `GL_ALL_ATTRIB_BITS` and pops it in `finally`. `drawMesh` reasserts blending for every mesh because stock labels change GL state.
- All sector backgrounds are drawn before any icon or label, and all outlines and dividers after them, so neighbouring sectors cannot cover each other.
- Icon hue and backdrop images are read once per icon with `openStream` and `ImageIO`. An unreadable icon logs a warning and the sector falls back to `Misc.getDarkPlayerColor()` with no backdrop.
- Backdrop textures are uploaded on first draw, on the render thread, and kept in `backdropTextures` for the listener's lifetime. They are never deleted; a new listener after each game load uploads them again.

### Shared cross-file constraints

- `RadialAbility.press` is the only path that uses an ability. It calls the vanilla `pressButton`, which keeps toggle behaviour, sounds, costs and confirmation dialogs; the mod adds no ability effects.
- `RadialMenuConfig` rejects blank, non-string and duplicate ids at startup. Ids the fleet does not own are skipped silently; no list is capped.

## Checks

`ReleaseBuildChecks` covers the release tooling and `PlayerSettingsChecks` the settings CSV. There are no automated checks of the wheel itself; rendering, input and ability behaviour need the in-game smoke test in [README.md](../README.md#verification).

## Dead or dormant

None.
