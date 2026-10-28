"""Audit historical R3 Surefire skips against the exact develop baseline."""
import argparse
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


def audit(reports, baseline):
    rows = []
    for report in sorted(reports.glob("TEST-*.xml")):
        for case in ET.parse(report).getroot().iter("testcase"):
            skipped = case.find("skipped")
            if skipped is None:
                continue
            classname, method = case.get("classname"), case.get("name")
            source = "backend/src/test/java/" + classname.replace(".", "/") + ".java"
            previous = subprocess.run(["git", "show", baseline + ":" + source],
                                      capture_output=True, text=True, check=True).stdout
            baseline_condition = ('@EnabledIfEnvironmentVariable(named="BACKGROUND_TEST_JDBC_URL",'
                                  'matches=".+/background_assessment")')
            existed = method + "(" in previous and baseline_condition in previous
            rows.append(dict(testClass=classname, method=method, reason=skipped.get("message"),
                             origin="baseline" if existed else "REQUIRES_REVIEW",
                             affected="Service Desk background assessment and worker security; shared service identity regression coverage",
                             directR3=False, directCore=False, directRoomRequest=False))
    return rows


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", type=Path)
    parser.add_argument("--baseline", default="a6a92845c48eeefa86e5e261e382dbd096160cdb")
    arguments = parser.parse_args()
    print(json.dumps(dict(baseline=arguments.baseline, skipped=audit(arguments.reports, arguments.baseline)), indent=2))
