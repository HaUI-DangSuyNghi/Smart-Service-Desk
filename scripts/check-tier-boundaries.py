"""Strict cross-language tier boundaries. Migration exceptions have been removed."""
import ast
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def java_strings(source):
    tokens = r'(?P<comment>//[^\n]*|/\*[\s\S]*?\*/)|(?P<block>"""[\s\S]*?""")|(?P<string>"(?:\\.|[^"\\])*")'
    for match in re.finditer(tokens, source):
        if match.group('block'):
            yield match.group('block')[3:-3]
        elif match.group('string'):
            try:
                yield json.loads(match.group('string'))
            except ValueError:
                yield match.group('string')[1:-1]


def violations(root=ROOT):
    errors = []
    profiles = {'service-desk'}
    for registry in (root / 'apps').rglob('registry.yaml'):
        profiles.update(re.findall(r'''^\s+["']?([a-z][a-z0-9-]+)@[^:]+:''', registry.read_text('utf-8'), re.M))
    process_keys = set()
    for process in (root / 'backend/src/main/resources/apps').rglob('*.bpmn20.xml'):
        document = ET.parse(process)
        process_keys.update(p.attrib['id'] for p in document.findall('{http://www.omg.org/spec/BPMN/20100524/MODEL}process'))

    def report(path, kind, value):
        key = f'{path.relative_to(root).as_posix()}:{kind}:{value}'
        errors.append(key)

    for path in (root / 'backend/src/main/java/vn/haui/dx').rglob('*.java'):
        source = path.read_text('utf-8')
        tier = path.relative_to(root / 'backend/src/main/java/vn/haui/dx').parts[0]
        for imported in re.findall(r'^import\s+(?:static\s+)?([\w.*]+);', source, re.M):
            if (tier == 'core' and imported.startswith(('vn.haui.dx.hpdi.', 'vn.haui.dx.apps.'))
                    or tier == 'hpdi' and imported.startswith('vn.haui.dx.apps.')
                    or tier in ('hpdi', 'apps') and re.match(r'vn\.haui\.dx\.core\.[^.]+\.internal\.', imported)
                    or imported.startswith('org.flowable.') and 'core/workflow/internal/' not in path.as_posix()):
                errors.append(f'{path}: forbidden import {imported}')
            if tier == 'apps' and imported.startswith('vn.haui.dx.core.'):
                report(path, 'core-import', imported)
            if tier == 'hpdi' and imported.startswith('vn.haui.dx.core.') and not re.match(r'vn\.haui\.dx\.core\.(?:\w+\.api|common)\.', imported):
                report(path, 'nonpublic-core-import', imported)
            if tier == 'apps' and imported.startswith('vn.haui.dx.hpdi.') and not re.match(r'vn\.haui\.dx\.hpdi\.\w+\.api\.', imported):
                report(path, 'nonpublic-hpdi-import', imported)
        literals = list(java_strings(source))
        if tier == 'core':
            for value in literals:
                if value in process_keys:
                    report(path, 'domain-process-key', value)
        if tier == 'apps':
            app = path.relative_to(root / 'backend/src/main/java/vn/haui/dx/apps').parts[0]
            identifier = r'(?:"[a-z_][\w$]*"|[a-z_][\w$]*)'
            for sql in literals:
                if not re.match(r'\s*(?:select|insert|update|delete|with)\b', sql, re.I):
                    continue
                for match in re.finditer(rf'\b(?:from|join|into|update)\s+({identifier}(?:\s*\.\s*{identifier})?)', sql, re.I):
                    table = match.group(1).replace('"', '').replace(' ', '').lower()
                    if '.' in table or not table.startswith(app + '_'):
                        report(path, 'internal-sql', table)
    for path in (root / 'intelligence/src').rglob('*.py'):
        for node in ast.walk(ast.parse(path.read_text('utf-8'))):
            if isinstance(node, ast.Constant) and isinstance(node.value, str) and any(profile in node.value.lower().replace('_', '-') for profile in profiles):
                report(path, 'app-literal', node.value)
            if isinstance(node, (ast.Import, ast.ImportFrom)):
                names = [a.name for a in node.names] if isinstance(node, ast.Import) else [node.module or '']
                if any(name == 'apps' or name.startswith('apps.') for name in names):
                    errors.append(f'{path}: app import in runtime')
    for path in (root / 'frontend/src/platform').rglob('*'):
        if path.suffix in ('.js', '.jsx', '.ts', '.tsx', '.mjs'):
            specifiers = re.findall(r'''(?:\bfrom\s*|\bimport\s*\(?\s*|\brequire\s*\(\s*)['"]([^'"]+)['"]''', path.read_text('utf-8'))
            for specifier in specifiers:
                outside = specifier.startswith('.') and not (path.parent / specifier).resolve().is_relative_to((root / 'frontend/src/platform').resolve())
                if outside or re.search(r'(?:^|/|@)apps(?:/|$)', specifier):
                    errors.append(f'{path}: platform imports apps or escapes platform: {specifier}')
    return errors


if __name__ == '__main__':
    failures = violations()
    if failures:
        raise SystemExit('\n'.join(failures))
    print('PASS tier boundaries (Java imports/SQL, Python AST, frontend imports)')
