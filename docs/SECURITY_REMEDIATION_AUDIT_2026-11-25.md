# KeyCraft v1.4.1 — Security Remediation Audit (2026-11-25)

**Decision: NO-GO for production distribution.** Source hardening builds successfully, but the new APK is unsigned and post-remediation MobSF/dynamic + new APK device testing have not been completed. Do not distribute the unsigned build or reuse an old signed artifact as evidence of the new changes.

## Baseline (MobSF v4.6.9, 2026-11-24)
- App `com.lakmal.keycraftsinhala`, 1.4.1, code 7; SHA-256 `6f78be8033c10320ed906bc73d633731e782bcae67582a0d31ed906337660065`.
- MobSF: **64/100**, Grade A, 1 high/7 medium; minSdk 21; targetSdk 36; v1+v2 signed, v3 absent.
- High: API 21 allows installation on old unpatched Android releases.
- Warnings: backup enabled, protected exported IME, protected AndroidX ProfileInstallReceiver, Janus/v1, random-number use, system trust anchors and related manifest/network findings.
- MobSF code findings: logging, clipboard listener; hardcoded-secret list dominated by accessibility/key translation resource strings.

## Changes actually applied
1. `AndroidManifest.xml`: `android:allowBackup=false`.
2. `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`: exclude entire credential- and device-protected app-data roots from cloud/legacy and device-to-device transfer; note that platform/OEM device-transfer implementations still need device verification.
3. `Logs.java`, `Keyboard2.java`: debug output now requires an app with `FLAG_DEBUGGABLE`; production configuration cannot activate the sensitive `EditorInfo` dump.
4. `Keyboard2.java`: explicit user confirmation required **each time** selection content is sent to external browser translation. URL still contains the selected text after confirmation, so browser history/server exposure must be disclosed.
5. `dict/DictionaryListView.java`: HTTPS required and URLConnection redirect following disabled; SHA-256 pinning, structural validation, bounded decompression and timeouts already existed.
6. `BackupPolicyTest.java`, `ProductionRemediationTest.java`: tests updated to assert deliberate new backup/logging policy. No scanner finding was suppressed.

## Evidence
- `./gradlew lintRelease testReleaseUnitTest assembleRelease`: **BUILD SUCCESSFUL**. Output `/tmp/keycraft-security-build2.log`, project lint report `build/reports/lint-results-release.txt`.
- JUnit XML in `build/test-results/testReleaseUnitTest/`: **279 tests, 0 failures, 0 errors, 0 skipped**, 40 suites.
- Gradle Lint: **0 errors, 249 warnings**, plus **454 baseline-filtered warnings**; existing baseline must be independently triaged; this is NOT a finding-free lint audit.
- Rebuilt *unsigned* artifact: `build/outputs/apk/release/KeyCraft-Sinhala-Keyboard-release-unsigned.apk`; SHA-256 `77d6371dfb7886e02dd10375363054f08bfe062a35ae9d879c61dc772465f194`. `apksigner verify` reports DOES NOT VERIFY (expected for unsigned APK).
- ADB identified one connected Android 15 phone (API 35), with `.Keyboard2` registered as an IME. This is environmental verification only; updated unsigned artifact was NOT installed, and no fresh UI/functional/instrumentation test has been claimed.
- Docker daemon is unavailable, and no running MobSF service was verified; no new MobSF static/dynamic report or score is available.
- Release keystore/signing environment variables not configured in accessible shell; no new signed APK or signature-scheme verification completed.

## Item-by-item disposition
- **minSdk 29 migration: NOT DONE.** Existing minSdk=21 supports Android 5+. Changing to 29 removes Android 5–9 compatibility (API 21–28), including existing users on these versions. Requires installed-base usage statistics, migration communications and acceptance before changing. Android 10 itself is now old; simply increasing minSdk does not guarantee patched devices. Consider Play distribution controls/recommendations and review support policy separately.
- **Janus:** reported because baseline v1 signing is enabled; v2 exists. Disabling v1 would exclude pre-Android-7 devices. Do not strip v1 while minSdk is 21. Evaluate v3 signing and v1 removal if minSdk is raised; release signature lineage/key continuity and `apksigner verify --verbose --min-sdk-version` across supported platforms must be proven before GO.
- **IME exported service:** `android:exported=true` and signature/system permission `android.permission.BIND_INPUT_METHOD` are required for system input-method discovery; do not mark it false. The AndroidX `ProfileInstallReceiver` is library-provided and protected by `android.permission.DUMP`; inspect merged release manifest and platform protection level for residual risk rather than blindly remove it.
- **Random finding `n/i.java`:** code attribution unresolved because report uses obfuscated JADX path. Source search did not find production `java.util.Random`/`Math.random` use. Test code uses deterministic `Random` for malformed input fuzz tests; these are not cryptographic secrets. Correlate finding to JADX class/stack and investigate actual use; require `SecureRandom` for any real secret/token/nonce generation.
- **Clipboard:** history is opt-in, in memory, bounded and expiring; sensitive clipboard metadata is rejected, but not all OTP apps mark content sensitive. Pinned clipboard preferences persist plaintext; backup excluded but data remains on device. Verify incognito/password/OTP clipboard handling and purge behavior in instrumentation, including host-app restrictions.
- **Fields and incognito:** `SuggestionPolicy` handles password variations and IME no-personalized-learning; verify actual keyboard behavior, analytics/network absence and learner persistence on a real device. Generic numeric OTP fields cannot always be distinguished reliably by inputType alone.
- **Networking:** dictionary endpoint `raw.githubusercontent.com`; integrity-pinned downloader hardened. Translation uses browser intent to `translate.google.com`, explicit per-action confirmation. Base network config disallows cleartext and uses normal system CA trust; latter is not an intrinsic vulnerability. Check final APK, redirects, URLs, and runtime traffic by proxy/MobSF dynamic testing.
- **Hardcoded secrets:** reported `a11y_key_*` and `key_descr_*` strings are UI descriptions and not secrets. Two standalone high-entropy hex strings in baseline report still require source/DEX attribution; do not broadly label them false positives without provenance.
- **Feature preservation:** Sinhala Unicode, Singlish transliteration, predictive suggestions, themes and dictionaries remain in source and were covered by existing unit suite; *device-level regression is unverified*.

## Required release gates
1. Review baseline-suppressed Lint warnings and attribute RNG/high-entropy strings.
2. Provide approved minSdk compatibility policy and assess install-base impact.
3. Run `connectedAndroidTest` and manual password/OTP/incognito, clipboard, Sinhala/Singlish suggestions, themes/personal dictionary and translation tests on Android 15 device plus representative older supported devices; capture evidence.
4. Configure legitimate release signing credentials *without exposing them*; build and verify final signed APK, update path, v1/v2/v3 signature schemes and certificate fingerprint.
5. Run MobSF 4.6.9+ static and dynamic scans on exact signed artifact; compare findings and score with report dated 2026-11-24. Target >=90 only if measured, never presumed.
6. Reissue decision only after the outstanding security and compatibility risks are accepted or remediated.
