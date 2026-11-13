# Writing the changelog

Read this for every implementation task. Update the root `Changelog` in the
same commit as the change; do not leave it for the user to reconstruct from PRs.
This guide covers change notes only. Other player-facing text follows the route
in [CLAUDE.md](../CLAUDE.md#which-guide-to-read).

## Format

Follow [Industrial Evolution's Changelog](https://github.com/SirHartley/Industrial.Evolution/blob/master/Changelog):
fixed Known issues and Notes sections first, then versions, newest first, with
one change per line. Use `Added -`, `Adjusted -`, `Fixed -` or `Removed -`. A
`Note -` directly below an entry can explain a limit or something the player
needs to do.

Group a long update by the affected feature, with General for broad changes. A
small patch with only a few entries does not need category headings.

```text
1.1.a - UNRELEASED

Wheel
Added - Hold Alt to open the wheel at the screen center
Adjusted - Label wrap 10 -> 12 characters

Configuration
Fixed - A trailing comma in radial_menu.json no longer stops the game from starting

1.0.b
Fixed - The wheel no longer opens while a dialog is closing
```

These are format examples, not changes to copy into the actual changelog.
Use plain text, not Markdown bullets, code fences, PR summaries or commit dumps.
Leave a blank line between categories and versions. The version heading is the
version alone on its line, optionally followed by ` - UNRELEASED` or
` - CURRENT PUBLIC VERSION`; no `v` prefix, no date.

## Wording

Entries are player-facing and follow the house-style skill's "Changelog entries"
section, which wins over this guide on wording. In short:

- One change per line, `Added`, `Adjusted`, `Fixed`, `Removed` in that order
  inside a category. Stay under 80 characters; go past 100 only for a new
  feature that needs its numbers.
- Capital letter after the dash, no full stop on a one-clause line, no
  semicolons, no hyphen as a dash inside the text. In-game names keep their
  capitals.
- What the player sees, never the call chain or class names.
- Added: name, colon, what it does (`Added - Icons only setting: hides tile labels`).
- Adjusted: numbers as old `->` new with the unit
  (`Adjusted - Wheel radius 180 -> 200 pixels`), otherwise a direction word.
- Fixed, in the user's forms: `Fixed - The wheel no longer opens while a dialog
  is closing`, `Fixed - Crash when ...`, or a short noun phrase.
- Removed: the thing, then the reason or replacement in parentheses.
- Credits name real people only, never an agent. No agent or model names,
  session links, validation logs or commit hashes, and no hedges about whether
  a fix works; untested status goes in the PR.
- Keep the user's own lines verbatim. Never tidy, shorten or merge them, and
  never imitate their jokes or asides.
- Use Development for useful changes to authoring tools or the build. Do not
  sell a refactor or documentation edit as a gameplay feature.

Combine entries when several edits fix the same player-visible problem.
Amend an existing unreleased entry if the task changes that same feature again.
A feature that did not exist in the last release gets one `Added -` entry that
describes it as it now stands. Do not add `Fixed -`, `Adjusted -` or `Removed -`
entries for later work on it in the same version; edit its `Added -` entry
instead. Changes to features that shipped in an earlier release still get their
own entries.
Do not repeat a change once per file, implementation step or PR.

## Versions and upkeep

The newest section holds work for the next release and is marked
`- UNRELEASED`. Keep adding to it across tasks. Do not invent a new version
for every commit. See [RELEASE.md](RELEASE.md#versions) for the major/minor/letter
scheme and ask which version comes next if it has not been chosen after a
published release.

Every agent implementation task must add or amend its change notes before
merge. Read-only investigations do not add entries. Check the actual diff:
record only completed changes, preserve earlier entries, and don't list plans
or unfinished work as shipped. When a change needs no entry, say why in the PR.

For release, remove `UNRELEASED`, check any compatibility notes, and use exactly
that version as the tag. Never claim save compatibility without evidence. Leave
older release sections unchanged.

## Maintenance

Update this guide when the changelog format or its upkeep rules change.
Workflow and commit rules stay in [CLAUDE.md](../CLAUDE.md#make-and-record-changes).
