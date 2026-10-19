"""Local authored retrieval regression; kb-* identifiers are NEVER runtime citations."""
import argparse
from hashlib import sha256
import json
from pathlib import Path

from ..normalization.v1 import NormalizerV1
from ..retrieval.chunking import chunk_text
from ..retrieval.models import CHUNK_POLICY, ENGINE_CONFIG, NORMALIZATION, digest
from ..retrieval.tfidf.engine import fit_chunks, TfidfSearchEngine
from .metrics import first_relevant_rank, retrieval_metrics
from .gates import evaluate_gates, exit_code


def load_authoring_index(root: Path, fixtures_root: Path):
    root = root.resolve()
    directory = fixtures_root.resolve() / 'knowledge'
    manifest = json.loads((directory / 'manifest.json').read_text('utf-8'))
    chunks, identifiers = [], set()
    for artifact in manifest['artifacts']:
        identifier = artifact['artifactId']
        path = (root / artifact['path']).resolve()
        if not path.is_relative_to(directory.resolve()) or identifier in identifiers:
            raise ValueError('Invalid authoring artifact path or duplicate identity')
        identifiers.add(identifier)
        raw = path.read_bytes()
        if len(raw) != artifact['byteSize'] or sha256(raw).hexdigest() != artifact['checksum']:
            raise ValueError('Authoring fixture exact-byte mismatch')
        chunks.extend(chunk_text(raw.decode('utf-8'), source_id=identifier,
                                 source_state=(artifact['version'], artifact['checksum'])))
    return fit_chunks(tuple(chunks)), manifest


def evaluate(root: Path, fixtures_root: Path, *, gates=None):
    index, manifest = load_authoring_index(root, fixtures_root)
    directory = fixtures_root.resolve()
    tickets_raw = (directory / 'tickets/tickets-v0.1.jsonl').read_bytes()
    gold_raw = (directory / 'retrieval/gold-v0.1.json').read_bytes()
    tickets = [json.loads(line) for line in tickets_raw.decode('utf-8').splitlines()]
    by_id = {t['id']: t for t in tickets}
    gold = json.loads(gold_raw)
    if (gold['annotationKind'], gold['benchmarkStatus'], gold['outputKind']) != (
            'MANUAL_EXPECTED', 'NOT BENCHMARK', 'NOT ENGINE OUTPUT'):
        raise ValueError('Unexpected authored evaluation classification')
    ids = [c['ticketId'] for c in gold['cases']]
    if len(by_id) != len(tickets) or len(set(ids)) != len(ids) or set(ids) != set(by_id):
        raise ValueError('Ticket/gold associations are incomplete or duplicated')
    known = {a['artifactId'] for a in manifest['artifacts']}
    engine, normalizer, cases, pairs = TfidfSearchEngine(), NormalizerV1(), [], []
    for case in gold['cases']:
        fields = by_id[case['ticketId']]['request']['input']
        query = normalizer.normalize(fields['title'] + '\n' + fields['description']).value
        relevant = case['expectedRelevantLocalArtifacts']
        if not relevant or not set(relevant) <= known:
            raise ValueError('Gold references an unknown or empty relevance set')
        hits = engine.rank(query, index)
        ranked = list(dict.fromkeys(h.chunk.source_id for h in hits))
        pairs.append((ranked, relevant))
        cases.append({'ticketId': case['ticketId'], 'rankedLocalArtifacts': ranked,
                      'expectedRelevantLocalArtifacts': relevant,
                      'firstRelevantRank': first_relevant_rank(ranked, set(relevant))})
    metrics = retrieval_metrics(pairs)
    return {'evidenceClass': 'AUTHORED_RETRIEVAL_REGRESSION', 'benchmarkClaim': False,
            'runtimeCitations': False, 'rightsStatus': gold['rightsStatus'],
            'datasetVersion': gold['datasetVersion'], 'engine': engine.name, 'engineVersion': engine.version,
            'engineConfigVersion': ENGINE_CONFIG, 'chunkPolicyVersion': CHUNK_POLICY,
            'normalizationVersion': NORMALIZATION, 'queryStrategy': 'title+newline+description@0.1.0',
            'rankingUnit': 'artifact; max chunk score; stable first-occurrence deduplication',
            'sourceStateDigest': digest(manifest), 'ticketsChecksum': sha256(tickets_raw).hexdigest(),
            'goldChecksum': sha256(gold_raw).hexdigest(), **metrics,
            'suggestedGates': {metric: metrics[metric] >= threshold for metric, threshold in (gates or {}).items()}, 'cases': cases}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root', required=True, type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--fixtures-root', required=True, type=Path)
    parser.add_argument('--gates', type=json.loads, default={})
    args = parser.parse_args()
    report = evaluate(args.repo_root, args.fixtures_root, gates=args.gates)
    report['gates'] = evaluate_gates(metrics=report, policy=args.gates)
    report['result'] = report['gates']['result']
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=True, indent=2) + '\n', 'utf-8')
    print(json.dumps({key: report[key] for key in ('evidenceClass', 'caseCount', 'hitAt1', 'hitAt3', 'mrr', 'suggestedGates')}))
    return exit_code(report)


if __name__ == '__main__':
    raise SystemExit(main())
