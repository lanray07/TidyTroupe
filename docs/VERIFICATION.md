# Verification — 27 October 2026

Development implementation; not ready for Appstore submission. No physical Fire-device results, app UI screenshots, live purchase results, human-reviewed translations or compliance certification are claimed.

## Passed

- JDK 17, Android SDK 35 and Gradle 8.13 wrapper used to build the native application.
- 17 core JVM tests: recurrence, monthly intervals/clamping, approval, duplicate completion, point balances/redemption and structured voice parsing.
- 4 encrypted-backup JVM tests: portable Unicode roundtrip, fresh randomness, wrong passwords, header/ciphertext tampering, invalid envelope/version/size/password.
- 4 Python translation tests: placeholder multiset/protection and plural source validation.
- 5 Python receipt-verifier tests: production entitlement matching, signed payload, refunds, RVS revocation and safe upstream failure.
- Debug APK, unsigned release APK and instrumentation-test APK assembled.
- Android lint passes with zero errors. Warnings include dependency update suggestions, KTX style suggestions and unused development resources; inspect the full report in `deliverables/reports`.
- Debug APK v2 signature verified with Android `apksigner`. `aapt` confirms application ID `com.tidytroupe`, version `0.1.0`, min API 26, target API 35.
- Original generated onboarding artwork and master icon PNG visually inspected; SVG/XML syntax validated. This is not app-screen visual QA.
- Localisation extraction/validation execute and correctly report `release_ready: false`. English source exists; 19 requested locale/review workflows remain pending.

## Device blocker

`adb devices` returned no connected devices. The installed emulator reported: **Android Emulator hypervisor driver is not installed on this machine.** A software-emulation attempt did not yield an attached, booted device and was stopped. No Windows drivers, BIOS, Hyper-V or virtualization settings were changed.

Repository instrumentation tests, Android encryption tests and the opt-in Compose end-to-end flow compile but have not executed on a device. Launch, rotation/layouts, large text, TalkBack, notifications, recognizer/TTS availability, Android Keystore and Amazon App Tester/LAT remain unverified.

## Packages and source

`python tools/package_build.py` checks passing JVM/lint reports and packages real build outputs. `deliverables/manifest.json` records package hashes and status; `SHA256SUMS.txt` also covers the source archive. The release APK is **unsigned**, so it cannot be installed until signed with a release identity. The debug APK is for development installation. Neither is a certified Appstore submission.

The source archive excludes local SDK paths, caches, signing keys, build intermediates and delivery outputs. Purchasing requires the Amazon product/key and a deployed HTTPS receipt-verifier configuration. No live service/site deployment or submission was made.

## Reproduce

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :core:test --max-workers=2
python -m unittest discover -s tools -p test_localization.py -v
python -m unittest discover -s server -p test_receipt_verifier.py -v
python tools/localization.py extract
python tools/localization.py validate
```

Localisation validation intentionally exits nonzero until actual translations and review evidence are supplied. Device tests reset their installation; use only a disposable test device, with the UI opt-in described in the README.
