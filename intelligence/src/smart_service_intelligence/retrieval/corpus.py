"""Safe local snapshot format; no pickle, executable deserialization or Core writes."""
from base64 import b64decode, b64encode
from dataclasses import asdict
import json
import os
from pathlib import Path
import tempfile

from ..domain.errors import CorpusUnavailable, EngineConfigurationError, CitationVerificationError
from .index import TfidfIndex, build_index
from .models import AuthorizedDocument, MAX_CORPUS_BYTES, PartitionKey


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate snapshot key')
        result[key] = value
    return result


def load_index(path: Path, expected: PartitionKey) -> TfidfIndex:
    try:
        with Path(path).open('rb') as stream:
            raw = stream.read(MAX_CORPUS_BYTES * 2 + 1)
        if len(raw) > MAX_CORPUS_BYTES * 2:
            raise ValueError('Oversized snapshot')
        data = json.loads(raw, object_pairs_hook=_unique_object)
        if set(data) != {'format', 'manifest', 'documents'} or data['format'] != 'ai03-json@0.1.0':
            raise ValueError('Unknown snapshot format')
        if data['manifest']['partition'] != asdict(expected):
            raise ValueError('Unexpected partition')
        documents = tuple(AuthorizedDocument(**{**d, 'content': b64decode(d['content'], validate=True)})
                          for d in data['documents'])
        index = build_index(documents, expected)
        if data['manifest'] != asdict(index.manifest):
            raise ValueError('Snapshot manifest differs from rebuilt index')
        return index
    except OSError as error:
        raise CorpusUnavailable('Index snapshot cannot be opened') from error
    except (ValueError, TypeError, KeyError, AttributeError, CitationVerificationError) as error:
        raise EngineConfigurationError('Corrupt or incompatible index snapshot') from error


def publish_index(index: TfidfIndex, path: Path):
    """Caller selects a trusted ACL-protected directory, never a client path."""
    path = Path(path)
    data = {'format': 'ai03-json@0.1.0', 'manifest': asdict(index.manifest),
            'documents': [{**asdict(d), 'content': b64encode(d.content).decode('ascii')} for d in index.documents]}
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent,
                                         prefix='.ai03-', suffix='.json', delete=False) as stream:
            temporary = Path(stream.name)
            json.dump(data, stream, ensure_ascii=False, sort_keys=True, allow_nan=False)
            stream.flush()
            os.fsync(stream.fileno())
        load_index(temporary, index.manifest.partition)
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
