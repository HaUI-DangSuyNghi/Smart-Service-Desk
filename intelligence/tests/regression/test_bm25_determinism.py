from itertools import permutations
import json
import os
from pathlib import Path
import subprocess
import sys

import hdpi

from hdpi.evaluation.candidates import Bm25Candidate
from hdpi.evaluation.evidence import serialize_report
from servicedesk.intelligence.evaluation.retrieval import evaluate
from hdpi.evaluation.retrieval import stable_report

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / 'fixtures/intelligence/service-desk'


def test_source_order_cannot_change_index_or_ranking():
    sources = [('c', '1', 'checksum', 'alpha beta'), ('a', '1', 'checksum', 'alpha beta'),
               ('b', '1', 'checksum', 'alpha gamma')]
    candidate = Bm25Candidate()
    index = candidate.build(sources)
    expected = candidate.engine.rank('alpha beta', index)
    for order in permutations(sources):
        rebuilt = candidate.build(order)
        assert rebuilt == index
        assert candidate.engine.rank('alpha beta', rebuilt) == expected


def test_repeated_evidence_and_hash_seed_independence():
    expected = serialize_report(stable_report(evaluate(ROOT, DATA, candidate=Bm25Candidate())))
    environment = os.environ.copy()
    installed = 'site-packages' in Path(hdpi.__file__).parts
    if not installed:
        environment['PYTHONPATH'] = str(Path(hdpi.__file__).resolve().parent.parent)
    # Do not use -I here: it ignores PYTHONHASHSEED. Installed mode has no source
    # pythonpath; verify the imported module is still the wheel in that case.
    code = ("import hdpi; from pathlib import Path; "
            + ("assert 'site-packages' in Path(hdpi.__file__).parts; " if installed else '')
            + "import sys; sys.path.insert(0, str(Path('intelligence/src/apps').resolve())); "
            + "from hdpi.evaluation.candidates import Bm25Candidate; "
            "from servicedesk.intelligence.evaluation.retrieval import evaluate; "
            "from hdpi.evaluation.retrieval import stable_report; "
            "from hdpi.evaluation.evidence import serialize_report; "
            "print(serialize_report(stable_report(evaluate(Path('.'), "
            "Path('fixtures/intelligence/service-desk'), candidate=Bm25Candidate()))), end='')")
    for seed in ('1', '42'):
        run = subprocess.run([sys.executable, '-c', code], cwd=ROOT,
                             env=environment | {'PYTHONHASHSEED': seed}, capture_output=True, text=True, timeout=60)
        assert run.returncode == 0, run.stderr
        assert json.loads(run.stdout) == json.loads(expected)
