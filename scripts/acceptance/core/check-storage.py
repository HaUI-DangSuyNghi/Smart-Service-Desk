import argparse
import hashlib
import io
import json
from pathlib import Path
import tarfile
import tempfile
import uuid


def inspect(inventory, archive):
    expected = {item["storage_key"]: item for item in inventory}
    found, invalid = {}, []
    with tarfile.open(archive) as contents:
        for member in contents:
            name = member.name.removeprefix("./")
            if member.isdir() and name in ["", "."]:
                continue
            try:
                if str(uuid.UUID(name)) != name or not member.isfile() or name in found:
                    raise ValueError("Unsafe or duplicate entry")
            except ValueError:
                invalid.append(member.name)
                continue
            digest = hashlib.sha256()
            with contents.extractfile(member) as content:
                while chunk := content.read(65536):
                    digest.update(chunk)
            found[name] = (member.size, digest.hexdigest())
    return dict(missing=sorted(expected.keys() - found.keys()), orphans=sorted(found.keys() - expected.keys()),
                corrupt=sorted(name for name in expected.keys() & found.keys()
                               if found[name] != (expected[name]["byte_size"], expected[name]["checksum"])), invalid=invalid)


def self_test():
    key, orphan = str(uuid.uuid4()), str(uuid.uuid4())
    inventory = [dict(storage_key=key, byte_size=4, checksum=hashlib.sha256(b"safe").hexdigest())]
    with tempfile.TemporaryDirectory() as directory:
        archive = Path(directory) / "files.tar"
        for entries, failure in [([(key, b"safe")], None), ([], "missing"), ([(key, b"evil")], "corrupt"),
                                 ([(key, b"safe"), (orphan, b"extra")], "orphans"), ([(key, b"safe"), ("../escape", b"bad")], "invalid"),
                                 ([(key, b"safe"), (key, b"safe")], "invalid")]:
            with tarfile.open(archive, "w") as contents:
                for name, content in entries:
                    member = tarfile.TarInfo(name)
                    member.size = len(content)
                    contents.addfile(member, io.BytesIO(content))
            result = inspect(inventory, archive)
            assert bool(any(result.values())) == bool(failure), result
            if failure:
                assert result[failure], result
        with tarfile.open(archive, "w") as contents:
            member = tarfile.TarInfo(key)
            member.type, member.linkname = tarfile.SYMTYPE, "../outside"
            contents.addfile(member)
        assert inspect(inventory, archive)["invalid"] == [key]
    print("PASS storage inventory: intact, missing, corrupt, orphan, duplicate, unsafe-path, symlink; no extraction/deletion")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Read-only DB/blob reconciliation; never extracts or deletes blobs")
    parser.add_argument("inventory", nargs="?", type=Path)
    parser.add_argument("archive", nargs="?", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
    else:
        if not args.inventory or not args.archive:
            parser.error("inventory and archive are required")
        result = inspect(json.loads(args.inventory.read_text(encoding="utf-8-sig")), args.archive)
        print(json.dumps(result, indent=2))
        raise SystemExit(1 if any(result.values()) else 0)
