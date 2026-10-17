import json
from pathlib import Path

from jsonschema import Draft202012Validator
from jsonschema.exceptions import SchemaError

from ..domain.errors import EngineConfigurationError
from ..resources import resource_root


def load_validator(filename: str, root: Path | None = None) -> Draft202012Validator:
    try:
        schema = json.loads(((root or resource_root('contracts')) / filename).read_text(encoding='utf-8'))
        Draft202012Validator.check_schema(schema)
        return Draft202012Validator(schema)
    except (OSError, ValueError, SchemaError) as error:
        raise EngineConfigurationError('Invalid shared contract resource') from error
