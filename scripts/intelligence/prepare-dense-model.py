"""Explicit provisioning only: download immutable public artifacts and verify pins.

Inference never invokes this command and never downloads model files.
"""
import argparse
from hashlib import sha256
from pathlib import Path
import sys
from urllib.request import urlopen


def provision(directory, *, offline=False):
    from hdpi.retrieval.dense.artifacts import FILES, MODEL, REVISION

    for relative, size, checksum in FILES:
        target = directory / relative
        if target.is_file() and target.stat().st_size == size:
            with target.open('rb') as stream:
                if sha256_stream(stream) == checksum:
                    continue
        if offline:
            raise ValueError('Missing or corrupt pinned model artifact')
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_suffix(target.suffix + '.download')
        try:
            with urlopen(f'https://huggingface.co/{MODEL}/resolve/{REVISION}/{relative}', timeout=120) as response:
                observed, total = sha256(), 0
                with temporary.open('wb') as stream:
                    while block := response.read(1024 * 1024):
                        total += len(block)
                        if total > size:
                            raise ValueError('Model artifact exceeds pinned size')
                        stream.write(block)
                        observed.update(block)
            if total != size or observed.hexdigest() != checksum:
                raise ValueError('Model artifact checksum/size mismatch')
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
    print('PASS pinned model artifacts ready; no inference or protected data transfer')


def sha256_stream(stream):
    observed = sha256()
    while block := stream.read(1024 * 1024):
        observed.update(block)
    return observed.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--installed', action='store_true')
    args = parser.parse_args()
    if not args.installed:
        sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'intelligence/src'))
    provision(args.directory.resolve(), offline=args.offline)


if __name__ == '__main__':
    main()
