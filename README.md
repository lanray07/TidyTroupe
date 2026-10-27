# TidyTroupe

Native, offline-first family chore planner for Fire OS and Android. Kotlin, Jetpack Compose, Material 3, Room, DataStore, Flow and WorkManager. The product name is provisional.

**Status: development build, not ready for Appstore submission.** Source and debug builds are real. Live purchasing, Fire-device testing, reviewed worldwide translations and real store screenshot captures remain release gates. See [verification](docs/VERIFICATION.md) and [release checklist](docs/RELEASE.md).

## Run

Install JDK 17 and Android SDK platform 35/build tools. Set `ANDROID_HOME` or your own ignored `local.properties` with `sdk.dir`. From PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Create a household, administrator nickname and a private 6–12 digit PIN. New installations start empty; there is no fabricated household activity. Add profiles in Family, then assign chores. The parent session expires after five minutes and locks when backgrounded. Enter child view from Family after unlocking. Children can report only their assigned tasks; parent approval awards stars where required.

## Implemented

- Adaptive tablet navigation, phone layout, light/dark/system appearance, original icon, illustrated onboarding and code-drawn avatars. No advertising, analytics, required account or Google Play Services.
- Household/profile management with nicknames, adult/child roles and protected mutations. Three free profiles; more require verified Amazon entitlement.
- Chore creation/editing/duplication/deletion, assignment, notes, effort, stars, pause/archive/restore, dates and recurrence. Daily/weekly/monthly intervals, selected weekdays and custom day intervals use the tested calendar engine.
- Dated completion history, idempotent completion, child approval, undo and transaction-protected star redemption. Spent stars cannot be undone into a negative balance. Private progress and weekly summaries use stored events.
- Ordered reusable routines, duplication, editing and morning/evening/weekend ideas that create real editable chores only when chosen.
- Parent-defined rewards, child reward progress and a private five-completion badge. No public ranking, penalties or random paid rewards.
- Push-to-talk adult speech with explicit opt-in, capability/permission/error handling, visible transcription, structured English commands, mutation confirmation, text alternatives and TTS. Child microphone input is disabled; optional spoken instructions are separately parent-enabled. Speech engines may require a network.
- Optional inexact daily and per-chore notifications, quiet hours, calendar-based next-occurrence scheduling and timezone recovery. Delivery needs device validation.
- Validated JSON import/export, household/profile deletion and portable password-encrypted AES-GCM backups. Exports exclude PIN and purchase keys. Encrypted backup passwords are separate from the parent PIN and cannot be recovered.
- Real Amazon SDK adapter and production receipt-verifier source. Prices come from Amazon; grants require signed verification. Restore, refund/account changes and a seven-day encrypted offline entitlement cache are implemented but not tested against a live store.
- Executable translation pipeline, terminology/review workflow, separate locale matrices, store-copy drafts, capture tooling/manifest and 12-page static marketing site. Missing translations remain pending.

## Code map

- `core/`: pure Kotlin calendar, ledger and structured voice parser with JVM tests.
- `app/.../data/`: Room persistence, transactions, parent authorization, DataStore and backup encryption.
- `app/.../billing/`: Amazon integration and signed entitlement cache.
- `app/.../MainActivity.kt`, `Editors.kt`, `TidyViewModel.kt`: Compose screens, form/dialog components and MVVM state.
- `app/.../Reminders.kt`: WorkManager scheduling and time-change receiver.
- `server/`: trusted Amazon RVS verifier; server secrets never enter the APK.
- `assets/`: original editable identity/illustration assets and provenance manifest.
- `tools/`: localisation, demo-data generation and real-device screenshot capture.
- `website/dist/`: local static site; intentionally noindex until a production domain and reviewed copy are configured.

The app currently has two Gradle modules and feature packages. The finer-grained feature-module split requested in the brief, advanced routine reminders/snooze, full configurable reward enablement/thresholds/accessory unlocking, and non-English voice dictionaries remain unfinished. Extra avatar artwork is delivered as assets; the native picker currently uses four base avatar colours. Core calendar dates follow the device timezone. There is no cloud sync or child microphone mode.

## Verify

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
python -m unittest discover -s tools -p test_localization.py -v
python -m unittest discover -s server -p test_receipt_verifier.py -v
python tools/localization.py extract
python tools/localization.py validate
```

Localisation validation deliberately fails until missing translations and review evidence are supplied. Android instrumentation tests reset their installation: run them only on a disposable emulator/test device. The UI flow additionally requires `-Pandroid.testInstrumentationRunnerArguments.tidyDestructiveUi=true`.

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.tidyDestructiveUi=true
```

Read [billing setup](docs/BILLING.md), [localisation workflow](tools/localisation/README.md), [privacy review draft](docs/PRIVACY-DRAFT.md), [support](docs/SUPPORT.md) and [website instructions](website/README.md). No production backend/site deployment or store submission has been made.
