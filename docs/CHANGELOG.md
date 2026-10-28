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
Adjusted - Labels wrap at 12 instead of 10 characters

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

- Say what changed and what it does. Name the feature so a player can find it.
- Include important numbers and units. For balance changes, old and new values
  are useful: `Adjusted - Wheel radius increased from 180 to 200 pixels`.
- For a fix, describe the broken behavior that is now fixed. Do not explain
  the internal call chain.
- Keep it short and ordinary. No claims about seamless experiences, robust
  frameworks, polished workflows, or how thoroughly the agent audited things.
- An occasional aside or a genuine contributor credit fits the format. Do not
  invent jokes, credits or testing claims.
- No agent/model names, session links, validation logs or commit hashes.
- Use Development for useful changes to authoring tools or the build. Do not
  sell a refactor or documentation edit as a gameplay feature.

Combine entries when several edits fix the same player-visible problem.
Amend an existing unreleased entry if the task changes that same feature again.
A feature that did not exist in the last release gets one `Added -` entry that
describes it as it now stands. Do not add `Changed -`, `Fixed -` or `Adjusted -`
entries for later work on it in the same version; edit its `Added -` entry
instead. Changes to features that shipped in an earlier release still get their
own entries.
Do not repeat a change once per file, implementation step or PR.

## Versions and upkeep

The newest section holds work for the next release and is marked
`- UNRELEASED`. Keep adding to it across tasks. Do not invent a new version
for every commit. Ask which version comes next if it has not been chosen after
a published release.

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
