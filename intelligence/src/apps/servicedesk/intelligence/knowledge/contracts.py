"""Service Desk data contracts; domain policy must stay in this application."""
from hashlib import sha256
from pathlib import Path
import unicodedata

from jsonschema import Draft202012Validator, ValidationError

from hdpi.evaluation.dataset import parse_json, require, safe_path, verified_bytes
from hdpi.evaluation.errors import EvaluationError

SCHEMA_DIRECTORY = Path('contracts/apps/servicedesk/intelligence')


def validate_schema(value, name, root):
    schema = parse_json((root / SCHEMA_DIRECTORY / (name + '.schema.json')).read_bytes())
    try:
        Draft202012Validator(schema).validate(value)
    except ValidationError as error:
        raise EvaluationError('Invalid ' + name + ': ' + error.message) from error
    return value


def read_pin(root, pin):
    raw = verified_bytes(safe_path(root, pin['path']), pin)
    text = raw.decode('utf-8')
    require(unicodedata.normalize('NFC', text) == text and '\r' not in text, 'Expected NFC UTF-8 with LF bytes')
    return raw


def pin_file(root, relative):
    path = safe_path(root, relative)
    raw = path.read_bytes()
    return {'path': relative, 'sha256': sha256(raw).hexdigest(), 'byteSize': len(raw)}


def review_consistency(value, state):
    provenance = value['provenance']
    if state == 'REVIEWED':
        require(provenance['humanReviewed'] is True and bool(provenance['reviewer']), 'Missing actual human review')
    else:
        require(provenance['humanReviewed'] is False and provenance['reviewer'] is None, 'Draft claims human approval')
