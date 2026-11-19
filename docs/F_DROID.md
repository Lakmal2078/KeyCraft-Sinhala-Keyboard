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
- Website: <https://lakmal2078.github.io/KeyCraft-Sinhala-Keyboard/>
- Source: <https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard>
- Issue tracker: <https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard/issues>
- Prepared app version: `1.1.1` (`versionCode` 3)

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
- The first F-Droid metadata build entry must use the full Git commit hash for
  the release tag, not the tag name. Create/publish `v1.1.1` only after the
  release candidate is approved.

## Metadata template

Copy the following into `fdroiddata/metadata/com.lakmal.keycraftsinhala.yml`
after the `v1.1.1` release commit is finalized. Replace the placeholder with
the full 40-character Git commit SHA; F-Droid build metadata must not use a
branch or tag name in the `commit` field.

```yaml
Categories:
  - System
License: GPL-3.0-only
AuthorName: Lakmal
WebSite: https://lakmal2078.github.io/KeyCraft-Sinhala-Keyboard/
SourceCode: https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard
IssueTracker: https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard/issues
Changelog: https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard/releases
CurrentVersion: 1.1.1
CurrentVersionCode: 3
RepoType: git
Repo: https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard.git
Builds:
  - versionName: 1.1.1
    versionCode: 3
    commit: REPLACE_WITH_FULL_COMMIT_SHA
    gradle:
      - yes
AutoUpdateMode: Version
UpdateCheckMode: Tags ^v1\.
```

F-Droid's metadata reference requires the full source commit for each build and
allows Gradle builds to be declared with `gradle: yes`. The `Tags` update check
is restricted to `v1.*` because the Git history also contains upstream
Unexpected Keyboard tags from a different package/version line.
