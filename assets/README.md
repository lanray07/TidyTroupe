# Original TidyTroupe assets

The editable SVG files and master PNG exports were created from original geometric artwork in `tools/generate_assets.py`. Reproduce with Python 3.10+ and Pillow (`python tools/generate_assets.py`). No stock illustrations or external icon packages are used in this asset folder. Colours follow the app's teal, cream, coral, sage and gold palette.

`brand/master-icon-512.png` is the 512px square icon export. `brand/master-icon-1024.png` is its larger master. Native adaptive foreground, cream background and monochrome silhouettes live in Android `res/drawable/ic_launcher_*.xml`, with the adaptive icon in `res/mipmap-anydpi-v26`. `ic_splash.xml` is available for an Android splash theme; adding a resource alone does not prove a splash screen is wired or tested.

The twelve avatar, nine category, four badge, sticker, empty-state, voice and premium SVG files are real editable artwork. Their existence does not mean every variant is already used in app UI. The promotional banner is English source artwork. The screenshot composition SVG is **only a template**: its open capture region deliberately contains no fabricated application screen. Actual matching-locale device captures and marketing review are still required.

The original raster onboarding illustration is `app/src/main/res/drawable/onboarding_art.png`, generated using the built-in OpenAI imagegen tool. Its source was `C:/Users/User/.codex/generated_images/01a17e03-1f4a-7813-bcb2-0029102b4550/exec-ddae6e16-2cc1-4e62-a076-c1004b82f972.png`; a project copy exists in Android resources. Prompt:

> Original premium editorial onboarding illustration for TidyTroupe family chore planner. Wide landscape composition 3:2. Warm cream #FAF7F0 background. A playful family of three abstract friendly rounded household characters: deep teal tall house shape holding a little folded towel, coral rounded square character tending a sage plant, small golden round character carrying a star. Together arranged around subtle checkmark motif. Flat gouache-inspired clean paper cut illustration, restrained texture, elegant rounded geometry, cheerful calm inclusive, soft shadows. Small orbiting sparkles and leaves. Generous cream negative space around subjects. No text, letters, UI, logos, borders or watermark. Beautiful mature family product style.

`MANIFEST.json` records files and hashes. All generated SVGs and native XML resources are syntax-checked by the generator. PNGs are inspected separately; this check does not replace Fire-device masking, contrast and visual review.
