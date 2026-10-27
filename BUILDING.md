# Building Campaign Radial Menu

Requires **JDK 17+** and the Starsector 0.98a core JARs. The included wrapper downloads Gradle on first use.

```sh
bash ./gradlew clean build -PcorePath=/path/to/Starsector/starsector-core
```

On Windows, use `gradlew.bat` and native paths. Set `STARSECTOR_CORE` or pass `-PcorePath=...`; otherwise, the build looks for `../../starsector-core`. Set `STARSECTOR_MODS` or pass `-PmodsPath=...` if the mods directory is not beside the core directory.

Main sources target Java 8; test sources target Java 17. No PatchLib or seed JAR is required.

Outputs:

- `build/package/RadialMenu/`: complete staged mod.
- `build/distributions/RadialMenu-0.1.0.zip`: installable archive.
- `jars/RadialMenu.jar`: local runtime JAR, copied from the staged package after building this checkout.

`verifyMod` runs the main-method gesture regression tests. `build` also runs these tests and packages the mod. To refresh this source folder's runtime JAR after a build:

```sh
cp build/package/RadialMenu/jars/RadialMenu.jar jars/RadialMenu.jar
```

`build` does not change a live game installation. To install into a separate game directory, close the game and run:

```sh
bash ./gradlew installMod -PcorePath=/path/to/Starsector/starsector-core -PmodsPath=/path/to/Starsector/mods
```

`installMod` overlays the ZIP without deleting unrelated files and refuses to overwrite this source folder. When replacing a previous installation, remove the old **RadialMenu** directory first so obsolete resources cannot remain. Do not delete a separate Voidborne Ascendancy installation needed by existing saves.

## Rolling GitHub release

Every push to `main` builds and tests the mod, then updates the `dev-latest` prerelease with the installable ZIP. The workflow can also be started manually. It skips publication if its commit is no longer the head of `main`.

The workflow requires a trusted **Linux self-hosted runner** with the `starsector` label, JDK 17 or later, Bash, Git, and the GitHub CLI (`gh`) installed. Install Starsector on that runner and set the repository Actions variable `STARSECTOR_CORE` to the absolute path of its `starsector-core` directory. Game JARs stay on the runner and are not packaged in the mod ZIP.

A public repository's self-hosted runner must not execute untrusted pull-request code. This workflow runs only for pushes to `main` and manual dispatches; restrict who can push to `main` and who can administer the runner. The runner needs network access to GitHub and the Gradle distribution.
