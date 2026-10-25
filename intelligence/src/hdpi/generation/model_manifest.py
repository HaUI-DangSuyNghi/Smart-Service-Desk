"""Offline model receipts. A manifest fingerprint binds every artifact and limit.

Receipts are references to owner reviews, not signatures or authorization minted
by this module. The trusted deployment must verify their authenticity separately.
"""

from dataclasses import asdict, dataclass
from hashlib import sha256
import json
from pathlib import Path, PurePosixPath
import re

from .errors import GenerationConfigurationError, GenerationContractError
from .selection_codec import strict_json


ARTIFACT_KINDS = ('weights', 'tokenizer', 'config', 'chat-template', 'license', 'quantization')


def fingerprint(value):
    serialized = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(',', ':'),
        allow_nan=False,
    )

    return 'sha256:' + sha256(serialized.encode('utf-8')).hexdigest()


def require(condition):
    if not condition:
        raise GenerationConfigurationError('Invalid or unapproved model manifest')


def digest(value):
    return type(value) is str and re.fullmatch(r'sha256:[0-9a-f]{64}', value) is not None


@dataclass(frozen=True, kw_only=True)
class ModelArtifact:
    path: str
    kind: str
    sha256: str

    def __post_init__(self):
        require(type(self.path) is str and 0 < len(self.path) <= 200)

        path = PurePosixPath(self.path)
        require(
            not path.is_absolute()
            and str(path) == self.path
            and all(part not in ('', '.', '..') for part in path.parts)
            and re.fullmatch(r'[A-Za-z0-9_./-]+', self.path) is not None
        )
        require(self.kind in ARTIFACT_KINDS)
        require(digest(self.sha256))


@dataclass(frozen=True, kw_only=True)
class InferenceLimits:
    max_request_bytes: int = 131072
    max_response_bytes: int = 8192
    context_tokens: int = 8192
    output_tokens: int = 128
    connect_seconds: int = 2
    read_seconds: int = 10
    total_seconds: int = 15
    concurrency: int = 1
    requests_per_minute: int = 30

    def __post_init__(self):
        ceilings = {
            'max_request_bytes': 262144,
            'max_response_bytes': 16384,
            'context_tokens': 131072,
            'output_tokens': 512,
            'connect_seconds': 10,
            'read_seconds': 60,
            'total_seconds': 120,
            'concurrency': 8,
            'requests_per_minute': 120,
        }

        for name, ceiling in ceilings.items():
            value = getattr(self, name)
            require(type(value) is int and 1 <= value <= ceiling)

        require(self.output_tokens < self.context_tokens)
        require(
            self.connect_seconds <= self.total_seconds and self.read_seconds <= self.total_seconds
        )


@dataclass(frozen=True, kw_only=True)
class ModelManifest:
    model: str
    revision: str
    quantization: str
    backend: str
    backend_version: str
    serving_image: str
    tokenizer_version: str
    license_id: str
    artifacts: tuple[ModelArtifact, ...]
    # Fixed tuple prevents later mutation of a validated governance mapping.
    approvals: tuple[tuple[str, str], ...]
    limits: InferenceLimits

    def __post_init__(self):
        for value in (
            self.model,
            self.quantization,
            self.backend,
            self.backend_version,
            self.tokenizer_version,
            self.license_id,
        ):
            require(
                type(value) is str
                and 0 < len(value) <= 160
                and re.fullmatch(r'[A-Za-z0-9_.+/-]+', value) is not None
                and value.lower() not in ('main', 'latest', 'pending', 'to_be_pinned', 'blocked')
            )
        require(
            type(self.revision) is str and re.fullmatch(r'[0-9a-f]{40}', self.revision) is not None
        )
        require(digest(self.serving_image))

        require(type(self.artifacts) is tuple and 5 <= len(self.artifacts) <= 128)
        require(all(type(item) is ModelArtifact for item in self.artifacts))

        paths = [item.path.casefold() for item in self.artifacts]
        require(len(set(paths)) == len(paths))

        kinds = {item.kind for item in self.artifacts}
        require({'weights', 'tokenizer', 'config', 'chat-template', 'license'} <= kinds)
        require(self.quantization == 'none' or 'quantization' in kinds)

        require(type(self.approvals) is tuple and len(self.approvals) == 5)
        require(
            all(
                type(pair) is tuple and len(pair) == 2 and type(pair[0]) is str and digest(pair[1])
                for pair in self.approvals
            )
        )
        require(
            {pair[0] for pair in self.approvals}
            == {'model', 'data', 'privacy', 'security', 'platform'}
        )

        require(type(self.limits) is InferenceLimits)

    def to_dict(self):
        value = asdict(self)
        value['approvals'] = dict(self.approvals)

        return value

    @property
    def digest(self):
        return fingerprint(self.to_dict())

    @classmethod
    def from_bytes(cls, raw):
        try:
            value = strict_json(raw, maximum=131072)
            require(type(value) is dict and set(value) == set(cls.__dataclass_fields__))
            require(type(value['artifacts']) is list and type(value['approvals']) is dict)
            require(
                type(value['limits']) is dict
                and set(value['limits']) == set(InferenceLimits.__dataclass_fields__)
            )

            fields = value | {
                'artifacts': tuple(ModelArtifact(**item) for item in value['artifacts']),
                'approvals': tuple(sorted(value['approvals'].items())),
                'limits': InferenceLimits(**value['limits']),
            }

            return cls(**fields)
        except (TypeError, KeyError, ValueError, GenerationContractError):
            raise GenerationConfigurationError('Invalid model manifest document') from None

    def verify_directory(self, directory):
        try:
            root = Path(directory).resolve(strict=True)
            require(root.is_dir())

            expected = {item.path for item in self.artifacts}
            discovered = set()

            for path in root.rglob('*'):
                require(not path.is_symlink() and not path.is_junction())

                if path.is_file():
                    discovered.add(path.relative_to(root).as_posix())
                    require(len(discovered) <= len(expected))

            require(discovered == expected)

            for item in self.artifacts:
                path = root / item.path
                # Deny symlinks/junctions at every level, including links within root.
                for part in (path, *path.parents):
                    if part == root:
                        break
                    require(not part.is_symlink() and not part.is_junction())

                require(path.resolve(strict=True).is_relative_to(root) and path.is_file())
                checksum = sha256()

                with path.open('rb') as stream:
                    for block in iter(lambda: stream.read(1048576), b''):
                        checksum.update(block)

                require('sha256:' + checksum.hexdigest() == item.sha256)
        except (OSError, TypeError):
            raise GenerationConfigurationError('Model artifact verification failed') from None
