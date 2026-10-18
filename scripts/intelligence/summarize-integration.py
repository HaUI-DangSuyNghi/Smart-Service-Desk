"""Record technical evidence separately from security and business release approval."""

import json
import os
from pathlib import Path
import subprocess
import tomllib
import xml.etree.ElementTree as ET


def load_json(path):
    return json.loads(path.read_text()) if path.is_file() else None


def junit_pass(path):
    if not path.is_file():
        return False
    suites = list(ET.parse(path).getroot().iter('testsuite'))
    return bool(suites) and sum(int(suite.get('tests', 0)) for suite in suites) > 0 and all(
        int(suite.get(key, 0)) == 0 for suite in suites for key in ('failures', 'errors', 'skipped'))


def main():
    root = Path(__file__).resolve().parents[2]
    directory = root / '.local/int01'
    directory.mkdir(parents=True, exist_ok=True)
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    java = load_json(directory / 'java-regression.json')
    transport = load_json(root / '.local/ai04-e2e/summary.json')
    gateway = load_json(directory / 'gateway-triage.json')
    retrieval = load_json(directory / 'authorized-retrieval.json')
    images = load_json(directory / 'images.json')
    package_version = tomllib.loads((root / 'intelligence/pyproject.toml').read_text())['project']['version']
    image_version = (directory / 'image-package-version.txt').read_text().strip() if (directory / 'image-package-version.txt').is_file() else None
    correlated = transport is not None and transport.get('javaHead') == transport.get('pythonHead') == head
    scenarios = {entry['scenario']: entry for entry in transport.get('results', [])} if transport else {}

    def scenario_pass(name):
        entry = scenarios.get(name)
        return correlated and entry is not None and entry['tests'] > 0 and not any(
            entry[key] for key in ('failures', 'errors', 'skipped'))

    scans = list((root / '.local').glob('scan-int01-*/summary.json'))
    security = load_json(scans[0]) if len(scans) == 1 else None
    scan_complete = security is not None and len(security) == 5 and all(entry['scan'] == 'PASS' for entry in security)
    production_security = scan_complete and all(entry['productionGate'] == 'PASS' for entry in security)
    clean = not subprocess.check_output(['git', 'status', '--porcelain'], cwd=root, text=True).strip()
    checkout = (directory / 'source-commit.txt').read_text().strip() if (directory / 'source-commit.txt').is_file() else None
    gates = {
        'G1': 'PASS' if java and java['tests'] and not any(java[key] for key in ('failures', 'errors', 'skipped')) else 'BLOCKED',
        'G2': 'PASS' if all(junit_pass(directory / name) for name in ('python-source.xml', 'python-wheel.xml')) else 'BLOCKED',
        'G3': 'PASS' if image_version == package_version and images and all(image['revision'] == head for image in images) else 'BLOCKED',
        'G4': 'PASS' if scenario_pass('success') else 'BLOCKED',
        'G5': 'PASS' if all(scenario_pass(name) for name in ('wrong-token', 'unavailable', 'timeout', 'malformed')) else 'BLOCKED',
        'G6': 'PASS' if gateway and gateway['sourceCommit'] == head and 'real-python-outage-503-and-manual-workflow-completion' in gateway['cases'] else 'BLOCKED',
        'G7': 'PASS' if gateway and gateway['sourceCommit'] == head and gateway['status'] == 'PASS' else 'BLOCKED',
        'G8': 'PASS' if retrieval and retrieval['aiCommit'] == head and retrieval['status'] == 'PASS_WITH_DOCUMENTED_CORE_LIMITS' else 'BLOCKED',
        'G9': 'PASS' if production_security else 'FAIL' if scan_complete else 'BLOCKED',
        'G10': 'PASS' if clean and checkout == head and images and image_version == package_version else 'BLOCKED',
    }
    technical = all(status == 'PASS' for gate, status in gates.items() if gate != 'G9')
    summary = {'sourceCommit': head, 'javaHead': transport.get('javaHead') if transport else None,
               'pythonHead': transport.get('pythonHead') if transport else None, 'packageVersion': package_version,
               'gates': gates, 'technicalAcceptance': 'PASS' if technical else 'FAIL',
               'imageScanExecution': 'PASS' if scan_complete else 'BLOCKED',
               'productionSecurity': 'PASS' if production_security else 'FAIL' if scan_complete else 'BLOCKED',
               'businessProfileApproval': 'PENDING', 'licenseGrant': 'PENDING', 'fixtureDataRights': 'PENDING',
               'browserUiE2E': 'NOT RUN', 'releaseApproval': 'BLOCKED',
               'faultInjection': 'Timeout/malformed after real Python SUCCESS; not natural Python failures',
               'scanFindings': security, 'javaRegression': java}
    (directory / 'integration-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps({key: summary[key] for key in ('sourceCommit', 'gates', 'technicalAcceptance', 'productionSecurity', 'releaseApproval')}, indent=2))
    github_summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if github_summary:
        with open(github_summary, 'a', encoding='utf-8') as stream:
            stream.write(f'## INT-01 {head}\n\n| Gate | Result |\n| --- | --- |\n')
            stream.writelines(f'| {gate} | {status} |\n' for gate, status in gates.items())
            stream.write('\nSecurity scan execution and production vulnerability approval are separate. '
                         'G0-B, formal LICENSE and fixture rights remain pending. Browser UI E2E not run.\n')
    if not technical or not scan_complete:
        raise SystemExit('Integration evidence incomplete; inspect integration-summary.json')


if __name__ == '__main__':
    main()
