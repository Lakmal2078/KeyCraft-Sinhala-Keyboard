# F-Droid submission preparation

## Source build

The release variant can be built without the maintainer's private signing key.
F-Droid should build the release APK unsigned and apply its own repository
signing key after the source build.

```sh
./gradlew --no-daemon assembleRelease
```

Expected artifact:
`build/outputs/apk/release/KeyCraft-Sinhala-Keyboard-release-unsigned.apk`

The maintainer's signed release workflow remains in `build_release.sh`; it
continues to validate the private signing configuration. Never commit a release
keystore or its credentials.

The standard Android build uses checked-in generated assets. Sinhala dictionary
generation is now offline and reads `srcs/dictionaries/si_LK.dic`; the English
word-list source and both upstream license notices are retained in
`srcs/dictionaries/`.

## App details

- Application ID: `com.lakmal.keycraftsinhala`
- License: `GPL-3.0-only`
- Website: <https://keycraft-sinhala-landing.lakmal-gamage.workers.dev/>
- Source: <https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard>
- Issue tracker: <https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard/issues>
- Prepared app version: `1.3.0` (`versionCode` 5)

## Review notes

- `INTERNET` is used for optional, user-initiated dictionary downloads.
  Sinhala transliteration runs locally; keyboard text is not uploaded.
- The F-Droid repository already lists Sithakuru, another Sinhala keyboard.
  The submission description should clearly explain KeyCraft's distinct
  Singlish transliteration, word suggestions, configurable swipe layouts,
  themes and productivity tools. F-Droid maintainers decide whether the app
  provides sufficient distinct value.
- The package ID was not found in a public F-Droid package search during this
  audit; verify it again with the current `fdroiddata` repository before PR.
- A successful local Gradle build does not guarantee acceptance. Run the
  official `fdroid lint` and `fdroid build` checks and address maintainer review.
- The `v1.3.0` release is prepared alongside `docs/fdroiddata/com.lakmal.keycraftsinhala.yml`.
  The F-Droid build entry below pins a full source commit hash rather than a mutable tag or branch name.

## Metadata template

The following metadata is ready to submit as
`fdroiddata/metadata/com.lakmal.keycraftsinhala.yml`. The maintained draft is
`docs/fdroiddata/com.lakmal.keycraftsinhala.yml`; its `commit` field points to
the full source commit SHA and must remain a full hash, not a branch or tag name.

```yaml
Categories:
  - System
License: GPL-3.0-only
AuthorName: Lakmal
WebSite: https://keycraft-sinhala-landing.lakmal-gamage.workers.dev/
SourceCode: https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard
IssueTracker: https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard/issues
Changelog: https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard/releases
CurrentVersion: 1.3.0
CurrentVersionCode: 5
RepoType: git
Repo: https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard.git
Builds:
  - versionName: 1.3.0
    versionCode: 5
    commit: 5e2674809add9b554a1ad80cb4e0c3d66fbfcc24
    gradle:
      - yes
AutoUpdateMode: Version
UpdateCheckMode: Tags ^v1\.
```

F-Droid's metadata reference requires the full source commit for each build and
allows Gradle builds to be declared with `gradle: yes`. The `Tags` update check
is restricted to `v1.*` because the Git history also contains upstream
Unexpected Keyboard tags from a different package/version line.
