# Release preparation

Status: development build. This is not an Appstore submission certification. Product name remains provisional pending trademark and listing clearance. No store account, registered SKU, translation credentials, release signing identity or production support contact was supplied.

## Release blockers

- Run the complete workflow on physical Fire 7/HD 8/HD 10 devices in portrait and landscape, offline, with large fonts, TalkBack, dark mode and reduced motion.
- Review every implemented feature against the original brief; a domain model or disabled integration is not a delivered product feature.
- Connect and test Amazon IAP with an entitlement SKU, store prices, purchase callbacks, account changes, restore, refunds/revocations and persistent offline entitlement. Verify receipts with a configured trusted backend before granting premium. No fake successful purchase is acceptable.
- Complete translations for all 19 requested app locales, professional review, RTL checks, locale-aware voice dictionaries and notification strings. English fallback is not a completed translation. Run `python tools/localization.py validate`; nonzero exit blocks the worldwide release.
- Capture real localised app screens for eight scenes per approved listing locale. Phone variants are separate secondary-distribution assets; Amazon tablet assets must show the tablet experience.
- Approve data mapping, retention, lawful bases, COPPA applicability and children's-code assessment. Check any recogniser provider before permitting a child's speech to leave the device. A local PIN is an access gate, not proof of legal parental consent.
- Fill the legal controller identity and parent support contact in the privacy draft. Verify disclosures against the final signed binary, SDKs and Amazon's wrapper.
- Configure a production domain, replace website canonical base and publish only reviewed factual pages. Add the real Amazon listing link after approval.
- Release signing: keep keystore/passwords outside the repository; build and test release artefact, inspect permissions, check shrinker rules and retain symbol/mapping files.

## Manual acceptance script

1. Start with empty app data. Create household, administrator PIN and child nickname; decline optional microphone and notification permissions. Core workflow must continue without account registration.
2. Create a daily chore, assign a child, set points and approval. Child reports completion; points remain unchanged until adult approval. Approve twice and verify points accrue once. Undo and confirm the ledger follows the chosen undo policy without duplication.
3. Test selected weekdays, January 31 monthly recurrence, leap day, timezone travel, DST, midnight and missed reminders. Completing one recurrence must not erase the next occurrence.
4. Kill/relaunch app and reboot device. Verify chores/history/settings persist. Notifications must respect opt-in and quiet hours, and must not resurrect deleted chores.
5. Lock parent session, background/reopen, navigate back and use child voice/text commands. Protected mutations, purchases, deletion and exports must remain inaccessible.
6. Test recogniser absent, no microphone permission, recogniser network failure, TTS locale unavailable and cancellation. Verify push-to-talk ends, raw audio is never saved by this app, and text/touch alternatives work.
7. Export/import on a disposable dataset and inspect schema validation, corruption handling, duplicate IDs, conflicting completion history and point integrity. Delete a profile/household and confirm linked data is removed consistently.
8. Run Amazon App Tester and Live App Testing. Distinguish a debug APK from the signed, tested submission build.

## Evidence and status ownership

Keep actual commands, test reports, build paths, screenshots and device models in `docs/VERIFICATION.md` when available. Do not mark this checklist complete from documentation alone. `store/screenshots/manifest.json` records pending captures. Translation report is regenerated from current source strings and cache.

## Distribution constraint

Amazon discontinued its Appstore for Android mobile devices on 20 August 2025; Fire tablets continue to be supported. General Android phone/tablet compatibility therefore needs a separate distribution path. [Official Amazon announcement](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program), checked 27 October 2026.
