# Screenshot production

`manifest.json` is an explicit pending capture plan, not completed images. No raw app screenshots or finished localised marketing compositions are present until an actual capture workflow runs.

Navigate a connected test device to a seeded app scene in the intended reviewed locale. Then run:

```powershell
python tools/capture_screen.py --adb C:/Users/User/AppData/Local/Android/Sdk/platform-tools/adb.exe --serial DEVICE --locale en-GB --scene dashboard --variant fire-tablet
```

The script captures real PNG pixels with `adb exec-out screencap -p`, records dimensions and SHA-256 and leaves content/locale/device review pending. It does not pretend that the app was translated or that the scene matches the flag. Verify those against the actual screen. It will not overwrite an existing capture.

Optional `--reviewed-copy path.json` produces an editable SVG composition containing the real screenshot. The JSON must include `locale`, `human_approved: true`, `headline` and `caption`; only a real reviewer should assert that flag. Rasterisation, native font selection, RTL positioning, headline wrapping and cropping are explicit review steps before final PNG/JPEG export. A translated headline must never be used to imply that English UI is localised. The composition template is not an accepted upload format or a verified production image.

Use synthetic nicknames in deterministic test data, hide personal/system notifications and show real app progress from that dataset. Record the build/device/OS/reviewer in `capture-checklist.csv`. Capture all eight scenes on tablet and phone variants; use phone images only where the secondary distribution channel permits them. Amazon's tablet submission takes three to ten images and the allowed dimensions in the manifest, including portrait transposes. Console policy review remains necessary for marketing compositions.

After locale/content/typography review, populate the manifest's output paths and reviewer evidence, export actual production PNG/JPEGs and mark only those individual records ready. Do not change the global status until all approved listing locales have the required set.
