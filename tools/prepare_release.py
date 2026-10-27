#!/usr/bin/env python3
"""Generate editable release manifests and a local buildless marketing site.

No screenshots, translations, ratings, store identities or available products are fabricated.
Run: python tools/prepare_release.py [--base-url https://your-owned-domain.example]
"""
import argparse
import html
import json
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
LOCALES = ["en-GB", "en-US", "en-AU", "es-ES", "es-419", "fr-FR", "fr-CA", "de-DE", "it-IT", "pt-BR", "pt-PT", "nl-NL", "pl-PL", "sv-SE", "ja-JP", "hi-IN", "ar", "tr-TR", "id-ID"]
LISTINGS = {"en-GB":"English (U.K.)", "en-US":"English (U.S.)", "en-AU":"English (Australia)", "es-ES":"Spanish", "de-DE":"German", "fr-FR":"French", "it-IT":"Italian", "ja-JP":"Japanese", "pt-BR":"Portuguese (Brazil)", "pt-PT":"Portuguese (Portugal)", "nl-NL":"Dutch (Netherlands)", "pl-PL":"Polish (Poland)", "sv-SE":"Swedish (Sweden)"}
SCENES = [
 ("dashboard", "Family Chore Planner", "Organise household tasks together."),
 ("child", "Kids Chore Chart & Rewards", "Make daily responsibilities fun and rewarding."),
 ("calendar", "Daily & Weekly Chore Schedule", "Keep family routines organised."),
 ("rewards", "Parent-Controlled Rewards", "Celebrate effort with rewards you manage."),
 ("routines", "Household Routine Planner", "Create simple routines for busy families."),
 ("voice", "Voice-Powered Chore Manager", "Add tasks and check progress using supported voice commands."),
 ("offline", "Offline Family Chore App", "Manage chores without internet."),
 ("progress", "Family Progress Tracker", "See completed tasks and weekly progress."),
]

def write(path, text):
    path = ROOT / path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")

def json_file(path, data):
    write(path, json.dumps(data, ensure_ascii=False, indent=2) + "\n")

def manifests():
    json_file("store/locales.json", {
      "checked_on": "2026-10-27",
      "app_languages": [{"locale":l,"status":"English source; review pending" if l=="en-GB" else "translation_pending", "rtl":l=="ar"} for l in LOCALES],
      "store_listing_languages": [{"locale":l, "documented_console_label": LISTINGS.get(l), "status":"copy_and_assets_pending" if l in LISTINGS else "no_exact_console_entry_verified", "candidate_mapping_requires_review": "Spanish (Mexico)" if l=="es-419" else "French" if l=="fr-CA" else None} for l in LOCALES],
      "additional_documented_listing_options": ["English (India)", "Spanish (Mexico)"],
      "distribution_countries": {"status":"not_selected", "approved_countries":[], "reason":"Requires developer-console country availability, rights, tax and privacy review; app language does not select countries."},
      "speech_recognition_locales": [{"locale":l, "status":"runtime_service_detection_and_device_test_required"} for l in LOCALES],
      "text_to_speech_locales": [{"locale":l, "status":"runtime_engine_language_availability_and_device_test_required"} for l in LOCALES],
      "sources":["https://www.developer.amazon.com/docs/app-submission/distribute-your-app-internationally.html"]
    })
    json_file("store/screenshots/manifest.json", {
      "status":"BLOCKED_PENDING_REAL_APP_CAPTURES_AND_LOCALISATION",
      "approved_listing_locales":[],
      "allowed_amazon_dimensions":[[800,480],[1024,600],[1280,720],[1280,800],[1920,1080],[1920,1200],[2560,1600]],
      "portrait_transpose_allowed":True,
      "source":"https://developer.amazon.com/docs/app-submission/appstore-details.html",
      "rules":["Use actual implemented app screens", "Seed demo data only in debug/test builds", "UI, headline and caption must share the same reviewed locale", "Do not wrap Amazon tablet screenshots in unsupported device hardware", "Never publish sample progress as household history", "Phone captures are secondary Android distribution assets", "Marketing compositions need console policy review before upload"],
      "scenes":[{"id":s,"headline_source_en_GB":h,"caption_source_en_GB":c} for s,h,c in SCENES],
      "captures":[{"locale":l,"scene":s,"variant":v,"status":"pending", "raw_capture":None,"composition":None,"human_review":None} for l in LOCALES for s,_,_ in SCENES for v in ["fire-tablet","android-phone"]]
    })
    json_file("store/listing-en-GB.draft.json", {
      "status":"DRAFT_RECONCILE_WITH_VERIFIED_RELEASE_FEATURES_BEFORE_UPLOAD",
      "display_title":"TidyTroupe – Family Chore & Reward Planner",
      "display_title_characters":42,
      "title_limit":"No numeric display-title limit verified on public details page; validate in console",
      "alternate_titles":["TidyTroupe – Family Chore Planner", "TidyTroupe – Chores & Family Rewards"],
      "short_description":"Plan household chores, share daily routines and celebrate teamwork with parent-controlled rewards. TidyTroupe is being developed for families using a shared Fire tablet.",
      "long_description":"Team up. Tidy up. Celebrate together.\n\nTidyTroupe is a family chore planner being developed to help households organise daily responsibilities. The design brings chore assignments, recurring schedules and parent-controlled rewards into a calm shared-tablet experience.\n\nAdults can plan age-appropriate tasks using nicknames and illustrated avatars, with a simplified child experience and positive encouragement. The product is designed around local household data and account-free core use.\n\nPlanned voice assistance uses push-to-talk and depends on the speech services and languages available on the device. Touch and text alternatives remain part of the experience. Offline core functionality does not imply offline speech recognition.\n\nThis is pre-release copy. Confirm every feature against the tested release before submitting. Premium availability, pricing, supported languages and Fire tablet compatibility are not yet final. No cloud sync service is configured.",
      "product_feature_bullets":["Plan family chores and recurring household responsibilities", "Use a child-friendly task view with adult controls", "Celebrate teamwork with parent-defined rewards", "Keep core household records on the shared device", "Use touch and text alternatives when device speech services are unavailable"],
      "keywords":"family chore planner,kids chore chart,chore tracker,household task manager,family routine planner,reward chart for kids",
      "keyword_research_status":"Source keywords are product hypotheses, not verified search-volume findings; native-market research pending",
      "release_notes":"Initial development build. Not a public release. See verification report for tested capabilities.",
      "premium_test_price_gbp":3.99,
      "price_disclosure":"Planning target only; query actual local price from Amazon product data. No live SKU configured."
    })
    # Blank evidence table, not eight invented app screenshots.
    write("store/screenshots/capture-checklist.csv", "locale,scene,variant,raw_capture_sha256,app_version,device,os,reviewer,status\n" + "".join(f"{l},{s},{v},,,,,,pending\n" for l in LOCALES for s,_,_ in SCENES for v in ["fire-tablet","android-phone"]))

PAGES = {
 "index": ("TidyTroupe – Family Chore Planner", "A little teamwork. A lighter home.", "A calmer way to share the everyday jobs. TidyTroupe brings family chores, simple routines and encouraging rewards together on a shared tablet.", [
  ("Make room for teamwork", "From feeding the cat to an evening tidy-up, shared responsibilities work best when everyone knows their next small step. TidyTroupe is being built to make that step clear."),
  ("Their tasks. Your guidance.", "A focused child experience and a protected adult area put family decisions in adults’ hands. Encouragement comes from effort and shared progress, without public rankings."),
  ("Designed for life at home", "The core experience is designed around local device storage, nicknames and no required account. Voice assistance is optional and depends on available device services.")]),
 "features": ("Features – TidyTroupe", "Small tasks. Thoughtfully connected.", "Explore the product direction behind TidyTroupe. This is a development project; final feature availability is subject to testing.", [("Chores with a rhythm", "Daily, weekly and custom schedules help organise recurring responsibilities."),("Positive rewards", "Adults choose the stars and rewards that make sense for their household."),("A shared view", "Family progress is private, based on recorded activity and free from public comparisons.")]),
 "family-chore-planner": ("Family Chore Planner – TidyTroupe", "Everyone has a part to play.", "A family chore planner can turn a vague list of household jobs into clear responsibilities.", [("Start with the everyday", "Choose one manageable chore, a nickname and a suitable due date. Discuss what done looks like before adding more."),("Share the plan", "Keep the shared tablet somewhere useful and use the child task view when handing it over."),("Review together", "An adult approval step can help younger children learn each task without turning the household into a competition.")]),
 "kids-chore-charts": ("Kids Chore Charts – TidyTroupe", "A clear next step for little helpers.", "Child-friendly chore charts should be easy to read, easy to touch and encouraging to use.", [("Keep tasks achievable", "Use short task names and responsibilities suitable for the child. Avoid using missed chores to judge personality or discipline."),("Choose nicknames", "A local child profile should not need a real name, birth date, email address or photo."),("Celebrate effort", "Positive feedback and a parent-defined reward can recognise a contribution without pressure or a countdown.")]),
 "parent-controlled-rewards": ("Parent-Controlled Rewards – TidyTroupe", "Celebrate in your own way.", "The best reward is one your family chooses together. Think a shared game, a story or choosing the weekend activity.", [("Set the rules together", "Adults define the star threshold and available rewards. Purchase prompts belong outside the child experience."),("Approval when it helps", "A reported task can wait for adult review before stars become available."),("No random purchases", "The product brief excludes loot boxes, paid advantages, gambling-style rewards and public leaderboards.")]),
 "household-routines": ("Household Routine Planner – TidyTroupe", "Find your family’s rhythm.", "Turn a busy morning or evening reset into a familiar sequence of small jobs.", [("Morning preparation", "Discuss a simple order: make the bed, get dressed, pack the school bag."),("Evening tidy-up", "Put shared items away, prepare tomorrow’s clothes and check the next day together."),("Make it yours", "Routine templates should be adapted to your household, your available time and individual support needs.")]),
 "voice-features": ("Voice Features – TidyTroupe", "A helping voice, when you choose.", "Voice assistance is designed around push-to-talk with visible listening and a text alternative.", [("Check your device", "Some Fire tablets do not have a suitable Android speech recognition service. Recognition and text-to-speech language support are separate."),("Review before acting", "A transcription and structured command preview help adults check what the device understood before making changes."),("Children and speech", "Child microphone input is disabled by default and needs parental activation plus review of any external speech processing. Offline core use does not guarantee offline voice recognition.")]),
 "fire-tablet-compatibility": ("Fire Tablet Compatibility – TidyTroupe", "Built with the shared tablet in mind.", "The primary target is Amazon Fire tablets. Compatibility still needs testing on physical devices before release.", [("A comfortable shared screen", "The design targets 7–10-inch screens in portrait and landscape with touch targets at least 48dp."),("No mandatory Google Play Services", "The product architecture is intended to work without Google account registration or required Play Services."),("Android distribution", "Amazon ended its Appstore for Android mobile devices on 20 August 2025; Fire tablet support continues. Secondary Android distribution needs a separate path.")]),
 "pricing": ("Pricing – TidyTroupe", "Family-friendly, from the start.", "The proposed model is a free core experience and an optional one-time Premium purchase. No subscriptions are planned for the initial release.", [("Free core — planned", "One household, up to three profiles, basic recurring chores, rewards, reminders and progress."),("Premium — proposed", "More profiles, advanced routines and other extensions are planned. The test target is £3.99 once; this is not an available offer or a confirmed local store price."),("Adults decide", "Purchases must stay in the protected adult area. A live price will come directly from Amazon after product configuration and verification.")]),
 "faq": ("Frequently Asked Questions – TidyTroupe", "A few helpful answers.", "Straight answers about the project, its privacy goals and release status.", [("Is TidyTroupe available now?", "This repository contains a development project. No public Amazon listing or launch date has been supplied."),("Will chores work offline?", "Offline core household management is the design goal. Speech recognition can depend on installed services and a network."),("Can children use a Fire tablet?", "A simplified child task view is part of the design. Physical Fire tablet validation remains a release requirement."),("Does it sync across devices?", "No cross-device sync service is configured. Do not assume another tablet can see local household records."),("Which languages are available?", "Nineteen app locales are targeted. Translations, human review and localised screenshots remain pending; the app, store and speech language lists are separate.")]),
 "privacy": ("Privacy – TidyTroupe", "A private place for household teamwork.", "TidyTroupe is designed around minimal profile information and local household records.", [("Use a nickname", "Child profiles should not require real names, birth dates, email addresses or photographs."),("Optional means optional", "Microphone use and notifications should be opt-in. Speech processing depends on the installed service; no offline recognition guarantee is made."),("Draft notice", "The project privacy notice still needs a legal controller, contact details and review against the final binary. This page is a development summary, not a final legal notice.")]),
 "support": ("Support – TidyTroupe", "A little help with the next step.", "The project is in development. A public support contact will be added before launch.", [("Voice unavailable?", "Use touch or typed commands and check the installed speech service, language and microphone permission."),("Reminder delayed?", "Check notification settings, quiet hours and system battery restrictions. Operating systems can delay background work."),("Protect your household information", "Remove nicknames and personal details before sharing a problem report. Never share a parent PIN, store password or raw child audio.")])
}

CSS = """@import url('');
:root{--teal:#246a73;--cream:#faf7f0;--coral:#e68a72;--sage:#8eb69b;--ink:#293236;--line:#dddcd3}*{box-sizing:border-box}body{margin:0;background:var(--cream);color:var(--ink);font-family:system-ui,-apple-system,'Segoe UI',sans-serif;line-height:1.65}a{color:var(--teal);text-underline-offset:4px}a:focus-visible,summary:focus-visible{outline:3px solid #bc573b;outline-offset:5px}header{max-width:1220px;margin:auto;padding:24px 32px;display:flex;align-items:center;justify-content:space-between;gap:24px;border-bottom:1px solid var(--line)}.brand{display:flex;align-items:center;gap:12px;font-size:25px;font-weight:800;letter-spacing:-1px;text-decoration:none;color:var(--teal)}.brand img{width:42px;height:42px}nav{display:flex;gap:24px;flex-wrap:wrap}nav a{text-decoration:none;font-size:14px;font-weight:600}.notice{text-align:center;background:#e5ece4;padding:9px 20px;font-size:12px;letter-spacing:.025em}main{max-width:1220px;margin:auto;padding:0 32px}.hero{display:grid;grid-template-columns:1.2fr 1fr;gap:56px;align-items:center;padding:80px 0 72px}.eyebrow{text-transform:uppercase;font-size:12px;letter-spacing:2px;font-weight:750;color:var(--teal)}h1{font-family:Georgia,serif;font-weight:500;font-size:clamp(42px,5.5vw,78px);letter-spacing:-2.5px;line-height:1.05;margin:20px 0 26px;max-width:800px}p.lead{font-size:18px;color:#525c5a;max-width:650px}.actions{display:flex;flex-wrap:wrap;gap:20px;align-items:center;margin-top:32px}.button{display:inline-block;min-height:48px;border-radius:50px;padding:12px 24px;background:var(--teal);color:white;text-decoration:none;font-size:14px;font-weight:650}.tiny{font-size:12px;color:#59645e}.art{width:100%;max-width:470px;justify-self:center}.content{display:grid;grid-template-columns:repeat(3,1fr);gap:32px;padding:48px 0 72px;border-top:1px solid var(--line)}article{padding-right:14px}article .number{font-family:Georgia,serif;color:#587b65;font-size:30px}h2{font-family:Georgia,serif;font-size:28px;line-height:1.2;font-weight:500;letter-spacing:-.5px}article p{color:#56615c;font-size:15px}.banner{background:var(--teal);color:white;border-radius:24px;padding:40px;display:flex;justify-content:space-between;gap:40px;align-items:center;margin-bottom:64px}.banner h2{font-size:34px;margin:0 0 8px}.banner p{margin:0;color:#dfebe8}.banner a{color:var(--cream);white-space:nowrap}footer{border-top:1px solid var(--line);padding:32px;max-width:1220px;margin:auto}.footer-links{display:flex;flex-wrap:wrap;gap:12px 24px;margin-bottom:20px}.footer-links a{font-size:13px}.subpage .hero{grid-template-columns:1fr;padding:70px 0 50px}.subpage h1{font-size:clamp(40px,5vw,66px)}.subpage .content{grid-template-columns:repeat(auto-fit,minmax(250px,1fr))}@media(max-width:750px){header{padding:20px;align-items:flex-start;flex-direction:column;gap:16px}nav{gap:12px 20px}.hero{grid-template-columns:1fr;gap:32px;padding:44px 0}.art{max-width:330px}main{padding:0 22px}h1{letter-spacing:-1.5px}.content{grid-template-columns:1fr;padding:30px 0;gap:12px}.banner{flex-direction:column;align-items:flex-start;padding:28px;margin:20px 0 44px}footer{padding:24px}.subpage .hero{padding-top:44px}}@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto!important}}""".replace("@import url('');\n", "")

ART = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 500 440" role="img" aria-labelledby="title"><title id="title">Three original household characters working together</title><ellipse cx="250" cy="390" rx="210" ry="18" fill="#e7e4d8"/><circle cx="255" cy="219" r="188" fill="#e8edde"/><path d="M130 178l85-74 86 74v141H130Z" fill="#246a73"/><rect x="253" y="198" width="127" height="141" rx="62" fill="#e68a72"/><rect x="66" y="258" width="116" height="106" rx="40" fill="#8eb69b"/><g fill="#293236"><circle cx="188" cy="207" r="5"/><circle cx="238" cy="207" r="5"/><circle cx="295" cy="247" r="5"/><circle cx="335" cy="247" r="5"/><circle cx="103" cy="299" r="4"/><circle cx="141" cy="299" r="4"/></g><g fill="none" stroke-linecap="round" stroke-width="4"><path d="M201 226q13 13 25 0" stroke="#faf7f0"/><path d="M304 265q12 13 24 0M112 316q12 10 22 0" stroke="#293236"/></g><path d="M181 320l42 38 76-96" stroke="#faf7f0" stroke-width="20" fill="none" stroke-linecap="round" stroke-linejoin="round"/><g fill="#bd8b47"><path d="M374 111l5 13 13 5-13 5-5 13-5-13-13-5 13-5Z"/><path d="M96 168l4 10 10 4-10 4-4 10-4-10-10-4 10-4Z"/></g><path d="M371 353q37-1 36-40M94 233q-26-11-20-37" fill="none" stroke="#8eb69b" stroke-width="5" stroke-linecap="round"/></svg>'''

def website(base):
    configured = base is not None
    base = (base or "https://tidytroupe.invalid").rstrip("/")
    links = [("features","Features"),("fire-tablet-compatibility","Fire tablets"),("pricing","Pricing"),("faq","FAQ")]
    nav = "".join(f'<a href="{s}.html">{t}</a>' for s,t in links)
    footer = "".join(f'<a href="{slug}.html">{title.split(" – ")[0]}</a>' for slug,(title,*_) in PAGES.items())
    for slug,(title, heading, intro, sections) in PAGES.items():
        home = slug == "index"
        canonical = base + "/" + ("" if home else slug + ".html")
        structured = {"@context":"https://schema.org", "@type":"SoftwareApplication", "name":"TidyTroupe", "applicationCategory":"LifestyleApplication", "operatingSystem":"Android / Fire OS", "description":"Family chore planner project in development; no public release available.", "url":base + "/", "inLanguage":"en-GB"}
        # No invented rating, aggregate review, offers, download link or store listing.
        robots = "index,follow" if configured else "noindex,nofollow"
        body = "".join(f'<article><span class="number">0{i+1}</span><h2>{html.escape(h)}</h2><p>{html.escape(p)}</p></article>' for i,(h,p) in enumerate(sections))
        art = '<img class="art" src="assets/teamwork.svg" width="500" height="440" alt="Three friendly household shapes with a shared checkmark. Original brand illustration, not an app screenshot.">' if home else ""
        actions = '<div class="actions"><a class="button" href="features.html">Explore the idea</a><span class="tiny">Made for everyday family teamwork.</span></div>' if home else ""
        banner = '<section class="banner"><div><h2>Team up. Tidy up.<br>Celebrate together.</h2><p>Thoughtful tools for a home you share.</p></div><a href="faq.html">Read the project FAQ →</a></section>' if home else ""
        write(f"website/dist/{slug}.html",f'''<!doctype html>
<html lang="en-GB"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{html.escape(title)}</title><meta name="description" content="{html.escape(intro,quote=True)}"><meta name="robots" content="{robots}"><link rel="canonical" href="{canonical}"><link rel="alternate" hreflang="en-GB" href="{canonical}"><link rel="alternate" hreflang="x-default" href="{canonical}"><link rel="stylesheet" href="assets/style.css"><script type="application/ld+json">{json.dumps(structured)}</script></head>
<body class="{'home' if home else 'subpage'}"><div class="notice">IN DEVELOPMENT · No public store listing yet</div><header><a class="brand" href="index.html"><img src="assets/teamwork.svg" alt="" width="42" height="42">TidyTroupe</a><nav aria-label="Main">{nav}</nav></header><main><section class="hero"><div><span class="eyebrow">A little help. A lot of heart.</span><h1>{html.escape(heading)}</h1><p class="lead">{html.escape(intro)}</p>{actions}</div>{art}</section><section class="content" aria-label="Learn more">{body}</section>{banner}</main><footer><div class="footer-links">{footer}</div><p class="tiny">TidyTroupe is a provisional product name. Local development website · English draft · No tracking, advertising, testimonials or ratings.</p></footer></body></html>''')
    write("website/dist/assets/style.css", CSS)
    write("website/dist/assets/teamwork.svg", ART)
    write("website/dist/robots.txt", "User-agent: *\n" + ("Allow: /\nSitemap: " + base + "/sitemap.xml\n" if configured else "Disallow: /\n# Configure an owned production domain before indexing.\n"))
    urls = "".join(f"<url><loc>{base}/{' ' if s=='index' else s+'.html'}</loc></url>".replace("/ </loc>","/</loc>") for s in PAGES)
    write("website/dist/sitemap.xml", '<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">'+urls+'</urlset>')
    write("website/README.md", "# Local marketing website\n\nBuildless static HTML, CSS and original SVG artwork. Generated by `python tools/prepare_release.py`; outputs are in `website/dist`. No external libraries, fonts, analytics or form submissions. Serve locally with `python -m http.server 8080 --directory website/dist`.\n\nBefore deployment, set an owned HTTPS domain with `python tools/prepare_release.py --base-url https://YOUR-DOMAIN`, review product claims and privacy notice, add the verified Amazon listing link, configure a real support route and complete localisation. The default `.invalid` domain is deliberately non-production and pages are noindex. Hreflang entries cover only the actual English page; no untranslated locale pages are invented. No live launch or purchase offer is claimed.\n\nThe friendly house/capsule/rounded shape artwork is original SVG source, not an app screenshot. The site has not been published.\n")

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url")
    args=parser.parse_args()
    if args.base_url:
        parsed=urlparse(args.base_url)
        if parsed.scheme!="https" or not parsed.netloc or parsed.query or parsed.fragment:
            parser.error("--base-url must be a clean HTTPS origin")
    manifests()
    website(args.base_url)
    print("Generated release manifests and local website. No screenshots captured; no site deployed.")

if __name__ == "__main__":
    main()
