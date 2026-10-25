"""Bounded collocated operator client; credentials stay in a mounted secret file."""

import argparse
import http.client
import json
import os
from pathlib import Path
import sys

from hdpi.classification.protocols import require
from local_classification.ollama_transport import strict_json


class WorkerFailure(ValueError):
    def __init__(self, status):
        self.status = status
        super().__init__("Local bridge rejected the request")


def exchange(path, value=None):
    token = Path(os.environ["AI07_AUTH_TOKEN_FILE"]).read_text("ascii").strip()
    require(32 <= len(token) <= 128)
    connection = http.client.HTTPConnection("127.0.0.1", 8002, timeout=125)
    body = (
        None if value is None else json.dumps(value, ensure_ascii=False).encode("utf-8")
    )
    require(body is None or len(body) <= 16384)

    try:
        connection.request(
            "GET" if body is None else "POST",
            path,
            body,
            {
                "Authorization": "Bearer " + token,
                "Content-Type": "application/json",
                "Connection": "close",
            },
        )
        response = connection.getresponse()
        raw = response.read(65537)
        value = strict_json(raw)

        if response.status != 200:
            raise WorkerFailure(response.status)

        return value
    finally:
        connection.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--health", action="store_true")
    parser.add_argument("--identity", action="store_true")
    parser.add_argument("--candidate")
    args = parser.parse_args()

    if args.health:
        result = exchange("/health/ready")
    else:
        identity = exchange("/v1/identity")

        if args.identity:
            result = identity
        else:
            require(args.candidate in identity["candidates"])
            fields = strict_json(sys.stdin.buffer.read(16385), 16384)
            result = exchange(
                "/v1/observe",
                {
                    "candidateId": args.candidate,
                    "runtimeDigest": identity["runtimeDigest"],
                    "policyDigest": identity["candidates"][args.candidate][
                        "policyDigest"
                    ],
                    "dataScope": "SYNTHETIC_DEV_ONLY",
                    "input": fields,
                },
            )

    print(json.dumps(result, ensure_ascii=True, allow_nan=False))


if __name__ == "__main__":
    try:
        main()
    except WorkerFailure as error:
        print(
            json.dumps({"code": "LOCAL_WORKER_FAILED", "status": error.status}),
            file=sys.stderr,
        )
        raise SystemExit(2) from None
    except Exception:
        print('{"code":"LOCAL_WORKER_FAILED"}', file=sys.stderr)
        raise SystemExit(2) from None
