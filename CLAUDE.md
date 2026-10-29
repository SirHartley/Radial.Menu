# Radial.Menu

Radial.Menu (Campaign Radial Menu) is a Starsector mod: hold a key on the campaign map to open a wheel of the fleet's abilities at the mouse, then release over one to use it.

## Required workflow

These instructions apply to all coding agents. App-specific tools and Claude model assignments are scoped below.

Reviews, explanations, audits, and proposals are read-only unless the user requests changes. For implementation, finish the authorized edit, commit, validation, pull request, and merge steps. Use reasonable assumptions for routine details; ask when a missing decision would materially change the scope or behavior. A request to stop or propose only overrides the implementation workflow.

### Read the current documentation

1. Read the current remote `CLAUDE.md` in full at the start of each new task. Within the same task, reuse the version already read unless the instructions change. Read new or changed instruction files before following them.
2. Use the `starsector-knowledge` skill before answering or changing anything related to vanilla Starsector. It contains the official API, decompiled internals, and vanilla data for one exact game version (0.98a-RC8). Do not rely on memory.
3. Use the `patchlib` skill only when the official API cannot reach what is needed, and for any PatchLib patch, hook or redirect.
4. If a skill is incomplete, inspect the read-only archives under `libs`. Extract them to a temporary directory, never into the repository.
   - `starfarer.api.zip` contains the official game API source; `starfarer.api.jar` is the compile jar.
   - `GraphicsLib.zip`, `Lazylib_lunalib.zip`, `MagicLib.zip` and `PatchLib.zip` contain the library mods and their jars.
   - For GraphicsLib, LazyLib, LunaLib, MagicLib and PatchLib, `libs` is the primary source because the Starsector skill covers vanilla only.
5. Select the required guides using [Which guide to read](#which-guide-to-read). Apply every matching row, including for reviews and fixes to existing behavior.

### Which guide to read

After reading this file, use the task to choose the next document. Read the relevant sections and their prerequisites. A task spanning several areas needs every matching route.

| Task | Required reading / order |
|---|---|
| Locate code or data, trace a feature, change an owner or lifecycle | [ARCHITECTURE.md](docs/ARCHITECTURE.md) before tracing code. |
| Change the wheel's drawing, input, labels, icons or geometry | [ARCHITECTURE.md](docs/ARCHITECTURE.md) source owners and contracts. This is UI work; see [Claude model assignments](#claude-model-assignments). |
| Write or revise any player-facing text, including Java strings, `README.md`, `mod_info.json`, config error messages and changelog notes | The `house-style` skill's player-text reference. Changelog notes also follow [CHANGELOG.md](docs/CHANGELOG.md). This project has no text guide of its own. |
| Add or change a LunaLib setting, including hotkeys | [ARCHITECTURE.md](docs/ARCHITECTURE.md#settings) settings contract; run `PlayerSettingsChecks`. Setting names and help text also take the player-facing text route. |
| Patch, hook or redirect vanilla code | The `patchlib` skill, then the target class through `starsector-knowledge`. |
| Change the build, rebuild the release jar, or prepare a release | [RELEASE.md](docs/RELEASE.md) for version numbers, release contents, jar freshness and publishing. |
| Edit a guide or add documentation | [Documentation upkeep](#documentation-upkeep), the owning guide and its inbound links. Documentation-only changes do not need the build gate. |
| Any implementation task | [CHANGELOG.md](docs/CHANGELOG.md): add or amend the task's entries in the current unreleased section before merge. |

### Technical references and conflicts

Resolve a technical conflict between documents, comments and code against the exact game and library source, and record the evidence; do not pick a winner by filename, recency or an authority label.

### Make and record changes

- Use one task branch for the user's message.
- Make one commit per requested change. If one message contains several changes, commit them separately in the requested order.
- Commit messages are as short as possible: all lowercase, at most 8 words, programmer speak (`fix label wrap on small wheels`). One line only. No body, no conventional-commit prefix, no testing notes, agent or model names, co-author trailers, session metadata, links or trailing full stop.
- Update the affected documentation in the same commit, following [Documentation upkeep](#documentation-upkeep). Do this without a separate user request.
- Update `Changelog` in the same commit, using [CHANGELOG.md](docs/CHANGELOG.md). Agents write the change notes; do not leave that work for the user.
- Rebuild and commit `jars/release/radialmenu.jar`, and publish a release, only when the user explicitly asks; follow [RELEASE.md](docs/RELEASE.md). Ordinary changes, including runtime Java, do not rebuild the jar. Never replace it with an IDE artifact.
- Work only in the current task checkout. A live mod installation and every unrelated checkout are read-only unless the user explicitly asks you to change them.
- Do not deploy or synchronize the mod after merge unless the user explicitly requests it.
- Until release, cross-version campaign saves are unsupported. Remove obsolete upgrades, saved payloads and migrations instead of retaining compatibility scaffolding. Preserve normal save/load within the current version, transient-state restoration and live runtime safeguards.
- Keep all Java tools and automated checks under `jars/src/radialmenu/tools`, in the normal source tree. Do not add separate test source trees or duplicate game classes. Tools may read existing game and mod constants and types; do not change runtime classes to support them.
- Prefer listeners and existing callbacks over `EveryFrameScript` polling. Before adding or retaining a polling solution, verify against the exact game source that no existing callback covers the requirement. Document the missing coverage and keep the scan scope and frequency to what the behavior needs.

### Validate and merge

- Every runtime-affecting final branch must pass the full clean Java 17 build described in [Building](#building).
- Build the exact final remote task-branch revision. Earlier builds, partial builds, IDE analysis, and static checks do not satisfy the gate.
- Missing compilers or required dependencies are merge blockers. Documentation-only changes do not require a Java build.
- A change to `data/config/LunaSettings.csv` or `config/Setting` must pass `PlayerSettingsChecks` ([ARCHITECTURE.md](docs/ARCHITECTURE.md#build-and-release-tools)).
- Report static checks separately from in-game testing. A clean compile does not verify drawing, input or feel; say plainly what was not tested in game. The in-game smoke test is in [README.md](README.md#verification).
- Open a pull request and merge it when the work is complete. Do not leave finished work on an unmerged branch.
- Use the connected GitHub app to create, inspect, and merge pull requests. Use `gh` only if the app is unavailable. A broken `gh` login is not a blocker when the app works.
- Fetch current remote `main` before branching and again before merging. Integrate intervening changes, push the exact final commit, and verify the merged remote revision.

### Branch cleanup

GitHub normally deletes a pull request's head branch after merge. Verify that the branch is gone. Remove it manually when it was pushed without a pull request, its pull request was closed without merging, or it predates the repository setting. If deletion returns HTTP 403, report the branch name and leave it in place; do not work around a credential boundary.

After merge, fast-forward the local task branch to the new `main`. The local task branch may keep its assigned name.

## Documentation upkeep

Update documents automatically as part of each relevant change, not by a background job. Before committing, check which entries the diff makes stale and update those entries in that commit.

| Document | Owns | Update when / how |
|---|---|---|
| `AGENTS.md` | Codex discovery | Keep it a short pointer to this file. Do not copy workflow policy into it. |
| `CLAUDE.md` | Task scope, subagent instructions, tools, commits, PRs, builds, comments, document maintenance | Replace changed policies here and update links. Keep provider-specific instructions explicitly scoped. |
| `README.md` | Player-facing mod page: controls, default abilities, configuration, installation and the in-game smoke test | Update when controls, defaults, the config format or install steps change. Player-facing text route applies. |
| `docs/RELEASE.md` | Version numbers, release builds, package contents, the version file and publishing | Keep the builder constants, `.github/workflows/release.yml` and `radialmenu.version` aligned with it. Do not publish tags without a release request. |
| `Changelog` and `docs/CHANGELOG.md` | Shipped change notes and how agents write them | Add or amend unreleased entries with each implementation task; preserve published entries. Keep formatting compatible with release-note extraction. |
| `docs/ARCHITECTURE.md` | Technical routing: owners, registrations, data flow, lifecycle and cross-system constraints | Update the affected route or contract; remove obsolete owners. Use exact paths and symbols. No writing advice, release history, or duplicate policy. |
| `.claude/agents/*.md` | Subagent definitions | Update when the research workflow, sources or `libs` layout change. |

Give each fact one home. Move useful detail to its proper owner and link to it; do not keep a second copy in architecture. After moving a section, repair inbound links and check that no requirement or technical constraint was lost.

For a new or renamed guide, update the required-reading table, this ownership table and relevant architecture and guide links in the same commit. State when to read it and what it does not govern. Keep `AGENTS.md` as the entry pointer, not a duplicate index.

Write documentation in ordinary English. Use direct verbs and concrete nouns; remove slogans, inflated claims, repeated contrasts, and commentary about how important the document is. Keep identifiers exact. Examples demonstrate a technique, not a sentence pattern to repeat everywhere. Preserve the distinction between absolute restrictions and defaults such as "normally" or "prefer."

## Answers and wording

- Keep answers short and succinct: lead with the result, no preamble or recap. Detailed evidence goes in the pull request.
- Programming-related text (commits, PRs, comments, identifiers, technical docs) uses the vocabulary programmers have used for the last 20 years. Prefer the old word: use, fix, add, show; not leverage, address, introduce, surface.
- No em dashes and no hyphen used as a dash. No emojis unless topical.

## Comments and documentation

- Default to no comments. Add one only where it explains what is not apparent from the code: engine quirks, required ordering, lifecycle rules, save compatibility, serialization, reflection, units, invariants, non-obvious math, cross-file coupling.
- Comments are minimal, all lowercase, terse programmer speak, one line where possible (`// labels change gl state, reassert blend per mesh`). Code identifiers keep their exact spelling.
- Add no other comments: no javadoc, section headers, restated code, history, TODOs or commented-out code.
- Fix or remove a comment your change makes stale; leave untouched comments alone.
- Documentation records current architecture, contracts, entry points, and live hazards. It must not become a development diary, pull-request history, or postmortem.
- Update or remove stale documentation in the same commit as the behavior change.

## Java class layout

- Organize members for reading, not alphabetical sorting.
- Default order:
  1. nested enums;
  2. static fields and initializers;
  3. instance fields and initializers;
  4. other nested types;
  5. constructors;
  6. lifecycle and public entry points in execution or usage order;
  7. supporting helpers near their callers.
- Preserve initializer order when moving code could change behavior.
- Keep a cohesive subsystem together when strict declaration grouping would make the class harder to follow.
- Group fields by responsibility. Put one blank line between groups and no blank lines inside one group.
- Leave one blank line after a class, enum, interface, or record opening brace.
- Use one blank line between methods and major sections. Do not stack empty lines.
- No section header comments; split or reorder a class that needs them.

## Codex and ChatGPT app tools

These tool choices apply to Codex and ChatGPT app sessions. They do not select a model or alter Claude's assignments.

### Local and GitHub work

- Use local and pinned GPTs directly through the ChatGPT app. Do not open `chatgpt.com` or use the browser to reach them.
- Use one dedicated local task checkout for clone, fetch, branch, edit, build, commit, integration, and push operations.
- Use local Git for source work and history. Use the connected GitHub app for pull requests, merges, and remote-only fallback when the local filesystem or Git state is unreliable.
- Do not use the browser for repository work.
- If one user message contains several tasks, commit each task separately on the same branch and open one pull request after all of them are complete.
- Compile from a clean output directory in the task checkout. Never compile from or write into a live mod installation.

### Filesystem validation

Before trusting a new or suspect filesystem:

1. Clone current remote `main` into a disposable directory.
2. Confirm its HEAD matches the remote commit.
3. Require a clean `git status`.
4. Run `git fsck --full`.
5. Compare one repository file with its remote blob.
6. Test write, read, and delete in the clone.
7. Create and switch a local branch.
8. Resolve and verify the disposable path before removing it.

If any check fails, keep local PC checkouts read-only and use the GitHub-app-only workflow until the cause is fixed.

## Agent behavior

- Keep the user's selected model. The main agent owns planning, integration, and shipped code. Delegate bounded research only when the user or applicable instructions authorize it and the app supports it. Do not copy Claude-specific model names into Codex calls.
- Treat repository instructions as project requirements and skill guidance as scoped reference material. If a skill blocks work or changes its scope, identify the exact instruction and explain the effect.
- Give brief progress updates and concise final results. Put detailed evidence in the pull request.

### Subagent dispatch template

Use this template for authorized subagent work, including programming support, crawling, audits, questions and research. It applies across models. The main agent fills in the brief and sets the selected model and reasoning effort in the dispatch call or through the agent definition it selects. Include relevant user clarifications explicitly; do not assume the subagent received the conversation.

```text
Task: <specific question or result, and what counts as complete>
Checkout: <absolute path and revision; note relevant uncommitted changes>
Context: <user clarifications, constraints and work already completed>
Access: <allowed sources; permitted writes and paths, if any>
Starting points: <relevant guides, files or symbols, if known>
Return: <desired detail, format and output path, if needed>

1. Read AGENTS.md and CLAUDE.md, then follow the required-reading routes for
   this task. Starting points are navigation help, not the full reading list.
   Follow required skills and linked prerequisites. Read full documents where
   required; otherwise read the relevant sections and their context.
2. Use searches to locate material. Open the surrounding section before using
   a match as evidence. Read definitions, scope notes, exceptions and warnings;
   do not answer from filenames, headings or search snippets alone.
3. Preserve what each passage applies to: game version, vanilla or mod code,
   current implementation, example, deprecated code or external tool. An account
   of an old mistake is not a current instruction to repeat it. Keep qualifiers
   such as "normally", "only", "never" and "not" when summarizing.
4. Use lookup tables to find relevant entries and follow their source links.
   An entry's presence does not prove it is usable in every context. Do not read
   or count an entire lookup table unless the task requires that coverage.
5. For implementation work or behavioral claims, follow the documentation to
   the relevant code, callers, registration and data within the allowed sources.
   Check the actual version and applicable source corrections. Do not invent
   APIs, names or behavior to fill gaps. If access is documentation-only, say
   what the guide describes and leave implementation verification to the parent.
6. Distinguish documented facts, source-verified behavior and your inferences.
   Before claiming a contradiction, cite what both passages actually say and
   check whether they concern the same version and situation. If evidence is
   missing or still conflicts, identify the uncertainty instead of guessing.
7. Return the requested result with file/line references or source URLs for
   material claims, plus checks performed and remaining questions. For searches
   or crawls, state the scope searched; "not found there" does not prove absence
   everywhere. Do not invent findings or expand the task to fill the report.
```

The main agent checks the cited evidence before using a conclusion to change code or documentation. A subagent's report does not replace the parent's integration review or the required validation.

## Claude model assignments

This section applies to Claude sessions, not ChatGPT.

| Work | Model |
|---|---|
| Planning, architecture, sequencing, and all Java changes | Opus 5 in the main session |
| Repository search, call tracing, and reading game sources | Opus 5.5 subagents at medium effort, using the `researcher` agent (`.claude/agents/researcher.md`) |
| UI and UI-adjacent work, including the wheel's rendering, labels, icons, geometry and input | Fable 5 subagents using `model: fable` |

Subagents perform research and scoping, not shipped code. The UI assignment takes precedence whenever the player can see the result.

## Version names

Versions use major.minor.letter, starting at `1.0.a`. The release tag sets both packaged version files. See [RELEASE.md](docs/RELEASE.md#versions); do not bump versions or publish tags as part of ordinary task work.

## Building

### Required compile gate

After the final runtime-affecting commit and before merge:

1. Fetch and build the exact remote task-branch revision.
2. Use a clean, empty output directory.
3. Compile every `.java` file under `jars/src` with Java 17 and the required dependencies below.
4. Require a successful exit with no compile errors.
5. Record the command, branch commit, Java version and result in the pull request. The final reply links the PR and states the result and any untested behavior.
6. Stop before merge if the build or a required dependency is unavailable.

Documentation-only changes are exempt. Changes to runtime data, including `data/config/radial_menu.json`, still require this gate unless the user explicitly grants an exemption.

The project uses Java 17 (`--release 17`).

Required compile dependencies:

- `libs/starfarer.api.jar`;
- `Graphics.jar` from `libs/GraphicsLib.zip`;
- `LazyLib.jar`, `LunaLib.jar` and `Kotlin-Runtime.jar` from `libs/Lazylib_lunalib.zip` (the Kotlin runtime resolves LunaLib's Kotlin metadata, which otherwise prints compiler warnings);
- `MagicLib.jar` from `libs/MagicLib.zip`;
- `PatchLibAPI.jar` from `libs/PatchLib.zip`;
- `lwjgl.jar`, `lwjgl_util.jar`, `json.jar` and `log4j-1.2.9.jar` from the target game's `starsector-core` directory (the `starsector` directory in the Linux build).

Use the game's bundled libraries for compilation. Do not substitute Maven versions or put a second JSON jar on the classpath: Starsector's `JSONException` is checked, while newer JSON versions make it unchecked and can hide build errors.

These dependencies provide `org.lwjgl.input`, `org.lwjgl.opengl`, `org.json`, and `Global.getLogger()`. The mod uses LunaLib for its settings. The other library mods are on the classpath so a change that starts using one compiles under the same gate; add that library to `mod_info.json` `dependencies` in the same commit. `starfarer_obf.jar` is not required; the source compiles against the API jar alone.

`ReleaseBuild build` in [RELEASE.md](docs/RELEASE.md#build) compiles the same sources against the same jars, but it also replaces the release jar, so use it only for a requested rebuild.

Reference command:

```sh
javac --release 17 -encoding UTF-8 -cp "<jars above>" -d <empty-output> $(find jars/src -name '*.java')
```

## Repository navigation

After the required read of this file, follow [Which guide to read](#which-guide-to-read). [ARCHITECTURE.md](docs/ARCHITECTURE.md) is the code and data map, not a substitute for the task-specific guides.
