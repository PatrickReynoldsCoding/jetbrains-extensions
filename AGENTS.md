# Agent notes

## Installing the review-marks plugin on this Mac

You can install the plugin yourself. Do not ask Patrick to do it by hand. There is no IDE command for it. The install is to unzip the built plugin into the IntelliJ plugins folder.

1. Build: `cd review-marks && ./gradlew buildPlugin`. The zip is `review-marks/build/distributions/review-marks-<version>.zip`.
2. Check IntelliJ is not running: `pgrep -x idea`. If it runs, tell Patrick to quit it first. Do not kill it.
3. Install into the plugins folder of the IDE version in `review-marks/gradle.properties` (`platformPath` is `/Applications/IntelliJ IDEA.app`; the current version folder is `IntelliJIdea2025.3`):

```bash
P=~/Library/Application\ Support/JetBrains/IntelliJIdea2025.3/plugins
cp -R "$P/review-marks" <scratchpad>/review-marks-previous
rm -rf "$P/review-marks"
unzip -q review-marks/build/distributions/review-marks-<version>.zip -d "$P"
```

4. Check: `cmp "$P/review-marks/lib/review-marks-<version>.jar" review-marks/build/libs/review-marks-<version>.jar` prints nothing.
5. Tell Patrick to start IntelliJ again. The new version loads on start.

Notes:
- The zip has a top-level `review-marks/` folder, so unzip into `plugins/`, not into `plugins/review-marks/`.
- If the IDE is updated to a new version, a new folder appears in `~/Library/Application Support/JetBrains/` (for example `IntelliJIdea2026.1`). Use that folder, and check that the `platformPath` IDE matches.
- To try the plugin without touching the installed copy, run `./gradlew runIde` (it opens a sandbox IDE).
- Run `./gradlew test` before you install.
