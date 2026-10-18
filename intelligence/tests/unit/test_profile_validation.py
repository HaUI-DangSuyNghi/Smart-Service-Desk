import copy
import json
from pathlib import Path
import shutil
import runpy
import tempfile
import unittest

from hdpi.domain.errors import ProfileInvalid
from hdpi.profiles.validation import load_documents, read_yaml, validate_documents, verify_lock

ROOT = Path(__file__).resolve().parents[3] / 'apps/servicedesk/intelligence/profiles'
MANIFEST = 'service-desk/versions/0.2.0/profile.yaml'


class ExecutableProfileTests(unittest.TestCase):
    def setUp(self):
        self.profile, docs = load_documents(ROOT, MANIFEST, 'service-desk@0.2.0')
        self.taxonomy, self.rules, self.policy = [docs[k] for k in ('taxonomy', 'rules', 'priorityPolicy')]

    def test_versioned_composition(self):
        self.assertEqual(self.profile['rules']['version'], '0.2.0')
        self.assertEqual(self.profile['taxonomy']['version'], '0.1.0')
        self.assertEqual(read_yaml(ROOT / 'service-desk/profile.yaml')['profile']['version'], 'service-desk@0.1.0')

    def test_invalid_executable_semantics(self):
        cases = [
            ('empty impact', lambda p, t, r, b: r['impactRules']['I1'].update(positiveSignals=[])),
            ('empty urgency', lambda p, t, r, b: r['urgencyRules']['U2'].update(positiveSignals=[])),
            ('duplicate ID', lambda p, t, r, b: r['impactRules']['I1'].update(ruleId='CAT-NET-001')),
            ('missing route', lambda p, t, r, b: t['categories']['NETWORK'].pop('suggestedGroup')),
            ('bad root ref', lambda p, t, r, b: r['conflictRules']['CONFLICT-ROOT-001']['overrides'][0].update(winnerRule='MISSING')),
            ('priority drift', lambda p, t, r, b: b['matrix']['I1'].update(U1='UNKNOWN')),
            ('incomplete matrix', lambda p, t, r, b: b['matrix']['I1'].pop('U2')),
            ('bad pin', lambda p, t, r, b: p['rules'].update(version='9.0.0')),
            ('normalization', lambda p, t, r, b: p['normalization'].update(version='unknown')),
            ('arbitrary regex', lambda p, t, r, b: r['impactRules']['I1'].update(regex='(a+)+$')),
            ('bad template', lambda p, t, r, b: r['impactRules']['I2']['countSignals'][0].update(template='{invalid}')),
            ('unbounded count', lambda p, t, r, b: r['impactRules']['I2']['countSignals'][0].update(max=1000000)),
            ('cyclic precedence', lambda p, t, r, b: r['impactRules']['I2'].update(overrides=['I3'])),
            ('fallback route', lambda p, t, r, b: b['reviewFallback'].update(suggestedGroup='IT-NETWORK')),
        ]
        for name, mutate in cases:
            with self.subTest(name=name):
                docs = copy.deepcopy((self.profile, self.taxonomy, self.rules, self.policy))
                mutate(*docs)
                with self.assertRaises((ProfileInvalid, KeyError)):
                    validate_documents(*docs)

    def test_immutable_labels_cannot_hide_content_changes(self):
        for relative in ('service-desk/rules.yaml', 'service-desk/versions/0.2.0/rules.yaml', 'service-desk/priority-policy.yaml'):
            with self.subTest(relative=relative), tempfile.TemporaryDirectory() as tmp:
                copied = Path(tmp) / 'profiles'
                shutil.copytree(ROOT, copied)
                with (copied / relative).open('a', encoding='utf-8') as stream:
                    stream.write('\n# changed under same version\n')
                with self.assertRaises(ProfileInvalid):
                    verify_lock(copied)

    def test_path_escape_even_if_lock_is_edited(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'immutable-artifacts.json').write_text(json.dumps({'../outside.yaml': 'a' * 64}))
            with self.assertRaises(ProfileInvalid):
                verify_lock(root)

    def test_repinning_existing_version_is_rejected_against_git_history(self):
        script = Path(__file__).resolve().parents[3] / 'scripts/intelligence/validate-executable-profiles.py'
        history = runpy.run_path(str(script))['check_history']
        lock = verify_lock(ROOT)
        lock['service-desk/versions/0.2.0/rules.yaml'] = 'a' * 64
        with self.assertRaises(ProfileInvalid):
            history(ROOT, lock, 'HEAD')


if __name__ == '__main__':
    unittest.main()
