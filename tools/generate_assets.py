#!/usr/bin/env python3
"""Reproduce TidyTroupe original vector artwork and master icon PNGs.

SVG artwork is deterministic, editable source. Pillow is used only to render the
original master icon geometry; it does not alter generated onboarding artwork.
No UI screenshots are synthesized by this script.
"""
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "assets"
RES = ROOT / "app/src/main/res"
TEAL, CORAL, SAGE, GOLD, CREAM, INK = "#246A73", "#E68A72", "#8EB69B", "#E8BB63", "#FAF7F0", "#293236"


def save(relative, text):
    target = ASSETS / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text.strip() + "\n", encoding="utf-8")


def svg(body, width=512, height=512, background=None):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img">' + (f'<rect width="{width}" height="{height}" fill="{background}"/>' if background else "") + body + '</svg>'


def family(x=0, y=0, scale=1):
    return f'''<g transform="translate({x} {y}) scale({scale})">
      <path d="M90 232 180 147 270 232V365Q270 398 232 398H128Q90 398 90 365Z" fill="{TEAL}"/>
      <rect x="256" y="119" width="161" height="251" rx="62" fill="{CORAL}"/>
      <circle cx="228" cy="360" r="81" fill="{GOLD}"/>
      <path d="m185 346 33 33 62-71" fill="none" stroke="#fff" stroke-width="24" stroke-linecap="round" stroke-linejoin="round"/>
      <g fill="{INK}"><circle cx="150" cy="261" r="7"/><circle cx="201" cy="261" r="7"/><circle cx="304" cy="231" r="7"/><circle cx="355" cy="231" r="7"/></g>
      <path d="M159 288q17 20 34 0M312 259q18 20 36 0" fill="none" stroke="{INK}" stroke-width="7" stroke-linecap="round"/>
    </g>'''


def master_png(size):
    # Draw at 3x output resolution for smooth, matching original brand geometry.
    factor = size * 3 / 512
    image = Image.new("RGB", (size * 3, size * 3), CREAM)
    d = ImageDraw.Draw(image)
    p = lambda coords: tuple(round(v * factor) for v in coords)
    d.polygon([p((90,232)),p((180,147)),p((270,232)),p((270,365)),p((90,365))], fill=TEAL)
    d.rounded_rectangle(p((90,232,270,398)), radius=round(33*factor), fill=TEAL)
    d.rounded_rectangle(p((256,119,417,370)), radius=round(62*factor), fill=CORAL)
    d.ellipse(p((147,279,309,441)), fill=GOLD)
    width = round(24*factor)
    d.line([p((185,346)),p((218,379)),p((280,308))], fill="white", width=width, joint="curve")
    for x,y in ((185,346),(218,379),(280,308)):
        d.ellipse(p((x-12,y-12,x+12,y+12)), fill="white")
    for x,y in ((150,261),(201,261),(304,231),(355,231)):
        d.ellipse(p((x-7,y-7,x+7,y+7)), fill=INK)
    d.arc(p((159,278,193,305)), 5,175, fill=INK, width=round(7*factor))
    d.arc(p((312,249,348,276)), 5,175, fill=INK, width=round(7*factor))
    return image.resize((size,size), Image.Resampling.LANCZOS)


def main():
    save("brand/master-icon.svg", svg(family(), background=CREAM))
    for size in (512,1024):
        target = ASSETS / f"brand/master-icon-{size}.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        master_png(size).save(target)
    save("brand/splash.svg", svg(family(130,36,.75),768,512,CREAM))
    save("illustrations/empty-state.svg", svg(f'<circle cx="256" cy="256" r="180" fill="{SAGE}" opacity=".13"/>' + family(48,20,.82)))
    save("illustrations/voice-assistant.svg", svg(f'''<circle cx="256" cy="256" r="184" fill="{SAGE}" opacity=".17"/><rect x="213" y="118" width="86" height="164" rx="43" fill="{TEAL}"/><path d="M174 230v17a82 82 0 0 0 164 0v-17M256 329v59M209 390h94" stroke="{TEAL}" stroke-width="18" fill="none" stroke-linecap="round"/><path d="M120 179v67M91 195v34M392 179v67M421 195v34" stroke="{CORAL}" stroke-width="13" stroke-linecap="round"/><path d="m345 113 13 21 24 6-21 14-4 25-15-20-25-3 19-17Z" fill="{GOLD}"/>'''))
    save("illustrations/premium.svg", svg(f'''<circle cx="256" cy="256" r="188" fill="{GOLD}" opacity=".16"/><path d="M136 189 204 240 256 135 308 240 376 189 350 346H162Z" fill="{GOLD}"/><path d="M165 346h182" stroke="{TEAL}" stroke-width="22" stroke-linecap="round"/><circle cx="256" cy="276" r="32" fill="{CREAM}"/><path d="m240 275 12 13 24-27" stroke="{TEAL}" stroke-width="9" fill="none" stroke-linecap="round"/>'''))
    save("marketing/promotional-banner.svg", svg(f'<circle cx="1120" cy="310" r="260" fill="{SAGE}" opacity=".15"/>'+family(820,40,1.05)+f'<text x="96" y="130" font-family="sans-serif" font-size="30" fill="{TEAL}">tidytroupe</text><text x="96" y="255" font-family="serif" font-size="70" fill="{INK}">A little teamwork.</text><text x="96" y="340" font-family="serif" font-size="70" fill="{INK}">A happier home.</text><text x="96" y="414" font-family="sans-serif" font-size="25" fill="{TEAL}">Team up. Tidy up. Celebrate together.</text>',1600,600,CREAM))
    colors = [SAGE,CORAL,GOLD,"#98BCC8",TEAL,"#B6A6C9"]
    for index,color in enumerate(colors):
        for shape in ("house","round"):
            silhouette = f'<path d="M126 221 256 105 386 221V365Q386 404 348 404H164Q126 404 126 365Z" fill="{color}"/>' if shape == "house" else f'<rect x="123" y="112" width="266" height="296" rx="125" fill="{color}"/>'
            face = f'<circle cx="208" cy="257" r="10" fill="{INK}"/><circle cx="304" cy="257" r="10" fill="{INK}"/><path d="M231 294q25 26 50 0" stroke="{INK}" stroke-width="10" fill="none" stroke-linecap="round"/><ellipse cx="180" cy="285" rx="18" ry="10" fill="{CREAM}" opacity=".35"/><ellipse cx="333" cy="285" rx="18" ry="10" fill="{CREAM}" opacity=".35"/>'
            save(f"avatars/{shape}-{index+1}.svg",svg(f'<circle cx="256" cy="256" r="240" fill="{color}" opacity=".15"/>'+silhouette+face))
    paths = {
        "bedroom":'<path d="M112 339V192m288 147V238H112m0 69h288M152 238v-51h96v51M272 238v-51h88v51"/>',
        "kitchen":'<path d="M155 119v110q0 42 42 42v122M197 119v110m42-110v110q0 42-42 42M350 119q-53 65-53 134h53m0-134v274"/>',
        "bathroom":'<path d="M256 102C221 163 152 226 152 297a104 104 0 0 0 208 0c0-71-69-134-104-195Z"/><path d="M196 293q0 53 48 64"/>',
        "laundry":'<rect x="133" y="103" width="246" height="306" rx="24"/><circle cx="256" cy="282" r="78"/><path d="M134 175h244M174 140h30m99 0h34M190 282q35-42 66 0t66 0"/>',
        "living-room":'<path d="M137 220v-43q0-27 27-27h184q27 0 27 27v43M112 282v-47q0-20 20-20h28v86h192v-86h28q20 0 20 20v117H112Zm25 70v30m238-30v30"/>',
        "garden":'<path d="M256 397V231M256 297q-130 0-130-122 130 0 130 122ZM256 235q0-127 129-127 0 127-129 127Z"/>',
        "pets":'<path d="M170 327q20-84 86-84t86 84q0 53-86 25-86 28-86-25Z"/><ellipse cx="135" cy="221" rx="29" ry="42"/><ellipse cx="209" cy="159" rx="28" ry="42"/><ellipse cx="303" cy="159" rx="28" ry="42"/><ellipse cx="377" cy="221" rx="29" ry="42"/>',
        "school":'<path d="m96 209 160-85 160 85-160 85Zm55 33v101q105 70 210 0V242M415 209v121"/>',
        "general-household":'<path d="m106 239 150-133 150 133M142 218v181h228V218M224 399V284h64v115"/>',
    }
    for name,path in paths.items():
        save(f"categories/{name}.svg", svg(f'<rect x="24" y="24" width="464" height="464" rx="140" fill="{SAGE}" opacity=".18"/><g fill="none" stroke="{TEAL}" stroke-width="17" stroke-linecap="round" stroke-linejoin="round">{path}</g>'))
    for name,label,color in (("teamwork","Teamwork",TEAL),("little-helper","Little helper",CORAL),("five-tasks","Five little wins",SAGE),("routine-ready","Routine ready",GOLD)):
        save(f"badges/{name}.svg", svg(f'<path d="m187 330-33 135 102-53 102 53-33-135" fill="{color}" opacity=".65"/><circle cx="256" cy="232" r="154" fill="{color}"/><circle cx="256" cy="232" r="123" fill="{CREAM}"/><path d="m256 141 27 55 61 9-44 43 10 61-54-29-54 29 10-61-44-43 61-9Z" fill="{color}"/><title>{label}</title>'))
    save("stickers/thank-you.svg",svg(f'<path d="M256 53 290 99 347 87 369 141 426 151 423 209 463 251 423 293 426 351 369 361 347 415 290 403 256 449 222 403 165 415 143 361 86 351 89 293 49 251 89 209 86 151 143 141 165 87 222 99Z" fill="{GOLD}"/><circle cx="213" cy="222" r="9" fill="{INK}"/><circle cx="299" cy="222" r="9" fill="{INK}"/><path d="M211 268q45 55 90 0" stroke="{INK}" stroke-width="13" fill="none" stroke-linecap="round"/>'))
    save("marketing/screenshot-composition-template.svg",svg(f'<text x="96" y="92" font-family="sans-serif" font-size="28" fill="{TEAL}">tidytroupe</text><text x="96" y="175" font-family="serif" font-size="60" fill="{INK}">Family Chore Planner</text><text x="96" y="233" font-family="sans-serif" font-size="28" fill="{INK}">Organise household tasks together.</text><rect x="96" y="290" width="1728" height="810" rx="30" fill="none" stroke="{SAGE}" stroke-width="3" stroke-dasharray="12 12"/><text x="960" y="700" text-anchor="middle" font-family="sans-serif" font-size="26" fill="{TEAL}">Insert a real, matching-locale app capture here</text>',1920,1200,CREAM))
    foreground = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><group android:pivotX="54" android:pivotY="54" android:scaleX="0.8" android:scaleY="0.8"><path android:fillColor="{TEAL}" android:pathData="M19,49L38,31L57,49L57,77Q57,84 49,84L27,84Q19,84 19,77z"/><path android:fillColor="{CORAL}" android:pathData="M54,38Q54,25 67,25L75,25Q88,25 88,38L88,66Q88,78 75,78L67,78Q54,78 54,66z"/><path android:fillColor="{GOLD}" android:pathData="M48,59a17,17 0,1 1,0,34a17,17 0,1 1,0,-34"/><path android:strokeColor="#FFFFFF" android:strokeWidth="5" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000" android:pathData="M39,73L46,80L59,65"/></group></vector>'''
    draw = RES / "drawable"; draw.mkdir(parents=True,exist_ok=True)
    (draw/"ic_launcher_foreground.xml").write_text(foreground,encoding="utf-8")
    (draw/"ic_launcher_background.xml").write_text(f'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="{CREAM}"/></shape>\n',encoding="utf-8")
    monochrome = '''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><group android:pivotX="54" android:pivotY="54" android:scaleX="0.8" android:scaleY="0.8"><path android:fillColor="#000000" android:pathData="M19,49L38,31L57,49L57,77Q57,84 49,84L27,84Q19,84 19,77zM54,38Q54,25 67,25L75,25Q88,25 88,38L88,66Q88,78 75,78L67,78Q54,78 54,66zM48,59a17,17 0,1 1,0,34a17,17 0,1 1,0,-34"/></group></vector>'''
    (draw/"ic_launcher_monochrome.xml").write_text(monochrome,encoding="utf-8")
    (draw/"ic_splash.xml").write_text(foreground,encoding="utf-8")
    adaptive = RES/"mipmap-anydpi-v26"; adaptive.mkdir(parents=True,exist_ok=True)
    (adaptive/"ic_launcher.xml").write_text('''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/ic_launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/><monochrome android:drawable="@drawable/ic_launcher_monochrome"/></adaptive-icon>\n''',encoding="utf-8")
    files = sorted([p for p in ASSETS.rglob("*") if p.is_file() and p.name != "MANIFEST.json"])
    originals=[]
    for p in files:
        if p.suffix == ".svg": ET.parse(p)
        originals.append({"file":str(p.relative_to(ROOT)).replace("\\","/"),"sha256":hashlib.sha256(p.read_bytes()).hexdigest(),"type":"editable_original_vector" if p.suffix == ".svg" else "original_master_icon_raster"})
    for p in list(draw.glob("ic_*.xml"))+list(adaptive.glob("*.xml")): ET.parse(p)
    manifest={"license":"Original artwork created for TidyTroupe; no third-party stock assets.","generator":"tools/generate_assets.py","screenshots":"No app screenshots are generated here. The screenshot SVG is an editable composition template only.","onboarding":{"file":"app/src/main/res/drawable/onboarding_art.png","origin":"OpenAI built-in imagegen; prompt and source path documented in assets/README.md"},"files":originals}
    (ASSETS/"MANIFEST.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
    print(f"Generated {len(originals)} asset files and 5 Android icon resources; all SVG/XML parsed.")


if __name__ == "__main__": main()
