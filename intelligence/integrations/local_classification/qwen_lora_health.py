"""Authenticated content-free readiness probe; no separate model loading."""

import json
import os
from pathlib import Path
import urllib.request


def main():
    try:
        token = Path(os.environ["AI07_QWEN_TOKEN_FILE"]).read_text("ascii").strip()
        request = urllib.request.Request(
            "http://127.0.0.1:8003/health/ready",
            headers={"Authorization": "Bearer " + token},
        )
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(
            request, timeout=2
        ) as response:
            raw = response.read(4097)
        if len(raw) > 4096 or json.loads(raw).get("ready") is not True:
            raise ValueError()
    except Exception:
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
