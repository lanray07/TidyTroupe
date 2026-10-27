#!/usr/bin/env python3
"""Capture a real attached device screen; never synthesise app UI.

Navigate the app to the requested scene and locale on a test device first.
python tools/capture_screen.py --adb /path/to/adb --serial DEVICE --locale en-GB --scene dashboard --variant fire-tablet

Captures remain pending human review. No locale, content or platform inference is made.
"""
import argparse
import base64
import hashlib
import html
import json
from pathlib import Path
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SCENES = ["dashboard", "child", "calendar", "rewards", "routines", "voice", "offline", "progress"]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--locale", required=True)
    parser.add_argument("--scene", choices=SCENES, required=True)
    parser.add_argument("--variant", choices=["fire-tablet", "android-phone"], required=True)
    parser.add_argument("--reviewed-copy", type=Path, help="Optional reviewed JSON with locale, human_approved=true, headline, caption")
    args=parser.parse_args()
    manifest_path=ROOT / "store/screenshots/manifest.json"
    manifest=json.loads(manifest_path.read_text(encoding="utf-8"))
    record=next((r for r in manifest["captures"] if r["locale"]==args.locale and r["scene"]==args.scene and r["variant"]==args.variant),None)
    if record is None:
        parser.error("Locale/scene/variant does not occur in screenshot manifest")
    output=ROOT / "store/screenshots/captures" / args.locale / args.variant / (args.scene + ".png")
    if output.exists():
        parser.error("Capture already exists; retain or move previous evidence before capturing again")
    command=[args.adb,"-s",args.serial,"exec-out","screencap","-p"]
    result=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30,check=True)
    data=result.stdout
    if data[:8] != b"\x89PNG\r\n\x1a\n" or len(data)<24:
        parser.error("Device did not return a PNG screenshot")
    width,height=struct.unpack(">II",data[16:24])
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_bytes(data)
    relative=str(output.relative_to(ROOT)).replace("\\","/")
    record.update({"raw_capture":relative,"sha256":hashlib.sha256(data).hexdigest(),"dimensions":[width,height],"status":"captured_pending_locale_content_and_device_review"})
    allowed=manifest["allowed_amazon_dimensions"]
    record["amazon_dimensions_valid"]=[width,height] in allowed or [height,width] in allowed
    if args.reviewed_copy:
        copy=json.loads(args.reviewed_copy.read_text(encoding="utf-8"))
        if copy.get("locale")!=args.locale or copy.get("human_approved") is not True:
            parser.error("Marketing copy must match capture locale and be human approved; raw capture retained")
        headline=html.escape(copy["headline"])
        caption=html.escape(copy["caption"])
        rtl='direction="rtl"' if args.locale=="ar" else ""
        # Editable SVG retains real screenshot pixels. It is a composition template,
        # not an approved PNG/JPEG upload. Review the UI language and rasterise only
        # after typography, wrapping and Amazon policy checks.
        svg=f'''<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1200" viewBox="0 0 1920 1200"><rect width="1920" height="1200" fill="#faf7f0"/><text x="96" y="90" font-family="sans-serif" font-size="24" fill="#246a73">TidyTroupe</text><text x="96" y="168" font-family="sans-serif" font-size="52" font-weight="bold" fill="#293236" {rtl}>{headline}</text><text x="96" y="225" font-family="sans-serif" font-size="27" fill="#293236" {rtl}>{caption}</text><image x="96" y="285" width="1728" height="850" preserveAspectRatio="xMidYMid meet" href="data:image/png;base64,{base64.b64encode(data).decode()}"/></svg>'''
        template=output.with_suffix(".composition.svg")
        template.write_text(svg,encoding="utf-8")
        record["composition"]=str(template.relative_to(ROOT)).replace("\\","/")
        record["composition_status"]="editable_template_pending_typography_and_policy_review"
    manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps({"captured":relative,"dimensions":[width,height],"status":record["status"]}))

if __name__=="__main__":
    main()
