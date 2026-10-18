"""Cross-language source guards; exact, expiring migration exceptions only."""
import ast
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / 'docs/architecture/boundary-waivers.json'


def violations(root=ROOT):
    waivers = json.loads((root / CONFIG.relative_to(ROOT)).read_text('utf-8'))
    errors, observed = [], set()

    def report(path, kind, value):
        key = f'{path.relative_to(root).as_posix()}:{kind}:{value}'
        observed.add(key)
        if key not in waivers:
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
        if tier == 'apps':
            for table in set(re.findall(r'\b(?:from|join|into|update)\s+(act_\w+|process_scope|metadata_record|file_object|file_link|audit_event|workspace_membership)\b', source, re.I)):
                report(path, 'internal-sql', table.lower())
    for path in (root / 'intelligence/src').rglob('*.py'):
        for node in ast.walk(ast.parse(path.read_text('utf-8'))):
            if isinstance(node, ast.Constant) and isinstance(node.value, str) and re.search(r'service[-_]desk', node.value, re.I):
                report(path, 'app-literal', node.value)
            if isinstance(node, (ast.Import, ast.ImportFrom)):
                names = [a.name for a in node.names] if isinstance(node, ast.Import) else [node.module or '']
                if any(name == 'apps' or name.startswith('apps.') for name in names):
                    errors.append(f'{path}: app import in runtime')
    for path in (root / 'frontend/src/platform').rglob('*'):
        if path.suffix in ('.js', '.jsx', '.ts', '.tsx', '.mjs'):
            if re.search(r'''(?:from\s*|import\s*\(?|require\s*\()\s*['"][^'"]*(?:/apps/|@apps|^apps/)''', path.read_text('utf-8')):
                errors.append(f'{path}: platform imports apps')
    for key, waiver in waivers.items():
        if not all(waiver.get(field) for field in ('debt', 'owner', 'removal')):
            errors.append(f'Incomplete waiver: {key}')
        if key not in observed:
            errors.append(f'Remove stale waiver: {key}')
    return errors


if __name__ == '__main__':
    failures = violations()
    if failures:
        raise SystemExit('\n'.join(failures))
    print('PASS tier boundaries (Java imports/SQL, Python AST, frontend imports)')
