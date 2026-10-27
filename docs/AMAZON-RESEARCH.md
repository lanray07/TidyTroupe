# Amazon submission research

Checked 27 October 2026 against official documentation. Recheck the developer console immediately before submission; no account-level country selection was inspected.

## Listing and media

Amazon's details page specifies short description up to 2,000 bytes, long description up to 4,000 characters, three to five feature bullets and an optional comma-separated keywords field. It asks for a brief display title but does not state a numeric title limit on the page; do not confuse the separate version-name limit with a title limit. Proposed title is 42 characters. Console acceptance is pending.

Tablet media: two transparent PNG icons (114 × 114 and 512 × 512); three to ten screenshots in PNG/JPEG, portrait or landscape. Permitted landscape dimension pairs are 800 × 480, 1024 × 600, 1280 × 720, 1280 × 800, 1920 × 1080, 1920 × 1200 and 2560 × 1600. Optional promotional image is 1024 × 500; keep text 50px from edges and omit prices/ratings/buttons. Tablet screenshots should show the app itself. Eight planned scenes fit the count. [Appstore details and image guidelines](https://developer.amazon.com/docs/app-submission/appstore-details.html).

## Language and distribution

The official international-distribution page lists store localisations including English US/UK/Australia/India, German, Spanish/Spanish Mexico, French, Italian, Japanese, Dutch Netherlands, Polish Poland, Portuguese Brazil/Portugal and Swedish Sweden. It distinguishes binary languages, listing language and availability; unsupported-marketplace descriptions fall back to English. Requested fr-CA and es-419 must not silently be treated as native console entries. Arabic, Hindi, Turkish and Indonesian app-language support does not establish a matching listing language. See the separate matrices in `store/locales.json`. [International distribution](https://www.developer.amazon.com/docs/app-submission/distribute-your-app-internationally.html).

Appstore Android mobile distribution ended 20 August 2025 while Fire tablet support continues. [Amazon policy announcement](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program).

## Purchase integration

Query product data for localised prices. Implement SDK listener callbacks, current-user refresh, paginated purchase updates and receipt deduplication. Restore requests full history; verify receipt and cancellation status through a trusted RVS backend before fulfilment. A cached boolean alone is not receipt verification. [Official IAP implementation](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html), [IAP overview](https://developer.amazon.com/docs/in-app-purchasing/iap-overview.html).

## Privacy review sources

The ICO children’s code calls for high privacy defaults and data minimisation, including per-user consideration on shared devices. Assess applicability rather than treating offline storage as blanket legal exemption. [ICO default settings](https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/childrens-information/childrens-code-guidance-and-resources/age-appropriate-design-a-code-of-practice-for-online-services/7-default-settings/).

COPPA's treatment of child voice recordings requires reviewing the actual collection/processing path and any narrow enforcement policy; a device PIN does not itself prove parental consent. [FTC COPPA FAQ](https://www.ftc.gov/business-guidance/resources/complying-coppa-frequently-asked-questions).

## Tooling references

Translation tooling uses DeepL's server-side text translation API with XML tag protection, environment-supplied key and optional provider glossary. It has not been exercised against a paid account. Provider language availability and regional fidelity must be reviewed, especially Hindi and requested regional variants. [DeepL translation API](https://developers.deepl.com/api-reference/translate/request-translation).

Android resource generation escapes apostrophes/quotes and validates quantities and format placeholders. Language-specific plural categories still require review. [Android string resources](https://developer.android.com/guide/topics/resources/string-resource).
