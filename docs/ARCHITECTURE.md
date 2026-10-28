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
| `mod_info.json` | Mod id `radial_menu`, `modPlugin` `radialmenu.RadialMenuModPlugin`, jar `jars/radialmenu.jar` (packaged from `jars/release/radialmenu.jar`) |
| `data/config/version/version_files.csv` | Registers `radialmenu.version` for Version Checker; [RELEASE.md](RELEASE.md#version-checker) |
| `RadialMenuModPlugin.onApplicationLoad()` | Reads `RadialMenuConfig.PATH` once through `loadJSON(RadialMenuConfig.PATH, "radial_menu")`; any config error is rethrown as `IllegalStateException` and stops the game start |
| `RadialMenuModPlugin.onGameLoad()` | `removeListenerOfClass(CampaignRadialMenu.class)`, then `addListener(new CampaignRadialMenu(config), true)` (transient) |
| `data/config/radial_menu.json` | Ordered `abilities` array of ability ids; `RadialMenuConfig.fromJSON` |

Load order: `onApplicationLoad -> RadialMenuConfig.fromJSON` -> `onGameLoad -> removeListenerOfClass -> addListener`.

One gesture: opening key down `-> CampaignRadialMenu.prepareWheel -> RadialSelection.update` (opens at the clamped mouse position) `-> renderInUICoordsAboveUIBelowTooltips` each frame `->` key up `-> RadialSelection.hoveredSector -> allowed() -> RadialAbility.press`.

### Build and release tools

Tools live in `radialmenu/tools/`, run as standalone programs with JDK 17 from the repository root, and never ship in the release jar.

| Entry point | Use / owners |
|---|---|
| `tools/ReleaseBuild.java` | `build <starsector-core>`: compiles `jars/src` into `jars/release/radialmenu.jar`; `package [tag]`: writes the ZIP, notes and stamped version file to `out/release/`. Reads `libs/` and the game jars, never writes to the game. [RELEASE.md](RELEASE.md) |
| `tools/ReleaseBuildChecks.java` | Headless checks of changelog parsing, version stamping and the package file filter |
| `.github/workflows/release.yml` | Runs the checks and a preview package on every push and PR; publishes on a version tag |

## Data identity

| Stable ID | Display name |
|---|---|
| `radial_menu` (mod id) | Campaign Radial Menu |
| `radialmenu` (Java package) | none |
| Ability ids in `radial_menu.json` (`scavenge`, `sustained_burn`, `emergency_burn`, `transponder`, `go_dark` by default) | `AbilityPlugin.getSpec().getName()` |

The mod id is passed as a literal to `loadJSON` in `RadialMenuModPlugin`; change both together.

## Source owners

Runtime classes are in `radialmenu/`, and all but `RadialMenuModPlugin` and `CampaignRadialMenu` are package-private.

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
| `radialmenu/IconBackdrop.java` | 2x bicubic copy of an icon with radial opacity falloff, and its GL texture upload |

## Contracts

### Engine and input

- `CampaignRadialMenu` is the only campaign listener. It is registered transient on every game load, so it writes nothing into saves; `removeListenerOfClass` first keeps one instance per session. `addListener(Object, boolean isTransient)` in `ListenerManagerAPI`.
- Opening keys (`KEY_B`, `KEY_LCONTROL`, `KEY_RCONTROL`) are read with `Keyboard.isKeyDown`, not from input events. Escape is read from unconsumed key-down events and consumed.
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

`ReleaseBuildChecks` covers the release tooling only. There are no automated checks of the mod itself; rendering, input and ability behaviour need the in-game smoke test in [README.md](../README.md#verification).

## Dead or dormant

None.
