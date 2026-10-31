"""Explicit operator-only public base download; never invoked by the inference runtime."""

import argparse
from hashlib import sha256
import json
from pathlib import Path
import re
import urllib.request
from concurrent.futures import ThreadPoolExecutor


def download_file(url, target, *, size, resume=False):
    offset = target.stat().st_size if resume and target.exists() else 0
    if not 0 <= offset <= size:
        raise ValueError("Partial download exceeds the pinned file size")
    if not resume and target.exists():
        raise ValueError("Download destination already exists")
    if offset == 0 and size <= 1024 * 1024:
        with urllib.request.urlopen(url, timeout=60) as response:
            block = response.read(size + 1)
        if len(block) != size:
            raise ValueError("Truncated repository metadata")
        with target.open("xb") as stream:
            stream.write(block)
        return
    ranges = [
        (start, min(start + 8 * 1024 * 1024, size) - 1)
        for start in range(offset, size, 8 * 1024 * 1024)
    ]

    def fetch(bounds):
        start, end = bounds
        request = urllib.request.Request(
            url + f"?download=true&ai07_range={start}",
            headers={"Range": f"bytes={start}-{end}"},
        )
        with urllib.request.urlopen(request, timeout=120) as response:
            expected = f"bytes {start}-{end}/{size}"
            if (
                response.status != 206
                or response.headers.get("Content-Range") != expected
            ):
                raise ValueError("Server did not honor the exact pinned byte range")
            block = response.read(end - start + 2)
        if len(block) != end - start + 1:
            raise ValueError("Truncated download chunk")
        return block

    # Bounded groups avoid eagerly buffering the entire multi-GB checkpoint.
    with (
        target.open("ab" if resume else "xb") as stream,
        ThreadPoolExecutor(max_workers=4) as pool,
    ):
        for group in range(0, len(ranges), 4):
            for block in pool.map(fetch, ranges[group : group + 4]):
                stream.write(block)
            stream.flush()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument(
        "--resume",
        action="store_true",
        help="Continue an incomplete private download, then verify its full hash",
    )
    args = parser.parse_args()
    if re.fullmatch(r"[0-9a-f]{40}", args.revision) is None:
        parser.error("An immutable repository commit is required")
    args.directory.mkdir(parents=True, exist_ok=args.resume)
    if (args.directory / "source-receipt.json").exists():
        parser.error("A completed immutable download cannot be overwritten")
    origin = "https://huggingface.co/unsloth/Qwen3.5-0.8B"
    with urllib.request.urlopen(
        f"https://huggingface.co/api/models/unsloth/Qwen3.5-0.8B/revision/{args.revision}?blobs=true",
        timeout=60,
    ) as response:
        metadata = json.load(response)
    if metadata["sha"] != args.revision:
        raise ValueError("Base revision changed")
    files = [
        item
        for item in metadata["siblings"]
        if item["rfilename"]
        in ("LICENSE", "config.json", "model.safetensors.index.json")
        or item["rfilename"].endswith(".safetensors")
    ]
    verified = {}
    for item in files:
        name = item["rfilename"]
        if "/" in name or "\\" in name:
            raise ValueError("Unexpected model layout")
        target = args.directory / name
        download_file(
            f"{origin}/resolve/{args.revision}/{name}",
            target,
            size=item["size"],
            resume=args.resume,
        )
        hasher = sha256()
        with target.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                hasher.update(block)
        expected = item.get("lfs", {}).get("sha256")
        if expected and hasher.hexdigest() != expected:
            raise ValueError("Downloaded model digest mismatch")
        if not expected:
            from hashlib import sha1

            blob = target.read_bytes()
            if (
                sha1(b"blob " + str(len(blob)).encode() + b"\0" + blob).hexdigest()
                != item["blobId"]
            ):
                raise ValueError("Downloaded repository blob identity mismatch")
        verified[name] = hasher.hexdigest()
        print(name, hasher.hexdigest(), flush=True)
    receipt = {
        "schemaVersion": "ai07-qwen-base-download@0.1.0",
        "repository": "unsloth/Qwen3.5-0.8B",
        "revision": args.revision,
        "files": verified,
        "trainingRevisionProven": False,
    }
    with (args.directory / "source-receipt.json").open(
        "x", encoding="utf-8", newline="\n"
    ) as stream:
        json.dump(receipt, stream, sort_keys=True, indent=2)
        stream.write("\n")


if __name__ == "__main__":
    main()
