# Localisation workflow

Run from the repository root with Python 3.10 or later:

```powershell
python tools/localization.py extract
python tools/localization.py translate --locale de-DE
python tools/localization.py generate --locale de-DE
python tools/localization.py validate
python -m unittest discover -s tools -p test_localization.py -v
```

`extract` reads actual Android source resources; `translate` uses `DEEPL_API_KEY` from the process environment. It never writes the key, logs request bodies or places provider code in the APK. Free-account keys use the API-free host; other keys use the standard host. An optional `DEEPL_GLOSSARY_ID` can point to a pre-created compatible glossary. Register provider credentials outside source control.

Without credentials, missing strings stay pending and no translations are fabricated. Caches are keyed by source hash; changes invalidate the corresponding translation. Tokens use protected XML tags and are validated by multiset, including repeats. Provider errors block the command. Review placeholder order, meaning, tone, truncation, cultural examples and accessibility labels before approving a cache entry with `review: human_approved`.

Generation writes Android BCP-47 resource qualifiers. Machine resources remain unreviewed; generating XML does not make a locale approved. Plurals preserve source quantities and require a reviewer to add language-specific forms (Arabic and Polish especially); locale approval must include these additions and native-speaker review. The pipeline deliberately reports any plurals for review. Current regional provider fallbacks (en-AU → EN-GB, es-419 → ES, fr-CA → FR) require regional editing rather than claims of native regional translation. Some target languages may be unavailable for an account/provider; select a reviewed provider or import reviewed cache text instead.

Store copy, voice dictionaries, screenshots and website text require separate localisation review; translating app XML does not translate those artefacts. `store/locales.json` tracks those separate matrices. `store/screenshots/manifest.json` is the capture plan, not evidence that screenshots exist.

The quality report contains missing names, placeholder errors, human review counts and plural review requirements. Record a completed native-speaker plural review in `reviews/<locale>.json` using `plural_rules_approved: ["resource_name"]`; record regional review using `regional_approved: true`. Keep reviewer identity/date/evidence alongside these fields. Do not mark approvals before editing and reviewing actual resources. Worldwide release remains blocked until all requested locales have complete resources and review evidence. Do not create English duplicate files and call them translated.
