# Release R8 Smoke-Test Checklist

## Build and artifact

- [ ] Run `./gradlew clean testReleaseUnitTest assembleRelease --no-daemon`.
- [ ] Confirm all release unit tests pass; record the test count and report path.
- [ ] Confirm the release APK exists at `build/outputs/apk/release/Unexpected-Keyboard-release.apk`.
- [ ] Record APK size and SHA-256 (`sha256sum`).
- [ ] Verify APK signature with `apksigner verify --verbose --print-certs <apk>`; record signer certificate digest and verified schemes. Do not change signing keys or signing configuration.
- [ ] Inspect the R8 mapping and usage outputs under `build/outputs/mapping/release/`; retain `mapping.txt` for this exact release artifact.

## Install and launch (physical device or emulator)

- [ ] Install the release APK on a clean test device and confirm installation succeeds.
- [ ] Upgrade-install over the currently installed production-signed build, if a compatible test build/device is available; verify app data remains intact.
- [ ] Launch the keyboard app and enable/select the IME through Android Settings.
- [ ] Confirm the keyboard appears in a text field and can type Sinhala and English.
- [ ] Exercise dictionary-backed suggestions, word completion, and candidate selection.
- [ ] Exercise Sinhala learner behavior: accept/reject suggestions, restart the app, and confirm learned words persist.
- [ ] Exercise themes, light/dark mode, key previews, sound/vibration settings, and settings navigation.
- [ ] Exercise special keys and layouts: shift, symbols, enter, backspace, spacebar Slot 0, and language switching.
- [ ] Check representative Singlish conversions, including `pru` / `prushna` and `anda` / `kanda`.
- [ ] Check Gatapilla, `nd` / `ng`, anusvara, and long Sinhala words in dictionary lookup.
- [ ] Inspect `adb logcat` for `FATAL EXCEPTION`, `NoClassDefFoundError`, `ClassNotFoundException`, missing-resource errors, and R8-related reflection/serialization failures.
- [ ] Repeat the critical flows after force-stop and device reboot.

## Regression and release gate

- [ ] Compare behavior against a non-minified/debug build for any missing classes/resources or changed behavior.
- [ ] Confirm no crashes, missing UI assets, broken preferences, or lost learner data.
- [ ] Attach device model, Android version, APK SHA-256, signer digest, test date, and pass/fail notes.
- [ ] Do not publish, push, merge, rotate keys, edit signing configuration, or rewrite Git history as part of this checklist without explicit approval.

## Notes

- A successful Gradle build and APK signature verification do not replace on-device smoke testing.
- Keep the `mapping.txt` file paired with the exact APK for crash de-obfuscation.
