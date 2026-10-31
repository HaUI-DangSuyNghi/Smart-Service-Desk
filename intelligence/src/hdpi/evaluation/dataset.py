"""Generic deterministic parsing and exact-byte helpers for evaluation adapters."""

from hashlib import sha256
import json
from pathlib import PurePosixPath

import yaml

from .errors import EvaluationError


def require(condition, message):
    if not condition:
        raise EvaluationError(message)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate JSON key: ' + key)
        result[key] = value
    return result


def parse_json(raw):
    return json.loads(
        raw,
        object_pairs_hook=unique_object,
        parse_constant=lambda value: (_ for _ in ()).throw(
            EvaluationError('Nonfinite JSON number')
        ),
    )


class UniqueLoader(yaml.SafeLoader):
    pass


def yaml_mapping(loader, node):
    return unique_object(loader.construct_pairs(node, deep=True))


UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, yaml_mapping)


def safe_path(base, relative):
    require(isinstance(relative, str) and bool(relative), 'Missing manifest path')
    path = PurePosixPath(relative)
    require(
        not path.is_absolute()
        and '..' not in path.parts
        and '\\' not in relative
        and ':' not in relative,
        'Invalid manifest path',
    )
    resolved = (base / relative).resolve()
    require(
        resolved.is_relative_to(base.resolve()) and resolved.is_file(),
        'Missing or escaped dataset file',
    )
    return resolved


def verified_bytes(path, pin):
    raw = path.read_bytes()
    require(
        type(pin['byteSize']) is int and len(raw) == pin['byteSize'],
        'Dataset byte-size mismatch',
    )
    require(sha256(raw).hexdigest() == pin['sha256'], 'Dataset checksum mismatch')
    return raw
