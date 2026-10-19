"""BND-00 source gate: exact, expiring debt; no new or stale exceptions."""
import argparse
import ast
from collections import Counter
from datetime import date
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASELINE = Path('docs/architecture/boundary-debt.json')
JAVA = Path('backend/src/main/java/vn/haui/dx')
RESOURCES = Path('backend/src/main/resources')
PHASES = {f'BND-{number:02}' for number in range(1, 8)}


def java_strings(source):
    """Read literals without treating SQL examples in comments as production SQL."""
    pattern = r'(?P<comment>//[^\n]*|/\*[\s\S]*?\*/)|(?P<block>"""[\s\S]*?""")|(?P<string>"(?:\\.|[^"\\])*")'
    for match in re.finditer(pattern, source):
        if match.group('block'):
            yield match.group('block')[3:-3]
        elif match.group('string'):
            try:
                yield json.loads(match.group('string'))
            except ValueError:
                yield match.group('string')[1:-1]


def violations(root=ROOT):
    observed = Counter()

    def report(path, rule, value):
        observed[(path.relative_to(root).as_posix(), rule, value)] += 1

    profiles = {'service-desk'}
    for registry in list((root / 'intelligence/src/apps').rglob('registry.yaml')) + list((root / 'intelligence/profiles').glob('registry.yaml')):
        profiles.update(re.findall(r'''^\s+["']?([a-z][a-z0-9-]+)@[^:]+:''', registry.read_text('utf-8'), re.M))
    process_keys = set()
    for path in (root / RESOURCES).rglob('*.bpmn20.xml'):
        document = ET.parse(path)
        process_keys.update(node.attrib['id'] for node in document.findall('{http://www.omg.org/spec/BPMN/20100524/MODEL}process'))
        if 'apps' not in path.relative_to(root / RESOURCES).parts:
            report(path, 'app-process-resource', path.name)

    identifier = r'(?:"[a-z_][\w$]*"|[a-z_][\w$]*)'
    app_names = set()
    for directory in (root / JAVA / 'apps', root / 'intelligence/src/apps', root / RESOURCES / 'apps', root / RESOURCES / 'db/migration/apps'):
        if directory.is_dir():
            app_names.update(path.name.lower() for path in directory.iterdir() if path.is_dir())
    core_tables = {'process_scope', 'metadata', 'metadata_record', 'file_object', 'file_link', 'audit_event', 'workspace_membership'}

    def private_platform_table(name):
        return name in core_tables or name.startswith(('act_', 'core_', 'hpdi_'))

    def table_owner(name):
        name = name.split('.')[-1]
        if name.startswith('hpdi_'):
            return 'hpdi'
        if private_platform_table(name):
            return 'core'
        for app in sorted(app_names, key=len, reverse=True):
            if name.startswith(app + '_'):
                return 'app:' + app
        return 'undeclared:' + name.split('_')[0]

    def app_sql(path, sql, app, ctes=()):
        ctes = set(ctes) | {name.lower() for name in re.findall(r'\b(?:with|,)\s*([a-z_]\w*)\s+as\s*\(', sql, re.I)}
        for match in re.finditer(rf'\b(?:from|join|into|update|references)\s+({identifier}(?:\s*\.\s*{identifier})?)', sql, re.I):
            table = re.sub(r'["\s]', '', match.group(1)).lower()
            if table != 'lateral' and table not in ctes and (private_platform_table(table) or '.' in table or not table.startswith(app + '_')):
                report(path, 'app-private-sql', table)

    for path in (root / JAVA).rglob('*.java'):
        source = path.read_text('utf-8')
        parts = path.relative_to(root / JAVA).parts
        tier = parts[0]
        # Compiled dependencies, including fully qualified references, are also
        # checked by TierBoundaryTest. This guard provides quick source feedback.
        imports_source = re.sub(r'/\*[\s\S]*?\*/|//[^\n]*', '', source)
        for imported in re.findall(r'^\s*import\s+(?:static\s+)?([\w.*]+);', imports_source, re.M):
            imported = imported.removesuffix('.*')
            rule = None
            if tier == 'core' and imported.startswith(('vn.haui.dx.hpdi.', 'vn.haui.dx.apps.', 'vn.haui.dx.assembly.')):
                rule = 'core-upper-import'
            elif tier == 'hpdi' and imported.startswith('vn.haui.dx.apps.'):
                rule = 'hpdi-app-import'
            elif tier in ('hpdi', 'apps') and imported.startswith('vn.haui.dx.assembly.'):
                rule = tier + '-assembly-import'
            elif tier == 'apps' and imported.startswith('vn.haui.dx.core.'):
                rule = 'app-core-import'
            elif tier == 'hpdi' and imported.startswith('vn.haui.dx.core.'):
                if 'api' in parts[1:-1] or not re.match(r'vn\.haui\.dx\.core\.(?:\w+\.api|common)(?:\.|$)', imported):
                    rule = 'hpdi-private-core-import'
            elif tier == 'apps' and imported.startswith('vn.haui.dx.hpdi.') and not re.match(r'vn\.haui\.dx\.hpdi\.\w+\.api(?:\.|$)', imported):
                rule = 'app-private-hpdi-import'
            if rule:
                report(path, rule, imported)
            if imported.startswith('org.flowable.') and parts[:3] != ('core', 'workflow', 'internal'):
                report(path, 'flowable-outside-internal', imported)
            if tier == 'apps' and imported.startswith(('java.net.http.', 'org.springframework.web.client.', 'com.networknt.schema.')):
                report(path, 'app-transport-import', imported)
        literals = list(java_strings(source))
        ctes = re.findall(r'\b(?:with|,)\s*([a-z_]\w*)\s+as\s*\(', '\n'.join(literals), re.I)
        for literal in literals:
            if tier == 'core' and literal in process_keys:
                report(path, 'core-domain-process-key', literal)
            if tier == 'hpdi' and (literal in process_keys or any(profile in literal.lower().replace('_', '-') for profile in profiles)):
                report(path, 'hpdi-domain-literal', literal)
            if tier == 'apps' and re.search(r'\b(?:select|insert\s+into|update|delete\s+from|join|references)\b', literal, re.I):
                app_sql(path, literal, parts[1], ctes)

    for path in (root / RESOURCES / 'apps').rglob('*.sql'):
        app_sql(path, path.read_text('utf-8'), path.relative_to(root / RESOURCES / 'apps').parts[0])
    # Historical migrations stay immutable, but their cross-owner FK is debt.
    foreign_keys = {}
    migrations = sorted((root / RESOURCES).rglob('*.sql'), key=lambda path: (
        tuple(int(part) for part in re.findall(r'\d+', path.name.split('__')[0])), path.as_posix()))
    for path in migrations:
        source = re.sub(r'--[^\n]*|/\*[\s\S]*?\*/', '', path.read_text('utf-8'))
        for statement in source.split(';'):
            owner = re.search(rf'\b(?:create\s+table(?:\s+if\s+not\s+exists)?|alter\s+table)\s+({identifier}(?:\s*\.\s*{identifier})?)', statement, re.I)
            if not owner:
                continue
            table = re.sub(r'["\s]', '', owner.group(1)).lower()
            dropped = re.search(r'\bdrop\s+constraint\s+(?:if\s+exists\s+)?([\w"]+)', statement, re.I)
            if dropped:
                foreign_keys.pop((table, dropped.group(1).strip('"').lower()), None)
            for reference in re.finditer(rf'\breferences\s+({identifier}(?:\s*\.\s*{identifier})?)', statement, re.I):
                target = re.sub(r'["\s]', '', reference.group(1)).lower()
                if table_owner(table) != table_owner(target):
                    prefix = statement[:reference.start()]
                    named = re.search(r'\bconstraint\s+([\w"]+)\s+[^,]*$', prefix, re.I)
                    explicit = re.search(r'\bforeign\s+key\s*\(\s*([\w"]+)\s*\)\s*$', prefix, re.I)
                    column = explicit.group(1).strip('"') if explicit else prefix.strip().splitlines()[-1].strip().split()[0].strip('"')
                    constraint = named.group(1).strip('"') if named else table.split('.')[-1] + '_' + column + '_fkey'
                    foreign_keys[(table, constraint.lower())] = (path, table + ' -> ' + target)
    for path, value in foreign_keys.values():
        report(path, 'cross-owner-fk', value)

    for path in (root / 'intelligence/src').rglob('*.py'):
        if path.is_relative_to(root / 'intelligence/src/apps'):
            continue  # Tier-3 policy lives beside, never inside, the generic runtime.
        for node in ast.walk(ast.parse(path.read_text('utf-8'), filename=str(path))):
            if isinstance(node, ast.Constant) and isinstance(node.value, str):
                if any(profile in node.value.lower().replace('_', '-') for profile in profiles):
                    report(path, 'python-app-literal', node.value)
            if isinstance(node, (ast.Import, ast.ImportFrom)):
                names = [alias.name for alias in node.names] if isinstance(node, ast.Import) else [node.module or '']
                for name in names:
                    if name == 'apps' or name.startswith('apps.'):
                        report(path, 'python-app-import', name)
    for path in (root / 'intelligence/profiles').rglob('*'):
        if path.is_file() and path.suffix in ('.yaml', '.json', '.md'):
            report(path, 'bundled-app-profile', path.relative_to(root / 'intelligence/profiles').as_posix())
    for path in (root / 'intelligence/src/hdpi/_resources/profiles').rglob('*'):
        if path.is_file():
            report(path, 'bundled-app-profile', path.relative_to(root / 'intelligence/src/hdpi').as_posix())
    for path in (root / 'frontend/src/platform').rglob('*'):
        if path.suffix in ('.js', '.jsx', '.ts', '.tsx', '.mjs'):
            for specifier in re.findall(r'''(?:\bfrom\s*|\bimport\s*\(?\s*|\brequire\s*\(\s*)['"]([^'"]+)['"]''', path.read_text('utf-8')):
                outside = specifier.startswith('.') and not (path.parent / specifier).resolve().is_relative_to((root / 'frontend/src/platform').resolve())
                if outside or re.search(r'(?:^|/|@)apps(?:/|$)', specifier):
                    report(path, 'platform-app-import', specifier)
    for path in list((root / 'contracts/openapi').glob('*.yaml')) + list((root / 'contracts/apps').rglob('*.yaml')):
        source = path.read_text('utf-8')
        if path.is_relative_to(root / 'contracts/apps') or '/api/apps/' in source:
            for dto in re.findall(r'core\.yaml#/components/schemas/(\w+)', source):
                report(path, 'app-core-contract-dto', dto)
    return observed


def validate_baseline(baseline, today=None):
    today = today or date.today()
    errors, ids = [], set()
    if baseline.get('schemaVersion') != 1:
        errors.append('Unsupported boundary baseline schemaVersion')
    if baseline.get('mode', 'recovery') not in ('recovery', 'strict'):
        errors.append('Invalid boundary enforcement mode')
    if baseline.get('mode') == 'strict' and (baseline.get('source') or baseline.get('java')):
        errors.append('Strict mode permits no migration exceptions')
    for section in ('source', 'java'):
        entries = baseline.get(section)
        if not isinstance(entries, list):
            errors.append(f'Missing {section} debt inventory')
            continue
        keys = set()
        for entry in entries:
            key = tuple(entry.get(field, '') for field in ('path', 'rule', 'value'))
            identity = entry.get('id', '<missing id>')
            if any(not isinstance(entry.get(field), str) or not entry[field].strip() for field in ('id', 'path', 'rule', 'value', 'owner', 'targetPhase', 'expires')):
                errors.append(f'Incomplete debt: {identity}')
            path = entry.get('path', '')
            if not path or Path(path).is_absolute() or ':' in path or '\\' in path or '..' in Path(path).parts or any(char in path for char in '*?[]'):
                errors.append(f'Debt needs an exact repository path: {identity}')
            if entry.get('targetPhase') not in PHASES:
                errors.append(f'Invalid removal phase: {identity}')
            if type(entry.get('occurrences')) is not int or entry['occurrences'] < 1:
                errors.append(f'Invalid occurrence count: {identity}')
            try:
                if date.fromisoformat(entry.get('expires', '')) <= today:
                    errors.append(f'Expired debt: {identity}')
            except (ValueError, TypeError):
                errors.append(f'Invalid expiry: {identity}')
            if identity in ids or key in keys:
                errors.append(f'Duplicate debt: {identity}')
            ids.add(identity)
            keys.add(key)
    return errors


def check(root=ROOT, today=None):
    baseline = json.loads((root / BASELINE).read_text('utf-8'))
    errors = validate_baseline(baseline, today)
    observed = violations(root)
    known = {(item['path'], item['rule'], item['value']): item for item in baseline['source']}
    for key, count in sorted(observed.items()):
        if key not in known or count > known[key]['occurrences']:
            errors.append(f'New boundary debt ({count}): {key}')
    for key, entry in known.items():
        if observed[key] < entry['occurrences']:
            errors.append(f'Remove/reduce stale debt {entry["id"]}: {key}')
    return errors


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', action='store_true', help='Print violations; does not enforce or update debt')
    args = parser.parse_args()
    if args.inventory:
        print(json.dumps([dict(path=key[0], rule=key[1], value=key[2], occurrences=count) for key, count in sorted(violations().items())], indent=2))
    else:
        failures = check()
        if failures:
            raise SystemExit('\n'.join(failures))
        print('PASS tier boundaries: exact source debt, expiry and metadata (Java bytecode runs in Maven)')
