# KeyCraft Sinhala Keyboard

KeyCraft is an open-source Sinhala keyboard for Android. It supports Sinhala typing with transliteration, configurable keyboard layouts, dictionary-based suggestions, themes, haptic feedback, and optional dictionary downloads.

## Features

- **Sinhala keyboard:** Type Sinhala using the app's Sinhala input support.
- **Keyboard layouts:** Choose and customize available layouts.
- **Dictionary support:** Use dictionary-backed word suggestions.
- **Themes:** Customize the keyboard's appearance.
- **Haptic feedback:** Enable or adjust vibration feedback while typing.
- **Optional dictionary downloads:** Download additional dictionaries when wanted.

## Typing Sinhala

Select the Sinhala layout in Settings for direct letter input. Shift selects vowel signs and additional letters. Use the number key for digits and symbols. On the Sinhala layout, **EN** switches to Latin keys and **සි** restores the Sinhala layer. On a Latin layout, the **සි / EN** key switches Singlish suggestions on or off and remembers the choice. By default Latin spelling stays in the editor until you tap a candidate. Space keeps the literal spelling, including English and abbreviations. Turn off **Keep Singlish until a suggestion is selected** to use the older live Sinhala preview and space-to-commit behavior.

Enter consonants before dependent vowels, including visually leading signs such as ෙ. For example ක followed by ෙ produces කෙ. A second vowel sign replaces the first. Type a virama followed by another consonant for ක්ක or ත්ත. The layout offers explicit joiner keys for joined forms. Backspace removes a final sign first, then unwinds the last consonant of a cluster and its connector. Clipboard paste inserts the original text.

The candidate strip shows up to three words. For example, `niwaradhi` suggests නිවරදි, and `aayuboovan` suggests ආයුබෝවන්. Spellings can be ambiguous. The bundled Sinhala dictionary at `assets/dict/si.dict` is a compiled Cdict trie. Its generator combines Sinhala words with integer frequency weights. The next-word engine uses one- and two-word contexts and locally learned transitions.

**Personal Sinhala dictionary** in Settings lists saved words. Tap **Add word** to save one or tap an entry to remove it. The keyboard counts accepted Sinhala words and direct entries completed with Space, up to 256 words. The app stores these counts locally, outside Android backup. Sensitive fields skip learning. Swipe gestures and double-space punctuation have separate switches. Double-space punctuation is off by default.

## Requirements

To build the Android app, install:

- [Android Studio](https://developer.android.com/studio)
- **JDK 17**
- **Android SDK** with Android platform 36 and the corresponding build tools

Android Studio can install and manage the required JDK and Android SDK components. If Gradle cannot locate your SDK, configure the SDK location in Android Studio or set `ANDROID_HOME` / `ANDROID_SDK_ROOT`.

## Build

Clone the repository:

```sh
git clone https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard.git
cd KeyCraft-Sinhala-Keyboard
```

Build the debug APK:

```sh
./gradlew assembleDebug
```

The APK is written to:

```text
build/outputs/apk/debug/KeyCraft-Sinhala-Keyboard-debug.apk
```

## Tests and limitations

Run `./gradlew testDebugUnitTest` for JVM tests, `python3 scripts/run_sinhala_python_tests.py` for layout and dictionary generation tests, and `./gradlew connectedDebugAndroidTest` with an Android device for instrumentation tests. App-specific editing depends on the host exposing selection and surrounding text through Android’s input connection. Private fields and editors that omit surrounding text can limit contextual editing. Gesture-only alternatives require swipes to be enabled. Offline predictions depend on the bundled vocabulary and local history.

## Install

1. Download the latest APK from the [GitHub Releases](https://github.com/Lakmal2078/KeyCraft-Sinhala-Keyboard/releases) page, or build it using the instructions above.
2. Open the APK on your Android device and approve installation from that source if Android asks.
3. Open KeyCraft and follow the in-app instructions to enable it as an input method and select it as the current keyboard.

The exact Settings labels can vary by Android version and device manufacturer.

## Privacy and backups

Android Backup and device-to-device transfer include only keyboard configuration settings stored in `com.lakmal.keycraftsinhala_preferences.xml`. Learned Sinhala predictions, downloaded dictionaries, recent emojis, cached files, and in-memory clipboard history are excluded.

## Screenshots

The following screenshots are stored in this repository:

| Keyboard | Settings | Themes |
| --- | --- | --- |
| ![Keyboard screenshot](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png) | ![Settings screenshot](fastlane/metadata/android/en-US/images/phoneScreenshots/2.png) | ![Themes screenshot](fastlane/metadata/android/en-US/images/phoneScreenshots/3.png) |

## Contributing

Bug reports, suggestions, and code contributions are welcome. Please read the [Contributing Guide](CONTRIBUTING.md) before submitting changes.

## License

KeyCraft is distributed under the [GNU General Public License v3.0](LICENSE). See [Third-party notices](docs/THIRD_PARTY_NOTICES.md) for notices and licensing information for bundled third-party components.
