# Contributing

Thanks for your interest in contributing to KeyCraft Sinhala Keyboard. Bug reports, documentation improvements, tests, and code changes are welcome.

## Development requirements

- Android Studio (recommended)
- JDK 17
- Android SDK with Android platform 36 and the corresponding build tools
- Python 3 for the Sinhala generator test suite

Open the project in Android Studio, or use the Gradle wrapper from a terminal. If the Android SDK is not detected automatically, configure its location in Android Studio or set `ANDROID_HOME` / `ANDROID_SDK_ROOT`.

## Build

From the repository root, run:

```sh
./gradlew assembleDebug
```

The debug APK is generated under:

```text
build/outputs/apk/debug/KeyCraft-Sinhala-Keyboard-debug.apk
```

To install the debug build on a connected device with USB debugging enabled:

```sh
./gradlew installDebug
```

## Tests and static analysis

Run the Sinhala Python generator tests:

```sh
python3 scripts/run_sinhala_python_tests.py
```

Run Android JVM unit tests and Android Lint:

```sh
./gradlew test
./gradlew lintDebug
```

For instrumentation tests, connect a compatible Android device or start an emulator:

```sh
./gradlew connectedDebugAndroidTest
```

When changing keyboard layout definitions, also run:

```sh
./gradlew checkKeyboardLayouts
```

Review generated-file changes before committing. Do not commit unrelated build outputs or local IDE files.

## Dictionary implementation and vendored sources

The Android app uses the pure-Java dictionary reader in the application source tree. **`vendor/cdict` and `vendor/Android.mk` are not required to build or package the current Android app.** They are retained in the repository for reference/history and are not part of the current app build path. An Android NDK installation or native-library build is not required for the current app.

## Pull request guidelines

- Keep each pull request focused and describe the user-visible or maintenance impact.
- Include the commands used to build and test the change, along with their results.
- Add or update tests when changing behavior or generators.
- Include screenshots for user-visible UI changes when practical.
- Do not include signing keys, passwords, personal data, generated build directories, or unrelated files.
- Call out any known limitations or follow-up work in the pull request description.

## Reporting bugs

Use the [GitHub issue tracker](https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard/issues). Include the Android version, device model, steps to reproduce, and relevant logs. Do not include passwords, private message contents, or sensitive clipboard data.

## License

By submitting a contribution, you agree that it may be distributed under the project's [GPL-3.0 license](LICENSE), subject to the applicable rights and notices for third-party components.
