"""Content-free selection-provider evidence. Fake contracts are not model quality."""

import argparse
from collections import Counter
from dataclasses import asdict
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
SUPPORT_PATH = Path(__file__).with_name('ai06_provider_support.py')
SPEC = importlib.util.spec_from_file_location('ai06_evidence_support', SUPPORT_PATH)
support = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = support
SPEC.loader.exec_module(support)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def source_revision():
    declared = os.environ.get('AI06_SOURCE_REVISION')

    try:
        result = subprocess.run(
            ['git', 'rev-parse', 'HEAD'],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=True,
        )
        revision = result.stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        # Offline minimal containers may lack git. The trusted test launcher
        # must explicitly bind the checkout revision in that environment.
        revision = declared

    require(
        type(revision) is str and re.fullmatch(r'[0-9a-f]{40}', revision),
        'A full verified checkout revision is required',
    )
    require(declared is None or declared == revision, 'Declared checkout revision differs from git')

    return revision


def configure_fake(transport, tokenizer, case):
    from hdpi.generation.errors import GenerationTimeout, GenerationUnavailable

    mode = case.mode

    if mode == 'refuse':
        transport.selection = {'decision': 'REFUSE', 'citation_ids': []}
    elif mode == 'unknown-alias':
        transport.selection['citation_ids'] = ['C99']
    elif mode == 'duplicate-alias':
        transport.selection['citation_ids'] = ['C1', 'C1']
    elif mode == 'extra-key':
        transport.selection['tool_calls'] = [{'name': 'DUMMY_CANARY'}]
    elif mode == 'duplicate-key':
        transport.raw = b'{"selection":{},"selection":{}}'
    elif mode == 'free-text':
        transport.raw = b'DUMMY_CANARY ungrounded prose'
    elif mode == 'partial':
        transport.raw = b'{"selection":'
    elif mode == 'timeout':
        transport.failure = GenerationTimeout('DUMMY_CANARY')
    elif mode == 'unavailable':
        transport.failure = GenerationUnavailable('DUMMY_CANARY')
    elif mode == 'oversize':
        transport.raw = b'x' * 16385
    elif mode == 'token-overflow':
        tokenizer.tokens = 1000000


def run_fake_case(manifest, directory, case):
    from hdpi.generation.errors import (
        GenerationContractError,
        GenerationTimeout,
        GenerationUnavailable,
    )
    from hdpi.generation.local_selection import PinnedLocalSelectionProvider
    from hdpi.generation.models import EvidenceExcerpt, GenerationRequest
    from hdpi.generation.validation import validate_grounded_draft

    transport = support.FakeSelectorTransport(manifest)
    tokenizer = support.SyntheticTokenCounter(manifest)
    configure_fake(transport, tokenizer, case)
    provider = PinnedLocalSelectionProvider(
        manifest=manifest,
        model_directory=directory,
        transport=transport,
        tokenizer=tokenizer,
        app_prompt_version='synthetic-security-policy@1',
        enabled=True,
    )
    excerpt = case.payload if case.category == 'indirect' else 'Synthetic approved evidence.'
    request = GenerationRequest(
        question=case.payload,
        policy_identity='synthetic-security-policy@1',
        policy_instructions='Select relevant evidence only; refuse when evidence is irrelevant.',
        output_schema_version=provider.runtime_identity.output_schema_version,
        evidence=(EvidenceExcerpt(citation_id='C1', excerpt=excerpt),),
    )
    claim_count = 0
    observed = 'SUCCESS'

    try:
        draft = provider.generate(request)
        validate_grounded_draft(draft, request, max_claims=1)
        claim_count = len(draft.claims)

        if not draft.claims:
            observed = 'REFUSED'
    except GenerationTimeout:
        observed = 'TIMEOUT'
    except GenerationUnavailable:
        observed = 'UNAVAILABLE'
    except GenerationContractError:
        observed = 'CONTRACT_REJECTED'

    expected = {
        'refuse': 'REFUSED',
        'timeout': 'TIMEOUT',
        'unavailable': 'UNAVAILABLE',
    }.get(case.mode, 'SUCCESS' if case.mode == 'select' else 'CONTRACT_REJECTED')
    require(observed == expected, 'Synthetic provider invariant failed: ' + case.case_id)

    for raw in transport.calls:
        envelope = json.loads(raw)
        messages = envelope['messages']

        require(
            [message['role'] for message in messages] == ['system', 'developer', 'user'],
            'Untrusted content escaped the message envelope',
        )
        require(
            json.loads(messages[2]['content'])['question'] == case.payload,
            'Untrusted data was changed',
        )
        require('tools' not in envelope, 'Tool capability exposed')

    result = {
        'id': case.case_id,
        'category': case.category,
        'observed': observed,
        'invariantResult': 'PASS',
        'claimCount': claim_count,
        'inferenceCalls': len(transport.calls),
        'unsafeSourceQuoted': case.category == 'indirect' and claim_count > 0,
    }

    return result, provider.runtime_identity


def build_report():
    import hdpi
    from hdpi.generation.model_manifest import fingerprint

    cases = support.security_cases()
    package = Path(hdpi.__file__).resolve().parent
    implementation = {
        name: fingerprint((package / name).read_text('utf-8'))
        for name in (
            'generation/local_selection.py',
            'generation/selection_codec.py',
            'generation/model_manifest.py',
            'generation/transport.py',
            'services/assist.py',
        )
    }

    with tempfile.TemporaryDirectory(prefix='ai06-synthetic-') as directory:
        manifest = support.synthetic_manifest(directory)

        with patch(
            'socket.socket', side_effect=AssertionError('Fake evidence must not open sockets')
        ):
            outcomes = [run_fake_case(manifest, directory, case) for case in cases]

    results = [result for result, _ in outcomes]
    runtime = outcomes[0][1]
    require(
        all(identity == runtime for _, identity in outcomes), 'Synthetic runtime identity drift'
    )

    return {
        'schemaVersion': 'ai06-provider-evidence@0.1.0',
        'evidenceClass': 'SYNTHETIC_SELECTOR_CONTRACT',
        'mode': 'fake',
        'sourceRevision': source_revision(),
        'implementationDigests': implementation,
        'testSetDigest': fingerprint([asdict(case) for case in cases]),
        'manifestDigest': manifest.digest,
        'runtime': asdict(runtime),
        'caseCount': len(cases),
        'categoryCounts': dict(Counter(case.category for case in cases)),
        'outcomeCounts': dict(Counter(case['observed'] for case in results)),
        'cases': results,
        'invariantViolations': 0,
        'benchmarkClaim': False,
        'runtimePromotion': 'NOT_AUTHORIZED',
        'realModelValidation': 'REAL_MODEL_VALIDATION_BLOCKED',
        'result': 'PASS',
    }


def write_report(report, output):
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(report, ensure_ascii=True, sort_keys=True, indent=2) + '\n', 'utf-8'
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--installed', action='store_true')
    parser.add_argument('--mode', choices=('fake', 'approved-local-model'), required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()

    if not args.installed:
        sys.path.insert(0, str(ROOT / 'intelligence/src'))

    if args.mode == 'approved-local-model':
        # No approved manifest, engine wrapper or real tokenizer adapter exists.
        # Refuse explicitly rather than allowing synthetic pins to operate a model.
        write_report(
            {
                'schemaVersion': 'ai06-real-provider-readiness@0.1.0',
                'result': 'BLOCKED',
                'realModelValidation': 'REAL_MODEL_VALIDATION_BLOCKED',
                'runtimePromotion': 'NOT_AUTHORIZED',
                'benchmarkClaim': False,
                'blockers': [
                    'MODEL_PINS',
                    'OWNER_SIGNOFFS',
                    'ISOLATED_DEPLOYMENT',
                    'PINNED_TOKENIZER',
                ],
            },
            args.output,
        )
        print('REAL_MODEL_VALIDATION_BLOCKED: approved offline deployment required')

        return 2

    import hdpi

    if args.installed:
        require('site-packages' in Path(hdpi.__file__).resolve().parts, 'Installed wheel required')

    report = build_report()
    write_report(report, args.output)
    print('PASS synthetic selector contract: 40 cases; real-model validation BLOCKED')

    return 0


if __name__ == '__main__':
    raise SystemExit(main())
