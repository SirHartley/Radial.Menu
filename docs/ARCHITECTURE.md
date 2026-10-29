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
| What each slot holds | `RadialLayout.load` (saved layout, else `data/config/radial_menu.json -> RadialMenuConfig.fromJSON -> RadialLayout.defaults`) `-> CampaignRadialMenu.openWheel` and `extend -> WheelEntry.of`; [Layout](#layout) |
| Slot counts | `Setting.WHEEL_SLOTS`, `Setting.SUBMENU_SLOTS -> PlayerSettings.get -> CampaignRadialMenu.openWheel` and `extend` |
| Submenu ring | `CampaignRadialMenu.updateExtension -> extend/retract -> RadialSelection.extend`, `childAngle`, `target` |
| UI sounds | `CampaignRadialMenu.trackPointer` (mouse-over), `activate` (release), `edit` (edit hold) |
| Editing a slot | `CampaignRadialMenu.trackPointer` (`EDIT_HOLD`) `-> edit -> SlotPicker.open -> RadialLayout.set` |
| Hotkeys | `data/config/LunaSettings.csv -> config/PlayerSettings.reload -> CampaignRadialMenu.openingKeyHeld` (`Setting.HOTKEYS`, `Setting.MOUSE_BUTTON`); [Settings](#settings) |
| Opening keys, Escape, cancel, release target | `CampaignRadialMenu.processCampaignInputPreCore -> RadialSelection.update -> RadialSelection.hoveredSector` |
| What a selection does | `CampaignRadialMenu.processCampaignInputPreCore -> CampaignRadialMenu.allowed -> RadialAbility.press -> AbilityPlugin.pressButton`, for ability entries only |
| Greyed-out or pulsing sector | `CampaignRadialMenu.available`, `RadialAbility.isAvailable` (`AbilityPlugin.isUsable`), `RadialAbility.isActive` (`AbilityPlugin.isActiveOrInProgress`) |
| Cooldown fill | `CampaignRadialMenu.drawCooldown` (`AbilityPlugin.showCooldownIndicator`, `getCooldownFraction`) |
| Ability tooltip | `CampaignRadialMenu.renderInUICoordsAboveUIAndTooltips -> AbilityTooltip.render -> AbilityPlugin.createTooltip` |
| Wheel does not open or closes by itself | `CampaignRadialMenu.allowed`, called from input and from `renderInUICoordsAboveUIBelowTooltips` |
| Tile shapes, gaps, borders, glow | `WheelRing.updateGeometry -> FeatheredSector`, drawn by `CampaignRadialMenu.drawRing` |
| Tile background and hover light | `CampaignRadialMenu.drawRing` (`BACKGROUND_SHADE`, `BACKGROUND_TINT`, glow faders); light colour from submenu `Slot.hue`, else `CampaignRadialMenu.readIcon -> IconHue.average` |
| Icon and icon backdrop | `CampaignRadialMenu.drawFadedIcon` (`IconFade.MESH`), `CampaignRadialMenu.drawIconBackdrop` (`IconBackdrop.create`, `IconBackdrop.upload`) |
| Label wrapping and fit | `CampaignRadialMenu.label -> SectorText.width/SectorText.wrap` |

## Registration and lifecycle

| Registry / hook | Owners |
|---|---|
| `mod_info.json` | Mod id `radial_menu` (`RadialMenuModPlugin.MOD_ID`), LunaLib dependency, `modPlugin` `radialmenu.RadialMenuModPlugin`, jar `jars/radialmenu.jar` (packaged from `jars/release/radialmenu.jar`) |
| `data/config/version/version_files.csv` | Registers `radialmenu.version` for Version Checker; [RELEASE.md](RELEASE.md#version-checker) |
| `RadialMenuModPlugin.onApplicationLoad()` | Reads `RadialMenuConfig.PATH` once through `loadJSON(RadialMenuConfig.PATH, MOD_ID)`; any config error is rethrown as `IllegalStateException` and stops the game start. Then `RadialLayout.load`, `loadTexture(RadialLayout.MOD_ICON)` and `PlayerSettings.install()` |
| `RadialMenuModPlugin.onGameLoad()` | `PlayerSettings.reload()`, `removeListenerOfClass(CampaignRadialMenu.class)`, then `addListener(new CampaignRadialMenu(layout), true)` (transient) |
| `data/config/radial_menu.json` | Default main wheel: an ordered `abilities` array of at most `RadialLayout.MAIN_SLOTS` ability ids; `RadialMenuConfig.fromJSON` |
| `radial_menu_layout.json` (common data) | The edited layout, shared by every campaign; `RadialLayout.load` and `RadialLayout.set` |
| `data/config/LunaSettings.csv` | LunaLib settings rows (one General tab: a Hotkeys header with three `Keycode` rows and one `Radio` row, a Wheel header with two `Int` rows and six `Boolean` rows, an Animation header and two `Boolean` rows); ids, types and defaults mirror `config/Setting` |
| `data/config/LunaSettingsConfig.json` | LunaLib mod-list icon: `radial_menu.iconPath` is `graphics/radialmenu/icon/small_icon_radialmenu.png` (40x40) |

Load order: `onApplicationLoad -> RadialMenuConfig.fromJSON -> RadialLayout.load -> loadTexture -> PlayerSettings.install` -> `onGameLoad -> PlayerSettings.reload -> removeListenerOfClass -> addListener`.

One gesture: opening key down `-> CampaignRadialMenu.openWheel -> RadialSelection.update` (opens at the clamped mouse position) `-> trackPointer` (with `updateExtension`) and `renderInUICoordsAboveUIBelowTooltips` each frame `->` key up `-> RadialSelection.target -> allowed() -> RadialAbility.press`. An edit hold cancels the gesture and opens `SlotPicker`.

### Settings

`config/Setting` owns ids and defaults. `data/config/LunaSettings.csv` supplies the rows and owns the setting names and help text (`fieldName`, `fieldDescription`); it must match `Setting` on ids, types and defaults. `config/PlayerSettings` installs a `LunaSettingsListener` on application start, reloads on every LunaLib save and on game load, and caches sanitized values. Runtime callers read `PlayerSettings.get`, never LunaLib each frame.

- A hotkey is a LunaLib `Keycode`: an LWJGL key code, `0` when the player clears it with Escape. `Setting.sanitize` maps a missing or out-of-range value to the default.
- LunaLib's keybind button records only keyboard key-down events, so a mouse button cannot be bound there. `Setting.MOUSE_BUTTON` is a LunaLib `Radio` row instead (`secondaryValue` "None, Middle, Mouse 4, Mouse 5"). LunaLib saves the chosen label; `PlayerSettings.read` reads it with `LunaSettings.getString` and caches its index in `Setting.choices` (`Type.CHOICE`), and an unknown label falls back to None.
- `Setting.ICONS_ONLY`, `Setting.TINTED_TILES`, `Setting.TOOLTIPS`, `Setting.RETURN_MOUSE`, `Setting.PAUSE`, `Setting.SELECT_BY_DIRECTION`, `Setting.LIFT` and `Setting.NO_ANIMATION` are LunaLib `Boolean` rows, read with `LunaSettings.getBoolean` and cached as 0 or 1; `PlayerSettings.enabled` reads them.
- The slot counts are LunaLib `Int` rows; `Setting.sanitize` clamps them to their bounds. `RadialLayout.MAIN_SLOTS` and `SUBMENU_SLOTS` are those settings' maximums, so the stored layout always covers the largest wheel.
- `PlayerSettings.reload` reads through `LunaSettings.getInt`, which loads LunaLib's settings on first use.
- LunaLib does not call listeners for settings saved during new-game setup (`if (!newGame) LunaSettings.reportSettingsChanged(mod)`), so `onGameLoad` reloads.
- LunaLib passes `fieldName` and `fieldDescription` to `addPara` as format strings; write a literal percent sign as `%%`.
- LunaLib reads `data/config/LunaSettingsConfig.json` merged across all mods and takes `iconPath` under this mod's id for its mod list. The path is a literal asset reference, so `ReleaseBuild` fails a package that leaves the icon out.

Sources (LunaLib 2.0.1, `libs/Lazylib_lunalib.zip`): `LunaSettings.kt` `getInt`, `LunaSettingsLoader.kt` (`Keycode` defaults), `LunaUIKeybindButton.kt` (key-down events only, Escape clears to `0`), `LunaSettingsUISettingsPanel.kt` `createRadioCard` (choices split on commas and trimmed), `LunaSettingsUIModsPanel.kt` line 614, `LunaSettingsUISettingsPanel.kt` lines 330 and 341, `LunaSettingsConfigLoader.kt`.

### Build and release tools

Tools live in `radialmenu/tools/`, run as standalone programs with JDK 17 from the repository root, and never ship in the release jar.

| Entry point | Use / owners |
|---|---|
| `tools/ReleaseBuild.java` | `build <starsector-core>`: compiles `jars/src` into `jars/release/radialmenu.jar`; `package [tag]`: writes the ZIP, notes and stamped version file to `out/release/`. Reads `libs/` and the game jars, never writes to the game. [RELEASE.md](RELEASE.md) |
| `tools/ReleaseBuildChecks.java` | Headless checks of changelog parsing, version stamping and the package file filter |
| `tools/PlayerSettingsChecks.java` | Checks `data/config/LunaSettings.csv` against `config/Setting`: columns, `radial_menu_` prefix, unique ids, a `Setting` for every row, `Keycode`, `Int`, `Boolean` or `Radio` type, defaults, `Int` bounds, `Radio` choices, help text, escaped `%`, keycode sanitizing and count clamping. Run from the repository root after the compile gate: `java -cp <classes>:<compile jars> radialmenu.tools.PlayerSettingsChecks` |
| `.github/workflows/release.yml` | Manual only (`workflow_dispatch`): from a branch it runs the checks and keeps a preview package; from a version tag it publishes |
| `.idea/`, `Radial.Menu.iml` | IntelliJ project, matching Catch.Release's: project JDK `corretto-17` at language level 17, module `Radial.Menu` (`modules.xml`), the `radialmenu:jar` artifact built on make into the gitignored `jars/radialmenu.jar` that `mod_info.json` loads, and the Run Starsector configuration, which makes the project and starts the game from the local install with the debugger on port 5005 |

## Data identity

| Stable ID | Display name |
|---|---|
| `radial_menu` (mod id) | Campaign Radial Menu |
| `radialmenu` (Java package) | none |
| Ability ids in `radial_menu.json` and the saved layout (`scavenge`, `sustained_burn`, `emergency_burn`, `transponder`, `go_dark`, `sensor_burst`, `interdiction_pulse` by default) | `SettingsAPI.getAbilitySpec(id).getName()` |

`RadialMenuModPlugin.MOD_ID` is the mod id for `loadJSON` and LunaLib; it must match `mod_info.json`. Setting ids carry the `radial_menu_` prefix.

| Setting id | LunaLib name |
|---|---|
| `radial_menu_hotkey` | Hotkey |
| `radial_menu_second_hotkey` | Second hotkey |
| `radial_menu_third_hotkey` | Third hotkey |
| `radial_menu_mouse_button` | Mouse button |
| `radial_menu_wheel_slots` | Main wheel slots |
| `radial_menu_submenu_slots` | Submenu slots |
| `radial_menu_icons_only` | Icons only |
| `radial_menu_tinted_tiles` | Tinted tiles |
| `radial_menu_tooltips` | Ability tooltips |
| `radial_menu_return_mouse` | Return the mouse |
| `radial_menu_pause` | Pause while open |
| `radial_menu_select_by_direction` | Select by direction |
| `radial_menu_lift` | Lift the hovered tile |
| `radial_menu_no_animation` | No animations |

## Source owners

Runtime classes are in `radialmenu/` and four packages under it, one per responsibility. Dependencies run one way: `wheel` uses `layout`, `render` and `config`; `layout` uses `render` and `config`; `render` uses no other mod package, and `config` only reads `RadialMenuModPlugin.MOD_ID`. A class or member is public only when another package uses it; `config/` is public so tools can read it. `tools/` never ships.

| File | Owner / connection |
|---|---|
| `radialmenu/RadialMenuModPlugin.java` | Config load and listener registration |
| `radialmenu/config/Setting.java` | Setting ids, types, defaults, bounds and sanitizing; `HOTKEYS` lists the keys that open the wheel |
| `radialmenu/config/PlayerSettings.java` | LunaLib listener and the cached values |
| `radialmenu/config/RadialMenuConfig.java` | Parses and validates `radial_menu.json`, the default main wheel |
| `radialmenu/layout/RadialLayout.java` | The slot layout: load, defaults, edits and saving to common data; `MAIN_SLOTS`, `SUBMENU_SLOTS`, `MOD_ICON` |
| `radialmenu/layout/Slot.java` | Immutable slot: empty, an ability id, or a submenu (icon, optional hue, children) |
| `radialmenu/layout/SlotPicker.java` | The edit dialog for one slot |
| `radialmenu/wheel/CampaignRadialMenu.java` | The listener: building the main and submenu rings, input capture, submenu extension and edit hold, gesture to action, all drawing, per-session icon caches |
| `radialmenu/wheel/RadialSelection.java` | Gesture state (held, open, centre, start point, extended submenu), sector and child angles and hit testing (`target`); fixed `INNER_RADIUS`, `OUTER_RADIUS`, `SELECTION_LIFT`, `CHILD_INNER_RADIUS`, `CHILD_OUTER_RADIUS` in virtual UI pixels and `CHILD_WIDTH` |
| `radialmenu/wheel/WheelRing.java` | One ring as shown: entries, angles, radii, its cached base, lifted and (submenu only) collapsed meshes, and lift faders |
| `radialmenu/wheel/WheelEntry.java` | One tile as shown (ability, submenu or empty), built from a `Slot` |
| `radialmenu/wheel/RadialAbility.java` | One assigned ability (id, title); availability, active state and the single press path |
| `radialmenu/render/FeatheredSector.java` | Cached quad meshes (x, y, coverage per vertex) for one closed tile: fill with feathered edges, vanilla's double border (`edge`, `innerEdge`) and glow |
| `radialmenu/render/SectorText.java` | Usable label width inside a sector and word wrapping |
| `radialmenu/render/IconHue.java` | Circular mean hue of an icon's visible, saturated pixels, and the tile colour for a hue (`tileColor`) |
| `radialmenu/render/IconFade.java` | Static mesh fading an icon's edges |
| `radialmenu/render/IconBackdrop.java` | 2x bicubic copy of an icon with radial opacity falloff, and its GL texture upload |
| `radialmenu/render/AbilityTooltip.java` | The hovered ability's own tooltip in vanilla's tooltip frame, beside the wheel |
| `radialmenu/tools/` | Release builder and headless checks; see [Build and release tools](#build-and-release-tools) |

## Contracts

### Engine and input

- `CampaignRadialMenu` is the only campaign listener. It is registered transient on every game load, so it writes nothing into saves; `removeListenerOfClass` first keeps one instance per session. `addListener(Object, boolean isTransient)` in `ListenerManagerAPI`.
- Hotkeys (`Setting.HOTKEYS`, by default `KEY_B`, `KEY_LCONTROL`, `KEY_RCONTROL`) are read with `Keyboard.isKeyDown` in `openingKeyHeld`, not from input events. A cleared key (`KEY_NONE`) is skipped. `Setting.MOUSE_BUTTON` adds LWJGL button `choice + 1` (2 middle, 3 and 4 the side buttons), polled with `Mouse.isButtonDown`, which is false for a button the mouse lacks. Left and right are not offered: the left button is the edit hold and the right moves the fleet. With all three keys cleared and no mouse button the wheel never opens. Escape is read from unconsumed key-down events and consumed.
- While the wheel is open, `processCampaignInputPreCore` consumes every unconsumed mouse and keyboard event and calls `setDisallowPlayerInteractionsForOneFrame`; `processCampaignInputPreFleetControl` does the same. Listener input priority is 100.
- `allowed()` requires `GameState.CAMPAIGN`, an active game window, a player fleet, and no dialog, menu, core tab or hidden UI. The render callback cancels an open wheel when it stops being allowed.
- The wheel does not pause campaign time.

### Gesture

- A ring's entries are captured when it is built (`openWheel` for the main ring, `extend` for a submenu) and cannot change while it is shown. Availability is checked again on release, through the current fleet's `getAbility`. Only ability entries act on release; Submenu and Empty do nothing.
- The centre hole is `INNER_RADIUS` (56 px); the pointer inside it cancels a release and closes an open submenu. A release closer than `INNER_RADIUS` to the press point also selects nothing, so edge clamping cannot turn a stationary tap into a selection.
- Sector 0 points up; the rest follow clockwise. Boundaries are half-open, so a point belongs to exactly one sector. Submenu tiles are `CHILD_WIDTH` (30 degrees) wide, centered on their parent sector, clockwise.
- `updateExtension` opens a submenu as soon as `target` lands on its main tile. While one is open, the pointer inside `INNER_RADIUS` closes it, the main band (`INNER_RADIUS` to `OUTER_RADIUS`) closes it on any other main tile (opening that one if it is a submenu), and anything farther out keeps it open, so a diagonal move toward a far child tile cannot cross a neighbour's tile and close it. Past `OUTER_RADIUS`, `target` returns a child by angle or nothing outside the arc.
- With `Setting.SELECT_BY_DIRECTION` on (`RadialSelection.setDirectional`, set when the wheel opens), `target` has no outer limit: past `INNER_RADIUS` it returns the tile in the pointer's direction at any distance. Past `OUTER_RADIUS` with a submenu open, an angle outside the submenu's arc returns the main tile there, and `updateExtension` switches the submenu to it, so the pointer never has to come back inside the ring. Only the centre hole and Escape cancel.
- The submenu ring starts at `CHILD_INNER_RADIUS`, past a lifted main tile. When any shown main slot is a submenu, the wheel is clamped on screen by `CHILD_OUTER_RADIUS + SELECTION_LIFT` instead of the main ring's lifted radius.
- `trackPointer` runs each input frame while the wheel is open, with a frame time from `System.nanoTime()` clamped to 0.1 s. Holding the left mouse button on any tile for `EDIT_HOLD` (0.5 s) cancels the gesture and opens `SlotPicker`; on a child tile it edits that submenu slot. Mouse events are consumed while the wheel is open, so the button is polled with `Mouse.isButtonDown(0)`.
- Sounds follow vanilla's ability bar, whose slot is a standard button: `ui_button_mouseover` whenever the pointer reaches a new tile, disabled or not; on release `ui_button_pressed` after a successful press, unless `AbilityPlugin.hasCustomButtonPressSounds()` (the ability then plays its own on-sound, as `BaseToggleAbility` and `BaseDurationAbility` do), and `ui_button_disabled_pressed` for an unusable or empty tile. A release over Submenu, in the centre or outside the wheel is silent. Completing an edit hold plays `ui_button_pressed`. Sources: `sounds.json`, `AbilityPlugin.hasCustomButtonPressSounds` (0.98a-RC8); the ability bar slot's button setup and the standard button's mouse-enter and press handling, translated from decompiled code.
- After an edit the gesture stays held, so the wheel reopens only on a fresh hotkey press.
- With `Setting.RETURN_MOUSE` on, `openWheel` saves `Mouse.getX/getY` (LWJGL window pixels, not UI coordinates) and the first input frame with the wheel closed puts the cursor back with `Mouse.setCursorPosition`, after any release has acted. That covers release, Escape, an edit hold (before `SlotPicker` takes input) and a close from the render pass, which is picked up on the next input frame. Vanilla moves the cursor the same way, in pixels (`NeuralLinkScript`, `getScreenWidthPixels`).
- With `Setting.PAUSE` on, the input frame that opens the wheel saves `SectorAPI.isPaused()` and pauses; `afterClose` restores the saved state, after any release has acted, so an ability is pressed while paused, as the vanilla ability bar allows. When the wheel closed into a dialog (an edit hold opening `SlotPicker`), the pause is left to the dialog, which vanilla unpauses when it is dismissed. Input and render callbacks keep running while paused, and the wheel's animations, edit hold and tooltip delay use `System.nanoTime()`, so they are unaffected.

### Rendering

- Coordinates are virtual UI pixels. `FeatheredSector` meshes are rebuilt only when `getScreenScaleMult()` changes (`WheelRing.updateGeometry`).
- Tile background is black at `BACKGROUND_SHADE` (0.3) under `Misc.getDarkPlayerColor()` at `BACKGROUND_TINT` (0.65) of its own alpha, about 61% opaque together. The icon backdrops are drawn on top at full strength and do not change with it. That colour is vanilla's dark UI colour, `darkUIColor` in `player.faction` and `buttonBgDark` in `settings.json`, (31,94,112,175). The hovered available tile, and an open submenu's parent, light up with vanilla's button hover: an additive wash of the tile's colour moving toward white, at `glow * 0.5 * min(1, (175 + 50) / 255)`, plus the icon drawn again additively at `glow * 0.33`. The tile colour (`IconHue.tileColor`) is the icon hue at saturation 0.55 and full brightness (`Misc.getBasePlayerColor()` without one); the active pulse and the cooldown fill use it too. With `Setting.TINTED_TILES` on (read when the wheel opens), an available tile with a hue draws its background in `restTint` instead: the dark UI colour moved `REST_TINT` (0.7) of the way toward the colour with its saturation and brightness in the tile's hue, cached per entry as `WheelEntry.tint`. Empty and unavailable tiles keep the plain background, and the hover wash goes on top unchanged, so the hovered tile stays the brightest. Each tile has a glow `FaderUtil` with vanilla's button timing (`GLOW_IN` 0.05 s, `GLOW_OUT` 0.25 s). Sources: `settings.json` and `player.faction` (0.98a-RC8); the text-button renderer and the ability icon widget, translated from decompiled code.
- Both rings go through `drawRing`. The open submenu's parent stays lit and lifted while the pointer is on its children. A submenu extends with a `FaderUtil` (`EXTEND_IN` 0.1 s, `EXTEND_OUT` 0.08 s, smoothstep) that grows its tiles out of `CHILD_INNER_RADIUS` and fades them in; a closing submenu keeps drawing until it has faded, but cannot be selected.
- `renderInUICoordsAboveUIBelowTooltips` pushes `GL_ALL_ATTRIB_BITS` and pops it in `finally`. `drawMesh` reasserts blending for every mesh because stock labels change GL state.
- All tile backgrounds are drawn before any icon or label, and all outlines after them, so neighbouring tiles cannot cover each other.
- Every tile is closed. Its straight sides run parallel to its boundary rays, `RadialSelection.TILE_GAP / 2` (3 px) inside them, so the gap between neighbours keeps one width at every radius; arcs stop where they meet the sides (`asin(side / radius)`). No border is shared, and a lifted tile needs no extra sides.
- Borders follow vanilla's ability slot: a 1-unit line in `Misc.getBasePlayerColor()` with 3-unit chamfered corners, and a second line 1 unit inside it with 2-unit chamfers. Vanilla's renderer passes full and half alpha to a textured line; on screen that reads as 70% and 35% of the base colour (measured from a 0.98a ability bar screenshot: (121,155,178) and (59,77,88) over black), so `EDGE_ALPHA` and `INNER_EDGE_ALPHA` use those. `stroke` keeps each line's antialiasing ramp inside its width, so a line's total coverage equals its width and the two lines stay one pixel each instead of blurring into one band. A disabled tile keeps its border; its icon dims and its label greys instead. `FeatheredSector.corners` builds each line's closed path and `stroke` turns it into a strip feathered by one physical pixel. Source: the ability bar slot's render and its border helper, translated from decompiled code (0.98a-RC8). Hit testing ignores the gaps: a point in a gap belongs to the sector its angle falls in.
- The cooldown fill is drawn after the glows and before the icons, only while `AbilityPlugin.showCooldownIndicator()` is true. It stays inside the tile's borders (`BORDER`, 2 units, from every edge, and the tile's straight sides) and covers the space between them from the outer edge down to `getCooldownFraction()` of its depth, where the javadoc defines 0 as the start of the cooldown and 1 as its end; `BaseAbilityPlugin` returns 1 and shows the indicator only below 1. It is drawn in immediate mode each frame (`drawBand`) because its radius changes every frame. `drawBand` takes the same side inset as `FeatheredSector`: each radius stops `asin(side / r)` inside the boundary rays, so a band's ends lie on straight sides parallel to them. Abilities that recharge while usable, such as Catch.Release's charged abilities, show their recharge the same way. Sources: `AbilityPlugin` (`showCooldownIndicator`, `getCooldownFraction`), `BaseAbilityPlugin.getCooldownFraction` and `showCooldownIndicator` (0.98a-RC8).
- The hovered available sector lifts with one `FaderUtil` per sector (`LIFT_IN` 0.08 s out, `LIFT_OUT` 0.15 s back, eased with smoothstep) while `Setting.LIFT` is on. With `Setting.NO_ANIMATION` both kinds of fader get zero durations, so `FaderUtil` jumps to its target, and the active glow stays at half its pulse. Settings are read when the wheel opens. `renderTime` clocks the faders with `System.nanoTime()`, clamped to 0.1 s, because the render callback has no frame time and runs while the campaign is paused. `drawMesh` blends the cached collapsed, base and lifted meshes vertex by vertex, which needs `FeatheredSector` to keep its vertex count and order independent of the outer radius. Icons, backdrops and labels move out by half the lift; the cooldown fill and the edit arc use the blended outer radius. Hit testing keeps the full lifted radius, so the edge does not flicker. Faders restart when a ring is built.
- With `Setting.ICONS_ONLY` on (the default), read when the wheel opens, no labels are built: icons sit at the tile's centre instead of 12 units below it, and Empty tiles draw nothing. Otherwise Empty, which has no icon, centres its label in the tile. The edit hold is drawn after the outlines as an arc along the outer edge, between the tile's straight sides, growing clockwise.
- Icons draw at their sprite's native size (vanilla ability icons are 48 units), one texel per unit, with their corner snapped to a physical pixel (`snap`), so linear filtering does not blur them. `SettingsAPI.getSprite(String)` returns a new sprite at native size on every call (decompiled `StarfarerSettings`, 0.98a-RC8), so nothing resizes the wheel's copies. `IconFade.MESH` fades the outer eighth of each side. The backdrop behind an icon does not follow its size: it is 2.5 times `BACKDROP` (44 units), or `NAMED_BACKDROP` (36) with labels.
- `readIcon` reads every shown icon once with `openStream` and `ImageIO` and caches its hue and backdrop image. It runs for a submenu with its own colour too, so its icon still gets a backdrop; only the hue goes unused. An unreadable icon logs a warning and the sector falls back to `Misc.getDarkPlayerColor()` with no backdrop.
- `IconBackdrop.upload` stores the image's top row first, so `drawIconBackdrop` maps texture v 0 to the quad's top edge; UI y points up.
- Backdrop textures are uploaded on first draw, on the render thread, and kept in `backdropTextures` for the listener's lifetime. They are never deleted; a new listener after each game load uploads them again.

### Ability tooltip

- With `Setting.TOOLTIPS` on (the default, read when the wheel opens), an ability tile hovered for `AbilityTooltip.DELAY` (0.3 s, vanilla's default tooltip delay) shows the ability's tooltip. The hover clock runs in `renderInUICoordsAboveUIBelowTooltips` and restarts on a new target or a new wheel; the tooltip is drawn in `renderInUICoordsAboveUIAndTooltips`, above everything else. Abilities the fleet lacks, `hasTooltip() == false`, Submenu and Empty show none.
- It copies vanilla's ability bar tooltip: `ability.createTooltip(tooltip, expanded)` into a `TooltipMakerAPI` of the ability's `getTooltipWidth()`, then "Hold left-click to assign another ability" in `Misc.getGrayColor()`, 10 below (3 when a `BaseAbilityPlugin` has interrupted abilities listed), where vanilla says right-click. It is rebuilt every frame, as vanilla recreates its ability tooltips, so live values stay current. It pops in without a fade, like vanilla's.
- The tooltip is built off the UI tree (`SettingsAPI.createCustom`, `createUIElement`, `addUIElement`) and drawn with `render(1f)` after `setLocation`; components start fully visible, so it needs no `advance`. `createUIElement` takes 10 off its width and turns the border and background off, so the width passed is `getTooltipWidth() + 10` and `AbilityTooltip.frame` draws vanilla's frame: black at 0.85 alpha, 7 units (`PAD`) around the content, and the `ui_border1b_w`/`_e` side sprites (16x8, `_top` and `_bot` caps, the middle piece tiled with `renderRegion`) at full alpha.
- An expandable tooltip (`isTooltipExpandable()`) gets vanilla's footer box under the frame: `8 + line height` high, `max(200, label width + 30)` wide, with "Press F1 for more info" or "Press F1 to hide" in `Fonts.ORBITRON_12`, `Misc.getGrayColor()`, and F1 in `buttonShortcut`. F1 toggles `expanded` for the shown tooltip. The wheel consumes all keys, so `processCampaignInputPreCore` reads F1 itself; a player who rebinds vanilla's expand key still uses F1 here, because the binding is not in the API.
- It sits `GAP` (10) outside the wheel's outer edge (the submenu ring's while one is shown) on the hovered tile's side, centred on the tile's middle height; it flips side when it does not fit, then is clamped on screen, leaving room for the footer below.

Sources (0.98a-RC8, decompiled): the ability bar slot's `createAbilityTooltip`, `StandardTooltipV2` (`renderImpl`, `updateSize`, `makeExpandable`, `bgAlpha`), the border edge renderer, `StandardTooltipV2Expandable.createAsUIElement` and `updateSizeAsUIElement`, the custom panel's `createUIElement` and `addUIElement`, the base UI component's faders and `setTooltip` (delay), `StarfarerSettings` (0.3 s default); `settings.json` (`ui_border1b_*`, `textGrayColor`, `buttonShortcut`).

### Shared cross-file constraints

- `RadialAbility.press` is the only path that uses an ability. It calls the vanilla `pressButton`, which keeps toggle behaviour, sounds, costs and confirmation dialogs; the mod adds no ability effects.
- `RadialMenuConfig` rejects more than `RadialLayout.MAIN_SLOTS`, blank, non-string and duplicate ids at startup. Assigned ids the fleet does not own are greyed out in their slot; an id with no ability spec shows as Empty but stays saved.

### Layout

- `RadialLayout` keeps `MAIN_SLOTS` (10) main slots, and each submenu `SUBMENU_SLOTS` (10) children. The wheel shows the first `Setting.WHEEL_SLOTS` main slots and the first `Setting.SUBMENU_SLOTS` children, read when a ring is built; hidden slots keep their contents.
- Submenus are one layer deep: `RadialLayout.slots(..., false)` ignores a submenu inside a submenu, and `SlotPicker` offers Submenu only for main slots.
- Saved format: `{"main": [slot, ...]}` where a slot is `{}`, `{"ability": id}` or `{"icon": sprite path, "hue": 0..1 (optional), "slots": [slot, ...]}`. A submenu with no `hue` uses its icon's colour.
- `RadialLayout.set` saves after every edit with `writeJSONToCommon(file, json, true)`, after `load` read it with `readJSONFromCommon(file, true)`, as vanilla's `BaseSharedJSONFile` does. The game stores it as `<saves>/common/radial_menu_layout.json.data` (`StarfarerSettings`, 0.98a-RC8). It can be lost with the common folder; any read error falls back to the defaults and the next edit overwrites the file.
- A submenu icon is a sprite path: an ability's `getIconName()` or `RadialLayout.MOD_ICON`, which `onApplicationLoad` loads with `loadTexture` because no vanilla loader does.

### Slot picker

- `SlotPicker.open` runs inside `processCampaignInputPreCore`: `showInteractionDialog(picker, null)` with the text and visual panels hidden, then `showCustomDialog` from `init`. Vanilla opens dialogs from the same input pass. The campaign is paused while it is open and unpaused when it closes, even if it was paused before (vanilla behaviour for every interaction dialog).
- One object is the dialog plugin, the `CustomDialogDelegate` and the panel plugin. Button presses are queued and handled in `advance`, because they change the panel tree. Each view (ability list, then icon and colour) is a fresh child panel; vanilla keeps per-panel element bookkeeping, so a view is never reused.
- The list offers Empty, Submenu (main slots only), then the fleet's abilities in `AbilitySpecAPI.getSortOrder()` order. A row press saves and closes. The icon and colour view saves on Confirm, keeping an existing submenu's children (`Slot.withLook`). Cancel, Escape and a right click outside save nothing; Confirm on the list view saves nothing either.
- Icon choices are the mod icon plus every ability spec's icon from `getSortedAbilityIds()`; the game queues every ability icon for loading at startup. Each icon or colour is an area checkbox in its own cell panel; `IconCell.render` runs after the checkbox and draws the sprite, or for a colour a filled quad in `IconHue.tileColor`, the colour the wheel uses, both inset `INSET` (4) units. `TooltipMakerAPI.addCustom` shifts a tooltip's first element 5 units right, so `cell` resets the box's `setXAlignOffset` to 0 to keep it under the sprite, as vanilla does for a section heading in `BaseEventIntel`.

Sources (0.98a-RC8): `CampaignUIAPI.showInteractionDialog`, `InteractionDialogAPI.showCustomDialog`, `CustomDialogDelegate`; decompiled `CampaignState.showInteractionDialog` and `dialogDismissed` (pause), `SpecStore` ability loading (icons), `StandardTooltipV2Expandable.addCustom` (first element offset).

## Checks

`ReleaseBuildChecks` covers the release tooling and `PlayerSettingsChecks` the settings CSV. There are no automated checks of the wheel itself; rendering, input and ability behaviour need the in-game smoke test in [README.md](../README.md#verification).

## Dead or dormant

None.
