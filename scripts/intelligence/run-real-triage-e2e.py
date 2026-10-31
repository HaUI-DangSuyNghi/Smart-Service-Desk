"""Run current integrated Java and Python from the same clean revision.

Timeout/malformed cases inject faults AFTER a real successful Python response;
they are fault-injected integration, not normal production Python behavior.
"""

import argparse
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
from threading import Thread
from time import sleep
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET

PACKAGE = 'vn.haui.dx.apps.servicedesk.triage.'
TEST_NAME = 'AI04TicketTriagePythonE2E'


@contextmanager
def injected_fault(upstream, mode):
    successes = []

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_POST(self):
            body = self.rfile.read(int(self.headers['Content-Length']))
            request = Request(upstream + self.path, body, method='POST', headers={
                'Content-Type': 'application/json', 'Authorization': self.headers['Authorization']})
            with urlopen(request, timeout=3) as response:
                result = json.load(response)
            assert result['status'] == 'SUCCESS'
            assert result['runtime']['profileVersion'] == 'service-desk@0.3.0'
            successes.append(True)
            if mode == 'timeout':
                sleep(5)
            else:
                result = {'unexpected': 'synthetic-malformed-response'}
            encoded = json.dumps(result).encode('utf-8')
            try:
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(encoded)))
                self.end_headers()
                self.wfile.write(encoded)
            except OSError:
                pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f'http://127.0.0.1:{server.server_port}'
        assert len(successes) == 1, 'Fault injection must follow exactly one real Python SUCCESS'
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-root', required=True, type=Path)
    parser.add_argument('--maven', default='mvn')
    args = parser.parse_args()
    root = args.java_root.resolve()
    for name in ('FILE_TEST_JDBC_URL', 'FILE_TEST_DB_PASSWORD', 'INTELLIGENCE_TRIAGE_BASE_URL', 'INTELLIGENCE_TRIAGE_TOKEN'):
        if not os.environ.get(name):
            parser.error('Missing setting: ' + name)
    if not os.environ['FILE_TEST_JDBC_URL'].endswith('/file_storage'):
        parser.error('Use a newly provisioned disposable file_storage database')
    head = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
    source_head = subprocess.check_output(['git', 'rev-parse', 'HEAD'],
                                          cwd=Path(__file__).resolve().parents[2], text=True).strip()
    assert head == source_head, 'Java and Python must use the same combined revision'
    assert not subprocess.check_output(['git', '-C', str(root), 'status', '--porcelain'], text=True).strip(), 'Clean disposable Java checkout required'
    source = Path(__file__).with_name(TEST_NAME + '.java')
    target = root / 'backend/src/test/java/vn/haui/dx/apps/servicedesk/triage' / source.name
    assert not target.exists(), 'Do not overwrite an existing test'
    output = Path(__file__).resolve().parents[2] / '.local/ai04-e2e'
    output.mkdir(parents=True, exist_ok=True)
    upstream = os.environ['INTELLIGENCE_TRIAGE_BASE_URL'].rstrip('/')
    results = []

    def run(mode, base_url, token):
        environment = {**os.environ, 'AI04_SCENARIO': mode,
                       'INTELLIGENCE_TRIAGE_BASE_URL': base_url, 'INTELLIGENCE_TRIAGE_TOKEN': token}
        tests = TEST_NAME + (',TicketTriagePythonE2E' if mode == 'success' else '')
        command = [args.maven, '-B', '-f', str(root / 'backend/pom.xml'), '-Dtest=' + tests, 'test']
        with (output / (mode + '.log')).open('w', encoding='utf-8') as log:
            completed = subprocess.run(command, env=environment, stdout=log, stderr=subprocess.STDOUT)
        if completed.returncode:
            for test in tests.split(','):
                report = root / 'backend/target/surefire-reports' / ('TEST-' + PACKAGE + test + '.xml')
                if report.exists():
                    for case in ET.parse(report).getroot().findall('testcase'):
                        for kind in ('failure', 'error'):
                            issue = case.find(kind)
                            if issue is not None:
                                print(json.dumps({'scenario': mode, 'test': case.get('name'),
                                                  'result': kind, 'type': issue.get('type')}), flush=True)
            raise AssertionError(f'{mode} failed; inspect local {mode}.log')
        count = 0
        for test in tests.split(','):
            report = root / 'backend/target/surefire-reports' / ('TEST-' + PACKAGE + test + '.xml')
            suite = ET.parse(report).getroot()
            assert int(suite.get('tests', 0)) > 0
            assert all(int(suite.get(key, 0)) == 0 for key in ('failures', 'errors', 'skipped'))
            count += int(suite.get('tests'))
        results.append({'scenario': mode, 'tests': count, 'failures': 0, 'errors': 0, 'skipped': 0,
                        'faultInjectedAfterRealPythonSuccess': mode in ('timeout', 'malformed')})
        print(f'PASS {mode}: {count} Java tests; authorization, no mutation and manual handling verified', flush=True)

    shutil.copyfile(source, target)
    try:
        run('success', upstream, os.environ['INTELLIGENCE_TRIAGE_TOKEN'])
        run('wrong-token', upstream, 'synthetic-deliberately-wrong-service-token')
        with socket.socket() as unavailable:
            unavailable.bind(('127.0.0.1', 0))
            run('unavailable', f'http://127.0.0.1:{unavailable.getsockname()[1]}', os.environ['INTELLIGENCE_TRIAGE_TOKEN'])
        for mode in ('timeout', 'malformed'):
            with injected_fault(upstream, mode) as base_url:
                run(mode, base_url, os.environ['INTELLIGENCE_TRIAGE_TOKEN'])
    finally:
        target.unlink()
        summary = {'javaHead': head, 'pythonHead': source_head,
            'identity': 'MockMvc JWT fixtures; NOT Keycloak/gateway/browser E2E', 'results': results}
        (output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
