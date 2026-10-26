"""Owned synthetic probe over container-local HTTP; never starts another GPU worker."""

import argparse
from hashlib import sha256
import http.client
import json
import os
from pathlib import Path
import sys
from time import monotonic
from uuid import uuid4


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stdin", action="store_true")
    args = parser.parse_args()
    package = json.loads(Path("/run/package.json").read_bytes())
    token = Path(os.environ["AI07_QWEN_TOKEN_FILE"]).read_text("ascii").strip()
    if args.stdin:
        raw = sys.stdin.buffer.read(16385)
        if not 0 < len(raw) <= 16384:
            raise SystemExit("Invalid bounded request")
    else:
        fields = {
            "description": "Synthetic laptop keyboard has a broken physical key.",
            "title": "Synthetic keyboard fault",
        }
        input_digest = sha256(
            json.dumps(
                fields, ensure_ascii=False, sort_keys=True, separators=(",", ":")
            ).encode()
        ).hexdigest()
        payload = {
            "contractVersion": "classification-inference@0.1.0",
            "requestId": str(uuid4()),
            "inputDigest": input_digest,
            "modelDigest": package["modelDigest"],
            "policyDigest": package["policyDigest"],
            "allowedClasses": ["ACCOUNT", "HARDWARE", "NETWORK", "OTHER", "SOFTWARE"],
            "input": fields,
        }
        raw = json.dumps(payload, ensure_ascii=False).encode()
    connection = http.client.HTTPConnection("127.0.0.1", 8003, timeout=4)
    started = monotonic()
    connection.request(
        "POST",
        "/internal/intelligence/v1/classify",
        raw,
        {"Authorization": "Bearer " + token, "Content-Type": "application/json"},
    )
    response = connection.getresponse()
    body = response.read(16385)
    if len(body) > 16384:
        raise SystemExit("Oversize response")
    result = {
        "httpStatus": response.status,
        "wallMs": round((monotonic() - started) * 1000, 3),
        "response": json.loads(body),
    }
    connection.close()
    print(json.dumps(result, ensure_ascii=True, allow_nan=False))
    raise SystemExit(0 if result["httpStatus"] == 200 else 2)


if __name__ == "__main__":
    main()
