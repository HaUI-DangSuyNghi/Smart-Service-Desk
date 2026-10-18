"""Export HIGH/CRITICAL package occurrences without deduplicating scanner findings."""

import argparse
import csv
import json
from pathlib import Path
from tempfile import TemporaryDirectory


def export_inventory(directory, output):
    summary = json.loads((directory / 'summary.json').read_text(encoding='utf-8-sig'))
    rows = []
    for index, image in enumerate(summary):
        if image['scan'] != 'PASS':
            raise ValueError('Cannot inventory an unsuccessful scan')
        report = json.loads((directory / f'image-{index}.json').read_text(encoding='utf-8-sig'))
        if report['Metadata']['ImageID'] != image['imageId']:
            raise ValueError('Image identity does not match summary')
        counts = {'HIGH': 0, 'CRITICAL': 0}
        for result in report['Results']:
            for finding in result.get('Vulnerabilities') or []:
                if finding['Severity'] not in counts:
                    continue
                counts[finding['Severity']] += 1
                rows.append({
                    'image': image['image'], 'image_id': image['imageId'],
                    'repo_digests': json.dumps(image.get('digests', [])),
                    'target': result['Target'], 'class': result['Class'], 'type': result['Type'],
                    'cve': finding['VulnerabilityID'], 'severity': finding['Severity'],
                    'package': finding['PkgName'], 'package_path': finding.get('PkgPath', ''),
                    'installed': finding['InstalledVersion'],
                    'fixed': finding.get('FixedVersion', ''), 'status': finding.get('Status', ''),
                    'title': finding.get('Title', ''), 'primary_url': finding.get('PrimaryURL', ''),
                    'data_source': json.dumps(finding.get('DataSource', {})),
                    'vendor_severity': json.dumps(finding.get('VendorSeverity', {})),
                    'references': json.dumps(finding.get('References', [])),
                })
        if counts != {'HIGH': image['high'], 'CRITICAL': image['critical']}:
            raise ValueError('Finding counts do not match summary')
    if not rows:
        raise ValueError('No HIGH/CRITICAL occurrences; use the full scan as clean evidence')
    with output.open('w', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    return len(rows)


def self_test():
    with TemporaryDirectory() as temporary:
        directory = Path(temporary)
        summary = [{'scan': 'PASS', 'image': 'test:ci', 'imageId': 'sha256:test',
                    'high': 2, 'critical': 0}]
        finding = {'Severity': 'HIGH', 'VulnerabilityID': 'CVE-test',
                   'PkgName': 'test', 'InstalledVersion': '1'}
        report = {'Metadata': {'ImageID': 'sha256:test'}, 'Results': [
            {'Target': 'test', 'Class': 'lang-pkgs', 'Type': 'jar',
             'Vulnerabilities': [finding, finding]}]}
        (directory / 'summary.json').write_text(json.dumps(summary), encoding='utf-8')
        (directory / 'image-0.json').write_text(json.dumps(report), encoding='utf-8')
        output = directory / 'inventory.csv'
        assert export_inventory(directory, output) == 2
        with output.open(encoding='utf-8', newline='') as stream:
            assert len(list(csv.DictReader(stream))) == 2
        report['Metadata']['ImageID'] = 'sha256:wrong'
        (directory / 'image-0.json').write_text(json.dumps(report), encoding='utf-8')
        try:
            export_inventory(directory, output)
        except ValueError:
            pass
        else:
            raise AssertionError('Mismatched image identity must fail')
    print('PASS inventory export, duplicate occurrences and identity validation')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path, nargs='?')
    parser.add_argument('output', type=Path, nargs='?')
    parser.add_argument('--self-test', action='store_true')
    arguments = parser.parse_args()
    if arguments.self_test:
        self_test()
    elif arguments.directory is None or arguments.output is None:
        parser.error('directory and output are required unless --self-test is used')
    else:
        print(f'Exported {export_inventory(arguments.directory, arguments.output)} occurrences')
