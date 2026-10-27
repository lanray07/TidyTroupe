#!/usr/bin/env python3
"""Localisation tooling. Python 3.10+, standard library only; never ships secrets.

python tools/localization.py extract
python tools/localization.py translate --locale de-DE  # DEEPL_API_KEY in environment
python tools/localization.py validate
"""
from __future__ import annotations
import argparse
import collections
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "tools" / "localisation"
RES = ROOT / "app" / "src" / "main" / "res"
LOCALES = {
    "en-GB": "EN-GB", "en-US": "EN-US", "en-AU": "EN-GB",
    "es-ES": "ES", "es-419": "ES", "fr-FR": "FR", "fr-CA": "FR",
    "de-DE": "DE", "it-IT": "IT", "pt-BR": "PT-BR", "pt-PT": "PT-PT",
    "nl-NL": "NL", "pl-PL": "PL", "sv-SE": "SV", "ja-JP": "JA",
    "hi-IN": "HI", "ar": "AR", "tr-TR": "TR", "id-ID": "ID",
}
# Android placeholders and escaped formatting characters are protected as XML tags.
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]|\{[A-Za-z0-9_.]+\}")
QUANTITIES = {"zero", "one", "two", "few", "many", "other"}

def read_json(path: Path, default):
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else default

def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def fingerprint(text: str):
    return hashlib.sha256(text.encode()).hexdigest()

def placeholders(text: str):
    return collections.Counter(PLACEHOLDER.findall(text))

def protect(text: str):
    parts = []
    def replace(match):
        parts.append(match.group())
        return f'<x id="{len(parts)-1}"/>'
    # XML escape first, then insert protected tags.
    from xml.sax.saxutils import escape
    return PLACEHOLDER.sub(replace, escape(text)), parts

def restore(text: str, parts: list[str]):
    node = ET.fromstring("<root>" + text + "</root>")
    restored = node.text or ""
    seen = []
    for child in node:
        if child.tag != "x" or set(child.attrib) != {"id"}:
            raise ValueError("Unexpected translation markup")
        index = int(child.attrib["id"])
        if not 0 <= index < len(parts):
            raise ValueError("Unknown placeholder")
        seen.append(index)
        restored += parts[index] + (child.tail or "")
    if sorted(seen) != list(range(len(parts))):
        raise ValueError("Translation lost or duplicated a placeholder")
    return restored

def extract(source: Path):
    if not source.exists():
        raise ValueError(f"Source strings do not exist: {source}")
    records = {}
    for entry in ET.parse(source).getroot():
        if entry.attrib.get("translatable") == "false":
            continue
        name = entry.attrib.get("name")
        if not name:
            continue
        if entry.tag == "string":
            records[name] = {"type": "string", "text": "".join(entry.itertext())}
        elif entry.tag in ("plurals", "string-array"):
            values = {}
            for index, item in enumerate(entry):
                quantity = item.attrib.get("quantity", str(index))
                values[quantity] = "".join(item.itertext())
            if entry.tag == "plurals" and ("other" not in values or not set(values) <= QUANTITIES):
                raise ValueError(f"Invalid plural quantities: {name}")
            records[name] = {"type": entry.tag, "items": values}
    write_json(WORK / "source.json", records)
    return records

def units(records):
    for name, record in records.items():
        if record["type"] == "string":
            yield name, record["text"]
        else:
            for quantity, value in record["items"].items():
                yield f"{name}:{quantity}", value

def translate_text(text: str, locale: str, key: str, glossary: dict):
    encoded, parts = protect(text)
    params = {"text": encoded, "source_lang": "EN", "target_lang": LOCALES[locale],
              "tag_handling": "xml", "ignore_tags": "x", "preserve_formatting": "1"}
    glossary_id = os.getenv("DEEPL_GLOSSARY_ID")
    if glossary_id:
        params["glossary_id"] = glossary_id
    host = "https://api-free.deepl.com/v2/translate" if key.endswith(":fx") else "https://api.deepl.com/v2/translate"
    request = urllib.request.Request(host, urllib.parse.urlencode(params).encode(),
        headers={"Authorization": "DeepL-Auth-Key " + key, "Content-Type": "application/x-www-form-urlencoded"})
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = json.load(response)
    result = restore(payload["translations"][0]["text"], parts)
    if placeholders(result) != placeholders(text):
        raise ValueError("Placeholder mismatch")
    # Brand spellings are reviewed in addition to provider-side glossary support.
    for term in glossary.get("preserve", []):
        if term in text and term not in result:
            raise ValueError("Glossary term changed: " + term)
    return result

def translate(records, locale: str):
    cache_path = WORK / "cache" / (locale + ".json")
    cache = read_json(cache_path, {})
    glossary = read_json(WORK / "glossary.json", {})
    key = os.getenv("DEEPL_API_KEY")
    missing = []
    for name, value in units(records):
        digest = fingerprint(value)
        if cache.get(name, {}).get("source_sha256") == digest:
            continue
        if not key:
            missing.append(name)
            continue
        translated = translate_text(value, locale, key, glossary)
        cache[name] = {"source_sha256": digest, "text": translated, "review": "machine_pending_human_review"}
        write_json(cache_path, cache)
    return cache, missing

def android_escape(text: str):
    return text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')

def generate(records, locale, cache, require_review=False):
    root = ET.Element("resources")
    for name, record in records.items():
        attrs = {"name": name}
        target = ET.SubElement(root, record["type"], attrs)
        if record["type"] == "string":
            target.text = android_escape(cache[name]["text"])
        else:
            for quantity in record["items"]:
                attrs = {"quantity": quantity} if record["type"] == "plurals" else {}
                ET.SubElement(target, "item", attrs).text = android_escape(cache[f"{name}:{quantity}"]["text"])
    ET.indent(root, space="    ")
    directory = RES / ("values-b+" + locale.replace("-", "+"))
    directory.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(directory / "strings.xml", encoding="utf-8", xml_declaration=True)

def validate(records):
    source_units = dict(units(records))
    report = {"source_units": len(source_units), "locales": {}, "release_ready": False}
    for locale in LOCALES:
        cache = read_json(WORK / "cache" / (locale + ".json"), {})
        review = read_json(WORK / "reviews" / (locale + ".json"), {})
        missing, errors, pending_review = [], [], []
        for name, value in source_units.items():
            entry = cache.get(name)
            if not entry or entry.get("source_sha256") != fingerprint(value):
                missing.append(name)
                continue
            if placeholders(value) != placeholders(entry.get("text", "")):
                errors.append(name + ": placeholder mismatch")
            if entry.get("review") != "human_approved":
                pending_review.append(name)
        # Source plural structures are validated during extraction. Locale plural rules
        # (especially Arabic/Polish) need a reviewer to add all required quantities.
        plural_review = [n for n, r in records.items() if r["type"] == "plurals" and n not in review.get("plural_rules_approved", [])]
        regional_review = locale in {"en-AU", "es-419", "fr-CA"} and not review.get("regional_approved", False)
        report["locales"][locale] = {"missing": missing, "errors": errors,
            "pending_human_review": pending_review, "plural_rule_review": plural_review,
            "regional_review": regional_review,
            "status": "pending" if missing else "review_required" if pending_review or errors or plural_review or regional_review else "approved"}
    report["release_ready"] = bool(source_units) and all(r["status"] == "approved" for r in report["locales"].values())
    write_json(WORK / "quality-report.json", report)
    return report

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["extract", "translate", "validate", "generate"])
    parser.add_argument("--source", type=Path, default=RES / "values" / "strings.xml")
    parser.add_argument("--locale", choices=list(LOCALES))
    args = parser.parse_args()
    try:
        records = extract(args.source)
        if args.command in {"translate", "generate"}:
            if not args.locale:
                parser.error("--locale is required")
            cache, missing = translate(records, args.locale)
            if args.command == "generate":
                if missing:
                    raise ValueError("Generation blocked: missing translations; English fallbacks are not published as translated UI")
                generate(records, args.locale, cache)
        report = validate(records)
        print(json.dumps({"source_units": report["source_units"], "release_ready": report["release_ready"],
            "report": str(WORK / "quality-report.json")}, indent=2))
        return 1 if args.command == "validate" and not report["release_ready"] else 0
    except (ValueError, ET.ParseError, OSError, KeyError) as error:
        # Do not print remote error bodies; they could contain sensitive data.
        print("Localisation failed: " + str(error), file=sys.stderr)
        return 2

if __name__ == "__main__":
    raise SystemExit(main())
