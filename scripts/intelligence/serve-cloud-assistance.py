"""Explicit deployment composition; the generic hdpi package never imports vendors."""

import argparse
import os
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]


def build_runtime(*, installed=False):
    if not installed:
        sys.path.insert(0, str(ROOT / "intelligence/src"))
        sys.path.insert(0, str(ROOT / "intelligence/integrations"))

    from cloud_providers.contracts import encode_json, require
    from cloud_providers.http_client import BoundedHTTPSClient
    from cloud_providers.registry import ProviderRegistry
    from cloud_providers.runtime import CloudAssistanceRuntime

    require(os.environ.get("PROVIDER_MODE") == "synthetic-cloud-dev")
    registry = ProviderRegistry(
        os.environ["PROVIDER_CATALOG_PATH"],
        os.environ["PROVIDER_CATALOG_SHA256"],
        os.environ["CLOUD_EGRESS_APPROVAL_FILE"],
        os.environ["CLOUD_EGRESS_APPROVAL_SHA256"],
        os.environ["CLOUD_ISOLATION_FILE"],
        os.environ.get("CLOUD_ENVIRONMENT"),
        repository_root=ROOT,
    )
    return CloudAssistanceRuntime(
        registry,
        client=BoundedHTTPSClient(proxy=os.environ.get("CLOUD_EGRESS_PROXY")),
        observer=lambda event: print(encode_json(event).decode(), flush=True),
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--installed", action="store_true")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8001)
    args = parser.parse_args()
    try:
        runtime = build_runtime(installed=args.installed)
        from hdpi.assistance_http import make_server

        server = make_server(
            (args.host, args.port),
            service_token=os.environ["INTELLIGENCE_ASSISTANCE_TOKEN"],
            runtime=runtime,
        )
    except Exception:
        parser.exit(
            2, "Cloud assistance blocked [INVALID_OR_UNAPPROVED_CONFIGURATION]\n"
        )

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
