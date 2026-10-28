# Releases

Read this when changing the build, preparing a release, or rebuilding the committed
release jar. This covers packaging and version numbers, not gameplay or prose.
For change notes, follow [CHANGELOG.md](CHANGELOG.md). The compile gate for ordinary
work is in [CLAUDE.md](../CLAUDE.md#building).

## Versions

The first release is `1.0.a`. The three parts are:

| Part | Change | Example |
|---|---|---|
| First number | Major release | `1.4.c -> 2.0.a` |
| Second number | Gameplay additions or other changes players will notice | `1.0.c -> 1.1.a` |
| Final letter | Bug fixes, balance changes and small patches | `1.0.a -> 1.0.b` |

Reset the lower parts when a higher one changes. Use lowercase letters and no
`v` prefix. Do not bump a version or publish a tag for every task. Work goes into
the newest, unreleased changelog section until a release is requested.

The tag is the released version. Packaging copies it into `mod_info.json` and
the major/minor/patch fields of `radialmenu.version` inside the ZIP. It does
not edit the working files. The only change committed back to `main` is the
version file update described under [Version Checker](#version-checker).

### Version Checker

`radialmenu.version` (registered in `data/config/version/version_files.csv`) is read by
Version Checker, LunaLib's bundled checker and mod managers such as triOS. It follows
[Industrial Evolution's setup](https://github.com/SirHartley/Industrial.Evolution/blob/master/IndEvo.version):
**main's copy is the online version file**, and it must always name the newest release.

- `masterVersionFile`: the raw main file, `raw.githubusercontent.com/SirHartley/Radial.Menu/main/radialmenu.version`;
- `modVersion`: the newest released version;
- `directDownloadURL`: that release's ZIP, `releases/download/<version>/Radial.Menu-<version>.zip`;
- `changelogURL`: the raw main `Changelog`.

Until the first release it names `1.0.a`, whose download does not exist yet.

Packaging stamps the version numbers and `directDownloadURL` into the ZIP's copy, and
writes the same stamped file to `out/release/radialmenu.version`. After the release
action creates the GitHub release for a tag, it commits that stamped file to `main` as
"update version file to <version>". Main therefore advertises a version only once
its download exists. Do not bump this file by hand for unreleased work. If the commit
step fails, for example because branch protection blocks the action, commit the stamped
file to `main` manually; until then, installed copies are not told about the release.

There is no forum thread yet; add `modThreadId` when one exists.

## Build

Use JDK 17 from the task checkout. The game path is a read-only input; nothing is
written to the installed game or mod.

```text
java jars/src/radialmenu/tools/ReleaseBuild.java build "<starsector-core>"
```

The game path may also point to the installation directory containing
`starsector-core`. In the Linux build, pass the `starsector` directory that holds
`lwjgl.jar`. The tool takes the API jar and library jars from `libs/`, extracts only
the needed jars into a new directory under `out/`, and compiles every Java source
under `jars/src` there. It checks that the game JSON library has the expected checked
exception type, and refuses any JDK but 17.

Rebuild and commit `jars/release/radialmenu.jar` only when the user explicitly
asks, normally before a release. Ordinary changes to runtime Java, the build tool
or `libs/` leave the committed jar stale; they still need the compile gate in
[CLAUDE.md](../CLAUDE.md#building). The jar's manifest records a `Source-SHA256` of
the runtime Java, `ReleaseBuild.java` and the `libs/` archives it uses. Packaging a
tagged release rejects a missing or stale jar. A preview (branch push, pull request or
local run without a version) accepts a stale jar and prints a warning, so it packages
the last rebuilt runtime.

The release jar holds only `radialmenu/` runtime classes; `radialmenu/tools/` never
ships. A local IDE build of `jars/radialmenu.jar` is gitignored and separate, so it
cannot replace the release jar. Rebuilding the release jar uses an empty class
directory, so removed classes cannot survive from an earlier build. A failed compile
leaves the old jar in place.

## Package and publish

```text
java jars/src/radialmenu/tools/ReleaseBuild.java package
java jars/src/radialmenu/tools/ReleaseBuild.java package 1.0.a
```

Without a version, packaging previews the newest changelog section, including
one marked `- UNRELEASED`. With a version, it applies the release checks: an
exact tag such as `1.0.a`, a matching newest changelog section, at least one
change, no duplicate version headings, and no `UNRELEASED` label.

Outputs go to `out/release/`: `Radial.Menu-<version>.zip`, `release-notes.md`, the
stamped version file and `version.txt`. The ZIP contains one `Radial.Menu/` folder,
ready for `mods/`.

- Included: `mod_info.json`, `radialmenu.version`, `Changelog`, `LICENSE`, `NOTICE`,
  runtime data, graphics and sounds, and the release jar as `jars/radialmenu.jar`.
  `LICENSE` and `NOTICE` are required: the GPL and the notice's additional permission
  ship with every copy.
- Excluded: `libs/`, source, tools, `README.md`, guides, agent files, CI and Git files,
  backups, and any path with a dot directory or an `unused`, `unedited`, `tools` or
  `docs` directory.
- The resource allowlist accepts CSV, JSON, shaders, `.ship`, `.wpn`, `.proj`,
  `.system`, `.variant` and `.skin` under `data`; PNG/JPG/JPEG under `graphics`;
  OGG/WAV under `sounds`. Add a format deliberately, in the same commit as the first
  asset that needs it. Literal `graphics/radialmenu/`, `sounds/radialmenu/` and
  `data/radialmenu/` references in packaged data must resolve inside the archive.

Only Git-tracked files are candidates for packaging. Stage new assets and the
release jar before a local preview; commit them before a remote build.

The [Release action](../.github/workflows/release.yml) compiles the builder and runs
`ReleaseBuildChecks`, then packages, on branch pushes, pull requests and manual runs.
These runs only keep a preview artifact for 14 days. A pushed version tag publishes the
checked ZIP as a GitHub release. Its body is that version's Changelog text, in a
plain-text block to retain line breaks and headings. It never generates notes from
commits. CI needs only Java and the repository; it does not download or distribute
game libraries.

Before tagging, finish the changelog, remove `UNRELEASED`, merge the tested
revision, and verify its package check. Push a tag only when the user requests
a release. No deployment or separate download-host upload is part of this
workflow. An existing release is never overwritten; a rerun stops with an
error instead.

## Running a release

Only when the user asks.

1. Confirm the version. Ask if the next version has not been chosen.
2. Finish the newest changelog section and remove `UNRELEASED`.
3. Rebuild the release jar with `build` and commit it with the changelog.
4. Run `package <version>` locally as a preview check.
5. Open a PR, let the Release action's package check pass, merge.
6. Tag the merged commit with exactly the version and push the tag.
7. Verify the GitHub release (ZIP and notes) and that main's `radialmenu.version`
   now names it.

## Maintenance

Update this guide when the builder, the workflow, the package contents or the version
file change, in the same commit. The builder's constants (`MOD`, `FOLDER`, `RELEASES`,
`PLUGIN`, `ARCHIVES`, `REQUIRED`, `ASSETS`) and `release.yml` must match what this guide
says.
