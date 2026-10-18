"""Local engineering CLI; it does not authenticate users or expose a service."""

import argparse
from dataclasses import asdict
import json
import sys

from .domain.errors import IntelligenceError
from .domain.models import TrustedContext
from .services.triage import TriageService


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', required=True, help='Exact immutable identity, e.g. service-desk@0.2.0')
    parser.add_argument('--workspace', required=True, help='Local test context only; no authorization grant')
    parser.add_argument('--title', required=True)
    parser.add_argument('--description', required=True)
    parser.add_argument('--request-id', default='local-engineering')
    parser.add_argument('--trace', action='store_true', help='Write engineering trace (without ticket content) to stderr')
    args = parser.parse_args()
    if args.profile.count('@') != 1:
        parser.error('--profile requires an exact ID@version')
    profile_id, version = args.profile.split('@')
    payload = {'contractVersion': '0.1.0', 'requestId': args.request_id, 'profile': profile_id,
               'context': {'workspaceId': args.workspace},
               'input': {'title': args.title, 'description': args.description}}
    try:
        run = TriageService().triage(payload,
                                    trusted_context=TrustedContext(workspace_id=args.workspace, subject='local-engineering'),
                                    profile_version=version)
    except IntelligenceError as error:
        print(type(error).__name__ + ': ' + str(error), file=sys.stderr)
        return 2
    print(json.dumps(run.response, ensure_ascii=True, allow_nan=False))
    if args.trace:
        print(json.dumps(asdict(run.trace), ensure_ascii=True, allow_nan=False), file=sys.stderr)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
