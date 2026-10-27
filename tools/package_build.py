#!/usr/bin/env python3
"""Package actual build outputs, rejecting failed test/lint reports."""
from pathlib import Path
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"deliverables"

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    lint=ROOT/"app/build/reports/lint-results-debug.xml"
    issues=ET.parse(lint).getroot().findall("issue")
    assert not any(x.get("severity") in {"Error","Fatal"} for x in issues), "Fix lint errors first"
    tests=[]
    for folder in ["core/build/test-results/test", "app/build/test-results/testDebugUnitTest"]:
        reports=list((ROOT/folder).glob("TEST-*.xml")); assert reports, "Missing JVM test results"
        for file in reports:
            suite=ET.parse(file).getroot()
            assert int(suite.get("failures","0")) == int(suite.get("errors","0")) == 0
            tests.append({"suite":suite.get("name"),"tests":int(suite.get("tests","0")),"failures":0})
    outputs=[("app/build/outputs/apk/debug/app-debug.apk","TidyTroupe-0.1.0-debug.apk"),
             ("app/build/outputs/apk/release/app-release-unsigned.apk","TidyTroupe-0.1.0-release-UNSIGNED.apk"),
             ("app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk","TidyTroupe-0.1.0-instrumentation-tests.apk")]
    OUT.mkdir(exist_ok=True)
    packages=[]
    for source,name in outputs:
        src=ROOT/source; assert src.is_file(), "Missing APK: "+source
        dest=OUT/name; shutil.copy2(src,dest)
        packages.append({"file":name,"bytes":dest.stat().st_size,"sha256":sha(dest)})
    reports=OUT/"reports"; reports.mkdir(exist_ok=True)
    shutil.copy2(lint,reports/lint.name)
    shutil.copy2(ROOT/"app/build/reports/lint-results-debug.html", reports/"lint-results-debug.html")
    manifest={"version":"0.1.0","date":"2026-10-27","status":"development_not_submission_ready",
              "packages":packages,"jvm_tests":tests,"lint_errors":0,
              "lint_warnings":sum(x.get("severity")=="Warning" for x in issues),
              "python_tests":{"localisation":4,"receipt_verifier":5},
              "device_tests":"not_run_no_booted_device","screenshots":"not_captured",
              "translations":"pending_credentials_and_review","live_purchasing":"not_configured_or_tested"}
    (OUT/"manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
    archive=OUT/"TidyTroupe-0.1.0-source.zip"
    excluded={".git",".gradle",".kotlin","build","deliverables","__pycache__",".venv","node_modules"}
    with zipfile.ZipFile(archive,"w",zipfile.ZIP_DEFLATED) as z:
        for file in sorted(ROOT.rglob("*")):
            relative=file.relative_to(ROOT)
            if not file.is_file() or excluded.intersection(relative.parts): continue
            if file.name in {"local.properties",".env"} or file.suffix in {".jks",".keystore",".pyc",".pem"}: continue
            z.write(file,relative.as_posix())
    checks=[(x["sha256"],x["file"]) for x in packages]+[(sha(archive),archive.name)]
    (OUT/"SHA256SUMS.txt").write_text("\n".join(h+"  "+n for h,n in checks)+"\n",encoding="utf-8")
    print(json.dumps({"directory":str(OUT),"apk_count":len(packages),"source_bytes":archive.stat().st_size,"lint_errors":0,"jvm_tests":sum(x["tests"] for x in tests)},indent=2))

if __name__=="__main__": main()
