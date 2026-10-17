"""Engineering authoring CLI only; does not create contract-facing Core citations."""
import argparse
import json
from pathlib import Path

from .evaluation.retrieval import load_authoring_index
from .normalization.v1 import NormalizerV1
from .profiles.registry import ProfileRegistry
from .retrieval.tfidf.engine import TfidfSearchEngine


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', required=True, help='Exact executable ID@version')
    parser.add_argument('--fixture-repo-root', required=True, type=Path)
    parser.add_argument('--query', required=True)
    parser.add_argument('--top-k', type=int, choices=range(1, 11), default=3)
    args = parser.parse_args()
    if args.profile.count('@') != 1:
        parser.error('--profile requires ID@version')
    ProfileRegistry().get(*args.profile.split('@'))
    index, _ = load_authoring_index(args.fixture_repo_root)
    hits = TfidfSearchEngine().search(NormalizerV1().normalize(args.query).value, index, args.top_k)
    print(json.dumps({'mode': 'AUTHORING_ONLY_NOT_RUNTIME', 'runtimeCitations': False,
                      'results': [{'localArtifactId': h.chunk.source_id, 'score': h.score,
                                   'excerpt': h.chunk.original_text} for h in hits]}, ensure_ascii=True, allow_nan=False))


if __name__ == '__main__':
    main()
