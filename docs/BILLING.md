# Amazon purchasing integration

Status: real SDK adapter and receipt-verifier source implemented; live purchases, Amazon App Tester, LAT, account switching and Fire-device behaviour are **not verified**. Default configuration is intentionally unavailable. No purchase is simulated and the proposed £3.99 test price is never used as a hard-coded store price.

## Android integration

The app uses `com.amazon.device:amazon-appstore-sdk:3.0.9`. Instantiate one application-scoped `BillingManager(context, repository.guard, BillingConfig(...))`, collect its `state`, and call `onResume()` whenever the activity resumes. `refresh()`, `restore()` and `purchase()` enforce the same parent grant used for repository mutations. Automatic `onResume()` sync does not display a purchasing interface; it checks account changes and purchase updates as Amazon requires.

`BillingState` exposes store-provided `price`, verified `premium`, request `busy`, supported `available`, and a status `message`. `BillingConfig` contains only public values: the configured entitlement `sku`, the production HTTPS `verifierUrl` ending `/verify`, and the dedicated verifier RSA public key `verifierPublicKeyPem`. Root app wiring must use this verified state for the actual premium feature gates; the adapter alone does not deliver premium product features.

The current app instantiates this adapter in `TidyViewModel` and calls `onResume()` from `MainActivity`. It reads public configuration from `app/src/main/assets/billing-public.json`; start from `config/billing-public.example.json` and supply real public values. This file is absent by default. The SDK dependency, queries, receiver and network permission are wired. Verified Premium currently unlocks additional profiles; the remaining premium/free feature split is a release task. Never copy the server signing private key or Amazon shared secret into this file.

Required before enabling purchases:

1. Register the app and its one-time **Entitlement** SKU in Amazon Developer Console, configure marketplace prices, and obtain approval.
2. Download the app-specific `AppstoreAuthenticationKey.pem` from the console into `app/src/main/assets/`. This Amazon public key is different from the receipt-verifier signing key.
3. Add the SDK dependency, `INTERNET` permission, package queries for `com.amazon.venezia`/`com.amazon.sdktestclient`, and the Amazon `ResponseReceiver` with its documented notification permission/action.
4. Deploy the verifier behind a production HTTPS reverse proxy with request/body/time/rate limits. Configure the public URL and pin its **RSA public key** in the app. Keep both the Amazon shared secret and verifier signing private key exclusively on that server.
5. Exercise purchase, interrupted purchase, duplicate receipt, restore pagination, refund, revoked entitlement, account switch, absent Appstore, verifier outage and offline expiry on disposable test devices. The production verifier rejects sandbox and beta/LAT receipts; create a separate isolated test verifier/build for those workflows. Never loosen production validation to accept test receipts.

## Receipt verification and offline policy

The adapter sends only the app-specific Amazon account ID, receipt ID and a random nonce to the configured verifier. It never includes nicknames, child profiles, household chores or audio. A successful SDK callback alone cannot grant Premium. The verifier uses Amazon RVS production HTTPS, verifies exact SKU, receipt identifier, `ENTITLED` product type, purchase date, `testTransaction=false`, `betaProduct=false` and `cancelDate` before signing an entitlement payload. RVS HTTP 410 signs a revoked entitlement. Transient/invalid RVS results fail closed without inventing a grant.

The client validates an RSA-SHA256 signature with a pinned public key, request nonce, user/receipt/SKU binding and a signed expiry. It acknowledges fulfillment only after a verified active entitlement. Canceled SDK receipts immediately revoke the local grant; a complete restore with no matching product clears it. Account changes clear the previous grant before loading purchases for the new account.

Verified offline access lasts up to **seven days**, then requires a fresh verification. The signed envelope is additionally encrypted/authenticated using an app-specific Android Keystore AES-GCM key. The cache is excluded from household export and the app disables automatic Android cloud backup. An offline device cannot discover an account change or a new refund until store connectivity returns; expiry limits that delay. Rooted-device protection and an infallible offline clock are not claimed. Check the product promise before approving this offline grace policy.

## Verifier service

`server/receipt_verifier.py` uses Python 3.10+ and `cryptography` (`server/requirements.txt`). It binds **127.0.0.1** only; TLS termination and a production process manager are required. It deliberately contains no deployment credentials, account database, or customer analytics. Receipt/account identifiers are not logged by the application handler. Review proxy and hosting logs separately.

Supply server environment variables `AMAZON_SHARED_SECRET`, `AMAZON_PREMIUM_SKU`, and `ENTITLEMENT_SIGNING_KEY_FILE` pointing to a dedicated RSA private key of at least 3072 bits. Optional `PORT` defaults to 8081. Generate/store that key in the deployment's secret-management workflow, keep it outside the repository, and distribute only the corresponding public key to the app. Rotate via an app update/key-overlap design before relying on this in production. Run locally with `python server/receipt_verifier.py`; run its deterministic tests with `python -m unittest discover -s server -p 'test_*.py'`.

The public verification endpoint is not a household account system. Production operators must apply upstream abuse protection and review receipt replay/app-integrity threats for their business model. An observed Amazon receipt and account ID should be treated as sensitive purchase identifiers. A published app should display accurate network and purchasing disclosures and name the verifier operator.

## Primary sources checked 27 October 2026

- [Amazon IAP implementation and PurchasingListener callbacks](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html): account data, purchase updates on resume, pagination, product prices and receipt verification before fulfillment.
- [Amazon RVS request and response contract](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html): production endpoint, exact fields, cancel dates, HTTP 410 and developer shared secret.
- [Amazon authentication-key troubleshooting](https://developer.amazon.com/docs/appstore-sdk/appstore-sdk-troubleshooting.html): app-specific `AppstoreAuthenticationKey.pem` asset.
- [Amazon SDK Maven artifact metadata](https://central.sonatype.com/artifact/com.amazon.device/amazon-appstore-sdk): version 3.0.9 and Amazon publisher identity.
